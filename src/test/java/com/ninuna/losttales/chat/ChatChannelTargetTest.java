package com.ninuna.losttales.chat;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A request names the other end of its conversation: a whisper its
 * account, a fellowship line its fellowship by id as an id is written,
 * every other line nothing.
 */
public final class ChatChannelTargetTest {
    private static final UUID GREY = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @Test
    public void eachChannelTakesItsOwnTarget() {
        assertTrue(ChatChannel.targetFits(ChatChannel.WHISPER, "Bilbo"));
        assertFalse(ChatChannel.targetFits(ChatChannel.WHISPER, ""));
        assertTrue(ChatChannel.targetFits(ChatChannel.FELLOWSHIP, GREY.toString()));
        assertFalse(ChatChannel.targetFits(ChatChannel.FELLOWSHIP, ""));
        assertFalse(ChatChannel.targetFits(ChatChannel.FELLOWSHIP, "Bilbo"));
        assertTrue(ChatChannel.targetFits(ChatChannel.GLOBAL, ""));
        assertFalse(ChatChannel.targetFits(ChatChannel.GLOBAL, "Bilbo"));
        assertFalse(ChatChannel.targetFits(ChatChannel.GLOBAL, GREY.toString()));
    }

    @Test
    public void aFellowshipIsNamedByItsIdAsAnIdIsWritten() {
        assertEquals(GREY, ChatFellowship.idOf(GREY.toString()));
        assertNull("upper case", ChatFellowship.idOf(GREY.toString().toUpperCase()));
        assertNull("short form", ChatFellowship.idOf("1-2-3-4-5"));
        assertNull(ChatFellowship.idOf(null));
        assertNull(ChatFellowship.idOf("not-an-id-but-thirty-six-characters"));
    }
}
