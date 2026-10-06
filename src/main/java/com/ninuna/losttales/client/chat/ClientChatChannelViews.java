package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.motion.MotionIds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.ChatLine;

/**
 * Per-tab views over vanilla's single chat history. Lines stay in
 * {@code GuiNewChat}; this class only remembers which tab each Lost Tales
 * chat-line id belongs to and derives a tab's visible subset, scroll
 * offset, and unread counts from that. Lines without a recorded tab
 * (printed on the client without passing through the chat event) belong
 * to the console view. Messages are never copied.
 *
 * <p>A view is described by a {@link ChatLineFilter}: one tab while the
 * chat screen is open, a window's tabs minus the muted ones for the
 * closed-chat feed. The last few filters' results are cached so every
 * window pays one comparison per frame. Scroll offsets are per tab and
 * survive closing the screen.</p>
 *
 * <p>A scroll offset is measured in message lines and fractions of one.
 * The wheel moves the offset by whole lines; the drawn offset eases
 * toward it, which is what makes the history glide instead of jumping.
 * The far ends are exact rather than whole: at rest the newest message
 * sits on the baseline, and scrolled all the way up the oldest one sits
 * on the window's top edge, however tall the window was dragged.</p>
 */
public final class ClientChatChannelViews {
    /**
     * Tracked line ids never fall below the history's capacity, so every
     * shown line has its tab; a little more covers ids allocated while
     * the history trims.
     */
    private static final int TRACKED_LINES_MARGIN = 64;
    /** Unread counts stop climbing here; the jump button and the {@code +}'s tip say "99+". */
    public static final int MAX_UNREAD = 99;
    /**
     * The channel whose tab shows lines Lost Tales did not route: they
     * are what only this player sees anyway, so they live in the console
     * tab and keep the conversation tabs clean.
     */
    public static final ChatChannel SYSTEM_LINE_VIEW = ChatChannel.CLIENT_CONSOLE;
    private static final LinkedHashMap<Integer, ConversationPage> TAB_BY_LINE_ID =
            new LinkedHashMap<Integer, ConversationPage>();
    /**
     * When each Lost Tales line was said, by chat line id: what a
     * window reads to stand a dated rule over each day's first message.
     * Bounded like the tab index and cleared with it.
     */
    private static final LinkedHashMap<Integer, Long> TIME_BY_LINE_ID =
            new LinkedHashMap<Integer, Long>();
    /** Where each view is scrolled to, in message lines. */
    private static final Map<ConversationPage, Double> SCROLL =
            new HashMap<ConversationPage, Double>();
    /** Where each view is drawn right now, easing toward its target. */
    private static final Map<ConversationPage, Ease> RENDERED =
            new HashMap<ConversationPage, Ease>();
    /**
     * Messages that have arrived in a tab while its own view was
     * scrolled back, capped like the unread counts. Cleared the moment
     * the view returns to the newest message, which is what the
     * jump-to-present button does.
     */
    private static final Map<ConversationPage, Integer> WAITING_BELOW =
            new HashMap<ConversationPage, Integer>();
    /** The message each scrolled-back view is holding on to. */
    private static final Map<ConversationPage, Anchor> ANCHORS =
            new HashMap<ConversationPage, Anchor>();
    /**
     * The row of a message a view keeps where it stands on its next
     * layout, once: see {@link #keepInPlace}.
     */
    private static final Map<ConversationPage, KeptRow> KEPT_ROWS =
            new HashMap<ConversationPage, KeptRow>();
    /**
     * How many times the player has moved each view themselves. A hold
     * is taken against the reading it was made from, so a wheel turn,
     * a scrollbar drag or a jump replaces it rather than being undone
     * by it.
     */
    private static final Map<ConversationPage, Integer> SCROLL_REVISION =
            new HashMap<ConversationPage, Integer>();
    /** Closer than this to the target and the offset simply arrives. */
    private static final double SCROLL_SNAP_LINES = 0.01D;
    /**
     * Unread messages per tab, split once at arrival: a message that
     * @-mentions the local player counts as a ping and nowhere else, so the
     * two counters never describe the same line twice.
     */
    private static final Map<ConversationPage, Integer> UNREAD_PINGS =
            new HashMap<ConversationPage, Integer>();
    private static final Map<ConversationPage, Integer> UNREAD_OTHER =
            new HashMap<ConversationPage, Integer>();
    /**
     * Where each tab's latest unread run begins: the divider the view
     * draws above that line — "new since you last looked". Set when the
     * first unread line of a run arrives, kept while the tab is being
     * read (so the divider does not vanish the moment the tab opens),
     * and replaced by the next run once this one has been seen.
     */
    private static final Map<ConversationPage, UnreadDivider> UNREAD_DIVIDERS =
            new HashMap<ConversationPage, UnreadDivider>();
    /**
     * The newest message the server named in each view: what the view's
     * read mark is moved to when the view is read, so the next join's
     * replay knows where this player left off.
     */
    private static final Map<ConversationPage, Long> NEWEST_MESSAGE_BY_VIEW =
            new HashMap<ConversationPage, Long>();
    /**
     * Where this player arrived on joining this time, as the login replay
     * states it: the id of their own join line, which is never news to
     * them; {@link ChatMessageIds#NONE} until the replay says.
     */
    private static long sessionArrival = ChatMessageIds.NONE;

