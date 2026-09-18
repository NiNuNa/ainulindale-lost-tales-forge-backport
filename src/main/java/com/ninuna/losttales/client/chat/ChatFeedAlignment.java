package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.config.LostTalesConfig;

/**
 * Which edge the closed-chat feed's lines stand against: the left, as an
 * open window's do, the right, or the middle. Only where a line stands in
 * the feed's box moves — each keeps its own shape, the channel before the
 * sender and the chevron before the words, and a message wrapped over
 * several rows stands each row on the same edge — and every line's band
 * thins out away from where the lines stand: from the left edge
 * rightward, from the right edge leftward, or from the middle to both
 * sides. The choice is the client's own ({@code chatFeedAlignment}).
 */
enum ChatFeedAlignment {
    LEFT,
    CENTRE,
    RIGHT;

    /** The band thinning out from the right edge: the left band turned round. */
    private static final float[] FROM_RIGHT = reversed(
            LostTalesChatOverlayRenderer.BACKDROP_FADE_WEIGHTS);
    /** The band thinning out from the middle, the left band's curve to either side. */
    private static final float[] FROM_MIDDLE = fromMiddle(
            LostTalesChatOverlayRenderer.BACKDROP_FADE_WEIGHTS);

    /** The alignment the client chose. */
    static ChatFeedAlignment current() {
        return of(LostTalesConfig.chatFeedAlignment);
    }

    /**
     * The alignment a config value names, read as the config reads it
     * ({@link LostTalesConfig#normalizeFeedAlignment}): the middle for
     * anything it does not name.
     */
    static ChatFeedAlignment of(String name) {
        return valueOf(LostTalesConfig.normalizeFeedAlignment(name));
    }

    /**
     * How far a row is moved sideways, in the feed's own units, so that
     * its ink — from {@code inkLeft} to {@code inkRight} in the row's
     * text space — stands against this alignment's edge of a text area
     * {@code areaWidth} wide that starts at zero: nowhere at the left,
     * its last pixel against the area's right edge, or its middle on the
     * area's middle. The area is the one a left-aligned line fills, so a
     * right-aligned line keeps the same clear pixels from the band's edge
     * a left-aligned one keeps from the other.
     */
    float rowShift(float areaWidth, float inkLeft, float inkRight) {
        switch (this) {
            case RIGHT:
                return areaWidth - inkRight;
            case CENTRE:
                return (areaWidth - inkLeft - inkRight) / 2.0F;
            default:
                return 0.0F;
        }
    }

    /**
     * The sideways part of a new line's entrance for a line that would
     * slide in by {@code leftSlide} at the left: from the right edge
     * for lines standing there, and none for centred ones, which rise
     * and fade in where they stand.
     */
    float slide(float leftSlide) {
        switch (this) {
            case RIGHT:
                return -leftSlide;
            case CENTRE:
                return 0.0F;
            default:
                return leftSlide;
        }
    }

    /**
     * The band's opacity profile, sampled evenly from its left edge to
     * its right, as the backdrop and the blur behind it draw it.
     */
    float[] bandWeights() {
        switch (this) {
            case RIGHT:
                return FROM_RIGHT;
            case CENTRE:
                return FROM_MIDDLE;
            default:
                return LostTalesChatOverlayRenderer.BACKDROP_FADE_WEIGHTS;
        }
    }

    /**
     * Whether a mention wears its bar: on the edge the lines stand
     * against, which centred lines do not have.
     */
    boolean hasMentionBar() {
        return this != CENTRE;
    }
    static float[] reversed(float[] weights) {
        float[] turned = new float[weights.length];
        for (int index = 0; index < weights.length; index++) {
            turned[index] = weights[weights.length - 1 - index];
        }
        return turned;
    }

    /**
     * The profile from the middle outward: the left band's samples, from
     * its strongest edge on, laid out once to the left of the middle and
     * once to the right, so the curve and its steps are the same.
     */
    static float[] fromMiddle(float[] weights) {
        int half = weights.length - 1;
        float[] middle = new float[half * 2 + 1];
        for (int index = 0; index < middle.length; index++) {
            middle[index] = weights[Math.abs(index - half)];
        }
        return middle;
    }
}
