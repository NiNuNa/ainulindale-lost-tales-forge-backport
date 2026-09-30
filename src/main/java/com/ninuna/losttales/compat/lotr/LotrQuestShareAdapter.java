package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import java.util.UUID;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.quest.LOTRMiniQuest;
import lotr.common.quest.LOTRMiniQuestWelcome;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.StatCollector;

/**
 * A LOTR mini-quest's card, built on the server; such a card only opens
 * the journal. Its words come from the lang file in the server's own
 * language, as a Lost Tales quest's card's do, and its category follows
 * the journal's rule ({@link LotrQuestCategory}).
 */
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
        boolean welcome = quest instanceof LOTRMiniQuestWelcome;
        String objective = safe(quest.getQuestObjective(),
                StatCollector.translateToLocal("gui.losttales.quest.lotr.untitled"));
        String title = welcome ? StatCollector.translateToLocal(
                "gui.losttales.quest.lotr.grey_wanderer") : objective;
        if (!normalizedTypedTitle.equals(
                ChatShareTokenParser.normalizeName(title))) {
            return null;
        }
        String progress = safe(quest.getQuestProgress(), objective);
        String rewards = welcome
                ? StatCollector.translateToLocal("gui.losttales.quest.lotr.reward.tutorial")
                : StatCollector.translateToLocalFormatted(
                        "gui.losttales.quest.reward.payment",
                        safe(quest.getFactionSubtitle(), StatCollector.translateToLocal(
                                "gui.losttales.quest.reward.faction")));
        return ChatShowcase.quest(tokenIndex, reference, title,
                LotrQuestCategory.of(quest), progress, rewards, false);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }
}
