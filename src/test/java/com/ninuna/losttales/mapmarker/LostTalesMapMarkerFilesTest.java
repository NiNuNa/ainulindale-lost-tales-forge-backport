package com.ninuna.losttales.mapmarker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.world.waystone.LostTalesWaystoneHouseFiles;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The map marker files, bundled and a server's own, read strictly (MOD-17). */
public final class LostTalesMapMarkerFilesTest {
    private static final Set<String> HOUSES =
            Collections.singleton("losttales:glowstone_house");

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    /** Every bundled file reads with no warning, under the one checker. */
    @Test
    public void theBundledFilesRaiseNoWarning() {
        Set<String> houses = LostTalesWaystoneHouseFiles.bundledIds(
                LostTalesMapMarkerFiles.CLASSPATH);
        assertEquals(HOUSES, houses);
        DataFiles.Result<LostTalesMapMarkerFiles.MarkerFile> read = DataFiles.readBundled(
                LostTalesMapMarkerFiles.CLASSPATH, LostTalesMapMarkerFiles.INDEX,
                LostTalesMapMarkerFiles.LIST_KEY, LostTalesMapMarkerFiles.bundled(houses));
        assertEquals(new ArrayList<String>(), read.problems);
        assertEquals(7, read.values.size());

        List<String> problems = new ArrayList<String>();
        Map<String, LostTalesMapMarkerDefinition> markers =
                LostTalesMapMarkerFiles.join(read.values, problems);
        assertEquals(new ArrayList<String>(), problems);
        assertEquals(289, markers.size());
        for (LostTalesMapMarkerDefinition marker : markers.values()) {
            assertTrue(marker.getId(), marker.getName().length() > 0);
            assertTrue(marker.getId() + " has a waystone in the glowstone house",
                    marker.hasWaystone() && HOUSES.contains(marker.getWaystoneStructureType()));
        }
        assertEquals("Greenway Market",
                markers.get("losttales:greenway_market").getName());
        assertEquals(LostTalesMapMarkerSource.LOTR_ADAPTER,
                markers.get("lotr:waypoint:hobbiton").getSource());
        assertEquals(LostTalesMapMarkerSource.CUSTOM_PRESET,
                markers.get("losttales:mossy_cave").getSource());
        assertEquals("HOBBITON",
                markers.get("lotr:waypoint:hobbiton").getLotrWaypointId());
    }

    @Test
    public void questMarkersNeverDefaultToWaystones() {
        LostTalesMapMarkerDefinition marker = new LostTalesMapMarkerDefinition(
                "losttales:quest/runtime", "Quest Target",
                "quest", "white", 0, 1.0D, 64.0D, 2.0D, true);

        assertEquals(LostTalesMapMarkerSource.QUEST_DYNAMIC, marker.getSource());
        assertFalse(marker.hasWaystone());
        assertEquals("", marker.getWaystoneStructureType());
    }

    @Test
    public void aWholeServerMarkerReadsAsWritten() {
        JsonObject marker = marker("myserver:old_mill");
        marker.addProperty("name", "The Old Mill");
        marker.addProperty("description", "Grain was ground here once.");
        marker.addProperty("category", "Ruin");
        marker.addProperty("y", 70);
        marker.addProperty("relevance", "very-high");

        LostTalesMapMarkerDefinition read = readOne(server(marker));

        assertEquals("The Old Mill", read.getName());
        assertEquals("Grain was ground here once.", read.getDescription());
        assertEquals("Ruin", read.getCategoryName());
        assertEquals(70.0D, read.getY(), 0.0D);
        assertEquals(LostTalesMapMarkerRelevance.VERY_HIGH.getRank(), read.getPriority());
        assertEquals(LostTalesMapMarkerSource.CUSTOM_PRESET, read.getSource());
        assertEquals("losttales:glowstone_house", read.getWaystoneStructureType());
        assertEquals(LostTalesMapMarkerDefinition.AUTOMATIC_Y,
                readOne(server(named("myserver:ground"))).getY(), 0.0D);
    }

