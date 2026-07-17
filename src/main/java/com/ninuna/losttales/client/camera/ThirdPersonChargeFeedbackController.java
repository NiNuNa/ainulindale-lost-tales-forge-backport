package com.ninuna.losttales.client.camera;

import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import com.ninuna.losttales.network.packet.LostTalesChargeTierSyncPacket;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/** Local audiovisual feedback for server-confirmed charge tiers. */
@SideOnly(Side.CLIENT)
public final class ThirdPersonChargeFeedbackController {
    private static final Map<Integer, RemoteChargeState> REMOTE_STATES =
            new HashMap<Integer, RemoteChargeState>();

    private static boolean active;
    private static int tier;
    private static double velocityMultiplier = 1.0D;
    private static int particleTicker;
    private static int releaseFlashTicks;
    private static int releaseFlashTier;

    private ThirdPersonChargeFeedbackController() {}

    public static void handle(LostTalesChargeTierSyncPacket packet) {
        if (packet == null || packet.isMalformed()
                || !LostTalesThirdPersonConfig.enableChargeTierFeedback) {
            reset();
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft == null
                ? null : minecraft.thePlayer;
        Entity source = minecraft == null || minecraft.theWorld == null
                ? null : minecraft.theWorld.getEntityByID(
                packet.getEntityId());
        if (!(source instanceof EntityLivingBase)) {
            return;
        }
        if (source != player) {
            handleRemote(
                    minecraft, (EntityLivingBase)source, packet);
            return;
        }
        if (packet.isReleased()) {
            if (player != null && packet.getTier() > 0) {
                playTierSound(player, packet.getTier(), true);
                spawnBurst(minecraft, player, packet.getTier() + 2);
            }
            active = false;
            tier = 0;
            velocityMultiplier = 1.0D;
            releaseFlashTicks = packet.getTier() > 0 ? 6 : 0;
            releaseFlashTier = packet.getTier();
            return;
        }
        if (!packet.isActive()) {
            active = false;
            tier = 0;
            velocityMultiplier = 1.0D;
            particleTicker = 0;
            releaseFlashTicks = 0;
            releaseFlashTier = 0;
            return;
        }

        int newTier = packet.getTier();
        if (player != null && newTier > tier) {
            playTierSound(player, newTier, false);
            spawnBurst(minecraft, player, newTier + 1);
        }
        active = true;
        tier = newTier;
        velocityMultiplier = packet.getVelocityMultiplier();
        releaseFlashTicks = 0;
        releaseFlashTier = 0;
    }

    public static void update(Minecraft minecraft) {
        if (releaseFlashTicks > 0) {
            releaseFlashTicks--;
        }
        if (minecraft == null || minecraft.theWorld == null) {
            return;
        }
        if (LostTalesThirdPersonConfig.enableChargeTierParticles
                && active && tier > 0
                && minecraft.thePlayer != null) {
            particleTicker++;
            int interval = Math.max(2, 6 - tier);
            if (particleTicker % interval == 0) {
                spawnChargeParticle(
                        minecraft, minecraft.thePlayer);
            }
        }
        updateRemoteParticles(minecraft);
    }

    public static boolean isActive() {
        return active;
    }

    public static int getTier() {
        return tier;
    }

    public static int getDisplayTier() {
        return tier > 0 ? tier
                : releaseFlashTicks > 0 ? releaseFlashTier : 0;
    }

    public static double getVelocityMultiplier() {
        return active ? velocityMultiplier : 1.0D;
    }

    public static void reset() {
        active = false;
        tier = 0;
        velocityMultiplier = 1.0D;
        particleTicker = 0;
        releaseFlashTicks = 0;
        releaseFlashTier = 0;
        REMOTE_STATES.clear();
    }

    private static void playTierSound(
            EntityLivingBase entity, int chargeTier, boolean released) {
        if (!LostTalesThirdPersonConfig.enableChargeTierSounds) {
            return;
        }
        float pitch = released
                ? 0.95F + chargeTier * 0.10F
                : 0.70F + chargeTier * 0.18F;
        entity.playSound("random.orb",
                released ? 0.45F : 0.32F,
                Math.min(1.8F, pitch));
    }

    private static void spawnChargeParticle(
            Minecraft minecraft, EntityLivingBase entity) {
        Random random = minecraft.theWorld.rand;
        double yaw = Math.toRadians(entity.rotationYaw);
        double x = entity.posX - Math.cos(yaw) * 0.32D
                + (random.nextDouble() - 0.5D) * 0.18D;
        double y = entity.posY + entity.getEyeHeight() - 0.30D
                + (random.nextDouble() - 0.5D) * 0.15D;
        double z = entity.posZ - Math.sin(yaw) * 0.32D
                + (random.nextDouble() - 0.5D) * 0.18D;
        minecraft.theWorld.spawnParticle(
                "enchantmenttable", x, y, z,
                0.0D, -0.15D, 0.0D);
    }

    private static void spawnBurst(
            Minecraft minecraft, EntityLivingBase entity, int count) {
        if (!LostTalesThirdPersonConfig.enableChargeTierParticles
                || minecraft == null || minecraft.theWorld == null) {
            return;
        }
        Random random = minecraft.theWorld.rand;
        for (int index = 0; index < count; index++) {
            double x = entity.posX
                    + (random.nextDouble() - 0.5D) * 0.65D;
            double y = entity.posY + entity.getEyeHeight() - 0.35D
                    + (random.nextDouble() - 0.5D) * 0.45D;
            double z = entity.posZ
                    + (random.nextDouble() - 0.5D) * 0.65D;
            minecraft.theWorld.spawnParticle(
                    "magicCrit", x, y, z,
                    0.0D, 0.015D, 0.0D);
        }
    }

    private static void handleRemote(
            Minecraft minecraft, EntityLivingBase entity,
            LostTalesChargeTierSyncPacket packet) {
        Integer entityId = Integer.valueOf(packet.getEntityId());
        RemoteChargeState previous = REMOTE_STATES.get(entityId);
        int previousTier = previous == null ? 0 : previous.tier;
        if (packet.isReleased()) {
            REMOTE_STATES.remove(entityId);
            if (packet.getTier() > 0) {
                playTierSound(entity, packet.getTier(), true);
                spawnBurst(minecraft, entity, packet.getTier() + 2);
            }
            return;
        }
        if (!packet.isActive()) {
            REMOTE_STATES.remove(entityId);
            return;
        }
        if (packet.getTier() > previousTier) {
            playTierSound(entity, packet.getTier(), false);
            spawnBurst(minecraft, entity, packet.getTier() + 1);
        }
        REMOTE_STATES.put(entityId,
                new RemoteChargeState(packet.getTier()));
    }

    private static void updateRemoteParticles(Minecraft minecraft) {
        Iterator<Map.Entry<Integer, RemoteChargeState>> iterator =
                REMOTE_STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, RemoteChargeState> entry = iterator.next();
            Entity entity = minecraft.theWorld.getEntityByID(
                    entry.getKey().intValue());
            RemoteChargeState state = entry.getValue();
            if (!(entity instanceof EntityLivingBase)
                    || !entity.isEntityAlive()) {
                iterator.remove();
                continue;
            }
            if (!LostTalesThirdPersonConfig.enableChargeTierParticles
                    || state.tier <= 0) {
                continue;
            }
            state.particleTicker++;
            int interval = Math.max(2, 6 - state.tier);
            if (state.particleTicker % interval == 0) {
                spawnChargeParticle(
                        minecraft, (EntityLivingBase)entity);
            }
        }
    }

    private static final class RemoteChargeState {
        private final int tier;
        private int particleTicker;

        private RemoteChargeState(int tier) {
            this.tier = tier;
        }
    }
}
