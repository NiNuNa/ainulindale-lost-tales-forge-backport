package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.quest.QuestFellowCount;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.StatCollector;

/**
 * How the members of this player's fellowships stand in the quests the
 * player runs too, as the server last said (QUEST-25), and whether the
 * server shares progress between members at all. Cleared on disconnect.
 */
public final class ClientQuestFellows {
    /** The tip of a member whose count does not help: the server shares no progress. */
    public static final String TIP_SHARING_OFF = "gui.losttales.quest.fellow.sharing_off";
    /** The tip of a member whose count does not help: the objective is not shared. */
    public static final String TIP_NOT_SHARED = "gui.losttales.quest.fellow.not_shared";
    /** The tip of a member whose count does not help: not travelling with the player. */
    public static final String TIP_AWAY = "gui.losttales.quest.fellow.away";

    private static List<QuestFellowCount> counts = Collections.emptyList();
    private static boolean shared = true;

    private ClientQuestFellows() {}

    /**
     * One member under an objective: their name and count, and whether
     * that count helps the player, which the journal dims by; a member
     * whose count does not help says why under the pointer.
     */
    public static final class Part {
        private final String text;
        private final String tipKey;

        Part(String text, String tipKey) {
            this.text = text == null ? "" : text;
            this.tipKey = tipKey == null ? "" : tipKey;
        }

        /** <i>Tobin 2/3</i> or <i>Dior done</i>. */
        public String getText() {
            return this.text;
        }

        /** Whether the member's count counts for the player too; the journal dims the others. */
        public boolean isCounted() {
            return this.tipKey.length() == 0;
        }

        /** The lang key of why the member's count does not help; empty where it does. */
        public String getTipKey() {
            return this.tipKey;
        }
    }

    public static synchronized void set(Collection<QuestFellowCount> sent, boolean sharing) {
        counts = sent == null || sent.isEmpty()
                ? Collections.<QuestFellowCount>emptyList()
                : Collections.unmodifiableList(new ArrayList<QuestFellowCount>(sent));
        shared = sharing;
    }

    public static synchronized void clear() {
        counts = Collections.emptyList();
        shared = true;
    }

    /** Whether the server shares kills and arrivals between members, as it last said. */
    public static synchronized boolean isShared() {
        return shared;
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
     * Why a member's count does not help the player (QUEST-25), as a tip's
     * lang key: the server shares no progress, the objective is not shared
     * ({@code objectiveShared} false), or the member does not travel with
     * the player. Empty where it helps.
     */
    static String whyNotCounted(boolean serverShares, boolean objectiveShared,
                                boolean travelling) {
        if (!serverShares) {
            return TIP_SHARING_OFF;
        }
        if (!objectiveShared) {
            return TIP_NOT_SHARED;
        }
        return travelling ? "" : TIP_AWAY;
    }

    /**
     * The members under an objective of the player's stage, one part
     * each: <i>Tobin 2/3 · Dior done</i>; none when no member runs the
     * quest at that stage or past it. {@code objectiveShared} says whether
     * members' progress can count for this objective at all
     * ({@code LostTalesQuestRules.sharesWithFellowship}).
     */
    public static List<Part> partsFor(String questId, String objectiveId, int target,
                                      boolean objectiveShared) {
        boolean serverShares = isShared();
        List<Part> parts = new ArrayList<Part>();
        for (QuestFellowCount count : of(questId)) {
            int reached = count.countOf(objectiveId);
            parts.add(new Part(count.isPast() || reached >= target
                    ? StatCollector.translateToLocalFormatted(
                            "gui.losttales.quest.fellow.done", count.getName())
                    : StatCollector.translateToLocalFormatted(
                            "gui.losttales.quest.fellow.count", count.getName(),
                            String.valueOf(reached), String.valueOf(target)),
                    whyNotCounted(serverShares, objectiveShared, count.isTravelling())));
        }
        return parts;
    }
}
