package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatDeliveryMark;
import com.ninuna.losttales.chat.ChatMessageIds;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the server said about the Discord posts of this player's own
 * lines: a clock on one still waiting, a crimson mark on one that will
 * not be posted, and why. Kept by the server's message id, so a mark
 * finds its line whether it arrives before or after the line is drawn,
 * and read by the chat line the renderer is drawing.
 *
 * <p>A mark lasts the session and is not replayed on joining again. A
 * clock never replaces a crimson mark: one copy of a line still waiting
 * does not undo another that failed. Bounded, the oldest falling out
 * first, and cleared with the rest of the client's chat state.</p>
 */
public final class ClientChatDeliveryMarks {
    /** Far more lines than anyone has waiting on Discord at once. */
    static final int MAX_MARKS = 128;

    private static final LinkedHashMap<Long, Mark> BY_MESSAGE =
            new LinkedHashMap<Long, Mark>();

    private ClientChatDeliveryMarks() {}

    /** The mark on a drawn line; {@code NONE} when it has none. */
    public static ChatDeliveryMark.State stateOf(int chatLineId) {
        Mark mark = markOf(chatLineId);
        return mark == null ? ChatDeliveryMark.State.NONE : mark.state;
    }

    /** Why a drawn line is marked; {@code NONE} when it has no mark. */
    public static ChatDeliveryMark.Reason reasonOf(int chatLineId) {
        Mark mark = markOf(chatLineId);
        return mark == null ? ChatDeliveryMark.Reason.NONE : mark.reason;
    }

    /**
     * Takes what the server said about a message: {@code NONE} lifts its
     * mark, and a clock leaves a crimson mark as it is.
     */
    public static synchronized void apply(long messageId,
                                          ChatDeliveryMark.State state,
                                          ChatDeliveryMark.Reason reason) {
        if (!ChatMessageIds.isServerId(messageId) || state == null) {
            return;
        }
        Long key = Long.valueOf(messageId);
        if (state == ChatDeliveryMark.State.NONE) {
            BY_MESSAGE.remove(key);
            return;
        }
        Mark previous = BY_MESSAGE.get(key);
        if (previous != null && previous.state == ChatDeliveryMark.State.FAILED
                && state == ChatDeliveryMark.State.RETRYING) {
            return;
        }
        BY_MESSAGE.put(key, new Mark(state,
                reason == null ? ChatDeliveryMark.Reason.NONE : reason));
        while (BY_MESSAGE.size() > MAX_MARKS) {
            Iterator<Map.Entry<Long, Mark>> oldest =
                    BY_MESSAGE.entrySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    public static synchronized void clear() {
        BY_MESSAGE.clear();
    }

    /** Test and diagnostics hook: messages marked. */
    static synchronized int size() {
        return BY_MESSAGE.size();
    }

    /**
     * The mark on a drawn line, or null. Asked for every line drawn, so
     * a session with no marks answers before the line is looked up.
     */
    private static Mark markOf(int chatLineId) {
        synchronized (ClientChatDeliveryMarks.class) {
            if (BY_MESSAGE.isEmpty()) {
                return null;
            }
        }
        long messageId = ClientChatMessageIds.messageIdOf(chatLineId);
        if (!ChatMessageIds.isServerId(messageId)) {
            return null;
        }
        synchronized (ClientChatDeliveryMarks.class) {
            return BY_MESSAGE.get(Long.valueOf(messageId));
        }
    }

    /** What the server said about one message. */
    private static final class Mark {
        final ChatDeliveryMark.State state;
        final ChatDeliveryMark.Reason reason;

        Mark(ChatDeliveryMark.State state, ChatDeliveryMark.Reason reason) {
            this.state = state;
            this.reason = reason;
        }
    }
}
