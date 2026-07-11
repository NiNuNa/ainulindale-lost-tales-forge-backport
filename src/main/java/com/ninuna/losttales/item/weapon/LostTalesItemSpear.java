package com.ninuna.losttales.item.weapon;

import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import com.ninuna.losttales.util.LostTalesClientUtil;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import lotr.common.item.LOTRItemSpear;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public class LostTalesItemSpear extends LOTRItemSpear {
    private final String credits;
    private final ELostTalesItemMaterial material;
    private final ELostTalesItem.Type itemType;

    public LostTalesItemSpear(ELostTalesItemMaterial material, ELostTalesItem.Type itemType, String credits) {
        super(material.getMaterial().getToolMaterial());
        this.material = material;
        this.credits = credits;
        this.itemType = itemType;
    }

    public LostTalesItemSpear(ELostTalesItemMaterial material, ELostTalesItem.Type itemType) {
        super(material.getMaterial().getToolMaterial());
        this.material = material;
        this.credits = null;
        this.itemType = itemType;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemStack, EntityPlayer player, List list, boolean advancedTooltips) {
        LostTalesClientUtil.addItemInformation(list, itemStack, this.material, this.credits, player, this.itemType);
    }

    public ELostTalesItem.Type getItemType() {
        return itemType;
    }
}