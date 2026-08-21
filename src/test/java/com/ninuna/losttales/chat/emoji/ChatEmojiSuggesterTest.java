package com.ninuna.losttales.chat.emoji;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class ChatEmojiSuggesterTest {

    @Test
    public void plainTextAndCommandsProduceNoQuery() {
        assertNull(ChatEmojiSuggester.findQuery(null, 0));
        assertNull(ChatEmojiSuggester.findQuery("hello", 5));
        assertNull(ChatEmojiSuggester.findQuery("/msg Bob :sm", 12));
        assertNull(ChatEmojiSuggester.findQuery("", 0));
    }

    @Test
    public void bareColonNeedsAtLeastOnePrefixCharacter() {
        assertNull(ChatEmojiSuggester.findQuery(":", 1));
        assertNull(ChatEmojiSuggester.findQuery("hi :", 4));
        assertNotNull(ChatEmojiSuggester.findQuery(":s", 2));
    }

    @Test
    public void queryTracksTheOpeningColonAndTypedPrefix() {
        ChatEmojiSuggester.Query start =
                ChatEmojiSuggester.findQuery(":sm", 3);
        assertEquals(0, start.colonIndex);
        assertEquals("sm", start.prefix);

        ChatEmojiSuggester.Query middle =
                ChatEmojiSuggester.findQuery("a :sm b", 5);
        assertEquals(2, middle.colonIndex);
        assertEquals("sm", middle.prefix);

        ChatEmojiSuggester.Query uppercase =
                ChatEmojiSuggester.findQuery("hello :FL", 9);
        assertEquals("fl", uppercase.prefix);
    }

    @Test
    public void completedShortcodeDoesNotReopenAQuery() {
        assertNull(ChatEmojiSuggester.findQuery(":smile:", 7));
        // Anchoring on :smile:'s closing colon would consume it on accept.
        assertNull(ChatEmojiSuggester.findQuery(":smile:s", 8));
        // A colon-bounded non-emote is not a completed shortcode, so its
        // trailing colon may open a fresh query.
        ChatEmojiSuggester.Query afterJunk =
                ChatEmojiSuggester.findQuery(":xyz:s", 6);
        assertNotNull(afterJunk);
        assertEquals(4, afterJunk.colonIndex);
        assertEquals("s", afterJunk.prefix);
    }

    @Test
    public void itemShowcaseOpenerDoesNotOpenAnEmoteQuery() {
        // "{i:s" belongs to the item completion list, not the emote one.
        assertNull(ChatEmojiSuggester.findQuery("[i:s", 4));
        assertNull(ChatEmojiSuggester.findQuery("look [m:sm", 10));
        assertNotNull(ChatEmojiSuggester.findQuery("[i:Bow] :sm", 11));
    }

    @Test
    public void overlongPrefixesStopSuggesting() {
        StringBuilder text = new StringBuilder(":");
        for (int index = 0; index <= ChatEmoji.longestName(); index++) {
            text.append('a');
        }
        assertNull(ChatEmojiSuggester.findQuery(
                text.toString(), text.length()));
    }

    @Test
    public void matchesUseRegistryOrderAndHonorTheLimit() {
        List<ChatEmoji> fl = ChatEmojiSuggester.matches("fl", 8);
        assertEquals(1, fl.size());
        assertSame(ChatEmoji.FLUSHED, fl.get(0));

        List<ChatEmoji> sm = ChatEmojiSuggester.matches("sm", 8);
        assertEquals(2, sm.size());
        assertSame(ChatEmoji.SMILE, sm.get(0));
        assertSame(ChatEmoji.SMUG, sm.get(1));

        List<ChatEmoji> s = ChatEmojiSuggester.matches("s", 8);
        assertEquals(8, s.size());
        assertSame(ChatEmoji.SMILE, s.get(0));

        assertTrue(ChatEmojiSuggester.matches("", 8).isEmpty());
        assertTrue(ChatEmojiSuggester.matches("zz", 8).isEmpty());
        assertTrue(ChatEmojiSuggester.matches("s", 0).isEmpty());
    }
}
