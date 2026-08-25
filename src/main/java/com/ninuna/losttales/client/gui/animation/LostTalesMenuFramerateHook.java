package com.ninuna.losttales.client.gui.animation;

import net.minecraft.client.Minecraft;

/**
 * Called by the coremod in place of the literal thirty in
 * {@code Minecraft.getLimitFramerate}, which caps every screen shown
 * without a world — the main menu above all — to thirty frames. The menus
 * follow the player's own framerate limit instead, so the pointer and the
 * animated screens move as smoothly there as they do in game; with the
 * limit at its unlimited maximum the sync is skipped entirely, exactly as
 * it is in game. Without the patch vanilla's thirty stays.
 */
public final class LostTalesMenuFramerateHook {
    private static final int VANILLA_MENU_FRAMERATE = 30;

    private LostTalesMenuFramerateHook() {}

    /** The framerate the menus run at: the game's own limit setting. */
    public static int menuFramerateLimit() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft != null && minecraft.gameSettings != null) {
                return Math.max(VANILLA_MENU_FRAMERATE,
                        minecraft.gameSettings.limitFramerate);
            }
        } catch (RuntimeException ignored) {
            // The vanilla cap is always a safe answer.
        }
        return VANILLA_MENU_FRAMERATE;
    }
}
