package com.ninuna.losttales.item.block;

import com.ninuna.losttales.block.LostTalesBlockDirectionalContainerBase;
import com.ninuna.losttales.entity.ELostTalesUser;
import com.ninuna.losttales.util.LostTalesUtil;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import java.util.List;

public class LostTalesItemBlockBase extends ItemBlock {

    public LostTalesItemBlockBase(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack itemStack, EntityPlayer player, List list, boolean advancedTooltips) {
        LostTalesUtil.addItemBlockInformation(list, itemStack, this.field_150939_a, this.field_150939_a instanceof LostTalesBlockDirectionalContainerBase ? ((LostTalesBlockDirectionalContainerBase)this.field_150939_a).getCredits() : ELostTalesUser.NULL);
    }
}