package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.input.LostTalesKeyPress;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/**
 * Settings' rows standing in a page: headers, switches, few-word
 * options, numbers between their chevrons, typed lines and rows that act,
 * drawn, lit, scrolled and pressed exactly as a menu draws them in its
 * own window ({@link MenuWindow}). A page that edits a handful of values
 * wears Settings' look this way rather than one of its own. The page
 * builds the rows ({@link Settings.Setting#row}, or entries of its own)
 * and is told which was pressed; a number's or a line's field opens
 * through {@link Settings#openValue}.
 */
public final class PageRows {
    /** What a press on a row does: the page's own business. */
    public interface Taker {
        /**
         * A row taken; with {@code back}, by the right button. On a
         * number's row {@code part} names the chevron or the value
         * pressed ({@link MenuWindow#PART_LESS} and the others); null for
         * the row as a whole. {@code row} is where the row is drawn, in
         * the page's whole pixels, for a window opened from it.
         */
        void take(MenuWindow.Entry entry, String part, boolean back,
                  LostTalesUiHitBox row);
    }

    private final MenuWindow menu;
    private final Taker taker;
    /** Where the press being handed on landed, and the box it was in. */
    private LostTalesUiHitBox pressBox;
    private double pressX;
    private double pressY;

    public PageRows(Taker taker) {
        this.taker = taker;
        this.menu = new MenuWindow(new MenuWindow.Owner() {
            @Override
            public void take(MenuWindow menu, MenuWindow.Entry entry,
                             String part, boolean back) {
                if (entry != null && entry.isTakeable()
                        && PageRows.this.pressBox != null) {
                    PageRows.this.taker.take(entry, part, back,
                            menu.rowBoxAt(PageRows.this.pressBox,
                                    PageRows.this.pressX,
                                    PageRows.this.pressY));
                }
            }

            @Override
            public void keyTyped(MenuWindow menu, LostTalesKeyPress press) {}
        });
    }

    /** The rows, top first, in place of the ones it had; the scroll and each row's light carry on. */
    public void setRows(List<MenuWindow.Entry> rows) {
        this.menu.setRows(rows);
    }

    /** The widest a page's column of rows grows, however wide the window. */
    private static final int MAX_COLUMN_WIDTH = 320;

    /**
     * The column a page's rows stand in: the page's width up to
     * {@link #MAX_COLUMN_WIDTH}, centred, its whole height, so the rows
     * stand under the page's strip as a sub-window's stand under its own.
     */
    public static LostTalesUiHitBox column(LostTalesUiHitBox box) {
        int boxWidth = (int)Math.floor(box.width);
        int width = Math.max(0, Math.min(boxWidth, MAX_COLUMN_WIDTH));
        return new LostTalesUiHitBox(Math.floor(box.left)
                + LostTalesUiInk.centredStart(boxWidth, width),
                Math.floor(box.top), width,
                Math.max(0.0D, Math.floor(box.height)));
    }

    /** How wide the rows ask to be, each whole. */
    public int naturalWidth() {
        return this.menu.naturalWidth();
    }

    /** How tall the rows ask to be at {@code width}, up to the rows shown at most. */
    public int naturalHeight(int width) {
        return this.menu.naturalHeight(width);
    }

    /** How many rows the rows ask room for at most; past them they scroll. */
    public void setVisibleRows(int rows) {
        this.menu.setVisibleRows(rows);
    }

    /** The row under the point, takeable or not; null for none. */
    public MenuWindow.Entry rowAt(LostTalesUiHitBox box, double x, double y) {
        return this.menu.rowAt(box, x, y);
    }

    /** Back at the top with no row lit: the page turned to something else. */
    public void toTop() {
        this.menu.restart();
    }

    /**
     * Draws the rows in {@code box} at {@code alpha}, on the page's own
     * surface, which the row under the pointer recolours; {@code clipX}
     * and {@code clipY} are where the box really stands, for the cut
     * the rows glide in.
     */
    public void draw(Minecraft minecraft, LostTalesUiHitBox box,
                     double clipX, double clipY, double pointerX,
                     double pointerY, int alpha) {
        this.menu.draw(minecraft, box, clipX, clipY, pointerX, pointerY,
                alpha, WindowLists.pageSurfaceAlpha(minecraft, alpha));
    }

    /** Whether a press at the point takes a row. */
    public boolean acts(LostTalesUiHitBox box, double x, double y) {
        WindowHover hover = this.menu.hoverAt(box, x, y);
        return hover != null && hover.acts;
    }

    /** What the row under the point says under the pointer: why it cannot be taken, how a number is typed; empty for nothing. */
    public String tipAt(LostTalesUiHitBox box, double x, double y) {
        WindowHover hover = this.menu.hoverAt(box, x, y);
        return hover == null || hover.tip == null ? "" : hover.tip;
    }

    /** A press at the point; answers whether it took a row. */
    public boolean press(LostTalesUiHitBox box, double x, double y,
                         int button) {
        WindowHover hover = this.menu.hoverAt(box, x, y);
        if (hover != null && button == 0 && hover.greyedWhy.length() > 0) {
            // A greyed row has no tip; a press says why over the bar.
            WindowScreen screen = WindowScreen.current();
            if (screen != null) {
                screen.sayWhyGreyedHere(hover.greyedWhy);
            }
            return true;
        }
        if (hover == null || hover.menuEntry == null
                || (button != 0 && button != 1)) {
            return false;
        }
        this.pressBox = box;
        this.pressX = x;
        this.pressY = y;
        try {
            this.menu.pressed(hover, x, y, button);
        } finally {
            this.pressBox = null;
        }
        return true;
    }

    /** Scrolls the chosen row into view as the rows are next drawn: the keys walked to it. */
    public void revealChosen() {
        this.menu.revealChosen();
    }

    /** A wheel turn, in lines, positive toward later rows. */
    public void scroll(int lines) {
        this.menu.scrollBy(lines);
    }

    /**
     * Adds a section's header and what the page's search keeps of its
     * rows, as Settings' search keeps them: all of them where the
     * section's name holds the words, else each row that holds them,
     * under its group's name; nothing where none does.
     */
    public static void addSection(List<MenuWindow.Entry> rows, String title,
                                  List<MenuWindow.Entry> members,
                                  String words) {
        Settings.section(rows, title, members, words == null ? ""
                : words.trim().toLowerCase(Locale.ROOT));
    }

    /** How many rows a search kept that are neither headers nor group names. */
    public static int found(List<MenuWindow.Entry> rows) {
        int found = 0;
        for (MenuWindow.Entry row : rows) {
            if (!row.header && !row.group) {
                found++;
            }
        }
        return found;
    }
}
