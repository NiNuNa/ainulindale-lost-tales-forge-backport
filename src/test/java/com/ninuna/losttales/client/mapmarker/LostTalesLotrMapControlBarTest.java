package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class LostTalesLotrMapControlBarTest {
    /** Four hints, as the map strip carries: close, zoom, legend, waypoint. */
    private static final int[] HINTS = {20, 16, 20, 20};

    @Test
    public void aWideStripShowsEveryHintWithItsLabel() {
        LostTalesLotrMapControlBar.Layout layout = layout(900);

        assertEquals(HINTS.length, layout.visibleHints);
        assertTrue(layout.showLabels);
        assertTrue(layout.leftEnd <= 900 / 2);
    }

    @Test
    public void labelsAreDroppedBeforeAnyHintIs() {
        LostTalesLotrMapControlBar.Layout layout = layout(240);

        assertFalse("labels must go first", layout.showLabels);
        assertEquals("no hint should have been dropped yet",
                HINTS.length, layout.visibleHints);
        assertTrue(layout.leftEnd <= 240 / 2);
    }

    @Test
    public void aNarrowStripKeepsTheHintsListedFirst() {
        LostTalesLotrMapControlBar.Layout layout = layout(120);

        assertTrue(layout.visibleHints > 0);
        assertTrue(layout.visibleHints < HINTS.length);
        assertFalse(layout.showLabels);
        assertTrue(layout.leftEnd <= 120 / 2);
    }

    @Test
    public void aStripWithNoRoomAtAllDrawsNothing() {
        LostTalesLotrMapControlBar.Layout layout = layout(20);

        assertEquals(0, layout.visibleHints);
        assertFalse(layout.showLabels);
    }

    @Test
    public void theStripNeverReachesPastItsHalfOfTheScreen() {
        for (int width = 0; width <= 1600; width += 17) {
            LostTalesLotrMapControlBar.Layout layout = layout(width);
            assertTrue("overflowed at " + width,
                    layout.leftEnd <= Math.max(6, width / 2));
        }
    }

    @Test
    public void anEmptyStripIsHandledWithoutHints() {
        LostTalesLotrMapControlBar.Layout layout =
                LostTalesLotrMapControlBar.calculateLayout(
                        800, Collections
                                .<LostTalesLotrMapControlBar.Hint>
                                        emptyList());

        assertEquals(0, layout.visibleHints);
        assertFalse(layout.showLabels);
    }

    private static LostTalesLotrMapControlBar.Layout layout(
            int screenWidth) {
        List<LostTalesLotrMapControlBar.Hint> hints =
                LostTalesLotrMapControlBar.measuredHints(HINTS);
        return LostTalesLotrMapControlBar.calculateLayout(
                screenWidth, hints);
    }
}
