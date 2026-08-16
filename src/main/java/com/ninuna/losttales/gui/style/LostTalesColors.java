package com.ninuna.losttales.gui.style;

/**
 * Single shared Lost Tales colour palette.
 *
 * <p>This class deliberately has no client-only dependencies, so the same
 * semantic colours can be used while the dedicated server snapshots chat
 * presentation and while the client renders GUIs, HUDs, and chat.</p>
 */
public class LostTalesColors {
    public static final int PANEL_FILL = 0xBC07090C;
    public static final int PANEL_FILL_SOFT = 0x9007090C;
    public static final int PANEL_HOVER = 0x4AFFFFFF;
    public static final int PANEL_SELECTED = 0x642C3440;
    public static final int BORDER = 0x82D8D1C3;
    public static final int BORDER_DIM = 0x446D6A63;
    public static final int TEXT = 0xFFEEE8D8;
    public static final int TEXT_BRIGHT = 0xFFFFF8EC;
    public static final int TEXT_MUTED = 0xFF9F9A8D;
    public static final int TEXT_DIM = 0xFF706D65;
    public static final int GOLD = 0xFFD8B36A;
    public static final int GOLD_DARK = 0xFF8E7240;
    public static final int BLUE = 0xFFAFCFE1;
    public static final int GREEN = 0xFF9ECA91;
    public static final int RED = 0xFFC98778;
    public static final int PURPLE = 0xFFCBB3E6;
    public static final int BLACK_SHADOW = 0xC0000000;

    /** Ivory used by map artwork, HUD labels, and default chat identities. */
    public static final int HUD_LABEL = 0xFFFCECD1;
    /** Shared plum-black shadow for HUD icons, labels, and chat portraits. */
    public static final int HUD_SHADOW = 0xFF2D1E2F;

    protected LostTalesColors() {}

    /** Strips the alpha byte so a renderer can supply its own opacity. */
    public static int rgb(int argb) {
        return argb & 0xFFFFFF;
    }
}
