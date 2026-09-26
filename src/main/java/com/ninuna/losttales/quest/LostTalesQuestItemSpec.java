package com.ninuna.losttales.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.item.Item;

/**
 * An item as a quest file writes it: {@code id}, {@code id@meta},
 * {@code id*count} or {@code id@meta*count}, as in
 * {@code minecraft:wool@14*2}. The one reading of it, so the item the
 * server grants as a reward and the words every screen shows for that
 * reward are always the same item, count and meta.
 */
public final class LostTalesQuestItemSpec {
    private final String itemId;
    private final int count;
    private final int meta;

    private LostTalesQuestItemSpec(String itemId, int count, int meta) {
        this.itemId = itemId;
        this.count = count;
        this.meta = meta;
    }

    /**
     * Reads a written item. {@code defaultCount} and {@code defaultMeta}
     * stand where it names no count or meta, or one that is not a whole
     * number; a count is 1 at least and a meta 0 at least. An id without
     * a namespace is Minecraft's.
     */
    public static LostTalesQuestItemSpec parse(String written,
            int defaultCount, int defaultMeta) {
        String spec = written == null ? "" : written.trim();
        int count = Math.max(1, defaultCount);
        int meta = Math.max(0, defaultMeta);
        int star = spec.lastIndexOf('*');
        if (star >= 0 && star + 1 < spec.length()) {
            count = Math.max(1, LostTalesQuestParams.parseInt(spec.substring(star + 1), count));
            spec = spec.substring(0, star);
        }
        int at = spec.lastIndexOf('@');
        if (at >= 0 && at + 1 < spec.length()) {
            meta = Math.max(0, LostTalesQuestParams.parseInt(spec.substring(at + 1), meta));
            spec = spec.substring(0, at);
        }
        String id = spec.trim();
        if (id.length() > 0 && id.indexOf(':') < 0) {
            id = "minecraft:" + id;
        }
        return new LostTalesQuestItemSpec(id, count, meta);
    }

    /**
     * A reward map's {@code item}, with the map's {@code count} and
     * {@code meta} standing where the item names none; null when it names
     * no item.
     */
    public static LostTalesQuestItemSpec rewardItem(Map<String, String> rewards) {
        String written = LostTalesQuestParams.value(rewards, "item");
        if (written.length() == 0) {
            return null;
        }
        LostTalesQuestItemSpec item = parse(written,
                LostTalesQuestParams.parseInt(rewards.get("count"), 1),
                LostTalesQuestParams.parseInt(rewards.get("meta"), 0));
        return item.isEmpty() ? null : item;
    }

    /** Each item a reward map's {@code items} names, parted by commas or semicolons. */
    public static List<LostTalesQuestItemSpec> rewardItems(Map<String, String> rewards) {
        List<LostTalesQuestItemSpec> items = new ArrayList<LostTalesQuestItemSpec>();
        String written = LostTalesQuestParams.value(rewards, "items");
        if (written.length() == 0) {
            return items;
        }
        for (String part : written.replace(';', ',').split(",")) {
            LostTalesQuestItemSpec item = parse(part, 1, 0);
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    /** The namespaced id as written; empty when the text names none. */
    public String getItemId() {
        return this.itemId;
    }

    public int getCount() {
        return this.count;
    }

    public int getMeta() {
        return this.meta;
    }

    public boolean isEmpty() {
        return this.itemId.length() == 0;
    }

    /**
     * The item the id names in this game, looked up as written and then
     * in lower case; null when it names none.
     */
    public Item item() {
        if (isEmpty()) {
            return null;
        }
        Object found = Item.itemRegistry.getObject(this.itemId);
        if (!(found instanceof Item)) {
            found = Item.itemRegistry.getObject(
                    this.itemId.toLowerCase(Locale.ROOT));
        }
        return found instanceof Item ? (Item) found : null;
    }
}
