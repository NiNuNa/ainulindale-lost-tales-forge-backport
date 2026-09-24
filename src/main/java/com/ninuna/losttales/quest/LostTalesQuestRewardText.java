package com.ninuna.losttales.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/**
 * What a quest pays, in the words every place shows: the journal, the
 * conversation, a quest card in the chat and a missive letter. Items go
 * by their names, never their ids ({@code 2x Bread}). Works on both
 * sides: a dedicated server names items in its own language.
 */
public final class LostTalesQuestRewardText {

    private LostTalesQuestRewardText() {}

    /** Each reward of a quest's reward map as one phrase, in the map's order. */
    public static List<String> phrases(Map<String, String> rewards) {
        List<String> result = new ArrayList<String>();
        if (rewards == null) {
            return result;
        }
        for (Map.Entry<String, String> entry : rewards.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (value.length() == 0) {
                continue;
            }
            if (isOneOf(key, "experience", "xp", "experiencePoints")) {
                result.add(StatCollector.translateToLocalFormatted(
                        "gui.losttales.quest.reward.experience", value));
            } else if (isOneOf(key, "levels", "experienceLevels", "xpLevels")) {
                result.add(StatCollector.translateToLocalFormatted("1".equals(value)
                        ? "gui.losttales.quest.reward.level"
                        : "gui.losttales.quest.reward.levels", value));
            } else if (isOneOf(key, "items", "stacks", "itemStacks")) {
                for (String part : value.replace(';', ',').split(",")) {
                    String item = itemPhrase(part);
                    if (item.length() > 0) {
                        result.add(item);
                    }
                }
            } else if (isOneOf(key, "item", "itemId", "stack")) {
                String item = itemPhrase(value);
                if (item.length() > 0) {
                    result.add(item);
                }
            } else {
                result.add(prettify(key) + ": " + value);
            }
        }
        return result;
    }

    /** The phrases on one line, parted by commas; empty for no reward. */
    public static String summary(Map<String, String> rewards) {
        StringBuilder line = new StringBuilder();
        for (String phrase : phrases(rewards)) {
            if (line.length() > 0) {
                line.append(", ");
            }
            line.append(phrase);
        }
        return line.toString();
    }

    /**
     * One item reward as written in a quest file, {@code minecraft:bread@0*2},
     * as {@code 2x Bread}: the item's own name, or its id made readable for
     * an item this game does not have.
     */
    static String itemPhrase(String written) {
        String spec = written == null ? "" : written.trim();
        if (spec.length() == 0) {
            return "";
        }
        int count = 1;
        int meta = 0;
        int star = spec.lastIndexOf('*');
        if (star >= 0 && star + 1 < spec.length()) {
            count = Math.max(1, parseInt(spec.substring(star + 1), 1));
            spec = spec.substring(0, star);
        }
        int at = spec.lastIndexOf('@');
        if (at >= 0 && at + 1 < spec.length()) {
            meta = Math.max(0, parseInt(spec.substring(at + 1), 0));
            spec = spec.substring(0, at);
        }
        if (spec.indexOf(':') < 0) {
            spec = "minecraft:" + spec;
        }
        return (count > 1 ? count + "x " : "") + itemName(spec, meta);
    }

    private static String itemName(String id, int meta) {
        String readable = prettify(id.substring(id.indexOf(':') + 1));
        Object item;
        try {
            item = Item.itemRegistry.getObject(id);
        } catch (RuntimeException unreadable) {
            return readable;
        }
        if (!(item instanceof Item)) {
            return readable;
        }
        try {
            // Another mod may name its item with code only a client has.
            String name = new ItemStack((Item)item, 1, meta).getDisplayName();
            return name == null || name.trim().length() == 0 ? readable : name;
        } catch (RuntimeException unnamed) {
            return readable;
        }
    }

    private static boolean isOneOf(String key, String... names) {
        for (String name : names) {
            if (name.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    /** An id or a key made readable: {@code iron_ingot} as {@code Iron ingot}. */
    static String prettify(String value) {
        String text = value == null ? "" : value.replace('_', ' ').trim();
        return text.length() == 0 ? ""
                : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }
}
