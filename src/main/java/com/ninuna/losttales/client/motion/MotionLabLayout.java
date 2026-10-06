package com.ninuna.losttales.client.motion;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;

/**
 * Where every part of the Motion Lab stands, worked out from the page's
 * box and the menus' row height alone.
 *
 * <p>The page is the list of motions, a panel the tool strip's left
 * button folds away, beside the picked motion, split by one rule. A page
 * too narrow for both shows one of them over the whole page: the list
 * while it is out, else the motion. Each stands in a band as a menu's
 * rows do: from the page's side to the gutter, {@link MenuWindow#PADDING_Y}
 * clear above and below, what it holds {@link MenuWindow#PADDING_X} in
 * from the band's edges, and every row one menu row high. The motion's
 * column holds its name, what it is for, the sample and then its rows,
 * the only part of it that scrolls. Each row ends in a stepper: a chevron
 * either side of the value.</p>
 *
 * <p>Free of Minecraft: the geometry is arithmetic, and a test can ask it
 * every question the page does.</p>
 */
final class MotionLabLayout {
    /** Clear pixels either side of the rule between the list and the motion. */
    static final int GUTTER = 10;
    /** The narrowest and widest the list's band is allowed to be. */
    static final int LIST_MIN_WIDTH = 120;
    static final int LIST_MAX_WIDTH = 170;
    /** The narrowest motion column the rows still read in. */
    static final int CONTENT_MIN_WIDTH = 180;
    /** The narrowest page the list and the motion both fit on. */
    static final int MIN_SPLIT_WIDTH = LIST_MIN_WIDTH + 2 * GUTTER + 1
            + 2 * MenuWindow.PADDING_X + CONTENT_MIN_WIDTH;
    /** Each line of what a motion is for. */
    static final int ABOUT_LINE = 10;
    /** The most lines of what a motion is for the column shows; the last is cut short where more is left. */
    static final int MAX_ABOUT_LINES = 6;
    /** The rows the column keeps room for before it gives lines to what a motion is for. */
    static final int MIN_ROWS = 4;
    /** Clear pixels between what a motion is for and the sample. */
    static final int GAP = 4;
    /** The sample's height: a third of the column, within these. */
    static final int SAMPLE_MIN_HEIGHT = 32;
    static final int SAMPLE_MAX_HEIGHT = 64;
    /** The stepper's width: two fifths of a row, within these. */
    static final int STEPPER_MIN_WIDTH = 72;
    static final int STEPPER_MAX_WIDTH = 110;

    private final int pageWidth;
    private final int pageHeight;
    private final int rowHeight;
    private final int listWidth;
    private final boolean listOut;
    private final int aboutLines;

    /**
     * {@code rowHeight}: the menus' row height, every row's;
     * {@code listOut}: whether the list is out, its button lit;
     * {@code aboutLines}: how many lines what the motion is for wraps to.
     */
    MotionLabLayout(int pageWidth, int pageHeight, int rowHeight,
                    boolean listOut, int aboutLines) {
        this.pageWidth = Math.max(0, pageWidth);
        this.pageHeight = Math.max(0, pageHeight);
        this.rowHeight = Math.max(1, rowHeight);
        this.listWidth = Math.max(LIST_MIN_WIDTH,
                Math.min(LIST_MAX_WIDTH, this.pageWidth / 3));
        this.listOut = listOut;
        this.aboutLines = Math.max(0, aboutLines);
    }

    /** How tall every row of the list and of the motion is. */
    int rowHeight() {
        return this.rowHeight;
    }

    /** Whether the page is wide enough for the list and the motion side by side. */
    boolean isWide() {
        return this.pageWidth >= MIN_SPLIT_WIDTH;
    }

    /** Whether the list and the motion stand side by side now. */
    boolean isSplit() {
        return isWide() && this.listOut;
    }

    /** The whole page's width, a menu's padding clear above and below. */
    private LostTalesUiHitBox body() {
        return new LostTalesUiHitBox(0, MenuWindow.PADDING_Y, this.pageWidth,
                Math.max(0, this.pageHeight - 2 * MenuWindow.PADDING_Y));
    }

    private static LostTalesUiHitBox none() {
        return new LostTalesUiHitBox(0, 0, 0, 0);
    }

    /**
     * The list's band: the page's left beside the motion, the whole body
     * while the two take turns, and empty while it is folded away.
     */
    LostTalesUiHitBox list() {
        if (!this.listOut) {
            return none();
        }
        LostTalesUiHitBox body = body();
        if (!isWide()) {
            return body;
        }
        return new LostTalesUiHitBox(0, body.top, this.listWidth,
                body.height);
    }

    /** The rule between the list and the motion; empty unless they stand side by side. */
    LostTalesUiHitBox divider() {
        if (!isSplit()) {
            return none();
        }
        LostTalesUiHitBox list = list();
        return new LostTalesUiHitBox(list.right() + GUTTER, list.top, 1,
                list.height);
    }

