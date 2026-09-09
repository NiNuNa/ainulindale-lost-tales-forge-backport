package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.character.validation.CharacterValidator;

/**
 * How the age slider's track maps to years.
 *
 * <p>Most characters are somewhere between one and a few hundred years
 * old, and an elf may be thousands. One straight track cannot serve both:
 * a track that reaches ten thousand puts every mortal age in its first
 * few pixels. So the track bends. Its first stretch runs one to
 * nine-hundred-and-ninety-nine a year at a time, and the last stretch
 * past the knee climbs by ever larger steps to the oldest age the slider
 * reaches. The end of the track reads {@code 999+}; the readout beside it
 * always shows the exact number.</p>
 *
 * <p>Pure arithmetic, so the screen that owns the slider stays thin and
 * the mapping can be proven without a screen.</p>
 */
public final class AgeSliderScale {

    /** The youngest a character may be; the left end of the track. */
    public static final int MIN = CharacterValidator.MIN_AGE;
    /** The last year the straight stretch of the track reaches. */
    public static final int KNEE = 999;
    /** The oldest age the slider reaches; typing allows more. */
    public static final int MAX = 10000;
    /** How much of the track the straight stretch takes. */
    public static final float KNEE_POSITION = 0.8F;

    private AgeSliderScale() {}

    /** Where along the track, from zero to one, that age sits. */
    public static float positionOf(int age) {
        int clamped = clamp(age);
        if (clamped <= KNEE) {
            return (clamped - MIN) / (float)(KNEE - MIN) * KNEE_POSITION;
        }
        double span = Math.log((double)MAX / KNEE);
        double along = Math.log((double)clamped / KNEE) / span;
        return (float)(KNEE_POSITION + along * (1.0F - KNEE_POSITION));
    }

    /** The age at that point of the track, from zero to one. */
    public static int ageAt(float position) {
        float along = position < 0.0F ? 0.0F : position > 1.0F ? 1.0F : position;
        if (along <= KNEE_POSITION) {
            return MIN + Math.round(along / KNEE_POSITION * (KNEE - MIN));
        }
        double fraction = (along - KNEE_POSITION) / (1.0F - KNEE_POSITION);
        double age = KNEE * Math.pow((double)MAX / KNEE, fraction);
        return clamp((int)Math.round(age));
    }

    /**
     * The next age a nudge lands on. A nudge is a year while the track is
     * straight and ten past the knee; a coarse nudge is ten times that.
     */
    public static int nudge(int age, int direction, boolean coarse) {
        int current = clamp(age);
        int step = (current > KNEE || (direction > 0 && current == KNEE))
                ? 10 : 1;
        if (coarse) {
            step *= 10;
        }
        if (age > MAX) {
            // Typed past the track's reach: a nudge down steps down from
            // where the age is, and a nudge up has nowhere to go.
            return direction < 0 ? Math.max(MAX, age - step) : age;
        }
        if (direction > 0 && current < KNEE && current + step > KNEE) {
            // The knee is a stop on the way up, not something to leap.
            return KNEE;
        }
        return clamp(current + Integer.signum(direction) * step);
    }

    /** The age kept within what the slider reaches. */
    public static int clamp(int age) {
        return age < MIN ? MIN : age > MAX ? MAX : age;
    }
}
