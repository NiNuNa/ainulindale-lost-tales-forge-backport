package com.ninuna.losttales.client.render.model.item;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.item.weapon.LostTalesItemWarhammer;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;

/** Dain's Hammer as GeckoLib draws it in the hand. */
public class LostTalesItemModelDainsHammer extends AnimatedGeoModel<LostTalesItemWarhammer> {

    @Override
    public ResourceLocation getModelLocation(LostTalesItemWarhammer hammer) {
        return new ResourceLocation(LostTalesMetaData.MOD_ID, "geo/dains_hammer.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(LostTalesItemWarhammer hammer) {
        return new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/models/dains_hammer.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(LostTalesItemWarhammer hammer) {
        return null;
    }
}
