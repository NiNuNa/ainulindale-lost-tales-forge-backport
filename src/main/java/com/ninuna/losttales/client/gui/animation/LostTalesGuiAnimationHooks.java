package com.ninuna.losttales.client.gui.animation;

import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.opengl.GL11;

/** Narrow bridge used by the core transformer to keep GUI input visual. */
public final class LostTalesGuiAnimationHooks {
    private static boolean vanillaBackgroundMasked;

    private LostTalesGuiAnimationHooks() {}

    /** Invoked at EntityRenderer's sole GuiScreen draw call. */
    public static void drawScreen(GuiScreen screen,
                                  int mouseX, int mouseY,
                                  float partialTicks) {
        if (screen == null) {
            return;
        }
        screen.drawScreen(
                LostTalesGuiAnimations.inverseMouseX(screen, mouseX),
                LostTalesGuiAnimations.inverseMouseY(screen, mouseY),
                partialTicks);
    }

    public static int inverseMouseX(GuiScreen screen, int mouseX) {
        return LostTalesGuiAnimations.inverseMouseX(screen, mouseX);
    }

    public static int inverseMouseY(GuiScreen screen, int mouseY) {
        return LostTalesGuiAnimations.inverseMouseY(screen, mouseY);
    }

    /** Hides vanilla's transformed veil while the stationary pre-pass owns it. */
    public static void beginVanillaBackground(GuiScreen screen) {
        vanillaBackgroundMasked =
                LostTalesGuiAnimations.isManagingBackdrop(screen);
        if (vanillaBackgroundMasked) {
            GL11.glColorMask(false, false, false, false);
        }
    }

    public static void endVanillaBackground() {
        if (vanillaBackgroundMasked) {
            GL11.glColorMask(true, true, true, true);
            vanillaBackgroundMasked = false;
        }
    }
}
