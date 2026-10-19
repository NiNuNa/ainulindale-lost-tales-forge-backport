package com.ninuna.losttales.quest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where a member of one of the player's fellowships stands in a quest the
 * player runs too (QUEST-25): the character's name, and either their counts
 * for the stage the player is at, or that they are past it. The player's
 * journal shows them under each objective: <i>Tobin 2/3 · Dior done</i>.
 */
public final class QuestFellowCount {
    /** The longest character name carried; a longer one is cut. */
    public static final int MAX_NAME_CHARACTERS = 64;
    /** The most objectives one stage's counts carry; a stage with more shows the first. */
    public static final int MAX_OBJECTIVES = 64;

    private final String questId;
    private final String name;
    private final boolean past;
    private final Map<String, Integer> counts;

    public QuestFellowCount(String questId, String name, boolean past,
                            Map<String, Integer> counts) {
        this.questId = questId == null ? "" : questId;
        String shown = name == null ? "" : name;
        this.name = shown.length() > MAX_NAME_CHARACTERS
                ? shown.substring(0, MAX_NAME_CHARACTERS) : shown;
        this.past = past;
        LinkedHashMap<String, Integer> kept = new LinkedHashMap<String, Integer>();
        if (!past && counts != null) {
            for (Map.Entry<String, Integer> count : counts.entrySet()) {
                if (kept.size() >= MAX_OBJECTIVES) {
                    break;
                }
                if (count.getKey() != null && count.getValue() != null) {
                    kept.put(count.getKey(),
                            Integer.valueOf(Math.max(0, count.getValue().intValue())));
                }
            }
        }
        this.counts = Collections.unmodifiableMap(kept);
    }

    public String getQuestId() {
        return this.questId;
    }

    /** The member's character, as the fellowship names it. */
    public String getName() {
        return this.name;
    }

    /** Whether the member is past the player's stage: every objective of it reads done. */
    public boolean isPast() {
        return this.past;
    }

    /** The member's count for an objective of the player's stage; 0 for none. */
    public int countOf(String objectiveId) {
        Integer count = objectiveId == null ? null : this.counts.get(objectiveId);
        return count == null ? 0 : count.intValue();
    }

    public Map<String, Integer> getCounts() {
        return this.counts;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof QuestFellowCount)) {
            return false;
        }
        QuestFellowCount that = (QuestFellowCount) other;
        return this.questId.equals(that.questId) && this.name.equals(that.name)
                && this.past == that.past && this.counts.equals(that.counts);
    }

    @Override
    public int hashCode() {
        return ((this.questId.hashCode() * 31 + this.name.hashCode()) * 31
                + (this.past ? 1 : 0)) * 31 + this.counts.hashCode();
    }
}
