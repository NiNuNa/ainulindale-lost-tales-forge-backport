package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Which edge of a neighbour a window rests against, within the snap. */
public final class ChatTabActionsTest {
    private ChatWindowFrame frame;
    private ChatWindowFrame other;

    @Before
    public void setUp() {
        ChatWindowLayout.reset();
        List<ChatWindow> windows = ChatWindowLayout.windows();
        this.frame = ChatWindowFrame.of(windows.get(0));
        this.other = ChatWindowFrame.of(windows.get(1));
        box(this.other, 100, 100, 300, 200);
    }

    @After
    public void tearDown() {
        ChatWindowLayout.reset();
    }

    private static void box(ChatWindowFrame frame, double left, double top,
                            double right, double bottom) {
        frame.drawn = true;
        frame.boxLeft = left;
        frame.boxTop = top;
        frame.boxRight = right;
        frame.boxBottom = bottom;
    }

    @Test
    public void aWindowRestingOnEachEdgeIsToldWhichOne() {
        int margin = ChatWindowPlacement.WINDOW_GAP;
        // Above: its bottom sits one window gap over the neighbour's top,
        // the two frames side by side.
        box(this.frame, 120, 20, 280, 100 - margin);
        assertEquals(ChatWindow.LinkSide.ABOVE,
                ChatTabActions.touchingSide(this.frame, this.other));
        // Below.
        box(this.frame, 120, 200 + margin, 280, 260);
        assertEquals(ChatWindow.LinkSide.BELOW,
                ChatTabActions.touchingSide(this.frame, this.other));
        // Left, within the snap distance.
        box(this.frame, 10, 120, 100 - margin - ChatTabActions.LINK_SNAP,
                180);
        assertEquals(ChatWindow.LinkSide.LEFT,
                ChatTabActions.touchingSide(this.frame, this.other));
        // Right.
        box(this.frame, 300 + margin, 120, 400, 180);
        assertEquals(ChatWindow.LinkSide.RIGHT,
                ChatTabActions.touchingSide(this.frame, this.other));
    }

    @Test
    public void aWindowClearOfTheNeighbourTouchesNothing() {
        int margin = ChatWindowPlacement.WINDOW_GAP;
        // Too far above.
        box(this.frame, 120, 20, 280, 100 - margin - ChatTabActions.LINK_SNAP
                - 1);
        assertNull(ChatTabActions.touchingSide(this.frame, this.other));
        // Level with it but far to the left.
        box(this.frame, 0, 120, 40, 180);
        assertNull(ChatTabActions.touchingSide(this.frame, this.other));
        // Diagonal: neither column nor row overlaps.
        box(this.frame, 320, 20, 400, 80);
        assertNull(ChatTabActions.touchingSide(this.frame, this.other));
    }
}
