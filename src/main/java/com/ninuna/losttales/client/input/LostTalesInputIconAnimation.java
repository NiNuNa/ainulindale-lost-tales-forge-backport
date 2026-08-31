package com.ninuna.losttales.client.input;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiEasing;
import org.lwjgl.input.Keyboard;

/** Per-key, frame-rate-independent motion for keyboard hint artwork. */
final class LostTalesInputIconAnimation {
    static final int IDLE_FRAME = 0;
    static final int RELEASE_FRAME = 1;
    static final int PRESSED_FRAME = 2;

    /** Fast contact, followed by a softer settle into held pressure. */
    static final long PRESS_IMPACT_NANOS = 50_000_000L;
    static final long PRESS_SETTLE_NANOS = 150_000_000L;
    /** The supplied middle artwork is the first half of the release. */
    static final long RELEASE_FRAME_NANOS = 70_000_000L;
    /** Full follow-through, including the damped return to rest. */
    static final long RELEASE_SETTLE_NANOS = 190_000_000L;
    /** Prevents a key released while its GUI was closed animating on reopen. */
    static final long STALE_AFTER_NANOS = 250_000_000L;

    private static final double TWO_PI = Math.PI * 2.0D;
    /** A restrained 5.4 Hz pressure tremor rather than a conspicuous shake. */
    private static final long HELD_JITTER_PERIOD_NANOS = 185_000_000L;
    private static final long HELD_JITTER_RAMP_NANOS = 130_000_000L;
    private static final long IDLE_MOTION_PERIOD_NANOS = 2_600_000_000L;

    private boolean previouslyPressed;
    private long pressStartedNanos;
    private long releaseStartedNanos;
    private long lastSeenNanos;
    private float releaseStrength = 1.0F;
    /** Exact undirected pose visible immediately before a release. */
    private Pose lastBasePose;
    private Pose releaseFromPose;

    int frame(boolean pressed, long nowNanos) {
        return pose(pressed, nowNanos, Keyboard.KEY_NONE).getFrame();
    }

    Pose pose(boolean pressed, long nowNanos, int keyCode) {
        if (this.lastSeenNanos != 0L
                && elapsed(this.lastSeenNanos, nowNanos)
                        > STALE_AFTER_NANOS) {
            resetTransitions();
        }
        this.lastSeenNanos = nowNanos;

        Pose pose;
        if (pressed) {
            if (!this.previouslyPressed) {
                this.pressStartedNanos = nowNanos;
            }
            this.previouslyPressed = true;
            this.releaseStartedNanos = 0L;
            pose = pressedPose(elapsed(this.pressStartedNanos, nowNanos),
                    nowNanos, keyCode);
        } else {
            if (this.previouslyPressed) {
                this.previouslyPressed = false;
                this.releaseStartedNanos = nowNanos;
                this.releaseFromPose = this.lastBasePose;
                float heldShare = clamp01((float)elapsed(
                        this.pressStartedNanos, nowNanos) / 180_000_000.0F);
                // A quick tap has less stored energy than a long press.
                this.releaseStrength = 0.65F + 0.35F * heldShare;
            }
            long releaseElapsed = this.releaseStartedNanos == 0L
                    ? RELEASE_SETTLE_NANOS
                    : elapsed(this.releaseStartedNanos, nowNanos);
            if (releaseElapsed < RELEASE_SETTLE_NANOS) {
                pose = releasePose(releaseElapsed, this.releaseStrength,
                        this.releaseFromPose);
            } else {
                this.releaseStartedNanos = 0L;
                pose = idlePose(nowNanos, keyCode);
            }
        }
        this.lastBasePose = pose;
        return addDirectionalAction(pose, keyCode);
    }

    static Pose staticPose() {
        return new Pose(IDLE_FRAME, 0.0F, 0.0F, 0.0F,
                1.0F, 1.0F, 1.0F,
                0.0F, 0.0F, 0.0F, 0.0F);
    }

