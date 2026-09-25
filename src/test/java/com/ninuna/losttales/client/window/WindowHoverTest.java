package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.mapmarker.LostTalesMapCursor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The one answer about what is under the pointer decides the pointer's
 * pose: the hand exactly where a press does something, the arrow over
 * what only shows, and nothing under the pointer answers while a control
 * above it has it.
 */
public final class WindowHoverTest {

    @Test
    public void theHandIsWhereAPressActs() {
        for (WindowHover.Kind kind : new WindowHover.Kind[] {
                WindowHover.Kind.TAB_ROW, WindowHover.Kind.SUB_WINDOW_CLOSE,
                WindowHover.Kind.SUB_WINDOW_STRIP}) {
            assertEquals(kind.name(), LostTalesMapCursor.Pose.HAND,
                    new WindowHover(kind).pose());
        }
    }

    @Test
    public void theArrowIsWhereAPressOnlyLands() {
        for (WindowHover.Kind kind : new WindowHover.Kind[] {
                WindowHover.Kind.NONE, WindowHover.Kind.SUB_WINDOW,
                WindowHover.Kind.OVERLAY}) {
            assertEquals(kind.name(), LostTalesMapCursor.Pose.ARROW,
                    new WindowHover(kind).pose());
        }
        assertEquals(LostTalesMapCursor.Pose.ARROW, WindowHover.NONE.pose());
    }

    @Test
    public void aPageActsOnlyWhenAPressWouldDoSomething() {
        WindowHover page = new WindowHover(WindowHover.Kind.PAGE);
        assertEquals(LostTalesMapCursor.Pose.ARROW, page.pose());
        page.acts = true;
        assertEquals(LostTalesMapCursor.Pose.HAND, page.pose());
        assertEquals("a strip of no window moves nothing",
                LostTalesMapCursor.Pose.ARROW,
                new WindowHover(WindowHover.Kind.STRIP).pose());
    }

    @Test
    public void aSubWindowsEdgeResizesAsAWindowsDoes() {
        WindowHover edge = new WindowHover(WindowHover.Kind.SUB_WINDOW_RESIZE);
        edge.subEdge = WindowGestures.ResizeEdge.LEFT;
        assertEquals(LostTalesMapCursor.Pose.RESIZE_HORIZONTAL, edge.pose());
        edge.subEdge = WindowGestures.ResizeEdge.BOTTOM;
        assertEquals(LostTalesMapCursor.Pose.RESIZE_VERTICAL, edge.pose());
    }

    @Test
    public void aControlAskedWithThePointerAwayFindsNothing() {
        double away = WindowHover.AWAY;
        assertFalse(away >= 0.0D);
        assertFalse(away < 0.0D);
        assertFalse(away >= Double.NEGATIVE_INFINITY);
        PointerRegions regions = new PointerRegions();
        regions.addScreen(-1000, -1000, 1000, 1000);
        assertTrue(regions.contains(0.5D, 0.5D));
        assertFalse(regions.contains(away, away));
    }

    @Test
    public void onlyTheRowThePointerIsOnSeesIt() {
        WindowHover tab = new WindowHover(WindowHover.Kind.TAB_ROW);
        assertFalse("a row hover names its window", tab.isOnRowOf(null));
        assertFalse(new WindowHover(WindowHover.Kind.PAGE).isOnRowOf(null));
    }
}
