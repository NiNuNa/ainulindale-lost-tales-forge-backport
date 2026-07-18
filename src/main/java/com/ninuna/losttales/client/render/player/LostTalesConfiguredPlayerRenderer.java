package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.physics.CharacterNameplateHeightHelper;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinDefinition;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import lotr.client.model.LOTRArmorModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

/** One immutable RenderPlayer configuration for a roleplaying body model. */
final class LostTalesConfiguredPlayerRenderer extends RenderPlayer {

    private static final float MODEL_UNIT = 0.0625F;
    private static final float VANILLA_ARM_PIVOT_X = -5.0F;
    private static final float VANILLA_ARM_PIVOT_Y = 2.0F;
    private static final float VANILLA_ARM_PIVOT_Z = 0.0F;

    private final String raceId;
    private final float modelScale;
    private final boolean configured;
    private final Map<String, ResourceLocation> textureCache =
            new HashMap<String, ResourceLocation>();

    LostTalesConfiguredPlayerRenderer(String raceId,
                                      ModelBiped mainModel,
                                      ModelBiped chestArmorModel,
                                      ModelBiped armorModel,
                                      float modelScale) {
        super();
        this.raceId = raceId == null ? "" : raceId;
        this.modelScale = modelScale;
        this.mainModel = mainModel;
        this.modelBipedMain = mainModel;
        this.modelArmorChestplate = chestArmorModel;
        this.modelArmor = armorModel;
        this.configured = mainModel != null
                && chestArmorModel != null
                && armorModel != null;
    }

    boolean isConfigured() {
        return this.configured;
    }

    String getRaceId() {
        return this.raceId;
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
        CharacterRaceDefinition definition =
                CharacterRaceRegistry.get(this.raceId);
        if (definition == null || physicalHeight <= 0.0F
                || entity != null && entity.isPlayerSleeping()) {
            return 0.0F;
        }
        return CharacterNameplateHeightHelper.resolveExtraHeight(
                physicalHeight, this.modelScale);
    }

    /*
     * Keep RenderPlayer's normal Y handling. EntityPlayer uses a player-only
     * yOffset and RenderPlayer compensates for it before rendering. Cancelling
     * that compensation moves the entire body upward by roughly eye height,
     * which is why the earlier body renderer appeared to stand on the camera.
     */

    /**
     * The LOTR half-troll renderer uses LOTRModelHalfTroll(1.0F) for the
     * outer armor layer and LOTRModelHalfTroll(0.5F) for the leggings layer.
     * RenderPlayer selects the same two fields, but explicitly applying
     * LOTR's runtime model setup keeps held-item, sneaking, riding, and armor
     * part visibility synchronized exactly as it is for LOTR NPC renderers.
     */
    @Override
    protected int shouldRenderPass(AbstractClientPlayer player,
                                   int armorPass, float partialTicks) {
        int result = super.shouldRenderPass(player, armorPass, partialTicks);
        if (result <= 0
                || !CharacterRaceRegistry.HALF_TROLL.equals(this.raceId)
                || LOTRArmorModels.INSTANCE == null) {
            return result;
        }

        ModelBiped passModel = armorPass == 2
                ? this.modelArmor
                : this.modelArmorChestplate;
        LOTRArmorModels.INSTANCE.setupModelForRender(
                passModel, this.modelBipedMain, player);
        LOTRArmorModels.INSTANCE.setupArmorForSlot(passModel, armorPass);
        this.setRenderPassModel(passModel);
        return result;
    }

    @Override
    protected ResourceLocation getEntityTexture(AbstractClientPlayer player) {
        CharacterAppearance appearance = player == null || player.getUniqueID() == null
                ? null : ClientCharacterAppearanceCache.get(player.getUniqueID());
        CharacterSkinDefinition skin = resolveSkin(player, appearance);
        if (skin == null) {
            return super.getEntityTexture(player);
        }

        ResourceLocation texture = this.textureCache.get(skin.getId());
        if (texture == null) {
            texture = new ResourceLocation(skin.getTextureLocation());
            this.textureCache.put(skin.getId(), texture);
        }
        return texture;
    }

    private CharacterSkinDefinition resolveSkin(AbstractClientPlayer player,
                                                 CharacterAppearance appearance) {
        if (appearance == null || !this.raceId.equals(appearance.getRaceId())) {
            return null;
        }

        CharacterSkinDefinition selected = CharacterSkinRegistry.get(appearance.getSkinId());
        if (selected != null && selected.isCompatibleWith(
                appearance.getRaceId(), appearance.getGenderId())) {
            return selected;
        }

        String fallbackId = CharacterSkinRegistry.getDefaultSkinId(
                appearance.getRaceId(), appearance.getGenderId(),
                player == null ? null : player.getUniqueID());
        return CharacterSkinRegistry.get(fallbackId);
    }

    /**
     * Render the actual right arm from the active LOTR race model and bind the
     * active roleplay skin. LOTR models move and scale their arm pivots, while
     * ItemRenderer expects the vanilla player pivot, so the pivot is normalized
     * before the part is drawn.
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
            // Force the selected race arm visible after that model logic ran.
            arm.showModel = true;
            arm.isHidden = false;

            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(
                        (VANILLA_ARM_PIVOT_X
                                - arm.rotationPointX * this.modelScale) * MODEL_UNIT,
                        (VANILLA_ARM_PIVOT_Y
                                - arm.rotationPointY * this.modelScale) * MODEL_UNIT,
                        (VANILLA_ARM_PIVOT_Z
                                - arm.rotationPointZ * this.modelScale) * MODEL_UNIT);
                if (this.modelScale != 1.0F) {
                    GL11.glScalef(this.modelScale, this.modelScale, this.modelScale);
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
        if (this.modelScale != 1.0F) {
            GL11.glScalef(this.modelScale, this.modelScale, this.modelScale);
        }
    }
}
