package com.ninuna.losttales.quest;

import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.compat.lotr.LotrQuestReference;
import com.ninuna.losttales.compat.lotr.LotrQuestShareAdapter;
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
        return ChatShowcase.quest(tokenIndex, reference, quest.getTitle(),
                category(quest), objective(quest, progress), rewards(quest),
                quest.canStartFromShare());
    }

    private static String objective(LostTalesQuestDefinition quest,
                                    LostTalesQuestProgress progress) {
        int stage = Math.max(0, Math.min(progress.getStageIndex(),
                quest.getStages().size() - 1));
        if (quest.getStages().isEmpty()) {
            return quest.getDescription();
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
        return bounded(text.length() == 0 ? quest.getDescription()
                : text.toString(), ChatShowcase.MAX_QUEST_OBJECTIVE_BYTES);
    }

    private static String rewards(LostTalesQuestDefinition quest) {
        String rewards = LostTalesQuestRewardText.summary(quest.getRewards());
        return bounded(rewards.length() == 0
                ? StatCollector.translateToLocal("gui.losttales.quest.reward.pending")
                : rewards, ChatShowcase.MAX_QUEST_REWARD_BYTES);
    }

    private static String category(LostTalesQuestDefinition quest) {
        String id = quest.getId() == null ? "" : quest.getId().toLowerCase();
        if (id.contains("tutorial")) return "Tutorials";
        if (id.contains("missive")) return "Missives";
        if (id.contains("path/")) return "Paths";
        if (id.contains("faction")) return "Factions";
        if (id.contains("story") || id.contains("main")) return "Main Story";
        return "Regional";
    }

    private static String bounded(String value, int maximum) {
        String text = value == null ? "" : value;
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }
}
