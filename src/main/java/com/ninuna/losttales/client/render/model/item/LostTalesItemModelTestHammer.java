package com.ninuna.losttales.client.render.model.item;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.item.weapon.LostTalesItemWarhammer;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;

public class LostTalesItemModelTestHammer extends AnimatedGeoModel<LostTalesItemWarhammer> {

    @Override
    public ResourceLocation getModelLocation(LostTalesItemWarhammer lostTalesTestHammer) {
        return new ResourceLocation(LostTalesMetaData.MOD_ID, "geo/test_model.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(LostTalesItemWarhammer lostTalesTestHammer) {
        return new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/models/test_model.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(LostTalesItemWarhammer lostTalesTestHammer) {
        return null;
    }
}