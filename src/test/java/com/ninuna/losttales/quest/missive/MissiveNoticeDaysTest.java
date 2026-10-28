package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.quest.QuestStartGate;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A notice's days follow one rule wherever they are asked: on the board,
 * on its page, as its letter is accepted and in the world's record of
 * missives taken. A letter with no posting time never runs out. Each
 * refusal of a letter is said once.
 */
public final class MissiveNoticeDaysTest {
    private static final long DAYS = 7L * 24000L;

    private static LostTalesMissiveData postedAt(long worldTime) {
        return LostTalesMissiveData.builder(
                "losttales:missive/days", "kill")
                .titleId("clear_the_paths")
                .generationWorldTime(worldTime)
                .objective(new LostTalesMissiveObjectiveData("kill_wolves",
                        "kill", false, Collections.singletonMap("count", "3")))
                .build();
    }

    @Test
    public void aNoticeRunsOutOnceItsDaysArePast() {
        assertEquals(DAYS - 100L, MissiveNotice.ticksLeft(1000L, DAYS, 1100L));
        assertFalse(MissiveNotice.isPast(1000L, DAYS, 1000L + DAYS - 1L));
        assertTrue(MissiveNotice.isPast(1000L, DAYS, 1000L + DAYS));
        assertEquals(0L, MissiveNotice.ticksLeft(1000L, DAYS, 1000L + DAYS * 3L));
    }

    @Test
    public void aNoticeWithNoPostingTimeOrExpiryOffStaysUp() {
        assertEquals(MissiveNotice.STAYS_UP, MissiveNotice.ticksLeft(0L, DAYS, DAYS * 9L));
        assertFalse("posted at no time, it stands for good",
                MissiveNotice.isPast(0L, DAYS, DAYS * 9L));
        assertEquals(MissiveNotice.STAYS_UP, MissiveNotice.ticksLeft(1000L, 0L, DAYS * 9L));
        assertEquals("posted after now, as after a clock set back",
                MissiveNotice.STAYS_UP, MissiveNotice.ticksLeft(5000L, DAYS, 4000L));
    }

    /** The board's page reads a letter's days by the same rule. */
    @Test
    public void theBoardsPageReadsTheSameDays() {
        assertEquals(MissiveNotice.ticksLeft(1000L, DAYS, 1100L),
                MissiveBoardService.ticksLeft(postedAt(1000L), DAYS, 1100L));
        assertEquals(MissiveNotice.STAYS_UP,
                MissiveBoardService.ticksLeft(postedAt(0L), DAYS, DAYS * 9L));
        assertEquals(MissiveNotice.STAYS_UP,
                MissiveBoardService.ticksLeft(null, DAYS, 1100L));
    }

    /**
     * A refusal the start gate said in its own line is not said again;
     * one it kept quiet about, a letter that proves nothing or a missive
     * the world could not record, is said in the chat.
     */
    @Test
    public void eachRefusalOfALetterIsSaidOnce() {
        for (QuestStartGate.StartResult result : QuestStartGate.StartResult.values()) {
            MissiveBoardStateReason reason = MissiveAcceptance.reasonFor(result);
            boolean gateQuiet = result == QuestStartGate.StartResult.UNPROVEN
                    || result == QuestStartGate.StartResult.NOT_RECORDED;
            if (result == QuestStartGate.StartResult.STARTED) {
                assertEquals(MissiveBoardStateReason.ACCEPTED, reason);
                continue;
            }
            assertTrue(result.name(), reason.isRefusal());
            assertEquals(result.name(), gateQuiet, reason.isSaidInChat());
        }
        assertEquals(MissiveBoardStateReason.NOT_NOW,
                MissiveAcceptance.reasonFor(QuestStartGate.StartResult.NOT_RECORDED));
    }
}
