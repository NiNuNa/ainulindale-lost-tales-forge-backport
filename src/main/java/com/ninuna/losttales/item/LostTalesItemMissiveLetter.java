package com.ninuna.losttales.item;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveNbt;
import com.ninuna.losttales.quest.missive.LostTalesMissiveObjectiveData;
import com.ninuna.losttales.quest.missive.MissiveWords;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import com.ninuna.losttales.quest.LostTalesQuestTimeText;
import com.ninuna.losttales.quest.LostTalesQuestRewardText;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveTextHelper;
import net.minecraft.util.StatCollector;

/**
 * A missive letter: a board's notice, its missive kept in the stack's
 * NBT. Using it opens the letter's page on the client, showing the letter
 * in the slot it was used from; its Accept asks the server, which reads
 * that slot again before starting the quest and using the letter up.
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
        if (missive != null) {
            return MissiveWords.title(missive);
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

        String issuer = MissiveWords.issuer(missive);
        if (issuer.length() > 0) {
            list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                    "gui.losttales.missive_letter.issued_by",
                    EnumChatFormatting.WHITE + issuer));
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
                list.add(EnumChatFormatting.GRAY + "- " + MissiveWords.objective(objective));
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
            LostTalesMod.proxy.openMissiveLetterPage(player.inventory.currentItem);
        }
        return stack;
    }

    public static String buildRewardSummary(LostTalesMissiveData missive) {
        return missive.getRewardData() == null ? ""
                : LostTalesQuestRewardText.summary(
                        missive.getRewardData().getRewards());
    }


}
