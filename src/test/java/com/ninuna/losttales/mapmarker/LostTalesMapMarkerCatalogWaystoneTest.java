package com.ninuna.losttales.mapmarker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LostTalesMapMarkerCatalogWaystoneTest {

    @Test
    public void legacyCustomPresetDefaultsToWaystoneHouse() {
        LostTalesMapMarkerDefinition marker = LostTalesMapMarkerCatalog.parseMarker(
                markerJson("losttales:test"), LostTalesMapMarkerSource.CUSTOM_PRESET);

        assertTrue(marker.hasWaystone());
        assertEquals(LostTalesMapMarkerDefinition.DEFAULT_WAYSTONE_STRUCTURE,
                marker.getWaystoneStructureType());
    }

    @Test
    public void legacyLotrPresetDefaultsToNoWaystone() {
        LostTalesMapMarkerDefinition marker = LostTalesMapMarkerCatalog.parseMarker(
                markerJson("lotr:waypoint:test"),
                LostTalesMapMarkerSource.LOTR_ADAPTER);

        assertFalse(marker.hasWaystone());
        assertEquals("", marker.getWaystoneStructureType());
    }

    @Test
    public void rejectsNonFiniteCoordinatesAndInvalidStructureIds() {
        JsonObject nonFinite = markerJson("losttales:bad_coordinate");
        nonFinite.addProperty("x", Double.NaN);
        assertNull(LostTalesMapMarkerCatalog.parseMarker(
                nonFinite, LostTalesMapMarkerSource.CUSTOM_PRESET));

        JsonObject invalidStructure = markerJson("losttales:bad_structure");
        invalidStructure.addProperty("structureType", "not namespaced");
        assertNull(LostTalesMapMarkerCatalog.parseMarker(
                invalidStructure, LostTalesMapMarkerSource.CUSTOM_PRESET));
    }

    @Test
    public void runtimeAndQuestDynamicDefinitionsNeverDefaultToWaystones() {
        LostTalesMapMarkerDefinition marker =
                new LostTalesMapMarkerDefinition(
                        "losttales:quest/runtime", "Quest Target",
                        "quest", "white", "Quest", false,
                        0, 1.0D, 64.0D, 2.0D,
                        128.0D, 8.0D, true);

        assertEquals(LostTalesMapMarkerSource.QUEST_DYNAMIC,
                marker.getSource());
        assertFalse(marker.hasWaystone());
        assertEquals("", marker.getWaystoneStructureType());
    }

    @Test
    public void omittedYUsesGroundResolutionButExplicitYIsPreserved() {
        JsonObject automaticJson = markerJson("losttales:automatic_y");
        automaticJson.remove("y");
        LostTalesMapMarkerDefinition automatic =
                LostTalesMapMarkerCatalog.parseMarker(
                        automaticJson,
                        LostTalesMapMarkerSource.CUSTOM_PRESET);
        assertNotNull(automatic);
        assertFalse(automatic.hasExplicitY());
        assertEquals(LostTalesMapMarkerDefinition.AUTOMATIC_Y,
                automatic.getY(), 0.0D);

        JsonObject explicitJson = markerJson("losttales:explicit_y");
        explicitJson.addProperty("y", 93.5D);
        LostTalesMapMarkerDefinition explicit =
                LostTalesMapMarkerCatalog.parseMarker(
                        explicitJson,
                        LostTalesMapMarkerSource.CUSTOM_PRESET);
        assertNotNull(explicit);
        assertTrue(explicit.hasExplicitY());
        assertEquals(93.5D, explicit.getY(), 0.0D);
    }

    @Test
    public void bundledMarkersDeclareWaystonePolicyExplicitly()
            throws Exception {
        String[] files = new String[] {
                "towns", "settlements", "cities", "forts",
                "camps", "caves", "lotr_waypoints"
        };
        for (String file : files) {
            String path = "assets/losttales/map_markers/"
                    + file + ".json";
            InputStream stream = getClass().getClassLoader()
                    .getResourceAsStream(path);
            assertNotNull(path, stream);
            try {
                JsonObject root = new JsonParser().parse(
                        new InputStreamReader(
                                stream, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                JsonArray markers = root.getAsJsonArray("map_markers");
                assertNotNull(path, markers);
                for (JsonElement element : markers) {
                    JsonObject marker = element.getAsJsonObject();
                    String id = marker.has("id")
                            ? marker.get("id").getAsString() : path;
                    assertFalse(id
                                    + " should currently use automatic ground Y",
                            marker.has("y"));
                    assertTrue(id + " must explicitly declare hasWaystone",
                            marker.has("hasWaystone"));
                    boolean hasWaystone =
                            marker.get("hasWaystone").getAsBoolean();
                    if ("lotr_waypoints".equals(file)) {
                        assertTrue(id
                                + " must generate a physical waystone",
                                hasWaystone);
                    }
                    assertTrue(id
                                    + " must declare waystoneStructureType",
                            marker.has("waystoneStructureType"));
                    String structure = marker.get(
                            "waystoneStructureType").getAsString();
                    assertTrue(id
                                    + " must use a namespaced structure",
                            structure.matches(
                                    "[a-z0-9_.-]+:[a-z0-9_./-]+"));
                }
            } finally {
                stream.close();
            }
        }
    }

    private static JsonObject markerJson(String id) {
        JsonObject marker = new JsonObject();
        marker.addProperty("id", id);
        marker.addProperty("name", "Test");
        marker.addProperty("x", 1.0D);
        marker.addProperty("y", 64.0D);
        marker.addProperty("z", 2.0D);
        return marker;
    }
}
