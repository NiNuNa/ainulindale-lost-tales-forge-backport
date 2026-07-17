package com.ninuna.losttales.client.camera;

/** Target framing and channel response rates for one camera state. */
public final class CameraProfile {
    private final CameraProfileId id;
    private final double distance;
    private final double shoulderOffset;
    private final double verticalOffset;
    private final double fovOffset;
    private final CameraSmoothing smoothing;
    private final CameraMotionProfile motion;

    public CameraProfile(
            CameraProfileId id, double distance, double shoulderOffset,
            double verticalOffset, double fovOffset,
            CameraSmoothing smoothing) {
        this(id, distance, shoulderOffset, verticalOffset, fovOffset,
                smoothing, CameraMotionProfile.NONE);
    }

    public CameraProfile(
            CameraProfileId id, double distance, double shoulderOffset,
            double verticalOffset, double fovOffset,
            CameraSmoothing smoothing, CameraMotionProfile motion) {
        if (id == null || smoothing == null || motion == null) {
            throw new IllegalArgumentException(
                    "id, smoothing, and motion are required");
        }
        CameraMath.requireNonNegativeFinite("distance", distance);
        CameraMath.requireFinite("shoulderOffset", shoulderOffset);
        CameraMath.requireFinite("verticalOffset", verticalOffset);
        CameraMath.requireFinite("fovOffset", fovOffset);
        this.id = id;
        this.distance = distance;
        this.shoulderOffset = shoulderOffset;
        this.verticalOffset = verticalOffset;
        this.fovOffset = fovOffset;
        this.smoothing = smoothing;
        this.motion = motion;
    }

    public CameraPose createTargetPose(
            double positionX, double positionY, double positionZ,
            double yaw, double pitch) {
        return new CameraPose(
                positionX, positionY, positionZ, yaw, pitch, distance,
                shoulderOffset, verticalOffset, fovOffset);
    }

    public CameraProfileId getId() {
        return id;
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

    public CameraSmoothing getSmoothing() {
        return smoothing;
    }

    public CameraMotionProfile getMotion() {
        return motion;
    }
}
