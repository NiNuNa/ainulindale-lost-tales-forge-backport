package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class LostTalesMapViewMemoryTest {
    @Before
    public void forgetAnythingLeftOver() {
        LostTalesMapViewMemory.clear();
    }

    @After
    public void leaveNothingBehind() {
        LostTalesMapViewMemory.clear();
    }

    /** Nothing to restore before the map has ever been opened. */
    @Test
    public void aMapNeverOpenedHasNoViewToGoBackTo() {
        assertFalse(LostTalesMapViewMemory.isHeld());
    }

    @Test
    public void theViewIsHandedBackExactlyAsItWasLeft() {
        LostTalesMapViewMemory.remember(
                812.5F, 731.25F, 2.35F, -0.4F, 0.6F);

        assertTrue(LostTalesMapViewMemory.isHeld());
        assertEquals(812.5F, LostTalesMapViewMemory.getPosX(), 0.0F);
        assertEquals(731.25F, LostTalesMapViewMemory.getPosY(), 0.0F);
        assertEquals(2.35F, LostTalesMapViewMemory.getZoomExp(), 0.0F);
        // The drags rather than the angles, so the next turn continues the
        // last one instead of starting again from square.
        assertEquals(-0.4F,
                LostTalesMapViewMemory.getRotationInput(), 0.0F);
        assertEquals(0.6F, LostTalesMapViewMemory.getLeanInput(), 0.0F);

        // Opening and closing again replaces it rather than accumulating.
        LostTalesMapViewMemory.remember(100.0F, 200.0F, -1.0F, 0.0F, 0.0F);
        assertEquals(100.0F, LostTalesMapViewMemory.getPosX(), 0.0F);
        assertEquals(-1.0F, LostTalesMapViewMemory.getZoomExp(), 0.0F);
    }

    /**
     * A view is of one world's map. Carrying it into the next one would put
     * the camera somewhere that means nothing there.
     */
    @Test
    public void leavingTheWorldForgetsTheView() {
        LostTalesMapViewMemory.remember(812.5F, 731.25F, 2.0F, 0.3F, 0.2F);
        LostTalesMapViewMemory.clear();

        assertFalse(LostTalesMapViewMemory.isHeld());
        assertEquals(0.0F, LostTalesMapViewMemory.getPosX(), 0.0F);
        assertEquals(0.0F, LostTalesMapViewMemory.getRotationInput(), 0.0F);
    }

    /**
     * A camera that has gone wrong must not be able to strand the map at a
     * position it cannot be dragged back from.
     */
    @Test
    public void aMalformedViewIsNotRemembered() {
        LostTalesMapViewMemory.remember(
                Float.NaN, 731.25F, 2.0F, 0.0F, 0.0F);
        assertFalse(LostTalesMapViewMemory.isHeld());
        LostTalesMapViewMemory.remember(
                812.5F, Float.NaN, 2.0F, 0.0F, 0.0F);
        assertFalse(LostTalesMapViewMemory.isHeld());
        LostTalesMapViewMemory.remember(
                812.5F, 731.25F, Float.NaN, 0.0F, 0.0F);
        assertFalse(LostTalesMapViewMemory.isHeld());

        // A good camera with a malformed angle is still worth keeping; the
        // angle simply comes back square.
        LostTalesMapViewMemory.remember(
                812.5F, 731.25F, 2.0F, Float.NaN, Float.NaN);
        assertTrue(LostTalesMapViewMemory.isHeld());
        assertEquals(0.0F, LostTalesMapViewMemory.getRotationInput(), 0.0F);
        assertEquals(0.0F, LostTalesMapViewMemory.getLeanInput(), 0.0F);
    }

    /**
     * Whatever is restored has to be somewhere the map can actually be, since
     * the angles are rebuilt from these drags on the way back in.
     */
    @Test
    public void aRestoredAngleIsStillInsideTheMapsLimits() {
        LostTalesMapViewMemory.remember(0.0F, 0.0F, 0.0F, 9.0F, 9.0F);

        float degrees = LostTalesLotrMapRotation.degreesForInput(
                LostTalesLotrMapRotation.releasedInput(
                        LostTalesMapViewMemory.getRotationInput()));
        float lean = LostTalesLotrMapRotation.leanForInput(
                LostTalesLotrMapRotation.releasedInput(
                        LostTalesMapViewMemory.getLeanInput()));

        assertTrue("a restored turn left the map's limits",
                Math.abs(degrees)
                        <= LostTalesLotrMapRotation.MAX_DEGREES + 0.001F);
        assertTrue("a restored lean left the map's limits",
                lean >= 0.0F && lean <= 1.0F);
    }
}
