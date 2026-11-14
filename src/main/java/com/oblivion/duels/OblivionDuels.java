
package com.oblivion.duels;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class OblivionDuels extends JavaPlugin
        implements CommandExecutor {

    private String prefix;
    private MatchmakingManager matchmaking;

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
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(prefix + ChatColor.LIGHT_PURPLE
                    + "OblivionDuels Commands");
            sender.sendMessage(ChatColor.GRAY
                    + "/duels join <1v1|2v2|3v3|4v4|5v5>");
            sender.sendMessage(ChatColor.GRAY + "/duels leave");
            sender.sendMessage(ChatColor.GRAY + "/duels queue");
            sender.sendMessage(ChatColor.GRAY + "/duels status");
            sender.sendMessage(ChatColor.GRAY + "/duels reload");
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "join":
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage("Only players can join queues.");
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage(prefix + ChatColor.RED
                            + "Usage: /duels join <1v1|2v2|3v3|4v4|5v5>");
                    return true;
                }

                int teamSize;

                try {
                    teamSize = Integer.parseInt(
                            args[1].split("v")[0]
                    );

                    int opponentSize = Integer.parseInt(
                            args[1].split("v")[1]
                    );

                    if (teamSize != opponentSize
                            || teamSize < 1 || teamSize > 5) {
                        throw new IllegalArgumentException();
                    }

                } catch (Exception exception) {
                    sender.sendMessage(prefix + ChatColor.RED
                            + "Invalid mode. Choose 1v1, 2v2, 3v3, 4v4, or 5v5.");
                    return true;
                }

                boolean joined = matchmaking.joinQueue(
                        player.getUniqueId(), teamSize
                );

                if (!joined) {
                    sender.sendMessage(prefix + ChatColor.RED
                            + "You are already in a queue.");
                    return true;
                }

                int queued = matchmaking.getQueueSize(teamSize);

                sender.sendMessage(prefix + ChatColor.GREEN
                        + "You joined the " + teamSize + "v"
                        + teamSize + " queue!");

                sender.sendMessage(ChatColor.GRAY
                        + "Players queued: " + queued
                        + "/" + (teamSize * 2));

                return true;

            case "leave":
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage("Only players can leave queues.");
                    return true;
                }

                if (matchmaking.leaveQueue(player.getUniqueId())) {
                    sender.sendMessage(prefix + ChatColor.GREEN
                            + "You left the queue.");
                } else {
                    sender.sendMessage(prefix + ChatColor.RED
                            + "You are not in a queue.");
                }

                return true;

            case "queue":
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage("Only players can check their queue.");
                    return true;
                }

                Integer mode = matchmaking.getQueuedMode(
                        player.getUniqueId()
                );

                if (mode == null) {
                    sender.sendMessage(prefix + ChatColor.YELLOW
                            + "You are not in a queue.");
                } else {
                    sender.sendMessage(prefix + ChatColor.GREEN
                            + "Your queue: " + mode + "v" + mode);
                    sender.sendMessage(ChatColor.GRAY
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
