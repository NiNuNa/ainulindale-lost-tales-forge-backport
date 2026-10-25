package com.ninuna.losttales.client.accessory;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The wearer sees a faint shade of themselves; everyone else sees nothing (ACC-02). */
public final class ConcealmentViewTest {

    @Test
    public void theWearerSeesAShade() {
        assertEquals(ConcealmentView.SHADE, ConcealmentView.of(true, true, true));
    }

    @Test
    public void everyoneElseSeesNothing() {
        assertEquals(ConcealmentView.HIDDEN, ConcealmentView.of(true, false, true));
        assertEquals(ConcealmentView.HIDDEN, ConcealmentView.of(true, false, false));
    }

    /** Where the card cannot blend the shade, the wearer is hidden from themselves too. */
    @Test
    public void noShadeWithoutBlending() {
        assertEquals(ConcealmentView.HIDDEN, ConcealmentView.of(true, true, false));
    }

    @Test
    public void aPlayerNotConcealedIsShown() {
        assertEquals(ConcealmentView.SHOWN, ConcealmentView.of(false, true, true));
        assertEquals(ConcealmentView.SHOWN, ConcealmentView.of(false, false, true));
        assertEquals(ConcealmentView.SHOWN, ConcealmentView.of(false, false, false));
    }

    @Test
    public void theShadeIsFaint() {
        assertTrue(ConcealmentView.SHADE_ALPHA > 0.0F);
        assertTrue(ConcealmentView.SHADE_ALPHA <= 0.3F);
    }
}
