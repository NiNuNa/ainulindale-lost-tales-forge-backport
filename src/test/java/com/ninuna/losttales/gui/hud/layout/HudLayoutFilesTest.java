package com.ninuna.losttales.gui.hud.layout;

import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.progression.ProgressionFiles;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The HUD layout files (MAP-11): the bundled four, a player's own, and what leaves a file out. */
public final class HudLayoutFilesTest {
    private static final String PANELS =
            "\"compass\": {\"across\": 50, \"down\": 90},"
                    + " \"fellowship\": {\"across\": 0, \"down\": 0},"
                    + " \"quickloot\": {\"across\": 100, \"down\": 100},"
                    + " \"quest\": {\"across\": 0, \"down\": 100},"
                    + " \"notifications\": {\"across\": 50, \"down\": 50}";

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void theBundledFilesAreTheFourLayoutsAndRaiseNoWarning() {
        DataFiles.Result<HudLayout> read = DataFiles.readBundled(
                ProgressionFiles.CLASSPATH, HudLayoutFiles.INDEX, HudLayoutFiles.LIST,
                HudLayoutFiles.KIND);
        assertEquals(new ArrayList<String>(), read.problems);
        List<String> ids = new ArrayList<String>();
        for (HudLayout layout : read.values) {
            ids.add(layout.getId());
        }
        assertEquals(Arrays.asList("default", "lotr-safe", "compact", "minimal"), ids);
    }

    @Test
    public void theBundledFilesHoldTheLayoutsNumbers() {
        // {across, down} for compass, fellowship, quick loot, quest tracker, notifications.
        double[][][] expected = {
                {{50, 2}, {3, 28}, {100, 80}, {4, 52}, {50, 35}},
                {{50, 15}, {3, 43}, {100, 85}, {4, 71}, {50, 32}},
                {{50, 9}, {2, 40}, {100, 93}, {2, 78}, {50, 30}},
                {{50, 5}, {2, 28}, {100, 100}, {2, 96}, {50, 28}}};
        Map<String, double[][]> places = HudLayoutFiles.placesById(DataFiles.readBundled(
                ProgressionFiles.CLASSPATH, HudLayoutFiles.INDEX, HudLayoutFiles.LIST,
                HudLayoutFiles.KIND).values);
        String[] ids = {"default", "lotr-safe", "compact", "minimal"};
        for (int layout = 0; layout < ids.length; layout++) {
            double[][] read = places.get(ids[layout]);
            assertNotNull(ids[layout], read);
            for (int panel = 0; panel < expected[layout].length; panel++) {
                assertArrayEquals(ids[layout] + " panel " + panel,
                        expected[layout][panel], read[panel], 0.0D);
            }
        }
    }

    @Test
    public void everyBundledLayoutIsNamedAndExplainedInEnglish() {
        Map<String, String> english = LostTalesLangFile.english();
        for (HudLayout layout : DataFiles.readBundled(ProgressionFiles.CLASSPATH,
                HudLayoutFiles.INDEX, HudLayoutFiles.LIST, HudLayoutFiles.KIND).values) {
            assertEquals(layout.getId(), "", layout.getName());
            assertTrue(layout.getId(), english.containsKey(
                    HudLayoutFiles.NAME_KEY_PREFIX + layout.getId()));
            assertTrue(layout.getId(), english.containsKey(
                    HudLayoutFiles.DESCRIPTION_KEY_PREFIX + layout.getId()));
        }
        assertTrue(english.containsKey(HudLayoutFiles.NAME_KEY_PREFIX + "custom"));
    }

    @Test
    public void aPlayersWrittenLayoutLoads() {
        List<String> problems = problemsOf("{\"id\": \"low\", \"name\": \"Low\","
                + " \"description\": \"Down by the hotbar.\", \"panels\": {" + PANELS + "}}");
        assertEquals(new ArrayList<String>(), problems);
    }

    @Test
    public void aFileWithAnythingWrongIsLeftOut() {
        String[] wrong = {
                // a panel missing
                "{\"id\": \"low\", \"name\": \"Low\", \"panels\": {"
                        + "\"compass\": {\"across\": 50, \"down\": 90}}}",
                // a panel no HUD has
                "{\"id\": \"low\", \"name\": \"Low\", \"panels\": {" + PANELS
                        + ", \"minimap\": {\"across\": 0, \"down\": 0}}}",
                // a key nothing reads, at the top and in a panel
                "{\"id\": \"low\", \"name\": \"Low\", \"colour\": 3, \"panels\": {" + PANELS + "}}",
                "{\"id\": \"low\", \"name\": \"Low\", \"panels\": {"
                        + PANELS.replace("\"down\": 90}", "\"down\": 90, \"scale\": 2}") + "}}",
                // a number past either end, a fraction, a word, one left out
                "{\"id\": \"low\", \"name\": \"Low\", \"panels\": {"
                        + PANELS.replace("\"down\": 90", "\"down\": 101") + "}}",
                "{\"id\": \"low\", \"name\": \"Low\", \"panels\": {"
                        + PANELS.replace("\"down\": 90", "\"down\": -1") + "}}",
                "{\"id\": \"low\", \"name\": \"Low\", \"panels\": {"
                        + PANELS.replace("\"down\": 90", "\"down\": 50.5") + "}}",
                "{\"id\": \"low\", \"name\": \"Low\", \"panels\": {"
                        + PANELS.replace("\"down\": 90", "\"down\": \"90\"") + "}}",
                "{\"id\": \"low\", \"name\": \"Low\", \"panels\": {"
                        + PANELS.replace(", \"down\": 90", "") + "}}",
                // panels not a block, a panel not a block, no panels
                "{\"id\": \"low\", \"name\": \"Low\", \"panels\": [1, 2]}",
                "{\"id\": \"low\", \"name\": \"Low\", \"panels\": {"
                        + PANELS.replace("{\"across\": 50, \"down\": 90}", "50") + "}}",
                "{\"id\": \"low\", \"name\": \"Low\"}",
                // no id, an id of other letters, the id custom
                "{\"name\": \"Low\", \"panels\": {" + PANELS + "}}",
                "{\"id\": \"Low Layout\", \"name\": \"Low\", \"panels\": {" + PANELS + "}}",
                "{\"id\": \"custom\", \"name\": \"Mine\", \"panels\": {" + PANELS + "}}",
                // no name and no lang line for its id, a formatting code, too long
                "{\"id\": \"low\", \"panels\": {" + PANELS + "}}",
                "{\"id\": \"low\", \"name\": \"§cLow\", \"panels\": {" + PANELS + "}}",
                "{\"id\": \"low\", \"name\": \"" + repeat('a', HudLayoutFiles.MAX_NAME + 1)
                        + "\", \"panels\": {" + PANELS + "}}",
                // not an object at all
                "[1, 2, 3]"};
        for (String file : wrong) {
            assertTrue(file, !problemsOf(file).isEmpty());
        }
    }

