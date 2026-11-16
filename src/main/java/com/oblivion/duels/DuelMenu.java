
package com.oblivion.duels;

import org.bukkit.Bukkit;
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
import java.util.Locale;

public final class DuelMenu implements Listener {

    private final OblivionDuels plugin;
    private final MatchmakingManager matchmaking;

    private static final String PREFIX = "§8[§bᴏʙʟɪᴠɪᴏɴ ᴅᴜᴇʟs§8] ";

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
                "§8ᴏʙʟɪᴠɪᴏɴ ᴅᴜᴇʟs"
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
                "§8ᴅᴜᴇʟ sᴛᴀᴛɪsᴛɪᴄs"
        );

        holder.inventory = inventory;

        updateStats(player, inventory);
        player.openInventory(inventory);
    }

    private void updateStats(Player player, Inventory inventory) {

        inventory.clear();

        ItemStack filler = createItem(
                Material.BLUE_STAINED_GLASS_PANE,
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
                "§b§lʏᴏᴜʀ ᴅᴜᴇʟ sᴛᴀᴛs",
                List.of(
                        "§7ᴘʟᴀʏᴇʀ ᴘʀᴏғɪʟᴇ",
                        "§8ᴏʙʟɪᴠɪᴏɴ ᴅᴜᴇʟs"
                )
        ));

        inventory.setItem(10, createItem(
                Material.GOLD_INGOT,
                "§b§lᴡɪɴs",
                List.of(
                        "§7ᴛᴏᴛᴀʟ ᴡɪɴs: §f" + stats.getWins()
                )
        ));

        inventory.setItem(11, createItem(
                Material.REDSTONE,
                "§c§lʟᴏssᴇs",
                List.of(
                        "§7ᴛᴏᴛᴀʟ ʟᴏssᴇs: §f"
                                + stats.getLosses()
                )
        ));

        inventory.setItem(12, createItem(
                Material.IRON_SWORD,
                "§b§lᴋɪʟʟs",
                List.of(
                        "§7ᴛᴏᴛᴀʟ ᴋɪʟʟs: §f"
                                + stats.getKills()
                )
        ));

        inventory.setItem(13, createItem(
                Material.SKELETON_SKULL,
                "§f§lᴅᴇᴀᴛʜs",
                List.of(
                        "§7ᴛᴏᴛᴀʟ ᴅᴇᴀᴛʜs: §f"
                                + stats.getDeaths()
                )
        ));

        inventory.setItem(14, createItem(
                Material.PAPER,
                "§3§lᴍᴀᴛᴄʜᴇs ᴘʟᴀʏᴇᴅ",
                List.of(
                        "§7ᴛᴏᴛᴀʟ ᴍᴀᴛᴄʜᴇs: §f"
                                + stats.getTotalMatches()
                )
        ));

        inventory.setItem(15, createItem(
                Material.EXPERIENCE_BOTTLE,
                "§b§lᴡɪɴ ʀᴀᴛᴇ",
                List.of(
                        "§7ᴡɪɴ ʀᴀᴛᴇ: §f"
                                + String.format(
                                        Locale.US,
                                        "%.2f%%",
                                        stats.getWinRate()
                                )
                )
        ));

        inventory.setItem(22, createItem(
                Material.ARROW,
                "§b§lʙᴀᴄᴋ",
                List.of(
                        "§7ʀᴇᴛᴜʀɴ ᴛᴏ ᴛʜᴇ ᴍᴀɪɴ ᴍᴇɴᴜ"
                )
        ));
    }

    private void updateMenu(Player player, Inventory inventory) {

        inventory.clear();

        ItemStack filler = createItem(
                Material.BLUE_STAINED_GLASS_PANE,
                " ",
                List.of()
        );

        for (int slot = 0; slot < 27; slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(4, createItem(
                Material.NETHER_STAR,
                "§b§lᴏʙʟɪᴠɪᴏɴ ᴅᴜᴇʟs",
                List.of(
                        "§7ᴡᴇʟᴄᴏᴍᴇ, §f" + player.getName(),
                        "",
                        "§8ᴄʜᴏᴏsᴇ ʏᴏᴜʀ ᴅᴜᴇʟ ᴍᴏᴅᴇ"
                )
        ));

        setMode(
                inventory, 11, 1,
                Material.IRON_SWORD,
                "ᴏɴᴇ ᴠs ᴏɴᴇ"
        );

        setMode(
                inventory, 12, 2,
                Material.GOLDEN_SWORD,
                "ᴛᴡᴏ ᴠs ᴛᴡᴏ"
        );

        setMode(
                inventory, 13, 3,
                Material.DIAMOND_SWORD,
                "ᴛʜʀᴇᴇ ᴠs ᴛʜʀᴇᴇ"
        );

        setMode(
                inventory, 14, 4,
                Material.NETHERITE_SWORD,
                "ғᴏᴜʀ ᴠs ғᴏᴜʀ"
        );

        setMode(
                inventory, 15, 5,
                Material.BOW,
                "ғɪᴠᴇ ᴠs ғɪᴠᴇ"
        );

        inventory.setItem(18, createItem(
                Material.PLAYER_HEAD,
                "§b§lᴘʟᴀʏᴇʀ sᴛᴀᴛs",
                List.of(
                        "§7ᴠɪᴇᴡ ʏᴏᴜʀ ᴅᴜᴇʟ ʀᴇᴄᴏʀᴅ",
                        "",
                        "§eᴄʟɪᴄᴋ ᴛᴏ ᴠɪᴇᴡ"
                )
        ));

        Integer queuedMode = matchmaking.getQueuedMode(
                player.getUniqueId()
        );

        if (queuedMode == null) {
            inventory.setItem(22, createItem(
                    Material.GRAY_DYE,
                    "§7§lǫᴜᴇᴜᴇ sᴛᴀᴛᴜs",
                    List.of(
                            "§7ʏᴏᴜ ᴀʀᴇ ɴᴏᴛ ǫᴜᴇᴜᴇᴅ",
                            "",
                            "§8sᴇʟᴇᴄᴛ ᴀ ᴍᴏᴅᴇ ᴛᴏ ᴘʟᴀʏ"
                    )
            ));
        } else {
            inventory.setItem(22, createItem(
                    Material.LIME_DYE,
                    "§b§lǫᴜᴇᴜᴇ sᴛᴀᴛᴜs",
                    List.of(
                            "§7ᴍᴏᴅᴇ: §f"
                                    + queuedMode + "ᴠ" + queuedMode,
                            "§7ᴘʟᴀʏᴇʀs: §f"
                                    + matchmaking.getQueueSize(queuedMode)
                                    + "/" + (queuedMode * 2),
                            "",
                            "§eᴄʟɪᴄᴋ ᴛᴏ ʟᴇᴀᴠᴇ ǫᴜᴇᴜᴇ"
                    )
            ));
        }

        inventory.setItem(26, createItem(
                Material.BARRIER,
                "§c§lᴄʟᴏsᴇ",
                List.of(
                        "§7ᴄʟᴏsᴇ ᴛʜɪs ᴍᴇɴᴜ"
                )
        ));
    }

    private void setMode(
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
                "§b§l" + teamSize + "ᴠ" + teamSize,
                List.of(
                        "§7" + description,
                        "",
                        "§7ᴘʟᴀʏᴇʀs ʀᴇǫᴜɪʀᴇᴅ: §f" + required,
                        "§7ᴄᴜʀʀᴇɴᴛ ǫᴜᴇᴜᴇ: §b"
                                + queued + "§7/" + required,
                        "",
                        "§eᴄʟɪᴄᴋ ᴛᴏ ᴊᴏɪɴ"
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
                        PREFIX + "§bʏᴏᴜ ʟᴇғᴛ ᴛʜᴇ ǫᴜᴇᴜᴇ."
                );
            } else {
                player.sendMessage(
                        PREFIX + "§cʏᴏᴜ ᴀʀᴇ ɴᴏᴛ ɪɴ ᴀ ǫᴜᴇᴜᴇ."
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

        plugin.joinQueue(player, teamSize);

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
