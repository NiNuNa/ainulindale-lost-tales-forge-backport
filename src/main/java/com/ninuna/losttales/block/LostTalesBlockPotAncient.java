package com.ninuna.losttales.block;

import com.ninuna.losttales.entity.ELostTalesUser;
import lotr.common.LOTRMod;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.ArrayList;

public class LostTalesBlockPotAncient extends LostTalesBlockPotBase {

    public LostTalesBlockPotAncient(ELostTalesUser credits) {
        super(credits);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<>();

        drops.add(new ItemStack(Items.coal, world.rand.nextInt(5)));
        drops.add(new ItemStack(ELostTalesBlock.AMPHORA.getBlock()));
        drops.add(new ItemStack(LOTRMod.thatch));

        return drops;
    }
}