
package com.oblivion.duels;

public final class PlayerStats {

    private int wins;
    private int losses;
    private int kills;
    private int deaths;

    public PlayerStats() {
        this(0, 0, 0, 0);
    }

    public PlayerStats(
            int wins,
            int losses,
            int kills,
            int deaths
    ) {
        this.wins = Math.max(0, wins);
        this.losses = Math.max(0, losses);
        this.kills = Math.max(0, kills);
        this.deaths = Math.max(0, deaths);
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public int getTotalMatches() {
        return wins + losses;
    }

    public double getWinRate() {
        int total = getTotalMatches();

        if (total == 0) {
            return 0.0;
        }

        return (double) wins / total * 100.0;
    }

    public void addWin() {
        wins++;
    }

    public void addLoss() {
        losses++;
    }

    public void addKill() {
        kills++;
    }

    public void addDeath() {
        deaths++;
    }
}
