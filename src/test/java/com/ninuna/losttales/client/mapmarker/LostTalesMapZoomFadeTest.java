package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesMapZoomFadeTest {
    /** The map's own zoom limits around the proportional marker fade. */
    private static final float MIN = LostTalesLotrMapGui.SMOOTH_ZOOM_MIN;
    private static final float MAX = LostTalesLotrMapGui.SMOOTH_ZOOM_MAX;
    private static final float SOLID = LostTalesMapZoomFade.solidZoomExp();
    private static final float CLEAR = LostTalesMapZoomFade.clearZoomExp();

    /**
     * Close views are solid and the final one percent of zoom-out is clear.
     */
    @Test
    public void closeViewsAreSolidAndTheRegionalWideViewIsClear() {
        assertEquals("zoomed right in, everything is solid", 1.0F,
                LostTalesMapZoomFade.alpha(MAX), 0.0F);
        assertEquals("the regional wide view retained map markers",
                0.0F, LostTalesMapZoomFade.alpha(MIN), 0.0F);

        for (float zoomExp = SOLID; zoomExp <= MAX; zoomExp += 0.1F) {
            assertEquals("a marker faded while pushed in past the boundary",
                    1.0F, LostTalesMapZoomFade.alpha(zoomExp), 0.0F);
        }
        assertTrue("the solid end must stop short of the closest zoom",
                SOLID < MAX);
        assertTrue("the clear boundary must leave a final empty plateau",
                CLEAR > MIN);
    }

    @Test
    public void theFadeUsesSeventyFiveAndNinetyNinePercentOfCurrentTravel() {
        float span = MAX - MIN;
        assertEquals(MAX - span * 0.75F, SOLID, 0.0001F);
        assertEquals(MAX - span * 0.99F, CLEAR, 0.0001F);
        assertEquals(1.0F, LostTalesMapZoomFade.alpha(SOLID), 0.0F);
        assertEquals(0.0F, LostTalesMapZoomFade.alpha(CLEAR), 0.0F);
        float middle = MAX - span * 0.87F;
        assertTrue("the middle of the fade is already clear",
                LostTalesMapZoomFade.alpha(middle) > 0.0F);
        assertTrue("the middle of the fade is still solid",
                LostTalesMapZoomFade.alpha(middle) < 1.0F);
    }

    @Test
    public void theFadeOnlyEverFallsAsTheMapPullsOut() {
        float previous = 1.0F;
        for (float zoomExp = MAX; zoomExp >= MIN; zoomExp -= 0.05F) {
            float alpha = LostTalesMapZoomFade.alpha(zoomExp);
            assertTrue("the fade came back at " + zoomExp,
                    alpha <= previous + 0.0001F);
            assertTrue(alpha >= 0.0F && alpha <= 1.0F);
            previous = alpha;
        }
        assertTrue("the fade changed after reaching the configured wide view",
                Math.abs(LostTalesMapZoomFade.alpha(MIN) - previous)
                        < 0.02F);
        // Nothing steps: the ends are flat and the middle is eased.
        float last = LostTalesMapZoomFade.alpha(MAX);
        for (float zoomExp = MAX; zoomExp >= MIN; zoomExp -= 0.02F) {
            float alpha = LostTalesMapZoomFade.alpha(zoomExp);
            assertTrue("the fade jumped at " + zoomExp,
                    Math.abs(alpha - last) < 0.05F);
            last = alpha;
        }
    }

    /**
     * A malformed zoom must not blank the map, and the boundaries are the
     * specification rather than something the easing may round away.
     */
    @Test
    public void theBoundariesAreExact() {
        assertEquals(1.0F, LostTalesMapZoomFade.alpha(SOLID), 0.0F);
        assertEquals(0.0F, LostTalesMapZoomFade.alpha(CLEAR), 0.0F);
        assertEquals(1.0F, LostTalesMapZoomFade.alpha(Float.NaN), 0.0F);
        assertTrue(LostTalesMapZoomFade.alpha(
                (SOLID + CLEAR) * 0.5F) > 0.5F);
    }

    /**
     * What can be clicked is what can be seen. A marker worth a few percent of
     * a colour that still owns the pointer reads as the map catching on
     * something that is not there.
     */
    @Test
    public void aMarkerStopsAnsweringThePointerBeforeItHasGone() {
        assertTrue("an invisible marker was still interactive",
                !LostTalesMapZoomFade.isInteractive(0.0F));
        assertTrue("an all but invisible marker was still interactive",
                !LostTalesMapZoomFade.isInteractive(
                        LostTalesMapZoomFade.alpha(CLEAR + 0.05F)));
        assertTrue("a solid marker must answer the pointer",
                LostTalesMapZoomFade.isInteractive(1.0F));
        assertTrue("a marker half way through the fade must still answer",
                LostTalesMapZoomFade.isInteractive(
                        LostTalesMapZoomFade.alpha((SOLID + CLEAR) * 0.5F)));
        assertTrue(LostTalesMapZoomFade.INTERACTIVE_ALPHA > 0.0F
                && LostTalesMapZoomFade.INTERACTIVE_ALPHA < 0.2F);
        float visibleTail = LostTalesMapZoomFade.alpha(CLEAR + 0.08F);
        assertTrue("the visible tail was cut off with zoom remaining",
                LostTalesMapZoomFade.isDrawable(visibleTail));
        assertTrue("the barely visible tail still owned the pointer",
                !LostTalesMapZoomFade.isInteractive(visibleTail));
    }

    /**
     * The exponent handed to LOTR has to reproduce the opacity asked for
     * through LOTR's own arithmetic, or the roads fade on a third rule.
     */
    @Test
    public void theNativeExponentReproducesTheOpacityItStandsFor() {
        for (float alpha = 0.0F; alpha <= 1.0F; alpha += 0.1F) {
            float exponent =
                    LostTalesMapZoomFade.nativeZoomExpForAlpha(alpha);
            float native_ = Math.max(0.0F,
                    Math.min(1.0F, (exponent + 3.3F) / 2.2F));

            assertEquals(alpha, native_, 0.0001F);
        }
    }
}
