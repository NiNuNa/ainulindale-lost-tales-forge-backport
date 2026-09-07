package com.ninuna.losttales.client.gui.tooltip;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The shift that puts a tooltip on the pointer rather than on the
 * interface grid. It has to be fine enough to keep pace with a cursor
 * drawn at screen resolution, and coarse enough that every glyph still
 * lands on a whole screen pixel.
 */
public final class LostTalesTooltipSmoothingTest {

    @Test
    public void theShiftIsAlwaysAWholeNumberOfScreenPixels() {
        for (int scaleFactor = 1; scaleFactor <= 4; scaleFactor++) {
            for (int step = -99; step <= 99; step++) {
                float delta = step / 100.0F;
                float shift = LostTalesTooltipSmoothing.quantize(
                        delta, scaleFactor);
                float pixels = shift * scaleFactor;
                assertEquals("scale " + scaleFactor + ", delta " + delta,
                        Math.round(pixels), pixels, 1.0E-4F);
            }
        }
    }

    @Test
    public void theShiftIsNeverFurtherThanTheRoundingThrewAway() {
        for (int scaleFactor = 1; scaleFactor <= 4; scaleFactor++) {
            for (int step = -99; step <= 99; step++) {
                float delta = step / 100.0F;
                float shift = LostTalesTooltipSmoothing.quantize(
                        delta, scaleFactor);
                assertTrue("scale " + scaleFactor + ", delta " + delta,
                        Math.abs(shift - delta) <= 0.5F / scaleFactor + 1.0E-4F);
            }
        }
    }

    @Test
    public void aPositionThatIsNotThePointersIsLeftAlone() {
        // A screen may draw a tooltip somewhere of its own choosing; the
        // shift is only ever the rounding put back, never a move.
        assertEquals(0.0F, LostTalesTooltipSmoothing.quantize(1.0F, 3), 0.0F);
        assertEquals(0.0F, LostTalesTooltipSmoothing.quantize(-1.0F, 3), 0.0F);
        assertEquals(0.0F, LostTalesTooltipSmoothing.quantize(42.0F, 2), 0.0F);
    }

    @Test
    public void thePointerIsFollowedAScreenPixelAtATime() {
        // At GUI Scale 3 a unit is three screen pixels, so the pointer has
        // three places to stand inside one and the tooltip has three to
        // stand with it — which is what stops it stepping a whole unit at
        // a time behind a cursor that is gliding.
        assertEquals(0.0F, LostTalesTooltipSmoothing.quantize(0.0F, 3), 1.0E-4F);
        assertEquals(1.0F / 3.0F,
                LostTalesTooltipSmoothing.quantize(1.0F / 3.0F, 3), 1.0E-4F);
        assertEquals(2.0F / 3.0F,
                LostTalesTooltipSmoothing.quantize(2.0F / 3.0F, 3), 1.0E-4F);
    }
}
