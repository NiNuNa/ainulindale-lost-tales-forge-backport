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
import static org.junit.Assert.assertNotNull;

/**
 * A view scrolled away from the newest message holds on to the message
 * it is reading rather than to a row number, so nothing that happens
 * beneath it moves the page: messages arriving, an unread divider
 * opening a row of its own, or the whole history re-wrapping when the
 * window is made narrower. A view resting on the newest message holds
 * nothing — there, arriving messages are meant to push it along.
 */
public final class ChatScrollHoldTest {

    @Before
    public void reset() {
        ClientChatChannelViews.clear();
    }

    @After
    public void cleanUp() {
        ClientChatChannelViews.clear();
    }

    /** Vanilla's list runs newest first, so index 0 is the latest line. */
    private static List<ChatLine> history(int... chatLineIds) {
        List<ChatLine> lines = new ArrayList<ChatLine>();
        for (int index = 0; index < chatLineIds.length; index++) {
            lines.add(new ChatLine(0, new ChatComponentText("x"),
                    chatLineIds[index]));
        }
        return lines;
    }

    private static ChatWindowFrame frameOver(List<ChatLine> lines,
                                             int dividerLineIndex) {
        ChatWindowFrame frame = ChatWindowFrame.feed();
        frame.lines = lines;
        frame.dividerLineIndex = dividerLineIndex;
        return frame;
    }

