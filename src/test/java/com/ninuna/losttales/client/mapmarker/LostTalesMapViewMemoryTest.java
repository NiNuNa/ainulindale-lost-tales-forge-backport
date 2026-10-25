package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
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
        assertFalse(LostTalesMapViewMemory.screen().isHeld());
        assertFalse(new LostTalesMapViewMemory().isHeld());
    }

    @Test
    public void theViewIsHandedBackExactlyAsItWasLeft() {
        LostTalesMapViewMemory memory = new LostTalesMapViewMemory();
        memory.remember(812.5F, 731.25F, 2.35F, -0.4F, 0.6F);

        assertTrue(memory.isHeld());
        assertEquals(812.5F, memory.getPosX(), 0.0F);
        assertEquals(731.25F, memory.getPosY(), 0.0F);
        assertEquals(2.35F, memory.getZoomExp(), 0.0F);
        // The drags rather than the angles, so the next turn continues the
        // last one instead of starting again from square.
        assertEquals(-0.4F, memory.getRotationInput(), 0.0F);
        assertEquals(0.6F, memory.getLeanInput(), 0.0F);

        // Opening and closing again replaces it rather than accumulating.
        memory.remember(100.0F, 200.0F, -1.0F, 0.0F, 0.0F);
        assertEquals(100.0F, memory.getPosX(), 0.0F);
        assertEquals(-1.0F, memory.getZoomExp(), 0.0F);
    }

    /**
     * Two copies of the map are two views of it (MAP-12): the copy put
     * away last leaves the other where it was.
     */
    @Test
    public void eachCopyKeepsItsOwnView() {
        LostTalesMapViewMemory first = new LostTalesMapViewMemory();
        LostTalesMapViewMemory second = new LostTalesMapViewMemory();
        first.remember(812.5F, 731.25F, 2.0F, 0.3F, 0.2F);
        second.remember(100.0F, 200.0F, -1.0F, -0.5F, 0.0F);

        assertEquals(812.5F, first.getPosX(), 0.0F);
        assertEquals(2.0F, first.getZoomExp(), 0.0F);
        assertEquals(0.3F, first.getRotationInput(), 0.0F);
        assertEquals(0.2F, first.getLeanInput(), 0.0F);
        assertEquals(100.0F, second.getPosX(), 0.0F);
        assertEquals(-1.0F, second.getZoomExp(), 0.0F);
        // Neither is the map screen's own.
        assertFalse(LostTalesMapViewMemory.screen().isHeld());
    }

    /** Each copy of the map page holds a view of its own. */
    @Test
    public void everyPageCopyHoldsItsOwnMemory() {
        LostTalesMapPage first = new LostTalesMapPage();
        LostTalesMapPage second = new LostTalesMapPage();

        assertNotSame(first.viewMemory(), second.viewMemory());
        assertNotSame(LostTalesMapViewMemory.screen(), first.viewMemory());
        first.viewMemory().remember(1.0F, 2.0F, 3.0F, 0.0F, 0.0F);
        assertFalse(second.viewMemory().isHeld());
    }

    /**
     * A view is of one world's map. Carrying it into the next one would put
     * the camera somewhere that means nothing there.
     */
    @Test
    public void leavingTheWorldForgetsTheScreensView() {
        LostTalesMapViewMemory screen = LostTalesMapViewMemory.screen();
        screen.remember(812.5F, 731.25F, 2.0F, 0.3F, 0.2F);
        LostTalesMapViewMemory.clear();

        assertFalse(screen.isHeld());
        assertEquals(0.0F, screen.getPosX(), 0.0F);
        assertEquals(0.0F, screen.getRotationInput(), 0.0F);
    }

    /** A copy opened anew, or let go with its content, starts over the player. */
    @Test
    public void aForgottenViewIsGone() {
        LostTalesMapViewMemory memory = new LostTalesMapViewMemory();
        memory.remember(812.5F, 731.25F, 2.0F, 0.3F, 0.2F);
        memory.forget();

        assertFalse(memory.isHeld());
        assertEquals(0.0F, memory.getZoomExp(), 0.0F);
        assertEquals(0.0F, memory.getLeanInput(), 0.0F);
    }

    /**
     * A camera that has gone wrong must not be able to strand the map at a
     * position it cannot be dragged back from.
     */
    @Test
    public void aMalformedViewIsNotRemembered() {
        LostTalesMapViewMemory memory = new LostTalesMapViewMemory();
        memory.remember(Float.NaN, 731.25F, 2.0F, 0.0F, 0.0F);
        assertFalse(memory.isHeld());
        memory.remember(812.5F, Float.NaN, 2.0F, 0.0F, 0.0F);
        assertFalse(memory.isHeld());
        memory.remember(812.5F, 731.25F, Float.NaN, 0.0F, 0.0F);
        assertFalse(memory.isHeld());

        // A good camera with a malformed angle is still worth keeping; the
        // angle simply comes back square.
        memory.remember(812.5F, 731.25F, 2.0F, Float.NaN, Float.NaN);
        assertTrue(memory.isHeld());
        assertEquals(0.0F, memory.getRotationInput(), 0.0F);
        assertEquals(0.0F, memory.getLeanInput(), 0.0F);
    }

    /**
     * Whatever is restored has to be somewhere the map can actually be, since
     * the angles are rebuilt from these drags on the way back in.
     */
    @Test
    public void aRestoredAngleIsStillInsideTheMapsLimits() {
        LostTalesMapViewMemory memory = new LostTalesMapViewMemory();
        memory.remember(0.0F, 0.0F, 0.0F, 9.0F, 9.0F);

        float degrees = LostTalesLotrMapRotation.degreesForInput(
                LostTalesLotrMapRotation.releasedInput(
                        memory.getRotationInput()));
        float lean = LostTalesLotrMapRotation.leanForInput(
                LostTalesLotrMapRotation.releasedInput(
                        memory.getLeanInput()));

        assertTrue("a restored turn left the map's limits",
                Math.abs(degrees)
                        <= LostTalesLotrMapRotation.MAX_DEGREES + 0.001F);
        assertTrue("a restored lean left the map's limits",
                lean >= 0.0F && lean <= 1.0F);
    }
}
