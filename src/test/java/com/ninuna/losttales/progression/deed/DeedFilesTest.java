package com.ninuna.losttales.progression.deed;

import com.ninuna.losttales.progression.ProgressionFiles;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.quest.BundledQuestFiles;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The deed files: the bundled ones load whole with their words; a wrong file is left out (PROG-11). */
public final class DeedFilesTest {

    @Test
    public void everyBundledDeedLoadsWithItsWords() {
        DataFiles.Result<DeedDefinition> read = DataFiles.readBundled(ProgressionFiles.CLASSPATH,
                DeedFiles.INDEX, "deeds", DeedFiles.KIND);
        assertEquals(new ArrayList<String>(), read.problems);
        assertEquals("about twenty deeds ship first (Nils, 2026-10-19)", 20, read.values.size());
        Map<String, String> english = LostTalesLangFile.english();
        for (DeedCategory category : DeedCategory.values()) {
            assertNotNull(category + " is named", english.get(DeedWords.categoryKey(category)));
        }
        for (DeedDefinition deed : read.values) {
            assertTrue(deed.getId() + " is worded by its achievement's lines",
                    deed.getName().length() == 0 && deed.getDescription().length() == 0);
            assertNotNull(deed.getId(), english.get(DeedWords.nameKey(deed.getId())));
            assertNotNull(deed.getId(), english.get(DeedWords.descriptionKey(deed.getId())));
            if (deed.hasTitle()) {
                assertNotNull(deed.getId() + " names its title",
                        english.get(DeedWords.titleKey(deed.getId())));
            }
            assertTrue(deed.getId() + " stands in a category",
                    DeedCategory.fromWord(deed.getCategory()) != null);
        }
    }

    @Test
    public void everyQuestABundledDeedNamesIsABundledQuest() {
        Set<String> quests = new HashSet<String>();
        for (LostTalesQuestDefinition quest : BundledQuestFiles.read(new BundledQuestFiles.Source() {
            @Override
            public Reader open(String path) {
                InputStream stream = DeedFilesTest.class.getResourceAsStream(
                        "/" + BundledQuestFiles.classpathPath(path));
                return stream == null ? null : new InputStreamReader(stream, StandardCharsets.UTF_8);
            }
        }).quests) {
            quests.add(quest.getId());
        }
        for (DeedDefinition deed : DataFiles.readBundled(ProgressionFiles.CLASSPATH,
                DeedFiles.INDEX, "deeds", DeedFiles.KIND).values) {
            for (String quest : deed.getQuests()) {
                assertTrue(deed.getId() + " names " + quest, quests.contains(quest));
            }
        }
    }

    @Test
    public void aWrongDeedIsLeftOut() {
        String good = "{\"id\": \"losttales:troll\", \"category\": \"foes\", \"kind\": \"kill\","
                + " \"creatures\": [\"lotr.Troll\"], \"count\": 3}";
        assertEquals(Arrays.<String>asList(), read(good).problems);
        assertLeftOut(good.replace("\"foes\"", "\"battles\""), "category");
        assertLeftOut(good.replace("\"kill\"", "\"juggle\""), "no deed has");
        assertLeftOut(good.replace("\"count\": 3", "\"count\": 0"), "count");
        assertLeftOut(good.replace("\"creatures\": [\"lotr.Troll\"], ", ""), "no creatures");
        assertLeftOut("{\"id\": \"losttales:walk\", \"category\": \"journeys\", \"kind\": \"places\","
                + " \"places\": [\"BREE\"]}", "neither");
        assertLeftOut(good.replace("\"count\": 3", "\"count\": 3, \"alignment\": \"lotr:bree\""),
                "faction*amount");
        assertLeftOut(good.replace("\"count\": 3", "\"count\": 3, \"renown\": 5"), "nothing reads");
    }

    @Test
    public void aDeedsTargetFollowsItsKind() {
        DeedDefinition places = deed(DeedDefinition.Kind.PLACES, Arrays.asList("lotr:BREE", "lotr:FORNOST"),
                new ArrayList<String>(), 1, 0);
        assertEquals(2L, places.target());
        DeedDefinition listed = deed(DeedDefinition.Kind.QUESTS, new ArrayList<String>(),
                Arrays.asList("losttales:a", "losttales:b", "losttales:c"), 1, 0);
        assertEquals(3L, listed.target());
        DeedDefinition counted = deed(DeedDefinition.Kind.QUESTS, new ArrayList<String>(),
                new ArrayList<String>(), 10, 0);
        assertEquals(10L, counted.target());
        assertEquals(50L, deed(DeedDefinition.Kind.CHARACTER_LEVEL, new ArrayList<String>(),
                new ArrayList<String>(), 1, 50).target());
    }

    private static DeedDefinition deed(DeedDefinition.Kind kind, List<String> places,
                                       List<String> quests, int count, int level) {
        return new DeedDefinition("losttales:test", "tales", "", kind, places, 64.0D,
                new ArrayList<String>(), false, quests, count, "", level, false, "", false, "", "");
    }

    private static void assertLeftOut(String file, String why) {
        DataFiles.Result<DeedDefinition> read = read(file);
        assertEquals(file, 0, read.values.size());
        assertTrue(read.problems.toString(), read.problems.toString().contains(why));
    }

    private static DataFiles.Result<DeedDefinition> read(String file) {
        final Map<String, String> files = new HashMap<String, String>();
        files.put("deeds/index.json", "{\"deeds\": [\"deeds/test.json\"]}");
        files.put("deeds/test.json", file);
        return DataFiles.readBundled(new DataFiles.Source() {
            @Override
            public Reader open(String path) {
                String text = files.get(path);
                return text == null ? null : new StringReader(text);
            }
        }, "deeds/index.json", "deeds", DeedFiles.KIND);
    }
}
