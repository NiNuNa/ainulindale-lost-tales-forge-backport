package com.ninuna.losttales.network.server;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesThirdPersonEntityActionServiceTest {

    @Test
    public void rejectsNonFiniteCoordinates() {
        assertFalse(LostTalesThirdPersonEntityActionService.isFinitePoint(
                Double.NaN, 0.0D, 0.0D));
        assertFalse(LostTalesThirdPersonEntityActionService.isFinitePoint(
                0.0D, Double.POSITIVE_INFINITY, 0.0D));
        assertTrue(LostTalesThirdPersonEntityActionService.isFinitePoint(
                0.0D, 64.0D, -12.0D));
    }

    @Test
    public void boundsPermitOnlyCollisionBorderAndInterpolationTolerance() {
        AxisAlignedBB bounds = AxisAlignedBB.getBoundingBox(
                0.0D, 0.0D, 0.0D,
                1.0D, 2.0D, 1.0D);
        assertTrue(LostTalesThirdPersonEntityActionService
                .isWithinExpandedBounds(
                        bounds, 0.1F, 1.59D, 1.0D, 0.5D));
        assertFalse(LostTalesThirdPersonEntityActionService
                .isWithinExpandedBounds(
                        bounds, 0.1F, 1.61D, 1.0D, 0.5D));
    }

    @Test
    public void reachHasOneQuarterBlockMovementTolerance() {
        Vec3 eye = Vec3.createVectorHelper(0.0D, 0.0D, 0.0D);
        assertTrue(LostTalesThirdPersonEntityActionService.isWithinReach(
                eye, Vec3.createVectorHelper(3.25D, 0.0D, 0.0D),
                3.0D));
        assertFalse(LostTalesThirdPersonEntityActionService.isWithinReach(
                eye, Vec3.createVectorHelper(3.26D, 0.0D, 0.0D),
                3.0D));
    }

    @Test
    public void currentServerTargetMustStillBeWithinMeleeReach() {
        Vec3 eye = Vec3.createVectorHelper(0.0D, 1.0D, 0.0D);
        AxisAlignedBB reachable = AxisAlignedBB.getBoundingBox(
                3.30D, 0.0D, -0.5D,
                3.90D, 2.0D, 0.5D);
        AxisAlignedBB tooFar = AxisAlignedBB.getBoundingBox(
                3.36D, 0.0D, -0.5D,
                3.96D, 2.0D, 0.5D);
        assertTrue(LostTalesThirdPersonEntityActionService
                .isCurrentTargetWithinReach(
                        reachable, 0.1F, eye, 3.0D));
        assertFalse(LostTalesThirdPersonEntityActionService
                .isCurrentTargetWithinReach(
                        tooFar, 0.1F, eye, 3.0D));
    }
}