    /**
     * The motion's band: right of the rule to the page's right beside the
     * list, the whole body while the list is folded, and empty while a
     * narrow page shows the list.
     */
    LostTalesUiHitBox content() {
        if (!isSplit()) {
            return this.listOut ? none() : body();
        }
        int left = (int)divider().right() + GUTTER;
        LostTalesUiHitBox body = body();
        return new LostTalesUiHitBox(left, body.top,
                Math.max(0, this.pageWidth - left), body.height);
    }

    /** The motion's column: its band less a menu's padding at either side. */
    LostTalesUiHitBox column() {
        LostTalesUiHitBox content = content();
        if (content.width <= 0) {
            return none();
        }
        return new LostTalesUiHitBox(content.left + MenuWindow.PADDING_X,
                content.top, Math.max(0.0D,
                        content.width - 2 * MenuWindow.PADDING_X),
                content.height);
    }

    /** The motion's name, one row at the column's top. */
    LostTalesUiHitBox name() {
        LostTalesUiHitBox column = column();
        return new LostTalesUiHitBox(column.left, column.top, column.width,
                column.width <= 0 ? 0 : this.rowHeight);
    }

    /**
     * How many lines of what the motion is for the column shows: as many
     * as it wraps to, up to {@link #MAX_ABOUT_LINES}, and no more than
     * leave room for {@link #MIN_ROWS} rows.
     */
    int aboutLines() {
        LostTalesUiHitBox content = content();
        if (content.width <= 0) {
            return 0;
        }
        int room = (int)content.height - this.rowHeight - GAP - sampleHeight()
                - MenuWindow.PADDING_Y - MIN_ROWS * this.rowHeight;
        return Math.max(0, Math.min(Math.min(this.aboutLines, MAX_ABOUT_LINES),
                room / ABOUT_LINE));
    }

    /** What the motion is for, under its name. */
    LostTalesUiHitBox about() {
        LostTalesUiHitBox name = name();
        return new LostTalesUiHitBox(name.left, name.bottom(), name.width,
                aboutLines() * ABOUT_LINE);
    }

    /** The sample's height: a third of the column, within its bounds. */
    private int sampleHeight() {
        return Math.max(SAMPLE_MIN_HEIGHT, Math.min(SAMPLE_MAX_HEIGHT,
                (int)content().height / 3));
    }

    /** The sample, under what the motion is for. */
    LostTalesUiHitBox sample() {
        LostTalesUiHitBox column = column();
        if (column.width <= 0) {
            return none();
        }
        return new LostTalesUiHitBox(column.left, about().bottom() + GAP,
                column.width, sampleHeight());
    }

    /**
     * The rows' band: the motion's band under the sample, a menu's
     * padding below it, where the rows scroll; empty where no room is
     * left.
     */
    LostTalesUiHitBox rows() {
        LostTalesUiHitBox content = content();
        if (content.width <= 0) {
            return none();
        }
        double top = sample().bottom() + MenuWindow.PADDING_Y;
        return new LostTalesUiHitBox(content.left, top, content.width,
                Math.max(0.0D, content.bottom() - top));
    }

    /** Where a row's label starts. */
    int rowLeft() {
        return (int)rows().left + MenuWindow.PADDING_X;
    }

    /** Where a row's stepper ends. */
    int rowRight() {
        return (int)rows().right() - MenuWindow.PADDING_X;
    }

    /** How wide a row's stepper is. */
    int stepperWidth() {
        return Math.max(STEPPER_MIN_WIDTH, Math.min(STEPPER_MAX_WIDTH,
                (rowRight() - rowLeft()) * 2 / 5));
    }

    /** The room a row's label has: the row less the stepper and the gap before it. */
    int labelWidth() {
        return Math.max(0, rowRight() - rowLeft() - stepperWidth() - MenuWindow.VALUE_GAP);
    }

    /** The chevron stepping back, at the stepper's left, for a row whose top is {@code top}. */
    LostTalesUiHitBox less(double top) {
        return new LostTalesUiHitBox(rowRight() - stepperWidth(), top,
                MenuWindow.STEPPER_CELL, this.rowHeight);
    }

    /** The value between the chevrons. */
    LostTalesUiHitBox value(double top) {
        LostTalesUiHitBox less = less(top);
        return new LostTalesUiHitBox(less.right(), top,
                Math.max(0, stepperWidth() - 2 * MenuWindow.STEPPER_CELL), this.rowHeight);
    }

    /** The chevron stepping on, at the stepper's right. */
    LostTalesUiHitBox more(double top) {
        return new LostTalesUiHitBox(rowRight() - MenuWindow.STEPPER_CELL, top,
                MenuWindow.STEPPER_CELL, this.rowHeight);
    }
}
