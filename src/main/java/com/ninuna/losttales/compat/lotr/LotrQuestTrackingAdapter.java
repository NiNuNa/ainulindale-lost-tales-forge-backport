package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import java.util.Set;
import java.util.UUID;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.quest.LOTRMiniQuest;
import net.minecraft.entity.player.EntityPlayer;

/** Validates unified tracking requests against LOTR's authoritative state. */
public final class LotrQuestTrackingAdapter {
    private LotrQuestTrackingAdapter() {}

    public static boolean pin(EntityPlayer player, String reference,
            LostTalesQuestPlayerData lostTalesData) {
        LOTRMiniQuest quest = findActive(player, reference);
        if (quest == null || lostTalesData == null) {
            return false;
        }
        boolean changed = lostTalesData.pinQuestReference(reference);
        LOTRPlayerData lotrData = LOTRLevelData.getData(player);
        if (lotrData != null) {
            lotrData.setTrackingMiniQuest(quest);
        }
        return changed;
    }

    public static boolean unpin(EntityPlayer player, String reference,
            LostTalesQuestPlayerData lostTalesData) {
        if (lostTalesData == null || !LotrQuestReference.isLotrQuest(reference)) {
            return false;
        }
        boolean changed = lostTalesData.unpinQuestId(reference);
        LOTRPlayerData lotrData = LOTRLevelData.getData(player);
        UUID questId = LotrQuestReference.parse(reference);
        LOTRMiniQuest tracked = lotrData == null
                ? null : lotrData.getTrackingMiniQuest();
        if (tracked != null && questId != null
                && questId.equals(tracked.questUUID)) {
            lotrData.setTrackingMiniQuest((LOTRMiniQuest)null);
        }
        return changed;
    }

    public static boolean prune(EntityPlayer player,
            LostTalesQuestPlayerData lostTalesData) {
        if (player == null || lostTalesData == null) {
            return false;
        }
        boolean changed = false;
        Set<String> references = lostTalesData.getPinnedQuestIds();
        for (String reference : references) {
            if (LotrQuestReference.isLotrQuest(reference)
                    && findActive(player, reference) == null) {
                changed |= lostTalesData.unpinQuestId(reference);
            }
        }
        return changed;
    }

    public static void clearNativeTracking(EntityPlayer player) {
        if (player == null) return;
        LOTRPlayerData data = LOTRLevelData.getData(player);
        if (data != null) {
            data.setTrackingMiniQuest((LOTRMiniQuest)null);
        }
    }

    public static String displayName(EntityPlayer player, String reference) {
        LOTRMiniQuest quest = findActive(player, reference);
        if (quest == null) {
            return "LOTR quest";
        }
        String giver = quest.entityNameFull;
        if (giver == null || giver.length() == 0) {
            giver = quest.entityName;
        }
        return giver == null || giver.length() == 0
                ? "LOTR quest" : "quest from " + giver;
    }

    private static LOTRMiniQuest findActive(EntityPlayer player,
            String reference) {
        UUID questId = LotrQuestReference.parse(reference);
        if (player == null || questId == null) {
            return null;
        }
        LOTRPlayerData data = LOTRLevelData.getData(player);
        LOTRMiniQuest quest = data == null
                ? null : data.getMiniQuestForID(questId, false);
        return quest != null && quest.isActive() ? quest : null;
    }
}
