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
 * between two runs is two thirds of one, the divider's row is a line
 * with the same gap on either side of it, and every distance up the
 * stack is the sum of the rows below it. The
 * scroll offset maps onto those distances both ways, so the draw, the
 * ceiling and the scrollbar cannot disagree about where a row stands.
 */
public final class ChatStackRowsGeometryTest {

    private static final int LINE = ChatStackRows.LINE_HEIGHT;
    private static final int GAP = ChatStackRows.SPACER_HEIGHT;

    @Test
    public void aBlankRowIsTwoThirdsOfALine() {
        assertEquals(8, GAP);
        assertEquals(12, LINE);
        assertEquals(GAP, ChatStackRows.heightOf(
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
        assertEquals(LINE + GAP, rows.top(2));
        assertEquals(2 * LINE + GAP, rows.top(3));
        assertEquals(3 * LINE + GAP, rows.top(4));
        assertEquals(3 * LINE + 2 * GAP, rows.top(5));
        assertEquals(4 * LINE + 2 * GAP, rows.total());
        assertEquals(rows.total(), rows.top(6));
        assertEquals(rows.total(), rows.top(99));
        assertEquals(GAP, rows.height(1));
        assertEquals(LINE, rows.height(0));
        // Past the end a row is a whole line, so a slide past the stack
        // measures like any other.
        assertEquals(LINE, rows.height(6));
    }

    /**
     * The divider takes a row of its own above the divided message: its
     * line, with the gap two runs stand apart by clear on each side where
     * a message stands.
     */
    @Test
    public void theDividerRowIsALineWithAGapEitherSide() {
        ChatStackRows rows = new ChatStackRows();
        // Newest first: message, blank, message(divided at index 2), message.
        rows.reset(lines(false, true, false, false), 2);
        assertEquals(5, rows.count());
        assertTrue(ChatStackRows.isDividerRow(3, 2));
        assertFalse(ChatStackRows.isDividerRow(2, 2));
        assertFalse(ChatStackRows.isDividerRow(3, -1));
        assertEquals(GAP + LINE + GAP, rows.height(3));
        // The line older than the divider stands on the divider's row.
        assertEquals(3 * LINE + 3 * GAP, rows.top(4));
        assertEquals(4 * LINE + 3 * GAP, rows.total());
        // The divider's own line starts a gap above the divided message.
        assertEquals(rows.top(3) + GAP, rows.dividerLineBottom());
        // GAP + LINE + GAP leaves the one-pixel rule an odd number of
        // clear rows, which cannot split evenly: the spare one stands
        // below the rule, which sits half a pixel above its line's
        // middle, as the capitals sit in theirs.
        assertEquals(28, rows.height(3));
        assertEquals(14, ruleClearBelow(rows, 3));
        assertEquals(13, ruleClearAbove(rows, 4));
        // Measured to the words, the rule stands exactly as far from the
        // capitals of the message below as from those of the one above.
        assertEquals(16, capsClearBelow(rows, 3));
        assertEquals(capsClearBelow(rows, 3), capsClearAbove(rows, 4));
    }

    /**
     * A blank row between two runs already standing above the divider is
     * its gap on that side, never a second one beside it: group, gap,
     * rule, gap, group.
     */
    @Test
    public void theDividerSharesTheBlankRowAboveIt() {
        ChatStackRows rows = new ChatStackRows();
        // Newest first: message, message(divided at 1), blank, message.
        List<ChatLine> lines = lines(false, false, true, false);
        rows.reset(lines, 1);
        assertEquals(GAP + LINE, ChatStackRows.dividerHeight(lines, 1));
        assertEquals(GAP + LINE, rows.height(2));
        assertEquals(GAP, rows.height(3));
        assertEquals(rows.top(2) + GAP, rows.dividerLineBottom());
        // The older message stands past the blank row, with the same
        // clearance a divider row of its own gives: the spare row below
        // the rule, and the capitals either side equally far from it.
        assertEquals(20, rows.height(2));
        assertEquals(14, ruleClearBelow(rows, 2));
        assertEquals(13, ruleClearAbove(rows, 4));
        assertEquals(capsClearBelow(rows, 2), capsClearAbove(rows, 4));
        assertEquals(4 * LINE + 2 * GAP, rows.total());
    }

    /** Where no line stands above the divider, no gap does either. */
    @Test
    public void theDividerAtTheTopOfTheHistoryHasNoGapAboveIt() {
        ChatStackRows rows = new ChatStackRows();
        List<ChatLine> lines = lines(false, false);
        rows.reset(lines, 1);
        assertEquals(3, rows.count());
        assertEquals(GAP + LINE, rows.height(2));
        assertEquals(3 * LINE + GAP, rows.total());
        assertEquals(0, ChatStackRows.gapBeside(lines, 2));
        assertEquals(0, ChatStackRows.gapBeside(lines, -1));
        assertEquals(0, ChatStackRows.gapBeside(null, 0));
        assertEquals(GAP, ChatStackRows.gapBeside(lines, 1));
        // No divider, no divider line.
        rows.reset(lines, -1);
        assertEquals(0, rows.dividerLineBottom());
    }

    /**
     * Clear pixels between the unread rule and the message row below the
     * divider's row, which starts where that row does.
     */
    private static int ruleClearBelow(ChatStackRows rows, int dividerRow) {
        return ruleBottom(rows) - rows.top(dividerRow);
    }

    /** Clear pixels between the rule and the bottom of the message row above. */
    private static int ruleClearAbove(ChatStackRows rows, int olderMessageRow) {
        return rows.top(olderMessageRow) - (ruleBottom(rows) + 1);
    }

    /**
     * Clear pixels between the rule and the capitals of the message row
     * below the divider's row: the rows between the rule and that row,
     * and the rows above its capitals.
     */
    private static int capsClearBelow(ChatStackRows rows, int dividerRow) {
        return ruleClearBelow(rows, dividerRow)
                + LostTalesChatOverlayRenderer.ROW_TEXT_TOP;
    }

    /**
     * Clear pixels between the rule and the capitals of the message row
     * above: the rows between the rule and that row, and the rows below
     * its capitals.
     */
    private static int capsClearAbove(ChatStackRows rows, int olderMessageRow) {
        return ruleClearAbove(rows, olderMessageRow)
                + LostTalesChatOverlayRenderer.TEXT_OFFSET
                - LostTalesChatOverlayRenderer.GLYPH_CAP_HEIGHT;
    }

    /**
     * The rule's one pixel, measured up the stack: its line's top edge
     * less the rows the rule stands below it.
     */
    private static int ruleBottom(ChatStackRows rows) {
        return rows.dividerLineBottom() + LINE
                - LostTalesChatOverlayRenderer.DIVIDER_RULE_OFFSET - 1;
    }

    @Test
    public void scrollOffsetsMapOntoDistancesAndBack() {
        ChatStackRows rows = new ChatStackRows();
        rows.reset(new int[] {LINE, GAP, LINE, LINE});
        assertEquals(0.0D, rows.offsetOf(0.0D), 1.0E-9D);
        assertEquals(LINE / 2.0D, rows.offsetOf(0.5D), 1.0E-9D);
        assertEquals(LINE, rows.offsetOf(1.0D), 1.0E-9D);
        // Half-way through the blank row is a third of a line past the
        // first row.
        assertEquals(LINE + GAP / 2.0D, rows.offsetOf(1.5D), 1.0E-9D);
        assertEquals(LINE + GAP, rows.offsetOf(2.0D), 1.0E-9D);
        assertEquals(3 * LINE + GAP, rows.offsetOf(4.0D), 1.0E-9D);
        // Past the stack, rows are whole lines of nothing.
        assertEquals(4 * LINE + GAP, rows.offsetOf(5.0D), 1.0E-9D);
        for (double offset = 0.0D; offset <= 5.0D; offset += 0.125D) {
            assertEquals(offset, rows.rowsAt(rows.offsetOf(offset)), 1.0E-9D);
        }
        assertEquals(0.0D, rows.rowsAt(-3.0D), 1.0E-9D);
        assertEquals(0.0D, new ChatStackRows().rowsAt(12.0D), 1.0E-9D);
    }

    @Test
    public void theHighestRowBelowALimit() {
        ChatStackRows rows = new ChatStackRows();
        rows.reset(new int[] {LINE, GAP, LINE});
        assertEquals(-1, rows.lastRowBelow(0.0D));
        assertEquals(0, rows.lastRowBelow(0.5D));
        assertEquals(0, rows.lastRowBelow(LINE));
        assertEquals(1, rows.lastRowBelow(LINE + 0.5D));
        assertEquals(2, rows.lastRowBelow(LINE + GAP + 1.0D));
        assertEquals(2, rows.lastRowBelow(1000.0D));
        assertEquals(-1, new ChatStackRows().lastRowBelow(1000.0D));
    }

    /**
     * A blank row slid into the trailing strip under the baseline leaves
     * room for part of the row under it, which the draw starts from too.
     */
    @Test
    public void theDrawStartsFromEveryRowTheStripReveals() {
        ChatStackRows rows = new ChatStackRows();
        // Newest first: a day's first message, the rule's lower gap, the
        // rule, its upper gap.
        rows.reset(new int[] {LINE, GAP, LINE, GAP});
        float strip = LINE;
        // Resting on the rule: the gap under it fills part of the strip,
        // and the message's top shows in the rest.
        assertEquals(0, rows.firstRowShown(2, 0.0F, strip));
        // Slid down until the gap fills the strip: the message is below it.
        assertEquals(1, rows.firstRowShown(2, LINE - GAP, strip));
        // Whole lines under the baseline: the one row under the view.
        rows.reset(new int[] {LINE, LINE, LINE});
        assertEquals(1, rows.firstRowShown(2, 0.0F, strip));
        assertEquals(0, rows.firstRowShown(1, 0.0F, strip));
        assertEquals(0, rows.firstRowShown(0, 0.0F, strip));
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
