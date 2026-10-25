package com.ninuna.losttales.client.armour;

import com.ninuna.losttales.util.LostTalesClientUtil;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

/**
 * Gives every armour piece's tooltip its weight, its named sets and, for
 * a relic piece, its own name and lore line (CONTENT-06, CONTENT-07,
 * CONTENT-11), whichever mod made the piece.
 */
public final class ArmourTooltipEvents {
    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        if (event.itemStack == null || event.toolTip == null) {
            return;
        }
        EntityPlayer player = event.entityPlayer != null ? event.entityPlayer
                : Minecraft.getMinecraft().thePlayer;
        LostTalesClientUtil.addArmourInformation(event.toolTip, event.itemStack, player);
    }
}
