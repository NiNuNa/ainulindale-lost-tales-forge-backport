package com.ninuna.losttales.client.camera;

import net.minecraft.util.MovingObjectPosition;

/** Immutable snapshot of the target vanilla currently permits the client to use. */
public final class ThirdPersonTargetingResult {
    private final MovingObjectPosition target;
    private final double eyeX;
    private final double eyeY;
    private final double eyeZ;
    private final double aimX;
    private final double aimY;
    private final double aimZ;
    private final double reach;
    private final boolean targetWithinReach;

    ThirdPersonTargetingResult(
            MovingObjectPosition target,
            double eyeX, double eyeY, double eyeZ,
            double aimX, double aimY, double aimZ,
            double reach, boolean targetWithinReach) {
        this.target = target;
        this.eyeX = eyeX;
        this.eyeY = eyeY;
        this.eyeZ = eyeZ;
        this.aimX = aimX;
        this.aimY = aimY;
        this.aimZ = aimZ;
        this.reach = reach;
        this.targetWithinReach = targetWithinReach;
    }

    public MovingObjectPosition getTarget() {
        return target;
    }

    public double getEyeX() {
        return eyeX;
    }

    public double getEyeY() {
        return eyeY;
    }

    public double getEyeZ() {
        return eyeZ;
    }

    public double getAimX() {
        return aimX;
    }

    public double getAimY() {
        return aimY;
    }

    public double getAimZ() {
        return aimZ;
    }

    public double getReach() {
        return reach;
    }

    public boolean hasTargetWithinReach() {
        return targetWithinReach;
    }
}
