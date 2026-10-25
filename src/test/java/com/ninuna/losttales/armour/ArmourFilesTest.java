package com.ninuna.losttales.armour;

import com.ninuna.losttales.data.DataFiles;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The armour weight and named set files (CONTENT-06, CONTENT-07,
 * CONTENT-11, MOD-17): a good file loads, a file with any warning is left
 * out whole, a server's file of a bundled id takes its place, and weight
 * files merge with a later file's entry over an earlier one's.
 */
public final class ArmourFilesTest {
    private static final String WEIGHTS = "{\"id\": \"losttales:weights\", \"materials\":"
            + " {\"IRON\": \"heavy\", \"CHAIN\": \"light\", \"LOTR_KAFTAN\": \"neither\"},"
            + " \"items\": {\"losttales:arnorian_helmet_light\": \"light\"}}";
    private static final String SET = "{\"id\": \"losttales:gondorian\","
            + " \"helmet\": [\"lotr:item.helmetGondor\", \"lotr:item.helmetGondorWinged\"],"
            + " \"chestplate\": [\"lotr:item.bodyGondor\"], \"leggings\": [\"lotr:item.legsGondor\"],"
            + " \"boots\": [\"lotr:item.bootsGondor\"],"
            + " \"bonus\": [{\"effect\": \"protection\", \"amount\": 0.05}]}";
    private static final String RELIC = SET.replace("losttales:gondorian", "losttales:isildurs")
            .replace("\"bonus\"", "\"relic\": true, \"names\": {\"helmet\": \"Isildur's Helm\"},"
                    + " \"lore\": {\"helmet\": \"Worn at the Last Alliance.\"}, \"bonus\"");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void goodFilesLoad() {
        DataFiles.Result<WeightFile> weights = weights(WEIGHTS);
        assertEquals(Arrays.<String>asList(), weights.problems);
        WeightFile file = weights.values.get(0);
        assertEquals(ArmourWeight.HEAVY, file.getMaterials().get("IRON"));
        assertEquals(ArmourWeight.NEITHER, file.getMaterials().get("LOTR_KAFTAN"));
        assertEquals(ArmourWeight.LIGHT, file.getItems().get("losttales:arnorian_helmet_light"));
        DataFiles.Result<NamedSet> sets = sets(SET);
        assertEquals(Arrays.<String>asList(), sets.problems);
        NamedSet set = sets.values.get(0);
        assertEquals(2, set.items(ArmourSlot.HELMET).size());
        assertEquals(SetEffect.PROTECTION, set.getBonuses().get(0).getEffect());
        assertTrue(!set.isRelic());
    }

    @Test
    public void aRelicSetNamesItsPieces() {
        DataFiles.Result<NamedSet> sets = sets(RELIC);
        assertEquals(Arrays.<String>asList(), sets.problems);
        NamedSet relic = sets.values.get(0);
        assertTrue(relic.isRelic());
        assertEquals("Isildur's Helm", relic.pieceName(ArmourSlot.HELMET));
        assertEquals("Worn at the Last Alliance.", relic.pieceLore(ArmourSlot.HELMET));
        assertEquals("", relic.pieceName(ArmourSlot.BOOTS));
        assertLeftOutSet(RELIC.replace("\"relic\": true, ", ""), "only a relic set");
        assertLeftOutSet(RELIC.replace("\"helmet\": \"Isildur's Helm\"", "\"cloak\": \"x\""),
                "the pieces are");
    }

    @Test
    public void aWeightFileWithAnyWarningIsLeftOut() {
        assertLeftOutWeights(WEIGHTS.replace("\"heavy\"", "\"weighty\""), "light, heavy or neither");
        assertLeftOutWeights(WEIGHTS.replace("\"materials\"", "\"material\""), "nothing reads");
        assertLeftOutWeights(WEIGHTS.replace("losttales:arnorian_helmet_light", "helmet"),
                "not an item's id");
        assertLeftOutWeights(WEIGHTS.replace("\"IRON\"", "\"IRON PLATE\""), "not a material's name");
        assertLeftOutWeights("{\"id\": \"losttales:empty\"}", "weighs nothing");
    }

    @Test
    public void aSetWithAnyWarningIsLeftOut() {
        assertLeftOutSet(SET.replace("\"boots\": [\"lotr:item.bootsGondor\"],", ""), "its boots takes");
        assertLeftOutSet(SET.replace("\"protection\"", "\"flight\""), "the effects are");
        assertLeftOutSet(SET.replace("0.05", "1.5"), "at most 1");
        assertLeftOutSet(SET.replace("0.05", "0"), "above 0");
        assertLeftOutSet(SET.replace("\"protection\", \"amount\": 0.05}",
                "\"protection\", \"amount\": 0.05}, {\"effect\": \"protection\", \"amount\": 0.05}"),
                "written twice");
        assertLeftOutSet(SET.replace("\"protection\", \"amount\": 0.05}",
                "\"experience\", \"amount\": 0.1}"), "no skill's id");
        assertLeftOutSet(SET.replace("\"amount\": 0.05}",
                "\"amount\": 0.05, \"skill\": \"losttales:archery\"}"), "only experience reads");
        assertLeftOutSet(SET.replace("\"bonus\": [{\"effect\": \"protection\", \"amount\": 0.05}]",
                "\"bonus\": []"), "between 1 and 3");
        StringBuilder four = new StringBuilder("\"bonus\": [");
        for (String effect : new String[] { "speed", "fire", "falls", "arrows" }) {
            four.append(four.length() > 10 ? ", " : "").append("{\"effect\": \"").append(effect)
                    .append("\", \"amount\": 0.1}");
        }
        assertLeftOutSet(SET.replace("\"bonus\": [{\"effect\": \"protection\", \"amount\": 0.05}]",
                four.append("]").toString()), "between 1 and 3");
        assertLeftOutSet(SET.replace("\"lotr:item.bodyGondor\"",
                "\"lotr:item.bodyGondor\", \"lotr:item.bodyGondor\""), "twice");
    }

