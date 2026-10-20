package com.ninuna.losttales.block.custom;

import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissions;
import com.ninuna.losttales.progression.statue.ResetStatueService;
import com.ninuna.losttales.user.ELostTalesUser;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

/**
 * The statue where a character resets one skill's perks for a price
 * (PROG-12). Operators place it where their server wants one; no one
 * crafts one and none is made by the world. Only a holder of
 * {@code progression.admin} places or breaks it, and nothing else harms
 * it. It stands in the Watch Stone's model until it has one of its own.
 */
public class LostTalesBlockResetStatue extends LostTalesBlockStatueTall {
    /** The upper half's metadata. */
    private static final int UPPER = 4;

    public LostTalesBlockResetStatue() {
        super(Material.rock, ELostTalesUser.SCOSHER);
        this.setBlockUnbreakable();
        this.setResistance(6000000.0F);
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity,
                                ItemStack itemStack) {
        if (!world.isRemote && !(entity instanceof EntityPlayerMP && LostTalesPermissions.has(
                (EntityPlayerMP) entity, LostTalesCapability.PROGRESSION_ADMIN))) {
            world.setBlockToAir(x, y, z);
            if (entity instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) entity;
                if (!player.capabilities.isCreativeMode && itemStack != null) {
                    ItemStack back = itemStack.copy();
                    back.stackSize = 1;
                    if (!player.inventory.addItemStackToInventory(back)) {
                        player.dropPlayerItemWithRandomChoice(back, false);
                    }
                }
                player.addChatMessage(new ChatComponentTranslation(
                        "chat.losttales.statue.refused.place"));
            }
            return;
        }
        super.onBlockPlacedBy(world, x, y, z, entity, itemStack);
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z,
                                   boolean willHarvest) {
        if (!world.isRemote && !(player instanceof EntityPlayerMP && LostTalesPermissions.has(
                (EntityPlayerMP) player, LostTalesCapability.PROGRESSION_ADMIN))) {
            player.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.statue.refused.place"));
            return false;
        }
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                    int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            int baseY = world.getBlockMetadata(x, y, z) == UPPER ? y - 1 : y;
            ResetStatueService.open((EntityPlayerMP) player, x, baseY, z);
        }
        return true;
    }
}
