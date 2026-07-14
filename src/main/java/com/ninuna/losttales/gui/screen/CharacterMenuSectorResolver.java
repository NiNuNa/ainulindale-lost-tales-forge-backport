package com.ninuna.losttales.gui.screen;

/** Dependency-free geometry for the four full-screen character-menu sectors. */
public final class CharacterMenuSectorResolver {

    public static final int NONE = -1;
    public static final int PROFILE = 0;
    public static final int QUESTS = 1;
    public static final int ITEMS = 2;
    public static final int MAP = 3;

    private CharacterMenuSectorResolver() {}

    public static int resolve(int mouseX, int mouseY,
                              int centerX, int centerY,
                              int screenWidth, int screenHeight) {
        int dx = mouseX - centerX;
        int dy = mouseY - centerY;
        if (dx == 0 && dy == 0) {
            return NONE;
        }

        // Compare normalized distance to the actual horizontal and vertical
        // edges in the mouse direction. The side-specific extents matter when
        // an odd screen dimension makes the integer GUI center asymmetric by
        // one pixel. Equality is then the exact center-to-corner diagonal.
        int horizontalExtent = dx < 0
                ? Math.max(1, centerX)
                : Math.max(1, screenWidth - centerX);
        int verticalExtent = dy < 0
                ? Math.max(1, centerY)
                : Math.max(1, screenHeight - centerY);
        long horizontalWeight = Math.abs((long) dx)
                * verticalExtent;
        long verticalWeight = Math.abs((long) dy)
                * horizontalExtent;
        if (horizontalWeight > verticalWeight) {
            return dx < 0 ? QUESTS : ITEMS;
        }
        return dy < 0 ? PROFILE : MAP;
    }
}
