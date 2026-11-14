
package com.oblivion.duels;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
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

        matchmaking = new MatchmakingManager();
        matchManager = new MatchManager();

        arenaSelection = new ArenaSelection(this);
        arenaManager = new ArenaManager(this);

        statsManager = new StatsManager();
        databaseManager = new DatabaseManager(this);

        databaseManager.initialize();
        databaseManager.loadStats(statsManager);

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
        if (sender.hasPermission("oblivionduels.admin")) {
            return true;
        }

        sender.sendMessage(prefix + ChatColor.RED
                + "You don't have permission.");
        return false;
    }

    private void sendArenaHelp(Player player) {
        player.sendMessage(prefix + ChatColor.LIGHT_PURPLE
                + "Arena Setup Commands");
        player.sendMessage(ChatColor.GRAY
                + "/duels create <name>");
        player.sendMessage(ChatColor.GRAY
                + "/duels setregion");
        player.sendMessage(ChatColor.GRAY
                + "/duels setspawn a");
        player.sendMessage(ChatColor.GRAY
                + "/duels setspawn b");
        player.sendMessage(ChatColor.GRAY
                + "/duels setspectator");
        player.sendMessage(ChatColor.GRAY
                + "/duels save");
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
                sender.sendMessage(
                        "Only players can open the duels menu."
                );
            }
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "help":
                sender.sendMessage(prefix + ChatColor.LIGHT_PURPLE
                        + "OblivionDuels Commands");
                sender.sendMessage(ChatColor.GRAY + "/duels");
                sender.sendMessage(ChatColor.GRAY
                        + "/duels join <1v1|2v2|3v3|4v4|5v5>");
                sender.sendMessage(ChatColor.GRAY + "/duels leave");
                sender.sendMessage(ChatColor.GRAY + "/duels queue");
                sender.sendMessage(ChatColor.GRAY + "/duels wand");
                sender.sendMessage(ChatColor.GRAY + "/duels arena help");
                sender.sendMessage(ChatColor.GRAY + "/duels status");
                sender.sendMessage(ChatColor.GRAY + "/duels reload");
                return true;

            case "arena":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(
                            "Only players can use arena commands."
                    );
                    return true;
                }

                if (!isAdmin(player)) {
                    return true;
                }

                sendArenaHelp(player);
                return true;

            case "create":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can create arenas.");
                    return true;
                }

                if (!isAdmin(player)) {
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Usage: /duels create <name>");
                    return true;
                }

                String arenaName = args[1];

                if (arenaManager.getArena(arenaName) != null) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "An arena with that name already exists.");
                    return true;
                }

                DuelArena draft = new DuelArena(
                        arenaName,
                        player.getWorld()
                );

                pendingArenas.put(player.getUniqueId(), draft);

                player.sendMessage(prefix + ChatColor.GREEN
                        + "Arena draft '" + arenaName + "' created.");
                player.sendMessage(ChatColor.GRAY
                        + "Select two positions using /duels wand,");
                player.sendMessage(ChatColor.GRAY
                        + "then use /duels setregion.");

                return true;

            case "setregion":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can set regions.");
                    return true;
                }

                if (!isAdmin(player)) {
                    return true;
                }

                DuelArena regionArena =
                        pendingArenas.get(player.getUniqueId());

                if (regionArena == null) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Create an arena first.");
                    return true;
                }

                UUID playerId = player.getUniqueId();

                if (!arenaSelection.hasSelection(playerId)) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Select both positions using your arena wand.");
                    return true;
                }

                Location pos1 = arenaSelection.getPos1(playerId);
                Location pos2 = arenaSelection.getPos2(playerId);

                if (pos1 == null || pos2 == null
                        || pos1.getWorld() == null
                        || pos2.getWorld() == null
                        || !pos1.getWorld().equals(pos2.getWorld())
                        || !pos1.getWorld().getUID().equals(
                                regionArena.getWorldId())) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Both positions must be in the arena's world.");
                    return true;
                }

                regionArena.setCorner1(pos1);
                regionArena.setCorner2(pos2);

                player.sendMessage(prefix + ChatColor.GREEN
                        + "Arena boundaries assigned successfully.");
                return true;

            case "setspawn":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can set spawns.");
                    return true;
                }

                if (!isAdmin(player)) {
                    return true;
                }

                DuelArena spawnArena =
                        pendingArenas.get(player.getUniqueId());

                if (spawnArena == null) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Create an arena first.");
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Usage: /duels setspawn <a|b>");
                    return true;
                }

                if (!player.getWorld().getUID().equals(
                        spawnArena.getWorldId())) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "You must be in the arena's world.");
                    return true;
                }

                if (args[1].equalsIgnoreCase("a")) {
                    spawnArena.setTeamASpawn(player.getLocation());

                    player.sendMessage(prefix + ChatColor.GREEN
                            + "Team A spawn set.");

                } else if (args[1].equalsIgnoreCase("b")) {
                    spawnArena.setTeamBSpawn(player.getLocation());

                    player.sendMessage(prefix + ChatColor.GREEN
                            + "Team B spawn set.");

                } else {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Use a or b.");
                }

                return true;

            case "setspectator":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(
                            "Only players can set spectator spawns."
                    );
                    return true;
                }

                if (!isAdmin(player)) {
                    return true;
                }

                DuelArena spectatorArena =
                        pendingArenas.get(player.getUniqueId());

                if (spectatorArena == null) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Create an arena first.");
                    return true;
                }

                if (!player.getWorld().getUID().equals(
                        spectatorArena.getWorldId())) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "You must be in the arena's world.");
                    return true;
                }

                spectatorArena.setSpectatorSpawn(
                        player.getLocation()
                );

                player.sendMessage(prefix + ChatColor.GREEN
                        + "Spectator spawn set.");
                return true;

            case "save":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can save arenas.");
                    return true;
                }

                if (!isAdmin(player)) {
                    return true;
                }

                DuelArena saveArena =
                        pendingArenas.get(player.getUniqueId());

                if (saveArena == null) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "You don't have an arena draft.");
                    return true;
                }

                if (!saveArena.isReady()) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Arena is incomplete. Set its region,");
                    player.sendMessage(ChatColor.GRAY
                            + "Team A spawn, Team B spawn, and spectator spawn.");
                    return true;
                }

                if (!arenaManager.registerArena(saveArena)) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Could not save arena.");
                    return true;
                }

                pendingArenas.remove(player.getUniqueId());
                arenaSelection.clearSelection(player.getUniqueId());

                player.sendMessage(prefix + ChatColor.GREEN
                        + "Arena '" + saveArena.getName()
                        + "' saved successfully!");

                player.sendMessage(ChatColor.GRAY
                        + "Total arenas: " + arenaManager.getArenaCount());
                return true;

            case "wand":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(
                            "Only players can use this command."
                    );
                    return true;
                }

                if (!isAdmin(player)) {
                    return true;
                }

                player.getInventory().addItem(
                        arenaSelection.createWand()
                );

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
                    String[] parts = args[1].toLowerCase().split("v");

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

                if (matchManager.isInMatch(player.getUniqueId())) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "You are already in a match.");
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
                    sender.sendMessage(
                            "Only players can check their queue."
                    );
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
                sender.sendMessage(ChatColor.GRAY
                        + "Database: "
                        + (databaseManager.isConnected()
                        ? "Connected" : "Disconnected"));
                sender.sendMessage(ChatColor.GRAY
                        + "Arenas loaded: "
                        + arenaManager.getArenaCount());
                sender.sendMessage(ChatColor.GRAY
                        + "Active matches: "
                        + matchManager.getActiveMatchCount());
                return true;

            case "reload":
                if (!isAdmin(sender)) {
                    return true;
                }

                reloadConfig();
                arenaManager.loadArenas();

                sender.sendMessage(prefix + ChatColor.GREEN
                        + "Configuration and arenas reloaded.");
                return true;

            default:
                sender.sendMessage(prefix + ChatColor.RED
                        + "Unknown command. Use /duels help.");
                return true;
        }
    }
}
st be in the arena's world.");
                    return true;
                }

                if (args[1].equalsIgnoreCase("a")) {
                    spawnArena.setTeamASpawn(player.getLocation());

                    player.sendMessage(prefix + ChatColor.GREEN
                            + "Team A spawn set.");

                } else if (args[1].equalsIgnoreCase("b")) {
                    spawnArena.setTeamBSpawn(player.getLocation());

                    player.sendMessage(prefix + ChatColor.GREEN
                            + "Team B spawn set.");

                } else {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Use a or b.");
                }

                return true;

            case "setspectator":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(
                            "Only players can set spectator spawns."
                    );
                    return true;
                }

                if (!isAdmin(player)) {
                    return true;
                }

                DuelArena spectatorArena =
                        pendingArenas.get(player.getUniqueId());

                if (spectatorArena == null) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Create an arena first.");
                    return true;
                }

                if (!player.getWorld().getUID().equals(
                        spectatorArena.getWorldId())) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "You must be in the arena's world.");
                    return true;
                }

                spectatorArena.setSpectatorSpawn(
                        player.getLocation()
                );

                player.sendMessage(prefix + ChatColor.GREEN
                        + "Spectator spawn set.");
                return true;

            case "save":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can save arenas.");
                    return true;
                }

                if (!isAdmin(player)) {
                    return true;
                }

                DuelArena saveArena =
                        pendingArenas.get(player.getUniqueId());

                if (saveArena == null) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "You don't have an arena draft.");
                    return true;
                }

                if (!saveArena.isReady()) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Arena is incomplete. Set its region,");
                    player.sendMessage(ChatColor.GRAY
                            + "Team A spawn, Team B spawn, and spectator spawn.");
                    return true;
                }

                if (!arenaManager.registerArena(saveArena)) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Could not save arena.");
                    return true;
                }

                pendingArenas.remove(player.getUniqueId());
                arenaSelection.clearSelection(player.getUniqueId());

                player.sendMessage(prefix + ChatColor.GREEN
                        + "Arena '" + saveArena.getName()
                        + "' saved successfully!");

                player.sendMessage(ChatColor.GRAY
                        + "Total arenas: " + arenaManager.getArenaCount());
                return true;

            case "wand":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(
                            "Only players can use this command."
                    );
                    return true;
                }

                if (!isAdmin(player)) {
                    return true;
                }

                player.getInventory().addItem(
                        arenaSelection.createWand()
                );

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
                    String[] parts = args[1].toLowerCase().split("v");

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

                if (matchManager.isInMatch(player.getUniqueId())) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "You are already in a match.");
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
                    sender.sendMessage(
                            "Only players can check their queue."
                    );
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
                sender.sendMessage(ChatColor.GRAY
                        + "Database: "
                        + (databaseManager.isConnected()
                        ? "Connected" : "Disconnected"));
                sender.sendMessage(ChatColor.GRAY
                        + "Arenas loaded: "
                        + arenaManager.getArenaCount());
                sender.sendMessage(ChatColor.GRAY
                        + "Active matches: "
                        + matchManager.getActiveMatchCount());
                return true;

            case "reload":
                if (!isAdmin(sender)) {
                    return true;
                }

                reloadConfig();
                arenaManager.loadArenas();

                sender.sendMessage(prefix + ChatColor.GREEN
                        + "Configuration and arenas reloaded.");
                return true;

            default:
                sender.sendMessage(prefix + ChatColor.RED
                        + "Unknown command. Use /duels help.");
                return true;
        }
    }
}
             return true;

            case "setspectator":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(
                            "Only players can set spectator spawns."
                    );
                    return true;
                }

                if (!isAdmin(player)) {
                    return true;
                }

                DuelArena spectatorArena =
                        pendingArenas.get(player.getUniqueId());

                if (spectatorArena == null) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Create an arena first.");
                    return true;
                }

                if (!player.getWorld().getUID().equals(
                        spectatorArena.getWorldId())) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "You must be in the arena's world.");
                    return true;
                }

                spectatorArena.setSpectatorSpawn(
                        player.getLocation()
                );

                player.sendMessage(prefix + ChatColor.GREEN
                        + "Spectator spawn set.");

                return true;

            case "save":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can save arenas.");
                    return true;
                }

                if (!isAdmin(player)) {
                    return true;
                }

                DuelArena saveArena =
                        pendingArenas.get(player.getUniqueId());

                if (saveArena == null) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "You don't have an arena draft.");
                    return true;
                }

                if (!saveArena.isReady()) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Arena is incomplete. Set its region,");
                    player.sendMessage(ChatColor.GRAY
                            + "Team A spawn, Team B spawn, and spectator spawn.");
                    return true;
                }

                if (!arenaManager.registerArena(saveArena)) {
                    player.sendMessage(prefix + ChatColor.RED
                            + "Could not save arena. Check its name.");
                    return true;
                }

                pendingArenas.remove(player.getUniqueId());

                arenaSelection.clearSelection(player.getUniqueId());

                player.sendMessage(prefix + ChatColor.GREEN
                        + "Arena '" + saveArena.getName()
                        + "' saved successfully!");

                player.sendMessage(ChatColor.GRAY
                        + "Total arenas: " + arenaManager.getArenaCount());

                return true;

            case "wand":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(
                            "Only players can use this command."
                    );
                    return true;
                }

                if (!isAdmin(player)) {
                    return true;
                }

                player.getInventory().addItem(
                        arenaSelection.createWand()
                );

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
                    sender.sendMessage(
                            "Only players can check their queue."
                    );
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
                sender.sendMessage(ChatColor.GRAY
                        + "Database: "
                        + (databaseManager.isConnected()
                        ? "Connected" : "Disconnected"));
                sender.sendMessage(ChatColor.GRAY
                        + "Arenas loaded: "
                        + arenaManager.getArenaCount());
                return true;

            case "reload":
                if (!isAdmin(sender)) {
                    return true;
                }

                reloadConfig();
                arenaManager.loadArenas();

                sender.sendMessage(prefix + ChatColor.GREEN
                        + "Configuration and arenas reloaded.");
                return true;

            default:
                sender.sendMessage(prefix + ChatColor.RED
                        + "Unknown command. Use /duels help.");
                return true;
        }
    }
}
