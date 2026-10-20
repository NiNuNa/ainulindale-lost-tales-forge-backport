package com.ninuna.losttales.progression;

/**
 * How much experience each skill level takes (PROG-03). Level 2 needs
 * {@code first}, and every level after needs {@code growthPercent} more
 * than the one before, so the early levels come quickly and the last
 * ones take months. Both numbers are server settings.
 */
public final class SkillCurve {
    public static final int FIRST_LEVEL = 1;
    public static final int TOP_LEVEL = 100;

    private final double first;
    private final double growth;

    public SkillCurve(double first, double growthPercent) {
        if (!(first > 0.0D) || Double.isInfinite(first)
                || !(growthPercent >= 0.0D) || Double.isInfinite(growthPercent)) {
            throw new IllegalArgumentException("a skill curve needs a first"
                    + " level above 0 and a growth of 0 or more");
        }
        this.first = first;
        this.growth = 1.0D + growthPercent / 100.0D;
    }

    /** The experience that takes a skill from {@code level} to the next; 0 at the top. */
    public double needed(int level) {
        if (level < FIRST_LEVEL || level >= TOP_LEVEL) {
            return 0.0D;
        }
        return this.first * Math.pow(this.growth, level - 1);
    }

    /** The experience from level 1 to {@code level}. */
    public double total(int level) {
        double total = 0.0D;
        int top = Math.min(level, TOP_LEVEL);
        for (int step = FIRST_LEVEL; step < top; step++) {
            total += needed(step);
        }
        return total;
    }

    /**
     * Where a skill stands after {@code amount} more experience: its
     * level and the experience into that level. A skill at the top keeps
     * no experience.
     */
    public Standing add(int level, double into, double amount) {
        int at = Math.max(FIRST_LEVEL, Math.min(TOP_LEVEL, level));
        double held = Math.max(0.0D, into) + Math.max(0.0D, amount);
        while (at < TOP_LEVEL && held >= needed(at)) {
            held -= needed(at);
            at++;
        }
        return new Standing(at, at >= TOP_LEVEL ? 0.0D : held);
    }

    /** A level and the experience gathered toward the next. */
    public static final class Standing {
        public final int level;
        public final double into;

        public Standing(int level, double into) {
            this.level = level;
            this.into = into;
        }
    }
}
