package com.ninuna.losttales.gui.style;

/**
 * Where a mark stands in an icon's corner: a head's status sphere, or
 * what waits unread on a tab's icon. It stands at the icon's bottom
 * right, partly past it, and every mark shares one round outline.
 */
public final class LostTalesUiCornerMark {
    /** A mark's size on screen: the sheet's own, one texel to one pixel. */
    public static final int SIZE = 5;

    /** How far the mark's left edge stands short of the head's right edge. */
    public static final int INSET_X = 3;

    /** How far its top edge stands short of the head's bottom edge: one more. */
    public static final int INSET_Y = INSET_X + 1;

    /**
     * How far the mark stands past the head's own edges. A head that
     * wears one is laid out as this much wider and taller: the head and
     * its mark are one icon, so a name beside it keeps its clear space
     * from the mark rather than from the face.
     */
    public static final int OVERHANG_X = SIZE - INSET_X;

    public static final int OVERHANG_Y = SIZE - INSET_Y;

    /**
     * The outline every mark shares, and the ivory sphere with them, row
     * by row from its top: the column each row's ink starts at. Round:
     * its top and bottom rows are a pixel in from its sides
     * ({@code ChatPresenceMarkTest} reads the sheet to hold it).
     */
    public static final int[] SPHERE_INK_LEFT = {1, 0, 0, 0, 1};

    private LostTalesUiCornerMark() {}
}
