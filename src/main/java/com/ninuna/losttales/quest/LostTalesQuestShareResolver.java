package com.ninuna.losttales.quest;

import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.compat.lotr.LotrQuestReference;
import com.ninuna.losttales.compat.lotr.LotrQuestShareAdapter;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.StatCollector;

/** Builds a bounded, server-authoritative preview for one active quest. */
public final class LostTalesQuestShareResolver {
    private LostTalesQuestShareResolver() {}

    public static ChatShowcase resolve(EntityPlayerMP sender,
            String reference, String normalizedTypedTitle, int tokenIndex) {
        if (sender == null || reference == null || reference.length() == 0) {
            return null;
        }
        if (LotrQuestReference.isLotrQuest(reference)) {
            return LotrQuestShareAdapter.resolve(sender, reference,
                    normalizedTypedTitle, tokenIndex);
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(sender);
        LostTalesQuestProgress progress = data == null
                ? null : data.getActiveQuest(reference);
        LostTalesQuestDefinition quest = progress == null
                ? null : LostTalesQuestRegistry.getQuest(reference);
        if (quest == null || !normalizedTypedTitle.equals(
                ChatShareTokenParser.normalizeName(quest.getTitle()))) {
            return null;
        }
        return ChatShowcase.quest(tokenIndex, reference,
                fitBytes(quest.getTitle(), ChatShowcase.MAX_QUEST_TITLE_BYTES),
                LostTalesQuestCategory.of(quest), objective(quest, progress),
                rewards(quest), quest.canStartFromShare(
                        LostTalesConfig.allowQuestItemStarts,
                        LostTalesConfig.allowQuestInteractionStarts));
    }

    private static String objective(LostTalesQuestDefinition quest,
                                    LostTalesQuestProgress progress) {
        int stage = LostTalesQuestObjectiveSelection.getCurrentStageIndex(
                quest, progress);
        if (stage < 0) {
            return fitBytes(quest.getDescription(),
                    ChatShowcase.MAX_QUEST_OBJECTIVE_BYTES);
        }
        StringBuilder text = new StringBuilder();
        for (LostTalesQuestObjectiveDefinition objective
                : quest.getStages().get(stage).getObjectives()) {
            if (text.length() > 0) {
                text.append("; ");
            }
            text.append(LostTalesQuestObjectiveTextHelper.buildObjectiveLine(
                    progress, objective, true, false));
            if (text.length() >= ChatShowcase.MAX_QUEST_OBJECTIVE_BYTES - 8) {
                break;
            }
        }
        return fitBytes(text.length() == 0 ? quest.getDescription()
                : text.toString(), ChatShowcase.MAX_QUEST_OBJECTIVE_BYTES);
    }

    private static String rewards(LostTalesQuestDefinition quest) {
        String rewards = LostTalesQuestRewardText.summary(quest.getRewards());
        return fitBytes(rewards.length() == 0
                ? StatCollector.translateToLocal("gui.losttales.quest.reward.pending")
                : rewards, ChatShowcase.MAX_QUEST_REWARD_BYTES);
    }

    /**
     * The text cut to at most {@code maximumBytes} bytes of UTF-8, at the
     * end of a character, so a card always fits what the chat sends.
     */
    public static String fitBytes(String value, int maximumBytes) {
        String text = value == null ? "" : value;
        int bytes = 0;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int width = utf8Width(codePoint);
            if (bytes + width > maximumBytes) {
                return text.substring(0, index);
            }
            bytes += width;
            index += Character.charCount(codePoint);
        }
        return text;
    }

    /** The bytes a character takes in UTF-8; a lone surrogate counts three, more than it is written as. */
    private static int utf8Width(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        return codePoint < 0x10000 ? 3 : 4;
    }
}
