package com.ninuna.losttales.client.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

/** A query is words that must all appear, and a from: that names the speaker. */
public final class ChatSearchQueryTest {

    @Test
    public void wordsAreMatchedWhateverTheirCaseAndAllMustAppear() {
        ChatSearchQuery query = ChatSearchQuery.parse("  Gate  falls ");
        assertEquals(Arrays.asList("gate", "falls"), query.words);
        assertEquals("", query.from);
        assertTrue(query.matches("The GATE falls at dawn", "Aldric", "Player93"));
        assertFalse(query.matches("The gate holds", "Aldric", "Player93"));
        assertFalse(query.matches(null, "Aldric", "Player93"));
    }

    @Test
    public void fromNarrowsToASpeakerByTheStartOfEitherName() {
        ChatSearchQuery query = ChatSearchQuery.parse("from:ald hello");
        assertEquals("ald", query.from);
        assertEquals(Arrays.asList("hello"), query.words);
        assertTrue(query.matches("hello there", "Aldric", "Player93"));
        assertTrue("the account counts too",
                query.matches("hello there", "Bob", "Aldric93"));
        assertFalse(query.matches("hello there", "Bob", "Player93"));
        assertFalse("the words still have to appear",
                query.matches("good day", "Aldric", "Player93"));
        assertTrue("from: alone finds every line of theirs",
                ChatSearchQuery.parse("FROM:Aldric").matches("anything", "aldric", ""));
    }

    @Test
    public void nothingAnswersAnEmptyQuery() {
        assertSame(ChatSearchQuery.EMPTY, ChatSearchQuery.parse(""));
        assertSame(ChatSearchQuery.EMPTY, ChatSearchQuery.parse("   "));
        assertSame(ChatSearchQuery.EMPTY, ChatSearchQuery.parse(null));
        assertSame("a from: with no name is nothing",
                ChatSearchQuery.EMPTY, ChatSearchQuery.parse("from:"));
        assertFalse(ChatSearchQuery.EMPTY.matches("anything", "anyone", "anyone"));
        assertTrue(ChatSearchQuery.EMPTY.isEmpty());
    }

    @Test
    public void theLastFromWinsAndItIsNeverAWord() {
        ChatSearchQuery query = ChatSearchQuery.parse("from:a from:b word");
        assertEquals("b", query.from);
        assertEquals(Arrays.asList("word"), query.words);
    }
}
