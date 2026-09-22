package com.ninuna.losttales.client.motion;

import java.util.Locale;

/**
 * One way a part can change over a motion. What each value means is the
 * drawing code's: a part moves, turns and stretches in GUI pixels and
 * degrees about its own place, and fades and brightens by shares of the
 * way to its full effect.
 *
 * <p>A <em>travel</em> track moves a part through space. Reduced motion
 * sets a travel track down at its end at once, and pixel art only ever
 * travels: a flat surface may stretch, text stretches by opening the
 * gaps between its words, and a sprite that should squash needs drawn
 * frames.</p>
 */
public enum MotionTrack {
    /** Sideways, in GUI pixels; right is positive. */
    X("x", 0.0F, -256.0F, 256.0F, true),
    /** Up and down, in GUI pixels; down is positive. */
    Y("y", 0.0F, -256.0F, 256.0F, true),
    /** Stretched across, as a factor of the part's width; surfaces only. */
    STRETCH_X("stretch_x", 1.0F, 0.25F, 4.0F, true),
    /** Stretched up, as a factor of the part's height; surfaces only. */
    STRETCH_Y("stretch_y", 1.0F, 0.25F, 4.0F, true),
    /** Turned about the part's middle, in degrees; clockwise is positive. */
    TURN("turn", 0.0F, -360.0F, 360.0F, true),
    /**
     * Extra room a row of words spreads across its gaps, in GUI pixels:
     * how text stretches.
     */
    GAP("gap", 0.0F, -16.0F, 16.0F, true),
    /** How much of the part shows, 0 to 1. */
    FADE("fade", 1.0F, 0.0F, 1.0F, false),
    /** How far the part has lit up, 0 to 1. */
    BRIGHTEN("brighten", 0.0F, 0.0F, 1.0F, false),
    /** A share the code gives its own meaning, usually 0 off and 1 on. */
    VALUE("value", 0.0F, -2.0F, 3.0F, false);

    private final String key;
    private final float neutral;
    private final float least;
    private final float most;
    private final boolean travel;

    MotionTrack(String key, float neutral, float least, float most,
                boolean travel) {
        this.key = key;
        this.neutral = neutral;
        this.least = least;
        this.most = most;
        this.travel = travel;
    }

    /** The name a motion file writes the track by. */
    public String key() {
        return this.key;
    }

    /** The value of a track no pose names: where a part stands untouched. */
    public float neutral() {
        return this.neutral;
    }

    /** Whether the track moves a part through space. */
    public boolean isTravel() {
        return this.travel;
    }

    /** The value kept inside the track's bounds. */
    public float bounded(float value) {
        if (Float.isNaN(value)) {
            return this.neutral;
        }
        return Math.max(this.least, Math.min(this.most, value));
    }

    /** The widest single swing the track allows, for bumps and rings. */
    float reach() {
        return this.most - this.least;
    }

    /** The track a file names, or null. */
    public static MotionTrack parse(String key) {
        if (key == null) {
            return null;
        }
        String wanted = key.trim().toLowerCase(Locale.ROOT);
        for (MotionTrack track : values()) {
            if (track.key.equals(wanted)) {
                return track;
            }
        }
        return null;
    }
}
