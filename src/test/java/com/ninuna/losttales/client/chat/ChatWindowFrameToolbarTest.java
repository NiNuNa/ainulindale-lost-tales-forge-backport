package com.ninuna.losttales.client.chat;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A hovered message's toolbar answers the pointer button by button,
 * where each of its framed buttons was drawn, and only the control the
 * pointer is on lights.
 */
public final class ChatWindowFrameToolbarTest {
    private static final int REACT =
            LostTalesChatOverlayRenderer.TOOLBAR_REACT;
    private static final int REPLY =
            LostTalesChatOverlayRenderer.TOOLBAR_REPLY;
    private static final int COPY = LostTalesChatOverlayRenderer.TOOLBAR_COPY;

    @After
    public void cleanUp() {
        ChatWindowFrame.clear();
    }

    @Test
    public void eachButtonAnswersAcrossItsStride() {
        ChatWindowFrame frame = ChatWindowFrame.of(new ChatWindow("w9"));
        frame.drawn = true;
        int size = LostTalesChatOverlayRenderer.TOOLBAR_BUTTON_SIZE;
        int stride = LostTalesChatOverlayRenderer.TOOLBAR_STRIDE;
        // Three buttons and the two gaps between them.
        assertEquals(58, LostTalesChatOverlayRenderer.toolbarWidth(3));
        frame.toolbarLeft = 100.0F;
        frame.toolbarTop = 20.0F;
        frame.toolbarRight = 100.0F
                + LostTalesChatOverlayRenderer.toolbarWidth(3);
        frame.toolbarBottom = 20.0F + size;
        frame.toolbarStride = stride;
        frame.toolbarKinds = new int[] {REACT, REPLY, COPY};
        assertEquals(REACT, frame.toolbarKindAt(100.0D, 25.0D));
        // The gap after a button belongs to it: no dead pixels between.
        assertEquals(REACT, frame.toolbarKindAt(100.0D + stride - 0.5D,
                25.0D));
        assertEquals(REPLY, frame.toolbarKindAt(100.0D + stride, 25.0D));
        assertEquals(COPY, frame.toolbarKindAt(157.5D, 25.0D));
        // Past the last button, and below the buttons, is not the toolbar.
        assertEquals(-1, frame.toolbarKindAt(158.0D, 25.0D));
        assertEquals(-1, frame.toolbarKindAt(120.0D, 20.0D + size));
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
