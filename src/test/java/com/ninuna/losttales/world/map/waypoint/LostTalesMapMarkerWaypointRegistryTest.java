package com.ninuna.losttales.world.map.waypoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
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
                assertTrue("Existing LOTR waypoint marker needs an ID: "
                                + marker.getId(),
                        marker.getLotrWaypointId().length() > 0);
                assertNotNull("Waypoint ID must resolve: " + marker.getId(),
                        LOTRWaypoint.waypointForName(
                                marker.getLotrWaypointId()));
                assertSame("Registry must bind the exact existing waypoint: "
                                + marker.getId(),
                        LOTRWaypoint.waypointForName(
                                marker.getLotrWaypointId()),
                        LostTalesMapMarkerWaypointRegistry
                                .resolveExistingWaypoint(marker));
            }
            checked++;
        }
        assertTrue("Expected bundled fast-travel markers", checked > 0);
    }

    @Test
    public void everyLotrModWaypointHasExactlyOneBundledMarker()
            throws Exception {
        ELostTalesWaypoint.values();
        LostTalesMapMarkerCatalog.reloadFromClasspath();
        Map<String, Integer> markerCounts =
                new LinkedHashMap<String, Integer>();
        for (LostTalesMapMarkerDefinition marker
                : LostTalesMapMarkerCatalog.getMarkers()) {
            String code = marker.getLotrWaypointId();
            if (code.length() == 0) {
                continue;
            }
            Integer previous = markerCounts.get(code);
            markerCounts.put(code, Integer.valueOf(
                    previous == null ? 1 : previous.intValue() + 1));
        }

        int dependencyWaypointCount = 0;
        for (Field field : LOTRWaypoint.class.getFields()) {
            if (!field.isEnumConstant()
                    || field.getType() != LOTRWaypoint.class) {
                continue;
            }
            LOTRWaypoint waypoint = (LOTRWaypoint)field.get(null);
            assertEquals("Missing or duplicate marker for "
                            + waypoint.name(),
                    Integer.valueOf(1),
                    markerCounts.get(waypoint.name()));
            dependencyWaypointCount++;
        }
        assertEquals("Unexpected LOTR v36.15 waypoint count",
                274, dependencyWaypointCount);
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
                hobbiton.getLotrWaypointId());
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

    @Test
    public void disabledNativeMarkerStillMatchesForServerDenial() {
        LostTalesMapMarkerDefinition disabled =
                new LostTalesMapMarkerDefinition(
                        "lotr:waypoint:bree", "Bree",
                        "fort", "white", "Town", "",
                        false,
                        LOTRDimension.MIDDLE_EARTH.dimensionID,
                        LOTRWaypoint.BREE.getXCoord(),
                        LostTalesMapMarkerDefinition.AUTOMATIC_Y,
                        LOTRWaypoint.BREE.getZCoord(),
                        128.0D, 8.0D,
                        false, false, true);

        assertTrue(LostTalesMapMarkerWaypointRegistry
                .matchesWaypoint(disabled, LOTRWaypoint.BREE));
    }

}
