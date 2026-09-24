package com.ninuna.losttales.item;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveNbt;
import com.ninuna.losttales.quest.missive.LostTalesMissiveObjectiveData;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import com.ninuna.losttales.quest.LostTalesQuestTimeText;
import com.ninuna.losttales.quest.LostTalesQuestRewardText;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveTextHelper;
import net.minecraft.util.StatCollector;

/**
 * NBT-backed missive letter item.
 *
 * This item displays server-authored missive data. Right-clicking opens a
 * client-side reader with an Accept button. The button sends a small server
 * request that re-validates the player inventory slot before starting the quest
 * or consuming the letter.
 */
public class LostTalesItemMissiveLetter extends Item {
    public LostTalesItemMissiveLetter() {
        this.setMaxStackSize(1);
    }

    public ItemStack createStack(LostTalesMissiveData missive) {
        ItemStack stack = new ItemStack(this);
        LostTalesMissiveNbt.writeToItemStack(stack, missive);
        return stack;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        LostTalesMissiveData missive = LostTalesMissiveNbt.readFromItemStack(stack);
        if (missive != null && missive.getTitle().length() > 0) {
            return missive.getTitle();
        }
        return super.getItemStackDisplayName(stack);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advancedTooltips) {
        LostTalesMissiveData missive = LostTalesMissiveNbt.readFromItemStack(stack);
        if (missive == null) {
            list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(
                    "gui.losttales.missive_letter.invalid"));
            return;
        }

        if (missive.getIssuer().length() > 0) {
            list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                    "gui.losttales.missive_letter.issued_by",
                    EnumChatFormatting.WHITE + missive.getIssuer()));
        }
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                "gui.losttales.missive_letter.type", EnumChatFormatting.WHITE
                        + LostTalesQuestObjectiveTextHelper.typeName(missive.getQuestType())));

        if (!missive.getObjectives().isEmpty()) {
            list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal(
                    "gui.losttales.missive_letter.objectives"));
            int shown = 0;
            for (LostTalesMissiveObjectiveData objective : missive.getObjectives()) {
                if (objective == null || !objective.isValid()) {
                    continue;
                }
                list.add(EnumChatFormatting.GRAY + "- " + buildObjectiveSummary(objective));
                shown++;
                if (shown >= 3 && missive.getObjectives().size() > shown) {
                    list.add(EnumChatFormatting.DARK_GRAY + "- ...");
                    break;
                }
            }
        }

        String rewardSummary = buildRewardSummary(missive);
        if (rewardSummary.length() > 0) {
            list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                    "gui.losttales.missive_letter.reward", rewardSummary));
        }
        if (missive.hasTimeLimit()) {
            list.add(EnumChatFormatting.RED + StatCollector.translateToLocalFormatted(
                    "gui.losttales.missive_letter.time_limit",
                    LostTalesQuestTimeText.shortForm(missive.getTimeLimitTicks())));
        }
        if (advancedTooltips) {
            list.add(EnumChatFormatting.DARK_GRAY + missive.getQuestId());
        }
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            LostTalesMod.proxy.openMissiveLetterGui(player, stack, player.inventory.currentItem);
        }
        return stack;
    }

    /** A missive objective in the words the journal gives the same objective. */
    public static String buildObjectiveSummary(LostTalesMissiveObjectiveData objective) {
        return LostTalesQuestObjectiveTextHelper.describe(new LostTalesQuestObjectiveDefinition(
                objective.getId(), objective.getType(), objective.getDescription().trim(),
                objective.isOptional(), objective.getParams()));
    }

    public static String buildRewardSummary(LostTalesMissiveData missive) {
        return missive.getRewardData() == null ? ""
                : LostTalesQuestRewardText.summary(
                        missive.getRewardData().getRewards());
    }


}
