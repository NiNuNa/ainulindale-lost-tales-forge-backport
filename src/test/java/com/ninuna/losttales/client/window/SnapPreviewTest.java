package com.ninuna.losttales.client.window;

import com.ninuna.losttales.config.LostTalesConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The preview pane: it stands in the part of the screen a carried
 * window would fill, goes when the pointer leaves the zone, and moves
 * every edge straight from where it set out to where it is bound.
 * Motion is off here, so every leg lands at once. Without a Minecraft
 * instance a window filling the whole of a 960 by 540 screen is 956 by
 * 536, two pixels in from every edge.
 */
public final class SnapPreviewTest {
    private static final double EPSILON = 1.0E-9D;
    private boolean animations;

    @Before
    public void setUp() {
        this.animations = LostTalesConfig.animations;
        LostTalesConfig.animations = false;
    }

    @After
    public void tearDown() {
        LostTalesConfig.animations = this.animations;
    }

    /** Aimed at a zone, the pane stands in it, whole, the window's own box aside. */
    @Test
    public void thePaneStandsInTheZoneAimedAt() {
        SnapPreview preview = new SnapPreview();
        preview.aim("w1", Window.ScreenFill.FULL);
        SnapPreview.Pane pane = preview.advance(null, window(), 960, 540);
        assertPane(2.0D, 2.0D, 958.0D, 538.0D, pane);
        assertEquals(1.0F, preview.opacity(), 0.0F);
        assertEquals("w1", preview.windowId());
        preview.aim("w1", Window.ScreenFill.RIGHT_THIRD);
        assertPane(642.0D, 2.0D, 958.0D, 538.0D,
                preview.advance(null, window(), 960, 540));
    }

    /**
     * Out of the zone, or let go where nothing lands, the pane goes and
     * the preview forgets its window; let go in the zone it goes too, the
     * window gliding into its place.
     */
    @Test
    public void thePaneGoesWhenThereIsNothingToShow() {
        SnapPreview preview = new SnapPreview();
        preview.aim("w1", Window.ScreenFill.LEFT);
        preview.advance(null, window(), 960, 540);
        preview.aim("w1", Window.ScreenFill.NONE);
        assertNull(preview.advance(null, window(), 960, 540));
        assertNull(preview.windowId());
        preview.aim("w1", Window.ScreenFill.LEFT);
        preview.advance(null, window(), 960, 540);
        preview.release(true);
        assertNull(preview.advance(null, window(), 960, 540));
        // Another window's aim takes the preview over at once.
        preview.aim("w1", Window.ScreenFill.LEFT);
        preview.aim("w2", Window.ScreenFill.TOP_LEFT);
        assertEquals("w2", preview.windowId());
    }

    /** Every edge travels straight, the same share of its own way. */
    @Test
    public void everyEdgeTravelsTheSameShareOfItsWay() {
        SnapPreview.Pane from = new SnapPreview.Pane(100.0D, 200.0D,
                260.0D, 290.0D);
        SnapPreview.Pane to = new SnapPreview.Pane(2.0D, 2.0D, 478.0D,
                538.0D);
        assertPane(51.0D, 101.0D, 369.0D, 414.0D, from.toward(to, 0.5D));
        assertPane(100.0D, 200.0D, 260.0D, 290.0D, from.toward(to, 0.0D));
        assertPane(2.0D, 2.0D, 478.0D, 538.0D, from.toward(to, 1.0D));
    }

    /** A window 160 wide and 90 tall at (100, 200): where a pane grows out of. */
    private static WindowPlacement.Box window() {
        return new WindowPlacement.Box(100.0D, 200.0D, 160, 90.0D, 37);
    }

    private static void assertPane(double left, double top, double right,
                                   double bottom, SnapPreview.Pane pane) {
        assertEquals(left, pane.left, EPSILON);
        assertEquals(top, pane.top, EPSILON);
        assertEquals(right, pane.right, EPSILON);
        assertEquals(bottom, pane.bottom, EPSILON);
    }
}
