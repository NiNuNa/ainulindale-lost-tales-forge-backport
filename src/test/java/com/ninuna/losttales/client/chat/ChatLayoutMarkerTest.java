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
    public void aBodyBreakCarriesTheSenderColourAndNoLabel() {
        ChatComponentText marker = ChatLayoutMarker.bodyBreak(0x12AB34);
        assertTrue(ChatLayoutMarker.isBodyBreak(marker));
        assertFalse(ChatLayoutMarker.isAnchor(marker));
        assertFalse(ChatLayoutMarker.isLineBreak(marker));
        assertEquals(0x12AB34, ChatLayoutMarker.bodyColor(marker));
        assertNull(ChatLayoutMarker.bodyLabel(marker));
        assertEquals("", marker.getUnformattedTextForChat());
    }

    @Test
    public void aLabelledBodyBreakCarriesBothAndKeepsColonsInTheLabel() {
        ChatComponentText marker = ChatLayoutMarker.bodyBreak(0x12AB34,
                "Used the command: ");
        assertTrue(ChatLayoutMarker.isBodyBreak(marker));
        assertEquals(0x12AB34, ChatLayoutMarker.bodyColor(marker));
        assertEquals("Used the command: ",
                ChatLayoutMarker.bodyLabel(marker));
        // The label is everything after the colour, colons included.
        ChatComponentText nested = ChatLayoutMarker.bodyBreak(7, "a:b: ");
        assertEquals(7, ChatLayoutMarker.bodyColor(nested));
        assertEquals("a:b: ", ChatLayoutMarker.bodyLabel(nested));
        // An empty label is the chevron.
        assertNull(ChatLayoutMarker.bodyLabel(
                ChatLayoutMarker.bodyBreak(7, "")));
        assertNull(ChatLayoutMarker.bodyLabel(
                ChatLayoutMarker.bodyBreak(7, null)));
        // Wrapped-line pieces copy the style, which carries the marker.
        ChatComponentText piece = new ChatComponentText("");
        piece.setChatStyle(marker.getChatStyle().createShallowCopy());
        assertEquals("Used the command: ",
                ChatLayoutMarker.bodyLabel(piece));
    }

    @Test
    public void aMalformedBodyBreakNamesNoColourButStillBreaks() {
        ChatComponentText marker = new ChatComponentText("");
        marker.setChatStyle(new ChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "losttales-chat-layout:body:notacolour:label")));
        assertTrue(ChatLayoutMarker.isBodyBreak(marker));
        assertEquals(-1, ChatLayoutMarker.bodyColor(marker));
        assertEquals("label", ChatLayoutMarker.bodyLabel(marker));
    }

    @Test
    public void otherMarkersAndPlainTextAreNotBodyBreaks() {
        ChatComponentText plain = new ChatComponentText("plain");
        assertFalse(ChatLayoutMarker.isBodyBreak(plain));
        assertEquals(-1, ChatLayoutMarker.bodyColor(plain));
        assertNull(ChatLayoutMarker.bodyLabel(plain));
        assertNull(ChatLayoutMarker.decode(plain));
        assertNull(ChatLayoutMarker.decode(null));

        assertTrue(ChatLayoutMarker.isAnchor(ChatLayoutMarker.anchor()));
        assertTrue(ChatLayoutMarker.isLineBreak(
                ChatLayoutMarker.lineBreak()));
        assertNull(ChatLayoutMarker.bodyLabel(ChatLayoutMarker.anchor()));
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
                ChatLayoutMarker.indent(12, 8)).hasColors());
    }
}
