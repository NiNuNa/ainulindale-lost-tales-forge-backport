package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.missive.MissiveWords;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import java.util.UUID;

/**
 * The rules every quest start is judged by, once {@link QuestStartGate}
 * has looked at the world: whether the quest is one a player takes, the
 * proof holds, the way is one the quest and the server allow, and the
 * player may take it now. Prerequisites come after, in the gate.
 *
 * <p>The ways a quest allows: a command, always; an item, for a quest
 * that starts by item while the server allows item starts; a touch, for
 * one that starts by interaction while the server allows those; a card,
 * for one that starts either way the server allows; a letter, for a
 * missive. A quest a giver offers in conversation starts only by that
 * conversation, a card or a command: a touch offers it, and an item is
 * refused.</p>
 */
public final class QuestStartRules {

    private QuestStartRules() {}

    /** What a start comes to. */
    public enum Verdict {
        /** Every rule holds; the prerequisites are asked next. */
        ADMIT,
        UNKNOWN_QUEST,
        /** A world quest, which the whole server works on and nobody takes. */
        WORLD_QUEST,
        NO_PLAYER_DATA,
        /** The proof does not hold: no giver in sight, no seal, no card. */
        UNPROVEN,
        /** The quest does not start this way, or the server allows no start this way. */
        WRONG_START,
        /** A touch on the giver of a quest offered in conversation: the giver offers it. */
        OFFER,
        ALREADY_ACTIVE,
        ALREADY_COMPLETED,
        NOT_RESTARTABLE
    }

    /**
     * Judges a start of {@code quest} for the player whose log is
     * {@code data}, by {@code way}, with or without its proof, under the
     * server's item and interaction start settings.
     */
    public static Verdict judge(LostTalesQuestDefinition quest,
                                LostTalesQuestPlayerData data,
                                QuestStartProof.Way way, boolean proven,
                                boolean itemStarts, boolean interactionStarts) {
        if (quest == null || way == null) {
            return Verdict.UNKNOWN_QUEST;
        }
        if (quest.isWorldQuest()) {
            return Verdict.WORLD_QUEST;
        }
        if (data == null) {
            return Verdict.NO_PLAYER_DATA;
        }
        if (!proven) {
            return Verdict.UNPROVEN;
        }
        boolean offered = LostTalesQuestDialogue.of(quest).isOffered();
        if (!wayAllowed(quest, way, offered, itemStarts, interactionStarts)) {
            return Verdict.WRONG_START;
        }
        if (way == QuestStartProof.Way.TOUCH && offered) {
            return Verdict.OFFER;
        }
        if (data.isQuestActive(quest.getId())) {
            return Verdict.ALREADY_ACTIVE;
        }
        LostTalesQuestHistoryEntry history =
                data.getQuestHistoryEntry(quest.getId());
        if (!quest.mayTakeAgain(history)) {
            return history != null && history.isCompleted()
                    ? Verdict.ALREADY_COMPLETED : Verdict.NOT_RESTARTABLE;
        }
        return Verdict.ADMIT;
    }

    private static boolean wayAllowed(LostTalesQuestDefinition quest,
                                      QuestStartProof.Way way,
                                      boolean offered, boolean itemStarts,
                                      boolean interactionStarts) {
        switch (way) {
            case COMMAND:
                return true;
            case ITEM:
                return !offered && itemStarts && quest.canStartFromItem();
            case TOUCH:
                return interactionStarts && quest.canStartFromInteraction();
            case CONVERSATION:
                return offered && interactionStarts
                        && quest.canStartFromInteraction();
            case CARD:
                return quest.canStartFromShare(itemStarts, interactionStarts);
            case LETTER:
                return MissiveWords.isMissive(quest.getWords());
            default:
                return false;
        }
    }

    /**
     * Whether a card proves a join: its reader is somebody other than its
     * author, and the author still runs the quest.
     */
    public static boolean cardHolds(UUID joinerId, UUID authorId,
                                    boolean authorRunsIt) {
        return joinerId != null && authorId != null
                && !authorId.equals(joinerId) && authorRunsIt;
    }
}
