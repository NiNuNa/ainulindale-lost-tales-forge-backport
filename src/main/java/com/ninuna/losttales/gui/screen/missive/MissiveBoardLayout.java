package com.ninuna.losttales.gui.screen.missive;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;

/**
 * Where every part of the missive board's page stands, worked out from
 * the page's box alone: the notices in a list, the panel the tool
 * strip's left button folds away, beside the picked notice's letter, the
 * two split by one rule. A page too
 * narrow for both shows one of them over the whole body: the list while
 * it is out, else the letter. Free of Minecraft, so a test can ask it
 * every question the page does.
 */
final class MissiveBoardLayout {
    /** Clear pixels between the page's edge and anything drawn. */
    static final int MARGIN = 8;
    /** Clear pixels either side of the rule between the list and the letter. */
    static final int GUTTER = 10;
    /** The narrowest and widest the list is allowed to be. */
    static final int LIST_MIN_WIDTH = 120;
    static final int LIST_MAX_WIDTH = 170;
    /** The narrowest letter that still reads. */
    static final int LETTER_MIN_WIDTH = 160;
    /** The narrowest page the list and the letter both fit on. */
    static final int MIN_SPLIT_WIDTH =
            2 * MARGIN + LIST_MIN_WIDTH + 2 * GUTTER + 1 + LETTER_MIN_WIDTH;
    /** One notice in the list: its title, and its giver and time left under it. */
    static final int ROW_HEIGHT = 24;
    /** How far a row's surface reaches left of the list's words, where it also answers the pointer. */
    static final int ROW_BLEED = 2;

    private final int pageWidth;
    private final int pageHeight;
    private final int listWidth;
    private final boolean listOut;

    /** {@code listOut}: whether the list is out, its button lit. */
    MissiveBoardLayout(int pageWidth, int pageHeight, boolean listOut) {
        this.pageWidth = Math.max(0, pageWidth);
        this.pageHeight = Math.max(0, pageHeight);
        this.listWidth = Math.max(LIST_MIN_WIDTH,
                Math.min(LIST_MAX_WIDTH, this.pageWidth / 3));
        this.listOut = listOut;
    }

    /** Whether the page is wide enough for the list and the letter side by side. */
    boolean isWide() {
        return this.pageWidth >= MIN_SPLIT_WIDTH;
    }

    /** Whether the list and the letter stand side by side now. */
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
     * The list: the left of the body beside the letter, the whole body
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

    /** The rule between the list and the letter; empty unless they stand side by side. */
    LostTalesUiHitBox divider() {
        if (!isSplit()) {
            return none();
        }
        LostTalesUiHitBox list = list();
        return new LostTalesUiHitBox(list.right() + GUTTER, list.top, 1,
                list.height);
    }

    /**
     * The letter: right of the rule beside the list, the whole body while
     * the list is folded, and empty while a narrow page shows the list.
     */
    LostTalesUiHitBox letter() {
        if (!isSplit()) {
            return this.listOut ? none() : body();
        }
        int left = (int)divider().right() + GUTTER;
        return new LostTalesUiHitBox(left, MARGIN,
                Math.max(0, this.pageWidth - MARGIN - left),
                Math.max(0, this.pageHeight - 2 * MARGIN));
    }

    /** The notice's row at {@code index} of the list, scrolled by {@code scroll} pixels. */
    LostTalesUiHitBox row(int index, double scroll) {
        LostTalesUiHitBox list = list();
        return new LostTalesUiHitBox(list.left, list.top - scroll
                + index * ROW_HEIGHT, list.width, ROW_HEIGHT);
    }

    /**
     * The index of the row drawn at the point, the list scrolled by
     * {@code scroll}: its surface's footprint, the bleed at its left
     * included; -1 outside the list.
     */
    int rowAt(double x, double y, double scroll, int rows) {
        LostTalesUiHitBox list = list();
        if (list.width <= 0 || !LostTalesUiHitBox.contains(x, y,
                list.left - ROW_BLEED, list.top, list.width + ROW_BLEED,
                list.height)) {
            return -1;
        }
        int index = (int)Math.floor((y - list.top + scroll) / ROW_HEIGHT);
        return index >= 0 && index < rows ? index : -1;
    }

    /** How far the list scrolls at most for {@code rows} rows. */
    int maxListScroll(int rows) {
        return Math.max(0, rows * ROW_HEIGHT - (int)list().height);
    }
}
