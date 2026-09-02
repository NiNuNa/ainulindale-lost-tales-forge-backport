package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterChestTypeDefinition;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinLayout;
import com.ninuna.losttales.config.LostTalesConfig;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

/**
 * Lost Tales' player body: a vanilla biped with a wide or slim arm width, a
 * skin layout, a chest type, and the race geometry of one {@link PlayerBodyShape}.
 *
 * With Minecraft's 64x64 layout the left arm and left leg get their own
 * texture regions and every limb carries an overlay box a quarter pixel
 * outside it; race features then sample the head and leg texels of the skin,
 * since a Minecraft skin has no room painted for them. With LOTR's layouts
 * the left limbs mirror the right ones, the hat is the hanging hair and
 * beard cuboid at (0,32) where the shape has one, and features read the
 * texels LOTR painted for them. An armor variant reads the classic 64x32
 * armor layout and keeps only the proportions of its shape; the half-troll's
 * armor is painted for its own 64x64 box layout and keeps it. Feature parts
 * are children of the limb they belong to, so every pose the biped takes
 * moves them.
 */
public final class LostTalesPlayerModel extends ModelBiped {

    private static final int TEXTURE_SIZE = 64;
    private static final int CLASSIC_TEXTURE_HEIGHT = 32;
    private static final float OVERLAY_INFLATION = 0.25F;
    private static final float HAT_INFLATION = 0.5F;
    /**
     * Both arm widths hang from the same shoulder height. Java Edition once
     * placed slim arms half a pixel lower and settled on level arms
     * (MC-275473, fixed in 24w34a); only Bedrock keeps them lower.
     */
    private static final float ARM_PIVOT_Y = 2.0F;
    private static final float VANILLA_ARM_REACH = 5.0F;
    private static final float HOBBIT_LIMB_SCALE = 10.0F / 12.0F;
    private static final float DWARF_WIDTH_SCALE = 1.25F;
    private static final float DWARF_ARM_SHIFT = 1.0F;
    private static final float DWARF_LEG_SHIFT = 0.25F;
    /** Rounded chest: hung 0.9 px below the neck at the torso front, tilted forward. */
    private static final float ROUNDED_HANG = 0.9F;
    private static final float ROUNDED_TILT = -35.0F;
    /** Full chest: a slab sloping down from the chest, placed and scaled by fullness. */
    private static final float FULL_SLOPE = 57.3F;
    private static final float TORSO_FRONT = -2.0F;
    private static final float BOUNCE_TILT = -6.0F;
    private static final float SWAY_TURN = 5.0F;
    private static final int CHESTPLATE_SLOT = 2;

    private final PlayerBodyShape shape;
    private final CharacterSkinLayout layout;
    private final boolean slim;
    private final boolean armorTexture;
    private final float bipedArmPivotY;
    private final ModelRenderer[] overlays;
    private final CharacterChestTypeRegistry.Shape chestShape;
    private final float chestSize;
    private final ModelRenderer chestLeft;
    private final ModelRenderer chestRight;
    private final ModelRenderer chestLeftOverlay;
    private final ModelRenderer chestRightOverlay;

    /** The plain player body in Minecraft's layout, without a chest. */
    public LostTalesPlayerModel(float inflation, boolean slim) {
        this(inflation, slim, PlayerBodyShape.PLAYER,
                CharacterSkinLayout.MINECRAFT_64X64, null, false);
    }

