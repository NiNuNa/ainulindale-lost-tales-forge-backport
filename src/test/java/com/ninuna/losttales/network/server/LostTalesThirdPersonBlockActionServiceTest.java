package com.ninuna.losttales.network.server;

import net.minecraft.util.Vec3;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesThirdPersonBlockActionServiceTest {

    @Test
    public void reachHasOneQuarterBlockMovementTolerance() {
        Vec3 eye = Vec3.createVectorHelper(0.0D, 0.0D, 0.0D);
        assertTrue(LostTalesThirdPersonBlockActionService.isWithinReach(
                eye, Vec3.createVectorHelper(5.25D, 0.0D, 0.0D),
                5.0D));
        assertFalse(LostTalesThirdPersonBlockActionService.isWithinReach(
                eye, Vec3.createVectorHelper(5.26D, 0.0D, 0.0D),
                5.0D));
        assertFalse(LostTalesThirdPersonBlockActionService.isWithinReach(
                eye, Vec3.createVectorHelper(Double.NaN, 0.0D, 0.0D),
                5.0D));
    }

    @Test
    public void rayEndpointExtendsThroughClaimedFace() {
        Vec3 hit = Vec3.createVectorHelper(1.0D, 2.0D, 3.0D);
        Vec3 throughTop = LostTalesThirdPersonBlockActionService
                .extendIntoBlock(hit, 1);
        assertEquals(1.0D, throughTop.xCoord, 0.0D);
        assertEquals(1.99D, throughTop.yCoord, 0.000001D);
        assertEquals(3.0D, throughTop.zCoord, 0.0D);

        Vec3 throughWest = LostTalesThirdPersonBlockActionService
                .extendIntoBlock(hit, 4);
        assertEquals(1.01D, throughWest.xCoord, 0.000001D);
        assertEquals(2.0D, throughWest.yCoord, 0.0D);
        assertEquals(3.0D, throughWest.zCoord, 0.0D);
    }
}
