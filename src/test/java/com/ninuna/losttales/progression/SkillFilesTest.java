package com.ninuna.losttales.progression;

import com.ninuna.losttales.armour.ArmourWeight;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** A skill file with any warning is left out whole; a server's file takes a bundled one's place (PROG-01). */
public final class SkillFilesTest {
    private static final String GOOD = "{\"id\": \"losttales:archery\", \"counts\": ["
            + "{\"act\": \"hit\", \"weapons\": [\"bow\"]}], \"perks\": ["
            + "{\"id\": \"steady_draw\", \"levels\": [10, 30], \"effect\": \"draw_speed\", \"amount\": 0.1}]}";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void aGoodFileLoads() {
        assertEquals(Arrays.<String>asList(), read(GOOD).problems);
    }

    @Test
    public void aKeyNothingReadsLeavesTheFileOut() {
        DataFiles.Result<SkillDefinition> read = read(GOOD.replace("\"perks\"", "\"perk\""));
        assertEquals(0, read.values.size());
        assertTrue(read.problems.toString(), read.problems.toString().contains("nothing reads"));
    }

    @Test
    public void anUnknownEffectActOrWeaponLeavesTheFileOut() {
        assertLeftOut(GOOD.replace("draw_speed", "fly"), "no perk has");
        assertLeftOut(GOOD.replace("\"hit\"", "\"juggle\""), "no act");
        assertLeftOut(GOOD.replace("[\"bow\"]", "[\"wand\"]"), "no kind of weapon");
    }

    @Test
    public void aPerkNeedingAMissingPerkOrRankLeavesTheFileOut() {
        assertLeftOut(GOOD.replace("\"effect\": \"draw_speed\"",
                "\"needs\": \"eagle_eye\", \"effect\": \"draw_speed\""), "no other perk");
        assertLeftOut(GOOD.replace("\"perks\": [", "\"perks\": [{\"id\": \"quick\", \"levels\": [60],"
                + " \"needs\": \"steady_draw:3\", \"effect\": \"quick_nock\"},"), "rank 3");
    }

    @Test
    public void levelsOutOfOrderOrPastTheTopLeaveTheFileOut() {
        assertLeftOut(GOOD.replace("[10, 30]", "[30, 10]"), "lower level");
        assertLeftOut(GOOD.replace("[10, 30]", "[10, 101]"), "outside");
    }

    @Test
    public void aNumberAnEffectDoesNotReadLeavesTheFileOut() {
        assertLeftOut(GOOD.replace("\"amount\": 0.1", "\"amount\": 0.1, \"params\": {\"seconds\": 3}"),
                "does not read");
    }

    @Test
    public void anArmourSkillNamesItsWeightAndCountsArmour() {
        String armour = "{\"id\": \"losttales:light_armour\", \"weight\": \"light\","
                + " \"counts\": [{\"act\": \"armour\", \"per\": 1.0}]}";
        DataFiles.Result<SkillDefinition> read = read(armour);
        assertEquals(Arrays.<String>asList(), read.problems);
        assertEquals(ArmourWeight.LIGHT, read.values.get(0).getWeight());
        assertLeftOut(armour.replace("\"light\",", "\"neither\","), "light or heavy");
        assertLeftOut(armour.replace("\"weight\": \"light\",", ""),
                "only a skill with a weight");
    }

    @Test
    public void aBlockNamesTheWeaponsItIsDoneWith() {
        assertEquals(Arrays.<String>asList(), read(GOOD.replace("\"hit\"", "\"block\"")).problems);
        assertLeftOut(GOOD.replace("{\"act\": \"hit\", \"weapons\": [\"bow\"]}",
                "{\"act\": \"block\"}"), "names no weapons");
    }

    @Test
    public void aServersFileWithABundledIdTakesItsPlace() throws IOException {
        File server = this.folder.newFolder("skills");
        write(new File(server, "archery.json"), GOOD.replace("0.1", "0.2"));
        write(new File(server, "sailing.json"), GOOD.replace("losttales:archery", "losttales:sailing"));
        DataFiles.Result<SkillDefinition> bundled = read(GOOD);
        DataFiles.Result<SkillDefinition> own = DataFiles.readFolder(server, ProgressionFiles.SKILLS);
        assertEquals(Arrays.<String>asList(), own.problems);
        List<SkillDefinition> merged = DataFiles.merge(bundled.values, own.values,
                ProgressionFiles.SKILLS);
        assertEquals(2, merged.size());
        assertEquals(0.2D, merged.get(0).perk("steady_draw").getAmount(), 0.0D);
        assertEquals("losttales:sailing", merged.get(1).getId());
    }

    private static void write(File file, String text) throws IOException {
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }

    private static void assertLeftOut(String file, String why) {
        DataFiles.Result<SkillDefinition> read = read(file);
        assertEquals(file, 0, read.values.size());
        assertTrue(read.problems.toString(), read.problems.toString().contains(why));
    }

    private static DataFiles.Result<SkillDefinition> read(String file) {
        final Map<String, String> files = new HashMap<String, String>();
        files.put("skills/index.json", "{\"skills\": [\"skills/archery.json\"]}");
        files.put("skills/archery.json", file);
        return DataFiles.readBundled(new DataFiles.Source() {
            @Override
            public Reader open(String path) {
                String text = files.get(path);
                return text == null ? null : new StringReader(text);
            }
        }, "skills/index.json", "skills", ProgressionFiles.SKILLS);
    }
}
