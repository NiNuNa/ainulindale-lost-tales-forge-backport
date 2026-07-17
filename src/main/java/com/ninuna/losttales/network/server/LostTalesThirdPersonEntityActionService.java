package com.ninuna.losttales.network.server;

import com.ninuna.losttales.network.packet.LostTalesThirdPersonEntityActionPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import cpw.mods.fml.common.eventhandler.Event;

import java.util.List;

/** Server-thread authority boundary for offset-camera entity actions. */
public final class LostTalesThirdPersonEntityActionService {
    private static final double SURVIVAL_ENTITY_REACH = 3.0D;
    private static final double CREATIVE_ENTITY_REACH = 6.0D;
    private static final double REACH_TOLERANCE = 0.25D;
    // Client entity interpolation can trail the authoritative position by
    // several ticks. This is only an aim-point tolerance; current server-side
    // target reach is checked separately without it.
    private static final double TARGET_POSITION_TOLERANCE = 0.50D;
    private static final double ENTITY_ORDER_TOLERANCE_SQUARED = 0.01D;
    private static final double BLOCK_ORDER_TOLERANCE_SQUARED = 0.0001D;
    private static final double ENTITY_SEARCH_PADDING = 1.0D;

    private LostTalesThirdPersonEntityActionService() {}

    public static void execute(
            EntityPlayerMP player, int expectedDimension,
            LostTalesThirdPersonEntityActionPacket.Action action,
            int entityId, double hitX, double hitY, double hitZ,
            boolean useItemIfInteractionDeclines,
            int expectedHotbarSlot, ItemStack expectedHeldItem) {
        if (!hasUsablePlayerContext(player, expectedDimension)
                || action == null || !isFinitePoint(hitX, hitY, hitZ)
                || !hasExpectedHeldItem(
                player, expectedHotbarSlot, expectedHeldItem)) {
            return;
        }

        Entity target = player.worldObj.getEntityByID(entityId);
        if (!isUsableTarget(player, target)
                || action
                == LostTalesThirdPersonEntityActionPacket.Action.ATTACK
                && !isUsableAttackTarget(target)) {
            return;
        }

        Vec3 eye = Vec3.createVectorHelper(
                player.posX,
                player.posY + (double)player.getEyeHeight(),
                player.posZ);
        Vec3 hit = Vec3.createVectorHelper(hitX, hitY, hitZ);
        double reach = player.theItemInWorldManager.isCreative()
                ? CREATIVE_ENTITY_REACH : SURVIVAL_ENTITY_REACH;
        if (!isWithinExpandedBounds(
                target.boundingBox, target.getCollisionBorderSize(),
                hitX, hitY, hitZ)
                || !isWithinReach(eye, hit, reach)
                || !isCurrentTargetWithinReach(
                target.boundingBox, target.getCollisionBorderSize(),
                eye, reach)
                || isBlockedByWorld(player, eye, hit)
                || !isFirstEntityAlongRay(player, target, eye, hit)) {
            return;
        }

        int selectedSlotAtExecution = player.inventory.currentItem;
        player.inventory.currentItem = expectedHotbarSlot;
        try {
            player.func_143004_u();
            if (action
                    == LostTalesThirdPersonEntityActionPacket.Action.ATTACK) {
                player.attackTargetEntityWithCurrentItem(target);
            } else {
                boolean handled = player.interactWith(target);
                if (!handled && useItemIfInteractionDeclines) {
                    useHeldItem(player);
                }
            }
        } finally {
            if (selectedSlotAtExecution != expectedHotbarSlot
                    && player.inventory.currentItem == expectedHotbarSlot) {
                player.inventory.currentItem = selectedSlotAtExecution;
            }
        }
    }

    private static boolean hasUsablePlayerContext(
            EntityPlayerMP player, int expectedDimension) {
        return player != null && player.worldObj != null
                && !player.worldObj.isRemote
                && player.worldObj.provider != null
                && player.worldObj.provider.dimensionId == expectedDimension
                && player.isEntityAlive() && !player.isDead
                && !player.isPlayerSleeping()
                && player.openContainer == player.inventoryContainer;
    }

    private static boolean isUsableTarget(
            EntityPlayerMP player, Entity target) {
        if (target == null || target == player || target.isDead
                || target.worldObj != player.worldObj
                || target.boundingBox == null
                || !target.canBeCollidedWith()) {
            return false;
        }
        if (target == player.ridingEntity && !target.canRiderInteract()) {
            return false;
        }
        return true;
    }

    private static boolean isUsableAttackTarget(Entity target) {
        return !(target instanceof EntityItem)
                && !(target instanceof EntityXPOrb)
                && !(target instanceof EntityArrow);
    }

    static boolean heldItemMatches(
            ItemStack expected, ItemStack current) {
        return ItemStack.areItemStacksEqual(expected, current);
    }

    private static boolean hasExpectedHeldItem(
            EntityPlayerMP player, int expectedHotbarSlot,
            ItemStack expectedHeldItem) {
        return player != null && expectedHotbarSlot >= 0
                && expectedHotbarSlot < 9
                && expectedHotbarSlot
                < player.inventory.mainInventory.length
                && heldItemMatches(
                expectedHeldItem,
                player.inventory.mainInventory[expectedHotbarSlot]);
    }

