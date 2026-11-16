
package com.oblivion.duels;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ArenaRollbackManager implements Listener {

    private final JavaPlugin plugin;

    private final Map<String, Map<Location, BlockState>> snapshots =
            new HashMap<>();

    private final Map<String, Set<Location>> trackedBlocks =
            new HashMap<>();

    public ArenaRollbackManager(JavaPlugin plugin) {
        this.plugin = plugin;

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean startTracking(DuelArena arena) {

        if (arena == null || !arena.isReady()) {
            return false;
        }

        String key = normalize(arena.getName());

        if (snapshots.containsKey(key)) {
            return false;
        }

        Map<Location, BlockState> originalBlocks =
                new HashMap<>();

        snapshots.put(key, originalBlocks);
        trackedBlocks.put(key, new HashSet<>());

        return true;
    }

    private void recordBlock(Block block) {

        if (block == null || block.getWorld() == null) {
            return;
        }

        Location location = block.getLocation();

        for (String arenaName : snapshots.keySet()) {

            DuelArena arena =
                    getArenaManager().getArena(arenaName);

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
                original.put(key, block.getState());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        recordBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        recordBlock(event.getBlock());
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

        restoreNextBatch(blocks, 0, onComplete);
    }

    private void restoreNextBatch(
            List<Map.Entry<Location, BlockState>> blocks,
            int start,
            Runnable onComplete
    ) {

        World world = null;

        int end = Math.min(start + 500, blocks.size());

        for (int i = start; i < end; i++) {

            Map.Entry<Location, BlockState> entry =
                    blocks.get(i);

            Location location = entry.getKey();

            if (location.getWorld() == null) {
                continue;
            }

            world = location.getWorld();

            BlockState original = entry.getValue();

            if (!world.isChunkLoaded(
                    location.getBlockX() >> 4,
                    location.getBlockZ() >> 4
            )) {
                world.getChunkAt(
                        location.getBlockX() >> 4,
                        location.getBlockZ() >> 4
                ).load();
            }

            original.update(true, false);
        }

        if (end >= blocks.size()) {
            plugin.getLogger().info(
                    "Arena rollback completed for "
                            + blocks.size() + " block(s)."
            );

            if (onComplete != null) {
                onComplete.run();
            }

            return;
        }

        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> restoreNextBatch(
                        blocks,
                        end,
                        onComplete
                ),
                1L
        );
    }

    public void shutdown() {

        for (String arenaName :
                new HashSet<>(snapshots.keySet())) {

            DuelArena arena =
                    getArenaManager().getArena(arenaName);

            if (arena != null) {
                restoreArena(arena, null);
            }
        }

        snapshots.clear();
        trackedBlocks.clear();
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
        return name.toLowerCase(java.util.Locale.ROOT);
    }
}
