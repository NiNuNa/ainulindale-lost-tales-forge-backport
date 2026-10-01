package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.ChatConsoleFixtures;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The console keeps the newest entries in order and repeats no secret. */
public final class ChatConsoleStreamTest {

    @Before
    public void setUp() {
        ChatConsoleStream.clear();
        ChatMessageIdAllocator.reset();
    }

    @After
    public void tearDown() {
        ChatConsoleStream.clear();
        ChatMessageIdAllocator.reset();
    }

    @Test
    public void entriesReplayOldestFirstAndTheOldestGoFirst() {
        long first = record("first");
        for (int index = 0; index < ChatConsoleStream.MAX_EVENTS; index++) {
            record("entry " + index);
        }
        // A ring's worth is kept, and the first of all is not among them.
        assertEquals(ChatConsoleStream.MAX_EVENTS,
                ChatConsoleStream.snapshot().size());
        assertNull(ChatConsoleStream.find(first));
        assertEquals("entry 0", ChatConsoleStream.snapshot().get(0).getText());
        List<ChatConsoleEvent> replay = ChatConsoleStream.replay(0L);
        assertEquals(ChatConsoleStream.MAX_REPLAY, replay.size());
        assertTrue(replay.get(0).getId() > first);
        for (int index = 1; index < replay.size(); index++) {
            assertTrue(replay.get(index - 1).getId() < replay.get(index).getId());
        }
        assertEquals("entry " + (ChatConsoleStream.MAX_EVENTS - 1),
                replay.get(replay.size() - 1).getText());
        long since = replay.get(replay.size() - 4).getId();
        assertEquals(3, ChatConsoleStream.replay(since).size());
        ChatConsoleStream.record(null);
        ChatConsoleStream.clear();
        assertTrue(ChatConsoleStream.snapshot().isEmpty());
        assertTrue(ChatConsoleStream.replay(0L).isEmpty());
    }

    @Test
    public void anEventHasAKindASeverityAndSomethingToSay() {
        try {
            ChatConsoleFixtures.entry(1L, 1L, null, ChatConsoleEvent.Severity.INFO, "a", "b");
            assertTrue(false);
        } catch (IllegalArgumentException expected) {
            // no kind
        }
        try {
            ChatConsoleFixtures.entry(
                    1L, 1L, ChatConsoleEvent.Kind.COMMAND, ChatConsoleEvent.Severity.INFO, "a",
                    "   ");
            assertTrue(false);
        } catch (IllegalArgumentException expected) {
            // nothing said
        }
        StringBuilder long_ = new StringBuilder();
        for (int index = 0; index < ChatConsoleEvent.MAX_TEXT_LENGTH + 50; index++) {
            long_.append('x');
        }
        ChatConsoleEvent clipped = ChatConsoleFixtures.entry(
                1L, 1L, ChatConsoleEvent.Kind.COMMAND, ChatConsoleEvent.Severity.INFO, null,
                long_.toString());
        assertEquals(ChatConsoleEvent.MAX_TEXT_LENGTH, clipped.getText().length());
        assertEquals("", clipped.getActor());
        // Only a command has a context, and only a printable one.
        assertEquals("global", ChatConsoleFixtures.command(1L, 1L, "a", "/x", " global ").getContext());
        assertEquals("", new ChatConsoleEvent(
                1L, 1L, ChatConsoleEvent.Kind.SERVER, ChatConsoleEvent.Severity.INFO, "a",
                "started", "global", null, null).getContext());
        assertEquals("", ChatConsoleFixtures.command(1L, 1L, "a", "/x", "global\nooc").getContext());
        assertEquals("", ChatConsoleFixtures.command(1L, 1L, "a", "/x", null).getContext());
        assertEquals(ChatConsoleEvent.Kind.WARNING, ChatConsoleEvent.Kind.fromOrdinal(4));
        assertEquals(null, ChatConsoleEvent.Kind.fromOrdinal(99));
        assertEquals(null, ChatConsoleEvent.Severity.fromOrdinal(-1));
    }

    /** The save's events come back in order under their own ids, once each. */
    @Test
    public void restoredEventsComeBackOnceEachAndMoveTheAllocatorOn() {
        List<ChatConsoleEvent> kept = new java.util.ArrayList<ChatConsoleEvent>();
        kept.add(ChatConsoleFixtures.entry(
                30L, 30L, ChatConsoleEvent.Kind.SERVER, ChatConsoleEvent.Severity.INFO, "Server",
                "third"));
        kept.add(ChatConsoleFixtures.entry(
                10L, 10L, ChatConsoleEvent.Kind.SERVER, ChatConsoleEvent.Severity.INFO, "Server",
                "first"));
        kept.add(ChatConsoleFixtures.entry(
                20L, 20L, ChatConsoleEvent.Kind.SERVER, ChatConsoleEvent.Severity.INFO, "Server",
                "second"));
        kept.add(ChatConsoleFixtures.entry(
                20L, 21L, ChatConsoleEvent.Kind.SERVER, ChatConsoleEvent.Severity.INFO, "Server",
                "second again"));
        kept.add(null);
        assertEquals(3, ChatConsoleStream.restore(kept,
                java.util.Collections.<Long, ChatReactions>emptyMap()));
        List<ChatConsoleEvent> replay = ChatConsoleStream.replay(0L);
        assertEquals(3, replay.size());
        assertEquals("first", replay.get(0).getText());
        assertEquals("second", replay.get(1).getText());
        assertEquals("third", replay.get(2).getText());
        assertEquals(3, ChatConsoleStream.snapshot().size());
        assertTrue(ChatMessageIdAllocator.next() > 30L);
        // Restoring nothing changes nothing.
        assertEquals(0, ChatConsoleStream.restore(null, null));
        assertEquals(3, ChatConsoleStream.snapshot().size());
    }

    private static long record(String text) {
        long id = ChatMessageIdAllocator.next();
        ChatConsoleStream.record(ChatConsoleFixtures.entry(
                id, id, ChatConsoleEvent.Kind.COMMAND, ChatConsoleEvent.Severity.INFO, "Steve",
                text));
        return id;
    }

    /**
     * An entry takes reactions as a message does, and loses them with
     * itself when the ring moves past it.
     */
    @Test
    public void anEntryTakesReactionsAndLosesThemWithItself() {
        java.util.UUID reader = java.util.UUID.randomUUID();
        ChatConsoleStream.record(ChatConsoleFixtures.entry(
                5L, 5L, ChatConsoleEvent.Kind.SERVER, ChatConsoleEvent.Severity.INFO, "Server",
                "Server started"));
        assertTrue(ChatConsoleStream.react(5L, reader, "Nils", "grinning", true));
        assertFalse(ChatConsoleStream.react(5L, reader, "Nils", "grinning", true));
        assertFalse(ChatConsoleStream.react(6L, reader, "Nils", "grinning", true));
        assertEquals(1, ChatConsoleStream.reactionsFor(5L, reader)
                .getReactions().size());
        for (long id = 10L; id < 10L + ChatConsoleStream.MAX_EVENTS; id++) {
            ChatConsoleStream.record(ChatConsoleFixtures.entry(
                    id, id, ChatConsoleEvent.Kind.SERVER, ChatConsoleEvent.Severity.INFO, "Server",
                    "tick"));
        }
        assertNull(ChatConsoleStream.find(5L));
        assertTrue(ChatConsoleStream.reactionsFor(5L, reader).isEmpty());
        assertTrue(ChatConsoleStream.reactionsSnapshot().isEmpty());
    }
}
