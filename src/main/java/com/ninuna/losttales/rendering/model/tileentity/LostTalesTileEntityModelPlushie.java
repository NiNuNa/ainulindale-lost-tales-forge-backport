package com.ninuna.losttales.rendering.model.tileentity;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityPlushie;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;

public class LostTalesTileEntityModelPlushie extends AnimatedGeoModel<LostTalesTileEntityPlushie> {

    @Override
    public ResourceLocation getModelLocation(LostTalesTileEntityPlushie tileEntityPlushie) {
        for (ELostTalesBlock block : ELostTalesBlock.values()) {
            if (tileEntityPlushie.getBlockType().getUnlocalizedName().equals(block.getBlock().getUnlocalizedName())) {
                return new ResourceLocation(LostTalesMetaData.MOD_ID, "geo/" + block.getBlock().getUnlocalizedName().substring(5) + ".geo.json");
            }
        }
        return null;
    }

    @Override
    public ResourceLocation getTextureLocation(LostTalesTileEntityPlushie tileEntityPlushie) {
        for (ELostTalesBlock block : ELostTalesBlock.values()) {
            if (tileEntityPlushie.getBlockType().getUnlocalizedName().equals(block.getBlock().getUnlocalizedName())) {
                return new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/models/" + block.getBlock().getUnlocalizedName().substring(5) + ".png");
            }
        }
        return null;
    }

    @Override
    public ResourceLocation getAnimationFileLocation(LostTalesTileEntityPlushie tileEntityPlushie) {
        for (ELostTalesBlock block : ELostTalesBlock.values()) {
            if (tileEntityPlushie.getBlockType().getUnlocalizedName().equals(block.getBlock().getUnlocalizedName())) {
                return new ResourceLocation(LostTalesMetaData.MOD_ID, "animations/" + block.getBlock().getUnlocalizedName().substring(5) + ".animation.json");
            }
        }
        return null;
    }
}