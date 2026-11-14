
package com.oblivion.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
    }

    private class MenuHolder implements InventoryHolder {

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
                54,
                ChatColor.DARK_PURPLE + "OblivionDuels"
        );

        holder.inventory = inventory;

        updateMenu(player, inventory);
        player.openInventory(inventory);
    }

    private void updateMenu(Player player, Inventory inventory) {

        inventory.clear();

        ItemStack filler = createItem(
                Material.PURPLE_STAINED_GLASS_PANE,
                " ",
                List.of()
        );

        for (int slot = 0; slot < 54; slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(4, createItem(
                Material.NETHER_STAR,
                "§5§lOBLIVION DUELS",
                List.of(
                        "§7Choose your preferred game mode.",
                        "§8Solo matchmaking system"
                )
        ));

        setMode(inventory, 20, 1, Material.IRON_SWORD, "Solo Duel");
        setMode(inventory, 21, 2, Material.GOLDEN_SWORD, "Doubles");
        setMode(inventory, 22, 3, Material.DIAMOND_SWORD, "Triples");
        setMode(inventory, 29, 4, Material.NETHERITE_SWORD, "Squads");
        setMode(inventory, 30, 5, Material.BOW, "Full Team");

        inventory.setItem(47, createItem(
                Material.GOLD_INGOT,
                "§6§lStatistics",
                List.of(
                        "§7View your duel statistics.",
                        "§cComing soon!"
                )
        ));

        Integer queuedMode = matchmaking.getQueuedMode(
                player.getUniqueId()
        );

        if (queuedMode == null) {
            inventory.setItem(49, createItem(
                    Material.GRAY_DYE,
                    "§7§lQueue Status",
                    List.of("§7You are not currently queued.")
            ));
        } else {
            inventory.setItem(49, createItem(
                    Material.LIME_DYE,
                    "§a§lQueue Status",
                    List.of(
                            "§7Mode: §f" + queuedMode + "v" + queuedMode,
                            "§7Players: §f"
                                    + matchmaking.getQueueSize(queuedMode)
                                    + "/" + (queuedMode * 2),
                            "§eClick to leave your queue."
                    )
            ));
        }

        inventory.setItem(53, createItem(
                Material.BARRIER,
                "§cClose Menu",
                List.of("§7Click to close.")
        ));
    }

    private void setMode(
            Inventory inventory,
            int slot,
            int teamSize,
            Material material,
            String description
    ) {
        inventory.setItem(slot, createItem(
                material,
                "§5§l" + teamSize + "v" + teamSize,
                List.of(
                        "§7" + description,
                        "§7Players required: §f" + (teamSize * 2),
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

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {

        if (!(event.getView().getTopInventory().getHolder()
                instanceof DuelMenu.MenuHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null
                || event.getClickedInventory()
                != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getRawSlot();

        if (slot == 53) {
            player.closeInventory();
            return;
        }

        if (slot == 49) {
            if (matchmaking.leaveQueue(player.getUniqueId())) {
                player.sendMessage(
                        "§5[OblivionDuels] §aYou left the queue."
                );
            } else {
                player.sendMessage(
                        "§5[OblivionDuels] §cYou are not in a queue."
                );
            }

            updateMenu(player, event.getView().getTopInventory());
            return;
        }

        int teamSize = switch (slot) {
            case 20 -> 1;
            case 21 -> 2;
            case 22 -> 3;
            case 29 -> 4;
            case 30 -> 5;
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

        updateMenu(player, event.getView().getTopInventory());
    }
}
