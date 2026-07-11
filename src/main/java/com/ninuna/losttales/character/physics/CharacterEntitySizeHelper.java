package com.ninuna.losttales.character.physics;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;

import java.lang.reflect.Method;
import java.util.List;

/** Applies and re-anchors an entity size through vanilla's protected setSize. */
public final class CharacterEntitySizeHelper {

    private static final float SIZE_EPSILON = 0.001F;
    private static final double POSITION_EPSILON = 0.001D;

    private static Method setSizeMethod;
    private static boolean lookupAttempted;
    private static boolean failureLogged;

    private CharacterEntitySizeHelper() {}

    /**
     * Applies the requested dimensions without changing the entity's logical
     * position. In 1.7.10 Entity#posY is not the bottom of a player's box:
     * setPosition anchors minY at posY - yOffset + ySize. All alignment and
     * collision checks therefore use that same vanilla formula.
     */
    public static boolean apply(Entity entity, float width, float height) {
        if (entity == null || width <= 0.0F || height <= 0.0F) {
            return false;
        }

        boolean sizeChanged = Math.abs(entity.width - width) >= SIZE_EPSILON
                || Math.abs(entity.height - height) >= SIZE_EPSILON;
        if (sizeChanged && isExpansionBlocked(entity, width, height)) {
            return false;
        }

        if (sizeChanged) {
            Method method = resolveSetSizeMethod();
            if (method == null) {
                logFailureOnce("setSize method was not found");
                return false;
            }
            try {
                method.invoke(entity, Float.valueOf(width), Float.valueOf(height));
            } catch (Exception exception) {
                logFailureOnce(exception.toString());
                return false;
            }
        }

        if (sizeChanged || !isBoundingBoxAligned(entity, width, height)) {
            // setPosition rebuilds the AABB using vanilla's yOffset/ySize
            // anchoring without changing posX/posY/posZ.
            entity.setPosition(entity.posX, entity.posY, entity.posZ);
        }
        return isBoundingBoxAligned(entity, width, height);
    }

    private static boolean isExpansionBlocked(Entity entity, float width, float height) {
        if (entity.noClip || entity.worldObj == null
                || (width <= entity.width + SIZE_EPSILON
                && height <= entity.height + SIZE_EPSILON)) {
            return false;
        }

        double halfWidth = width / 2.0D;
        double feetY = getBoundingBoxBaseY(entity);
        AxisAlignedBB desired = AxisAlignedBB.getBoundingBox(
                entity.posX - halfWidth + POSITION_EPSILON,
                feetY + POSITION_EPSILON,
                entity.posZ - halfWidth + POSITION_EPSILON,
                entity.posX + halfWidth - POSITION_EPSILON,
                feetY + height - POSITION_EPSILON,
                entity.posZ + halfWidth - POSITION_EPSILON);
        @SuppressWarnings("unchecked")
        List<AxisAlignedBB> collisions = entity.worldObj.getCollidingBoundingBoxes(
                entity, desired);
        return collisions != null && !collisions.isEmpty();
    }

    private static boolean isBoundingBoxAligned(
            Entity entity, float width, float height) {
        AxisAlignedBB box = entity.boundingBox;
        if (box == null) {
            return false;
        }
        double halfWidth = width / 2.0D;
        double feetY = getBoundingBoxBaseY(entity);
        return close(box.minX, entity.posX - halfWidth)
                && close(box.maxX, entity.posX + halfWidth)
                && close(box.minY, feetY)
                && close(box.maxY, feetY + height)
                && close(box.minZ, entity.posZ - halfWidth)
                && close(box.maxZ, entity.posZ + halfWidth);
    }

    static double getBoundingBoxBaseY(Entity entity) {
        return entity.posY - (double)entity.yOffset + (double)entity.ySize;
    }

    private static boolean close(double left, double right) {
        return Math.abs(left - right) < POSITION_EPSILON;
    }

    private static synchronized Method resolveSetSizeMethod() {
        if (lookupAttempted) {
            return setSizeMethod;
        }
        lookupAttempted = true;
        String[] methodNames = {"setSize", "func_70105_a"};
        for (String methodName : methodNames) {
            try {
                Method method = Entity.class.getDeclaredMethod(
                        methodName, Float.TYPE, Float.TYPE);
                method.setAccessible(true);
                setSizeMethod = method;
                break;
            } catch (Exception ignored) {
                // Try the next deobfuscated/SRG name.
            }
        }
        return setSizeMethod;
    }

    private static synchronized void logFailureOnce(String reason) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        FMLLog.warning("[%s] Could not apply roleplay-character hitboxes: %s",
                LostTalesMetaData.MOD_ID, reason);
    }
}
