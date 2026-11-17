
package com.oblivion.duels;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DatabaseManager {

    private final JavaPlugin plugin;
    private Connection connection;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            File folder = plugin.getDataFolder();

            if (!folder.exists() && !folder.mkdirs()) {
                throw new IllegalStateException(
                        "Could not create plugin data folder."
                );
            }

            Class.forName("org.sqlite.JDBC");

            File databaseFile = new File(folder, "duels.db");

            connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + databaseFile.getAbsolutePath()
            );

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_stats (
                            uuid TEXT PRIMARY KEY,
                            wins INTEGER NOT NULL DEFAULT 0,
                            losses INTEGER NOT NULL DEFAULT 0,
                            kills INTEGER NOT NULL DEFAULT 0,
                            deaths INTEGER NOT NULL DEFAULT 0
                        )
                        """);

                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS pending_loot (
                            uuid TEXT NOT NULL,
                            item_index INTEGER NOT NULL,
                            item_data BLOB NOT NULL,
                            PRIMARY KEY (uuid, item_index)
                        )
                        """);

                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS loot_chests (
                            match_id TEXT PRIMARY KEY,
                            created_at INTEGER NOT NULL
                        )
                        """);

                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS loot_chest_members (
                            match_id TEXT NOT NULL,
                            uuid TEXT NOT NULL,
                            PRIMARY KEY (match_id, uuid),
                            FOREIGN KEY (match_id)
                                REFERENCES loot_chests(match_id)
                                ON DELETE CASCADE
                        )
                        """);

                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS loot_chest_items (
                            match_id TEXT NOT NULL,
                            item_slot INTEGER NOT NULL,
                            item_data BLOB NOT NULL,
                            PRIMARY KEY (match_id, item_slot),
                            FOREIGN KEY (match_id)
                                REFERENCES loot_chests(match_id)
                                ON DELETE CASCADE
                        )
                        """);

                statement.executeUpdate("PRAGMA foreign_keys = ON");
            }

            plugin.getLogger().info(
                    "SQLite database connected successfully!"
            );

        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Could not initialize SQLite database!"
            );
            exception.printStackTrace();
        }
    }

    // =========================================================
    // PLAYER STATISTICS
    // =========================================================

    public void loadStats(StatsManager statsManager) {
        if (!isConnected()) {
            return;
        }

        String sql = "SELECT * FROM player_stats";

        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(sql)) {

            while (results.next()) {
                UUID playerId = UUID.fromString(
                        results.getString("uuid")
                );

                PlayerStats stats = new PlayerStats(
                        results.getInt("wins"),
                        results.getInt("losses"),
                        results.getInt("kills"),
                        results.getInt("deaths")
                );

                statsManager.setStats(playerId, stats);
            }

            plugin.getLogger().info(
                    "Player statistics loaded successfully!"
            );

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Could not load player statistics!"
            );
            exception.printStackTrace();
        }
    }

    public void saveStats(UUID playerId, PlayerStats stats) {
        if (!isConnected() || playerId == null || stats == null) {
            return;
        }

        String sql = """
                INSERT INTO player_stats
                (uuid, wins, losses, kills, deaths)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    wins = excluded.wins,
                    losses = excluded.losses,
                    kills = excluded.kills,
                    deaths = excluded.deaths
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, playerId.toString());
            statement.setInt(2, stats.getWins());
            statement.setInt(3, stats.getLosses());
            statement.setInt(4, stats.getKills());
            statement.setInt(5, stats.getDeaths());

            statement.executeUpdate();

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Could not save statistics for " + playerId
            );
            exception.printStackTrace();
        }
    }

    public void saveAll(StatsManager statsManager) {
        if (!isConnected()) {
            return;
        }

        try {
            connection.setAutoCommit(false);

            for (Map.Entry<UUID, PlayerStats> entry
                    : statsManager.getAllStats().entrySet()) {

                saveStats(entry.getKey(), entry.getValue());
            }

            connection.commit();

        } catch (SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                rollbackException.printStackTrace();
            }

            plugin.getLogger().severe(
                    "Could not save all player statistics!"
            );
            exception.printStackTrace();

        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        }
    }

    // =========================================================
    // PENDING LOOT
    // =========================================================

    public void savePendingLoot(
            UUID playerId,
            List<ItemStack> items
    ) {
        if (!isConnected() || playerId == null || items == null) {
            return;
        }

        boolean previousAutoCommit = true;

        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement delete =
                         connection.prepareStatement(
                                 "DELETE FROM pending_loot WHERE uuid = ?"
                         )) {

                delete.setString(1, playerId.toString());
                delete.executeUpdate();
            }

            String sql = """
                    INSERT INTO pending_loot
                    (uuid, item_index, item_data)
                    VALUES (?, ?, ?)
                    """;

            try (PreparedStatement insert =
                         connection.prepareStatement(sql)) {

                int index = 0;

                for (ItemStack item : items) {
                    if (item == null || item.getType().isAir()) {
                        continue;
                    }

                    insert.setString(1, playerId.toString());
                    insert.setInt(2, index++);
                    insert.setBytes(3, item.serializeAsBytes());
                    insert.addBatch();
                }

                insert.executeBatch();
            }

            connection.commit();

        } catch (Exception exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                rollbackException.printStackTrace();
            }

            plugin.getLogger().severe(
                    "Could not save pending loot for " + playerId
            );
            exception.printStackTrace();

        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        }
    }

    public List<ItemStack> loadPendingLoot(UUID playerId) {
        List<ItemStack> items = new ArrayList<>();

        if (!isConnected() || playerId == null) {
            return items;
        }

        String sql = """
                SELECT item_data
                FROM pending_loot
                WHERE uuid = ?
                ORDER BY item_index ASC
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, playerId.toString());

            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    byte[] data = results.getBytes("item_data");

                    if (data == null || data.length == 0) {
                        continue;
                    }

                    try {
                        ItemStack item = ItemStack.deserializeBytes(data);

                        if (item != null && !item.getType().isAir()) {
                            items.add(item);
                        }
                    } catch (Exception exception) {
                        plugin.getLogger().warning(
                                "Could not deserialize a pending item for "
                                        + playerId
                        );
                    }
                }
            }

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Could not load pending loot for " + playerId
            );
            exception.printStackTrace();
        }

        return items;
    }

    public void removePendingLoot(UUID playerId) {
        if (!isConnected() || playerId == null) {
            return;
        }

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             "DELETE FROM pending_loot WHERE uuid = ?"
                     )) {

            statement.setString(1, playerId.toString());
            statement.executeUpdate();

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Could not remove pending loot for " + playerId
            );
            exception.printStackTrace();
        }
    }

    // =========================================================
    // SHARED VIRTUAL LOOT CHEST
    // =========================================================

    public boolean saveLootChest(
            UUID matchId,
            List<UUID> winners,
            ItemStack[] contents
    ) {
        if (!isConnected()
                || matchId == null
                || winners == null
                || contents == null) {
            return false;
        }

        boolean previousAutoCommit = true;

        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement insertChest =
                         connection.prepareStatement("""
                                 INSERT INTO loot_chests
                                 (match_id, created_at)
                                 VALUES (?, ?)
                                 ON CONFLICT(match_id) DO NOTHING
                                 """)) {

                insertChest.setString(1, matchId.toString());
                insertChest.setLong(2, System.currentTimeMillis());
                insertChest.executeUpdate();
            }

            try (PreparedStatement deleteMembers =
                         connection.prepareStatement(
                                 "DELETE FROM loot_chest_members "
                                         + "WHERE match_id = ?"
                         )) {

                deleteMembers.setString(1, matchId.toString());
                deleteMembers.executeUpdate();
            }

            try (PreparedStatement insertMember =
                         connection.prepareStatement("""
                                 INSERT INTO loot_chest_members
                                 (match_id, uuid)
                                 VALUES (?, ?)
                                 """)) {

                for (UUID winner : winners) {
                    if (winner == null) {
                        continue;
                    }

                    insertMember.setString(1, matchId.toString());
                    insertMember.setString(2, winner.toString());
                    insertMember.addBatch();
                }

                insertMember.executeBatch();
            }

            saveLootChestItemsInternal(matchId, contents);

            connection.commit();
            return true;

        } catch (Exception exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                rollbackException.printStackTrace();
            }

            plugin.getLogger().severe(
                    "Could not save loot chest for match " + matchId
            );
            exception.printStackTrace();
            return false;

        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        }
    }

    public boolean updateLootChestItems(
            UUID matchId,
            ItemStack[] contents
    ) {
        if (!isConnected()
                || matchId == null
                || contents == null) {
            return false;
        }

        boolean previousAutoCommit = true;

        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement check =
                         connection.prepareStatement(
                                 "SELECT 1 FROM loot_chests "
                                         + "WHERE match_id = ?"
                         )) {

                check.setString(1, matchId.toString());

                try (ResultSet results = check.executeQuery()) {
                    if (!results.next()) {
                        connection.rollback();
                        return false;
                    }
                }
            }

            saveLootChestItemsInternal(matchId, contents);

            connection.commit();
            return true;

        } catch (Exception exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                rollbackException.printStackTrace();
            }

            plugin.getLogger().severe(
                    "Could not update loot chest for match " + matchId
            );
            exception.printStackTrace();
            return false;

        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        }
    }

    private void saveLootChestItemsInternal(
            UUID matchId,
            ItemStack[] contents
    ) throws SQLException {

        try (PreparedStatement deleteItems =
                     connection.prepareStatement(
                             "DELETE FROM loot_chest_items "
                                     + "WHERE match_id = ?"
                     )) {

            deleteItems.setString(1, matchId.toString());
            deleteItems.executeUpdate();
        }

        String sql = """
                INSERT INTO loot_chest_items
                (match_id, item_slot, item_data)
                VALUES (?, ?, ?)
                """;

        try (PreparedStatement insert =
                     connection.prepareStatement(sql)) {

            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack item = contents[slot];

                if (item == null || item.getType().isAir()) {
                    continue;
                }

                insert.setString(1, matchId.toString());
                insert.setInt(2, slot);
                insert.setBytes(3, item.serializeAsBytes());
                insert.addBatch();
            }

            insert.executeBatch();
        }
    }

    public LootChestData loadLootChest(UUID matchId) {
        if (!isConnected() || matchId == null) {
            return null;
        }

        boolean chestExists = false;
        List<UUID> winners = new ArrayList<>();
        Map<Integer, ItemStack> items = new HashMap<>();

        try (PreparedStatement chestStatement =
                     connection.prepareStatement(
                             "SELECT 1 FROM loot_chests WHERE match_id = ?"
                     )) {

            chestStatement.setString(1, matchId.toString());

            try (ResultSet results = chestStatement.executeQuery()) {
                chestExists = results.next();
            }

        } catch (SQLException exception) {
            exception.printStackTrace();
            return null;
        }

        if (!chestExists) {
            return null;
        }

        try (PreparedStatement memberStatement =
                     connection.prepareStatement("""
                             SELECT uuid
                             FROM loot_chest_members
                             WHERE match_id = ?
                             """)) {

            memberStatement.setString(1, matchId.toString());

            try (ResultSet results = memberStatement.executeQuery()) {
                while (results.next()) {
                    try {
                        winners.add(UUID.fromString(
                                results.getString("uuid")
                        ));
                    } catch (IllegalArgumentException exception) {
                        plugin.getLogger().warning(
                                "Invalid winner UUID in loot chest "
                                        + matchId
                        );
                    }
                }
            }

        } catch (SQLException exception) {
            exception.printStackTrace();
            return null;
        }

        try (PreparedStatement itemStatement =
                     connection.prepareStatement("""
                             SELECT item_slot, item_data
                             FROM loot_chest_items
                             WHERE match_id = ?
                             ORDER BY item_slot ASC
                             """)) {

            itemStatement.setString(1, matchId.toString());

            try (ResultSet results = itemStatement.executeQuery()) {
                while (results.next()) {
                    int slot = results.getInt("item_slot");
                    byte[] data = results.getBytes("item_data");

                    if (data == null || data.length == 0) {
                        continue;
                    }

                    try {
                        ItemStack item = ItemStack.deserializeBytes(data);

                        if (item != null && !item.getType().isAir()) {
                            items.put(slot, item);
                        }
                    } catch (Exception exception) {
                        plugin.getLogger().warning(
                                "Could not deserialize loot chest item "
                                        + slot + " for match " + matchId
                        );
                    }
                }
            }

        } catch (SQLException exception) {
            exception.printStackTrace();
            return null;
        }

        return new LootChestData(matchId, winners, items);
    }

    /**
     * Finds the newest shared loot chest belonging to a player.
     * Returns null if the player has no available chest.
     */
    public LootChestData findLatestLootChestForPlayer(UUID playerId) {
        if (!isConnected() || playerId == null) {
            return null;
        }

        String sql = """
                SELECT c.match_id
                FROM loot_chests c
                INNER JOIN loot_chest_members m
                    ON c.match_id = m.match_id
                WHERE m.uuid = ?
                ORDER BY c.created_at DESC, c.match_id DESC
                LIMIT 1
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, playerId.toString());

            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return null;
                }

                UUID matchId = UUID.fromString(
                        results.getString("match_id")
                );

                return loadLootChest(matchId);
            }

        } catch (SQLException | IllegalArgumentException exception) {
            plugin.getLogger().severe(
                    "Could not find loot chest for " + playerId
            );
            exception.printStackTrace();
            return null;
        }
    }

    public boolean deleteLootChest(UUID matchId) {
        if (!isConnected() || matchId == null) {
            return false;
        }

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             "DELETE FROM loot_chests WHERE match_id = ?"
                     )) {

            statement.setString(1, matchId.toString());
            return statement.executeUpdate() > 0;

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Could not delete loot chest for match " + matchId
            );
            exception.printStackTrace();
            return false;
        }
    }

    // =========================================================
    // CONNECTION
    // =========================================================

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException exception) {
            return false;
        }
    }

    public void close() {
        if (!isConnected()) {
            return;
        }

        try {
            connection.close();

            plugin.getLogger().info(
                    "SQLite database connection closed."
            );

        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    // =========================================================
    // LOOT CHEST DATA
    // =========================================================

    public static final class LootChestData {

        private final UUID matchId;
        private final List<UUID> winners;
        private final Map<Integer, ItemStack> items;

        public LootChestData(
                UUID matchId,
                List<UUID> winners,
                Map<Integer, ItemStack> items
        ) {
            this.matchId = matchId;
            this.winners = new ArrayList<>(winners);
            this.items = new HashMap<>(items);
        }

        public UUID getMatchId() {
            return matchId;
        }

        public List<UUID> getWinners() {
            return new ArrayList<>(winners);
        }

        public Map<Integer, ItemStack> getItems() {
            return new HashMap<>(items);
        }

        public boolean isWinner(UUID playerId) {
            return playerId != null && winners.contains(playerId);
        }
    }
}
