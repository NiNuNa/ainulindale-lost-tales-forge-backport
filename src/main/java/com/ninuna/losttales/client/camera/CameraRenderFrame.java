package com.ninuna.losttales.client.camera;

/**
 * Immutable geometry for the camera that was actually rendered after
 * collision and shoulder-offset scaling were applied.
 */
public final class CameraRenderFrame {
    private final double pivotX;
    private final double pivotY;
    private final double pivotZ;
    private final double yaw;
    private final double pitch;
    private final double distance;
    private final double shoulderOffset;
    private final double verticalOffset;
    private final double verticalFov;

    public CameraRenderFrame(
            double pivotX, double pivotY, double pivotZ,
            double yaw, double pitch, double distance,
            double shoulderOffset, double verticalOffset,
            double verticalFov) {
        CameraMath.requireFinite("pivotX", pivotX);
        CameraMath.requireFinite("pivotY", pivotY);
        CameraMath.requireFinite("pivotZ", pivotZ);
        CameraMath.requireFinite("yaw", yaw);
        CameraMath.requireFinite("pitch", pitch);
        CameraMath.requireNonNegativeFinite("distance", distance);
        CameraMath.requireFinite("shoulderOffset", shoulderOffset);
        CameraMath.requireFinite("verticalOffset", verticalOffset);
        CameraMath.requireFinite("verticalFov", verticalFov);
        if (pitch < -90.0D || pitch > 90.0D) {
            throw new IllegalArgumentException(
                    "pitch must be between -90 and 90 degrees");
        }
        if (verticalFov <= 1.0D || verticalFov >= 179.0D) {
            throw new IllegalArgumentException(
                    "verticalFov must be between 1 and 179 degrees");
        }
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        this.yaw = CameraMath.wrapDegrees(yaw);
        this.pitch = pitch;
        this.distance = distance;
        this.shoulderOffset = shoulderOffset;
        this.verticalOffset = verticalOffset;
        this.verticalFov = verticalFov;
    }

    public double getPivotX() {
        return pivotX;
    }

    public double getPivotY() {
        return pivotY;
    }

    public double getPivotZ() {
        return pivotZ;
    }

    public double getYaw() {
        return yaw;
    }

    public double getPitch() {
        return pitch;
    }

    public double getDistance() {
        return distance;
    }

    public double getShoulderOffset() {
        return shoulderOffset;
    }

    public double getVerticalOffset() {
        return verticalOffset;
    }

    public double getVerticalFov() {
        return verticalFov;
    }

    public double getForwardX() {
        return -Math.sin(yawRadians()) * Math.cos(pitchRadians());
    }

    public double getForwardY() {
        return -Math.sin(pitchRadians());
    }

    public double getForwardZ() {
        return Math.cos(yawRadians()) * Math.cos(pitchRadians());
    }

    public double getRightX() {
        return -Math.cos(yawRadians());
    }

    public double getRightY() {
        return 0.0D;
    }

    public double getRightZ() {
        return -Math.sin(yawRadians());
    }

    public double getUpX() {
        return -Math.sin(yawRadians()) * Math.sin(pitchRadians());
    }

    public double getUpY() {
        return Math.cos(pitchRadians());
    }

    public double getUpZ() {
        return Math.cos(yawRadians()) * Math.sin(pitchRadians());
    }

    public double getCameraX() {
        return pivotX - getForwardX() * distance
                + getRightX() * shoulderOffset
                + getUpX() * verticalOffset;
    }

    public double getCameraY() {
        return pivotY - getForwardY() * distance
                + getRightY() * shoulderOffset
                + getUpY() * verticalOffset;
    }

    public double getCameraZ() {
        return pivotZ - getForwardZ() * distance
                + getRightZ() * shoulderOffset
                + getUpZ() * verticalOffset;
    }

    private double yawRadians() {
        return Math.toRadians(yaw);
    }

    private double pitchRadians() {
        return Math.toRadians(pitch);
    }
}
