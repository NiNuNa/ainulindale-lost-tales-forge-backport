package com.ninuna.losttales.accessory;

import com.ninuna.losttales.progression.ProgressionFiles;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * What the ring slot takes and what each accessory does are data files
 * (ACC-01): read strictly, an unknown item or effect warned and left out,
 * and a server's own in place of a bundled one of its id.
 */
public final class AccessoryFilesTest {
    /** The items the bundled files name, as a game with LOTR has them. */
    private static final AccessoryFiles.Game GAME = new AccessoryFiles.Game() {
        @Override
        public boolean hasItem(String itemId) {
            return Arrays.asList("losttales:the_one_ring", "lotr:item.goldRing",
                    "lotr:item.silverRing", "lotr:item.mithrilRing", "lotr:item.hobbitRing",
                    "lotr:item.dwarvenRing", "minecraft:gold_nugget").contains(itemId);
        }
    };
    private static final String RING = "{\"id\": \"myserver:signet\", \"slot\": \"ring\","
            + " \"item\": \"minecraft:gold_nugget\", \"effects\": [\"losttales:concealment\"]}";

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    /** The bundled files: the One Ring, concealing and showing the wraith world, and LOTR's five plain rings. */
    @Test
    public void theBundledFilesHoldTheRings() {
        List<String> problems = new ArrayList<String>();
        AccessoryCatalogue catalogue = Accessories.read(ProgressionFiles.CLASSPATH, null,
                GAME, problems);

        assertEquals(new ArrayList<String>(), problems);
        assertEquals(6, catalogue.getDefinitions().size());
        AccessoryDefinition oneRing = catalogue.find(AccessorySlotType.RING,
                "losttales:the_one_ring");
        assertEquals("losttales:the_one_ring", oneRing.getId());
        assertEquals(EnumSet.of(AccessoryEffect.CONCEALMENT, AccessoryEffect.WRAITH_WORLD),
                oneRing.getEffects());
        for (String ring : Arrays.asList("gold", "silver", "mithril", "hobbit", "dwarven")) {
            AccessoryDefinition plain = catalogue.find(AccessorySlotType.RING,
                    "lotr:item." + ring + "Ring");
            assertEquals("lotr:" + ring + "_ring", plain.getId());
            assertTrue(plain.getEffects().isEmpty());
        }
        assertNull(catalogue.find(AccessorySlotType.RING, "minecraft:gold_nugget"));
    }

    @Test
    public void aWholeFileReadsWithNoWarning() {
        List<String> said = new ArrayList<String>();
        AccessoryDefinition definition = AccessoryFiles.parse(new StringReader(RING), "signet.json",
                said);

        assertEquals(new ArrayList<String>(), said);
        assertEquals(new ArrayList<String>(), AccessoryFiles.warningsOf(definition, GAME));
        assertEquals("myserver:signet", definition.getId());
        assertSame(AccessorySlotType.RING, definition.getSlotType());
        assertEquals("minecraft:gold_nugget", definition.getItemId());
        assertTrue(definition.has(AccessoryEffect.CONCEALMENT));
        assertFalse(definition.has(AccessoryEffect.WRAITH_WORLD));
    }

    @Test
    public void noEffectsIsAPlainRing() {
        List<String> said = new ArrayList<String>();
        AccessoryDefinition definition = AccessoryFiles.parse(new StringReader(
                RING.replace(", \"effects\": [\"losttales:concealment\"]", "")), "signet.json",
                said);
        assertEquals(new ArrayList<String>(), said);
        assertTrue(definition.getEffects().isEmpty());
    }

    /** An effect the code does not hold, or a slot it has not, is said and the file left out. */
    @Test
    public void unknownEffectsAndSlotsAreWarned() {
        assertSays(RING.replace("losttales:concealment", "losttales:flight"),
                "the effect 'losttales:flight'");
        assertSays(RING.replace("[\"losttales:concealment\"]",
                "[\"losttales:concealment\", \"losttales:concealment\"]"), "twice");
        assertSays(RING.replace("\"ring\"", "\"amulet\""), "the slot 'amulet'");
        assertSays(RING.replace("}", ", \"glow\": true}"), "nothing reads");
        assertSays(RING.replace("[\"losttales:concealment\"]", "\"losttales:concealment\""),
                "other than a list");
    }

    /** An item the game lacks, or one not written as an item's id, is a warning. */
    @Test
    public void unknownItemsAreWarned() {
        assertWarns(RING.replace("minecraft:gold_nugget", "minecraft:no_such_ring"),
                "which the game does not have");
        assertWarns(RING.replace("minecraft:gold_nugget", "gold_nugget"), "an item's id");
        assertWarns(RING.replace("myserver:signet", "Signet"), "the id 'Signet'");
    }

    /**
     * A server's file of a bundled id takes its place, a new one joins, and
     * one with a warning is left out and said, never a crash.
     */
    @Test
    public void aServersOwnReplacesAndAdds() throws IOException {
        File folder = this.temporary.newFolder("accessories");
        write(new File(folder, "gold.json"), "{\"id\": \"lotr:gold_ring\", \"slot\": \"ring\","
                + " \"item\": \"lotr:item.goldRing\", \"effects\": [\"losttales:wraith_world\"]}");
        write(new File(folder, "signet.json"), RING);
        write(new File(folder, "broken.json"), RING.replace("myserver:signet", "myserver:broken")
                .replace("minecraft:gold_nugget", "minecraft:no_such_ring"));
        write(new File(folder, "garbled.json"), "{\"id\": ");
        List<String> problems = new ArrayList<String>();

        AccessoryCatalogue catalogue = Accessories.read(ProgressionFiles.CLASSPATH, folder, GAME,
                problems);

        assertEquals(7, catalogue.getDefinitions().size());
        assertTrue(catalogue.byId("lotr:gold_ring").has(AccessoryEffect.WRAITH_WORLD));
        assertTrue(catalogue.find(AccessorySlotType.RING, "minecraft:gold_nugget")
                .has(AccessoryEffect.CONCEALMENT));
        assertNull(catalogue.byId("myserver:broken"));
        assertTrue(problems.toString(), problems.toString().contains("no_such_ring"));
        assertTrue(problems.toString(), problems.toString().contains("garbled.json"));
    }

    /** Two accessories naming one item for one slot: the later is left out, said. */
    @Test
    public void anItemIsOneAccessory() throws IOException {
        File folder = this.temporary.newFolder("twice");
        write(new File(folder, "second.json"), RING.replace("myserver:signet", "myserver:second")
                .replace("minecraft:gold_nugget", "losttales:the_one_ring"));
        List<String> problems = new ArrayList<String>();

        AccessoryCatalogue catalogue = Accessories.read(ProgressionFiles.CLASSPATH, folder, GAME,
                problems);

        assertEquals("losttales:the_one_ring",
                catalogue.find(AccessorySlotType.RING, "losttales:the_one_ring").getId());
        assertNull(catalogue.byId("myserver:second"));
        assertTrue(problems.toString(), problems.toString().contains("is already"));
    }

    private static void assertSays(String json, String fragment) {
        List<String> said = new ArrayList<String>();
        AccessoryFiles.parse(new StringReader(json), "file.json", said);
        assertTrue(said + " should say " + fragment, said.toString().contains(fragment));
    }

    private static void assertWarns(String json, String fragment) {
        List<String> said = new ArrayList<String>();
        AccessoryDefinition definition = AccessoryFiles.parse(new StringReader(json), "file.json",
                said);
        List<String> warnings = AccessoryFiles.warningsOf(definition, GAME);
        assertTrue(warnings + " should say " + fragment, warnings.toString().contains(fragment));
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
