package com.ninuna.losttales.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Whisper, talk and shout (CHAT-37): a click steps out and round, a
 * right-click back; only a line heard nearby takes a range; a reader's
 * size step follows the third of the range they stood in.
 */
public final class ChatSpeechRangeTest {

    @Test
    public void aClickStepsOutAndARightClickBack() {
        assertEquals(ChatSpeechRange.TALK, ChatSpeechRange.WHISPER.next());
        assertEquals(ChatSpeechRange.SHOUT, ChatSpeechRange.TALK.next());
        assertEquals(ChatSpeechRange.WHISPER, ChatSpeechRange.SHOUT.next());
        assertEquals(ChatSpeechRange.SHOUT, ChatSpeechRange.WHISPER.previous());
        assertEquals(ChatSpeechRange.WHISPER, ChatSpeechRange.TALK.previous());
    }

    @Test
    public void aRangeIsNamedByItsCodeNameAndItsByte() {
        assertEquals(ChatSpeechRange.SHOUT, ChatSpeechRange.of(" Shout ", null));
        assertNull(ChatSpeechRange.of("yell", null));
        assertEquals(ChatSpeechRange.TALK, ChatSpeechRange.of("", ChatSpeechRange.TALK));
        for (ChatSpeechRange range : ChatSpeechRange.values()) {
            assertEquals(range, ChatSpeechRange.fromCode(range.code()));
        }
        assertNull(ChatSpeechRange.fromCode(3));
        assertNull(ChatSpeechRange.fromCode(-1));
    }

    @Test
    public void onlyALineHeardNearbyTakesARange() {
        assertTrue(ChatSpeechRange.appliesTo(ChatChannel.PROXIMITY));
        assertFalse(ChatSpeechRange.appliesTo(ChatChannel.GLOBAL));
        assertFalse(ChatSpeechRange.appliesTo(ChatChannel.WHISPER));
        assertEquals(ChatSpeechRange.TALK, ChatSpeechRange.forChannel(
                ChatChannel.GLOBAL, ChatSpeechRange.SHOUT));
        assertEquals(ChatSpeechRange.SHOUT, ChatSpeechRange.forChannel(
                ChatChannel.PROXIMITY, ChatSpeechRange.SHOUT));
        assertEquals(ChatSpeechRange.TALK, ChatSpeechRange.forChannel(
                ChatChannel.PROXIMITY, null));
    }

    @Test
    public void aReadersSizeFollowsTheThirdTheyStoodIn() {
        assertEquals(ChatSpeechRange.NEAR_STEP, ChatSpeechRange.stepFor(0.0D, 30));
        assertEquals(ChatSpeechRange.NEAR_STEP, ChatSpeechRange.stepFor(10.0D, 30));
        assertEquals(ChatSpeechRange.USUAL_STEP, ChatSpeechRange.stepFor(10.5D, 30));
        assertEquals(ChatSpeechRange.USUAL_STEP, ChatSpeechRange.stepFor(20.0D, 30));
        assertEquals(ChatSpeechRange.FAR_STEP, ChatSpeechRange.stepFor(20.5D, 30));
        assertEquals(ChatSpeechRange.FAR_STEP, ChatSpeechRange.stepFor(96.0D, 30));
    }

    @Test
    public void eachRangeCarriesAsFarAsItsSettingAndNeverUnderABlock() {
        assertEquals(8, ChatSpeechRange.WHISPER.blocks(8, 32, 96));
        assertEquals(32, ChatSpeechRange.TALK.blocks(8, 32, 96));
        assertEquals(96, ChatSpeechRange.SHOUT.blocks(8, 32, 96));
        assertEquals(1, ChatSpeechRange.WHISPER.blocks(0, 32, 96));
    }
}
