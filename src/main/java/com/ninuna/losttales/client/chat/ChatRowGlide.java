package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.motion.MotionIds;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.util.IChatComponent;

/**
 * How a view's rows move when the messages it shows are laid out again:
 * the window narrowed or widened and its words wrapped anew, a message
 * rewritten where it stands, the unread divider come or gone. Every row
 * glides from where it was drawn to where it now belongs and settles
 * there past its place by a hair and back, and a row the new layout adds
 * fades in where it lands, so words flowing onto another line read as
 * the text moving rather than the stack jumping.
 *
 * <p>A row is known from one layout to the next by the message it
 * belongs to and its place among that message's rows of its kind,
 * counted from the message's top: the speaker's row, a reply's quote,
 * the first row of words, the second, the first row of chips. A day's
 * rule is known by its day. A blank row draws nothing and is not
 * followed.</p>
 *
 * <p>Only a layout of the same messages moves this way. A message
 * arriving or leaving, or another tab's messages coming in, is a cut:
 * the newest message has an entrance of its own, and a row already on
 * its way finishes its trip. The draw halts every trip while the view is
 * scrolled away from the newest line, where the scroll keeps the line
 * being read in place instead.</p>
 *
 * <p>Distances are pixels of stack, measured up from its bottom. A trip
 * is read from the instant it began, and a layout arriving during one
 * sets the row off again from where it is drawn at that instant, so a
 * window dragged through many widths moves its rows without a jump.
 * Trips run on the chat's animation duration and only while chat
 * animations are on.</p>
 */
final class ChatRowGlide {
    private static final int KIND_WORDS = 0;
    private static final int KIND_SPEAKER = 1;
    private static final int KIND_QUOTE = 2;
    private static final int KIND_CHIPS = 3;
    private static final int KIND_DAY = 4;
    private static final int KINDS = 5;
    /** Nearer its place than this, in pixels of stack, a row has arrived. */
    private static final float ARRIVED = 0.01F;

    /** Each line's row in the current layout; null for a blank row. */
    private Key[] keys = new Key[0];
    /** Pixels of stack below each line's row in the current layout. */
    private int[] places = new int[0];
    /** The messages the current layout shows, oldest first. */
    private long[] messages = new long[0];
    private Map<Key, Trip> trips = new HashMap<Key, Trip>();
    /** This frame's lift and share shown of each line's row. */
    private float[] lifts = new float[0];
    private float[] shows = new float[0];
    private float deepestDrop;
    /** Whether this frame's lifts were worked out for the current layout. */
    private boolean moving;

    /**
     * Takes in a new layout of the view: {@code lines} as {@code rows}
     * now places them, with the unread divider's row over the line at
     * {@code dividerIndex}. When the layout shows the same messages as
     * the last, every row whose place moved sets off from where it is
     * drawn now, and every row it adds fades in; otherwise the trips
     * under way go on and no other starts.
     */
    void relaid(List<ChatLine> lines, ChatStackRows rows, int dividerIndex,
                long now) {
        int count = lines == null ? 0 : lines.size();
        Key[] nextKeys = new Key[count];
        long[] nextMessages = keysOf(lines, nextKeys);
        int[] nextPlaces = new int[count];
        for (int index = 0; index < count; index++) {
            nextPlaces[index] = rows.top(LostTalesChatOverlayRenderer
                    .rowOfLine(index, dividerIndex));
        }
        boolean glides = Motions.travelNanos(MotionIds.CHAT_ROW_MOVE) > 0L
                && this.messages.length > 0
                && Arrays.equals(this.messages, nextMessages);
        Map<Key, Integer> before = new HashMap<Key, Integer>();
        if (glides) {
            for (int index = 0; index < this.keys.length; index++) {
                if (this.keys[index] != null) {
                    before.put(this.keys[index], Integer.valueOf(index));
                }
            }
        }
        Map<Key, Trip> next = new HashMap<Key, Trip>();
        for (int index = 0; index < count; index++) {
            Key key = nextKeys[index];
            if (key == null) {
                continue;
            }
            Trip trip = this.trips.get(key);
            Integer old = glides ? before.get(key) : null;
            if (glides && old == null) {
                next.put(key, new Trip(0.0F, now, now));
                continue;
            }
            if (old == null || nextPlaces[index] == this.places[old.intValue()]) {
                // Nothing moved it: a trip under way keeps its course.
                if (trip != null) {
                    next.put(key, trip);
                }
                continue;
            }
            float from = this.places[old.intValue()]
                    + (trip == null ? 0.0F : trip.liftAt(now))
                    - nextPlaces[index];
            long fading = trip == null ? Trip.NOT_FADING : trip.fadeStarted;
            if (Math.abs(from) >= ARRIVED || fading != Trip.NOT_FADING) {
                next.put(key, new Trip(from, now, fading));
            }
        }
        this.trips = next;
        this.keys = nextKeys;
        this.places = nextPlaces;
        this.messages = nextMessages;
        this.moving = false;
    }

