
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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
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
    private final Map<UUID, List<ItemStack>> disconnectedInventories =
            new HashMap<>();

    private final Set<UUID> eliminatedDisconnectedPlayers = new HashSet<>();

    private final Map<UUID, BukkitTask> disconnectTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> timeoutTasks = new HashMap<>();

    // Match HUD
    private final Map<UUID, BukkitTask> hudTasks = new HashMap<>();
    private final Map<UUID, BossBar> matchBossBars = new HashMap<>();

    private final Map<UUID, Scoreboard> matchScoreboards = new HashMap<>();
    private final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();

    private long matchEndTimesPlaceholder;

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

                    int teamAAlive = match.getAliveCount(
                            DuelMatch.WinningTeam.TEAM_A
                    );

                    int teamBAlive = match.getAliveCount(
                            DuelMatch.WinningTeam.TEAM_B
                    );

                    DuelMatch.WinningTeam winner;

                    if (teamAAlive > teamBAlive) {
                        winner = DuelMatch.WinningTeam.TEAM_A;
                    } else if (teamBAlive > teamAAlive) {
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
                                        + "Match timed out! It ended in a draw.",
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
                                        + winningTeam + " wins!",
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

        if (player == null || !player.isOnline()) {
            return;
        }

        if (!isHudEnabled()) {
            return;
        }

        UUID matchId = match.getMatchId();

        BossBar bossBar = matchBossBars.get(matchId);

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
    // SCORE AWARDING AND SOUND
    // =========================================================

    private void awardEliminationPoint(
            DuelMatch match,
            UUID eliminatedPlayer
    ) {

        if (match == null
                || match.getState() != DuelMatch.MatchState.ACTIVE) {
            return;
        }

        match.awardPointToOpposingTeam(eliminatedPlayer);

        updateMatchScoreboards(match);
        playScoreSound(match);
    }

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

        saveInventory(match, player);

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

        awardEliminationPoint(match, playerId);

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
                snapshotInventory(player)
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

                    List<ItemStack> inventory =
                            disconnectedInventories.remove(playerId);

                    if (inventory != null && !inventory.isEmpty()) {

                        matchLoot.computeIfAbsent(
                                match.getMatchId(),
                                key -> new HashMap<>()
                        ).put(playerId, inventory);
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

                    awardEliminationPoint(match, playerId);

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
    // MATCH WINNER
    // =========================================================

    private void checkMatchWinner(
            DuelMatch match,
            String arenaName
    ) {

        if (match.getState() != DuelMatch.MatchState.ACTIVE) {
            return;
        }

        if (match.getAliveCount() == 0) {

            finishMatch(
                    match,
                    arenaName,
                    "The match ended in a draw.",
                    DuelMatch.WinningTeam.DRAW
            );

            return;
        }

        DuelMatch.WinningTeam winner = match.getWinner();

        if (winner == DuelMatch.WinningTeam.TEAM_A
                || winner == DuelMatch.WinningTeam.TEAM_B) {

            String winningTeam =
                    winner == DuelMatch.WinningTeam.TEAM_A
                            ? "Team A" : "Team B";

            finishMatch(
                    match,
                    arenaName,
                    ChatColor.GREEN + winningTeam + " wins!",
                    winner
            );
        }
    }

    // =========================================================
    // ARENA SPAWNS
    // =========================================================

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
    // INVENTORY
    // =========================================================

    private List<ItemStack> snapshotInventory(Player player) {

        List<ItemStack> items = new ArrayList<>();

        for (ItemStack item :
                player.getInventory().getStorageContents()) {
            addCopy(items, item);
        }

        for (ItemStack item :
                player.getInventory().getArmorContents()) {
            addCopy(items, item);
        }

        addCopy(
                items,
                player.getInventory().getItemInOffHand()
        );

        return items;
    }

    private void saveInventory(DuelMatch match, Player player) {

        List<ItemStack> items = snapshotInventory(player);

        matchLoot.computeIfAbsent(
                match.getMatchId(),
                key -> new HashMap<>()
        ).put(player.getUniqueId(), items);

        clearInventory(player);
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

        match.setState(DuelMatch.MatchState.ENDING);

        removeMatchHud(match);
        cancelDisconnectTasks(match);

        BukkitTask timeout = timeoutTasks.remove(
                match.getMatchId()
        );

        if (timeout != null) {
            timeout.cancel();
        }

        distributeLoot(match, winner);
        recordResults(match, winner);

        match.setState(DuelMatch.MatchState.ENDED);

        broadcast(match, message);

        restoreParticipants(match);

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

    private void restoreParticipants(DuelMatch match) {

        Set<UUID> participants = new HashSet<>();

        participants.addAll(match.getTeamA());
        participants.addAll(match.getTeamB());

        for (UUID playerId : participants) {

            Player player = Bukkit.getPlayer(playerId);

            if (player != null && player.isOnline()) {

                player.setGameMode(GameMode.SURVIVAL);

                Location exitSpawn = exitSpawns.get(playerId);

                if (exitSpawn != null
                        && exitSpawn.getWorld() != null) {
                    player.teleport(exitSpawn);
                }

                spectatorSpawns.remove(playerId);
                exitSpawns.remove(playerId);
                eliminatedDisconnectedPlayers.remove(playerId);
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

        if (savedLoot == null || savedLoot.isEmpty()) {
            return;
        }

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

        List<UUID> winners = new ArrayList<>(
                winner == DuelMatch.WinningTeam.TEAM_A
                        ? match.getTeamA()
                        : match.getTeamB()
        );

        if (winners.isEmpty()) {
            return;
        }

        int recipientIndex = 0;

        for (List<ItemStack> items : savedLoot.values()) {

            UUID recipient = winners.get(
                    recipientIndex % winners.size()
            );

            giveItems(recipient, items);

            recipientIndex++;
        }

        for (UUID winnerId : winners) {

            Player player = Bukkit.getPlayer(winnerId);

            if (player != null) {
                player.sendMessage(
                        ChatColor.GOLD
                                + "Your team has received the match loot!"
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

        } else if (wasEliminatedWhileDisconnected
                || exitSpawns.containsKey(playerId)) {

            clearInventory(player);
            restorePlayerAfterMatch(player, playerId);
            restorePlayerScoreboard(playerId);

        } else {

            restorePlayerScoreboard(playerId);
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
    }
}
