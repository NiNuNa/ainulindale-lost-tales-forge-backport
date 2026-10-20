package com.ninuna.losttales.progression;

/**
 * Skyrim's character level (PROG-04): every skill level reached adds its
 * own number to the character's progress, and the next character level
 * needs {@code base + step * level}. There is no cap. A level a race
 * starts a skill at adds nothing (PROG-05).
 */
public final class CharacterLevelRule {
    /** The highest level kept, far past anything eleven skills reach. */
    public static final int MAX_LEVEL = 10000;

    private final int base;
    private final int step;

    public CharacterLevelRule(int base, int step) {
        if (base < 1 || step < 0) {
            throw new IllegalArgumentException("a character level needs a"
                    + " base of at least 1 and a step of 0 or more");
        }
        this.base = base;
        this.step = step;
    }

    /** The progress that takes a character from {@code level} to the next. */
    public long needed(int level) {
        return (long) this.base + (long) this.step * (long) Math.max(1, level);
    }

    /** Where a character stands after {@code amount} more progress. */
    public Standing add(int level, long into, long amount) {
        int at = Math.max(1, Math.min(MAX_LEVEL, level));
        long held = Math.max(0L, into) + Math.max(0L, amount);
        while (at < MAX_LEVEL && held >= needed(at)) {
            held -= needed(at);
            at++;
        }
        return new Standing(at, at >= MAX_LEVEL ? 0L : held);
    }

    /** What reaching {@code level} in a skill adds to the character. */
    public static long progressFor(int skillLevel) {
        return Math.max(0, skillLevel);
    }

    /** A level and the progress gathered toward the next. */
    public static final class Standing {
        public final int level;
        public final long into;

        public Standing(int level, long into) {
            this.level = level;
            this.into = into;
        }
    }
}
