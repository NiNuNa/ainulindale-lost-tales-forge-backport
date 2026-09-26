package com.ninuna.losttales.client.gui.animation;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.opengl.GL11;

/**
 * Secondary, slightly delayed entrance shared by bottom control strips:
 * after its motion's {@code delay} the strip rises its {@code travel}
 * pixels into place ({@link MotionIds#SCREEN_CONTROL_BAR}).
 */
public final class LostTalesControlBarAnimation {
    private static Object currentScreen;
    private static long startedNanos;

    private LostTalesControlBarAnimation() {}

    public static void onScreenOpened(Object screen) {
        currentScreen = screen;
        startedNanos = System.nanoTime();
    }

    public static void push(Object screen) {
        GL11.glPushMatrix();
        GL11.glTranslatef(0.0F, offsetY(screen), 0.0F);
    }

    public static void pop() {
        GL11.glPopMatrix();
    }

    /**
     * Draws controls outside the parent GUI transform, from the screen's
     * {@link LostTalesGuiOrigin}, using only bar motion.
     */
    public static void pushFixed(Object screen) {
        GL11.glPushMatrix();
        LostTalesGuiOrigin.load();
        GL11.glTranslatef(0.0F, offsetY(screen), 0.0F);
    }

    public static int fixedMouseX(GuiScreen screen, int logicalMouseX) {
        return LostTalesGuiAnimations.forwardMouseX(screen, logicalMouseX);
    }

    public static int fixedMouseY(GuiScreen screen, int logicalMouseY) {
        return Math.round(LostTalesGuiAnimations.forwardMouseY(
                screen, logicalMouseY) - offsetY(screen));
    }

    public static int inverseMouseY(Object screen, int mouseY) {
        return Math.round(mouseY - offsetY(screen));
    }

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
