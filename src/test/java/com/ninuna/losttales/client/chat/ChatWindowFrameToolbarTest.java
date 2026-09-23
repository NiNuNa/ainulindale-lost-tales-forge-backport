package com.ninuna.losttales.client.chat;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A hovered message's toolbar is one frame of controls side by side; it
 * answers the pointer control by control, where each was drawn, and only
 * the control the pointer is on lights.
 */
public final class ChatWindowFrameToolbarTest {
    private static final int REACT =
            LostTalesChatOverlayRenderer.TOOLBAR_REACT;
    private static final int REPLY =
            LostTalesChatOverlayRenderer.TOOLBAR_REPLY;
    private static final int LINK = LostTalesChatOverlayRenderer.TOOLBAR_LINK;
    private static final int MORE = LostTalesChatOverlayRenderer.TOOLBAR_MORE;

    @After
    public void cleanUp() {
        ChatWindowFrame.clear();
    }

    @Test
    public void eachControlAnswersAcrossItsSquare() {
        ChatWindowFrame frame = ChatWindowFrame.of(new ChatWindow("w9"));
        frame.drawn = true;
        // Five fourteen-pixel squares inside one frame's two-pixel edge,
        // as tall as a framed button.
        assertEquals(74, LostTalesChatOverlayRenderer.toolbarWidth());
        assertEquals(18, LostTalesChatOverlayRenderer.TOOLBAR_HEIGHT);
        // Drawn at half size, as at GUI scale 2.
        frame.toolbarLeft = 100.0F;
        frame.toolbarTop = 20.0F;
        frame.toolbarRight = 137.0F;
        frame.toolbarBottom = 29.0F;
        frame.toolbarCellsLeft = 101.0F;
        frame.toolbarCellWidth = 7.0F;
        frame.toolbarKinds = LostTalesChatOverlayRenderer.TOOLBAR_KINDS;
        frame.toolbarWhy = new String[] {"", "", "", "Only on this computer",
                ""};
        // The frame's edge belongs to the control beside it.
        assertEquals(REACT, frame.toolbarKindAt(100.0D, 25.0D));
        assertEquals(REACT, frame.toolbarKindAt(107.9D, 25.0D));
        assertEquals(REPLY, frame.toolbarKindAt(108.0D, 25.0D));
        assertEquals(LINK, frame.toolbarKindAt(128.5D, 25.0D));
        // The menu's dots stand last, where a menu opens from.
        assertEquals(MORE, frame.toolbarKindAt(129.0D, 25.0D));
        assertEquals(MORE, frame.toolbarKindAt(136.5D, 25.0D));
        assertEquals(129.0F, frame.toolbarCellLeft(MORE), 0.0F);
        assertEquals(101.0F, frame.toolbarCellLeft(REACT), 0.0F);
        // Past the frame, and below it, is not the toolbar.
        assertEquals(-1, frame.toolbarKindAt(137.0D, 25.0D));
        assertEquals(-1, frame.toolbarKindAt(120.0D, 29.0D));
        // A control that cannot be taken says why; the others say nothing.
        assertEquals("Only on this computer", frame.toolbarWhy(LINK));
        assertEquals("", frame.toolbarWhy(REACT));
    }

    /**
     * After a scroll the toolbar stays away until the history rests and
     * the pointer has rested on one message; moving from message to
     * message without a scroll keeps it up.
     */
    @Test
    public void theToolbarWaitsForTheHistoryAndThePointerToRest() {
        ChatWindowFrame frame = ChatWindowFrame.of(new ChatWindow("w3"));
        long second = 1000000000L;
        assertEquals(0.0F, frame.toolbarShare(7, true, second), 0.0F);
        // The history rests; the pointer has not rested long yet.
        assertEquals(0.0F, frame.toolbarShare(7, false, second + 1000000L), 0.0F);
        assertEquals(0.0F, frame.toolbarShare(7, false, second + 50000000L), 0.0F);
        // Another message under the pointer starts the rest again.
        assertEquals(0.0F, frame.toolbarShare(8, false, second + 100000000L), 0.0F);
        assertEquals(0.0F, frame.toolbarShare(8, false, second + 200000000L), 0.0F);
        // Rested long enough: it comes up, and stays up from message to message.
        float share = frame.toolbarShare(8, false, second + 400000000L);
        assertTrue(share >= 0.0F);
        assertTrue(frame.toolbarShare(9, false, second + 900000000L) > 0.0F);
    }

    @Test
    public void onlyTheControlThePointerIsOnLights() {
        ChatWindowFrame first = ChatWindowFrame.of(new ChatWindow("w1"));
        ChatWindowFrame second = ChatWindowFrame.of(new ChatWindow("w2"));
        ChatWindowFrame.noteHoveredControls(first, REPLY, second);
        assertEquals(REPLY, first.hoveredToolbarKind);
        assertFalse(first.jumpHovered);
        assertEquals(-1, second.hoveredToolbarKind);
        assertTrue(second.jumpHovered);
        ChatWindowFrame.noteHoveredControls(null, -1, null);
        assertEquals(-1, first.hoveredToolbarKind);
        assertFalse(second.jumpHovered);
    }
}
