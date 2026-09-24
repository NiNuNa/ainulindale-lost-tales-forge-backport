package com.ninuna.losttales.gui.hud;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.loot.LostTalesQuickLootHudRenderer;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;

/** The key that shows and hides the mod's HUD. */
@SideOnly(Side.CLIENT)
public final class LostTalesHudHelper {
    private LostTalesHudHelper() {}

    public static void toggleLostTalesHud() {
        LostTalesConfig.toggleLostTalesHud();
        LostTalesQuickLootHudRenderer.resetHud();
        sendHudToggleMessage();
    }

    /** Says in the Client Console whether the HUD is shown now. */
    private static void sendHudToggleMessage() {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft == null ? null : minecraft.thePlayer;
        if (player == null) {
            return;
        }
        player.addChatMessage(new ChatComponentTranslation(
                LostTalesConfig.showLostTalesHud ? "chat.losttales.hud.shown"
                        : "chat.losttales.hud.hidden"));
    }
}
