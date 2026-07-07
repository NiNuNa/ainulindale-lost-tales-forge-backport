package com.ninuna.losttales.client.render.model.item;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.item.armor.LostTalesItemArmor3D;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;

public class LostTalesItemModelArmor3D extends AnimatedGeoModel<LostTalesItemArmor3D> {

    @Override
    public ResourceLocation getModelLocation(LostTalesItemArmor3D lostTalesItemArmor) {
        return new ResourceLocation(LostTalesMetaData.MOD_ID, "geo/test.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(LostTalesItemArmor3D lostTalesItemArmor) {
        return new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/models/test.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(LostTalesItemArmor3D lostTalesItemArmor) {
        return null;
    }
}