    private static Pose pressedPose(
            long elapsedNanos, long nowNanos, int keyCode) {
        if (elapsedNanos < PRESS_IMPACT_NANOS) {
            float progress = easeOutCubic((float)elapsedNanos
                    / (float)PRESS_IMPACT_NANOS);
            return new Pose(PRESSED_FRAME, 0.0F, 0.72F * progress, 0.0F,
                    1.0F + 0.045F * progress,
                    1.0F - 0.080F * progress,
                    1.0F + 0.065F * progress,
                    0.0F, lerp(0.58F, 0.12F, progress),
                    lerp(0.10F, 0.18F, progress), progress);
        }
        if (elapsedNanos < PRESS_SETTLE_NANOS) {
            float progress = (float)(elapsedNanos - PRESS_IMPACT_NANOS)
                    / (float)(PRESS_SETTLE_NANOS - PRESS_IMPACT_NANOS);
            float ring = (float)Math.exp(-4.0F * progress)
                    * (float)Math.cos(progress * Math.PI * 2.0D);
            return new Pose(PRESSED_FRAME, 0.0F,
                    0.52F + 0.20F * ring, -0.28F * ring,
                    1.012F + 0.033F * ring,
                    0.974F - 0.054F * ring,
                    1.035F + 0.030F * Math.abs(ring),
                    0.0F, 0.16F + 0.04F * ring,
                    0.16F + 0.02F * Math.abs(ring), 1.0F);
        }

        // Layered, deterministic pressure jitter. Non-matching frequencies
        // keep it from tracing the vertical line that a single sine produced,
        // while the envelope prevents a jump out of the impact settle.
        long heldNanos = elapsedNanos - PRESS_SETTLE_NANOS;
        float envelope = smoothStep((float)heldNanos
                / (float)HELD_JITTER_RAMP_NANOS);
        double phase = phase(nowNanos, HELD_JITTER_PERIOD_NANOS, keyCode);
        float jitterX = envelope * (0.18F * (float)Math.sin(phase)
                + 0.08F * (float)Math.sin(phase * 1.91D + 0.7D));
        float jitterY = envelope * (0.11F
                * (float)Math.sin(phase * 1.37D + 1.3D)
                + 0.05F * (float)Math.sin(phase * 2.83D + 0.2D));
        float rotation = envelope * (0.28F
                * (float)Math.sin(phase * 0.83D + 0.4D)
                + 0.12F * (float)Math.sin(phase * 1.73D + 1.1D));
        float pressureWave = (float)Math.sin(phase * 0.71D + 0.9D);
        return new Pose(PRESSED_FRAME,
                jitterX, 0.52F + jitterY, rotation,
                1.014F + 0.0025F * pressureWave,
                0.972F - 0.0025F * pressureWave,
                1.040F + 0.005F * pressureWave,
                -jitterX * 0.20F,
                0.16F - jitterY * 0.18F,
                0.16F, 1.0F);
    }

    private static Pose releasePose(
            long elapsedNanos, float strength, Pose fromPose) {
        Pose from = fromPose == null
                ? new Pose(PRESSED_FRAME, 0.0F, 0.52F, 0.0F,
                        1.012F, 0.974F, 1.037F,
                        0.0F, 0.16F, 0.16F, 1.0F)
                : fromPose;
        if (elapsedNanos < RELEASE_FRAME_NANOS) {
            float progress = smoothStep((float)elapsedNanos
                    / (float)RELEASE_FRAME_NANOS);
            return new Pose(RELEASE_FRAME,
                    lerp(from.offsetX, 0.0F, progress),
                    lerp(from.offsetY, -0.42F * strength, progress),
                    lerp(from.rotationDegrees, -1.25F * strength, progress),
                    lerp(from.scaleX,
                            1.0F - 0.028F * strength, progress),
                    lerp(from.scaleY,
                            1.0F + 0.050F * strength, progress),
                    lerp(from.brightness,
                            1.0F + 0.075F * strength, progress),
                    lerp(from.shadowOffsetX, 0.0F, progress),
                    lerp(from.shadowOffsetY, 1.15F, progress),
                    lerp(from.shadowAlpha, 0.12F, progress),
                    lerp(from.pressure, 0.0F, progress));
        }

        float progress = (float)(elapsedNanos - RELEASE_FRAME_NANOS)
                / (float)(RELEASE_SETTLE_NANOS - RELEASE_FRAME_NANOS);
        float decay = (float)Math.exp(-4.2F * progress);
        float ring = decay
                * (float)Math.cos(progress * Math.PI * 2.3D);
        return new Pose(IDLE_FRAME, 0.0F,
                -0.42F * strength * ring, -1.25F * strength * ring,
                1.0F - 0.028F * strength * ring,
                1.0F + 0.050F * strength * ring,
                1.0F + 0.075F * strength * decay,
                0.0F, 0.58F + 0.57F * decay,
                0.10F + 0.02F * decay, 0.0F);
    }

