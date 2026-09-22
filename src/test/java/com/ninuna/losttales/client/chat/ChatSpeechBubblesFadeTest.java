package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.motion.MotionTestSettings;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.config.LostTalesConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * A line over a head stands for its reading time whatever the motion
 * settings say; only the fade after it follows them.
 */
public final class ChatSpeechBubblesFadeTest {
    private static final UUID SPEAKER = new UUID(7L, 11L);
    private static final long MILLIS = 1000000L;

    private MotionTestSettings settings;

    @Before
    public void setUp() {
        this.settings = MotionTestSettings.reset();
        ChatSpeechBubbles.clear();
    }

    @After
    public void tearDown() {
        ChatSpeechBubbles.clear();
        this.settings.restore();
    }

    @Test
    public void aLineFadesOutAfterItIsHeld() {
        long spoken = speak();
        ChatSpeechBubbles.Speech speech = ChatSpeechBubbles.speechOf(SPEAKER,
                spoken + ChatSpeechBubbles.HOLD_NANOS
                        + ChatSpeechBubbles.FADE_MILLIS * MILLIS / 2L);
        assertNotNull(speech);
        assertEquals(0.5F, speech.lines.get(0).opacity(spoken
                + ChatSpeechBubbles.HOLD_NANOS
                + ChatSpeechBubbles.FADE_MILLIS * MILLIS / 2L), 0.001F);
    }

    @Test
    public void withMotionOffALineGoesTheMomentItsTimeIsUp() {
        LostTalesConfig.animations = false;
        long spoken = speak();
        ChatSpeechBubbles.Speech held = ChatSpeechBubbles.speechOf(SPEAKER,
                spoken + ChatSpeechBubbles.HOLD_NANOS);
        assertNotNull(held);
        assertEquals(1.0F, held.lines.get(0).opacity(
                spoken + ChatSpeechBubbles.HOLD_NANOS), 0.0F);
        assertEquals(0.0F, held.lines.get(0).opacity(
                spoken + ChatSpeechBubbles.HOLD_NANOS + 1L), 0.0F);
        assertNull(ChatSpeechBubbles.speechOf(SPEAKER,
                spoken + ChatSpeechBubbles.HOLD_NANOS + 1L));
    }

    @Test
    public void reducedMotionShortensTheFade() {
        LostTalesConfig.reducedMotion = true;
        long spoken = speak();
        long pastTheShortFade = spoken + ChatSpeechBubbles.HOLD_NANOS
                + (Motions.REDUCED_MILLIS + 1)
                * MILLIS;
        assertNull(ChatSpeechBubbles.speechOf(SPEAKER, pastTheShortFade));
    }

    /** Files one line and answers when it was spoken. */
    private static long speak() {
        ChatSpeechBubbles.receiveNpc(SPEAKER, "Gandalf", 0xFFFFFF,
                "Fly, you fools");
        ChatSpeechBubbles.Speech speech = ChatSpeechBubbles.speechOf(SPEAKER,
                System.nanoTime());
        assertNotNull(speech);
        return speech.lines.get(0).spokenNanos;
    }
}
