package com.oblivion.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
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
                new MatchProtectionListener(matchManager);

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

        if (getCommand("duels") != null) {
            getCommand("duels").setExecutor(this);
        }

        getLogger().info("OblivionDuels by Komomii enabled!");
    }

    @Override
    public void onDisable() {
        if (matchmakingCoordinator != null) {
            matchmakingCoordinator.stop();
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
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
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

        String sub = args[0].toLowerCase();

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

                String action = args[1].toLowerCase();

                if (action.equals("wand")) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }

                    arenaSelection.giveWand(player);
                    sender.sendMessage(prefix + ChatColor.GREEN
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

                    if (arenaManager.getArena(name) != null) {
                        sender.sendMessage(prefix + ChatColor.RED
                                + "That arena already exists.");
                        return true;
                    }

                    DuelArena arena = arenaManager.createArena(
                            name,
                            player.getWorld().getUID()
                    );

                    pendingArenas.put(player.getUniqueId(), arena);

                    sender.sendMessage(prefix + ChatColor.GREEN
                            + "Created arena " + name + ".");
                    sender.sendMessage(ChatColor.YELLOW
                            + "Select two corners with your wand, "
                            + "then use /duels arena setregion.");
                    return true;
                }

                if (action.equals("setregion")) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }

                    DuelArena arena = pendingArenas.get(
                            player.getUniqueId()
                    );

                    if (arena == null) {
                        sender.sendMessage(prefix + ChatColor.RED
                                + "Create an arena first.");
                        return true;
                    }

                    if (!arenaSelection.hasSelection(player)) {
                        sender.sendMessage(prefix + ChatColor.RED
                                + "Select both corners first.");
                        return true;
                    }

                    arena.setCorners(
                            arenaSelection.getPos1(player),
                            arenaSelection.getPos2(player)
                    );

                    sender.sendMessage(prefix + ChatColor.GREEN
                            + "Arena region selected.");
                    return true;
                }

                if (action.equals("setspawn")) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }

                    if (args.length < 3) {
                        sender.sendMessage(prefix + ChatColor.RED
                                + "Usage: /duels arena setspawn <a|b>");
                        return true;
                    }

                    DuelArena arena = pendingArenas.get(
                            player.getUniqueId()
                    );

                    if (arena == null) {
                        sender.sendMessage(prefix + ChatColor.RED
                                + "Create an arena first.");
                        return true;
                    }

                    String team = args[2].toLowerCase();
                    Location location = player.getLocation();

                    if (team.equals("a")) {
                        arena.setTeamASpawn(location);
                    } else if (team.equals("b")) {
                        arena.setTeamBSpawn(location);
                    } else {
                        sender.sendMessage(prefix + ChatColor.RED
                                + "Use a or b.");
                        return true;
                    }

                    sender.sendMessage(prefix + ChatColor.GREEN
                            + "Team " + team.toUpperCase()
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
                        sender.sendMessage(prefix + ChatColor.RED
                                + "Create an arena first.");
                        return true;
                    }

                    arena.setSpectatorSpawn(player.getLocation());

                    sender.sendMessage(prefix + ChatColor.GREEN
                            + "Spectator spawn saved.");
                    return true;
                }

                if (action.equals("save")) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }

                    DuelArena arena = pendingArenas.get(
                            player.getUniqueId()
                    );

                    if (arena == null) {
                        sender.sendMessage(prefix + ChatColor.RED
                                + "Create an arena first.");
                        return true;
                    }

                    if (!arena.isReady()) {
                        sender.sendMessage(prefix + ChatColor.RED
                                + "Arena is incomplete. Set its region, "
                                + "both team spawns, and spectator spawn.");
                        return true;
                    }

                    arenaManager.registerArena(arena);
                    arenaManager.saveArenas();
                    pendingArenas.remove(player.getUniqueId());

                    sender.sendMessage(prefix + ChatColor.GREEN
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
                    String mode = args[1].toLowerCase()
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

                if (matchManager.isInMatch(player.getUniqueId())) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "You are already in a match.");
                    return true;
                }

                if (matchmaking.getQueuedMode(player.getUniqueId()) != null) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "You are already in a queue.");
                    return true;
                }

                matchmaking.joinQueue(player.getUniqueId(), teamSize);

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
                    sender.sendMessage(ChatColor.GRAY
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
                        + matchManager.getPlayerMatchCount());
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
        }    
