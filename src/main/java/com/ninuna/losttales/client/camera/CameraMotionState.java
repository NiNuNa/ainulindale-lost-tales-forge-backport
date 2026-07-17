package com.ninuna.losttales.client.camera;

/** Frame-rate-independent movement phase, turn inertia, and follow limits. */
public final class CameraMotionState {
    private static final double TWO_PI = Math.PI * 2.0D;
    private static final double REFERENCE_MOVEMENT_SPEED = 4.3D;
    private static final double TELEPORT_DISTANCE = 4.0D;
    private static final double STILL_MOVEMENT_INTENSITY = 0.01D;
    private static final double STILL_LOOK_SPEED = 0.5D;
    private static final double IDLE_FADE_IN_RATE = 2.0D;
    private static final double IDLE_FADE_OUT_RATE = 12.0D;

    private boolean initialized;
    private double previousX;
    private double previousY;
    private double previousZ;
    private double previousYaw;
    private double previousPitch;
    private double phase;
    private double idlePhase;
    private double movementIntensity;
    private double idleIntensity;
    private CameraMotionOffset baseCurrent = CameraMotionOffset.ZERO;
    private CameraMotionOffset lookCurrent = CameraMotionOffset.ZERO;
    private CameraMotionOffset current = CameraMotionOffset.ZERO;

    public void reset(CameraPose target) {
        if (target == null) {
            throw new IllegalArgumentException("target is required");
        }
        initialized = true;
        previousX = target.getPositionX();
        previousY = target.getPositionY();
        previousZ = target.getPositionZ();
        previousYaw = target.getYaw();
        previousPitch = target.getPitch();
        phase = 0.0D;
        idlePhase = 0.0D;
        movementIntensity = 0.0D;
        idleIntensity = 0.0D;
        baseCurrent = CameraMotionOffset.ZERO;
        lookCurrent = CameraMotionOffset.ZERO;
        current = CameraMotionOffset.ZERO;
    }

    public void clear() {
        initialized = false;
        previousX = 0.0D;
        previousY = 0.0D;
        previousZ = 0.0D;
        previousYaw = 0.0D;
        previousPitch = 0.0D;
        phase = 0.0D;
        idlePhase = 0.0D;
        movementIntensity = 0.0D;
        idleIntensity = 0.0D;
        baseCurrent = CameraMotionOffset.ZERO;
        lookCurrent = CameraMotionOffset.ZERO;
        current = CameraMotionOffset.ZERO;
    }

    public CameraMotionOffset update(
            CameraPose target, CameraMotionProfile profile,
            double multiplier, double deltaSeconds) {
        return update(target, profile, multiplier, deltaSeconds,
                Double.NaN, Double.NaN);
    }

