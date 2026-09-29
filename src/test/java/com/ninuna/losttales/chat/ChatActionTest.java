package com.ninuna.losttales.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * An action's words become the sentence the speaker's name opens:
 * trimmed, ended with a full stop where they end on a word, and refused
 * where a message would be, an empty one first of all.
 */
public final class ChatActionTest {
    @Test
    public void wordsEndedOnAWordGetAFullStop() {
        assertEquals("draws his sword.", ChatAction.sentence("draws his sword"));
        assertEquals("draws his sword.", ChatAction.sentence("  draws his sword  "));
        assertEquals("counts to 3.", ChatAction.sentence("counts to 3"));
    }

    @Test
    public void wordsAlreadyEndedStayAsTheyAre() {
        assertEquals("draws his sword.", ChatAction.sentence("draws his sword."));
        assertEquals("shouts!", ChatAction.sentence("shouts!"));
        assertEquals("wonders?", ChatAction.sentence("wonders?"));
        assertEquals("waves :wave:", ChatAction.sentence("waves :wave:"));
        assertEquals("says \"hello\"", ChatAction.sentence("says \"hello\""));
        // Markup that closes the words is left closed.
        assertEquals("*trips*", ChatAction.sentence("*trips*"));
    }

    @Test
    public void anEmptyActionIsRefused() {
        assertEquals("", ChatAction.sentence(""));
        assertEquals("", ChatAction.sentence("   "));
        assertEquals("", ChatAction.sentence(null));
        assertFalse(ChatAction.isValid(""));
        assertFalse(ChatAction.isValid("   "));
        assertFalse(ChatAction.isValid(null));
        assertTrue(ChatAction.isValid("nods"));
    }

    /** The words obey a message's rules; a full stop is added only where it still fits. */
    @Test
    public void anActionKeepsAMessagesLimits() {
        StringBuilder full = new StringBuilder();
        while (full.length() < ChatMessageValidator.MAX_CHARACTERS) {
            full.append('a');
        }
        String words = full.toString();
        assertTrue(ChatAction.isValid(words));
        assertEquals(words, ChatAction.sentence(words));
        assertFalse(ChatAction.isValid(words + "a"));
        assertFalse(ChatAction.isValid("draws §chis sword"));
    }
}
