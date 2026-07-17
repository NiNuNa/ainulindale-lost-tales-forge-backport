package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.client.camera.DirectionalMovementMath;

/** Pure wrap-safe and frame-rate-independent cape motion math. */
final class CapeMotionMath {
    // Matches the camera state model: supports genuine 4 FPS rendering while
    // preventing pause menus or debugger stops from becoming one giant step.
    private static final float MAXIMUM_DELTA_SECONDS = 0.25F;

    private CapeMotionMath() {}

    static float interpolateDegrees(
            float previous, float current, float partialTicks) {
        if (!Float.isFinite(previous) || !Float.isFinite(current)) {
            return 0.0F;
        }
        float partial = clamp(partialTicks, 0.0F, 1.0F);
        float difference = DirectionalMovementMath.wrapDegrees(
                current - previous);
        return DirectionalMovementMath.wrapDegrees(
                previous + difference * partial);
    }

    static float damp(
            float current, float target,
            float responseSpeed, float deltaSeconds) {
        if (!Float.isFinite(target)) {
            return Float.isFinite(current) ? current : 0.0F;
        }
        if (!Float.isFinite(current)) {
            return target;
        }
        float speed = Float.isFinite(responseSpeed)
                ? Math.max(0.0F, responseSpeed) : 0.0F;
        float delta = Float.isFinite(deltaSeconds)
                ? clamp(deltaSeconds, 0.0F, MAXIMUM_DELTA_SECONDS)
                : 0.0F;
        float alpha = 1.0F
                - (float)Math.exp(-(double)speed * (double)delta);
        return current + (target - current) * alpha;
    }

    static float clamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        return Math.max(minimum, Math.min(value, maximum));
    }
}