    public CameraMotionOffset update(
            CameraPose target, CameraMotionProfile profile,
            double multiplier, double deltaSeconds,
            double stridePhase, double strideIntensity) {
        if (target == null || profile == null) {
            throw new IllegalArgumentException(
                    "target and motion profile are required");
        }
        CameraMath.requireNonNegativeFinite("multiplier", multiplier);
        boolean hasStridePhase = isFinite(stridePhase);
        boolean hasStrideIntensity = isFinite(strideIntensity);
        if (hasStridePhase != hasStrideIntensity
                || (hasStrideIntensity && strideIntensity < 0.0D)) {
            throw new IllegalArgumentException(
                    "stride phase and non-negative intensity must be supplied together");
        }
        if (!initialized) {
            reset(target);
            return current;
        }

        double delta = CameraMath.sanitizeDeltaSeconds(deltaSeconds);
        if (delta == 0.0D) {
            return current;
        }
        double deltaX = target.getPositionX() - previousX;
        double deltaY = target.getPositionY() - previousY;
        double deltaZ = target.getPositionZ() - previousZ;
        double travelledSquared = deltaX * deltaX
                + deltaY * deltaY + deltaZ * deltaZ;
        if (travelledSquared
                > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
            reset(target);
            return current;
        }

        double horizontalTravel = Math.sqrt(
                deltaX * deltaX + deltaZ * deltaZ);
        double targetIntensity;
        if (hasStridePhase) {
            phase = wrapPhase(stridePhase);
            targetIntensity = clamp01(strideIntensity);
        } else {
            phase = wrapPhase(phase + horizontalTravel
                    * profile.getSwayCyclesPerBlock() * TWO_PI);
            targetIntensity = clamp01(
                    horizontalTravel / delta / REFERENCE_MOVEMENT_SPEED);
        }
        movementIntensity = CameraMath.interpolate(
                movementIntensity, targetIntensity,
                profile.getResponseRate(), delta);
        idlePhase = wrapPhase(idlePhase
                + profile.getIdleCyclesPerSecond() * TWO_PI * delta);

        double yawDifference = CameraMath.wrapDegrees(
                target.getYaw() - previousYaw);
        double pitchDifference = CameraMath.wrapDegrees(
                target.getPitch() - previousPitch);
        double lookSpeed = Math.sqrt(
                yawDifference * yawDifference
                        + pitchDifference * pitchDifference) / delta;
        double targetIdleIntensity = targetIntensity
                <= STILL_MOVEMENT_INTENSITY
                && lookSpeed <= STILL_LOOK_SPEED ? 1.0D : 0.0D;
        double idleResponseRate = targetIdleIntensity > idleIntensity
                ? IDLE_FADE_IN_RATE : IDLE_FADE_OUT_RATE;
        idleIntensity = CameraMath.interpolate(
                idleIntensity, targetIdleIntensity,
                idleResponseRate, delta);
        double yawTurn = clamp(
                yawDifference / delta / profile.getLookReferenceSpeed(),
                -1.0D, 1.0D);
        double pitchTurn = clamp(
                pitchDifference / delta
                        / profile.getLookReferenceSpeed(),
                -1.0D, 1.0D);
        double turnSpeed = clamp(
                Math.sqrt(yawTurn * yawTurn
                        + pitchTurn * pitchTurn),
                0.0D, 1.0D);
        double amplitude = multiplier * movementIntensity;
        double idleAmplitude = multiplier * idleIntensity;
        double targetSide = Math.sin(phase)
                * profile.getSideSway() * amplitude
                + Math.sin(idlePhase)
                * profile.getIdleSideSway() * idleAmplitude;
        double doublePhase = phase * 2.0D;
        double targetVertical = -Math.cos(doublePhase)
                * profile.getVerticalSway() * amplitude
                + Math.sin(idlePhase * 2.0D + 0.6D)
                * profile.getIdleVerticalSway() * idleAmplitude;
        double targetForward = Math.cos(doublePhase)
                * profile.getForwardSway() * amplitude
                + Math.cos(idlePhase + 1.3D)
                * profile.getIdleForwardSway() * idleAmplitude;
        CameraMotionOffset baseTarget = new CameraMotionOffset(
                targetSide, targetVertical, targetForward);
        CameraMotionOffset lookTarget = new CameraMotionOffset(
                -yawTurn * profile.getTurnSway() * multiplier,
                -pitchTurn * profile.getLookPitchSway() * multiplier,
                -turnSpeed * profile.getLookForwardSway() * multiplier);
        baseCurrent = new CameraMotionOffset(
                CameraMath.interpolate(
                        baseCurrent.getSide(), baseTarget.getSide(),
                        profile.getResponseRate(), delta),
                CameraMath.interpolate(
                        baseCurrent.getVertical(), baseTarget.getVertical(),
                        profile.getResponseRate(), delta),
                CameraMath.interpolate(
                        baseCurrent.getForward(), baseTarget.getForward(),
                        profile.getResponseRate(), delta));
        lookCurrent = new CameraMotionOffset(
                CameraMath.interpolate(
                        lookCurrent.getSide(), lookTarget.getSide(),
                        profile.getLookResponseRate(), delta),
                CameraMath.interpolate(
                        lookCurrent.getVertical(), lookTarget.getVertical(),
                        profile.getLookResponseRate(), delta),
                CameraMath.interpolate(
                        lookCurrent.getForward(), lookTarget.getForward(),
                        profile.getLookResponseRate(), delta));
        current = baseCurrent.add(lookCurrent);
        previousX = target.getPositionX();
        previousY = target.getPositionY();
        previousZ = target.getPositionZ();
        previousYaw = target.getYaw();
        previousPitch = target.getPitch();
        return current;
    }

    public CameraMotionOffset getCurrent() {
        return current;
    }

    public static CameraPose constrainFollow(
            CameraPose currentPose, CameraPose targetPose,
            CameraMotionProfile profile, double multiplier) {
        if (currentPose == null || targetPose == null || profile == null) {
            throw new IllegalArgumentException(
                    "current, target, and motion profile are required");
        }
        CameraMath.requireNonNegativeFinite("multiplier", multiplier);
        double deltaX = currentPose.getPositionX()
                - targetPose.getPositionX();
        double deltaZ = currentPose.getPositionZ()
                - targetPose.getPositionZ();
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double horizontalLimit = profile.getHorizontalFollowLimit()
                * multiplier;
        if (horizontal > horizontalLimit && horizontal > 0.0000001D) {
            double scale = horizontalLimit / horizontal;
            deltaX *= scale;
            deltaZ *= scale;
        }
        double verticalLimit = profile.getVerticalFollowLimit()
                * multiplier;
        double deltaY = clamp(
                currentPose.getPositionY() - targetPose.getPositionY(),
                -verticalLimit, verticalLimit);
        return new CameraPose(
                targetPose.getPositionX() + deltaX,
                targetPose.getPositionY() + deltaY,
                targetPose.getPositionZ() + deltaZ,
                currentPose.getYaw(), currentPose.getPitch(),
                currentPose.getDistance(),
                currentPose.getShoulderOffset(),
                currentPose.getVerticalOffset(),
                currentPose.getFovOffset());
    }

    private static double wrapPhase(double value) {
        double wrapped = value % TWO_PI;
        return wrapped < 0.0D ? wrapped + TWO_PI : wrapped;
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0D, 1.0D);
    }

    private static double clamp(
            double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
