package com.ninuna.losttales.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/** A presence travels by its place, and an unknown place names none. */
public final class ChatPresenceTest {

    @Test
    public void everyPresenceRoundTripsByItsCode() {
        for (ChatPresence presence : ChatPresence.values()) {
            assertSame(presence, ChatPresence.fromCode(presence.code()));
        }
        assertEquals(0, ChatPresence.ONLINE.code());
        assertEquals(1, ChatPresence.AWAY.code());
        assertEquals(2, ChatPresence.DO_NOT_DISTURB.code());
    }

    @Test
    public void anUnknownCodeNamesNothing() {
        assertNull(ChatPresence.fromCode(-1));
        assertNull(ChatPresence.fromCode(ChatPresence.values().length));
    }

    @Test
    public void theLabelKeyFollowsTheId() {
        assertEquals("gui.losttales.chat.status.do_not_disturb",
                ChatPresence.DO_NOT_DISTURB.labelKey());
        assertEquals("away", ChatPresence.AWAY.getId());
    }
}
