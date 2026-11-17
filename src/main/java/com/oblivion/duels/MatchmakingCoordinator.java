package com.oblivion.duels;

import org.bukkit.Bukkit;
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
    private final ArenaRollbackManager rollbackManager;
    private final MatchPreparationManager preparationManager;

    private BukkitTask task;

    private boolean matchmakingEnabled;

    public MatchmakingCoordinator(
            JavaPlugin plugin,
            MatchmakingManager matchmaking,
            MatchManager matchManager,
            ArenaManager arenaManager,
            ArenaRollbackManager rollbackManager
    ) {
        this.plugin = plugin;
        this.matchmaking = matchmaking;
        this.matchManager = matchManager;
        this.arenaManager = arenaManager;
        this.rollbackManager = rollbackManager;

        this.preparationManager = new MatchPreparationManager(
                plugin,
                matchManager,
                arenaManager,
                rollbackManager
        );

        reloadSettings();
    }

    public void start() {
        if (task != null) {
            return;
        }

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

        preparationManager.shutdown();
    }

    public MatchPreparationManager getPreparationManager() {
        return preparationManager;
    }

    public void reloadSettings() {
        matchmakingEnabled = plugin.getConfig().getBoolean(
                "matchmaking.enabled",
                true
        );
    }

    public boolean isMatchmakingEnabled() {
        return matchmakingEnabled;
    }

    private void processQueues() {
        if (!matchmakingEnabled) {
            return;
        }

        for (int teamSize = 1; teamSize <= 5; teamSize++) {
            try {
                processQueue(teamSize);
            } catch (RuntimeException exception) {
                plugin.getLogger().severe(
                        "Error processing the "
                                + teamSize + "v" + teamSize
                                + " queue: " + exception.getMessage()
                );

                exception.printStackTrace();
            }
        }
    }

    private void processQueue(int teamSize) {
        while (matchmaking.isQueueFull(teamSize)) {
            List<UUID> players =
                    matchmaking.peekPlayersForMatch(teamSize);

            if (players == null || players.isEmpty()) {
                return;
            }

            boolean invalidPlayerFound = false;

            for (UUID playerId : players) {
                Player player = Bukkit.getPlayer(playerId);

                if (player == null
                        || !player.isOnline()
                        || matchManager.isInMatch(playerId)) {
                    matchmaking.leaveQueue(playerId);
                    invalidPlayerFound = true;
                }
            }

            if (invalidPlayerFound) {
                continue;
            }

            DuelArena arena =
                    arenaManager.getAvailableArena(teamSize);

            if (arena == null) {
                return;
            }

            if (!arena.isReady()
                    || arena.getTeamSize() != teamSize) {
                plugin.getLogger().warning(
                        "Arena " + arena.getName()
                                + " is not valid for "
                                + teamSize + "v" + teamSize + "."
                );
                return;
            }

            if (!arenaManager.reserveArena(arena.getName())) {
                return;
            }

            DuelMatch match = null;
            boolean preparationStarted = false;

            try {
                match = matchManager.createMatch(
                        teamSize,
                        players,
                        arena.getName()
                );

                if (match == null) {
                    throw new IllegalStateException(
                            "MatchManager returned null."
                    );
                }

                if (!matchmaking.removePlayersForMatch(
                        teamSize,
                        players
                )) {
                    match.setState(DuelMatch.MatchState.ENDED);
                    matchManager.endMatch(match.getMatchId());
                    arenaManager.releaseArena(arena.getName());

                    plugin.getLogger().warning(
                            "Match cancelled because players "
                                    + "could not be removed from queue."
                    );
                    return;
                }

                preparationStarted = true;

                boolean prepared =
                        preparationManager.prepareMatch(match, arena);

                if (!prepared) {
                    plugin.getLogger().warning(
                            "Could not prepare match "
                                    + match.getMatchId()
                    );
                }

            } catch (RuntimeException exception) {
                if (match != null) {
                    if (match.getState()
                            != DuelMatch.MatchState.ENDED) {
                        match.setState(DuelMatch.MatchState.ENDED);
                    }

                    matchManager.endMatch(match.getMatchId());
                }

                if (preparationStarted) {
                    rollbackManager.restoreArena(
                            arena,
                            () -> arenaManager.releaseArena(
                                    arena.getName()
                            )
                    );
                } else {
                    arenaManager.releaseArena(arena.getName());
                }

                plugin.getLogger().severe(
                        "Could not create or prepare duel match: "
                                + exception.getMessage()
                );

                exception.printStackTrace();
                return;
            }
        }
    }
}
