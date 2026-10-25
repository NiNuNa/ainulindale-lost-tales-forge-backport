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

    /**
     * One member under an objective: their name and count, and whether
     * they travel with the player, sharing the player's credit; the
     * journal dims the others.
     */
    public static final class Part {
        private final String text;
        private final boolean travelling;

        Part(String text, boolean travelling) {
            this.text = text == null ? "" : text;
            this.travelling = travelling;
        }

        /** <i>Tobin 2/3</i> or <i>Dior done</i>. */
        public String getText() {
            return this.text;
        }

        public boolean isTravelling() {
            return this.travelling;
        }
    }

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
     * The members under an objective of the player's stage, one part
     * each: <i>Tobin 2/3 · Dior done</i>; none when no member runs the
     * quest at that stage or past it.
     */
    public static List<Part> partsFor(String questId, String objectiveId, int target) {
        List<Part> parts = new ArrayList<Part>();
        for (QuestFellowCount count : of(questId)) {
            int reached = count.countOf(objectiveId);
            parts.add(new Part(count.isPast() || reached >= target
                    ? StatCollector.translateToLocalFormatted(
                            "gui.losttales.quest.fellow.done", count.getName())
                    : StatCollector.translateToLocalFormatted(
                            "gui.losttales.quest.fellow.count", count.getName(),
                            String.valueOf(reached), String.valueOf(target)),
                    count.isTravelling()));
        }
        return parts;
    }
}
