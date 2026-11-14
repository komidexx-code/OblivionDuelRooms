
package com.oblivion.duels;

import java.util.*;

public final class MatchManager {

    private final Map<UUID, DuelMatch> activeMatches =
            new HashMap<>();

    private final Map<UUID, UUID> playerMatches =
            new HashMap<>();

    // Stores the arena assigned to each match.
    private final Map<UUID, String> matchArenas =
            new HashMap<>();

    public DuelMatch createMatch(
            int teamSize,
            List<UUID> players
    ) {
        return createMatch(teamSize, players, null);
    }

    public DuelMatch createMatch(
            int teamSize,
            List<UUID> players,
            String arenaName
    ) {
        if (teamSize < 1 || teamSize > 5
                || players == null
                || players.size() != teamSize * 2) {
            throw new IllegalArgumentException(
                    "Invalid match setup."
            );
        }

        if (new HashSet<>(players).size() != players.size()) {
            throw new IllegalArgumentException(
                    "A player cannot appear twice in a match."
            );
        }

        for (UUID player : players) {
            if (playerMatches.containsKey(player)) {
                throw new IllegalStateException(
                        "A player is already in a match."
                );
            }
        }

        List<UUID> teamA = new ArrayList<>(
                players.subList(0, teamSize)
        );

        List<UUID> teamB = new ArrayList<>(
                players.subList(teamSize, teamSize * 2)
        );

        DuelMatch match = new DuelMatch(
                teamSize,
                teamA,
                teamB
        );

        UUID matchId = match.getMatchId();

        activeMatches.put(matchId, match);

        if (arenaName != null && !arenaName.isBlank()) {
            matchArenas.put(matchId, arenaName);
        }

        for (UUID player : players) {
            playerMatches.put(player, matchId);
        }

        return match;
    }

    public DuelMatch getMatchForPlayer(UUID playerId) {
        UUID matchId = playerMatches.get(playerId);

        if (matchId == null) {
            return null;
        }

        return activeMatches.get(matchId);
    }

    public DuelMatch getMatch(UUID matchId) {
        return activeMatches.get(matchId);
    }

    public String getArenaForMatch(UUID matchId) {
        return matchArenas.get(matchId);
    }

    public void endMatch(UUID matchId) {
        DuelMatch match = activeMatches.remove(matchId);

        if (match == null) {
            return;
        }

        for (UUID player : match.getTeamA()) {
            playerMatches.remove(player);
        }

        for (UUID player : match.getTeamB()) {
            playerMatches.remove(player);
        }

        matchArenas.remove(matchId);
    }

    public boolean isInMatch(UUID playerId) {
        return playerMatches.containsKey(playerId);
    }

    public int getActiveMatchCount() {
        return activeMatches.size();
    }

    public int getActivePlayerCount() {
        return playerMatches.size();
    }
}
