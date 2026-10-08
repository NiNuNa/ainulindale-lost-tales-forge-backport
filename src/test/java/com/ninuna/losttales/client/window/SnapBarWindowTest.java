package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The snap bar as Windows 11 keeps its own: hidden above the screen, a
 * tip peeking down as a carried window nears the top, all the way down
 * once the pointer touches the tip.
 */
public final class SnapBarWindowTest {
    private static final double EPSILON = 1.0E-6D;
    /** A frame every tenth of a second. */
    private static final long FRAME = 100L * 1000000L;
    /** Ten seconds of frames: long enough for any of the bar's motions to arrive. */
    private static final int SETTLING_FRAMES = 100;

    /**
     * The nearer the pointer comes to the bar, the further the peeking
     * bar comes down: its frame and padding where the pointer brings it
     * out, and half the top edge's snap band as the pointer reaches that
     * line, never further, so the rest of the band still snaps as the
     * edge does. Off to the side the pointer is further from it.
     */
    @Test
    public void thePeekDeepensAsThePointerNearsButLeavesTheTopEdgesBand() {
        assertEquals(6, SnapBarWindow.PEEK);
        assertEquals(8, SnapBarWindow.PEEK_MOST);
        assertTrue(SnapBarWindow.PEEK_MOST < WindowGestures.SNAP_REACH);
        // A bar spanning 320 to 640 that comes out above 65.
        assertEquals(SnapBarWindow.PEEK, peekDepth(480.0D, 65.0D), EPSILON);
        assertEquals(7.0D, peekDepth(480.0D, 36.5D), EPSILON);
        assertEquals(SnapBarWindow.PEEK_MOST, peekDepth(480.0D, 8.0D),
                EPSILON);
        assertEquals(SnapBarWindow.PEEK_MOST, peekDepth(480.0D, 0.0D),
                EPSILON);
        double above = 0.0D;
        for (int y = 64; y >= 0; y--) {
            double depth = peekDepth(480.0D, y);
            assertTrue(depth >= above);
            assertTrue(depth <= SnapBarWindow.PEEK_MOST);
            above = depth;
        }
        assertEquals(7.0D, peekDepth(320.0D - 28.5D, 5.0D), EPSILON);
        assertEquals(SnapBarWindow.PEEK, peekDepth(100.0D, 0.0D), EPSILON);
    }

    /**
     * The stage follows the pointer, where the bar rests all the way
     * down and where it is drawn: hidden past its reach below, down once
     * what is drawn of it is touched and for as long as the pointer stays
     * within its reach, peeking anywhere else.
     */
    @Test
    public void theBarsStageFollowsThePointer() {
        LostTalesUiHitBox resting = new LostTalesUiHitBox(320.0D, 2.0D,
                320.0D, 39.0D);
        LostTalesUiHitBox peeking = new LostTalesUiHitBox(320.0D, -33.0D,
                320.0D, 39.0D);
        assertEquals(SnapBarWindow.Stage.HIDDEN, SnapBarWindow.stageFor(
                SnapBarWindow.Stage.REVEALED, 480.0D, 65.0D, resting, resting));
        assertEquals(SnapBarWindow.Stage.PEEKING, SnapBarWindow.stageFor(
                SnapBarWindow.Stage.HIDDEN, 480.0D, 64.0D, resting, peeking));
        assertEquals(SnapBarWindow.Stage.PEEKING, SnapBarWindow.stageFor(
                SnapBarWindow.Stage.PEEKING, 480.0D, 6.0D, resting, peeking));
        assertEquals(SnapBarWindow.Stage.PEEKING, SnapBarWindow.stageFor(
                SnapBarWindow.Stage.PEEKING, 319.5D, 0.0D, resting, peeking));
        assertEquals(SnapBarWindow.Stage.REVEALED, SnapBarWindow.stageFor(
                SnapBarWindow.Stage.PEEKING, 480.0D, 5.5D, resting, peeking));
        assertEquals(SnapBarWindow.Stage.REVEALED, SnapBarWindow.stageFor(
                SnapBarWindow.Stage.REVEALED, 663.5D, 64.5D, resting, resting));
        assertEquals(SnapBarWindow.Stage.PEEKING, SnapBarWindow.stageFor(
                SnapBarWindow.Stage.REVEALED, 664.0D, 20.0D, resting, resting));
        // A peeking bar is not held down by its reach.
        assertEquals(SnapBarWindow.Stage.PEEKING, SnapBarWindow.stageFor(
                SnapBarWindow.Stage.PEEKING, 480.0D, 20.0D, resting, peeking));
    }

    /**
     * Through one carry: hidden while the pointer is low, a tip above the
     * screen's top edge as it nears the top, answering nothing so the
     * edge decides; all the way down once the tip is touched, answering;
     * hidden again once the pointer leaves the top or the carry ends.
     */
    @Test
    public void aCarriedWindowBringsOutTheTipAndATouchTheWholeBar() {
        SnapBarWindow bar = new SnapBarWindow();
        bar.bind(960, 540);
        // Where it stands all the way down: 2 to 43 with its frame.
        LostTalesUiHitBox stands = new LostTalesUiHitBox(320.0D, 4.0D,
                320.0D, 37.0D);
        long now = 1L;
        bar.standsAt(stands, now);
        assertNull(bar.follow(null, null, 480.0D, 300.0D));
        assertTrue(bar.isTucked());

        assertNull(bar.follow(null, null, 480.0D, 60.0D));
        assertFalse("near the top it peeks", bar.isTucked());
        now = settle(bar, stands, now);
        double bottom = 43.0D - bar.drawnAbove();
        assertTrue("only a tip shows", bottom > SnapBarWindow.PEEK - EPSILON
                && bottom <= SnapBarWindow.PEEK_MOST + EPSILON);

        // Under the tip the top edge decides.
        assertNull(bar.follow(null, null, 480.0D, 12.0D));
        assertFalse(bar.isTucked());
        // Touching the tip brings the bar down, and it answers.
        assertNotNull(bar.follow(null, null, 480.0D, 5.0D));
        now = settle(bar, stands, now);
        assertEquals(0.0D, bar.drawnAbove(), EPSILON);
        assertNotNull("down, within its reach", bar.follow(null, null,
                480.0D, 20.0D));

        assertNull(bar.follow(null, null, 480.0D, 300.0D));
        assertTrue("away from the top it hides", bar.isTucked());
        now = settle(bar, stands, now);
        assertEquals(43.0D, bar.drawnAbove(), EPSILON);

        assertNull(bar.follow(null, null, 480.0D, 60.0D));
        bar.hide();
        assertTrue("the carry over, it hides", bar.isTucked());
    }

    /**
     * Frames of the bar standing at {@code stands} until whatever motion
     * the last pointer asked for has arrived. Answers the last frame's
     * time.
     */
    private static long settle(SnapBarWindow bar, LostTalesUiHitBox stands,
                               long now) {
        for (int frame = 0; frame < SETTLING_FRAMES; frame++) {
            now += FRAME;
            bar.standsAt(stands, now);
        }
        return now;
    }

    /** How deep a bar spanning 320 to 640 and coming out above 65 peeks for a pointer at ({@code x}, {@code y}). */
    private static double peekDepth(double x, double y) {
        return SnapBarWindow.peekDepth(x, y, 320.0D, 640.0D, 65.0D);
    }
}
