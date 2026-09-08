package com.ninuna.losttales.gui.screen.character.creator;

/**
 * How a page of the creator frames the character before the player
 * touches the camera: from which side, how near, and which part of the
 * body sits at the middle of the stage.
 *
 * <p>A page about the face wants the head and shoulders; a page about
 * the body or its clothes wants all of it; a page about the cape wants
 * the back. The player's own drag and wheel are laid on top of the shot,
 * and the shot eases from one to the next as the pages turn.</p>
 */
public enum CharacterCreatorShot {

    /** The whole figure, seen from the front. */
    FULL_FRONT(0.0F, 1.0F, 0.5F),
    /** Head and shoulders, seen from the front. */
    UPPER_FRONT(0.0F, 2.1F, 0.74F),
    /** The whole figure, seen from behind. */
    FULL_BACK(180.0F, 1.0F, 0.5F);

    private final float yaw;
    private final float zoom;
    private final float focus;

    CharacterCreatorShot(float yaw, float zoom, float focus) {
        this.yaw = yaw;
        this.zoom = zoom;
        this.focus = focus;
    }

    /** Degrees the camera is round from the front; 180 is behind. */
    public float getYaw() {
        return this.yaw;
    }

    /** How much nearer than the fitted distance the camera stands. */
    public float getZoom() {
        return this.zoom;
    }

    /** The point on the body, from the feet at zero to the head at one, that sits at the middle of the stage. */
    public float getFocus() {
        return this.focus;
    }
}
