package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChatFeedPlacementTest {
    @Test
    public void theFeedStaysWholeOnScreen() {
        // A 60px feed whose baseline is its bottom: never below the screen.
        assertEquals(300.0D, ChatFeedPlacement.keepOnScreen(400.0D, 60,
                300), 0.0001D);
        assertEquals(60.0D, ChatFeedPlacement.keepOnScreen(20.0D, 60,
                300), 0.0001D);
    }

    /**
     * The rows over the hotbar reach as far as Forge counted them, the
     * top row's top a step under the count; with none, the experience
     * bar's top, or the hotbar's where there is no experience bar.
     */
    @Test
    public void theRowsOverTheHotbarReachAsForgeCountedThem() {
        assertEquals("hearts and food", 39,
                ChatFeedPlacement.hudRise(49, true, true));
        assertEquals("armour over the hearts", 49,
                ChatFeedPlacement.hudRise(59, true, true));
        assertEquals("no row, the experience bar", 29,
                ChatFeedPlacement.hudRise(39, true, true));
        assertEquals("creative: the hotbar alone", 22,
                ChatFeedPlacement.hudRise(39, false, false));
    }
}