    /** Filters whose visible subset is kept; one per window is plenty. */
    private static final int MAX_CACHED_FILTERS = 8;
    private static final LinkedHashMap<ChatLineFilter, CachedView> CACHE =
            new LinkedHashMap<ChatLineFilter, CachedView>(16, 0.75F, true);

    private ClientChatChannelViews() {}

    /**
     * The key a view's state is kept under: where it is scrolled to, what
     * it is holding on to, what arrived while it was scrolled back, what
     * waits unread in it, its divider and its newest line. Each copy of a
     * conversation open is its own person and keeps its own; a
     * conversation with no copy open keeps it under its first copy.
     *
     * <p>Everything here belongs to a conversation, not to a row. A scoped
     * channel has a row entry and a conversation tab for every
     * conversation in it, and the two are never the same value: a line
     * arrives under its own conversation while the window and the
     * renderer both ask about the row. Every access goes through here so
     * that one side cannot write what the other never reads.</p>
     */
    private static ConversationPage viewKey(ConversationPage tab) {
        return ConversationPage.viewed(tab);
    }

    /**
     * The views a line of {@code conversation} arrives in: each open copy
     * showing it, or with none the conversation's first copy, where it
     * waits until a copy opens.
     */
    private static List<ConversationPage> readersOf(ConversationPage conversation) {
        List<ConversationPage> views = new ArrayList<ConversationPage>();
        for (ConversationPage copy : ChatLayout.readersOf(conversation)) {
            views.add(viewKey(copy));
        }
        if (views.isEmpty()) {
            views.add(conversation.conversation());
        }
        return views;
    }

