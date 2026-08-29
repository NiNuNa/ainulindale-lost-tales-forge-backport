package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMessageIds;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which drawn line is which message, both ways round.
 *
 * <p>The server names a message by the id it stamped on it; this client
 * draws it under a chat line id of its own. Everything that acts on a
 * message has to cross between the two: a click lands on a line and has
 * to say which message it means, and a message the server has something
 * to say about later — it was edited, it was removed — has to be
 * found among the lines on screen. Both directions are kept here so
 * neither is derived by scanning the history.</p>
 *
 * <p>Lines the client wrote for itself — its half of an NPC
 * conversation, which no server ever sees — are named too, from
 * {@link #nextLocal()}: negative, so a local id can never be mistaken
 * for one the server handed out, and never sent anywhere. A line with
 * no id at all ({@link ChatMessageIds#NONE}) is one nobody can name: a
 * system notice, an adopted stray, a vanilla print.</p>
 *
 * <p>Bounded like the tab index, and cleared with the rest of the
 * client's chat state.</p>
 */
public final class ClientChatMessageIds {
    private static final LinkedHashMap<Integer, Long> BY_LINE =
            new LinkedHashMap<Integer, Long>();
    private static final Map<Long, Integer> BY_MESSAGE =
            new HashMap<Long, Integer>();
    /** Client-local ids run downward from -1, away from the server's. */
    private static long lastLocalId;

    private ClientChatMessageIds() {}

    /** An id for a line this client wrote and nobody else will see. */
    public static synchronized long nextLocal() {
        lastLocalId--;
        return lastLocalId;
    }

    /** Records which message a drawn line is; {@code NONE} records nothing. */
    public static synchronized void remember(int chatLineId, long messageId) {
        if (messageId == ChatMessageIds.NONE) {
            return;
        }
        Long id = Long.valueOf(messageId);
        Long previous = BY_LINE.put(Integer.valueOf(chatLineId), id);
        if (previous != null) {
            BY_MESSAGE.remove(previous);
        }
        BY_MESSAGE.put(id, Integer.valueOf(chatLineId));
        while (BY_LINE.size() > ClientChatChannelViews.maxTrackedLines()) {
            Iterator<Map.Entry<Integer, Long>> oldest =
                    BY_LINE.entrySet().iterator();
            Map.Entry<Integer, Long> entry = oldest.next();
            oldest.remove();
            // Only if the message still points back at the line that is
            // leaving: a replaced line handed its id on already.
            Integer line = BY_MESSAGE.get(entry.getValue());
            if (line != null && line.equals(entry.getKey())) {
                BY_MESSAGE.remove(entry.getValue());
            }
        }
    }

    /** The message a drawn line is, or {@link ChatMessageIds#NONE}. */
    public static synchronized long messageIdOf(int chatLineId) {
        Long id = BY_LINE.get(Integer.valueOf(chatLineId));
        return id == null ? ChatMessageIds.NONE : id.longValue();
    }

    /** The line a message is drawn on, or null when it is not on screen. */
    public static synchronized Integer chatLineIdOf(long messageId) {
        return messageId == ChatMessageIds.NONE ? null
                : BY_MESSAGE.get(Long.valueOf(messageId));
    }

    public static synchronized void clear() {
        BY_LINE.clear();
        BY_MESSAGE.clear();
        lastLocalId = 0L;
    }

    /** Test and diagnostics hook: how many lines are named. */
    static synchronized int size() {
        return BY_LINE.size();
    }
}
