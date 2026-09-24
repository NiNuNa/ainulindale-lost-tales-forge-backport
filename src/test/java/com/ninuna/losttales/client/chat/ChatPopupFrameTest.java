package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiWindowFrame;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * What follows the pointer or the caret wears a chat window's frame
 * inside its footprint, its content two pixels clear of the frame's ink
 * as a framed button's is (Nils, 2026-09-24, F1 a): a one-line popup is
 * sixteen pixels tall.
 */
public final class ChatPopupFrameTest {
    @Test
    public void aPopupWearsTheWindowFrameInsideItsFootprint() {
        assertEquals(LostTalesUiWindowFrame.WIDTH,
                LostTalesChatVisualStyle.POPUP_FRAME);
        assertEquals("the frame and two clear pixels",
                LostTalesUiWindowFrame.WIDTH + 2,
                LostTalesChatVisualStyle.POPUP_INSET);
    }

    @Test
    public void aOneLinePopupIsSixteenPixelsTall() {
        // Surface, ink, two clear, seven rows of capitals, the shadow,
        // two clear, ink, surface.
        assertEquals(16, LostTalesChatVisualStyle.POPUP_LINE_HEIGHT);
    }
}