    /**
     * @param inflation    box inflation, 0 for the skin and 1.0 / 0.5 for armor layers
     * @param slim         three-pixel arms instead of four
     * @param shape        race geometry
     * @param layout       the layout of the skin the model reads
     * @param chest        the chest type to draw, or null for none
     * @param armorTexture read an armor texture: classic hat, mirrored left
     *                     limbs, no overlays, chest, ears, or feet
     */
    public LostTalesPlayerModel(float inflation, boolean slim, PlayerBodyShape shape,
                                CharacterSkinLayout layout, CharacterChestTypeDefinition chest,
                                boolean armorTexture) {
        super(inflation, 0.0F, TEXTURE_SIZE, textureHeight(shape, layout, armorTexture));
        if (shape == null || layout == null) {
            throw new IllegalArgumentException("shape and layout must not be null");
        }
        this.shape = shape;
        this.layout = layout;
        this.slim = slim;
        this.armorTexture = armorTexture;
        this.bipedArmPivotY = ARM_PIVOT_Y;
        boolean minecraftLayout = !armorTexture
                && layout == CharacterSkinLayout.MINECRAFT_64X64;
        int armWidth = slim ? 3 : 4;
        boolean drawChest = chest != null && !chest.isNone() && !armorTexture
                && !shape.isHalfTroll();
        this.chestShape = drawChest ? chest.getShape() : CharacterChestTypeRegistry.Shape.NONE;
        this.chestSize = drawChest ? chest.getSize() : 0.0F;

        if (shape.isHalfTroll()) {
            buildHalfTroll(inflation, slim, armorTexture);
            this.overlays = new ModelRenderer[0];
            this.chestLeft = null;
            this.chestRight = null;
            this.chestLeftOverlay = null;
            this.chestRightOverlay = null;
            applyShapePivots();
            return;
        }

        if (slim) {
            this.bipedRightArm = new ModelRenderer(this, 40, 16);
            this.bipedRightArm.addBox(-2.0F, -2.0F, -2.0F, armWidth, 12, 4, inflation);
            this.bipedRightArm.setRotationPoint(-5.0F, this.bipedArmPivotY, 0.0F);
        }
        if (minecraftLayout) {
            this.bipedLeftArm = new ModelRenderer(this, 32, 48);
            this.bipedLeftLeg = new ModelRenderer(this, 16, 48);
            this.bipedLeftLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, inflation);
            this.bipedLeftLeg.setRotationPoint(1.9F, 12.0F, 0.0F);
        } else {
            this.bipedLeftArm = new ModelRenderer(this, 40, 16);
            this.bipedLeftArm.mirror = true;
        }
        this.bipedLeftArm.addBox(-1.0F, -2.0F, -2.0F, armWidth, 12, 4, inflation);
        this.bipedLeftArm.setRotationPoint(5.0F, this.bipedArmPivotY, 0.0F);

        if (!armorTexture && layout == CharacterSkinLayout.LOTR_64X64
                && shape.getLotrHeadwearHeight() > 0) {
            this.bipedHeadwear = new ModelRenderer(this, 0, 32);
            this.bipedHeadwear.addBox(-4.0F, -8.0F, -4.0F,
                    8, shape.getLotrHeadwearHeight(), 8, inflation + HAT_INFLATION);
            this.bipedHeadwear.setRotationPoint(0.0F, 0.0F, 0.0F);
        }

        if (minecraftLayout) {
            float overlay = inflation + OVERLAY_INFLATION;
            this.overlays = new ModelRenderer[] {
                    child(this.bipedBody, 16, 32, -4.0F, 0.0F, -2.0F, 8, 12, 4, overlay),
                    child(this.bipedRightArm, 40, 32,
                            slim ? -2.0F : -3.0F, -2.0F, -2.0F, armWidth, 12, 4, overlay),
                    child(this.bipedLeftArm, 48, 48,
                            -1.0F, -2.0F, -2.0F, armWidth, 12, 4, overlay),
                    child(this.bipedRightLeg, 0, 32, -2.0F, 0.0F, -2.0F, 4, 12, 4, overlay),
                    child(this.bipedLeftLeg, 0, 48, -2.0F, 0.0F, -2.0F, 4, 12, 4, overlay)};
        } else {
            this.overlays = new ModelRenderer[0];
        }

