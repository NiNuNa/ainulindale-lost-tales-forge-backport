package com.ninuna.losttales.gui.style;

/**
 * How a lit glyph of the window sheet is repainted for another accent, by
 * the rule the sheet is painted by. Every glyph has three ink colours side
 * by side on one ramp of the palette (a highlight, the main colour and a
 * shade) and up to three background colours: plum black always, then two
 * darks that match the ink's hue. A lit glyph is painted in honey: ivory,
 * honey and apricot over plum black, dark mulberry and wine.
 *
 * <p>For accent A, honey becomes A, each warm colour under honey the entry
 * as many steps under A on A's ramp, ivory the entry over A (ivory itself
 * where A tops its ramp), and the two darks A's ramp's own
 * ({@link LostTalesColors#spriteDarks}). Plum black and every colour off
 * that family stay as painted. For honey the picture comes back exactly as
 * painted; for fern green a lit glyph comes out as the sheet's own green
 * one, and for crimson as its red one.</p>
 */
public final class LostTalesUiAccentInk {
    /** The ramp a lit glyph's ink is painted on, darkest first, honey last. */
    private static final String[] PAINTED_RAMP =
            {"WINE", "CRIMSON", "CORAL", "APRICOT", "HONEY"};

    private LostTalesUiAccentInk() {}

    /**
     * The colour a pixel of a lit glyph takes for {@code accent}, a palette
     * name, both as ARGB. A pixel of less than full strength is a painted
     * hollow: its dark mulberry and wine take the accent's darks. A pixel
     * at full strength is ink: ivory takes the highlight, the warm ramp
     * steps down from the accent. Anything else stays.
     */
    public static int repaint(int argb, String accent) {
        int alpha = argb >>> 24;
        String painted = LostTalesColors.nameOf(argb);
        if (alpha == 0 || painted == null) {
            return argb;
        }
        String[] darks = LostTalesColors.spriteDarks(accent);
        String taken = null;
        if ("DARK_MULBERRY".equals(painted)) {
            taken = darks[0];
        } else if (alpha < 255) {
            taken = "WINE".equals(painted) ? darks[1] : null;
        } else if ("IVORY".equals(painted)) {
            taken = highlight(accent);
        } else {
            int steps = PAINTED_RAMP.length - 1
                    - java.util.Arrays.asList(PAINTED_RAMP).indexOf(painted);
            if (steps < PAINTED_RAMP.length) {
                taken = stepsDown(accent, steps);
            }
        }
        return taken == null ? argb : (alpha << 24)
                | LostTalesColors.rgb(LostTalesColors.paletteColor(taken, argb));
    }

    /** The entry over {@code accent} on its ramp; ivory where it tops its ramp. */
    static String highlight(String accent) {
        String lighter = LostTalesColors.lighterStep(accent);
        return lighter == null ? "IVORY" : lighter;
    }

    /** The entry {@code steps} under {@code accent} on its ramp, or the ramp's darkest. */
    static String stepsDown(String accent, int steps) {
        String shade = accent;
        for (int step = 0; step < steps; step++) {
            String darker = LostTalesColors.darkerStep(shade);
            if (darker == null) {
                break;
            }
            shade = darker;
        }
        return shade;
    }

    /**
     * Repaints every lit glyph of a sheet {@code width} pixels wide, its
     * pixels as ARGB in rows, for {@code accent}.
     */
    public static void repaintSheet(int[] pixels, int width, String accent) {
        for (LostTalesUiSheet cell : LostTalesUiSheet.values()) {
            if (!cell.isAccentLit()) {
                continue;
            }
            for (int y = cell.getTextureV(); y < cell.getTextureV() + cell.getHeight(); y++) {
                for (int x = cell.getTextureU(); x < cell.getTextureU() + cell.getWidth(); x++) {
                    int at = y * width + x;
                    if (at >= 0 && at < pixels.length) {
                        pixels[at] = repaint(pixels[at], accent);
                    }
                }
            }
        }
    }
}
