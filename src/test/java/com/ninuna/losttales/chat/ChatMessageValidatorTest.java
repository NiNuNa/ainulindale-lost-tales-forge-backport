package com.ninuna.losttales.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The trust boundary every message, whisper and Discord line crosses. */
public final class ChatMessageValidatorTest {

    @Test
    public void plainTextWithinTheLimitIsValid() {
        assertTrue(ChatMessageValidator.isValid("Hail and well met."));
        assertTrue(ChatMessageValidator.isValid("a"));
        assertTrue(ChatMessageValidator.isValid(repeat('x',
                ChatMessageValidator.MAX_CHARACTERS)));
    }

    @Test
    public void emptyUntrimmedAndOverlongTextIsRefused() {
        assertFalse(ChatMessageValidator.isValid(null));
        assertFalse(ChatMessageValidator.isValid(""));
        assertFalse(ChatMessageValidator.isValid(" leading"));
        assertFalse(ChatMessageValidator.isValid("trailing "));
        assertFalse(ChatMessageValidator.isValid(repeat('x',
                ChatMessageValidator.MAX_CHARACTERS + 1)));
    }

    /**
     * Up to eight paragraphs, in the one form a message keeps them: each
     * trimmed, none empty; anything else is refused, and the normalizer
     * puts any text in that form.
     */
    @Test
    public void paragraphsAreKeptInOneForm() {
        assertTrue(ChatMessageValidator.isValid("two\nparagraphs"));
        assertFalse(ChatMessageValidator.isValid("two\n\nblank"));
        assertFalse(ChatMessageValidator.isValid("space \nbefore"));
        assertFalse(ChatMessageValidator.isValid("space\n after"));
        assertFalse(ChatMessageValidator.isValid("return\r\nbreak"));
        StringBuilder eight = new StringBuilder("p");
        for (int index = 1; index < ChatMessageValidator.MAX_PARAGRAPHS; index++) {
            eight.append("\np");
        }
        assertTrue(ChatMessageValidator.isValid(eight.toString()));
        assertFalse(ChatMessageValidator.isValid(eight + "\np"));

        assertEquals("one\ntwo", ChatMessageValidator.paragraphs(
                "  one  \r\n\n\r  two \n  "));
        assertEquals("a\nb\nc\nd\ne\nf\ng\nh i j",
                ChatMessageValidator.paragraphs("a\nb\nc\nd\ne\nf\ng\nh\ni\nj"));
        assertEquals("", ChatMessageValidator.paragraphs(" \n \n"));
        assertEquals("one two", ChatMessageValidator.oneLine("one\ntwo"));
        assertEquals("one\\ntwo", ChatMessageValidator.logged("one\ntwo"));
        assertEquals("an action is one paragraph", "Aldric bows. He waits.",
                ChatAction.sentence("Aldric bows.\nHe waits"));
    }

    @Test
    public void sectionSignsAndControlCharactersAreRefused() {
        assertFalse(ChatMessageValidator.isValid("gold §6text"));
        assertFalse(ChatMessageValidator.isValid("bell"));
        // The ampersand form is how players write a colour; it passes.
        assertTrue(ChatMessageValidator.isValid("gold &6text"));
    }

    @Test
    public void shareTokensCountAsOneVisibleCharacter() {
        String token = "[i:Sword of Westernesse]";
        String message = repeat('x', ChatMessageValidator.MAX_CHARACTERS - 1)
                + token;
        assertEquals(ChatMessageValidator.MAX_CHARACTERS,
                ChatMessageValidator.visibleLength(message));
        assertTrue(ChatMessageValidator.isValid(message));
        assertFalse(ChatMessageValidator.isValid(message + "x"));
        assertEquals(0, ChatMessageValidator.visibleLength(null));
    }

    @Test
    public void theRawBoundHoldsWhateverTheTokensCost() {
        assertTrue(ChatMessageValidator.MAX_RAW_CHARACTERS
                > ChatMessageValidator.MAX_CHARACTERS);
        assertEquals(ChatMessageValidator.MAX_RAW_CHARACTERS * 3,
                ChatMessageValidator.MAX_UTF8_BYTES);
        assertFalse(ChatMessageValidator.isValid(repeat('x',
                ChatMessageValidator.MAX_RAW_CHARACTERS + 1)));
    }

    private static String repeat(char character, int count) {
        StringBuilder text = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            text.append(character);
        }
        return text.toString();
    }
}
