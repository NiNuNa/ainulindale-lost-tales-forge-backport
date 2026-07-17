package com.ninuna.losttales.client.camera;

/** Per-frame player state consumed by situational camera motion. */
public final class CameraMotionEffectsSample {
    public static final CameraMotionEffectsSample NONE =
            new CameraMotionEffectsSample(
                    false, true, false, false,
                    false, 0, 0.0D, 0.0D);

    private final boolean airborne;
    private final boolean onGround;
    private final boolean riding;
    private final boolean swimming;
    private final boolean swinging;
    private final int hurtTime;
    private final double verticalVelocity;
    private final double horizontalSpeed;

    public CameraMotionEffectsSample(
            boolean airborne, boolean onGround,
            boolean riding, boolean swimming,
            boolean swinging, int hurtTime,
            double verticalVelocity, double horizontalSpeed) {
        if (hurtTime < 0) {
            throw new IllegalArgumentException(
                    "hurt time must be non-negative");
        }
        CameraMath.requireFinite(
                "verticalVelocity", verticalVelocity);
        CameraMath.requireNonNegativeFinite(
                "horizontalSpeed", horizontalSpeed);
        this.airborne = airborne;
        this.onGround = onGround;
        this.riding = riding;
        this.swimming = swimming;
        this.swinging = swinging;
        this.hurtTime = hurtTime;
        this.verticalVelocity = verticalVelocity;
        this.horizontalSpeed = horizontalSpeed;
    }

    public boolean isAirborne() {
        return airborne;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public boolean isRiding() {
        return riding;
    }

    public boolean isSwimming() {
        return swimming;
    }

    public boolean isSwinging() {
        return swinging;
    }

    public int getHurtTime() {
        return hurtTime;
    }

    public double getVerticalVelocity() {
        return verticalVelocity;
    }

    public double getHorizontalSpeed() {
        return horizontalSpeed;
    }
}