    private static Pose idlePose(long nowNanos, int keyCode) {
        double phase = phase(nowNanos, IDLE_MOTION_PERIOD_NANOS, keyCode);
        float wave = (float)Math.sin(phase);
        return new Pose(IDLE_FRAME, 0.0F, 0.035F * wave, 0.0F,
                1.0F + 0.0015F * wave,
                1.0F - 0.0015F * wave,
                1.003F + 0.003F * wave,
                0.0F, 0.58F + 0.03F * wave,
                0.10F, 0.0F);
    }

    private static Pose addDirectionalAction(Pose pose, int keyCode) {
        float directionX = 0.0F;
        float directionY = 0.0F;
        switch (keyCode) {
            case Keyboard.KEY_LEFT:
                directionX = -1.0F;
                break;
            case Keyboard.KEY_RIGHT:
                directionX = 1.0F;
                break;
            case Keyboard.KEY_UP:
                directionY = -1.0F;
                break;
            case Keyboard.KEY_DOWN:
                directionY = 1.0F;
                break;
            default:
                return pose;
        }
        float travelX = directionX * 0.42F * pose.pressure;
        float travelY = directionY * 0.42F * pose.pressure;
        return new Pose(pose.frame,
                pose.offsetX + travelX, pose.offsetY + travelY,
                pose.rotationDegrees,
                pose.scaleX, pose.scaleY, pose.brightness,
                pose.shadowOffsetX - travelX * 0.28F,
                pose.shadowOffsetY - travelY * 0.28F,
                pose.shadowAlpha, pose.pressure);
    }

    private void resetTransitions() {
        this.previouslyPressed = false;
        this.pressStartedNanos = 0L;
        this.releaseStartedNanos = 0L;
        this.releaseStrength = 1.0F;
        this.lastBasePose = null;
        this.releaseFromPose = null;
    }

    private static double phase(long nowNanos, long periodNanos, int keyCode) {
        double time = (double)(nowNanos % periodNanos) / (double)periodNanos;
        double keyPhase = (double)((keyCode * 37) & 255) / 256.0D;
        return (time + keyPhase) * TWO_PI;
    }

    private static float easeOutCubic(float value) {
        return LostTalesGuiEasing.easeOutCubic(value);
    }

    private static float smoothStep(float value) {
        return LostTalesGuiEasing.smoothStep(value);
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    private static float clamp01(float value) {
        return LostTalesGuiEasing.clamp(value);
    }

    private static long elapsed(long earlier, long later) {
        return later <= earlier ? 0L : later - earlier;
    }

    static final class Pose {
        private final int frame;
        private final float offsetX;
        private final float offsetY;
        private final float rotationDegrees;
        private final float scaleX;
        private final float scaleY;
        private final float brightness;
        private final float shadowOffsetX;
        private final float shadowOffsetY;
        private final float shadowAlpha;
        private final float pressure;

        private Pose(int frame, float offsetX, float offsetY,
                     float rotationDegrees,
                     float scaleX, float scaleY, float brightness,
                     float shadowOffsetX, float shadowOffsetY,
                     float shadowAlpha, float pressure) {
            this.frame = frame;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.rotationDegrees = rotationDegrees;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.brightness = brightness;
            this.shadowOffsetX = shadowOffsetX;
            this.shadowOffsetY = shadowOffsetY;
            this.shadowAlpha = shadowAlpha;
            this.pressure = pressure;
        }

        int getFrame() {
            return this.frame;
        }

        float getOffsetX() {
            return this.offsetX;
        }

        float getOffsetY() {
            return this.offsetY;
        }

        float getRotationDegrees() {
            return this.rotationDegrees;
        }

        float getScaleX() {
            return this.scaleX;
        }

        float getScaleY() {
            return this.scaleY;
        }

        float getBrightness() {
            return this.brightness;
        }

        float getShadowOffsetX() {
            return this.shadowOffsetX;
        }

        float getShadowOffsetY() {
            return this.shadowOffsetY;
        }

        float getShadowAlpha() {
            return this.shadowAlpha;
        }

        float getPressure() {
            return this.pressure;
        }
    }
}
