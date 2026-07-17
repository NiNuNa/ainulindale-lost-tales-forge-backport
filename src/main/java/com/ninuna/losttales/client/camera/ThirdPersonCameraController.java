package com.ninuna.losttales.client.camera;

/**
 * Single client-side owner for interpolated camera, collision, shoulder,
 * manual zoom, FOV, and render-frame state. Inactive calls are pass-through.
 */
public final class ThirdPersonCameraController {
    private static final CameraState STATE = new CameraState();
    private static final CameraCollisionState COLLISION =
            new CameraCollisionState();
    private static final CameraMotionState MOTION =
            new CameraMotionState();
    private static final CameraMotionEffectsState EFFECTS =
            new CameraMotionEffectsState();
    private static boolean active;
    private static boolean rightShoulder = true;
    private static String contextKey;
    private static long lastUpdateNanos;
    private static double lastDeltaSeconds;
    private static double renderedFov = 70.0D;
    private static CameraRenderFrame renderFrame;
    private static double manualZoomOffset = Double.NaN;
    private static double lastProfileDistance = Double.NaN;

    private ThirdPersonCameraController() {}

    public static synchronized void activate(CameraPose initialPose) {
        STATE.reset(initialPose);
        COLLISION.reset();
        MOTION.reset(initialPose);
        EFFECTS.reset(CameraMotionEffectsSample.NONE);
        contextKey = null;
        lastUpdateNanos = 0L;
        lastDeltaSeconds = 0.0D;
        active = true;
    }

    public static synchronized CameraPose update(
            String newContextKey, CameraPose targetPose,
            CameraSmoothing smoothing, long updateNanos) {
        return update(newContextKey, targetPose, smoothing,
                CameraMotionProfile.NONE, 0.0D, updateNanos);
    }

    public static synchronized CameraPose update(
            String newContextKey, CameraPose targetPose,
            CameraSmoothing smoothing, CameraMotionProfile motion,
            double motionMultiplier, long updateNanos) {
        return update(newContextKey, targetPose, smoothing, motion,
                motionMultiplier, Double.NaN, Double.NaN,
                CameraMotionEffectsSample.NONE,
                CameraMotionEffectsSettings.NONE, updateNanos);
    }

    public static synchronized CameraPose update(
            String newContextKey, CameraPose targetPose,
            CameraSmoothing smoothing, CameraMotionProfile motion,
            double motionMultiplier, double stridePhase,
            double strideIntensity, long updateNanos) {
        return update(newContextKey, targetPose, smoothing, motion,
                motionMultiplier, stridePhase, strideIntensity,
                CameraMotionEffectsSample.NONE,
                CameraMotionEffectsSettings.NONE, updateNanos);
    }

    public static synchronized CameraPose update(
            String newContextKey, CameraPose targetPose,
            CameraSmoothing smoothing, CameraMotionProfile motion,
            double motionMultiplier, double stridePhase,
            double strideIntensity, CameraMotionEffectsSample effectsSample,
            CameraMotionEffectsSettings effectsSettings,
            long updateNanos) {
        if (newContextKey == null || targetPose == null
                || smoothing == null || motion == null
                || effectsSample == null || effectsSettings == null
                || updateNanos < 0L) {
            throw new IllegalArgumentException(
                    "context, target, smoothing, motion, and monotonic time are required");
        }
        CameraMath.requireNonNegativeFinite(
                "motionMultiplier", motionMultiplier);
        if (!active || !newContextKey.equals(contextKey)) {
            STATE.reset(targetPose);
            COLLISION.reset();
            MOTION.reset(targetPose);
            EFFECTS.reset(effectsSample);
            active = true;
            contextKey = newContextKey;
            lastUpdateNanos = updateNanos;
            lastDeltaSeconds = 0.0D;
            return targetPose;
        }

        double deltaSeconds = updateNanos <= lastUpdateNanos
                ? 0.0D
                : (double)(updateNanos - lastUpdateNanos) / 1000000000.0D;
        lastUpdateNanos = updateNanos;
        lastDeltaSeconds = CameraMath.sanitizeDeltaSeconds(deltaSeconds);
        STATE.setTarget(targetPose);
        CameraPose current = CameraMotionState.constrainFollow(
                STATE.advance(smoothing, lastDeltaSeconds),
                targetPose, motion, motionMultiplier);
        STATE.replaceCurrent(current);
        if (motionMultiplier == 0.0D) {
            MOTION.reset(targetPose);
            EFFECTS.reset(effectsSample);
        } else {
            MOTION.update(
                    targetPose, motion, motionMultiplier,
                    lastDeltaSeconds, stridePhase, strideIntensity);
            EFFECTS.update(
                    effectsSample, effectsSettings,
                    lastDeltaSeconds);
        }
        return current;
    }

    public static synchronized void setTarget(CameraPose targetPose) {
        if (!active) {
            throw new IllegalStateException("third-person camera is inactive");
        }
        STATE.setTarget(targetPose);
    }

    public static synchronized CameraPose advance(
            CameraSmoothing smoothing, double deltaSeconds) {
        if (!active) {
            throw new IllegalStateException("third-person camera is inactive");
        }
        lastDeltaSeconds = CameraMath.sanitizeDeltaSeconds(deltaSeconds);
        return STATE.advance(smoothing, lastDeltaSeconds);
    }

    public static synchronized double constrainDistance(
            double desiredDistance, double allowedDistance,
            double releaseRate) {
        if (!active) {
            return Math.min(desiredDistance, allowedDistance);
        }
        return COLLISION.update(
                desiredDistance, allowedDistance,
                releaseRate, lastDeltaSeconds);
    }

