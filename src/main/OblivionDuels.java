
package com.oblivion.duels;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class OblivionDuels extends JavaPlugin
        implements CommandExecutor {

    private String prefix;

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

        if (getCommand("duels") != null) {
            getCommand("duels").setExecutor(this);
        }

        getLogger().info("OblivionDuels enabled!");
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
            sender.sendMessage(ChatColor.GRAY + "/duels help");
            sender.sendMessage(ChatColor.GRAY + "/duels status");
            sender.sendMessage(ChatColor.GRAY + "/duels reload");
            return true;
        }

        switch (args[0].toLowerCase()) {
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
