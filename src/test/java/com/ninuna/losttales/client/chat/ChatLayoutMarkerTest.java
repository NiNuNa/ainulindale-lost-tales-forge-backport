package com.ninuna.losttales.client.chat;

import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ChatLayoutMarkerTest {

    @Test
    public void aBodyBreakCarriesTheSenderColour() {
        ChatComponentText marker = ChatLayoutMarker.bodyBreak(0x12AB34);
        assertTrue(ChatLayoutMarker.isBodyBreak(marker));
        assertFalse(ChatLayoutMarker.isAnchor(marker));
        assertFalse(ChatLayoutMarker.isLineBreak(marker));
        assertEquals(0x12AB34, ChatLayoutMarker.bodyColor(marker));
        assertEquals("", marker.getUnformattedTextForChat());
        // Wrapped-line pieces copy the style, which carries the marker.
        ChatComponentText piece = new ChatComponentText("");
        piece.setChatStyle(marker.getChatStyle().createShallowCopy());
        assertEquals(0x12AB34, ChatLayoutMarker.bodyColor(piece));
    }

    /** A body break carries the line's mark with its colour; a plain one is words said. */
    @Test
    public void aBodyBreakCarriesTheLinesMark() {
        assertEquals(ChatLineMark.SAID,
                ChatLayoutMarker.bodyMark(ChatLayoutMarker.bodyBreak(0x12AB34)));
        for (ChatLineMark mark : ChatLineMark.values()) {
            ChatComponentText marker = ChatLayoutMarker.bodyBreak(0x12AB34,
                    mark);
            assertTrue(ChatLayoutMarker.isBodyBreak(marker));
            assertEquals(mark, ChatLayoutMarker.bodyMark(marker));
            assertEquals(0x12AB34, ChatLayoutMarker.bodyColor(marker));
            assertTrue(ChatLineMark.isMark(mark.separator));
        }
        assertFalse(ChatLineMark.isMark("<"));
        assertFalse(ChatLineMark.isMark("> >"));
    }

    @Test
    public void aMalformedBodyBreakNamesNoColourButStillBreaks() {
        ChatComponentText marker = new ChatComponentText("");
        marker.setChatStyle(new ChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "losttales-chat-layout:body:notacolour")));
        assertTrue(ChatLayoutMarker.isBodyBreak(marker));
        assertEquals(-1, ChatLayoutMarker.bodyColor(marker));
    }

    @Test
    public void otherMarkersAndPlainTextAreNotBodyBreaks() {
        ChatComponentText plain = new ChatComponentText("plain");
        assertFalse(ChatLayoutMarker.isBodyBreak(plain));
        assertEquals(-1, ChatLayoutMarker.bodyColor(plain));
        assertNull(ChatLayoutMarker.decode(plain));
        assertNull(ChatLayoutMarker.decode(null));

        assertTrue(ChatLayoutMarker.isAnchor(ChatLayoutMarker.anchor()));
        assertTrue(ChatLayoutMarker.isLineBreak(
                ChatLayoutMarker.lineBreak()));
        assertEquals(-1, ChatLayoutMarker.bodyColor(
                ChatLayoutMarker.lineBreak()));

        ChatLayoutMarker.Data indent = ChatLayoutMarker.decode(
                ChatLayoutMarker.indent(12, 8, 0x111111, 0x222222));
        assertNotNull(indent);
        assertFalse(indent.bodyBreak);
        assertEquals(12, indent.indent(false));
        assertEquals(8, indent.indent(true));
        assertTrue(indent.hasColors());
        assertEquals(0x111111, indent.nameColor);
        assertEquals(0x222222, indent.titleColor);
        assertFalse(ChatLayoutMarker.decode(
                ChatLayoutMarker.indent(12, 8, -1, -1)).hasColors());
    }
}
