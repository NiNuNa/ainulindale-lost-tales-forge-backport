package com.ninuna.losttales.client.accessory;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntitySmokeFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Sparse world-space smoke veils visible only to the Ring wearer. */
@SideOnly(Side.CLIENT)
final class WraithWorldVeilField {

    private static final int MAXIMUM_PARTICLES = 56;
    private static final Random RANDOM = new Random();
    private static final List<VeilParticle> PARTICLES =
            new ArrayList<VeilParticle>();
    private static int spawnTicks;

    private WraithWorldVeilField() {}

    static void update(
            Minecraft minecraft, boolean active, float effectIntensity) {
        float amount = Math.max(0.0F, Math.min(1.0F, effectIntensity));
        Iterator<VeilParticle> iterator = PARTICLES.iterator();
        while (iterator.hasNext()) {
            VeilParticle particle = iterator.next();
            if (particle == null || particle.isDead) {
                iterator.remove();
            } else {
                particle.setEffectIntensity(amount);
            }
        }

        if (!active || amount < 0.18F || minecraft == null
                || minecraft.theWorld == null
                || minecraft.renderViewEntity == null
                || minecraft.effectRenderer == null) {
            if (amount <= 0.001F) {
                clear();
            }
            return;
        }

        int particleSetting = minecraft.gameSettings == null
                ? 0 : minecraft.gameSettings.particleSetting;
        int interval = particleSetting <= 0 ? 1
                : particleSetting == 1 ? 2 : 3;
        if (++spawnTicks % interval != 0
                || PARTICLES.size() >= MAXIMUM_PARTICLES) {
            return;
        }
        spawn(minecraft, amount);
    }

    static void clear() {
        for (VeilParticle particle : PARTICLES) {
            if (particle != null) {
                particle.setDead();
            }
        }
        PARTICLES.clear();
        spawnTicks = 0;
    }

    private static void spawn(Minecraft minecraft, float intensity) {
        Entity view = minecraft.renderViewEntity;
        World world = minecraft.theWorld;
        double yaw = Math.toRadians(view.rotationYaw);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = forwardZ;
        double rightZ = -forwardX;

        for (int attempt = 0; attempt < 4; attempt++) {
            double forward = 2.2D + RANDOM.nextDouble() * 7.0D;
            double sideMagnitude = 1.4D + RANDOM.nextDouble() * 4.4D;
            double sideways = RANDOM.nextBoolean()
                    ? sideMagnitude : -sideMagnitude;
            double vertical = (RANDOM.nextDouble() - 0.45D) * 4.2D;
            double x = view.posX + forwardX * forward
                    + rightX * sideways;
            double y = view.posY + view.getEyeHeight() + vertical;
            double z = view.posZ + forwardZ * forward
                    + rightZ * sideways;
            int blockX = MathHelper.floor_double(x);
            int blockY = MathHelper.floor_double(y);
            int blockZ = MathHelper.floor_double(z);
            if (!world.isAirBlock(blockX, blockY, blockZ)) {
                continue;
            }

            double driftAngle = RANDOM.nextDouble() * Math.PI * 2.0D;
            double drift = 0.002D + RANDOM.nextDouble() * 0.006D;
            VeilParticle particle = new VeilParticle(
                    world, x, y, z,
                    Math.cos(driftAngle) * drift + forwardX * 0.0015D,
                    0.002D + RANDOM.nextDouble() * 0.007D,
                    Math.sin(driftAngle) * drift + forwardZ * 0.0015D,
                    10.0F + RANDOM.nextFloat() * 11.0F,
                    65 + RANDOM.nextInt(56),
                    0.065F + RANDOM.nextFloat() * 0.060F,
                    RANDOM.nextFloat() * 6.2831855F);
            particle.setEffectIntensity(intensity);
            PARTICLES.add(particle);
            minecraft.effectRenderer.addEffect(particle);
            return;
        }
    }

    private static final class VeilParticle extends EntitySmokeFX {

        private final float maximumAlpha;
        private final float maximumScale;
        private final float verticalStretch;
        private float effectIntensity;
        private float phase;

