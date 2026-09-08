package com.ninuna.losttales.client.character.room;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The journey line breathes: in, held, out, a rest, and round again. */
public final class CharacterRoomJourneyPromptTest {

    @Test
    public void lineIsGoneAtTheStartFullThroughTheHoldAndGoneThroughTheRest() {
        assertEquals(0.0F, CharacterRoomJourneyPrompt.alphaAt(0L), 0.0001F);
        assertEquals(1.0F, CharacterRoomJourneyPrompt.alphaAt(
                CharacterRoomJourneyPrompt.FADE_IN_MS), 0.0001F);
        assertEquals(1.0F, CharacterRoomJourneyPrompt.alphaAt(
                CharacterRoomJourneyPrompt.FADE_IN_MS
                        + CharacterRoomJourneyPrompt.HOLD_MS / 2), 0.0001F);
        assertEquals(0.0F, CharacterRoomJourneyPrompt.alphaAt(
                CharacterRoomJourneyPrompt.FADE_IN_MS
                        + CharacterRoomJourneyPrompt.HOLD_MS
                        + CharacterRoomJourneyPrompt.FADE_OUT_MS), 0.0001F);
        assertEquals(0.0F, CharacterRoomJourneyPrompt.alphaAt(
                CharacterRoomJourneyPrompt.CYCLE_MS - 1L), 0.0001F);
    }

    @Test
    public void fadesRiseAndFallMonotonically() {
        float previous = -1.0F;
        for (long t = 0; t <= CharacterRoomJourneyPrompt.FADE_IN_MS; t += 50L) {
            float alpha = CharacterRoomJourneyPrompt.alphaAt(t);
            assertTrue(alpha >= previous);
            previous = alpha;
        }
        long outStart = CharacterRoomJourneyPrompt.FADE_IN_MS
                + CharacterRoomJourneyPrompt.HOLD_MS;
        previous = 2.0F;
        for (long t = 0; t <= CharacterRoomJourneyPrompt.FADE_OUT_MS; t += 50L) {
            float alpha = CharacterRoomJourneyPrompt.alphaAt(outStart + t);
            assertTrue(alpha <= previous);
            previous = alpha;
        }
    }

    @Test
    public void cycleRepeats() {
        assertEquals(CharacterRoomJourneyPrompt.alphaAt(300L),
                CharacterRoomJourneyPrompt.alphaAt(
                        300L + CharacterRoomJourneyPrompt.CYCLE_MS), 0.0001F);
    }
}
