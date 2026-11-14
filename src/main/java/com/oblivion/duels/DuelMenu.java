
package com.oblivion.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public final class DuelMenu implements Listener {

    private final OblivionDuels plugin;
    private final MatchmakingManager matchmaking;

    public DuelMenu(
            OblivionDuels plugin,
            MatchmakingManager matchmaking
    ) {
        this.plugin = plugin;
        this.matchmaking = matchmaking;

        Bukkit.getPluginManager().registerEvents(this, plugin);

        startAutoRefresh(plugin);
    }

    private class MenuHolder implements InventoryHolder {

        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private class StatsHolder implements InventoryHolder {

        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void openMenu(Player player) {
        MenuHolder holder = new MenuHolder();

        Inventory inventory = Bukkit.createInventory(
                holder,
                27,
                ChatColor.DARK_PURPLE + "OblivionDuels"
        );

        holder.inventory = inventory;

        updateMenu(player, inventory);
        player.openInventory(inventory);
    }

    private void openStats(Player player) {
        StatsHolder holder = new StatsHolder();

        Inventory inventory = Bukkit.createInventory(
                holder,
                27,
                ChatColor.DARK_PURPLE + "Your Statistics"
        );

        holder.inventory = inventory;

        updateStats(player, inventory);
        player.openInventory(inventory);
    }

    private void updateStats(Player player, Inventory inventory) {

        inventory.clear();

        ItemStack filler = createItem(
                Material.PURPLE_STAINED_GLASS_PANE,
                " ",
                List.of()
        );

        for (int slot = 0; slot < 27; slot++) {
            inventory.setItem(slot, filler);
        }

        PlayerStats stats = plugin.getStatsManager().getStats(
                player.getUniqueId()
        );

        inventory.setItem(4, createItem(
                Material.NETHER_STAR,
                "§5§lYOUR DUEL STATISTICS",
                List.of("§7OblivionDuels player profile")
        ));

        inventory.setItem(10, createItem(
                Material.GOLD_INGOT,
                "§6§lWins",
                List.of("§7Total wins: §a" + stats.getWins())
        ));

        inventory.setItem(11, createItem(
                Material.REDSTONE,
                "§c§lLosses",
                List.of("§7Total losses: §c" + stats.getLosses())
        ));

        inventory.setItem(12, createItem(
                Material.IRON_SWORD,
                "§e§lKills",
                List.of("§7Total kills: §e" + stats.getKills())
        ));

        inventory.setItem(13, createItem(
                Material.SKELETON_SKULL,
                "§f§lDeaths",
                List.of("§7Total deaths: §f" + stats.getDeaths())
        ));

        inventory.setItem(14, createItem(
                Material.PAPER,
                "§b§lTotal Matches",
                List.of("§7Matches played: §f"
                        + stats.getTotalMatches())
        ));

        inventory.setItem(15, createItem(
                Material.EXPERIENCE_BOTTLE,
                "§d§lWin Rate",
                List.of("§7Win rate: §d"
                        + String.format("%.2f%%", stats.getWinRate()))
        ));

        inventory.setItem(22, createItem(
                Material.ARROW,
                "§e§lBack",
                List.of("§7Return to the main menu.")
        ));
    }

    private void updateMenu(Player player, Inventory inventory) {

        inventory.clear();

        ItemStack filler = createItem(
                Material.PURPLE_STAINED_GLASS_PANE,
                " ",
                List.of()
        );

        for (int slot = 0; slot < 27; slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(4, createItem(
                Material.NETHER_STAR,
                "§5§lOBLIVION DUELS",
                List.of(
                        "§7Welcome to OblivionDuels!",
                        "§8Choose your preferred game mode."
                )
        ));

        setMode(player, inventory, 11, 1,
                Material.IRON_SWORD, "Solo Duel");

        setMode(player, inventory, 12, 2,
                Material.GOLDEN_SWORD, "Doubles");

        setMode(player, inventory, 13, 3,
                Material.DIAMOND_SWORD, "Triples");

        setMode(player, inventory, 14, 4,
                Material.NETHERITE_SWORD, "Squads");

        setMode(player, inventory, 15, 5,
                Material.BOW, "Full Team");

        inventory.setItem(18, createItem(
                Material.GOLD_INGOT,
                "§6§lStatistics",
                List.of(
                        "§7View your duel statistics.",
                        "§eClick to view!"
                )
        ));

        Integer queuedMode = matchmaking.getQueuedMode(
                player.getUniqueId()
        );

        if (queuedMode == null) {
            inventory.setItem(22, createItem(
                    Material.GRAY_DYE,
                    "§7§lQueue Status",
                    List.of(
                            "§7You are not currently queued."
                    )
            ));
        } else {
            inventory.setItem(22, createItem(
                    Material.LIME_DYE,
                    "§a§lQueue Status",
                    List.of(
                            "§7Mode: §f" + queuedMode + "v" + queuedMode,
                            "§7Players: §f"
                                    + matchmaking.getQueueSize(queuedMode)
                                    + "/" + (queuedMode * 2),
                            "",
                            "§eClick to leave your queue."
                    )
            ));
        }

        inventory.setItem(26, createItem(
                Material.BARRIER,
                "§c§lClose Menu",
                List.of("§7Click to close the menu.")
        ));
    }

    private void setMode(
            Player player,
            Inventory inventory,
            int slot,
            int teamSize,
            Material material,
            String description
    ) {
        int queued = matchmaking.getQueueSize(teamSize);
        int required = teamSize * 2;

        inventory.setItem(slot, createItem(
                material,
                "§5§l" + teamSize + "v" + teamSize,
                List.of(
                        "§7" + description,
                        "§7Players required: §f" + required,
                        "§7Currently queued: §a"
                                + queued + "§7/" + required,
                        "",
                        "§eClick to join queue!"
                )
        ));
    }

    private ItemStack createItem(
            Material material,
            String name,
            List<String> lore
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private void startAutoRefresh(OblivionDuels plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Inventory inventory =
                            player.getOpenInventory().getTopInventory();

                    if (inventory.getHolder() instanceof MenuHolder) {
                        updateMenu(player, inventory);
                    } else if (inventory.getHolder()
                            instanceof StatsHolder) {
                        updateStats(player, inventory);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {

        Inventory top = event.getView().getTopInventory();

        if (!(top.getHolder() instanceof MenuHolder)
                && !(top.getHolder() instanceof StatsHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null
                || event.getClickedInventory() != top) {
            return;
        }

        int slot = event.getRawSlot();

        if (top.getHolder() instanceof StatsHolder) {
            if (slot == 22) {
                openMenu(player);
            }
            return;
        }

        if (slot == 26) {
            player.closeInventory();
            return;
        }

        if (slot == 18) {
            openStats(player);
            return;
        }

        if (slot == 22) {
            if (matchmaking.leaveQueue(player.getUniqueId())) {
                player.sendMessage(
                        "§5[OblivionDuels] §aYou left the queue."
                );
            } else {
                player.sendMessage(
                        "§5[OblivionDuels] §cYou are not in a queue."
                );
            }

            updateMenu(player, top);
            return;
        }

        int teamSize = switch (slot) {
            case 11 -> 1;
            case 12 -> 2;
            case 13 -> 3;
            case 14 -> 4;
            case 15 -> 5;
            default -> 0;
        };

        if (teamSize == 0) {
            return;
        }

        boolean joined = matchmaking.joinQueue(
                player.getUniqueId(),
                teamSize
        );

        if (joined) {
            player.sendMessage(
                    "§5[OblivionDuels] §aYou joined the "
                            + teamSize + "v" + teamSize + " queue!"
            );
        } else {
            player.sendMessage(
                    "§5[OblivionDuels] §cYou are already in a queue."
            );
        }

        updateMenu(player, top);
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {

        if (event.getView().getTopInventory().getHolder()
                instanceof MenuHolder
                || event.getView().getTopInventory().getHolder()
                instanceof StatsHolder) {
            event.setCancelled(true);
        }
    }
}
