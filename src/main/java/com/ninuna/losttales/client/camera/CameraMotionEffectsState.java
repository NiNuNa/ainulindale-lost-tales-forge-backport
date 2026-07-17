package com.ninuna.losttales.client.camera;

/** Frame-rate-independent situational motion and decaying camera impulses. */
public final class CameraMotionEffectsState {
    private static final double TWO_PI = Math.PI * 2.0D;
    private static final double RESPONSE_RATE = 14.0D;

    private boolean initialized;
    private boolean previousAirborne;
    private boolean previousSwinging;
    private int previousHurtTime;
    private double previousVerticalVelocity;
    private double ridingPhase;
    private double swimmingPhase;
    private double impulsePhase;
    private double landingImpulse;
    private double attackImpulse;
    private double damageImpulse;
    private double explosionImpulse;
    private double damageDirection = 1.0D;
    private CameraMotionOffset current = CameraMotionOffset.ZERO;

    public void reset(CameraMotionEffectsSample sample) {
        if (sample == null) {
            throw new IllegalArgumentException("motion sample is required");
        }
        initialized = true;
        previousAirborne = sample.isAirborne();
        previousSwinging = sample.isSwinging();
        previousHurtTime = sample.getHurtTime();
        previousVerticalVelocity = sample.getVerticalVelocity();
        ridingPhase = 0.0D;
        swimmingPhase = 0.0D;
        impulsePhase = 0.0D;
        landingImpulse = 0.0D;
        attackImpulse = 0.0D;
        damageImpulse = 0.0D;
        explosionImpulse = 0.0D;
        damageDirection = 1.0D;
        current = CameraMotionOffset.ZERO;
    }

    public void clear() {
        initialized = false;
        resetValues();
    }

    public void triggerExplosion(double intensity) {
        CameraMath.requireNonNegativeFinite(
                "explosion intensity", intensity);
        explosionImpulse = Math.min(
                2.0D, explosionImpulse + intensity);
    }

    public CameraMotionOffset update(
            CameraMotionEffectsSample sample,
            CameraMotionEffectsSettings settings,
            double deltaSeconds) {
        if (sample == null || settings == null) {
            throw new IllegalArgumentException(
                    "motion sample and settings are required");
        }
        if (!initialized) {
            reset(sample);
            return current;
        }
        double delta = CameraMath.sanitizeDeltaSeconds(deltaSeconds);
        if (delta == 0.0D) {
            return current;
        }

        if (previousAirborne && sample.isOnGround()
                && previousVerticalVelocity < -0.15D) {
            landingImpulse = Math.max(
                    landingImpulse,
                    Math.min(0.12D,
                    (-previousVerticalVelocity - 0.10D) * 0.12D));
        }
        if (sample.isSwinging() && !previousSwinging) {
            attackImpulse = Math.max(attackImpulse, 0.035D);
        }
        if (sample.getHurtTime() > previousHurtTime) {
            damageDirection = -damageDirection;
            damageImpulse = Math.max(damageImpulse, 0.055D);
        }

        double speed = sample.getHorizontalSpeed();
        ridingPhase = wrapPhase(ridingPhase + TWO_PI
                * (0.75D + Math.min(6.0D, speed) * 0.12D) * delta);
        swimmingPhase = wrapPhase(swimmingPhase + TWO_PI
                * (0.35D + Math.min(4.0D, speed) * 0.08D) * delta);
        impulsePhase = wrapPhase(impulsePhase
                + TWO_PI * 5.5D * delta);

        double side = 0.0D;
        double vertical = 0.0D;
        double forward = 0.0D;
        if (sample.isAirborne()) {
            vertical += clamp(
                    -sample.getVerticalVelocity() * 0.035D,
                    -0.08D, 0.10D)
                    * settings.getAirborneMultiplier();
            forward -= Math.min(
                    0.025D,
                    Math.abs(sample.getVerticalVelocity()) * 0.008D)
                    * settings.getAirborneMultiplier();
        }
        if (sample.isRiding()) {
            double intensity = 0.25D
                    + 0.75D * clamp(speed / 5.0D, 0.0D, 1.0D);
            side += Math.sin(ridingPhase) * 0.025D * intensity
                    * settings.getRidingMultiplier();
            vertical -= Math.cos(ridingPhase * 2.0D) * 0.018D
                    * intensity * settings.getRidingMultiplier();
            forward += Math.cos(ridingPhase) * 0.008D * intensity
                    * settings.getRidingMultiplier();
        }
        if (sample.isSwimming()) {
            double intensity = 0.35D
                    + 0.65D * clamp(speed / 3.0D, 0.0D, 1.0D);
            side += Math.sin(swimmingPhase) * 0.018D * intensity
                    * settings.getSwimmingMultiplier();
            vertical += Math.sin(swimmingPhase * 2.0D + 0.5D)
                    * 0.016D * intensity
                    * settings.getSwimmingMultiplier();
            forward += Math.cos(swimmingPhase + 0.8D) * 0.008D
                    * intensity * settings.getSwimmingMultiplier();
        }

        vertical -= Math.cos(impulsePhase) * landingImpulse
                * settings.getLandingMultiplier();
        forward += attackImpulse * settings.getAttackMultiplier();
        side += damageDirection * damageImpulse
                * settings.getDamageMultiplier();
        forward -= damageImpulse * 0.55D
                * settings.getDamageMultiplier();
        double explosion = explosionImpulse
                * settings.getExplosionMultiplier();
        side += Math.sin(impulsePhase * 1.7D) * explosion * 0.09D;
        vertical += Math.sin(impulsePhase * 2.3D + 0.7D)
                * explosion * 0.07D;
        forward += Math.cos(impulsePhase * 1.3D)
                * explosion * 0.04D;

        current = new CameraMotionOffset(
                CameraMath.interpolate(
                        current.getSide(), side,
                        RESPONSE_RATE, delta),
                CameraMath.interpolate(
                        current.getVertical(), vertical,
                        RESPONSE_RATE, delta),
                CameraMath.interpolate(
                        current.getForward(), forward,
                        RESPONSE_RATE, delta));
        landingImpulse = decay(landingImpulse, 7.0D, delta);
        attackImpulse = decay(attackImpulse, 10.0D, delta);
        damageImpulse = decay(damageImpulse, 7.0D, delta);
        explosionImpulse = decay(explosionImpulse, 3.5D, delta);
        previousAirborne = sample.isAirborne();
        previousSwinging = sample.isSwinging();
        previousHurtTime = sample.getHurtTime();
        previousVerticalVelocity = sample.getVerticalVelocity();
        return current;
    }

    public CameraMotionOffset getCurrent() {
        return current;
    }

    private void resetValues() {
        previousAirborne = false;
        previousSwinging = false;
        previousHurtTime = 0;
        previousVerticalVelocity = 0.0D;
        ridingPhase = 0.0D;
        swimmingPhase = 0.0D;
        impulsePhase = 0.0D;
        landingImpulse = 0.0D;
        attackImpulse = 0.0D;
        damageImpulse = 0.0D;
        explosionImpulse = 0.0D;
        damageDirection = 1.0D;
        current = CameraMotionOffset.ZERO;
    }

    private static double decay(
            double value, double rate, double delta) {
        double decayed = value * Math.exp(-rate * delta);
        return decayed < 0.000001D ? 0.0D : decayed;
    }

    private static double wrapPhase(double value) {
        double wrapped = value % TWO_PI;
        return wrapped < 0.0D ? wrapped + TWO_PI : wrapped;
    }

    private static double clamp(
            double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
