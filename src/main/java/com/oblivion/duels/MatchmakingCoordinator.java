
package com.oblivion.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;

public final class MatchmakingCoordinator {

    private final JavaPlugin plugin;
    private final MatchmakingManager matchmaking;
    private final MatchManager matchManager;
    private final ArenaManager arenaManager;

    private BukkitTask task;

    public MatchmakingCoordinator(
            JavaPlugin plugin,
            MatchmakingManager matchmaking,
            MatchManager matchManager,
            ArenaManager arenaManager
    ) {
        this.plugin = plugin;
        this.matchmaking = matchmaking;
        this.matchManager = matchManager;
        this.arenaManager = arenaManager;
    }

    public void start() {
        if (task != null) {
            return;
        }

        // Check matchmaking queues every second.
        task = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::processQueues,
                20L,
                20L
        );
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void processQueues() {
        for (int teamSize = 1; teamSize <= 5; teamSize++) {
            processQueue(teamSize);
        }
    }

    private void processQueue(int teamSize) {

        while (matchmaking.isQueueFull(teamSize)) {

            List<UUID> players =
                    matchmaking.peekPlayersForMatch(teamSize);

            if (players.isEmpty()) {
                return;
            }

            // Remove players who are offline or already in a match.
            boolean invalidPlayerFound = false;

            for (UUID playerId : players) {
                Player player = Bukkit.getPlayer(playerId);

                if (player == null || !player.isOnline()
                        || matchManager.isInMatch(playerId)) {

                    matchmaking.leaveQueue(playerId);
                    invalidPlayerFound = true;
                }
            }

            if (invalidPlayerFound) {
                continue;
            }

            DuelArena arena = arenaManager.getAvailableArena();

            if (arena == null) {
                return;
            }

            if (!arenaManager.reserveArena(arena.getName())) {
                return;
            }

            DuelMatch match;

            try {
                match = matchManager.createMatch(
                        teamSize,
                        players,
                        arena.getName()
                );

            } catch (Exception exception) {

                arenaManager.releaseArena(arena.getName());

                plugin.getLogger().severe(
                        "Could not create duel match: "
                                + exception.getMessage()
                );

                return;
            }

            if (!matchmaking.removePlayersForMatch(
                    teamSize, players
            )) {

                matchManager.endMatch(match.getMatchId());
                arenaManager.releaseArena(arena.getName());

                plugin.getLogger().warning(
                        "Match preparation was cancelled because "
                                + "players could not be removed from the queue."
                );

                return;
            }

            notifyPlayers(match, arena);
        }
    }

    private void notifyPlayers(
            DuelMatch match,
            DuelArena arena
    ) {
        String prefix = ChatColor.DARK_GRAY
                + "[" + ChatColor.LIGHT_PURPLE
                + "OblivionDuels" + ChatColor.DARK_GRAY
                + "] ";

        for (UUID playerId : match.getTeamA()) {
            Player player = Bukkit.getPlayer(playerId);

            if (player != null) {
                player.sendMessage(prefix + ChatColor.GREEN
                        + "A match has been prepared!");

                player.sendMessage(ChatColor.GRAY
                        + "Mode: " + match.getTeamSize()
                        + "v" + match.getTeamSize());

                player.sendMessage(ChatColor.GRAY
                        + "Arena: " + arena.getName());

                player.sendMessage(ChatColor.GRAY
                        + "Team: A");
            }
        }

        for (UUID playerId : match.getTeamB()) {
            Player player = Bukkit.getPlayer(playerId);

            if (player != null) {
                player.sendMessage(prefix + ChatColor.GREEN
                        + "A match has been prepared!");

                player.sendMessage(ChatColor.GRAY
                        + "Mode: " + match.getTeamSize()
                        + "v" + match.getTeamSize());

                player.sendMessage(ChatColor.GRAY
                        + "Arena: " + arena.getName());

                player.sendMessage(ChatColor.GRAY
                        + "Team: B");
            }
        }

        plugin.getLogger().info(
                "Prepared " + match.getTeamSize()
                        + "v" + match.getTeamSize()
                        + " match in arena " + arena.getName()
        );
    }
}
