package com.ninuna.losttales.mapmarker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@code /losttales reload markers} (DATA-11): the marker files are read,
 * bundled and the server's own, before anything is put in place, and then
 * put in place together, so the store seeds itself again from them.
 */
public final class LostTalesMapMarkerCatalogReloadTest {
    private static final Set<String> HOUSES =
            Collections.singleton("losttales:glowstone_house");

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void aReadChangesNothingUntilItIsPutInPlace() throws IOException {
        File folder = this.temporary.newFolder("map_markers");
        write(new File(folder, "mill.json"), file(marker("myserver:mill")));
        LostTalesMapMarkerCatalog.reloadFromClasspath();
        LostTalesMapMarkerCatalog.clearServerFiles();
        try {
            int before = LostTalesMapMarkerCatalog.revision();
            LostTalesMapMarkerCatalog.Read read = LostTalesMapMarkerCatalog.read(folder, HOUSES);

            assertEquals(Collections.<String>emptyList(), read.getProblems());
            assertNull("nothing is in place yet", LostTalesMapMarkerCatalog.getMarker("myserver:mill"));
            assertFalse(LostTalesMapMarkerCatalog.isServerRead());

            LostTalesMapMarkerCatalog.put(read);
            assertNotNull(LostTalesMapMarkerCatalog.getMarker("myserver:mill"));
            assertNotNull(LostTalesMapMarkerCatalog.getBundledMarker("losttales:greenway_market"));
            assertTrue(LostTalesMapMarkerCatalog.isServerRead());
            assertTrue("the store seeds itself again", LostTalesMapMarkerCatalog.revision() > before);
        } finally {
            LostTalesMapMarkerCatalog.clearServerFiles();
        }
    }

    @Test
    public void aFileNamingAHouseNobodyBroughtIsLeftOutWithItsReason() throws IOException {
        File folder = this.temporary.newFolder("map_markers");
        JsonObject marker = marker("myserver:hut");
        marker.addProperty("waystoneStructureType", "myserver:no_such_house");
        write(new File(folder, "hut.json"), file(marker));

        LostTalesMapMarkerCatalog.Read read = LostTalesMapMarkerCatalog.read(folder, HOUSES);

        assertEquals(read.getProblems().toString(), 1, read.getProblems().size());
        assertTrue(read.getProblems().get(0).startsWith("Server map marker file: hut.json"));
    }

    private static JsonObject file(JsonObject marker) {
        JsonObject file = new JsonObject();
        JsonArray list = new JsonArray();
        list.add(marker);
        file.add("map_markers", list);
        return file;
    }

    /** A whole marker as a server's file writes it, its name with it. */
    private static JsonObject marker(String id) {
        JsonObject marker = new JsonObject();
        marker.addProperty("id", id);
        marker.addProperty("name", "Somewhere");
        marker.addProperty("relevance", "medium");
        marker.addProperty("icon", "shack");
        marker.addProperty("color", "white");
        marker.addProperty("dimension", "lotr:middle_earth");
        marker.addProperty("x", 180.0D);
        marker.addProperty("z", 40.0D);
        marker.addProperty("compassFadeInRadius", 220.0D);
        marker.addProperty("discoveryRadius", 14.0D);
        marker.addProperty("isDiscoverable", true);
        marker.addProperty("hiddenUntilDiscovered", false);
        marker.addProperty("requiresRegionUnlock", true);
        marker.addProperty("hasFastTravel", true);
        marker.addProperty("hasWaystone", true);
        marker.addProperty("waystoneStructureType", "losttales:glowstone_house");
        return marker;
    }

    private static void write(File file, JsonObject json) throws IOException {
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            writer.write(json.toString());
        } finally {
            writer.close();
        }
    }
}
