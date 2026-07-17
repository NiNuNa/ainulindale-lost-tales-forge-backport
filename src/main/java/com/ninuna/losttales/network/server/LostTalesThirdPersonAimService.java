package com.ninuna.losttales.network.server;

import com.ninuna.losttales.gameplay.projectile.ThirdPersonProjectileItemPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;

/** Server-owned, short-lived projectile aim intent for offset cameras. */
public final class LostTalesThirdPersonAimService {
    private static final double MINIMUM_DIRECTION_LENGTH = 0.90D;
    private static final double MAXIMUM_DIRECTION_LENGTH = 1.10D;
    private static final double MINIMUM_LOOK_DOT = 0.7071067811865476D;
    private static final double MINIMUM_PROJECTILE_SPEED = 0.0001D;
    private static final long MAXIMUM_AIM_AGE_TICKS = 10L;

    private static final Map<UUID, AimState> AIM_STATES =
            new HashMap<UUID, AimState>();

    private LostTalesThirdPersonAimService() {}

    public static synchronized void execute(
            EntityPlayerMP player, int expectedDimension,
            boolean active, double directionX,
            double directionY, double directionZ,
            int expectedHotbarSlot, Item expectedItem) {
        if (player == null || player.getUniqueID() == null) {
            return;
        }
        if (!active) {
            clearPlayer(player.getUniqueID());
            return;
        }
        if (!hasUsablePlayerContext(player, expectedDimension)
                || expectedHotbarSlot < 0 || expectedHotbarSlot >= 9
                || expectedHotbarSlot
                >= player.inventory.mainInventory.length) {
            clearPlayer(player.getUniqueID());
            return;
        }

        ItemStack held = player.inventory
                .mainInventory[expectedHotbarSlot];
        Vec3 requested = normalizeDirection(
                directionX, directionY, directionZ);
        Vec3 look = player.getLookVec();
        if (player.inventory.currentItem != expectedHotbarSlot
                || held == null || held.getItem() != expectedItem
                || !ThirdPersonProjectileItemPolicy.isSupported(held)
                || requested == null
                || !isDirectionAllowed(requested, look)) {
            clearPlayer(player.getUniqueID());
            return;
        }

        AIM_STATES.put(player.getUniqueID(), new AimState(
                expectedDimension,
                player.worldObj.getTotalWorldTime(),
                expectedHotbarSlot, expectedItem, requested));
    }

    public static synchronized boolean applyAim(
            Entity projectile, EntityPlayerMP shooter) {
        if (projectile == null || shooter == null
                || shooter.getUniqueID() == null
                || projectile.worldObj == null
                || projectile.worldObj != shooter.worldObj
                || projectile.ticksExisted != 0
                || projectile.getDistanceSqToEntity(shooter) > 16.0D) {
            return false;
        }
        AimState state = AIM_STATES.get(shooter.getUniqueID());
        if (!isCurrent(state, shooter)) {
            return false;
        }

        Vec3 redirected = redirectMotion(
                projectile.motionX, projectile.motionY,
                projectile.motionZ, state.direction);
        if (redirected == null) {
            return false;
        }
        projectile.motionX = redirected.xCoord;
        projectile.motionY = redirected.yCoord;
        projectile.motionZ = redirected.zCoord;
        double horizontal = Math.sqrt(
                redirected.xCoord * redirected.xCoord
                        + redirected.zCoord * redirected.zCoord);
        projectile.rotationYaw = (float)(Math.atan2(
                redirected.xCoord, redirected.zCoord)
                * 180.0D / Math.PI);
        projectile.rotationPitch = (float)(Math.atan2(
                redirected.yCoord, horizontal)
                * 180.0D / Math.PI);
        projectile.prevRotationYaw = projectile.rotationYaw;
        projectile.prevRotationPitch = projectile.rotationPitch;
        projectile.velocityChanged = true;
        return true;
    }

    private static boolean isCurrent(
            AimState state, EntityPlayerMP shooter) {
        if (state == null || shooter.worldObj.provider == null
                || shooter.worldObj.provider.dimensionId
                != state.dimension
                || shooter.inventory.currentItem != state.hotbarSlot) {
            return false;
        }
        long age = shooter.worldObj.getTotalWorldTime()
                - state.receivedAtWorldTick;
        if (age < 0L || age > MAXIMUM_AIM_AGE_TICKS) {
            return false;
        }
        ItemStack held = shooter.inventory.getCurrentItem();
        return held != null && held.getItem() == state.item
                && ThirdPersonProjectileItemPolicy.isSupported(held)
                && isDirectionAllowed(
                state.direction, shooter.getLookVec());
    }

    private static boolean hasUsablePlayerContext(
            EntityPlayerMP player, int expectedDimension) {
        return player.worldObj != null && !player.worldObj.isRemote
                && player.worldObj.provider != null
                && player.worldObj.provider.dimensionId == expectedDimension
                && player.isEntityAlive() && !player.isDead
                && !player.isPlayerSleeping()
                && player.openContainer == player.inventoryContainer;
    }

    static Vec3 normalizeDirection(double x, double y, double z) {
        if (!isFinite(x) || !isFinite(y) || !isFinite(z)) {
            return null;
        }
        double length = Math.sqrt(x * x + y * y + z * z);
        if (!isFinite(length)
                || length < MINIMUM_DIRECTION_LENGTH
                || length > MAXIMUM_DIRECTION_LENGTH) {
            return null;
        }
        return Vec3.createVectorHelper(
                x / length, y / length, z / length);
    }

    static boolean isDirectionAllowed(Vec3 requested, Vec3 serverLook) {
        if (requested == null || serverLook == null) {
            return false;
        }
        Vec3 normalizedLook = normalizeAnyDirection(serverLook);
        return normalizedLook != null
                && requested.dotProduct(normalizedLook)
                >= MINIMUM_LOOK_DOT;
    }

    static Vec3 redirectMotion(
            double motionX, double motionY, double motionZ,
            Vec3 direction) {
        if (direction == null || !isFinite(motionX)
                || !isFinite(motionY) || !isFinite(motionZ)) {
            return null;
        }
        double speed = Math.sqrt(
                motionX * motionX + motionY * motionY
                        + motionZ * motionZ);
        if (!isFinite(speed) || speed < MINIMUM_PROJECTILE_SPEED) {
            return null;
        }
        return Vec3.createVectorHelper(
                direction.xCoord * speed,
                direction.yCoord * speed,
                direction.zCoord * speed);
    }

    private static Vec3 normalizeAnyDirection(Vec3 vector) {
        if (!isFinite(vector.xCoord) || !isFinite(vector.yCoord)
                || !isFinite(vector.zCoord)) {
            return null;
        }
        double length = vector.lengthVector();
        return !isFinite(length) || length < 0.000001D
                ? null : Vec3.createVectorHelper(
                vector.xCoord / length,
                vector.yCoord / length,
                vector.zCoord / length);
    }

    public static synchronized void clearPlayer(UUID playerId) {
        if (playerId != null) {
            AIM_STATES.remove(playerId);
        }
    }

    public static synchronized void clear() {
        AIM_STATES.clear();
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static final class AimState {
        private final int dimension;
        private final long receivedAtWorldTick;
        private final int hotbarSlot;
        private final Item item;
        private final Vec3 direction;

        private AimState(
                int dimension, long receivedAtWorldTick,
                int hotbarSlot, Item item, Vec3 direction) {
            this.dimension = dimension;
            this.receivedAtWorldTick = receivedAtWorldTick;
            this.hotbarSlot = hotbarSlot;
            this.item = item;
            this.direction = direction;
        }
    }
}
