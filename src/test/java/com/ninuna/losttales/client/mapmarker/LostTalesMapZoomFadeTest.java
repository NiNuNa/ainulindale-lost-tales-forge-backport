package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesMapZoomFadeTest {
    /** The map's own zoom limits, which the fade sits well inside. */
    private static final float MIN = LostTalesLotrMapGui.SMOOTH_ZOOM_MIN;
    private static final float MAX = LostTalesLotrMapGui.SMOOTH_ZOOM_MAX;
    private static final float SOLID = LostTalesMapZoomFade.SOLID_ZOOM_EXP;
    private static final float CLEAR = LostTalesMapZoomFade.CLEAR_ZOOM_EXP;

    /**
     * Solid over a stretch of the zoom, gone over another, and a fade between
     * — with room to spare at both ends, so neither pushing in nor pulling out
     * ends with icons still half there.
     */
    @Test
    public void thereIsRoomAtBothEndsWhereNothingChanges() {
        assertEquals("zoomed right in, everything is solid", 1.0F,
                LostTalesMapZoomFade.alpha(MAX), 0.0F);
        assertEquals("pulled right out, nothing is drawn", 0.0F,
                LostTalesMapZoomFade.alpha(MIN), 0.0F);

        for (float zoomExp = SOLID; zoomExp <= MAX; zoomExp += 0.1F) {
            assertEquals("a marker faded while pushed in past the boundary",
                    1.0F, LostTalesMapZoomFade.alpha(zoomExp), 0.0F);
        }
        for (float zoomExp = MIN; zoomExp <= CLEAR; zoomExp += 0.1F) {
            assertEquals("a marker showed while pulled out past the boundary",
                    0.0F, LostTalesMapZoomFade.alpha(zoomExp), 0.0F);
        }
        assertTrue("the solid end must stop short of the closest zoom",
                SOLID < MAX - 1.0F);
        assertTrue("the clear end must stop short of the widest zoom",
                CLEAR > MIN + 1.0F);
    }

    /**
     * The bug this was written for: the fade used to be measured as a share of
     * the zoom's whole range, and that range runs from a postage stamp on an
     * empty screen to closer than anyone needs. "Most of the way in" came out
     * at an exponent of nearly three, so at the zoom the map opens at every
     * marker was down to about a third of its colour.
     */
    @Test
    public void theOrdinaryZoomsAreNotSpentHalfFaded() {
        assertTrue("markers are faded at the zoom the map opens at",
                LostTalesMapZoomFade.alpha(0.0F) > 0.6F);
        assertTrue("markers are faded at an ordinary working zoom",
                LostTalesMapZoomFade.alpha(0.5F) > 0.85F);
        // And they are properly gone before the map is a continent on screen.
        assertTrue("markers survive too far out",
                LostTalesMapZoomFade.alpha(-2.5F) <= 0.0F);
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
        assertEquals("the fade never finished", 0.0F, previous, 0.0F);
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
        assertEquals(0.5F,
                LostTalesMapZoomFade.alpha((SOLID + CLEAR) * 0.5F), 0.0001F);
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
