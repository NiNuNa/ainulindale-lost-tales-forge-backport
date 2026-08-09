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
     * Zoomed in this far, everything is fully drawn.
     *
     * <p>An exponent rather than a share of the zoom's range, and that is the
     * whole correction. Measured against the range, "most of the way in" came
     * out at an exponent of nearly three — a zoom where the screen holds a
     * few dozen map pixels — so markers were still part-faded through every
     * zoom anyone actually reads the map at, and were down to a third of their
     * colour at the one the map opens at. The range is not the map: its far
     * end is a postage stamp on an empty screen and its near end is closer
     * than anyone needs. What the fade has to be pinned to is the zoom a
     * player is at, not the zoom the slider can reach.</p>
     */
    static final float SOLID_ZOOM_EXP = 1.0F;
    /**
     * And pulled out this far, nothing is drawn at all.
     *
     * <p>Just before the whole of Middle-earth fits on the screen, which is
     * where a map full of pins stops being a map. There is deliberately zoom
     * left below this: the last of the way out is spent looking at the
     * country, with the fade already finished rather than still running.</p>
     */
    static final float CLEAR_ZOOM_EXP = -2.0F;
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

    /**
     * How far a zoom exponent is through the fade: 0 gone, 1 fully drawn.
     *
     * <p>There is room at both ends on purpose. Pushed in past
     * {@link #SOLID_ZOOM_EXP} nothing changes however much further it goes,
     * and pulled out past {@link #CLEAR_ZOOM_EXP} nothing is drawn however
     * much further it goes; the fade happens between them and nowhere
     * else.</p>
     */
    static float progress(float zoomExp) {
        float span = SOLID_ZOOM_EXP - CLEAR_ZOOM_EXP;
        if (!(span > 0.0F) || Float.isNaN(zoomExp)) {
            return 1.0F;
        }
        return clamp((zoomExp - CLEAR_ZOOM_EXP) / span);
    }

    /**
     * The opacity a place in the fade earns.
     *
     * <p>Flat at both ends and eased between them, so nothing pops as it
     * crosses either boundary.</p>
     */
    static float alphaForProgress(float progress) {
        return smoothstep(progress);
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
