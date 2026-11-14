
package com.oblivion.duels;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

public final class MatchProtectionListener implements Listener {

    private final MatchManager matchManager;

    public MatchProtectionListener(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {

        // Allow teleports, including the initial arena teleport.
        if (event instanceof PlayerTeleportEvent) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        DuelMatch match =
                matchManager.getMatchForPlayer(playerId);

        if (match == null) {
            return;
        }

        if (match.getState() != DuelMatch.MatchState.COUNTDOWN) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        boolean changedPosition =
                event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ();

        if (changedPosition) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamage(EntityDamageEvent event) {

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        DuelMatch match =
                matchManager.getMatchForPlayer(player.getUniqueId());

        if (match == null) {
            return;
        }

        if (match.getState() == DuelMatch.MatchState.COUNTDOWN
                || match.getState() == DuelMatch.MatchState.WAITING) {
            event.setCancelled(true);
        }
    }
}
