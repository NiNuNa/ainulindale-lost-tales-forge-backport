package com.ninuna.losttales.gui.style;

/**
 * Single shared Lost Tales colour system.
 *
 * <p>The mod's art direction uses exactly one palette: <i>Nanner Pancakes</i>
 * (32 colours, mirrored from the authored palette swatch in the asset repo).
 * Every colour drawn anywhere in the project must be one of these entries,
 * optionally with a caller-chosen alpha via {@link #withAlpha(int, int)}.
 * The semantic aliases below are the names the GUIs, HUD, map, and chat
 * actually use; they are remapped onto palette entries so a palette change
 * restyles the whole mod from this one file.</p>
 *
 * <p>This class deliberately has no client-only dependencies, so the same
 * semantic colours can be used while the dedicated server snapshots chat
 * presentation and while the client renders GUIs, HUDs, and chat.</p>
 */
public class LostTalesColors {

    // ---- Nanner Pancakes palette (opaque ARGB, sheet order) ----
    public static final int SEAFOAM = 0xFFA0DDD3;
    public static final int TEAL = 0xFF6FB0B7;
    public static final int STEEL_BLUE = 0xFF577F9D;
    public static final int SLATE_BLUE = 0xFF4A5786;
    public static final int INDIGO = 0xFF3E3B66;
    public static final int DUSK_VIOLET = 0xFF392945;
    /** The project-wide shadow/black. Never use pure black. */
    public static final int PLUM_BLACK = 0xFF2D1E2F;
    public static final int PLUM_DARK = 0xFF452E3F;
    public static final int PLUM_GRAY = 0xFF5D4550;
    public static final int MAUVE = 0xFF7B6268;
    public static final int ROSE_GRAY = 0xFF9C807E;
    public static final int ROSE_BEIGE = 0xFFC3A79C;
    public static final int SAND = 0xFFDBC9B4;
    public static final int IVORY = 0xFFFCECD1;
    public static final int MEADOW_GREEN = 0xFFAAD795;
    public static final int FERN_GREEN = 0xFF64B082;
    public static final int SEA_GREEN = 0xFF488885;
    public static final int HARBOR_BLUE = 0xFF3F5B74;
    public static final int PARCHMENT = 0xFFEBC8A7;
    public static final int TAN = 0xFFD3A084;
    public static final int CLAY = 0xFFB87E6C;
    public static final int RUST = 0xFF8F5252;
    public static final int MAROON = 0xFF6A3948;
    public static final int SALMON = 0xFFC57F79;
    public static final int ORCHID = 0xFFAB597D;
    public static final int MULBERRY = 0xFF7C3D64;
    public static final int DARK_MULBERRY = 0xFF4E2B45;
    public static final int WINE = 0xFF7A3B4F;
    public static final int CRIMSON = 0xFFA94B54;
    public static final int CORAL = 0xFFD8725E;
    public static final int APRICOT = 0xFFF09F71;
    public static final int HONEY = 0xFFF7CF91;

    // ---- Semantic aliases used across GUIs, HUD, map, and chat ----
    public static final int TEXT = SAND;
    public static final int TEXT_BRIGHT = IVORY;
    public static final int TEXT_MUTED = ROSE_GRAY;
    public static final int TEXT_DIM = MAUVE;
    public static final int GOLD = HONEY;
    public static final int GOLD_DARK = CLAY;
    public static final int BLUE = SEAFOAM;
    public static final int GREEN = MEADOW_GREEN;
    public static final int RED = SALMON;
    public static final int PURPLE = ORCHID;

    /** Ivory used by map artwork, HUD labels, and default chat identities. */
    public static final int HUD_LABEL = IVORY;
    /** Shared plum-black shadow for HUD icons, labels, and chat portraits. */
    public static final int HUD_SHADOW = PLUM_BLACK;

    // ---- Translucent surfaces (palette entries with a chosen alpha) ----
    public static final int PANEL_FILL = withAlpha(PLUM_BLACK, 0xBC);
    public static final int PANEL_FILL_SOFT = withAlpha(PLUM_BLACK, 0x90);
    public static final int PANEL_HOVER = withAlpha(IVORY, 0x4A);
    public static final int PANEL_SELECTED = withAlpha(PLUM_DARK, 0x64);
    public static final int BORDER = withAlpha(SAND, 0x82);
    public static final int BORDER_DIM = withAlpha(MAUVE, 0x44);
    public static final int BLACK_SHADOW = withAlpha(PLUM_BLACK, 0xC0);

    protected LostTalesColors() {}

    /** Strips the alpha byte so a renderer can supply its own opacity. */
    public static int rgb(int argb) {
        return argb & 0xFFFFFF;
    }

    /** Replaces the alpha byte; the alpha is clamped to 0..255. */
    public static int withAlpha(int color, int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        return (clamped << 24) | (color & 0xFFFFFF);
    }

    /** Red channel as 0..1, for fixed-function GL tinting. */
    public static float redF(int color) {
        return ((color >> 16) & 0xFF) / 255.0F;
    }

    /** Green channel as 0..1, for fixed-function GL tinting. */
    public static float greenF(int color) {
        return ((color >> 8) & 0xFF) / 255.0F;
    }

    /** Blue channel as 0..1, for fixed-function GL tinting. */
    public static float blueF(int color) {
        return (color & 0xFF) / 255.0F;
    }
}
