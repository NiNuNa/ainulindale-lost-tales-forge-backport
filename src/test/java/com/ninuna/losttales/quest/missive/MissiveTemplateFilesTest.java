package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.progression.ProgressionFiles;
import com.ninuna.losttales.quest.QuestItemNames;
import com.ninuna.losttales.util.EnglishWords;
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
import java.util.List;
import java.util.Random;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The missive template files (QUEST-42): the bundled ones raise no warning
 * and are the notices boards always posted; every file is held to one
 * strict checker; a server's template with a bundled one's id takes its
 * place; {@code bundledMissiveTemplates} off leaves the bundled set out;
 * a gather of an item the game lacks is left out; and with no template
 * left a board posts nothing.
 */
public final class MissiveTemplateFilesTest {
    private static final List<String> ISSUERS = Arrays.asList("local_watch",
            "road_warden", "village_reeve", "caravan_master", "quartermaster");
    private static final List<String> KILL_TITLES = Arrays.asList(
            "trouble_on_the_road", "dangerous_work", "clear_the_paths");
    private static final List<String> KILL_FLAVOURS = Arrays.asList(
            "shapes_beyond_firelight", "roads_open", "steady_blade");
    private static final List<String> GATHER_TITLES = Arrays.asList(
            "materials_wanted", "gatherers_pay", "stores_run_low");
    private static final List<String> GATHER_FLAVOURS = Arrays.asList(
            "small_stores", "plain_notice", "useful_materials");
    private static final QuestItemNames.Items EVERY_ITEM = new QuestItemNames.Items() {
        @Override
        public boolean has(String itemId) {
            return true;
        }
    };

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @After
    public void forget() {
        MissiveTemplates.clear();
    }

    @Test
    public void theBundledFilesRaiseNoWarning() {
        DataFiles.Result<MissiveTemplate> read = bundled();
        assertEquals(Collections.<String>emptyList(), read.problems);
        assertEquals(13, read.values.size());
    }

    /** The bundled templates are the notices the generator always wrote, in its order. */
    @Test
    public void theBundledFilesAreTheNoticesBoardsAlwaysPosted() {
        List<MissiveTemplate> read = bundled().values;
        String[][] expected = {
                {"kill", "group", "enemy", "4", "8", "12"},
                {"kill", "entity", "Zombie", "3", "7", "10"},
                {"kill", "entity", "Skeleton", "3", "6", "12"},
                {"kill", "entity", "Spider", "3", "6", "12"},
                {"kill", "entity", "Creeper", "2", "4", "18"},
                {"gather", "item", "minecraft:coal", "8", "18", "3"},
                {"gather", "item", "minecraft:iron_ingot", "4", "10", "6"},
                {"gather", "item", "minecraft:gold_ingot", "2", "6", "10"},
                {"gather", "item", "minecraft:wheat", "8", "18", "3"},
                {"gather", "item", "minecraft:leather", "3", "8", "6"},
                {"gather", "item", "minecraft:string", "4", "10", "5"},
                {"gather", "item", "minecraft:bone", "4", "10", "5"},
                {"gather", "item", "minecraft:log", "8", "20", "2"}};
        for (int index = 0; index < expected.length; index++) {
            MissiveTemplate template = read.get(index);
            String[] row = expected[index];
            String where = template.getId();
            assertEquals(where, row[0], template.getKind());
            assertEquals(where, row[1], template.getTargetParam());
            assertEquals(where, row[2], template.getTarget());
            assertEquals(where, Integer.parseInt(row[3]), template.getLeastCount());
            assertEquals(where, Integer.parseInt(row[4]), template.getMostCount());
            assertEquals(where, Integer.parseInt(row[5]), template.getExperience());
            assertFalse(where, template.isWritten());
            assertEquals(where, ISSUERS, template.getIssuers());
            assertEquals(where, template.isKill() ? KILL_TITLES : GATHER_TITLES,
                    template.getTitles());
            assertEquals(where, template.isKill() ? KILL_FLAVOURS : GATHER_FLAVOURS,
                    template.getFlavours());
        }
    }

    /** Every word a bundled template names, and every target, has its English line. */
    @Test
    public void everyBundledWordHasAnEnglishLine() {
        for (MissiveTemplate template : bundled().values) {
            for (String title : template.getTitles()) {
                assertTrue(title, EnglishWords.INSTANCE.has(MissiveWords.titleKey(title)));
            }
            for (String issuer : template.getIssuers()) {
                assertTrue(issuer, EnglishWords.INSTANCE.has(MissiveWords.issuerKey(issuer)));
            }
            for (String flavour : template.getFlavours()) {
                assertTrue(flavour, EnglishWords.INSTANCE.has(MissiveWords.flavorKey(flavour)));
            }
            assertTrue(template.getTarget(), EnglishWords.INSTANCE.has(
                    MissiveWords.targetKey(template.getTarget())));
            assertTrue(EnglishWords.INSTANCE.has(MissiveWords.descriptionKey(template.getKind())));
            assertTrue(EnglishWords.INSTANCE.has(MissiveWords.objectiveKey(template.getKind())));
        }
    }

