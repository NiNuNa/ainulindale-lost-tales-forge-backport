package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.client.camera.CameraMath;
import com.ninuna.losttales.client.motion.Motions;

/**
 * How the character on the creator's stage is framed, and how it answers
 * the mouse.
 *
 * <p>Two things are laid on top of each other. The page's shot says where
 * the camera starts: from the front or the back, near or far, looking at
 * the face or the whole body. The player's own handling is laid over it:
 * a drag with the left button turns and tilts as if taking hold of the
 * figure, a drag with the right button slides it across the stage, the
 * wheel brings it nearer or farther, and a reset lets go of all of that
 * and leaves the page's shot alone.</p>
 *
 * <p>Nothing shown jumps. What is asked for and what is shown are kept
 * apart, and what is shown eases toward what is asked for every frame:
 * quickly for the player's own handling, so a drag still feels held, and
 * slowly for the page's shot, so turning a page glides the camera to its
 * new place rather than cutting to it.</p>
 *
 * <p>Angles are degrees. Yaw is the figure's own turn about its feet; zero
 * faces the viewer and positive turns its front toward the viewer's
 * right. Pitch is the tilt of the whole figure about its middle; positive
 * leans the head toward the viewer, which is what a drag downward does.
 * Zoom is a multiplier on the figure's fitted size. The pan is in
 * interface pixels, positive to the right and down.</p>
 */
public final class CharacterStagePose {

    /** How far the figure may lean, either way. */
    public static final float PITCH_LIMIT = 30.0F;
    public static final float ZOOM_MIN = 0.6F;
    public static final float ZOOM_MAX = 2.4F;
    /** How far the figure may be slid off its place, in interface pixels. */
    public static final float PAN_LIMIT = 220.0F;
    /** The turn where the head stops following the pointer. */
    public static final float HEAD_FOLLOW_LIMIT = 60.0F;
    /** The turn past which the head has fully let the pointer go. */
    private static final float HEAD_FOLLOW_FADE_END = 120.0F;

    static final float DEGREES_PER_DRAG_PIXEL = 1.2F;
    private static final float PITCH_PER_DRAG_PIXEL = 0.45F;
    private static final float ZOOM_PER_NOTCH = 1.12F;
    /** The distance, in pixels, over which the head's turn eases off. */
    private static final float HEAD_FOLLOW_REACH = 40.0F;
    /**
     * How much of the angle the pointer subtends the head follows. The
     * inventory doll turns its head twenty radians-as-degrees per unit of
     * arc tangent and tilts it ten; these are the same ratios in degrees.
     */
    private static final float HEAD_TURN_SHARE = 0.7F;
    private static final float HEAD_TILT_SHARE = 0.35F;

    /**
     * How quickly what is shown catches up with what was asked, per
     * second. High for the player's handling, so a drag still feels held;
     * lower for the zoom, so a wheel notch glides; lowest for the page's
     * shot, so a turned page is a camera move rather than a cut.
     */
    private static final float HANDLING_RESPONSE = 28.0F;
    private static final float ZOOM_RESPONSE = 12.0F;
    private static final float SHOT_RESPONSE = 5.0F;
    /** A frame longer than this is a hitch, not motion to integrate over. */
    private static final float MAX_STEP_SECONDS = 0.1F;

    /** The player's handling, as asked for. */
    private float yaw;
    private float pitch;
    private float zoom = 1.0F;
    private float panX;
    private float panY;
    /** The player's handling, as shown. */
    private float shownYaw;
    private float shownPitch;
    private float shownZoom = 1.0F;
    private float shownPanX;
    private float shownPanY;
    /** The page's shot, as asked for and as shown. */
    private float shotYaw;
    private float shotZoom = 1.0F;
    private float shotFocus = 0.5F;
    private float shownShotYaw;
    private float shownShotZoom = 1.0F;
    private float shownShotFocus = 0.5F;
    private long lastAdvanceNanos;

    /** The player's own turn, apart from the page's shot. */
    public float getYaw() { return this.yaw; }
    public float getPitch() { return this.pitch; }
    /** The player's own zoom, apart from the page's shot. */
    public float getZoom() { return this.zoom; }
    public float getPanX() { return this.panX; }
    public float getPanY() { return this.panY; }
    public float getShotYaw() { return this.shotYaw; }
    public float getShotZoom() { return this.shotZoom; }
    public float getFocus() { return this.shotFocus; }

    /** The turn as drawn this frame: the page's shot and the player's turn together. */
    public float getShownYaw() {
        return wrap(this.shownShotYaw + this.shownYaw);
    }

    public float getShownPitch() { return this.shownPitch; }

    /** The zoom as drawn this frame: the page's shot and the player's zoom together. */
    public float getShownZoom() {
        return this.shownShotZoom * this.shownZoom;
    }

    /** The point on the body at the middle of the stage, from feet at zero to head at one. */
    public float getShownFocus() { return this.shownShotFocus; }
    public float getShownPanX() { return this.shownPanX; }
    public float getShownPanY() { return this.shownPanY; }

    /** The page turned: where its shot stands, which the camera glides to. */
    public void setShot(CharacterCreatorShot shot) {
        if (shot == null) {
            return;
        }
        this.shotYaw = shot.getYaw();
        this.shotZoom = shot.getZoom();
        this.shotFocus = shot.getFocus();
    }

