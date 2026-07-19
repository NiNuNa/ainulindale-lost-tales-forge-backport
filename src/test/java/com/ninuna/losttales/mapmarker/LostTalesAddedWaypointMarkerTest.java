package com.ninuna.losttales.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.world.map.waypoint.ELostTalesWaypoint;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import lotr.common.world.map.LOTRWaypoint;
import org.junit.Test;

/** Covers marker rendering for public and intentionally hidden Lost Tales waypoints. */
public class LostTalesAddedWaypointMarkerTest {
    @Test
    public void everyVisibleAddedWaypointHasDiscoverableMarkerMetadata() {
        LostTalesMapMarkerCatalog.reloadFromClasspath();
        Map<String, LostTalesMapMarkerDefinition> markersByWaypointCode = new LinkedHashMap<String, LostTalesMapMarkerDefinition>();
        for (LostTalesMapMarkerDefinition marker : LostTalesMapMarkerCatalog.getMarkers()) {
            if (marker.hasFastTravel() && marker.getFastTravelWaypointCode().length() > 0) {
                markersByWaypointCode.put(marker.getFastTravelWaypointCode(), marker);
            }
        }

        for (ELostTalesWaypoint addedWaypoint : ELostTalesWaypoint.values()) {
            LOTRWaypoint waypoint = addedWaypoint.getWaypoint();
            LostTalesMapMarkerDefinition marker = markersByWaypointCode.get(waypoint.getCodeName());
            if (addedWaypoint == ELostTalesWaypoint.LOSSOTH) {
                assertTrue("Lossoth control-zone anchor must stay hidden",
                        waypoint.isHidden());
                assertNull("Lossoth must not have a visible map marker",
                        marker);
                continue;
            }
            assertNotNull("Missing marker metadata for " + waypoint.getCodeName(), marker);
            assertTrue("Lost Tales waypoint must remain discoverable: " + waypoint.getCodeName(), marker.isDiscoverable());
            assertTrue("Lost Tales waypoint must follow its LOTR region: " + waypoint.getCodeName(), marker.requiresRegionUnlock());
            assertSame("Waypoint must use the region unlocked by its territory: "
                            + waypoint.getCodeName(),
                    expectedRegion(addedWaypoint), findRegion(waypoint));
            assertEquals(waypoint.getXCoord(), marker.getX(), 0.0D);
            assertEquals(waypoint.getZCoord(), marker.getZ(), 0.0D);
        }
    }

    @Test
    public void everyLotrWaypointExplicitlyRequiresRegionUnlock()
            throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "assets/losttales/map_marker/lotr_waypoints.json");
        assertNotNull(stream);
        int waypointMarkerCount = 0;
        try {
            JsonObject root = new JsonParser().parse(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonArray markers = root.getAsJsonArray("map_markers");
            for (JsonElement element : markers) {
                JsonObject marker = element.getAsJsonObject();
                waypointMarkerCount++;
                assertTrue("LOTR waypoint must explicitly declare its region policy: "
                                + marker.get("id").getAsString(),
                        marker.has("requiresRegionUnlock"));
                assertTrue("LOTR waypoint must use marker discovery: "
                                + marker.get("id").getAsString(),
                        marker.get("isDiscoverable").getAsBoolean());
                assertFalse("LOTR waypoint must be visible before discovery: "
                                + marker.get("id").getAsString(),
                        marker.get("hiddenUntilDiscovered").getAsBoolean());
                assertTrue("LOTR waypoint must follow its region: "
                                + marker.get("id").getAsString(),
                        marker.get("requiresRegionUnlock").getAsBoolean());
            }
        } finally {
            stream.close();
        }
        assertTrue("Expected bundled LOTR waypoint markers",
                waypointMarkerCount > 0);
    }

    @Test
    public void lossothUsesHiddenInternalAnchorWithoutMapMarker() {
        LostTalesMapMarkerCatalog.reloadFromClasspath();
        ELostTalesWaypoint.values();

        assertNull(LostTalesMapMarkerCatalog.getMarker(
                "lotr:waypoint:lossoth"));
        LOTRWaypoint waypoint = LOTRWaypoint.waypointForName("LOSSOTH");
        assertNotNull(waypoint);
        assertTrue(waypoint.isHidden());
    }

    private static LOTRWaypoint.Region expectedRegion(
            ELostTalesWaypoint waypoint) {
        switch (waypoint) {
            case SUN_ELVES:
                return LOTRWaypoint.Region.RHUN;
            case MOON_ELVES:
            case MOON_ELVES_2:
                return LOTRWaypoint.Region.FORODWAITH;
            case OROCARNI:
                return LOTRWaypoint.Region.RED_MOUNTAINS;
            case ODANE:
            case ODANE_MOUNTAINS:
            case ODANE_PORT:
            case ODANE_EASTWATCH:
                return ELostTalesWaypoint.Region.ODANE.getRegion();
            default:
                throw new AssertionError(waypoint);
        }
    }

    private static LOTRWaypoint.Region findRegion(LOTRWaypoint waypoint) {
        for (LOTRWaypoint.Region region : LOTRWaypoint.Region.values()) {
            if (region != null && region.waypoints != null
                    && region.waypoints.contains(waypoint)) {
                return region;
            }
        }
        return null;
    }

}
