package com.ninuna.losttales.client.mapmarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * How far out the map has to be pulled before a thing drawn on it gives up.
 *
 * <p>One rule, in one place, expressed against how far through its own range
 * the zoom is rather than against any particular exponent. LOTR's own fade was
 * written for its {@code -3..4} zoom and reached nothing at {@code -3.3}; this
 * map goes further out than that, so anything still using those numbers
 * disappeared while there was map left to pull out of. Reading the extrema
 * instead of the numbers means widening the zoom again cannot repeat that.</p>
 *
 * <p>Only for things that share this fade — roads and the marker icons. Labels
 * fade on a rule of their own, on purpose: a name has to go long before the
 * thing it names does, or the map turns into a wall of text.</p>
 */
@SideOnly(Side.CLIENT)
final class LostTalesMapZoomFade {
    /**
     * Zoomed in this far through the range, everything is fully drawn.
     *
     * <p>Which is to say: once the map is 85% of the way to its closest zoom,
     * nothing is faded any more, however much further it is pushed.</p>
     */
    static final float OPAQUE_PROGRESS = 0.15F;
    /** And 85% of the way to its furthest zoom, nothing is drawn at all. */
    static final float CLEAR_PROGRESS = 0.85F;

    private LostTalesMapZoomFade() {}

    /**
     * Where a zoom exponent sits in the map's own range: 0 fully zoomed in,
     * 1 fully out.
     */
    static float progress(
            float zoomExp, float minZoomExp, float maxZoomExp) {
        float span = maxZoomExp - minZoomExp;
        if (!(span > 0.0F) || Float.isNaN(zoomExp)) {
            return 0.0F;
        }
        return clamp((maxZoomExp - zoomExp) / span);
    }

    /**
     * The opacity that progress earns.
     *
     * <p>Flat at both ends and eased between them, so nothing pops as it
     * crosses either boundary.</p>
     */
    static float alphaForProgress(float progress) {
        float clamped = clamp(progress);
        if (clamped <= OPAQUE_PROGRESS) {
            return 1.0F;
        }
        if (clamped >= CLEAR_PROGRESS) {
            return 0.0F;
        }
        float faded = (clamped - OPAQUE_PROGRESS)
                / (CLEAR_PROGRESS - OPAQUE_PROGRESS);
        return smoothstep(1.0F - faded);
    }

    static float alpha(float zoomExp, float minZoomExp, float maxZoomExp) {
        return alphaForProgress(
                progress(zoomExp, minZoomExp, maxZoomExp));
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
