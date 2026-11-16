
package com.oblivion.duels;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DuelArena {

    private final String name;
    private final UUID worldId;
    private final int teamSize;

    private Location corner1;
    private Location corner2;

    private Location teamASpawn;
    private Location teamBSpawn;
    private Location spectatorSpawn;

    public DuelArena(String name, World world) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Arena name cannot be empty."
            );
        }

        if (world == null) {
            throw new IllegalArgumentException(
                    "Arena world cannot be null."
            );
        }

        this.name = name;
        this.worldId = world.getUID();
        this.teamSize = detectTeamSize(name);
    }

    private int detectTeamSize(String arenaName) {
        Matcher matcher = Pattern.compile(
                "^(1|2|3|4|5)v\\1(?:[-_ ]|$)",
                Pattern.CASE_INSENSITIVE
        ).matcher(arenaName);

        if (!matcher.find()) {
            return 0;
        }

        return Integer.parseInt(matcher.group(1));
    }

    public String getName() {
        return name;
    }

    public UUID getWorldId() {
        return worldId;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public Location getCorner1() {
        return cloneLocation(corner1);
    }

    public Location getCorner2() {
        return cloneLocation(corner2);
    }

    public Location getTeamASpawn() {
        return cloneLocation(teamASpawn);
    }

    public Location getTeamBSpawn() {
        return cloneLocation(teamBSpawn);
    }

    public Location getSpectatorSpawn() {
        return cloneLocation(spectatorSpawn);
    }

    public void setCorner1(Location location) {
        this.corner1 = cloneLocation(location);
    }

    public void setCorner2(Location location) {
        this.corner2 = cloneLocation(location);
    }

    public void setTeamASpawn(Location location) {
        this.teamASpawn = cloneLocation(location);
    }

    public void setTeamBSpawn(Location location) {
        this.teamBSpawn = cloneLocation(location);
    }

    public void setSpectatorSpawn(Location location) {
        this.spectatorSpawn = cloneLocation(location);
    }

    private Location cloneLocation(Location location) {
        return location == null ? null : location.clone();
    }

    public boolean contains(Location location) {
        if (location == null
                || location.getWorld() == null
                || !location.getWorld().getUID().equals(worldId)
                || corner1 == null
                || corner2 == null) {
            return false;
        }

        double minX = Math.min(corner1.getX(), corner2.getX());
        double maxX = Math.max(corner1.getX(), corner2.getX());

        double minY = Math.min(corner1.getY(), corner2.getY());
        double maxY = Math.max(corner1.getY(), corner2.getY());

        double minZ = Math.min(corner1.getZ(), corner2.getZ());
        double maxZ = Math.max(corner1.getZ(), corner2.getZ());

        return location.getX() >= minX
                && location.getX() <= maxX
                && location.getY() >= minY
                && location.getY() <= maxY
                && location.getZ() >= minZ
                && location.getZ() <= maxZ;
    }

    public boolean isReady() {
        return corner1 != null
                && corner2 != null
                && teamASpawn != null
                && teamBSpawn != null
                && spectatorSpawn != null
                && sameWorld(corner1)
                && sameWorld(corner2)
                && sameWorld(teamASpawn)
                && sameWorld(teamBSpawn)
                && sameWorld(spectatorSpawn);
    }

    private boolean sameWorld(Location location) {
        return location != null
                && location.getWorld() != null
                && location.getWorld().getUID().equals(worldId);
    }
}
