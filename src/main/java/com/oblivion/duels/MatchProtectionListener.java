
package com.oblivion.duels;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

public final class MatchProtectionListener implements Listener {

    private final MatchManager matchManager;

    public MatchProtectionListener(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {

        if (event instanceof PlayerTeleportEvent) {
            return;
        }

        Player player = event.getPlayer();

        DuelMatch match =
                matchManager.getMatchForPlayer(player.getUniqueId());

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

        if (match.getState() != DuelMatch.MatchState.ACTIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(
            EntityDamageByEntityEvent event
    ) {

        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = getAttackingPlayer(event.getDamager());

        DuelMatch victimMatch =
                matchManager.getMatchForPlayer(
                        victim.getUniqueId()
                );

        DuelMatch attackerMatch = attacker == null
                ? null
                : matchManager.getMatchForPlayer(
                        attacker.getUniqueId()
                );

        // An outsider cannot attack a match participant.
        if (victimMatch != null && attackerMatch == null) {
            event.setCancelled(true);
            return;
        }

        // A match participant cannot attack an outsider.
        if (victimMatch == null && attackerMatch != null) {
            event.setCancelled(true);
            return;
        }

        // Neither player is participating in a match.
        if (victimMatch == null) {
            return;
        }

        // Both players must belong to the same match.
        if (victimMatch != attackerMatch) {
            event.setCancelled(true);
            return;
        }

        // Combat is allowed only after the countdown.
        if (victimMatch.getState() != DuelMatch.MatchState.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        // Only players can damage match participants.
        if (attacker == null) {
            event.setCancelled(true);
            return;
        }

        // Respect the friendly-fire configuration.
        if (!victimMatch.isOnSameTeam(
                victim.getUniqueId(),
                attacker.getUniqueId()
        )) {
            return;
        }

        if (!Bukkit.getPluginManager()
                .getPlugin("OblivionDuels")
                .getConfig()
                .getBoolean("combat.friendly-fire", false)) {

            event.setCancelled(true);
        }
    }

    private Player getAttackingPlayer(Entity damager) {

        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile) {

            ProjectileSource shooter = projectile.getShooter();

            if (shooter instanceof Player player) {
                return player;
            }
        }

        return null;
    }
}
