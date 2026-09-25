package com.ninuna.losttales.client.chat;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.util.IChatComponent;

/**
 * How tall each row of a view's stack is, and where every row starts,
 * measured up from the baseline in the chat's own (unscaled) pixels.
 *
 * <p>The stack's rows are not all one height: every row is as tall as
 * the text it is drawn in — a message's row and a day's rule a whole
 * line, the row naming its speaker the large text's height, a reply's
 * quote the small text's — and the blank row between two runs is two
 * thirds of a line, so groups read apart without a whole empty line
 * between them. The unread divider is a chat element like a run: its
 * row holds its own line and the same gap on either side of it, sharing
 * a blank row that already stands beside it. Scrolling still counts
 * rows — a scroll offset is a row
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
public final class ChatStackRows {
    /** A message's row, a day's rule, and the unread divider's own line. */
    static final int LINE_HEIGHT = LostTalesChatOverlayRenderer.LINE_HEIGHT;
    /**
     * The one gap the stack lays: the blank row between two runs, and
     * the space on either side of a day's rule and the unread divider.
     * Two thirds of a line, rounded to the nearest whole pixel so the
     * rows above it stay on the pixel grid; two thirds of a whole number
     * is never a half, so the rounding has no tie to break.
     */
    static final int SPACER_HEIGHT = (2 * LINE_HEIGHT + 1) / 3;
    private static final int INITIAL_CAPACITY = 32;

    /** {@code tops[r]} is where row {@code r} starts; {@code tops[count]} is the total. */
    private int[] tops = new int[INITIAL_CAPACITY + 1];
    private int count;
    private Object source;
    private int sourceSize;
    private int dividerIndex = -1;
    /** The gap under the divider's own line, inside the divider's row. */
    private int dividerGapBelow;
    /** The height reaction rows were laid out at: it follows the display. */
    private int reactionHeight;
    /** The state the rows were laid out for: the sizes differ by it. */
    private boolean chatOpen = true;
    /** The height the speaker's rows were laid out at; the display's too. */
    private int speakerHeight;
    /** The height a reply's quote row was laid out at. */
    private int quoteHeight;
    /** The height the rows of a message's words were laid out at. */
    private int messageHeight;

    /**
     * Lays the rows of a view's line list out, the divider's row
     * included, at the sizes {@code chatOpen} draws them at.
     */
    public void reset(List<ChatLine> lines, int dividerIndex, boolean chatOpen) {
        this.chatOpen = chatOpen;
        reset(lines, dividerIndex);
    }

    /** Lays the rows out for the open window's sizes. */
    public void reset(List<ChatLine> lines, int dividerIndex) {
        this.source = lines;
        this.sourceSize = lines == null ? 0 : lines.size();
        this.dividerIndex = dividerIndex;
        this.dividerGapBelow = dividerIndex >= 0
                ? gapBeside(lines, dividerIndex) : 0;
        this.reactionHeight = reactionRowHeight();
        this.speakerHeight = speakerRowHeight(this.chatOpen);
        this.quoteHeight = quoteRowHeight(this.chatOpen);
        this.messageHeight = messageRowHeight(this.chatOpen);
        int rows = this.sourceSize + (dividerIndex >= 0 ? 1 : 0);
        ensureCapacity(rows);
        this.count = rows;
        this.tops[0] = 0;
        for (int row = 0; row < rows; row++) {
            int height;
            if (isDividerRow(row, dividerIndex)) {
                height = dividerHeight(lines, dividerIndex);
            } else {
                int line = LostTalesChatOverlayRenderer.lineOfRow(row,
                        dividerIndex);
                height = heightOf(lines.get(line), this.reactionHeight,
                        this.speakerHeight, this.quoteHeight,
                        this.messageHeight);
            }
            this.tops[row + 1] = this.tops[row] + height;
        }
    }

    /** Rows of the given heights, oldest last; the geometry's own test hook. */
    public void reset(int[] heights) {
        this.source = null;
        this.sourceSize = 0;
        this.dividerIndex = -1;
        this.dividerGapBelow = 0;
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

    /**
     * True when the rows describe the given list with the given divider,
     * at the heights every kind of row takes on the display, and in the
     * state, as they are now.
     */
    public boolean describes(List<ChatLine> lines, int size, int dividerIndex,
                      boolean chatOpen) {
        return this.source == lines && this.sourceSize == size
                && this.dividerIndex == dividerIndex
                && this.chatOpen == chatOpen
                && this.reactionHeight == reactionRowHeight()
                && this.speakerHeight == speakerRowHeight(chatOpen)
                && this.quoteHeight == quoteRowHeight(chatOpen)
                && this.messageHeight == messageRowHeight(chatOpen);
    }

    /** As above for the open window's sizes. */
    public boolean describes(List<ChatLine> lines, int size, int dividerIndex) {
        return describes(lines, size, dividerIndex, true);
    }

    /**
     * The height a line's row takes: {@link #SPACER_HEIGHT} for a blank
     * row, between two runs or beside a day's rule, a message's reaction
     * row as tall as its chips need ({@link #reactionRowHeight}), and
     * every other row as tall as the text it is drawn in: the row naming
     * the speaker ({@link #speakerRowHeight}), a reply's quote
     * ({@link #quoteRowHeight}) and the rows of a message's own words
     * ({@link #messageRowHeight}), which the closed feed may draw
     * smaller. A whole line for anything else, a line of another mod's
     * included.
     */
    static int heightOf(ChatLine line) {
        return heightOf(line, reactionRowHeight(), speakerRowHeight(true),
                quoteRowHeight(true), messageRowHeight(true));
    }

    private static int heightOf(ChatLine line, int reactionHeight,
                                int speakerHeight, int quoteHeight,
                                int messageHeight) {
        if (ChatWindowLines.isSpacer(line)) {
            return SPACER_HEIGHT;
        }
        if (line == null) {
            return LINE_HEIGHT;
        }
        IChatComponent row = line.func_151461_a();
        if (ChatReactionMarker.isReactionRow(row)) {
            return reactionHeight;
        }
        if (ChatLayoutMarker.isHeaderRow(row)) {
            return speakerHeight;
        }
        if (ChatReplyMarker.isQuoteRow(row)) {
            return quoteHeight;
        }
        return ChatLayoutMarker.isBodyRow(row) ? messageHeight : LINE_HEIGHT;
    }

    /**
     * How tall a reaction row is on this display: a whole line, or as
     * tall as its chips — framed buttons drawn at the chat's small size —
     * where they cannot shrink into one: at GUI scale 1, which has no
     * smaller size, and at 4, where three quarters of a chip is more than
     * a line.
     */
    static int reactionRowHeight() {
        return reactionRowHeight(LostTalesChatVisualStyle.stackSmallScale());
    }

    /** As above for small text drawn at {@code smallScale} of the words. */
    static int reactionRowHeight(float smallScale) {
        return Math.max(LINE_HEIGHT, (int)Math.ceil(
                ChatReactionMarker.HEIGHT * smallScale - 0.001F));
    }

    /**
     * How tall a row drawn at {@code rowScale} of a message's words is:
     * the words' own row at that size. Every row keeps the line it
     * stands on, so the room one gains or gives back is above it, and a
     * speaker stays over their words while a quote sits close over the
     * message answering it.
     */
    static int rowHeight(float rowScale) {
        return Math.max(1, (int)Math.ceil(LINE_HEIGHT * rowScale - 0.001F));
    }

    /** How tall the row a message names its speaker on is. */
    static int speakerRowHeight(boolean chatOpen) {
        return rowHeight(LostTalesChatVisualStyle.speakerRowScale(chatOpen));
    }

    /** How tall the row a reply opens with is. */
    static int quoteRowHeight(boolean chatOpen) {
        return rowHeight(LostTalesChatVisualStyle.quoteRowScale(chatOpen));
    }

    /** How tall a row of a message's own words is. */
    static int messageRowHeight(boolean chatOpen) {
        return rowHeight(LostTalesChatVisualStyle.messageRowScale(chatOpen));
    }

    /**
     * Whether the row is the unread divider's own: the row directly
     * above the message it divides at, which is the row after that
     * message's in the stack.
     */
    static boolean isDividerRow(int row, int dividerIndex) {
        return dividerIndex >= 0 && row == dividerIndex + 1;
    }

    /**
     * The height of the unread divider's row: its own line, with the
     * gap two runs stand apart by on each side of it. A side
     * where a blank row already stands shares that one, and a side with
     * no line at all, the top of the loaded history, has none.
     */
    static int dividerHeight(List<ChatLine> lines, int dividerIndex) {
        return gapBeside(lines, dividerIndex) + LINE_HEIGHT
                + gapBeside(lines, dividerIndex + 1);
    }

    /**
     * The gap the unread divider's row, or a day's rule, leaves toward
     * the line at {@code index}:
     * {@link #SPACER_HEIGHT}, or nothing where no line stands there or
     * where a blank row already does.
     */
    static int gapBeside(List<ChatLine> lines, int index) {
        if (lines == null || index < 0 || index >= lines.size()) {
            return 0;
        }
        return ChatWindowLines.isSpacer(lines.get(index)) ? 0 : SPACER_HEIGHT;
    }

    /**
     * Pixels of stack below the unread divider's own line: the rows
     * under the divider's row and the gap under the line inside it. The
     * rule is drawn on that line. Zero without a divider.
     */
    int dividerLineBottom() {
        return this.dividerIndex < 0 ? 0
                : top(this.dividerIndex + 1) + this.dividerGapBelow;
    }

    public int count() {
        return this.count;
    }

    /** Height of the whole stack. */
    public int total() {
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
    public double offsetOf(double rows) {
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
    public double rowsAt(double pixels) {
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
     * The lowest row the draw starts from for a view scrolled to
     * {@code scrollRow}: the row under it, and below that every row whose
     * top stands inside the {@code strip} pixels under the baseline the
     * clip reveals, the stack moved down by {@code offset}. A blank row in
     * the strip leaves room for part of the row under it, which is drawn
     * too.
     */
    int firstRowShown(int scrollRow, float offset, float strip) {
        int first = Math.max(0, Math.min(this.count, scrollRow) - 1);
        float base = top(scrollRow);
        while (first > 0 && base - top(first) + offset < strip) {
            first--;
        }
        return first;
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
