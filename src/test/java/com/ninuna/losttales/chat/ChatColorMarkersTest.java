package com.ninuna.losttales.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** A colour mark is six hex digits behind one prefix, and nothing else reads as one. */
public final class ChatColorMarkersTest {

    @Test
    public void aColourRoundTripsThroughItsMark() {
        assertEquals("losttales-chat-color:00a0ff", ChatColorMarkers.value(0x00A0FF));
        assertEquals("losttales-chat-color:000000", ChatColorMarkers.value(0));
        assertEquals(Integer.valueOf(0x00A0FF),
                ChatColorMarkers.decode(ChatColorMarkers.value(0x00A0FF)));
        // The alpha byte is not part of a colour.
        assertEquals(Integer.valueOf(0x123456),
                ChatColorMarkers.decode(ChatColorMarkers.value(0xFF123456)));
    }

    @Test
    public void anythingElseIsNotAMark() {
        assertNull(ChatColorMarkers.decode(null));
        assertNull(ChatColorMarkers.decode("/msg Steve "));
        assertNull(ChatColorMarkers.decode("losttales-chat-color:"));
        assertNull(ChatColorMarkers.decode("losttales-chat-color:12345"));
        assertNull(ChatColorMarkers.decode("losttales-chat-color:1234567"));
        assertNull(ChatColorMarkers.decode("losttales-chat-color:zzzzzz"));
        assertNull(ChatColorMarkers.decode("losttales-chat-title:123456:"));
    }
}
