package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesMapInputTrackerTest {
    @Test
    public void aStationaryPressAndReleaseIsAClick() {
        LostTalesMapInputTracker tracker = new LostTalesMapInputTracker();
        tracker.press(100, 60);

        assertTrue(tracker.isPressActive());
        assertTrue(tracker.releaseAsClick(100, 60));
        assertFalse(tracker.isPressActive());
    }

    @Test
    public void smallJitterStillCountsAsAClick() {
        LostTalesMapInputTracker tracker = new LostTalesMapInputTracker();
        tracker.press(100, 60);

        assertFalse(tracker.moved(
                100 + LostTalesMapInputTracker.DRAG_THRESHOLD_PIXELS,
                60));
        assertTrue(tracker.releaseAsClick(
                100 + LostTalesMapInputTracker.DRAG_THRESHOLD_PIXELS,
                60));
    }

    @Test
    public void aDragIsNeverReportedAsAClick() {
        LostTalesMapInputTracker tracker = new LostTalesMapInputTracker();
        tracker.press(100, 60);

        assertTrue(tracker.moved(140, 90));
        // Panning back to the press position must not turn the drag into a
        // click; the map has already moved underneath the pointer.
        assertFalse(tracker.releaseAsClick(100, 60));
    }

    @Test
    public void releaseWithoutAPressIsNotAClick() {
        LostTalesMapInputTracker tracker = new LostTalesMapInputTracker();

        assertFalse(tracker.releaseAsClick(100, 60));
    }

    @Test
    public void clearingForgetsThePress() {
        LostTalesMapInputTracker tracker = new LostTalesMapInputTracker();
        tracker.press(100, 60);
        tracker.clear();

        assertFalse(tracker.isPressActive());
        assertFalse(tracker.releaseAsClick(100, 60));
    }
}
