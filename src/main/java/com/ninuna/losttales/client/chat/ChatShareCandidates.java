package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareReference;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerVisibility;
import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * The things the local player may currently share, in a deterministic
 * order with duplicate names numbered, so the pickers, the typed
 * completion list, and send-time token resolution all agree on which
 * {@code [i:Name#2]} means which stack and which {@code [m:Name]} means
 * which marker. Items come from the main inventory and armour slots (the
 * range the server accepts); markers from the synced marker list, limited
 * to those the map would show. Nothing here is trusted by the server —
 * it re-resolves every reference itself.
 */
final class ChatShareCandidates {
    /** Slots 0–8 of {@code InventoryPlayer} are the hotbar. */
    static final int HOTBAR_END = 8;
    /** Slots 36–39 are the armour pieces. */
    static final int ARMOR_START = 36;

    private ChatShareCandidates() {}

    /** Shareable stacks in slot order with duplicate-name ordinals. */
    static List<ItemEntry> items(EntityPlayer player) {
        if (player == null || player.inventory == null) {
            return Collections.emptyList();
        }
        List<ItemEntry> entries = new ArrayList<ItemEntry>();
        List<String> seenNames = new ArrayList<String>();
        List<Integer> seenCounts = new ArrayList<Integer>();
        for (int slot = 0; slot <= ChatShareReference.MAX_ITEM_SLOT; slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack == null || stack.getItem() == null
                    || stack.stackSize <= 0) {
                continue;
            }
            String name = ChatShareTokenParser.plainName(stack.getDisplayName());
            int ordinal = nextOrdinal(seenNames, seenCounts, name);
            if (ordinal > 0) {
                entries.add(new ItemEntry(slot, stack, name, ordinal));
            }
        }
        return entries;
    }

    /**
     * Map markers the player has actually reached — discovered and
     * region-unlocked, the same rule the server applies on send — in store
     * order with duplicate-name ordinals.
     */
    static List<MarkerEntry> markers() {
        List<MarkerEntry> entries = new ArrayList<MarkerEntry>();
        List<String> seenNames = new ArrayList<String>();
        List<Integer> seenCounts = new ArrayList<Integer>();
        for (LostTalesMapMarkerData marker
                : LostTalesClientMapMarkerStore.getAllMarkers()) {
            if (marker == null || marker.getId() == null
                    || marker.getId().length() == 0
                    || !LostTalesClientMapMarkerVisibility.isVisited(marker)) {
                continue;
            }
            String name = ChatShareTokenParser.plainName(marker.getName());
            int ordinal = nextOrdinal(seenNames, seenCounts, name);
            if (ordinal > 0) {
                entries.add(new MarkerEntry(marker, name, ordinal));
            }
        }
        return entries;
    }

    /** Next ordinal for a name, or 0 when the name cannot be a token. */
    private static int nextOrdinal(List<String> seenNames,
                                   List<Integer> seenCounts, String name) {
        if (name.length() == 0
                || name.length() > ChatShareTokenParser.MAX_NAME_LENGTH) {
            return 0;
        }
        String key = ChatShareTokenParser.normalizeName(name);
        int position = seenNames.indexOf(key);
        int ordinal;
        if (position < 0) {
            seenNames.add(key);
            seenCounts.add(Integer.valueOf(1));
            ordinal = 1;
        } else {
            ordinal = seenCounts.get(position).intValue() + 1;
            seenCounts.set(position, Integer.valueOf(ordinal));
        }
        return ordinal > ChatShareTokenParser.MAX_ORDINAL ? 0 : ordinal;
    }

    static boolean sameItems(List<ItemEntry> left, List<ItemEntry> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            ItemEntry a = left.get(index);
            ItemEntry b = right.get(index);
            if (a.slot != b.slot || a.ordinal != b.ordinal
                    || !a.name.equals(b.name)
                    || !ItemStack.areItemStacksEqual(a.stack, b.stack)) {
                return false;
            }
        }
        return true;
    }

    /** Common view of an entry for the completion list and pickers. */
    abstract static class Entry {
        final String name;
        final int ordinal;

        Entry(String name, int ordinal) {
            this.name = name;
            this.ordinal = ordinal;
        }

        abstract ChatShareKind kind();

        String label() {
            return this.ordinal > 1
                    ? this.name + " (" + this.ordinal + ")" : this.name;
        }

        String token() {
            return ChatShareTokenParser.buildToken(kind(), this.name,
                    this.ordinal);
        }

        boolean matchesToken(ChatShareTokenParser.Token token) {
            return token != null && token.kind == kind()
                    && token.ordinal == this.ordinal
                    && token.normalizedName().equals(
                            ChatShareTokenParser.normalizeName(this.name));
        }
    }

    static final class ItemEntry extends Entry {
        final int slot;
        final ItemStack stack;

        ItemEntry(int slot, ItemStack stack, String name, int ordinal) {
            super(name, ordinal);
            this.slot = slot;
            this.stack = stack;
        }

        @Override
        ChatShareKind kind() {
            return ChatShareKind.ITEM;
        }
    }

    static final class MarkerEntry extends Entry {
        final LostTalesMapMarkerData marker;

        MarkerEntry(LostTalesMapMarkerData marker, String name,
                    int ordinal) {
            super(name, ordinal);
            this.marker = marker;
        }

        @Override
        ChatShareKind kind() {
            return ChatShareKind.MARKER;
        }
    }
}
