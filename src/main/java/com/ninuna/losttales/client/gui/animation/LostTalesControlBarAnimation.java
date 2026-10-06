package com.ninuna.losttales.client.gui.animation;

import com.ninuna.losttales.client.motion.Motion;
import com.ninuna.losttales.client.motion.MotionBeat;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.opengl.GL11;

/**
 * Where a screen's bottom control strip and its other fixed furniture
 * stand while the screen comes in: drawn from the screen's
 * {@link LostTalesGuiOrigin}, free of the content's own matrix, they move
 * as the content moves, so the screen arrives as one piece. The motion
 * files can give the strips an entrance of their own
 * ({@link MotionIds#SCREEN_CONTROL_BAR}, no time by default): then they
 * stand still while the content comes in and, after the motion's
 * {@code delay}, rise its {@code travel} pixels into place by themselves.
 */
public final class LostTalesControlBarAnimation {
    private static Object currentScreen;
    private static long startedNanos;

    private LostTalesControlBarAnimation() {}

    public static void onScreenOpened(Object screen) {
        currentScreen = screen;
        startedNanos = System.nanoTime();
    }

    public static void pop() {
        GL11.glPopMatrix();
    }

    /**
     * Whether the strips have an entrance of their own: their motion was
     * given a time. Read from the motion itself, so they keep to one way
     * of coming in whatever the speed or reduced motion say.
     */
    public static boolean entersOnItsOwn() {
        MotionBeat beat = Motions.get(MotionIds.SCREEN_CONTROL_BAR)
                .beat(Motion.ON);
        return beat != null && beat.durationMillis() > 0;
    }

    /**
     * Draws controls outside the parent GUI transform, from the screen's
     * {@link LostTalesGuiOrigin}, where the strip stands now
     * ({@link #place}).
     */
    public static void pushFixed(Object screen) {
        GL11.glPushMatrix();
        LostTalesGuiOrigin.load();
        place(screen);
    }

    /**
     * Moves the matrix from the screen's origin to where its strip stands
     * now: with the content while the strip rides its screen, else by the
     * strip's own rise.
     */
    public static void place(Object screen) {
        if (ridesContent(screen)) {
            GuiScreen gui = (GuiScreen)screen;
            LostTalesGuiAnimations.applyContentTransform(gui, gui.width,
                    gui.height);
            return;
        }
        GL11.glTranslatef(0.0F, offsetY(screen), 0.0F);
    }

    /**
     * How far down the strip stands from its place now, which way it
     * travels: the content's slide while it rides its screen, else its
     * own rise.
     */
    public static float shiftY(Object screen) {
        if (ridesContent(screen)) {
            return LostTalesGuiAnimations.sample((GuiScreen)screen)
                    .getTranslationY();
        }
        return offsetY(screen);
    }

    /** Whether the strip moves with its screen's content: it has no entrance of its own, and the content is moved. */
    private static boolean ridesContent(Object screen) {
        return !entersOnItsOwn() && screen instanceof GuiScreen
                && LostTalesGuiAnimations.isContentTransformActive(
                        (GuiScreen)screen);
    }

    /** How far below its place the strip stands now in its own rise; nothing while it has none. */
    public static float offsetY(Object screen) {
        if (screen != null && screen != currentScreen) {
            // Also covers another GuiOpenEvent subscriber replacing the
            // screen instance after this animation handler observed it.
            onScreenOpened(screen);
        }
        long duration = Motions.travelNanos(MotionIds.SCREEN_CONTROL_BAR);
        if (duration <= 0L || screen == null || screen != currentScreen) {
            return 0.0F;
        }
        long delay = Motions.scaledNanos(Math.round(Motions.param(
                MotionIds.SCREEN_CONTROL_BAR, "delay", 0.0F)));
        float progress = LostTalesGuiEasing.clamp(
                (System.nanoTime() - startedNanos - delay) / (float)duration);
        return offsetForProgress(progress);
    }

    /** How far below its place the strip stands {@code progress} of the way through its rise. */
    static float offsetForProgress(float progress) {
        return Motions.param(MotionIds.SCREEN_CONTROL_BAR, "travel", 24.0F)
                * (1.0F - Motions.curve(MotionIds.SCREEN_CONTROL_BAR)
                        .apply(progress));
    }
}
