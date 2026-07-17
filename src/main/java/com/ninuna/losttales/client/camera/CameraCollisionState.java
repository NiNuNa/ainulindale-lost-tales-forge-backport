package com.ninuna.losttales.client.camera;

/** Immediate obstruction pull-in with a smoothed release away from walls. */
public final class CameraCollisionState {
    private double currentDistance = Double.NaN;

    public void reset() {
        currentDistance = Double.NaN;
    }

    public double update(
            double desiredDistance, double allowedDistance,
            double releaseRate, double deltaSeconds) {
        CameraMath.requireNonNegativeFinite(
                "desiredDistance", desiredDistance);
        CameraMath.requireNonNegativeFinite(
                "allowedDistance", allowedDistance);
        CameraMath.requireNonNegativeFinite("releaseRate", releaseRate);
        double target = Math.min(desiredDistance, allowedDistance);
        if (Double.isNaN(currentDistance)
                || target <= currentDistance
                || currentDistance > desiredDistance) {
            currentDistance = target;
        } else {
            currentDistance = CameraMath.interpolate(
                    currentDistance, target, releaseRate, deltaSeconds);
        }
        currentDistance = Math.max(
                0.0D, Math.min(currentDistance, desiredDistance));
        return currentDistance;
    }
}
