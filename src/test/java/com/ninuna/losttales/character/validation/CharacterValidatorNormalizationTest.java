package com.ninuna.losttales.character.validation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Names, facts and glances are stored under one whitespace rule; an About
 * text follows it paragraph by paragraph and keeps one empty line at most
 * between them.
 */
public final class CharacterValidatorNormalizationTest {

    @Test
    public void whitespaceIsFoldedAndTrimmed() {
        assertEquals("Aldric of Bree",
                CharacterValidator.normalizeName("  Aldric \t of\n\nBree  "));
        assertEquals("grey green",
                CharacterValidator.normalizeLine("  grey\tgreen  "));
        assertEquals("", CharacterValidator.normalizeName(null));
        assertEquals("", CharacterValidator.normalizeSection("   "));
    }

    @Test
    public void anAboutTextKeepsItsParagraphs() {
        assertEquals("A long   tale is one line.\nTold twice.".replace("   ", " "),
                CharacterValidator.normalizeSection(
                        "  A long   tale is one line.\r\nTold\ttwice.  "));
        assertEquals("One.\n\nTwo.", CharacterValidator.normalizeSection(
                "\n\nOne.\n \n\n\nTwo.\n\n"));
        assertEquals("", CharacterValidator.normalizeSection(null));
    }

    @Test
    public void textIsComposedToNfcAndTheKeyIsLowercased() {
        // e + combining acute becomes the precomposed letter.
        String decomposed = "Féanor";
        assertEquals("Féanor", CharacterValidator.normalizeName(decomposed));
        assertEquals("Féanor",
                CharacterValidator.normalizeSection(decomposed));
        assertEquals("féanor", CharacterValidator.normalizeNameKey(
                " FÉANOR "));
    }

    @Test
    public void namesAndFactsFollowOneRule() {
        String[] samples = {"  a  b  ", "x", "§cred", "tab\tsep", "é"};
        for (String sample : samples) {
            assertEquals(CharacterValidator.normalizeName(sample),
                    CharacterValidator.normalizeLine(sample));
        }
    }
}
