package com.ninuna.losttales.world.map.waypoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import lotr.common.LOTRDimension;
import lotr.common.world.map.LOTRWaypoint;
import org.junit.Test;

/** Catalog-wide coverage for generated and native waypoint-backed markers. */
public final class LostTalesMapMarkerWaypointRegistryTest {
    @Test
    public void townHasOneCoherentPublicFastTravelState() {
        LostTalesMapMarkerDefinition town =
                LostTalesMapMarkerCatalog.getMarker(
                        "losttales:test_town");

        assertNotNull(town);
        assertTrue(town.hasFastTravel());
        assertFalse(town.isDiscoverable());
        assertFalse(town.isHiddenUntilDiscovered());
        assertTrue(town.requiresRegionUnlock());
        assertFalse(LostTalesMapMarkerWaypointUnlockHelper
                .shouldUnlockPrivateRegion(town, true, false));
        assertTrue(LostTalesMapMarkerWaypointUnlockHelper
                .shouldUnlockPrivateRegion(town, true, true));
    }

    @Test
    public void everyFastTravelMarkerHasARegistrationStrategy() {
        // Register Lost Tales' additional enum entries before resolving the
        // codes stored by their bundled marker definitions.
        ELostTalesWaypoint.values();
        LostTalesMapMarkerCatalog.reloadFromClasspath();
        int checked = 0;
        for (LostTalesMapMarkerDefinition marker
                : LostTalesMapMarkerCatalog.getMarkers()) {
            if (!marker.hasFastTravel()) {
                continue;
            }
            assertEquals("Fast travel is Middle-earth-only: "
                            + marker.getId(),
                    LOTRDimension.MIDDLE_EARTH.dimensionID,
                    marker.getDimensionId());
            if (LostTalesMapMarkerWaypointRegistry
                    .isExistingLotrWaypointMarker(marker)) {
                assertTrue("Existing LOTR waypoint marker needs a code: "
                                + marker.getId(),
                        marker.getFastTravelWaypointCode().length() > 0);
                assertNotNull("Waypoint code must resolve: " + marker.getId(),
                        LOTRWaypoint.waypointForName(
                                marker.getFastTravelWaypointCode()));
                assertSame("Registry must bind the exact existing waypoint: "
                                + marker.getId(),
                        LOTRWaypoint.waypointForName(
                                marker.getFastTravelWaypointCode()),
                        LostTalesMapMarkerWaypointRegistry
                                .resolveExistingWaypoint(marker));
            }
            checked++;
        }
        assertTrue("Expected bundled fast-travel markers", checked > 0);
    }

    @Test
    public void everyGeneratedWaypointMatchesItsMarkerInteractionState() {
        LostTalesMapMarkerCatalog.reloadFromClasspath();
        int checked = 0;
        for (LostTalesMapMarkerDefinition marker
                : LostTalesMapMarkerCatalog.getMarkers()) {
            if (!marker.hasFastTravel()
                    || LostTalesMapMarkerWaypointRegistry
                    .isExistingLotrWaypointMarker(marker)) {
                continue;
            }
            assertEquals(
                    "Only discovery-gated generated waypoints may be hidden: "
                            + marker.getId(),
                    marker.isDiscoverable(),
                    LostTalesMapMarkerWaypointRegistry
                            .shouldHideGeneratedWaypoint(marker));
            if (marker.requiresRegionUnlock()) {
                assertFalse("A locked location region must keep the fallback "
                                + "waypoint locked: " + marker.getId(),
                        LostTalesMapMarkerWaypointUnlockHelper
                                .shouldUnlockPrivateRegion(
                                        marker, true, false));
            }
            if (marker.isDiscoverable()) {
                assertFalse("An undiscovered marker must keep the fallback "
                                + "waypoint locked: " + marker.getId(),
                        LostTalesMapMarkerWaypointUnlockHelper
                                .shouldUnlockPrivateRegion(
                                        marker, false, true));
            }
            checked++;
        }
        assertEquals("Unexpected generated waypoint count", 8, checked);
    }

    @Test
    public void nativeWaypointMarkersUseTheirExactNativeRegion() {
        LostTalesMapMarkerDefinition hobbiton =
                LostTalesMapMarkerCatalog.getMarker(
                        "lotr:waypoint:hobbiton");
        assertNotNull(hobbiton);
        LOTRWaypoint waypoint = LOTRWaypoint.waypointForName(
                hobbiton.getFastTravelWaypointCode());
        assertNotNull(waypoint);

        LOTRWaypoint.Region expected = null;
        for (LOTRWaypoint.Region region : LOTRWaypoint.Region.values()) {
            if (region != null && region.waypoints != null
                    && region.waypoints.contains(waypoint)) {
                expected = region;
                break;
            }
        }
        assertNotNull(expected);
        assertSame(expected,
                LostTalesMapMarkerRegionResolver.resolve(hobbiton));
    }

}
