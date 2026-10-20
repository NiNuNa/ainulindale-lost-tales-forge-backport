package com.ninuna.losttales.world.waystone;

import com.ninuna.losttales.data.DataFiles;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** A waystone's house is a LOTR scan and a rules file beside it, read strictly (WAY-17). */
public final class LostTalesWaystoneHouseFilesTest {
    /** The mod's own files off the test classpath. */
    private static final DataFiles.Source CLASSPATH = new DataFiles.Source() {
        @Override
        public Reader open(String path) {
            InputStream stream = LostTalesWaystoneHouseFilesTest.class.getClassLoader()
                    .getResourceAsStream("assets/losttales/" + path);
            return stream == null ? null
                    : new InputStreamReader(stream, StandardCharsets.UTF_8);
        }
    };
    /** Every block the tests name, as the game would have them. */
    private static final LostTalesWaystoneHouseFiles.BlockNames BLOCKS =
            new LostTalesWaystoneHouseFiles.BlockNames() {
                @Override
                public boolean has(String name) {
                    return Arrays.asList("glowstone", "minecraft:glowstone", "stone",
                            "minecraft:stone", "planks", "lotr:tile.brick").contains(name);
                }
            };
    private static final String RULES = "{\"waystone\": [1, 1, -2], \"arrival\": [0, 0, 3],"
            + " \"clearance\": 5, \"foundation\": 8, \"foundationBlock\": \"minecraft:stone\"}";
    private static final String SCAN = "0.0.-4.\"stone\".0\n3.0.0.\"stone\".0\n"
            + "0.4.-4.\"planks\".2\n";

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    /** The bundled glowstone house keeps today's numbers, read from its rules file. */
    @Test
    public void theGlowstoneHouseKeepsItsNumbers() {
        DataFiles.Result<LostTalesWaystoneHouse> read = DataFiles.readBundled(CLASSPATH,
                LostTalesWaystoneHouseFiles.INDEX, LostTalesWaystoneHouseFiles.LIST_KEY,
                LostTalesWaystoneHouseFiles.kind(
                        LostTalesWaystoneHouseFiles.bundledScans(CLASSPATH), BLOCKS));

        assertEquals(new ArrayList<String>(), read.problems);
        assertEquals(1, read.values.size());
        LostTalesWaystoneHouse house = read.values.get(0);
        assertEquals("losttales:glowstone_house", house.getId());
        assertArrayEquals(new int[] {1, 1, -2}, house.getWaystone());
        assertArrayEquals(new int[] {0, 0, 3}, house.getArrival());
        assertEquals(5, house.getClearance());
        assertEquals(8, house.getFoundation());
        assertEquals("minecraft:glowstone", house.getFoundationBlock());
        assertEquals(0, house.getMinX());
        assertEquals(3, house.getMaxX());
        assertEquals(-4, house.getMinZ());
        assertEquals(0, house.getMaxZ());
        assertEquals(0, house.getMinY());
        assertEquals(4, house.getMaxY());
        assertEquals(69, house.getBlocks().size());
        assertEquals(java.util.Collections.singleton("losttales:glowstone_house"),
                LostTalesWaystoneHouseFiles.bundledIds(CLASSPATH));
    }

    @Test
    public void aWholeHouseRaisesNoWarning() {
        assertEquals(new ArrayList<String>(), warnings(RULES, SCAN));
    }

    /** A key nothing reads, a missing one or a number out of bounds: the house is left out. */
    @Test
    public void theRulesAreReadStrictly() {
        assertWarns(RULES.replace("}", ", \"anchor\": [0, 0, 0]}"), SCAN, "nothing reads");
        assertWarns(RULES.replace("\"clearance\": 5, ", ""), SCAN, "writes no 'clearance'");
        assertWarns(RULES.replace("[1, 1, -2]", "[1, 1]"), SCAN, "three whole numbers");
        assertWarns(RULES.replace("[1, 1, -2]", "[1, 1.5, -2]"), SCAN, "whole number");
        assertWarns(RULES.replace("\"clearance\": 5", "\"clearance\": 30"), SCAN, "clears 30");
        assertWarns(RULES.replace("\"foundation\": 8", "\"foundation\": 40"), SCAN, "foundation 40");
        assertWarns(RULES.replace("minecraft:stone", "minecraft:cheese"), SCAN, "foundation with");
        assertWarns(RULES.replace("minecraft:stone", "stone"), SCAN, "foundation with");
    }

    /** A house whose waystone does not stand inside it is refused. */
    @Test
    public void theWaystoneStandsInsideTheHouse() {
        assertWarns(RULES.replace("[1, 1, -2]", "[5, 1, -2]"), SCAN, "outside the house");
        assertWarns(RULES.replace("[1, 1, -2]", "[1, 1, 2]"), SCAN, "outside the house");
        assertWarns(RULES.replace("[1, 1, -2]", "[1, 5, -2]"), SCAN, "outside the house");
        assertWarns(RULES.replace("[1, 1, -2]", "[0, 0, -4]"), SCAN, "where the waystone stands");
        assertWarns(RULES.replace("[0, 0, 3]", "[0, 1, 0]"), SCAN, "waystone itself");
        assertWarns(RULES.replace("[0, 0, 3]", "[0, 0, 20]"), SCAN, "more than 16");
    }

