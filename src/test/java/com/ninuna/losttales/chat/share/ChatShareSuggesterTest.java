package com.ninuna.losttales.chat.share;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ChatShareSuggesterTest {
    private static final List<String> LABELS = Arrays.asList(
            "Iron Sword", "Bow", "Golden Apple", "§6Iron Helmet", "",
            "Longbow");

    @Test
    public void plainTextCommandsAndClosedTokensProduceNoQuery() {
        assertNull(ChatShareSuggester.findQuery(null, 0));
        assertNull(ChatShareSuggester.findQuery("hello", 5));
        assertNull(ChatShareSuggester.findQuery("/give [i:Bo", 11));
        assertNull(ChatShareSuggester.findQuery("[i:Bow] and", 11));
        assertNull(ChatShareSuggester.findQuery("[i", 2));
    }

    @Test
    public void queryTracksTheNearestOpenerAndItsKind() {
        ChatShareSuggester.Query bare = ChatShareSuggester.findQuery("[m:", 3);
        assertNotNull(bare);
        assertEquals(ChatShareKind.MARKER, bare.kind);
        assertEquals(0, bare.openIndex);
        assertEquals("", bare.prefix);

        ChatShareSuggester.Query typed =
                ChatShareSuggester.findQuery("see [i:Iron Sw", 14);
        assertNotNull(typed);
        assertEquals(ChatShareKind.ITEM, typed.kind);
        assertEquals(4, typed.openIndex);
        assertEquals("iron sw", typed.prefix);

        ChatShareSuggester.Query nearest =
                ChatShareSuggester.findQuery("[i:a] [m:Bre", 12);
        assertNotNull(nearest);
        assertEquals(ChatShareKind.MARKER, nearest.kind);
        assertEquals(6, nearest.openIndex);
    }

    @Test
    public void matchesPreferPrefixThenSubstringInCandidateOrder() {
        assertEquals(Arrays.asList(0, 3),
                ChatShareSuggester.matches("iron", LABELS, 8));
        assertEquals(Arrays.asList(2),
                ChatShareSuggester.matches("apple", LABELS, 8));
        assertEquals(Arrays.asList(1, 5),
                ChatShareSuggester.matches("bow", LABELS, 8));
        assertEquals(Arrays.asList(0, 2),
                ChatShareSuggester.matches("d", LABELS, 8));
        assertEquals(5, ChatShareSuggester.matches("", LABELS, 8).size());
        assertEquals(1, ChatShareSuggester.matches("", LABELS, 1).size());
        assertTrue(ChatShareSuggester.matches("zz", LABELS, 8).isEmpty());
        assertTrue(ChatShareSuggester.matches("a", null, 8).isEmpty());
    }
}
