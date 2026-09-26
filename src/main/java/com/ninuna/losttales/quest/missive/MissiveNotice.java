package com.ninuna.losttales.quest.missive;

/**
 * One notice on a missive board as the board's page shows it: the slot
 * of the board it is pinned in, how long it stays up, and its letter.
 * A letter that cannot be read has no missive; it can still be taken
 * down, and its quest id reads empty.
 */
public final class MissiveNotice {
    /** The time left of a notice the board never takes down by itself. */
    public static final long STAYS_UP = -1L;

    private final int slot;
    private final long ticksLeft;
    private final LostTalesMissiveData missive;

    /**
     * A notice in {@code slot}, taken down by the board in
     * {@code ticksLeft} world ticks ({@link #STAYS_UP} for never), with
     * {@code missive} as its letter, or null for one that cannot be read.
     */
    public MissiveNotice(int slot, long ticksLeft,
                         LostTalesMissiveData missive) {
        this.slot = slot;
        this.ticksLeft = ticksLeft < 0L ? STAYS_UP : ticksLeft;
        this.missive = missive != null && missive.isValid() ? missive : null;
    }

    public int getSlot() {
        return this.slot;
    }

    /** World ticks until the board takes it down; {@link #STAYS_UP} for never. */
    public long getTicksLeft() {
        return this.ticksLeft;
    }

    public boolean staysUp() {
        return this.ticksLeft < 0L;
    }

    /** Its letter; null for one that cannot be read. */
    public LostTalesMissiveData getMissive() {
        return this.missive;
    }

    public boolean isReadable() {
        return this.missive != null;
    }

    /** The letter's quest id; empty for one that cannot be read. */
    public String getQuestId() {
        return this.missive == null ? "" : this.missive.getQuestId();
    }
}
