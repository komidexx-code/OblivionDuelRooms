
package com.oblivion.duels;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ArenaManager {

    private final JavaPlugin plugin;

    private final Map<String, DuelArena> arenas =
            new LinkedHashMap<>();

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

        String key = normalizeName(name);

        if (arenas.containsKey(key)) {
            return false;
        }

        arenas.put(key, arena);
        return true;
    }

    public boolean registerArena(DuelArena arena) {
        if (arena == null
                || !arena.isReady()
                || !isValidTeamSize(arena.getTeamSize())
                || arena.getName() == null
                || arena.getName().isBlank()) {
            return false;
        }

        String key = normalizeName(arena.getName());
        DuelArena existing = arenas.get(key);

        if (existing != null
                && existing != arena
                && occupiedArenas.contains(key)) {
            return false;
        }

        arenas.put(key, arena);
        saveArena(arena);

        return true;
    }

    public boolean deleteArena(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        String key = normalizeName(name);

        if (occupiedArenas.contains(key)) {
            return false;
        }

        if (!arenas.containsKey(key)) {
            return false;
        }

        arenas.remove(key);
        plugin.getConfig().set("arenas." + name, null);
        plugin.saveConfig();

        return true;
    }

    public boolean arenaExists(String name) {
        return name != null
                && arenas.containsKey(normalizeName(name));
    }

    public List<String> getArenaNames() {
        List<String> names = new ArrayList<>();

        for (DuelArena arena : arenas.values()) {
            names.add(arena.getName());
        }

        return names;
    }

    public boolean isValidTeamSize(int teamSize) {
        return teamSize >= 1 && teamSize <= 5;
    }

    private String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public DuelArena getArena(String name) {
        if (name == null) {
            return null;
        }

        return arenas.get(normalizeName(name));
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
        if (name == null || name.isBlank()) {
            return false;
        }

        String key = normalizeName(name);
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
            occupiedArenas.remove(normalizeName(name));
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
                    "Cannot save arena '" + arena.getName()
                            + "': world is unavailable."
            );
            return;
        }

        plugin.getConfig().set(path + ".world", world.getName());

        saveLocation(path + ".corner1", arena.getCorner1());
        saveLocation(path + ".corner2", arena.getCorner2());
        saveLocation(path + ".team-a-spawn", arena.getTeamASpawn());
        saveLocation(path + ".team-b-spawn", arena.getTeamBSpawn());
        saveLocation(
                path + ".spectator-spawn",
                arena.getSpectatorSpawn()
        );
        saveLocation(
                path + ".exit-spawn",
                arena.getExitSpawn()
        );

        plugin.saveConfig();
    }

    private void saveLocation(String path, Location location) {
        if (location == null) {
            plugin.getConfig().set(path, null);
            return;
        }

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

    public void reloadArenas() {
        ConfigurationSection section =
                plugin.getConfig().getConfigurationSection("arenas");

        if (section == null) {
            removeUnoccupiedArenas(new HashSet<>());
            return;
        }

        Set<String> loadedKeys = new HashSet<>();

        for (String name : section.getKeys(false)) {
            String key = normalizeName(name);

            if (occupiedArenas.contains(key)) {
                loadedKeys.add(key);
                continue;
            }

            ConfigurationSection arenaSection =
                    section.getConfigurationSection(name);

            if (arenaSection == null) {
                continue;
            }

            String worldName = arenaSection.getString("world");

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
                plugin.getLogger().warning(
                        "Arena '" + name
                                + "' has an invalid name and was skipped."
                );
                continue;
            }

            if (!isValidTeamSize(arena.getTeamSize())) {
                continue;
            }

            loadArenaLocations(arena, arenaSection, world);

            if (arena.isReady()) {
                arenas.put(key, arena);
                loadedKeys.add(key);
            } else {
                plugin.getLogger().warning(
                        "Arena '" + name
                                + "' is incomplete and was skipped."
                );
            }
        }

        removeUnoccupiedArenas(loadedKeys);

        plugin.getLogger().info(
                "Reloaded arena definitions. Loaded "
                        + arenas.size() + " arena(s)."
        );
    }

    private void loadArenaLocations(
            DuelArena arena,
            ConfigurationSection section,
            World world
    ) {
        arena.setCorner1(loadLocation(
                section.getConfigurationSection("corner1"),
                world
        ));

        arena.setCorner2(loadLocation(
                section.getConfigurationSection("corner2"),
                world
        ));

        arena.setTeamASpawn(loadLocation(
                section.getConfigurationSection("team-a-spawn"),
                world
        ));

        arena.setTeamBSpawn(loadLocation(
                section.getConfigurationSection("team-b-spawn"),
                world
        ));

        arena.setSpectatorSpawn(loadLocation(
                section.getConfigurationSection("spectator-spawn"),
                world
        ));

        arena.setExitSpawn(loadLocation(
                section.getConfigurationSection("exit-spawn"),
                world
        ));
    }

    private void removeUnoccupiedArenas(Set<String> loadedKeys) {
        arenas.keySet().removeIf(key ->
                !occupiedArenas.contains(key)
                        && !loadedKeys.contains(key)
        );
    }

    public void loadArenas() {
        Set<String> previouslyOccupied =
                new HashSet<>(occupiedArenas);

        arenas.clear();

        ConfigurationSection section =
                plugin.getConfig().getConfigurationSection("arenas");

        if (section == null) {
            occupiedArenas.clear();
            occupiedArenas.addAll(previouslyOccupied);
            return;
        }

        for (String name : section.getKeys(false)) {
            String key = normalizeName(name);

            if (previouslyOccupied.contains(key)) {
                continue;
            }

            ConfigurationSection arenaSection =
                    section.getConfigurationSection(name);

            if (arenaSection == null) {
                continue;
            }

            String worldName = arenaSection.getString("world");

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
                continue;
            }

            loadArenaLocations(arena, arenaSection, world);

            if (arena.isReady()) {
                arenas.put(key, arena);
            } else {
                plugin.getLogger().warning(
                        "Arena '" + name
                                + "' was skipped because it is incomplete."
                );
            }
        }

        occupiedArenas.clear();
        occupiedArenas.addAll(previouslyOccupied);

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
                && occupiedArenas.contains(normalizeName(name));
    }
}
