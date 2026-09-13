package com.ninuna.losttales.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The closed feed's alignment is one of three names, read however it was
 * typed; anything else is the left, which is where the lines stood before
 * the option existed.
 */
public final class ChatFeedAlignmentOptionTest {

    @Test
    public void theOptionReadsItsThreeNames() {
        assertEquals("LEFT", LostTalesConfig.normalizeFeedAlignment("LEFT"));
        assertEquals("RIGHT", LostTalesConfig.normalizeFeedAlignment(" right"));
        assertEquals("CENTRE", LostTalesConfig.normalizeFeedAlignment("centre"));
        assertEquals("CENTRE", LostTalesConfig.normalizeFeedAlignment("Center"));
    }

    @Test
    public void anythingElseIsTheLeft() {
        assertEquals("LEFT", LostTalesConfig.normalizeFeedAlignment(""));
        assertEquals("LEFT", LostTalesConfig.normalizeFeedAlignment(null));
        assertEquals("LEFT", LostTalesConfig.normalizeFeedAlignment("JUSTIFY"));
        assertEquals("LEFT", LostTalesConfig.chatFeedAlignment);
        assertEquals(3, LostTalesConfig.CHAT_FEED_ALIGNMENTS.length);
    }
}
