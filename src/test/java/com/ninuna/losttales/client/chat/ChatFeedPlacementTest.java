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
}
