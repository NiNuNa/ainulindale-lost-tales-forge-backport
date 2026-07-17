package com.ninuna.losttales.client.camera;

/** Immutable world-space camera state for a single rendered frame. */
public final class CameraPose {
    private final double positionX;
    private final double positionY;
    private final double positionZ;
    private final double yaw;
    private final double pitch;
    private final double distance;
    private final double shoulderOffset;
    private final double verticalOffset;
    private final double fovOffset;

    public CameraPose(
            double positionX, double positionY, double positionZ,
            double yaw, double pitch, double distance,
            double shoulderOffset, double verticalOffset, double fovOffset) {
        CameraMath.requireFinite("positionX", positionX);
        CameraMath.requireFinite("positionY", positionY);
        CameraMath.requireFinite("positionZ", positionZ);
        CameraMath.requireFinite("yaw", yaw);
        CameraMath.requireFinite("pitch", pitch);
        CameraMath.requireNonNegativeFinite("distance", distance);
        CameraMath.requireFinite("shoulderOffset", shoulderOffset);
        CameraMath.requireFinite("verticalOffset", verticalOffset);
        CameraMath.requireFinite("fovOffset", fovOffset);
        if (pitch < -90.0D || pitch > 90.0D) {
            throw new IllegalArgumentException(
                    "pitch must be between -90 and 90 degrees");
        }
        this.positionX = positionX;
        this.positionY = positionY;
        this.positionZ = positionZ;
        this.yaw = CameraMath.wrapDegrees(yaw);
        this.pitch = pitch;
        this.distance = distance;
        this.shoulderOffset = shoulderOffset;
        this.verticalOffset = verticalOffset;
        this.fovOffset = fovOffset;
    }

    public double getPositionX() {
        return positionX;
    }

    public double getPositionY() {
        return positionY;
    }

    public double getPositionZ() {
        return positionZ;
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

    public double getFovOffset() {
        return fovOffset;
    }
}