    public static synchronized void recordRenderedFov(double verticalFov) {
        if (verticalFov > 1.0D && verticalFov < 179.0D
                && !Double.isNaN(verticalFov)
                && !Double.isInfinite(verticalFov)) {
            renderedFov = verticalFov;
        }
    }

    public static synchronized void recordRenderFrame(
            double pivotX, double pivotY, double pivotZ,
            double yaw, double pitch, double actualDistance) {
        prepareRenderFrame(
                pivotX, pivotY, pivotZ,
                yaw, pitch, actualDistance);
    }

    public static synchronized CameraRenderTransform prepareRenderFrame(
            double pivotX, double pivotY, double pivotZ,
            double yaw, double pitch, double actualDistance) {
        CameraPose pose = active ? STATE.getCurrent() : null;
        if (pose == null || Double.isNaN(actualDistance)
                || Double.isInfinite(actualDistance)
                || actualDistance < 0.0D) {
            renderFrame = null;
            return null;
        }
        CameraRenderTransform transform = CameraRenderTransform.resolve(
                pivotX, pivotY, pivotZ, yaw, pitch,
                actualDistance, pose, combinedMotion(), renderedFov);
        renderFrame = transform.getFrame();
        return transform;
    }

    public static synchronized CameraRenderFrame getRenderFrame() {
        return active ? renderFrame : null;
    }

    public static synchronized double resolveZoomDistance(
            double profileDistance, double minimumDistance,
            double maximumDistance) {
        validateZoomBounds(minimumDistance, maximumDistance);
        CameraMath.requireNonNegativeFinite(
                "profileDistance", profileDistance);
        lastProfileDistance = profileDistance;
        double requested = profileDistance + (Double.isNaN(
                manualZoomOffset) ? 0.0D : manualZoomOffset);
        return clamp(requested, minimumDistance, maximumDistance);
    }

    public static synchronized boolean adjustZoom(
            int wheelDelta, double minimumDistance,
            double maximumDistance, double step) {
        validateZoomBounds(minimumDistance, maximumDistance);
        CameraMath.requireNonNegativeFinite("step", step);
        CameraPose pose = active ? STATE.getCurrent() : null;
        if (wheelDelta == 0 || pose == null || step == 0.0D) {
            return false;
        }
        double profileDistance = Double.isNaN(lastProfileDistance)
                ? pose.getDistance() : lastProfileDistance;
        double current = clamp(profileDistance + (Double.isNaN(
                manualZoomOffset) ? 0.0D : manualZoomOffset),
                minimumDistance, maximumDistance);
        double direction = wheelDelta > 0 ? -1.0D : 1.0D;
        double requested = clamp(current + direction * step,
                minimumDistance, maximumDistance);
        manualZoomOffset = requested - profileDistance;
        return true;
    }

    static synchronized double getManualZoomOffset() {
        return manualZoomOffset;
    }

    public static synchronized boolean isActive() {
        return active;
    }

    public static synchronized CameraPose getCurrentPose() {
        return active ? STATE.getCurrent() : null;
    }

    public static synchronized CameraMotionOffset getMotionOffset() {
        return active ? combinedMotion() : CameraMotionOffset.ZERO;
    }

    public static synchronized void triggerExplosionShake(
            double intensity) {
        if (active) {
            EFFECTS.triggerExplosion(intensity);
        }
    }

    public static synchronized boolean isRightShoulder() {
        return rightShoulder;
    }

    public static synchronized double getShoulderSign() {
        return rightShoulder ? 1.0D : -1.0D;
    }

    public static synchronized void toggleShoulder() {
        rightShoulder = !rightShoulder;
    }

    public static synchronized void setRightShoulder(boolean useRightShoulder) {
        rightShoulder = useRightShoulder;
    }

    public static synchronized float resolveViewYaw(
            float fallbackPreviousYaw, float fallbackCurrentYaw,
            float partialTicks) {
        CameraPose pose = active ? STATE.getCurrent() : null;
        if (pose != null) {
            return (float)pose.getYaw();
        }
        float safePartialTicks = Math.max(0.0F, Math.min(1.0F, partialTicks));
        return fallbackPreviousYaw
                + (fallbackCurrentYaw - fallbackPreviousYaw)
                * safePartialTicks;
    }

    public static synchronized void deactivate() {
        active = false;
        STATE.clear();
        COLLISION.reset();
        MOTION.clear();
        EFFECTS.clear();
        contextKey = null;
        lastUpdateNanos = 0L;
        lastDeltaSeconds = 0.0D;
        renderedFov = 70.0D;
        renderFrame = null;
        lastProfileDistance = Double.NaN;
    }

    public static synchronized void reset() {
        deactivate();
        rightShoulder = true;
        manualZoomOffset = Double.NaN;
    }

    public static synchronized void reset(boolean useRightShoulder) {
        deactivate();
        rightShoulder = useRightShoulder;
        manualZoomOffset = Double.NaN;
    }

    private static void validateZoomBounds(
            double minimumDistance, double maximumDistance) {
        CameraMath.requireNonNegativeFinite(
                "minimumDistance", minimumDistance);
        CameraMath.requireNonNegativeFinite(
                "maximumDistance", maximumDistance);
        if (minimumDistance > maximumDistance) {
            throw new IllegalArgumentException(
                    "minimumDistance cannot exceed maximumDistance");
        }
    }

    private static double clamp(
            double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static CameraMotionOffset combinedMotion() {
        return MOTION.getCurrent().add(EFFECTS.getCurrent());
    }
}
