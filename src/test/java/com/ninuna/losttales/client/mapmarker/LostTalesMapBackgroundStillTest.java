package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.config.LostTalesConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** The map behind the main menu and the loading screens holds still with reduced motion (MAP-14). */
public final class LostTalesMapBackgroundStillTest {
    private boolean animations;
    private boolean reducedMotion;

    @Before
    public void keepTheSettings() {
        this.animations = LostTalesConfig.animations;
        this.reducedMotion = LostTalesConfig.reducedMotion;
    }

    @After
    public void putTheSettingsBack() {
        LostTalesConfig.animations = this.animations;
        LostTalesConfig.reducedMotion = this.reducedMotion;
    }

    @Test
    public void theAnimationsSwitchAndReducedMotionBothStillTheBackground() {
        LostTalesConfig.animations = true;
        LostTalesConfig.reducedMotion = false;
        assertFalse(LostTalesMapBackgroundStill.stills());

        LostTalesConfig.animations = false;
        assertTrue(LostTalesMapBackgroundStill.stills());

        LostTalesConfig.animations = true;
        LostTalesConfig.reducedMotion = true;
        assertTrue(LostTalesMapBackgroundStill.stills());
    }

    @Test
    public void aStillBackgroundDoesNotSway() {
        for (double seconds = 0.0D; seconds < 400.0D; seconds += 7.3D) {
            assertEquals(0.0F,
                    LostTalesMapBackgroundStill.swayDegrees(seconds, true),
                    0.0F);
        }
        float widest = LostTalesMapBackgroundStill.swayDegrees(
                24.0D * Math.PI / 2.0D, false);
        assertEquals(LostTalesMapBackgroundStill.SWAY_DEGREES, widest, 1.0E-4F);
    }

    /** The flyover stops where it stood as stillness began, and goes on from LOTR's place once motion is back. */
    @Test
    public void theMenusFlyoverHoldsWhereItStood() {
        LostTalesMapBackgroundStill still = new LostTalesMapBackgroundStill();

        assertPlace(still.place(false, 10.0D, 20.0D), 10.0D, 20.0D);
        assertPlace(still.place(true, 11.0D, 21.0D), 11.0D, 21.0D);
        // LOTR moves its flyover on; the held map does not.
        assertPlace(still.place(true, 12.5D, 23.0D), 11.0D, 21.0D);
        assertPlace(still.place(true, 40.0D, 90.0D), 11.0D, 21.0D);

        assertPlace(still.place(false, 41.0D, 91.0D), 41.0D, 91.0D);
        // Stilled again, it holds the new place.
        assertPlace(still.place(true, 42.0D, 92.0D), 42.0D, 92.0D);
        assertPlace(still.place(true, 50.0D, 99.0D), 42.0D, 92.0D);
    }

    private static void assertPlace(double[] place, double x, double y) {
        assertEquals(x, place[0], 0.0D);
        assertEquals(y, place[1], 0.0D);
    }
}
