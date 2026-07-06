package com.ninuna.losttales.item;

import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class LostTalesItemHorn extends LostTalesItemSimple {
    public LostTalesItemHorn(ELostTalesItemMaterial material, ELostTalesItem.Type itemType) {
        super(material, itemType);
        this.setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack itemStack, World world, EntityPlayer player) {
        player.setItemInUse(itemStack, this.getMaxItemUseDuration(itemStack));
        if (!world.isRemote) {
            world.playSoundAtEntity(player, "note.bassattack", 1.0F, 0.7F + world.rand.nextFloat() * 0.3F);
        }
        return itemStack;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack itemStack) {
        return 32;
    }

    @Override
    public EnumAction getItemUseAction(ItemStack itemStack) {
        return EnumAction.bow;
    }
}
