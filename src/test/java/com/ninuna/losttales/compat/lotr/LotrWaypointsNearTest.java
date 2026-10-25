package com.ninuna.losttales.compat.lotr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lotr.common.world.map.LOTRWaypoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The LOTR waypoints a look finds (PROG-01, Lore): those within the
 * radius, named as deed files name them, the grid finding just what a
 * look at every waypoint would.
 */
public final class LotrWaypointsNearTest {
    private static final double RADIUS = 64.0D;

    @Test
    public void standingAtBreeFindsBree() {
        LotrWaypointsNear.Grid grid = LotrWaypointsNear.Grid.of(
                Arrays.asList(LOTRWaypoint.values()));
        double x = LOTRWaypoint.BREE.getXCoord() + 0.5D;
        double z = LOTRWaypoint.BREE.getZCoord() + 0.5D;
        assertTrue(grid.near(x, z, RADIUS).contains("lotr:BREE"));
        assertTrue("63 blocks off is near", grid.near(x + 63.0D, z, RADIUS).contains("lotr:BREE"));
        assertFalse("65 blocks off is not", grid.near(x, z - 65.0D, RADIUS).contains("lotr:BREE"));
    }

    @Test
    public void noRadiusFindsNothing() {
        LotrWaypointsNear.Grid grid = LotrWaypointsNear.Grid.of(
                Arrays.asList(LOTRWaypoint.values()));
        double x = LOTRWaypoint.BREE.getXCoord() + 0.5D;
        double z = LOTRWaypoint.BREE.getZCoord() + 0.5D;
        assertEquals(Collections.<String>emptyList(), grid.near(x, z, 0.0D));
    }

    @Test
    public void theGridFindsWhatALookAtEveryWaypointWould() {
        List<LOTRWaypoint> waypoints = Arrays.asList(LOTRWaypoint.values());
        LotrWaypointsNear.Grid grid = LotrWaypointsNear.Grid.of(waypoints);
        double[][] offsets = { { 0.0D, 0.0D }, { 40.0D, -50.0D }, { -63.0D, 0.0D },
                { 130.0D, 129.0D }, { -255.0D, 300.0D }, { 1000.0D, -1000.0D } };
        double[] radii = { RADIUS, 300.0D, 1024.0D };
        for (LOTRWaypoint at : waypoints) {
            for (double[] offset : offsets) {
                double x = at.getXCoord() + 0.5D + offset[0];
                double z = at.getZCoord() + 0.5D + offset[1];
                for (double radius : radii) {
                    List<String> found = grid.near(x, z, radius);
                    Collections.sort(found);
                    assertEquals(at.name() + " " + Arrays.toString(offset) + " within " + radius,
                            everyWaypointWithin(waypoints, x, z, radius), found);
                }
            }
        }
    }

    private static List<String> everyWaypointWithin(List<LOTRWaypoint> waypoints, double x,
                                                    double z, double radius) {
        List<String> within = new ArrayList<String>();
        for (LOTRWaypoint waypoint : waypoints) {
            double dx = x - (waypoint.getXCoord() + 0.5D);
            double dz = z - (waypoint.getZCoord() + 0.5D);
            if (dx * dx + dz * dz <= radius * radius) {
                within.add(LotrDeedPlaces.PREFIX + waypoint.name());
            }
        }
        Collections.sort(within);
        return within;
    }
}
