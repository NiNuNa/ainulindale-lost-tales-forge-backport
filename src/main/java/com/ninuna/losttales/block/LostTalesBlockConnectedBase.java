package com.ninuna.losttales.block;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.render.LOTRConnectedTextures;
import lotr.common.block.LOTRConnectedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

public class LostTalesBlockConnectedBase extends Block implements LOTRConnectedBlock {

    public LostTalesBlockConnectedBase(Material material) {
        super(material);
    }

    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister iconregister) {
        LOTRConnectedTextures.registerConnectedIcons(iconregister, this, 0, false);
    }

    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int i, int j, int k, int side) {
        return LOTRConnectedTextures.getConnectedIconBlock(this, world, i, j, k, side, false);
    }

    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int i, int j) {
        return LOTRConnectedTextures.getConnectedIconItem(this, j);
    }

    public String getConnectedName(int meta) {
        return this.textureName;
    }

    public boolean areBlocksConnected(IBlockAccess world, int i, int j, int k, int i1, int j1, int k1) {
        return Checks.matchBlockAndMeta(this, world, i, j, k, i1, j1, k1);
    }
}