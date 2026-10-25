package com.ninuna.losttales.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * A quote is cut without dots (CHAT-86): the server keeps a message's
 * first 96 letters and adds no mark, and a game fades the quote out at
 * the end of its room.
 */
public final class ChatReplyReferenceExcerptTest {

    @Test
    public void aLongMessageKeepsItsFirstLettersAndNoMark() {
        String words = repeat("the road goes ever on ", 10);
        String excerpt = ChatReplyReference.excerptOf(words);
        assertEquals(words.substring(0, ChatReplyReference.MAX_EXCERPT_CHARACTERS).trim(),
                excerpt);
        assertFalse(excerpt.endsWith("..."));
        assertEquals("cut once, a quote stays as it is", excerpt,
                ChatReplyReference.excerptOf(excerpt));
    }

    @Test
    public void aShortMessageIsQuotedWhole() {
        assertEquals("over here", ChatReplyReference.excerptOf("  over here "));
        assertEquals("one line of two", ChatReplyReference.excerptOf("one line\nof two"));
    }

    @Test
    public void aCutNeverSplitsACharacter() {
        String words = repeat("x", ChatReplyReference.MAX_EXCERPT_CHARACTERS - 1)
                + "😀 and more";
        String excerpt = ChatReplyReference.excerptOf(words);
        assertEquals(ChatReplyReference.MAX_EXCERPT_CHARACTERS - 1, excerpt.length());
    }

    @Test
    public void theQuoteCarriesItsExcerptWithinItsBound() {
        String words = repeat("é", 200);
        ChatReplyReference quote = ChatReplyReference.of(7L, "Aldric", words);
        assertEquals(ChatReplyReference.MAX_EXCERPT_CHARACTERS, quote.getExcerpt().length());
        assertEquals(ChatReplyReference.MAX_EXCERPT_CHARACTERS * 3,
                ChatReplyReference.MAX_EXCERPT_BYTES);
    }

    private static String repeat(String text, int times) {
        StringBuilder built = new StringBuilder();
        for (int index = 0; index < times; index++) {
            built.append(text);
        }
        return built.toString();
    }
}
