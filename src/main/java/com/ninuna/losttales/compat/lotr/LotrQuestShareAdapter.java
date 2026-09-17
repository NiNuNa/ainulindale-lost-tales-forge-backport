package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import java.util.UUID;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.quest.LOTRMiniQuest;
import lotr.common.quest.LOTRMiniQuestWelcome;
import net.minecraft.entity.player.EntityPlayerMP;

/** Server-side LOTR miniquest preview; these shares are deliberately view-only. */
public final class LotrQuestShareAdapter {
    private LotrQuestShareAdapter() {}

    public static ChatShowcase resolve(EntityPlayerMP sender, String reference,
            String normalizedTypedTitle, int tokenIndex) {
        UUID id = LotrQuestReference.parse(reference);
        LOTRPlayerData data = sender == null || id == null ? null
                : LOTRLevelData.getData(sender);
        LOTRMiniQuest quest = data == null ? null
                : data.getMiniQuestForID(id, false);
        if (quest == null || !quest.isActive()) {
            return null;
        }
        String objective = safe(quest.getQuestObjective(), "LOTR quest");
        String title = quest instanceof LOTRMiniQuestWelcome
                ? "The Grey Wanderer" : objective;
        if (!normalizedTypedTitle.equals(
                ChatShareTokenParser.normalizeName(title))) {
            return null;
        }
        String progress = safe(quest.getQuestProgress(), objective);
        String faction = safe(quest.getFactionSubtitle(), "Regional");
        String rewards = quest instanceof LOTRMiniQuestWelcome
                ? "Tutorial progression"
                : "Alignment and payment from the quest giver";
        return ChatShowcase.quest(tokenIndex, reference, title,
                quest instanceof LOTRMiniQuestWelcome ? "Tutorials" : faction,
                progress, rewards, false);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }
}
