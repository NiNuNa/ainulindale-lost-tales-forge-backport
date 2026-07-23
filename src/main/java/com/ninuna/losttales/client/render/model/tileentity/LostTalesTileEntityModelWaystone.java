package com.ninuna.losttales.client.render.model.tileentity;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;

/** Temporary watch-stone visual kept separate for a future model swap. */
public final class LostTalesTileEntityModelWaystone
        extends AnimatedGeoModel<LostTalesTileEntityWaystone> {
    @Override
    public ResourceLocation getModelLocation(
            LostTalesTileEntityWaystone tileEntity) {
        return new ResourceLocation(
                LostTalesMetaData.MOD_ID,
                "geo/druedain_statue.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(
            LostTalesTileEntityWaystone tileEntity) {
        return new ResourceLocation(
                LostTalesMetaData.MOD_ID,
                "textures/models/druedain_statue.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(
            LostTalesTileEntityWaystone tileEntity) {
        return null;
    }
}
