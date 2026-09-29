package com.ninuna.losttales.client.window;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A page's answer over its bar (W2 a): done and refused answers stand
 * their hold and go, a working answer and a fault stand until the page
 * says something else, and a new answer takes the old one's place.
 */
public final class PageAnswerTest {
    private static final long SECOND = 1000000000L;

    @Test
    public void aDoneOrRefusedAnswerStandsItsHoldAndGoes() {
        PageAnswer answer = new PageAnswer();
        answer.say("Saved.", PageAnswer.Kind.DONE, 0L);
        assertTrue(answer.stands(2 * SECOND, 3 * SECOND));
        assertFalse(answer.stands(3 * SECOND, 3 * SECOND));
        answer.say("Refused.", PageAnswer.Kind.REFUSED, 10 * SECOND);
        assertTrue(answer.stands(12 * SECOND, 3 * SECOND));
        assertFalse(answer.stands(14 * SECOND, 3 * SECOND));
    }

    @Test
    public void aWorkingAnswerAndAFaultStandUntilReplaced() {
        PageAnswer answer = new PageAnswer();
        answer.say("Sending...", PageAnswer.Kind.WORKING, 0L);
        assertTrue(answer.stands(60 * SECOND, 3 * SECOND));
        answer.say("Saved.", PageAnswer.Kind.DONE, 61 * SECOND);
        assertSame(PageAnswer.Kind.DONE, answer.kind());
        assertEquals("Saved.", answer.words());
        assertFalse(answer.stands(65 * SECOND, 3 * SECOND));
        answer.say("Line 3 cannot be read.", PageAnswer.Kind.FAULT,
                70 * SECOND);
        assertTrue(answer.stands(700 * SECOND, 3 * SECOND));
    }

    @Test
    public void clearingOrSayingNothingLeavesNothingStanding() {
        PageAnswer answer = new PageAnswer();
        assertFalse(answer.stands(0L, 3 * SECOND));
        answer.say("Sending...", PageAnswer.Kind.WORKING, 0L);
        answer.clear();
        assertFalse(answer.stands(0L, 3 * SECOND));
        answer.say(null, PageAnswer.Kind.WORKING, 0L);
        assertFalse(answer.stands(0L, 3 * SECOND));
    }
}
