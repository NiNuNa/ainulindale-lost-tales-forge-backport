package com.ninuna.losttales.client.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.IChatComponent;

/**
 * Called by the coremod at the head of {@code GuiNewChat.func_146236_a}
 * (the vanilla "chat component under the mouse" lookup). While the Lost
 * Tales chat screen is open, vanilla's nine-pixel line math does not match
 * what is drawn, so every consumer of that lookup — LOTR's achievement
 * hover card, other mods, vanilla's own click path — is answered from the
 * line bands the Lost Tales renderer actually drew. Nothing is reported
 * while the pointer is inside one of the screen's overlays, so a popup
 * never lets a tooltip through from the lines it covers. Any failure
 * falls back to vanilla's own answer.
 */
public final class LostTalesChatHitHooks {
    private LostTalesChatHitHooks() {}

    /** True only for the HUD chat while the Lost Tales chat screen is open. */
    public static boolean isActive(GuiNewChat chat) {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            return minecraft != null && chat != null
                    && minecraft.ingameGUI != null
                    && minecraft.ingameGUI.getChatGUI() == chat
                    && minecraft.currentScreen instanceof LostTalesChatGui;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * The component under the raw (window, bottom-origin) mouse position
     * vanilla passes, or null. Raw coordinates become GUI coordinates with
     * the same arithmetic {@code EntityRenderer} uses for the screen.
     */
    public static IChatComponent componentAt(GuiNewChat chat,
                                             int rawMouseX, int rawMouseY) {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (!isActive(chat) || minecraft.displayWidth <= 0
                    || minecraft.displayHeight <= 0) {
                return null;
            }
            LostTalesChatGui screen = (LostTalesChatGui)minecraft.currentScreen;
            int guiX = rawMouseX * screen.width / minecraft.displayWidth;
            int guiY = screen.height
                    - rawMouseY * screen.height / minecraft.displayHeight - 1;
            if (screen.isPointerOwnedByOverlay(guiX, guiY)) {
                return null;
            }
            LostTalesChatOverlayRenderer.Hit hit =
                    LostTalesChatOverlayRenderer.hitAt(minecraft, guiX, guiY);
            return hit == null ? null : hit.component;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
