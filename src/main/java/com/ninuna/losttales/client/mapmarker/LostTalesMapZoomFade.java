package com.ninuna.losttales.client.mapmarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * How far out the map has to be pulled before a thing drawn on it gives up.
 *
 * <p>One rule, in one place, expressed as shares of the map's current zoom
 * travel. The close and wide limits have both changed as the map evolved, so
 * the fade follows those limits: solid through seventy-five percent of the
 * outward journey, fading until ninety-nine percent, and clear for the last
 * one percent.</p>
 *
 * <p>Roads, marker icons, and their names all consume this same opacity so
 * the map does not present half of a location at either boundary.</p>
 */
@SideOnly(Side.CLIENT)
final class LostTalesMapZoomFade {
    static final float SOLID_OUTWARD_FRACTION = 0.75F;
    static final float CLEAR_OUTWARD_FRACTION = 0.99F;
    /** Keeps useful working zooms readable while retaining a long fade. */
    private static final float FADE_BIAS = 0.3F;
    /**
     * Below this opacity a thing on the map stops answering the pointer.
     *
     * <p>Hover, selection and clicks belong to what the player can actually
     * see. Testing against nothing at all leaves a band at the end of the fade
     * where an icon is a few percent of a colour and still owns the pointer,
     * which reads as the map catching on something that is not there.</p>
     */
    static final float INTERACTIVE_ALPHA = 0.06F;

    private LostTalesMapZoomFade() {}

    /** Whether something drawn at this opacity may still be pointed at. */
    static boolean isInteractive(float alpha) {
        return alpha >= INTERACTIVE_ALPHA;
    }

    /** Rendering continues below the interaction threshold until truly clear. */
    static boolean isDrawable(float alpha) {
        return alpha > 0.0F;
    }

    /**
     * How far a zoom exponent is through the fade: 0 gone, 1 fully drawn.
     *
     * <p>There is room at both ends on purpose. Pushed in past
     * {@link #solidZoomExp()} nothing changes however much further it goes,
     * and pulled out past {@link #clearZoomExp()} nothing is drawn however
     * much further it goes; the fade happens between them and nowhere else.</p>
     */
    static float progress(float zoomExp) {
        float solid = solidZoomExp();
        float clear = clearZoomExp();
        float span = solid - clear;
        if (!(span > 0.0F) || Float.isNaN(zoomExp)) {
            return 1.0F;
        }
        return clamp((zoomExp - clear) / span);
    }

    static float solidZoomExp() {
        return zoomExpAtOutwardFraction(SOLID_OUTWARD_FRACTION);
    }

    static float clearZoomExp() {
        return zoomExpAtOutwardFraction(CLEAR_OUTWARD_FRACTION);
    }

    private static float zoomExpAtOutwardFraction(float fraction) {
        float close = LostTalesLotrMapGui.SMOOTH_ZOOM_MAX;
        float wide = LostTalesLotrMapGui.SMOOTH_ZOOM_MIN;
        return close - (close - wide) * clamp(fraction);
    }

    /**
     * The opacity a place in the fade earns.
     *
     * <p>Flat at both ends and eased between them, so nothing pops as it
     * crosses either boundary.</p>
     */
    static float alphaForProgress(float progress) {
        float eased = smoothstep(progress);
        float inverse = 1.0F - eased;
        float denominator = eased + FADE_BIAS * inverse;
        return denominator <= 0.0F
                ? 0.0F : eased / denominator;
    }

    static float alpha(float zoomExp) {
        return alphaForProgress(progress(zoomExp));
    }

    /**
     * The zoom exponent LOTR would have to be at to arrive at this opacity on
     * its own.
     *
     * <p>Its road pass works the alpha out from {@code zoomExp} inside a
     * method that draws several hundred quads; there is no seam to fade them
     * through and no reason to reimplement the drawing. So the pass is told
     * the exponent that produces the wanted answer, which leaves LOTR's
     * artwork, spacing and clipping exactly as they were and changes only the
     * one number this class owns.</p>
     */
    static float nativeZoomExpForAlpha(float alpha) {
        return clamp(alpha) * NATIVE_FADE_SPAN + NATIVE_FADE_CLEAR_EXP;
    }

    /**
     * LOTR v36.15's own fade, as {@code alpha = (zoomExp + 3.3) / 2.2}. Read
     * off its {@code renderRoads} and {@code renderWaypoints} bytecode, and
     * covered by {@code LostTalesClassTransformerTest} through the methods
     * that carry it.
     */
    private static final float NATIVE_FADE_CLEAR_EXP = -3.3F;
    private static final float NATIVE_FADE_SPAN = 2.2F;

    private static float smoothstep(float value) {
        float clamped = clamp(value);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
