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

    private static ChatFrame frameOver(List<ChatLine> lines,
                                             int dividerLineIndex) {
        ChatFrame frame = ChatFrame.feed();
        frame.lines = lines;
        frame.dividerLineIndex = dividerLineIndex;
        return frame;
    }

    @Test
    public void arrivingMessagesDoNotMoveThePageBeingRead() {
        ConversationPage page = ConversationPage.of(ChatChannel.GLOBAL);
        List<ChatLine> lines = history(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        scroll(page, 4, lines.size(), 3.0D);
        // The hold is taken on the line at the view's edge: row 4, which
        // is the line with id 6.
        ClientChatChannelViews.holdPosition(page, frameOver(lines, -1));
        assertEquals(4.0D,
                ClientChatChannelViews.getScroll(page, lines.size(), 3.0D),
                0.0001D);

        // Two messages arrive underneath. The same line is now two rows
        // further from the newest, and the view follows it there.
        List<ChatLine> grown = history(12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        ClientChatChannelViews.holdPosition(page, frameOver(grown, -1));
        assertEquals(6.0D,
                ClientChatChannelViews.getScroll(page, grown.size(), 3.0D),
                0.0001D);
    }

    @Test
    public void aViewAtTheNewestMessageIsPushedAlong() {
        ConversationPage page = ConversationPage.of(ChatChannel.OOC);
        List<ChatLine> lines = history(3, 2, 1);
        ClientChatChannelViews.holdPosition(page, frameOver(lines, -1));
        List<ChatLine> grown = history(5, 4, 3, 2, 1);
        ClientChatChannelViews.holdPosition(page, frameOver(grown, -1));
        assertEquals("resting on the newest line, nothing is held",
                0.0D,
                ClientChatChannelViews.getScroll(page, grown.size(), 3.0D),
                0.0001D);
    }

    @Test
    public void anUnreadDividerOpeningARowDoesNotMoveThePage() {
        ConversationPage page = ConversationPage.of(ChatChannel.OOC);
        List<ChatLine> lines = history(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        scroll(page, 5, lines.size(), 3.0D);
        ClientChatChannelViews.holdPosition(page, frameOver(lines, -1));
        assertEquals(5.0D,
                ClientChatChannelViews.getScroll(page, lines.size(), 3.0D),
                0.0001D);
        // A divider opens above the line at index 1, taking a row of its
        // own; everything older stands one row higher, the held line
        // among them.
        ClientChatChannelViews.holdPosition(page, frameOver(lines, 1));
        assertEquals(6.0D,
                ClientChatChannelViews.getScroll(page, lines.size() + 1,
                        3.0D),
                0.0001D);
    }

    /**
     * With the view's edge halfway through the unread divider's row, the
     * hold is taken on the message above the divider, in pixels: the
     * divider moving onto a newly arrived message takes its row away
     * from under the page, and the page stays exactly where it was.
     */
    @Test
    public void anUnreadDividerMovingAwayLeavesThePageWhereItWas() {
        ConversationPage page = ConversationPage.of(ChatChannel.OOC);
        List<ChatLine> lines = history(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        // The divider stands over the line at index 2; its row is row 3.
        ChatFrame frame = frameOver(lines, 2);
        ClientChatChannelViews.scrollTo(page, 3.5D, lines.size() + 1, 3.0D);
        ClientChatChannelViews.holdPosition(page, frame);
        // The line above the divider (index 3, id 7) stands this far
        // above the view's edge: the upper half of the divider's row,
        // which is a gap, the divider's line and a gap.
        double before = frame.rows.top(LostTalesChatOverlayRenderer
                .rowOfLine(3, 2)) - frame.rows.offsetOf(3.5D);
        assertEquals((2 * ChatStackRows.SPACER_HEIGHT
                        + ChatStackRows.LINE_HEIGHT) / 2.0D, before, 1.0E-9D);

        // A message arrives and the divider moves onto it; the line with
        // id 7 is at index 4 now.
        List<ChatLine> grown = history(11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        ChatFrame after = frameOver(grown, 0);
        ClientChatChannelViews.holdPosition(page, after);
        double scroll = ClientChatChannelViews.getScroll(page,
                grown.size() + 1, 3.0D);
        after.resolveRows();
        assertEquals(before, after.rows.top(LostTalesChatOverlayRenderer
                .rowOfLine(4, 0)) - after.rows.offsetOf(scroll), 1.0E-9D);
    }

    @Test
    public void reWrappingTheHistoryDoesNotMoveThePage() {
        ConversationPage page = ConversationPage.of(ChatChannel.GLOBAL);
        List<ChatLine> lines = history(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        scroll(page, 4, lines.size(), 3.0D);
        ClientChatChannelViews.holdPosition(page, frameOver(lines, -1));
        // The window is made narrower: every message below the held one
        // wraps onto a second row, so the list is longer and the held
        // line has moved down it.
        List<ChatLine> wrapped = history(10, 10, 9, 9, 8, 8, 7, 7, 6, 5, 4,
                3, 2, 1);
        ClientChatChannelViews.holdPosition(page, frameOver(wrapped, -1));
        assertEquals("the held line is at index 8 now", 8.0D,
                ClientChatChannelViews.getScroll(page, wrapped.size(), 3.0D),
                0.0001D);
    }

    /**
     * The window is made taller under a view scrolled to its oldest
     * line: the ceiling comes down, and the view comes to rest on it
     * rather than being held above it and clamped back every frame.
     */
    @Test
    public void aTallerWindowLowersTheCeilingWithoutAJitter() {
        ConversationPage page = ConversationPage.of(ChatChannel.GLOBAL);
        List<ChatLine> lines = history(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        ChatFrame frame = frameOver(lines, -1);
        // Three lines of room: the ceiling is seven rows up.
        frame.room = 3 * LostTalesChatOverlayRenderer.LINE_HEIGHT;
        scroll(page, 7, lines.size(), 3.0D);
        ClientChatChannelViews.holdPosition(page, frame);
        assertEquals(7.0D,
                ClientChatChannelViews.getScroll(page, lines.size(), 3.0D),
                0.0001D);
        // Five lines of room: the ceiling is five rows up, and the held
        // line stands above it.
        frame.room = 5 * LostTalesChatOverlayRenderer.LINE_HEIGHT;
        ClientChatChannelViews.holdPosition(page, frame);
        assertEquals(5.0D,
                ClientChatChannelViews.getScroll(page, lines.size(), 5.0D),
                0.0001D);
        // The next frame stays there instead of climbing back.
        ClientChatChannelViews.holdPosition(page, frame);
        assertEquals(5.0D,
                ClientChatChannelViews.getScroll(page, lines.size(), 5.0D),
                0.0001D);
        ClientChatChannelViews.holdPosition(page, frame);
        assertEquals(5.0D,
                ClientChatChannelViews.getScroll(page, lines.size(), 5.0D),
                0.0001D);
    }

    @Test
    public void scrollingAgainTakesAFreshHold() {
        ConversationPage page = ConversationPage.of(ChatChannel.GLOBAL);
        List<ChatLine> lines = history(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        scroll(page, 4, lines.size(), 3.0D);
        ClientChatChannelViews.holdPosition(page, frameOver(lines, -1));
        // The player turns the wheel: the view is theirs to move, and
        // the hold it had is replaced rather than undoing the turn.
        scroll(page, 2, lines.size(), 3.0D);
        ClientChatChannelViews.holdPosition(page, frameOver(lines, -1));
        assertEquals(6.0D,
                ClientChatChannelViews.getScroll(page, lines.size(), 3.0D),
                0.0001D);
    }

    @Test
    public void messagesArrivingWhileScrolledBackAreCounted() {
        ConversationPage page = ConversationPage.of(ChatChannel.GLOBAL);
        List<ChatLine> lines = history(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        assertEquals(0, ClientChatChannelViews.waitingBelow(page));

        // Resting on the newest message, an arrival is not waiting: it
        // is simply the next line, and the stack is pushed along.
        ClientChatChannelViews.record(11, page, page, false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        assertEquals(0, ClientChatChannelViews.waitingBelow(page));

        scroll(page, 4, lines.size(), 3.0D);
        ClientChatChannelViews.record(12, page, page, false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        ClientChatChannelViews.record(13, page, page, false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        assertEquals("two arrived behind the page being read",
                2, ClientChatChannelViews.waitingBelow(page));
        // The first of them opened the divider that marks the place.
        assertNotNull(ClientChatChannelViews.unreadDividerLine(page));
        assertEquals(12,
                ClientChatChannelViews.unreadDividerLine(page).intValue());

        // Jumping to the present is the whole of catching up.
        ClientChatChannelViews.scrollHome(page);
        assertEquals(0, ClientChatChannelViews.waitingBelow(page));
    }

    @Test
    public void scrollingBackDownClearsWhatWasWaiting() {
        ConversationPage page = ConversationPage.of(ChatChannel.GLOBAL);
        List<ChatLine> lines = history(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        scroll(page, 4, lines.size(), 3.0D);
        ClientChatChannelViews.record(11, page, page, false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        assertEquals(1, ClientChatChannelViews.waitingBelow(page));
        scroll(page, -100, lines.size(), 3.0D);
        // The hold is let go the frame the view reaches the newest line.
        ClientChatChannelViews.holdPosition(page, frameOver(lines, -1));
        assertEquals(0, ClientChatChannelViews.waitingBelow(page));
    }

    @Test
    public void aHeldMessageTrimmedAwayLeavesTheViewWhereItIs() {
        ConversationPage page = ConversationPage.of(ChatChannel.GLOBAL);
        List<ChatLine> lines = history(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        scroll(page, 8, lines.size(), 3.0D);
        ClientChatChannelViews.holdPosition(page, frameOver(lines, -1));
        // The history trims past the held line; the view keeps the
        // offset it had and takes hold of whatever is at its edge now.
        List<ChatLine> trimmed = history(10, 9, 8, 7, 6, 5);
        ClientChatChannelViews.holdPosition(page, frameOver(trimmed, -1));
        assertEquals(3.0D,
                ClientChatChannelViews.getScroll(page, trimmed.size(), 3.0D),
                0.0001D);
    }

    /** Scrolls a view by whole lines the way the wheel does: from where it stands. */
    private static void scroll(ConversationPage page, int lines, int totalLines, double roomLines) {
        double current = ClientChatChannelViews.getScroll(page, totalLines, roomLines);
        ClientChatChannelViews.scrollTo(page, current + lines, totalLines, roomLines);
    }
}
