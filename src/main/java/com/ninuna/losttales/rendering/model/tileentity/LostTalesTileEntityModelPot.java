package com.ninuna.losttales.rendering.model.tileentity;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityPot;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;

public class LostTalesTileEntityModelPot extends AnimatedGeoModel<LostTalesTileEntityPot> {

    @Override
    public ResourceLocation getModelLocation(LostTalesTileEntityPot tileEntityPot) {
        if (tileEntityPot.getBlockType().getUnlocalizedName().equals(ELostTalesBlock.AMPHORA.getBlock().getUnlocalizedName())) {
            return new ResourceLocation(LostTalesMetaData.MOD_ID, "geo/amphora.geo.json");

        } else if (tileEntityPot.getBlockType().getUnlocalizedName().equals(ELostTalesBlock.LOUTROPHOROS.getBlock().getUnlocalizedName())) {
            return new ResourceLocation(LostTalesMetaData.MOD_ID, "geo/loutrophoros.geo.json");
        } else {
            return null;
        }
    }

    @Override
    public ResourceLocation getTextureLocation(LostTalesTileEntityPot tileEntityPot) {
        if (tileEntityPot.getBlockType().getUnlocalizedName().equals(ELostTalesBlock.AMPHORA.getBlock().getUnlocalizedName())) {
            return new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/models/amphora.png");

        } else if (tileEntityPot.getBlockType().getUnlocalizedName().equals(ELostTalesBlock.LOUTROPHOROS.getBlock().getUnlocalizedName())) {
            return new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/models/loutrophoros.png");
        } else {
            return null;
        }
    }

    @Override
    public ResourceLocation getAnimationFileLocation(LostTalesTileEntityPot tileEntityPot) {
        return null;
    }
}