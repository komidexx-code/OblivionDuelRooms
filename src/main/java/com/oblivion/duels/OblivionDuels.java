
package com.oblivion.duels;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OblivionDuels extends JavaPlugin
        implements CommandExecutor {

    private static final Pattern HEX_PATTERN =
            Pattern.compile("&#([A-Fa-f0-9]{6})");

    private String prefix;

    private MatchmakingManager matchmaking;
    private MatchManager matchManager;
    private MatchmakingCoordinator matchmakingCoordinator;
    private MatchProtectionListener matchProtectionListener;
    private MatchDeathListener matchDeathListener;

    private ArenaSelection arenaSelection;
    private ArenaManager arenaManager;
    private ArenaRollbackManager rollbackManager;
    private DuelMenu duelMenu;

    private StatsManager statsManager;
    private DatabaseManager databaseManager;

    private DuelPlaceholderExpansion placeholderExpansion;

    private final Map<UUID, DuelArena> pendingArenas =
            new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        updatePrefix();

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

        rollbackManager = new ArenaRollbackManager(this);

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
                arenaManager,
                rollbackManager
        );

        matchmakingCoordinator.start();

        registerPlaceholderExpansion();
        registerCommand();

        getLogger().info("OblivionDuels by Komomii enabled!");
    }

    // ==========================================
    // COLOR AND MESSAGE SYSTEM
    // ==========================================

    public String color(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);

            StringBuilder replacement = new StringBuilder("§x");

            for (char character : hex.toCharArray()) {
                replacement.append('§').append(character);
            }

            matcher.appendReplacement(
                    buffer,
                    Matcher.quoteReplacement(replacement.toString())
            );
        }

        matcher.appendTail(buffer);

        return ChatColor.translateAlternateColorCodes(
                '&',
                buffer.toString()
        );
    }

    private void updatePrefix() {
        prefix = color(getConfig().getString(
                "settings.prefix",
                "&8[&5OblivionDuels&8] "
        ));
    }

    public String getPrefix() {
        return prefix;
    }

    public String getMessage(String path, String fallback) {
        return color(getConfig().getString(path, fallback));
    }

    public String getPrefixedMessage(
            String path,
            String fallback
    ) {
        return prefix + getMessage(path, fallback);
    }

    // ==========================================
    // PLACEHOLDER API
    // ==========================================

    private void registerPlaceholderExpansion() {
        if (!Bukkit.getPluginManager().isPluginEnabled(
                "PlaceholderAPI")) {

            getLogger().info(
                    "PlaceholderAPI not found. "
                            + "Placeholder support disabled."
            );
            return;
        }

        placeholderExpansion = new DuelPlaceholderExpansion();
        placeholderExpansion.register();

        getLogger().info(
                "PlaceholderAPI expansion registered."
        );
    }

    private void registerCommand() {
        if (getCommand("duels") == null) {
            getLogger().severe(
                    "Command 'duels' is missing from plugin.yml!"
            );
            return;
        }

        getCommand("duels").setExecutor(this);
    }

    // ==========================================
    // SHUTDOWN
    // ==========================================

    @Override
    public void onDisable() {
        if (matchmakingCoordinator != null) {
            matchmakingCoordinator.stop();
        }

        if (matchDeathListener != null) {
            matchDeathListener.shutdown();
        }

        if (rollbackManager != null) {
            rollbackManager.shutdown();
        }

        if (arenaSelection != null) {
            arenaSelection.shutdown();
        }

        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }

        if (databaseManager != null && statsManager != null) {
            databaseManager.saveAll(statsManager);
            databaseManager.close();
        }

        getLogger().info("OblivionDuels disabled.");
    }

    // ==========================================
    // GETTERS
    // ==========================================

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public ArenaRollbackManager getRollbackManager() {
        return rollbackManager;
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

    public DuelMenu getDuelMenu() {
        return duelMenu;
    }

    // ==========================================
    // MATCHMAKING
    // ==========================================

    private boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("oblivionduels.admin");
    }

    private void announceQueueJoin(
            Player joiningPlayer,
            int teamSize
    ) {
        int currentPlayers =
                matchmaking.getQueueSize(teamSize);

        int maximumPlayers = teamSize * 2;

        String message = getMessage(
                "messages.queue-announcement",
                "&b{player} joined the {mode} queue! &8({players}/{max})"
        );

        message = message
                .replace("{player}", joiningPlayer.getName())
                .replace("{mode}", teamSize + "v" + teamSize)
                .replace("{players}", String.valueOf(currentPlayers))
                .replace("{max}", String.valueOf(maximumPlayers));

        Bukkit.broadcastMessage(prefix + message);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.playSound(
                    onlinePlayer.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_PLING,
                    0.8f,
                    1.5f
            );
        }
    }

    public boolean joinQueue(Player player, int teamSize) {
        if (player == null
                || teamSize < 1
                || teamSize > 5) {
            return false;
        }

        UUID playerId = player.getUniqueId();

        if (matchManager.isInMatch(playerId)) {
            player.sendMessage(getPrefixedMessage(
                    "messages.already-in-match",
                    "&cYou are already in a match."
            ));
            return false;
        }

        if (matchmaking.getQueuedMode(playerId) != null) {
            player.sendMessage(getPrefixedMessage(
                    "messages.already-in-queue",
                    "&cYou are already in a queue."
            ));
            return false;
        }

        if (!matchmaking.joinQueue(playerId, teamSize)) {
            player.sendMessage(getPrefixedMessage(
                    "messages.queue-failed",
                    "&cCould not join the queue."
            ));
            return false;
        }

        String message = getMessage(
                "messages.queue-joined",
                "&aYou joined the {mode} queue!"
        );

        message = message.replace(
                "{mode}",
                teamSize + "v" + teamSize
        );

        player.sendMessage(prefix + message);

        announceQueueJoin(player, teamSize);

        return true;
    }

    // ==========================================
    // ARENA HELP
    // ==========================================

    private void sendArenaHelp(CommandSender sender) {
        sender.sendMessage(prefix + color("&bᴬʀᴇɴᴀ ᴄᴏᴍᴍᴀɴᴅs:"));

        sender.sendMessage(color("&7/duels arena create <name>"));
        sender.sendMessage(color("&7/duels arena setregion"));
        sender.sendMessage(color("&7/duels arena setspawn <a|b>"));
        sender.sendMessage(color("&7/duels arena setspectator"));
        sender.sendMessage(color("&7/duels arena wand"));
        sender.sendMessage(color("&7/duels arena save"));
        sender.sendMessage(color("&7/duels setexit <arena>"));
    }

    // ==========================================
    // COMMAND HANDLER
    // ==========================================

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (args.length == 0
                || args[0].equalsIgnoreCase("help")) {

            sender.sendMessage(prefix + color("&b&lᴏʙʟɪᴠɪᴏɴ ᴅᴜᴇʟs ᴄᴏᴍᴍᴀɴᴅs"));

            sender.sendMessage(color("&7/duels menu"));
            sender.sendMessage(color("&7/duels join <1v1|2v2|3v3|4v4|5v5>"));
            sender.sendMessage(color("&7/duels leave"));
            sender.sendMessage(color("&7/duels queue"));
            sender.sendMessage(color("&7/duels status"));
            sender.sendMessage(color("&7/duels arena"));
            sender.sendMessage(color("&7/duels setexit <arena>"));
            sender.sendMessage(color("&7/duels reload"));

            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {

            case "menu": {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }

                duelMenu.openMenu(player);
                return true;
            }

            case "setexit": {
                if (!isAdmin(sender)) {
                    sender.sendMessage(prefix + color("&cYou don't have permission."));
                    return true;
                }

                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(prefix + color("&cUsage: /duels setexit <arena>"));
                    return true;
                }

                DuelArena arena = arenaManager.getArena(args[1]);

                if (arena == null) {
                    player.sendMessage(prefix + color("&cArena not found: " + args[1]));
                    return true;
                }

                arena.setExitSpawn(player.getLocation());
                arenaManager.saveArena(arena);

                player.sendMessage(prefix + color(
                        "&aExit spawn saved for arena " + arena.getName() + "!"
                ));

                return true;
            }

            case "arena": {
                if (!isAdmin(sender)) {
                    sender.sendMessage(prefix + color("&cYou don't have permission."));
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

                    player.sendMessage(prefix + color("&aArena selection wand given."));
                    return true;
                }

                if (action.equals("create")) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }

                    if (args.length < 3) {
                        player.sendMessage(prefix + color("&cUsage: /duels arena create <name>"));
                        return true;
                    }

                    String name = args[2];

                    if (!arenaManager.createArena(
                            name,
                            player.getWorld()
                    )) {
                        player.sendMessage(prefix + color("&cCould not create that arena."));
                        return true;
                    }

                    DuelArena arena = arenaManager.getArena(name);

                    pendingArenas.put(
                            player.getUniqueId(),
                            arena
                    );

                    player.sendMessage(prefix + color("&aCreated arena " + name + "."));

                    player.sendMessage(color(
                            "&eSelect two corners with your wand, "
                                    + "then use /duels arena setregion."
                    ));

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
                        player.sendMessage(prefix + color("&cCreate an arena first."));
                        return true;
                    }

                    if (!arenaSelection.hasSelection(playerId)) {
                        player.sendMessage(prefix + color("&cSelect both corners first."));
                        return true;
                    }

                    Location pos1 =
                            arenaSelection.getPos1(playerId);

                    Location pos2 =
                            arenaSelection.getPos2(playerId);

                    arena.setCorner1(pos1);
                    arena.setCorner2(pos2);

                    player.sendMessage(prefix + color("&aArena region selected."));
                    return true;
                }

                if (action.equals("setspawn")) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }

                    if (args.length < 3) {
                        player.sendMessage(prefix + color("&cUsage: /duels arena setspawn <a|b>"));
                        return true;
                    }

                    DuelArena arena = pendingArenas.get(
                            player.getUniqueId()
                    );

                    if (arena == null) {
                        player.sendMessage(prefix + color("&cCreate an arena first."));
                        return true;
                    }

                    String team = args[2].toLowerCase(Locale.ROOT);
                    Location location = player.getLocation();

                    if (team.equals("a")) {
                        arena.setTeamASpawn(location);
                    } else if (team.equals("b")) {
                        arena.setTeamBSpawn(location);
                    } else {
                        player.sendMessage(prefix + color("&cUse a or b."));
                        return true;
                    }

                    player.sendMessage(prefix + color(
                            "&aTeam " + team.toUpperCase(Locale.ROOT)
                                    + " spawn saved."
                    ));

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
                        player.sendMessage(prefix + color("&cCreate an arena first."));
                        return true;
                    }

                    arena.setSpectatorSpawn(player.getLocation());

                    player.sendMessage(prefix + color("&aSpectator spawn saved."));
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
                        player.sendMessage(prefix + color("&cCreate an arena first."));
                        return true;
                    }

                    if (!arena.isReady()) {
                        player.sendMessage(prefix + color(
                                "&cArena is incomplete. Set its region, "
                                        + "both team spawns, and spectator spawn."
                        ));
                        return true;
                    }

                    if (!arenaManager.registerArena(arena)) {
                        player.sendMessage(prefix + color("&cCould not register this arena."));
                        return true;
                    }

                    pendingArenas.remove(playerId);
                    arenaSelection.clearSelection(playerId);

                    player.sendMessage(prefix + color("&aArena saved successfully!"));
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
                    player.sendMessage(prefix + color(
                            "&cUsage: /duels join <1v1|2v2|3v3|4v4|5v5>"
                    ));
                    return true;
                }

                String mode = args[1].toLowerCase(Locale.ROOT);

                int teamSize = switch (mode) {
                    case "1v1" -> 1;
                    case "2v2" -> 2;
                    case "3v3" -> 3;
                    case "4v4" -> 4;
                    case "5v5" -> 5;
                    default -> 0;
                };

                if (teamSize == 0) {
                    player.sendMessage(prefix + color(
                            "&cInvalid mode. Use 1v1, 2v2, 3v3, 4v4, or 5v5."
                    ));
                    return true;
                }

                joinQueue(player, teamSize);
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

                    player.sendMessage(getPrefixedMessage(
                            "messages.queue-left",
                            "&aYou left the queue."
                    ));
                    return true;
                }

                player.sendMessage(prefix + color("&cYou are not in a queue."));
                return true;
            }

            case "queue": {
                sender.sendMessage(prefix + color("&b&lǫᴜᴇᴜᴇ sᴛᴀᴛᴜs:"));

                for (int size = 1; size <= 5; size++) {
                    sender.sendMessage(color("&7"
                            + size + "v" + size + ": "
                            + "&f"
                            + matchmaking.getQueueSize(size)
                            + "/" + (size * 2)));
                }

                return true;
            }

            case "status": {
                sender.sendMessage(prefix + color("&b&lᴏʙʟɪᴠɪᴏɴ ᴅᴜᴇʟs sᴛᴀᴛᴜs"));

                sender.sendMessage(color("&7Active matches: &f"
                        + matchManager.getActiveMatchCount()));

                sender.sendMessage(color("&7Players in matches: &f"
                        + matchManager.getActivePlayerCount()));

                sender.sendMessage(color("&7Available arenas: &f"
                        + arenaManager.getAvailableArenaCount(1)
                        + " (1v1)"));

                sender.sendMessage(color("&7Total arenas: &f"
                        + arenaManager.getArenaCount()));

                return true;
            }

            case "reload": {
                if (!isAdmin(sender)) {
                    sender.sendMessage(prefix + color("&cYou don't have permission."));
                    return true;
                }

                reloadConfig();
                updatePrefix();
                arenaManager.reloadArenas();
                matchmakingCoordinator.reloadSettings();

                sender.sendMessage(prefix + color(
                        "&aConfiguration, matchmaking settings, "
                                + "and arena data reloaded safely."
                ));

                return true;
            }

            default: {
                sender.sendMessage(prefix + color(
                        "&cUnknown command. Use /duels help."
                ));

                return true;
            }
        }
    }

    // ==========================================
    // PLACEHOLDER EXPANSION
    // ==========================================

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
