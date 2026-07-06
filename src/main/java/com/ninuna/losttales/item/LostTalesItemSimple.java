package com.ninuna.losttales.item;

import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import com.ninuna.losttales.util.LostTalesUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;

public class LostTalesItemSimple extends Item {
    private final ELostTalesItemMaterial material;
    private final ELostTalesItem.Type itemType;
    private final String credits;

    public LostTalesItemSimple(ELostTalesItemMaterial material, ELostTalesItem.Type itemType) {
        this(material, itemType, null);
    }

    public LostTalesItemSimple(ELostTalesItemMaterial material, ELostTalesItem.Type itemType, String credits) {
        this.material = material;
        this.itemType = itemType;
        this.credits = credits;
    }

    @Override
    public void addInformation(ItemStack itemStack, EntityPlayer player, List list, boolean advancedTooltips) {
        LostTalesUtil.addItemInformation(list, itemStack, this.material, this.credits, player, this.itemType);
    }
}
