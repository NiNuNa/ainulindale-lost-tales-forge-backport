package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.StringTranslate;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The words every quest screen shares: time in the short in-game form,
 * rewards by name, and the one rule for taking a quest again.
 */
public final class LostTalesQuestTextTest {

    @BeforeClass
    public static void loadTheModsWords() {
        StringTranslate.inject(LostTalesQuestTextTest.class
                .getResourceAsStream("/assets/losttales/lang/en_US.lang"));
    }

    @Test
    public void timeIsTwoPartsAtMostAndLeavesOutANothing() {
        assertEquals("1d 3h", LostTalesQuestTimeText.shortForm(27000L));
        assertEquals("1d", LostTalesQuestTimeText.shortForm(24000L));
        assertEquals("1d", LostTalesQuestTimeText.shortForm(24999L));
        assertEquals("3h 30m", LostTalesQuestTimeText.shortForm(3500L));
        assertEquals("3h", LostTalesQuestTimeText.shortForm(3000L));
        assertEquals("30m", LostTalesQuestTimeText.shortForm(500L));
        // Any time at all is a minute at least; none is none.
        assertEquals("1m", LostTalesQuestTimeText.shortForm(1L));
        assertEquals("0m", LostTalesQuestTimeText.shortForm(0L));
        assertEquals("0m", LostTalesQuestTimeText.shortForm(-40L));
    }

    @Test
    public void rewardsAreNamedInTheirMapsOrder() {
        Map<String, String> rewards = new LinkedHashMap<String, String>();
        rewards.put("experience", "40");
        rewards.put("levels", "1");
        rewards.put("items", "losttales_test:no_such_thing*2");
        rewards.put("silver_coins", "3");
        assertEquals(Arrays.asList("40 experience", "1 experience level",
                "2x No such thing", "Silver coins: 3"),
                LostTalesQuestRewardText.phrases(rewards));
        assertEquals("40 experience, 1 experience level, 2x No such thing,"
                + " Silver coins: 3", LostTalesQuestRewardText.summary(rewards));
        assertEquals("", LostTalesQuestRewardText.summary(
                Collections.<String, String>emptyMap()));
    }

    @Test
    public void aQuestIsTakenAgainByTheOneRule() {
        LostTalesQuestDefinition repeatable = quest(true, false);
        LostTalesQuestDefinition restartable = quest(false, true);
        LostTalesQuestHistoryEntry finished = ended(
                LostTalesQuestHistoryEntry.Outcome.COMPLETED);
        LostTalesQuestHistoryEntry failed = ended(
                LostTalesQuestHistoryEntry.Outcome.FAILED);
        LostTalesQuestHistoryEntry abandoned = ended(
                LostTalesQuestHistoryEntry.Outcome.ABANDONED);

        assertTrue(restartable.mayTakeAgain(null));
        assertTrue(repeatable.mayTakeAgain(finished));
        assertFalse(restartable.mayTakeAgain(finished));
        assertTrue(restartable.mayTakeAgain(failed));
        assertTrue(restartable.mayTakeAgain(abandoned));
        assertFalse(repeatable.mayTakeAgain(failed));
        assertFalse(repeatable.mayTakeAgain(abandoned));
    }

    private static LostTalesQuestDefinition quest(boolean repeatable,
            boolean restartable) {
        return new LostTalesQuestDefinition("quest", "Quest", "", repeatable,
                restartable, LostTalesQuestDefinition.START_MODE_ANY, null,
                null, null, null, null,
                Collections.<LostTalesQuestStageDefinition>emptyList());
    }

    private static LostTalesQuestHistoryEntry ended(
            LostTalesQuestHistoryEntry.Outcome outcome) {
        return new LostTalesQuestHistoryEntry("quest", outcome, "", 100L);
    }
}
