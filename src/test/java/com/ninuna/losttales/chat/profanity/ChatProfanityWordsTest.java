package com.ninuna.losttales.chat.profanity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * A word list is read leniently — blank lines and comments skipped, a
 * bad line warned about and skipped — and bounded; the bundled list is
 * well formed and collides with nothing the chat reads as a token.
 */
public final class ChatProfanityWordsTest {

    @Test
    public void entriesAreWordEqualsReplacement() {
        List<String> warnings = new ArrayList<String>();
        ChatProfanityWords words = ChatProfanityWords.parse(new String[] {
                "# a comment", "", "  Fuck = Flip  ", "shit=poop"},
                ChatProfanityWords.MAX_WORDS, warnings);
        assertTrue(warnings.isEmpty());
        assertEquals(2, words.size());
        assertEquals(Arrays.asList("fuck=flip", "shit=poop"), words.entries());
    }

    @Test
    public void aBadLineIsWarnedAboutAndSkipped() {
        List<String> warnings = new ArrayList<String>();
        ChatProfanityWords words = ChatProfanityWords.parse(new String[] {
                "fuck", "=flip", "two words=flip", "fuck=two words",
                "sh1t=poop", "fuck=flip", "FUCK=fudge",
                "abcdefghijklmnopqrstuvwxyz=long"},
                ChatProfanityWords.MAX_WORDS, warnings);
        assertEquals(1, words.size());
        assertEquals(7, warnings.size());
        assertTrue(warnings.get(0).contains("word=replacement"));
        assertTrue(warnings.get(5).contains("already listed"));
        assertTrue(warnings.get(6).contains("word=replacement"));
    }

    @Test
    public void theListIsBounded() {
        List<String> warnings = new ArrayList<String>();
        ChatProfanityWords words = ChatProfanityWords.parse(
                new String[] {"a=b", "c=d", "e=f"}, 2, warnings);
        assertEquals(2, words.size());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("past the 2 words"));
    }

    @Test
    public void aLookupNeedsEveryLetterTheListedWordHas() {
        ChatProfanityWords words = ChatProfanityWords.parse(
                new String[] {"ass=bum"}, ChatProfanityWords.MAX_WORDS, null);
        assertNotNull(words.lookup("as", ChatProfanityWords.runsOf("ass")));
        assertNotNull(words.lookup("as", ChatProfanityWords.runsOf("asssss")));
        assertNull("as is not ass", words.lookup("as", ChatProfanityWords.runsOf("as")));
        assertNull(words.lookup("bass", ChatProfanityWords.runsOf("bass")));
    }

    @Test
    public void collapsingAndRunsDescribeTheSameWord() {
        assertEquals("fuck", ChatProfanityWords.collapse("fuuuck"));
        assertEquals("as", ChatProfanityWords.collapse("ass"));
        assertTrue(Arrays.equals(new int[] {1, 3, 1, 1},
                ChatProfanityWords.runsOf("fuuuck")));
        assertTrue(Arrays.equals(new int[] {1, 2}, ChatProfanityWords.runsOf("ass")));
        assertEquals("", ChatProfanityWords.collapse(""));
        assertEquals(0, ChatProfanityWords.runsOf("").length);
    }

    @Test
    public void plusAddsAndOverrides() {
        ChatProfanityWords first = ChatProfanityWords.parse(
                new String[] {"fuck=flip", "shit=poop"}, ChatProfanityWords.MAX_WORDS, null);
        ChatProfanityWords second = ChatProfanityWords.parse(
                new String[] {"fuck=fudge", "ass=bum"}, ChatProfanityWords.MAX_WORDS, null);
        ChatProfanityWords merged = first.plus(second);
        assertEquals(Arrays.asList("fuck=fudge", "shit=poop", "ass=bum"),
                merged.entries());
        assertSame(first, first.plus(ChatProfanityWords.NONE));
        assertSame(second, ChatProfanityWords.NONE.plus(second));
        assertTrue(ChatProfanityWords.NONE.isEmpty());
    }

    /**
     * The bundled list is the mod's own: every line parses, and no word
     * on it is an emoji's shortcode, which the filter would otherwise
     * read as a word in a speech bubble.
     */
    @Test
    public void theBundledListIsWellFormed() {
        List<String> warnings = new ArrayList<String>();
        ChatProfanityWords bundled = ChatProfanityWords.parse(
                ChatProfanityCatalog.readResource(ChatProfanityCatalog.RESOURCE),
                1024, warnings);
        assertEquals(warnings.toString(), 0, warnings.size());
        assertTrue("the list has words", bundled.size() > 50);
        for (String entry : bundled.entries()) {
            String word = entry.substring(0, entry.indexOf('='));
            String replacement = entry.substring(entry.indexOf('=') + 1);
            assertNull(word + " is an emoji's name", ChatEmoji.fromName(word));
            assertFalse(replacement + " stands in for " + word
                    + " and is itself listed",
                    ChatProfanityFilter.hasListedWord(replacement, bundled));
        }
        assertSame(ChatProfanityCatalog.bundled(), ChatProfanityCatalog.bundled());
    }

    private static void assertSame(Object expected, Object actual) {
        org.junit.Assert.assertSame(expected, actual);
    }
}
