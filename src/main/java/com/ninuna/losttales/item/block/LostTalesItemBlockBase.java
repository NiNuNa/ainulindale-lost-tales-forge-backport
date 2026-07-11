package com.ninuna.losttales.item.block;

import com.ninuna.losttales.block.base.LostTalesBlockDirectionalContainerBase;
import com.ninuna.losttales.entity.ELostTalesUser;
import com.ninuna.losttales.util.LostTalesClientUtil;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public class LostTalesItemBlockBase extends ItemBlock {

    public LostTalesItemBlockBase(Block block) {
        super(block);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemStack, EntityPlayer player, List list, boolean advancedTooltips) {
        LostTalesClientUtil.addItemBlockInformation(list, itemStack, this.field_150939_a, this.field_150939_a instanceof LostTalesBlockDirectionalContainerBase ? ((LostTalesBlockDirectionalContainerBase)this.field_150939_a).getCredits() : ELostTalesUser.NULL);
    }
}