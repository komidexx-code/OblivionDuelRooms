
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
}
