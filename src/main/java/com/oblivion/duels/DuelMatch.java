
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

    private final Set<UUID> alivePlayers = new LinkedHashSet<>();

    private MatchState state = MatchState.WAITING;

    private int teamAScore;
    private int teamBScore;

    private int currentRound = 1;
    private int completedRounds;

    public DuelMatch(
            int teamSize,
            List<UUID> teamA,
            List<UUID> teamB
    ) {
        if (teamSize < 1 || teamSize > 5) {
            throw new IllegalArgumentException("Invalid team size.");
        }

        if (teamA == null || teamB == null
                || teamA.size() != teamSize
                || teamB.size() != teamSize) {
            throw new IllegalArgumentException(
                    "Teams must be equal in size."
            );
        }

        Set<UUID> allPlayers = new LinkedHashSet<>();

        for (UUID player : teamA) {
            if (player == null || !allPlayers.add(player)) {
                throw new IllegalArgumentException(
                        "Duplicate or invalid player in Team A."
                );
            }
        }

        for (UUID player : teamB) {
            if (player == null || !allPlayers.add(player)) {
                throw new IllegalArgumentException(
                        "Duplicate or invalid player in Team B."
                );
            }
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

    public int getTeamAScore() {
        return teamAScore;
    }

    public int getTeamBScore() {
        return teamBScore;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public int getCompletedRounds() {
        return completedRounds;
    }

    public MatchState getState() {
        return state;
    }

    public void setState(MatchState newState) {
        if (newState == null || state == MatchState.ENDED) {
            return;
        }

        boolean validTransition = switch (state) {
            case WAITING ->
                    newState == MatchState.COUNTDOWN
                            || newState == MatchState.ENDING
                            || newState == MatchState.ENDED;

            case COUNTDOWN ->
                    newState == MatchState.ACTIVE
                            || newState == MatchState.ENDING
                            || newState == MatchState.ENDED;

            case ACTIVE ->
                    newState == MatchState.COUNTDOWN
                            || newState == MatchState.ENDING
                            || newState == MatchState.ENDED;

            case ENDING ->
                    newState == MatchState.ENDED;

            case ENDED -> false;
        };

        if (validTransition) {
            state = newState;
        }
    }

    public void addPointToTeamA() {
        teamAScore++;
    }

    public void addPointToTeamB() {
        teamBScore++;
    }

    /**
     * Records one completed round.
     * A draw completes the round without awarding a point.
     */
    public void recordRoundResult(WinningTeam winner) {
        if (winner == null || winner == WinningTeam.NONE) {
            return;
        }

        if (getMatchWinner() != WinningTeam.NONE
                || completedRounds >= 3) {
            return;
        }

        completedRounds++;

        if (winner == WinningTeam.TEAM_A) {
            teamAScore++;
        } else if (winner == WinningTeam.TEAM_B) {
            teamBScore++;
        }
    }

    public void awardRoundWin(WinningTeam winner) {
        if (winner == WinningTeam.TEAM_A
                || winner == WinningTeam.TEAM_B) {
            recordRoundResult(winner);
        }
    }

    public void recordRoundDraw() {
        recordRoundResult(WinningTeam.DRAW);
    }

    /**
     * Returns the final match winner.
     * A team wins immediately after reaching two round wins.
     * After three completed rounds, the higher score wins.
     */
    public WinningTeam getMatchWinner() {
        if (teamAScore >= 2) {
            return WinningTeam.TEAM_A;
        }

        if (teamBScore >= 2) {
            return WinningTeam.TEAM_B;
        }

        if (completedRounds >= 3) {
            if (teamAScore > teamBScore) {
                return WinningTeam.TEAM_A;
            }

            if (teamBScore > teamAScore) {
                return WinningTeam.TEAM_B;
            }

            return WinningTeam.DRAW;
        }

        return WinningTeam.NONE;
    }

    /**
     * Advances to the next round and revives all participants.
     * Returns false if no further round can be played.
     */
    public boolean advanceRound() {
        if (getMatchWinner() != WinningTeam.NONE
                || completedRounds >= 3
                || currentRound >= 3) {
            return false;
        }

        currentRound++;
        resetAlivePlayers();

        return true;
    }

    public void resetAlivePlayers() {
        alivePlayers.clear();
        alivePlayers.addAll(teamA);
        alivePlayers.addAll(teamB);
    }

    /**
     * Legacy method retained for compatibility.
     * Round scoring should use recordRoundResult() instead.
     */
    public void awardPointToOpposingTeam(UUID eliminatedPlayer) {
        if (eliminatedPlayer == null) {
            return;
        }

        if (teamA.contains(eliminatedPlayer)) {
            addPointToTeamB();
        } else if (teamB.contains(eliminatedPlayer)) {
            addPointToTeamA();
        }
    }

    public boolean contains(UUID playerId) {
        return playerId != null
                && (teamA.contains(playerId)
                || teamB.contains(playerId));
    }

    public boolean isOnSameTeam(UUID first, UUID second) {
        if (first == null || second == null) {
            return false;
        }

        return (teamA.contains(first) && teamA.contains(second))
                || (teamB.contains(first) && teamB.contains(second));
    }

    public List<UUID> getOpponents(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }

        if (teamA.contains(playerId)) {
            return teamB;
        }

        if (teamB.contains(playerId)) {
            return teamA;
        }

        return List.of();
    }

    public boolean isAlive(UUID playerId) {
        return playerId != null && alivePlayers.contains(playerId);
    }

    public boolean eliminatePlayer(UUID playerId) {
        return playerId != null && alivePlayers.remove(playerId);
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

        for (UUID playerId : team) {
            if (alivePlayers.contains(playerId)) {
                count++;
            }
        }

        return count;
    }

    public List<UUID> getAlivePlayers() {
        return new ArrayList<>(alivePlayers);
    }

    /**
     * Determines the winner of the current round
     * based on which team still has living players.
     */
    public WinningTeam getWinner() {
        int teamAAlive = getAliveCount(WinningTeam.TEAM_A);
        int teamBAlive = getAliveCount(WinningTeam.TEAM_B);

        if (teamAAlive == 0 && teamBAlive == 0) {
            return WinningTeam.DRAW;
        }

        if (teamAAlive == 0) {
            return WinningTeam.TEAM_B;
        }

        if (teamBAlive == 0) {
            return WinningTeam.TEAM_A;
        }

        return WinningTeam.NONE;
    }
}
