package com.ninuna.losttales.gui.style;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The one rectangle test every hover and press in the chat asks: a box
 * holds a point from its first pixel up to the pixel past its last.
 */
public final class LostTalesUiHitBoxTest {

    @Test
    public void aBoxHoldsItsFirstPixelAndNotThePixelPastItsLast() {
        LostTalesUiHitBox box = new LostTalesUiHitBox(10, 20, 15, 15);
        assertTrue(box.contains(10, 20));
        assertTrue(box.contains(24.999, 34.999));
        assertFalse(box.contains(25, 20));
        assertFalse(box.contains(10, 35));
        assertFalse(box.contains(9.999, 20));
        assertFalse(box.contains(10, 19.999));
        assertEquals(25, box.right(), 0.0D);
        assertEquals(35, box.bottom(), 0.0D);
        assertEquals(box.contains(12, 22),
                LostTalesUiHitBox.contains(12, 22, 10, 20, 15, 15));
    }

    @Test
    public void aBoxWithNoSizeHoldsNothing() {
        assertFalse(LostTalesUiHitBox.contains(0, 0, 0, 0, 0, 5));
        assertFalse(LostTalesUiHitBox.contains(0, 0, 0, 0, 5, 0));
        assertFalse(new LostTalesUiHitBox(3, 3, -1, 4).contains(3, 3));
    }

    @Test
    public void growingAddsTheSameClearingOnEverySide() {
        LostTalesUiHitBox grown = new LostTalesUiHitBox(10, 20, 5, 5).grown(2);
        assertEquals(8, grown.left, 0.0D);
        assertEquals(18, grown.top, 0.0D);
        assertEquals(9, grown.width, 0.0D);
        assertEquals(9, grown.height, 0.0D);
        assertTrue(grown.contains(8, 18));
        assertTrue(grown.contains(16.5, 26.5));
        assertFalse(grown.contains(17, 18));
        assertFalse(grown.contains(8, 27));
    }
}
