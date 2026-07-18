package com.ninuna.losttales.client.gui;

import com.ninuna.losttales.accessory.inventory.LostTalesContainerPlayer;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.EntityPlayer;

/** Vanilla survival inventory with a subtle background for the ring slot. */
@SideOnly(Side.CLIENT)
public final class LostTalesGuiInventory extends GuiInventory {

    public LostTalesGuiInventory(EntityPlayer player) {
        super(player);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(
            float partialTicks, int mouseX, int mouseY) {
        super.drawGuiContainerBackgroundLayer(partialTicks, mouseX, mouseY);
        int left = this.guiLeft + LostTalesContainerPlayer.ACCESSORY_X - 1;
        int top = this.guiTop + LostTalesContainerPlayer.ACCESSORY_Y - 1;
        Gui.drawRect(left, top, left + 18, top + 18, 0xFF373737);
        Gui.drawRect(left + 1, top + 1, left + 17, top + 17, 0xFF8B8B8B);
        Gui.drawRect(left + 2, top + 2, left + 17, top + 17, 0xFF242424);
    }
}
