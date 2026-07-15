package com.ninuna.losttales.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

/** Ensures every waypoint added by Lost Tales participates in marker discovery rendering. */
public class LostTalesAddedWaypointMarkerTest {
    @Test
    public void everyAddedWaypointHasMatchingDiscoverableMarkerMetadata() {
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
            assertNotNull("Missing marker metadata for " + waypoint.getCodeName(), marker);
            assertTrue("Lost Tales waypoint must remain discoverable: " + waypoint.getCodeName(), marker.isDiscoverable());
            assertTrue("Lost Tales waypoint must follow its LOTR region: " + waypoint.getCodeName(), marker.requiresRegionUnlock());
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

}
