package com.ninuna.losttales.client.render.player;

import lotr.client.model.LOTRModelDwarf;
import lotr.client.model.LOTRModelElf;
import lotr.client.model.LOTRModelHobbit;
import lotr.client.model.LOTRModelHalfTroll;
import lotr.client.model.LOTRModelHuman;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

/**
 * Small player adapters for LOTR models whose feminine chest geometry is
 * normally enabled only for LOTR NPC entities.
 */
final class LostTalesGenderedPlayerModels {

    private LostTalesGenderedPlayerModels() {}

    static void renderChest(boolean female, ModelRenderer body,
                            ModelRenderer chest, float scale) {
        if (!female || body == null || chest == null || !body.showModel) {
            return;
        }

        boolean previousVisibility = chest.showModel;
        chest.showModel = true;
        GL11.glPushMatrix();
        body.postRender(scale);
        chest.render(scale);
        GL11.glPopMatrix();
        chest.showModel = previousVisibility;
    }
}

final class LostTalesPlayerHumanModel extends LOTRModelHuman {

    private final boolean female;

    LostTalesPlayerHumanModel(float modelSize, boolean armorTexture,
                              boolean female) {
        super(modelSize, armorTexture);
        this.female = female;
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount,
                       float ageInTicks, float netHeadYaw, float headPitch,
                       float scale) {
        super.render(entity, limbSwing, limbSwingAmount, ageInTicks,
                netHeadYaw, headPitch, scale);
        LostTalesGenderedPlayerModels.renderChest(
                this.female, this.bipedBody, this.bipedChest, scale);
    }
}

final class LostTalesPlayerElfModel extends LOTRModelElf {

    private final boolean female;

    LostTalesPlayerElfModel(float modelSize, boolean female) {
        super(modelSize);
        this.female = female;
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount,
                       float ageInTicks, float netHeadYaw, float headPitch,
                       float scale) {
        super.render(entity, limbSwing, limbSwingAmount, ageInTicks,
                netHeadYaw, headPitch, scale);
        LostTalesGenderedPlayerModels.renderChest(
                this.female, this.bipedBody, this.bipedChest, scale);
    }
}

final class LostTalesPlayerDwarfModel extends LOTRModelDwarf {

    private final boolean female;

    LostTalesPlayerDwarfModel(float modelSize, boolean female) {
        super(modelSize);
        this.female = female;
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount,
                       float ageInTicks, float netHeadYaw, float headPitch,
                       float scale) {
        super.render(entity, limbSwing, limbSwingAmount, ageInTicks,
                netHeadYaw, headPitch, scale);
        LostTalesGenderedPlayerModels.renderChest(
                this.female, this.bipedBody, this.bipedChest, scale);
    }
}

final class LostTalesPlayerHobbitModel extends LOTRModelHobbit {

    private final boolean female;

    LostTalesPlayerHobbitModel(float modelSize, boolean female) {
        super(modelSize);
        this.female = female;
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount,
                       float ageInTicks, float netHeadYaw, float headPitch,
                       float scale) {
        super.render(entity, limbSwing, limbSwingAmount, ageInTicks,
                netHeadYaw, headPitch, scale);
        LostTalesGenderedPlayerModels.renderChest(
                this.female, this.bipedBody, this.bipedChest, scale);
    }
}

/**
 * Player-safe adapter for LOTR's half-troll geometry. The original NPC model
 * reads half-troll-only variant flags from the rendered entity, while a player
 * is not a LOTREntityHalfTroll. Rendering the inherited biped parts directly
 * keeps the original geometry and ordinary biped animations without an unsafe
 * entity cast.
 */
final class LostTalesPlayerHalfTrollModel extends LOTRModelHalfTroll {

    LostTalesPlayerHalfTrollModel(float modelSize) {
        super(modelSize);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount,
                       float ageInTicks, float netHeadYaw, float headPitch,
                       float scale) {
        this.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks,
                netHeadYaw, headPitch, scale, entity);
        this.bipedHead.render(scale);
        this.bipedBody.render(scale);
        this.bipedRightArm.render(scale);
        this.bipedLeftArm.render(scale);
        this.bipedRightLeg.render(scale);
        this.bipedLeftLeg.render(scale);
        this.bipedHeadwear.render(scale);
    }
}
