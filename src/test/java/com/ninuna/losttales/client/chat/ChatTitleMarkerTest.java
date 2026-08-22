package com.ninuna.losttales.client.chat;

import net.minecraft.util.ChatComponentText;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ChatTitleMarkerTest {

    @Test
    public void markerCarriesColourAndEpithetAndSurvivesStyleCopies() {
        ChatComponentText part = ChatTitleMarker.apply(
                new ChatComponentText(", the Gondor Farmer"), 0x12AB34,
                "Gondor Farmer");
        ChatTitleMarker.Data data = ChatTitleMarker.decode(part);
        assertNotNull(data);
        assertEquals(0x12AB34, data.color);
        assertEquals("Gondor Farmer", data.epithet);
        assertEquals(Integer.valueOf(0x12AB34), ChatTitleMarker.colorOf(part));
        assertTrue(ChatTitleMarker.isMarker(part));
        // Wrapped-line pieces copy the style, which carries the marker.
        ChatComponentText piece = new ChatComponentText("Farmer");
        piece.setChatStyle(part.getChatStyle().createShallowCopy());
        assertEquals("Gondor Farmer", ChatTitleMarker.decode(piece).epithet);
        // Non-ASCII epithets round-trip.
        ChatComponentText elvish = ChatTitleMarker.apply(
                new ChatComponentText(""), 0, "Dúnedain Ranger");
        assertEquals("Dúnedain Ranger",
                ChatTitleMarker.decode(elvish).epithet);
    }

    @Test
    public void otherMarkersAndPlainTextAreNotTitles() {
        assertNull(ChatTitleMarker.decode(new ChatComponentText("plain")));
        assertNull(ChatTitleMarker.decode(null));
        ChatComponentText colour = ChatColorMarker.apply(
                new ChatComponentText("<"), 0xFFFFFF);
        assertFalse(ChatTitleMarker.isMarker(colour));
        assertNull(ChatTitleMarker.colorOf(colour));
    }
}
