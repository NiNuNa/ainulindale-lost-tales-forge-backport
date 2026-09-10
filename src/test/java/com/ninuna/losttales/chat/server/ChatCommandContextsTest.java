package com.ninuna.losttales.chat.server;

import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A note answers the next command once, only while it is fresh, and
 * the store never grows past what a server of players needs.
 */
public final class ChatCommandContextsTest {

    private static final UUID STEVE = UUID.fromString("a0000000-0000-0000-0000-00000000000a");
    private static final UUID ALEX = UUID.fromString("b0000000-0000-0000-0000-00000000000b");

    @Before
    public void setUp() {
        ChatCommandContexts.clear();
    }

    @After
    public void tearDown() {
        ChatCommandContexts.clear();
    }

    @Test
    public void aNoteAnswersOnceWhileFresh() {
        ChatCommandContexts.note(STEVE, "all", 1000L);
        ChatCommandContexts.note(ALEX, "ooc", 1000L);
        assertEquals("all", ChatCommandContexts.take(STEVE, 1500L));
        assertEquals("taken with the command it was for",
                "", ChatCommandContexts.take(STEVE, 1600L));
        assertEquals("ooc", ChatCommandContexts.take(ALEX, 1000L));
        assertEquals("", ChatCommandContexts.take(null, 1000L));
    }

    @Test
    public void aStaleNoteAnswersNothing() {
        ChatCommandContexts.note(STEVE, "all", 1000L);
        assertEquals("", ChatCommandContexts.take(STEVE,
                1000L + ChatCommandContexts.VALID_MILLIS + 1L));
        // A note from the future is no note either.
        ChatCommandContexts.note(STEVE, "all", 5000L);
        assertEquals("", ChatCommandContexts.take(STEVE, 4000L));
        // The newest note is the one that counts.
        ChatCommandContexts.note(STEVE, "all", 1000L);
        ChatCommandContexts.note(STEVE, "proximity", 1100L);
        assertEquals("proximity", ChatCommandContexts.take(STEVE, 1200L));
    }

    @Test
    public void nothingEmptyIsKeptAndTheStoreStaysBounded() {
        ChatCommandContexts.note(STEVE, "", 1000L);
        ChatCommandContexts.note(STEVE, null, 1000L);
        assertEquals(0, ChatCommandContexts.size());
        for (int index = 0; index < 600; index++) {
            ChatCommandContexts.note(new UUID(0L, index + 1L), "all", 1000L);
        }
        assertEquals("full of fresh notes, no more are taken",
                512, ChatCommandContexts.size());
        // Once they are stale the next note purges them.
        ChatCommandContexts.note(STEVE, "all",
                1000L + ChatCommandContexts.VALID_MILLIS + 1L);
        assertEquals(1, ChatCommandContexts.size());
        ChatCommandContexts.clear();
        assertEquals(0, ChatCommandContexts.size());
    }
}