    /**
     * Remembers a new Lost Tales line's tab and counts it unread in every
     * copy that shows it but the one being read at its newest line; a
     * line the server named was said at
     * {@code timestampMillis}. {@code serverId} is where the view's read
     * mark moves once the line is seen: a message's own id, or for a
     * console line the id of the entry it shows, which comes from the
     * same clock. A {@code replayed} line is one the server is catching
     * this player up on: one said before this player first arrived on
     * this server ({@link ClientChatReadMarks#arrival}), and their own
     * arrival, were never news to them and are filed as read; one they
     * were shown before — no newer than the view's read mark — is filed
     * and nothing else; and the first one they were not stands under the
     * unread divider, in the tab in front as in any other, the way a
     * messenger marks where its reader left off.
     */
    public static synchronized void record(int chatLineId, ConversationPage tab,
                                           ConversationPage selected,
                                           boolean mentionsLocalPlayer,
                                           long serverId,
                                           long timestampMillis,
                                           boolean replayed) {
        if (tab == null) {
            return;
        }
        TAB_BY_LINE_ID.put(Integer.valueOf(chatLineId), tab.conversation());
        while (TAB_BY_LINE_ID.size() > maxTrackedLines()) {
            Iterator<Integer> iterator = TAB_BY_LINE_ID.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        invalidateCache();
        for (ConversationPage view : readersOf(tab)) {
            recordIn(view, chatLineId, selected, mentionsLocalPlayer,
                    serverId, timestampMillis, replayed);
        }
    }

    /** A new line as one view takes it: see {@link #record}. */
    private static void recordIn(ConversationPage view, int chatLineId,
                                 ConversationPage selected,
                                 boolean mentionsLocalPlayer, long serverId,
                                 long timestampMillis, boolean replayed) {
        boolean named = ChatMessageIds.isServerId(serverId);
        if (named) {
            Long newest = NEWEST_MESSAGE_BY_VIEW.get(view);
            if (newest == null || serverId > newest.longValue()) {
                NEWEST_MESSAGE_BY_VIEW.put(view, Long.valueOf(serverId));
            }
        }
        String server = ClientChatSession.currentKey();
        if (replayed && named) {
            long arrived = ClientChatReadMarks.arrival(server);
            if (!ChatMessageIds.isServerId(arrived) || serverId < arrived
                    || serverId == sessionArrival) {
                // Said before this player first came to this server, or
                // their own arrival: never news to them, so it is filed
                // and read, whether or not they ever opened the view.
                ClientChatReadMarks.markRead(server, view, serverId);
                return;
            }
            if (serverId <= ClientChatReadMarks.lastRead(server, view)) {
                // Shown before this player left: filed, and nothing to count.
                return;
            }
        }
        // Both sides through the same normalisation: the line carries its
        // own conversation while the selection is the channel's row entry,
        // and for a scoped channel those are never the same value.
        if (view.equals(viewKey(selected))) {
            if (replayed) {
                // Said while this player was away, in the tab in front:
                // the divider stands over the first of them and the run
                // is read from there, marked as seen once the tab is
                // looked at like any other.
                UnreadDivider divider = UNREAD_DIVIDERS.get(view);
                if (divider == null || divider.seen) {
                    UNREAD_DIVIDERS.put(view,
                            new UnreadDivider(chatLineId, timestampMillis));
                }
                return;
            }
            // The tab is open in front of the player — but if they have
            // scrolled back to read, a message arriving is one they have
            // not seen. It is counted on the jump-to-present button, and
            // the first of a run opens the same crimson divider an unread
            // run in another tab opens, so where they were reading is
            // marked as plainly there as anywhere else.
            if (target(view) > 0.0D) {
                int waiting = count(WAITING_BELOW, view);
                WAITING_BELOW.put(view, Integer.valueOf(
                        Math.min(MAX_UNREAD + 1, waiting + 1)));
                if (waiting == 0) {
                    UNREAD_DIVIDERS.put(view,
                            new UnreadDivider(chatLineId, timestampMillis));
                }
            } else if (named) {
                // In front and at the newest line: shown as it arrived,
                // in the feed or the open window, so it is read.
                ClientChatReadMarks.markRead(server, view, serverId);
            }
            return;
        }
        {
            Map<ConversationPage, Integer> counter = mentionsLocalPlayer
                    ? UNREAD_PINGS : UNREAD_OTHER;
            counter.put(view, Integer.valueOf(
                    Math.min(MAX_UNREAD + 1, count(counter, view) + 1)));
            UnreadDivider divider = UNREAD_DIVIDERS.get(view);
            if (divider == null || divider.seen) {
                UNREAD_DIVIDERS.put(view,
                        new UnreadDivider(chatLineId, timestampMillis));
            }
        }
    }

    /**
     * Where this player arrived on joining, as the login replay states
     * it. The first arrival on a server is remembered for good: what was
     * said before it was never news to them, and anything said since
     * that they were not shown is unread.
     */
    public static synchronized void noteArrival(long arrivalId) {
        if (!ChatMessageIds.isServerId(arrivalId)) {
            return;
        }
        sessionArrival = arrivalId;
        ClientChatReadMarks.markArrival(ClientChatSession.currentKey(),
                arrivalId);
    }

    /**
     * Files a line laid in above the view's oldest — a page of older
     * history — and nothing more: it was said before anything on screen,
     * so it is neither unread nor the newest, and moves no mark.
     */
    public static synchronized void recordBackfilled(int chatLineId, ConversationPage tab) {
        if (tab == null) {
            return;
        }
        TAB_BY_LINE_ID.put(Integer.valueOf(chatLineId), tab.conversation());
        while (TAB_BY_LINE_ID.size() > maxTrackedLines()) {
            Iterator<Integer> iterator = TAB_BY_LINE_ID.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        invalidateCache();
    }

    /** Remembers when a printed line was said. */
    public static synchronized void recordTime(int chatLineId,
                                               long timestampMillis) {
        TIME_BY_LINE_ID.put(Integer.valueOf(chatLineId),
                Long.valueOf(timestampMillis));
        while (TIME_BY_LINE_ID.size() > maxTrackedLines()) {
            Iterator<Integer> iterator = TIME_BY_LINE_ID.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    /** When the line was said, or null for a line nobody stamped. */
    public static synchronized Long timeOf(int chatLineId) {
        return TIME_BY_LINE_ID.get(Integer.valueOf(chatLineId));
    }

    /**
     * Holds a scrolled-back view on the message it is reading. Called
     * once for every open window before its scroll is clamped, so the
     * correction is made in the same frame the stack grows in and never
     * shows as a jump.
     *
     * <p>A view scrolled away from the newest message is a view of a
     * <em>message</em>, not of a row number: what should stay still
     * under the eye is the conversation, whatever happens beneath it.
     * So the view remembers the line at its bottom edge and how far
     * above that line it sits, and every frame it is put back on that
     * line. New messages arriving underneath, an unread divider opening
     * a row of its own, the window being made narrower and the whole
     * history re-wrapping — none of them move the page being read. A
     * view resting on the newest message holds nothing: it belongs to
     * the foot of the stack, and there the arriving messages should push
     * it, which is what jumping to the present goes back to.</p>
     */
    static synchronized void holdPosition(ConversationPage tab,
                                          ChatFrame frame) {
        ConversationPage view = viewKey(tab);
        if (view == null || frame == null) {
            return;
        }
        double current = target(view);
        List<ChatLine> lines = frame.lines;
        if (placeKeptRow(view, frame, current)) {
            // The page is held again from where it rests now.
            ANCHORS.remove(view);
            return;
        }
        if (current <= 0.0D || lines == null || lines.isEmpty()) {
            ANCHORS.remove(view);
            // Back at the newest message: nothing is waiting below any
            // more, whether the player scrolled down or jumped.
            WAITING_BELOW.remove(view);
            return;
        }
        int divider = frame.dividerLineIndex;
        frame.resolveRows();
        ChatStackRows rows = frame.rows;
        Anchor anchor = ANCHORS.get(view);
        if (anchor != null && anchor.revision == revision(view)) {
            int index = anchor.locate(lines);
            if (index >= 0) {
                // Put back by pixels, so a row between the held line and
                // the edge changing height — the divider's, above all —
                // cannot stretch the part of a row the edge was in.
                double offset = rows.rowsAt(rows.top(
                        LostTalesChatOverlayRenderer.rowOfLine(index, divider))
                        + anchor.deltaPixels);
                double ceiling = frame.scrollCeiling();
                if (offset > ceiling) {
                    // The window has grown under a view scrolled to its
                    // top: the held line would now stand above the
                    // ceiling the clamp holds the view to. Held and
                    // clamped by turns, the page would jitter between
                    // the two every frame, so the hold is let go here
                    // and taken again at the ceiling on the next frame.
                    place(view, ceiling, current);
                    ANCHORS.remove(view);
                } else {
                    place(view, offset, current);
                }
                return;
            }
        }
        // Nothing held yet, or the message that was held has been
        // trimmed away: take hold of whatever is at the view's edge now.
        // An edge inside the unread divider's row holds the message above
        // the divider, so the divider going or moving leaves the page
        // above it where it is.
        int edgeRow = (int)Math.floor(current);
        int index = ChatStackRows.isDividerRow(edgeRow, divider)
                ? olderMessageOver(lines, divider) : -1;
        if (index < 0) {
            index = Math.max(0, Math.min(lines.size() - 1,
                    LostTalesChatOverlayRenderer.lineOfRow(edgeRow, divider)));
            // A blank row between runs stands for no message and cannot
            // be held: the nearest message's row is.
            index = ChatWindowLines.nearestMessageRow(lines, index);
        }
        ChatLine line = index < 0 ? null : lines.get(index);
        if (line == null) {
            ANCHORS.remove(view);
            return;
        }
        ANCHORS.put(view, new Anchor(line.getChatLineID(), index,
                rows.offsetOf(current) - rows.top(
                        LostTalesChatOverlayRenderer.rowOfLine(index, divider)),
                revision(view)));
    }

    /**
     * Keeps a row of a message where it stands while the message is laid
     * out again, whether the view is scrolled back or not: its top row
     * ({@code top}), so a long message just opened goes on under the row
     * being read, or its foot, so one just folded keeps the run clicked
     * under the pointer. Taken from the layout drawn now, and put back
     * once on the next.
     */
    static synchronized void keepInPlace(ConversationPage tab, ChatFrame frame,
                                         int chatLineId, boolean top) {
        ConversationPage view = viewKey(tab);
        int index = view == null || frame == null || frame.lines == null
                ? -1 : rowOf(frame.lines, chatLineId, top);
        if (index < 0) {
            return;
        }
        frame.resolveRows();
        ChatStackRows rows = frame.rows;
        double scrollPixels = rows.offsetOf(target(view));
        KEPT_ROWS.put(view, new KeptRow(chatLineId, top,
                scrollPixels - rows.top(LostTalesChatOverlayRenderer
                        .rowOfLine(index, frame.dividerLineIndex)),
                scrollPixels, revision(view)));
    }

    /**
     * Puts the view back on the row {@link #keepInPlace} kept, if one
     * waits and the player has not scrolled since, and has the rows glide
     * on screen from where they were ({@link ChatRowGlide#shifted}).
     * Answers whether it did.
     */
    private static boolean placeKeptRow(ConversationPage view, ChatFrame frame,
                                        double current) {
        KeptRow kept = KEPT_ROWS.remove(view);
        List<ChatLine> lines = frame.lines;
        if (kept == null || kept.revision != revision(view) || lines == null) {
            return false;
        }
        int index = rowOf(lines, kept.chatLineId, kept.top);
        if (index < 0) {
            return false;
        }
        frame.resolveRows();
        ChatStackRows rows = frame.rows;
        double offset = Math.max(0.0D, Math.min(frame.scrollCeiling(),
                rows.rowsAt(rows.top(LostTalesChatOverlayRenderer.rowOfLine(
                        index, frame.dividerLineIndex)) + kept.deltaPixels)));
        place(view, offset, current);
        frame.glide.shifted((float)(rows.offsetOf(offset)
                - kept.scrollPixels), System.nanoTime());
        return true;
    }

    /**
     * The index of a message's top row in a view's lines, or of its
     * foot, which comes first there; -1 when the view does not show it.
     */
    static int rowOf(List<ChatLine> lines, int chatLineId, boolean top) {
        int found = -1;
        for (int index = 0; index < lines.size(); index++) {
            ChatLine line = lines.get(index);
            if (line != null && line.getChatLineID() == chatLineId) {
                if (!top) {
                    return index;
                }
                found = index;
            } else if (found >= 0) {
                break;
            }
        }
        return found;
    }

    /**
     * The first message older than the unread divider at
     * {@code divider}, past any filler between them, or -1 for none.
     */
    private static int olderMessageOver(List<ChatLine> lines, int divider) {
        for (int index = divider + 1; index < lines.size(); index++) {
            ChatLine line = lines.get(index);
            if (line != null && !ChatWindowLines.isFiller(line)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Moves a view's offset to where its held message now stands. The
     * drawn offset moves with it rather than easing after it: nothing
     * has happened that the eye should see travel — the page is exactly
     * where it was, and only the rows beneath it have changed.
     */
    private static void place(ConversationPage view, double offset, double current) {
        double bounded = Math.max(0.0D, offset);
        if (bounded == current) {
            return;
        }
        SCROLL.put(view, Double.valueOf(bounded));
        Ease ease = RENDERED.get(view);
        if (ease != null) {
            ease.value = Math.max(0.0D, ease.value + bounded - current);
        }
    }

    /** A row of a message a view keeps where it stands. */
    private static final class KeptRow {
        final int chatLineId;
        /** The message's top row, or else its foot. */
        final boolean top;
        /** Pixels of stack from the bottom of that row up to the view's offset. */
        final double deltaPixels;
        /** Where the view's offset stood, in pixels of stack, when the row was kept. */
        final double scrollPixels;
        /** The scroll this was taken against; a later one drops it. */
        final int revision;

        KeptRow(int chatLineId, boolean top, double deltaPixels,
                double scrollPixels, int revision) {
            this.chatLineId = chatLineId;
            this.top = top;
            this.deltaPixels = deltaPixels;
            this.scrollPixels = scrollPixels;
            this.revision = revision;
        }
    }

    /** What a view is holding on to while it is scrolled back. */
    private static final class Anchor {
        final int chatLineId;
        /**
         * Pixels of stack from the bottom of that line's own row up to the
         * view's offset; negative when the offset is below the line.
         */
        final double deltaPixels;
        /** The scroll this hold was taken against; a later one drops it. */
        final int revision;
        /** Where the line was last found, tried first next time. */
        private int lastIndex;

        Anchor(int chatLineId, int index, double deltaPixels, int revision) {
            this.chatLineId = chatLineId;
            this.lastIndex = index;
            this.deltaPixels = deltaPixels;
            this.revision = revision;
        }

        /**
         * Where the held line is in the list now, or -1 once the history
         * has trimmed past it. The place it was last found is tried
         * first, which is the answer on every frame nothing arrived.
         */
        int locate(List<ChatLine> lines) {
            if (this.lastIndex >= 0 && this.lastIndex < lines.size()
                    && lines.get(this.lastIndex) != null
                    && lines.get(this.lastIndex).getChatLineID()
                            == this.chatLineId) {
                return this.lastIndex;
            }
            for (int index = 0; index < lines.size(); index++) {
                if (lines.get(index) != null
                        && lines.get(index).getChatLineID()
                                == this.chatLineId) {
                    this.lastIndex = index;
                    return index;
                }
            }
            return -1;
        }
    }

    /**
     * Called while a view is on screen; clears its unread counts and
     * moves the view's read mark to its newest line, so the next join
     * knows these were seen.
     */
    public static synchronized void markViewed(ConversationPage tab) {
        tab = viewKey(tab);
        if (tab != null) {
            UNREAD_PINGS.remove(tab);
            UNREAD_OTHER.remove(tab);
            UnreadDivider divider = UNREAD_DIVIDERS.get(tab);
            if (divider != null) {
                // The divider stays while the tab is read; the next
                // unread run replaces it.
                divider.seen = true;
            }
            Long newest = NEWEST_MESSAGE_BY_VIEW.get(tab);
            if (newest != null) {
                ClientChatReadMarks.markRead(ClientChatSession.currentKey(),
                        tab, newest.longValue());
            }
        }
    }

    /**
     * Files a line this player said themselves once the server has named
     * it. The line was shown as an echo the moment it was typed and the
     * server's copy took its place, so it never arrived as a line to
     * count: it is theirs, read in every copy it went to, and each copy's
     * read mark moves up to it so the next join's replay does not count it
     * as new.
     */
    public static synchronized void noteOwnLine(ConversationPage tab, int chatLineId,
                                                long serverId) {
        if (tab == null || !ChatMessageIds.isServerId(serverId)) {
            return;
        }
        TAB_BY_LINE_ID.put(Integer.valueOf(chatLineId), tab.conversation());
        for (ConversationPage view : readersOf(tab)) {
            Long newest = NEWEST_MESSAGE_BY_VIEW.get(view);
            if (newest == null || serverId > newest.longValue()) {
                NEWEST_MESSAGE_BY_VIEW.put(view, Long.valueOf(serverId));
            }
            ClientChatReadMarks.markRead(ClientChatSession.currentKey(), view,
                    serverId);
        }
        invalidateCache();
    }

    /**
     * A copy opening anew has read nothing and waits on nothing: what an
     * earlier copy of its number kept, whoever it spoke as, goes.
     */
    static synchronized void forgetCopy(ConversationPage copy) {
        ConversationPage row = ConversationPage.row(copy);
        if (row == null) {
            return;
        }
        for (Map<ConversationPage, ?> kept : Arrays.<Map<ConversationPage, ?>>asList(
                UNREAD_PINGS, UNREAD_OTHER, UNREAD_DIVIDERS,
                NEWEST_MESSAGE_BY_VIEW, SCROLL, RENDERED, WAITING_BELOW,
                ANCHORS, KEPT_ROWS, SCROLL_REVISION)) {
            Iterator<ConversationPage> views = kept.keySet().iterator();
            while (views.hasNext()) {
                if (row.equals(ConversationPage.row(views.next()))) {
                    views.remove();
                }
            }
        }
    }

    /**
     * The line the tab's unread divider stands above, or null: the first
     * line of the latest unread run.
     */
    public static synchronized Integer unreadDividerLine(ConversationPage tab) {
        UnreadDivider divider = tab == null ? null
                : UNREAD_DIVIDERS.get(viewKey(tab));
        return divider == null ? null : Integer.valueOf(divider.lineId);
    }

    /**
     * When the tab's unread run began: the time its first line was
     * said, or zero without a divider. What the divider's label dates
     * itself by when that was not today.
     */
    public static synchronized long unreadDividerTimestamp(ConversationPage tab) {
        UnreadDivider divider = tab == null ? null
                : UNREAD_DIVIDERS.get(viewKey(tab));
        return divider == null ? 0L : divider.timestampMillis;
    }

    /**
     * Removes the tab's divider once its run has been seen: called when
     * the player moves on — the tab is deselected, or the screen closes
     * — so the divider shows while the new messages are being read and
     * is gone the next time the tab opens, the way Discord's is.
     */
    public static synchronized void dismissSeenDivider(ConversationPage tab) {
        ConversationPage view = viewKey(tab);
        UnreadDivider divider = view == null ? null
                : UNREAD_DIVIDERS.get(view);
        if (divider != null && divider.seen) {
            UNREAD_DIVIDERS.remove(view);
        }
    }

    /**
     * Removes the tab's divider outright, seen or not: sending a
     * message there says the conversation has moved on, the way it does
     * on Discord.
     */
    public static synchronized void dismissDivider(ConversationPage tab) {
        if (tab != null) {
            UNREAD_DIVIDERS.remove(viewKey(tab));
        }
    }

    /** As above for every tab at once; what closing the screen calls. */
    public static synchronized void dismissSeenDividers() {
        Iterator<Map.Entry<ConversationPage, UnreadDivider>> iterator =
                UNREAD_DIVIDERS.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().seen) {
                iterator.remove();
            }
        }
    }

    /** Sends the view back to the newest line; it glides there. */
    public static synchronized void scrollHome(ConversationPage tab) {
        ConversationPage view = viewKey(tab);
        if (view != null) {
            SCROLL.remove(view);
            ANCHORS.remove(view);
            WAITING_BELOW.remove(view);
            noteScrolled(view);
        }
    }

    /**
     * Messages that arrived in this view while it was scrolled back:
     * what the jump-to-present button counts. Zero once the view is home.
     */
    public static synchronized int waitingBelow(ConversationPage view) {
        return count(WAITING_BELOW, viewKey(view));
    }

    /** One tab's divider: where the latest unread run starts. */
    private static final class UnreadDivider {
        final int lineId;
        /**
         * When the run's first line was said: the line's own time, so
         * a run replayed from yesterday is dated yesterday and not the
         * day it was shown.
         */
        final long timestampMillis;
        boolean seen;

        UnreadDivider(int lineId, long timestampMillis) {
            this.lineId = lineId;
            this.timestampMillis = timestampMillis > 0L ? timestampMillis
                    : System.currentTimeMillis();
        }
    }

    /** Unread messages that mentioned the player, capped at MAX_UNREAD + 1. */
    public static synchronized int unreadPingCount(ConversationPage tab) {
        tab = viewKey(tab);
        return count(UNREAD_PINGS, tab);
    }

    /** Unread messages other than pings, capped at MAX_UNREAD + 1. */
    public static synchronized int unreadOtherCount(ConversationPage tab) {
        tab = viewKey(tab);
        return count(UNREAD_OTHER, tab);
    }

    /** Every unread message, pings included, capped at MAX_UNREAD + 1. */
    public static synchronized int unreadCount(ConversationPage tab) {
        return Math.min(MAX_UNREAD + 1,
                unreadPingCount(tab) + unreadOtherCount(tab));
    }

    public static synchronized int unreadCount(ChatChannel channel) {
        return unreadCount(ConversationPage.of(channel));
    }

    public static synchronized boolean hasUnread(ConversationPage tab) {
        return unreadPingCount(tab) + unreadOtherCount(tab) > 0;
    }

    /** The recorded tab, or null for vanilla and untracked lines. */
    public static synchronized ConversationPage tabOf(int chatLineId) {
        return TAB_BY_LINE_ID.get(Integer.valueOf(chatLineId));
    }

    /**
     * The lines the filter accepts, in vanilla's newest-first order.
     * Cached per filter until the drawn-line list, its size or its head
     * changes, so per-frame callers pay one comparison.
     */
    static synchronized List<ChatLine> visibleLines(
            List<ChatLine> drawnLines, ChatLineFilter filter) {
        if (drawnLines == null || drawnLines.isEmpty() || filter == null
                || filter.isEmpty()) {
            return Collections.emptyList();
        }
        ChatLine first = drawnLines.get(0);
        CachedView cached = CACHE.get(filter);
        if (cached != null && cached.describes(drawnLines, first)) {
            return cached.visible;
        }
        List<ChatLine> visible = new ArrayList<ChatLine>(drawnLines.size());
        for (int index = 0; index < drawnLines.size(); index++) {
            ChatLine line = drawnLines.get(index);
            if (line == null) {
                continue;
            }
            // Untracked lines belong to the console wherever its tab lives;
            // a line addressed to the player passes where only such lines
            // of its conversation do.
            if (filter.accepts(TAB_BY_LINE_ID.get(
                    Integer.valueOf(line.getChatLineID())),
                    LostTalesChatPresentation.isPingedLine(
                            line.getChatLineID()))) {
                visible.add(line);
            }
        }
        List<ChatLine> result = Collections.unmodifiableList(visible);
        CACHE.put(filter, new CachedView(drawnLines, first, result));
        while (CACHE.size() > MAX_CACHED_FILTERS) {
            Iterator<ChatLineFilter> iterator = CACHE.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        return result;
    }

    /**
     * The view's scroll target in message lines, clamped to what the
     * window can actually reach: nothing below the newest line, and no
     * further up than leaves the oldest line sitting exactly on the
     * window's top edge. {@code roomLines} is the room the window has,
     * fractions of a line included, so a window dragged to an odd height
     * still comes to rest on a whole message at both ends.
     */
    public static synchronized double getScroll(ConversationPage tab, int totalLines,
                                                double roomLines) {
        ConversationPage view = viewKey(tab);
        if (view == null) {
            return 0.0D;
        }
        double maximum = Math.max(0.0D,
                totalLines - Math.max(1.0D, roomLines));
        double clamped = Math.max(0.0D, Math.min(maximum, target(view)));
        if (clamped <= 0.0D) {
            SCROLL.remove(view);
        } else {
            SCROLL.put(view, Double.valueOf(clamped));
        }
        return clamped;
    }

    /**
     * While a window resize is in progress the scroll never glides: the
     * clamp moves with the room every frame, and easing after a moving
     * clamp reads as the stack wobbling against the drag. The screen
     * raises this for exactly the frames a resize is active.
     */
    private static volatile boolean scrollEasingSuppressed;

    public static void setScrollEasingSuppressed(boolean suppressed) {
        scrollEasingSuppressed = suppressed;
    }

    /**
     * The offset the view is drawn at this frame: the target once it has
     * arrived, and on the way there a value easing toward it. Called
     * once per window draw; with chat animation switched off, or while
     * a resize holds the clamp in motion, it is the target itself.
     */
    public static synchronized double renderedScroll(ConversationPage tab,
                                                     double target) {
        ConversationPage view = viewKey(tab);
        if (view == null) {
            return 0.0D;
        }
        long now = System.nanoTime();
        Ease ease = RENDERED.get(view);
        if (ease == null) {
            ease = new Ease(target, now);
            RENDERED.put(view, ease);
            return target;
        }
        if (!Motions.enabled() || scrollEasingSuppressed) {
            ease.value = target;
            ease.nanos = now;
            return target;
        }
        double elapsed = (now - ease.nanos) / 1.0E9D;
        ease.nanos = now;
        if (Math.abs(target - ease.value) <= SCROLL_SNAP_LINES) {
            ease.value = target;
        } else {
            ease.value = Motions.followTravel(MotionIds.WINDOW_SCROLL, ease.value,
                    target, elapsed);
        }
        while (RENDERED.size() > MAX_EASED_VIEWS) {
            Iterator<ConversationPage> oldest = RENDERED.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        return ease.value;
    }

    /**
     * Sends the view to a line, clamped to what the window can reach:
     * what a jump to a quoted message asks for. The offset is a target
     * like any other, so the view glides to it rather than snapping.
     */
    public static synchronized void scrollTo(ConversationPage tab, double lines,
                                             int totalLines,
                                             double roomLines) {
        ConversationPage view = viewKey(tab);
        if (view != null) {
            SCROLL.put(view, Double.valueOf(Math.max(0.0D, lines)));
            noteScrolled(view);
            getScroll(view, totalLines, roomLines);
        }
    }

    private static double target(ConversationPage view) {
        Double value = view == null ? null : SCROLL.get(view);
        return value == null ? 0.0D : value.doubleValue();
    }

    private static int revision(ConversationPage view) {
        Integer value = view == null ? null : SCROLL_REVISION.get(view);
        return value == null ? 0 : value.intValue();
    }

    /** Records that the player has moved this view themselves. */
    private static void noteScrolled(ConversationPage view) {
        if (view != null) {
            SCROLL_REVISION.put(view,
                    Integer.valueOf(revision(view) + 1));
        }
    }

    /** Views whose easing is remembered; one per window is plenty. */
    private static final int MAX_EASED_VIEWS = 16;

    /** One view's drawn scroll offset and when it was last advanced. */
    private static final class Ease {
        double value;
        long nanos;

        Ease(double value, long nanos) {
            this.value = value;
            this.nanos = nanos;
        }
    }

    public static synchronized void clear() {
        ClientChatContextHistory.clear();
        ClientChatOlderHistory.clear();
        TAB_BY_LINE_ID.clear();
        TIME_BY_LINE_ID.clear();
        SCROLL.clear();
        RENDERED.clear();
        ANCHORS.clear();
        KEPT_ROWS.clear();
        SCROLL_REVISION.clear();
        WAITING_BELOW.clear();
        UNREAD_PINGS.clear();
        UNREAD_OTHER.clear();
        UNREAD_DIVIDERS.clear();
        NEWEST_MESSAGE_BY_VIEW.clear();
        sessionArrival = ChatMessageIds.NONE;
        invalidateCache();
        ChatGroupRuns.clear();
        ClientChatMessageIds.clear();
        ClientChatDeliveryMarks.clear();
        ClientChatMessages.clear();
        ClientChatPendingEchoes.clear();
        LostTalesChatPresentation.forgetServerClock();
        ChatWindowLines.clear();
        ChatFrame.clear();
        ClientChatAccountRoles.clear();
        ClientChatIdentities.clear();
        ClientChatIdentitySelection.clear();
        ClientChatConsoleEvents.clear();
        // The history is gone with the world, and so are its conversations
        // and what was said in them.
        ChatLayout.closeConversations();
        ClientChatChannelState.forgetConversationHistory();
        ChatChannelIcons.forgetPortraits();
        ChatChannelIcons.forgetChannelIcons();
    }

    /**
     * Forgets everything said about the lines themselves — their tabs,
     * times, ids, runs, scroll and unread state — while keeping what the
     * session knows about the server: its channels, roles, identities,
     * conversations and layout. What the game clearing its own message
     * list calls for, which it does whenever the main menu opens: the
     * lines are gone from the screen, and the server's replay on the
     * next join brings them back as new lines, so nothing here may go
     * on answering for the ids they had.
     */
    public static synchronized void forgetLines() {
        ClientChatContextHistory.clear();
        ClientChatOlderHistory.clear();
        TAB_BY_LINE_ID.clear();
        TIME_BY_LINE_ID.clear();
        SCROLL.clear();
        RENDERED.clear();
        ANCHORS.clear();
        KEPT_ROWS.clear();
        SCROLL_REVISION.clear();
        WAITING_BELOW.clear();
        UNREAD_PINGS.clear();
        UNREAD_OTHER.clear();
        UNREAD_DIVIDERS.clear();
        NEWEST_MESSAGE_BY_VIEW.clear();
        sessionArrival = ChatMessageIds.NONE;
        invalidateCache();
        ChatGroupRuns.clear();
        ClientChatMessageIds.clear();
        ClientChatDeliveryMarks.clear();
        ClientChatMessages.clear();
        ClientChatPendingEchoes.clear();
        LostTalesChatPresentation.forgetServerClock();
        ChatWindowLines.clear();
        ChatFrame.clear();
        ClientChatConsoleEvents.clear();
    }

    /** Line ids remembered: the history's capacity and a margin. */
    static int maxTrackedLines() {
        return LostTalesChatHistoryHooks.capacity() + TRACKED_LINES_MARGIN;
    }

    private static int count(Map<ConversationPage, Integer> counter, ConversationPage tab) {
        Integer value = tab == null ? null : counter.get(tab);
        return value == null ? 0 : value.intValue();
    }

    /**
     * Forgets every view's lines: what a filter passes has changed for a
     * line already standing, as when a line is found to be addressed to
     * the player after it was printed.
     */
    static synchronized void invalidateCache() {
        CACHE.clear();
    }

    /** One filter's last result and the history state it was built from. */
    private static final class CachedView {
        final List<ChatLine> source;
        final int sourceSize;
        final ChatLine first;
        final List<ChatLine> visible;

        CachedView(List<ChatLine> source, ChatLine first,
                   List<ChatLine> visible) {
            this.source = source;
            this.sourceSize = source.size();
            this.first = first;
            this.visible = visible;
        }

        boolean describes(List<ChatLine> drawnLines, ChatLine head) {
            return drawnLines == this.source
                    && drawnLines.size() == this.sourceSize
                    && head == this.first;
        }
    }
}
