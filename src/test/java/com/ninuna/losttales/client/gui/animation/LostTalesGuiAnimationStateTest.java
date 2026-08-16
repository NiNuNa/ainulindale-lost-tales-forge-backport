package com.ninuna.losttales.client.gui.animation;

import java.lang.reflect.Field;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LostTalesGuiAnimationStateTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void elapsedTimeClampsAndSettlesIndependentlyOfFrames()
            throws Exception {
        LostTalesGuiAnimationState state =
                new LostTalesGuiAnimationState();
        long started = startedAt(state);
        LostTalesGuiAnimationSample halfway = state.sample(
                started + 100000000L, 200, false);
        LostTalesGuiAnimationSample settled = state.sample(
                started + 900000000L, 200, false);

        assertEquals(0.5F, halfway.getProgress(), EPSILON);
        assertTrue(halfway.getOpacity() > 0.0F);
        assertTrue(halfway.getTranslationY() > 0.0F);
        assertEquals(1.0F, settled.getProgress(), EPSILON);
        assertEquals(0.0F, settled.getTranslationY(), EPSILON);
        assertEquals(1.0F, settled.getScaleX(), EPSILON);
        assertEquals(1.0F, settled.getScaleY(), EPSILON);
    }

    @Test
    public void reducedMotionRemovesSpatialMovement() throws Exception {
        LostTalesGuiAnimationState state =
                new LostTalesGuiAnimationState();
        long started = startedAt(state);
        LostTalesGuiAnimationSample sample = state.sample(
                started + 50000000L, 200, true);

        assertEquals(0.0F, sample.getTranslationY(), EPSILON);
        assertEquals(1.0F, sample.getScaleX(), EPSILON);
        assertEquals(1.0F, sample.getScaleY(), EPSILON);
    }

    @Test
    public void openingUsesRestrainedFlyInAndSmallSettle()
            throws Exception {
        LostTalesGuiAnimationState state =
                new LostTalesGuiAnimationState();
        long started = startedAt(state);
        LostTalesGuiAnimationSample entering = state.sample(
                started + 30000000L, 300, false);
        LostTalesGuiAnimationSample settle = state.sample(
                started + 228000000L, 300, false);

        assertTrue(entering.getTranslationY() < 0.0F);
        assertEquals(1.0F, entering.getScaleX(), EPSILON);
        assertEquals(1.0F, entering.getScaleY(), EPSILON);
        assertTrue(settle.getTranslationY() > 0.0F);
        assertTrue(settle.getTranslationY() < 1.0F);
    }

    @Test
    public void inverseMouseMatchesDisplayedTransform() {
        LostTalesGuiAnimationSample sample =
                new LostTalesGuiAnimationSample(
                        0.5F, 0.5F, 0.5F, 8.0F, 0.5F, 0.25F);
        assertEquals(120, sample.inverseMouseX(110, 200));
        assertEquals(188, sample.inverseMouseY(100, 120));
    }

    @Test
    public void preservedBackdropStaysSettledAcrossScreenChanges()
            throws Exception {
        LostTalesGuiAnimationState state =
                new LostTalesGuiAnimationState();
        state.restart(true);
        long started = startedAt(state);
        LostTalesGuiAnimationSample opening = state.sample(
                started, 220, 150, false,
                "BACK", "DOWN", 1.0F);

        assertEquals(0.0F, opening.getProgress(), EPSILON);
        assertEquals(1.0F, opening.getBackdropProgress(), EPSILON);
        assertTrue(opening.getTranslationY() < 0.0F);
    }

    private static long startedAt(LostTalesGuiAnimationState state)
            throws Exception {
        Field field = LostTalesGuiAnimationState.class
                .getDeclaredField("startedNanos");
        field.setAccessible(true);
        return field.getLong(state);
    }
}
