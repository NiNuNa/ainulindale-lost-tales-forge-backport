package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A name cut by a tab's or the indicator's edge sinks into it the way
 * the history sinks into its rules: a shade hangs from each side as far
 * as the name has run past it, one line deep, never past a third of the
 * room, and at the history's own edge opacity.
 */
public final class ChatSideFadeTest {

    @Test
    public void theShadeGrowsWithWhatIsOutOfSight() {
        assertEquals(0.0F,
                LostTalesChatOverlayRenderer.sideFadeStrength(0.0D, 12.0F), 0.0F);
        assertEquals(0.0F,
                LostTalesChatOverlayRenderer.sideFadeStrength(-3.0D, 12.0F), 0.0F);
        assertEquals(0.5F,
                LostTalesChatOverlayRenderer.sideFadeStrength(6.0D, 12.0F), 0.0F);
        assertEquals(1.0F,
                LostTalesChatOverlayRenderer.sideFadeStrength(40.0D, 12.0F), 0.0F);
        assertEquals(0.0F,
                LostTalesChatOverlayRenderer.sideFadeStrength(5.0D, 0.0F), 0.0F);
    }

    @Test
    public void theShadeIsOneLineDeepButNeverMoreThanAThirdOfTheRoom() {
        assertEquals(LostTalesChatOverlayRenderer.TOP_EDGE_FADE_HEIGHT,
                LostTalesChatOverlayRenderer.sideFadeDepth(90.0D), 0.0F);
        assertEquals(10.0F,
                LostTalesChatOverlayRenderer.sideFadeDepth(30.0D), 0.0001F);
        assertEquals(0.0F,
                LostTalesChatOverlayRenderer.sideFadeDepth(-4.0D), 0.0F);
    }

    @Test
    public void theShadeIsTheHistorysEdgeShade() {
        assertEquals(LostTalesChatOverlayRenderer.EDGE_FADE_ALPHA,
                ChatChannelTabBar.sideFadeAlpha(100.0D, 12.0F, 255));
        assertEquals(0, ChatChannelTabBar.sideFadeAlpha(0.0D, 12.0F, 255));
        // A tab fading in with its window takes the shade down with it.
        assertEquals(Math.round(LostTalesChatOverlayRenderer.EDGE_FADE_ALPHA
                        * 0.5F),
                ChatChannelTabBar.sideFadeAlpha(100.0D, 12.0F, 128), 1);
    }
}
