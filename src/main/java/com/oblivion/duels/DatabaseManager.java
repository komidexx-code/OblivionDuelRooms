
package com.oblivion.duels;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
        if (!isConnected()) {
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
