
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

    private final Map<UUID, BukkitTask> pendingRoundTasks =
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

    // =========================================================
    // INITIAL MATCH PREPARATION
    // =========================================================

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

        // Capture original inventories before moving anyone.
        MatchDeathListener listener = getDeathListener();

        if (listener != null) {
            listener.captureStartingInventories(match);
        }

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

    // =========================================================
    // NEXT ROUND PREPARATION
    // =========================================================

    /**
     * Queues the next round.
     *
     * The match remains ACTIVE while waiting for all participants
     * to finish their respawn process. The round is not advanced
     * until everyone is online and no longer dead.
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

        // Prevent duplicate next-round preparation requests.
        if (pendingRoundTasks.containsKey(matchId)) {
            return false;
        }

        int waitSeconds = Math.max(
                5,
                plugin.getConfig().getInt(
                        "matchmaking.respawn-wait-seconds",
                        30
                )
        );

        long maxWaitTicks = waitSeconds * 20L;

        BukkitRunnable waitTask = new BukkitRunnable() {

            long elapsedTicks = 0;

            @Override
            public void run() {

                if (matchManager.getMatch(matchId) != match
                        || match.getState()
                        != DuelMatch.MatchState.ACTIVE) {

                    cancel();
                    pendingRoundTasks.remove(matchId);
                    return;
                }

                if (areParticipantsRespawned(match)) {

                    cancel();

                    // Allow the Bukkit respawn process and other
                    // respawn-event tasks to finish before preparing.
                    BukkitTask finalPreparationTask =
                            Bukkit.getScheduler().runTaskLater(
                                    plugin,
                                    () -> {

                                        pendingRoundTasks.remove(matchId);

                                        if (matchManager.getMatch(matchId)
                                                        != match
                                                || match.getState()
                                                != DuelMatch.MatchState.ACTIVE) {
                                            return;
                                        }

                                        if (!areParticipantsRespawned(match)) {
                                            abortMatch(
                                                    match,
                                                    arena,
                                                    "A player was not ready after respawning."
                                            );
                                            return;
                                        }

                                        startNextRoundNow(match, arena);
                                    },
                                    2L
                            );

                    pendingRoundTasks.put(
                            matchId,
                            finalPreparationTask
                    );

                    return;
                }

                elapsedTicks += 2L;

                if (elapsedTicks >= maxWaitTicks) {

                    cancel();
                    pendingRoundTasks.remove(matchId);

                    abortMatch(
                            match,
                            arena,
                            "Players did not finish respawning in time."
            );
                }
            }

        };

        BukkitTask task = waitTask.runTaskTimer(
                plugin,
                0L,
                2L
        );

        pendingRoundTasks.put(matchId, task);

        broadcast(
                match,
                ChatColor.YELLOW
                        + "Waiting for players to respawn before the next round..."
        );

        return true;
    }

    /**
     * Actually starts the next round after everyone is ready.
     */
    private void startNextRoundNow(
            DuelMatch match,
            DuelArena arena
    ) {

        if (match == null
                || match.getState() != DuelMatch.MatchState.ACTIVE) {
            return;
        }

        UUID matchId = match.getMatchId();

        if (matchManager.getMatch(matchId) != match) {
            return;
        }

        if (arena == null || !isArenaValid(match, arena)) {
            abortMatch(
                    match,
                    arena,
                    "Arena is invalid for the next round."
            );
            return;
        }

        if (!areParticipantsRespawned(match)) {
            abortMatch(
                    match,
                    arena,
                    "A player was not ready for the next round."
            );
            return;
        }

        if (!match.advanceRound()) {
            return;
        }

        // Restore original equipment before preparing the new round.
        MatchDeathListener listener = getDeathListener();

        if (listener != null) {
            listener.restoreStartingInventories(match);
        }

        match.setState(DuelMatch.MatchState.COUNTDOWN);

        if (!teleportTeams(match, arena)) {
            abortMatch(
                    match,
                    arena,
                    "A player could not be prepared for the next round."
            );
            return;
        }

        broadcast(
                match,
                ChatColor.LIGHT_PURPLE
                        + "Round "
                        + match.getCurrentRound()
                        + " is starting soon!"
        );

        // false means the existing overall match timer continues.
        startCountdown(match, arena, false);
    }

    // =========================================================
    // VALIDATION
    // =========================================================

    private boolean isArenaValid(
            DuelMatch match,
            DuelArena arena
    ) {

        return arena.isReady()
                && arena.getTeamSize() == match.getTeamA().size()
                && match.getTeamA().size() == match.getTeamB().size();
    }

    /**
     * Checks every participant, not only the survivors.
     *
     * Eliminated players must finish respawning before the next
     * round can begin.
     */
    private boolean areParticipantsRespawned(DuelMatch match) {

        for (UUID id : match.getTeamA()) {
            Player player = Bukkit.getPlayer(id);

            if (player == null
                    || !player.isOnline()
                    || player.isDead()) {
                return false;
            }
        }

        for (UUID id : match.getTeamB()) {
            Player player = Bukkit.getPlayer(id);

            if (player == null
                    || !player.isOnline()
                    || player.isDead()) {
                return false;
            }
        }

        return true;
    }

    // =========================================================
    // PLAYER TELEPORTATION AND RESET
    // =========================================================

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
                || player.isDead()
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

    // =========================================================
    // DEATH LISTENER ACCESS
    // =========================================================

    private MatchDeathListener getDeathListener() {

        if (!(plugin instanceof OblivionDuels duels)) {
            return null;
        }

        return duels.getMatchDeathListener();
    }

    private void saveParticipantSpawns(
            DuelMatch match,
            DuelArena arena
    ) {

        MatchDeathListener listener = getDeathListener();

        if (listener != null) {
            listener.saveParticipantSpawns(
                    match,
                    arena.getName()
            );
        }
    }

    // =========================================================
    // COUNTDOWN
    // =========================================================

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

                if (!areAllPlayersOnline(match)) {
                    abortMatch(
                            match,
                            arena,
                            "A player disconnected."
                    );
                    return;
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

                    if (startMatchTimeout) {

                        MatchDeathListener listener =
                                getDeathListener();

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

    private boolean areAllPlayersOnline(DuelMatch match) {

        for (UUID id : match.getTeamA()) {
            Player player = Bukkit.getPlayer(id);

            if (player == null || !player.isOnline()) {
                return false;
            }
        }

        for (UUID id : match.getTeamB()) {
            Player player = Bukkit.getPlayer(id);

            if (player == null || !player.isOnline()) {
                return false;
            }
        }

        return true;
    }

    // =========================================================
    // BROADCAST
    // =========================================================

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

    // =========================================================
    // ORIGINAL LOCATION RESTORATION
    // =========================================================

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

    // =========================================================
    // MATCH ABORT
    // =========================================================

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

        BukkitTask pendingTask = pendingRoundTasks.remove(matchId);

        if (pendingTask != null) {
            pendingTask.cancel();
        }

        restoreOriginalLocations(matchId);

        // Restore original equipment if preparation was interrupted.
        MatchDeathListener listener = getDeathListener();

        if (listener != null) {
            listener.restoreStartingInventories(match);
        }

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

    // =========================================================
    // SHUTDOWN
    // =========================================================

    public void shutdown() {

        for (BukkitTask task : countdownTasks.values()) {
            task.cancel();
        }

        countdownTasks.clear();

        for (BukkitTask task : pendingRoundTasks.values()) {
            task.cancel();
        }

        pendingRoundTasks.clear();

        Set<UUID> matchIds =
                new HashSet<>(originalLocations.keySet());

        for (UUID matchId : matchIds) {

            DuelMatch match = matchManager.getMatch(matchId);

            restoreOriginalLocations(matchId);

            if (match == null) {
                continue;
            }

            MatchDeathListener listener = getDeathListener();

            if (listener != null) {
                listener.restoreStartingInventories(match);
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
