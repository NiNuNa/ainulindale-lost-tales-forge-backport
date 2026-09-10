package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.util.ChatComponentText;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The stack's row heights: a message's row is a line, the blank row
 * between two runs is half of one, the divider's row is a line, and
 * every distance up the stack is the sum of the rows below it. The
 * scroll offset maps onto those distances both ways, so the draw, the
 * ceiling and the scrollbar cannot disagree about where a row stands.
 */
public final class ChatStackRowsGeometryTest {

    private static final int LINE = ChatStackRows.LINE_HEIGHT;
    private static final int HALF = ChatStackRows.SPACER_HEIGHT;

    @Test
    public void aBlankRowIsHalfALine() {
        assertEquals(6, HALF);
        assertEquals(11, LINE);
        assertEquals(HALF, ChatStackRows.heightOf(
                new ChatLine(0, ChatWindowLines.SPACER, 0)));
        assertEquals(LINE, ChatStackRows.heightOf(
                new ChatLine(0, new ChatComponentText("x"), 1)));
        assertEquals(LINE, ChatStackRows.heightOf(null));
    }

    /** Newest first: message, blank, message, message, blank, message. */
    @Test
    public void rowsStandOnTheRowsBelowThem() {
        ChatStackRows rows = new ChatStackRows();
        rows.reset(lines(false, true, false, false, true, false), -1);
        assertEquals(6, rows.count());
        assertEquals(0, rows.top(0));
        assertEquals(LINE, rows.top(1));
        assertEquals(LINE + HALF, rows.top(2));
        assertEquals(2 * LINE + HALF, rows.top(3));
        assertEquals(3 * LINE + HALF, rows.top(4));
        assertEquals(3 * LINE + 2 * HALF, rows.top(5));
        assertEquals(4 * LINE + 2 * HALF, rows.total());
        assertEquals(rows.total(), rows.top(6));
        assertEquals(rows.total(), rows.top(99));
        assertEquals(HALF, rows.height(1));
        assertEquals(LINE, rows.height(0));
        // Past the end a row is a whole line, so a slide past the stack
        // measures like any other.
        assertEquals(LINE, rows.height(6));
    }

    /** The divider takes a whole row of its own above the divided message. */
    @Test
    public void theDividerRowIsAWholeLine() {
        ChatStackRows rows = new ChatStackRows();
        // Newest first: message, blank, message(divided at index 2), message.
        rows.reset(lines(false, true, false, false), 2);
        assertEquals(5, rows.count());
        assertTrue(ChatStackRows.isDividerRow(3, 2));
        assertFalse(ChatStackRows.isDividerRow(2, 2));
        assertFalse(ChatStackRows.isDividerRow(3, -1));
        assertEquals(LINE, rows.height(3));
        // The line older than the divider stands on the divider's row.
        assertEquals(3 * LINE + HALF, rows.top(4));
        assertEquals(4 * LINE + HALF, rows.total());
    }

    @Test
    public void scrollOffsetsMapOntoDistancesAndBack() {
        ChatStackRows rows = new ChatStackRows();
        rows.reset(new int[] {LINE, HALF, LINE, LINE});
        assertEquals(0.0D, rows.offsetOf(0.0D), 1.0E-9D);
        assertEquals(LINE / 2.0D, rows.offsetOf(0.5D), 1.0E-9D);
        assertEquals(LINE, rows.offsetOf(1.0D), 1.0E-9D);
        // Half-way through the blank row is a sixth of a line up.
        assertEquals(LINE + HALF / 2.0D, rows.offsetOf(1.5D), 1.0E-9D);
        assertEquals(LINE + HALF, rows.offsetOf(2.0D), 1.0E-9D);
        assertEquals(3 * LINE + HALF, rows.offsetOf(4.0D), 1.0E-9D);
        // Past the stack, rows are whole lines of nothing.
        assertEquals(4 * LINE + HALF, rows.offsetOf(5.0D), 1.0E-9D);
        for (double offset = 0.0D; offset <= 5.0D; offset += 0.125D) {
            assertEquals(offset, rows.rowsAt(rows.offsetOf(offset)), 1.0E-9D);
        }
        assertEquals(0.0D, rows.rowsAt(-3.0D), 1.0E-9D);
        assertEquals(0.0D, new ChatStackRows().rowsAt(12.0D), 1.0E-9D);
    }

    @Test
    public void theHighestRowBelowALimit() {
        ChatStackRows rows = new ChatStackRows();
        rows.reset(new int[] {LINE, HALF, LINE});
        assertEquals(-1, rows.lastRowBelow(0.0D));
        assertEquals(0, rows.lastRowBelow(0.5D));
        assertEquals(0, rows.lastRowBelow(LINE));
        assertEquals(1, rows.lastRowBelow(LINE + 0.5D));
        assertEquals(2, rows.lastRowBelow(LINE + HALF + 1.0D));
        assertEquals(2, rows.lastRowBelow(1000.0D));
        assertEquals(-1, new ChatStackRows().lastRowBelow(1000.0D));
    }

    /** The geometry knows which list it was laid out for. */
    @Test
    public void rowsDescribeTheirSource() {
        List<ChatLine> lines = lines(false, true, false);
        ChatStackRows rows = new ChatStackRows();
        rows.reset(lines, -1);
        assertTrue(rows.describes(lines, 3, -1));
        assertFalse(rows.describes(lines, 3, 0));
        assertFalse(rows.describes(lines, 4, -1));
        assertFalse(rows.describes(new ArrayList<ChatLine>(lines), 3, -1));
    }

    private static List<ChatLine> lines(boolean... spacers) {
        List<ChatLine> lines = new ArrayList<ChatLine>();
        for (int index = 0; index < spacers.length; index++) {
            lines.add(spacers[index]
                    ? new ChatLine(0, ChatWindowLines.SPACER, 0)
                    : new ChatLine(0, new ChatComponentText("x"), index + 1));
        }
        return lines;
    }
}
