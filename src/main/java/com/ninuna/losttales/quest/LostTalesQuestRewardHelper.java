package com.ninuna.losttales.quest;

import com.ninuna.losttales.compat.lotr.LotrQuestRewards;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
/**
 * Pays a finished quest: its own rewards, the item picked from its
 * {@code pick}, and each optional objective's that was achieved.
 * Experience and items are the game's; alignment and coins are paid
 * through LOTR's own calls (QUEST-33).
 */
public final class LostTalesQuestRewardHelper {

    private LostTalesQuestRewardHelper() {}

    /**
     * Pays the quest; {@code picked} is the index of the item taken from
     * its pick, and anything out of range takes the first.
     */
    public static boolean grantRewards(EntityPlayerMP player,
            LostTalesQuestDefinition quest,
            LostTalesQuestProgress progress, int picked) {
        if (player == null || quest == null) {
            return false;
        }

        boolean granted = grantRewardMap(player, quest.getRewards());
        List<LostTalesQuestItemSpec> picks =
                LostTalesQuestRewards.picks(quest.getRewards());
        if (!picks.isEmpty()) {
            granted |= giveItem(player, picks.get(
                    picked >= 0 && picked < picks.size() ? picked : 0));
        }
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
                granted |= grantRewardMap(player,
                        LostTalesQuestRewards.optionalRewards(objective));
            }
        }
        return granted;
    }

    /**
     * Pays one reward block as it is, a world quest's podium among them
     * (QUEST-36); a pick is never in one. Answers whether anything was paid.
     */
    public static boolean grantRewardBlock(EntityPlayerMP player,
            Map<String, String> rewards) {
        boolean granted = grantRewardMap(player, rewards);
        if (granted) {
            player.addChatMessage(new net.minecraft.util.ChatComponentTranslation(
                    "chat.losttales.quest.note.rewards"));
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

        for (LostTalesQuestRewards.Alignment alignment
                : LostTalesQuestRewards.alignments(rewards)) {
            granted |= LotrQuestRewards.addAlignment(player,
                    alignment.getFactionId(), alignment.getAmount());
        }

        granted |= LotrQuestRewards.giveCoins(player,
                LostTalesQuestRewards.coins(rewards));

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

    /**
     * Gives the player the item in stacks no larger than the item's own
     * limit; what the inventory cannot take falls at their feet.
     */
    private static boolean giveItem(EntityPlayerMP player, LostTalesQuestItemSpec spec) {
        Item registered = spec.item();
        if (registered == null) {
            return false;
        }

        int limit = new ItemStack(registered, 1, spec.getMeta()).getMaxStackSize();
        for (int size : stackSizes(spec.getCount(), limit)) {
            ItemStack stack = new ItemStack(registered, size, spec.getMeta());
            if (!player.inventory.addItemStackToInventory(stack)) {
                EntityItem entityItem = player.dropPlayerItemWithRandomChoice(stack, false);
                if (entityItem != null) {
                    entityItem.delayBeforeCanPickup = 0;
                }
            }
        }
        return true;
    }

    /**
     * {@code count} items as stacks of at most {@code limit} each, full
     * stacks first; a limit under one is taken as one.
     */
    static int[] stackSizes(int count, int limit) {
        int each = Math.max(1, limit);
        int total = Math.max(0, count);
        int[] sizes = new int[(total + each - 1) / each];
        for (int index = 0; index < sizes.length; index++) {
            sizes[index] = Math.min(each, total - index * each);
        }
        return sizes;
    }
}
