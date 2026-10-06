package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.share.ChatQuestCard;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNamedAfter;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNames;
import com.ninuna.losttales.quest.LostTalesQuestCardWords;
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
            // A bundled marker reads in this game's language; words an
            // operator gave one stay as they came; one with no name of its
            // own is named after what it is called after.
            String called = showcase.getMarkerName().trim().length() == 0
                    ? LostTalesMapMarkerNamedAfter.shownName(
                            showcase.getMarkerNamedAfter()) : "";
            this.name = called.length() > 0 ? called
                    : LostTalesMapMarkerNames.shownName(this.id,
                            showcase.getMarkerName());
            this.iconName = showcase.getMarkerIcon();
            this.colorName = showcase.getMarkerColor();
            this.dimensionId = showcase.getMarkerDimension();
            this.x = showcase.getMarkerX();
            this.z = showcase.getMarkerZ();
        }
    }

    /**
     * A quest card's fields in this game's words, made once from the card
     * the sending server validated ({@link LostTalesQuestCardWords}).
     */
    static final class Quest {
        final long messageId;
        final int tokenIndex;
        final String title;
        final String category;
        final String objective;
        final String reward;
        final boolean joinable;

        Quest(ChatShowcase showcase, long messageId) {
            this.messageId = messageId;
            this.tokenIndex = showcase.getTokenIndex();
            ChatQuestCard card = showcase.getQuestCard();
            this.title = LostTalesQuestCardWords.title(
                    showcase.getQuestReference(), card);
            this.category = card.getCategory();
            this.objective = LostTalesQuestCardWords.objectives(
                    showcase.getQuestReference(), card);
            this.reward = LostTalesQuestCardWords.reward(card);
            this.joinable = showcase.isQuestJoinable()
                    && com.ninuna.losttales.chat.ChatMessageIds.isServerId(
                            messageId);
        }
    }
}
