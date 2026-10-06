package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.chat.share.ChatQuestCard;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.quest.LostTalesQuestShareResolver;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.fac.LOTRFaction;
import lotr.common.quest.LOTRMiniQuest;
import lotr.common.quest.LOTRMiniQuestWelcome;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * A LOTR mini-quest's card, built on the server; such a card only opens
 * the journal. Its title and progress are the words LOTR gives the quest on
 * the server, which no reader's game could find in its own lang file; the
 * faction that pays travels by LOTR's code and each reader's game names it,
 * and the welcome quest is worded by the mod's own lines. Its category
 * follows the journal's rule ({@link LotrQuestCategory}). The quest is
 * found by its reference alone, whatever its token was typed as.
 */
public final class LotrQuestShareAdapter {
    private LotrQuestShareAdapter() {}

    public static ChatShowcase resolve(EntityPlayerMP sender, String reference,
            int tokenIndex) {
        UUID id = LotrQuestReference.parse(reference);
        LOTRPlayerData data = sender == null || id == null ? null
                : LOTRLevelData.getData(sender);
        LOTRMiniQuest quest = data == null ? null
                : data.getMiniQuestForID(id, false);
        if (quest == null || !quest.isActive()) {
            return null;
        }
        String category = LostTalesQuestShareResolver.fitBytes(
                LotrQuestCategory.of(quest), ChatQuestCard.MAX_CATEGORY_BYTES);
        if (quest instanceof LOTRMiniQuestWelcome) {
            // Its objective names a key binding, which only a client has.
            return ChatShowcase.quest(tokenIndex, reference, new ChatQuestCard(
                    ChatQuestCard.Source.LOTR_WELCOME, "", category,
                    Collections.<ChatQuestCard.Objective>emptyList(),
                    Collections.<String, String>emptyMap()), false);
        }
        String objective = safe(quest.getQuestObjective());
        String progress = safe(quest.getQuestProgress());
        if (progress.length() == 0) {
            progress = objective;
        }
        List<ChatQuestCard.Objective> objectives = progress.length() == 0
                ? Collections.<ChatQuestCard.Objective>emptyList()
                : Collections.singletonList(new ChatQuestCard.Objective("", "",
                        Collections.<String, String>emptyMap(), 0, 0, false,
                        LostTalesQuestShareResolver.fitBytes(progress,
                                ChatQuestCard.MAX_OBJECTIVE_TEXT_BYTES)));
        Map<String, String> rewards = new LinkedHashMap<String, String>();
        rewards.put(ChatQuestCard.LOTR_PAYMENT, payingFaction(quest));
        // LOTR's words have no bound of their own; the card has.
        return ChatShowcase.quest(tokenIndex, reference, new ChatQuestCard(
                ChatQuestCard.Source.LOTR,
                LostTalesQuestShareResolver.fitBytes(objective,
                        ChatQuestCard.MAX_TITLE_BYTES),
                category, objectives, rewards), false);
    }

    /**
     * The code of the faction that pays for the quest, which LOTR names
     * the quest's subtitle by; empty for a faction no player aligns with.
     */
    private static String payingFaction(LOTRMiniQuest quest) {
        try {
            LOTRFaction faction = quest.entityFaction;
            return faction != null && faction.isPlayableAlignmentFaction()
                    ? faction.codeName() : "";
        } catch (RuntimeException unknown) {
            return "";
        }
    }

    /**
     * A faction's name in the language of the side that asks, from LOTR's
     * own lang file; empty for a code LOTR does not know.
     */
    public static String factionName(String code) {
        if (code == null || code.length() == 0) {
            return "";
        }
        try {
            LOTRFaction faction = LOTRFaction.forName(code);
            return faction == null ? "" : safe(faction.factionName());
        } catch (RuntimeException unknown) {
            return "";
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
