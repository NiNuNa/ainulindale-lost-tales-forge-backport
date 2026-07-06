package com.ninuna.losttales.quest;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import java.util.Locale;
import java.util.Map;

/** Grants simple optional quest rewards using systems available in Minecraft 1.7.10. */
public final class LostTalesQuestRewardHelper {

    private LostTalesQuestRewardHelper() {}

    public static boolean grantRewards(EntityPlayerMP player, LostTalesQuestDefinition quest) {
        if (player == null || quest == null || quest.getRewards().isEmpty()) {
            return false;
        }

        Map<String, String> rewards = quest.getRewards();
        boolean granted = false;

        int xp = parseInt(firstNonEmpty(rewards.get("experience"), rewards.get("xp"), rewards.get("experiencePoints")), 0);
        if (xp > 0) {
            player.addExperience(xp);
            granted = true;
        }

        int levels = parseInt(firstNonEmpty(rewards.get("levels"), rewards.get("experienceLevels"), rewards.get("xpLevels")), 0);
        if (levels > 0) {
            player.addExperienceLevel(levels);
            granted = true;
        }

        String singleItem = firstNonEmpty(rewards.get("item"), rewards.get("itemId"), rewards.get("stack"));
        if (singleItem.length() > 0) {
            int count = Math.max(1, parseInt(rewards.get("count"), 1));
            int meta = Math.max(0, parseInt(rewards.get("meta"), 0));
            granted |= giveItem(player, singleItem, count, meta);
        }

        String items = firstNonEmpty(rewards.get("items"), rewards.get("stacks"), rewards.get("itemStacks"));
        if (items.length() > 0) {
            String[] entries = items.replace(';', ',').split(",");
            for (String entry : entries) {
                granted |= giveItem(player, entry, 1, 0);
            }
        }

        if (granted) {
            player.addChatMessage(new net.minecraft.util.ChatComponentText(EnumChatFormatting.DARK_AQUA + "[Lost Tales] " + EnumChatFormatting.RESET + EnumChatFormatting.GOLD + "Quest rewards received."));
        }
        return granted;
    }

    private static boolean giveItem(EntityPlayerMP player, String itemSpec, int defaultCount, int defaultMeta) {
        ParsedItem parsed = parseItemSpec(itemSpec, defaultCount, defaultMeta);
        if (parsed.itemId.length() == 0 || parsed.count <= 0) {
            return false;
        }

        Object registered = Item.itemRegistry.getObject(parsed.itemId);
        if (!(registered instanceof Item)) {
            return false;
        }

        ItemStack stack = new ItemStack((Item) registered, parsed.count, parsed.meta);
        if (!player.inventory.addItemStackToInventory(stack)) {
            EntityItem entityItem = player.dropPlayerItemWithRandomChoice(stack, false);
            if (entityItem != null) {
                entityItem.delayBeforeCanPickup = 0;
            }
        }
        return true;
    }

    /**
     * Supports id, id*count, id@meta, and id@meta*count.
     * Example: minecraft:gold_ingot*3 or minecraft:wool@14*2.
     */
    private static ParsedItem parseItemSpec(String value, int defaultCount, int defaultMeta) {
        String spec = value == null ? "" : value.trim();
        int count = Math.max(1, defaultCount);
        int meta = Math.max(0, defaultMeta);

        int star = spec.lastIndexOf('*');
        if (star >= 0 && star + 1 < spec.length()) {
            count = Math.max(1, parseInt(spec.substring(star + 1), count));
            spec = spec.substring(0, star);
        }

        int at = spec.lastIndexOf('@');
        if (at >= 0 && at + 1 < spec.length()) {
            meta = Math.max(0, parseInt(spec.substring(at + 1), meta));
            spec = spec.substring(0, at);
        }

        return new ParsedItem(normalizeResourceId(spec), count, meta);
    }

    private static String normalizeResourceId(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.indexOf(':') < 0 && normalized.length() > 0) {
            normalized = "minecraft:" + normalized;
        }
        return normalized;
    }

    private static String firstNonEmpty(String a, String b, String c) {
        if (a != null && a.trim().length() > 0) return a.trim();
        if (b != null && b.trim().length() > 0) return b.trim();
        if (c != null && c.trim().length() > 0) return c.trim();
        return "";
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static final class ParsedItem {
        private final String itemId;
        private final int count;
        private final int meta;

        private ParsedItem(String itemId, int count, int meta) {
            this.itemId = itemId;
            this.count = count;
            this.meta = meta;
        }
    }
}
