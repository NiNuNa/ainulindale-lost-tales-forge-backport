package com.ninuna.losttales.gui.screen.quest;

import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;

/**
 * Where every part of the quest journal stands, worked out from the
 * screen alone.
 *
 * <p>The screen is read top to bottom: a header strip carrying the
 * title, the filters and the search; the body, split by one rule into
 * the quest list and what the chosen quest says; and an action strip
 * under it. Each area is a box, and the drawing and the pointer ask the
 * same box, so a control answers on exactly the pixels it was drawn
 * in.</p>
 *
 * <p>Free of Minecraft: the geometry is arithmetic, and a test can ask
 * it every question the screen does.</p>
 */
public final class QuestJournalLayout {

    /** The strip at the top; the chat's bar height, so the two agree. */
    public static final int HEADER_HEIGHT = 25;
    /** The strip at the bottom, a framed button with room above and below. */
    public static final int ACTION_HEIGHT =
            LostTalesUiFramedButton.HEIGHT + 2 * 4;
    /** Clear pixels between the screen's edge and anything drawn. */
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
    /** Clear pixels between a strip's edge and the controls on it. */
    public static final int CONTROL_INSET = 6;
    /** Clear pixels between two controls standing side by side. */
    public static final int CONTROL_GAP = 4;
    /**
     * The smallest screen the two halves both fit on. Under it the
     * detail pane alone is drawn, and the list is reached by closing it.
     */
    public static final int MIN_SPLIT_WIDTH =
            2 * MARGIN + LIST_MIN_WIDTH + 2 * GUTTER + 1 + 120;

    private final int screenWidth;
    private final int screenHeight;
    private final int listWidth;

    public QuestJournalLayout(int screenWidth, int screenHeight) {
        this.screenWidth = Math.max(0, screenWidth);
        this.screenHeight = Math.max(0, screenHeight);
        this.listWidth = Math.max(LIST_MIN_WIDTH,
                Math.min(LIST_MAX_WIDTH, this.screenWidth / 3));
    }

    /** Whether there is room for the list and the detail side by side. */
    public boolean isSplit() {
        return this.screenWidth >= MIN_SPLIT_WIDTH;
    }

    /** The strip across the top. */
    public LostTalesUiHitBox header() {
        return new LostTalesUiHitBox(0, 0, this.screenWidth, HEADER_HEIGHT);
    }

    /** The one-pixel rule under the header. */
    public LostTalesUiHitBox headerRule() {
        return new LostTalesUiHitBox(0, HEADER_HEIGHT, this.screenWidth, 1);
    }

    /** The strip across the bottom. */
    public LostTalesUiHitBox actions() {
        return new LostTalesUiHitBox(0,
                Math.max(HEADER_HEIGHT + 1, this.screenHeight - ACTION_HEIGHT),
                this.screenWidth, ACTION_HEIGHT);
    }

    /** The one-pixel rule over the action strip. */
    public LostTalesUiHitBox actionRule() {
        return new LostTalesUiHitBox(0, actions().top - 1,
                this.screenWidth, 1);
    }

    /** The first row of the body, and the row past its last. */
    public int bodyTop() {
        return HEADER_HEIGHT + 1 + MARGIN;
    }

    public int bodyBottom() {
        return (int)actions().top - 1 - MARGIN;
    }

    /** The quest list, scrollbar included; empty on a narrow screen. */
    public LostTalesUiHitBox list() {
        if (!isSplit()) {
            return new LostTalesUiHitBox(0, 0, 0, 0);
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

    /** The rule between the halves; empty on a narrow screen. */
    public LostTalesUiHitBox divider() {
        if (!isSplit()) {
            return new LostTalesUiHitBox(0, 0, 0, 0);
        }
        LostTalesUiHitBox list = list();
        return new LostTalesUiHitBox(list.right() + GUTTER, list.top, 1,
                list.height);
    }

    /** What the chosen quest says. */
    public LostTalesUiHitBox detail() {
        int left = isSplit()
                ? (int)divider().right() + GUTTER : MARGIN;
        return new LostTalesUiHitBox(left, bodyTop(),
                Math.max(0, this.screenWidth - MARGIN - left),
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
            return new LostTalesUiHitBox(0, 0, 0, 0);
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
            return new LostTalesUiHitBox(0, 0, 0, 0);
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

    /**
     * A row of framed buttons laid left to right from {@code left},
     * vertically centred in {@code strip}: the box of the button at
     * {@code index} given every button's width in order.
     */
    public static LostTalesUiHitBox buttonAt(LostTalesUiHitBox strip,
                                             double left, int[] widths,
                                             int index) {
        if (strip == null || widths == null || index < 0
                || index >= widths.length) {
            return new LostTalesUiHitBox(0, 0, 0, 0);
        }
        double x = left;
        for (int before = 0; before < index; before++) {
            x += Math.max(LostTalesUiFramedButton.MIN_SIZE, widths[before])
                    + CONTROL_GAP;
        }
        double top = strip.top
                + (strip.height - LostTalesUiFramedButton.HEIGHT) / 2.0D;
        return new LostTalesUiHitBox(x, Math.floor(top),
                Math.max(LostTalesUiFramedButton.MIN_SIZE, widths[index]),
                LostTalesUiFramedButton.HEIGHT);
    }

    /** The width a row of buttons of these widths takes up in all. */
    public static int buttonsWidth(int[] widths) {
        if (widths == null || widths.length == 0) {
            return 0;
        }
        int total = 0;
        for (int index = 0; index < widths.length; index++) {
            total += Math.max(LostTalesUiFramedButton.MIN_SIZE, widths[index]);
        }
        return total + CONTROL_GAP * (widths.length - 1);
    }

    /** The width a framed button needs to hold {@code contentWidth}. */
    public static int buttonWidthFor(int contentWidth) {
        return Math.max(LostTalesUiFramedButton.MIN_SIZE,
                contentWidth + 2 * LostTalesUiFramedButton.WIDE_INSET);
    }
}
