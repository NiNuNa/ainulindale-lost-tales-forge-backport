package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import org.junit.Test;

public final class ProjectileLaunchGeometryTest {
    private static final double EPSILON = 0.0000001D;

    @Test
    public void physicalOriginMatchesVanillaAndLotrConstructors() {
        TargetingVector eye = vector(10.0D, 20.0D, 30.0D);

        assertVector(ProjectileLaunchGeometry.resolvePhysicalOrigin(
                eye, 0.0D), 9.84D, 19.90D, 30.0D);
        assertVector(ProjectileLaunchGeometry.resolvePhysicalOrigin(
                eye, 90.0D), 10.0D, 19.90D, 29.84D);
    }

    @Test
    public void bowGuideStartsAtArrowTipAndSmoothlyJoinsPhysicalArc() {
        TargetingVector physical = vector(9.84D, 19.90D, 30.0D);
        TargetingVector visual = ProjectileLaunchGeometry
                .resolveVisualOrigin(
                new ItemStack(new ItemBow()), physical,
                vector(0.0D, 0.0D, 1.0D), 0.0D, 1.0D);

        assertVector(visual, 9.58D, 19.68D, 30.48D);

        TargetingVector firstPhysicalStep = vector(
                9.84D, 19.90D, 33.0D);
        TargetingVector secondPhysicalStep = vector(
                9.84D, 19.85D, 35.97D);
        List<TargetingVector> rendered = ProjectileLaunchGeometry
                .useVisualOrigin(Arrays.asList(
                physical, firstPhysicalStep, secondPhysicalStep),
                visual, 4.0D);

        assertEquals(3, rendered.size());
        assertVector(rendered.get(0), 9.58D, 19.68D, 30.48D);
        assertTrue(rendered.get(1).distanceSquared(firstPhysicalStep)
                < visual.distanceSquared(physical));
        assertVector(rendered.get(2), 9.84D, 19.85D, 35.97D);
    }

    @Test
    public void shortTrajectoryStillEndsAtPhysicalCollision() {
        TargetingVector physical = vector(0.0D, 1.5D, 0.0D);
        TargetingVector visual = vector(-0.3D, 1.2D, 0.4D);
        TargetingVector collision = vector(0.0D, 1.4D, 1.0D);

        List<TargetingVector> rendered = ProjectileLaunchGeometry
                .useVisualOrigin(Arrays.asList(
                physical, collision), visual, 3.0D);

        assertVector(rendered.get(0), -0.3D, 1.2D, 0.4D);
        assertVector(rendered.get(1), 0.0D, 1.4D, 1.0D);
    }

    @Test
    public void yawInterpolationUsesTheShortestWrappedPath() {
        assertEquals(-180.0D,
                ProjectileLaunchGeometry.interpolateYaw(
                        170.0D, -170.0D, 0.5D), EPSILON);
    }

    private static TargetingVector vector(
            double x, double y, double z) {
        return new TargetingVector(x, y, z);
    }

    private static void assertVector(
            TargetingVector actual, double x, double y, double z) {
        assertEquals(x, actual.getX(), EPSILON);
        assertEquals(y, actual.getY(), EPSILON);
        assertEquals(z, actual.getZ(), EPSILON);
    }
}
