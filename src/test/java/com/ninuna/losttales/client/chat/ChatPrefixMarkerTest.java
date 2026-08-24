package com.ninuna.losttales.client.chat;

import net.minecraft.util.ChatComponentText;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A line's header runs each belong to one chat state: the channel prefix
 * to the closed feed, the timestamp to the open screen. Both carry their
 * exact colour, and neither is ever shown in the other's state.
 */
public final class ChatPrefixMarkerTest {

    @Test
    public void eachHeaderRunIsHiddenInTheStateThatDoesNotNeedIt() {
        ChatComponentText channel = ChatPrefixMarker.channel(
                new ChatComponentText("Global: "), 0x00FF00);
        ChatComponentText time = ChatPrefixMarker.timestamp(
                new ChatComponentText("[12:34] "), 0xDBC9B4);

        // The feed shows the channel and no time; the screen the reverse.
        assertFalse(ChatPrefixMarker.isHidden(channel, false));
        assertTrue(ChatPrefixMarker.isHidden(channel, true));
        assertTrue(ChatPrefixMarker.isHidden(time, false));
        assertFalse(ChatPrefixMarker.isHidden(time, true));
    }

    @Test
    public void bothKindsKeepTheirExactColourAndAreToldApart() {
        ChatComponentText channel = ChatPrefixMarker.channel(
                new ChatComponentText("Party: "), 0x123456);
        ChatComponentText time = ChatPrefixMarker.timestamp(
                new ChatComponentText("[12:34] "), 0xABCDEF);
        assertEquals(Integer.valueOf(0x123456),
                ChatPrefixMarker.decode(channel));
        assertEquals(Integer.valueOf(0xABCDEF),
                ChatPrefixMarker.decode(time));
        assertTrue(ChatPrefixMarker.isChannel(channel));
        assertFalse(ChatPrefixMarker.isChannel(time));
        assertTrue(ChatPrefixMarker.isMarker(time));
    }

    @Test
    public void anOrdinaryComponentIsNeither() {
        ChatComponentText plain = new ChatComponentText("hello");
        assertNull(ChatPrefixMarker.decode(plain));
        assertFalse(ChatPrefixMarker.isMarker(plain));
        assertFalse(ChatPrefixMarker.isHidden(plain, true));
        assertFalse(ChatPrefixMarker.isHidden(plain, false));
        assertFalse(ChatPrefixMarker.isHidden(null, false));
    }
}
