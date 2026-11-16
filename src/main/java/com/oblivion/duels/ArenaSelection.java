
package com.oblivion.duels;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ArenaSelection implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey wandKey;

    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    private BukkitTask particleTask;

    public ArenaSelection(JavaPlugin plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "arena_wand");

        Bukkit.getPluginManager().registerEvents(this, plugin);

        startParticleDisplay();
    }

    public ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.WOODEN_AXE);

        ItemMeta meta = wand.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§5§lOblivion Arena Selector");

            meta.setLore(List.of(
                    "§7Left-click: Position 1",
                    "§7Right-click: Position 2",
                    "§8OblivionDuels Arena Tool"
            ));

            meta.getPersistentDataContainer().set(
                    wandKey,
                    PersistentDataType.BYTE,
                    (byte) 1
            );

            wand.setItemMeta(meta);
        }

        return wand;
    }

    private boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        return item.getItemMeta()
                .getPersistentDataContainer()
                .has(wandKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {

        if (event.getAction() != Action.LEFT_CLICK_BLOCK
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!isWand(event.getItem())) {
            return;
        }

        Player player = event.getPlayer();

        if (!player.hasPermission("oblivionduels.admin")) {
            player.sendMessage("§cYou don't have permission.");
            return;
        }

        event.setCancelled(true);

        if (event.getClickedBlock() == null) {
            return;
        }

        Location location =
                event.getClickedBlock().getLocation();

        UUID playerId = player.getUniqueId();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            pos1.put(playerId, location);

            player.sendMessage(
                    "§5[OblivionDuels] §aPosition 1 selected: "
                            + format(location)
            );

        } else {
            pos2.put(playerId, location);

            player.sendMessage(
                    "§5[OblivionDuels] §aPosition 2 selected: "
                            + format(location)
            );
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearSelection(event.getPlayer().getUniqueId());
    }

    private String format(Location location) {
        return location.getBlockX() + ", "
                + location.getBlockY() + ", "
                + location.getBlockZ();
    }

    private void startParticleDisplay() {
        particleTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        UUID id = player.getUniqueId();

                        Location first = pos1.get(id);
                        Location second = pos2.get(id);

                        if (first == null || second == null) {
                            continue;
                        }

                        if (first.getWorld() == null
                                || second.getWorld() == null
                                || !first.getWorld().equals(second.getWorld())) {
                            continue;
                        }

                        displayBoundary(player, first, second);
                    }
                },
                20L,
                20L
        );
    }

    private void displayBoundary(
            Player player,
            Location first,
            Location second
    ) {
        World world = first.getWorld();

        if (world == null
                || !world.equals(player.getWorld())) {
            return;
        }

        int minX = Math.min(first.getBlockX(), second.getBlockX());
        int maxX = Math.max(first.getBlockX(), second.getBlockX());

        int minY = Math.min(first.getBlockY(), second.getBlockY());
        int maxY = Math.max(first.getBlockY(), second.getBlockY());

        int minZ = Math.min(first.getBlockZ(), second.getBlockZ());
        int maxZ = Math.max(first.getBlockZ(), second.getBlockZ());

        int step = 2;

        for (int x = minX; x <= maxX; x += step) {
            spawnParticle(player, x, minY, minZ);
            spawnParticle(player, x, minY, maxZ);
            spawnParticle(player, x, maxY, minZ);
            spawnParticle(player, x, maxY, maxZ);
        }

        for (int z = minZ; z <= maxZ; z += step) {
            spawnParticle(player, minX, minY, z);
            spawnParticle(player, minX, maxY, z);
            spawnParticle(player, maxX, minY, z);
            spawnParticle(player, maxX, maxY, z);
        }

        for (int y = minY; y <= maxY; y += step) {
            spawnParticle(player, minX, y, minZ);
            spawnParticle(player, minX, y, maxZ);
            spawnParticle(player, maxX, y, minZ);
            spawnParticle(player, maxX, y, maxZ);
        }
    }

    private void spawnParticle(
            Player player,
            int x,
            int y,
            int z
    ) {
        player.spawnParticle(
                Particle.END_ROD,
                x + 0.5,
                y + 0.5,
                z + 0.5,
                1,
                0,
                0,
                0,
                0
        );
    }

    public Location getPos1(UUID playerId) {
        Location location = pos1.get(playerId);
        return location == null ? null : location.clone();
    }

    public Location getPos2(UUID playerId) {
        Location location = pos2.get(playerId);
        return location == null ? null : location.clone();
    }

    public boolean hasSelection(UUID playerId) {
        return pos1.containsKey(playerId)
                && pos2.containsKey(playerId);
    }

    public void clearSelection(UUID playerId) {
        pos1.remove(playerId);
        pos2.remove(playerId);
    }

    public void shutdown() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }

        pos1.clear();
        pos2.clear();
    }
}
