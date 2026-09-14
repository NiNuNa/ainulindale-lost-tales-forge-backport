package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatDeliveryMark;
import com.ninuna.losttales.chat.ChatMessageIds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where the Discord posts of the players' own lines have got, so a
 * sender can be told when theirs is slow or will not arrive. A line goes
 * out as one post per binding that posts it, so one message can wait in
 * several lanes at once: each copy is counted as it reaches a lane, and
 * the line is settled when its last copy is. The sender hears at most
 * two things about a line — a clock once it has waited
 * {@link #RETRY_MARK_AFTER_MILLIS} since it was said, then either the
 * clock lifted when every copy went out or a crimson mark as soon as one
 * copy will not — and the worst of its copies is what they hear: a copy
 * lost is not undone by another delivered.
 *
 * <p>Worker thread only; nothing here is synchronized. Bounded to
 * {@link #MAX_TRACKED} lines, the oldest falling out first and saying
 * nothing more. A line with no sender, a notice of the server's own, is
 * never tracked.</p>
 */
final class DiscordDeliveryTracker {
    /** How long a line waits before its sender is shown a clock. */
    static final long RETRY_MARK_AFTER_MILLIS = 5000L;
    /** Lines followed at once; more than the lanes can hold. */
    static final int MAX_TRACKED = 1024;
    /**
     * Lost lines remembered once settled, so a copy of one that reaches
     * its lane afterwards adds nothing to what the sender was told.
     */
    static final int MAX_REMEMBERED_LOST = 256;

    /** The lines followed, by message id, in the order they were queued. */
    private final LinkedHashMap<Long, Entry> entries = new LinkedHashMap<Long, Entry>();
    /** Settled lines whose senders were told they were lost, oldest first. */
    private final LinkedHashSet<Long> lostIds = new LinkedHashSet<Long>();

    /** A copy of a line, queued at {@code queuedAtMillis}, reached its lane. */
    void queued(long messageId, UUID senderId, long queuedAtMillis) {
        if (senderId == null || !ChatMessageIds.isServerId(messageId)) {
            return;
        }
        Long key = Long.valueOf(messageId);
        if (this.lostIds.contains(key)) {
            return;
        }
        Entry entry = this.entries.get(key);
        if (entry == null) {
            entry = new Entry(senderId, queuedAtMillis);
            this.entries.put(key, entry);
            while (this.entries.size() > MAX_TRACKED) {
                Iterator<Long> oldest = this.entries.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
        }
        entry.pending++;
    }

    /** Discord asked the lane a copy of the line waits in to slow down. */
    void limited(long messageId) {
        waitsOn(messageId, ChatDeliveryMark.Reason.LIMITED);
    }

    /** A send of a copy of the line failed and will be tried again. */
    void failed(long messageId) {
        waitsOn(messageId, ChatDeliveryMark.Reason.FAILING);
    }

    /**
     * A copy of the line went out. Answers the lifted clock once the last
     * copy has, when a clock was shown and no copy was lost; null
     * otherwise.
     */
    Mark delivered(long messageId) {
        Long key = Long.valueOf(messageId);
        Entry entry = this.entries.get(key);
        if (entry == null) {
            return null;
        }
        entry.pending--;
        if (entry.pending > 0) {
            return null;
        }
        settle(key, entry);
        return entry.clockShown && !entry.copyLost
                ? new Mark(entry.senderId, messageId,
                        ChatDeliveryMark.State.NONE, ChatDeliveryMark.Reason.NONE)
                : null;
    }

    /**
     * A copy of the line will not go out, for {@code reason}. Answers the
     * crimson mark the first time a copy of the line is lost; null after
     * that.
     */
    Mark lost(long messageId, ChatDeliveryMark.Reason reason) {
        Long key = Long.valueOf(messageId);
        Entry entry = this.entries.get(key);
        if (entry == null) {
            return null;
        }
        entry.pending--;
        boolean first = !entry.copyLost;
        entry.copyLost = true;
        if (entry.pending <= 0) {
            settle(key, entry);
        }
        return first ? new Mark(entry.senderId, messageId,
                ChatDeliveryMark.State.FAILED, reason) : null;
    }

    /**
     * A clock for every line that has waited {@link #RETRY_MARK_AFTER_MILLIS}
     * since it was queued and shows none yet, saying what it waits on.
     */
    List<Mark> due(long nowMillis) {
        List<Mark> marks = null;
        for (Map.Entry<Long, Entry> item : this.entries.entrySet()) {
            Entry entry = item.getValue();
            if (nowMillis - entry.queuedAtMillis < RETRY_MARK_AFTER_MILLIS) {
                // Lines are kept in the order they were queued, so none
                // after this one has waited long enough either.
                break;
            }
            if (entry.clockShown || entry.copyLost) {
                continue;
            }
            entry.clockShown = true;
            if (marks == null) {
                marks = new ArrayList<Mark>();
            }
            marks.add(new Mark(entry.senderId, item.getKey().longValue(),
                    ChatDeliveryMark.State.RETRYING, entry.cause));
        }
        return marks == null ? Collections.<Mark>emptyList() : marks;
    }

    /** When the next clock is due; {@link Long#MAX_VALUE} when none is. */
    long nextDueMillis() {
        for (Entry entry : this.entries.values()) {
            if (!entry.clockShown && !entry.copyLost) {
                return entry.queuedAtMillis + RETRY_MARK_AFTER_MILLIS;
            }
        }
        return Long.MAX_VALUE;
    }

    /**
     * The bridge is stopping and sends nothing more: a crimson mark for
     * every line still waiting whose sender has not been told it was
     * lost, clock or no clock, and everything forgotten.
     */
    List<Mark> abandon() {
        List<Mark> marks = new ArrayList<Mark>();
        for (Map.Entry<Long, Entry> item : this.entries.entrySet()) {
            Entry entry = item.getValue();
            if (!entry.copyLost) {
                marks.add(new Mark(entry.senderId, item.getKey().longValue(),
                        ChatDeliveryMark.State.FAILED,
                        ChatDeliveryMark.Reason.STOPPED));
            }
        }
        this.entries.clear();
        this.lostIds.clear();
        return marks;
    }

    /** Test hook: lines followed. */
    int size() {
        return this.entries.size();
    }

    private void waitsOn(long messageId, ChatDeliveryMark.Reason cause) {
        Entry entry = this.entries.get(Long.valueOf(messageId));
        if (entry != null) {
            entry.cause = cause;
        }
    }

    /** The line's last copy is done: it leaves, remembered when it was lost. */
    private void settle(Long key, Entry entry) {
        this.entries.remove(key);
        if (entry.copyLost) {
            this.lostIds.add(key);
            while (this.lostIds.size() > MAX_REMEMBERED_LOST) {
                Iterator<Long> oldest = this.lostIds.iterator();
                oldest.next();
                oldest.remove();
            }
        }
    }

    /** One thing to tell one sender about one line. */
    static final class Mark {
        final UUID senderId;
        final long messageId;
        final ChatDeliveryMark.State state;
        final ChatDeliveryMark.Reason reason;

        Mark(UUID senderId, long messageId, ChatDeliveryMark.State state,
             ChatDeliveryMark.Reason reason) {
            this.senderId = senderId;
            this.messageId = messageId;
            this.state = state;
            this.reason = reason;
        }
    }

    /** One line followed. */
    private static final class Entry {
        final UUID senderId;
        final long queuedAtMillis;
        /** Copies on a lane that have neither gone out nor been lost. */
        int pending;
        /** Whether the sender was shown a clock. */
        boolean clockShown;
        /** Whether a copy was lost, and the sender told so. */
        boolean copyLost;
        /** What the clock says the line waits on. */
        ChatDeliveryMark.Reason cause = ChatDeliveryMark.Reason.WAITING;

        Entry(UUID senderId, long queuedAtMillis) {
            this.senderId = senderId;
            this.queuedAtMillis = queuedAtMillis;
        }
    }
}
