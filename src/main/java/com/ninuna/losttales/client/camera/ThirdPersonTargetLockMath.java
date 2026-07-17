package com.ninuna.losttales.client.camera;

/** Pure target-lock direction and angle calculations. */
public final class ThirdPersonTargetLockMath {
    private ThirdPersonTargetLockMath() {}

    public static double angleDegrees(
            TargetingVector origin, TargetingVector forward,
            TargetingVector target) {
        require(origin, "origin");
        require(forward, "forward");
        require(target, "target");
        TargetingVector normalizedForward = forward.normalizeOr(
                new TargetingVector(0.0D, 0.0D, 1.0D));
        TargetingVector direction = target.subtract(origin).normalizeOr(
                normalizedForward);
        double dot = Math.max(-1.0D, Math.min(
                1.0D, normalizedForward.dot(direction)));
        return Math.toDegrees(Math.acos(dot));
    }

    public static float resolveYaw(
            TargetingVector origin, TargetingVector target) {
        require(origin, "origin");
        require(target, "target");
        TargetingVector difference = target.subtract(origin);
        return DirectionalMovementMath.wrapDegrees((float)(
                Math.toDegrees(Math.atan2(
                difference.getZ(), difference.getX())) - 90.0D));
    }

    public static float resolvePitch(
            TargetingVector origin, TargetingVector target) {
        require(origin, "origin");
        require(target, "target");
        TargetingVector difference = target.subtract(origin);
        double horizontal = Math.sqrt(
                difference.getX() * difference.getX()
                        + difference.getZ() * difference.getZ());
        return (float)-Math.toDegrees(Math.atan2(
                difference.getY(), horizontal));
    }

    public static TargetingVector directionFromRotation(
            float yawDegrees, float pitchDegrees) {
        if (!Float.isFinite(yawDegrees)
                || !Float.isFinite(pitchDegrees)) {
            throw new IllegalArgumentException(
                    "rotation must be finite");
        }
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cosinePitch = Math.cos(pitch);
        return new TargetingVector(
                -Math.sin(yaw) * cosinePitch,
                -Math.sin(pitch),
                Math.cos(yaw) * cosinePitch);
    }

    private static void require(TargetingVector value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
