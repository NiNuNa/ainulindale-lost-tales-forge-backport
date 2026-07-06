package com.ninuna.losttales.item.weapon;

import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import com.ninuna.losttales.util.LostTalesUtil;
import lotr.common.item.LOTRItemSword;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.List;

public class LostTalesItemSword extends LOTRItemSword {
    private final String credits;
    private final ELostTalesItemMaterial material;
    private final ELostTalesItem.Type itemType;

    public LostTalesItemSword(ELostTalesItemMaterial material, ELostTalesItem.Type itemType, String credits) {
        super(material.getMaterial().getToolMaterial());
        this.credits = credits;
        this.material = material;
        this.itemType = itemType;
    }

    public LostTalesItemSword(ELostTalesItemMaterial material, ELostTalesItem.Type itemType) {
        super(material.getMaterial().getToolMaterial());
        this.credits = null;
        this.material = material;
        this.itemType = itemType;
    }

    @Override
    public void addInformation(ItemStack itemStack, EntityPlayer player, List list, boolean advancedTooltips) {
        LostTalesUtil.addItemInformation(list, itemStack, this.material, this.credits, player, this.itemType);
    }

    public ELostTalesItem.Type getItemType() {
        return itemType;
    }
}