package com.ninuna.losttales.client.render.player;

/**
 * A small spring-damper for the chest: a sideways sway and a vertical
 * bounce, both in model pixels, that lag behind the body and settle again.
 *
 * Each tick the body's motion becomes a target the chest is pulled towards,
 * the spring pulls the displacement back to rest, and damping bleeds the
 * velocity off. Everything is bounded so a teleport or a lag spike cannot
 * fling the chest anywhere. Pure math; the model feeds it and reads it.
 */
public final class ChestPhysics {

    /** Largest displacement in either axis, in model pixels. */
    public static final float MAX_DISPLACEMENT = 0.75F;
    private static final float STIFFNESS = 0.32F;
    private static final float DAMPING = 0.42F;
    private static final float INPUT_GAIN = 0.55F;
    private static final float VERTICAL_MOTION_GAIN = 3.0F;
    private static final float WALK_GAIN = 0.35F;
    private static final float TURN_GAIN = 0.045F;
    private static final float SNEAK_IMPULSE = 0.35F;
    private static final float MAX_VELOCITY = 0.5F;
    private static final float REST_EPSILON = 0.0005F;

    private float positionX;
    private float positionY;
    private float previousX;
    private float previousY;
    private float velocityX;
    private float velocityY;

    /**
     * Advances one tick.
     *
     * @param verticalMotion  the body's vertical movement this tick, in blocks
     * @param walkCycle       signed walk-cycle sample scaled by swing amount
     * @param turnDegrees     change of the body yaw this tick, in degrees
     * @param sneakChange     +1 when the body just crouched, -1 when it stood up, else 0
     * @param bounce          strength from the config, 0 to 1
     */
    public void tick(float verticalMotion, float walkCycle, float turnDegrees,
                     int sneakChange, float bounce) {
        this.previousX = this.positionX;
        this.previousY = this.positionY;
        float gain = INPUT_GAIN * clamp(bounce, 0.0F, 1.0F);
        // Moving up leaves the chest behind, so the target is the inverse.
        float targetY = clamp(-verticalMotion * VERTICAL_MOTION_GAIN
                + walkCycle * WALK_GAIN
                - sneakChange * SNEAK_IMPULSE, -1.5F, 1.5F) * gain;
        float targetX = clamp(-turnDegrees * TURN_GAIN, -1.0F, 1.0F) * gain;

        this.velocityX += (targetX - this.positionX) * STIFFNESS - this.velocityX * DAMPING;
        this.velocityY += (targetY - this.positionY) * STIFFNESS - this.velocityY * DAMPING;
        this.velocityX = clamp(this.velocityX, -MAX_VELOCITY, MAX_VELOCITY);
        this.velocityY = clamp(this.velocityY, -MAX_VELOCITY, MAX_VELOCITY);
        this.positionX = clamp(this.positionX + this.velocityX,
                -MAX_DISPLACEMENT, MAX_DISPLACEMENT);
        this.positionY = clamp(this.positionY + this.velocityY,
                -MAX_DISPLACEMENT, MAX_DISPLACEMENT);
        if (isNegligible(this.positionX) && isNegligible(this.velocityX)
                && targetX == 0.0F) {
            this.positionX = 0.0F;
            this.velocityX = 0.0F;
        }
        if (isNegligible(this.positionY) && isNegligible(this.velocityY)
                && targetY == 0.0F) {
            this.positionY = 0.0F;
            this.velocityY = 0.0F;
        }
    }

    /** Sideways displacement between the last two ticks, in model pixels. */
    public float swayX(float partialTick) {
        return this.previousX + (this.positionX - this.previousX) * clamp(partialTick, 0.0F, 1.0F);
    }

    /** Vertical displacement between the last two ticks, in model pixels. */
    public float bounceY(float partialTick) {
        return this.previousY + (this.positionY - this.previousY) * clamp(partialTick, 0.0F, 1.0F);
    }

    public boolean isAtRest() {
        return this.positionX == 0.0F && this.positionY == 0.0F
                && this.velocityX == 0.0F && this.velocityY == 0.0F;
    }

    private static boolean isNegligible(float value) {
        return Math.abs(value) < REST_EPSILON;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return value < minimum ? minimum : value > maximum ? maximum : value;
    }
}
