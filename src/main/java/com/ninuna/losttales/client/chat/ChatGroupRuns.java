package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.config.LostTalesConfig;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.util.IChatComponent;

/**
 * What a view needs to decide for itself whether a message continues its
 * sender's run.
 *
 * <p>Grouping is presentation, and two views of the same history do not
 * see the same neighbours: a channel's own window sees only that
 * channel's messages, while the closed feed sees every unmuted channel
 * interleaved. One decision taken when the message was printed would be
 * right in at most one of them, so none is taken there. Vanilla's
 * history keeps every message in full, header and all; this keeps the
 * sender identity each message was signed with and the grouped form of
 * its line, and every view walks the sequence it actually shows.</p>
 *
 * <p>How long a run stays open is the view's too, and so is what the
 * span is measured from, because the views do not keep a message for
 * the same length of time.</p>
 *
 * <p>A window is a scrollable log: its runs hold for as long as a
 * conversation reads as one, measured from the message that
 * <em>opened</em> the run ({@link #continuationsOf},
 * {@link #GROUP_WINDOW_MILLIS}), so one speaker cannot extend a single
 * run all evening.</p>
 *
 * <p>The closed feed drops a run when it fades, and it fades a whole
 * run at once on the clock of its newest message. A run there
 * therefore continues while the group is still on screen, which is
 * exactly while the message <em>before</em> this one is still within
 * the fade ({@link #continuationsInFeed}, {@link #FEED_RUN_MILLIS}) —
 * the group's clock is that message's arrival right up until this one
 * joins it. Nothing but silence long enough for the whole group to go,
 * or another voice, starts a new run there.</p>
 *
 * <p>A message with no entry here — a system line, an adopted stray, a
 * line printed straight into vanilla's chat — has no identity to
 * continue, so it ends whatever run it lands in, in every view alike.
 * Entries are bounded like the tab index and go with the rest of the
 * client's chat state.</p>
 */
final class ChatGroupRuns {
    /**
     * How long a sender keeps their run: a message this close behind the
     * previous one from the same identity in the same tab drops the
     * repeated head and name, Discord-style. Short on purpose — chat
     * moves fast, and a header returning after a couple of quiet
     * minutes reads better than one missing after them.
     */
    private static final long GROUP_WINDOW_MILLIS = 120L * 1000L;
    /**
     * How long the closed feed keeps a line on screen, in milliseconds:
     * the fade the renderer draws, in the units a message's timestamp is
     * in. A message this long after the one before it finds the whole
     * group gone and opens one of its own.
     */
    static final long FEED_RUN_MILLIS =
            LostTalesChatOverlayRenderer.FEED_FADE_TICKS * 1000L / 20L;
    private static final LinkedHashMap<Integer, Entry> ENTRIES =
            new LinkedHashMap<Integer, Entry>();

    private ChatGroupRuns() {}

