package com.ninuna.losttales.client.render.player;

import lotr.client.model.LOTRModelTroll;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

/**
 * Adapts LOTR's full troll model to RenderPlayer's ModelBiped contract.
 *
 * The inherited biped anchors are kept in sync for held items and head items;
 * visible body geometry is rendered exclusively by LOTRModelTroll.
 */
final class LostTalesPlayerTrollModel extends ModelBiped {

    private final LOTRModelTroll trollModel;

    LostTalesPlayerTrollModel() {
        super(0.0F);
        this.trollModel = new LOTRModelTroll();
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount,
                       float ageInTicks, float netHeadYaw, float headPitch,
                       float scale) {
        prepare(limbSwing, limbSwingAmount, ageInTicks,
                netHeadYaw, headPitch, scale, entity);
        this.trollModel.render(entity, limbSwing, limbSwingAmount,
                ageInTicks, netHeadYaw, headPitch, scale);
        syncAnchors();
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount,
                                  float ageInTicks, float netHeadYaw,
                                  float headPitch, float scale, Entity entity) {
        prepare(limbSwing, limbSwingAmount, ageInTicks,
                netHeadYaw, headPitch, scale, entity);
        syncAnchors();
    }

    private void prepare(float limbSwing, float limbSwingAmount,
                         float ageInTicks, float netHeadYaw,
                         float headPitch, float scale, Entity entity) {
        this.trollModel.onGround = this.onGround;
        this.trollModel.setRotationAngles(limbSwing, limbSwingAmount,
                ageInTicks, netHeadYaw, headPitch, scale, entity);
    }

    private void syncAnchors() {
        copyTransform(this.trollModel.head, this.bipedHead);
        copyTransform(this.trollModel.head, this.bipedHeadwear);
        copyTransform(this.trollModel.body, this.bipedBody);
        copyTransform(this.trollModel.rightArm, this.bipedRightArm);
        copyTransform(this.trollModel.leftArm, this.bipedLeftArm);
        copyTransform(this.trollModel.rightLeg, this.bipedRightLeg);
        copyTransform(this.trollModel.leftLeg, this.bipedLeftLeg);
    }

    private static void copyTransform(ModelRenderer source, ModelRenderer target) {
        target.rotationPointX = source.rotationPointX;
        target.rotationPointY = source.rotationPointY;
        target.rotationPointZ = source.rotationPointZ;
        target.rotateAngleX = source.rotateAngleX;
        target.rotateAngleY = source.rotateAngleY;
        target.rotateAngleZ = source.rotateAngleZ;
    }
}
