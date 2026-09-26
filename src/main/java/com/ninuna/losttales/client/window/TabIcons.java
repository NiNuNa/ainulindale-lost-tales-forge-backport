package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiCornerMark;

/** Where a tab's icon stands: its box, the room it takes and the gap before the words. */
public final class TabIcons {
    /** An icon's box: an emoji's size, drawn at the sheet's own size, never scaled. */
    public static final int SIZE = 10;

    /**
     * The room an icon that may wear a mark takes across: the icon and
     * the two pixels its mark stands past it, kept whether a mark shows
     * or not, so a name never moves as one comes and goes.
     */
    public static final int SLOT = SIZE + LostTalesUiCornerMark.OVERHANG_X;

    /** Gap between the icon and the text. */
    public static final int GAP = 3;

    /**
     * Where a ten-row icon's box stands from the top of the capitals it
     * sits beside: two rows above them, so its middle is half a pixel
     * above the capitals' middle — the one rule for a ten-row box.
     */
    public static final int CONTENT_TOP_OFFSET = -2;

    private TabIcons() {}
}