    @Test
    public void aBundledIdNeedsNoWrittenName() {
        assertEquals(new ArrayList<String>(), problemsOf(
                "{\"id\": \"compact\", \"panels\": {" + PANELS + "}}"));
    }

    @Test
    public void aPlayersFileAddsALayoutAfterTheBundled() throws IOException {
        write("low.json", "{\"id\": \"low\", \"name\": \"Low\", \"panels\": {" + PANELS + "}}");
        DataFiles.Result<HudLayout> read = HudLayoutFiles.read(ProgressionFiles.CLASSPATH,
                this.folder.getRoot());
        assertEquals(new ArrayList<String>(), read.problems);
        assertEquals(5, read.values.size());
        assertEquals("low", read.values.get(4).getId());
        assertEquals("Low", read.values.get(4).getName());
    }

    @Test
    public void aPlayersFileWithABundledIdTakesItsPlace() throws IOException {
        write("mine.json", "{\"id\": \"compact\", \"name\": \"My Compact\", \"panels\": {"
                + PANELS + "}}");
        DataFiles.Result<HudLayout> read = HudLayoutFiles.read(ProgressionFiles.CLASSPATH,
                this.folder.getRoot());
        assertEquals(4, read.values.size());
        HudLayout compact = read.values.get(2);
        assertEquals("compact", compact.getId());
        assertEquals("My Compact", compact.getName());
        assertArrayEquals(new int[] {50, 90}, compact.place("compass"));
    }

    @Test
    public void aBrokenPlayersFileLeavesTheBundledStanding() throws IOException {
        write("broken.json", "{\"id\": \"compact\", \"panels\": {}}");
        write("garbled.json", "{ not json");
        DataFiles.Result<HudLayout> read = HudLayoutFiles.read(ProgressionFiles.CLASSPATH,
                this.folder.getRoot());
        assertEquals(4, read.values.size());
        assertArrayEquals(new int[] {50, 9}, read.values.get(2).place("compass"));
        assertTrue(read.problems.size() >= 2);
        for (String problem : read.problems) {
            assertTrue(problem, problem.startsWith("your "));
        }
    }

    @Test
    public void thePlacesFollowThePanelsOrder() {
        HudLayout layout = HudLayoutFiles.parse(new StringReader("{\"id\": \"low\","
                + " \"name\": \"Low\", \"panels\": {\"notifications\": {\"across\": 1, \"down\": 2},"
                + " \"quest\": {\"across\": 3, \"down\": 4}, \"quickloot\": {\"across\": 5, \"down\": 6},"
                + " \"fellowship\": {\"across\": 7, \"down\": 8},"
                + " \"compass\": {\"across\": 9, \"down\": 10}}}"), "low.json",
                new ArrayList<String>());
        double[][] places = HudLayoutFiles.placesById(Arrays.asList(layout)).get("low");
        assertArrayEquals(new double[] {9, 10}, places[0], 0.0D);
        assertArrayEquals(new double[] {7, 8}, places[1], 0.0D);
        assertArrayEquals(new double[] {5, 6}, places[2], 0.0D);
        assertArrayEquals(new double[] {3, 4}, places[3], 0.0D);
        assertArrayEquals(new double[] {1, 2}, places[4], 0.0D);
    }

    /** Everything a file of that text is left out for: unreadable, or what it says and raises. */
    private static List<String> problemsOf(String text) {
        List<String> problems = new ArrayList<String>();
        HudLayout layout;
        try {
            Reader reader = new StringReader(text);
            layout = HudLayoutFiles.parse(reader, "test.json", problems);
        } catch (RuntimeException unreadable) {
            problems.add("cannot be read: " + unreadable.getMessage());
            return problems;
        }
        if (layout == null) {
            problems.add("is not a JSON object");
            return problems;
        }
        problems.addAll(HudLayoutFiles.warningsOf(layout));
        return problems;
    }

    private void write(String name, String text) throws IOException {
        File file = new File(this.folder.getRoot(), name);
        Writer writer = new OutputStreamWriter(new FileOutputStream(file),
                StandardCharsets.UTF_8);
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }

    private static String repeat(char letter, int times) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < times; index++) {
            text.append(letter);
        }
        return text.toString();
    }
}
