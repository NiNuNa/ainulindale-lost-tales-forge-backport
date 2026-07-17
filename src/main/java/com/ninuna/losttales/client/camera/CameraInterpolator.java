package com.ninuna.losttales.client.camera;

/** Frame-rate-independent interpolation across every camera pose channel. */
public final class CameraInterpolator {
    private CameraInterpolator() {}

    public static CameraPose interpolate(
            CameraPose current, CameraPose target,
            CameraSmoothing smoothing, double deltaSeconds) {
        if (current == null || target == null || smoothing == null) {
            throw new IllegalArgumentException(
                    "current, target, and smoothing are required");
        }
        double safeDelta = CameraMath.sanitizeDeltaSeconds(deltaSeconds);
        if (safeDelta == 0.0D) {
            return current;
        }
        return new CameraPose(
                CameraMath.interpolate(
                        current.getPositionX(), target.getPositionX(),
                        smoothing.getPositionRate(), safeDelta),
                CameraMath.interpolate(
                        current.getPositionY(), target.getPositionY(),
                        smoothing.getVerticalPositionRate(), safeDelta),
                CameraMath.interpolate(
                        current.getPositionZ(), target.getPositionZ(),
                        smoothing.getPositionRate(), safeDelta),
                CameraMath.interpolateAngle(
                        current.getYaw(), target.getYaw(),
                        smoothing.getRotationRate(), safeDelta),
                CameraMath.interpolate(
                        current.getPitch(), target.getPitch(),
                        smoothing.getRotationRate(), safeDelta),
                CameraMath.interpolate(
                        current.getDistance(), target.getDistance(),
                        smoothing.getZoomRate(), safeDelta),
                CameraMath.interpolate(
                        current.getShoulderOffset(),
                        target.getShoulderOffset(),
                        smoothing.getShoulderRate(), safeDelta),
                CameraMath.interpolate(
                        current.getVerticalOffset(),
                        target.getVerticalOffset(),
                        smoothing.getVerticalRate(), safeDelta),
                CameraMath.interpolate(
                        current.getFovOffset(), target.getFovOffset(),
                        smoothing.getFovRate(), safeDelta));
    }
}
