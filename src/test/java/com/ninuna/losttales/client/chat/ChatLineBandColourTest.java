package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiInk;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A highlighted line wears its highlight as the panel's own colour in its
 * stretch: the panel's colour while nothing lights it, each highlight
 * crossing in as far as it has come, and under the pointer every layer in
 * its selected colour, so a highlighted line lightens its own colour
 * rather than giving it up.
 */
public final class ChatLineBandColourTest {
    private static final int PANEL = 0x2D1E2F;
    private static final int SELECTED = 0x7B6268;
    private static final int MENTION = 0xAB597D;
    private static final int SELECTED_MENTION = 0xC57F79;
    private static final int FLASH = 0xF09F71;
    private static final int SELECTED_FLASH = 0xF7CF91;

    private static int band(boolean pinged, float share, float flash,
                            float hover) {
        return LostTalesChatOverlayRenderer.lineBandRgb(PANEL, SELECTED,
                pinged, MENTION, SELECTED_MENTION, share, flash, FLASH,
                SELECTED_FLASH, hover);
    }

    @Test
    public void anUnlitLineIsThePanel() {
        assertEquals(PANEL, band(false, 1.0F, 0.0F, 0.0F));
    }

    @Test
    public void aSettledMentionIsTheMentionColour() {
        assertEquals(MENTION, band(true, 1.0F, 0.0F, 0.0F));
    }

    @Test
    public void aMentionStillArrivingIsPartWayThere() {
        assertEquals(LostTalesUiInk.blend(PANEL, MENTION, 0.5F),
                band(true, 0.5F, 0.0F, 0.0F));
    }

    @Test
    public void aJumpsFlashCrossesOverTheMention() {
        assertEquals(FLASH, band(true, 1.0F, 1.0F, 0.0F));
    }

    /**
     * A flash burning out crosses back into the line's own colour rather
     * than fading its band away, so nothing pops when it ends: the open
     * window's panel and the closed feed's band both wear this.
     */
    @Test
    public void aFlashBurningOutCrossesBackIntoTheLinesOwnColour() {
        assertEquals(LostTalesUiInk.blend(MENTION, FLASH, 0.5F),
                band(true, 1.0F, 0.5F, 0.0F));
        assertEquals(LostTalesUiInk.blend(PANEL, FLASH, 0.25F),
                band(false, 1.0F, 0.25F, 0.0F));
    }

    @Test
    public void aPlainLineUnderThePointerTakesTheSelectedColour() {
        assertEquals(SELECTED, band(false, 1.0F, 0.0F, 1.0F));
    }

    @Test
    public void aMentionUnderThePointerLightensInsteadOfGivingItsTintUp() {
        assertEquals(SELECTED_MENTION, band(true, 1.0F, 0.0F, 1.0F));
    }

    @Test
    public void aFlashUnderThePointerLightensToo() {
        assertEquals(SELECTED_FLASH, band(true, 1.0F, 1.0F, 1.0F));
    }

    @Test
    public void thePointersShadeCrossesInPartWay() {
        assertEquals(LostTalesUiInk.blend(MENTION, SELECTED_MENTION,
                0.5F), band(true, 1.0F, 0.0F, 0.5F));
    }
}
