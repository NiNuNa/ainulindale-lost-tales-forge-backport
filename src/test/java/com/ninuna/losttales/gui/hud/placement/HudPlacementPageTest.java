package com.ninuna.losttales.gui.hud.placement;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The HUD Placement page shows the game's screen in small, kept to its
 * own shape, whole inside its side of the page with a margin, centred on
 * whole pixels.
 */
public final class HudPlacementPageTest {
    @Test
    public void theScreenStandsWholeAndCentredInItsSide() {
        LostTalesUiHitBox side = new LostTalesUiHitBox(10.0D, 20.0D, 200.0D, 300.0D);
        double[] fitted = HudPlacementPage.fitted(side, 480, 270);
        double scale = fitted[4];
        assertEquals("the side's width less its margins", (200.0D - 12.0D) / 480.0D,
                scale, 1.0E-9D);
        assertEquals(Math.floor(480 * scale), fitted[2], 0.0D);
        assertEquals(Math.floor(270 * scale), fitted[3], 0.0D);
        assertTrue(fitted[0] >= side.left && fitted[1] >= side.top);
        assertEquals(Math.floor(fitted[0]), fitted[0], 0.0D);
        assertEquals(Math.floor(fitted[1]), fitted[1], 0.0D);
        assertEquals("centred across", Math.floor(10.0D + (200.0D - fitted[2]) / 2.0D),
                fitted[0], 0.0D);
    }
}
