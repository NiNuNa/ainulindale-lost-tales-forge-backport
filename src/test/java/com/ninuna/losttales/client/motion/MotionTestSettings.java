package com.ninuna.losttales.client.motion;

import com.ninuna.losttales.config.LostTalesConfig;

/**
 * The motion settings a test runs under, and the motions it plays in
 * place of the mod's own: set with {@link #reset}, put back with
 * {@link #restore}.
 */
public final class MotionTestSettings {
    private final boolean animations = LostTalesConfig.animations;
    private final double speed = LostTalesConfig.animationSpeed;
    private final boolean reduced = LostTalesConfig.reducedMotion;

    /** Motion on, at its own speed, not reduced, every preview cleared. */
    public static MotionTestSettings reset() {
        MotionTestSettings saved = new MotionTestSettings();
        LostTalesConfig.animations = true;
        LostTalesConfig.animationSpeed = 1.0D;
        LostTalesConfig.reducedMotion = false;
        Motions.clearPreviews();
        return saved;
    }

    /** Plays the motions a file's text holds in place of the mod's. */
    public static void preview(String json) {
        MotionCodec.Result result = MotionCodec.read(json);
        if (!result.problems().isEmpty()) {
            throw new IllegalArgumentException(result.problems().toString());
        }
        for (Motion motion : result.motions().values()) {
            Motions.preview(motion);
        }
    }

    public void restore() {
        LostTalesConfig.animations = this.animations;
        LostTalesConfig.animationSpeed = this.speed;
        LostTalesConfig.reducedMotion = this.reduced;
        Motions.clearPreviews();
    }
}
