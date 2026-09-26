package com.ninuna.losttales.gui.screen.quest;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;

/**
 * Where every part of the quest journal stands, worked out from the
 * page's box alone.
 *
 * <p>The page is its body, split by one rule into the quest list and what
 * the chosen quest says. Its name, its filters, its search and its actions
 * are the window's: its tab, its tool strip's cog and well, and its input
 * bar. The list is a panel the strip's
 * left button folds away, and a page too narrow for both halves shows
 * one of them over the whole body: the list while it is out, else the
 * details. Each area is a box, and the drawing and the pointer ask the
 * same box, so a control answers on exactly the pixels it was drawn
 * in.</p>
 *
 * <p>Free of Minecraft: the geometry is arithmetic, and a test can ask
 * it every question the page does.</p>
 */
public final class QuestJournalLayout {

    /** Clear pixels between the page's edge and anything drawn. */
    public static final int MARGIN = 8;
    /** One quest's row: the chat's line stride, so text sits the same. */
    public static final int ROW_STRIDE = 12;
    /** A category's row, which carries its rule and a little more air. */
    public static final int CATEGORY_STRIDE = 15;
    /** Clear pixels either side of the rule dividing the two halves. */
    public static final int GUTTER = 10;
    /** The narrowest and widest the quest list is allowed to be. */
    public static final int LIST_MIN_WIDTH = 150;
    public static final int LIST_MAX_WIDTH = 220;
    /** The scrollbar's column, inside the list's right edge. */
    public static final int SCROLLBAR_WIDTH = 3;
    /**
     * The narrowest page the two halves both fit on. Under it they take
     * turns: the list while it is out, else the details.
     */
    public static final int MIN_SPLIT_WIDTH =
            2 * MARGIN + LIST_MIN_WIDTH + 2 * GUTTER + 1 + 120;

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

    /** The first row of the body, and the row past its last. */
    public int bodyTop() {
        return MARGIN;
    }

    public int bodyBottom() {
        return Math.max(MARGIN, this.pageHeight - MARGIN);
    }

    /** The whole body between the margins, which one half takes when they take turns. */
    private LostTalesUiHitBox body() {
        return new LostTalesUiHitBox(MARGIN, bodyTop(),
                Math.max(0, this.pageWidth - 2 * MARGIN),
                Math.max(0, bodyBottom() - bodyTop()));
    }

    private static LostTalesUiHitBox none() {
        return new LostTalesUiHitBox(0, 0, 0, 0);
    }

    /**
     * The quest list, scrollbar included: the left of the body beside
     * the details, the whole body while the halves take turns, and empty
     * while it is folded away.
     */
    public LostTalesUiHitBox list() {
        if (!this.listOut) {
            return none();
        }
        if (!isWide()) {
            return body();
        }
        return new LostTalesUiHitBox(MARGIN, bodyTop(), this.listWidth,
                Math.max(0, bodyBottom() - bodyTop()));
    }

    /** The column a list row's text and glyphs may use. */
    public LostTalesUiHitBox listRows() {
        LostTalesUiHitBox list = list();
        return new LostTalesUiHitBox(list.left, list.top,
                Math.max(0, list.width - SCROLLBAR_WIDTH - 2), list.height);
    }

    /** The rule between the halves; empty unless they stand side by side. */
    public LostTalesUiHitBox divider() {
        if (!isSplit()) {
            return none();
        }
        LostTalesUiHitBox list = list();
        return new LostTalesUiHitBox(list.right() + GUTTER, list.top, 1,
                list.height);
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
                Math.max(0, this.pageWidth - MARGIN - left),
                Math.max(0, bodyBottom() - bodyTop()));
    }

    /** The column the detail's text may use, its scrollbar left out. */
    public LostTalesUiHitBox detailRows() {
        LostTalesUiHitBox detail = detail();
        return new LostTalesUiHitBox(detail.left, detail.top,
                Math.max(0, detail.width - SCROLLBAR_WIDTH - 2), detail.height);
    }

    /**
     * A scrollbar's column inside {@code area}'s right edge, or an empty
     * box where there is nothing to scroll.
     */
    public static LostTalesUiHitBox scrollbar(LostTalesUiHitBox area,
                                              int contentHeight) {
        if (area == null || area.width <= 0.0D || area.height <= 0.0D
                || contentHeight <= area.height) {
            return none();
        }
        return new LostTalesUiHitBox(area.right() - SCROLLBAR_WIDTH, area.top,
                SCROLLBAR_WIDTH, area.height);
    }

    /**
     * The handle inside a scrollbar for a view {@code scroll} pixels down
     * a {@code contentHeight}-tall run: at least a square, and never past
     * either end.
     */
    public static LostTalesUiHitBox scrollHandle(LostTalesUiHitBox bar,
                                                 int contentHeight,
                                                 double scroll) {
        if (bar == null || bar.height <= 0.0D || contentHeight <= bar.height) {
            return none();
        }
        double visible = bar.height;
        double handle = Math.max(SCROLLBAR_WIDTH * 2,
                visible * visible / contentHeight);
        double travel = visible - handle;
        double hidden = contentHeight - visible;
        double at = hidden <= 0.0D ? 0.0D
                : Math.max(0.0D, Math.min(1.0D, scroll / hidden));
        return new LostTalesUiHitBox(bar.left, bar.top + travel * at,
                bar.width, handle);
    }
}
