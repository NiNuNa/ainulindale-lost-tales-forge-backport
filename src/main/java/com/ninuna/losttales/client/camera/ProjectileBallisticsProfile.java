package com.ninuna.losttales.client.camera;

/** Immutable per-tick launch physics used by trajectory prediction. */
public final class ProjectileBallisticsProfile {
    private final double launchSpeed;
    private final double gravity;
    private final double drag;

    public ProjectileBallisticsProfile(
            double launchSpeed, double gravity, double drag) {
        CameraMath.requireNonNegativeFinite("launchSpeed", launchSpeed);
        CameraMath.requireNonNegativeFinite("gravity", gravity);
        CameraMath.requireNonNegativeFinite("drag", drag);
        if (launchSpeed <= 0.0D || drag <= 0.0D || drag > 1.0D) {
            throw new IllegalArgumentException(
                    "launch speed must be positive and drag must be in (0, 1]");
        }
        this.launchSpeed = launchSpeed;
        this.gravity = gravity;
        this.drag = drag;
    }

    public double getLaunchSpeed() {
        return launchSpeed;
    }

    public double getGravity() {
        return gravity;
    }

    public double getDrag() {
        return drag;
    }

    public ProjectileBallisticsProfile scaleLaunchSpeed(
            double multiplier) {
        CameraMath.requireNonNegativeFinite("multiplier", multiplier);
        if (multiplier <= 0.0D) {
            throw new IllegalArgumentException(
                    "launch-speed multiplier must be positive");
        }
        return multiplier == 1.0D ? this
                : new ProjectileBallisticsProfile(
                launchSpeed * multiplier, gravity, drag);
    }
}
