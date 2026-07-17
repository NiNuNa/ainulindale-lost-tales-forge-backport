package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.camera.ThirdPersonDirectionalMovementController;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import lotr.client.model.LOTRModelBiped;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renders the normal or selected LOTR cape in the configured race model's torso space.
 *
 * The vanilla cape animation is retained, but the attachment translation is
 * applied before those rotations. This is important for short and tall models:
 * moving the cloak ModelRenderer pivot after the cape rotations would make the
 * shoulder point orbit while the cape swings.
 */
final class LostTalesPlayerCapeRenderer {

    private static final float MODEL_UNIT = 0.0625F;

    /**
     * LOTR cape textures use the original 64x32 biped cloak UV layout. The
     * configured player models use 64x64 skins, so their inherited cloak part
     * maps only half of a LOTR cape texture and stretches it vertically. LOTR's
     * own NPC renderer solves this with a dedicated default LOTRModelBiped.
     */
    private static final ModelBiped CAPE_MODEL = new LOTRModelBiped();
    private static final float VANILLA_BACK_OFFSET = 2.0F * MODEL_UNIT;
    private static final float HOBBIT_VERTICAL_SCALE = 0.8333333F;
    private static final float DWARF_WIDTH_SCALE = 1.25F;
    private static final float HALF_TROLL_WIDTH_SCALE = 1.5F;
    private static final float HALF_TROLL_LENGTH_SCALE = 1.3333334F;
    private static final float HALF_TROLL_BACK_OFFSET = 4.0F * MODEL_UNIT;
    private static final float MAXIMUM_FORWARD_LIFT = 80.0F;
    private static final float MAXIMUM_SIDE_SWING = 65.0F;
    private static final float WALKING_RESPONSE_SPEED = 12.0F;
    private static final float SPRINTING_RESPONSE_SPEED = 9.0F;
    private static final Map<AbstractClientPlayer, CapeMotionState>
            MOTION_STATES =
            new WeakHashMap<AbstractClientPlayer, CapeMotionState>();

    private LostTalesPlayerCapeRenderer() {}

    static void render(LostTalesConfiguredPlayerRenderer renderer,
                       AbstractClientPlayer player,
                       float partialTicks) {
        if (renderer == null || player == null || player.isInvisible()) {
            return;
        }

        CharacterAppearance appearance = player.getUniqueID() == null
                ? null
                : ClientCharacterAppearanceCache.get(player.getUniqueID());
        ResourceLocation capeTexture = appearance == null
                ? null
                : LostTalesLotrCapeTextureResolver.resolve(
                        appearance.getCosmeticCapeId());
        if (capeTexture == null) {
            if (appearance != null && !appearance.isMinecraftCapeVisible()) {
                return;
            }
            if (!player.func_152122_n() || player.getHideCape()) {
                return;
            }
            capeTexture = player.getLocationCape();
        }

        ModelBiped model = renderer.getConfiguredModel();
        if (capeTexture == null || model == null
                || CAPE_MODEL.bipedCloak == null) {
            return;
        }

        CapeTransform transform = CapeTransform.forRace(renderer.getRaceId());
        ModelRenderer body = model.bipedBody;
        ModelRenderer cloak = CAPE_MODEL.bipedCloak;

        float attachmentX = body == null ? 0.0F
                : body.rotationPointX * transform.bodyScaleX * MODEL_UNIT;
        float attachmentY = body == null ? 0.0F
                : body.rotationPointY * transform.bodyScaleY * MODEL_UNIT;
        float attachmentZ = body == null ? 0.0F
                : body.rotationPointZ * transform.bodyScaleZ * MODEL_UNIT;
        float backOffset = transform.backOffset;

        float previousPointX = cloak.rotationPointX;
        float previousPointY = cloak.rotationPointY;
        float previousPointZ = cloak.rotationPointZ;
        float previousAngleX = cloak.rotateAngleX;
        float previousAngleY = cloak.rotateAngleY;
        float previousAngleZ = cloak.rotateAngleZ;

        renderer.bindCapeTexture(capeTexture);
        GL11.glPushMatrix();
        try {
            // RenderCloak normally owns a zero-pivot part. Zero it explicitly so
            // LOTR model setup cannot add a second attachment translation.
            cloak.rotationPointX = 0.0F;
            cloak.rotationPointY = 0.0F;
            cloak.rotationPointZ = 0.0F;
            cloak.rotateAngleX = 0.0F;
            cloak.rotateAngleY = 0.0F;
            cloak.rotateAngleZ = 0.0F;

            GL11.glTranslatef(
                    attachmentX,
                    attachmentY,
                    attachmentZ + backOffset);

            CapeMotion motion = calculateMotion(player, partialTicks);
            GL11.glRotatef(
                    6.0F + motion.forwardLift * 0.5F + motion.verticalLift,
                    1.0F, 0.0F, 0.0F);
            GL11.glRotatef(motion.sideSwing * 0.5F,
                    0.0F, 0.0F, 1.0F);
            GL11.glRotatef(-motion.sideSwing * 0.5F,
                    0.0F, 1.0F, 0.0F);
            GL11.glRotatef(180.0F, 0.0F, 1.0F, 0.0F);

            if (transform.capeScaleX != 1.0F
                    || transform.capeScaleY != 1.0F
                    || transform.capeScaleZ != 1.0F) {
                GL11.glScalef(
                        transform.capeScaleX,
                        transform.capeScaleY,
                        transform.capeScaleZ);
            }
            CAPE_MODEL.renderCloak(MODEL_UNIT);
        } finally {
            GL11.glPopMatrix();
            cloak.rotationPointX = previousPointX;
            cloak.rotationPointY = previousPointY;
            cloak.rotationPointZ = previousPointZ;
            cloak.rotateAngleX = previousAngleX;
            cloak.rotateAngleY = previousAngleY;
            cloak.rotateAngleZ = previousAngleZ;
        }
    }

