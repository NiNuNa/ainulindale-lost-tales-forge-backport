package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ChatSentHistoryTest {

    private static final ChatTab GLOBAL = ChatTab.of(ChatChannel.ALL);
    private static final ChatTab OOC = ChatTab.of(ChatChannel.OOC);

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
        assertFalse(history.isBrowsing());
        assertNull("nothing lies below the draft", history.step(GLOBAL, 1, "draft"));
    }

    @Test
    public void eachTabHasItsOwnHistory() {
        ChatSentHistory history = new ChatSentHistory();
        history.record(GLOBAL, "Hi");

        assertNull("OOC has nothing to recall", history.step(OOC, -1, ""));
        assertEquals("Hi", history.step(GLOBAL, -1, ""));
    }

    @Test
    public void sendingEndsTheWalkAndAppends() {
        ChatSentHistory history = new ChatSentHistory();
        history.record(GLOBAL, "one");
        assertEquals("one", history.step(GLOBAL, -1, "typing"));
        history.record(GLOBAL, "two");
        assertFalse(history.isBrowsing());
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
    public void oneEntryWalksUpAndBack() {
        ChatSentHistory history = new ChatSentHistory();
        history.record(GLOBAL, "only");
        assertEquals("only", history.step(GLOBAL, -1, ""));
        assertNull(history.step(GLOBAL, -1, "only"));
        assertEquals("", history.step(GLOBAL, 1, "only"));
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
            history.record(ChatTab.whisper("Partner" + index), "hi");
        }
        // Writing to Global again makes it the most recent tab.
        history.record(GLOBAL, "again");
        history.record(ChatTab.whisper("Newest"), "hi");
        assertEquals(ChatSentHistory.MAX_TABS, history.tabs().size());
        assertEquals(Arrays.asList("kept", "again"), history.entries(GLOBAL));
        assertTrue("the oldest whisper made room",
                history.entries(ChatTab.whisper("Partner0")).isEmpty());
    }

    @Test
    public void conversationsAreForgottenTogether() {
        ChatSentHistory history = new ChatSentHistory();
        history.record(GLOBAL, "kept");
        history.record(ChatTab.whisper("Bilbo"), "gone");
        history.record(ChatTab.npc("Gandalf"), "gone too");
        history.forgetConversations();
        assertEquals(Arrays.asList("kept"), history.entries(GLOBAL));
        assertTrue(history.entries(ChatTab.whisper("Bilbo")).isEmpty());
        assertTrue(history.entries(ChatTab.npc("Gandalf")).isEmpty());
    }

    @Test
    public void clearForgetsEverything() {
        ChatSentHistory history = new ChatSentHistory();
        history.record(GLOBAL, "a");
        assertEquals("a", history.step(GLOBAL, -1, "pending"));
        history.clear();
        assertTrue(history.entries(GLOBAL).isEmpty());
        assertFalse(history.isBrowsing());
        assertEquals("", history.pending());
    }
}
