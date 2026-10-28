package com.ninuna.losttales.block.custom;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.BlockCake;
import net.minecraft.item.Item;
import net.minecraft.world.World;

/** The cheese wheel: eaten slice by slice, as a cake is. */
public class LostTalesBlockCheeseWheel extends BlockCake {
    public LostTalesBlockCheeseWheel() {
        super();
        this.setHardness(0.5F);
        this.setStepSound(soundTypeCloth);
    }

    /** A creative pick gives the cheese wheel, not the cake the cake block names. */
    @Override
    @SideOnly(Side.CLIENT)
    public Item getItem(World world, int x, int y, int z) {
        return Item.getItemFromBlock(this);
    }
}
