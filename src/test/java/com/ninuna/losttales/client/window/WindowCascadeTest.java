package com.ninuna.losttales.client.window;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WindowCascadeTest {

    private static final int SCREEN_WIDTH = 427;
    private static final int SCREEN_HEIGHT = 240;
    private static final int WIDTH = 160;
    private static final double HEIGHT = 68.0D;

    @Test
    public void aWindowLandsOneStepRightAndDown() {
        WindowCascade.Corner corner = WindowCascade.place(
                10.0D, 20.0D, WIDTH, HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT, 0,
                WindowCascade.STEP);
        assertEquals(10.0D + WindowCascade.STEP, corner.x, 0.0D);
        assertEquals(20.0D + WindowCascade.STEP, corner.y, 0.0D);
    }

    @Test
    public void theStepKeepsTheTabStripBehindInView() {
        assertTrue(WindowCascade.STEP >= TabRow.ROW_HEIGHT);
    }

    @Test
    public void aBottomOverflowRestartsTheColumnAtTheTop() {
        double referenceY = SCREEN_HEIGHT - HEIGHT - 5.0D;
        WindowCascade.Corner corner = WindowCascade.place(
                10.0D, referenceY, WIDTH, HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT, 0,
                WindowCascade.STEP);
        assertEquals("x keeps stepping", 10.0D + WindowCascade.STEP, corner.x, 0.0D);
        assertEquals("y wraps to the margin", 0.0D, corner.y, 0.0D);
    }

    @Test
    public void aRightOverflowRestartsTheRowAtTheLeft() {
        double referenceX = SCREEN_WIDTH - WIDTH - 5.0D;
        WindowCascade.Corner corner = WindowCascade.place(
                referenceX, 20.0D, WIDTH, HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT, 0,
                WindowCascade.STEP);
        assertEquals("x wraps to the margin", 0.0D, corner.x, 0.0D);
        assertEquals("y keeps stepping", 20.0D + WindowCascade.STEP, corner.y, 0.0D);
    }

    @Test
    public void bothOverflowingGivesTheOrigin() {
        WindowCascade.Corner corner = WindowCascade.place(
                SCREEN_WIDTH - WIDTH, SCREEN_HEIGHT - HEIGHT, WIDTH, HEIGHT,
                SCREEN_WIDTH, SCREEN_HEIGHT, 4, WindowCascade.STEP);
        assertEquals(4.0D, corner.x, 0.0D);
        assertEquals(4.0D, corner.y, 0.0D);
    }

    @Test
    public void aBoxTooBigForTheScreenIsClampedNotLost() {
        WindowCascade.Corner corner = WindowCascade.place(
                0.0D, 0.0D, SCREEN_WIDTH + 50, SCREEN_HEIGHT + 50.0D,
                SCREEN_WIDTH, SCREEN_HEIGHT, 0, WindowCascade.STEP);
        assertEquals(0.0D, corner.x, 0.0D);
        assertEquals(0.0D, corner.y, 0.0D);
    }

}
