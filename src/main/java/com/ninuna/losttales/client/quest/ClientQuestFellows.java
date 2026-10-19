package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.quest.QuestFellowCount;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.StatCollector;

/**
 * How the members of this player's fellowships stand in the quests the
 * player runs too, as the server last said (QUEST-25). Cleared on
 * disconnect.
 */
public final class ClientQuestFellows {
    private static List<QuestFellowCount> counts = Collections.emptyList();

    private ClientQuestFellows() {}

    public static synchronized void set(Collection<QuestFellowCount> sent) {
        counts = sent == null || sent.isEmpty()
                ? Collections.<QuestFellowCount>emptyList()
                : Collections.unmodifiableList(new ArrayList<QuestFellowCount>(sent));
    }

    public static synchronized void clear() {
        counts = Collections.emptyList();
    }

    /** The members' standing in one quest, in the order the server sent them. */
    public static synchronized List<QuestFellowCount> of(String questId) {
        List<QuestFellowCount> found = new ArrayList<QuestFellowCount>();
        for (QuestFellowCount count : counts) {
            if (count.getQuestId().equals(questId)) {
                found.add(count);
            }
        }
        return found;
    }

    /**
     * The line under an objective of the player's stage: <i>Tobin 2/3 ·
     * Dior done</i>; empty when no member runs the quest at that stage or
     * past it.
     */
    public static String lineFor(String questId, String objectiveId, int target) {
        StringBuilder line = new StringBuilder();
        for (QuestFellowCount count : of(questId)) {
            if (line.length() > 0) {
                line.append(" · ");
            }
            int reached = count.countOf(objectiveId);
            line.append(count.isPast() || reached >= target
                    ? StatCollector.translateToLocalFormatted(
                            "gui.losttales.quest.fellow.done", count.getName())
                    : StatCollector.translateToLocalFormatted(
                            "gui.losttales.quest.fellow.count", count.getName(),
                            String.valueOf(reached), String.valueOf(target)));
        }
        return line.toString();
    }
}
