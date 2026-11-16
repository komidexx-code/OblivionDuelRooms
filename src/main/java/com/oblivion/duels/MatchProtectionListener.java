
package com.oblivion.duels;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class MatchProtectionListener implements Listener {

    private final OblivionDuels plugin;
    private final MatchManager matchManager;

    public MatchProtectionListener(
            OblivionDuels plugin,
            MatchManager matchManager
    ) {
        this.plugin = plugin;
        this.matchManager = matchManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {

        Player player = event.getPlayer();

        DuelMatch match = matchManager.getMatchForPlayer(
                player.getUniqueId()
        );

        if (match == null) return;

        if (match.getState() != DuelMatch.MatchState.COUNTDOWN) {
            return;
        }

        if (event.getTo() == null) return;

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

        DuelMatch match = matchManager.getMatchForPlayer(
                player.getUniqueId()
        );

        if (match == null) return;

        if (match.getState() != DuelMatch.MatchState.ACTIVE
                || !match.isAlive(player.getUniqueId())) {
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

        DuelMatch victimMatch = matchManager.getMatchForPlayer(
                victim.getUniqueId()
        );

        DuelMatch attackerMatch = attacker == null
                ? null
                : matchManager.getMatchForPlayer(
                        attacker.getUniqueId()
                );

        // Players outside a match cannot attack match participants.
        if (victimMatch != null && attackerMatch == null) {

            // Allow environmental explosions during active matches.
            if (isExplosion(event)
                    && victimMatch.getState()
                    == DuelMatch.MatchState.ACTIVE
                    && victimMatch.isAlive(victim.getUniqueId())) {
                return;
            }

            event.setCancelled(true);
            return;
        }

        // Match participants cannot attack players outside their match.
        if (victimMatch == null && attackerMatch != null) {
            event.setCancelled(true);
            return;
        }

        if (victimMatch == null) return;

        // Prevent attacks between different matches.
        if (victimMatch != attackerMatch) {
            event.setCancelled(true);
            return;
        }

        if (victimMatch.getState()
                != DuelMatch.MatchState.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        // Environmental explosions have no player attacker.
        if (attacker == null) {
            if (isExplosion(event)
                    && victimMatch.isAlive(victim.getUniqueId())) {
                return;
            }

            event.setCancelled(true);
            return;
        }

        if (!victimMatch.isAlive(victim.getUniqueId())
                || !victimMatch.isAlive(attacker.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (victimMatch.isOnSameTeam(
                victim.getUniqueId(),
                attacker.getUniqueId()
        )) {

            if (!plugin.getConfig().getBoolean(
                    "combat.friendly-fire",
                    false
            )) {
                event.setCancelled(true);
            }
        }
    }

    private boolean isExplosion(EntityDamageEvent event) {

        return event.getCause()
                == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || event.getCause()
                == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
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
