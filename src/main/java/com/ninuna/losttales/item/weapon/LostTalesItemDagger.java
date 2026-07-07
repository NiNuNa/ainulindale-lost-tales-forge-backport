package com.ninuna.losttales.item.weapon;

import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import com.ninuna.losttales.util.LostTalesUtil;
import java.util.List;
import lotr.common.item.LOTRItemDagger;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public class LostTalesItemDagger extends LOTRItemDagger {
    private final String credits;
    private final ELostTalesItemMaterial material;
    private final ELostTalesItem.Type itemType;

    public LostTalesItemDagger(ELostTalesItemMaterial material, ELostTalesItem.Type itemType, DaggerEffect effect) {
        super(material.getMaterial().getToolMaterial(), effect);
        this.credits = null;
        this.material = material;
        this.itemType = itemType;
    }

    public LostTalesItemDagger(ELostTalesItemMaterial material, ELostTalesItem.Type itemType, DaggerEffect effect, String credits) {
        super(material.getMaterial().getToolMaterial(), effect);
        this.credits = credits;
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