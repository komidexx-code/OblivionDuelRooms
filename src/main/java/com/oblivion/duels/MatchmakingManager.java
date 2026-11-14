
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
        if (teamSize < 1 || teamSize > 5) {
            return false;
        }

        if (getQueuedMode(playerId) != null) {
            return false;
        }

        queues.get(teamSize).add(playerId);
        return true;
    }

    public boolean leaveQueue(UUID playerId) {
        for (Set<UUID> queue : queues.values()) {
            if (queue.remove(playerId)) {
                return true;
            }
        }

        return false;
    }

    public Integer getQueuedMode(UUID playerId) {
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
                teamSize, new LinkedHashSet<>()
        ).size();
    }

    public List<UUID> getPlayersForMatch(int teamSize) {
        LinkedHashSet<UUID> queue = queues.get(teamSize);

        if (queue == null || queue.size() < teamSize * 2) {
            return Collections.emptyList();
        }

        List<UUID> players = new ArrayList<>();
        Iterator<UUID> iterator = queue.iterator();

        while (players.size() < teamSize * 2
                && iterator.hasNext()) {
            players.add(iterator.next());
            iterator.remove();
        }

        return players;
    }
}
