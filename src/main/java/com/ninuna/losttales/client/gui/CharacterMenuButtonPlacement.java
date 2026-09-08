package com.ninuna.losttales.client.gui;

/**
 * Where the character button goes on the main menu: beside the column of
 * menu buttons, never on top of one.
 *
 * <p>Every measurement comes from the buttons the menu actually built, so
 * the placement follows the menu rather than repeating its arithmetic.
 * That matters because LOTR moves the whole column down after vanilla has
 * laid it out, and because the column is centred, so every window size
 * and every GUI scale puts it somewhere else.</p>
 *
 * <p>The right of the column is the first choice and the left is the
 * fallback, which is where the language button already sits. A window too
 * narrow for either gets no button rather than one drawn over Singleplayer:
 * the menu is never gated on it, so leaving it out costs nothing but the
 * shortcut.</p>
 */
public final class CharacterMenuButtonPlacement {

    /**
     * Wide enough for the broadest figure it may show — a dwarf stands
     * twenty texels across — with a margin either side. Still a thin
     * button beside the menu's own.
     */
    public static final int WIDTH = 24;
    /** Between the button and the column of menu buttons it sits beside. */
    public static final int GAP = MainMenuButtonLayout.GAP;
    /**
     * The height the vanilla menu's own spacing gives the button: its
     * buttons are twenty tall and twenty-four apart, so the top of
     * Singleplayer to the bottom of Multiplayer is forty-four. LOTR moves
     * the column but keeps the spacing, and the real height is measured
     * from the buttons; this is what to size a figure against.
     */
    public static final int VANILLA_HEIGHT = 44;

    /** Clear of the screen edge, the same margin vanilla leaves. */
    private static final int SCREEN_MARGIN = 2;

    private final int x;
    private final int y;
    private final int height;

    private CharacterMenuButtonPlacement(int x, int y, int height) {
        this.x = x;
        this.y = y;
        this.height = height;
    }

    /**
     * The placement beside a column running from {@code columnLeft} to
     * {@code columnRight} and from {@code columnTop} to
     * {@code columnBottom}, or null when neither side has room.
     */
    public static CharacterMenuButtonPlacement beside(
            int screenWidth, int columnLeft, int columnRight,
            int columnTop, int columnBottom) {
        int height = columnBottom - columnTop;
        if (height <= 0 || columnRight <= columnLeft) {
            return null;
        }
        int right = columnRight + GAP;
        if (right + WIDTH <= screenWidth - SCREEN_MARGIN) {
            return new CharacterMenuButtonPlacement(right, columnTop, height);
        }
        int left = columnLeft - GAP - WIDTH;
        if (left >= SCREEN_MARGIN) {
            return new CharacterMenuButtonPlacement(left, columnTop, height);
        }
        return null;
    }

    public int getX() { return this.x; }
    public int getY() { return this.y; }
    public int getHeight() { return this.height; }
}
