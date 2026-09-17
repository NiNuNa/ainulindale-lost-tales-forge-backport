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
 * <p>There is no datapack reload in 1.7.10, so a quest whose JSON is
 * wrong is only found when a player walks into it. This reads
 * {@code quests/index.json}, parses every file it names, and puts the
 * definitions through the same validator the server logs from: a file
 * the index forgets, an id said twice, a stage with nothing in it or an
 * objective missing the parameters its type needs all fail here instead.</p>
 */
public final class BundledQuestDefinitionTest {

    private static final String ROOT = "/assets/losttales/";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    @Test
    public void everyIndexedQuestParsesAndValidatesCleanly() {
        List<String> files = index();
        assertTrue("the index names some quests", files.size() > 0);

        List<LostTalesQuestDefinition> quests =
                new ArrayList<LostTalesQuestDefinition>();
        Set<String> ids = new HashSet<String>();
        for (String file : files) {
            LostTalesQuestDefinition quest = parse(file);
            assertNotNull(file + " could not be parsed", quest);
            assertTrue(file + " has no id",
                    quest.getId() != null && quest.getId().length() > 0);
            assertTrue(file + " has no title",
                    quest.getTitle() != null && quest.getTitle().length() > 0);
            assertTrue("two quests share the id " + quest.getId(),
                    ids.add(quest.getId()));
            assertTrue(quest.getId() + " has no stages",
                    !quest.getStages().isEmpty());
            for (LostTalesQuestStageDefinition stage : quest.getStages()) {
                assertTrue(quest.getId() + " has a stage with no id",
                        stage.getId() != null && stage.getId().length() > 0);
                assertTrue(quest.getId() + " stage " + stage.getId()
                        + " asks for nothing", !stage.getObjectives().isEmpty());
            }
            quests.add(quest);
        }

        assertEquals("the bundled quests raise no warnings",
                new ArrayList<String>(),
                LostTalesQuestDefinitionValidator.describeWarnings(quests));
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