        float overlay = inflation + OVERLAY_INFLATION;
        switch (this.chestShape) {
            case CLASSIC:
                // LOTR's slab: a child of the body, painted at (24,0) on LOTR
                // skins; on a Minecraft skin it borrows the upper torso front.
                this.chestLeft = child(this.bipedBody,
                        minecraftLayout ? 19 : 24, minecraftLayout ? 19 : 0,
                        -3.0F, 2.0F, -4.0F, 6, 3, 2, inflation);
                this.chestRight = null;
                this.chestLeftOverlay = null;
                this.chestRightOverlay = null;
                break;
            case ROUNDED:
                // Two boxes hung from the torso front, drawn in body space by
                // renderChest. Their fronts sample the upper torso front and
                // their outer sides the torso sides, so they read as part of
                // the shirt on any skin; a Minecraft skin's jacket rows give
                // the overlay pair.
                this.chestLeft = standalone(17, 18, -4.0F, 0.0F, 0.0F, 4, 5, 3, inflation);
                this.chestRight = standalone(21, 18, 0.0F, 0.0F, 0.0F, 4, 5, 3, inflation);
                this.chestLeftOverlay = minecraftLayout
                        ? standalone(17, 34, -4.0F, 0.0F, 0.0F, 4, 5, 3, overlay) : null;
                this.chestRightOverlay = minecraftLayout
                        ? standalone(21, 34, 0.0F, 0.0F, 0.0F, 4, 5, 3, overlay) : null;
                break;
            case FULL:
                // One slab across the chest, sloping down and forward, scaled
                // by fullness; it samples the torso front two rows down.
                this.chestLeft = standalone(18, 22, -4.0F, 0.0F, 0.0F, 8, 2, 2, inflation);
                this.chestRight = null;
                this.chestLeftOverlay = minecraftLayout
                        ? standalone(18, 38, -4.0F, 0.0F, 0.0F, 8, 2, 2, overlay) : null;
                this.chestRightOverlay = null;
                break;
            default:
                this.chestLeft = null;
                this.chestRight = null;
                this.chestLeftOverlay = null;
                this.chestRightOverlay = null;
                break;
        }

        if (!armorTexture && shape.hasElfEars()) {
            // LOTR paints elf ears at (0,0); on a Minecraft skin they take
            // the side of the head, so they come out in skin and hair tones.
            int earU = minecraftLayout ? 1 : 0;
            int earV = minecraftLayout ? 9 : 0;
            ModelRenderer earRight = child(this.bipedHead, earU, earV,
                    -4.0F, -6.5F, -1.0F, 1, 4, 2, 0.0F);
            earRight.rotateAngleZ = -(float)Math.toRadians(15.0D);
            ModelRenderer earLeft = new ModelRenderer(this, earU, earV);
            earLeft.mirror = true;
            earLeft.addBox(3.0F, -6.5F, -1.0F, 1, 4, 2, 0.0F);
            earLeft.rotateAngleZ = (float)Math.toRadians(15.0D);
            this.bipedHead.addChild(earLeft);
        }

        if (!armorTexture && shape.hasOrcFeatures()) {
            // LOTR paints the nose at (14,17) and the ears at (0,0) and
            // (24,0); on a Minecraft skin they take the face and the side
            // of the head.
            ModelRenderer nose = child(this.bipedHead,
                    minecraftLayout ? 10 : 14, minecraftLayout ? 11 : 17,
                    -0.5F, -4.0F, -4.8F, 1, 2, 1, inflation);
            nose.setRotationPoint(0.0F, 0.0F, 0.0F);
            ModelRenderer earRight = child(this.bipedHead,
                    minecraftLayout ? 1 : 0, minecraftLayout ? 9 : 0,
                    -3.5F, -5.5F, 2.0F, 1, 2, 3, inflation);
            earRight.rotateAngleX = (float)Math.toRadians(15.0D);
            earRight.rotateAngleY = -(float)Math.toRadians(30.0D);
            earRight.rotateAngleZ = -(float)Math.toRadians(13.0D);
            ModelRenderer earLeft = child(this.bipedHead,
                    minecraftLayout ? 1 : 24, minecraftLayout ? 9 : 0,
                    2.5F, -5.5F, 2.0F, 1, 2, 3, inflation);
            earLeft.rotateAngleX = (float)Math.toRadians(15.0D);
            earLeft.rotateAngleY = (float)Math.toRadians(30.0D);
            earLeft.rotateAngleZ = (float)Math.toRadians(13.0D);
        }

