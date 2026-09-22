package com.ninuna.losttales.character.validation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Creation and a later edit hold a character's description and age to
 * one set of bounds, and the description is judged as it is stored.
 */
public final class CharacterProfileValidationTest {

    @Test
    public void aProfileInsideTheBoundsIsAccepted() {
        assertTrue(CharacterValidator.validateProfile("",
                CharacterValidator.MIN_AGE).isValid());
        assertTrue(CharacterValidator.validateProfile(
                repeat(CharacterValidator.MAX_DESCRIPTION_LENGTH),
                CharacterValidator.MAX_AGE).isValid());
    }

    @Test
    public void anAgeOutsideTheBoundsIsRefused() {
        assertEquals(CharacterErrorId.INVALID_AGE, CharacterValidator
                .validateProfile("", CharacterValidator.MIN_AGE - 1).getErrorId());
        assertEquals(CharacterErrorId.INVALID_AGE, CharacterValidator
                .validateProfile("", CharacterValidator.MAX_AGE + 1).getErrorId());
    }

    @Test
    public void aDescriptionTooLongOrCarryingCodesIsRefused() {
        assertEquals(CharacterErrorId.INVALID_DESCRIPTION, CharacterValidator
                .validateProfile(repeat(CharacterValidator.MAX_DESCRIPTION_LENGTH + 1),
                        20).getErrorId());
        assertEquals(CharacterErrorId.INVALID_DESCRIPTION, CharacterValidator
                .validateProfile("§cRed", 20).getErrorId());
        assertEquals(CharacterErrorId.INVALID_DESCRIPTION, CharacterValidator
                .validateProfile("abell", 20).getErrorId());
    }

    @Test
    public void spaceThatFoldsAwayDoesNotCountAgainstTheLimit() {
        String padded = "   " + repeat(CharacterValidator.MAX_DESCRIPTION_LENGTH)
                + "\t\t";
        assertTrue(CharacterValidator.validateProfile(
                CharacterValidator.normalizeDescription(padded), 20).isValid());
    }

    private static String repeat(int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append('a');
        }
        return text.toString();
    }
}
