
package com.oblivion.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MatchPreparationManager {

    private final JavaPlugin plugin;
    private final MatchManager matchManager;
    private final ArenaManager arenaManager;
    private final ArenaRollbackManager rollbackManager;

    private final Map<UUID, BukkitTask> countdownTasks =
            new HashMap<>();

    private final Map<UUID, Map<UUID, Location>> originalLocations =
            new HashMap<>();

    public MatchPreparationManager(
            JavaPlugin plugin,
            MatchManager matchManager,
            ArenaManager arenaManager,
            ArenaRollbackManager rollbackManager
    ) {
        this.plugin = plugin;
        this.matchManager = matchManager;
        this.arenaManager = arenaManager;
        this.rollbackManager = rollbackManager;
    }

    public boolean prepareMatch(DuelMatch match, DuelArena arena) {

        if (match == null
                || match.getState() != DuelMatch.MatchState.WAITING) {
            return false;
        }

        if (arena == null) {
            abortMatch(match, null, "Arena could not be found.");
            return false;
        }

        if (!isArenaValid(match, arena)) {
            abortMatch(match, arena, "Arena is invalid for this match.");
            return false;
        }

        Map<UUID, Location> savedLocations = new HashMap<>();

        for (UUID id : match.getTeamA()) {
            Player player = Bukkit.getPlayer(id);

            if (player == null || !player.isOnline()) {
                abortMatch(match, arena, "A player disconnected.");
                return false;
            }

            savedLocations.put(id, player.getLocation().clone());
        }

        for (UUID id : match.getTeamB()) {
            Player player = Bukkit.getPlayer(id);

            if (player == null || !player.isOnline()) {
                abortMatch(match, arena, "A player disconnected.");
                return false;
            }

            savedLocations.put(id, player.getLocation().clone());
        }

        UUID matchId = match.getMatchId();

        if (!rollbackManager.startTracking(arena)) {
            abortMatch(
                    match,
                    arena,
                    "Arena rollback tracking could not start."
            );
            return false;
        }

        originalLocations.put(matchId, savedLocations);

        if (!teleportTeams(match, arena)) {
            abortMatch(
                    match,
                    arena,
                    "A player could not be teleported."
            );
            return false;
        }

        saveParticipantSpawns(match, arena);

        match.setState(DuelMatch.MatchState.COUNTDOWN);

        broadcast(
                match,
                ChatColor.LIGHT_PURPLE
                        + "OblivionDuels "
                        + ChatColor.GRAY
                        + "Match starting soon!"
        );

        startCountdown(match, arena, true);

        return true;
    }

    /**
     * Prepares the next round without restarting arena rollback tracking
     * or resetting the overall match timeout.
     */
    public boolean prepareNextRound(
            DuelMatch match,
            DuelArena arena
    ) {
        if (match == null
                || match.getState() != DuelMatch.MatchState.ACTIVE) {
            return false;
        }

        if (arena == null || !isArenaValid(match, arena)) {
            abortMatch(match, arena, "Arena is invalid for the next round.");
            return false;
        }

        UUID matchId = match.getMatchId();

        if (matchManager.getMatch(matchId) != match) {
            return false;
        }

        if (!match.advanceRound()) {
            return false;
        }

        match.setState(DuelMatch.MatchState.COUNTDOWN);

        if (!teleportTeams(match, arena)) {
            abortMatch(
                    match,
                    arena,
                    "A player could not be prepared for the next round."
            );
            return false;
        }

        broadcast(
                match,
                ChatColor.LIGHT_PURPLE
                        + "Round "
                        + match.getCurrentRound()
                        + " is starting soon!"
        );

        startCountdown(match, arena, false);

        return true;
    }

    private boolean isArenaValid(
            DuelMatch match,
            DuelArena arena
    ) {
        return arena.isReady()
                && arena.getTeamSize() == match.getTeamA().size()
                && match.getTeamA().size() == match.getTeamB().size();
    }

    private boolean teleportTeams(
            DuelMatch match,
            DuelArena arena
    ) {
        for (UUID id : match.getTeamA()) {
            Player player = Bukkit.getPlayer(id);

            if (!preparePlayer(player, arena.getTeamASpawn())) {
                return false;
            }
        }

        for (UUID id : match.getTeamB()) {
            Player player = Bukkit.getPlayer(id);

            if (!preparePlayer(player, arena.getTeamBSpawn())) {
                return false;
            }
        }

        return true;
    }

    private boolean preparePlayer(
            Player player,
            Location spawn
    ) {
        if (player == null
                || !player.isOnline()
                || spawn == null
                || !player.teleport(spawn)) {
            return false;
        }

        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        player.setExp(0.0f);
        player.setLevel(0);

        return true;
    }

    private void saveParticipantSpawns(
            DuelMatch match,
            DuelArena arena
    ) {
        if (!(plugin instanceof OblivionDuels duels)) {
            return;
        }

        MatchDeathListener listener =
                duels.getMatchDeathListener();

        if (listener != null) {
            listener.saveParticipantSpawns(
                    match,
                    arena.getName()
            );
        }
    }

    private void startCountdown(
            DuelMatch match,
            DuelArena arena,
            boolean startMatchTimeout
    ) {
        int seconds = Math.max(
                1,
                plugin.getConfig().getInt(
                        "matchmaking.countdown-seconds",
                        10
                )
        );

        UUID matchId = match.getMatchId();

        BukkitTask previousTask = countdownTasks.remove(matchId);

        if (previousTask != null) {
            previousTask.cancel();
        }

        BukkitTask task = new BukkitRunnable() {

            int remaining = seconds;

            @Override
            public void run() {

                if (match.getState()
                        != DuelMatch.MatchState.COUNTDOWN
                        || matchManager.getMatch(matchId) != match) {
                    cancel();
                    countdownTasks.remove(matchId);
                    return;
                }

                for (UUID id : match.getTeamA()) {
                    Player player = Bukkit.getPlayer(id);

                    if (player == null || !player.isOnline()) {
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

                    if (startMatchTimeout) {
                        originalLocations.remove(matchId);
                    }

                    broadcast(
                            match,
                            ChatColor.GREEN + "FIGHT!"
                    );

                    if (startMatchTimeout
                            && plugin instanceof OblivionDuels duels) {

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
                                + "Round "
                                + match.getCurrentRound()
                                + " starts in "
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

    private void restoreOriginalLocations(UUID matchId) {
        Map<UUID, Location> savedLocations =
                originalLocations.remove(matchId);

        if (savedLocations == null) {
            return;
        }

        for (Map.Entry<UUID, Location> entry
                : savedLocations.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());

            if (player != null && player.isOnline()) {
                player.teleport(entry.getValue());
            }
        }
    }

    private void abortMatch(
            DuelMatch match,
            DuelArena arena,
            String reason
    ) {
        if (match == null
                || match.getState() == DuelMatch.MatchState.ENDING
                || match.getState() == DuelMatch.MatchState.ENDED) {
            return;
        }

        UUID matchId = match.getMatchId();

        BukkitTask task = countdownTasks.remove(matchId);

        if (task != null) {
            task.cancel();
        }

        restoreOriginalLocations(matchId);

        String arenaName = arena != null
                ? arena.getName()
                : matchManager.getArenaForMatch(matchId);

        match.setState(DuelMatch.MatchState.ENDED);
        matchManager.endMatch(matchId);

        broadcast(
                match,
                ChatColor.RED + "Match cancelled: " + reason
        );

        if (arena != null) {
            rollbackManager.restoreArena(
                    arena,
                    () -> arenaManager.releaseArena(arenaName)
            );
        } else if (arenaName != null) {
            arenaManager.releaseArena(arenaName);
        }
    }

    public void shutdown() {
        for (BukkitTask task : countdownTasks.values()) {
            task.cancel();
        }

        countdownTasks.clear();

        Set<UUID> matchIds =
                new HashSet<>(originalLocations.keySet());

        for (UUID matchId : matchIds) {
            DuelMatch match = matchManager.getMatch(matchId);

            restoreOriginalLocations(matchId);

            if (match == null) {
                continue;
            }

            String arenaName =
                    matchManager.getArenaForMatch(matchId);

            DuelArena arena = arenaManager.getArena(arenaName);

            if (match.getState()
                    != DuelMatch.MatchState.ENDING
                    && match.getState()
                    != DuelMatch.MatchState.ENDED) {
                match.setState(DuelMatch.MatchState.ENDED);
            }

            matchManager.endMatch(matchId);

            if (arena != null) {
                rollbackManager.restoreArena(
                        arena,
                        () -> arenaManager.releaseArena(arenaName)
                );
            } else if (arenaName != null) {
                arenaManager.releaseArena(arenaName);
            }
        }

        originalLocations.clear();
    }
}
