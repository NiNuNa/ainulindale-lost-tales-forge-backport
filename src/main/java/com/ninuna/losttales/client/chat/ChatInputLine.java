package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiCaret;

/**
 * The one description of the input bar's typing line, in the bar's own
 * pixels, left to right:
 *
 * <pre>
 * indicator |3| divider |3| field + caret |3| divider |3| counter |3| controls
 * </pre>
 *
 * <p>The field lies in a well: the stretch between the two dividers,
 * which the bar lays once more with its own surface, as the timestamp
 * column is laid over the panel. Every gap is measured in ink, like
 * every other gap the chat keeps: the indicator is measured to its last
 * pixel of ink, and the field ends a caret's width short of its gap, so
 * a caret after the last character of a full field keeps the same clear
 * space from the divider that the first character keeps from the other
 * one. The counter's slot is handed in at the widest count the font can
 * draw, so nothing on the line moves while the count grows.</p>
 *
 * <p>The bar's drawing, the field's bounds, the indicator's box and the
 * tests all read these offsets from here, so what is drawn, what is
 * typed into and what answers the pointer never disagree.</p>
 */
final class ChatInputLine {
    /** Left edge of the divider after the channel indicator. */
    final int leftDividerX;
    /** The well: from past the left divider up to the right one. */
    final int wellLeft;
    final int wellRight;
    /** The field's box; its text, caret and selection stay inside it. */
    final int fieldLeft;
    final int fieldRight;
    /** Left edge of the divider before the counter. */
    final int rightDividerX;
    /**
     * Where the counter's text begins; its slot ends a gap before the
     * controls.
     */
    final int counterLeft;

    private ChatInputLine(int leftDividerX, int rightDividerX,
                          int counterLeft) {
        this.leftDividerX = leftDividerX;
        this.wellLeft = leftDividerX + LostTalesChatVisualStyle.DIVIDER_WIDTH;
        this.wellRight = rightDividerX;
        this.fieldLeft = this.wellLeft + ChatInputBar.BAR_GAP;
        this.fieldRight = rightDividerX - ChatInputBar.BAR_GAP
                - LostTalesUiCaret.WIDTH;
        this.rightDividerX = rightDividerX;
        this.counterLeft = counterLeft;
    }

    /**
     * The line from the indicator's last pixel of ink to the leftmost
     * control's slot, with a counter slot {@code counterWidth} pixels
     * wide before the controls.
     */
    static ChatInputLine between(int indicatorInkRight, int controlsLeft,
                                 int counterWidth) {
        int counterLeft = controlsLeft - ChatInputBar.BAR_GAP
                - Math.max(0, counterWidth);
        return new ChatInputLine(dividerAfter(indicatorInkRight),
                counterLeft - ChatInputBar.BAR_GAP
                        - LostTalesChatVisualStyle.DIVIDER_WIDTH,
                counterLeft);
    }

    /**
     * Where the divider after the indicator stands: a gap past the
     * indicator's frame.
     */
    static int dividerAfter(int indicatorInkRight) {
        return indicatorInkRight + ChatInputBar.BAR_GAP;
    }
}

