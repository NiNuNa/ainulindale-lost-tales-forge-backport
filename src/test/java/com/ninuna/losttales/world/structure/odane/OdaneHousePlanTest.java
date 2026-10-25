package com.ninuna.losttales.world.structure.odane;

import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.world.waystone.LostTalesWaystoneHouse;
import com.ninuna.losttales.world.waystone.LostTalesWaystoneHouseFiles;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The Odane glowstone house is read through the house files, as a
 * generated waystone's house is, and a server's own glowstone_house takes
 * the bundled one's place (CONTENT-10).
 */
public final class OdaneHousePlanTest {
    private static final DataFiles.Source CLASSPATH = new DataFiles.Source() {
        @Override
        public Reader open(String path) {
            InputStream stream = OdaneHousePlanTest.class.getClassLoader()
                    .getResourceAsStream("assets/losttales/" + path);
            return stream == null ? null
                    : new InputStreamReader(stream, StandardCharsets.UTF_8);
        }
    };
    private static final LostTalesWaystoneHouseFiles.BlockNames BLOCKS =
            new LostTalesWaystoneHouseFiles.BlockNames() {
                @Override
                public boolean has(String name) {
                    return Arrays.asList("glowstone", "minecraft:glowstone", "stone",
                            "minecraft:stone", "planks").contains(name);
                }
            };

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void theOdaneHouseIsTheGlowstoneHouseFile() {
        assertEquals(LostTalesWaystoneHouseFiles.idOf("structures/glowstone_house.json"),
                OdaneHousePlan.HOUSE_ID);
        assertTrue(LostTalesWaystoneHouseFiles.bundledIds(CLASSPATH)
                .contains(OdaneHousePlan.HOUSE_ID));
    }

    /** The bundled house is four blocks by five, cleared five layers up, its man inside. */
    @Test
    public void theBundledHouseIsTheOdaneHouse() {
        OdaneHousePlan plan = OdaneHousePlan.from(houses(bundled()));

        assertNotNull(plan);
        assertEquals(0, plan.getMinX());
        assertEquals(3, plan.getMaxX());
        assertEquals(-4, plan.getMinZ());
        assertEquals(0, plan.getMaxZ());
        assertEquals("layers 1 to 5 cleared", 5, plan.getClearance());
        assertArrayEquals("the Odane man lives where the waystone would stand", new int[] {1, 1, -2},
                plan.getResident());
        assertEquals(69, plan.getBlocks().size());
        assertEquals("glowstone", plan.getBlocks().get(0).name);
    }

    /** A server's own glowstone_house is the one the Odane biomes build. */
    @Test
    public void aServersOwnHouseTakesItsPlace() throws IOException {
        File folder = this.temporary.newFolder("structures");
        write(new File(folder, "glowstone_house.json"), "{\"waystone\": [1, 1, 1],"
                + " \"arrival\": [0, 0, 3], \"clearance\": 4, \"foundation\": 2,"
                + " \"foundationBlock\": \"minecraft:stone\"}");
        write(new File(folder, "glowstone_house.strscan"),
                "0.0.0.\"stone\".0\n2.0.2.\"stone\".0\n0.3.0.\"planks\".1\n");
        DataFiles.Kind<LostTalesWaystoneHouse> kind = LostTalesWaystoneHouseFiles.kind(
                LostTalesWaystoneHouseFiles.folderScans(folder), BLOCKS);
        DataFiles.Result<LostTalesWaystoneHouse> own = DataFiles.readFolder(folder, kind);
        assertEquals(new ArrayList<String>(), own.problems);

        OdaneHousePlan plan = OdaneHousePlan.from(houses(
                DataFiles.merge(bundled(), own.values, kind)));

        assertNotNull(plan);
        assertEquals(3, plan.getBlocks().size());
        assertEquals("stone", plan.getBlocks().get(0).name);
        assertEquals(2, plan.getMaxX());
        assertEquals(0, plan.getMinZ());
        assertEquals(2, plan.getMaxZ());
        assertEquals(4, plan.getClearance());
        assertArrayEquals(new int[] {1, 1, 1}, plan.getResident());
    }

    /** With no glowstone_house loaded, the Odane biomes build nothing. */
    @Test
    public void noHouseBuildsNothing() {
        assertNull(OdaneHousePlan.from(houses(new ArrayList<LostTalesWaystoneHouse>())));
        assertNull(OdaneHousePlan.from(null));
        assertNull(OdaneHousePlan.of(null));
        assertNull(OdaneHousePlan.of(new LostTalesWaystoneHouse(OdaneHousePlan.HOUSE_ID,
                new ArrayList<com.ninuna.losttales.world.waystone.LostTalesStructureScan.Block>(),
                new int[3], new int[3], 5, 0, "minecraft:stone")));
    }

    @Test
    public void theGroundMayStepTwoBlocks() {
        assertTrue(OdaneHousePlan.isLevelEnough(64, 64));
        assertTrue(OdaneHousePlan.isLevelEnough(64, 66));
        assertFalse(OdaneHousePlan.isLevelEnough(64, 67));
    }

    private static List<LostTalesWaystoneHouse> bundled() {
        DataFiles.Result<LostTalesWaystoneHouse> read = DataFiles.readBundled(CLASSPATH,
                LostTalesWaystoneHouseFiles.INDEX, LostTalesWaystoneHouseFiles.LIST_KEY,
                LostTalesWaystoneHouseFiles.kind(
                        LostTalesWaystoneHouseFiles.bundledScans(CLASSPATH), BLOCKS));
        assertEquals(new ArrayList<String>(), read.problems);
        return read.values;
    }

    private static OdaneHousePlan.Houses houses(final List<LostTalesWaystoneHouse> loaded) {
        return new OdaneHousePlan.Houses() {
            @Override
            public LostTalesWaystoneHouse get(String id) {
                for (LostTalesWaystoneHouse house : loaded) {
                    if (house.getId().equals(id)) {
                        return house;
                    }
                }
                return null;
            }
        };
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
