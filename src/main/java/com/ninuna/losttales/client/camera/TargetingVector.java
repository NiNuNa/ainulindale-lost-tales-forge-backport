package com.ninuna.losttales.client.camera;

/** Small immutable vector used by targeting, locking, and future prediction. */
public final class TargetingVector {
    private static final double MINIMUM_LENGTH_SQUARED = 0.000000000001D;

    private final double x;
    private final double y;
    private final double z;

    public TargetingVector(double x, double y, double z) {
        CameraMath.requireFinite("x", x);
        CameraMath.requireFinite("y", y);
        CameraMath.requireFinite("z", z);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public TargetingVector add(TargetingVector other) {
        require(other);
        return new TargetingVector(
                x + other.x, y + other.y, z + other.z);
    }

    public TargetingVector subtract(TargetingVector other) {
        require(other);
        return new TargetingVector(
                x - other.x, y - other.y, z - other.z);
    }

    public TargetingVector scale(double scale) {
        CameraMath.requireFinite("scale", scale);
        return new TargetingVector(x * scale, y * scale, z * scale);
    }

    public double dot(TargetingVector other) {
        require(other);
        return x * other.x + y * other.y + z * other.z;
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double distanceSquared(TargetingVector other) {
        return subtract(other).lengthSquared();
    }

    public TargetingVector normalizeOr(TargetingVector fallback) {
        double lengthSquared = lengthSquared();
        if (lengthSquared <= MINIMUM_LENGTH_SQUARED) {
            require(fallback);
            double fallbackLengthSquared = fallback.lengthSquared();
            if (fallbackLengthSquared <= MINIMUM_LENGTH_SQUARED) {
                throw new IllegalArgumentException(
                        "fallback direction must not be zero");
            }
            return fallback.scale(1.0D / Math.sqrt(
                    fallbackLengthSquared));
        }
        return scale(1.0D / Math.sqrt(lengthSquared));
    }

    private static void require(TargetingVector value) {
        if (value == null) {
            throw new IllegalArgumentException("vector is required");
        }
    }
}
