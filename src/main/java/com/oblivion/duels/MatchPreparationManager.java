
package com.oblivion.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MatchPreparationManager {

    private final JavaPlugin plugin;
    private final MatchManager matchManager;
    private final ArenaManager arenaManager;

    private final Map<UUID, BukkitTask> countdownTasks =
            new HashMap<>();

    public MatchPreparationManager(
            JavaPlugin plugin,
            MatchManager matchManager,
            ArenaManager arenaManager
    ) {
        this.plugin = plugin;
        this.matchManager = matchManager;
        this.arenaManager = arenaManager;
    }

    public boolean prepareMatch(
            DuelMatch match,
            DuelArena arena
    ) {
        if (match == null || arena == null || !arena.isReady()) {
            return false;
        }

        if (match.getState() != DuelMatch.MatchState.WAITING) {
            return false;
        }

        if (arena.getTeamASpawn() == null
                || arena.getTeamBSpawn() == null) {
            return false;
        }

        for (UUID playerId : match.getTeamA()) {
            Player player = Bukkit.getPlayer(playerId);

            if (player == null || !player.isOnline()) {
                abortMatch(match, arena, "A player disconnected.");
                return false;
            }
        }

        for (UUID playerId : match.getTeamB()) {
            Player player = Bukkit.getPlayer(playerId);

            if (player == null || !player.isOnline()) {
                abortMatch(match, arena, "A player disconnected.");
                return false;
            }
        }

        for (UUID playerId : match.getTeamA()) {
            Player player = Bukkit.getPlayer(playerId);
            player.teleport(arena.getTeamASpawn());
        }

        for (UUID playerId : match.getTeamB()) {
            Player player = Bukkit.getPlayer(playerId);
            player.teleport(arena.getTeamBSpawn());
        }

        match.setState(DuelMatch.MatchState.COUNTDOWN);

        for (UUID playerId : match.getTeamA()) {
            Player player = Bukkit.getPlayer(playerId);

            if (player != null) {
                player.sendMessage(ChatColor.LIGHT_PURPLE
                        + "OblivionDuels "
                        + ChatColor.GRAY
                        + "Match starting soon!");
            }
        }

        for (UUID playerId : match.getTeamB()) {
            Player player = Bukkit.getPlayer(playerId);

            if (player != null) {
                player.sendMessage(ChatColor.LIGHT_PURPLE
                        + "OblivionDuels "
                        + ChatColor.GRAY
                        + "Match starting soon!");
            }
        }

        startCountdown(match, arena);
        return true;
    }

    private void startCountdown(
            DuelMatch match,
            DuelArena arena
    ) {
        int seconds = Math.max(
                1,
                plugin.getConfig().getInt(
                        "matchmaking.countdown-seconds",
                        10
                )
        );

        UUID matchId = match.getMatchId();

        BukkitTask task = new BukkitRunnable() {

            int remaining = seconds;

            @Override
            public void run() {

                if (match.getState()
                        != DuelMatch.MatchState.COUNTDOWN) {
                    cancel();
                    countdownTasks.remove(matchId);
                    return;
                }

                for (UUID playerId : match.getTeamA()) {
                    Player player = Bukkit.getPlayer(playerId);

                    if (player == null || !player.isOnline()) {
                        cancel();
                        countdownTasks.remove(matchId);
                        abortMatch(match, arena,
                                "A player disconnected.");
                        return;
                    }
                }

                for (UUID playerId : match.getTeamB()) {
                    Player player = Bukkit.getPlayer(playerId);

                    if (player == null || !player.isOnline()) {
                        cancel();
                        countdownTasks.remove(matchId);
                        abortMatch(match, arena,
                                "A player disconnected.");
                        return;
                    }
                }

                if (remaining <= 0) {
                    match.setState(DuelMatch.MatchState.ACTIVE);

                    broadcast(match,
                            ChatColor.GREEN + "FIGHT!");

                    cancel();
                    countdownTasks.remove(matchId);
                    return;
                }

                broadcast(match,
                        ChatColor.YELLOW
                                + "Match starts in "
                                + remaining + "...");

                remaining--;
            }

        }.runTaskTimer(plugin, 0L, 20L);

        countdownTasks.put(matchId, task);
    }

    private void broadcast(DuelMatch match, String message) {
        for (UUID playerId : match.getTeamA()) {
            Player player = Bukkit.getPlayer(playerId);

            if (player != null) {
                player.sendMessage(message);
            }
        }

        for (UUID playerId : match.getTeamB()) {
            Player player = Bukkit.getPlayer(playerId);

            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void abortMatch(
            DuelMatch match,
            DuelArena arena,
            String reason
    ) {
        BukkitTask task = countdownTasks.remove(
                match.getMatchId()
        );

        if (task != null) {
            task.cancel();
        }

        match.setState(DuelMatch.MatchState.ENDED);

        matchManager.endMatch(match.getMatchId());
        arenaManager.releaseArena(arena.getName());

        broadcast(match,
                ChatColor.RED + "Match cancelled: " + reason);
    }

    public void shutdown() {
        for (BukkitTask task : countdownTasks.values()) {
            task.cancel();
        }

        countdownTasks.clear();
    }
}
