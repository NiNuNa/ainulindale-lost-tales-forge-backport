package com.ninuna.losttales.client.camera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lotr.common.item.LOTRItemBlowgun;
import lotr.common.item.LOTRItemBow;
import lotr.common.item.LOTRItemCrossbow;
import lotr.common.item.LOTRItemSpear;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;

/**
 * Keeps the real projectile spawn point separate from the rendered weapon tip.
 * Vanilla arrows, throwables, and LOTR projectiles all spawn 0.1 blocks below
 * the eye and 0.16 blocks toward the shooter's right-hand side.
 */
public final class ProjectileLaunchGeometry {
    private static final double PHYSICAL_SIDE_OFFSET = 0.16D;
    private static final double PHYSICAL_VERTICAL_OFFSET = -0.10D;

    private static final HeldOffsets BOW_OFFSETS =
            new HeldOffsets(0.26D, -0.22D, 0.48D);
    private static final HeldOffsets CROSSBOW_OFFSETS =
            new HeldOffsets(0.18D, -0.28D, 0.65D);
    private static final HeldOffsets SPEAR_OFFSETS =
            new HeldOffsets(0.30D, -0.35D, 0.90D);
    private static final HeldOffsets BLOWGUN_OFFSETS =
            new HeldOffsets(-0.08D, -0.08D, 0.62D);
    private static final HeldOffsets THROWABLE_OFFSETS =
            new HeldOffsets(0.26D, -0.35D, 0.28D);

    private ProjectileLaunchGeometry() {}

    public static TargetingVector resolvePhysicalOrigin(
            TargetingVector eyeOrigin, double shooterYawDegrees) {
        require(eyeOrigin, "eye origin");
        TargetingVector right = rightDirection(shooterYawDegrees);
        return eyeOrigin.add(right.scale(PHYSICAL_SIDE_OFFSET)).add(
                new TargetingVector(
                        0.0D, PHYSICAL_VERTICAL_OFFSET, 0.0D));
    }

    public static TargetingVector resolveVisualOrigin(
            ItemStack heldItem, TargetingVector physicalOrigin,
            TargetingVector aimDirection, double shooterYawDegrees,
            double modelScale) {
        require(physicalOrigin, "physical origin");
        require(aimDirection, "aim direction");
        CameraMath.requireNonNegativeFinite("modelScale", modelScale);
        if (modelScale <= 0.0D) {
            throw new IllegalArgumentException(
                    "modelScale must be positive");
        }

        HeldOffsets offsets = resolveOffsets(heldItem);
        TargetingVector forward = aimDirection.normalizeOr(
                new TargetingVector(0.0D, 0.0D, 1.0D));
        TargetingVector right = rightDirection(shooterYawDegrees);
        return physicalOrigin
                .add(right.scale(offsets.side * modelScale))
                .add(new TargetingVector(
                        0.0D, offsets.vertical * modelScale, 0.0D))
                .add(forward.scale(offsets.forward * modelScale));
    }

    /**
     * Replaces only the displayed first point. The remaining points retain the
     * exact physical spawn origin, velocity, gravity, drag, and collisions.
     */
    public static List<TargetingVector> useVisualOrigin(
            List<TargetingVector> physicalTrajectory,
            TargetingVector visualOrigin) {
        if (physicalTrajectory == null) {
            throw new IllegalArgumentException(
                    "physical trajectory is required");
        }
        require(visualOrigin, "visual origin");
        if (physicalTrajectory.isEmpty()) {
            return Collections.emptyList();
        }
        List<TargetingVector> rendered =
                new ArrayList<TargetingVector>(physicalTrajectory.size());
        rendered.add(visualOrigin);
        for (int index = 1; index < physicalTrajectory.size(); index++) {
            rendered.add(physicalTrajectory.get(index));
        }
        return Collections.unmodifiableList(rendered);
    }

    static double interpolateYaw(
            double previousYaw, double currentYaw, double partialTicks) {
        CameraMath.requireFinite("previousYaw", previousYaw);
        CameraMath.requireFinite("currentYaw", currentYaw);
        CameraMath.requireFinite("partialTicks", partialTicks);
        double partial = Math.max(0.0D, Math.min(1.0D, partialTicks));
        return CameraMath.wrapDegrees(previousYaw + CameraMath.wrapDegrees(
                currentYaw - previousYaw) * partial);
    }

    private static HeldOffsets resolveOffsets(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return THROWABLE_OFFSETS;
        }
        Item item = stack.getItem();
        if (item instanceof LOTRItemSpear
                || "LOTRItemThrowingAxe".equals(
                item.getClass().getSimpleName())) {
            return SPEAR_OFFSETS;
        }
        if (item instanceof LOTRItemBlowgun) {
            return BLOWGUN_OFFSETS;
        }
        if (item instanceof LOTRItemCrossbow) {
            return CROSSBOW_OFFSETS;
        }
        if (item instanceof ItemBow || item instanceof LOTRItemBow) {
            return BOW_OFFSETS;
        }
        return THROWABLE_OFFSETS;
    }

    private static TargetingVector rightDirection(double yawDegrees) {
        CameraMath.requireFinite("shooterYawDegrees", yawDegrees);
        double yawRadians = Math.toRadians(yawDegrees);
        return new TargetingVector(
                -Math.cos(yawRadians), 0.0D,
                -Math.sin(yawRadians));
    }

    private static void require(TargetingVector value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static final class HeldOffsets {
        private final double side;
        private final double vertical;
        private final double forward;

        private HeldOffsets(
                double side, double vertical, double forward) {
            this.side = side;
            this.vertical = vertical;
            this.forward = forward;
        }
    }
}
