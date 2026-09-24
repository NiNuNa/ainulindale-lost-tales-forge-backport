package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import java.util.Set;
import java.util.UUID;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.quest.LOTRMiniQuest;
import net.minecraft.entity.player.EntityPlayer;

/**
 * The journal's requests about a LOTR quest, checked against LOTR's own
 * state: tracking one, giving one up, and clearing a finished or failed
 * one out of History. Giving up and clearing call what LOTR's own quest
 * book calls.
 */
public final class LotrQuestJournalAdapter {
    private LotrQuestJournalAdapter() {}

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

    /**
     * Gives up a LOTR quest the player is on. Answers whether it was one
     * they are on; a failed quest is cleared, not given up.
     */
    public static boolean abandon(EntityPlayer player, String reference,
            LostTalesQuestPlayerData lostTalesData) {
        LOTRMiniQuest quest = findActive(player, reference);
        if (quest == null) {
            return false;
        }
        remove(player, quest, false, reference, lostTalesData);
        return true;
    }

    /**
     * Clears a finished or failed LOTR quest out of the player's History.
     * Answers whether there was one; a quest still running is not
     * cleared.
     */
    public static boolean clear(EntityPlayer player, String reference,
            LostTalesQuestPlayerData lostTalesData) {
        UUID questId = LotrQuestReference.parse(reference);
        LOTRPlayerData data = player == null || questId == null
                ? null : LOTRLevelData.getData(player);
        if (data == null) {
            return false;
        }
        LOTRMiniQuest finished = data.getMiniQuestForID(questId, true);
        if (finished != null) {
            remove(player, finished, true, reference, lostTalesData);
            return true;
        }
        // A failed quest stays in LOTR's list of running ones until it is
        // cleared.
        LOTRMiniQuest failed = data.getMiniQuestForID(questId, false);
        if (failed != null && failed.isFailed()) {
            remove(player, failed, false, reference, lostTalesData);
            return true;
        }
        return false;
    }

    /** Removes the quest from LOTR's list, which tells the client, and stops tracking it. */
    private static void remove(EntityPlayer player, LOTRMiniQuest quest,
            boolean completedList, String reference,
            LostTalesQuestPlayerData lostTalesData) {
        unpin(player, reference, lostTalesData);
        LOTRLevelData.getData(player).removeMiniQuest(quest, completedList);
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
