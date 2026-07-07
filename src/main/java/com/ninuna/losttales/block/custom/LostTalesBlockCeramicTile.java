package com.ninuna.losttales.block.custom;

import com.ninuna.losttales.block.base.LostTalesBlockConnectedBase;
import com.ninuna.losttales.sound.ELostTalesBlockSoundType;
import net.minecraft.block.material.Material;

public class LostTalesBlockCeramicTile extends LostTalesBlockConnectedBase {

    public LostTalesBlockCeramicTile() {
        super(Material.rock);
        this.setHardness(1.5F);
        this.setResistance(10.0F);
        this.setStepSound(ELostTalesBlockSoundType.CERAMIC.getSoundType());
    }
}