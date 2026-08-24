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

        assertSame(drawn, ClientChatChannelViews.visibleLines(drawn, (ChatChannel)null));
        // Vanilla lines (achievements, commands) belong to the console only.
        List<ChatLine> party = ClientChatChannelViews.visibleLines(
                drawn, ChatChannel.PARTY);
        assertEquals(1, party.size());
        assertEquals(-10, party.get(0).getChatLineID());
        List<ChatLine> all = ClientChatChannelViews.visibleLines(
                drawn, ChatChannel.ALL);
        assertEquals(1, all.size());
        assertEquals(-11, all.get(0).getChatLineID());
        List<ChatLine> console = ClientChatChannelViews.visibleLines(
                drawn, ChatChannel.CONSOLE);
        assertEquals(1, console.size());
        assertEquals(0, console.get(0).getChatLineID());
        // Same list, same head, same view: the cached instance is reused.
        assertSame(console, ClientChatChannelViews.visibleLines(
                drawn, ChatChannel.CONSOLE));
        assertNull(ClientChatChannelViews.channelOf(0));
        assertEquals(ChatChannel.PARTY, ClientChatChannelViews.channelOf(-10));
    }

    @Test
    public void filtersCombineChannelsAndCarryUntrackedLinesWithTheConsole() {
        ClientChatChannelViews.record(-10, ChatChannel.PARTY,
                ChatChannel.ALL, false);
        ClientChatChannelViews.record(-11, ChatChannel.ALL,
                ChatChannel.ALL, false);
        ClientChatChannelViews.record(-12, ChatChannel.OOC,
                ChatChannel.ALL, false);
        List<ChatLine> drawn = new ArrayList<ChatLine>();
        drawn.add(line(-12));
        drawn.add(line(-11));
        drawn.add(line(0));
        drawn.add(line(-10));

        ChatLineFilter window = ChatLineFilter.ofChannels(java.util.Arrays.asList(
                ChatChannel.ALL, ChatChannel.PARTY));
        List<ChatLine> lines = ClientChatChannelViews.visibleLines(drawn, window);
        assertEquals(2, lines.size());
        assertEquals(-11, lines.get(0).getChatLineID());
        assertEquals(-10, lines.get(1).getChatLineID());
        assertSame(lines, ClientChatChannelViews.visibleLines(drawn,
                ChatLineFilter.ofChannels(java.util.Arrays.asList(
                        ChatChannel.PARTY, ChatChannel.ALL))));
        // The console channel brings the untracked line with it.
        List<ChatLine> withConsole = ClientChatChannelViews.visibleLines(
                drawn, ChatLineFilter.ofChannels(java.util.Arrays.asList(
                        ChatChannel.OOC, ChatChannel.CONSOLE)));
        assertEquals(2, withConsole.size());
        assertEquals(-12, withConsole.get(0).getChatLineID());
        assertEquals(0, withConsole.get(1).getChatLineID());
        assertTrue(ClientChatChannelViews.visibleLines(drawn,
                ChatLineFilter.ofChannels(java.util.Collections.<ChatChannel>emptyList()))
                .isEmpty());
        assertTrue(ChatLineFilter.of(ChatChannel.CONSOLE).accepts(null));
        assertFalse(ChatLineFilter.of(ChatChannel.ALL).accepts(null));
        // Separate filters keep separate cached results.
        assertEquals(1, ClientChatChannelViews.visibleLines(drawn,
                ChatChannel.OOC).size());
        assertEquals(2, ClientChatChannelViews.visibleLines(drawn, window)
                .size());
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
    public void unreadCountersFollowTheSelectedChannel() {
        ClientChatChannelViews.record(-1, ChatChannel.PARTY,
                ChatChannel.ALL, true);
        ClientChatChannelViews.record(-2, ChatChannel.ALL,
                ChatChannel.ALL, true);
        ClientChatChannelViews.record(-3, ChatChannel.PARTY,
                ChatChannel.ALL, false);
        assertTrue(ClientChatChannelViews.hasUnread(ChatChannel.PARTY));
        // A ping is counted once, as a ping, never also as "other".
        assertEquals(1, ClientChatChannelViews.unreadPingCount(
                ChatChannel.PARTY));
        assertEquals(1, ClientChatChannelViews.unreadOtherCount(
                ChatChannel.PARTY));
        assertEquals(2, ClientChatChannelViews.unreadCount(ChatChannel.PARTY));
        assertTrue(ClientChatChannelViews.hasUnreadMention(ChatChannel.PARTY));
        // Lines arriving in the selected channel are read on arrival.
        assertFalse(ClientChatChannelViews.hasUnread(ChatChannel.ALL));
        assertEquals(0, ClientChatChannelViews.unreadPingCount(
                ChatChannel.ALL));
        assertEquals(0, ClientChatChannelViews.unreadOtherCount(
                ChatChannel.ALL));
        ClientChatChannelViews.markViewed(ChatChannel.PARTY);
        assertFalse(ClientChatChannelViews.hasUnread(ChatChannel.PARTY));
        assertEquals(0, ClientChatChannelViews.unreadPingCount(
                ChatChannel.PARTY));
        assertEquals(0, ClientChatChannelViews.unreadOtherCount(
                ChatChannel.PARTY));
        assertFalse(ClientChatChannelViews.hasUnreadMention(
                ChatChannel.PARTY));
        for (int index = 0; index < 150; index++) {
            ClientChatChannelViews.record(-10 - index, ChatChannel.OOC,
                    ChatChannel.ALL, index % 2 == 0);
        }
        assertEquals(75, ClientChatChannelViews.unreadPingCount(
                ChatChannel.OOC));
        assertEquals(75, ClientChatChannelViews.unreadOtherCount(
                ChatChannel.OOC));
        for (int index = 0; index < 150; index++) {
            ClientChatChannelViews.record(-200 - index, ChatChannel.OOC,
                    ChatChannel.ALL, false);
        }
        assertEquals(ClientChatChannelViews.MAX_UNREAD + 1,
                ClientChatChannelViews.unreadOtherCount(ChatChannel.OOC));
        // The total shares the cap rather than summing past it.
        assertEquals(ClientChatChannelViews.MAX_UNREAD + 1,
                ClientChatChannelViews.unreadCount(ChatChannel.OOC));
        assertEquals("", ClientChatChannelViews.counterText(0));
        assertEquals("[3]", ClientChatChannelViews.counterText(3));
        assertEquals("[99]", ClientChatChannelViews.counterText(99));
        assertEquals("[99+]", ClientChatChannelViews.counterText(100));
    }

    /**
     * Unread state does not care whether a channel has a tab: a closed
     * channel keeps counting, and restoring it (which selects it and
     * marks it viewed) clears the count the same way a tab switch does.
     */
    @Test
    public void closedChannelsAccumulateUnreadUntilRestoredAndViewed() {
        ChatWindowLayout.reset();
        try {
            assertTrue(ChatWindowLayout.close(ChatChannel.PARTY));
            ClientChatChannelViews.record(-1, ChatChannel.PARTY,
                    ChatChannel.ALL, false);
            ClientChatChannelViews.record(-2, ChatChannel.PARTY,
                    ChatChannel.ALL, true);
            ClientChatChannelViews.record(-3, ChatChannel.PARTY,
                    ChatChannel.ALL, false);
            assertEquals(3, ClientChatChannelViews.unreadCount(
                    ChatChannel.PARTY));
            assertEquals("[3]", ClientChatChannelViews.counterText(
                    ClientChatChannelViews.unreadCount(ChatChannel.PARTY)));
            // Closing and restoring touch no counter on their own.
            assertTrue(ChatWindowLayout.restore(ChatChannel.PARTY));
            assertEquals(3, ClientChatChannelViews.unreadCount(
                    ChatChannel.PARTY));
            assertTrue(ChatWindowLayout.close(ChatChannel.PARTY));
            assertEquals(3, ClientChatChannelViews.unreadCount(
                    ChatChannel.PARTY));
            ClientChatChannelViews.markViewed(ChatChannel.PARTY);
            assertEquals(0, ClientChatChannelViews.unreadCount(
                    ChatChannel.PARTY));
        } finally {
            ChatWindowLayout.reset();
        }
    }

    @Test
    public void scrollIsPerChannelClampedAndStableUnderInsertions() {
        ClientChatChannelViews.scroll(ChatChannel.ALL, 7, 30, 10.0D);
        assertEquals(7.0D, ClientChatChannelViews.getScroll(
                ChatChannel.ALL, 30, 10.0D), 0.0D);
        assertEquals(0.0D, ClientChatChannelViews.getScroll(
                ChatChannel.OOC, 30, 10.0D), 0.0D);
        ClientChatChannelViews.scroll(ChatChannel.ALL, 100, 30, 10.0D);
        assertEquals(20.0D, ClientChatChannelViews.getScroll(
                ChatChannel.ALL, 30, 10.0D), 0.0D);
        ClientChatChannelViews.onLinesAdded(ChatChannel.ALL, 2);
        assertEquals(22.0D, ClientChatChannelViews.getScroll(
                ChatChannel.ALL, 32, 10.0D), 0.0D);
        // A view that is not scrolled stays pinned to the newest line.
        ClientChatChannelViews.onLinesAdded(ChatChannel.OOC, 2);
        assertEquals(0.0D, ClientChatChannelViews.getScroll(
                ChatChannel.OOC, 32, 10.0D), 0.0D);
        ClientChatChannelViews.scroll(ChatChannel.ALL, -100, 32, 10.0D);
        assertEquals(0.0D, ClientChatChannelViews.getScroll(
                ChatChannel.ALL, 32, 10.0D), 0.0D);
        assertEquals(0.0D, ClientChatChannelViews.getScroll(
                (ChatTab)null, 32, 10.0D), 0.0D);
        ClientChatChannelViews.scroll(ChatChannel.ALL, 5, 32, 10.0D);
        ClientChatChannelViews.resetScroll();
        assertEquals(0.0D, ClientChatChannelViews.getScroll(
                ChatChannel.ALL, 32, 10.0D), 0.0D);
    }

    /**
     * A window dragged to an odd height still comes to rest on whole
     * messages: scrolled all the way up, the oldest line sits exactly on
     * the window's top edge rather than half out of it.
     */
    @Test
    public void theFarEndOfAScrollLeavesTheOldestLineWhole() {
        ClientChatChannelViews.scroll(ChatChannel.ALL, 100, 30, 12.37D);
        assertEquals(30.0D - 12.37D, ClientChatChannelViews.getScroll(
                ChatChannel.ALL, 30, 12.37D), 1.0E-9D);
        ClientChatChannelViews.resetScroll();
    }

    /** The bound follows the history's capacity, so every kept line has its tab. */
    @Test
    public void trackingIsBoundedByTheHistoryCapacity() {
        int bound = ClientChatChannelViews.maxTrackedLines();
        assertTrue(bound >= LostTalesChatHistoryHooks.capacity());
        int recorded = bound + 150;
        for (int index = 0; index < recorded; index++) {
            ClientChatChannelViews.record(-index - 1, ChatChannel.ALL,
                    ChatChannel.ALL, false);
        }
        assertEquals(bound, ClientChatChannelViews.trackedLineCount());
        assertNull(ClientChatChannelViews.channelOf(-1));
        assertEquals(ChatChannel.ALL,
                ClientChatChannelViews.channelOf(-recorded));
    }
}
