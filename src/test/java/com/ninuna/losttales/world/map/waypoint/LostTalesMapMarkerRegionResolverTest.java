package com.ninuna.losttales.world.map.waypoint;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import lotr.common.LOTRDimension;
import lotr.common.world.map.LOTRWaypoint;
import org.junit.Test;

/** Regression coverage for region-gated non-discoverable waypoints. */
public final class LostTalesMapMarkerRegionResolverTest {
    @Test
    public void nonDiscoverableMarkerInheritsItsLocationRegion() {
        LostTalesMapMarkerDefinition marker = marker(false, true, true);
        LOTRWaypoint.Region expected =
                LostTalesMapMarkerRegionResolver.resolve(marker);

        assertNotNull(expected);
        assertSame(expected,
                LostTalesMapMarkerWaypointRegistry
                        .resolveInheritedRegion(marker));
    }

    @Test
    public void discoverableMarkerKeepsPrivateDiscoveryRegion() {
        assertNull(LostTalesMapMarkerWaypointRegistry
                .resolveInheritedRegion(marker(true, true, true)));
    }

    @Test
    public void regionIndependentMarkerDoesNotInheritNativeRegion() {
        LostTalesMapMarkerDefinition marker = marker(false, true, false);
        assertNull(LostTalesMapMarkerWaypointRegistry
                .resolveInheritedRegion(marker));
        assertTrue(
                LostTalesMapMarkerWaypointRegistry
                .usesPrivateRegion(marker));
    }

    @Test
    public void waypointRegionTakesPriorityOverCoordinateFallback() {
        LOTRWaypoint.Region hobbitonRegion = LostTalesMapMarkerRegionResolver
                .resolveWaypointRegion(LOTRWaypoint.HOBBITON);
        assertNotNull(hobbitonRegion);
        assertSame(hobbitonRegion, LostTalesMapMarkerRegionResolver.resolve(
                LOTRDimension.MIDDLE_EARTH.dimensionID,
                LOTRWaypoint.MINAS_TIRITH.getXCoord(),
                LOTRWaypoint.MINAS_TIRITH.getZCoord(),
                LOTRWaypoint.HOBBITON.getCodeName()));
    }

    private static LostTalesMapMarkerDefinition marker(
            boolean discoverable, boolean hiddenUntilDiscovered,
            boolean requiresRegionUnlock) {
        return new LostTalesMapMarkerDefinition(
                "lotr:waypoint:hobbiton",
                "Region policy test",
                "fort",
                "white",
                LostTalesMapMarkerDefinition.CATEGORY_POINT_OF_INTEREST,
                "Test marker",
                true,
                LOTRDimension.MIDDLE_EARTH.dimensionID,
                15.0D,
                64.0D,
                15.0D,
                180.0D,
                12.0D,
                hiddenUntilDiscovered,
                discoverable,
                requiresRegionUnlock);
    }
}