        private VeilParticle(
                World world, double x, double y, double z,
                double motionX, double motionY, double motionZ,
                float scale, int maximumAge, float maximumAlpha,
                float phase) {
            super(world, x, y, z,
                    motionX, motionY, motionZ, scale);
            this.particleMaxAge = maximumAge;
            this.maximumAlpha = maximumAlpha;
            this.maximumScale = this.particleScale;
            this.verticalStretch = 1.45F
                    + RANDOM.nextFloat() * 1.15F;
            this.phase = phase;
            this.noClip = true;
            setRBGColorF(0.68F, 0.82F, 0.88F);
            setAlphaF(0.0F);
        }

        private void setEffectIntensity(float intensity) {
            this.effectIntensity = Math.max(
                    0.0F, Math.min(1.0F, intensity));
        }

        @Override
        public void renderParticle(
                Tessellator tessellator, float partialTicks,
                float rotationX, float rotationXZ, float rotationZ,
                float rotationYZ, float rotationXY) {
            float growth = (this.particleAge + partialTicks)
                    / this.particleMaxAge * 20.0F;
            growth = Math.max(0.0F, Math.min(1.0F, growth));
            float flutter = 0.94F + 0.06F
                    * MathHelper.sin(this.phase + partialTicks * 0.075F);
            float halfWidth = 0.1F * this.maximumScale
                    * growth * flutter;
            float halfHeight = halfWidth * this.verticalStretch;
            float x = (float)(this.prevPosX
                    + (this.posX - this.prevPosX) * partialTicks
                    - interpPosX);
            float y = (float)(this.prevPosY
                    + (this.posY - this.prevPosY) * partialTicks
                    - interpPosY);
            float z = (float)(this.prevPosZ
                    + (this.posZ - this.prevPosZ) * partialTicks
                    - interpPosZ);

            renderBillboard(tessellator, x, y, z,
                    halfWidth, halfHeight,
                    rotationX, rotationXZ, rotationZ,
                    rotationYZ, rotationXY, this.particleAlpha);
            renderBillboard(tessellator, x, y - halfHeight * 0.72F, z,
                    halfWidth * 0.72F, halfHeight * 0.84F,
                    rotationX, rotationXZ, rotationZ,
                    rotationYZ, rotationXY, this.particleAlpha * 0.36F);
        }

        private void renderBillboard(
                Tessellator tessellator, float x, float y, float z,
                float halfWidth, float halfHeight,
                float rotationX, float rotationXZ, float rotationZ,
                float rotationYZ, float rotationXY, float alpha) {
            float minimumU = this.particleTextureIndexX / 16.0F;
            float maximumU = minimumU + 0.0624375F;
            float minimumV = this.particleTextureIndexY / 16.0F;
            float maximumV = minimumV + 0.0624375F;
            tessellator.setColorRGBA_F(this.particleRed,
                    this.particleGreen, this.particleBlue, alpha);
            tessellator.addVertexWithUV(
                    x - rotationX * halfWidth
                            - rotationYZ * halfHeight,
                    y - rotationXZ * halfHeight,
                    z - rotationZ * halfWidth
                            - rotationXY * halfHeight,
                    maximumU, maximumV);
            tessellator.addVertexWithUV(
                    x - rotationX * halfWidth
                            + rotationYZ * halfHeight,
                    y + rotationXZ * halfHeight,
                    z - rotationZ * halfWidth
                            + rotationXY * halfHeight,
                    maximumU, minimumV);
            tessellator.addVertexWithUV(
                    x + rotationX * halfWidth
                            + rotationYZ * halfHeight,
                    y + rotationXZ * halfHeight,
                    z + rotationZ * halfWidth
                            + rotationXY * halfHeight,
                    minimumU, minimumV);
            tessellator.addVertexWithUV(
                    x + rotationX * halfWidth
                            - rotationYZ * halfHeight,
                    y - rotationXZ * halfHeight,
                    z + rotationZ * halfWidth
                            - rotationXY * halfHeight,
                    minimumU, maximumV);
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            this.phase += 0.075F;
            this.motionX += Math.sin(this.phase) * 0.00035D;
            this.motionZ += Math.cos(this.phase * 0.83F) * 0.00035D;
            float life = this.particleMaxAge <= 0 ? 1.0F
                    : this.particleAge / (float)this.particleMaxAge;
            float fadeIn = Math.min(1.0F, life / 0.18F);
            float fadeOut = Math.min(1.0F, (1.0F - life) / 0.30F);
            setAlphaF(this.maximumAlpha
                    * Math.max(0.0F, Math.min(fadeIn, fadeOut))
                    * this.effectIntensity);
        }
    }
}
