package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesMapZoomFadeTest {
    /** The map's own range, which the rule has to be expressed against. */
    private static final float MIN = LostTalesLotrMapGui.SMOOTH_ZOOM_MIN;
    private static final float MAX = LostTalesLotrMapGui.SMOOTH_ZOOM_MAX;

    @Test
    public void theEndsOfTheRangeAreTheEndsOfTheProgress() {
        assertEquals(0.0F,
                LostTalesMapZoomFade.progress(MAX, MIN, MAX), 0.0F);
        assertEquals(1.0F,
                LostTalesMapZoomFade.progress(MIN, MIN, MAX), 0.0F);
        assertEquals(0.5F, LostTalesMapZoomFade.progress(
                (MIN + MAX) * 0.5F, MIN, MAX), 0.0001F);
        // Past either end there is no map left to ask about.
        assertEquals(0.0F,
                LostTalesMapZoomFade.progress(MAX + 5.0F, MIN, MAX), 0.0F);
        assertEquals(1.0F,
                LostTalesMapZoomFade.progress(MIN - 5.0F, MIN, MAX), 0.0F);
    }

    /** The boundaries are the specification and may not be eased away. */
    @Test
    public void theFadeIsFlatAtBothEndsAndExactAtItsBoundaries() {
        assertEquals(1.0F, LostTalesMapZoomFade.alphaForProgress(0.0F), 0.0F);
        assertEquals(1.0F, LostTalesMapZoomFade.alphaForProgress(0.1F), 0.0F);
        assertEquals("fully visible at the near boundary", 1.0F,
                LostTalesMapZoomFade.alphaForProgress(
                        LostTalesMapZoomFade.OPAQUE_PROGRESS), 0.0F);
        assertEquals("fully invisible at the far boundary", 0.0F,
                LostTalesMapZoomFade.alphaForProgress(
                        LostTalesMapZoomFade.CLEAR_PROGRESS), 0.0F);
        assertEquals(0.0F, LostTalesMapZoomFade.alphaForProgress(0.9F), 0.0F);
        assertEquals(0.0F, LostTalesMapZoomFade.alphaForProgress(1.0F), 0.0F);
    }

    @Test
    public void theFadeOnlyEverFallsAsTheMapPullsOut() {
        float previous = 1.0F;
        for (float progress = 0.0F; progress <= 1.0F; progress += 0.02F) {
            float alpha = LostTalesMapZoomFade.alphaForProgress(progress);
            assertTrue("the fade came back at " + progress,
                    alpha <= previous + 0.0001F);
            assertTrue(alpha >= 0.0F && alpha <= 1.0F);
            previous = alpha;
        }
    }

    /**
     * The rule is anchored to the range rather than to any exponent, which is
     * the whole point: the last time the zoom was widened, everything written
     * against the old numbers started fading in the wrong place. Widening it
     * again moves these boundaries with it.
     */
    @Test
    public void theBoundariesFollowTheRangeRatherThanTheExponent() {
        float opaqueBelow = MAX
                - LostTalesMapZoomFade.OPAQUE_PROGRESS * (MAX - MIN);
        float clearBeyond = MAX
                - LostTalesMapZoomFade.CLEAR_PROGRESS * (MAX - MIN);
        assertEquals(1.0F,
                LostTalesMapZoomFade.alpha(opaqueBelow, MIN, MAX), 0.0F);
        assertEquals(0.0F,
                LostTalesMapZoomFade.alpha(clearBeyond, MIN, MAX), 0.0F);

        // A wider zoom moves both, in exponent terms, without the rule
        // changing at all.
        float wideMin = MIN - 4.0F;
        assertTrue("a wider zoom has to hold detail further out",
                LostTalesMapZoomFade.alpha(clearBeyond, wideMin, MAX)
                        > 0.0F);
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
