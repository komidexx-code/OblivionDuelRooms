
package com.oblivion.duels;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
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

    private static final String BLUE = "&#0062FF";
    private static final String SECONDARY = "&#3185FF";
    private static final String INFO = "&#65A6FF";
    private static final String DESCRIPTION = "&#A5CCFF";

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

    private final Map<UUID, String> pendingArenaDeletions =
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
        prefix = color(
                "&#0062FF[ᴏʙʟɪᴠɪᴏɴᴅᴜᴇʟs] "
        );
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

    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(prefix + color(message));
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
        sender.sendMessage(color(BLUE + "━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(color(BLUE + "ᴏʙʟɪᴠɪᴏɴ ᴅᴜᴇʟs &8» " + SECONDARY + "ᴀʀᴇɴᴀ ʜᴇʟᴘ"));
        sender.sendMessage(color(BLUE + "━━━━━━━━━━━━━━━━━━━━━━━━"));

        sender.sendMessage(color(INFO + "ᴀʀᴇɴᴀ sᴇᴛᴜᴘ ᴏʀᴅᴇʀ"));
        sender.sendMessage(color(DESCRIPTION + "1. " + SECONDARY + "/duels arena create <name>"));
        sender.sendMessage(color(DESCRIPTION + "2. " + SECONDARY + "/duels arena setsize <name> <1-5>"));
        sender.sendMessage(color(DESCRIPTION + "3. " + SECONDARY + "/duels arena wand"));
        sender.sendMessage(color(DESCRIPTION + "4. " + SECONDARY + "Select both corners with the wand."));
        sender.sendMessage(color(DESCRIPTION + "5. " + SECONDARY + "/duels arena setregion"));
        sender.sendMessage(color(DESCRIPTION + "6. " + SECONDARY + "/duels arena setspawn a"));
        sender.sendMessage(color(DESCRIPTION + "7. " + SECONDARY + "/duels arena setspawn b"));
        sender.sendMessage(color(DESCRIPTION + "8. " + SECONDARY + "/duels arena setspectator"));
        sender.sendMessage(color(DESCRIPTION + "9. " + SECONDARY + "/duels arena save"));

        sender.sendMessage(color(INFO + "ᴏᴘᴛɪᴏɴᴀʟ"));
        sender.sendMessage(color(DESCRIPTION + "/duels setexit <arena>"));

        sender.sendMessage(color(INFO + "ᴀʀᴇɴᴀ ᴍᴀɴᴀɢᴇᴍᴇɴᴛ"));
        sender.sendMessage(color(DESCRIPTION + "/duels arena list"));
        sender.sendMessage(color(DESCRIPTION + "/duels arena info <name>"));
        sender.sendMessage(color(DESCRIPTION + "/duels arena delete <name>"));

        sender.sendMessage(color(INFO + "ᴅᴇʟᴇᴛɪᴏɴ"));
        sender.sendMessage(color(DESCRIPTION + "/duels arena delete confirm"));
        sender.sendMessage(color(DESCRIPTION + "/duels arena delete cancel"));

        sender.sendMessage(color(BLUE + "━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    // ==========================================
    // ARENA LIST
    // ==========================================

    private void sendArenaList(CommandSender sender) {
        sender.sendMessage(color(BLUE + "━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(color(BLUE + "ᴏʙʟɪᴠɪᴏɴ ᴅᴜᴇʟs &8» " + SECONDARY + "ᴀʀᴇɴᴀs"));
        sender.sendMessage(color(BLUE + "━━━━━━━━━━━━━━━━━━━━━━━━"));

        if (arenaManager.getArenaNames().isEmpty()) {
            sender.sendMessage(color(INFO + "No registered arenas found."));
            sender.sendMessage(color(DESCRIPTION + "Create one using /duels arena create <name>."));
            sender.sendMessage(color(BLUE + "━━━━━━━━━━━━━━━━━━━━━━━━"));
            return;
        }

        for (String name : arenaManager.getArenaNames()) {
            DuelArena arena = arenaManager.getArena(name);

            if (arena == null) {
                continue;
            }

            String status = arenaManager.isOccupied(name)
                    ? "&#FF5555ᴏᴄᴄᴜᴘɪᴇᴅ"
                    : "&#55FFAAᴀᴠᴀɪʟᴀʙʟᴇ";

            sender.sendMessage(color(
                    SECONDARY + "• " + INFO + arena.getName()
                            + " " + DESCRIPTION + "("
                            + arena.getTeamSize() + "v"
                            + arena.getTeamSize() + ") "
                            + status
            ));
        }

        sender.sendMessage(color(BLUE + "━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(color(DESCRIPTION + "Total: " + INFO + arenaManager.getArenaCount()));
    }

    // ==========================================
    // ARENA INFO
    // ==========================================

    private String locationStatus(Location location) {
        return location == null
                ? "&#FF5555ɴᴏᴛ sᴇᴛ"
                : "&#55FFAA sᴇᴛ";
    }

    private void sendArenaInfo(CommandSender sender, String name) {
        DuelArena arena = arenaManager.getArena(name);

        if (arena == null) {
            sendMessage(sender, "&cArena not found: " + name);
            return;
        }

        World world = Bukkit.getWorld(arena.getWorldId());

        String status = arenaManager.isOccupied(name)
                ? "&#FF5555ᴏᴄᴄᴜᴘɪᴇᴅ"
                : arena.isReady()
                ? "&#55FFAAʀᴇᴀᴅʏ"
                : "&#FFAA55ɪɴᴄᴏᴍᴘʟᴇᴛᴇ";

        sender.sendMessage(color(BLUE + "━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(color(BLUE + "ᴏʙʟɪᴠɪᴏɴ ᴅᴜᴇʟs &8» " + SECONDARY + "ᴀʀᴇɴᴀ ɪɴғᴏ"));
        sender.sendMessage(color(BLUE + "━━━━━━━━━━━━━━━━━━━━━━━━"));

        sender.sendMessage(color(INFO + "ɴᴀᴍᴇ: " + DESCRIPTION + arena.getName()));
        sender.sendMessage(color(INFO + "ᴡᴏʀʟᴅ: " + DESCRIPTION
                + (world == null ? "Unknown" : world.getName())));
        sender.sendMessage(color(INFO + "ᴍᴏᴅᴇ: " + DESCRIPTION
                + arena.getTeamSize() + "v" + arena.getTeamSize()));
        sender.sendMessage(color(INFO + "sᴛᴀᴛᴜs: " + status));

        sender.sendMessage(color(SECONDARY + "ʟᴏᴄᴀᴛɪᴏɴs"));
        sender.sendMessage(color(DESCRIPTION + "Region: " + locationStatus(arena.getCorner1())
                + " / " + locationStatus(arena.getCorner2())));
        sender.sendMessage(color(DESCRIPTION + "Team A spawn: "
                + locationStatus(arena.getTeamASpawn())));
        sender.sendMessage(color(DESCRIPTION + "Team B spawn: "
                + locationStatus(arena.getTeamBSpawn())));
        sender.sendMessage(color(DESCRIPTION + "Spectator spawn: "
                + locationStatus(arena.getSpectatorSpawn())));
        sender.sendMessage(color(DESCRIPTION + "Exit spawn: "
                + locationStatus(arena.getExitSpawn())));

        sender.sendMessage(color(BLUE + "━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    // ==========================================
    // ARENA DELETE CONFIRMATION
    // ==========================================

    private void handleArenaDelete(
            CommandSender sender,
            String[] args
    ) {
        if (args.length < 3) {
            sendMessage(sender, "&cUsage: /duels arena delete <name>");
            return;
        }

        String target = args[2].toLowerCase(Locale.ROOT);

        if (target.equals("cancel")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return;
            }

            String pending = pendingArenaDeletions.remove(
                    player.getUniqueId()
            );

            if (pending == null) {
                sendMessage(sender, "&cYou have no pending arena deletion.");
                return;
            }

            sendMessage(sender, "&aArena deletion cancelled.");
            return;
        }

        if (target.equals("confirm")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return;
            }

            UUID playerId = player.getUniqueId();

            String pending = pendingArenaDeletions.remove(playerId);

            if (pending == null) {
                sendMessage(sender, "&cYou have no pending arena deletion.");
                return;
            }

            DuelArena arena = arenaManager.getArena(pending);

            if (arena == null) {
                sendMessage(sender, "&cThat arena no longer exists.");
                return;
            }

            if (arenaManager.isOccupied(arena.getName())) {
                sendMessage(sender, "&cYou cannot delete an occupied arena.");
                return;
            }

            if (!arenaManager.deleteArena(arena.getName())) {
                sendMessage(sender, "&cCould not delete that arena.");
                return;
            }

            pendingArenas.entrySet().removeIf(
                    entry -> entry.getValue() == arena
            );

            sendMessage(sender, "&aArena &f" + arena.getName()
                    + " &ahas been deleted.");

            return;
        }

        String arenaName = args[2];
        DuelArena arena = arenaManager.getArena(arenaName);

        if (arena == null) {
            sendMessage(sender, "&cArena not found: " + arenaName);
            return;
        }

        if (arenaManager.isOccupied(arena.getName())) {
            sendMessage(sender, "&cThat arena is currently occupied.");
            return;
        }

        if (sender instanceof Player player) {
            pendingArenaDeletions.put(
                    player.getUniqueId(),
                    arena.getName()
            );
        } else {
            sendMessage(sender, "&cDeletion confirmation requires a player.");
            return;
        }

        sender.sendMessage(color(BLUE + "━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(color("&#FF5555ᴄᴏɴғɪʀᴍ ᴀʀᴇɴᴀ ᴅᴇʟᴇᴛɪᴏɴ"));
        sender.sendMessage(color(DESCRIPTION + "Arena: " + INFO + arena.getName()));
        sender.sendMessage(color(DESCRIPTION + "This action cannot be undone."));
        sender.sendMessage(color(DESCRIPTION + "Type " + SECONDARY
                + "/duels arena delete confirm"));
        sender.sendMessage(color(DESCRIPTION + "or " + SECONDARY
                + "/duels arena delete cancel"));
        sender.sendMessage(color(BLUE + "━━━━━━━━━━━━━━━━━━━━━━━━"));
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

            sender.sendMessage(color(BLUE + "━━━━━━━━━━━━━━━━━━━━━━━━"));
            sender.sendMessage(color(BLUE + "ᴏʙʟɪᴠɪᴏɴ ᴅᴜᴇʟs ᴄᴏᴍᴍᴀɴᴅs"));
            sender.sendMessage(color(DESCRIPTION + "/duels menu"));
            sender.sendMessage(color(DESCRIPTION + "/duels join <1v1|2v2|3v3|4v4|5v5>"));
            sender.sendMessage(color(DESCRIPTION + "/duels leave"));
            sender.sendMessage(color(DESCRIPTION + "/duels queue"));
            sender.sendMessage(color(DESCRIPTION + "/duels status"));
            sender.sendMessage(color(DESCRIPTION + "/duels arena help"));
            sender.sendMessage(color(DESCRIPTION + "/duels setexit <arena>"));
            sender.sendMessage(color(DESCRIPTION + "/duels reload"));
            sender.sendMessage(color(BLUE + "━━━━━━━━━━━━━━━━━━━━━━━━"));

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
                    sendMessage(sender, "&cYou don't have permission.");
                    return true;
                }

                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }

                if (args.length < 2) {
                    sendMessage(player, "&cUsage: /duels setexit <arena>");
                    return true;
                }

                DuelArena arena = arenaManager.getArena(args[1]);

                if (arena == null) {
                    sendMessage(player, "&cArena not found: " + args[1]);
                    return true;
                }

                arena.setExitSpawn(player.getLocation());
                arenaManager.saveArena(arena);

                sendMessage(player, "&aExit spawn saved for arena "
                        + arena.getName() + "!");

                return true;
            }

            case "arena": {
                if (!isAdmin(sender)) {
                    sendMessage(sender, "&cYou don't have permission.");
                    return true;
                }

                if (args.length == 1) {
                    sendArenaHelp(sender);
                    return true;
                }

                String action = args[1].toLowerCase(Locale.ROOT);

                if (action.equals("help")) {
                    sendArenaHelp(sender);
                    return true;
                }

                if (action.equals("list")) {
                    sendArenaList(sender);
                    return true;
                }

                if (action.equals("info")) {
                    if (args.length < 3) {
                        sendMessage(sender, "&cUsage: /duels arena info <name>");
                        return true;
                    }

                    sendArenaInfo(sender, args[2]);
                    return true;
                }

                if (action.equals("setsize")) {
                    if (args.length < 4) {
                        sendMessage(sender,
                                "&cUsage: /duels arena setsize <name> <1-5>"
                        );
                        return true;
                    }

                    String arenaName = args[2];
                    int teamSize;

                    try {
                        teamSize = Integer.parseInt(args[3]);
                    } catch (NumberFormatException exception) {
                        sendMessage(sender,
                                "&cInvalid team size. Use a number from 1 to 5."
                        );
                        return true;
                    }

                    if (!arenaManager.isValidTeamSize(teamSize)) {
                        sendMessage(sender,
                                "&cTeam size must be between 1 and 5."
                        );
                        return true;
                    }

                    DuelArena arena = arenaManager.getArena(arenaName);

                    if (arena == null) {
                        sendMessage(sender,
                                "&cArena not found: " + arenaName
                        );
                        return true;
                    }

                    if (arenaManager.isOccupied(arenaName)) {
                        sendMessage(sender,
                                "&cYou cannot change the size of an occupied arena."
                        );
                        return true;
                    }

                    if (!arenaManager.setArenaTeamSize(
                            arenaName,
                            teamSize
                    )) {
                        sendMessage(sender,
                                "&cCould not update the arena size."
                        );
                        return true;
                    }

                    sendMessage(sender,
                            "&aArena &f" + arena.getName()
                                    + " &ais now configured for &f"
                                    + teamSize + "v" + teamSize + "&a."
                    );

                    return true;
                }

                if (action.equals("delete")) {
                    handleArenaDelete(sender, args);
                    return true;
                }

                if (action.equals("wand")) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }

                    player.getInventory().addItem(
                            arenaSelection.createWand()
                    );

                    sendMessage(player, "&aArena selection wand given.");
                    return true;
                }

                if (action.equals("create")) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }

                    if (args.length < 3) {
                        sendMessage(player, "&cUsage: /duels arena create <name>");
                        return true;
                    }

                    String name = args[2];

                    if (!arenaManager.createArena(
                            name,
                            player.getWorld()
                    )) {
                        sendMessage(player, "&cCould not create that arena.");
                        return true;
                    }

                    DuelArena arena = arenaManager.getArena(name);

                    pendingArenas.put(
                            player.getUniqueId(),
                            arena
                    );

                    sendMessage(player, "&aCreated arena " + name + ".");

                    player.sendMessage(color(
                            DESCRIPTION + "Next: set its size with "
                                    + SECONDARY
                                    + "/duels arena setsize "
                                    + name + " <1-5>"
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
                        sendMessage(player, "&cCreate an arena first.");
                        return true;
                    }

                    if (!arenaSelection.hasSelection(playerId)) {
                        sendMessage(player, "&cSelect both corners first.");
                        return true;
                    }

                    Location pos1 =
                            arenaSelection.getPos1(playerId);

                    Location pos2 =
                            arenaSelection.getPos2(playerId);

                    arena.setCorner1(pos1);
                    arena.setCorner2(pos2);

                    sendMessage(player, "&aArena region selected.");
                    return true;
                }

                if (action.equals("setspawn")) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }

                    if (args.length < 3) {
                        sendMessage(player, "&cUsage: /duels arena setspawn <a|b>");
                        return true;
                    }

                    DuelArena arena = pendingArenas.get(
                            player.getUniqueId()
                    );

                    if (arena == null) {
                        sendMessage(player, "&cCreate an arena first.");
                        return true;
                    }

                    String team = args[2].toLowerCase(Locale.ROOT);
                    Location location = player.getLocation();

                    if (team.equals("a")) {
                        arena.setTeamASpawn(location);
                    } else if (team.equals("b")) {
                        arena.setTeamBSpawn(location);
                    } else {
                        sendMessage(player, "&cUse a or b.");
                        return true;
                    }

                    sendMessage(player, "&aTeam "
                            + team.toUpperCase(Locale.ROOT)
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
                        sendMessage(player, "&cCreate an arena first.");
                        return true;
                    }

                    arena.setSpectatorSpawn(player.getLocation());

                    sendMessage(player, "&aSpectator spawn saved.");
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
                        sendMessage(player, "&cCreate an arena first.");
                        return true;
                    }

                    if (!arena.isReady()) {
                        sendMessage(player,
                                "&cArena is incomplete. Set its region, "
                                        + "both team spawns, and spectator spawn."
                        );
                        return true;
                    }

                    if (!arenaManager.registerArena(arena)) {
                        sendMessage(player, "&cCould not register this arena.");
                        return true;
                    }

                    pendingArenas.remove(playerId);
                    arenaSelection.clearSelection(playerId);

                    sendMessage(player, "&aArena saved successfully!");
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
                    sendMessage(player,
                            "&cUsage: /duels join <1v1|2v2|3v3|4v4|5v5>"
                    );
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
                    sendMessage(player,
                            "&cInvalid mode. Use 1v1, 2v2, 3v3, 4v4, or 5v5."
                    );
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

                sendMessage(player, "&cYou are not in a queue.");
                return true;
            }

            case "queue": {
                sender.sendMessage(color(BLUE + "━━━━━━━━━━━━━━━━━━━━━━━━"));
                sender.sendMessage(color(BLUE + "ᴏʙʟɪᴠɪᴏɴ ᴅᴜᴇʟs &8» " + SECONDARY + "ǫᴜᴇᴜᴇ sᴛᴀᴛᴜs"));

                for (int size = 1; size <= 5; size++) {
                    sender.sendMessage(color(INFO
                            + size + "v" + size + ": "
                            + DESCRIPTION
                            + matchmaking.getQueueSize(size)
                            + "/" + (size * 2)));
                }

                sender.sendMessage(color(BLUE + "━━━━━━━━━━━━━━━━━━━━━━━━"));
                return true;
            }

            case "status": {
                sender.sendMessage(color(BLUE + "━━━━━━━━━━━━━━━━━━━━━━━━"));
                sender.sendMessage(color(BLUE + "ᴏʙʟɪᴠɪᴏɴ ᴅᴜᴇʟs sᴛᴀᴛᴜs"));

                sender.sendMessage(color(INFO + "Active matches: "
                        + DESCRIPTION + matchManager.getActiveMatchCount()));

                sender.sendMessage(color(INFO + "Players in matches: "
                        + DESCRIPTION + matchManager.getActivePlayerCount()));

                sender.sendMessage(color(INFO + "Available arenas: "
                        + DESCRIPTION + arenaManager.getAvailableArenaCount(1)
                        + " (1v1)"));

                sender.sendMessage(color(INFO + "Total arenas: "
                        + DESCRIPTION + arenaManager.getArenaCount()));

                sender.sendMessage(color(BLUE + "━━━━━━━━━━━━━━━━━━━━━━━━"));
                return true;
            }

            case "reload": {
                if (!isAdmin(sender)) {
                    sendMessage(sender, "&cYou don't have permission.");
                    return true;
                }

                reloadConfig();
                updatePrefix();
                arenaManager.reloadArenas();
                matchmakingCoordinator.reloadSettings();

                sendMessage(sender,
                        "&aConfiguration, matchmaking settings, "
                                + "and arena data reloaded safely."
                );

                return true;
            }

            default: {
                sendMessage(sender, "&cUnknown command. Use /duels help.");
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
