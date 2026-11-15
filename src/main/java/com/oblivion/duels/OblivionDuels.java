
package com.oblivion.duels;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class OblivionDuels extends JavaPlugin
        implements CommandExecutor {

    private String prefix;

    private MatchmakingManager matchmaking;
    private MatchManager matchManager;
    private MatchmakingCoordinator matchmakingCoordinator;
    private MatchProtectionListener matchProtectionListener;
    private MatchDeathListener matchDeathListener;

    private ArenaSelection arenaSelection;
    private ArenaManager arenaManager;
    private DuelMenu duelMenu;

    private StatsManager statsManager;
    private DatabaseManager databaseManager;

    private DuelPlaceholderExpansion placeholderExpansion;

    private final Map<UUID, DuelArena> pendingArenas =
            new HashMap<>();

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

        statsManager = new StatsManager();
        databaseManager = new DatabaseManager(this);

        databaseManager.initialize();
        databaseManager.loadStats(statsManager);

        matchmaking = new MatchmakingManager();
        matchManager = new MatchManager();

        matchProtectionListener =
                new MatchProtectionListener(this, matchManager);

        Bukkit.getPluginManager().registerEvents(
                matchProtectionListener,
                this
        );

        arenaSelection = new ArenaSelection(this);
        arenaManager = new ArenaManager(this);

        matchDeathListener = new MatchDeathListener(
                this,
                matchManager,
                arenaManager,
                statsManager,
                databaseManager
        );

        duelMenu = new DuelMenu(this, matchmaking);

        matchmakingCoordinator = new MatchmakingCoordinator(
                this,
                matchmaking,
                matchManager,
                arenaManager
        );

        matchmakingCoordinator.start();

        if (Bukkit.getPluginManager().isPluginEnabled(
                "PlaceholderAPI")) {

            placeholderExpansion =
                    new DuelPlaceholderExpansion();

            placeholderExpansion.register();

            getLogger().info(
                    "PlaceholderAPI expansion registered."
            );

        } else {
            getLogger().info(
                    "PlaceholderAPI not found. "
                    + "Placeholder support disabled."
            );
        }

        if (getCommand("duels") != null) {
            getCommand("duels").setExecutor(this);
        }

        getLogger().info("OblivionDuels by Komomii enabled!");
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }

        if (matchmakingCoordinator != null) {
            matchmakingCoordinator.stop();
        }

        if (matchDeathListener != null) {
            matchDeathListener.shutdown();
        }

        if (databaseManager != null && statsManager != null) {
            databaseManager.saveAll(statsManager);
            databaseManager.close();
        }

        getLogger().info("OblivionDuels disabled.");
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public ArenaSelection getArenaSelection() {
        return arenaSelection;
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }

    public MatchmakingManager getMatchmaking() {
        return matchmaking;
    }

    public MatchmakingCoordinator getMatchmakingCoordinator() {
        return matchmakingCoordinator;
    }

    public MatchDeathListener getMatchDeathListener() {
        return matchDeathListener;
    }

    private boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("oblivionduels.admin");
    }

    private void sendArenaHelp(CommandSender sender) {
        sender.sendMessage(prefix + ChatColor.YELLOW
                + "Arena commands:");

        sender.sendMessage(ChatColor.GRAY
                + "/duels arena create <name>");

        sender.sendMessage(ChatColor.GRAY
                + "/duels arena setregion");

        sender.sendMessage(ChatColor.GRAY
                + "/duels arena setspawn <a|b>");

        sender.sendMessage(ChatColor.GRAY
                + "/duels arena setspectator");

        sender.sendMessage(ChatColor.GRAY
                + "/duels arena wand");

        sender.sendMessage(ChatColor.GRAY
                + "/duels arena save");
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (args.length == 0
                || args[0].equalsIgnoreCase("help")) {

            sender.sendMessage(prefix + ChatColor.LIGHT_PURPLE
                    + "OblivionDuels Commands");

            sender.sendMessage(ChatColor.GRAY
                    + "/duels join <1v1|2v2|3v3|4v4|5v5>");

            sender.sendMessage(ChatColor.GRAY
                    + "/duels leave");

            sender.sendMessage(ChatColor.GRAY
                    + "/duels queue");

            sender.sendMessage(ChatColor.GRAY
                    + "/duels status");

            sender.sendMessage(ChatColor.GRAY
                    + "/duels arena");

            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {

            case "arena": {

                if (!isAdmin(sender)) {
                    sender.sendMessage(prefix + ChatColor.RED
                            + "You don't have permission.");
                    return true;
                }

                if (args.length == 1) {
                    sendArenaHelp(sender);
                    return true;
                }

                String action = args[1].toLowerCase(Locale.ROOT);

                if (action.equals("wand")) {

                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }

                    player.getInventory().addItem(
                            arenaSelection.createWand()
                    );

                    player.sendMessage(prefix + ChatColor.GREEN
                            + "Arena selection wand given.");

                    return true;
                }

                if (action.equals("create")) {

                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }

                    if (args.length < 3) {
                        sender.sendMessage(prefix + ChatColor.RED
                                + "Usage: /duels arena create <name>");
                        return true;
                    }

                    String name = args[2];

                    if (!arenaManager.createArena(
                            name,
                            player.getWorld()
                    )) {
                        sender.sendMessage(prefix + ChatColor.RED
                                + "Could not create that arena.");
                        return true;
                    }

                    DuelArena arena = arenaManager.getArena(name);

                    pendingArenas.put(
                            player.getUniqueId(),
                            arena
                    );

                    player.sendMessage(prefix + ChatColor.GREEN
                            + "Created arena " + name + ".");

                    player.sendMessage(ChatColor.YELLOW
                            + "Select two corners with your wand, "
                            + "then use /duels arena setregion.");

                    return true;
                }

                if (action.equals("setregion")) {

                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }

                    UUID playerId = player.getUniqueId();

                    DuelArena arena = pendingArenas.get(playerId);

                    if (arena == null) {
                        player.sendMessage(prefix + ChatColor.RED
                                + "Create an arena first.");
                        return true;
                    }

                    if (!arenaSelection.hasSelection(playerId)) {
                        player.sendMessage(prefix + ChatColor.RED
                                + "Select both corners first.");
                        return true;
                    }

                    Location pos1 =
                            arenaSelection.getPos1(playerId);

                    Location pos2 =
                            arenaSelection.getPos2(playerId);

                    arena.setCorner1(pos1);
                    arena.setCorner2(pos2);

                    player.sendMessage(prefix + ChatColor.GREEN
                            + "Arena region selected.");

                    return true;
                }

                if (action.equals("setspawn")) {

                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }

                    if (args.length < 3) {
                        player.sendMessage(prefix + ChatColor.RED
                                + "Usage: /duels arena setspawn <a|b>");
                        return true;
                    }

                    DuelArena arena = pendingArenas.get(
                            player.getUniqueId()
                    );

                    if (arena == null) {
                        player.sendMessage(prefix + ChatColor.RED
                                + "Create an arena first.");
                        return true;
                    }

                    String team = args[2].toLowerCase(Locale.ROOT);

                    Location location = player.getLocation();

                    if (team.equals("a")) {
                        arena.setTeamASpawn(location);

                    } else if (team.equals("b")) {
                        arena.setTeamBSpawn(location);

                    } else {
                        player.sendMessage(prefix + ChatColor.RED
                                + "Use a or b.");
                        return true;
                    }

                    player.sendMessage(prefix + ChatColor.GREEN
                            + "Team " + team.toUpperCase(Locale.ROOT)
                            + " spawn saved.");

                    return true;
                }

                if (action.equals("setspectator")) {

                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }

                    DuelArena arena = pendingArenas.get(
                            player.getUniqueId()
                    );

                    if (arena == null) {
                        player.sendMessage(prefix + ChatColor.RED
                                + "Create an arena first.");
                        return true;
                    }

                    arena.setSpectatorSpawn(
                            player.getLocation()
                    );

                    player.sendMessage(prefix + ChatColor.GREEN
                            + "Spectator spawn saved.");

                    return true;
                }

                if (action.equals("save")) {

                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }

                    UUID playerId = player.getUniqueId();

                    DuelArena arena = pendingArenas.get(playerId);

                    if (arena == null) {
                        player.sendMessage(prefix + ChatColor.RED
                                + "Create an arena first.");
                        return true;
                    }

                    if (!arena.isReady()) {
                        player.sendMessage(prefix + ChatColor.RED
                                + "Arena is incomplete. Set its region, "
                                + "both team spawns, and spectator spawn.");
                        return true;
                    }

                    arenaManager.saveArena(arena);

                    pendingArenas.remove(playerId);

                    arenaSelection.clearSelection(playerId);

                    player.sendMessage(prefix + ChatColor.GREEN
                            + "Arena saved successfully!");

                    return true;
                }

                sendArenaHelp(sender);
                return true;
            }

            case "join": {

                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Usage: /duels join <1v1|2v2|3v3|4v4|5v5>");
                    return true;
                }

                int teamSize;

                try {
                    String mode = args[1].toLowerCase(Locale.ROOT)
                            .replace("v", "");

                    teamSize = Integer.parseInt(mode);

                } catch (NumberFormatException exception) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Invalid mode. Use 1v1, 2v2, 3v3, 4v4, or 5v5.");
                    return true;
                }

                if (teamSize < 1 || teamSize > 5) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Invalid mode. Choose between 1v1 and 5v5.");
                    return true;
                }

                UUID playerId = player.getUniqueId();

                if (matchManager.isInMatch(playerId)) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "You are already in a match.");
                    return true;
                }

                if (matchmaking.getQueuedMode(playerId) != null) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "You are already in a queue.");
                    return true;
                }

                if (!matchmaking.joinQueue(playerId, teamSize)) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Could not join the queue.");
                    return true;
                }

                player.sendMessage(prefix + ChatColor.GREEN
                        + "You joined the " + teamSize + "v" + teamSize
                        + " queue!");

                return true;
            }

            case "leave": {

                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }

                UUID playerId = player.getUniqueId();

                if (matchmaking.getQueuedMode(playerId) != null) {

                    matchmaking.leaveQueue(playerId);

                    player.sendMessage(prefix + ChatColor.GREEN
                            + "You left the queue.");

                    return true;
                }

                player.sendMessage(prefix + ChatColor.RED
                        + "You are not in a queue.");

                return true;
            }

            case "queue": {

                sender.sendMessage(prefix + ChatColor.LIGHT_PURPLE
                        + "Queue Status:");

                for (int size = 1; size <= 5; size++) {

                    sender.sendMessage(ChatColor.GRAY.toString()
                            + size + "v" + size + ": "
                            + ChatColor.WHITE
                            + matchmaking.getQueueSize(size)
                            + "/" + (size * 2));
                }

                return true;
            }

            case "status": {

                sender.sendMessage(prefix + ChatColor.LIGHT_PURPLE
                        + "OblivionDuels Status");

                sender.sendMessage(ChatColor.GRAY
                        + "Active matches: " + ChatColor.WHITE
                        + matchManager.getActiveMatchCount());

                sender.sendMessage(ChatColor.GRAY
                        + "Players in matches: " + ChatColor.WHITE
                        + matchManager.getActivePlayerCount());

                sender.sendMessage(ChatColor.GRAY
                        + "Available arenas: " + ChatColor.WHITE
                        + arenaManager.getArenaCount());

                return true;
            }

            case "reload": {

                if (!isAdmin(sender)) {
                    sender.sendMessage(prefix + ChatColor.RED
                            + "You don't have permission.");
                    return true;
                }

                reloadConfig();

                prefix = ChatColor.translateAlternateColorCodes(
                        '&',
                        getConfig().getString(
                                "settings.prefix",
                                "&8[&5OblivionDuels&8] "
                        )
                );

                sender.sendMessage(prefix + ChatColor.GREEN
                        + "Configuration reloaded.");

                return true;
            }

            default: {

                sender.sendMessage(prefix + ChatColor.RED
                        + "Unknown command. Use /duels help.");

                return true;
            }
        }
    }

    private final class DuelPlaceholderExpansion
            extends PlaceholderExpansion {

        @Override
        public String getIdentifier() {
            return "oblivionduels";
        }

        @Override
        public String getAuthor() {
            return "Komomii";
        }

        @Override
        public String getVersion() {
            return getPluginMeta().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onRequest(
                OfflinePlayer player,
                String params
        ) {
            if (player == null || statsManager == null) {
                return "0";
            }

            PlayerStats stats =
                    statsManager.getStats(player.getUniqueId());

            switch (params.toLowerCase(Locale.ROOT)) {

                case "wins":
                    return String.valueOf(stats.getWins());

                case "losses":
                    return String.valueOf(stats.getLosses());

                case "kills":
                    return String.valueOf(stats.getKills());

                case "deaths":
                    return String.valueOf(stats.getDeaths());

                case "matches":
                    return String.valueOf(stats.getTotalMatches());

                case "winrate":
                    return String.format(
                            Locale.US,
                            "%.2f",
                            stats.getWinRate()
                    );

                case "kdr":
                    double kdr = stats.getDeaths() == 0
                            ? stats.getKills()
                            : (double) stats.getKills()
                            / stats.getDeaths();

                    return String.format(
                            Locale.US,
                            "%.2f",
                            kdr
                    );

                default:
                    return null;
            }
        }
    }
}
