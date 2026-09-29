package com.ninuna.losttales.character.validation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * One rule for when two names are the same name: letters and digits alone,
 * lower case, accents off. A few names are the chat's own voices.
 */
public final class CharacterNamesTest {

    @Test
    public void caseAccentsAndMarksMakeNoNewName() {
        assertTrue(CharacterNames.same("Aldric", "aldric"));
        assertTrue(CharacterNames.same("Al-dric", "Aldric"));
        assertTrue(CharacterNames.same("\u00c9omer", "Eomer"));
        assertTrue(CharacterNames.same(" Fe\u0301anor ", "FEANOR"));
        assertFalse(CharacterNames.same("Aldric", "Aldrec"));
        assertEquals("feanor", CharacterNames.key("F\u00e9anor"));
    }

    @Test
    public void namesWithNothingToCompareAreNeverTheSame() {
        assertFalse(CharacterNames.same("", ""));
        assertFalse(CharacterNames.same("'-'", "'-'"));
        assertFalse(CharacterNames.same(null, null));
    }

    @Test
    public void theChatsVoicesAreReservedHoweverTheyAreWritten() {
        assertTrue(CharacterNames.isVoice("Server"));
        assertTrue(CharacterNames.isVoice("s e r v e r"));
        assertTrue(CharacterNames.isVoice("NARRATOR"));
        assertTrue(CharacterNames.isVoice("Client"));
        assertTrue(CharacterNames.isVoice("Discord"));
        assertFalse(CharacterNames.isVoice("Servant"));
    }
}
