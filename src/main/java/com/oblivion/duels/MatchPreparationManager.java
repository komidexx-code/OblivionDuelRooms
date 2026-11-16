
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

    public boolean prepareMatch(DuelMatch match, DuelArena arena) {

        if (match == null) {
            return false;
        }

        if (match.getState() != DuelMatch.MatchState.WAITING) {
            return false;
        }

        if (arena == null) {
            abortMatch(match, null, "Arena could not be found.");
            return false;
        }

        if (!arena.isReady()) {
            abortMatch(match, arena, "Arena is not ready.");
            return false;
        }

        if (arena.getTeamASpawn() == null
                || arena.getTeamBSpawn() == null) {
            abortMatch(match, arena, "Arena spawns are missing.");
            return false;
        }

        for (UUID id : match.getTeamA()) {
            Player player = Bukkit.getPlayer(id);

            if (player == null || !player.isOnline()) {
                abortMatch(match, arena, "A player disconnected.");
                return false;
            }
        }

        for (UUID id : match.getTeamB()) {
            Player player = Bukkit.getPlayer(id);

            if (player == null || !player.isOnline()) {
                abortMatch(match, arena, "A player disconnected.");
                return false;
            }
        }

        for (UUID id : match.getTeamA()) {
            Player player = Bukkit.getPlayer(id);

            if (player == null
                    || !player.teleport(arena.getTeamASpawn())) {
                abortMatch(match, arena, "A player could not be teleported.");
                return false;
            }
        }

        for (UUID id : match.getTeamB()) {
            Player player = Bukkit.getPlayer(id);

            if (player == null
                    || !player.teleport(arena.getTeamBSpawn())) {
                abortMatch(match, arena, "A player could not be teleported.");
                return false;
            }
        }

        match.setState(DuelMatch.MatchState.COUNTDOWN);

        broadcast(match, ChatColor.LIGHT_PURPLE
                + "OblivionDuels " + ChatColor.GRAY
                + "Match starting soon!");

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

                if (matchManager.getMatch(matchId) != match) {
                    cancel();
                    countdownTasks.remove(matchId);
                    return;
                }

                for (UUID id : match.getTeamA()) {
                    Player player = Bukkit.getPlayer(id);

                    if (player == null || !player.isOnline()) {
                        cancel();
                        countdownTasks.remove(matchId);
                        abortMatch(
                                match,
                                arena,
                                "A player disconnected."
                        );
                        return;
                    }
                }

                for (UUID id : match.getTeamB()) {
                    Player player = Bukkit.getPlayer(id);

                    if (player == null || !player.isOnline()) {
                        cancel();
                        countdownTasks.remove(matchId);
                        abortMatch(
                                match,
                                arena,
                                "A player disconnected."
                        );
                        return;
                    }
                }

                if (remaining <= 0) {

                    match.setState(DuelMatch.MatchState.ACTIVE);

                    broadcast(
                            match,
                            ChatColor.GREEN + "FIGHT!"
                    );

                    if (plugin instanceof OblivionDuels duels) {
                        MatchDeathListener listener =
                                duels.getMatchDeathListener();

                        if (listener != null) {
                            listener.startMatchTimeout(match);
                        }
                    }

                    cancel();
                    countdownTasks.remove(matchId);
                    return;
                }

                broadcast(
                        match,
                        ChatColor.YELLOW
                                + "Match starts in "
                                + remaining
                                + "..."
                );

                remaining--;
            }

        }.runTaskTimer(plugin, 0L, 20L);

        countdownTasks.put(matchId, task);
    }

    private void broadcast(
            DuelMatch match,
            String message
    ) {

        for (UUID id : match.getTeamA()) {
            Player player = Bukkit.getPlayer(id);

            if (player != null) {
                player.sendMessage(message);
            }
        }

        for (UUID id : match.getTeamB()) {
            Player player = Bukkit.getPlayer(id);

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

        if (match == null) {
            return;
        }

        if (match.getState() == DuelMatch.MatchState.ENDING
                || match.getState() == DuelMatch.MatchState.ENDED) {
            return;
        }

        BukkitTask task = countdownTasks.remove(
                match.getMatchId()
        );

        if (task != null) {
            task.cancel();
        }

        match.setState(DuelMatch.MatchState.ENDED);

        matchManager.endMatch(match.getMatchId());

        if (arena != null) {
            arenaManager.releaseArena(arena.getName());
        }

        broadcast(
                match,
                ChatColor.RED + "Match cancelled: " + reason
        );
    }

    public void shutdown() {

        for (BukkitTask task : countdownTasks.values()) {
            task.cancel();
        }

        countdownTasks.clear();
    }
}
