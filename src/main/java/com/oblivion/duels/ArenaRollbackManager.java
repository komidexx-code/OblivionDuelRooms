
package com.oblivion.duels;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ArenaRollbackManager implements Listener {

    private static final int RESTORE_BATCH_SIZE = 500;

    private final JavaPlugin plugin;

    private final Map<String, Map<Location, BlockState>> snapshots =
            new HashMap<>();

    private final Map<String, Set<Location>> trackedBlocks =
            new HashMap<>();

    private final Map<String, List<Map.Entry<Location, BlockState>>>
            pendingRestorations = new HashMap<>();

    private final Map<String, Runnable> pendingCallbacks =
            new HashMap<>();

    private final Map<String, BukkitTask> restorationTasks =
            new HashMap<>();

    private boolean shuttingDown;

    public ArenaRollbackManager(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean startTracking(DuelArena arena) {

        if (arena == null || !arena.isReady() || shuttingDown) {
            return false;
        }

        String key = normalize(arena.getName());

        if (snapshots.containsKey(key)
                || pendingRestorations.containsKey(key)) {
            return false;
        }

        snapshots.put(key, new HashMap<>());
        trackedBlocks.put(key, new HashSet<>());

        plugin.getLogger().info(
                "Started arena rollback tracking for " + arena.getName()
        );

        return true;
    }

    private void recordBlock(Block block) {

        if (block == null) {
            return;
        }

        recordBlock(block, block.getState());
    }

    private void recordBlock(
            Block block,
            BlockState originalState
    ) {

        if (block == null
                || originalState == null
                || block.getWorld() == null
                || shuttingDown) {
            return;
        }

        Location location = block.getLocation();

        for (String arenaName : new HashSet<>(snapshots.keySet())) {

            DuelArena arena = getArenaManager().getArena(arenaName);

            if (arena == null || !arena.contains(location)) {
                continue;
            }

            Map<Location, BlockState> original =
                    snapshots.get(arenaName);

            Set<Location> tracked =
                    trackedBlocks.get(arenaName);

            if (original == null || tracked == null) {
                continue;
            }

            Location key = location.clone();

            if (tracked.add(key)) {
                original.put(key, originalState);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        recordBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {

        BlockState replacedState = event.getBlockReplacedState();

        recordBlock(
                event.getBlock(),
                replacedState
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {

        for (Block block : event.blockList()) {
            recordBlock(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {

        for (Block block : event.blockList()) {
            recordBlock(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        recordBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        recordBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        recordBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        recordBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        recordBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        recordBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        recordBlock(event.getToBlock());
    }

    public void restoreArena(
            DuelArena arena,
            Runnable onComplete
    ) {

        if (arena == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        String key = normalize(arena.getName());

        // If this arena is already being restored, join that restoration.
        // Never release the arena before the existing restoration finishes.
        if (pendingRestorations.containsKey(key)) {

            if (onComplete != null) {

                Runnable existingCallback =
                        pendingCallbacks.get(key);

                if (existingCallback == null) {
                    pendingCallbacks.put(key, onComplete);
                } else {
                    pendingCallbacks.put(key, () -> {
                        try {
                            existingCallback.run();
                        } finally {
                            onComplete.run();
                        }
                    });
                }
            }

            return;
        }

        Map<Location, BlockState> original =
                snapshots.remove(key);

        trackedBlocks.remove(key);

        if (original == null || original.isEmpty()) {

            if (onComplete != null) {
                onComplete.run();
            }

            return;
        }

        List<Map.Entry<Location, BlockState>> blocks =
                new ArrayList<>(original.entrySet());

        if (shuttingDown) {
            restoreBlocksImmediately(blocks);

            if (onComplete != null) {
                onComplete.run();
            }

            return;
        }

        pendingRestorations.put(key, blocks);

        if (onComplete != null) {
            pendingCallbacks.put(key, onComplete);
        }

        plugin.getLogger().info(
                "Restoring " + blocks.size()
                        + " blocks in arena " + arena.getName() + "."
        );

        restoreNextBatch(key, 0);
    }

    private void restoreNextBatch(String arenaName, int start) {

        if (shuttingDown) {
            return;
        }

        List<Map.Entry<Location, BlockState>> blocks =
                pendingRestorations.get(arenaName);

        if (blocks == null) {
            return;
        }

        int end = Math.min(
                start + RESTORE_BATCH_SIZE,
                blocks.size()
        );

        for (int i = start; i < end; i++) {
            restoreBlock(blocks.get(i));
        }

        if (end >= blocks.size()) {

            pendingRestorations.remove(arenaName);

            BukkitTask task = restorationTasks.remove(arenaName);

            if (task != null) {
                task.cancel();
            }

            Runnable callback = pendingCallbacks.remove(arenaName);

            plugin.getLogger().info(
                    "Finished restoring arena " + arenaName + "."
            );

            if (callback != null) {
                callback.run();
            }

            return;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> restoreNextBatch(arenaName, end),
                1L
        );

        restorationTasks.put(arenaName, task);
    }

    private void restoreBlock(
            Map.Entry<Location, BlockState> entry
    ) {

        Location location = entry.getKey();

        if (location == null || location.getWorld() == null) {
            return;
        }

        World world = location.getWorld();

        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;

        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.getChunkAt(chunkX, chunkZ).load();
        }

        BlockState original = entry.getValue();

        if (original != null) {
            original.update(true, false);
        }
    }

    private void restoreBlocksImmediately(
            List<Map.Entry<Location, BlockState>> blocks
    ) {

        for (Map.Entry<Location, BlockState> entry : blocks) {
            restoreBlock(entry);
        }
    }

    public void shutdown() {

        if (shuttingDown) {
            return;
        }

        shuttingDown = true;

        for (BukkitTask task : restorationTasks.values()) {
            task.cancel();
        }

        restorationTasks.clear();

        for (Map.Entry<String, Map<Location, BlockState>> snapshot
                : snapshots.entrySet()) {

            restoreBlocksImmediately(
                    new ArrayList<>(snapshot.getValue().entrySet())
            );
        }

        snapshots.clear();
        trackedBlocks.clear();

        for (List<Map.Entry<Location, BlockState>> blocks
                : pendingRestorations.values()) {

            restoreBlocksImmediately(blocks);
        }

        pendingRestorations.clear();

        for (Runnable callback : pendingCallbacks.values()) {
            if (callback != null) {
                callback.run();
            }
        }

        pendingCallbacks.clear();

        plugin.getLogger().info(
                "Arena rollback manager shutdown completed."
        );
    }

    private ArenaManager getArenaManager() {

        if (plugin instanceof OblivionDuels duels) {
            return duels.getArenaManager();
        }

        throw new IllegalStateException(
                "ArenaManager is unavailable."
        );
    }

    private String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