    /** A key nothing reads, a missing or wrong field, a number out of bounds: each leaves a file out. */
    @Test
    public void everyFileIsHeldToEveryRule() {
        String good = "{\"id\":\"bree:wolves\",\"kind\":\"kill\",\"entity\":\"lotr.Wolf\","
                + "\"count\":[4,8],\"experience\":10,\"titles\":[\"Wanted: Wolf Pelts\"],"
                + "\"issuers\":[\"Barliman Butterbur\"],\"flavours\":[\"Wolves again.\"]}";
        assertEquals(Collections.<String>emptyList(), serverWarnings(good));
        assertWarns(good.replace("\"experience\":10", "\"experience\":10,\"coins\":4"),
                "'coins', which nothing reads");
        assertWarns(good.replace("\"kill\"", "\"hunt\""), "a kill or a gather");
        assertWarns(good.replace("\"entity\":\"lotr.Wolf\"", "\"item\":\"minecraft:coal\""),
                "names an 'item'");
        assertWarns(good.replace("\"entity\":\"lotr.Wolf\"",
                "\"entity\":\"lotr.Wolf\",\"group\":\"enemy\""), "both");
        assertWarns(good.replace("\"entity\":\"lotr.Wolf\"", "\"group\":\"wolves\""),
                "the group 'wolves'");
        assertWarns(good.replace("[4,8]", "[8,4]"), "the least first");
        assertWarns(good.replace("[4,8]", "[0,4]"), "a count runs from 1");
        assertWarns(good.replace("[4,8]", "[4,999]"), "a count runs from 1");
        assertWarns(good.replace("[4,8]", "[4]"), "two whole numbers");
        assertWarns(good.replace(",\"experience\":10", ""), "no 'experience'");
        assertWarns(good.replace("\"experience\":10", "\"experience\":-1"), "0 to");
        assertWarns(good.replace("[\"Wanted: Wolf Pelts\"]", "[]"), "nothing in 'titles'");
        assertWarns(good.replace(",\"titles\":[\"Wanted: Wolf Pelts\"]", ""), "no 'titles'");
        assertWarns(good.replace("bree:wolves", "Bree Wolves"), "the id");
        String gather = "{\"id\":\"bree:pipeweed\",\"kind\":\"gather\",\"item\":\"coal\","
                + "\"count\":[4,8],\"experience\":3,\"titles\":[\"Pipe-weed Wanted\"]}";
        assertWarns(gather, "without its namespace");
        assertWarns(gather.replace(",\"item\":\"coal\"", ""), "no 'item'");
        assertEquals("issuers and flavours may be left out", Collections.<String>emptyList(),
                serverWarnings(gather.replace("\"coal\"", "\"minecraft:coal\"")));
    }

    /** A bundled file names its words by id; a server's writes them, and holds them to their bound. */
    @Test
    public void wordsAreIdsInABundledFileAndWrittenInAServers() {
        String file = "{\"id\":\"losttales:wolves\",\"kind\":\"kill\",\"entity\":\"lotr.Wolf\","
                + "\"count\":[4,8],\"experience\":10,\"titles\":[\"%s\"]}";
        assertEquals(Collections.<String>emptyList(),
                warnings(String.format(file, "dangerous_work"), false));
        assertTrue(warnings(String.format(file, "Wanted: Wolf Pelts"), false).toString()
                .contains("no word id"));
        assertEquals(Collections.<String>emptyList(),
                warnings(String.format(file, "Wanted: Wolf Pelts"), true));
        StringBuilder longTitle = new StringBuilder();
        while (longTitle.length() <= MissiveWords.MAX_WRITTEN_BYTES) {
            longTitle.append('a');
        }
        assertTrue(warnings(String.format(file, longTitle), true).toString()
                .contains("pass " + MissiveWords.MAX_WRITTEN_BYTES));
    }

    /** A server's template with a bundled one's id takes its place; a new one comes after. */
    @Test
    public void aServersTemplateReplacesTheBundledOneOfItsId() throws IOException {
        write("zombies.json", "{\"id\":\"losttales:zombies\",\"kind\":\"kill\","
                + "\"entity\":\"Zombie\",\"count\":[10,12],\"experience\":5,"
                + "\"titles\":[\"The Dead Walk\"]}");
        write("wolves.json", "{\"id\":\"bree:wolves\",\"kind\":\"kill\","
                + "\"entity\":\"lotr.Wolf\",\"count\":[4,8],\"experience\":10,"
                + "\"titles\":[\"Wanted: Wolf Pelts\"]}");
        assertEquals(Collections.<String>emptyList(), load(true, EVERY_ITEM));
        List<MissiveTemplate> loaded = MissiveTemplates.templates();
        assertEquals(14, loaded.size());
        MissiveTemplate zombies = loaded.get(1);
        assertEquals("losttales:zombies", zombies.getId());
        assertTrue(zombies.isWritten());
        assertEquals(10, zombies.getLeastCount());
        assertEquals("bree:wolves", loaded.get(13).getId());
    }

