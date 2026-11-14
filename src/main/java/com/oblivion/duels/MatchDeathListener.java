
package com.oblivion.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MatchDeathListener implements Listener {

    private final JavaPlugin plugin;
    private final MatchManager matchManager;
    private final ArenaManager arenaManager;

    private final Map<UUID, Location> spectatorSpawns =
            new HashMap<>();

    public MatchDeathListener(
            JavaPlugin plugin,
            MatchManager matchManager,
            ArenaManager arenaManager
    ) {
        this.plugin = plugin;
        this.matchManager = matchManager;
        this.arenaManager = arenaManager;

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {

        Player player = event.getEntity();

        DuelMatch match = matchManager.getMatchForPlayer(
                player.getUniqueId()
        );

        if (match == null
                || match.getState() != DuelMatch.MatchState.ACTIVE) {
            return;
        }

        UUID playerId = player.getUniqueId();

        // Preserve inventory until controlled winner-only loot is implemented.
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);

        String arenaName =
                matchManager.getArenaForMatch(match.getMatchId());

        if (arenaName != null) {
            DuelArena arena = arenaManager.getArena(arenaName);

            if (arena != null && arena.getSpectatorSpawn() != null) {
                spectatorSpawns.put(
                        playerId,
                        arena.getSpectatorSpawn()
                );
            }
        }

        match.eliminatePlayer(playerId);

        broadcast(match,
                ChatColor.RED + player.getName()
                        + " has been eliminated!");

        if (match.getAliveCount() == 0) {
            finishMatch(match, arenaName, "The match ended in a draw.");
            return;
        }

        DuelMatch.WinningTeam winner = match.getWinner();

        if (winner != DuelMatch.WinningTeam.NONE
                && winner != DuelMatch.WinningTeam.DRAW) {

            String winningTeam = winner
                    == DuelMatch.WinningTeam.TEAM_A
                    ? "Team A" : "Team B";

            finishMatch(
                    match,
                    arenaName,
                    ChatColor.GREEN + winningTeam + " wins!"
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {

        UUID playerId = event.getPlayer().getUniqueId();

        Location spectatorSpawn = spectatorSpawns.remove(playerId);

        if (spectatorSpawn != null
                && spectatorSpawn.getWorld() != null) {
            event.setRespawnLocation(spectatorSpawn);
        }
    }

    private void finishMatch(
            DuelMatch match,
            String arenaName,
            String message
    ) {
        match.setState(DuelMatch.MatchState.ENDED);

        broadcast(match, message);

        matchManager.endMatch(match.getMatchId());

        if (arenaName != null) {
            arenaManager.releaseArena(arenaName);
        }

        plugin.getLogger().info(
                "Match " + match.getMatchId() + " has ended."
        );
    }

    private void broadcast(DuelMatch match, String message) {

        for (UUID playerId : match.getTeamA()) {
            Player player = Bukkit.getPlayer(playerId);

            if (player != null) {
                player.sendMessage(
                        ChatColor.LIGHT_PURPLE
                                + "[OblivionDuels] "
                                + ChatColor.RESET + message
                );
            }
        }

        for (UUID playerId : match.getTeamB()) {
            Player player = Bukkit.getPlayer(playerId);

            if (player != null) {
                player.sendMessage(
                        ChatColor.LIGHT_PURPLE
                                + "[OblivionDuels] "
                                + ChatColor.RESET + message
                );
            }
        }
    }
}
