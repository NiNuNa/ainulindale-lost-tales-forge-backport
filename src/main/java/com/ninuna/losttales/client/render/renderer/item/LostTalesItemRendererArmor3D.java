package com.ninuna.losttales.client.render.renderer.item;

import com.ninuna.losttales.client.render.model.item.LostTalesItemModelArmor3D;
import com.ninuna.losttales.item.armor.LostTalesItemArmor3D;
import software.bernie.geckolib3.renderers.geo.GeoArmorRenderer;

public class LostTalesItemRendererArmor3D extends GeoArmorRenderer<LostTalesItemArmor3D> {

    public LostTalesItemRendererArmor3D() {
        super(new LostTalesItemModelArmor3D());
        this.rightLegBone = "armorLeftLeg";
        this.leftLegBone = "armorRightLeg";
        this.rightBootBone = "armorLeftBoot";
        this.leftBootBone = "armorRightBoot";
    }
}