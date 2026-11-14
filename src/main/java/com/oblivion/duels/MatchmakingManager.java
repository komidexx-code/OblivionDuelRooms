
package com.oblivion.duels;

import java.util.*;

public final class MatchmakingManager {

    private final Map<Integer, LinkedHashSet<UUID>> queues =
            new HashMap<>();

    public MatchmakingManager() {
        for (int size = 1; size <= 5; size++) {
            queues.put(size, new LinkedHashSet<>());
        }
    }

    public boolean joinQueue(UUID playerId, int teamSize) {
        if (playerId == null || teamSize < 1 || teamSize > 5) {
            return false;
        }

        if (getQueuedMode(playerId) != null) {
            return false;
        }

        queues.get(teamSize).add(playerId);
        return true;
    }

    public boolean leaveQueue(UUID playerId) {
        if (playerId == null) {
            return false;
        }

        for (Set<UUID> queue : queues.values()) {
            if (queue.remove(playerId)) {
                return true;
            }
        }

        return false;
    }

    public Integer getQueuedMode(UUID playerId) {
        if (playerId == null) {
            return null;
        }

        for (Map.Entry<Integer, LinkedHashSet<UUID>> entry
                : queues.entrySet()) {

            if (entry.getValue().contains(playerId)) {
                return entry.getKey();
            }
        }

        return null;
    }

    public int getQueueSize(int teamSize) {
        return queues.getOrDefault(
                teamSize,
                new LinkedHashSet<>()
        ).size();
    }

    // Checks whether enough players are waiting.
    public boolean isQueueFull(int teamSize) {
        return getQueueSize(teamSize) >= teamSize * 2;
    }

    // Returns the next available players without removing them.
    public List<UUID> peekPlayersForMatch(int teamSize) {
        LinkedHashSet<UUID> queue = queues.get(teamSize);

        if (queue == null || queue.size() < teamSize * 2) {
            return Collections.emptyList();
        }

        List<UUID> players = new ArrayList<>();

        for (UUID player : queue) {
            players.add(player);

            if (players.size() == teamSize * 2) {
                break;
            }
        }

        return players;
    }

    // Removes players only after a match has been successfully prepared.
    public boolean removePlayersForMatch(
            int teamSize,
            List<UUID> players
    ) {
        LinkedHashSet<UUID> queue = queues.get(teamSize);

        if (queue == null || players == null
                || players.size() != teamSize * 2) {
            return false;
        }

        if (!queue.containsAll(players)) {
            return false;
        }

        for (UUID player : players) {
            queue.remove(player);
        }

        return true;
    }

    public List<UUID> getPlayersForMatch(int teamSize) {
        List<UUID> players = peekPlayersForMatch(teamSize);

        if (players.isEmpty()) {
            return Collections.emptyList();
        }

        if (!removePlayersForMatch(teamSize, players)) {
            return Collections.emptyList();
        }

        return players;
    }

    public Map<Integer, Integer> getAllQueueSizes() {
        Map<Integer, Integer> result = new HashMap<>();

        for (int size = 1; size <= 5; size++) {
            result.put(size, getQueueSize(size));
        }

        return Collections.unmodifiableMap(result);
    }
}
