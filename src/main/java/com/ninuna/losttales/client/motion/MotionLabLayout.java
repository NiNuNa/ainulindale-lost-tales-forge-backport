package com.ninuna.losttales.client.motion;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;

/**
 * Where every part of the Motion Lab stands, worked out from the page's
 * box alone.
 *
 * <p>The page is the list of motions, a panel the tool strip's left
 * button folds away, beside the picked motion, split by one rule. A page
 * too narrow for both shows one of them over the whole body: the list
 * while it is out, else the motion. The motion's column holds its name,
 * what it is for, the sample and then its rows, the only part that
 * scrolls. Each row ends in a stepper: a chevron either side
 * of the value.</p>
 *
 * <p>Free of Minecraft: the geometry is arithmetic, and a test can ask it
 * every question the page does.</p>
 */
final class MotionLabLayout {
    /** Clear pixels between the page's edge and anything drawn. */
    static final int MARGIN = 8;
    /** Clear pixels either side of the rule between the list and the motion. */
    static final int GUTTER = 10;
    /** The narrowest and widest the list is allowed to be. */
    static final int LIST_MIN_WIDTH = 120;
    static final int LIST_MAX_WIDTH = 170;
    /** The narrowest motion column the rows still read in. */
    static final int CONTENT_MIN_WIDTH = 180;
    /** The narrowest page the list and the motion both fit on. */
    static final int MIN_SPLIT_WIDTH =
            2 * MARGIN + LIST_MIN_WIDTH + 2 * GUTTER + 1 + CONTENT_MIN_WIDTH;
    /** A family's heading in the list, and a motion's line. */
    static final int HEADING_HEIGHT = 15;
    static final int LINE_HEIGHT = 12;
    /** The motion's name, and each line of what it is for. */
    static final int NAME_HEIGHT = 12;
    static final int ABOUT_LINE = 10;
    /** The most lines of what a motion is for the column shows; the last is cut short where more is left. */
    static final int MAX_ABOUT_LINES = 6;
    /** The rows the column keeps room for before it gives lines to what a motion is for. */
    static final int MIN_ROWS = 4;
    /** Clear pixels between the parts of the column. */
    static final int GAP = 4;
    /** The sample's height: a third of the column, within these. */
    static final int SAMPLE_MIN_HEIGHT = 32;
    static final int SAMPLE_MAX_HEIGHT = 64;
    /** One row of the motion. */
    static final int ROW_HEIGHT = 12;
    /** The stepper's width: two fifths of the rows, within these. */
    static final int STEPPER_MIN_WIDTH = 72;
    static final int STEPPER_MAX_WIDTH = 110;
    /** The square each of a stepper's chevrons answers on. */
    static final int CHEVRON_BOX = 9;

    private final int pageWidth;
    private final int pageHeight;
    private final int listWidth;
    private final boolean listOut;
    private final int aboutLines;

    /**
     * {@code listOut}: whether the list is out, its button lit;
     * {@code aboutLines}: how many lines what the motion is for wraps to.
     */
    MotionLabLayout(int pageWidth, int pageHeight, boolean listOut,
                    int aboutLines) {
        this.pageWidth = Math.max(0, pageWidth);
        this.pageHeight = Math.max(0, pageHeight);
        this.listWidth = Math.max(LIST_MIN_WIDTH,
                Math.min(LIST_MAX_WIDTH, this.pageWidth / 3));
        this.listOut = listOut;
        this.aboutLines = Math.max(0, aboutLines);
    }

    /** Whether the page is wide enough for the list and the motion side by side. */
    boolean isWide() {
        return this.pageWidth >= MIN_SPLIT_WIDTH;
    }

    /** Whether the list and the motion stand side by side now. */
    boolean isSplit() {
        return isWide() && this.listOut;
    }

    private LostTalesUiHitBox body() {
        return new LostTalesUiHitBox(MARGIN, MARGIN,
                Math.max(0, this.pageWidth - 2 * MARGIN),
                Math.max(0, this.pageHeight - 2 * MARGIN));
    }

    private static LostTalesUiHitBox none() {
        return new LostTalesUiHitBox(0, 0, 0, 0);
    }

    /**
     * The list: the left of the body beside the motion, the whole body
     * while the two take turns, and empty while it is folded away.
     */
    LostTalesUiHitBox list() {
        if (!this.listOut) {
            return none();
        }
        if (!isWide()) {
            return body();
        }
        return new LostTalesUiHitBox(MARGIN, MARGIN, this.listWidth,
                body().height);
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
     * The motion's column: right of the rule beside the list, the whole
     * body while the list is folded, and empty while a narrow page shows
     * the list.
     */
    LostTalesUiHitBox content() {
        if (!isSplit()) {
            return this.listOut ? none() : body();
        }
        int left = (int)divider().right() + GUTTER;
        return new LostTalesUiHitBox(left, MARGIN,
                Math.max(0, this.pageWidth - MARGIN - left),
                Math.max(0, this.pageHeight - 2 * MARGIN));
    }

    /** The motion's name, at the column's top. */
    LostTalesUiHitBox name() {
        LostTalesUiHitBox content = content();
        return new LostTalesUiHitBox(content.left, content.top, content.width,
                content.width <= 0 ? 0 : NAME_HEIGHT);
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
        int room = (int)content.height - NAME_HEIGHT - GAP - sampleHeight()
                - GAP - MIN_ROWS * ROW_HEIGHT;
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
        LostTalesUiHitBox content = content();
        if (content.width <= 0) {
            return none();
        }
        return new LostTalesUiHitBox(content.left, about().bottom() + GAP,
                content.width, sampleHeight());
    }

    /** The rows: the rest of the column under the sample, where they scroll; empty where no room is left. */
    LostTalesUiHitBox rows() {
        LostTalesUiHitBox content = content();
        if (content.width <= 0) {
            return none();
        }
        double top = sample().bottom() + GAP;
        return new LostTalesUiHitBox(content.left, top, content.width,
                Math.max(0.0D, content.bottom() - top));
    }

    /** How wide a row's stepper is. */
    int stepperWidth() {
        return Math.max(STEPPER_MIN_WIDTH, Math.min(STEPPER_MAX_WIDTH,
                (int)rows().width * 2 / 5));
    }

    /** The room a row's label has: the rows less the stepper and a gap. */
    int labelWidth() {
        return Math.max(0, (int)rows().width - stepperWidth() - GAP);
    }

    /** The chevron stepping back, at the stepper's left, for a row whose top is {@code top}. */
    LostTalesUiHitBox less(double top) {
        return new LostTalesUiHitBox(rows().right() - stepperWidth(), top,
                CHEVRON_BOX, ROW_HEIGHT);
    }

    /** The value between the chevrons. */
    LostTalesUiHitBox value(double top) {
        LostTalesUiHitBox less = less(top);
        return new LostTalesUiHitBox(less.right(), top,
                Math.max(0, stepperWidth() - 2 * CHEVRON_BOX), ROW_HEIGHT);
    }

    /** The chevron stepping on, at the stepper's right. */
    LostTalesUiHitBox more(double top) {
        return new LostTalesUiHitBox(rows().right() - CHEVRON_BOX, top,
                CHEVRON_BOX, ROW_HEIGHT);
    }
}
