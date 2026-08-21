package com.ninuna.losttales.chat;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ChatNameSuggesterTest {
    private static final List<String> CANDIDATES = Arrays.asList(
            "Player812", "Grey Wanderer", "player812", "Padda", null);

    @Test
    public void plainTextCommandsAndAddressesProduceNoQuery() {
        assertNull(ChatNameSuggester.findQuery(null, 0));
        assertNull(ChatNameSuggester.findQuery("hello", 5));
        assertNull(ChatNameSuggester.findQuery("/msg @Pl", 8));
        assertNull(ChatNameSuggester.findQuery("mail@Bob", 8));
    }

    @Test
    public void bareAtOpensAnEmptyQueryListingEveryone() {
        ChatNameSuggester.Query bare = ChatNameSuggester.findQuery("@", 1);
        assertNotNull(bare);
        assertEquals(0, bare.atIndex);
        assertEquals("", bare.prefix);
        assertEquals(3, ChatNameSuggester.matches(
                bare.prefix, CANDIDATES, 8).size());
    }

    @Test
    public void queryTracksTheAtSignAndTypedPrefix() {
        ChatNameSuggester.Query query =
                ChatNameSuggester.findQuery("hi @Pl", 6);
        assertNotNull(query);
        assertEquals(3, query.atIndex);
        assertEquals("pl", query.prefix);

        ChatNameSuggester.Query spaced =
                ChatNameSuggester.findQuery("@Grey Wan", 9);
        assertNotNull(spaced);
        assertEquals("grey wan", spaced.prefix);
    }

    @Test
    public void overlongPrefixesStopSuggesting() {
        StringBuilder text = new StringBuilder("@");
        for (int index = 0; index < 33; index++) {
            text.append('a');
        }
        assertNull(ChatNameSuggester.findQuery(
                text.toString(), text.length()));
    }

    @Test
    public void matchesFilterCaseInsensitivelyAndDeduplicate() {
        List<String> p = ChatNameSuggester.matches("p", CANDIDATES, 8);
        assertEquals(2, p.size());
        assertEquals("Player812", p.get(0));
        assertEquals("Padda", p.get(1));

        List<String> spaced = ChatNameSuggester.matches(
                "grey w", CANDIDATES, 8);
        assertEquals(1, spaced.size());
        assertEquals("Grey Wanderer", spaced.get(0));

        assertEquals(1, ChatNameSuggester.matches(
                "", CANDIDATES, 1).size());
        assertTrue(ChatNameSuggester.matches(
                "zz", CANDIDATES, 8).isEmpty());
        assertTrue(ChatNameSuggester.matches("p", null, 8).isEmpty());
        assertTrue(ChatNameSuggester.matches(null, CANDIDATES, 8)
                .isEmpty());
    }
}
