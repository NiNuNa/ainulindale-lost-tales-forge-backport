package com.ninuna.losttales.client.armour;

import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where the set bonuses stand in the effect column left of an inventory
 * (CONTENT-12): one popup each, under the potion boxes vanilla draws
 * there and of their width, their right edges in line. A popup holds the
 * worn helmet's icon, the set's name and a line for each thing the bonus
 * does. Narrower where the inventory leaves less room, none where it
 * leaves too little; short of room down the screen, every entry shows
 * its name alone, and one that still reaches past the foot is left out.
 * Whole numbers only, so it is tested without a game.
 */
public final class SetBonusColumnLayout {
    /** Where vanilla's potion boxes start, left of the inventory. */
    static final int POTION_OFFSET = 124;
    /** A potion box as vanilla draws it: wide, tall, and the step from one to the next. */
    static final int POTION_WIDTH = 120;
    static final int POTION_HEIGHT = 32;
    static final int POTION_STEP = 33;
    /** More potions than this share {@link #POTION_ROOM} between them, overlapping. */
    static final int POTIONS_UNSQUEEZED = 5;
    static final int POTION_ROOM = 132;
    /** Clear room between the column and the inventory, as vanilla's boxes keep it, and the screen's edges. */
    static final int GAP = POTION_OFFSET - POTION_WIDTH;
    /** An entry's width, as a potion box's. */
    public static final int WIDTH = POTION_WIDTH;
    /** An item icon, as a slot shows it. */
    public static final int ICON = 16;
    /** Between the icon and the words, and between two entries. */
    public static final int SPACING = WindowStyle.BUTTON_GAP;
    /** The fewest pixels of words an entry is drawn for. */
    static final int MIN_WORDS = 20;
    /** The narrowest entry: its icon, the fewest words, the inset both sides. */
    static final int MIN_WIDTH = WindowStyle.POPUP_INSET * 2 + ICON + SPACING + MIN_WORDS;

    private SetBonusColumnLayout() {}

    /** One entry's popup and where its parts stand in it. */
    public static final class Box {
        public final int left;
        public final int top;
        public final int width;
        public final int height;
        /** How many lines of what the bonus does it shows: none where it is folded. */
        public final int effectLines;

        Box(int left, int top, int width, int height, int effectLines) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.effectLines = effectLines;
        }

        public int right() {
            return this.left + this.width;
        }

        public int bottom() {
            return this.top + this.height;
        }

        /** The icon's left edge, the inset in. */
        public int iconLeft() {
            return this.left + WindowStyle.POPUP_INSET;
        }

        /** The icon's top edge, centred down the entry. */
        public int iconTop() {
            return this.top + WindowStyle.POPUP_INSET + LostTalesUiInk.centredStart(content(), ICON);
        }

        /** Where the words start, past the icon. */
        public int textLeft() {
            return iconLeft() + ICON + SPACING;
        }

        /** The room the words have before the inset at the right. */
        public int textRoom() {
            return right() - WindowStyle.POPUP_INSET - textLeft();
        }

        /** The top of the name's capitals; each line under it a line further. */
        public int textTop() {
            int ink = this.effectLines * WindowStyle.LINE_HEIGHT + LostTalesUiInk.CAP_HEIGHT;
            return this.top + WindowStyle.POPUP_INSET + LostTalesUiInk.centredStart(content(), ink);
        }

        private int content() {
            return this.height - WindowStyle.POPUP_INSET * 2;
        }
    }

    /**
     * The entries' popups for an inventory at {@code guiLeft},
     * {@code guiTop} on a screen {@code screenHeight} tall, below the
     * {@code potionsShown} potion boxes vanilla draws, each entry
     * {@code effectLines} lines of what its bonus does. Empty where the
     * inventory leaves too little room beside it.
     */
    public static List<Box> place(int guiLeft, int guiTop, int screenHeight, int potionsShown,
                                  int[] effectLines) {
        if (effectLines == null || effectLines.length == 0) {
            return Collections.<Box>emptyList();
        }
        int right = guiLeft - GAP;
        int width = Math.min(WIDTH, right - GAP);
        if (width < MIN_WIDTH) {
            return Collections.<Box>emptyList();
        }
        int top = firstTop(guiTop, potionsShown);
        int foot = screenHeight - GAP;
        boolean folded = stackHeight(effectLines) > foot - top;
        List<Box> boxes = new ArrayList<Box>();
        int y = top;
        for (int lines : effectLines) {
            int shown = folded ? 0 : Math.max(0, lines);
            int height = height(shown);
            if (y + height > foot) {
                break;
            }
            boxes.add(new Box(right - width, y, width, height, shown));
            y += height + SPACING;
        }
        return boxes;
    }

    /** Where the first entry stands: the inventory's top, or under the last potion box. */
    static int firstTop(int guiTop, int potionsShown) {
        if (potionsShown <= 0) {
            return guiTop;
        }
        int step = potionsShown > POTIONS_UNSQUEEZED
                ? POTION_ROOM / (potionsShown - 1) : POTION_STEP;
        return guiTop + (potionsShown - 1) * step + POTION_HEIGHT + SPACING;
    }

    /** An entry's height: a popup of its name and its lines, never shorter than its icon and the inset. */
    static int height(int effectLines) {
        return Math.max(WindowStyle.POPUP_INSET * 2 + ICON,
                WindowStyle.POPUP_LINE_HEIGHT + Math.max(0, effectLines) * WindowStyle.LINE_HEIGHT);
    }

    /** How tall the entries stand, one under another, none folded. */
    private static int stackHeight(int[] effectLines) {
        int total = 0;
        for (int index = 0; index < effectLines.length; index++) {
            total += height(effectLines[index]) + (index > 0 ? SPACING : 0);
        }
        return total;
    }
}
