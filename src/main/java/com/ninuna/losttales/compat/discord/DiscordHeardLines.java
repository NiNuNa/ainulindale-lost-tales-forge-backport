package com.ninuna.losttales.compat.discord;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The Discord members' lines the bridge heard in each channel it reads,
 * this server run, by either reader, each with the edit stamp it was last
 * heard with. While the live connection is up, nothing re-reads a
 * channel: Discord says each edit and deletion as it happens. Once the
 * connection drops, the re-read that stands in watches these lines
 * ({@link DiscordMessageSweep#track(String, String)}), so an edit or a
 * deletion of a line heard live, made while the connection is down, is
 * caught; a worker started by a restart of the bridge takes them up the
 * same way.
 *
 * <p>Bounded to the newest {@link #MAX_PER_CHANNEL} lines of each channel,
 * the sweep's own bound, and to {@link #MAX_CHANNELS} channels; the bridge
 * keeps only the channels it reads ({@link #keepOnly}) and forgets
 * everything as the server stops. Any thread.</p>
 */
final class DiscordHeardLines {
    /** Lines kept for one channel: as many as one re-read watches. */
    static final int MAX_PER_CHANNEL = DiscordMessageSweep.MAX_TRACKED;
    /** Channels kept at all; far more than a server links. */
    static final int MAX_CHANNELS = 256;

    /** Discord channel id to message id to edit stamp, oldest first. */
    private final Map<String, LinkedHashMap<String, String>> byChannel =
            new HashMap<String, LinkedHashMap<String, String>>();

    /**
     * A member's line heard in {@code channelId}, as it was sent or as an
     * edit left it. A line heard already keeps its place and the newer of
     * the two stamps; past the bound the oldest line is let go.
     */
    synchronized void heard(String channelId, String messageId, String editedTimestamp) {
        if (channelId == null || channelId.length() == 0 || messageId == null
                || messageId.length() == 0) {
            return;
        }
        LinkedHashMap<String, String> lines = this.byChannel.get(channelId);
        if (lines == null) {
            if (this.byChannel.size() >= MAX_CHANNELS) {
                return;
            }
            lines = new LinkedHashMap<String, String>();
            this.byChannel.put(channelId, lines);
        }
        String stamp = editedTimestamp == null ? "" : editedTimestamp;
        String held = lines.get(messageId);
        if (held != null && !DiscordMessageSweep.isNewer(stamp, held)) {
            return;
        }
        lines.put(messageId, stamp);
        Iterator<String> oldest = lines.keySet().iterator();
        while (lines.size() > MAX_PER_CHANNEL) {
            oldest.next();
            oldest.remove();
        }
    }

    /** A line deleted on Discord: nothing more is to be heard of it. */
    synchronized void forget(String channelId, String messageId) {
        LinkedHashMap<String, String> lines = channelId == null ? null
                : this.byChannel.get(channelId);
        if (lines != null && messageId != null) {
            lines.remove(messageId);
            if (lines.isEmpty()) {
                this.byChannel.remove(channelId);
            }
        }
    }

    /** The lines heard in {@code channelId}, oldest first, with their stamps; a copy. */
    synchronized Map<String, String> linesIn(String channelId) {
        LinkedHashMap<String, String> lines = channelId == null ? null
                : this.byChannel.get(channelId);
        return lines == null ? Collections.<String, String>emptyMap()
                : new LinkedHashMap<String, String>(lines);
    }

    /** Lets go of every channel but {@code channelIds}: the ones a start reads. */
    synchronized void keepOnly(Collection<String> channelIds) {
        Set<String> kept = channelIds == null ? Collections.<String>emptySet()
                : new HashSet<String>(channelIds);
        this.byChannel.keySet().retainAll(kept);
    }

    synchronized void clear() {
        this.byChannel.clear();
    }
}
