package com.ninuna.losttales.compat.discord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * How each kept topic is paced, by Discord channel id, for one world: the
 * words Discord last took on each channel and when it took the last two
 * writes. The world save keeps it beside the message links
 * ({@link DiscordMessageLinkWorldData}), so a restart of the server, like
 * one of the bridge, neither writes words a channel already shows nor
 * spends an edit Discord has not given back.
 *
 * <p>The bridge's worker records the writes and the server thread saves
 * them, so every touch is synchronized, and every write Discord takes
 * moves the {@link #revision()} on, which is how the save knows it has
 * something to write. The save keeps the {@link #MAX_CHANNELS} channels
 * written last.</p>
 */
final class DiscordTopicMemory {
    /** The most channels the save keeps; past it the ones written longest ago are left out. */
    static final int MAX_CHANNELS = 256;

    private final LinkedHashMap<String, DiscordTopicPacing> byChannel =
            new LinkedHashMap<String, DiscordTopicPacing>();
    private final AtomicLong revision = new AtomicLong();

    /** One channel's topic as the save keeps it. */
    static final class SavedTopic {
        /** The time standing for no write before the last one: the first write ever. */
        static final long NO_WRITE = 0L;

        final String channelId;
        /** The words Discord last took. */
        final String words;
        /** When Discord took the last write, in milliseconds of the wall clock. */
        final long lastWrite;
        /** When it took the one before, or {@link #NO_WRITE}. */
        final long writeBefore;

        SavedTopic(String channelId, String words, long lastWrite, long writeBefore) {
            this.channelId = channelId == null ? "" : channelId;
            this.words = words == null ? "" : words;
            this.lastWrite = lastWrite;
            this.writeBefore = writeBefore;
        }
    }

    /** What the save writes: every channel written, as of one revision. */
    static final class Snapshot {
        final List<SavedTopic> topics;
        final long revision;

        Snapshot(List<SavedTopic> topics, long revision) {
            this.topics = Collections.unmodifiableList(topics);
            this.revision = revision;
        }
    }

    /** How the topic of a Discord channel is paced; made at its first use. */
    synchronized DiscordTopicPacing pacingOf(String channelId) {
        String key = channelId == null ? "" : channelId;
        DiscordTopicPacing pacing = this.byChannel.get(key);
        if (pacing == null) {
            pacing = new DiscordTopicPacing(this.revision);
            this.byChannel.put(key, pacing);
        }
        return pacing;
    }

    /** Moves on with every write Discord takes on any channel. */
    long revision() {
        return this.revision.get();
    }

    /**
     * Takes up the topics the save kept, as the server starts. Answers
     * how many it took. Moves the revision on for none of them: the save
     * already holds them.
     */
    synchronized int restore(List<SavedTopic> saved, long nowMillis) {
        if (saved == null) {
            return 0;
        }
        int taken = 0;
        for (SavedTopic topic : saved) {
            if (topic == null || topic.channelId.length() == 0) {
                continue;
            }
            pacingOf(topic.channelId).restore(topic, nowMillis);
            taken++;
        }
        return taken;
    }

    /**
     * Every channel written, in the order the channels were first used,
     * as of the revision read before them: a write made meanwhile leaves
     * the save dirty for the next one. Past {@link #MAX_CHANNELS}, the
     * channels written longest ago are left out.
     */
    synchronized Snapshot snapshot() {
        long asOf = this.revision.get();
        List<SavedTopic> topics = new ArrayList<SavedTopic>();
        for (Map.Entry<String, DiscordTopicPacing> entry : this.byChannel.entrySet()) {
            SavedTopic saved = entry.getValue().saved(entry.getKey());
            if (saved != null) {
                topics.add(saved);
            }
        }
        if (topics.size() > MAX_CHANNELS) {
            List<SavedTopic> newest = new ArrayList<SavedTopic>(topics);
            Collections.sort(newest, new Comparator<SavedTopic>() {
                @Override
                public int compare(SavedTopic first, SavedTopic second) {
                    return first.lastWrite < second.lastWrite ? 1
                            : first.lastWrite > second.lastWrite ? -1 : 0;
                }
            });
            List<SavedTopic> kept = newest.subList(0, MAX_CHANNELS);
            topics.retainAll(kept);
        }
        return new Snapshot(topics, asOf);
    }
}
