package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.util.ChatComponentText;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class ClientChatChannelViewsTest {

    @Before
    public void reset() {
        ClientChatChannelViews.clear();
    }

    @After
    public void cleanUp() {
        ClientChatChannelViews.clear();
    }

    private static ChatLine line(int chatLineId) {
        return new ChatLine(0, new ChatComponentText("x"), chatLineId);
    }

    @Test
    public void viewsFilterByRecordedChannelAndKeepVanillaLines() {
        ClientChatChannelViews.record(-10, ChatChannel.PARTY,
                ChatChannel.ALL, false);
        ClientChatChannelViews.record(-11, ChatChannel.ALL,
                ChatChannel.ALL, false);
        List<ChatLine> drawn = new ArrayList<ChatLine>();
        drawn.add(line(-11));
        drawn.add(line(0));
        drawn.add(line(-10));

        assertSame(drawn, ClientChatChannelViews.visibleLines(drawn, null));
        // Vanilla lines (achievements, commands) belong to Global only.
        List<ChatLine> party = ClientChatChannelViews.visibleLines(
                drawn, ChatChannel.PARTY);
        assertEquals(1, party.size());
        assertEquals(-10, party.get(0).getChatLineID());
        List<ChatLine> all = ClientChatChannelViews.visibleLines(
                drawn, ChatChannel.ALL);
        assertEquals(2, all.size());
        assertEquals(-11, all.get(0).getChatLineID());
        assertEquals(0, all.get(1).getChatLineID());
        // Same list, same head, same view: the cached instance is reused.
        assertSame(all, ClientChatChannelViews.visibleLines(
                drawn, ChatChannel.ALL));
        assertNull(ClientChatChannelViews.channelOf(0));
        assertEquals(ChatChannel.PARTY, ClientChatChannelViews.channelOf(-10));
    }

    @Test
    public void cacheInvalidatesWhenTheHeadOfHistoryChanges() {
        ClientChatChannelViews.record(-1, ChatChannel.OOC,
                ChatChannel.OOC, false);
        List<ChatLine> drawn = new ArrayList<ChatLine>();
        drawn.add(line(-1));
        List<ChatLine> first = ClientChatChannelViews.visibleLines(
                drawn, ChatChannel.OOC);
        assertEquals(1, first.size());
        ClientChatChannelViews.record(-2, ChatChannel.OOC,
                ChatChannel.OOC, false);
        drawn.add(0, line(-2));
        List<ChatLine> second = ClientChatChannelViews.visibleLines(
                drawn, ChatChannel.OOC);
        assertEquals(2, second.size());
        assertEquals(-2, second.get(0).getChatLineID());
    }

    @Test
    public void unreadFlagsFollowTheSelectedChannel() {
        ClientChatChannelViews.record(-1, ChatChannel.PARTY,
                ChatChannel.ALL, true);
        ClientChatChannelViews.record(-2, ChatChannel.ALL,
                ChatChannel.ALL, true);
        ClientChatChannelViews.record(-3, ChatChannel.PARTY,
                ChatChannel.ALL, false);
        assertTrue(ClientChatChannelViews.hasUnread(ChatChannel.PARTY));
        assertEquals(2, ClientChatChannelViews.unreadCount(ChatChannel.PARTY));
        assertTrue(ClientChatChannelViews.hasUnreadMention(ChatChannel.PARTY));
        assertFalse(ClientChatChannelViews.hasUnread(ChatChannel.ALL));
        assertEquals(0, ClientChatChannelViews.unreadCount(ChatChannel.ALL));
        ClientChatChannelViews.markViewed(ChatChannel.PARTY);
        assertFalse(ClientChatChannelViews.hasUnread(ChatChannel.PARTY));
        assertEquals(0, ClientChatChannelViews.unreadCount(ChatChannel.PARTY));
        assertFalse(ClientChatChannelViews.hasUnreadMention(
                ChatChannel.PARTY));
        for (int index = 0; index < 150; index++) {
            ClientChatChannelViews.record(-10 - index, ChatChannel.OOC,
                    ChatChannel.ALL, false);
        }
        assertEquals(ClientChatChannelViews.MAX_UNREAD + 1,
                ClientChatChannelViews.unreadCount(ChatChannel.OOC));
    }

    @Test
    public void scrollIsPerChannelClampedAndStableUnderInsertions() {
        ClientChatChannelViews.scroll(ChatChannel.ALL, 7, 30, 10);
        assertEquals(7, ClientChatChannelViews.getScroll(
                ChatChannel.ALL, 30, 10));
        assertEquals(0, ClientChatChannelViews.getScroll(
                ChatChannel.OOC, 30, 10));
        ClientChatChannelViews.scroll(ChatChannel.ALL, 100, 30, 10);
        assertEquals(20, ClientChatChannelViews.getScroll(
                ChatChannel.ALL, 30, 10));
        ClientChatChannelViews.onLinesAdded(ChatChannel.ALL, 2);
        assertEquals(22, ClientChatChannelViews.getScroll(
                ChatChannel.ALL, 32, 10));
        // A view that is not scrolled stays pinned to the newest line.
        ClientChatChannelViews.onLinesAdded(ChatChannel.OOC, 2);
        assertEquals(0, ClientChatChannelViews.getScroll(
                ChatChannel.OOC, 32, 10));
        ClientChatChannelViews.scroll(ChatChannel.ALL, -100, 32, 10);
        assertEquals(0, ClientChatChannelViews.getScroll(
                ChatChannel.ALL, 32, 10));
        assertEquals(0, ClientChatChannelViews.getScroll(null, 32, 10));
        ClientChatChannelViews.scroll(ChatChannel.ALL, 5, 32, 10);
        ClientChatChannelViews.resetScroll();
        assertEquals(0, ClientChatChannelViews.getScroll(
                ChatChannel.ALL, 32, 10));
    }

    @Test
    public void trackingIsBounded() {
        for (int index = 0; index < 400; index++) {
            ClientChatChannelViews.record(-index - 1, ChatChannel.ALL,
                    ChatChannel.ALL, false);
        }
        assertEquals(256, ClientChatChannelViews.trackedLineCount());
        assertNull(ClientChatChannelViews.channelOf(-1));
        assertEquals(ChatChannel.ALL, ClientChatChannelViews.channelOf(-400));
    }
}