    @Test
    public void arrivingMessagesDoNotMoveThePageBeingRead() {
        ChatTab tab = ChatTab.of(ChatChannel.ALL);
        List<ChatLine> lines = history(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        ClientChatChannelViews.scroll(tab, 4, lines.size(), 3.0D);
        // The hold is taken on the line at the view's edge: row 4, which
        // is the line with id 6.
        ClientChatChannelViews.holdPosition(tab, frameOver(lines, -1));
        assertEquals(4.0D,
                ClientChatChannelViews.getScroll(tab, lines.size(), 3.0D),
                0.0001D);

        // Two messages arrive underneath. The same line is now two rows
        // further from the newest, and the view follows it there.
        List<ChatLine> grown = history(12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        ClientChatChannelViews.holdPosition(tab, frameOver(grown, -1));
        assertEquals(6.0D,
                ClientChatChannelViews.getScroll(tab, grown.size(), 3.0D),
                0.0001D);
    }

    @Test
    public void aViewAtTheNewestMessageIsPushedAlong() {
        ChatTab tab = ChatTab.of(ChatChannel.OOC);
        List<ChatLine> lines = history(3, 2, 1);
        ClientChatChannelViews.holdPosition(tab, frameOver(lines, -1));
        List<ChatLine> grown = history(5, 4, 3, 2, 1);
        ClientChatChannelViews.holdPosition(tab, frameOver(grown, -1));
        assertEquals("resting on the newest line, nothing is held",
                0.0D,
                ClientChatChannelViews.getScroll(tab, grown.size(), 3.0D),
                0.0001D);
    }

    @Test
    public void anUnreadDividerOpeningARowDoesNotMoveThePage() {
        ChatTab tab = ChatTab.of(ChatChannel.DISCORD);
        List<ChatLine> lines = history(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        ClientChatChannelViews.scroll(tab, 5, lines.size(), 3.0D);
        ClientChatChannelViews.holdPosition(tab, frameOver(lines, -1));
        assertEquals(5.0D,
                ClientChatChannelViews.getScroll(tab, lines.size(), 3.0D),
                0.0001D);
        // A divider opens above the line at index 1, taking a row of its
        // own; everything older stands one row higher, the held line
        // among them.
        ClientChatChannelViews.holdPosition(tab, frameOver(lines, 1));
        assertEquals(6.0D,
                ClientChatChannelViews.getScroll(tab, lines.size() + 1,
                        3.0D),
                0.0001D);
    }

    @Test
    public void reWrappingTheHistoryDoesNotMoveThePage() {
        ChatTab tab = ChatTab.of(ChatChannel.ALL);
        List<ChatLine> lines = history(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        ClientChatChannelViews.scroll(tab, 4, lines.size(), 3.0D);
        ClientChatChannelViews.holdPosition(tab, frameOver(lines, -1));
        // The window is made narrower: every message below the held one
        // wraps onto a second row, so the list is longer and the held
        // line has moved down it.
        List<ChatLine> wrapped = history(10, 10, 9, 9, 8, 8, 7, 7, 6, 5, 4,
                3, 2, 1);
        ClientChatChannelViews.holdPosition(tab, frameOver(wrapped, -1));
        assertEquals("the held line is at index 8 now", 8.0D,
                ClientChatChannelViews.getScroll(tab, wrapped.size(), 3.0D),
                0.0001D);
    }

    @Test
    public void scrollingAgainTakesAFreshHold() {
        ChatTab tab = ChatTab.of(ChatChannel.ALL);
        List<ChatLine> lines = history(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        ClientChatChannelViews.scroll(tab, 4, lines.size(), 3.0D);
        ClientChatChannelViews.holdPosition(tab, frameOver(lines, -1));
        // The player turns the wheel: the view is theirs to move, and
        // the hold it had is replaced rather than undoing the turn.
        ClientChatChannelViews.scroll(tab, 2, lines.size(), 3.0D);
        ClientChatChannelViews.holdPosition(tab, frameOver(lines, -1));
        assertEquals(6.0D,
                ClientChatChannelViews.getScroll(tab, lines.size(), 3.0D),
                0.0001D);
    }

    @Test
    public void messagesArrivingWhileScrolledBackAreCounted() {
        ChatTab tab = ChatTab.of(ChatChannel.ALL);
        List<ChatLine> lines = history(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        assertEquals(0, ClientChatChannelViews.waitingBelow(tab));

        // Resting on the newest message, an arrival is not waiting: it
        // is simply the next line, and the stack is pushed along.
        ClientChatChannelViews.record(11, tab, tab, false);
        assertEquals(0, ClientChatChannelViews.waitingBelow(tab));

        ClientChatChannelViews.scroll(tab, 4, lines.size(), 3.0D);
        ClientChatChannelViews.record(12, tab, tab, false);
        ClientChatChannelViews.record(13, tab, tab, false);
        assertEquals("two arrived behind the page being read",
                2, ClientChatChannelViews.waitingBelow(tab));
        // The first of them opened the divider that marks the place.
        assertNotNull(ClientChatChannelViews.unreadDividerLine(tab));
        assertEquals(12,
                ClientChatChannelViews.unreadDividerLine(tab).intValue());

        // Jumping to the present is the whole of catching up.
        ClientChatChannelViews.scrollHome(tab);
        assertEquals(0, ClientChatChannelViews.waitingBelow(tab));
    }

    @Test
    public void scrollingBackDownClearsWhatWasWaiting() {
        ChatTab tab = ChatTab.of(ChatChannel.ALL);
        List<ChatLine> lines = history(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        ClientChatChannelViews.scroll(tab, 4, lines.size(), 3.0D);
        ClientChatChannelViews.record(11, tab, tab, false);
        assertEquals(1, ClientChatChannelViews.waitingBelow(tab));
        ClientChatChannelViews.scroll(tab, -100, lines.size(), 3.0D);
        // The hold is let go the frame the view reaches the newest line.
        ClientChatChannelViews.holdPosition(tab, frameOver(lines, -1));
        assertEquals(0, ClientChatChannelViews.waitingBelow(tab));
    }

    @Test
    public void aHeldMessageTrimmedAwayLeavesTheViewWhereItIs() {
        ChatTab tab = ChatTab.of(ChatChannel.ALL);
        List<ChatLine> lines = history(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        ClientChatChannelViews.scroll(tab, 8, lines.size(), 3.0D);
        ClientChatChannelViews.holdPosition(tab, frameOver(lines, -1));
        // The history trims past the held line; the view keeps the
        // offset it had and takes hold of whatever is at its edge now.
        List<ChatLine> trimmed = history(10, 9, 8, 7, 6, 5);
        ClientChatChannelViews.holdPosition(tab, frameOver(trimmed, -1));
        assertEquals(3.0D,
                ClientChatChannelViews.getScroll(tab, trimmed.size(), 3.0D),
                0.0001D);
    }
}
