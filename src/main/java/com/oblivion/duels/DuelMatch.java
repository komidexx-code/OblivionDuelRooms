
package com.oblivion.duels;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DuelMatch {

    public enum MatchState {
        WAITING,
        COUNTDOWN,
        ACTIVE,
        ENDING,
        ENDED
    }

    public enum WinningTeam {
        TEAM_A,
        TEAM_B,
        DRAW,
        NONE
    }

    private final UUID matchId;
    private final int teamSize;

    private final List<UUID> teamA;
    private final List<UUID> teamB;

    private final Set<UUID> alivePlayers =
            new LinkedHashSet<>();

    private MatchState state = MatchState.WAITING;

    public DuelMatch(
            int teamSize,
            List<UUID> teamA,
            List<UUID> teamB
    ) {
        if (teamSize < 1 || teamSize > 5) {
            throw new IllegalArgumentException(
                    "Invalid team size."
            );
        }

        if (teamA == null || teamB == null
                || teamA.size() != teamSize
                || teamB.size() != teamSize) {
            throw new IllegalArgumentException(
                    "Teams must be equal in size."
            );
        }

        Set<UUID> allPlayers = new LinkedHashSet<>();
        allPlayers.addAll(teamA);

        if (allPlayers.size() != teamA.size()) {
            throw new IllegalArgumentException(
                    "Duplicate player in Team A."
            );
        }

        for (UUID player : teamB) {
            if (player == null || !allPlayers.add(player)) {
                throw new IllegalArgumentException(
                        "Duplicate or invalid player."
                );
            }
        }

        if (teamA.contains(null)) {
            throw new IllegalArgumentException(
                    "Invalid player in Team A."
            );
        }

        this.matchId = UUID.randomUUID();
        this.teamSize = teamSize;

        this.teamA = List.copyOf(teamA);
        this.teamB = List.copyOf(teamB);

        alivePlayers.addAll(this.teamA);
        alivePlayers.addAll(this.teamB);
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

    public MatchState getState() {
        return state;
    }

    public void setState(MatchState state) {
        if (state == null) {
            throw new IllegalArgumentException(
                    "Match state cannot be null."
            );
        }

        this.state = state;
    }

    public boolean contains(UUID playerId) {
        return teamA.contains(playerId)
                || teamB.contains(playerId);
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

    public boolean isAlive(UUID playerId) {
        return alivePlayers.contains(playerId);
    }

    public boolean eliminatePlayer(UUID playerId) {
        if (!contains(playerId)) {
            return false;
        }

        return alivePlayers.remove(playerId);
    }

    public int getAliveCount() {
        return alivePlayers.size();
    }

    public int getAliveCount(WinningTeam team) {
        if (team == WinningTeam.TEAM_A) {
            return countAlive(teamA);
        }

        if (team == WinningTeam.TEAM_B) {
            return countAlive(teamB);
        }

        return 0;
    }

    private int countAlive(List<UUID> team) {
        int count = 0;

        for (UUID player : team) {
            if (alivePlayers.contains(player)) {
                count++;
            }
        }

        return count;
    }

    public List<UUID> getAlivePlayers() {
        return new ArrayList<>(alivePlayers);
    }

    public WinningTeam getWinner() {
        boolean teamAAlive = getAliveCount(
                WinningTeam.TEAM_A
        ) > 0;

        boolean teamBAlive = getAliveCount(
                WinningTeam.TEAM_B
        ) > 0;

        if (teamAAlive && teamBAlive) {
            return WinningTeam.NONE;
        }

        if (teamAAlive) {
            return WinningTeam.TEAM_A;
        }

        if (teamBAlive) {
            return WinningTeam.TEAM_B;
        }

        return WinningTeam.DRAW;
    }
}
