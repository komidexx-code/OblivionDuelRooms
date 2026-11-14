
package com.oblivion.duels;

import java.util.List;
import java.util.UUID;

public final class DuelMatch {

    private final UUID matchId;
    private final int teamSize;
    private final List<UUID> teamA;
    private final List<UUID> teamB;

    public DuelMatch(
            int teamSize,
            List<UUID> teamA,
            List<UUID> teamB
    ) {
        if (teamSize < 1 || teamSize > 5) {
            throw new IllegalArgumentException("Invalid team size.");
        }

        if (teamA.size() != teamSize || teamB.size() != teamSize) {
            throw new IllegalArgumentException("Teams must be equal in size.");
        }

        this.matchId = UUID.randomUUID();
        this.teamSize = teamSize;
        this.teamA = List.copyOf(teamA);
        this.teamB = List.copyOf(teamB);
    }

    public UUID getMatchId() {
        return matchId;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public List<UUID> getTeamA() {
        return teamA;
    }

    public List<UUID> getTeamB() {
        return teamB;
    }

    public boolean contains(UUID playerId) {
        return teamA.contains(playerId) || teamB.contains(playerId);
    }

    public boolean isOnSameTeam(UUID first, UUID second) {
        return (teamA.contains(first) && teamA.contains(second))
                || (teamB.contains(first) && teamB.contains(second));
    }

    public List<UUID> getOpponents(UUID playerId) {
        if (teamA.contains(playerId)) {
            return teamB;
        }

        if (teamB.contains(playerId)) {
            return teamA;
        }

        return List.of();
    }
}
