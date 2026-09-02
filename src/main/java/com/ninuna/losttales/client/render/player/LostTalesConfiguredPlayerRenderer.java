package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.physics.CharacterNameplateHeightHelper;
import com.ninuna.losttales.character.registry.CharacterBodyModelDefinition;
import com.ninuna.losttales.config.LostTalesConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * One immutable RenderPlayer configuration for a body model. Everything that
 * varies per player (texture, scale, race) comes from the resolved appearance,
 * so one instance serves every player drawn with the same body.
 */
final class LostTalesConfiguredPlayerRenderer extends RenderPlayer {

    private static final float MODEL_UNIT = 0.0625F;
    private static final float VANILLA_ARM_PIVOT_X = -5.0F;
    private static final float VANILLA_ARM_PIVOT_Y = 2.0F;
    private static final float VANILLA_ARM_PIVOT_Z = 0.0F;

    private final String modelId;
    private final String bodyTypeId;
    private final String chestTypeId;
    private final boolean vanillaArmPivots;
    private final boolean configured;

    LostTalesConfiguredPlayerRenderer(CharacterBodyModelDefinition definition,
                                      String bodyTypeId,
                                      String chestTypeId,
                                      ModelBiped mainModel,
                                      ModelBiped chestArmorModel,
                                      ModelBiped armorModel) {
        super();
        this.modelId = definition == null ? "" : definition.getId();
        this.bodyTypeId = bodyTypeId == null ? "" : bodyTypeId;
        this.chestTypeId = chestTypeId == null ? "" : chestTypeId;
        this.vanillaArmPivots = definition != null && definition.hasVanillaArmPivots();
        this.mainModel = mainModel;
        this.modelBipedMain = mainModel;
        this.modelArmorChestplate = chestArmorModel;
        this.modelArmor = armorModel;
        this.configured = definition != null
                && mainModel != null
                && chestArmorModel != null
                && armorModel != null;
    }

    boolean isConfigured() {
        return this.configured;
    }

    String getModelId() {
        return this.modelId;
    }

    String getBodyTypeId() {
        return this.bodyTypeId;
    }

    String getChestTypeId() {
        return this.chestTypeId;
    }

    ModelBiped getConfiguredModel() {
        return this.modelBipedMain;
    }

    void bindCapeTexture(ResourceLocation texture) {
        if (texture != null) {
            this.bindTexture(texture);
        }
    }

    /**
     * Vanilla anchors the nameplate to the collision height, while several
     * LOTR player models are visually taller than their gameplay hitbox. A
     * temporary render-only height keeps the label above the actual head for
     * both normal and sneaking nameplate paths.
     */
    @Override
    protected void passSpecialRender(
            EntityLivingBase entity, double x, double y, double z) {
        float originalHeight = entity == null ? 0.0F : entity.height;
        float offset = resolveNameplateHeightOffset(entity, originalHeight);
        if (entity != null && offset > 0.0F) {
            entity.height = originalHeight + offset;
        }
        try {
            super.passSpecialRender(entity, x, y, z);
        } finally {
            if (entity != null) {
                entity.height = originalHeight;
            }
        }
    }

    private float resolveNameplateHeightOffset(
            EntityLivingBase entity, float physicalHeight) {
        if (!(entity instanceof EntityPlayer) || physicalHeight <= 0.0F
                || entity.isPlayerSleeping()) {
            return 0.0F;
        }
        ResolvedPlayerAppearance appearance =
                PlayerAppearanceResolver.resolve((EntityPlayer)entity);
        if (appearance == null) {
            return 0.0F;
        }
        return CharacterNameplateHeightHelper.resolveExtraHeight(
                physicalHeight, appearance.getRendererScale());
    }

    /*
     * Keep RenderPlayer's normal Y handling. EntityPlayer uses a player-only
     * yOffset and RenderPlayer compensates for it before rendering. Cancelling
     * that compensation moves the entire body upward by roughly eye height.
     */

    @Override
    protected ResourceLocation getEntityTexture(AbstractClientPlayer player) {
        ResolvedPlayerAppearance appearance = PlayerAppearanceResolver.resolve(player);
        ResourceLocation texture = appearance == null ? null : appearance.getTexture();
        return texture == null ? super.getEntityTexture(player) : texture;
    }

