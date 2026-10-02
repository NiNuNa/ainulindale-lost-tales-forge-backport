package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ChatSentHistoryTest {

    private static final ConversationPage GLOBAL = ConversationPage.of(ChatChannel.GLOBAL);
    private static final ConversationPage OOC = ConversationPage.of(ChatChannel.OOC);

    @Test
    public void upWalksBackAndDownRestoresThePendingText() {
        ChatSentHistory history = new ChatSentHistory();
        history.record(GLOBAL, "first");
        history.record(GLOBAL, "second");

        assertEquals("second", history.step(GLOBAL, -1, "draft"));
        assertEquals("first", history.step(GLOBAL, -1, "second"));
        assertNull("the oldest line is the end of the walk",
                history.step(GLOBAL, -1, "first"));
        assertEquals("second", history.step(GLOBAL, 1, "first"));
        assertEquals("the draft comes back past the newest line",
                "draft", history.step(GLOBAL, 1, "second"));
        assertNull("nothing lies below the draft", history.step(GLOBAL, 1, "draft"));
    }


    @Test
    public void sendingEndsTheWalkAndAppends() {
        ChatSentHistory history = new ChatSentHistory();
        history.record(GLOBAL, "one");
        assertEquals("one", history.step(GLOBAL, -1, "typing"));
        history.record(GLOBAL, "two");
        assertEquals(Arrays.asList("one", "two"), history.entries(GLOBAL));
        assertEquals("two", history.step(GLOBAL, -1, ""));
    }

    @Test
    public void leavingTheTabEndsTheWalk() {
        ChatSentHistory history = new ChatSentHistory();
        history.record(GLOBAL, "one");
        history.record(OOC, "ooc");
        assertEquals("one", history.step(GLOBAL, -1, "draft"));
        history.endBrowse();
        assertEquals("a fresh walk starts at the other tab's newest line",
                "ooc", history.step(OOC, -1, "one"));
        assertEquals("one", history.step(OOC, 1, "ooc"));
    }

    @Test
    public void aWalkOnOneTabDoesNotContinueOnAnother() {
        ChatSentHistory history = new ChatSentHistory();
        history.record(GLOBAL, "a");
        history.record(GLOBAL, "b");
        history.record(OOC, "x");
        assertEquals("b", history.step(GLOBAL, -1, ""));
        // Asked for the other tab mid-walk: that tab's own walk begins.
        assertEquals("x", history.step(OOC, -1, "b"));
        assertEquals("b", history.step(OOC, 1, "x"));
    }


    @Test
    public void emptyTextIsNeverRecorded() {
        ChatSentHistory history = new ChatSentHistory();
        history.record(GLOBAL, "");
        history.record(GLOBAL, "   ");
        history.record(GLOBAL, null);
        assertTrue(history.entries(GLOBAL).isEmpty());
        assertNull(history.step(GLOBAL, -1, ""));
    }

    @Test
    public void linesAreCappedPerTab() {
        ChatSentHistory history = new ChatSentHistory();
        for (int index = 0; index <= ChatSentHistory.MAX_ENTRIES_PER_TAB; index++) {
            history.record(GLOBAL, "line " + index);
        }
        assertEquals(ChatSentHistory.MAX_ENTRIES_PER_TAB,
                history.entries(GLOBAL).size());
        assertEquals("line 1", history.entries(GLOBAL).get(0));
    }

    @Test
    public void theTabWrittenToLongestAgoGoesFirstAtTheTabCap() {
        ChatSentHistory history = new ChatSentHistory();
        history.record(GLOBAL, "kept");
        for (int index = 0; index < ChatSentHistory.MAX_TABS - 1; index++) {
            history.record(ConversationPage.whisper("Partner" + index, ""), "hi");
        }
        // Writing to Global again makes it the most recent tab.
        history.record(GLOBAL, "again");
        history.record(ConversationPage.whisper("Newest", ""), "hi");
        assertEquals(Arrays.asList("kept", "again"), history.entries(GLOBAL));
        assertTrue("the oldest whisper made room",
                history.entries(ConversationPage.whisper("Partner0", "")).isEmpty());
    }

    @Test
    public void conversationsAreForgottenTogether() {
        ChatSentHistory history = new ChatSentHistory();
        history.record(GLOBAL, "kept");
        history.record(ConversationPage.whisper("Bilbo", ""), "gone");
        history.record(ConversationPage.npc("Gandalf"), "gone too");
        history.forgetConversations();
        assertEquals(Arrays.asList("kept"), history.entries(GLOBAL));
        assertTrue(history.entries(ConversationPage.whisper("Bilbo", "")).isEmpty());
        assertTrue(history.entries(ConversationPage.npc("Gandalf")).isEmpty());
    }

    @Test
    public void clearForgetsEverything() {
        ChatSentHistory history = new ChatSentHistory();
        history.record(GLOBAL, "a");
        assertEquals("a", history.step(GLOBAL, -1, "pending"));
        history.clear();
        assertTrue(history.entries(GLOBAL).isEmpty());
    }
}
