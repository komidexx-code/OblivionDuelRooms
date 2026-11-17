
package com.oblivion.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

public final class MatchDeathListener implements Listener {

    private final JavaPlugin plugin;
    private final MatchManager matchManager;
    private final ArenaManager arenaManager;
    private final ArenaRollbackManager rollbackManager;
    private final StatsManager statsManager;
    private final DatabaseManager databaseManager;

    private final Map<UUID, Location> spectatorSpawns = new HashMap<>();
    private final Map<UUID, Location> exitSpawns = new HashMap<>();

    private final Map<UUID, Map<UUID, List<ItemStack>>> matchLoot =
            new HashMap<>();

    private final Map<UUID, List<ItemStack>> pendingLoot = new HashMap<>();

    private final Map<UUID, InventorySnapshot> disconnectedInventories =
            new HashMap<>();

    private final Map<UUID, Map<UUID, InventorySnapshot>>
            startingInventories = new HashMap<>();

    private final Map<UUID, InventorySnapshot> pendingStartingInventories =
            new HashMap<>();

    private final Set<UUID> eliminatedDisconnectedPlayers = new HashSet<>();

    private final Map<UUID, BukkitTask> disconnectTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> timeoutTasks = new HashMap<>();

    private final Map<UUID, BukkitTask> hudTasks = new HashMap<>();
    private final Map<UUID, BossBar> matchBossBars = new HashMap<>();

    private final Map<UUID, Scoreboard> matchScoreboards = new HashMap<>();
    private final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();

    private final Map<UUID, Integer> resolvedRounds = new HashMap<>();

    // One cached shared inventory per match.
    private final Map<UUID, Inventory> liveLootInventories =
            new HashMap<>();

    public MatchDeathListener(
            JavaPlugin plugin,
            MatchManager matchManager,
            ArenaManager arenaManager,
            StatsManager statsManager,
            DatabaseManager databaseManager
    ) {
        this.plugin = plugin;
        this.matchManager = matchManager;
        this.arenaManager = arenaManager;
        this.statsManager = statsManager;
        this.databaseManager = databaseManager;

        this.rollbackManager =
                plugin instanceof OblivionDuels duels
                        ? duels.getRollbackManager()
                        : null;

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // =========================================================
    // STARTING INVENTORY MANAGEMENT
    // =========================================================

    public void captureStartingInventories(DuelMatch match) {

        if (match == null) {
            return;
        }

        UUID matchId = match.getMatchId();

        Map<UUID, InventorySnapshot> snapshots =
                startingInventories.computeIfAbsent(
                        matchId,
                        key -> new HashMap<>()
                );

        for (UUID playerId : match.getTeamA()) {
            captureStartingInventory(playerId, snapshots);
        }

        for (UUID playerId : match.getTeamB()) {
            captureStartingInventory(playerId, snapshots);
        }
    }

    private void captureStartingInventory(
            UUID playerId,
            Map<UUID, InventorySnapshot> snapshots
    ) {

        if (snapshots.containsKey(playerId)) {
            return;
        }

        Player player = Bukkit.getPlayer(playerId);

        if (player != null && player.isOnline()) {
            snapshots.put(
                    playerId,
                    InventorySnapshot.capture(player)
            );
        }
    }

    public void restoreStartingInventories(DuelMatch match) {

        if (match == null) {
            return;
        }

        Map<UUID, InventorySnapshot> snapshots =
                startingInventories.get(match.getMatchId());

        if (snapshots == null) {
            return;
        }

        for (UUID playerId : match.getTeamA()) {
            restoreStartingInventory(playerId, snapshots);
        }

        for (UUID playerId : match.getTeamB()) {
            restoreStartingInventory(playerId, snapshots);
        }
    }

    private void restoreStartingInventory(
            UUID playerId,
            Map<UUID, InventorySnapshot> snapshots
    ) {

        Player player = Bukkit.getPlayer(playerId);
        InventorySnapshot snapshot = snapshots.get(playerId);

        if (player == null || !player.isOnline() || snapshot == null) {
            return;
        }

        snapshot.restore(player);
    }

    // =========================================================
    // MATCH TIMER
    // =========================================================

    public void startMatchTimeout(DuelMatch match) {

        if (match == null
                || match.getState() != DuelMatch.MatchState.ACTIVE) {
            return;
        }

        UUID matchId = match.getMatchId();

        BukkitTask previous = timeoutTasks.remove(matchId);

        if (previous != null) {
            previous.cancel();
        }

        int minutes = Math.max(
                1,
                plugin.getConfig().getInt(
                        "matchmaking.match-timeout-minutes",
                        15
                )
        );

        long durationTicks = minutes * 60L * 20L;
        long endTimeMillis =
                System.currentTimeMillis() + minutes * 60_000L;

        startMatchHud(match, durationTicks, endTimeMillis);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {

                    timeoutTasks.remove(matchId);

                    if (match.getState()
                            != DuelMatch.MatchState.ACTIVE) {
                        return;
                    }

                    DuelMatch.WinningTeam winner;

                    if (match.getTeamAScore() > match.getTeamBScore()) {
                        winner = DuelMatch.WinningTeam.TEAM_A;
                    } else if (match.getTeamBScore()
                            > match.getTeamAScore()) {
                        winner = DuelMatch.WinningTeam.TEAM_B;
                    } else {
                        winner = DuelMatch.WinningTeam.DRAW;
                    }

                    String arenaName =
                            matchManager.getArenaForMatch(matchId);

                    if (winner == DuelMatch.WinningTeam.DRAW) {
                        finishMatch(
                                match,
                                arenaName,
                                ChatColor.YELLOW
                                        + "Match timed out! The series is tied.",
                                winner
                        );
                    } else {
                        String winningTeam =
                                winner == DuelMatch.WinningTeam.TEAM_A
                                        ? "Team A" : "Team B";

                        finishMatch(
                                match,
                                arenaName,
                                ChatColor.GREEN
                                        + "Match timed out! "
                                        + winningTeam + " wins the series!",
                                winner
                        );
                    }
                },
                durationTicks
        );

        timeoutTasks.put(matchId, task);
    }

    // =========================================================
    // HUD SETUP
    // =========================================================