    /**
     * Render the actual right arm from the active body model and bind the
     * active skin. LOTR models move and scale their arm pivots, while
     * ItemRenderer expects the vanilla player pivot, so those pivots are
     * normalized before the part is drawn. A body that keeps vanilla's pivots
     * is drawn where it sits.
     */
    @Override
    public void renderFirstPersonArm(EntityPlayer player) {
        if (!(player instanceof AbstractClientPlayer)
                || this.modelBipedMain == null
                || this.modelBipedMain.bipedRightArm == null) {
            super.renderFirstPersonArm(player);
            return;
        }

        AbstractClientPlayer clientPlayer = (AbstractClientPlayer)player;
        ResourceLocation texture = getEntityTexture(clientPlayer);
        Minecraft minecraft = Minecraft.getMinecraft();
        TextureManager textureManager = minecraft == null
                ? null : minecraft.getTextureManager();
        if (texture == null || textureManager == null) {
            // A disconnect can tear down RenderManager before the final hand
            // frame. Skipping that frame is safer than dereferencing it.
            return;
        }
        textureManager.bindTexture(texture);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        applyOverlaySetting();

        float modelScale = resolveScale(player);
        ModelBiped model = this.modelBipedMain;
        ModelRenderer arm = model.bipedRightArm;
        float previousOnGround = model.onGround;
        boolean previousRiding = model.isRiding;
        boolean previousChild = model.isChild;
        int previousHeldItemRight = model.heldItemRight;
        boolean previousAimedBow = model.aimedBow;
        boolean previousShowModel = arm.showModel;
        boolean previousHidden = arm.isHidden;

        try {
            model.onGround = 0.0F;
            model.isRiding = false;
            model.isChild = false;
            model.heldItemRight = 0;
            model.aimedBow = false;
            model.setRotationAngles(0.0F, 0.0F, 0.0F,
                    0.0F, 0.0F, MODEL_UNIT, player);

            // Some LOTR models decide part visibility inside setRotationAngles.
            // Force the selected arm visible after that model logic ran.
            arm.showModel = true;
            arm.isHidden = false;

            GL11.glPushMatrix();
            try {
                if (!this.vanillaArmPivots) {
                    GL11.glTranslatef(
                            (VANILLA_ARM_PIVOT_X
                                    - arm.rotationPointX * modelScale) * MODEL_UNIT,
                            (VANILLA_ARM_PIVOT_Y
                                    - arm.rotationPointY * modelScale) * MODEL_UNIT,
                            (VANILLA_ARM_PIVOT_Z
                                    - arm.rotationPointZ * modelScale) * MODEL_UNIT);
                }
                if (modelScale != 1.0F) {
                    GL11.glScalef(modelScale, modelScale, modelScale);
                }
                arm.render(MODEL_UNIT);
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            arm.showModel = previousShowModel;
            arm.isHidden = previousHidden;
            model.onGround = previousOnGround;
            model.isRiding = previousRiding;
            model.isChild = previousChild;
            model.heldItemRight = previousHeldItemRight;
            model.aimedBow = previousAimedBow;
        }
    }

    @Override
    protected void preRenderCallback(AbstractClientPlayer player, float partialTicks) {
        super.preRenderCallback(player, partialTicks);
        applyOverlaySetting();
        float modelScale = resolveScale(player);
        if (modelScale != 1.0F) {
            GL11.glScalef(modelScale, modelScale, modelScale);
        }
    }

    /** The overlay toggle is a client option, so it is re-read before every draw. */
    private void applyOverlaySetting() {
        if (this.modelBipedMain instanceof LostTalesPlayerModel) {
            ((LostTalesPlayerModel)this.modelBipedMain)
                    .setOverlaysVisible(LostTalesConfig.showSkinOverlays);
        }
    }

    private static float resolveScale(EntityPlayer player) {
        ResolvedPlayerAppearance appearance = PlayerAppearanceResolver.resolve(player);
        return appearance == null ? 1.0F : appearance.getRendererScale();
    }
}
