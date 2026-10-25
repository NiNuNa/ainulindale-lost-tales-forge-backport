package com.ninuna.losttales.client.armour;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import net.minecraftforge.client.event.GuiScreenEvent;

/**
 * Gives every screen that shows the player's potion effects, vanilla's
 * and any mod's built on vanilla's effect column, the set bonuses'
 * column as it opens (CONTENT-12).
 */
public final class SetBonusColumnEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unchecked")
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.gui instanceof InventoryEffectRenderer && event.buttonList != null) {
            event.buttonList.add(new SetBonusColumn((GuiContainer) event.gui));
        }
    }
}