    /** A key nothing reads, a missing one or a value out of bounds: the file is left out. */
    @Test
    public void anythingWrongLeavesTheFileOut() {
        assertWarns(with(named("myserver:a"), "colour", "red"), "nothing reads");
        assertWarns(without(named("myserver:a"), "discoveryRadius"), "writes no 'discoveryRadius'");
        assertWarns(without(named("myserver:a"), "name"), "writes no 'name'");
        assertWarns(with(named("myserver:a"), "relevance", "very_high"), "relevance");
        assertWarns(with(named("myserver:a"), "dimension", "middle_earth"), "the world");
        assertWarns(with(named("myserver:a"), "color", "teal"), "colour");
        assertWarns(with(named("myserver:a"), "icon", "a b"), "icon");
        assertWarns(with(named("myserver:a"), "x", 40000000), "edge");
        assertWarns(with(named("myserver:a"), "y", 300), "height");
        assertWarns(with(named("myserver:a"), "discoveryRadius", 0.5), "found from");
        assertWarns(with(named("myserver:a"), "compassFadeInRadius", -1), "compass");
        assertWarns(with(named("myserver:a"), "hasFastTravel", "yes"), "true or false");
        assertWarns(with(with(named("myserver:a"), "isDiscoverable", false),
                "hiddenUntilDiscovered", true), "cannot be discovered");
        assertWarns(with(named("myserver:a"), "dimension", "minecraft:overworld"),
                "fast travel outside Middle-earth");
        assertWarns(without(named("myserver:a"), "waystoneStructureType"), "names no house");
        assertWarns(with(with(named("myserver:a"), "hasWaystone", false),
                "hasFastTravel", true), "names a house but has no waystone");
        assertWarns(named("MyServer:Mill"), "lower-case");
        assertWarns(named("losttales:player/abc"), "players place");
        assertWarns(named("lotr:waypoint:nowhere"), "no bundled marker");
        assertWarns(with(named("myserver:a"), "name", ""), "empty name");
    }

    /** A marker naming a house no file brought is a warning, and the file is left out. */
    @Test
    public void anUnknownHouseLeavesTheFileOut() {
        assertWarns(with(named("myserver:a"), "waystoneStructureType", "myserver:hut"),
                "no house that loaded");
        assertTrue(warnings(server(with(named("myserver:a"), "waystoneStructureType",
                "losttales:stone_hut")), new HashSet<String>(Arrays.asList(
                "losttales:glowstone_house", "losttales:stone_hut"))).isEmpty());
    }

    /** A bundled Lost Tales marker takes its words from the lang file and writes none. */
    @Test
    public void aBundledMarkerTakesItsWordsFromTheLangFile() {
        JsonObject marker = marker("losttales:greenway_market");
        assertTrue(warnings(bundledFile(marker), HOUSES).isEmpty());
        assertEquals("Greenway Market", readOne(bundledFile(marker), true).getName());

        assertFalse(warnings(bundledFile(with(marker("losttales:greenway_market"),
                "name", "Market")), HOUSES).isEmpty());
        List<String> unnamed = warnings(bundledFile(marker("losttales:no_line_here")), HOUSES);
        assertTrue(unnamed.toString(), unnamed.toString().contains("English name"));
    }

    @Test
    public void aMarkerTwiceInOneFileIsAWarning() {
        assertWarns(server(named("myserver:a"), named("myserver:a")), "twice");
    }

    /** A later file repeating an earlier file's marker is left out whole. */
    @Test
    public void aLaterFileRepeatingAMarkerIsLeftOut() {
        LostTalesMapMarkerFiles.MarkerFile first = file("a", server(named("myserver:mill")));
        LostTalesMapMarkerFiles.MarkerFile second = file("b",
                server(named("myserver:mill"), named("myserver:well")));
        List<String> problems = new ArrayList<String>();

        Map<String, LostTalesMapMarkerDefinition> joined =
                LostTalesMapMarkerFiles.join(Arrays.asList(first, second), problems);

        assertEquals(Collections.singleton("myserver:mill"), joined.keySet());
        assertEquals(1, problems.size());
    }

    /**
     * A server's file is read from its folder: a marker with a bundled
     * marker's id takes its place, its words as written, and a new one
     * joins the others; the bundled one stays the bundled one.
     */
    @Test
    public void aServerMarkerTakesTheBundledOnesPlace() throws IOException {
        File folder = this.temporary.newFolder("map_markers");
        JsonObject market = marker("losttales:greenway_market");
        market.addProperty("name", "Greenway Fair");
        write(new File(folder, "shire.json"), server(market, named("myserver:mill")));
        write(new File(folder, "broken.json"), server(without(named("myserver:well"), "x")));

        LostTalesMapMarkerCatalog.reloadFromClasspath();
        try {
            List<String> problems = LostTalesMapMarkerCatalog.loadServerFiles(folder, HOUSES);

            assertEquals(problems.toString(), 1, problems.size());
            assertTrue(problems.get(0).contains("broken"));
            assertTrue(LostTalesMapMarkerCatalog.isServerRead());
            assertEquals("Greenway Fair",
                    LostTalesMapMarkerCatalog.getMarker("losttales:greenway_market").getName());
            assertEquals("Greenway Market", LostTalesMapMarkerCatalog
                    .getBundledMarker("losttales:greenway_market").getName());
            assertNotNull(LostTalesMapMarkerCatalog.getMarker("myserver:mill"));
            assertNull(LostTalesMapMarkerCatalog.getBundledMarker("myserver:mill"));
            assertNull(LostTalesMapMarkerCatalog.getMarker("myserver:well"));
            assertEquals(290, LostTalesMapMarkerCatalog.getMarkers().size());
            assertEquals(289, LostTalesMapMarkerCatalog.getBundledMarkers().size());
            assertFalse("a server's words are shown as written",
                    LostTalesMapMarkerNames.isBundledName(
                            "losttales:greenway_market", "Greenway Fair"));
        } finally {
            LostTalesMapMarkerCatalog.clearServerFiles();
        }
        assertFalse(LostTalesMapMarkerCatalog.isServerRead());
        assertNull(LostTalesMapMarkerCatalog.getMarker("myserver:mill"));
        assertEquals("Greenway Market",
                LostTalesMapMarkerCatalog.getMarker("losttales:greenway_market").getName());
    }

