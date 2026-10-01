package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Which lines a choice lets through: the lines that chime under
 * Notifications, and the lines that reach the closed feed under Show in
 * Feed, one set of three words for both. A line is addressed to the
 * player when it mentions them or replies to them.
 */
public final class ChatLineChoiceTest {
    @Test
    public void everythingLetsEveryLineThrough() {
        assertTrue(ChatLineChoice.EVERYTHING.lets(true));
        assertTrue(ChatLineChoice.EVERYTHING.lets(false));
    }

    @Test
    public void onlyMentionsLetsAddressedLinesAloneThrough() {
        assertTrue(ChatLineChoice.ONLY_MENTIONS.lets(true));
        assertFalse(ChatLineChoice.ONLY_MENTIONS.lets(false));
    }

    @Test
    public void nothingLetsNoLineThrough() {
        assertFalse(ChatLineChoice.NOTHING.lets(true));
        assertFalse(ChatLineChoice.NOTHING.lets(false));
    }

    /** The words the layout file keeps each choice under read back as it. */
    @Test
    public void eachChoiceReadsBackFromItsWord() {
        for (ChatLineChoice choice : ChatLineChoice.values()) {
            assertEquals(choice, ChatLineChoice.fromId(choice.id()));
            assertEquals(choice,
                    ChatLineChoice.fromId(" " + choice.id().toUpperCase(
                            java.util.Locale.ROOT) + " "));
        }
        assertEquals("everything", ChatLineChoice.EVERYTHING.id());
        assertEquals("mentions", ChatLineChoice.ONLY_MENTIONS.id());
        assertEquals("nothing", ChatLineChoice.NOTHING.id());
        assertNull(ChatLineChoice.fromId("all"));
        assertNull(ChatLineChoice.fromId(""));
        assertNull(ChatLineChoice.fromId(null));
    }

    /** Every choice's words are in the shipped language file. */
    @Test
    public void everyChoiceHasItsWords() throws java.io.IOException {
        java.util.Set<String> keys = new java.util.HashSet<String>();
        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(ChatLineChoiceTest.class
                        .getResourceAsStream("/assets/losttales/lang/en_US.lang"),
                        "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                int equals = line.indexOf('=');
                if (equals > 0) {
                    keys.add(line.substring(0, equals));
                }
            }
        } finally {
            reader.close();
        }
        for (ChatLineChoice choice : ChatLineChoice.values()) {
            assertTrue(choice.labelKey(), keys.contains(choice.labelKey()));
        }
        assertTrue(keys.contains("gui.losttales.chat.tab.notify"));
        assertTrue(keys.contains("gui.losttales.chat.tab.feed"));
    }

    /**
     * The feed's filter lets a conversation's lines through as its Show
     * in Feed says: every line of one, only an addressed line of another.
     */
    @Test
    public void theFeedFilterFollowsTheChoice() {
        ChatTab bilbo = ChatTab.npc("Bilbo");
        ChatTab frodo = ChatTab.npc("Frodo");
        ChatTab sam = ChatTab.npc("Sam");
        ChatLineFilter feed = ChatLineFilter.of(
                java.util.Collections.singletonList(bilbo),
                java.util.Collections.singletonList(frodo));
        assertTrue(feed.accepts(bilbo, false));
        assertTrue(feed.accepts(bilbo, true));
        assertFalse(feed.accepts(frodo, false));
        assertTrue(feed.accepts(frodo, true));
        assertFalse(feed.accepts(sam, true));
        assertFalse(feed.accepts(frodo));
        assertFalse(feed.equals(ChatLineFilter.of(
                java.util.Collections.singletonList(bilbo),
                java.util.Collections.<ChatTab>emptySet())));
    }
}
