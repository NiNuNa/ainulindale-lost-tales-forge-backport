package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The stack's row model: the unread divider takes a row of its own, so a
 * line older than the message it divides at stands a row higher than its
 * index. Rendering and the scroll range both read this, and the bug it
 * closes was the two disagreeing — a reopened channel drew a row the
 * scroll ceiling could not reach, leaving the oldest message stranded
 * under the tab strip.
 */
public final class ChatStackRowsTest {

    @Before
    public void reset() {
        ClientChatChannelViews.clear();
    }

    @After
    public void cleanUp() {
        ClientChatChannelViews.clear();
    }

    @Test
    public void withoutADividerRowsAreLines() {
        for (int index = 0; index < 5; index++) {
            assertEquals(index,
                    LostTalesChatOverlayRenderer.rowOfLine(index, -1));
            assertEquals(index,
                    LostTalesChatOverlayRenderer.lineOfRow(index, -1));
        }
    }

    @Test
    public void linesOlderThanTheDividerStandARowHigher() {
        int divider = 2;
        assertEquals(0, LostTalesChatOverlayRenderer.rowOfLine(0, divider));
        assertEquals(1, LostTalesChatOverlayRenderer.rowOfLine(1, divider));
        // The divided message keeps its own row; the divider is above it.
        assertEquals(2, LostTalesChatOverlayRenderer.rowOfLine(2, divider));
        assertEquals(4, LostTalesChatOverlayRenderer.rowOfLine(3, divider));
        assertEquals(5, LostTalesChatOverlayRenderer.rowOfLine(4, divider));
    }

    @Test
    public void theDividerOwnsExactlyOneRow() {
        int divider = 2;
        // Row 3 is the divider's: no line stands on it.
        for (int line = 0; line < 6; line++) {
            assertEquals("no line may claim the divider's row", true,
                    LostTalesChatOverlayRenderer.rowOfLine(line, divider)
                            != divider + 1);
        }
    }

    @Test
    public void rowsMapBackToTheLineThatDrawsThem() {
        int divider = 2;
        for (int line = 0; line < 6; line++) {
            int row = LostTalesChatOverlayRenderer.rowOfLine(line, divider);
            assertEquals(line,
                    LostTalesChatOverlayRenderer.lineOfRow(row, divider));
        }
        // The divider's own row answers with the line below it, so a
        // draw starting there begins a row early rather than skipping it.
        assertEquals(divider,
                LostTalesChatOverlayRenderer.lineOfRow(divider + 1,
                        divider));
    }

    @Test
    public void rowsAreMonotonicSoTheStackNeverOverlaps() {
        int divider = 3;
        int previous = -1;
        for (int line = 0; line < 10; line++) {
            int row = LostTalesChatOverlayRenderer.rowOfLine(line, divider);
            assertEquals("rows must climb with the line index", true,
                    row > previous);
            previous = row;
        }
    }

    /**
     * The bug this closes end to end: a reopened channel whose content
     * is a row taller than its lines could not be scrolled far enough to
     * bring the oldest message out from under the tab strip, because the
     * ceiling was measured in lines while the stack was drawn in rows.
     */
    @Test
    public void theScrollCeilingReachesTheDividersRow() {
        ChatTab tab = ChatTab.of(ChatChannel.DISCORD);
        int lines = 30;
        int rows = lines + 1;
        double room = 10.0D;
        ClientChatChannelViews.scroll(tab, 1000, rows, room);
        double ceiling = ClientChatChannelViews.getScroll(tab, rows, room);
        // The topmost row is the oldest line, pushed up by the divider.
        int topRow = LostTalesChatOverlayRenderer.rowOfLine(lines - 1, 0);
        assertEquals(rows - room, ceiling, 0.0001D);
        assertTrue("the oldest message must be reachable",
                ceiling + room - 1 >= topRow);
        // Measured in lines, as it used to be, the same view stops a row
        // short of it.
        double lineCeiling = ClientChatChannelViews.getScroll(tab, lines,
                room);
        assertTrue(lineCeiling + room - 1 < topRow);
    }

    @Test
    public void theTopmostRowIsReachable() {
        // A history of ten lines with a divider is eleven rows tall; a
        // window with room for four must be able to scroll to the row
        // that holds the oldest line.
        int lines = 10;
        int divider = 1;
        int rows = lines + 1;
        double room = 4.0D;
        double ceiling = rows - room;
        int topRow = LostTalesChatOverlayRenderer.rowOfLine(lines - 1,
                divider);
        assertEquals(10, topRow);
        // At the ceiling the topmost drawn row is scroll + room - 1.
        assertEquals(topRow, (int)Math.round(ceiling + room - 1));
    }
}
