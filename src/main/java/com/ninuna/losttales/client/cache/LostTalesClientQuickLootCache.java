package com.ninuna.losttales.client.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

public final class LostTalesClientQuickLootCache {
    /** The word for a container the server names nothing. */
    public static final String CONTAINER_KEY = "quickLootHud.losttales.container";
    /** A sealed container's title: {@code %s (Sealed)}. */
    public static final String SEALED_KEY = "quickLootHud.losttales.sealed_title";
    private static volatile Snapshot snapshot;

    private LostTalesClientQuickLootCache() {}

    public static synchronized void update(int x, int y, int z, String title, boolean sealed, ItemStack[] items) {
        snapshot = new Snapshot(x, y, z, title, sealed, items);
    }

    public static synchronized Snapshot get(int x, int y, int z) {
        if (snapshot != null && snapshot.x == x && snapshot.y == y && snapshot.z == z) {
            return snapshot;
        }
        return null;
    }

    public static synchronized void clear() {
        snapshot = null;
    }

    public static final class Snapshot {
        public final int x;
        public final int y;
        public final int z;
        public final String title;
        public final boolean sealed;
        public final ItemStack[] items;

        private Snapshot(int x, int y, int z, String title, boolean sealed, ItemStack[] items) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.title = localizeTitle(title, sealed);
            this.sealed = sealed;
            this.items = items == null ? new ItemStack[0] : copy(items);
        }

        /**
         * The container's title in the game's language: a key translated, a
         * name of its own as it is, none as the word for a container; a
         * sealed one says so.
         */
        private static String localizeTitle(String title, boolean sealed) {
            String translated = title == null || title.length() == 0
                    ? StatCollector.translateToLocal(CONTAINER_KEY)
                    : StatCollector.translateToLocal(title);
            if (translated == null || translated.length() == 0) {
                translated = title;
            }
            return sealed ? StatCollector.translateToLocalFormatted(
                    SEALED_KEY, translated) : translated;
        }

        public List<Integer> getNonEmptySlots() {
            if (this.items.length == 0) return Collections.emptyList();
            List<Integer> slots = new ArrayList<Integer>();
            for (int i = 0; i < this.items.length; i++) {
                ItemStack stack = this.items[i];
                if (stack != null && stack.stackSize > 0) {
                    slots.add(i);
                }
            }
            return slots;
        }

        public ItemStack getStack(int slot) {
            if (slot < 0 || slot >= this.items.length) return null;
            return this.items[slot];
        }

        private static ItemStack[] copy(ItemStack[] source) {
            ItemStack[] result = new ItemStack[source.length];
            for (int i = 0; i < source.length; i++) {
                result[i] = source[i] == null ? null : source[i].copy();
            }
            return result;
        }
    }
}