    /**
     * The pointer moved that far while holding the figure, in fractional
     * pixels: what a window-pixel movement comes to in interface pixels.
     */
    public void drag(float deltaX, float deltaY) {
        this.yaw = wrap(this.yaw + deltaX * DEGREES_PER_DRAG_PIXEL);
        this.pitch = clamp(this.pitch + deltaY * PITCH_PER_DRAG_PIXEL,
                -PITCH_LIMIT, PITCH_LIMIT);
    }

    /** The pointer moved that far while sliding the figure across the stage. */
    public void pan(float deltaX, float deltaY) {
        this.panX = clamp(this.panX + deltaX, -PAN_LIMIT, PAN_LIMIT);
        this.panY = clamp(this.panY + deltaY, -PAN_LIMIT, PAN_LIMIT);
    }

    /** The wheel moved that many notches; forward brings the figure nearer. */
    public void wheel(int notches) {
        if (notches == 0) {
            return;
        }
        float factor = (float)Math.pow(ZOOM_PER_NOTCH, notches);
        this.zoom = clamp(this.zoom * factor, ZOOM_MIN, ZOOM_MAX);
    }

    /** Lets go of the player's handling; the page's shot stands as it was. */
    public void reset() {
        this.yaw = 0.0F;
        this.pitch = 0.0F;
        this.zoom = 1.0F;
        this.panX = 0.0F;
        this.panY = 0.0F;
        this.lastAdvanceNanos = 0L;
    }

    /**
     * Eases what is shown toward what was asked for, by the time since the
     * last call, at the motion speed in force. The first call, any call
     * after {@link #reset}, and every call while motion is off or reduced
     * shows what was asked for at once rather than easing toward it: the
     * camera's moves are travel.
     */
    public void advance(long nowNanos) {
        if (this.lastAdvanceNanos == 0L || !Motions.enabled()
                || Motions.reduced()) {
            this.lastAdvanceNanos = nowNanos;
            snap();
            return;
        }
        float seconds = (nowNanos - this.lastAdvanceNanos) / 1.0e9F;
        this.lastAdvanceNanos = nowNanos;
        if (seconds <= 0.0F) {
            return;
        }
        seconds = Math.min(seconds, MAX_STEP_SECONDS) * (float)Motions.speed();
        float handling = 1.0F - (float)Math.exp(-HANDLING_RESPONSE * seconds);
        float zooming = 1.0F - (float)Math.exp(-ZOOM_RESPONSE * seconds);
        float shot = 1.0F - (float)Math.exp(-SHOT_RESPONSE * seconds);
        this.shownYaw = wrap(this.shownYaw + wrap(this.yaw - this.shownYaw) * handling);
        this.shownPitch += (this.pitch - this.shownPitch) * handling;
        this.shownPanX += (this.panX - this.shownPanX) * handling;
        this.shownPanY += (this.panY - this.shownPanY) * handling;
        this.shownZoom += (this.zoom - this.shownZoom) * zooming;
        this.shownShotYaw = wrap(this.shownShotYaw
                + wrap(this.shotYaw - this.shownShotYaw) * shot);
        this.shownShotZoom += (this.shotZoom - this.shownShotZoom) * shot;
        this.shownShotFocus += (this.shotFocus - this.shownShotFocus) * shot;
    }

    private void snap() {
        this.shownYaw = this.yaw;
        this.shownPitch = this.pitch;
        this.shownZoom = this.zoom;
        this.shownPanX = this.panX;
        this.shownPanY = this.panY;
        this.shownShotYaw = this.shotYaw;
        this.shownShotZoom = this.shotZoom;
        this.shownShotFocus = this.shotFocus;
    }

    /**
     * How far the head turns toward a pointer that far to the side of it.
     *
     * @param pointerOffsetX pixels from the head to the pointer, positive
     *                       when the pointer is to the viewer's right
     */
    public float headYawToward(float pointerOffsetX) {
        // The angle the pointer subtends, then less the figure's own turn:
        // a head turned to face the pointer while the body faces it too
        // is not turned at all.
        float toward = (float)Math.toDegrees(
                Math.atan(pointerOffsetX / HEAD_FOLLOW_REACH)) * HEAD_TURN_SHARE;
        float relative = wrap(toward - getShownYaw());
        float magnitude = Math.abs(relative);
        if (magnitude >= HEAD_FOLLOW_FADE_END) {
            return 0.0F;
        }
        float limited = clamp(relative, -HEAD_FOLLOW_LIMIT, HEAD_FOLLOW_LIMIT);
        if (magnitude <= HEAD_FOLLOW_LIMIT) {
            return limited;
        }
        // Between the limit and the end the follow eases out to nothing,
        // so turning the figure away never snaps the head.
        float fade = (HEAD_FOLLOW_FADE_END - magnitude)
                / (HEAD_FOLLOW_FADE_END - HEAD_FOLLOW_LIMIT);
        return limited * fade;
    }

    /**
     * How far the head tilts toward a pointer that far below it.
     *
     * @param pointerOffsetY pixels from the head to the pointer, positive
     *                       when the pointer is below
     */
    public float headPitchToward(float pointerOffsetY) {
        if (Math.abs(getShownYaw()) >= HEAD_FOLLOW_FADE_END) {
            return 0.0F;
        }
        return (float)Math.toDegrees(
                Math.atan(pointerOffsetY / HEAD_FOLLOW_REACH)) * HEAD_TILT_SHARE;
    }

    /** Degrees brought into the half-open range from -180 to 180. */
    public static float wrap(float degrees) {
        return (float)CameraMath.wrapDegrees(degrees);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : value > max ? max : value;
    }
}