    /**
     * Records what a printed message can be grouped by, and how it reads
     * grouped. A message that is not {@code groupable} always keeps its
     * own header — a reply's quote answers for a sender the grouped form
     * would not name — but still opens a run the messages after it may
     * join.
     */
    static synchronized void remember(int chatLineId, ChatTab tab,
                                      UUID senderId, String identityName,
                                      boolean accountLine,
                                      long timestampMillis,
                                      boolean groupable,
                                      IChatComponent groupedLine) {
        if (tab == null || senderId == null || identityName == null
                || groupedLine == null) {
            return;
        }
        ENTRIES.put(Integer.valueOf(chatLineId), new Entry(tab, senderId,
                identityName, accountLine, timestampMillis, groupable,
                groupedLine));
        while (ENTRIES.size() > ClientChatChannelViews.maxTrackedLines()) {
            Iterator<Integer> oldest = ENTRIES.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /**
     * Replaces how one message reads grouped, leaving everything a run
     * is followed by — sender, identity, time — as it was: an edit
     * changes the words and nothing about whose turn it is to speak.
     * The message keeps its place in the order, so what is evicted next
     * does not change either.
     */
    static synchronized void replaceGroupedLine(int chatLineId,
                                                IChatComponent groupedLine) {
        Entry entry = ENTRIES.get(Integer.valueOf(chatLineId));
        if (entry == null || groupedLine == null) {
            return;
        }
        ENTRIES.put(Integer.valueOf(chatLineId), new Entry(entry.tab,
                entry.senderId, entry.identityName, entry.accountLine,
                entry.timestampMillis, entry.groupable, groupedLine));
    }

    /**
     * Forgets a message that is no longer shown. The run closes over the
     * gap: the messages either side of a removed one are followed
     * against each other, which is what the view now draws.
     */
    static synchronized void forget(int chatLineId) {
        ENTRIES.remove(Integer.valueOf(chatLineId));
    }

    /** What the line was signed with, or null when it is not groupable. */
    static synchronized Entry of(int chatLineId) {
        return ENTRIES.get(Integer.valueOf(chatLineId));
    }

    /**
     * Which of a view's messages read as continuations, for the line ids
     * it shows in the history's own newest-first order. The run is
     * followed over this sequence alone, oldest first: a message the
     * view leaves out never breaks a run in it, however it reads in the
     * feed or in another window. For a view that keeps its messages — a
     * window — a run holds while the sender keeps talking.
     */
    static synchronized boolean[] continuationsOf(int[] lineIdsNewestFirst) {
        return walk(lineIdsNewestFirst, GROUP_WINDOW_MILLIS, true);
    }

    /**
     * As above for the closed feed, where the span is measured from the
     * message before rather than from the run's head: the group is on
     * screen for as long as that one is, so anything arriving inside
     * the fade joins it however long the run has been going.
     */
    static synchronized boolean[] continuationsInFeed(
            int[] lineIdsNewestFirst) {
        return walk(lineIdsNewestFirst, FEED_RUN_MILLIS, false);
    }

    /**
     * The one walk, oldest first, carrying the message before this one —
     * which decides whether it is the same voice, and, unless the span
     * is measured {@code fromHead}, whether the run is still open — and
     * the message that opened the run.
     */
    private static boolean[] walk(int[] lineIds, long spanMillis,
                                  boolean fromHead) {
        boolean[] grouped = new boolean[lineIds == null ? 0 : lineIds.length];
        Entry previous = null;
        Entry runHead = null;
        for (int index = grouped.length - 1; index >= 0; index--) {
            Entry entry = of(lineIds[index]);
            Entry against = fromHead ? runHead : previous;
            grouped[index] = entry != null && entry.groupable
                    && sameVoice(previous, entry)
                    && against != null
                    && entry.timestampMillis - against.timestampMillis
                            <= spanMillis;
            if (!grouped[index]) {
                runHead = entry;
            }
            previous = entry;
        }
        return grouped;
    }

    /**
     * Whether the two are the same voice speaking on: the same sender,
     * as the same identity, in the same tab, in that order in time.
     * {@code previous} is the message before this one <em>in the view
     * being laid out</em>, not in the history. How long a run may stay
     * open is not decided here.
     */
    private static boolean sameVoice(Entry previous, Entry next) {
        return LostTalesConfig.enableChatMessageGrouping
                && previous != null && next != null
                && previous.tab.equals(next.tab)
                && previous.senderId.equals(next.senderId)
                && previous.accountLine == next.accountLine
                && previous.identityName.equalsIgnoreCase(next.identityName)
                && next.timestampMillis >= previous.timestampMillis;
    }

    static synchronized void clear() {
        ENTRIES.clear();
    }

    /** One printed message's sender identity and its grouped line. */
    static final class Entry {
        final ChatTab tab;
        final UUID senderId;
        final String identityName;
        final boolean accountLine;
        final long timestampMillis;
        /** Whether this message may ever be shown without its header. */
        final boolean groupable;
        /** The line without its repeated header; a view picks between the two. */
        final IChatComponent groupedLine;

        private Entry(ChatTab tab, UUID senderId, String identityName,
                      boolean accountLine, long timestampMillis,
                      boolean groupable, IChatComponent groupedLine) {
            this.groupable = groupable;
            this.tab = tab;
            this.senderId = senderId;
            this.identityName = identityName;
            this.accountLine = accountLine;
            this.timestampMillis = timestampMillis;
            this.groupedLine = groupedLine;
        }
    }
}
