package com.ninuna.losttales.client.fellowship;

import com.ninuna.losttales.fellowship.sync.FellowshipMemberAvailability;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberPresence;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * One word for where a member stands (FEL-32): the HUD says what the
 * Fellowships page says, Away for an account offline, and the character
 * played instead or Making a Character for one online as another.
 */
public final class FellowshipMemberWordsTest {

    @Test
    public void eachPresenceHasThePagesWord() {
        assertEquals("gui.losttales.fellowship.away",
                FellowshipMemberWords.keyOf(FellowshipMemberPresence.AWAY));
        assertEquals("gui.losttales.fellowship.playing_as",
                FellowshipMemberWords.keyOf(FellowshipMemberPresence.ELSEWHERE));
        assertEquals("gui.losttales.fellowship.ghost",
                FellowshipMemberWords.keyOf(FellowshipMemberPresence.GHOST));
        assertNull(FellowshipMemberWords.keyOf(FellowshipMemberPresence.HERE));
    }

    @Test
    public void theHudsStatusReadsAsThePageDoes() {
        assertEquals(FellowshipMemberPresence.AWAY, FellowshipMemberWords.presenceOf(
                FellowshipMemberAvailability.OFFLINE, FellowshipMemberPresence.HERE));
        assertEquals(FellowshipMemberPresence.ELSEWHERE, FellowshipMemberWords.presenceOf(
                FellowshipMemberAvailability.INACTIVE_CHARACTER,
                FellowshipMemberPresence.ELSEWHERE));
        assertEquals(FellowshipMemberPresence.GHOST, FellowshipMemberWords.presenceOf(
                FellowshipMemberAvailability.INACTIVE_CHARACTER,
                FellowshipMemberPresence.GHOST));
    }

    @Test
    public void aStatusTheStateDoesNotBackSaysNothing() {
        assertNull(FellowshipMemberWords.presenceOf(
                FellowshipMemberAvailability.INACTIVE_CHARACTER,
                FellowshipMemberPresence.HERE));
        assertNull(FellowshipMemberWords.presenceOf(
                FellowshipMemberAvailability.UNAVAILABLE, FellowshipMemberPresence.HERE));
    }
}
