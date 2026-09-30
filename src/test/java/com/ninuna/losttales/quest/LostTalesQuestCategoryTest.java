package com.ninuna.losttales.quest;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.StatCollector;
import net.minecraft.util.StringTranslate;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The one rule for a quest's category, which the journal and a quest
 * card both follow, and the one way a stage is named: its id, which the
 * journal's lines are written under.
 */
public final class LostTalesQuestCategoryTest {

    @BeforeClass
    public static void loadTheModsWords() {
        StringTranslate.inject(LostTalesQuestCategoryTest.class
                .getResourceAsStream("/assets/losttales/lang/en_US.lang"));
    }

    private static LostTalesQuestDefinition quest(String id,
            Map<String, String> journal, String... stageIds) {
        LostTalesQuestStageDefinition[] stages =
                new LostTalesQuestStageDefinition[stageIds.length];
        for (int index = 0; index < stageIds.length; index++) {
            stages[index] = new LostTalesQuestStageDefinition(stageIds[index],
                    Collections.<LostTalesQuestObjectiveDefinition>emptyList());
        }
        return new LostTalesQuestDefinition(id, "Q", "", false,
                LostTalesQuestDefinition.START_MODE_LOCKED,
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(), journal,
                Arrays.asList(stages));
    }

    private static String category(String id) {
        return LostTalesQuestCategory.of(quest(id,
                Collections.<String, String>emptyMap()));
    }

    @Test
    public void aQuestIsFiledByTheFirstPartOfItsPath() {
        assertEquals(LostTalesQuestCategory.TUTORIALS, category("losttales:tutorial/meet_nia"));
        assertEquals(LostTalesQuestCategory.PATHS, category("losttales:path/survivalist/night_watch"));
        assertEquals(LostTalesQuestCategory.MISSIVES, category("losttales:missive/generated/b/1_0"));
        assertEquals(LostTalesQuestCategory.FACTIONS, category("losttales:faction/bree/watch"));
        assertEquals(LostTalesQuestCategory.REGIONAL, category("losttales:regional/bree"));
        assertEquals(LostTalesQuestCategory.MAIN_STORY, category("losttales:story/prologue"));
        assertEquals("a word part way along is not the path's first",
                LostTalesQuestCategory.MISC, category("losttales:server/my_tutorial"));
        assertEquals(LostTalesQuestCategory.MISC, category("losttales:main/prologue"));
    }

    @Test
    public void everyCategoryHasItsNameInTheLangFile() {
        String[] categories = {LostTalesQuestCategory.TUTORIALS,
                LostTalesQuestCategory.PATHS, LostTalesQuestCategory.MISSIVES,
                LostTalesQuestCategory.FACTIONS, LostTalesQuestCategory.REGIONAL,
                LostTalesQuestCategory.MAIN_STORY, LostTalesQuestCategory.WORLD,
                LostTalesQuestCategory.MISC};
        for (String category : categories) {
            assertTrue(category, StatCollector.canTranslate(
                    LostTalesQuestCategory.key(category)));
        }
        assertEquals("Main Story", StatCollector.translateToLocal(
                LostTalesQuestCategory.key("Main Story")));
        assertEquals(LostTalesQuestCategory.key(LostTalesQuestCategory.MISC),
                LostTalesQuestCategory.key(""));
    }

    @Test
    public void aJournalLineIsTheLatestStageThatHasOne() {
        Map<String, String> journal = new LinkedHashMap<String, String>();
        journal.put("start", "I set out.");
        journal.put("end", "I came back.");
        LostTalesQuestDefinition quest = quest("losttales:test/q", journal,
                "start", "middle", "end");
        assertEquals("I set out.", quest.journalLine(0));
        assertEquals("a stage without a line keeps the one before",
                "I set out.", quest.journalLine(1));
        assertEquals("I came back.", quest.journalLine(2));
        assertEquals("I came back.", quest.journalLine(9));
        assertEquals("", quest.journalLine(-1));
    }
}
