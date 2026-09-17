package com.ninuna.losttales.gui.style;

import net.minecraft.client.renderer.Tessellator;

/**
 * The ink every Lost Tales control is drawn with, wherever it stands.
 *
 * <p>The chat settled these rules first and they are not the chat's:
 * one ivory for words, one plum-black shadow a pixel down and right at
 * two thirds of what it follows, one surface tone that lights toward
 * plum grey, and one way of laying a flat colour over a rectangle. A
 * screen that wants to look like the rest of the mod draws from here
 * rather than choosing again.</p>
 *
 * <p>Colours are alpha-free RGB: a control that animates its own
 * opacity puts the alpha on with {@link #argb} as it draws, so a tone
 * and an opacity are never baked together.</p>
 */
public final class LostTalesUiInk {

    /** The one colour words are written in. */
    public static final int IVORY =
            LostTalesColors.rgb(LostTalesColors.HUD_LABEL);
    /** The one shadow; never black. */
    public static final int SHADOW =
            LostTalesColors.rgb(LostTalesColors.HUD_SHADOW);
    /** How far the shadow falls, right and down. */
    public static final int SHADOW_OFFSET = 1;
    /** A shadow's share of what it follows. */
    public static final float SHADOW_OPACITY = LostTalesColors.SHADOW_OPACITY;
    /**
     * Lowest alpha {@code FontRenderer} honours: a colour whose alpha is
     * below four is treated as opaque, so a near-invisible shadow would
     * flash at full strength. Anything under this is not drawn at all.
     */
    public static final int MIN_VISIBLE_ALPHA = 4;
    /** A surface at rest. */
    public static final int SURFACE_RGB =
            LostTalesColors.rgb(LostTalesColors.PLUM_BLACK);
    /** A surface fully lit. */
    public static final int SURFACE_HIGHLIGHT_RGB =
            LostTalesColors.rgb(LostTalesColors.PLUM_GRAY);
    /** The square an inline icon is drawn in, and the row height it sets. */
    public static final int ICON_SIZE = 10;

    private LostTalesUiInk() {}

    /** An alpha-free colour at {@code alpha}. */
    public static int argb(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    /**
     * The shadow's alpha under content drawn at {@code alpha}, or zero
     * where it would fall under {@link #MIN_VISIBLE_ALPHA} and must be
     * skipped rather than drawn opaque.
     */
    public static int shadowAlpha(int alpha) {
        int shadow = Math.round(
                Math.max(0, Math.min(255, alpha)) * SHADOW_OPACITY);
        return shadow < MIN_VISIBLE_ALPHA ? 0 : shadow;
    }

    /**
     * A colour {@code progress} of the way from one to another, channel
     * by channel: what a control lighting under the pointer crosses
     * through, so it reaches its lit tone with its artwork rather than
     * snapping at the same moment.
     */
    public static int blend(int fromRgb, int toRgb, float progress) {
        if (progress <= 0.0F) {
            return fromRgb;
        }
        if (progress >= 1.0F) {
            return toRgb;
        }
        return (channel(fromRgb, toRgb, progress, 16) << 16)
                | (channel(fromRgb, toRgb, progress, 8) << 8)
                | channel(fromRgb, toRgb, progress, 0);
    }

    private static int channel(int fromRgb, int toRgb, float progress,
                               int shift) {
        int from = (fromRgb >> shift) & 0xFF;
        int to = (toRgb >> shift) & 0xFF;
        return Math.max(0, Math.min(255,
                Math.round(from + (to - from) * progress)));
    }

    /**
     * One flat colour over a rectangle, at fractional coordinates so a
     * control that slides keeps its edges where it stands. Puts the
     * drawing state back the way it was found.
     */
    public static void fillRect(float left, float top, float right,
                                float bottom, int argb) {
        int alpha = argb >>> 24;
        if (alpha <= 0 || right <= left || bottom <= top) {
            return;
        }
        Tessellator tessellator = LostTalesSkyrimUiStyle.beginQuads(false);
        tessellator.setColorRGBA_I(argb & 0xFFFFFF, alpha);
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
        LostTalesSkyrimUiStyle.endQuads(tessellator, false);
    }

    /**
     * Puts blending back the way content needs it after any
     * {@code drawRect}; see {@link LostTalesSkyrimUiStyle#beginContent}.
     */
    public static void beginContent() {
        LostTalesSkyrimUiStyle.beginContent();
    }
}
