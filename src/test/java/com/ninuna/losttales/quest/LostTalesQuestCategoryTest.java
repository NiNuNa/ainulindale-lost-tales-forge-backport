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
        return new LostTalesQuestDefinition(id, "Q", "", false, false,
                LostTalesQuestDefinition.START_MODE_LOCKED,
                null, null, null, null, journal, null, null,
                Arrays.asList(stages), false, null, null);
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

    /** Each stage reached adds its paragraph, in the order reached (QUEST-30). */
    @Test
    public void theJournalTellsEachStageReached() {
        Map<String, String> journal = new LinkedHashMap<String, String>();
        journal.put("start", "I set out.");
        journal.put("end", "I came back.");
        LostTalesQuestDefinition quest = quest("losttales:test/q", journal,
                "start", "middle", "end");
        assertEquals(Arrays.asList("I set out."), LostTalesQuestWords
                .journalParagraphs(quest, Arrays.asList("start"), false));
        assertEquals("a stage without a line adds nothing",
                Arrays.asList("I set out."), LostTalesQuestWords
                        .journalParagraphs(quest, Arrays.asList("start", "middle"), false));
        assertEquals(Arrays.asList("I set out.", "I came back."),
                LostTalesQuestWords.journalParagraphs(quest,
                        Arrays.asList("start", "middle", "end"), false));
        assertEquals("a path that branched tells only the stages it took",
                Arrays.asList("I came back."), LostTalesQuestWords
                        .journalParagraphs(quest, Arrays.asList("middle", "end"), false));
        assertEquals("no path is the first stage",
                Arrays.asList("I set out."), LostTalesQuestWords
                        .journalParagraphs(quest, Collections.<String>emptyList(), false));
        assertEquals("no path of a finished quest is every stage",
                Arrays.asList("I set out.", "I came back."), LostTalesQuestWords
                        .journalParagraphs(quest, Collections.<String>emptyList(), true));
    }
}
