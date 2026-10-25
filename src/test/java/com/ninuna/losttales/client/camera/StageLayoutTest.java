package com.ninuna.losttales.client.camera;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The character on a stage stands in the widest stretch of the screen no
 * window covers (WIN-50): beside the Profile view's window on the left
 * third, in the two thirds right of it.
 */
public final class StageLayoutTest {

    @Test
    public void besideAWindowOnTheLeftThirdTheStageIsTheRest() {
        StageLayout stage = StageLayout.of(960, 540,
                Arrays.asList(new int[] {0, 320}));
        assertEquals(320 + StageLayout.MARGIN, stage.getLeft());
        assertEquals(960 - StageLayout.MARGIN, stage.getRight());
        assertEquals(StageLayout.MARGIN, stage.getTop());
        assertEquals(540 - StageLayout.MARGIN, stage.getBottom());
        assertEquals((320 + 960) / 2.0F, stage.getFigureCenterX(), 1.0E-4F);
    }

    @Test
    public void theWidestGapWinsAndOverlapsCountOnce() {
        List<int[]> covered = new ArrayList<int[]>();
        covered.add(new int[] {600, 700});
        covered.add(new int[] {100, 300});
        covered.add(new int[] {250, 400});
        StageLayout stage = StageLayout.of(800, 400, covered);
        // Gaps: 0-100, 400-600, 700-800; the widest is 400-600.
        assertEquals(400 + StageLayout.MARGIN, stage.getLeft());
        assertEquals(600 - StageLayout.MARGIN, stage.getRight());
    }

    @Test
    public void withNoRoomTheWholeScreenIsTheStage() {
        StageLayout stage = StageLayout.of(400, 300,
                Arrays.asList(new int[] {0, 380}));
        assertEquals(StageLayout.MARGIN, stage.getLeft());
        assertEquals(400 - StageLayout.MARGIN, stage.getRight());
    }

    @Test
    public void noWindowsLeaveTheWholeScreen() {
        StageLayout stage = StageLayout.of(640, 360, null);
        assertEquals(StageLayout.MARGIN, stage.getLeft());
        assertEquals(640 - 2 * StageLayout.MARGIN, stage.getWidth());
        assertTrue(stage.contains(320, 180));
        assertFalse(stage.contains(2, 180));
    }

    @Test
    public void theFigureStandsNearTheFootFittedToMostOfTheHeight() {
        StageLayout stage = StageLayout.of(960, 540,
                Arrays.asList(new int[] {0, 320}));
        assertTrue(stage.getFigureBaselineY() < stage.getBottom());
        assertTrue(stage.getFigureBaselineY() > stage.getTop()
                + stage.getHeight() / 2);
        assertTrue(stage.getFigureFitHeight() < stage.getHeight());
        assertEquals(stage.getFigureBaselineY()
                - stage.getFigureFitHeight() / 2.0F, stage.getFigureMiddleY(),
                1.0E-4F);
    }
}
