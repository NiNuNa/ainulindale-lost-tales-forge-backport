package com.ninuna.losttales.client.chat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** A player goes idle after the idle time, and the first move brings them back. */
public final class ChatAutoAwayTest {

    @Test
    public void idleTimeMakesThePlayerIdle() {
        long idle = ChatAutoAway.IDLE_NANOS;
        assertEquals(ChatAutoAway.Change.NONE,
                ChatAutoAway.step(false, idle - 1L, 0L, false));
        assertEquals(ChatAutoAway.Change.GO_IDLE,
                ChatAutoAway.step(false, idle, 0L, false));
        assertEquals("already idle",
                ChatAutoAway.Change.NONE,
                ChatAutoAway.step(false, idle * 2L, 0L, true));
    }

    @Test
    public void theFirstMoveBringsAnIdlePlayerBack() {
        assertEquals(ChatAutoAway.Change.COME_BACK,
                ChatAutoAway.step(true, 10L, 0L, true));
        assertEquals(ChatAutoAway.Change.NONE,
                ChatAutoAway.step(true, 10L, 0L, false));
    }
}
