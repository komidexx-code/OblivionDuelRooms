
package com.oblivion.duels;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ArenaManager {

    private final JavaPlugin plugin;

    private final Map<String, DuelArena> arenas =
            new HashMap<>();

    private final Set<String> occupiedArenas =
            new HashSet<>();

    public ArenaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadArenas();
    }

    public boolean createArena(String name, World world) {
        if (name == null || name.isBlank() || world == null) {
            return false;
        }

        DuelArena arena;

        try {
            arena = new DuelArena(name, world);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        if (!isValidTeamSize(arena.getTeamSize())) {
            return false;
        }

        String key = name.toLowerCase(Locale.ROOT);

        if (arenas.containsKey(key)) {
            return false;
        }

        arenas.put(key, arena);
        return true;
    }

    public boolean registerArena(DuelArena arena) {
        if (arena == null
                || !arena.isReady()
                || !isValidTeamSize(arena.getTeamSize())) {
            return false;
        }

        String key = arena.getName().toLowerCase(Locale.ROOT);

        if (arenas.containsKey(key)) {
            return false;
        }

        arenas.put(key, arena);
        saveArena(arena);

        return true;
    }

    private boolean isValidTeamSize(int teamSize) {
        return teamSize >= 1 && teamSize <= 5;
    }

    public DuelArena getArena(String name) {
        if (name == null) {
            return null;
        }

        return arenas.get(name.toLowerCase(Locale.ROOT));
    }

    public DuelArena getAvailableArena(int teamSize) {
        if (!isValidTeamSize(teamSize)) {
            return null;
        }

        for (Map.Entry<String, DuelArena> entry
                : arenas.entrySet()) {

            DuelArena arena = entry.getValue();

            if (arena.getTeamSize() == teamSize
                    && arena.isReady()
                    && !occupiedArenas.contains(entry.getKey())) {
                return arena;
            }
        }

        return null;
    }

    public DuelArena getAvailableArena() {
        for (Map.Entry<String, DuelArena> entry
                : arenas.entrySet()) {

            DuelArena arena = entry.getValue();

            if (arena.isReady()
                    && !occupiedArenas.contains(entry.getKey())) {
                return arena;
            }
        }

        return null;
    }

    public boolean reserveArena(String name) {
        if (name == null) {
            return false;
        }

        String key = name.toLowerCase(Locale.ROOT);
        DuelArena arena = arenas.get(key);

        if (arena == null
                || !arena.isReady()
                || occupiedArenas.contains(key)) {
            return false;
        }

        occupiedArenas.add(key);
        return true;
    }

    public void releaseArena(String name) {
        if (name != null) {
            occupiedArenas.remove(
                    name.toLowerCase(Locale.ROOT)
            );
        }
    }

    public void saveArena(DuelArena arena) {
        if (arena == null
                || !arena.isReady()
                || !isValidTeamSize(arena.getTeamSize())) {
            return;
        }

        String path = "arenas." + arena.getName();
        World world = Bukkit.getWorld(arena.getWorldId());

        if (world == null) {
            plugin.getLogger().warning(
                    "Cannot save arena: world is unavailable."
            );
            return;
        }

        plugin.getConfig().set(
                path + ".world",
                world.getName()
        );

        saveLocation(path + ".corner1", arena.getCorner1());
        saveLocation(path + ".corner2", arena.getCorner2());
        saveLocation(path + ".team-a-spawn", arena.getTeamASpawn());
        saveLocation(path + ".team-b-spawn", arena.getTeamBSpawn());
        saveLocation(
                path + ".spectator-spawn",
                arena.getSpectatorSpawn()
        );

        plugin.saveConfig();
    }

    private void saveLocation(
            String path,
            Location location
    ) {
        plugin.getConfig().set(path + ".x", location.getX());
        plugin.getConfig().set(path + ".y", location.getY());
        plugin.getConfig().set(path + ".z", location.getZ());
        plugin.getConfig().set(path + ".yaw", location.getYaw());
        plugin.getConfig().set(path + ".pitch", location.getPitch());
    }

    private Location loadLocation(
            ConfigurationSection section,
            World world
    ) {
        if (section == null || world == null) {
            return null;
        }

        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
    }

    public void loadArenas() {
        arenas.clear();
        occupiedArenas.clear();

        ConfigurationSection section =
                plugin.getConfig().getConfigurationSection("arenas");

        if (section == null) {
            return;
        }

        for (String name : section.getKeys(false)) {
            ConfigurationSection arenaSection =
                    section.getConfigurationSection(name);

            if (arenaSection == null) {
                continue;
            }

            String worldName =
                    arenaSection.getString("world");

            World world = worldName == null
                    ? null
                    : Bukkit.getWorld(worldName);

            if (world == null) {
                plugin.getLogger().warning(
                        "Arena '" + name
                                + "' was skipped because its world "
                                + "is unavailable."
                );
                continue;
            }

            DuelArena arena;

            try {
                arena = new DuelArena(name, world);
            } catch (IllegalArgumentException exception) {
                continue;
            }

            if (!isValidTeamSize(arena.getTeamSize())) {
                plugin.getLogger().warning(
                        "Arena '" + name
                                + "' was skipped because its name "
                                + "does not specify a valid match size."
                );
                continue;
            }

            arena.setCorner1(loadLocation(
                    arenaSection.getConfigurationSection("corner1"),
                    world
            ));

            arena.setCorner2(loadLocation(
                    arenaSection.getConfigurationSection("corner2"),
                    world
            ));

            arena.setTeamASpawn(loadLocation(
                    arenaSection.getConfigurationSection("team-a-spawn"),
                    world
            ));

            arena.setTeamBSpawn(loadLocation(
                    arenaSection.getConfigurationSection("team-b-spawn"),
                    world
            ));

            arena.setSpectatorSpawn(loadLocation(
                    arenaSection.getConfigurationSection(
                            "spectator-spawn"
                    ),
                    world
            ));

            if (arena.isReady()) {
                arenas.put(
                        name.toLowerCase(Locale.ROOT),
                        arena
                );
            } else {
                plugin.getLogger().warning(
                        "Arena '" + name
                                + "' was skipped because it is incomplete."
                );
            }
        }

        plugin.getLogger().info(
                "Loaded " + arenas.size() + " duel arena(s)."
        );
    }

    public int getArenaCount() {
        return arenas.size();
    }

    public int getAvailableArenaCount(int teamSize) {
        if (!isValidTeamSize(teamSize)) {
            return 0;
        }

        int count = 0;

        for (Map.Entry<String, DuelArena> entry
                : arenas.entrySet()) {

            DuelArena arena = entry.getValue();

            if (arena.getTeamSize() == teamSize
                    && arena.isReady()
                    && !occupiedArenas.contains(entry.getKey())) {
                count++;
            }
        }

        return count;
    }

    public boolean isOccupied(String name) {
        return name != null
                && occupiedArenas.contains(
                        name.toLowerCase(Locale.ROOT)
                );
    }
}
