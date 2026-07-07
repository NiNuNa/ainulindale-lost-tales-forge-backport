package com.ninuna.losttales.item.consumable;

import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import com.ninuna.losttales.util.LostTalesUtil;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;

public class LostTalesItemFood extends ItemFood {
    private final String credits;
    private final ELostTalesItemMaterial material;
    private final ELostTalesItem.Type itemType;

    public LostTalesItemFood(ELostTalesItemMaterial material, ELostTalesItem.Type itemType, int healAmount, float saturationModifier, boolean isWolfsFavoriteMeat, String credits) {
        super(healAmount, saturationModifier, isWolfsFavoriteMeat);
        this.material = material;
        this.itemType = itemType;
        this.credits = credits;
    }

    public LostTalesItemFood(ELostTalesItemMaterial material, ELostTalesItem.Type itemType, int healAmount, float saturationModifier, boolean isWolfsFavoriteMeat) {
        super(healAmount, saturationModifier, isWolfsFavoriteMeat);
        this.material = material;
        this.itemType = itemType;
        this.credits = null;
    }

    @Override
    public void addInformation(ItemStack itemStack, EntityPlayer player, List list, boolean advancedTooltips) {
        LostTalesUtil.addItemInformation(list, itemStack, this.material, this.credits, player, this.itemType);
    }

    public ELostTalesItem.Type getItemType() {
        return itemType;
    }
}