package com.ninuna.losttales.character.kit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.data.DataFiles;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * A kit file is read strictly (CHAR-40, MOD-17): a key no kit has, a
 * missing id, a kit for nobody, a count or damage out of bounds, and a
 * race, faction or item the game lacks each raise a warning, and a file
 * with any warning is left out.
 */
public final class StartingKitFilesTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    /** The six races are not needed here: one race, one faction, three items. */
    private static final StartingKitFiles.Game GAME = new StartingKitFiles.Game() {
        @Override
        public boolean hasRace(String raceId) {
            return "losttales:hobbit".equals(raceId);
        }

        @Override
        public boolean hasFaction(String factionId) {
            return "lotr:hobbit".equals(factionId);
        }

        @Override
        public int stackLimit(String itemId, int damage) {
            if ("minecraft:bread".equals(itemId)) {
                return 64;
            }
            if ("minecraft:ender_pearl".equals(itemId)) {
                return 16;
            }
            return "lotr:item.hobbitPipe".equals(itemId) ? 1 : 0;
        }

        @Override
        public boolean relicTakes(String relicId, String itemId) {
            return "losttales:striders_garb".equals(relicId);
        }

        @Override
        public boolean wornThrough(String itemId, int damage) {
            return "lotr:item.hobbitPipe".equals(itemId) && damage >= 100;
        }
    };

    @Test
    public void aWellWrittenKitRaisesNoWarning() {
        assertEquals(new ArrayList<String>(), warnings("{\"id\": \"myserver:shire\","
                + " \"race\": \"losttales:hobbit\", \"faction\": \"lotr:hobbit\", \"items\": ["
                + "{\"item\": \"minecraft:bread\", \"count\": 8},"
                + "{\"item\": \"lotr:item.hobbitPipe\", \"count\": 1, \"damage\": 0}]}"));
    }

    @Test
    public void aKeyNoKitHasIsSaid() {
        assertSaid("nothing reads", "{\"id\": \"a:b\", \"race\": \"losttales:hobbit\","
                + " \"name\": \"Shire kit\", \"items\": [{\"item\": \"minecraft:bread\", \"count\": 1}]}");
        assertSaid("nothing reads", "{\"id\": \"a:b\", \"race\": \"losttales:hobbit\","
                + " \"items\": [{\"item\": \"minecraft:bread\", \"count\": 1, \"nbt\": \"{}\"}]}");
    }

    @Test
    public void aKitNeedsAnIdSomeoneToFitAndItems() {
        assertSaid("the id ''", "{\"race\": \"losttales:hobbit\","
                + " \"items\": [{\"item\": \"minecraft:bread\", \"count\": 1}]}");
        assertSaid("neither a race nor a faction",
                "{\"id\": \"a:b\", \"items\": [{\"item\": \"minecraft:bread\", \"count\": 1}]}");
        assertSaid("gives nothing", "{\"id\": \"a:b\", \"race\": \"losttales:hobbit\"}");
        assertSaid("a count runs", "{\"id\": \"a:b\", \"race\": \"losttales:hobbit\","
                + " \"items\": [{\"item\": \"minecraft:bread\"}]}");
    }

    @Test
    public void countsDamagesAndIdsAreBounded() {
        assertSaid("a count runs", kit("{\"item\": \"minecraft:bread\", \"count\": 65}"));
        assertSaid("a count runs", kit("{\"item\": \"minecraft:bread\", \"count\": 0}"));
        assertSaid("a damage runs",
                kit("{\"item\": \"minecraft:bread\", \"count\": 1, \"damage\": -1}"));
        assertSaid("mod's prefix", kit("{\"item\": \"bread\", \"count\": 1}"));
        assertSaid("whole number", kit("{\"item\": \"minecraft:bread\", \"count\": 1.5}"));
        StringBuilder many = new StringBuilder();
        for (int index = 0; index <= StartingKitFiles.MAX_ITEMS; index++) {
            many.append(index == 0 ? "" : ",").append("{\"item\": \"minecraft:bread\", \"count\": 1}");
        }
        assertSaid("at most 36", kit(many.toString()));
    }

    @Test
    public void whatTheGameLacksIsSaid() {
        assertSaid("the race losttales:ent", "{\"id\": \"a:b\", \"race\": \"losttales:ent\","
                + " \"items\": [{\"item\": \"minecraft:bread\", \"count\": 1}]}");
        assertSaid("the faction lotr:ents", "{\"id\": \"a:b\", \"faction\": \"lotr:ents\","
                + " \"items\": [{\"item\": \"minecraft:bread\", \"count\": 1}]}");
        assertSaid("does not have", kit("{\"item\": \"lotr:item.entDraught\", \"count\": 1}"));
        assertSaid("one stack of it holds 16", kit("{\"item\": \"minecraft:ender_pearl\", \"count\": 20}"));
        assertSaid("one stack of it holds 1", kit("{\"item\": \"lotr:item.hobbitPipe\", \"count\": 2}"));
        assertSaid("wears it through",
                kit("{\"item\": \"lotr:item.hobbitPipe\", \"count\": 1, \"damage\": 100}"));
    }

    /** A kit may hand out a relic piece, marked with a relic set that takes it (CONTENT-11). */
    @Test
    public void aKitMarksARelicPieceASetTakes() {
        String relic = kit("{\"item\": \"lotr:item.hobbitPipe\", \"count\": 1,"
                + " \"relic\": \"losttales:striders_garb\"}");
        assertEquals(new ArrayList<String>(), warnings(relic));
        assertSaid("no relic set's id", relic.replace("losttales:striders_garb", "Strider"));
        assertSaid("no relic set of that id takes",
                relic.replace("losttales:striders_garb", "losttales:unknown"));
        KitItem marked = new KitItem("lotr:item.hobbitPipe", 1, 0, "losttales:striders_garb");
        assertTrue("a relic piece stacks with no unmarked one",
                !marked.stacksWith(new KitItem("lotr:item.hobbitPipe", 1, 0)));
        assertEquals("losttales:striders_garb", marked.withCount(1).getRelic());
    }

    @Test
    public void aRaceOrFactionIsWrittenAsItsId() {
        assertSaid("written as a race's id", "{\"id\": \"a:b\", \"race\": \"Hobbit\","
                + " \"items\": [{\"item\": \"minecraft:bread\", \"count\": 1}]}");
        assertSaid("written as a faction's id", "{\"id\": \"a:b\", \"faction\": \"lotr:HOBBIT\","
                + " \"items\": [{\"item\": \"minecraft:bread\", \"count\": 1}]}");
    }

    /** A server's folder: the file with a warning is left out, the good one loads. */
    @Test
    public void aFileWithAWarningIsLeftOutOfTheFolder() throws IOException {
        File kits = folder.newFolder("kits");
        write(new File(kits, "a_shire.json"), "{\"id\": \"myserver:shire\", \"faction\":"
                + " \"lotr:hobbit\", \"items\": [{\"item\": \"minecraft:bread\", \"count\": 4}]}");
        write(new File(kits, "b_broken.json"), "{\"id\": \"myserver:broken\", \"faction\":"
                + " \"lotr:hobbit\", \"items\": [{\"item\": \"minecraft:bread\", \"count\": 99}]}");
        DataFiles.Result<StartingKit> read = DataFiles.readFolder(kits, StartingKitFiles.kind(GAME));
        assertEquals(1, read.values.size());
        assertEquals("myserver:shire", read.values.get(0).getId());
        assertEquals(1, read.problems.size());
        assertTrue(read.problems.get(0), read.problems.get(0).startsWith("b_broken.json"));
    }

    private static String kit(String items) {
        return "{\"id\": \"a:b\", \"race\": \"losttales:hobbit\", \"items\": [" + items + "]}";
    }

    private static void assertSaid(String part, String json) {
        List<String> warnings = warnings(json);
        for (String warning : warnings) {
            if (warning.contains(part)) {
                return;
            }
        }
        throw new AssertionError("no warning says '" + part + "': " + warnings);
    }

    private static List<String> warnings(String json) {
        List<String> said = new ArrayList<String>();
        StartingKit kit = StartingKitFiles.parse(new StringReader(json), "kits/test.json", said);
        List<String> warnings = new ArrayList<String>(said);
        warnings.addAll(StartingKitFiles.warningsOf(kit, GAME));
        return warnings;
    }

    private static void write(File file, String text) throws IOException {
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }
}
