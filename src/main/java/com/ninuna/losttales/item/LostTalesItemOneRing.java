package com.ninuna.losttales.item;

import com.ninuna.losttales.accessory.player.AccessoryEquipService;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import java.util.List;

/** The unique Lost Tales ring item. Gameplay is driven by equipped state. */
public final class LostTalesItemOneRing extends LostTalesItemSimple {

    public LostTalesItemOneRing() {
        super(ELostTalesItemMaterial.NEUTRAL, ELostTalesItem.Type.ACCESSORY);
        setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(
            ItemStack stack, World world, EntityPlayer player) {
        if (world != null && !world.isRemote) {
            AccessoryEquipService.trySwapHeldRing(player, stack);
        }
        // The service mutates the authoritative hotbar directly. Returning the
        // unchanged object prevents vanilla creative mode from restoring it.
        return stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("rawtypes")
    public void addInformation(
            ItemStack stack, EntityPlayer player, List list,
            boolean advancedTooltips) {
        super.addInformation(stack, player, list, advancedTooltips);
        list.add(EnumChatFormatting.GRAY
                + I18n.format("item.the_one_ring.equip_hint"));
    }
}
