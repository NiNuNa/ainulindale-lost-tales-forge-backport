package com.ninuna.losttales.gui.hud;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.loot.LostTalesQuickLootHudRenderer;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/** Client-side HUD helpers adapted from the modern branch for Forge 1.7.10. */
@SideOnly(Side.CLIENT)
public final class LostTalesHudHelper {
    private LostTalesHudHelper() {}

    public static void toggleLostTalesHud() {
        LostTalesConfig.toggleLostTalesHud();
        LostTalesQuickLootHudRenderer.resetHud();
        sendHudToggleMessage();
    }

    private static void sendHudToggleMessage() {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft == null ? null : minecraft.thePlayer;
        if (player == null) {
            return;
        }

        String state = LostTalesConfig.showLostTalesHud
                ? EnumChatFormatting.GREEN + "ON"
                : EnumChatFormatting.RED + "OFF";
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.DARK_AQUA + "[Lost Tales] " + EnumChatFormatting.RESET + "Lost Tales HUD: " + state));
    }
}
