package com.ninuna.losttales.client.window;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.chat.ChatLayout;
import com.ninuna.losttales.client.chat.ChatTab;
import com.ninuna.losttales.client.chat.ClientChatChannelState;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WindowGesturesTest {
    private WindowFrame frame;

    @Before
    public void setUp() {
        ChatLayout.reset();
        ClientChatChannelState.clear();
        this.frame = WindowFrame.of(WindowLayout.windows().get(0));
        this.frame.drawn = true;
        this.frame.boxLeft = 100;
        this.frame.boxTop = 50;
        this.frame.boxRight = 300;
        this.frame.boxBottom = 150;
    }

    @After
    public void tearDown() {
        ChatLayout.reset();
        ClientChatChannelState.clear();
    }

    /**
     * Where a dragged window snaps to: the whole screen from the top
     * edge, a half from a side, a quarter from the end of a side edge
     * nearest a corner or from the corner itself along the top and
     * bottom edges, nothing from the bottom edge's middle or from
     * anywhere clear of the edges.
     */
    @Test
    public void theScreenEdgesSnapAWindowToTheirHalvesAndCorners() {
        assertEquals(Window.ScreenFill.FULL,
                WindowGestures.snapZoneAt(500.0D, 0.5D, 1000, 600, false));
        assertEquals(Window.ScreenFill.LEFT,
                WindowGestures.snapZoneAt(0.0D, 300.0D, 1000, 600, false));
        assertEquals(Window.ScreenFill.RIGHT,
                WindowGestures.snapZoneAt(999.5D, 300.0D, 1000, 600, false));
        assertEquals(Window.ScreenFill.TOP_LEFT,
                WindowGestures.snapZoneAt(1.0D, 50.0D, 1000, 600, false));
        assertEquals(Window.ScreenFill.TOP_LEFT,
                WindowGestures.snapZoneAt(60.0D, 1.0D, 1000, 600, false));
        assertEquals(Window.ScreenFill.TOP_RIGHT,
                WindowGestures.snapZoneAt(999.0D, 100.0D, 1000, 600, false));
        assertEquals(Window.ScreenFill.BOTTOM_LEFT,
                WindowGestures.snapZoneAt(0.0D, 550.0D, 1000, 600, false));
        assertEquals(Window.ScreenFill.BOTTOM_RIGHT,
                WindowGestures.snapZoneAt(950.0D, 599.5D, 1000, 600, false));
        // Past the corner along the top edge a screen without the thirds
        // fills the whole screen.
        assertEquals(Window.ScreenFill.FULL,
                WindowGestures.snapZoneAt(100.0D, 1.0D, 1000, 600, false));
        // The bottom edge's middle, and anywhere inside, snap nowhere.
        assertEquals(Window.ScreenFill.NONE,
                WindowGestures.snapZoneAt(500.0D, 599.5D, 1000, 600, false));
        assertEquals(Window.ScreenFill.NONE,
                WindowGestures.snapZoneAt(900.0D, 599.5D, 1000, 600, false));
        assertEquals(Window.ScreenFill.LEFT,
                WindowGestures.snapZoneAt(14.0D, 300.0D, 1000, 600, false));
        assertEquals(Window.ScreenFill.NONE,
                WindowGestures.snapZoneAt(18.0D, 300.0D, 1000, 600, false));
        assertEquals(Window.ScreenFill.NONE,
                WindowGestures.snapZoneAt(500.0D, 300.0D, 1000, 600, false));
    }

    /**
     * On a screen wide enough to offer the thirds, the top edge snaps to
     * them as Windows 11 does on a large screen: its left third to the
     * left third, its right third to the right, the corners still to
     * their quarters and the middle to the whole screen.
     */
    @Test
    public void aLargeScreensTopEdgeSnapsToTheThirds() {
        assertEquals(Window.ScreenFill.TOP_LEFT,
                WindowGestures.snapZoneAt(60.0D, 1.0D, 1000, 600, true));
        assertEquals(Window.ScreenFill.LEFT_THIRD,
                WindowGestures.snapZoneAt(190.0D, 1.0D, 1000, 600, true));
        assertEquals(Window.ScreenFill.LEFT_THIRD,
                WindowGestures.snapZoneAt(332.0D, 1.0D, 1000, 600, true));
        assertEquals(Window.ScreenFill.FULL,
                WindowGestures.snapZoneAt(500.0D, 1.0D, 1000, 600, true));
        assertEquals(Window.ScreenFill.RIGHT_THIRD,
                WindowGestures.snapZoneAt(700.0D, 1.0D, 1000, 600, true));
        assertEquals(Window.ScreenFill.TOP_RIGHT,
                WindowGestures.snapZoneAt(940.0D, 1.0D, 1000, 600, true));
        // The side edges keep their halves and quarters.
        assertEquals(Window.ScreenFill.LEFT,
                WindowGestures.snapZoneAt(0.0D, 300.0D, 1000, 600, true));
    }

    @Test
    public void theBorderOutsideAWindowAnswersWithItsEdgeOrCorner() {
        int border = WindowGestures.RESIZE_BORDER;
        int corner = WindowGestures.RESIZE_CORNER;
        // Just outside each edge, mid-way along it: the band is the
        // border's width of whole pixels outside the window's own.
        assertEquals(WindowGestures.ResizeEdge.LEFT,
                WindowGestures.edgeAt(this.frame, 100 - border, 100));
        assertEquals(WindowGestures.ResizeEdge.RIGHT,
                WindowGestures.edgeAt(this.frame, 300 + border - 1, 100));
        assertEquals(WindowGestures.ResizeEdge.TOP,
                WindowGestures.edgeAt(this.frame, 200, 50 - border));
        assertEquals(WindowGestures.ResizeEdge.BOTTOM,
                WindowGestures.edgeAt(this.frame, 200, 150 + border - 1));
        // On an edge but near its end: the corner reaches along it.
        assertEquals(WindowGestures.ResizeEdge.TOP_LEFT,
                WindowGestures.edgeAt(this.frame, 100 + corner, 50 - 1));
        assertEquals(WindowGestures.ResizeEdge.TOP_RIGHT,
                WindowGestures.edgeAt(this.frame, 300 - corner, 50 - 1));
        assertEquals(WindowGestures.ResizeEdge.BOTTOM_LEFT,
                WindowGestures.edgeAt(this.frame, 100 - 1, 150 - corner));
        assertEquals(WindowGestures.ResizeEdge.BOTTOM_RIGHT,
                WindowGestures.edgeAt(this.frame, 300 + 1, 150 - corner));
        // Beyond the border, and inside the window, is nobody's edge.
        assertNull(WindowGestures.edgeAt(this.frame, 100 - border - 1,
                100));
        assertNull(WindowGestures.edgeAt(this.frame, 300 + border, 100));
        assertNull(WindowGestures.edgeAt(this.frame, 200, 100));
        assertTrue(WindowGestures.coversPoint(this.frame, 200, 100));
        assertFalse(WindowGestures.coversPoint(this.frame, 200, 160));
    }

    /**
     * The window's first pixel and its last are the window's, whatever
     * asks: the resize band begins outside them, so its hover, its press
     * and the window's own never claim the same point.
     */
    @Test
    public void aWindowsEdgePixelsAreItsOwnAndNotTheBorders() {
        assertTrue(WindowGestures.coversPoint(this.frame, 100, 100));
        assertTrue(this.frame.contains(100, 50));
        assertTrue(this.frame.contains(299.5, 149.5));
        assertNull(WindowGestures.edgeAt(this.frame, 100, 100));
        assertNull(WindowGestures.edgeAt(this.frame, 299.5, 100));
        assertNull(WindowGestures.edgeAt(this.frame, 200, 50));
        assertFalse(this.frame.contains(99.5, 100));
        assertFalse(this.frame.contains(300, 100));
        assertEquals(WindowGestures.ResizeEdge.LEFT,
                WindowGestures.edgeAt(this.frame, 99.5, 100));
        assertEquals(WindowGestures.ResizeEdge.RIGHT,
                WindowGestures.edgeAt(this.frame, 300, 100));
        assertEquals(WindowGestures.ResizeEdge.TOP,
                WindowGestures.edgeAt(this.frame, 200, 49.5));
    }

    @Test
    public void pullsAreMeasuredAsOneStraightLine() {
        assertEquals(0, WindowGestures.overhangOf(5, 0, 10));
        assertEquals(3, WindowGestures.overhangOf(-3, 0, 10));
        assertEquals(0, WindowGestures.overhangOf(10, 0, 10));
        assertEquals(1, WindowGestures.overhangOf(11, 0, 10));
        assertTrue(WindowGestures.pulledBeyond(3, 4, 5));
        assertFalse(WindowGestures.pulledBeyond(3, 3, 5));
        assertTrue(WindowGestures.pulledBeyond(0, 5, 5));
    }

    @Test
    public void aDraggedHeightStaysBetweenTheLeastBoxAndTheLargestWindow() {
        double least = 88.0D;
        assertEquals(least, WindowGestures.clampedHeight(0.0D, least), 0.0D);
        assertEquals(WindowLayout.MAX_WINDOW_SIZE,
                WindowGestures.clampedHeight(99999.0D, least), 0.0D);
        assertEquals(least + 5.5D,
                WindowGestures.clampedHeight(least + 5.5D, least), 0.0D);
    }

    @Test
    public void aVisibleSlotIsTranslatedPastHiddenTabsAndTheMovingOnes() {
        Window window = WindowLayout.windows().get(1);
        List<WindowTab> tabs = window.getTabs();
        // A server gate hides Party while its layout slot stays intact.
        java.util.List<String> allowed = new java.util.ArrayList<String>();
        for (ChatChannel channel : ChatChannel.values()) {
            if (channel != ChatChannel.PARTY) { allowed.add(channel.getId()); }
        }
        ClientChatChannelState.setChannelGates(allowed, allowed);
        assertTrue(tabs.contains(ChatTab.of(ChatChannel.PARTY)));
        assertFalse(ClientChatChannelState.isAvailable(
                ChatTab.of(ChatChannel.PARTY)));
        // The k-th visible slot lands on the list index of the k-th
        // available tab, hidden ones between counted past.
        int visible = 0;
        for (int index = 0; index < tabs.size(); index++) {
            if (tabs.get(index).isAvailable()) {
                assertEquals(index, WindowGestures.listPosition(window,
                        Collections.<WindowTab>emptyList(), visible));
                visible++;
            }
        }
        // Past the end: the position after the last tab.
        assertEquals(tabs.size(), WindowGestures.listPosition(window,
                Collections.<WindowTab>emptyList(), visible));
        // A tab on its way out is neither counted nor stood on: with the
        // first available tab moving, slot zero lands where the second
        // available tab stands, less the moving tab's own place.
        WindowTab first = null;
        int firstIndex = -1;
        int secondIndex = -1;
        for (int index = 0; index < tabs.size(); index++) {
            if (!tabs.get(index).isAvailable()) {
                continue;
            }
            if (first == null) {
                first = tabs.get(index);
                firstIndex = index;
            } else {
                secondIndex = index;
                break;
            }
        }
        assertTrue(firstIndex >= 0 && secondIndex > firstIndex);
        assertEquals(secondIndex - 1, WindowGestures.listPosition(window,
                Arrays.asList(first), 0));
    }
}
