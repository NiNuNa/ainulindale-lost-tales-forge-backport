package com.ninuna.losttales.client.chat;

import net.minecraft.util.ChatComponentText;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A line's header runs: the channel prefix belongs to the closed feed,
 * and the timestamp never takes inline width at all — the open screen
 * draws it in the timestamp column instead. Both carry their exact
 * colour.
 */
public final class ChatPrefixMarkerTest {

    @Test
    public void eachHeaderRunIsHiddenInTheStateThatDoesNotNeedIt() {
        ChatComponentText channel = ChatPrefixMarker.channel(
                new ChatComponentText("Global: "), 0x00FF00);
        ChatComponentText time = ChatPrefixMarker.timestamp(
                new ChatComponentText("[12:34] "), 0xDBC9B4);

        // The feed shows the channel; the timestamp is never inline —
        // the open screen draws it in the column at the window's edge.
        assertFalse(ChatPrefixMarker.isHidden(channel, false));
        assertTrue(ChatPrefixMarker.isHidden(channel, true));
        assertTrue(ChatPrefixMarker.isHidden(time, false));
        assertTrue(ChatPrefixMarker.isHidden(time, true));
        assertTrue(ChatPrefixMarker.isTimestamp(time));
        assertFalse(ChatPrefixMarker.isTimestamp(channel));
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
