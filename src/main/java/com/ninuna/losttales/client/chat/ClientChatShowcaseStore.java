package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShowcase;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.item.ItemStack;

/**
 * Client-only registry of shared things decoded once on arrival and
 * referenced from chat components by a small integer key. Bounded above
 * the vanilla chat history (100 messages), so every line still on screen
 * keeps its payload while old entries age out; a dropped key renders as
 * text.
 */
public final class ClientChatShowcaseStore {
    private static final int MAX_ENTRIES = 256;
    private static final LinkedHashMap<Integer, Entry> ENTRIES =
            new LinkedHashMap<Integer, Entry>();
    private static int nextId;

    private ClientChatShowcaseStore() {}

    static synchronized int registerItem(ItemStack stack) {
        return stack == null ? -1 : register(new Entry(stack, null));
    }

    static synchronized int registerMarker(ChatShowcase showcase) {
        if (showcase == null || showcase.getKind() != ChatShareKind.MARKER) {
            return -1;
        }
        return register(new Entry(null, new Marker(showcase)));
    }

    static synchronized int registerQuest(ChatShowcase showcase,
                                           long messageId) {
        if (showcase == null || showcase.getKind() != ChatShareKind.QUEST) {
            return -1;
        }
        return register(new Entry(null, null,
                new Quest(showcase, messageId)));
    }

    private static int register(Entry entry) {
        int id = nextId++;
        ENTRIES.put(Integer.valueOf(id), entry);
        while (ENTRIES.size() > MAX_ENTRIES) {
            Iterator<Map.Entry<Integer, Entry>> iterator =
                    ENTRIES.entrySet().iterator();
            iterator.next();
            iterator.remove();
        }
        return id;
    }

    static synchronized ItemStack getItem(int id) {
        Entry entry = id < 0 ? null : ENTRIES.get(Integer.valueOf(id));
        return entry == null ? null : entry.stack;
    }

    static synchronized Marker getMarker(int id) {
        Entry entry = id < 0 ? null : ENTRIES.get(Integer.valueOf(id));
        return entry == null ? null : entry.marker;
    }

    static synchronized Quest getQuest(int id) {
        Entry entry = id < 0 ? null : ENTRIES.get(Integer.valueOf(id));
        return entry == null ? null : entry.quest;
    }

    public static synchronized void clear() {
        ENTRIES.clear();
        nextId = 0;
    }

    private static final class Entry {
        final ItemStack stack;
        final Marker marker;
        final Quest quest;

        Entry(ItemStack stack, Marker marker) {
            this(stack, marker, null);
        }

        Entry(ItemStack stack, Marker marker, Quest quest) {
            this.stack = stack;
            this.marker = marker;
            this.quest = quest;
        }
    }

    /** The public marker fields a recipient needs to draw, describe, and open. */
    static final class Marker {
        final String id;
        final String name;
        final String iconName;
        final String colorName;
        final int dimensionId;
        final double x;
        final double z;

        Marker(ChatShowcase showcase) {
            this.id = showcase.getMarkerId();
            this.name = showcase.getMarkerName();
            this.iconName = showcase.getMarkerIcon();
            this.colorName = showcase.getMarkerColor();
            this.dimensionId = showcase.getMarkerDimension();
            this.x = showcase.getMarkerX();
            this.z = showcase.getMarkerZ();
        }
    }

    /** Immutable quest-card fields validated by the sending server. */
    static final class Quest {
        final long messageId;
        final int tokenIndex;
        final String reference;
        final String title;
        final String category;
        final String objective;
        final String reward;
        final boolean joinable;

        Quest(ChatShowcase showcase, long messageId) {
            this.messageId = messageId;
            this.tokenIndex = showcase.getTokenIndex();
            this.reference = showcase.getQuestReference();
            this.title = showcase.getQuestTitle();
            this.category = showcase.getQuestCategory();
            this.objective = showcase.getQuestObjective();
            this.reward = showcase.getQuestReward();
            this.joinable = showcase.isQuestJoinable()
                    && com.ninuna.losttales.chat.ChatMessageIds.isServerId(
                            messageId);
        }
    }
}
