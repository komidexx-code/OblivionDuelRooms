
package com.oblivion.duels;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public final class DuelArena {

    private final String name;
    private final UUID worldId;

    private Location corner1;
    private Location corner2;

    private Location teamASpawn;
    private Location teamBSpawn;
    private Location spectatorSpawn;

    public DuelArena(String name, World world) {
        this.name = name;
        this.worldId = world.getUID();
    }

    public String getName() {
        return name;
    }

    public UUID getWorldId() {
        return worldId;
    }

    public Location getCorner1() {
        return corner1;
    }

    public Location getCorner2() {
        return corner2;
    }

    public Location getTeamASpawn() {
        return teamASpawn;
    }

    public Location getTeamBSpawn() {
        return teamBSpawn;
    }

    public Location getSpectatorSpawn() {
        return spectatorSpawn;
    }

    public void setCorner1(Location location) {
        this.corner1 = location.clone();
    }

    public void setCorner2(Location location) {
        this.corner2 = location.clone();
    }

    public void setTeamASpawn(Location location) {
        this.teamASpawn = location.clone();
    }

    public void setTeamBSpawn(Location location) {
        this.teamBSpawn = location.clone();
    }

    public void setSpectatorSpawn(Location location) {
        this.spectatorSpawn = location.clone();
    }

    public boolean contains(Location location) {
        if (location.getWorld() == null
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
                && spectatorSpawn != null;
    }
}
