package com.ninuna.losttales.progression;

/** One skill of one character: its level and the experience gathered toward the next. */
public final class SkillStanding {
    private final int level;
    private final double into;

    public SkillStanding(int level, double into) {
        if (level < SkillCurve.FIRST_LEVEL || level > SkillCurve.TOP_LEVEL
                || Double.isNaN(into) || Double.isInfinite(into) || into < 0.0D) {
            throw new IllegalArgumentException("a skill stands between level "
                    + SkillCurve.FIRST_LEVEL + " and " + SkillCurve.TOP_LEVEL
                    + " with experience of 0 or more");
        }
        this.level = level;
        this.into = level >= SkillCurve.TOP_LEVEL ? 0.0D : into;
    }

    public static SkillStanding start(int level) {
        return new SkillStanding(Math.max(SkillCurve.FIRST_LEVEL,
                Math.min(SkillCurve.TOP_LEVEL, level)), 0.0D);
    }

    public int getLevel() {
        return this.level;
    }

    public double getInto() {
        return this.into;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SkillStanding)) {
            return false;
        }
        SkillStanding standing = (SkillStanding) other;
        return this.level == standing.level
                && Double.compare(this.into, standing.into) == 0;
    }

    @Override
    public int hashCode() {
        long bits = Double.doubleToLongBits(this.into);
        return 31 * this.level + (int) (bits ^ (bits >>> 32));
    }
}
