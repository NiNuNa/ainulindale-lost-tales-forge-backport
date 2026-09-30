package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
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
        ClientChatReadMarks.initialize(null, null);
        ClientChatSession.resume("");
    }

    /**
     * A line this player said is read wherever it went: once the server
     * names it, the view's read mark stands at it, so the next join's
     * replay of it is filed and not counted, while a later line is.
     */
    @Test
    public void aPlayersOwnLineIsReadOnceTheServerNamesIt() {
        ClientChatSession.resume("server:play.example");
        ClientChatReadMarks.markArrival("server:play.example", 4000L);
        ChatTab party = ChatTab.of(ChatChannel.PARTY);
        ChatTab global = ChatTab.of(ChatChannel.GLOBAL);
        ClientChatChannelViews.noteOwnLine(party, -7, 4200L);
        ClientChatChannelViews.record(-8, party, global, false, 4200L, 1000L, true);
        assertFalse(ClientChatChannelViews.hasUnread(ChatTab.of(ChatChannel.PARTY)));
        ClientChatChannelViews.record(-9, party, global, false, 4201L, 1000L, true);
        assertEquals(1, ClientChatChannelViews.unreadCount(ChatChannel.PARTY));
    }

    /**
     * Arriving somewhere for the first time counts nothing unread: the
     * replay up to this player's own arrival was said before they were
     * there, so it is filed and read, and so is their arrival itself; the
     * count begins with the next line said.
     */
    @Test
    public void aFirstVisitCountsNoneOfTheReplayUnread() {
        ClientChatSession.resume("server:new.example");
        ClientChatChannelViews.noteArrival(700L);
        assertEquals(700L, ClientChatReadMarks.arrival("server:new.example"));
        ChatTab global = ChatTab.of(ChatChannel.GLOBAL);
        ChatTab console = ChatTab.of(ChatChannel.CLIENT_CONSOLE);
        for (int index = 0; index < 200; index++) {
            ClientChatChannelViews.record(-100 - index, global, console,
                    index % 10 == 0, 500L + index, 1000L, true);
        }
        // Their own join line.
        ClientChatChannelViews.record(-399, global, console, false, 700L,
                1000L, true);
        assertFalse(ClientChatChannelViews.hasUnread(ChatTab.of(ChatChannel.GLOBAL)));
        assertEquals(0, ClientChatChannelViews.unreadCount(ChatChannel.GLOBAL));
        // Said after they arrived, in a tab they are not looking at.
        ClientChatChannelViews.record(-400, global, console, false, 900L,
                1000L, false);
        assertEquals(1, ClientChatChannelViews.unreadCount(ChatChannel.GLOBAL));
    }

    /**
     * Coming back is not a first visit: the replay is measured against
     * the mark this player left, so what was said while they were away
     * still counts.
     */
    @Test
    public void aReturnVisitStillCountsWhatWasSaidWhileAway() {
        ClientChatSession.resume("server:known.example");
        ClientChatReadMarks.markArrival("server:known.example", 100L);
        ChatTab global = ChatTab.of(ChatChannel.GLOBAL);
        ChatTab console = ChatTab.of(ChatChannel.CLIENT_CONSOLE);
        ClientChatReadMarks.markRead("server:known.example", global, 500L);
        ClientChatChannelViews.record(-1, global, console, false, 500L,
                1000L, true);
        ClientChatChannelViews.record(-2, global, console, false, 501L,
                1000L, true);
        ClientChatChannelViews.record(-3, global, console, false, 502L,
                1000L, true);
        assertEquals(2, ClientChatChannelViews.unreadCount(ChatChannel.GLOBAL));
    }

    /**
     * A view this player never opened has no mark of its own, and still
     * counts what was said in it after they first came to the server:
     * only what was said before their first arrival, and their own
     * arrival this time, are never news. A later arrival does not move
     * where they first came.
     */
    @Test
    public void aViewNeverReadCountsWhatWasSaidSinceTheFirstArrival() {
        ClientChatSession.resume("server:return.example");
        ClientChatReadMarks.markArrival("server:return.example", 400L);
        ClientChatChannelViews.noteArrival(900L);
        assertEquals(400L,
                ClientChatReadMarks.arrival("server:return.example"));
        ChatTab proximity = ChatTab.of(ChatChannel.PROXIMITY);
        ChatTab global = ChatTab.of(ChatChannel.GLOBAL);
        ClientChatChannelViews.record(-1, proximity, global, false, 300L,
                1000L, true);
        ClientChatChannelViews.record(-2, proximity, global, false, 500L,
                1000L, true);
        ClientChatChannelViews.record(-3, proximity, global, true, 600L,
                1000L, true);
        ClientChatChannelViews.record(-4, global, global, false, 900L,
                1000L, true);
        assertEquals(2,
                ClientChatChannelViews.unreadCount(ChatChannel.PROXIMITY));
        assertTrue((ClientChatChannelViews.unreadPingCount(
                ChatTab.of(ChatChannel.PROXIMITY)) > 0));
        assertEquals(Integer.valueOf(-2),
                ClientChatChannelViews.unreadDividerLine(proximity));
    }

    /** A line said while the player is here, in a tab not in front, is unread. */
    @Test
    public void aLiveLineInAnotherTabIsUnread() {
        ClientChatSession.resume("server:live.example");
        ClientChatChannelViews.noteArrival(100L);
        ChatTab proximity = ChatTab.of(ChatChannel.PROXIMITY);
        ChatTab global = ChatTab.of(ChatChannel.GLOBAL);
        ClientChatChannelViews.record(-1, proximity, global, false, 150L,
                1000L, false);
        assertEquals(1,
                ClientChatChannelViews.unreadCount(ChatChannel.PROXIMITY));
        // In the tab in front, at the newest line, it is read as it comes.
        ClientChatChannelViews.record(-2, global, global, false, 151L,
                1000L, false);
        assertEquals(0, ClientChatChannelViews.unreadCount(ChatChannel.GLOBAL));
    }

    private static ChatLine line(int chatLineId) {
        return new ChatLine(0, new ChatComponentText("x"), chatLineId);
    }

    @Test
    public void viewsFilterByRecordedChannelAndKeepVanillaLines() {
        ClientChatChannelViews.record(-10, ChatTab.of(ChatChannel.PARTY),
                ChatTab.of(ChatChannel.GLOBAL), false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        ClientChatChannelViews.record(-11, ChatTab.of(ChatChannel.GLOBAL),
                ChatTab.of(ChatChannel.GLOBAL), false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        List<ChatLine> drawn = new ArrayList<ChatLine>();
        drawn.add(line(-11));
        drawn.add(line(0));
        drawn.add(line(-10));

        // Vanilla lines (achievements, commands) belong to the console only.
        List<ChatLine> party = ClientChatChannelViews.visibleLines(
                drawn, ChatLineFilter.of(ChatTab.of(ChatChannel.PARTY)));
        assertEquals(1, party.size());
        assertEquals(-10, party.get(0).getChatLineID());
        List<ChatLine> all = ClientChatChannelViews.visibleLines(
                drawn, ChatLineFilter.of(ChatTab.of(ChatChannel.GLOBAL)));
        assertEquals(1, all.size());
        assertEquals(-11, all.get(0).getChatLineID());
        List<ChatLine> console = ClientChatChannelViews.visibleLines(
                drawn, ChatLineFilter.of(ChatTab.of(ChatChannel.CLIENT_CONSOLE)));
        assertEquals(1, console.size());
        assertEquals(0, console.get(0).getChatLineID());
        // Same list, same head, same view: the cached instance is reused.
        assertSame(console, ClientChatChannelViews.visibleLines(
                drawn, ChatLineFilter.of(ChatTab.of(ChatChannel.CLIENT_CONSOLE))));
        assertNull(channelOf(0));
        assertEquals(ChatChannel.PARTY, channelOf(-10));
    }

    @Test
    public void filtersCombineChannelsAndCarryUntrackedLinesWithTheConsole() {
        ClientChatChannelViews.record(-10, ChatTab.of(ChatChannel.PARTY),
                ChatTab.of(ChatChannel.GLOBAL), false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        ClientChatChannelViews.record(-11, ChatTab.of(ChatChannel.GLOBAL),
                ChatTab.of(ChatChannel.GLOBAL), false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        ClientChatChannelViews.record(-12, ChatTab.of(ChatChannel.OOC),
                ChatTab.of(ChatChannel.GLOBAL), false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        List<ChatLine> drawn = new ArrayList<ChatLine>();
        drawn.add(line(-12));
        drawn.add(line(-11));
        drawn.add(line(0));
        drawn.add(line(-10));

        ChatLineFilter window = ChatLineFilter.of(java.util.Arrays.asList(
                ChatTab.of(ChatChannel.GLOBAL), ChatTab.of(ChatChannel.PARTY)), java.util.Collections.<ChatTab>emptySet());
        List<ChatLine> lines = ClientChatChannelViews.visibleLines(drawn, window);
        assertEquals(2, lines.size());
        assertEquals(-11, lines.get(0).getChatLineID());
        assertEquals(-10, lines.get(1).getChatLineID());
        assertSame(lines, ClientChatChannelViews.visibleLines(drawn,
                ChatLineFilter.of(java.util.Arrays.asList(
                        ChatTab.of(ChatChannel.PARTY), ChatTab.of(ChatChannel.GLOBAL)), java.util.Collections.<ChatTab>emptySet())));
        // The console channel brings the untracked line with it.
        List<ChatLine> withConsole = ClientChatChannelViews.visibleLines(
                drawn, ChatLineFilter.of(java.util.Arrays.asList(
                        ChatTab.of(ChatChannel.OOC), ChatTab.of(ChatChannel.CLIENT_CONSOLE)), java.util.Collections.<ChatTab>emptySet()));
        assertEquals(2, withConsole.size());
        assertEquals(-12, withConsole.get(0).getChatLineID());
        assertEquals(0, withConsole.get(1).getChatLineID());
        assertTrue(ClientChatChannelViews.visibleLines(drawn,
                ChatLineFilter.of(java.util.Collections.<ChatTab>emptyList(),
                        java.util.Collections.<ChatTab>emptySet()))
                .isEmpty());
        assertTrue(ChatLineFilter.of(ChatTab.of(ChatChannel.CLIENT_CONSOLE)).accepts(null));
        assertFalse(ChatLineFilter.of(ChatTab.of(ChatChannel.GLOBAL)).accepts(null));
        // Separate filters keep separate cached results.
        assertEquals(1, ClientChatChannelViews.visibleLines(drawn,
                ChatLineFilter.of(ChatTab.of(ChatChannel.OOC))).size());
        assertEquals(2, ClientChatChannelViews.visibleLines(drawn, window)
                .size());
    }

    @Test
    public void cacheInvalidatesWhenTheHeadOfHistoryChanges() {
        ClientChatChannelViews.record(-1, ChatTab.of(ChatChannel.OOC),
                ChatTab.of(ChatChannel.OOC), false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        List<ChatLine> drawn = new ArrayList<ChatLine>();
        drawn.add(line(-1));
        List<ChatLine> first = ClientChatChannelViews.visibleLines(
                drawn, ChatLineFilter.of(ChatTab.of(ChatChannel.OOC)));
        assertEquals(1, first.size());
        ClientChatChannelViews.record(-2, ChatTab.of(ChatChannel.OOC),
                ChatTab.of(ChatChannel.OOC), false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        drawn.add(0, line(-2));
        List<ChatLine> second = ClientChatChannelViews.visibleLines(
                drawn, ChatLineFilter.of(ChatTab.of(ChatChannel.OOC)));
        assertEquals(2, second.size());
        assertEquals(-2, second.get(0).getChatLineID());
    }

    @Test
    public void unreadCountersFollowTheSelectedChannel() {
        ClientChatChannelViews.record(-1, ChatTab.of(ChatChannel.PARTY),
                ChatTab.of(ChatChannel.GLOBAL), true, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        ClientChatChannelViews.record(-2, ChatTab.of(ChatChannel.GLOBAL),
                ChatTab.of(ChatChannel.GLOBAL), true, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        ClientChatChannelViews.record(-3, ChatTab.of(ChatChannel.PARTY),
                ChatTab.of(ChatChannel.GLOBAL), false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        assertTrue(ClientChatChannelViews.hasUnread(ChatTab.of(ChatChannel.PARTY)));
        // A ping is counted once, as a ping, never also as "other".
        assertEquals(1, ClientChatChannelViews.unreadPingCount(
                ChatTab.of(ChatChannel.PARTY)));
        assertEquals(1, ClientChatChannelViews.unreadOtherCount(
                ChatTab.of(ChatChannel.PARTY)));
        assertEquals(2, ClientChatChannelViews.unreadCount(ChatChannel.PARTY));
        assertTrue((ClientChatChannelViews.unreadPingCount(ChatTab.of(ChatChannel.PARTY)) > 0));
        // Lines arriving in the selected channel are read on arrival.
        assertFalse(ClientChatChannelViews.hasUnread(ChatTab.of(ChatChannel.GLOBAL)));
        assertEquals(0, ClientChatChannelViews.unreadPingCount(
                ChatTab.of(ChatChannel.GLOBAL)));
        assertEquals(0, ClientChatChannelViews.unreadOtherCount(
                ChatTab.of(ChatChannel.GLOBAL)));
        ClientChatChannelViews.markViewed(ChatTab.of(ChatChannel.PARTY));
        assertFalse(ClientChatChannelViews.hasUnread(ChatTab.of(ChatChannel.PARTY)));
        assertEquals(0, ClientChatChannelViews.unreadPingCount(
                ChatTab.of(ChatChannel.PARTY)));
        assertEquals(0, ClientChatChannelViews.unreadOtherCount(
                ChatTab.of(ChatChannel.PARTY)));
        assertFalse((ClientChatChannelViews.unreadPingCount(
                ChatTab.of(ChatChannel.PARTY)) > 0));
        for (int index = 0; index < 150; index++) {
            ClientChatChannelViews.record(-10 - index, ChatTab.of(ChatChannel.OOC),
                    ChatTab.of(ChatChannel.GLOBAL), index % 2 == 0, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        }
        assertEquals(75, ClientChatChannelViews.unreadPingCount(
                ChatTab.of(ChatChannel.OOC)));
        assertEquals(75, ClientChatChannelViews.unreadOtherCount(
                ChatTab.of(ChatChannel.OOC)));
        for (int index = 0; index < 150; index++) {
            ClientChatChannelViews.record(-200 - index, ChatTab.of(ChatChannel.OOC),
                    ChatTab.of(ChatChannel.GLOBAL), false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        }
        assertEquals(ClientChatChannelViews.MAX_UNREAD + 1,
                ClientChatChannelViews.unreadOtherCount(ChatTab.of(ChatChannel.OOC)));
        // The total shares the cap rather than summing past it.
        assertEquals(ClientChatChannelViews.MAX_UNREAD + 1,
                ClientChatChannelViews.unreadCount(ChatChannel.OOC));
    }

    /**
     * Unread state does not care whether a channel has a tab: a closed
     * channel keeps counting, and restoring it (which selects it and
     * marks it viewed) clears the count the same way a tab switch does.
     */
    @Test
    public void closedChannelsAccumulateUnreadUntilRestoredAndViewed() {
        TwoWindowLayout.reset();
        try {
            assertTrue(ChatLayout.close(ChatTab.of(ChatChannel.PARTY)));
            ClientChatChannelViews.record(-1, ChatTab.of(ChatChannel.PARTY),
                    ChatTab.of(ChatChannel.GLOBAL), false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
            ClientChatChannelViews.record(-2, ChatTab.of(ChatChannel.PARTY),
                    ChatTab.of(ChatChannel.GLOBAL), true, ChatMessageIds.NONE, System.currentTimeMillis(), false);
            ClientChatChannelViews.record(-3, ChatTab.of(ChatChannel.PARTY),
                    ChatTab.of(ChatChannel.GLOBAL), false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
            assertEquals(3, ClientChatChannelViews.unreadCount(
                    ChatChannel.PARTY));
            // Closing and restoring touch no counter on their own.
            assertTrue(ChatLayoutViews.reopen(ChatChannel.PARTY));
            assertEquals(3, ClientChatChannelViews.unreadCount(
                    ChatChannel.PARTY));
            assertTrue(ChatLayout.close(ChatTab.of(ChatChannel.PARTY)));
            assertEquals(3, ClientChatChannelViews.unreadCount(
                    ChatChannel.PARTY));
            ClientChatChannelViews.markViewed(ChatTab.of(ChatChannel.PARTY));
            assertEquals(0, ClientChatChannelViews.unreadCount(
                    ChatChannel.PARTY));
        } finally {
            TwoWindowLayout.reset();
        }
    }

    /**
     * A jump to a quoted message asks for an offset outright, clamped to
     * what the window can reach like any other.
     */
    @Test
    public void scrollToPlacesTheViewAtALineAndClamps() {
        ChatTab tab = ChatTab.of(ChatChannel.GLOBAL);
        ClientChatChannelViews.scrollTo(tab, 12.0D, 30, 10.0D);
        assertEquals(12.0D, ClientChatChannelViews.getScroll(tab, 30, 10.0D),
                0.0001D);
        // Past the oldest line it comes to rest on the oldest line.
        ClientChatChannelViews.scrollTo(tab, 500.0D, 30, 10.0D);
        assertEquals(20.0D, ClientChatChannelViews.getScroll(tab, 30, 10.0D),
                0.0001D);
        // A target above the newest line is simply the newest line.
        ClientChatChannelViews.scrollTo(tab, -5.0D, 30, 10.0D);
        assertEquals(0.0D, ClientChatChannelViews.getScroll(tab, 30, 10.0D),
                0.0001D);
        ClientChatChannelViews.clear();
    }

    @Test
    public void scrollIsPerChannelAndClamped() {
        scroll(ChatTab.of(ChatChannel.GLOBAL), 7, 30, 10.0D);
        assertEquals(7.0D, ClientChatChannelViews.getScroll(
                ChatTab.of(ChatChannel.GLOBAL), 30, 10.0D), 0.0D);
        assertEquals(0.0D, ClientChatChannelViews.getScroll(
                ChatTab.of(ChatChannel.OOC), 30, 10.0D), 0.0D);
        scroll(ChatTab.of(ChatChannel.GLOBAL), 100, 30, 10.0D);
        assertEquals(20.0D, ClientChatChannelViews.getScroll(
                ChatTab.of(ChatChannel.GLOBAL), 30, 10.0D), 0.0D);
        scroll(ChatTab.of(ChatChannel.GLOBAL), -100, 32, 10.0D);
        assertEquals(0.0D, ClientChatChannelViews.getScroll(
                ChatTab.of(ChatChannel.GLOBAL), 32, 10.0D), 0.0D);
        assertEquals(0.0D, ClientChatChannelViews.getScroll(
                (ChatTab)null, 32, 10.0D), 0.0D);
        scroll(ChatTab.of(ChatChannel.GLOBAL), 5, 32, 10.0D);
        ClientChatChannelViews.clear();
        assertEquals(0.0D, ClientChatChannelViews.getScroll(
                ChatTab.of(ChatChannel.GLOBAL), 32, 10.0D), 0.0D);
    }

    /**
     * A window dragged to an odd height still comes to rest on whole
     * messages: scrolled all the way up, the oldest line sits exactly on
     * the window's top edge rather than half out of it.
     */
    @Test
    public void theFarEndOfAScrollLeavesTheOldestLineWhole() {
        scroll(ChatTab.of(ChatChannel.GLOBAL), 100, 30, 12.37D);
        assertEquals(30.0D - 12.37D, ClientChatChannelViews.getScroll(
                ChatTab.of(ChatChannel.GLOBAL), 30, 12.37D), 1.0E-9D);
        ClientChatChannelViews.clear();
    }

    /**
     * A view whose lines all fit its window does not scroll at all, so
     * no scroll can push the newest message off the baseline.
     */
    @Test
    public void aViewThatFitsItsWindowDoesNotScroll() {
        scroll(ChatTab.of(ChatChannel.GLOBAL), 100, 10, 10.0D);
        assertEquals(0.0D, ClientChatChannelViews.getScroll(
                ChatTab.of(ChatChannel.GLOBAL), 10, 10.0D), 0.0D);
        scroll(ChatTab.of(ChatChannel.GLOBAL), 100, 8, 12.5D);
        assertEquals(0.0D, ClientChatChannelViews.getScroll(
                ChatTab.of(ChatChannel.GLOBAL), 8, 12.5D), 0.0D);
        ClientChatChannelViews.clear();
    }

    /** The bound follows the history's capacity, so every kept line has its tab. */
    @Test
    public void trackingIsBoundedByTheHistoryCapacity() {
        int bound = ClientChatChannelViews.maxTrackedLines();
        assertTrue(bound >= LostTalesChatHistoryHooks.capacity());
        int recorded = bound + 150;
        for (int index = 0; index < recorded; index++) {
            ClientChatChannelViews.record(-index - 1, ChatTab.of(ChatChannel.GLOBAL),
                    ChatTab.of(ChatChannel.GLOBAL), false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        }
        // The newest `bound` lines keep their tab; every older one is let go.
        assertNull(channelOf(-1));
        assertNull(channelOf(-(recorded - bound)));
        assertEquals(ChatChannel.GLOBAL, channelOf(-(recorded - bound + 1)));
        assertEquals(ChatChannel.GLOBAL,
                channelOf(-recorded));
    }

    /** Scrolls a view by whole lines the way the wheel does: from where it stands. */
    private static void scroll(ChatTab tab, int lines, int totalLines, double roomLines) {
        double current = ClientChatChannelViews.getScroll(tab, totalLines, roomLines);
        ClientChatChannelViews.scrollTo(tab, current + lines, totalLines, roomLines);
    }

    /** The recorded line's channel, or null for vanilla and untracked lines. */
    private static ChatChannel channelOf(int chatLineId) {
        ChatTab tab = ClientChatChannelViews.tabOf(chatLineId);
        return tab == null ? null : tab.getChannel();
    }
}
