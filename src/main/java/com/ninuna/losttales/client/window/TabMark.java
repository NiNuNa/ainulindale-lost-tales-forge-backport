package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiCornerCut;
import com.ninuna.losttales.gui.style.LostTalesUiCornerMark;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;

/**
 * What a tab's icon says in its corner about what waits there: the
 * crimson tile counting the pings not yet read, or else the white sphere
 * for anything else not yet read, or nothing. Each kind of tab says what
 * its own mark is ({@link WindowTab#mark}); a conversation with one
 * person counts every line not yet read as a ping, as a messenger's
 * direct messages are. The tile goes to nine; past nine it shows a plus.
 *
 * <p>It stands where a head's status sphere stands, two pixels past the
 * icon's right edge and one below its bottom, and the icon gives it its
 * corner as a head gives its sphere one: the mark's shape grown by a
 * pixel is cut out of the icon ({@link LostTalesUiCornerCut}). On a
 * whisper's tab it takes the corner from the partner's status sphere
 * while anything is unread.</p>
 */
public final class TabMark {
    /** The tile's outline, row by row from its top: a plain rectangle. */
    public static final int[] TILE_INK_LEFT = {0, 0, 0, 0, 0, 0, 0};
    /** The tile's width, its left edge and a figure; the white sphere is as wide. */
    public static final int TILE_WIDTH = LostTalesUiSheet.COUNT_LEFT.getWidth()
            + LostTalesUiSheet.COUNT_1.getWidth();
    /** Nothing waiting. */
    public static final TabMark NONE = new TabMark(0, false);
    /** Something unread that pinged nobody. */
    public static final TabMark UNREAD = new TabMark(0, true);

    private final int pings;
    private final boolean unread;

    private TabMark(int pings, boolean unread) {
        this.pings = pings;
        this.unread = unread;
    }

    /** The tile for {@code count} pings; nothing for none. */
    public static TabMark pings(int count) {
        return count > 0 ? new TabMark(count, true) : NONE;
    }

    /** What waits in a tab's corner, as the tab says; nothing for no tab. */
    public static TabMark of(WindowTab tab) {
        return tab == null ? NONE : tab.mark();
    }

    /** The mark for several tabs together: the sum of their pings, else the sphere if any has something unread. */
    public static TabMark combined(Iterable<? extends WindowTab> tabs) {
        int pings = 0;
        boolean unread = false;
        for (WindowTab tab : tabs) {
            TabMark mark = of(tab);
            pings += mark.pings;
            unread |= mark.unread;
        }
        return pings > 0 ? pings(pings) : unread ? UNREAD : NONE;
    }

    public boolean isNone() {
        return !this.unread;
    }

    /** How many pings the tile counts; zero for the sphere and for nothing. */
    public int pingCount() {
        return this.pings;
    }

    /**
     * What the mark shows: the tile's figure for its count — one to nine,
     * or the plus past nine — or the white sphere.
     */
    public LostTalesUiSheet figure() {
        return this.pings > 0 ? LostTalesUiSheet.countFigure(this.pings)
                : LostTalesUiSheet.PRESENCE_SELECTED;
    }

    /** How wide the mark stands: the tile, or the sphere. */
    public int width() {
        return this.pings > 0 ? TILE_WIDTH
                : LostTalesUiSheet.PRESENCE_SELECTED.getWidth();
    }

    /** How tall the mark stands: the tile, or the sphere. */
    public int height() {
        return figure().getHeight();
    }

    /** The mark's outline, row by row, as {@link LostTalesUiCornerCut#around} reads one. */
    public int[] inkLeft() {
        return this.pings > 0 ? TILE_INK_LEFT : LostTalesUiCornerMark.SPHERE_INK_LEFT;
    }

    /** Where the mark's left edge stands on an icon drawn {@code iconSize} wide from {@code iconX}. */
    public float markX(float iconX, float iconSize) {
        return iconX + iconSize + LostTalesUiCornerMark.OVERHANG_X - width();
    }

    /** Where the mark's top stands on an icon drawn {@code iconSize} tall from {@code iconY}. */
    public float markY(float iconY, float iconSize) {
        return iconY + iconSize + LostTalesUiCornerMark.OVERHANG_Y - height();
    }

    /** The corner an icon drawn at {@code iconX}, {@code iconY}, {@code iconSize} square gives the mark. */
    public LostTalesUiCornerCut cut(float iconX, float iconY, float iconSize) {
        return isNone() ? LostTalesUiCornerCut.NONE
                : LostTalesUiCornerCut.around(markX(iconX, iconSize),
                        markY(iconY, iconSize), inkLeft());
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TabMark)) {
            return false;
        }
        TabMark mark = (TabMark)other;
        return this.pings == mark.pings && this.unread == mark.unread;
    }

    @Override
    public int hashCode() {
        return this.pings * 2 + (this.unread ? 1 : 0);
    }

    /** The mark in the corner of an icon drawn at {@code iconX}, {@code iconY}, over the one shadow. */
    public void draw(float iconX, float iconY, float iconSize, int alpha) {
        drawAt(markX(iconX, iconSize), markY(iconY, iconSize), alpha);
    }

    /**
     * The mark with its top-left at ({@code x}, {@code y}), over the one
     * shadow: the tile's left edge and its figure, or the sphere.
     */
    public void drawAt(float x, float y, int alpha) {
        if (isNone()) {
            return;
        }
        if (this.pings > 0) {
            LostTalesUiSheet.drawJoinedWithShadow(LostTalesUiSheet.COUNT_LEFT,
                    figure(), x, y, alpha);
        } else {
            figure().drawWithShadow(x, y, alpha);
        }
    }
}
