package com.ninuna.losttales.client.gui.animation;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.client.mapmarker.LostTalesLotrMapGui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.opengl.GL11;

/** Current-screen access plus aligned transform helpers for opt-in GUIs. */
public final class LostTalesGuiAnimations {
    private static final String TRANSFORM_ACTIVE_PROPERTY =
            "losttales.guiAnimationTransformer.active";
    private static GuiScreen currentScreen;
    private static LostTalesGuiAnimationProfile currentProfile =
            LostTalesGuiAnimationProfile.NONE;
    private static final LostTalesGuiAnimationState STATE =
            new LostTalesGuiAnimationState();
    private static int currentDurationMillis = 1;
    private static int currentBackdropDurationMillis = 1;
    private static boolean reducedMotion;

    private LostTalesGuiAnimations() {}

    static void begin(GuiScreen screen,
                      LostTalesGuiAnimationProfile profile,
                      int durationMillis, int backdropDurationMillis,
                      boolean reduceMotion, boolean preserveBackdrop) {
        currentScreen = screen;
        currentProfile = profile == null
                ? LostTalesGuiAnimationProfile.NONE : profile;
        currentDurationMillis = Math.max(1, durationMillis);
        currentBackdropDurationMillis = Math.max(0,
                backdropDurationMillis);
        reducedMotion = reduceMotion;
        STATE.restart(preserveBackdrop);
    }

    static void clear() {
        currentScreen = null;
        currentProfile = LostTalesGuiAnimationProfile.NONE;
        currentDurationMillis = 1;
        currentBackdropDurationMillis = 1;
        reducedMotion = false;
    }

    public static LostTalesGuiAnimationSample sample(GuiScreen screen) {
        if (screen == null || screen != currentScreen
                || !currentProfile.isEnabled()) {
            return LostTalesGuiAnimationSample.SETTLED;
        }
        return STATE.sample(System.nanoTime(),
                currentDurationMillis, currentBackdropDurationMillis,
                reducedMotion, LostTalesConfig.guiAnimationEasingStyle,
                LostTalesConfig.guiAnimationDirection,
                (float)LostTalesConfig.guiAnimationScale);
    }

    static boolean isSpatialTransformAvailable() {
        return Boolean.getBoolean(TRANSFORM_ACTIVE_PROPERTY);
    }

    public static boolean isContentTransformActive(GuiScreen screen) {
        return isSpatialTransformAvailable() && isCurrent(screen);
    }

    public static boolean isManagingBackdrop() {
        return isManagingBackdrop(currentScreen);
    }

    /**
     * Whether the handler, rather than the screen, paints the veil
     * behind the screen: it does once the transformer has taken
     * vanilla's own veil away from it.
     */
    public static boolean isManagingBackdrop(GuiScreen screen) {
        return isContentTransformActive(screen);
    }

    private static boolean isCurrent(GuiScreen screen) {
        net.minecraft.client.Minecraft minecraft =
                net.minecraft.client.Minecraft.getMinecraft();
        return LostTalesConfig.enableGuiAnimations
                && minecraft != null && minecraft.theWorld != null
                && screen != null && screen == currentScreen
                && currentProfile.isEnabled();
    }

    /**
     * Applies the optional content transform. A screen using this method must
     * pair it with {@link #popContentTransform()} and pass mouse coordinates
     * through the sample's inverse methods before hit detection.
     */
    public static LostTalesGuiAnimationSample pushContentTransform(
            GuiScreen screen, int width, int height) {
        LostTalesGuiAnimationSample sample = sample(screen);
        boolean projectiveMap = screen instanceof LostTalesLotrMapGui;
        float scaleX = projectiveMap ? 1.0F : sample.getScaleX();
        float scaleY = projectiveMap ? 1.0F : sample.getScaleY();
        GL11.glPushMatrix();
        GL11.glTranslatef(width * 0.5F + sample.getTranslationX(),
                height * 0.5F + sample.getTranslationY(), 0.0F);
        GL11.glScalef(scaleX, scaleY, 1.0F);
        GL11.glTranslatef(-width * 0.5F, -height * 0.5F, 0.0F);
        return sample;
    }

    public static void popContentTransform() {
        GL11.glPopMatrix();
    }

    public static int inverseMouseX(GuiScreen screen, int mouseX) {
        if (!isContentTransformActive(screen)) {
            return mouseX;
        }
        LostTalesGuiAnimationSample sample = sample(screen);
        if (screen instanceof LostTalesLotrMapGui) {
            return Math.round(mouseX - sample.getTranslationX());
        }
        return sample.inverseMouseX(mouseX, screen.width);
    }

    public static int inverseMouseY(GuiScreen screen, int mouseY) {
        if (!isContentTransformActive(screen)) {
            return mouseY;
        }
        LostTalesGuiAnimationSample sample = sample(screen);
        if (screen instanceof LostTalesLotrMapGui) {
            return Math.round(mouseY - sample.getTranslationY());
        }
        return sample.inverseMouseY(mouseY, screen.height);
    }

    public static int forwardMouseX(GuiScreen screen, int mouseX) {
        if (!isContentTransformActive(screen)) {
            return mouseX;
        }
        LostTalesGuiAnimationSample sample = sample(screen);
        float center = screen == null ? 0.0F : screen.width * 0.5F;
        float scale = screen instanceof LostTalesLotrMapGui
                ? 1.0F : sample.getScaleX();
        return Math.round(center + sample.getTranslationX()
                + (mouseX - center) * scale);
    }

    public static int forwardMouseY(GuiScreen screen, int mouseY) {
        if (!isContentTransformActive(screen)) {
            return mouseY;
        }
        LostTalesGuiAnimationSample sample = sample(screen);
        float center = screen == null ? 0.0F : screen.height * 0.5F;
        float scale = screen instanceof LostTalesLotrMapGui
                ? 1.0F : sample.getScaleY();
        return Math.round(center + sample.getTranslationY()
                + (mouseY - center) * scale);
    }

    public static boolean isContentAnimating(GuiScreen screen) {
        return isContentTransformActive(screen)
                && sample(screen).getProgress() < 0.999F;
    }
}
