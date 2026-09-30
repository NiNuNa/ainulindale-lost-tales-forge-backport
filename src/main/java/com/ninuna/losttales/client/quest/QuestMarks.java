package com.ninuna.losttales.client.quest;

/**
 * The marks a quest and its objectives are read by, the same in the
 * journal and on the tracker: one mark each.
 */
public final class QuestMarks {
    /** A quest finished, an objective done. */
    public static final String DONE = "✔";
    /** A quest running, an objective under way. */
    public static final String OPEN = "◇";
    /** A quest failed or given up. */
    public static final String ENDED = "✕";
    /** An objective of a stage not reached. */
    public static final String AHEAD = "○";

    private QuestMarks() {}
}
