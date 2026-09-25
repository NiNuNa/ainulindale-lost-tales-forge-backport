package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;

/**
 * The one description of the input bar's typing line, in the bar's own
 * pixels, left to right:
 *
 * <pre>
 * head button |3| divider |2| well: |3| field + caret |3| :well |2| divider |3| counter |3| controls
 * </pre>
 *
 * <p>The field lies in a well: a stretch of the chat's darker inset
 * surface between the two dividers, {@link #WELL_GAP} clear pixels inside
 * each. Every gap is measured in ink, like every other gap the chat
 * keeps: the head button is measured from its frame's outer edge, and the
 * field ends a caret's width short of its gap, so a caret after the last
 * character of a full field keeps the same clear space from the well's
 * edge that the first character keeps from the other one. The counter's
 * slot is handed in at the widest count the font can draw, so nothing on
 * the line moves while the count grows.</p>
 *
 * <p>The bar's drawing, the field's bounds, the indicator's box and the
 * tests all read these offsets from here, so what is drawn, what is
 * typed into and what answers the pointer never disagree.</p>
 */
final class ChatInputLine {
    /** Clear pixels between each divider and the well. */
    static final int WELL_GAP = 2;

    /** Left edge of the divider after the head button. */
    final int leftDividerX;
    /** The well: {@link #WELL_GAP} inside each divider. */
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
        this.wellLeft = leftDividerX + WindowStyle.DIVIDER_WIDTH
                + WELL_GAP;
        this.wellRight = rightDividerX - WELL_GAP;
        this.fieldLeft = this.wellLeft + ChatInputBar.BAR_GAP;
        this.fieldRight = this.wellRight - ChatInputBar.BAR_GAP
                - LostTalesUiCaret.WIDTH;
        this.rightDividerX = rightDividerX;
        this.counterLeft = counterLeft;
    }

    /**
     * The line from {@code buttonsRight}, just past the bar's leading
     * buttons, to the leftmost control's slot, with a counter slot
     * {@code counterWidth} pixels wide before the controls.
     */
    static ChatInputLine between(int buttonsRight, int controlsLeft,
                                 int counterWidth) {
        int counterLeft = controlsLeft - ChatInputBar.BAR_GAP
                - Math.max(0, counterWidth);
        return new ChatInputLine(dividerAfter(buttonsRight),
                counterLeft - ChatInputBar.BAR_GAP
                        - WindowStyle.DIVIDER_WIDTH,
                counterLeft);
    }

    /**
     * Where the divider after the bar's leading buttons stands: a gap
     * past {@code buttonsRight}, the head button's frame.
     */
    static int dividerAfter(int buttonsRight) {
        return buttonsRight + ChatInputBar.BAR_GAP;
    }
}

