package com.ninuna.losttales.item.block;

import com.ninuna.losttales.block.LostTalesBlockPlushie;
import net.minecraft.block.Block;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;

public class LostTalesItemBlockPlushie extends LostTalesItemBlockBase {

    public LostTalesItemBlockPlushie(Block block) {
        super(block);
        this.setMaxStackSize(1);
    }

    @Override
    public EnumRarity getRarity(ItemStack itemStack) {
        return ((LostTalesBlockPlushie)this.field_150939_a).getRarity();
    }
}