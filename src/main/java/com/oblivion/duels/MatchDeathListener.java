
package com.oblivion.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class MatchDeathListener implements Listener {

    private final JavaPlugin plugin;
    private final MatchManager matchManager;
    private final ArenaManager arenaManager;
    private final StatsManager statsManager;
    private final DatabaseManager databaseManager;

    private final Map<UUID, Location> spectatorSpawns =
            new HashMap<>();

    // Match ID -> defeated player ID -> saved inventory items
    private final Map<UUID, Map<UUID, List<ItemStack>>> matchLoot =
            new HashMap<>();

    // Player ID -> items waiting to be collected
    private final Map<UUID, List<ItemStack>> pendingLoot =
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

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {

        Player player = event.getEntity();

        DuelMatch match = matchManager.getMatchForPlayer(
                player.getUniqueId()
        );

        if (match == null
                || match.getState() != DuelMatch.MatchState.ACTIVE) {
            return;
        }

        UUID playerId = player.getUniqueId();

        // Save the defeated player's inventory.
        saveInventory(match, player);

        event.setKeepInventory(false);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);

        // Record death.
        statsManager.addDeath(playerId);
        saveStats(playerId);

        // Record a kill only if the killer is an opposing participant.
        Player killer = player.getKiller();

        if (killer != null) {
            UUID killerId = killer.getUniqueId();

            if (match.contains(killerId)
                    && !match.isOnSameTeam(killerId, playerId)) {

                statsManager.addKill(killerId);
                saveStats(killerId);
            }
        }

        String arenaName =
                matchManager.getArenaForMatch(match.getMatchId());

        if (arenaName != null) {
            DuelArena arena = arenaManager.getArena(arenaName);

            if (arena != null && arena.getSpectatorSpawn() != null) {
                spectatorSpawns.put(
                        playerId,
                        arena.getSpectatorSpawn()
                );
            }
        }

        match.eliminatePlayer(playerId);

        broadcast(match,
                ChatColor.RED + player.getName()
                        + " has been eliminated!");

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

            String winningTeam = winner
                    == DuelMatch.WinningTeam.TEAM_A
                    ? "Team A" : "Team B";

            finishMatch(
                    match,
                    arenaName,
                    ChatColor.GREEN + winningTeam + " wins!",
                    winner
            );
        }
    }

    private void saveInventory(DuelMatch match, Player player) {

        List<ItemStack> items = new ArrayList<>();

        for (ItemStack item : player.getInventory().getStorageContents()) {
            addCopy(items, item);
        }

        for (ItemStack item : player.getInventory().getArmorContents()) {
            addCopy(items, item);
        }

        addCopy(items, player.getInventory().getItemInOffHand());

        matchLoot.computeIfAbsent(
                match.getMatchId(),
                key -> new HashMap<>()
        ).put(player.getUniqueId(), items);

        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
    }

    private void addCopy(List<ItemStack> items, ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            items.add(item.clone());
        }
    }

    private void finishMatch(
            DuelMatch match,
            String arenaName,
            String message,
            DuelMatch.WinningTeam winner
    ) {
        distributeLoot(match, winner);
        recordResults(match, winner);

        match.setState(DuelMatch.MatchState.ENDED);

        broadcast(match, message);

        matchManager.endMatch(match.getMatchId());

        if (arenaName != null) {
            arenaManager.releaseArena(arenaName);
        }

        plugin.getLogger().info(
                "Match " + match.getMatchId() + " has ended."
        );
    }

    private void distributeLoot(
            DuelMatch match,
            DuelMatch.WinningTeam winner
    ) {
        Map<UUID, List<ItemStack>> savedLoot =
                matchLoot.remove(match.getMatchId());

        if (savedLoot == null || savedLoot.isEmpty()) {
            return;
        }

        // A draw returns each inventory to its original owner.
        if (winner == DuelMatch.WinningTeam.DRAW
                || winner == DuelMatch.WinningTeam.NONE) {

            for (Map.Entry<UUID, List<ItemStack>> entry
                    : savedLoot.entrySet()) {
                giveItems(entry.getKey(), entry.getValue());
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
                player.sendMessage(ChatColor.GOLD
                        + "Your team has received the match loot!");
            }
        }
    }

    private void giveItems(UUID playerId, List<ItemStack> items) {

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

            player.sendMessage(ChatColor.YELLOW
                    + "Some loot is waiting because your inventory is full.");
        }
    }

    private void storePendingLoot(
            UUID playerId,
            List<ItemStack> items
    ) {
        List<ItemStack> stored = pendingLoot.computeIfAbsent(
                playerId,
                key -> new ArrayList<>()
        );

        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                stored.add(item.clone());
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();

        List<ItemStack> items = pendingLoot.remove(
                player.getUniqueId()
        );

        if (items == null || items.isEmpty()) {
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
                    player.getUniqueId(),
                    new ArrayList<>(leftovers.values())
            );
        } else {
            player.sendMessage(ChatColor.GREEN
                    + "Your waiting match loot has been delivered!");
        }
    }

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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {

        UUID playerId = event.getPlayer().getUniqueId();

        Location spectatorSpawn = spectatorSpawns.remove(playerId);

        if (spectatorSpawn != null
                && spectatorSpawn.getWorld() != null) {
            event.setRespawnLocation(spectatorSpawn);
        }
    }

    private void broadcast(DuelMatch match, String message) {

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
}