    private static void useHeldItem(EntityPlayerMP player) {
        ItemStack itemStack = player.inventory.getCurrentItem();
        if (itemStack == null) {
            return;
        }
        PlayerInteractEvent event = ForgeEventFactory.onPlayerInteract(
                player, PlayerInteractEvent.Action.RIGHT_CLICK_AIR,
                0, 0, 0, -1, player.worldObj);
        if (event.useItem != Event.Result.DENY) {
            player.theItemInWorldManager.tryUseItem(
                    player, player.worldObj, itemStack);
        }
    }

    static boolean isFinitePoint(double x, double y, double z) {
        return isFinite(x) && isFinite(y) && isFinite(z);
    }

    static boolean isWithinExpandedBounds(
            AxisAlignedBB bounds, float collisionBorder,
            double x, double y, double z) {
        if (bounds == null || !isFinitePoint(x, y, z)) {
            return false;
        }
        double expansion = Math.max(0.0D, collisionBorder)
                + TARGET_POSITION_TOLERANCE;
        return x >= bounds.minX - expansion
                && x <= bounds.maxX + expansion
                && y >= bounds.minY - expansion
                && y <= bounds.maxY + expansion
                && z >= bounds.minZ - expansion
                && z <= bounds.maxZ + expansion;
    }

    static boolean isWithinReach(Vec3 eye, Vec3 hit, double reach) {
        if (eye == null || hit == null || !isFinitePoint(
                eye.xCoord, eye.yCoord, eye.zCoord)
                || !isFinitePoint(hit.xCoord, hit.yCoord, hit.zCoord)
                || !isFinite(reach) || reach < 0.0D) {
            return false;
        }
        double permitted = reach + REACH_TOLERANCE;
        return eye.squareDistanceTo(hit) <= permitted * permitted;
    }

    static boolean isCurrentTargetWithinReach(
            AxisAlignedBB bounds, float collisionBorder,
            Vec3 eye, double reach) {
        if (bounds == null || eye == null
                || !isFinitePoint(
                eye.xCoord, eye.yCoord, eye.zCoord)
                || !isFinite(reach) || reach < 0.0D) {
            return false;
        }
        double border = Math.max(0.0D, collisionBorder);
        AxisAlignedBB expanded = bounds.expand(border, border, border);
        double closestX = clamp(
                eye.xCoord, expanded.minX, expanded.maxX);
        double closestY = clamp(
                eye.yCoord, expanded.minY, expanded.maxY);
        double closestZ = clamp(
                eye.zCoord, expanded.minZ, expanded.maxZ);
        double dx = closestX - eye.xCoord;
        double dy = closestY - eye.yCoord;
        double dz = closestZ - eye.zCoord;
        double permitted = reach + REACH_TOLERANCE;
        return dx * dx + dy * dy + dz * dz
                <= permitted * permitted;
    }

    private static boolean isBlockedByWorld(
            EntityPlayerMP player, Vec3 eye, Vec3 hit) {
        MovingObjectPosition blockHit = player.worldObj.func_147447_a(
                eye, hit, false, false, true);
        return blockHit != null && blockHit.hitVec != null
                && eye.squareDistanceTo(blockHit.hitVec)
                + BLOCK_ORDER_TOLERANCE_SQUARED
                < eye.squareDistanceTo(hit);
    }

    @SuppressWarnings("unchecked")
    private static boolean isFirstEntityAlongRay(
            EntityPlayerMP player, Entity target, Vec3 eye, Vec3 hit) {
        double targetDistance = interceptDistanceSquared(
                target, eye, hit, TARGET_POSITION_TOLERANCE);
        if (!isFinite(targetDistance)) {
            return false;
        }

        AxisAlignedBB sweep = AxisAlignedBB.getBoundingBox(
                Math.min(eye.xCoord, hit.xCoord),
                Math.min(eye.yCoord, hit.yCoord),
                Math.min(eye.zCoord, hit.zCoord),
                Math.max(eye.xCoord, hit.xCoord),
                Math.max(eye.yCoord, hit.yCoord),
                Math.max(eye.zCoord, hit.zCoord)).expand(
                ENTITY_SEARCH_PADDING,
                ENTITY_SEARCH_PADDING,
                ENTITY_SEARCH_PADDING);
        List<Entity> entities = player.worldObj
                .getEntitiesWithinAABBExcludingEntity(player, sweep);
        for (Entity candidate : entities) {
            if (candidate == null || candidate == target
                    || candidate.isDead || candidate.boundingBox == null
                    || !candidate.canBeCollidedWith()
                    || candidate == player.ridingEntity
                    && !candidate.canRiderInteract()) {
                continue;
            }
            double distance = interceptDistanceSquared(
                    candidate, eye, hit, 0.0D);
            if (isFinite(distance)
                    && distance
                    + ENTITY_ORDER_TOLERANCE_SQUARED
                    < targetDistance) {
                return false;
            }
        }
        return true;
    }

    private static double interceptDistanceSquared(
            Entity entity, Vec3 start, Vec3 end,
            double additionalExpansion) {
        if (entity == null || entity.boundingBox == null) {
            return Double.NaN;
        }
        double border = Math.max(0.0F, entity.getCollisionBorderSize())
                + Math.max(0.0D, additionalExpansion);
        AxisAlignedBB bounds = entity.boundingBox.expand(
                border, border, border);
        if (bounds.isVecInside(start)) {
            return 0.0D;
        }
        MovingObjectPosition intercept =
                bounds.calculateIntercept(start, end);
        return intercept == null || intercept.hitVec == null
                ? Double.NaN
                : start.squareDistanceTo(intercept.hitVec);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
