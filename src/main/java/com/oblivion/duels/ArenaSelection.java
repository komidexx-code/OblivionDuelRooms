
package com.oblivion.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ArenaSelection implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey wandKey;

    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

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
            meta.setLore(java.util.List.of(
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

        Location location = event.getClickedBlock().getLocation();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            pos1.put(player.getUniqueId(), location);

            player.sendMessage(
                    "§5[OblivionDuels] §aPosition 1 selected: "
                            + format(location)
            );

        } else {
            pos2.put(player.getUniqueId(), location);

            player.sendMessage(
                    "§5[OblivionDuels] §aPosition 2 selected: "
                            + format(location)
            );
        }
    }

    private String format(Location location) {
        return location.getBlockX() + ", "
                + location.getBlockY() + ", "
                + location.getBlockZ();
    }

    private void startParticleDisplay() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {

                    UUID id = player.getUniqueId();

                    Location first = pos1.get(id);
                    Location second = pos2.get(id);

                    if (first == null || second == null) {
                        continue;
                    }

                    if (!first.getWorld().equals(second.getWorld())) {
                        continue;
                    }

                    displayBoundary(player, first, second);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void displayBoundary(
            Player player,
            Location first,
            Location second
    ) {
        int minX = Math.min(first.getBlockX(), second.getBlockX());
        int maxX = Math.max(first.getBlockX(), second.getBlockX());

        int minY = Math.min(first.getBlockY(), second.getBlockY());
        int maxY = Math.max(first.getBlockY(), second.getBlockY());

        int minZ = Math.min(first.getBlockZ(), second.getBlockZ());
        int maxZ = Math.max(first.getBlockZ(), second.getBlockZ());

        // Display the 12 edges of the selected cuboid.
        for (int x = minX; x <= maxX; x++) {
            for (int y : new int[]{minY, maxY}) {
                for (int z : new int[]{minZ, maxZ}) {
                    spawnParticle(player, x, y, z);
                }
            }
        }

        for (int z = minZ; z <= maxZ; z++) {
            for (int y : new int[]{minY, maxY}) {
                for (int x : new int[]{minX, maxX}) {
                    spawnParticle(player, x, y, z);
                }
            }
        }

        for (int y = minY; y <= maxY; y++) {
            for (int x : new int[]{minX, maxX}) {
                for (int z : new int[]{minZ, maxZ}) {
                    spawnParticle(player, x, y, z);
                }
            }
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
}
