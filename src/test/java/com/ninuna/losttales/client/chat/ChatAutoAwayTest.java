package com.ninuna.losttales.client.chat;

import static org.junit.Assert.assertEquals;

import com.ninuna.losttales.chat.ChatPresence;
import org.junit.Test;

/** Away sets in after the idle time on Online alone, and lifts on the first move. */
public final class ChatAutoAwayTest {

    @Test
    public void idleTimeOnOnlineGoesAway() {
        long idle = ChatAutoAway.IDLE_NANOS;
        assertEquals(ChatAutoAway.Change.NONE,
                ChatAutoAway.step(false, idle - 1L, 0L, ChatPresence.ONLINE, false));
        assertEquals(ChatAutoAway.Change.GO_AWAY,
                ChatAutoAway.step(false, idle, 0L, ChatPresence.ONLINE, false));
        assertEquals("already away by idle time",
                ChatAutoAway.Change.NONE,
                ChatAutoAway.step(false, idle * 2L, 0L, ChatPresence.ONLINE, true));
    }

    @Test
    public void aPresenceChosenByHandIsNeverTouched() {
        long idle = ChatAutoAway.IDLE_NANOS;
        assertEquals(ChatAutoAway.Change.NONE,
                ChatAutoAway.step(false, idle * 3L, 0L, ChatPresence.AWAY, false));
        assertEquals(ChatAutoAway.Change.NONE,
                ChatAutoAway.step(false, idle * 3L, 0L, ChatPresence.DO_NOT_DISTURB, false));
        assertEquals(ChatAutoAway.Change.NONE,
                ChatAutoAway.step(true, idle * 3L, 0L, ChatPresence.DO_NOT_DISTURB, false));
    }

    @Test
    public void theFirstMoveBringsAnIdlePlayerBack() {
        assertEquals(ChatAutoAway.Change.COME_BACK,
                ChatAutoAway.step(true, 10L, 0L, ChatPresence.ONLINE, true));
        assertEquals(ChatAutoAway.Change.NONE,
                ChatAutoAway.step(true, 10L, 0L, ChatPresence.ONLINE, false));
    }
}
