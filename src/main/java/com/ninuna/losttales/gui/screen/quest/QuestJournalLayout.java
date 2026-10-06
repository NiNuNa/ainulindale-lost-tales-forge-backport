package com.ninuna.losttales.gui.screen.quest;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;

/**
 * Where every part of the quest journal stands, worked out from the
 * page's box alone.
 *
 * <p>The page is split by one rule into the quest list and what the
 * chosen quest says. Its name, its filters, its search and its actions
 * are the window's: its tab, its tool strip's options and well, and its input
 * bar. The list is a panel the strip's
 * left button folds away, and a page too narrow for both halves shows
 * one of them over the whole page: the list while it is out, else the
 * details. The list stands as a menu's rows do: from the page's left
 * edge, so a lit row reaches the window's frame, with a menu's padding
 * over and under its rows and either side of what a row holds. The
 * details keep a margin of their own. Each area is a box, and the
 * drawing and the pointer ask the same box, so a control answers on
 * exactly the pixels it was drawn in.</p>
 *
 * <p>Free of Minecraft: the geometry is arithmetic, and a test can ask
 * it every question the page does.</p>
 */
public final class QuestJournalLayout {

    /** Clear room between the page's edge and what it shows: a menu's, across and down. */
    public static final int MARGIN_X = MenuWindow.PADDING_X;
    public static final int MARGIN_Y = MenuWindow.PADDING_Y;
    /** Clear pixels either side of the rule dividing the two halves. */
    public static final int GUTTER = 10;
    /** The narrowest and widest the quest list is allowed to be. */
    public static final int LIST_MIN_WIDTH = 150;
    public static final int LIST_MAX_WIDTH = 220;
    /**
     * The narrowest page the two halves both fit on. Under it they take
     * turns: the list while it is out, else the details.
     */
    public static final int MIN_SPLIT_WIDTH =
            LIST_MIN_WIDTH + 2 * GUTTER + 1 + 120 + MARGIN_X;

    private final int pageWidth;
    private final int pageHeight;
    private final int listWidth;
    private final boolean listOut;

    /** {@code listOut}: whether the quest list is out, its button lit. */
    public QuestJournalLayout(int pageWidth, int pageHeight,
                              boolean listOut) {
        this.pageWidth = Math.max(0, pageWidth);
        this.pageHeight = Math.max(0, pageHeight);
        this.listWidth = Math.max(LIST_MIN_WIDTH,
                Math.min(LIST_MAX_WIDTH, this.pageWidth / 3));
        this.listOut = listOut;
    }

    /** Whether the page is wide enough for the list and the details side by side. */
    public boolean isWide() {
        return this.pageWidth >= MIN_SPLIT_WIDTH;
    }

    /** Whether the list and the details stand side by side now. */
    public boolean isSplit() {
        return isWide() && this.listOut;
    }

    /** The details' first row, and the row past their last. */
    public int bodyTop() {
        return MARGIN_Y;
    }

    public int bodyBottom() {
        return Math.max(MARGIN_Y, this.pageHeight - MARGIN_Y);
    }

    /** The whole page between the margins, which the details take while the list is folded. */
    private LostTalesUiHitBox body() {
        return new LostTalesUiHitBox(MARGIN_X, bodyTop(),
                Math.max(0, this.pageWidth - 2 * MARGIN_X),
                Math.max(0, bodyBottom() - bodyTop()));
    }

    private static LostTalesUiHitBox none() {
        return new LostTalesUiHitBox(0, 0, 0, 0);
    }

    /**
     * The band the quest list's rows show in, its scroll edge included:
     * from the page's left edge, a menu's padding under the page's top
     * and over its foot. The left of the page beside the details, the
     * page's whole width while the halves take turns, and empty while it
     * is folded away.
     */
    public LostTalesUiHitBox list() {
        if (!this.listOut) {
            return none();
        }
        return new LostTalesUiHitBox(0, MenuWindow.PADDING_Y,
                isWide() ? this.listWidth : this.pageWidth,
                Math.max(0, this.pageHeight - 2 * MenuWindow.PADDING_Y));
    }

    /**
     * The column a list row's glyph and words stand in: a menu's padding
     * in from either side of the list.
     */
    public LostTalesUiHitBox listRows() {
        LostTalesUiHitBox list = list();
        return new LostTalesUiHitBox(list.left + MenuWindow.PADDING_X,
                list.top, Math.max(0, list.width - 2 * MenuWindow.PADDING_X),
                list.height);
    }

    /**
     * The rule between the halves, as tall as the details; empty unless
     * they stand side by side.
     */
    public LostTalesUiHitBox divider() {
        if (!isSplit()) {
            return none();
        }
        return new LostTalesUiHitBox(list().right() + GUTTER, bodyTop(), 1,
                Math.max(0, bodyBottom() - bodyTop()));
    }

    /**
     * What the chosen quest says: right of the rule beside the list, the
     * whole body while the list is folded, and empty while a narrow page
     * shows the list.
     */
    public LostTalesUiHitBox detail() {
        if (!isSplit()) {
            return this.listOut ? none() : body();
        }
        int left = (int)divider().right() + GUTTER;
        return new LostTalesUiHitBox(left, bodyTop(),
                Math.max(0, this.pageWidth - MARGIN_X - left),
                Math.max(0, bodyBottom() - bodyTop()));
    }

    /** The column the detail's text may use, its scroll edge left clear. */
    public LostTalesUiHitBox detailRows() {
        LostTalesUiHitBox detail = detail();
        return new LostTalesUiHitBox(detail.left, detail.top,
                Math.max(0, detail.width - WindowLists.SCROLLBAR_ROOM - 2),
                detail.height);
    }

}
