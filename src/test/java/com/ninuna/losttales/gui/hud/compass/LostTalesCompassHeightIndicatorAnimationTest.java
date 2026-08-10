package com.ninuna.losttales.gui.hud.compass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesCompassHeightIndicatorAnimationTest {
    @Test
    public void entranceAnticipatesThenSettlesWithSquashAndStretch() {
        LostTalesCompassHeightIndicatorAnimation animation =
                new LostTalesCompassHeightIndicatorAnimation();
        long started = 1L;

        LostTalesCompassHeightIndicatorAnimation.Pose anticipation =
                animation.frame("marker", -1, 1, started).getPose(0);
        assertEquals(0.0F, anticipation.getAlpha(), 0.0F);
        assertTrue(anticipation.getOffsetY() > 0.9F);
        assertTrue(anticipation.getScaleX() > 1.1F);
        assertTrue(anticipation.getScaleY() < 0.8F);

        LostTalesCompassHeightIndicatorAnimation.Pose overshoot =
                animation.frame("marker", -1, 1,
                        started + LostTalesCompassHeightIndicatorAnimation
                                .ENTER_NANOS / 2L).getPose(0);
        assertTrue("up arrow should pass its resting point",
                overshoot.getOffsetY() < 0.0F);

        animation.frame("marker", -1, 1,
                started + LostTalesCompassHeightIndicatorAnimation
                        .ENTER_NANOS);
        LostTalesCompassHeightIndicatorAnimation.Pose settled =
                animation.frame("marker", -1, 1,
                        started + LostTalesCompassHeightIndicatorAnimation
                                .ENTER_NANOS * 2L).getPose(0);
        assertTrue(settled.getAlpha() > 0.95F);
        assertTrue(Math.abs(settled.getOffsetY()) < 0.4F);
        assertTrue(Math.abs(settled.getScaleX() - 1.0F) < 0.03F);
        assertTrue(Math.abs(settled.getScaleY() - 1.0F) < 0.04F);
    }

    @Test
    public void idleBreathingMovesTowardTheRepresentedHeight() {
        LostTalesCompassHeightIndicatorAnimation above =
                new LostTalesCompassHeightIndicatorAnimation();
        LostTalesCompassHeightIndicatorAnimation below =
                new LostTalesCompassHeightIndicatorAnimation();
        long started = 1L;
        above.frame("above", -1, 1, started);
        below.frame("below", 1, 1, started);
        above.frame("above", -1, 1, started
                + LostTalesCompassHeightIndicatorAnimation.ENTER_NANOS);
        below.frame("below", 1, 1, started
                + LostTalesCompassHeightIndicatorAnimation.ENTER_NANOS);
        long quarterCycle = started
                + LostTalesCompassHeightIndicatorAnimation
                        .IDLE_PERIOD_NANOS / 4L;

        float aboveY = above.frame("above", -1, 1, quarterCycle)
                .getPose(0).getOffsetY();
        float belowY = below.frame("below", 1, 1, quarterCycle)
                .getPose(0).getOffsetY();
        assertTrue(aboveY < -0.25F);
        assertTrue(belowY > 0.25F);
        assertEquals(Math.abs(aboveY), Math.abs(belowY), 0.001F);
    }

    @Test
    public void highlightFlowsThroughTheStackInDisplayedDirection() {
        LostTalesCompassHeightIndicatorAnimation above =
                new LostTalesCompassHeightIndicatorAnimation();
        LostTalesCompassHeightIndicatorAnimation below =
                new LostTalesCompassHeightIndicatorAnimation();
        long started = 1L;
        above.frame("above", -1, 2, started);
        below.frame("below", 1, 2, started);
        for (long time = LostTalesCompassHeightIndicatorAnimation
                        .ENTER_NANOS;
             time <= LostTalesCompassHeightIndicatorAnimation
                        .FLOW_PERIOD_NANOS;
             time += LostTalesCompassHeightIndicatorAnimation
                        .ENTER_NANOS) {
            above.frame("above", -1, 2, started + time);
            below.frame("below", 1, 2, started + time);
        }

        long firstBeat = started
                + LostTalesCompassHeightIndicatorAnimation
                        .FLOW_PERIOD_NANOS
                + LostTalesCompassHeightIndicatorAnimation
                        .FLOW_PERIOD_NANOS * 14L / 100L;
        LostTalesCompassHeightIndicatorAnimation.Frame aboveFirst =
                above.frame("above", -1, 2, firstBeat);
        LostTalesCompassHeightIndicatorAnimation.Frame belowFirst =
                below.frame("below", 1, 2, firstBeat);
        assertTrue(aboveFirst.getPose(1).getFlowEmphasis() > 0.95F);
        assertTrue(aboveFirst.getPose(1).getFlowEmphasis()
                > aboveFirst.getPose(0).getFlowEmphasis() + 0.8F);
        assertTrue(belowFirst.getPose(0).getFlowEmphasis() > 0.95F);
        assertTrue(belowFirst.getPose(0).getFlowEmphasis()
                > belowFirst.getPose(1).getFlowEmphasis() + 0.8F);

        long secondBeat = started
                + LostTalesCompassHeightIndicatorAnimation
                        .FLOW_PERIOD_NANOS
                + LostTalesCompassHeightIndicatorAnimation
                        .FLOW_PERIOD_NANOS * 36L / 100L;
        LostTalesCompassHeightIndicatorAnimation.Frame belowSecond =
                below.frame("below", 1, 2, secondBeat);
        assertTrue(belowSecond.getPose(1).getFlowEmphasis() > 0.95F);
        assertTrue(belowSecond.getPose(1).getBrightness()
                > belowSecond.getPose(0).getBrightness() + 0.15F);
        assertTrue(belowSecond.getPose(1).getScaleY()
                > belowSecond.getPose(0).getScaleY() + 0.03F);
    }

    @Test
    public void flowCycleLeavesAQuietReadabilityInterval() {
        LostTalesCompassHeightIndicatorAnimation animation =
                new LostTalesCompassHeightIndicatorAnimation();
        long started = 1L;
        animation.frame("marker", 1, 2, started);
        animation.frame("marker", 1, 2, started
                + LostTalesCompassHeightIndicatorAnimation.ENTER_NANOS);
        animation.frame("marker", 1, 2, started
                + LostTalesCompassHeightIndicatorAnimation.ENTER_NANOS * 2L);
        long quietBeat = started
                + LostTalesCompassHeightIndicatorAnimation
                        .FLOW_PERIOD_NANOS * 70L / 100L;
        LostTalesCompassHeightIndicatorAnimation.Frame quiet =
                animation.frame("marker", 1, 2, quietBeat);
        assertTrue(quiet.getPose(0).getFlowEmphasis() < 0.01F);
        assertTrue(quiet.getPose(1).getFlowEmphasis() < 0.01F);
    }

    @Test
    public void secondTierFollowsThenCollapsesWithoutPopping() {
        LostTalesCompassHeightIndicatorAnimation animation =
                new LostTalesCompassHeightIndicatorAnimation();
        long started = 1L;
        long settled = started
                + LostTalesCompassHeightIndicatorAnimation.ENTER_NANOS * 2L;
        assertEquals(1, animation.frame(
                "marker", -1, 1, started).getArrowCount());
        animation.frame("marker", -1, 1, started
                + LostTalesCompassHeightIndicatorAnimation.ENTER_NANOS);
        animation.frame("marker", -1, 1, settled);

        LostTalesCompassHeightIndicatorAnimation.Frame escalated =
                animation.frame("marker", -1, 2, settled + 1L);
        assertEquals(2, escalated.getArrowCount());
        assertTrue(escalated.getPose(0).getAlpha() > 0.95F);
        assertEquals(0.0F, escalated.getPose(1).getAlpha(), 0.0F);

        long secondarySettled = settled + 1L
                + LostTalesCompassHeightIndicatorAnimation
                        .SECONDARY_DELAY_NANOS
                + LostTalesCompassHeightIndicatorAnimation.ENTER_NANOS;
        LostTalesCompassHeightIndicatorAnimation.Frame full =
                animation.frame("marker", -1, 2, secondarySettled);
        assertTrue(full.getPose(1).getAlpha() > 0.95F);

        long deescalated = secondarySettled + 1L;
        LostTalesCompassHeightIndicatorAnimation.Frame exiting =
                animation.frame("marker", -1, 1, deescalated);
        assertEquals(2, exiting.getArrowCount());
        assertTrue(exiting.getPose(1).getAlpha() > 0.95F);

        LostTalesCompassHeightIndicatorAnimation.Frame collapsed =
                animation.frame("marker", -1, 1,
                        deescalated
                                + LostTalesCompassHeightIndicatorAnimation
                                        .EXIT_NANOS + 1L);
        assertEquals(1, collapsed.getArrowCount());
    }
}
