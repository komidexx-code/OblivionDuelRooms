
package com.oblivion.duels;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class StatsManager {

    private final Map<UUID, PlayerStats> playerStats =
            new HashMap<>();

    public PlayerStats getStats(UUID playerId) {
        return playerStats.computeIfAbsent(
                playerId,
                id -> new PlayerStats()
        );
    }

    public void addWin(UUID playerId) {
        getStats(playerId).addWin();
    }

    public void addLoss(UUID playerId) {
        getStats(playerId).addLoss();
    }

    public void addKill(UUID playerId) {
        getStats(playerId).addKill();
    }

    public void addDeath(UUID playerId) {
        getStats(playerId).addDeath();
    }

    public Map<UUID, PlayerStats> getAllStats() {
        return Map.copyOf(playerStats);
    }

    public void setStats(UUID playerId, PlayerStats stats) {
        playerStats.put(playerId, stats);
    }

    public void clearStats(UUID playerId) {
        playerStats.remove(playerId);
    }
}