    private boolean isHudEnabled() {
        return plugin.getConfig().getBoolean(
                "match-hud.enabled",
                true
        );
    }

    private void startMatchHud(
            DuelMatch match,
            long durationTicks,
            long endTimeMillis
    ) {

        if (!isHudEnabled()) {
            return;
        }

        UUID matchId = match.getMatchId();

        if (plugin.getConfig().getBoolean(
                "match-hud.timer.enabled",
                true
        )) {

            BarColor barColor;

            try {
                barColor = BarColor.valueOf(
                        plugin.getConfig().getString(
                                "match-hud.timer.color",
                                "PURPLE"
                        ).toUpperCase(Locale.ROOT)
                );
            } catch (IllegalArgumentException exception) {
                barColor = BarColor.PURPLE;
            }

            BossBar bossBar = Bukkit.createBossBar(
                    format(
                            plugin.getConfig().getString(
                                    "match-hud.timer.title",
                                    "&5&lMATCH TIME"
                            )
                    ),
                    barColor,
                    BarStyle.SOLID
            );

            bossBar.setVisible(true);
            matchBossBars.put(matchId, bossBar);
        }

        for (UUID playerId : match.getTeamA()) {
            setupPlayerHud(match, playerId);
        }

        for (UUID playerId : match.getTeamB()) {
            setupPlayerHud(match, playerId);
        }

        if (matchBossBars.containsKey(matchId)) {

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                    plugin,
                    () -> {

                        if (match.getState()
                                != DuelMatch.MatchState.ACTIVE) {
                            removeMatchHud(match);
                            return;
                        }

                        long remainingMillis = Math.max(
                                0,
                                endTimeMillis - System.currentTimeMillis()
                        );

                        long remainingSeconds =
                                (remainingMillis + 999L) / 1000L;

                        updateBossBar(
                                match,
                                remainingSeconds,
                                durationTicks / 20L
                        );
                    },
                    0L,
                    20L
            );

            hudTasks.put(matchId, task);
        }
    }

    private void setupPlayerHud(
            DuelMatch match,
            UUID playerId
    ) {

        Player player = Bukkit.getPlayer(playerId);

        if (player == null || !player.isOnline() || !isHudEnabled()) {
            return;
        }

        BossBar bossBar = matchBossBars.get(match.getMatchId());

        if (bossBar != null) {
            bossBar.addPlayer(player);
        }

        if (plugin.getConfig().getBoolean(
                "match-hud.scoreboard.enabled",
                true
        )) {
            createPlayerScoreboard(match, player);
        }
    }

    // =========================================================
    // SCOREBOARD
    // =========================================================

    private void createPlayerScoreboard(
            DuelMatch match,
            Player player
    ) {

        UUID playerId = player.getUniqueId();

        Scoreboard scoreboard = matchScoreboards.get(playerId);

        if (scoreboard == null) {

            if (!previousScoreboards.containsKey(playerId)) {
                previousScoreboards.put(
                        playerId,
                        player.getScoreboard()
                );
            }

            scoreboard = Bukkit.getScoreboardManager()
                    .getNewScoreboard();

            Objective objective = scoreboard.registerNewObjective(
                    "oblivionduels",
                    "dummy",
                    format(
                            plugin.getConfig().getString(
                                    "match-hud.scoreboard.title",
                                    "&5&lOBLIVION DUELS"
                            )
                    )
            );

            objective.setDisplaySlot(DisplaySlot.SIDEBAR);

            Team teamA = scoreboard.registerNewTeam("od_team_a");
            Team teamB = scoreboard.registerNewTeam("od_team_b");

            String entryA = ChatColor.BLACK.toString();
            String entryB = ChatColor.DARK_BLUE.toString();

            teamA.addEntry(entryA);
            teamB.addEntry(entryB);

            objective.getScore(entryA).setScore(2);
            objective.getScore(entryB).setScore(1);

            matchScoreboards.put(playerId, scoreboard);
        }

        updatePlayerScoreboard(match, playerId);
        player.setScoreboard(scoreboard);
    }

    private void updatePlayerScoreboard(
            DuelMatch match,
            UUID playerId
    ) {

        Scoreboard scoreboard = matchScoreboards.get(playerId);

        if (scoreboard == null) {
            return;
        }

        Team teamA = scoreboard.getTeam("od_team_a");
        Team teamB = scoreboard.getTeam("od_team_b");

        if (teamA == null || teamB == null) {
            return;
        }

        String teamAName = format(
                plugin.getConfig().getString(
                        "match-hud.scoreboard.team-a-name",
                        "&e&lTEAM A"
                )
        );

        String teamBName = format(
                plugin.getConfig().getString(
                        "match-hud.scoreboard.team-b-name",
                        "&b&lTEAM B"
                )
        );

        teamA.setPrefix(teamAName + " ");
        teamA.setSuffix(
                ChatColor.WHITE.toString()
                        + match.getTeamAScore()
        );

        teamB.setPrefix(teamBName + " ");
        teamB.setSuffix(
                ChatColor.WHITE.toString()
                        + match.getTeamBScore()
        );
    }

    private void updateMatchScoreboards(DuelMatch match) {

        for (UUID playerId : match.getTeamA()) {
            updatePlayerScoreboard(match, playerId);
        }

        for (UUID playerId : match.getTeamB()) {
            updatePlayerScoreboard(match, playerId);
        }
    }

    // =========================================================
    // SCORE SOUND
    // =========================================================

    private void playScoreSound(DuelMatch match) {

        if (!plugin.getConfig().getBoolean(
                "match-hud.score-sound.enabled",
                true
        )) {
            return;
        }

        Sound sound;

        try {
            sound = Sound.valueOf(
                    plugin.getConfig().getString(
                            "match-hud.score-sound.sound",
                            "ENTITY_FIREWORK_ROCKET_BLAST"
                    ).toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            sound = Sound.ENTITY_FIREWORK_ROCKET_BLAST;
        }

        float volume = (float) plugin.getConfig().getDouble(
                "match-hud.score-sound.volume",
                1.0
        );

        float pitch = (float) plugin.getConfig().getDouble(
                "match-hud.score-sound.pitch",
                1.0
        );

        for (UUID playerId : match.getTeamA()) {
            Player player = Bukkit.getPlayer(playerId);

            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }

        for (UUID playerId : match.getTeamB()) {
            Player player = Bukkit.getPlayer(playerId);

            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    // =========================================================
    // BOSS BAR TIMER
    // =========================================================

    private void updateBossBar(
            DuelMatch match,
            long remainingSeconds,
            long totalSeconds
    ) {

        BossBar bossBar = matchBossBars.get(match.getMatchId());

        if (bossBar == null) {
            return;
        }

        long minutes = remainingSeconds / 60;
        long seconds = remainingSeconds % 60;

        String time = String.format(
                Locale.ROOT,
                "%02d:%02d",
                minutes,
                seconds
        );

        String title = format(
                plugin.getConfig().getString(
                        "match-hud.timer.title",
                        "&5&lMATCH TIME"
                )
        );

        bossBar.setTitle(title + " &7- &f" + time);

        double progress = totalSeconds <= 0
                ? 0.0
                : (double) remainingSeconds / totalSeconds;

        bossBar.setProgress(
                Math.max(0.0, Math.min(1.0, progress))
        );
    }

    // =========================================================
    // HUD CLEANUP
    // =========================================================

    private void removeMatchHud(DuelMatch match) {

        if (match == null) {
            return;
        }

        UUID matchId = match.getMatchId();

        BukkitTask task = hudTasks.remove(matchId);

        if (task != null) {
            task.cancel();
        }

        BossBar bossBar = matchBossBars.remove(matchId);

        if (bossBar != null) {
            bossBar.removeAll();
        }

        for (UUID playerId : match.getTeamA()) {
            restorePlayerScoreboard(playerId);
        }

        for (UUID playerId : match.getTeamB()) {
            restorePlayerScoreboard(playerId);
        }
    }

    private void restorePlayerScoreboard(UUID playerId) {

        matchScoreboards.remove(playerId);

        Scoreboard previous = previousScoreboards.remove(playerId);

        Player player = Bukkit.getPlayer(playerId);

        if (previous != null && player != null && player.isOnline()) {
            player.setScoreboard(previous);
        }
    }

    private String format(String text) {
        return ChatColor.translateAlternateColorCodes(
                '&',
                text == null ? "" : text
        );
    }

    // =========================================================
    // VIRTUAL LOOT CHEST
    // =========================================================

    /**
     * Opens the newest shared loot chest the player belongs to.
     * The database membership check is the access-control check.
     */
    public void openLoot(Player player) {

        if (player == null || !player.isOnline()) {
            return;
        }

        if (!databaseManager.isConnected()) {
            player.sendMessage(
                    ChatColor.RED
                            + "Loot storage is currently unavailable."
            );
            return;
        }

        DatabaseManager.LootChestData data =
                databaseManager.findLatestLootChestForPlayer(
                        player.getUniqueId()
                );

        if (data == null) {
            player.sendMessage(
                    ChatColor.YELLOW
                            + "You have no available match loot."
            );
            return;
        }

        if (!data.isWinner(player.getUniqueId())) {
            player.sendMessage(
                    ChatColor.RED
                            + "You are not allowed to access this loot chest."
            );
            return;
        }

        UUID matchId = data.getMatchId();

        Inventory inventory = liveLootInventories.get(matchId);

        if (inventory == null) {

            LootChestHolder holder = new LootChestHolder(matchId);

            inventory = Bukkit.createInventory(
                    holder,
                    54,
                    ChatColor.GOLD + "Shared Match Loot"
            );

            holder.setInventory(inventory);

            for (Map.Entry<Integer, ItemStack> entry
                    : data.getItems().entrySet()) {

                int slot = entry.getKey();

                if (slot < 0 || slot >= inventory.getSize()) {
                    continue;
                }

                ItemStack item = entry.getValue();

                if (item != null && !item.getType().isAir()) {
                    inventory.setItem(slot, item.clone());
                }
            }

            liveLootInventories.put(matchId, inventory);
        }

        if (isInventoryEmpty(inventory)) {
            databaseManager.deleteLootChest(matchId);
            liveLootInventories.remove(matchId);

            player.sendMessage(
                    ChatColor.YELLOW
                            + "This shared loot chest is empty."
            );
            return;
        }

        player.openInventory(inventory);
    }

    private boolean isInventoryEmpty(Inventory inventory) {

        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }

        return true;
    }

    private boolean saveVirtualChest(
            UUID matchId,
            Inventory inventory
    ) {

        if (!databaseManager.isConnected()) {
            return false;
        }

        return databaseManager.updateLootChestItems(
                matchId,
                inventory.getContents()
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLootChestClick(InventoryClickEvent event) {

        Inventory top = event.getView().getTopInventory();

        if (!(top.getHolder() instanceof LootChestHolder holder)) {
            return;
        }

        int topSize = top.getSize();
        int rawSlot = event.getRawSlot();

        // Handle clicks inside the shared chest manually.
        if (rawSlot >= 0 && rawSlot < topSize) {

            event.setCancelled(true);

            // No depositing items into the shared chest.
            if (event.getCursor() != null
                    && !event.getCursor().getType().isAir()) {
                return;
            }

            if (event.getClick() != ClickType.LEFT
                    && event.getClick() != ClickType.RIGHT) {
                return;
            }

            ItemStack stored = top.getItem(rawSlot);

            if (stored == null || stored.getType().isAir()) {
                return;
            }

            ItemStack original = stored.clone();
            ItemStack withdrawn = stored.clone();

            if (event.getClick() == ClickType.RIGHT) {

                int amount = (stored.getAmount() + 1) / 2;

                withdrawn.setAmount(amount);

                int remaining = stored.getAmount() - amount;

                if (remaining <= 0) {
                    top.setItem(rawSlot, null);
                } else {
                    ItemStack updated = stored.clone();
                    updated.setAmount(remaining);
                    top.setItem(rawSlot, updated);
                }

            } else {
                top.setItem(rawSlot, null);
            }

            event.getWhoClicked().setItemOnCursor(withdrawn);

            // Persist before allowing the withdrawal to stand.
            if (!saveVirtualChest(holder.getMatchId(), top)) {

                top.setItem(rawSlot, original);
                event.getWhoClicked().setItemOnCursor(null);

                event.getWhoClicked().sendMessage(
                        ChatColor.RED
                                + "Could not save that withdrawal. Please try again."
                );

                return;
            }

            if (isInventoryEmpty(top)) {

                if (databaseManager.deleteLootChest(holder.getMatchId())) {
                    liveLootInventories.remove(holder.getMatchId());
                }
            }

            return;
        }

        // Prevent moving items between player inventory and chest
        // using shift-click, number keys, offhand swap or double-click.
        ClickType click = event.getClick();

        if (click.isShiftClick()
                || click == ClickType.NUMBER_KEY
                || click == ClickType.SWAP_OFFHAND
                || click == ClickType.DOUBLE_CLICK
                || click == ClickType.MIDDLE
                || click == ClickType.DROP
                || click == ClickType.CONTROL_DROP) {

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLootChestDrag(InventoryDragEvent event) {

        Inventory top = event.getView().getTopInventory();

        if (!(top.getHolder() instanceof LootChestHolder)) {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {

            if (rawSlot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onLootChestClose(InventoryCloseEvent event) {

        Inventory top = event.getView().getTopInventory();

        if (!(top.getHolder() instanceof LootChestHolder holder)) {
            return;
        }

        if (!saveVirtualChest(holder.getMatchId(), top)) {
            plugin.getLogger().severe(
                    "Could not save shared loot chest "
                            + holder.getMatchId()
                            + " when it was closed."
            );
        }

        if (isInventoryEmpty(top)
                && databaseManager.deleteLootChest(holder.getMatchId())) {

            liveLootInventories.remove(holder.getMatchId());
        }
    }

    private static final class LootChestHolder implements InventoryHolder {

        private final UUID matchId;
        private Inventory inventory;

        private LootChestHolder(UUID matchId) {
            this.matchId = matchId;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public UUID getMatchId() {
            return matchId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // =========================================================
    // PLAYER DEATH
    // =========================================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {

        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();

        DuelMatch match =
                matchManager.getMatchForPlayer(playerId);

        if (match == null
                || match.getState() != DuelMatch.MatchState.ACTIVE
                || !match.isAlive(playerId)) {
            return;
        }

        cancelDisconnectTask(playerId);
        disconnectedInventories.remove(playerId);

        saveEarnedInventory(match, player);

        event.setKeepInventory(false);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);

        statsManager.addDeath(playerId);
        saveStats(playerId);

        Player killer = player.getKiller();

        if (killer != null) {

            UUID killerId = killer.getUniqueId();

            if (match.contains(killerId)
                    && match.isAlive(killerId)
                    && !match.isOnSameTeam(killerId, playerId)) {

                statsManager.addKill(killerId);
                saveStats(killerId);
            }
        }

        String arenaName =
                matchManager.getArenaForMatch(match.getMatchId());

        setSpectatorSpawn(playerId, arenaName);

        if (!match.eliminatePlayer(playerId)) {
            return;
        }

        broadcast(
                match,
                ChatColor.RED + player.getName()
                        + " has been eliminated!"
        );

        checkMatchWinner(match, arenaName);
    }

    // =========================================================
    // PLAYER DISCONNECT
    // =========================================================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        DuelMatch match =
                matchManager.getMatchForPlayer(playerId);

        if (match == null
                || match.getState() != DuelMatch.MatchState.ACTIVE
                || !match.isAlive(playerId)) {
            return;
        }

        cancelDisconnectTask(playerId);

        disconnectedInventories.put(
                playerId,
                InventorySnapshot.capture(player)
        );

        int graceSeconds = Math.max(
                1,
                plugin.getConfig().getInt(
                        "matchmaking.disconnect-grace-seconds",
                        30
                )
        );

        broadcast(
                match,
                ChatColor.YELLOW + player.getName()
                        + " disconnected! They have "
                        + graceSeconds + " seconds to reconnect."
        );

        BukkitTask task = Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {

                    disconnectTasks.remove(playerId);

                    Player onlinePlayer = Bukkit.getPlayer(playerId);

                    DuelMatch currentMatch =
                            matchManager.getMatchForPlayer(playerId);

                    if (onlinePlayer != null
                            || currentMatch != match
                            || match.getState()
                            != DuelMatch.MatchState.ACTIVE
                            || !match.isAlive(playerId)) {
                        return;
                    }

                    InventorySnapshot snapshot =
                            disconnectedInventories.remove(playerId);

                    if (snapshot != null) {
                        saveEarnedSnapshot(match, playerId, snapshot);
                    }

                    eliminatedDisconnectedPlayers.add(playerId);

                    String arenaName =
                            matchManager.getArenaForMatch(
                                    match.getMatchId()
                            );

                    setSpectatorSpawn(playerId, arenaName);

                    if (!match.eliminatePlayer(playerId)) {
                        return;
                    }

                    statsManager.addDeath(playerId);
                    saveStats(playerId);

                    broadcast(
                            match,
                            ChatColor.RED
                                    + "A disconnected player was eliminated!"
                    );

                    checkMatchWinner(match, arenaName);
                },
                graceSeconds * 20L
        );

        disconnectTasks.put(playerId, task);
    }

    // =========================================================
    // ROUND AND MATCH WINNER
    // =========================================================

    private void checkMatchWinner(
            DuelMatch match,
            String arenaName
    ) {

        if (match.getState() != DuelMatch.MatchState.ACTIVE) {
            return;
        }

        DuelMatch.WinningTeam roundWinner = match.getWinner();

        if (roundWinner == DuelMatch.WinningTeam.NONE) {
            return;
        }

        UUID matchId = match.getMatchId();
        int round = match.getCurrentRound();

        if (resolvedRounds.getOrDefault(matchId, 0) == round) {
            return;
        }

        resolvedRounds.put(matchId, round);

        match.recordRoundResult(roundWinner);

        updateMatchScoreboards(match);
        playScoreSound(match);

        String roundMessage;

        if (roundWinner == DuelMatch.WinningTeam.DRAW) {
            roundMessage = ChatColor.YELLOW + "ROUND " + round + " DRAW!";
        } else {
            String teamName =
                    roundWinner == DuelMatch.WinningTeam.TEAM_A
                            ? "TEAM A" : "TEAM B";

            roundMessage = ChatColor.GOLD
                    + teamName + " WON ROUND " + round + "!";
        }

        String scoreMessage = ChatColor.YELLOW
                + "TEAM A " + match.getTeamAScore()
                + " - " + match.getTeamBScore()
                + " TEAM B";

        for (UUID playerId : match.getTeamA()) {
            Player player = Bukkit.getPlayer(playerId);

            if (player != null) {
                player.sendTitle(
                        roundMessage,
                        scoreMessage,
                        5,
                        50,
                        10
                );
            }
        }

        for (UUID playerId : match.getTeamB()) {
            Player player = Bukkit.getPlayer(playerId);

            if (player != null) {
                player.sendTitle(
                        roundMessage,
                        scoreMessage,
                        5,
                        50,
                        10
                );
            }
        }

        broadcast(match, roundMessage);
        broadcast(match, scoreMessage);

        DuelMatch.WinningTeam matchWinner = match.getMatchWinner();

        if (matchWinner != DuelMatch.WinningTeam.NONE) {

            String finalMessage;

            if (matchWinner == DuelMatch.WinningTeam.DRAW) {
                finalMessage = ChatColor.YELLOW
                        + "The best-of-three match ended in a draw!";
            } else {
                String teamName =
                        matchWinner == DuelMatch.WinningTeam.TEAM_A
                                ? "Team A" : "Team B";

                finalMessage = ChatColor.GREEN
                        + teamName + " wins the match!";
            }

            finishMatch(
                    match,
                    arenaName,
                    finalMessage,
                    matchWinner
            );

            return;
        }

        DuelArena arena = arenaName == null
                ? null
                : arenaManager.getArena(arenaName);

        if (arena == null
                || !(plugin instanceof OblivionDuels duels)
                || duels.getMatchmakingCoordinator() == null) {

            finishMatch(
                    match,
                    arenaName,
                    ChatColor.RED
                            + "The next round could not be prepared.",
                    getCurrentSeriesLeader(match)
            );

            return;
        }

        duels.getMatchmakingCoordinator()
                .getPreparationManager()
                .prepareNextRound(match, arena);
    }

    private DuelMatch.WinningTeam getCurrentSeriesLeader(
            DuelMatch match
    ) {

        if (match.getTeamAScore() > match.getTeamBScore()) {
            return DuelMatch.WinningTeam.TEAM_A;
        }

        if (match.getTeamBScore() > match.getTeamAScore()) {
            return DuelMatch.WinningTeam.TEAM_B;
        }

        return DuelMatch.WinningTeam.DRAW;
    }

    // =========================================================
    // ARENA SPAWNS
    // =========================================================

    public void saveParticipantSpawns(
            DuelMatch match,
            String arenaName
    ) {

        if (match == null || arenaName == null) {
            return;
        }

        for (UUID playerId : match.getTeamA()) {
            setSpectatorSpawn(playerId, arenaName);
        }

        for (UUID playerId : match.getTeamB()) {
            setSpectatorSpawn(playerId, arenaName);
        }
    }

    private void setSpectatorSpawn(
            UUID playerId,
            String arenaName
    ) {

        if (arenaName == null) {
            return;
        }

        DuelArena arena = arenaManager.getArena(arenaName);

        if (arena == null) {
            return;
        }

        Location spectatorSpawn = arena.getSpectatorSpawn();

        if (spectatorSpawn != null
                && spectatorSpawn.getWorld() != null) {

            spectatorSpawns.put(
                    playerId,
                    spectatorSpawn.clone()
            );
        }

        Location exitSpawn = arena.getExitSpawn();

        if (exitSpawn != null
                && exitSpawn.getWorld() != null) {

            exitSpawns.put(
                    playerId,
                    exitSpawn.clone()
            );
        }
    }

    // =========================================================
    // EARNED LOOT TRACKING
    // =========================================================

    private void saveEarnedInventory(
            DuelMatch match,
            Player player
    ) {

        InventorySnapshot current = InventorySnapshot.capture(player);

        saveEarnedSnapshot(
                match,
                player.getUniqueId(),
                current
        );

        clearInventory(player);
    }

    private void saveEarnedSnapshot(
            DuelMatch match,
            UUID playerId,
            InventorySnapshot current
    ) {

        if (match == null || current == null) {
            return;
        }

        InventorySnapshot starting = getStartingSnapshot(
                match.getMatchId(),
                playerId
        );

        List<ItemStack> earned = calculateEarnedItems(
                starting,
                current
        );

        if (earned.isEmpty()) {
            return;
        }

        List<ItemStack> stored = matchLoot.computeIfAbsent(
                match.getMatchId(),
                key -> new HashMap<>()
        ).computeIfAbsent(
                playerId,
                key -> new ArrayList<>()
        );

        for (ItemStack item : earned) {
            addCopy(stored, item);
        }
    }

    private InventorySnapshot getStartingSnapshot(
            UUID matchId,
            UUID playerId
    ) {

        Map<UUID, InventorySnapshot> snapshots =
                startingInventories.get(matchId);

        if (snapshots == null) {
            return null;
        }

        return snapshots.get(playerId);
    }

    private List<ItemStack> calculateEarnedItems(
            InventorySnapshot starting,
            InventorySnapshot current
    ) {

        List<ItemStack> earned = new ArrayList<>();
        List<ItemStack> remainingStarting = new ArrayList<>();

        if (starting != null) {
            for (ItemStack item : starting.getAllItems()) {
                addCopy(remainingStarting, item);
            }
        }

        for (ItemStack currentItem : current.getAllItems()) {

            if (currentItem == null || currentItem.getType().isAir()) {
                continue;
            }

            int remainingAmount = currentItem.getAmount();

            for (int i = 0;
                 i < remainingStarting.size() && remainingAmount > 0;
                 i++) {

                ItemStack startingItem = remainingStarting.get(i);

                if (!startingItem.isSimilar(currentItem)) {
                    continue;
                }

                int used = Math.min(
                        remainingAmount,
                        startingItem.getAmount()
                );

                remainingAmount -= used;

                int newAmount = startingItem.getAmount() - used;

                if (newAmount <= 0) {
                    remainingStarting.remove(i);
                    i--;
                } else {
                    startingItem.setAmount(newAmount);
                }
            }

            if (remainingAmount > 0) {
                ItemStack earnedItem = currentItem.clone();
                earnedItem.setAmount(remainingAmount);
                earned.add(earnedItem);
            }
        }

        return earned;
    }

    private void addCopy(List<ItemStack> items, ItemStack item) {

        if (item != null && !item.getType().isAir()) {
            items.add(item.clone());
        }
    }

    private void clearInventory(Player player) {

        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
    }

    // =========================================================
    // FINISH MATCH
    // =========================================================

    private void finishMatch(
            DuelMatch match,
            String arenaName,
            String message,
            DuelMatch.WinningTeam winner
    ) {

        if (match == null
                || match.getState() == DuelMatch.MatchState.ENDING
                || match.getState() == DuelMatch.MatchState.ENDED) {
            return;
        }

        collectFinalInventories(match);

        match.setState(DuelMatch.MatchState.ENDING);

        removeMatchHud(match);
        cancelDisconnectTasks(match);

        BukkitTask timeout = timeoutTasks.remove(
                match.getMatchId()
        );

        if (timeout != null) {
            timeout.cancel();
        }

        // Restore original equipment first. Earned loot is delivered after.
        restoreParticipants(match);

        distributeLoot(match, winner);
        recordResults(match, winner);

        match.setState(DuelMatch.MatchState.ENDED);

        broadcast(match, message);

        resolvedRounds.remove(match.getMatchId());

        matchManager.endMatch(match.getMatchId());

        if (arenaName != null) {

            DuelArena arena = arenaManager.getArena(arenaName);

            if (arena != null && rollbackManager != null) {

                rollbackManager.restoreArena(
                        arena,
                        () -> arenaManager.releaseArena(arenaName)
                );

            } else {

                arenaManager.releaseArena(arenaName);
            }
        }

        plugin.getLogger().info(
                "Match " + match.getMatchId()
                        + " has ended. Arena restoration started."
        );
    }

    private void collectFinalInventories(DuelMatch match) {

        for (UUID playerId : match.getTeamA()) {
            collectFinalInventory(match, playerId);
        }

        for (UUID playerId : match.getTeamB()) {
            collectFinalInventory(match, playerId);
        }

        for (UUID playerId : match.getTeamA()) {
            collectDisconnectedInventory(match, playerId);
        }

        for (UUID playerId : match.getTeamB()) {
            collectDisconnectedInventory(match, playerId);
        }
    }

    private void collectFinalInventory(
            DuelMatch match,
            UUID playerId
    ) {

        Player player = Bukkit.getPlayer(playerId);

        if (player == null || !player.isOnline()) {
            return;
        }

        saveEarnedSnapshot(
                match,
                playerId,
                InventorySnapshot.capture(player)
        );
    }

    private void collectDisconnectedInventory(
            DuelMatch match,
            UUID playerId
    ) {

        InventorySnapshot snapshot =
                disconnectedInventories.remove(playerId);

        if (snapshot != null) {
            saveEarnedSnapshot(match, playerId, snapshot);
        }
    }

    private void restoreParticipants(DuelMatch match) {

        UUID matchId = match.getMatchId();

        Map<UUID, InventorySnapshot> snapshots =
                startingInventories.remove(matchId);

        Set<UUID> participants = new HashSet<>();

        participants.addAll(match.getTeamA());
        participants.addAll(match.getTeamB());

        for (UUID playerId : participants) {

            Player player = Bukkit.getPlayer(playerId);

            InventorySnapshot starting =
                    snapshots == null ? null : snapshots.get(playerId);

            if (player != null && player.isOnline()) {

                if (starting != null) {
                    starting.restore(player);
                }

                player.setGameMode(GameMode.SURVIVAL);

                Location exitSpawn = exitSpawns.get(playerId);

                if (exitSpawn != null
                        && exitSpawn.getWorld() != null) {
                    player.teleport(exitSpawn);
                }

                spectatorSpawns.remove(playerId);
                exitSpawns.remove(playerId);
                eliminatedDisconnectedPlayers.remove(playerId);

            } else if (starting != null) {

                pendingStartingInventories.put(playerId, starting);
            }
        }
    }

    private void cancelDisconnectTasks(DuelMatch match) {

        for (UUID playerId : match.getTeamA()) {
            cancelDisconnectTask(playerId);
        }

        for (UUID playerId : match.getTeamB()) {
            cancelDisconnectTask(playerId);
        }
    }

    private void cancelDisconnectTask(UUID playerId) {

        BukkitTask task = disconnectTasks.remove(playerId);

        if (task != null) {
            task.cancel();
        }
    }

    // =========================================================
    // LOOT DISTRIBUTION
    // =========================================================

    private void distributeLoot(
            DuelMatch match,
            DuelMatch.WinningTeam winner
    ) {

        Map<UUID, List<ItemStack>> savedLoot =
                matchLoot.remove(match.getMatchId());

        if (savedLoot == null) {
            savedLoot = new HashMap<>();
        }

        // A draw returns each player's earned loot to that player.
        if (winner == DuelMatch.WinningTeam.DRAW
                || winner == DuelMatch.WinningTeam.NONE) {

            for (Map.Entry<UUID, List<ItemStack>> entry
                    : savedLoot.entrySet()) {

                giveItems(
                        entry.getKey(),
                        entry.getValue()
                );
            }

            return;
        }

        List<UUID> winningTeam = new ArrayList<>(
                winner == DuelMatch.WinningTeam.TEAM_A
                        ? match.getTeamA()
                        : match.getTeamB()
        );

        List<UUID> losingTeam = new ArrayList<>(
                winner == DuelMatch.WinningTeam.TEAM_A
                        ? match.getTeamB()
                        : match.getTeamA()
        );

        // Return winners' own earned loot to them.
        for (UUID winnerId : winningTeam) {

            List<ItemStack> ownLoot = savedLoot.get(winnerId);

            if (ownLoot != null) {
                giveItems(winnerId, ownLoot);
            }
        }

        // Collect the losing team's loot for the shared chest.
        List<ItemStack> losingLoot = new ArrayList<>();

        for (UUID loserId : losingTeam) {

            List<ItemStack> items = savedLoot.get(loserId);

            if (items != null) {
                for (ItemStack item : items) {
                    addCopy(losingLoot, item);
                }
            }
        }

        if (losingLoot.isEmpty() || winningTeam.isEmpty()) {
            return;
        }

        Inventory chestInventory = Bukkit.createInventory(
                null,
                54,
                ChatColor.GOLD + "Shared Match Loot"
        );

        Map<Integer, ItemStack> leftovers =
                chestInventory.addItem(
                        losingLoot.stream()
                                .map(ItemStack::clone)
                                .toArray(ItemStack[]::new)
                );

        boolean saved = databaseManager.saveLootChest(
                match.getMatchId(),
                winningTeam,
                chestInventory.getContents()
        );

        if (!saved) {

            // Do not discard loot if the chest could not be saved.
            List<ItemStack> fallbackLoot =
                    new ArrayList<>(losingLoot);

            for (UUID winnerId : winningTeam) {
                if (fallbackLoot.isEmpty()) {
                    break;
                }

                giveItems(
                        winnerId,
                        Collections.singletonList(
                                fallbackLoot.remove(0)
                        )
                );
            }

            for (ItemStack item : fallbackLoot) {
                storePendingLoot(winningTeam.get(0),
                        Collections.singletonList(item));
            }

            for (UUID winnerId : winningTeam) {
                Player player = Bukkit.getPlayer(winnerId);

                if (player != null) {
                    player.sendMessage(
                            ChatColor.RED
                                    + "The shared loot chest could not be saved. "
                                    + "Loot was sent to your inventory or pending loot instead."
                    );
                }
            }

            return;
        }

        // If the 54-slot chest is full, keep excess loot as pending loot
        // for the winning team instead of silently discarding it.
        for (ItemStack item : leftovers.values()) {
            storePendingLoot(
                    winningTeam.get(0),
                    Collections.singletonList(item)
            );
        }

        for (UUID winnerId : winningTeam) {

            Player player = Bukkit.getPlayer(winnerId);

            if (player != null) {
                player.sendMessage(
                        ChatColor.GOLD
                                + "Your team has received shared match loot! "
                                + ChatColor.YELLOW
                                + "Use /duels loot to open it."
                );
            }
        }
    }

    private void giveItems(
            UUID playerId,
            List<ItemStack> items
    ) {

        if (items == null || items.isEmpty()) {
            return;
        }

        Player player = Bukkit.getPlayer(playerId);

        if (player == null) {
            storePendingLoot(playerId, items);
            return;
        }

        Map<Integer, ItemStack> leftovers =
                player.getInventory().addItem(
                        items.stream()
                                .map(ItemStack::clone)
                                .toArray(ItemStack[]::new)
                );

        if (!leftovers.isEmpty()) {

            storePendingLoot(
                    playerId,
                    new ArrayList<>(leftovers.values())
            );

            player.sendMessage(
                    ChatColor.YELLOW
                            + "Some loot is waiting because your inventory is full."
            );
        }
    }

    private void storePendingLoot(
            UUID playerId,
            List<ItemStack> items
    ) {

        if (playerId == null || items == null || items.isEmpty()) {
            return;
        }

        if (databaseManager.isConnected()) {

            List<ItemStack> stored =
                    databaseManager.loadPendingLoot(playerId);

            for (ItemStack item : items) {
                addCopy(stored, item);
            }

            databaseManager.savePendingLoot(playerId, stored);
            return;
        }

        List<ItemStack> stored = pendingLoot.computeIfAbsent(
                playerId,
                key -> new ArrayList<>()
        );

        for (ItemStack item : items) {
            addCopy(stored, item);
        }
    }

    // =========================================================
    // PLAYER JOIN
    // =========================================================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        BukkitTask task = disconnectTasks.remove(playerId);

        if (task != null) {
            task.cancel();
        }

        boolean wasEliminatedWhileDisconnected =
                eliminatedDisconnectedPlayers.remove(playerId);

        DuelMatch match =
                matchManager.getMatchForPlayer(playerId);

        if (match != null
                && match.getState() == DuelMatch.MatchState.ACTIVE) {

            if (match.isAlive(playerId)) {

                disconnectedInventories.remove(playerId);

                if (task != null) {
                    broadcast(
                            match,
                            ChatColor.GREEN + player.getName()
                                    + " reconnected!"
                    );
                }

            } else {

                clearInventory(player);

                Location spectatorSpawn =
                        spectatorSpawns.get(playerId);

                if (spectatorSpawn != null
                        && spectatorSpawn.getWorld() != null) {

                    player.teleport(spectatorSpawn);
                    player.setGameMode(GameMode.SPECTATOR);
                }
            }

            setupPlayerHud(match, playerId);

        } else {

            InventorySnapshot pendingStarting =
                    pendingStartingInventories.remove(playerId);

            if (pendingStarting != null) {
                pendingStarting.restore(player);
            }

            if (wasEliminatedWhileDisconnected
                    || exitSpawns.containsKey(playerId)) {

                restorePlayerAfterMatch(player, playerId);
                restorePlayerScoreboard(playerId);

            } else {

                restorePlayerScoreboard(playerId);
            }
        }

        disconnectedInventories.remove(playerId);
        deliverPendingLoot(player);
    }

    private void restorePlayerAfterMatch(
            Player player,
            UUID playerId
    ) {

        Location exitSpawn = exitSpawns.remove(playerId);

        spectatorSpawns.remove(playerId);
        eliminatedDisconnectedPlayers.remove(playerId);

        player.setGameMode(GameMode.SURVIVAL);

        if (exitSpawn != null
                && exitSpawn.getWorld() != null) {
            player.teleport(exitSpawn);
        }
    }

    // =========================================================
    // PENDING LOOT
    // =========================================================

    private void deliverPendingLoot(Player player) {

        UUID playerId = player.getUniqueId();

        List<ItemStack> items = new ArrayList<>();

        if (databaseManager.isConnected()) {
            items.addAll(
                    databaseManager.loadPendingLoot(playerId)
            );
        }

        List<ItemStack> memoryItems = pendingLoot.remove(playerId);

        if (memoryItems != null) {
            for (ItemStack item : memoryItems) {
                addCopy(items, item);
            }
        }

        if (items.isEmpty()) {
            return;
        }

        Map<Integer, ItemStack> leftovers =
                player.getInventory().addItem(
                        items.stream()
                                .map(ItemStack::clone)
                                .toArray(ItemStack[]::new)
                );

        List<ItemStack> remaining =
                new ArrayList<>(leftovers.values());

        if (databaseManager.isConnected()) {

            databaseManager.savePendingLoot(
                    playerId,
                    remaining
            );

        } else if (!remaining.isEmpty()) {

            pendingLoot.put(playerId, remaining);
        }

        if (remaining.isEmpty()) {

            player.sendMessage(
                    ChatColor.GREEN
                            + "Your waiting match loot has been delivered!"
            );

        } else {

            player.sendMessage(
                    ChatColor.YELLOW
                            + "Some match loot is still waiting because your inventory is full."
            );
        }
    }

    // =========================================================
    // STATISTICS
    // =========================================================

    private void recordResults(
            DuelMatch match,
            DuelMatch.WinningTeam winner
    ) {

        if (winner == DuelMatch.WinningTeam.DRAW
                || winner == DuelMatch.WinningTeam.NONE) {
            return;
        }

        for (UUID playerId : match.getTeamA()) {

            if (winner == DuelMatch.WinningTeam.TEAM_A) {
                statsManager.addWin(playerId);
            } else {
                statsManager.addLoss(playerId);
            }

            saveStats(playerId);
        }

        for (UUID playerId : match.getTeamB()) {

            if (winner == DuelMatch.WinningTeam.TEAM_B) {
                statsManager.addWin(playerId);
            } else {
                statsManager.addLoss(playerId);
            }

            saveStats(playerId);
        }
    }

    private void saveStats(UUID playerId) {

        databaseManager.saveStats(
                playerId,
                statsManager.getStats(playerId)
        );
    }

    // =========================================================
    // RESPAWN
    // =========================================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {

        UUID playerId = event.getPlayer().getUniqueId();

        Location spectatorSpawn =
                spectatorSpawns.get(playerId);

        if (spectatorSpawn != null
                && spectatorSpawn.getWorld() != null) {

            event.setRespawnLocation(spectatorSpawn);

            Bukkit.getScheduler().runTask(
                    plugin,
                    () -> {
                        Player player = event.getPlayer();

                        if (player.isOnline()
                                && spectatorSpawns.containsKey(playerId)) {

                            player.setGameMode(GameMode.SPECTATOR);
                        }
                    }
            );
        }
    }

    // =========================================================
    // BROADCAST
    // =========================================================

    private void broadcast(
            DuelMatch match,
            String message
    ) {

        for (UUID playerId : match.getTeamA()) {

            Player player = Bukkit.getPlayer(playerId);

            if (player != null) {
                player.sendMessage(
                        ChatColor.LIGHT_PURPLE
                                + "[OblivionDuels] "
                                + ChatColor.RESET + message
                );
            }
        }

        for (UUID playerId : match.getTeamB()) {

            Player player = Bukkit.getPlayer(playerId);

            if (player != null) {
                player.sendMessage(
                        ChatColor.LIGHT_PURPLE
                                + "[OblivionDuels] "
                                + ChatColor.RESET + message
                );
            }
        }
    }

    // =========================================================
    // INVENTORY SNAPSHOT
    // =========================================================

    private static final class InventorySnapshot {

        private final ItemStack[] storage;
        private final ItemStack[] armor;
        private final ItemStack offhand;

        private InventorySnapshot(
                ItemStack[] storage,
                ItemStack[] armor,
                ItemStack offhand
        ) {
            this.storage = cloneContents(storage);
            this.armor = cloneContents(armor);
            this.offhand = offhand == null ? null : offhand.clone();
        }

        private static InventorySnapshot capture(Player player) {

            return new InventorySnapshot(
                    player.getInventory().getStorageContents(),
                    player.getInventory().getArmorContents(),
                    player.getInventory().getItemInOffHand()
            );
        }

        private void restore(Player player) {

            player.getInventory().setStorageContents(
                    cloneContents(storage)
            );

            player.getInventory().setArmorContents(
                    cloneContents(armor)
            );

            player.getInventory().setItemInOffHand(
                    offhand == null ? null : offhand.clone()
            );
        }

        private List<ItemStack> getAllItems() {

            List<ItemStack> items = new ArrayList<>();

            for (ItemStack item : storage) {
                if (item != null && !item.getType().isAir()) {
                    items.add(item.clone());
                }
            }

            for (ItemStack item : armor) {
                if (item != null && !item.getType().isAir()) {
                    items.add(item.clone());
                }
            }

            if (offhand != null && !offhand.getType().isAir()) {
                items.add(offhand.clone());
            }

            return items;
        }

        private static ItemStack[] cloneContents(ItemStack[] contents) {

            if (contents == null) {
                return new ItemStack[0];
            }

            ItemStack[] cloned = new ItemStack[contents.length];

            for (int i = 0; i < contents.length; i++) {
                cloned[i] = contents[i] == null
                        ? null
                        : contents[i].clone();
            }

            return cloned;
        }
    }

    // =========================================================
    // SHUTDOWN
    // =========================================================

    public void shutdown() {

        for (BukkitTask task : disconnectTasks.values()) {
            task.cancel();
        }

        disconnectTasks.clear();

        for (BukkitTask task : timeoutTasks.values()) {
            task.cancel();
        }

        timeoutTasks.clear();

        for (BukkitTask task : hudTasks.values()) {
            task.cancel();
        }

        hudTasks.clear();

        for (BossBar bossBar : matchBossBars.values()) {
            bossBar.removeAll();
        }

        matchBossBars.clear();

        Set<UUID> players = new HashSet<>(matchScoreboards.keySet());

        for (UUID playerId : players) {
            restorePlayerScoreboard(playerId);
        }

        // Persist every cached chest before the database is closed.
        for (Map.Entry<UUID, Inventory> entry
                : liveLootInventories.entrySet()) {

            if (!saveVirtualChest(entry.getKey(), entry.getValue())) {
                plugin.getLogger().severe(
                        "Could not save loot chest during shutdown: "
                                + entry.getKey()
                );
            }
        }

        liveLootInventories.clear();
    }
}
