package com.ninuna.losttales.network.server;

import net.minecraft.util.Vec3;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LostTalesThirdPersonAimServiceTest {
    private static final double EPSILON = 0.0000001D;

    @Test
    public void directionMustBeFiniteAndApproximatelyNormalized() {
        Vec3 normalized = LostTalesThirdPersonAimService
                .normalizeDirection(0.0D, 0.0D, 1.05D);
        assertEquals(1.0D, normalized.lengthVector(), EPSILON);
        assertNull(LostTalesThirdPersonAimService
                .normalizeDirection(0.0D, 0.0D, 0.5D));
        assertNull(LostTalesThirdPersonAimService
                .normalizeDirection(Double.NaN, 0.0D, 1.0D));
    }

    @Test
    public void aimMustRemainInsideServerLookCone() {
        Vec3 forward = Vec3.createVectorHelper(0.0D, 0.0D, 1.0D);
        assertTrue(LostTalesThirdPersonAimService.isDirectionAllowed(
                Vec3.createVectorHelper(0.7071068D, 0.0D, 0.7071068D),
                forward));
        assertFalse(LostTalesThirdPersonAimService.isDirectionAllowed(
                Vec3.createVectorHelper(1.0D, 0.0D, 0.0D), forward));
        assertFalse(LostTalesThirdPersonAimService.isDirectionAllowed(
                Vec3.createVectorHelper(0.0D, 0.0D, -1.0D), forward));
    }

    @Test
    public void redirectionPreservesProjectileSpeed() {
        Vec3 redirected = LostTalesThirdPersonAimService.redirectMotion(
                0.0D, 0.0D, 2.5D,
                Vec3.createVectorHelper(0.6D, 0.0D, 0.8D));
        assertEquals(2.5D, redirected.lengthVector(), EPSILON);
        assertEquals(1.5D, redirected.xCoord, EPSILON);
        assertEquals(2.0D, redirected.zCoord, EPSILON);
    }
}
