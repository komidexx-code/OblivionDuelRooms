
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

        if (playerId == null
                || teamSize < 1
                || teamSize > 5) {
            return false;
        }

        if (getQueuedMode(playerId) != null) {
            return false;
        }

        return queues.get(teamSize).add(playerId);
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

        LinkedHashSet<UUID> queue = queues.get(teamSize);

        return queue == null ? 0 : queue.size();
    }

    public boolean isQueueFull(int teamSize) {

        if (teamSize < 1 || teamSize > 5) {
            return false;
        }

        return getQueueSize(teamSize) >= teamSize * 2;
    }

    public List<UUID> peekPlayersForMatch(int teamSize) {

        LinkedHashSet<UUID> queue = queues.get(teamSize);

        if (queue == null
                || queue.size() < teamSize * 2) {
            return Collections.emptyList();
        }

        List<UUID> players = new ArrayList<>();

        for (UUID player : queue) {

            players.add(player);

            if (players.size() == teamSize * 2) {
                break;
            }
        }

        return Collections.unmodifiableList(players);
    }

    public boolean removePlayersForMatch(
            int teamSize,
            List<UUID> players
    ) {

        if (teamSize < 1 || teamSize > 5
                || players == null
                || players.size() != teamSize * 2) {
            return false;
        }

        LinkedHashSet<UUID> queue = queues.get(teamSize);

        if (queue == null) {
            return false;
        }

        Set<UUID> uniquePlayers = new HashSet<>(players);

        if (uniquePlayers.size() != players.size()
                || uniquePlayers.contains(null)) {
            return false;
        }

        if (!queue.containsAll(uniquePlayers)) {
            return false;
        }

        queue.removeAll(uniquePlayers);

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