    /** {@code bundledMissiveTemplates} off: only the server's own. */
    @Test
    public void theSettingLeavesTheBundledSetOut() throws IOException {
        write("wolves.json", "{\"id\":\"bree:wolves\",\"kind\":\"kill\","
                + "\"entity\":\"lotr.Wolf\",\"count\":[4,8],\"experience\":10,"
                + "\"titles\":[\"Wanted: Wolf Pelts\"]}");
        load(false, EVERY_ITEM);
        assertEquals(1, MissiveTemplates.templates().size());
        MissiveTemplate picked = MissiveTemplates.pick(new Random(3));
        assertEquals("bree:wolves", picked.getId());
        LostTalesMissiveData notice = LostTalesMissiveGenerator.createRandomMissive(null,
                "dim0_1_64_1", 1000L, 0, new Random(3));
        assertTrue(notice.isValid());
        assertTrue(notice.isWritten());
        assertEquals("Wanted: Wolf Pelts", MissiveWords.title(notice));
        assertEquals("lotr.Wolf", notice.getTarget());
    }

    /** A gather of an item the game does not have is left out, with a line naming it. */
    @Test
    public void aGatherOfAnItemTheGameLacksIsLeftOut() throws IOException {
        write("weed.json", "{\"id\":\"bree:weed\",\"kind\":\"gather\","
                + "\"item\":\"minecraft:pipeweed\",\"count\":[4,8],\"experience\":3,"
                + "\"titles\":[\"Pipe-weed Wanted\"]}");
        List<String> problems = load(false, new QuestItemNames.Items() {
            @Override
            public boolean has(String itemId) {
                return !"minecraft:pipeweed".equals(itemId);
            }
        });
        assertEquals(1, problems.size());
        assertTrue(problems.get(0), problems.get(0).contains("minecraft:pipeweed"));
        assertTrue(MissiveTemplates.templates().isEmpty());
    }

    /**
     * A world that cannot seal (here, none: no key) posts no letter, though
     * a template is loaded, so no notice stands that nobody could accept.
     */
    @Test
    public void withoutASealKeyABoardPostsNothing() {
        load(true, EVERY_ITEM);
        assertFalse(MissiveTemplates.templates().isEmpty());
        assertNull(LostTalesMissiveGenerator.createRandomMissiveLetter(null, "dim0_1_64_1",
                1000L, 0, new Random(1)));
    }

    /** With no template left, a board posts nothing. */
    @Test
    public void withNoTemplateABoardPostsNothing() {
        load(false, EVERY_ITEM);
        assertTrue(MissiveTemplates.templates().isEmpty());
        assertNull(MissiveTemplates.pick(new Random(1)));
        assertNull(LostTalesMissiveGenerator.createRandomMissive(null, "dim0_1_64_1",
                1000L, 0, new Random(1)));
    }

    /** Kills and gathers come as often as each other, as they always did. */
    @Test
    public void killsAndGathersAreEquallyLikely() {
        load(true, EVERY_ITEM);
        Random random = new Random(7);
        int kills = 0;
        for (int index = 0; index < 2000; index++) {
            if (MissiveTemplates.pick(random).isKill()) {
                kills++;
            }
        }
        assertTrue(String.valueOf(kills), kills > 900 && kills < 1100);
        assertSame(MissiveTemplates.templates(), MissiveTemplates.templates());
    }

    private static DataFiles.Result<MissiveTemplate> bundled() {
        return DataFiles.readBundled(ProgressionFiles.CLASSPATH, MissiveTemplateFiles.INDEX,
                MissiveTemplateFiles.LIST_KEY, MissiveTemplateFiles.BUNDLED);
    }

    private List<String> load(boolean bundled, QuestItemNames.Items items) {
        return MissiveTemplates.load(ProgressionFiles.CLASSPATH, this.folder.getRoot(),
                bundled, items);
    }

    private void write(String name, String json) throws IOException {
        Writer writer = new OutputStreamWriter(new FileOutputStream(
                new File(this.folder.getRoot(), name)), StandardCharsets.UTF_8);
        try {
            writer.write(json);
        } finally {
            writer.close();
        }
    }

    private static List<String> serverWarnings(String json) {
        return warnings(json, true);
    }

    private static List<String> warnings(String json, boolean written) {
        List<String> said = new ArrayList<String>();
        MissiveTemplate template = MissiveTemplateFiles.parse(new StringReader(json),
                "test.json", said, written);
        List<String> all = new ArrayList<String>(said);
        all.addAll(MissiveTemplateFiles.warningsOf(template));
        return all;
    }

    private static void assertWarns(String json, String about) {
        List<String> found = serverWarnings(json);
        assertFalse(json, found.isEmpty());
        assertTrue(found.toString(), found.toString().contains(about));
    }
}
