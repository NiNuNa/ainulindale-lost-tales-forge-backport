package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.gui.controlbar.LostTalesControlBar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class LostTalesLotrMapControlBarTest {
    /** Six hints, as the map strip carries them, in the order they are given up. */
    private static final int[] HINTS = {20, 16, 20, 20, 24, 20};
    /** A date about as long as "Mersday 22 Halimath, S.R. 1401   08:53". */
    private static final int[] CALENDAR = {160, 110};

    @Test
    public void aWideStripShowsEveryHintWithItsLabel() {
        LostTalesControlBar.Layout layout = layout(1200, CALENDAR);

        assertEquals(HINTS.length, layout.visibleHints());
        assertTrue(layout.showLabels);
        assertTrue(layout.showStatus);
    }

    /** Controls belong at both ends, not stacked against one of them. */
    @Test
    public void hintsAreSplitAcrossBothEndsOfTheStrip() {
        LostTalesControlBar.Layout layout = layout(1200, CALENDAR);

        assertTrue("nothing was put on the left", layout.leftHints > 0);
        assertTrue("nothing was put on the right", layout.rightHints > 0);
    }

    /**
     * The middle carries the place name and the coordinates. Neither group
     * may reach into it, at any width, or the two overwrite each other.
     */
    @Test
    public void neitherEndReachesIntoTheMiddle() {
        for (int width = 0; width <= 2400; width += 13) {
            LostTalesControlBar.Layout layout = layout(width, CALENDAR);
            int band = Math.max(0, (width - 180) / 2 - 6);
            assertTrue("the left end overflowed at " + width,
                    groupWidth(layout, 0, layout.leftHints) <= band);
            assertTrue("the right end overflowed at " + width,
                    groupWidth(layout, 3, layout.rightHints)
                            + layout.statusWidth() <= band);
        }
    }

    /**
     * A row of bare keys says which keys do something but not what, so a hint
     * is given up before every other hint's name is.
     */
    @Test
    public void hintsAreDroppedBeforeTheirLabelsAre() {
        LostTalesControlBar.Layout layout = layout(360, CALENDAR);

        assertTrue("names are worth more than an extra bare key",
                layout.showLabels);
        assertTrue(layout.visibleHints() > 0);
        assertTrue(layout.visibleHints() < HINTS.length);
    }

    @Test
    public void aStripTooNarrowForOneNamedHintFallsBackToIcons() {
        LostTalesControlBar.Layout layout = layout(250, new int[0]);

        assertTrue(layout.visibleHints() > 0);
        assertFalse(layout.showLabels);
    }

    @Test
    public void aStripWithNoRoomAtAllDrawsNothing() {
        LostTalesControlBar.Layout layout = layout(20, CALENDAR);

        assertEquals(0, layout.visibleHints());
        assertFalse(layout.showLabels);
    }

    /**
     * The date shares the right end with a control group and is worth less
     * than any of it, so it may only ever take room the controls left.
     */
    @Test
    public void theDateIsGivenUpBeforeAControlIs() {
        boolean sawCalendarDropped = false;
        for (int width = 0; width <= 2400; width += 7) {
            LostTalesControlBar.Layout layout = layout(width, CALENDAR);
            LostTalesControlBar.Layout without =
                    layout(width, new int[0]);
            if (layout.rightHints > 0 && !layout.showStatus) {
                sawCalendarDropped = true;
            }
            assertEquals("the date cost a control its place at " + width,
                    without.visibleHints(), layout.visibleHints());
            assertEquals(without.showLabels, layout.showLabels);
        }
        assertTrue("a narrow strip must drop the date", sawCalendarDropped);
    }

    @Test
    public void anEmptyStripIsHandledWithoutHints() {
        LostTalesControlBar.Layout layout =
                LostTalesControlBar.calculateLayout(
                        800, Collections
                                .<LostTalesControlBar.Hint>
                                        emptyList(), 3, 180, CALENDAR);

        assertEquals(0, layout.visibleHints());
        assertFalse(layout.showLabels);
    }

    /** Widest each drawn group is, from the same measurements it was fitted on. */
    private static int groupWidth(
            LostTalesControlBar.Layout layout, int from, int count) {
        int width = 0;
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                width += 10;
            }
            width += HINTS[from + index]
                    + (layout.showLabels ? 3 + HINTS[from + index] : 0);
        }
        return width;
    }

    private static LostTalesControlBar.Layout layout(
            int screenWidth, int[] calendarWidths) {
        List<LostTalesControlBar.Hint> hints =
                LostTalesControlBar.measuredHints(HINTS);
        return LostTalesControlBar.calculateLayout(
                screenWidth, hints, 3, 180, calendarWidths);
    }
}
