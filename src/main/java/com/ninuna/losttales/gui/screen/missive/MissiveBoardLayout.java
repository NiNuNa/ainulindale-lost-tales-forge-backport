package com.ninuna.losttales.gui.screen.missive;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;

/**
 * Where every part of the missive board's page stands, worked out from
 * the page's box and the menus' row height alone: the notices in a list,
 * the panel the tool strip's left button folds away, beside the picked
 * notice's letter, the two split by one rule. The list stands as a
 * menu's rows do: from the page's side and its whole height, each row
 * across its whole width, the rows {@link MenuWindow#PADDING_Y} clear of
 * its top and foot and their words {@link MenuWindow#PADDING_X} in from
 * either side. A page too narrow for both shows one of them over the
 * whole page: the list while it is out, else the letter. Free of
 * Minecraft, so a test can ask it every question the page does.
 */
final class MissiveBoardLayout {
    /** Clear room between the page's edge and what it shows: a menu's, across and down. */
    public static final int MARGIN_X = MenuWindow.PADDING_X;
    public static final int MARGIN_Y = MenuWindow.PADDING_Y;
    /** Clear pixels either side of the rule, between it and the list's words and the letter. */
    static final int GUTTER = 10;
    /** The narrowest and widest the list's words are allowed to run. */
    static final int LIST_MIN_WIDTH = 120;
    static final int LIST_MAX_WIDTH = 170;
    /** The narrowest letter that still reads. */
    static final int LETTER_MIN_WIDTH = 160;
    /** The narrowest page the list and the letter both fit on. */
    static final int MIN_SPLIT_WIDTH = MenuWindow.PADDING_X + LIST_MIN_WIDTH
            + 2 * GUTTER + 1 + LETTER_MIN_WIDTH + MARGIN_X;
    /** Lines in one notice's row: its title, and its giver and time left under it. */
    static final int ROW_LINES = 2;

    private final int pageWidth;
    private final int pageHeight;
    /** How wide the list's words run beside the letter. */
    private final int listWidth;
    private final boolean listOut;
    private final int lineHeight;

    /**
     * {@code listOut}: whether the list is out, its button lit;
     * {@code lineHeight}: one line of a notice, a menu row's height.
     */
    MissiveBoardLayout(int pageWidth, int pageHeight, boolean listOut,
                       int lineHeight) {
        this.pageWidth = Math.max(0, pageWidth);
        this.pageHeight = Math.max(0, pageHeight);
        this.listWidth = Math.max(LIST_MIN_WIDTH,
                Math.min(LIST_MAX_WIDTH, this.pageWidth / 3));
        this.listOut = listOut;
        this.lineHeight = Math.max(1, lineHeight);
    }

    /** Whether the page is wide enough for the list and the letter side by side. */
    boolean isWide() {
        return this.pageWidth >= MIN_SPLIT_WIDTH;
    }

    /** Whether the list and the letter stand side by side now. */
    boolean isSplit() {
        return isWide() && this.listOut;
    }

    /** Where the letter stands while it has the page to itself. */
    private LostTalesUiHitBox body() {
        return new LostTalesUiHitBox(MARGIN_X, MARGIN_Y,
                Math.max(0, this.pageWidth - 2 * MARGIN_X),
                Math.max(0, this.pageHeight - 2 * MARGIN_Y));
    }

    private static LostTalesUiHitBox none() {
        return new LostTalesUiHitBox(0, 0, 0, 0);
    }

    /**
     * The list: the page's left beside the letter, its words and their
     * padding wide, the whole page while the two take turns, and empty
     * while it is folded away.
     */
    LostTalesUiHitBox list() {
        if (!this.listOut) {
            return none();
        }
        if (!isWide()) {
            return new LostTalesUiHitBox(0, 0, this.pageWidth,
                    this.pageHeight);
        }
        return new LostTalesUiHitBox(0, 0,
                this.listWidth + 2 * MenuWindow.PADDING_X, this.pageHeight);
    }

    /** The band the rows show in: the list less its padding above and below. */
    LostTalesUiHitBox band() {
        LostTalesUiHitBox list = list();
        return new LostTalesUiHitBox(list.left,
                list.top + MenuWindow.PADDING_Y, list.width,
                Math.max(0.0D, list.height - 2 * MenuWindow.PADDING_Y));
    }

    /** Where every row's words start. */
    int textLeft() {
        return (int)list().left + MenuWindow.PADDING_X;
    }

    /** Where every row's words end. */
    int textRight() {
        return (int)list().right() - MenuWindow.PADDING_X;
    }

    /** One line of a notice: a menu row's height. */
    int lineHeight() {
        return this.lineHeight;
    }

    /** One notice: {@link #ROW_LINES} lines. */
    int rowHeight() {
        return ROW_LINES * this.lineHeight;
    }

    /** The rule between the list and the letter, {@link #GUTTER} clear of the list's words; empty unless they stand side by side. */
    LostTalesUiHitBox divider() {
        if (!isSplit()) {
            return none();
        }
        LostTalesUiHitBox body = body();
        return new LostTalesUiHitBox(textRight() + GUTTER, body.top, 1,
                body.height);
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
        return new LostTalesUiHitBox(left, MARGIN_Y,
                Math.max(0, this.pageWidth - MARGIN_X - left),
                Math.max(0, this.pageHeight - 2 * MARGIN_Y));
    }

    /** The notice's row at {@code index} of the list, scrolled by {@code scroll} pixels, across the list's whole width. */
    LostTalesUiHitBox row(int index, double scroll) {
        LostTalesUiHitBox list = list();
        return new LostTalesUiHitBox(list.left, band().top - scroll
                + index * rowHeight(), list.width, rowHeight());
    }

    /**
     * The index of the row drawn at the point, the list scrolled by
     * {@code scroll}: the row's whole width, cut to the band; -1 outside
     * the band or past the last row.
     */
    int rowAt(double x, double y, double scroll, int rows) {
        LostTalesUiHitBox band = band();
        if (!band.contains(x, y)) {
            return -1;
        }
        int index = (int)Math.floor((y - band.top + scroll) / rowHeight());
        return index >= 0 && index < rows ? index : -1;
    }

    /** How far the list scrolls at most for {@code rows} rows. */
    int maxListScroll(int rows) {
        return Math.max(0, rows * rowHeight() - (int)band().height);
    }
}
