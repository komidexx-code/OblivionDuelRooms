
package com.oblivion.duels;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class OblivionDuels extends JavaPlugin
        implements CommandExecutor {

    private String prefix;
    private MatchmakingManager matchmaking;
    private MatchManager matchManager;
    private ArenaSelection arenaSelection;
    private DuelMenu duelMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        prefix = ChatColor.translateAlternateColorCodes(
                '&',
                getConfig().getString(
                        "settings.prefix",
                        "&8[&5OblivionDuels&8] "
                )
        );

        matchmaking = new MatchmakingManager();
        matchManager = new MatchManager();
        arenaSelection = new ArenaSelection(this);
        duelMenu = new DuelMenu(this, matchmaking);

        if (getCommand("duels") != null) {
            getCommand("duels").setExecutor(this);
        }

        getLogger().info("OblivionDuels by Komomii enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("OblivionDuels disabled.");
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                duelMenu.openMenu(player);
            } else {
                sender.sendMessage("Only players can open the duels menu.");
            }
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "help":
                sender.sendMessage(prefix + ChatColor.LIGHT_PURPLE
                        + "OblivionDuels Commands");
                sender.sendMessage(ChatColor.GRAY
                        + "/duels");
                sender.sendMessage(ChatColor.GRAY
                        + "/duels join <1v1|2v2|3v3|4v4|5v5>");
                sender.sendMessage(ChatColor.GRAY + "/duels leave");
                sender.sendMessage(ChatColor.GRAY + "/duels queue");
                sender.sendMessage(ChatColor.GRAY + "/duels wand");
                sender.sendMessage(ChatColor.GRAY + "/duels status");
                sender.sendMessage(ChatColor.GRAY + "/duels reload");
                return true;

            case "wand":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can use this command.");
                    return true;
                }

                if (!player.hasPermission("oblivionduels.admin")) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "You don't have permission.");
                    return true;
                }

                player.getInventory().addItem(arenaSelection.createWand());
                player.sendMessage(prefix + ChatColor.GREEN
                        + "You received the Oblivion Arena Selector!");
                return true;

            case "join":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can join queues.");
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Usage: /duels join <1v1|2v2|3v3|4v4|5v5>");
                    return true;
                }

                int teamSize;

                try {
                    String[] parts = args[1].split("v");

                    if (parts.length != 2) {
                        throw new IllegalArgumentException();
                    }

                    teamSize = Integer.parseInt(parts[0]);
                    int opponentSize = Integer.parseInt(parts[1]);

                    if (teamSize != opponentSize
                            || teamSize < 1 || teamSize > 5) {
                        throw new IllegalArgumentException();
                    }
                } catch (Exception exception) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Invalid mode. Choose 1v1, 2v2, 3v3, 4v4, or 5v5.");
                    return true;
                }

                boolean joined = matchmaking.joinQueue(
                        player.getUniqueId(), teamSize
                );

                if (!joined) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "You are already in a queue.");
                    return true;
                }

                player.sendMessage(prefix + ChatColor.GREEN
                        + "You joined the " + teamSize + "v"
                        + teamSize + " queue!");

                player.sendMessage(ChatColor.GRAY
                        + "Players queued: "
                        + matchmaking.getQueueSize(teamSize)
                        + "/" + (teamSize * 2));
                return true;

            case "leave":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can leave queues.");
                    return true;
                }

                if (matchmaking.leaveQueue(player.getUniqueId())) {
                    player.sendMessage(prefix + ChatColor.GREEN
                            + "You left the queue.");
                } else {
                    player.sendMessage(prefix + ChatColor.RED
                            + "You are not in a queue.");
                }
                return true;

            case "queue":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can check their queue.");
                    return true;
                }

                Integer mode = matchmaking.getQueuedMode(
                        player.getUniqueId()
                );

                if (mode == null) {
                    player.sendMessage(prefix + ChatColor.YELLOW
                            + "You are not in a queue.");
                } else {
                    player.sendMessage(prefix + ChatColor.GREEN
                            + "Your queue: " + mode + "v" + mode);
                    player.sendMessage(ChatColor.GRAY
                            + "Players queued: "
                            + matchmaking.getQueueSize(mode)
                            + "/" + (mode * 2));
                }
                return true;

            case "status":
                sender.sendMessage(prefix + ChatColor.GREEN
                        + "Plugin is running.");
                sender.sendMessage(ChatColor.GRAY
                        + "Version: " + getDescription().getVersion());
                return true;

            case "reload":
                if (!sender.hasPermission("oblivionduels.admin")) {
                    sender.sendMessage(prefix + ChatColor.RED
                            + "You don't have permission.");
                    return true;
                }

                reloadConfig();
                sender.sendMessage(prefix + ChatColor.GREEN
                        + "Configuration reloaded.");
                return true;

            default:
                sender.sendMessage(prefix + ChatColor.RED
                        + "Unknown command. Use /duels help.");
                return true;
        }
    }
}
