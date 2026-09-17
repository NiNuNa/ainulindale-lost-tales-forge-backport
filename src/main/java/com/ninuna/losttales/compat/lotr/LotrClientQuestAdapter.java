package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.client.quest.ClientQuestEntry;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.quest.LOTRMiniQuest;
import lotr.common.quest.LOTRMiniQuestCollectBase;
import lotr.common.quest.LOTRMiniQuestKill;
import lotr.common.quest.LOTRMiniQuestWelcome;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;

/** Converts LOTR miniquests into the shared Lost Tales presentation. */
@SideOnly(Side.CLIENT)
public final class LotrClientQuestAdapter {
    private LotrClientQuestAdapter() {}

    public static List<ClientQuestEntry> getEntries(Minecraft minecraft) {
        if (minecraft == null || minecraft.thePlayer == null) {
            return Collections.emptyList();
        }
        LOTRPlayerData data = LOTRLevelData.getData(minecraft.thePlayer);
        if (data == null) {
            return Collections.emptyList();
        }

        ArrayList<ClientQuestEntry> entries = new ArrayList<ClientQuestEntry>();
        for (LOTRMiniQuest quest : data.getMiniQuests()) {
            ClientQuestEntry entry = createEntry(data, quest, false);
            if (entry != null) {
                entries.add(entry);
            }
        }
        for (LOTRMiniQuest quest : data.getMiniQuestsCompleted()) {
            ClientQuestEntry entry = createEntry(data, quest, true);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private static ClientQuestEntry createEntry(LOTRPlayerData data,
            LOTRMiniQuest quest, boolean completedList) {
        if (quest == null || quest.questUUID == null) {
            return null;
        }
        String reference = LotrQuestReference.create(quest.questUUID);
        boolean failed = !completedList && quest.isFailed();
        ClientQuestEntry.Status status = completedList || quest.isCompleted()
                ? ClientQuestEntry.Status.COMPLETED
                : failed ? ClientQuestEntry.Status.FAILED
                : ClientQuestEntry.Status.ACTIVE;
        boolean nativeTracked = data.getTrackingMiniQuest() == quest;
        boolean tracked = LostTalesClientQuestProgressStore
                .isQuestReferencePinned(reference) || nativeTracked;

        String objective = safe(quest.getQuestObjective(), "LOTR quest");
        String giver = safe(quest.entityNameFull,
                safe(quest.entityName, "Unknown quest giver"));
        String faction = safe(quest.getFactionSubtitle(), "");
        String subtitle = faction.length() == 0
                ? "Offered by " + giver
                : "Offered by " + giver + " | " + faction;
        String journal = failed
                ? safe(quest.getQuestFailure(), "This quest has failed.")
                : objective;

        int[] progress = progress(quest, completedList);
        ArrayList<ClientQuestEntry.Objective> objectives =
                new ArrayList<ClientQuestEntry.Objective>();
        String progressText = safe(quest.getQuestProgress(), "");
        String objectiveLine = progressText.length() == 0
                ? objective : objective + " | " + progressText;
        objectives.add(new ClientQuestEntry.Objective(objectiveLine,
                progress[0], progress[1], completedList
                        || progress[0] >= progress[1], false));

        ArrayList<String> rewards = rewardLines(quest, completedList);
        ArrayList<ClientQuestEntry.Target> targets = target(quest);
        int stageNumber = quest instanceof LOTRMiniQuestWelcome
                ? Math.max(1, ((LOTRMiniQuestWelcome)quest).stage) : 1;
        int stageCount = quest instanceof LOTRMiniQuestWelcome ? 15 : 1;
        String title = quest instanceof LOTRMiniQuestWelcome
                ? "The Grey Wanderer" : objective;

        return new ClientQuestEntry(ClientQuestEntry.Source.LOTR,
                reference, title, subtitle, category(quest), journal, status,
                tracked, stageNumber, stageCount, -1L, objectives, rewards,
                targets, null, null);
    }

    private static int[] progress(LOTRMiniQuest quest, boolean completed) {
        if (quest instanceof LOTRMiniQuestKill) {
            LOTRMiniQuestKill kill = (LOTRMiniQuestKill)quest;
            return new int[] {completed ? kill.killTarget : kill.killCount,
                    Math.max(1, kill.killTarget)};
        }
        if (quest instanceof LOTRMiniQuestCollectBase) {
            LOTRMiniQuestCollectBase collect = (LOTRMiniQuestCollectBase)quest;
            return new int[] {completed ? collect.collectTarget
                    : collect.amountGiven, Math.max(1, collect.collectTarget)};
        }
        if (quest instanceof LOTRMiniQuestWelcome) {
            int stage = Math.max(0, ((LOTRMiniQuestWelcome)quest).stage);
            return new int[] {completed ? 15 : stage, 15};
        }
        int value = completed ? 1000
                : Math.max(0, Math.min(1000,
                        Math.round(quest.getCompletionFactor() * 1000.0F)));
        return new int[] {value, 1000};
    }

    private static ArrayList<String> rewardLines(LOTRMiniQuest quest,
            boolean completed) {
        ArrayList<String> rewards = new ArrayList<String>();
        if (!completed) {
            if (!(quest instanceof LOTRMiniQuestWelcome)) {
                String faction = safe(quest.getFactionSubtitle(), "the giver's faction");
                rewards.add("Alignment and payment from " + faction);
                if (quest.willHire) {
                    rewards.add("The quest giver may become available for hire");
                }
            }
            return rewards;
        }
        if (quest.alignmentRewarded > 0.0F) {
            rewards.add(formatNumber(quest.alignmentRewarded) + " alignment");
        }
        if (quest.coinsRewarded > 0) {
            rewards.add(quest.coinsRewarded + " silver coins");
        }
        for (ItemStack stack : quest.itemsRewarded) {
            if (stack != null) {
                rewards.add((stack.stackSize > 1 ? stack.stackSize + "x " : "")
                        + stack.getDisplayName());
            }
        }
        if (quest.wasHired) {
            rewards.add("The quest giver joined you");
        }
        return rewards;
    }

    private static ArrayList<ClientQuestEntry.Target> target(
            LOTRMiniQuest quest) {
        ArrayList<ClientQuestEntry.Target> targets =
                new ArrayList<ClientQuestEntry.Target>();
        LotrMiniQuestLocationAccess.Location location =
                LotrMiniQuestLocationAccess.lastLocation(quest);
        if (location == null || location.getCoordinates() == null) {
            return targets;
        }
        ChunkCoordinates coordinates = location.getCoordinates();
        targets.add(new ClientQuestEntry.Target(location.getDimensionId(),
                coordinates.posX, coordinates.posY, coordinates.posZ));
        return targets;
    }

    private static String category(LOTRMiniQuest quest) {
        if (quest instanceof LOTRMiniQuestWelcome) {
            return "Tutorials";
        }
        return quest.getFactionSubtitle() == null
                || quest.getFactionSubtitle().length() == 0
                ? "Regional" : "Factions";
    }

    private static String formatNumber(float value) {
        int whole = Math.round(value);
        return Math.abs(value - whole) < 0.01F
                ? String.valueOf(whole) : String.format("%.1f", value);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }
}
