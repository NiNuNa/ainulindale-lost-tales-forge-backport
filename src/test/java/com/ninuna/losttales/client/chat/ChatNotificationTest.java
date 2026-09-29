package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * What each notification choice lets reach the player while they are
 * not reading the conversation: which lines reach the closed feed and
 * which chime. A line is addressed to the player when it mentions them
 * or replies to them.
 */
public final class ChatNotificationTest {
    @Test
    public void everythingShowsEveryLineAndChimesForMentionsAndWhispers() {
        ChatNotification choice = ChatNotification.EVERYTHING;
        assertTrue(choice.chimes(true, false));
        assertFalse(choice.chimes(false, false));
        // A whisper's every line is a cue where everything reaches you.
        assertTrue(choice.chimes(false, true));
        assertTrue(choice.chimes(true, true));
    }

    @Test
    public void onlyMentionsShowsAndChimesForAddressedLinesAlone() {
        ChatNotification choice = ChatNotification.ONLY_MENTIONS;
        assertTrue(choice.chimes(true, false));
        assertFalse(choice.chimes(false, false));
        assertFalse(choice.chimes(false, true));
        assertTrue(choice.chimes(true, true));
    }

    @Test
    public void nothingShowsNothingAndNeverChimes() {
        ChatNotification choice = ChatNotification.NOTHING;
        assertFalse(choice.chimes(true, false));
        assertFalse(choice.chimes(false, true));
        assertFalse(choice.chimes(true, true));
    }

    /** A few-word option: a click steps on, a right-click back, round the three. */
    @Test
    public void theChoicesCycleBothWays() {
        assertEquals(ChatNotification.ONLY_MENTIONS,
                ChatNotification.EVERYTHING.step(false));
        assertEquals(ChatNotification.NOTHING,
                ChatNotification.ONLY_MENTIONS.step(false));
        assertEquals(ChatNotification.EVERYTHING,
                ChatNotification.NOTHING.step(false));
        assertEquals(ChatNotification.NOTHING,
                ChatNotification.EVERYTHING.step(true));
        assertEquals(ChatNotification.ONLY_MENTIONS,
                ChatNotification.NOTHING.step(true));
    }

    /** The words the layout file keeps each choice under read back as it. */
    @Test
    public void eachChoiceReadsBackFromItsWord() {
        for (ChatNotification choice : ChatNotification.values()) {
            assertEquals(choice, ChatNotification.fromId(choice.id()));
            assertEquals(choice,
                    ChatNotification.fromId(" " + choice.id().toUpperCase(
                            java.util.Locale.ROOT) + " "));
        }
        assertEquals("mentions", ChatNotification.ONLY_MENTIONS.id());
        assertNull(ChatNotification.fromId("muted"));
        assertNull(ChatNotification.fromId(""));
        assertNull(ChatNotification.fromId(null));
    }

    /** Every choice's words are in the shipped language file. */
    @Test
    public void everyChoiceHasItsWords() throws java.io.IOException {
        java.util.Set<String> keys = new java.util.HashSet<String>();
        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(ChatNotificationTest.class
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
        for (ChatNotification choice : ChatNotification.values()) {
            assertTrue(choice.labelKey(), keys.contains(choice.labelKey()));
        }
        assertTrue(keys.contains("gui.losttales.chat.tab.notify"));
        assertTrue(keys.contains("gui.losttales.chat.settings.channel.notify"));
    }

    /**
     * The feed's filter lets a conversation's lines through as its choice
     * says: every line of one, only an addressed line of another.
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
