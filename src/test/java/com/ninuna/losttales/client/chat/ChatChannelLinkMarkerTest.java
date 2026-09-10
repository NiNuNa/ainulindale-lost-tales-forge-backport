package com.ninuna.losttales.client.chat;

import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A channel link carries its colour, the tab it opens and the line it
 * lands on through the marker, and nothing else reads as one.
 */
public final class ChatChannelLinkMarkerTest {

    @Test
    public void aLinkRoundTripsItsTabColourAndLine() {
        ChatComponentText run = ChatChannelLinkMarker.apply(
                new ChatComponentText("#Faction"), 0x577F9D,
                "faction|scope:gondor", -12);
        assertTrue(ChatChannelLinkMarker.isMarker(run));
        ChatChannelLinkMarker.Data data = ChatChannelLinkMarker.decode(run);
        assertEquals(0x577F9D, data.color);
        assertEquals("faction|scope:gondor", data.tabId);
        assertEquals(-12, data.chatLineId);
        assertEquals(Integer.valueOf(0x577F9D),
                ChatChannelLinkMarker.colorOf(run));
        // The whisper id's own separators and marks survive the trip.
        ChatComponentText whisper = ChatChannelLinkMarker.apply(
                new ChatComponentText("#Steve"), 0xA0DDD3,
                "whisper:Steve|Aragorn|own:b0000000-0000-0000-0000-00000000000b", 0);
        assertEquals("whisper:Steve|Aragorn|own:b0000000-0000-0000-0000-00000000000b",
                ChatChannelLinkMarker.decode(whisper).tabId);
        assertEquals(0, ChatChannelLinkMarker.decode(whisper).chatLineId);
    }

    @Test
    public void aLinkToAMessageCarriesTheServersIdAndItsPiecesActTogether() {
        ChatComponentText name = ChatChannelLinkMarker.applyMessage(
                new ChatComponentText("#Global"), 0x577F9D, "all", 1757522000000L);
        ChatChannelLinkMarker.Data data = ChatChannelLinkMarker.decode(name);
        assertEquals(1757522000000L, data.messageId);
        assertEquals(0, data.chatLineId);
        assertEquals("all", data.tabId);
        assertTrue(data.linksMessage());
        assertFalse(ChatChannelLinkMarker.isIconSlot(name));
        // The bubble's slot: the same link on the two reserved spaces.
        ChatComponentText slot = ChatChannelLinkMarker.applyMessage(
                new ChatComponentText(ChatChannelLinkMarker.ICON_SLOT),
                0x577F9D, "all", 1757522000000L);
        assertTrue(ChatChannelLinkMarker.isIconSlot(slot));
        assertTrue(ChatChannelLinkMarker.sameLink(name, slot));
        // Two spaces linking to the tab alone are not a bubble.
        ChatComponentText plainSlot = ChatChannelLinkMarker.apply(
                new ChatComponentText(ChatChannelLinkMarker.ICON_SLOT),
                0x577F9D, "all", 0);
        assertFalse(ChatChannelLinkMarker.isIconSlot(plainSlot));
        assertFalse(ChatChannelLinkMarker.decode(plainSlot).linksMessage());
        assertFalse(ChatChannelLinkMarker.sameLink(name, plainSlot));
        // A link by this client's own line id is a link to a message too.
        assertTrue(ChatChannelLinkMarker.decode(ChatChannelLinkMarker.apply(
                new ChatComponentText("#Global"), 0, "all", -7)).linksMessage());
        // An id the server never gave is not carried, and not read.
        assertEquals(0L, ChatChannelLinkMarker.decode(ChatChannelLinkMarker.applyMessage(
                new ChatComponentText("#Global"), 0, "all", 0L)).messageId);
        ChatComponentText negative = new ChatComponentText("x");
        negative.setChatStyle(negative.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "losttales-chat-link:000000:0:YWxs:-5")));
        assertNull(ChatChannelLinkMarker.decode(negative));
    }

    @Test
    public void nothingElseReadsAsALink() {
        ChatComponentText plain = new ChatComponentText("all");
        assertFalse(ChatChannelLinkMarker.isMarker(plain));
        assertNull(ChatChannelLinkMarker.decode(plain));
        assertNull(ChatChannelLinkMarker.colorOf(plain));
        // An empty tab id marks nothing.
        ChatComponentText empty = ChatChannelLinkMarker.apply(
                new ChatComponentText("#"), 0, "", 1);
        assertFalse(ChatChannelLinkMarker.isMarker(empty));
        // Another marker's suggestion, or a real suggestion, is not a link.
        ChatComponentText other = new ChatComponentText("x");
        other.setChatStyle(other.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "losttales-chat-reply:ffffff:5")));
        assertNull(ChatChannelLinkMarker.decode(other));
        ChatComponentText broken = new ChatComponentText("x");
        broken.setChatStyle(broken.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "losttales-chat-link:zz:1:YWxs")));
        assertNull(ChatChannelLinkMarker.decode(broken));
        assertTrue(ChatChannelLinkMarker.isMarker(broken));
    }
}
