package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesUiFading;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A name cut by a tab's or the indicator's edge thins out into it: the
 * words fade from each side as far as the name has run past it, one
 * line deep, never past a third of the room.
 */
public final class ChatSideFadeTest {

    @Test
    public void theShadeGrowsWithWhatIsOutOfSight() {
        assertEquals(0.0F,
                LostTalesUiFading.sideFadeStrength(0.0D, 12.0F), 0.0F);
        assertEquals(0.0F,
                LostTalesUiFading.sideFadeStrength(-3.0D, 12.0F), 0.0F);
        assertEquals(0.5F,
                LostTalesUiFading.sideFadeStrength(6.0D, 12.0F), 0.0F);
        assertEquals(1.0F,
                LostTalesUiFading.sideFadeStrength(40.0D, 12.0F), 0.0F);
        assertEquals(0.0F,
                LostTalesUiFading.sideFadeStrength(5.0D, 0.0F), 0.0F);
    }

    @Test
    public void theShadeIsOneLineDeepButNeverMoreThanAThirdOfTheRoom() {
        assertEquals(WindowStyle.TOP_EDGE_FADE_HEIGHT,
                LostTalesUiFading.sideFadeDepth(90.0D), 0.0F);
        assertEquals(10.0F,
                LostTalesUiFading.sideFadeDepth(30.0D), 0.0001F);
        assertEquals(0.0F,
                LostTalesUiFading.sideFadeDepth(-4.0D), 0.0F);
    }
}