    /**
     * Works out where every row is drawn at {@code now}, each lift laid
     * on the display's grid at {@code pixelsPerUnit} display pixels to a
     * pixel of stack, and lets go of the trips that are over. Once a
     * frame, after the layout is taken in and before anything is drawn.
     */
    void advance(long now, float pixelsPerUnit) {
        this.deepestDrop = 0.0F;
        if (this.trips.isEmpty()) {
            this.moving = false;
            return;
        }
        int count = this.keys.length;
        if (this.lifts.length < count) {
            this.lifts = new float[count];
            this.shows = new float[count];
        }
        for (int index = 0; index < count; index++) {
            Key key = this.keys[index];
            Trip trip = key == null ? null : this.trips.get(key);
            float lift = trip == null ? 0.0F
                    : onGrid(trip.liftAt(now), pixelsPerUnit);
            this.lifts[index] = lift;
            this.shows[index] = trip == null ? 1.0F : trip.shownAt(now);
            this.deepestDrop = Math.max(this.deepestDrop, -lift);
        }
        Iterator<Trip> iterator = this.trips.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isOver(now)) {
                iterator.remove();
            }
        }
        this.moving = true;
    }

    /**
     * How far above its place the line's row is drawn this frame, in
     * pixels of stack; below its place is negative.
     */
    float lift(int lineIndex) {
        return this.moving && lineIndex >= 0 && lineIndex < this.keys.length
                ? this.lifts[lineIndex] : 0.0F;
    }

    /** How much of the line's row shows this frame: less while it fades in. */
    float shown(int lineIndex) {
        return this.moving && lineIndex >= 0 && lineIndex < this.keys.length
                ? this.shows[lineIndex] : 1.0F;
    }

    /** The most any row is drawn below its place this frame. */
    float deepestDrop() {
        return this.moving ? this.deepestDrop : 0.0F;
    }

    /** Sets every row down in its place at once. */
    void halt() {
        this.trips.clear();
        this.moving = false;
    }

    /** Forgets the layout along with the view's history. */
    void clear() {
        this.keys = new Key[0];
        this.places = new int[0];
        this.messages = new long[0];
        halt();
    }

    /**
     * Fills {@code keys} with each line's row, walking down from the top
     * of the stack so that every message's rows are counted from its top,
     * and answers the messages met, oldest first: each by its id and by
     * how many messages of that id stand above it, so lines another mod
     * printed, which share an id, are still told apart.
     */
    static long[] keysOf(List<ChatLine> lines, Key[] keys) {
        int count = lines == null ? 0 : lines.size();
        long[] messages = new long[count];
        int found = 0;
        Map<Integer, Integer> seen = new HashMap<Integer, Integer>();
        int[] ordinals = new int[KINDS];
        boolean inMessage = false;
        int messageId = 0;
        int occurrence = 0;
        for (int index = count - 1; index >= 0; index--) {
            ChatLine line = lines.get(index);
            if (line == null || ChatWindowLines.isSpacer(line)) {
                inMessage = false;
                continue;
            }
            String day = ChatWindowLines.dateDividerLabel(line);
            if (day != null) {
                keys[index] = new Key(0, 0, KIND_DAY, 0, day);
                inMessage = false;
                continue;
            }
            int id = line.getChatLineID();
            if (!inMessage || id != messageId) {
                inMessage = true;
                messageId = id;
                Integer above = seen.get(Integer.valueOf(id));
                occurrence = above == null ? 0 : above.intValue();
                seen.put(Integer.valueOf(id), Integer.valueOf(occurrence + 1));
                Arrays.fill(ordinals, 0);
                messages[found++] = ((long)id << 32)
                        | (occurrence & 0xFFFFFFFFL);
            }
            int kind = kindOf(line.func_151461_a());
            keys[index] = new Key(id, occurrence, kind, ordinals[kind]++,
                    null);
        }
        return Arrays.copyOf(messages, found);
    }

    /** Which of a message's kinds of row the row is, as the draw sizes them. */
    private static int kindOf(IChatComponent row) {
        if (ChatLayoutMarker.isHeaderRow(row)) {
            return KIND_SPEAKER;
        }
        if (ChatReactionMarker.isReactionRow(row)) {
            return KIND_CHIPS;
        }
        return ChatReplyMarker.isQuoteRow(row) ? KIND_QUOTE : KIND_WORDS;
    }

    private static float onGrid(float distance, float pixelsPerUnit) {
        return pixelsPerUnit > 0.0F
                ? Math.round(distance * pixelsPerUnit) / pixelsPerUnit
                : distance;
    }

    /** How far through a beat of {@code duration} {@code now} is; all of it for none. */
    private static float progress(long started, long now, long duration) {
        if (duration <= 0L) {
            return 1.0F;
        }
        return Math.max(0.0F, Math.min(1.0F,
                (now - started) / (float)duration));
    }

    /** A row as one layout knows it, the same in the next. */
    static final class Key {
        private final int message;
        private final int occurrence;
        private final int kind;
        private final int ordinal;
        /** The day a day's rule is written with; null for any other row. */
        private final String day;

        Key(int message, int occurrence, int kind, int ordinal, String day) {
            this.message = message;
            this.occurrence = occurrence;
            this.kind = kind;
            this.ordinal = ordinal;
            this.day = day;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) {
                return false;
            }
            Key key = (Key)other;
            return this.message == key.message
                    && this.occurrence == key.occurrence
                    && this.kind == key.kind && this.ordinal == key.ordinal
                    && (this.day == null ? key.day == null
                            : this.day.equals(key.day));
        }

        @Override
        public int hashCode() {
            int hash = this.message;
            hash = hash * 31 + this.occurrence;
            hash = hash * 31 + this.kind;
            hash = hash * 31 + this.ordinal;
            return hash * 31 + (this.day == null ? 0 : this.day.hashCode());
        }
    }

    /** One row's way to its place. */
    private static final class Trip {
        static final long NOT_FADING = Long.MIN_VALUE;
        /** How far above its place the row set off; below is negative. */
        final float from;
        final long started;
        /** When the row began to fade in; {@link #NOT_FADING} for one that was there. */
        final long fadeStarted;

        Trip(float from, long started, long fadeStarted) {
            this.from = from;
            this.started = started;
            this.fadeStarted = fadeStarted;
        }

        /** The lift at {@code now}: all of it at the start, none at the end, as its motion travels. */
        float liftAt(long now) {
            return this.from * (1.0F - Motions.curve(MotionIds.CHAT_ROW_MOVE)
                    .apply(progress(this.started, now,
                            Motions.travelNanos(MotionIds.CHAT_ROW_MOVE))));
        }

        float shownAt(long now) {
            return this.fadeStarted == NOT_FADING ? 1.0F
                    : Motions.curve(MotionIds.CHAT_ROW_APPEAR).apply(
                            progress(this.fadeStarted, now,
                                    Motions.nanos(MotionIds.CHAT_ROW_APPEAR)));
        }

        boolean isOver(long now) {
            return progress(this.started, now, Motions.travelNanos(
                    MotionIds.CHAT_ROW_MOVE)) >= 1.0F
                    && (this.fadeStarted == NOT_FADING
                            || progress(this.fadeStarted, now, Motions.nanos(
                                    MotionIds.CHAT_ROW_APPEAR)) >= 1.0F);
        }
    }
}
