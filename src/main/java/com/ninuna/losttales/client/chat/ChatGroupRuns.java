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
 * <p>Every view holds a run for the same time: eight minutes from the
 * message that <em>opened</em> it ({@link #GROUP_WINDOW_MILLIS}), so one
 * speaker cannot extend a single run all evening, wherever it is
 * read.</p>
 *
 * <p>The closed feed also drops a run when it fades, and it fades a
 * whole run at once on the clock of its newest message. A message there
 * can only continue a group still on screen, which is exactly while the
 * message <em>before</em> it is within the fade
 * ({@link #continuationsInFeed}, {@link #FEED_RUN_MILLIS}); after longer
 * silence it would stand headless under nothing, so it opens a run with
 * its name.</p>
 *
 * <p>A message with no entry here — a system line, an adopted stray, a
 * line printed straight into vanilla's chat — has no identity to
 * continue, so it ends whatever run it lands in, in every view alike.
 * Entries are bounded like the tab index and go with the rest of the
 * client's chat state.</p>
 */
final class ChatGroupRuns {
    /**
     * How long a sender keeps their run, in every view: a message from
     * the same identity in the same tab no later than this after the one
     * that opened the run drops the repeated head and name. Eight
     * minutes, as Discord ends a group eight minutes after its first
     * message.
     */
    private static final long GROUP_WINDOW_MILLIS = 8L * 60L * 1000L;
    /**
     * How long the closed feed keeps a line on screen, in milliseconds:
     * the fade the renderer draws, in the units a message's timestamp is
     * in. A message there this long after the one before it finds the
     * whole group gone and opens one of its own.
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
        // Remembered under the conversation the line is read in: a
        // scoped channel's row tab and its conversation tab are one
        // place to the reader, and a line filed under one — this
        // player's own echo, a command's echo — must run on with lines
        // filed under the other.
        ENTRIES.put(Integer.valueOf(chatLineId), new Entry(ChatTab.viewed(tab),
                senderId, identityName, accountLine, timestampMillis,
                groupable, groupedLine));
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
        return walk(lineIdsNewestFirst, Long.MAX_VALUE, null);
    }

    /**
     * As {@link #continuationsOf(int[])}, with every message
     * {@code opensRun} marks opening a run of its own whatever stands
     * before it: in a window, each day's first message, which stands
     * under the day's rule. The run after it is measured from it.
     */
    static synchronized boolean[] continuationsOf(int[] lineIdsNewestFirst,
                                                  boolean[] opensRun) {
        return walk(lineIdsNewestFirst, Long.MAX_VALUE, opensRun);
    }

    /**
     * As above for the closed feed, where a message also needs the one
     * before it still on screen: the group fades with its newest line,
     * and a message after it has gone would stand headless.
     */
    static synchronized boolean[] continuationsInFeed(
            int[] lineIdsNewestFirst) {
        return walk(lineIdsNewestFirst, FEED_RUN_MILLIS, null);
    }

    /**
     * The one walk, oldest first, carrying the message before this one —
     * which decides whether it is the same voice, and whether it lies
     * within {@code previousSpanMillis} — and the message that opened the
     * run, which the run's eight minutes are measured from. A message
     * {@code opensRun} marks opens a run of its own.
     */
    private static boolean[] walk(int[] lineIds, long previousSpanMillis,
                                  boolean[] opensRun) {
        boolean[] grouped = new boolean[lineIds == null ? 0 : lineIds.length];
        Entry previous = null;
        Entry runHead = null;
        for (int index = grouped.length - 1; index >= 0; index--) {
            Entry entry = of(lineIds[index]);
            boolean opens = opensRun != null && index < opensRun.length
                    && opensRun[index];
            grouped[index] = !opens && entry != null && entry.groupable
                    && sameVoice(previous, entry)
                    && runHead != null
                    && entry.timestampMillis - runHead.timestampMillis
                            <= GROUP_WINDOW_MILLIS
                    && entry.timestampMillis - previous.timestampMillis
                            <= previousSpanMillis;
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
