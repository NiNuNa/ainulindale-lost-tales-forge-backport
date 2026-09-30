package com.ninuna.losttales.quest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The quest files shipped in the jar, read the way the game reads them.
 *
 * <p>A quest whose JSON is wrong is otherwise only found when a player
 * walks into it. This reads {@code quests/index.json} and every file it
 * names through {@link BundledQuestFiles}, as the server and the client
 * do, and puts the quests through the checker the server logs from: a
 * file the index names but the jar lacks, an id said twice, a stage with
 * nothing in it or an objective missing what its type needs all fail
 * here instead.</p>
 */
public final class BundledQuestDefinitionTest {

    private static final String ROOT = "/assets/losttales/";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    @Test
    public void everyIndexedQuestParsesAndValidatesCleanly() {
        List<String> files = index();
        assertTrue("the index names some quests", files.size() > 0);

        BundledQuestFiles.Result read = BundledQuestFiles.read(
                new BundledQuestFiles.Source() {
                    @Override
                    public Reader open(String path) {
                        return BundledQuestDefinitionTest.open(path);
                    }
                });
        assertEquals("no bundled file is left out",
                new ArrayList<String>(), read.problems);
        assertEquals("every indexed file loads", files.size(),
                read.quests.size());
        Set<String> ids = new HashSet<String>();
        for (LostTalesQuestDefinition quest : read.quests) {
            assertTrue(quest.getId() + " has no title",
                    quest.getTitle() != null && quest.getTitle().length() > 0);
            assertTrue("two quests share the id " + quest.getId(),
                    ids.add(quest.getId()));
        }

        assertEquals("the bundled quests raise no warnings",
                new ArrayList<String>(),
                LostTalesQuestDefinitionValidator.describeWarnings(read.quests));
    }

    /** Nia is named the one way the game names her kind. */
    @Test
    public void niaIsNamedByHerRegisteredName() {
        LostTalesQuestDefinition quest = parse("quests/tutorial/meet_nia.json");
        assertEquals("losttales.Nia",
                LostTalesQuestParams.value(quest.getInteraction(), "entity"));
    }

    /**
     * Nia's tutorial hands its sticks over rather than only walking back,
     * which is the delivery objective the rest of the content is written
     * against.
     */
    @Test
    public void niasTutorialEndsInADelivery() {
        LostTalesQuestDefinition quest = parse("quests/tutorial/meet_nia.json");
        assertNotNull(quest);
        LostTalesQuestStageDefinition last = quest.getStages()
                .get(quest.getStages().size() - 1);
        LostTalesQuestObjectiveDefinition objective = last.getObjectives().get(0);
        assertTrue("the last thing asked is a delivery",
                LostTalesQuestObjectiveType.DELIVER.is(objective));
        assertEquals("minecraft:stick", objective.getParam("item", ""));
        assertEquals(4, LostTalesQuestObjectiveTextHelper
                .getObjectiveTargetCount(objective));
        assertTrue("it names who receives them",
                objective.getParam("entity", "").length() > 0);
    }

    private static List<String> index() {
        Reader reader = open("quests/index.json");
        assertNotNull("quests/index.json is missing from the jar", reader);
        try {
            return LostTalesQuestDefinitionJsonParser.parseQuestIndex(reader);
        } finally {
            close(reader);
        }
    }

    private static LostTalesQuestDefinition parse(String file) {
        Reader reader = open(file);
        assertNotNull(file + " is named by the index but is not in the jar",
                reader);
        try {
            return LostTalesQuestDefinitionJsonParser.parseQuest(reader, file);
        } finally {
            close(reader);
        }
    }

    private static Reader open(String file) {
        InputStream stream = BundledQuestDefinitionTest.class
                .getResourceAsStream(ROOT + file);
        return stream == null ? null : new InputStreamReader(stream, UTF_8);
    }

    private static void close(Reader reader) {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (java.io.IOException ignored) {
            // Reading a bundled file is all this test does.
        }
    }
}