    /** Every read counts up, so the store seeds again after one. */
    @Test
    public void everyReadCountsUp() {
        int before = LostTalesMapMarkerCatalog.revision();
        LostTalesMapMarkerCatalog.clearServerFiles();
        assertTrue(LostTalesMapMarkerCatalog.revision() != before);
        assertSame(LostTalesMapMarkerCatalog.getMarkers(), LostTalesMapMarkerCatalog.getMarkers());
    }

    /** A server's file holding {@code markerOrFile}, or that file itself, warns {@code fragment}. */
    private static void assertWarns(JsonObject markerOrFile, String fragment) {
        JsonObject file = markerOrFile.has("map_markers") ? markerOrFile
                : server(markerOrFile);
        List<String> warnings = warnings(file, HOUSES);
        assertFalse("no warning for " + file, warnings.isEmpty());
        assertTrue(warnings + " says " + fragment, warnings.toString().contains(fragment));
    }

    private static List<String> warnings(JsonObject file, Set<String> houses) {
        boolean bundled = file.has("bundled");
        file.remove("bundled");
        List<String> said = new ArrayList<String>();
        DataFiles.Kind<LostTalesMapMarkerFiles.MarkerFile> kind = bundled
                ? LostTalesMapMarkerFiles.bundled(houses)
                : LostTalesMapMarkerFiles.server(houses,
                        Collections.singleton("lotr:waypoint:hobbiton"));
        LostTalesMapMarkerFiles.MarkerFile read = kind.parse(
                new StringReader(file.toString()), "test.json", said);
        List<String> warnings = new ArrayList<String>(said);
        if (read != null) {
            warnings.addAll(kind.warnings(read));
        }
        return warnings;
    }

    private static LostTalesMapMarkerDefinition readOne(JsonObject file) {
        return readOne(file, false);
    }

    private static LostTalesMapMarkerDefinition readOne(JsonObject file, boolean bundled) {
        file.remove("bundled");
        List<String> said = new ArrayList<String>();
        LostTalesMapMarkerFiles.MarkerFile read = LostTalesMapMarkerFiles.parse(
                new StringReader(file.toString()), "test.json", said, bundled);
        assertEquals(new ArrayList<String>(), said);
        return read.getMarkers().get(0);
    }

    private static LostTalesMapMarkerFiles.MarkerFile file(String name, JsonObject file) {
        return LostTalesMapMarkerFiles.parse(new StringReader(file.toString()),
                name + ".json", new ArrayList<String>(), false);
    }

    private static JsonObject server(JsonObject... markers) {
        JsonObject file = new JsonObject();
        JsonArray list = new JsonArray();
        for (JsonObject marker : markers) {
            list.add(marker);
        }
        file.add("map_markers", list);
        return file;
    }

    private static JsonObject bundledFile(JsonObject marker) {
        JsonObject file = server(marker);
        file.addProperty("bundled", true);
        return file;
    }

    /** A whole marker as a bundled file writes it: no words of its own. */
    private static JsonObject marker(String id) {
        JsonObject marker = new JsonObject();
        marker.addProperty("id", id);
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

    /** A whole marker as a server's file writes it, its name with it. */
    private static JsonObject named(String id) {
        JsonObject marker = marker(id);
        marker.addProperty("name", "Somewhere");
        return marker;
    }

    /** A copy of {@code marker} writing {@code value} under {@code key}. */
    private static JsonObject with(JsonObject marker, String key, Object value) {
        JsonObject copy = new com.google.gson.JsonParser().parse(marker.toString())
                .getAsJsonObject();
        if (value instanceof Boolean) {
            copy.addProperty(key, (Boolean)value);
        } else if (value instanceof Number) {
            copy.addProperty(key, (Number)value);
        } else {
            copy.addProperty(key, String.valueOf(value));
        }
        return copy;
    }

    /** A copy of {@code marker} without {@code key}. */
    private static JsonObject without(JsonObject marker, String key) {
        JsonObject copy = new com.google.gson.JsonParser().parse(marker.toString())
                .getAsJsonObject();
        copy.remove(key);
        return copy;
    }

    private static void write(File file, JsonObject json) throws IOException {
        Writer writer = new OutputStreamWriter(new FileOutputStream(file),
                StandardCharsets.UTF_8);
        try {
            writer.write(json.toString());
        } finally {
            writer.close();
        }
    }
}