    private static CapeMotion calculateMotion(AbstractClientPlayer player,
                                              float partialTicks) {
        double capeX = interpolate(
                player.field_71091_bM, player.field_71094_bP, partialTicks);
        double capeY = interpolate(
                player.field_71096_bN, player.field_71095_bQ, partialTicks);
        double capeZ = interpolate(
                player.field_71097_bO, player.field_71085_bR, partialTicks);
        double playerX = interpolate(player.prevPosX, player.posX, partialTicks);
        double playerY = interpolate(player.prevPosY, player.posY, partialTicks);
        double playerZ = interpolate(player.prevPosZ, player.posZ, partialTicks);

        double deltaX = capeX - playerX;
        double deltaY = capeY - playerY;
        double deltaZ = capeZ - playerZ;
        float bodyYaw = CapeMotionMath.interpolateDegrees(
                player.prevRenderYawOffset,
                player.renderYawOffset,
                partialTicks);
        double yawSin = MathHelper.sin(bodyYaw * (float)Math.PI / 180.0F);
        double yawBack = -MathHelper.cos(bodyYaw * (float)Math.PI / 180.0F);

        float verticalLift = clamp((float)deltaY * 10.0F, -6.0F, 32.0F);
        float forwardLift = (float)(deltaX * yawSin + deltaZ * yawBack) * 100.0F;
        float sideSwing = (float)(deltaX * yawBack - deltaZ * yawSin) * 100.0F;
        if (forwardLift < 0.0F) {
            forwardLift = 0.0F;
        }

        float cameraYaw = interpolate(
                player.prevCameraYaw, player.cameraYaw, partialTicks);
        float walked = interpolate(
                player.prevDistanceWalkedModified,
                player.distanceWalkedModified,
                partialTicks);
        verticalLift += MathHelper.sin(walked * 6.0F) * 32.0F * cameraYaw;
        if (player.isSneaking()) {
            verticalLift += 25.0F;
        }
        CapeMotion raw = new CapeMotion(
                verticalLift, forwardLift, sideSwing);
        return stabilizeDirectionalMotion(
                player, raw, System.nanoTime());
    }

