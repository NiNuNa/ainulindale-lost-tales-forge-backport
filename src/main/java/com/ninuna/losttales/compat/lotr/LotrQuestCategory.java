package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.quest.LostTalesQuestCategory;
import lotr.common.quest.LOTRMiniQuest;
import lotr.common.quest.LOTRMiniQuestWelcome;

/**
 * The category a LOTR mini-quest is filed under, in the journal and on its
 * card alike ({@link LostTalesQuestCategory}): the welcome quest under
 * tutorials, a quest a faction gives under factions, any other under
 * regional.
 */
public final class LotrQuestCategory {
    private LotrQuestCategory() {}

    public static String of(LOTRMiniQuest quest) {
        if (quest instanceof LOTRMiniQuestWelcome) {
            return LostTalesQuestCategory.TUTORIALS;
        }
        String faction = quest == null ? null : quest.getFactionSubtitle();
        return faction == null || faction.length() == 0
                ? LostTalesQuestCategory.REGIONAL
                : LostTalesQuestCategory.FACTIONS;
    }
}
