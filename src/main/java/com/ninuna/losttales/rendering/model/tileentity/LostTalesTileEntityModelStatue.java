package com.ninuna.losttales.rendering.model.tileentity;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityStatue;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;

public class LostTalesTileEntityModelStatue extends AnimatedGeoModel<LostTalesTileEntityStatue> {

    @Override
    public ResourceLocation getModelLocation(LostTalesTileEntityStatue tileEntityStatue) {
        return new ResourceLocation(LostTalesMetaData.MOD_ID, "geo/druedain_statue.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(LostTalesTileEntityStatue tileEntityStatue) {
        return new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/models/druedain_statue.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(LostTalesTileEntityStatue tileEntityStatue) {
        return null;
    }
}