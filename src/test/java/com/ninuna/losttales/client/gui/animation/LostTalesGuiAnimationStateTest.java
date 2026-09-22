package com.ninuna.losttales.client.gui.animation;

import com.ninuna.losttales.client.motion.MotionTestSettings;
import com.ninuna.losttales.config.LostTalesConfig;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** A screen's opening along its motion: frame-rate independent, and still under reduced motion. */
public final class LostTalesGuiAnimationStateTest {
    private static final float EPSILON = 0.0001F;
    private static final long MILLIS = 1000000L;

    private MotionTestSettings settings;

    @Before
    public void setUp() {
        this.settings = MotionTestSettings.reset();
        MotionTestSettings.preview("{"
                + "\"screen.open\": {\"duration\": 200, \"curve\": \"back_out\","
                + " \"params\": {\"start_x\": 0, \"start_y\": -14,"
                + " \"start_scale\": 1}},"
                + "\"screen.backdrop\": {\"duration\": 150,"
                + " \"curve\": \"ease_in_out\"}}");
    }

    @After
    public void tearDown() {
        this.settings.restore();
    }

    @Test
    public void elapsedTimeClampsAndSettlesIndependentlyOfFrames()
            throws Exception {
        LostTalesGuiAnimationState state = new LostTalesGuiAnimationState();
        long started = startedAt(state);
        LostTalesGuiAnimationSample halfway = state.sample(
                started + 100L * MILLIS);
        LostTalesGuiAnimationSample settled = state.sample(
                started + 900L * MILLIS);

        assertEquals(0.5F, halfway.getProgress(), EPSILON);
        assertTrue(halfway.getOpacity() > 0.0F);
        // Half way along its curve the content has already swung past
        // its place, and settles back onto it.
        assertTrue(halfway.getTranslationY() > 0.0F);
        assertEquals(1.0F, settled.getProgress(), EPSILON);
        assertEquals(0.0F, settled.getTranslationY(), EPSILON);
        assertEquals(1.0F, settled.getScaleX(), EPSILON);
        assertEquals(1.0F, settled.getScaleY(), EPSILON);
    }

    @Test
    public void reducedMotionRemovesSpatialMovement() throws Exception {
        LostTalesConfig.reducedMotion = true;
        LostTalesGuiAnimationState state = new LostTalesGuiAnimationState();
        long started = startedAt(state);
        LostTalesGuiAnimationSample sample = state.sample(
                started + 50L * MILLIS);

        assertEquals(0.0F, sample.getTranslationY(), EPSILON);
        assertEquals(1.0F, sample.getScaleX(), EPSILON);
        assertEquals(1.0F, sample.getScaleY(), EPSILON);
        // The veil still fades, briefly.
        assertTrue(sample.getOpacity() > 0.0F);
    }

    @Test
    public void openingFliesInAndSettlesPastItsPlace() throws Exception {
        LostTalesGuiAnimationState state = new LostTalesGuiAnimationState();
        long started = startedAt(state);
        LostTalesGuiAnimationSample entering = state.sample(
                started + 20L * MILLIS);
        LostTalesGuiAnimationSample settle = state.sample(
                started + 150L * MILLIS);

        assertTrue(entering.getTranslationY() < 0.0F);
        assertEquals(1.0F, entering.getScaleX(), EPSILON);
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
        LostTalesGuiAnimationState state = new LostTalesGuiAnimationState();
        state.restart(true);
        long started = startedAt(state);
        LostTalesGuiAnimationSample opening = state.sample(started);

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