        if (!armorTexture && shape.hasHobbitProportions()) {
            // LOTR paints the feet at (40,32); on a Minecraft skin they
            // take the lower rows of the right leg.
            int footU = minecraftLayout ? 1 : 40;
            int footV = minecraftLayout ? 19 : 32;
            ModelRenderer footRight = child(this.bipedRightLeg, footU, footV,
                    -2.0F, 10.0F, -5.0F, 4, 2, 3, 0.0F);
            footRight.rotateAngleY = (float)Math.toRadians(10.0D);
            ModelRenderer footLeft = child(this.bipedLeftLeg, footU, footV,
                    -2.0F, 10.0F, -5.0F, 4, 2, 3, 0.0F);
            footLeft.rotateAngleY = -(float)Math.toRadians(10.0D);
        }

        applyShapePivots();
    }

    /**
     * The half-troll replaces every biped box. Its armor is painted for the
     * same boxes, so the armor variant keeps them and only drops the
     * features on the head. Parts start at vanilla's pivots; the shape's
     * pivot rules then move them, as they do after every pose.
     */
    private void buildHalfTroll(float inflation, boolean slim, boolean armorTexture) {
        this.bipedHead = new ModelRenderer(this, 0, 0);
        this.bipedHead.setRotationPoint(0.0F, 0.0F, 0.0F);
        this.bipedHead.addBox(-5.0F, -10.0F, -5.0F, 10, 10, 10, inflation);
        this.bipedHead.setTextureOffset(40, 5);
        this.bipedHead.addBox(-4.0F, -3.0F, -7.0F, 8, 3, 2, inflation);
        if (!armorTexture) {
            ModelRenderer nose = child(this.bipedHead, 30, 0,
                    -1.0F, -4.5F, -8.0F, 2, 3, 3, inflation);
            nose.rotateAngleX = -(float)Math.toRadians(20.0D);
            ModelRenderer tusks = new ModelRenderer(this, 60, 7);
            tusks.addBox(-3.5F, -7.5F, -5.0F, 1, 2, 1, inflation);
            tusks.mirror = true;
            tusks.addBox(2.5F, -7.5F, -5.0F, 1, 2, 1, inflation);
            tusks.rotateAngleX = (float)Math.toRadians(30.0D);
            this.bipedHead.addChild(tusks);
            ModelRenderer earRight = child(this.bipedHead, 0, 0,
                    -5.0F, -6.0F, -2.0F, 1, 3, 3, inflation);
            earRight.rotateAngleY = -(float)Math.toRadians(35.0D);
            ModelRenderer earLeft = new ModelRenderer(this, 0, 0);
            earLeft.mirror = true;
            earLeft.addBox(4.0F, -6.0F, -2.0F, 1, 3, 3, inflation);
            earLeft.rotateAngleY = (float)Math.toRadians(35.0D);
            this.bipedHead.addChild(earLeft);
            child(this.bipedHead, 40, 10, -1.0F, -12.5F, -1.5F, 2, 10, 8, inflation);
            ModelRenderer hornRight = child(this.bipedHead, 40, 0,
                    -10.0F, -8.0F, 1.0F, 3, 2, 2, inflation);
            hornRight.rotateAngleZ = (float)Math.toRadians(20.0D);
            ModelRenderer hornRightTip = child(this.bipedHead, 50, 2,
                    -14.5F, -4.0F, 1.5F, 3, 1, 1, inflation);
            hornRightTip.rotateAngleZ = (float)Math.toRadians(40.0D);
            ModelRenderer hornLeft = new ModelRenderer(this, 40, 0);
            hornLeft.mirror = true;
            hornLeft.addBox(7.0F, -8.0F, 1.0F, 3, 2, 2, inflation);
            hornLeft.rotateAngleZ = -(float)Math.toRadians(20.0D);
            this.bipedHead.addChild(hornLeft);
            ModelRenderer hornLeftTip = new ModelRenderer(this, 50, 2);
            hornLeftTip.mirror = true;
            hornLeftTip.addBox(11.5F, -4.0F, 1.5F, 3, 1, 1, inflation);
            hornLeftTip.rotateAngleZ = -(float)Math.toRadians(40.0D);
            this.bipedHead.addChild(hornLeftTip);
        }

        this.bipedBody = new ModelRenderer(this, 0, 20);
        this.bipedBody.setRotationPoint(0.0F, -8.0F, 0.0F);
        this.bipedBody.addBox(-6.0F, 0.0F, -4.0F, 12, 16, 8, inflation);

        // Slim trims one pixel off the outer side of each arm piece.
        int upperWidth = slim ? 5 : 6;
        int lowerWidth = slim ? 4 : 5;
        this.bipedRightArm = new ModelRenderer(this, 20, 50);
        this.bipedRightArm.setRotationPoint(-VANILLA_ARM_REACH, 0.0F, 0.0F);
        this.bipedRightArm.addBox(slim ? -2.5F : -3.5F, -2.0F, -3.0F,
                upperWidth, 8, 6, inflation);
        this.bipedRightArm.setTextureOffset(0, 49);
        this.bipedRightArm.addBox(slim ? -2.0F : -3.0F, 6.0F, -2.5F,
                lowerWidth, 10, 5, inflation);
        this.bipedLeftArm = new ModelRenderer(this, 20, 50);
        this.bipedLeftArm.setRotationPoint(VANILLA_ARM_REACH, 0.0F, 0.0F);
        this.bipedLeftArm.mirror = true;
        this.bipedLeftArm.addBox(-2.5F, -2.0F, -3.0F, upperWidth, 8, 6, inflation);
        this.bipedLeftArm.setTextureOffset(0, 49);
        this.bipedLeftArm.addBox(-2.0F, 6.0F, -2.5F, lowerWidth, 10, 5, inflation);

        this.bipedRightLeg = new ModelRenderer(this, 40, 28);
        this.bipedRightLeg.setRotationPoint(-3.2F, 12.0F, 0.0F);
        this.bipedRightLeg.addBox(-3.0F, 0.0F, -3.0F, 6, 16, 6, inflation);
        this.bipedLeftLeg = new ModelRenderer(this, 40, 28);
        this.bipedLeftLeg.setRotationPoint(3.2F, 12.0F, 0.0F);
        this.bipedLeftLeg.mirror = true;
        this.bipedLeftLeg.addBox(-3.0F, 0.0F, -3.0F, 6, 16, 6, inflation);
        this.bipedHeadwear.isHidden = true;
    }

    public PlayerBodyShape getShape() {
        return this.shape;
    }

    public CharacterSkinLayout getLayout() {
        return this.layout;
    }

    public boolean isSlim() {
        return this.slim;
    }

    public boolean isArmorTexture() {
        return this.armorTexture;
    }

    /** True when a feminine chest is drawn. */
    public boolean hasChest() {
        return this.chestShape != CharacterChestTypeRegistry.Shape.NONE;
    }

    public CharacterChestTypeRegistry.Shape getChestShape() {
        return this.chestShape;
    }

    /** True when a Minecraft-layout skin's jacket rows are drawn over the chest. */
    public boolean hasChestOverlay() {
        return this.chestLeftOverlay != null;
    }

    /** Shows or hides the body, arm, leg, and chest overlays; the hat is vanilla's. */
    public void setOverlaysVisible(boolean visible) {
        for (int index = 0; index < this.overlays.length; index++) {
            this.overlays[index].showModel = visible;
        }
        if (this.chestLeftOverlay != null) {
            this.chestLeftOverlay.showModel = visible;
        }
        if (this.chestRightOverlay != null) {
            this.chestRightOverlay.showModel = visible;
        }
    }

    /**
     * Vanilla's pose, then the pivots the shape keeps away from vanilla's,
     * which the biped rewrites every frame for sneaking and swinging.
     */
    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount,
                                  float ageInTicks, float netHeadYaw,
                                  float headPitch, float scale, Entity entity) {
        super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks,
                netHeadYaw, headPitch, scale, entity);
        applyShapePivots();
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount,
                       float ageInTicks, float netHeadYaw, float headPitch,
                       float scale) {
        if (this.isChild) {
            super.render(entity, limbSwing, limbSwingAmount, ageInTicks,
                    netHeadYaw, headPitch, scale);
            return;
        }
        this.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks,
                netHeadYaw, headPitch, scale, entity);
        this.bipedHead.render(scale);
        if (this.shape.hasDwarfProportions()) {
            renderDwarfLimbs(entity, limbSwing, limbSwingAmount, ageInTicks, scale);
        } else if (this.shape.hasHobbitProportions()) {
            renderHobbitLimbs(entity, limbSwing, limbSwingAmount, ageInTicks, scale);
        } else {
            this.bipedBody.render(scale);
            renderChest(entity, limbSwing, limbSwingAmount, ageInTicks, scale);
            this.bipedRightArm.render(scale);
            this.bipedLeftArm.render(scale);
            this.bipedRightLeg.render(scale);
            this.bipedLeftLeg.render(scale);
        }
        this.bipedHeadwear.render(scale);
    }

    /**
     * The chest, drawn in the torso's own space so sneaking and turning
     * carry it, swayed by its physics. The classic slab is a child of the
     * body and needs nothing here. A chestplate covers every shape.
     */
    private void renderChest(Entity entity, float limbSwing, float limbSwingAmount,
                             float ageInTicks, float scale) {
        if (this.chestLeft == null || !this.bipedBody.showModel
                || this.chestShape == CharacterChestTypeRegistry.Shape.CLASSIC
                || this.chestShape == CharacterChestTypeRegistry.Shape.NONE) {
            return;
        }
        if (entity instanceof EntityPlayer
                && ((EntityPlayer)entity).getCurrentArmor(CHESTPLATE_SLOT) != null) {
            return;
        }
        float sway = 0.0F;
        float bounce = 0.0F;
        if (LostTalesConfig.chestPhysics && entity instanceof EntityLivingBase) {
            ChestPhysics physics = ChestPhysicsStates.advance(
                    (EntityLivingBase)entity, limbSwing, limbSwingAmount,
                    clamp(LostTalesConfig.chestBounce, 0.0F, 1.0F));
            float partialTick = ageInTicks - MathHelper.floor_float(ageInTicks);
            sway = physics.swayX(partialTick);
            bounce = physics.bounceY(partialTick);
        }

        GL11.glPushMatrix();
        this.bipedBody.postRender(scale);
        if (this.chestShape == CharacterChestTypeRegistry.Shape.ROUNDED) {
            float bodyScale = this.chestSize;
            GL11.glTranslatef(sway * scale,
                    (ROUNDED_HANG + bounce) * scale,
                    (TORSO_FRONT + 1.0F - Math.min(bodyScale, 1.0F)) * scale);
            GL11.glRotatef(ROUNDED_TILT * Math.min(bodyScale, 1.0F) + bounce * BOUNCE_TILT,
                    1.0F, 0.0F, 0.0F);
            GL11.glRotatef(sway * SWAY_TURN, 0.0F, 1.0F, 0.0F);
            GL11.glScalef(bodyScale, bodyScale, bodyScale);
            this.chestLeft.render(scale);
            this.chestRight.render(scale);
            if (this.chestLeftOverlay != null && this.chestLeftOverlay.showModel) {
                this.chestLeftOverlay.render(scale);
                this.chestRightOverlay.render(scale);
            }
        } else {
            float fullness = fullness(this.chestSize);
            GL11.glTranslatef(sway * scale,
                    (4.0F + this.chestSize * 0.864F * fullness + bounce) * scale,
                    (-1.9F - this.chestSize * 1.944F * fullness) * scale);
            GL11.glRotatef(FULL_SLOPE + bounce * BOUNCE_TILT, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(sway * SWAY_TURN, 0.0F, 1.0F, 0.0F);
            GL11.glScalef(1.0F,
                    (1.0F + this.chestSize * 2.0F * fullness) * 0.5F,
                    (1.0F + this.chestSize * 2.5F * fullness) * 0.5F);
            this.chestLeft.render(scale);
            if (this.chestLeftOverlay != null && this.chestLeftOverlay.showModel) {
                this.chestLeftOverlay.render(scale);
            }
        }
        GL11.glPopMatrix();
    }

    /** Eases a fullness level from 0 to 1 so growth slows towards the top. */
    static float fullness(float level) {
        return 1.48F - (float)(1.0D / Math.sqrt((level + 1.6F) * 0.95F));
    }

    /** Torso and legs a quarter wider, arms pushed one pixel outward. */
    private void renderDwarfLimbs(Entity entity, float limbSwing, float limbSwingAmount,
                                  float ageInTicks, float scale) {
        GL11.glPushMatrix();
        GL11.glScalef(DWARF_WIDTH_SCALE, 1.0F, 1.0F);
        this.bipedBody.render(scale);
        renderChest(entity, limbSwing, limbSwingAmount, ageInTicks, scale);
        GL11.glPopMatrix();
        GL11.glPushMatrix();
        GL11.glTranslatef(-DWARF_ARM_SHIFT * scale, 0.0F, 0.0F);
        this.bipedRightArm.render(scale);
        GL11.glPopMatrix();
        GL11.glPushMatrix();
        GL11.glTranslatef(DWARF_ARM_SHIFT * scale, 0.0F, 0.0F);
        this.bipedLeftArm.render(scale);
        GL11.glPopMatrix();
        GL11.glPushMatrix();
        GL11.glTranslatef(-DWARF_LEG_SHIFT * scale, 0.0F, 0.0F);
        GL11.glScalef(DWARF_WIDTH_SCALE, 1.0F, 1.0F);
        this.bipedRightLeg.render(scale);
        GL11.glPopMatrix();
        GL11.glPushMatrix();
        GL11.glTranslatef(DWARF_LEG_SHIFT * scale, 0.0F, 0.0F);
        GL11.glScalef(DWARF_WIDTH_SCALE, 1.0F, 1.0F);
        this.bipedLeftLeg.render(scale);
        GL11.glPopMatrix();
    }

    /** Torso and limbs squeezed to ten twelfths about the neck, feet staying on the ground. */
    private void renderHobbitLimbs(Entity entity, float limbSwing, float limbSwingAmount,
                                   float ageInTicks, float scale) {
        GL11.glPushMatrix();
        GL11.glScalef(1.0F, HOBBIT_LIMB_SCALE, 1.0F);
        this.bipedBody.render(scale);
        renderChest(entity, limbSwing, limbSwingAmount, ageInTicks, scale);
        this.bipedRightArm.render(scale);
        this.bipedLeftArm.render(scale);
        this.bipedRightLeg.render(scale);
        this.bipedLeftLeg.render(scale);
        GL11.glPopMatrix();
    }

    private void applyShapePivots() {
        if (!this.shape.hasOwnPivots()) {
            return;
        }
        this.bipedHead.rotationPointY += this.shape.getHeadDrop();
        this.bipedHeadwear.rotationPointY = this.bipedHead.rotationPointY;
        this.bipedBody.rotationPointY = this.shape.getBodyPivotY();
        float armY = this.shape.armPivotY(this.bipedArmPivotY);
        float reach = this.shape.getArmReach() / VANILLA_ARM_REACH;
        this.bipedRightArm.rotationPointY = armY;
        this.bipedLeftArm.rotationPointY = armY;
        this.bipedRightArm.rotationPointX *= reach;
        this.bipedRightArm.rotationPointZ *= reach;
        this.bipedLeftArm.rotationPointX *= reach;
        this.bipedLeftArm.rotationPointZ *= reach;
        this.bipedRightLeg.rotationPointY += this.shape.getLegDrop();
        this.bipedLeftLeg.rotationPointY += this.shape.getLegDrop();
    }

    private ModelRenderer child(ModelRenderer parent, int textureU, int textureV,
                                float x, float y, float z,
                                int width, int height, int depth,
                                float inflation) {
        ModelRenderer part = standalone(textureU, textureV, x, y, z,
                width, height, depth, inflation);
        parent.addChild(part);
        return part;
    }

    private ModelRenderer standalone(int textureU, int textureV,
                                     float x, float y, float z,
                                     int width, int height, int depth,
                                     float inflation) {
        ModelRenderer part = new ModelRenderer(this, textureU, textureV);
        part.addBox(x, y, z, width, height, depth, inflation);
        part.setRotationPoint(0.0F, 0.0F, 0.0F);
        return part;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return value < minimum ? minimum : value > maximum ? maximum : value;
    }

    private static int textureHeight(PlayerBodyShape shape, CharacterSkinLayout layout,
                                     boolean armorTexture) {
        if (shape != null && shape.isHalfTroll()) {
            return TEXTURE_SIZE;
        }
        if (armorTexture || layout == CharacterSkinLayout.LOTR_64X32) {
            return CLASSIC_TEXTURE_HEIGHT;
        }
        return TEXTURE_SIZE;
    }
}
