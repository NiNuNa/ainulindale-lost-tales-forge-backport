package com.ninuna.losttales.item;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveNbt;
import com.ninuna.losttales.quest.missive.LostTalesMissiveObjectiveData;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

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
            list.add(EnumChatFormatting.GRAY + "Blank missive letter");
            list.add(EnumChatFormatting.DARK_GRAY + "Generated missive data will be stored here.");
            return;
        }

        if (missive.getIssuer().length() > 0) {
            list.add(EnumChatFormatting.GRAY + "Issued by: " + EnumChatFormatting.WHITE + missive.getIssuer());
        }
        list.add(EnumChatFormatting.GRAY + "Type: " + EnumChatFormatting.WHITE + prettify(missive.getQuestType()));

        if (!missive.getObjectives().isEmpty()) {
            list.add(EnumChatFormatting.GOLD + "Objectives:");
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
            list.add(EnumChatFormatting.GRAY + "Reward: " + EnumChatFormatting.WHITE + rewardSummary);
        }
        if (missive.hasTimeLimit()) {
            list.add(EnumChatFormatting.RED + "Time limit: " + formatTicks(missive.getTimeLimitTicks()));
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

    public static String buildObjectiveSummary(LostTalesMissiveObjectiveData objective) {
        String description = objective.getDescription();
        if (description != null && description.trim().length() > 0) {
            return description.trim();
        }

        String type = objective.getType();
        int count = getObjectiveCount(objective);
        if (LostTalesMissiveObjectiveData.TYPE_KILL.equalsIgnoreCase(type)) {
            String target = firstNonEmpty(objective.getParam("entity", ""), objective.getParam("entityId", ""), objective.getParam("target", ""), objective.getParam("group", ""));
            return "Defeat " + count + " " + (target.length() > 0 ? prettify(target) : count == 1 ? "enemy" : "enemies") + ".";
        }
        if (LostTalesMissiveObjectiveData.TYPE_GATHER.equalsIgnoreCase(type)) {
            String item = firstNonEmpty(objective.getParam("item", ""), objective.getParam("itemId", ""), objective.getParam("target", ""));
            return "Gather " + count + " " + (item.length() > 0 ? prettify(item) : count == 1 ? "item" : "items") + ".";
        }
        if (LostTalesMissiveObjectiveData.TYPE_CRAFT.equalsIgnoreCase(type)) {
            String item = firstNonEmpty(objective.getParam("item", ""), objective.getParam("itemId", ""), objective.getParam("target", ""));
            return "Craft " + count + " " + (item.length() > 0 ? prettify(item) : count == 1 ? "item" : "items") + ".";
        }
        if (LostTalesMissiveObjectiveData.TYPE_GOTO.equalsIgnoreCase(type)) {
            return "Scout the marked location.";
        }
        if (LostTalesMissiveObjectiveData.TYPE_DELIVER.equalsIgnoreCase(type)) {
            String item = firstNonEmpty(objective.getParam("item", ""), objective.getParam("itemId", ""), objective.getParam("target", ""));
            return "Deliver " + count + " " + (item.length() > 0 ? prettify(item) : count == 1 ? "item" : "items") + ".";
        }
        return prettify(type) + ".";
    }

    private static int getObjectiveCount(LostTalesMissiveObjectiveData objective) {
        try {
            return Math.max(1, Integer.parseInt(objective.getParam("count", "1")));
        } catch (Exception ignored) {
            return 1;
        }
    }

    public static String buildRewardSummary(LostTalesMissiveData missive) {
        if (missive.getRewardData() == null || missive.getRewardData().getRewards().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : missive.getRewardData().getRewards().entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null || entry.getValue().length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(prettify(entry.getKey())).append(' ').append(entry.getValue());
        }
        return builder.toString();
    }

    public static String formatTicks(long ticks) {
        if (ticks >= 24000L && ticks % 24000L == 0L) {
            long days = ticks / 24000L;
            return days + " in-game day" + (days == 1L ? "" : "s");
        }
        if (ticks >= 1200L) {
            long minutes = ticks / 1200L;
            return minutes + " in-game minute" + (minutes == 1L ? "" : "s");
        }
        long seconds = Math.max(1L, ticks / 20L);
        return seconds + " second" + (seconds == 1L ? "" : "s");
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return "";
    }

    private static String prettify(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        int colon = text.indexOf(':');
        if (colon >= 0 && colon + 1 < text.length()) {
            text = text.substring(colon + 1);
        }
        int at = text.indexOf('@');
        if (at >= 0) {
            text = text.substring(0, at);
        }
        text = text.replace('_', ' ').replace('-', ' ');
        return text;
    }
}
