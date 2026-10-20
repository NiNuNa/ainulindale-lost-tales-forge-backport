package com.ninuna.losttales.item.weapon;

import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;

public class LostTalesItemPoleArm extends LostTalesItemSword {

    public LostTalesItemPoleArm(ELostTalesItemMaterial material, ELostTalesItem.Type itemType) {
        super(material, itemType);
    }

    @Override
    public EnumAction getItemUseAction(ItemStack itemstack) {
        return EnumAction.none;
    }
}