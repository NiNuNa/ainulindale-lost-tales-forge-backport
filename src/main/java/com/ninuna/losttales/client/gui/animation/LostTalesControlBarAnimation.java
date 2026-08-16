package com.ninuna.losttales.client.gui.animation;

import com.ninuna.losttales.config.LostTalesConfig;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.opengl.GL11;

/** Secondary, slightly delayed entrance shared by bottom control strips. */
public final class LostTalesControlBarAnimation {
    private static final float DELAY_FRACTION = 0.14F;
    private static final float TRAVEL_PIXELS = 24.0F;
    private static final float GUI_MODELVIEW_Z = -2000.0F;
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

    /** Draws controls outside the parent GUI transform, using only bar motion. */
    public static void pushFixed(Object screen) {
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, offsetY(screen), GUI_MODELVIEW_Z);
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
        if (!LostTalesConfig.enableGuiAnimations
                || LostTalesConfig.reducedGuiMotion
                || screen == null || screen != currentScreen) {
            return 0.0F;
        }
        long duration = Math.max(10,
                LostTalesConfig.guiAnimationDurationMillis) * 1000000L;
        float progress = LostTalesGuiEasing.clamp(
                (System.nanoTime() - startedNanos) / (float)duration);
        return offsetForProgress(progress);
    }

    static float offsetForProgress(float progress) {
        float local = LostTalesGuiEasing.clamp(
                (progress - DELAY_FRACTION)
                        / (1.0F - DELAY_FRACTION));
        return TRAVEL_PIXELS
                * (1.0F - LostTalesGuiEasing.subtleBackOut(local));
    }
}
