package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Windows lying over each other: the one in front shows whole, each window
 * an eighth less for every window in front of it that overlaps it, never
 * less than half; windows apart or only touching keep their strength.
 */
public final class StackFadeTest {

    @Test
    public void eachWindowInFrontTakesAnEighthDownToHalf() {
        assertEquals(1.0F, StackFade.strengthUnder(0), 0.0F);
        assertEquals(0.875F, StackFade.strengthUnder(1), 0.0F);
        assertEquals(0.75F, StackFade.strengthUnder(2), 0.0F);
        assertEquals(0.5F, StackFade.strengthUnder(4), 0.0F);
        assertEquals(0.5F, StackFade.strengthUnder(9), 0.0F);
    }

    /** A cascade: every window overlaps all those in front of it. */
    @Test
    public void aCascadeFadesStepByStep() {
        int[] over = StackFade.overlapsInFront(Arrays.asList(
                box(0, 0), box(20, 20), box(40, 40)));
        assertArrayEquals(new int[] {2, 1, 0}, over);
    }

    /** Only the windows that overlap it count, wherever they stand in front. */
    @Test
    public void onlyOverlappingWindowsCount() {
        int[] over = StackFade.overlapsInFront(Arrays.asList(
                box(0, 0), box(500, 0), box(60, 60)));
        assertArrayEquals(new int[] {1, 0, 0}, over);
    }

    /** Windows lined up edge to edge, or one off screen, take nothing from each other. */
    @Test
    public void touchingAndHiddenWindowsTakeNothing() {
        int[] over = StackFade.overlapsInFront(Arrays.asList(
                box(0, 0), box(100, 0), null));
        assertArrayEquals(new int[] {0, 0, 0}, over);
        assertArrayEquals(new int[] {0}, StackFade.overlapsInFront(
                Collections.singletonList(box(0, 0))));
    }

    /** A window seen for the first time stands at its strength at once; one never listed stands whole. */
    @Test
    public void aWindowFirstSeenStandsAtItsStrength() {
        StackFade fade = new StackFade();
        fade.advance(Arrays.asList("back", "front"),
                Arrays.asList(box(0, 0), box(20, 20)), 1000L);
        assertEquals(0.875F, fade.shareOf("back"), 0.0F);
        assertEquals(1.0F, fade.shareOf("front"), 0.0F);
        assertEquals(1.0F, fade.shareOf("elsewhere"), 0.0F);
    }

    private static LostTalesUiHitBox box(double left, double top) {
        return new LostTalesUiHitBox(left, top, 100.0D, 100.0D);
    }
}
