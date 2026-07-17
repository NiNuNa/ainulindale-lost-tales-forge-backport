package com.ninuna.losttales.client.camera;

/** Bounded follow-lag and procedural motion values for one camera profile. */
public final class CameraMotionProfile {
    public static final CameraMotionProfile NONE = new CameraMotionProfile(
            0.0D, 0.0D, 0.0D, 0.0D,
            0.0D, 0.0D, 0.0D, 8.0D);

    private final double horizontalFollowLimit;
    private final double verticalFollowLimit;
    private final double sideSway;
    private final double verticalSway;
    private final double forwardSway;
    private final double turnSway;
    private final double lookPitchSway;
    private final double lookForwardSway;
    private final double lookResponseRate;
    private final double lookReferenceSpeed;
    private final double swayCyclesPerBlock;
    private final double responseRate;
    private final double idleSideSway;
    private final double idleVerticalSway;
    private final double idleForwardSway;
    private final double idleCyclesPerSecond;

    public CameraMotionProfile(
            double horizontalFollowLimit, double verticalFollowLimit,
            double sideSway, double verticalSway, double forwardSway,
            double turnSway, double swayCyclesPerBlock,
            double responseRate) {
        this(horizontalFollowLimit, verticalFollowLimit,
                sideSway, verticalSway, forwardSway,
                turnSway, swayCyclesPerBlock, responseRate,
                0.0D, 0.0D, 0.0D, 0.0D);
    }

    public CameraMotionProfile(
            double horizontalFollowLimit, double verticalFollowLimit,
            double sideSway, double verticalSway, double forwardSway,
            double turnSway, double swayCyclesPerBlock,
            double responseRate, double idleSideSway,
            double idleVerticalSway, double idleForwardSway,
            double idleCyclesPerSecond) {
        this(horizontalFollowLimit, verticalFollowLimit,
                sideSway, verticalSway, forwardSway,
                turnSway, 0.0D, 0.0D,
                responseRate, 240.0D,
                swayCyclesPerBlock, responseRate,
                idleSideSway, idleVerticalSway,
                idleForwardSway, idleCyclesPerSecond);
    }

    public CameraMotionProfile(
            double horizontalFollowLimit, double verticalFollowLimit,
            double sideSway, double verticalSway, double forwardSway,
            double turnSway, double lookPitchSway,
            double lookForwardSway, double lookResponseRate,
            double lookReferenceSpeed, double swayCyclesPerBlock,
            double responseRate, double idleSideSway,
            double idleVerticalSway, double idleForwardSway,
            double idleCyclesPerSecond) {
        CameraMath.requireNonNegativeFinite(
                "horizontalFollowLimit", horizontalFollowLimit);
        CameraMath.requireNonNegativeFinite(
                "verticalFollowLimit", verticalFollowLimit);
        CameraMath.requireNonNegativeFinite("sideSway", sideSway);
        CameraMath.requireNonNegativeFinite("verticalSway", verticalSway);
        CameraMath.requireNonNegativeFinite("forwardSway", forwardSway);
        CameraMath.requireNonNegativeFinite("turnSway", turnSway);
        CameraMath.requireNonNegativeFinite(
                "lookPitchSway", lookPitchSway);
        CameraMath.requireNonNegativeFinite(
                "lookForwardSway", lookForwardSway);
        CameraMath.requireNonNegativeFinite(
                "lookResponseRate", lookResponseRate);
        CameraMath.requireNonNegativeFinite(
                "lookReferenceSpeed", lookReferenceSpeed);
        if (lookReferenceSpeed <= 0.0D) {
            throw new IllegalArgumentException(
                    "lookReferenceSpeed must be positive");
        }
        CameraMath.requireNonNegativeFinite(
                "swayCyclesPerBlock", swayCyclesPerBlock);
        CameraMath.requireNonNegativeFinite("responseRate", responseRate);
        CameraMath.requireNonNegativeFinite("idleSideSway", idleSideSway);
        CameraMath.requireNonNegativeFinite(
                "idleVerticalSway", idleVerticalSway);
        CameraMath.requireNonNegativeFinite(
                "idleForwardSway", idleForwardSway);
        CameraMath.requireNonNegativeFinite(
                "idleCyclesPerSecond", idleCyclesPerSecond);
        this.horizontalFollowLimit = horizontalFollowLimit;
        this.verticalFollowLimit = verticalFollowLimit;
        this.sideSway = sideSway;
        this.verticalSway = verticalSway;
        this.forwardSway = forwardSway;
        this.turnSway = turnSway;
        this.lookPitchSway = lookPitchSway;
        this.lookForwardSway = lookForwardSway;
        this.lookResponseRate = lookResponseRate;
        this.lookReferenceSpeed = lookReferenceSpeed;
        this.swayCyclesPerBlock = swayCyclesPerBlock;
        this.responseRate = responseRate;
        this.idleSideSway = idleSideSway;
        this.idleVerticalSway = idleVerticalSway;
        this.idleForwardSway = idleForwardSway;
        this.idleCyclesPerSecond = idleCyclesPerSecond;
    }

    public double getHorizontalFollowLimit() {
        return horizontalFollowLimit;
    }

    public double getVerticalFollowLimit() {
        return verticalFollowLimit;
    }

    public double getSideSway() {
        return sideSway;
    }

    public double getVerticalSway() {
        return verticalSway;
    }

    public double getForwardSway() {
        return forwardSway;
    }

    public double getTurnSway() {
        return turnSway;
    }

    public double getLookPitchSway() {
        return lookPitchSway;
    }

    public double getLookForwardSway() {
        return lookForwardSway;
    }

    public double getLookResponseRate() {
        return lookResponseRate;
    }

    public double getLookReferenceSpeed() {
        return lookReferenceSpeed;
    }

    public double getSwayCyclesPerBlock() {
        return swayCyclesPerBlock;
    }

    public double getResponseRate() {
        return responseRate;
    }

    public double getIdleSideSway() {
        return idleSideSway;
    }

    public double getIdleVerticalSway() {
        return idleVerticalSway;
    }

    public double getIdleForwardSway() {
        return idleForwardSway;
    }

    public double getIdleCyclesPerSecond() {
        return idleCyclesPerSecond;
    }

    public CameraMotionProfile scaled(
            double amplitudeMultiplier, double responseMultiplier) {
        CameraMath.requireNonNegativeFinite(
                "amplitudeMultiplier", amplitudeMultiplier);
        CameraMath.requireNonNegativeFinite(
                "responseMultiplier", responseMultiplier);
        return new CameraMotionProfile(
                horizontalFollowLimit * amplitudeMultiplier,
                verticalFollowLimit * amplitudeMultiplier,
                sideSway * amplitudeMultiplier,
                verticalSway * amplitudeMultiplier,
                forwardSway * amplitudeMultiplier,
                turnSway * amplitudeMultiplier,
                lookPitchSway * amplitudeMultiplier,
                lookForwardSway * amplitudeMultiplier,
                lookResponseRate * responseMultiplier,
                lookReferenceSpeed,
                swayCyclesPerBlock,
                responseRate * responseMultiplier,
                idleSideSway * amplitudeMultiplier,
                idleVerticalSway * amplitudeMultiplier,
                idleForwardSway * amplitudeMultiplier,
                idleCyclesPerSecond);
    }
}
