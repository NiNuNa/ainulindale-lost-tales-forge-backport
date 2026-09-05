package com.ninuna.losttales.character.validation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Names and biographies are stored under the same whitespace rule. */
public final class CharacterValidatorNormalizationTest {

    @Test
    public void whitespaceIsFoldedAndTrimmed() {
        assertEquals("Aldric of Bree",
                CharacterValidator.normalizeName("  Aldric \t of\n\nBree  "));
        assertEquals("A long tale, told twice.",
                CharacterValidator.normalizeDescription(
                        "A long   tale,\r\ntold\ttwice.  "));
        assertEquals("", CharacterValidator.normalizeName(null));
        assertEquals("", CharacterValidator.normalizeDescription("   "));
    }

    @Test
    public void textIsComposedToNfcAndTheKeyIsLowercased() {
        // e + combining acute becomes the precomposed letter.
        String decomposed = "Féanor";
        assertEquals("Féanor", CharacterValidator.normalizeName(decomposed));
        assertEquals("Féanor",
                CharacterValidator.normalizeDescription(decomposed));
        assertEquals("féanor", CharacterValidator.normalizeNameKey(
                " FÉANOR "));
    }

    @Test
    public void namesAndDescriptionsFollowOneRule() {
        String[] samples = {"  a  b  ", "x", "§cred", "tab\tsep", "é"};
        for (String sample : samples) {
            assertEquals(CharacterValidator.normalizeName(sample),
                    CharacterValidator.normalizeDescription(sample));
        }
    }
}