    private static CapeMotion stabilizeDirectionalMotion(
            AbstractClientPlayer player, CapeMotion raw,
            long updateNanos) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer != player
                || !ThirdPersonDirectionalMovementController.isActive()) {
            MOTION_STATES.remove(player);
            return raw;
        }

        float targetForward = CapeMotionMath.clamp(
                raw.forwardLift, 0.0F, MAXIMUM_FORWARD_LIFT);
        float targetSide = CapeMotionMath.clamp(
                raw.sideSwing,
                -MAXIMUM_SIDE_SWING, MAXIMUM_SIDE_SWING);
        long activationSequence =
                ThirdPersonDirectionalMovementController
                        .getActivationSequence();
        CapeMotionState state = MOTION_STATES.get(player);
        if (state == null
                || state.activationSequence != activationSequence
                || updateNanos <= state.lastUpdateNanos) {
            state = new CapeMotionState(
                    activationSequence, updateNanos,
                    targetForward, targetSide);
            MOTION_STATES.put(player, state);
        } else {
            float deltaSeconds = (float)(
                    (double)(updateNanos - state.lastUpdateNanos)
                            / 1000000000.0D);
            float response = player.isSprinting()
                    ? SPRINTING_RESPONSE_SPEED
                    : WALKING_RESPONSE_SPEED;
            state.forwardLift = CapeMotionMath.damp(
                    state.forwardLift, targetForward,
                    response, deltaSeconds);
            state.sideSwing = CapeMotionMath.damp(
                    state.sideSwing, targetSide,
                    response, deltaSeconds);
            state.lastUpdateNanos = updateNanos;
        }
        return new CapeMotion(
                raw.verticalLift,
                state.forwardLift,
                state.sideSwing);
    }

    private static double interpolate(double previous, double current,
                                      float partialTicks) {
        return previous + (current - previous) * (double)partialTicks;
    }

    private static float interpolate(float previous, float current,
                                     float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static final class CapeMotion {
        private final float verticalLift;
        private final float forwardLift;
        private final float sideSwing;

        private CapeMotion(float verticalLift, float forwardLift,
                           float sideSwing) {
            this.verticalLift = verticalLift;
            this.forwardLift = forwardLift;
            this.sideSwing = sideSwing;
        }
    }

    private static final class CapeMotionState {
        private final long activationSequence;
        private long lastUpdateNanos;
        private float forwardLift;
        private float sideSwing;

        private CapeMotionState(
                long activationSequence, long lastUpdateNanos,
                float forwardLift, float sideSwing) {
            this.activationSequence = activationSequence;
            this.lastUpdateNanos = lastUpdateNanos;
            this.forwardLift = forwardLift;
            this.sideSwing = sideSwing;
        }
    }

    /**
     * Mirrors the non-uniform torso transforms used by the supplied LOTR
     * player adapters. Values are client model data, not gameplay dimensions.
     */
    private static final class CapeTransform {
        private final float bodyScaleX;
        private final float bodyScaleY;
        private final float bodyScaleZ;
        private final float capeScaleX;
        private final float capeScaleY;
        private final float capeScaleZ;
        private final float backOffset;

        private CapeTransform(float bodyScaleX, float bodyScaleY,
                              float bodyScaleZ, float capeScaleX,
                              float capeScaleY, float capeScaleZ,
                              float backOffset) {
            this.bodyScaleX = bodyScaleX;
            this.bodyScaleY = bodyScaleY;
            this.bodyScaleZ = bodyScaleZ;
            this.capeScaleX = capeScaleX;
            this.capeScaleY = capeScaleY;
            this.capeScaleZ = capeScaleZ;
            this.backOffset = backOffset;
        }

        private static CapeTransform forRace(String raceId) {
            if (CharacterRaceRegistry.HOBBIT.equals(raceId)) {
                return new CapeTransform(
                        1.0F, HOBBIT_VERTICAL_SCALE, 1.0F,
                        1.0F, HOBBIT_VERTICAL_SCALE, 1.0F,
                        VANILLA_BACK_OFFSET);
            }
            if (CharacterRaceRegistry.DWARF.equals(raceId)) {
                return new CapeTransform(
                        DWARF_WIDTH_SCALE, 1.0F, 1.0F,
                        DWARF_WIDTH_SCALE, 1.0F, 1.0F,
                        VANILLA_BACK_OFFSET);
            }
            if (CharacterRaceRegistry.HALF_TROLL.equals(raceId)) {
                return new CapeTransform(
                        1.0F, 1.0F, 1.0F,
                        HALF_TROLL_WIDTH_SCALE,
                        HALF_TROLL_LENGTH_SCALE,
                        1.0F,
                        HALF_TROLL_BACK_OFFSET);
            }
            return new CapeTransform(
                    1.0F, 1.0F, 1.0F,
                    1.0F, 1.0F, 1.0F,
                    VANILLA_BACK_OFFSET);
        }
    }
}