    @Test
    public void anExperienceBonusNamesItsSkill() {
        DataFiles.Result<NamedSet> sets = sets(SET.replace("\"protection\", \"amount\": 0.05}",
                "\"experience\", \"amount\": 0.1, \"skill\": \"losttales:archery\"}"));
        assertEquals(Arrays.<String>asList(), sets.problems);
        assertEquals("losttales:archery", sets.values.get(0).getBonuses().get(0).getSkill());
    }

    @Test
    public void aServersFileTakesABundledOnesPlaceAndWeightsMergeInOrder() throws IOException {
        File weightsFolder = this.folder.newFolder("weights");
        File setsFolder = this.folder.newFolder("sets");
        write(new File(weightsFolder, "weights.json"),
                WEIGHTS.replace("\"IRON\": \"heavy\"", "\"IRON\": \"light\""));
        write(new File(weightsFolder, "other_mod.json"), "{\"id\": \"othermod:weights\","
                + " \"materials\": {\"CHAIN\": \"heavy\", \"OTHER_STEEL\": \"heavy\"}}");
        write(new File(setsFolder, "gondorian.json"), SET.replace("0.05", "0.1"));
        write(new File(setsFolder, "own.json"), SET.replace("losttales:gondorian", "myserver:own"));
        List<String> problems = new ArrayList<String>();
        ArmourCatalogue catalogue = Armour.read(source(WEIGHTS, SET), weightsFolder, setsFolder,
                problems);
        assertEquals(new ArrayList<String>(), problems);
        assertEquals("the server's file of the bundled id stands in its place",
                ArmourWeight.LIGHT, catalogue.weightOf("", "IRON"));
        assertEquals("a later file's entry goes over an earlier one's",
                ArmourWeight.HEAVY, catalogue.weightOf("", "CHAIN"));
        assertEquals(ArmourWeight.HEAVY, catalogue.weightOf("", "OTHER_STEEL"));
        assertEquals(0.1D, catalogue.set("losttales:gondorian").getBonuses().get(0).getAmount(),
                0.0D);
        assertEquals(2, catalogue.getSets().size());
        assertEquals("myserver:own", catalogue.getSets().get(1).getId());
    }

    @Test
    public void anItemsWeightGoesBeforeItsMaterialsAndAnUnlistedOneIsNeither() {
        ArmourCatalogue catalogue = ArmourCatalogue.of(weights(WEIGHTS).values,
                new ArrayList<NamedSet>());
        assertEquals(ArmourWeight.LIGHT, catalogue.weightOf("losttales:arnorian_helmet_light",
                "IRON"));
        assertEquals(ArmourWeight.HEAVY, catalogue.weightOf("minecraft:iron_helmet", "IRON"));
        assertEquals(ArmourWeight.NEITHER, catalogue.weightOf("othermod:hat", "OTHER_CLOTH"));
        assertEquals(ArmourWeight.NEITHER, catalogue.weightOf(null, null));
    }

    @Test
    public void aFilesIdComesFromItsNameWhenItWritesNone() {
        assertEquals("losttales:gondorian", ArmourFiles.idFromFile("armour/sets/gondorian.json"));
        assertTrue(ArmourFiles.isId("losttales:striders_garb"));
        assertTrue(!ArmourFiles.isId("Losttales:Garb"));
    }

    private static void assertLeftOutWeights(String file, String why) {
        DataFiles.Result<WeightFile> read = weights(file);
        assertEquals(file, 0, read.values.size());
        assertTrue(read.problems.toString(), read.problems.toString().contains(why));
    }

    private static void assertLeftOutSet(String file, String why) {
        DataFiles.Result<NamedSet> read = sets(file);
        assertEquals(file, 0, read.values.size());
        assertTrue(read.problems.toString(), read.problems.toString().contains(why));
    }

    private static DataFiles.Result<WeightFile> weights(String file) {
        return DataFiles.readBundled(source(file, SET), ArmourFiles.INDEX,
                ArmourFiles.WEIGHTS_KEY, ArmourFiles.WEIGHTS);
    }

    private static DataFiles.Result<NamedSet> sets(String file) {
        return DataFiles.readBundled(source(WEIGHTS, file), ArmourFiles.INDEX,
                ArmourFiles.SETS_KEY, ArmourFiles.SETS);
    }

    private static DataFiles.Source source(String weights, String set) {
        final Map<String, String> files = new HashMap<String, String>();
        files.put(ArmourFiles.INDEX, "{\"weights\": [\"armour/weights/weights.json\"],"
                + " \"sets\": [\"armour/sets/gondorian.json\"]}");
        files.put("armour/weights/weights.json", weights);
        files.put("armour/sets/gondorian.json", set);
        return new DataFiles.Source() {
            @Override
            public Reader open(String path) {
                String text = files.get(path);
                return text == null ? null : new StringReader(text);
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
