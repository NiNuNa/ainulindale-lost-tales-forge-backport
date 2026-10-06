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
            if (isOneOf(key, "experience")) {
                result.add(StatCollector.translateToLocalFormatted(
                        "gui.losttales.quest.reward.experience", value));
            } else if (isOneOf(key, "levels")) {
                result.add(StatCollector.translateToLocalFormatted("1".equals(value)
                        ? "gui.losttales.quest.reward.level"
                        : "gui.losttales.quest.reward.levels", value));
            } else if ("items".equals(key)) {
                // Named as the server reads them to grant them.
                for (LostTalesQuestItemSpec item
                        : LostTalesQuestItemSpec.rewardItems(rewards)) {
                    addPhrase(result, itemPhrase(item));
                }
            } else if ("item".equals(key)) {
                addPhrase(result, itemPhrase(
                        LostTalesQuestItemSpec.rewardItem(rewards)));
            } else if (("count".equals(key) || "meta".equals(key))
                    && LostTalesQuestParams.value(rewards, "item").length() > 0) {
                // Parts of the item, which its own phrase says.
                continue;
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
    public static String itemPhrase(String written) {
        return itemPhrase(LostTalesQuestItemSpec.parse(written, 1, 0));
    }

    /** One item as {@code 2x Bread}; empty for none. */
    static String itemPhrase(LostTalesQuestItemSpec item) {
        if (item == null || item.isEmpty()) {
            return "";
        }
        return (item.getCount() > 1 ? item.getCount() + "x " : "")
                + itemName(item);
    }

    private static String itemName(LostTalesQuestItemSpec spec) {
        String id = spec.getItemId();
        String readable = prettify(id.substring(id.indexOf(':') + 1));
        Item item;
        try {
            item = spec.item();
        } catch (RuntimeException unreadable) {
            return readable;
        }
        if (item == null) {
            return readable;
        }
        try {
            // Another mod may name its item with code only a client has.
            String name = new ItemStack(item, 1, spec.getMeta()).getDisplayName();
            return name == null || name.trim().length() == 0 ? readable : name;
        } catch (RuntimeException unnamed) {
            return readable;
        }
    }

    private static void addPhrase(List<String> phrases, String phrase) {
        if (phrase.length() > 0) {
            phrases.add(phrase);
        }
    }

    private static boolean isOneOf(String key, String... names) {
        for (String name : names) {
            if (name.equals(key)) {
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
}
