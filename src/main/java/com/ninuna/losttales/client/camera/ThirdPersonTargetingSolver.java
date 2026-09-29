package com.ninuna.losttales.client.camera;

/** The reach vanilla permits for blocks and entities, as the camera's targeting reads it. */
public final class ThirdPersonTargetingSolver {
    private static final double DEFAULT_REACH = 3.0D;

    private ThirdPersonTargetingSolver() {}

    static double resolveEntityReach(
            double blockReach, boolean extendedReach) {
        double safeReach = sanitizeReach(blockReach);
        return extendedReach ? 6.0D : Math.min(safeReach, 3.0D);
    }

    static double sanitizeReach(double reach) {
        return !Double.isFinite(reach) || reach <= 0.0D
                ? DEFAULT_REACH : reach;
    }
}
