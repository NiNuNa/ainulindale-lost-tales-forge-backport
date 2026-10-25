package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.util.LostTalesLog;
import com.ninuna.losttales.world.map.waypoint.LostTalesMapMarkerWaypointRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lotr.common.LOTRDimension;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.entity.player.EntityPlayer;

/**
 * The LOTR waypoints a player stands near (PROG-01, Lore), named as deed
 * files name them ({@code lotr:BREE}). LOTR's waypoints lie fixed in
 * Middle-earth, so they are filed once in a grid of cells and a look reads
 * only the cells round the player. The waypoints LOTR holds for the mod's
 * own map markers are left out: those count as markers. The grid holds
 * nothing of a world, so it is never cleared.
 */
public final class LotrWaypointsNear {
    /** The side of a grid cell, in blocks. */
    static final int CELL = 256;
    /** The farthest a look reaches, as a deed's places do at most. */
    static final double MAX_RADIUS = 1024.0D;

    private LotrWaypointsNear() {}

    /** The waypoints within {@code radius} blocks of the player; none outside Middle-earth. */
    public static List<String> near(EntityPlayer player, double radius) {
        if (player == null || player.dimension != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return Collections.<String>emptyList();
        }
        return Filed.GRID.near(player.posX, player.posZ, radius);
    }

    /** Filed at the first look, once every mod has added its waypoints. */
    private static final class Filed {
        static final Grid GRID = Grid.of(lotrWaypoints());
    }

    /**
     * Every LOTR waypoint but those LOTR holds for the mod's own map
     * markers. A waypoint that cannot be told apart is left out, and the
     * log says how many were.
     */
    static List<LOTRWaypoint> lotrWaypoints() {
        List<LOTRWaypoint> waypoints = new ArrayList<LOTRWaypoint>();
        int unread = 0;
        RuntimeException last = null;
        for (LOTRWaypoint waypoint : LOTRWaypoint.values()) {
            try {
                if (waypoint != null && !standsForMarker(waypoint)) {
                    waypoints.add(waypoint);
                }
            } catch (RuntimeException failed) {
                unread++;
                last = failed;
            }
        }
        if (unread > 0) {
            LostTalesLog.warning("%d LOTR waypoints could not be told apart from the mod's map"
                    + " markers and count for no Lore (%s)", Integer.valueOf(unread), last);
        }
        return waypoints;
    }

    /** Whether the mod made the waypoint for a map marker of its own, which counts as a marker. */
    private static boolean standsForMarker(LOTRWaypoint waypoint) {
        LostTalesMapMarkerDefinition marker =
                LostTalesMapMarkerWaypointRegistry.getMarkerForWaypoint(waypoint);
        return marker != null
                && !LostTalesMapMarkerWaypointRegistry.isExistingLotrWaypointMarker(marker);
    }

    /** Waypoints filed by the cell they stand in. */
    static final class Grid {
        private final Map<Long, List<Spot>> cells = new HashMap<Long, List<Spot>>();

        static Grid of(List<LOTRWaypoint> waypoints) {
            Grid grid = new Grid();
            for (LOTRWaypoint waypoint : waypoints) {
                grid.file(LotrDeedPlaces.PREFIX + waypoint.name(),
                        waypoint.getXCoord() + 0.5D, waypoint.getZCoord() + 0.5D);
            }
            return grid;
        }

        private void file(String id, double x, double z) {
            Long key = Long.valueOf(key(cell(x), cell(z)));
            List<Spot> spots = this.cells.get(key);
            if (spots == null) {
                spots = new ArrayList<Spot>(2);
                this.cells.put(key, spots);
            }
            spots.add(new Spot(id, x, z));
        }

        /** The waypoints within {@code radius} blocks of x and z, the radius held to {@link #MAX_RADIUS}. */
        List<String> near(double x, double z, double radius) {
            List<String> near = new ArrayList<String>();
            if (!(radius > 0.0D)) {
                return near;
            }
            double reach = Math.min(radius, MAX_RADIUS);
            double reachSquared = reach * reach;
            int fromX = cell(x - reach);
            int toX = cell(x + reach);
            int fromZ = cell(z - reach);
            int toZ = cell(z + reach);
            for (int cellX = fromX; cellX <= toX; cellX++) {
                for (int cellZ = fromZ; cellZ <= toZ; cellZ++) {
                    List<Spot> spots = this.cells.get(Long.valueOf(key(cellX, cellZ)));
                    if (spots == null) {
                        continue;
                    }
                    for (Spot spot : spots) {
                        double dx = x - spot.x;
                        double dz = z - spot.z;
                        if (dx * dx + dz * dz <= reachSquared) {
                            near.add(spot.id);
                        }
                    }
                }
            }
            return near;
        }

        private static int cell(double coordinate) {
            return (int) Math.floor(coordinate / CELL);
        }

        private static long key(int cellX, int cellZ) {
            return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
        }
    }

    private static final class Spot {
        final String id;
        final double x;
        final double z;

        Spot(String id, double x, double z) {
            this.id = id;
            this.x = x;
            this.z = z;
        }
    }
}
