package com.ninuna.losttales.client.chat;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.ChatLine;

/**
 * How tall each row of a view's stack is, and where every row starts,
 * measured up from the baseline in the chat's own (unscaled) pixels.
 *
 * <p>The stack's rows are not all one height: a message's row and the
 * unread divider's are a whole line, while the blank row between two
 * runs is half of one, so groups read apart without a whole empty line
 * between them. Scrolling still counts rows — a scroll offset is a row
 * and a fraction of the next — but every distance up the stack is a
 * sum of row heights, and this is the one place those sums are taken:
 * the draw, the scroll ceiling, the scrollbar and the window's own
 * height all read the same geometry, so a row can never be drawn where
 * the scroll cannot reach it.</p>
 *
 * <p>Row {@code r} stands on {@link #top(int)} pixels of rows below it;
 * row zero is the newest line, on the baseline. Rebuilt only when the
 * line list, its length or the divider's row changes, against arrays
 * reused across frames.</p>
 */
final class ChatStackRows {
    /** A message's row, and the unread divider's. */
    static final int LINE_HEIGHT = LostTalesChatOverlayRenderer.LINE_HEIGHT;
    /**
     * The blank row between two runs: half a line, rounded up to a
     * whole pixel so the rows above it stay on the pixel grid.
     */
    static final int SPACER_HEIGHT = (LINE_HEIGHT + 1) / 2;
    private static final int INITIAL_CAPACITY = 32;

    /** {@code tops[r]} is where row {@code r} starts; {@code tops[count]} is the total. */
    private int[] tops = new int[INITIAL_CAPACITY + 1];
    private int count;
    private Object source;
    private int sourceSize;
    private int dividerIndex = -1;

    /** Lays the rows of a view's line list out, the divider's row included. */
    void reset(List<ChatLine> lines, int dividerIndex) {
        this.source = lines;
        this.sourceSize = lines == null ? 0 : lines.size();
        this.dividerIndex = dividerIndex;
        int rows = this.sourceSize + (dividerIndex >= 0 ? 1 : 0);
        ensureCapacity(rows);
        this.count = rows;
        this.tops[0] = 0;
        for (int row = 0; row < rows; row++) {
            int height;
            if (isDividerRow(row, dividerIndex)) {
                height = LINE_HEIGHT;
            } else {
                int line = LostTalesChatOverlayRenderer.lineOfRow(row,
                        dividerIndex);
                height = heightOf(lines.get(line));
            }
            this.tops[row + 1] = this.tops[row] + height;
        }
    }

    /** Rows of the given heights, oldest last; the geometry's own test hook. */
    void reset(int[] heights) {
        this.source = null;
        this.sourceSize = 0;
        this.dividerIndex = -1;
        int rows = heights == null ? 0 : heights.length;
        ensureCapacity(rows);
        this.count = rows;
        this.tops[0] = 0;
        for (int row = 0; row < rows; row++) {
            this.tops[row + 1] = this.tops[row] + Math.max(1, heights[row]);
        }
    }

    private void ensureCapacity(int rows) {
        if (this.tops.length < rows + 1) {
            this.tops = Arrays.copyOf(this.tops,
                    Math.max(rows + 1, this.tops.length * 2));
        }
    }

    /** True when the rows describe the given list with the given divider. */
    boolean describes(List<ChatLine> lines, int size, int dividerIndex) {
        return this.source == lines && this.sourceSize == size
                && this.dividerIndex == dividerIndex;
    }

    /** The height a line's row takes: half a line for a blank row between runs. */
    static int heightOf(ChatLine line) {
        return ChatWindowLines.isSpacer(line) ? SPACER_HEIGHT : LINE_HEIGHT;
    }

    /**
     * Whether the row is the unread divider's own: the row directly
     * above the message it divides at, which is the row after that
     * message's in the stack.
     */
    static boolean isDividerRow(int row, int dividerIndex) {
        return dividerIndex >= 0 && row == dividerIndex + 1;
    }

    int count() {
        return this.count;
    }

    /** Height of the whole stack. */
    int total() {
        return this.tops[this.count];
    }

    /**
     * Pixels of rows below the row's bottom edge. Asking for the row
     * past the last answers with the total, so a scroll resting on the
     * stack's end reads a distance like any other.
     */
    int top(int row) {
        return this.tops[Math.max(0, Math.min(this.count, row))];
    }

    /** Height of the row; a whole line for any row past the last. */
    int height(int row) {
        if (row < 0 || row >= this.count) {
            return LINE_HEIGHT;
        }
        return this.tops[row + 1] - this.tops[row];
    }

    /**
     * The distance a scroll offset stands for: whole rows and the
     * fraction of the row the offset is inside.
     */
    double offsetOf(double rows) {
        double bounded = Math.max(0.0D, rows);
        int whole = (int)Math.floor(bounded);
        if (whole >= this.count) {
            // Past the stack's end, whole lines of nothing.
            return total() + (bounded - this.count) * LINE_HEIGHT;
        }
        return this.tops[whole] + (bounded - whole) * height(whole);
    }

    /**
     * The scroll offset that stands {@code pixels} up the stack, the
     * inverse of {@link #offsetOf}: nothing below the baseline, and
     * past the stack's end whole lines of nothing.
     */
    double rowsAt(double pixels) {
        if (pixels <= 0.0D || this.count == 0) {
            return 0.0D;
        }
        int total = total();
        if (pixels >= total) {
            return this.count + (pixels - total) / LINE_HEIGHT;
        }
        int row = lastRowBelow(pixels);
        return row + (pixels - this.tops[row]) / (double)height(row);
    }

    /**
     * The highest row that starts below {@code limit}, or -1 when none
     * does. Rows start at strictly increasing heights, so this is a
     * binary search.
     */
    int lastRowBelow(double limit) {
        int low = 0;
        int high = this.count - 1;
        int found = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (this.tops[middle] < limit) {
                found = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return found;
    }
}
