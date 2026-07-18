package com.ninuna.losttales.accessory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/** Server-side policy for character-specific accessory restrictions. */
public interface AccessoryEligibility {

    AccessoryEligibility ALLOW_ALL = new AccessoryEligibility() {
        @Override
        public boolean canEquip(EntityPlayer player, ItemStack stack) {
            return true;
        }
    };

    boolean canEquip(EntityPlayer player, ItemStack stack);
}
