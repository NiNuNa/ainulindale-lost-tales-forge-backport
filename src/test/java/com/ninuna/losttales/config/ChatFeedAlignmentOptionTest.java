package com.ninuna.losttales.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The closed feed's alignment is one of three names, read however it was
 * typed; anything else is the middle, which is where the lines stand
 * until the player chooses.
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
    public void anythingElseIsTheMiddle() {
        assertEquals("CENTRE", LostTalesConfig.normalizeFeedAlignment(""));
        assertEquals("CENTRE", LostTalesConfig.normalizeFeedAlignment(null));
        assertEquals("CENTRE", LostTalesConfig.normalizeFeedAlignment("JUSTIFY"));
        assertEquals("CENTRE", LostTalesConfig.chatFeedAlignment);
        assertEquals(3, LostTalesConfig.CHAT_FEED_ALIGNMENTS.length);
    }
}
