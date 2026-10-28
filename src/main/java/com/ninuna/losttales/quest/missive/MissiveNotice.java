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
    private final String worldQuestId;

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
        this.worldQuestId = "";
    }

    private MissiveNotice(int slot, long ticksLeft, String worldQuestId) {
        this.slot = slot;
        this.ticksLeft = ticksLeft < 0L ? STAYS_UP : ticksLeft;
        this.missive = null;
        this.worldQuestId = worldQuestId == null ? "" : worldQuestId;
    }

    /**
     * World ticks until a notice posted at {@code postedAt} is past its
     * days, {@code expiration} ticks long, at {@code worldTime}:
     * {@link #STAYS_UP} for one that never is, with expiry off, with no
     * posting time (0) or posted after {@code worldTime}. The one rule for
     * a notice's days: the board taking it down, its page, accepting its
     * letter and the world's record of missives taken all ask it.
     */
    public static long ticksLeft(long postedAt, long expiration,
                                 long worldTime) {
        if (expiration <= 0L || postedAt <= 0L || worldTime < postedAt) {
            return STAYS_UP;
        }
        long stood = worldTime - postedAt;
        return stood >= expiration ? 0L : expiration - stood;
    }

    /** Whether a notice posted at {@code postedAt} is past its days at {@code worldTime} ({@link #ticksLeft}). */
    public static boolean isPast(long postedAt, long expiration,
                                 long worldTime) {
        return ticksLeft(postedAt, expiration, worldTime) == 0L;
    }

    /**
     * A running world quest's notice, which every board shows while it runs
     * (QUEST-36): no letter, nothing to accept or take; {@code slot} is the
     * page's own, below every board slot.
     */
    public static MissiveNotice world(int slot, long ticksLeft, String questId) {
        return new MissiveNotice(slot, ticksLeft, questId);
    }

    /** Whether it is a world quest's notice rather than a letter. */
    public boolean isWorld() {
        return this.worldQuestId.length() > 0;
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

    /** The letter's quest id, or the world quest's; empty for a letter that cannot be read. */
    public String getQuestId() {
        return isWorld() ? this.worldQuestId
                : this.missive == null ? "" : this.missive.getQuestId();
    }
}
