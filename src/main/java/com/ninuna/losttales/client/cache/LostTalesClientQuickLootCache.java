package com.ninuna.losttales.client.cache;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LostTalesClientQuickLootCache {
    private static Snapshot snapshot;

    private LostTalesClientQuickLootCache() {}

    public static void update(int x, int y, int z, String title, boolean sealed, ItemStack[] items) {
        snapshot = new Snapshot(x, y, z, title, sealed, items);
    }

    public static Snapshot get(int x, int y, int z) {
        if (snapshot != null && snapshot.x == x && snapshot.y == y && snapshot.z == z) {
            return snapshot;
        }
        return null;
    }

    public static void clear() {
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
            this.title = localizeTitle(title);
            this.sealed = sealed;
            this.items = items == null ? new ItemStack[0] : copy(items);
        }

        private static String localizeTitle(String title) {
            if (title == null || title.length() == 0) {
                return "Container";
            }

            String translated = StatCollector.translateToLocal(title);
            if (translated != null && translated.length() > 0) {
                return translated;
            }
            return title;
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