    /** A scan of anything but plain blocks, in bounds, is refused. */
    @Test
    public void theScanIsReadStrictly() {
        assertWarns(RULES, "#WALL#\n\n" + SCAN, "aliases");
        assertWarns(RULES, SCAN + "1.0.-1.#WALL#.0\n", "aliases");
        assertWarns(RULES, SCAN + "1.0v.-1.\"stone\".0\n", "fill down");
        assertWarns(RULES, SCAN + "1.0_.-1.\"stone\".0\n", "fill down");
        assertWarns(RULES, SCAN + "1,0,-1,stone\n", "x.y.z");
        assertWarns(RULES, SCAN + "1.0.-1.\"stone\".16\n", "metadata 16");
        assertWarns(RULES, SCAN + "1.-1.-1.\"stone\".0\n", "below its origin");
        assertWarns(RULES, SCAN + "1.6.-1.\"stone\".0\n", "layers high");
        assertWarns(RULES, SCAN + "20.0.-1.\"stone\".0\n", "at most 16 by 16");
        assertWarns(RULES, SCAN + "1.0.-1.\"lotr:tile.unknown\".0\n", "no block the game has");
        assertWarns(RULES, "", "lays no block");
        StringBuilder huge = new StringBuilder();
        for (int index = 0; index <= LostTalesStructureScan.MAX_BLOCKS; index++) {
            huge.append(index % 10).append(".0.").append(-(index / 10) % 10)
                    .append(".\"stone\".0\n");
        }
        assertWarns(RULES, huge.toString(), "more than " + LostTalesStructureScan.MAX_BLOCKS);
    }

    @Test
    public void aHouseWithoutItsScanIsLeftOut() {
        List<String> said = new ArrayList<String>();
        LostTalesWaystoneHouseFiles.parse(new StringReader(RULES), "hut.json", said,
                new LostTalesWaystoneHouseFiles.Scans() {
                    @Override
                    public Reader open(String rulesFile) {
                        return null;
                    }
                });
        assertTrue(said.toString(), said.toString().contains("hut.strscan"));
    }

    /**
     * A server's house with a bundled house's name takes its place, and a
     * new one joins it; one with a warning is left out.
     */
    @Test
    public void aServerHouseReplacesAndAdds() throws IOException {
        File folder = this.temporary.newFolder("structures");
        write(new File(folder, "glowstone_house.json"), RULES);
        write(new File(folder, "glowstone_house.strscan"), SCAN);
        write(new File(folder, "stone_hut.json"),
                RULES.replace("\"clearance\": 5", "\"clearance\": 6"));
        write(new File(folder, "stone_hut.strscan"), SCAN);
        write(new File(folder, "no_scan.json"), RULES);
        write(new File(folder, "Bad Name.json"), RULES);
        write(new File(folder, "Bad Name.strscan"), SCAN);

        DataFiles.Kind<LostTalesWaystoneHouse> bundledKind = LostTalesWaystoneHouseFiles.kind(
                LostTalesWaystoneHouseFiles.bundledScans(CLASSPATH), BLOCKS);
        DataFiles.Result<LostTalesWaystoneHouse> bundled = DataFiles.readBundled(CLASSPATH,
                LostTalesWaystoneHouseFiles.INDEX, LostTalesWaystoneHouseFiles.LIST_KEY,
                bundledKind);
        DataFiles.Kind<LostTalesWaystoneHouse> serverKind = LostTalesWaystoneHouseFiles.kind(
                LostTalesWaystoneHouseFiles.folderScans(folder), BLOCKS);
        DataFiles.Result<LostTalesWaystoneHouse> server = DataFiles.readFolder(folder,
                serverKind);
        List<LostTalesWaystoneHouse> houses = DataFiles.merge(bundled.values, server.values,
                serverKind);

        assertEquals(2, houses.size());
        assertEquals("losttales:glowstone_house", houses.get(0).getId());
        assertEquals("the server's own in the bundled one's place", "minecraft:stone",
                houses.get(0).getFoundationBlock());
        assertEquals("losttales:stone_hut", houses.get(1).getId());
        assertEquals(6, houses.get(1).getClearance());
        assertTrue(server.problems.toString(), server.problems.toString().contains("no_scan"));
        assertTrue(server.problems.toString(), server.problems.toString().contains("file name"));
    }

    @Test
    public void aScanTooLargeIsNotRead() throws IOException {
        File folder = this.temporary.newFolder("large");
        StringBuilder large = new StringBuilder();
        while (large.length() <= LostTalesStructureScan.MAX_CHARACTERS) {
            large.append("0.0.0.\"stone\".0\n");
        }
        write(new File(folder, "big.strscan"), large.toString());
        List<String> said = new ArrayList<String>();
        LostTalesWaystoneHouseFiles.parse(new StringReader(RULES), "big.json", said,
                LostTalesWaystoneHouseFiles.folderScans(folder));
        assertTrue(said.toString(), said.toString().contains("larger than"));
    }

    private static void assertWarns(String rules, String scan, String fragment) {
        List<String> warnings = warnings(rules, scan);
        assertFalse("no warning for " + rules + " / " + scan, warnings.isEmpty());
        assertTrue(warnings + " says " + fragment, warnings.toString().contains(fragment));
    }

    private static List<String> warnings(String rules, final String scan) {
        List<String> said = new ArrayList<String>();
        LostTalesWaystoneHouse house = LostTalesWaystoneHouseFiles.parse(
                new StringReader(rules), "test_house.json", said,
                new LostTalesWaystoneHouseFiles.Scans() {
                    @Override
                    public Reader open(String rulesFile) {
                        return new StringReader(scan);
                    }
                });
        assertNotNull(house);
        List<String> warnings = new ArrayList<String>(said);
        warnings.addAll(LostTalesWaystoneHouseFiles.warningsOf(house, BLOCKS));
        return warnings;
    }

    private static void write(File file, String text) throws IOException {
        Writer writer = new OutputStreamWriter(new FileOutputStream(file),
                StandardCharsets.UTF_8);
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }
}
