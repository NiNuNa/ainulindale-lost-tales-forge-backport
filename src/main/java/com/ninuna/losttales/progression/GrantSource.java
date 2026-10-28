package com.ninuna.losttales.progression;

import com.ninuna.losttales.progression.skill.SkillAct;

/**
 * Where skill experience comes from (PROG-08): an act a skill's file
 * counts, a quest finished or a first find among them, a quest's skill
 * pay, or an operator's {@code give}. An act is held to the hourly cap; a
 * finished quest and a first find go past it while the server lets them,
 * and quest pay and a give always do. What goes past the cap takes nothing
 * from it.
 */
public enum GrantSource {
    /** Something the character did, as a skill's file counts it. */
    ACT,
    /**
     * A quest finished, counted as an act by its size (PROG-04): repeats
     * of the same quest still shrink it.
     */
    FINISHED_QUEST,
    /** A place found for the first time: a LOTR region or waypoint, a map marker, a waystone. */
    FIRST_FIND,
    /** A quest's reward in skill experience (QUEST-19). */
    QUEST,
    /** An operator's {@code /losttales skills give}. */
    GIVE;

    /** What an act a skill's file counts comes from: a finished quest, a first find, else an act. */
    public static GrantSource of(SkillAct act) {
        if (act == SkillAct.QUEST) {
            return FINISHED_QUEST;
        }
        return act == SkillAct.DISCOVER ? FIRST_FIND : ACT;
    }

    /** Whether the hourly cap holds it back, as the server sets the cap. */
    public boolean isCapped(HourlyCap cap) {
        switch (this) {
            case ACT:
                return true;
            case FINISHED_QUEST:
                return cap == null || !cap.finishedQuestsPass();
            case FIRST_FIND:
                return cap == null || !cap.firstFindsPass();
            default:
                return false;
        }
    }

    /**
     * How much of {@code amount} the skill gains: where the cap holds it,
     * what the hour still allows under the cap, counted against it; for
     * anything else, all of it.
     */
    public double allow(RepeatMemory memory, String skillId, double amount, HourlyCap cap,
                        long nowMillis) {
        if (!isCapped(cap) || cap == null) {
            return amount;
        }
        return memory.allowWithinHour(skillId, amount, cap.amount(), nowMillis);
    }
}
