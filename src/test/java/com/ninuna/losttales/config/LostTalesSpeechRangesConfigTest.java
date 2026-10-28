package com.ninuna.losttales.config;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * The speech ranges in order (CHAT-37): talk carries at least as far as a
 * whisper and a shout at least as far as talk; and the client's way of
 * showing distance names one of its three words.
 */
public final class LostTalesSpeechRangesConfigTest {

    @Test
    public void theRangesStandInOrder() {
        assertArrayEquals(new int[] {8, 32, 96},
                LostTalesConfig.orderedSpeechRanges(8, 32, 96));
        assertArrayEquals(new int[] {40, 40, 96},
                LostTalesConfig.orderedSpeechRanges(40, 32, 96));
        assertArrayEquals(new int[] {8, 32, 32},
                LostTalesConfig.orderedSpeechRanges(8, 32, 16));
    }

    @Test
    public void distanceSizesReadOneOfItsWords() {
        assertEquals(LostTalesConfig.CHAT_DISTANCE_OFF,
                LostTalesConfig.normalizeDistanceSizes(" OFF "));
        assertEquals(LostTalesConfig.CHAT_DISTANCE_SIZES_AND_FAINTER,
                LostTalesConfig.normalizeDistanceSizes("sizes_and_fainter"));
        assertEquals(LostTalesConfig.CHAT_DISTANCE_THREE_SIZES,
                LostTalesConfig.normalizeDistanceSizes("louder"));
    }
}
