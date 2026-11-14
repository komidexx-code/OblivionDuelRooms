
package com.oblivion.duels;

import org.bukkit.Bukkit;
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

        startParticleTask();
    }

    public ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta = wand.getItemMeta();

        meta.setDisplayName("§5§lOblivion Arena Selector");
        meta.setLore(java.util.List.of(
                "§7Left-click: §fPosition 1",
                "§7Right-click: §fPosition 2"
        ));

        meta.getPersistentDataContainer().set(
                wandKey,
                PersistentDataType.BYTE,
                (byte) 1
        );

        wand.setItemMeta(meta);
        return wand;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        return item.getItemMeta()
                .getPersistentDataContainer()
                .has(wandKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onSelection(PlayerInteractEvent event) {

        if (event.getItem() == null || !isWand(event.getItem())) {
            return;
        }

        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }

        if (event.getAction() != Action.LEFT_CLICK_BLOCK
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();

        if (!player.hasPermission("oblivionduels.admin")) {
            player.sendMessage("§cYou don't have permission.");
            return;
        }

        if (event.getClickedBlock() == null) {
            return;
        }

        Location location = event.getClickedBlock().getLocation();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            pos1.put(player.getUniqueId(), location);

            player.sendMessage(
                    "§5[OblivionDuels] §aPosition 1 selected: §f"
                            + format(location)
            );
        } else {
            pos2.put(player.getUniqueId(), location);

            player.sendMessage(
                    "§5[OblivionDuels] §aPosition 2 selected: §f"
                            + format(location)
            );
        }

        if (hasSelection(player.getUniqueId())) {
            player.sendMessage(
                    "§dArena boundaries selected! "
                            + "Particles will display your region."
            );
        }
    }

    public Location getPosition1(UUID playerId) {
        Location location = pos1.get(playerId);
        return location == null ? null : location.clone();
    }

    public Location getPosition2(UUID playerId) {
        Location location = pos2.get(playerId);
        return location == null ? null : location.clone();
    }

    public boolean hasSelection(UUID playerId) {
        Location first = pos1.get(playerId);
        Location second = pos2.get(playerId);

        return first != null
                && second != null
                && first.getWorld() != null
                && first.getWorld().equals(second.getWorld());
    }

    public void clearSelection(UUID playerId) {
        pos1.remove(playerId);
        pos2.remove(playerId);
    }

    private String format(Location location) {
        return location.getBlockX() + ", "
                + location.getBlockY() + ", "
                + location.getBlockZ();
    }

    private void startParticleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID id = player.getUniqueId();

                    if (!hasSelection(id)) {
                        continue;
                    }

                    Location first = pos1.get(id);
                    Location second = pos2.get(id);

                    if (first.getWorld() == null
                            || !first.getWorld().equals(player.getWorld())) {
                        continue;
                    }

                    drawBoundary(first, second);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void drawBoundary(Location first, Location second) {
        double minX = Math.min(first.getBlockX(), second.getBlockX());
        double maxX = Math.max(first.getBlockX(), second.getBlockX()) + 1;
        double minY = Math.min(first.getBlockY(), second.getBlockY());
        double maxY = Math.max(first.getBlockY(), second.getBlockY()) + 1;
        double minZ = Math.min(first.getBlockZ(), second.getBlockZ());
        double maxZ = Math.max(first.getBlockZ(), second.getBlockZ()) + 1;

        Location a = new Location(first.getWorld(), minX, minY, minZ);
        Location b = new Location(first.getWorld(), maxX, minY, minZ);
        Location c = new Location(first.getWorld(), maxX, minY, maxZ);
        Location d = new Location(first.getWorld(), minX, minY, maxZ);

        Location e = new Location(first.getWorld(), minX, maxY, minZ);
        Location f = new Location(first.getWorld(), maxX, maxY, minZ);
        Location g = new Location(first.getWorld(), maxX, maxY, maxZ);
        Location h = new Location(first.getWorld(), minX, maxY, maxZ);

        drawLine(a, b);
        drawLine(b, c);
        drawLine(c, d);
        drawLine(d, a);

        drawLine(e, f);
        drawLine(f, g);
        drawLine(g, h);
        drawLine(h, e);

        drawLine(a, e);
        drawLine(b, f);
        drawLine(c, g);
        drawLine(d, h);
    }

    private void drawLine(Location start, Location end) {
        double distance = start.distance(end);
        int points = Math.min(40, Math.max(1, (int) Math.ceil(distance)));

        for (int i = 0; i <= points; i++) {
            double ratio = (double) i / points;

            double x = start.getX()
                    + (end.getX() - start.getX()) * ratio;
            double y = start.getY()
                    + (end.getY() - start.getY()) * ratio;
            double z = start.getZ()
                    + (end.getZ() - start.getZ()) * ratio;

            start.getWorld().spawnParticle(
                    Particle.END_ROD,
                    x, y, z,
                    1, 0, 0, 0, 0
            );
        }
    }
}
