package com.ninuna.losttales.rendering.model.tileentity;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityLamp;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;

public class LostTalesTileEntityModelLamp extends AnimatedGeoModel<LostTalesTileEntityLamp> {

    @Override
    public ResourceLocation getModelLocation(LostTalesTileEntityLamp tileEntityLamp) {
        return new ResourceLocation(LostTalesMetaData.MOD_ID, "geo/amphora.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(LostTalesTileEntityLamp tileEntityLamp) {
        return new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/models/amphora.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(LostTalesTileEntityLamp tileEntityLamp) {
        return null;
    }
}