
package com.oblivion.duels;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public final class DuelArena {

    private final String name;
    private final UUID worldId;
    private int teamSize;

    private Location corner1;
    private Location corner2;

    private Location teamASpawn;
    private Location teamBSpawn;
    private Location spectatorSpawn;
    private Location exitSpawn;

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

        // Default size for newly created arenas.
        this.teamSize = 1;
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

    public void setTeamSize(int teamSize) {
        if (teamSize < 1 || teamSize > 5) {
            throw new IllegalArgumentException(
                    "Team size must be between 1 and 5."
            );
        }

        this.teamSize = teamSize;
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

    public Location getExitSpawn() {
        return cloneLocation(exitSpawn);
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

    public void setExitSpawn(Location location) {
        this.exitSpawn = cloneLocation(location);
    }

    private Location cloneLocation(Location location) {
        return location == null ? null : location.clone();
    }

    public boolean contains(Location location) {
        if (location == null
                || location.getWorld() == null
                || !location.getWorld().getUID().equals(worldId)
                || !hasValidBounds()) {
            return false;
        }

        int minX = Math.min(
                corner1.getBlockX(),
                corner2.getBlockX()
        );

        int maxX = Math.max(
                corner1.getBlockX(),
                corner2.getBlockX()
        );

        int minY = Math.min(
                corner1.getBlockY(),
                corner2.getBlockY()
        );

        int maxY = Math.max(
                corner1.getBlockY(),
                corner2.getBlockY()
        );

        int minZ = Math.min(
                corner1.getBlockZ(),
                corner2.getBlockZ()
        );

        int maxZ = Math.max(
                corner1.getBlockZ(),
                corner2.getBlockZ()
        );

        return location.getBlockX() >= minX
                && location.getBlockX() <= maxX
                && location.getBlockY() >= minY
                && location.getBlockY() <= maxY
                && location.getBlockZ() >= minZ
                && location.getBlockZ() <= maxZ;
    }

    public boolean hasValidBounds() {
        return sameWorld(corner1)
                && sameWorld(corner2);
    }

    public boolean isReady() {
        return teamSize >= 1
                && teamSize <= 5
                && hasValidBounds()
                && sameWorld(teamASpawn)
                && sameWorld(teamBSpawn)
                && sameWorld(spectatorSpawn)
                && contains(teamASpawn)
                && contains(teamBSpawn);
    }

    private boolean sameWorld(Location location) {
        return location != null
                && location.getWorld() != null
                && location.getWorld().getUID().equals(worldId);
    }
}
