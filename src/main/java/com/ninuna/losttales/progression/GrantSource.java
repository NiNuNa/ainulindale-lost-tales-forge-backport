package com.ninuna.losttales.progression;

/**
 * Where skill experience comes from (PROG-08): an act a skill's file
 * counts, a quest's skill pay, or an operator's {@code give}. Only what
 * acts earn is held to the hourly cap; quest pay and a give go past it and
 * take nothing from it.
 */
public enum GrantSource {
    /** Something the character did, as a skill's file counts it. */
    ACT(true),
    /** A quest's reward in skill experience (QUEST-19). */
    QUEST(false),
    /** An operator's {@code /losttales skills give}. */
    GIVE(false);

    private final boolean capped;

    GrantSource(boolean capped) {
        this.capped = capped;
    }

    /** Whether the hourly cap holds it back. */
    public boolean isCapped() {
        return this.capped;
    }

    /**
     * How much of {@code amount} the skill gains: for an act, what the
     * hour still allows under {@code cap} (0 for no cap), counted against
     * it; for anything else, all of it.
     */
    public double allow(RepeatMemory memory, String skillId, double amount, int cap,
                        long nowMillis) {
        if (!this.capped) {
            return amount;
        }
        return memory.allowWithinHour(skillId, amount, cap, nowMillis);
    }
}
