package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
/** Grants simple optional quest rewards using systems available in Minecraft 1.7.10. */
public final class LostTalesQuestRewardHelper {

    private LostTalesQuestRewardHelper() {}

    public static boolean grantRewards(EntityPlayerMP player,
            LostTalesQuestDefinition quest,
            LostTalesQuestProgress progress) {
        if (player == null || quest == null) {
            return false;
        }

        boolean granted = grantRewardMap(player, quest.getRewards());
        granted = grantOptionalRewards(player, quest, progress, granted);
        if (granted) {
            player.addChatMessage(new net.minecraft.util.ChatComponentTranslation(
                    "chat.losttales.quest.note.rewards"));
        }
        return granted;
    }

    private static boolean grantOptionalRewards(EntityPlayerMP player,
            LostTalesQuestDefinition quest, LostTalesQuestProgress progress,
            boolean alreadyGranted) {
        boolean granted = alreadyGranted;
        if (player == null || quest == null || progress == null) {
            return granted;
        }
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            for (LostTalesQuestObjectiveDefinition objective
                    : stage.getObjectives()) {
                if (!objective.isOptional()
                        || progress.getObjectiveProgress(objective.getId())
                        < LostTalesQuestObjectiveTextHelper
                        .getObjectiveTargetCount(objective)) {
                    continue;
                }
                Map<String, String> optionalRewards =
                        new LinkedHashMap<String, String>();
                for (Map.Entry<String, String> entry
                        : objective.getParams().entrySet()) {
                    String key = entry.getKey();
                    if (key != null && key.startsWith("reward.")
                            && key.length() > "reward.".length()) {
                        optionalRewards.put(
                                key.substring("reward.".length()),
                                entry.getValue());
                    }
                }
                granted |= grantRewardMap(player, optionalRewards);
            }
        }
        return granted;
    }

    private static boolean grantRewardMap(EntityPlayerMP player,
            Map<String, String> rewards) {
        if (player == null || rewards == null || rewards.isEmpty()) {
            return false;
        }
        boolean granted = false;

        int xp = LostTalesQuestParams.parseInt(rewards.get("experience"), 0);
        if (xp > 0) {
            player.addExperience(xp);
            granted = true;
        }

        int levels = LostTalesQuestParams.parseInt(rewards.get("levels"), 0);
        if (levels > 0) {
            player.addExperienceLevel(levels);
            granted = true;
        }

        for (LostTalesQuestItemSpec item : itemsGranted(rewards)) {
            granted |= giveItem(player, item);
        }

        return granted;
    }

    /**
     * The items a reward map grants: its {@code item}, then each of its
     * {@code items}, read as {@link LostTalesQuestRewardText} names them.
     */
    static List<LostTalesQuestItemSpec> itemsGranted(Map<String, String> rewards) {
        List<LostTalesQuestItemSpec> items = new ArrayList<LostTalesQuestItemSpec>();
        LostTalesQuestItemSpec single = LostTalesQuestItemSpec.rewardItem(rewards);
        if (single != null) {
            items.add(single);
        }
        items.addAll(LostTalesQuestItemSpec.rewardItems(rewards));
        return items;
    }

    private static boolean giveItem(EntityPlayerMP player, LostTalesQuestItemSpec spec) {
        Item registered = spec.item();
        if (registered == null) {
            return false;
        }

        ItemStack stack = new ItemStack(registered, spec.getCount(), spec.getMeta());
        if (!player.inventory.addItemStackToInventory(stack)) {
            EntityItem entityItem = player.dropPlayerItemWithRandomChoice(stack, false);
            if (entityItem != null) {
                entityItem.delayBeforeCanPickup = 0;
            }
        }
        return true;
    }
}
