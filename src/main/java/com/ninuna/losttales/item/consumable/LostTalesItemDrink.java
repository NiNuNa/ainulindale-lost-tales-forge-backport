package com.ninuna.losttales.item.consumable;

import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;

public class LostTalesItemDrink extends LostTalesItemFood {
    public LostTalesItemDrink(ELostTalesItemMaterial material, ELostTalesItem.Type itemType, int healAmount, float saturationModifier, boolean isWolfsFavoriteMeat) {
        super(material, itemType, healAmount, saturationModifier, isWolfsFavoriteMeat);
        this.setMaxStackSize(16);
    }

    @Override
    public EnumAction getItemUseAction(ItemStack itemStack) {
        return EnumAction.drink;
    }
}
