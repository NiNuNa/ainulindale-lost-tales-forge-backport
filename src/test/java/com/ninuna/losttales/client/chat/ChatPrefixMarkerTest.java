package com.ninuna.losttales.client.chat;

import net.minecraft.util.ChatComponentText;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A line's channel prefix belongs to the closed feed, where one stack
 * carries every channel; the open screen's tabs name the channel, so it
 * takes no width there. It carries its exact colour.
 */
public final class ChatPrefixMarkerTest {

    @Test
    public void thePrefixIsHiddenWhileTheScreenIsOpen() {
        ChatComponentText channel = ChatPrefixMarker.channel(
                new ChatComponentText("Global: "), 0x00FF00);
        assertFalse(ChatPrefixMarker.isHidden(channel, false));
        assertTrue(ChatPrefixMarker.isHidden(channel, true));
    }

    @Test
    public void thePrefixKeepsItsExactColour() {
        ChatComponentText channel = ChatPrefixMarker.channel(
                new ChatComponentText("Party: "), 0x123456);
        assertEquals(Integer.valueOf(0x123456),
                ChatPrefixMarker.decode(channel));
        assertTrue(ChatPrefixMarker.isMarker(channel));
    }

    @Test
    public void anOrdinaryComponentIsNoPrefix() {
        ChatComponentText plain = new ChatComponentText("hello");
        assertNull(ChatPrefixMarker.decode(plain));
        assertFalse(ChatPrefixMarker.isMarker(plain));
        assertFalse(ChatPrefixMarker.isHidden(plain, true));
        assertFalse(ChatPrefixMarker.isHidden(plain, false));
        assertFalse(ChatPrefixMarker.isHidden(null, false));
    }
}
