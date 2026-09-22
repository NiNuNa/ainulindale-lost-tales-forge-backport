package com.ninuna.losttales.client.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.config.LostTalesConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** The three settings reach every time the motions give, and a missing motion lands at once. */
public final class MotionsTest {
    private static final long MILLIS = 1000000L;

    private MotionTestSettings settings;

    @Before
    public void setUp() {
        this.settings = MotionTestSettings.reset();
        MotionTestSettings.preview("{\"test.fade\": {\"duration\": 200},"
                + "\"test.follow\": {\"follow\": 0.1, \"params\": {\"size\": 3}}}");
    }

    @After
    public void tearDown() {
        this.settings.restore();
    }

    @Test
    public void speedIsKeptInsideItsBounds() {
        LostTalesConfig.animationSpeed = 100.0D;
        assertEquals(Motions.MAX_SPEED, Motions.speed(), 0.0D);
        LostTalesConfig.animationSpeed = 0.0D;
        assertEquals(Motions.MIN_SPEED, Motions.speed(), 0.0D);
        LostTalesConfig.animationSpeed = Double.NaN;
        assertEquals(1.0D, Motions.speed(), 0.0D);
    }

    @Test
    public void timesFollowTheSettings() {
        assertEquals(200L * MILLIS, Motions.nanos("test.fade"));
        assertEquals(200L * MILLIS, Motions.travelNanos("test.fade"));
        LostTalesConfig.animationSpeed = 2.0D;
        assertEquals(100L * MILLIS, Motions.nanos("test.fade"));
        LostTalesConfig.reducedMotion = true;
        assertEquals(Motions.REDUCED_MILLIS * MILLIS,
                Motions.nanos("test.fade"));
        assertEquals(0L, Motions.travelNanos("test.fade"));
        LostTalesConfig.reducedMotion = false;
        LostTalesConfig.animations = false;
        assertEquals(0L, Motions.nanos("test.fade"));
        assertEquals(0L, Motions.scaledNanos(500));
    }

    @Test
    public void aFollowerCoversMostOfItsWayInItsTime() {
        double moved = Motions.follow("test.follow", 0.0D, 1.0D, 0.1D);
        assertEquals(1.0D - Math.exp(-1.0D), moved, 1.0E-6D);
        LostTalesConfig.reducedMotion = true;
        assertEquals(moved, Motions.follow("test.follow", 0.0D, 1.0D, 0.1D),
                1.0E-9D);
        assertEquals(1.0D, Motions.followTravel("test.follow", 0.0D, 1.0D,
                0.1D), 0.0D);
        LostTalesConfig.reducedMotion = false;
        LostTalesConfig.animations = false;
        assertEquals(1.0D, Motions.follow("test.follow", 0.0D, 1.0D, 0.1D),
                0.0D);
    }

    @Test
    public void aMissingNumberReadsAsItsFallback() {
        assertEquals(3.0F, Motions.param("test.follow", "size", 7.0F), 0.0F);
        assertEquals(7.0F, Motions.param("test.follow", "missing", 7.0F), 0.0F);
    }

    @Test
    public void aMotionNoFileHasMovesNothing() {
        Motion missing = Motions.get("test.nowhere");
        assertTrue(missing.parts().isEmpty());
        assertEquals(0L, Motions.nanos("test.nowhere"));
        assertEquals(1.0D, Motions.follow("test.nowhere", 0.0D, 1.0D, 0.01D),
                0.0D);
    }

    @Test
    public void aPreviewPlaysUntilItIsCleared() {
        assertEquals(200L * MILLIS, Motions.nanos("test.fade"));
        Motions.clearPreviews();
        assertEquals(0L, Motions.nanos("test.fade"));
    }

    @Test
    public void theModsOwnMotionsAreInForce() {
        assertTrue(Motions.ids().contains(MotionIds.UI_BUTTON_LIFT));
        assertEquals("ui", Motions.family(MotionIds.UI_BUTTON_LIFT));
        assertTrue(Motions.bundled(MotionIds.CHAT_LINE_HOVER) != null);
    }
}
