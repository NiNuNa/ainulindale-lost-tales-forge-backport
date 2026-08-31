package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationState;
import com.ninuna.losttales.config.LostTalesConfig;
import java.util.ArrayList;
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
    /** Unread counts stop climbing here; the tab shows "99+". */
    public static final int MAX_UNREAD = 99;
    /**
     * The channel whose tab shows lines Lost Tales did not route: they
     * are what only this player sees anyway, so they live in the console
     * tab and keep the conversation tabs clean.
     */
    public static final ChatChannel SYSTEM_LINE_VIEW = ChatChannel.CONSOLE;
    private static final LinkedHashMap<Integer, ChatTab> TAB_BY_LINE_ID =
            new LinkedHashMap<Integer, ChatTab>();
    /** Where each view is scrolled to, in message lines. */
    private static final Map<ChatTab, Double> SCROLL =
            new HashMap<ChatTab, Double>();
    /** Where each view is drawn right now, easing toward its target. */
    private static final Map<ChatTab, Ease> RENDERED =
            new HashMap<ChatTab, Ease>();
    /**
     * Messages that have arrived in a tab while its own view was
     * scrolled back, capped like the unread counters. Cleared the moment
     * the view returns to the newest message, which is what the
     * jump-to-present button does.
     */
    private static final Map<ChatTab, Integer> WAITING_BELOW =
            new HashMap<ChatTab, Integer>();
    /** The message each scrolled-back view is holding on to. */
    private static final Map<ChatTab, Anchor> ANCHORS =
            new HashMap<ChatTab, Anchor>();
    /**
     * How many times the player has moved each view themselves. A hold
     * is taken against the reading it was made from, so a wheel turn,
     * a scrollbar drag or a jump replaces it rather than being undone
     * by it.
     */
    private static final Map<ChatTab, Integer> SCROLL_REVISION =
            new HashMap<ChatTab, Integer>();
    /** Closer than this to the target and the offset simply arrives. */
    private static final double SCROLL_SNAP_LINES = 0.01D;
    /**
     * Unread messages per tab, split once at arrival: a message that
     * @-mentions the local player counts as a ping and nowhere else, so the
     * two counters never describe the same line twice.
     */
    private static final Map<ChatTab, Integer> UNREAD_PINGS =
            new HashMap<ChatTab, Integer>();
    private static final Map<ChatTab, Integer> UNREAD_OTHER =
            new HashMap<ChatTab, Integer>();
    /**
     * Where each tab's latest unread run begins: the divider the view
     * draws above that line — "new since you last looked". Set when the
     * first unread line of a run arrives, kept while the tab is being
     * read (so the divider does not vanish the moment the tab opens),
     * and replaced by the next run once this one has been seen.
     */
    private static final Map<ChatTab, UnreadDivider> UNREAD_DIVIDERS =
            new HashMap<ChatTab, UnreadDivider>();

    private static long openedNanos;
    private static final LostTalesGuiAnimationState OPEN_STATE =
            new LostTalesGuiAnimationState();

    /** Filters whose visible subset is kept; one per window is plenty. */
    private static final int MAX_CACHED_FILTERS = 8;
    private static final LinkedHashMap<ChatLineFilter, CachedView> CACHE =
            new LinkedHashMap<ChatLineFilter, CachedView>(16, 0.75F, true);

    private ClientChatChannelViews() {}

    /** Remembers a new Lost Tales line's tab and counts it unread elsewhere. */
    public static synchronized void record(int chatLineId, ChatTab tab,
                                           ChatTab selected,
                                           boolean mentionsLocalPlayer) {
        if (tab == null) {
            return;
        }
        TAB_BY_LINE_ID.put(Integer.valueOf(chatLineId), tab);
        while (TAB_BY_LINE_ID.size() > maxTrackedLines()) {
            Iterator<Integer> iterator = TAB_BY_LINE_ID.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        invalidateCache();
        if (tab.equals(selected)) {
            // The tab is open in front of the player — but if they have
            // scrolled back to read, a message arriving is one they have
            // not seen. It is counted on the jump-to-present button, and
            // the first of a run opens the same crimson divider an unread
            // run in another tab opens, so where they were reading is
            // marked as plainly there as anywhere else.
            if (target(tab) > 0.0D) {
                int waiting = count(WAITING_BELOW, tab);
                WAITING_BELOW.put(tab, Integer.valueOf(
                        Math.min(MAX_UNREAD + 1, waiting + 1)));
                if (waiting == 0) {
                    UNREAD_DIVIDERS.put(tab, new UnreadDivider(chatLineId));
                }
            }
            return;
        }
        {
            Map<ChatTab, Integer> counter = mentionsLocalPlayer
                    ? UNREAD_PINGS : UNREAD_OTHER;
            counter.put(tab, Integer.valueOf(
                    Math.min(MAX_UNREAD + 1, count(counter, tab) + 1)));
            UnreadDivider divider = UNREAD_DIVIDERS.get(tab);
            if (divider == null || divider.seen) {
                UNREAD_DIVIDERS.put(tab, new UnreadDivider(chatLineId));
            }
        }
    }

    public static synchronized void record(int chatLineId, ChatChannel channel,
                                           ChatChannel selected,
                                           boolean mentionsLocalPlayer) {
        record(chatLineId, ChatTab.of(channel), ChatTab.of(selected),
                mentionsLocalPlayer);
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
    static synchronized void holdPosition(ChatTab view,
                                          ChatWindowFrame frame) {
        if (view == null || frame == null) {
            return;
        }
        double current = target(view);
        List<ChatLine> lines = frame.lines;
        if (current <= 0.0D || lines == null || lines.isEmpty()) {
            ANCHORS.remove(view);
            // Back at the newest message: nothing is waiting below any
            // more, whether the player scrolled down or jumped.
            WAITING_BELOW.remove(view);
            return;
        }
        int divider = frame.dividerLineIndex;
        Anchor anchor = ANCHORS.get(view);
        if (anchor != null && anchor.revision == revision(view)) {
            int index = anchor.locate(lines);
            if (index >= 0) {
                place(view, LostTalesChatOverlayRenderer.rowOfLine(
                        index, divider) + anchor.delta, current);
                return;
            }
        }
        // Nothing held yet, or the message that was held has been
        // trimmed away: take hold of whatever is at the view's edge now.
        int index = Math.max(0, Math.min(lines.size() - 1,
                LostTalesChatOverlayRenderer.lineOfRow(
                        (int)Math.floor(current), divider)));
        ChatLine line = lines.get(index);
        if (line == null) {
            ANCHORS.remove(view);
            return;
        }
        ANCHORS.put(view, new Anchor(line.getChatLineID(), index,
                current - LostTalesChatOverlayRenderer.rowOfLine(
                        index, divider), revision(view)));
    }

    /**
     * Moves a view's offset to where its held message now stands. The
     * drawn offset moves with it rather than easing after it: nothing
     * has happened that the eye should see travel — the page is exactly
     * where it was, and only the rows beneath it have changed.
     */
    private static void place(ChatTab view, double offset, double current) {
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

    /** What a view is holding on to while it is scrolled back. */
    private static final class Anchor {
        final int chatLineId;
        /** Rows between that line's own row and the view's offset. */
        final double delta;
        /** The scroll this hold was taken against; a later one drops it. */
        final int revision;
        /** Where the line was last found, tried first next time. */
        private int lastIndex;

        Anchor(int chatLineId, int index, double delta, int revision) {
            this.chatLineId = chatLineId;
            this.lastIndex = index;
            this.delta = delta;
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

    /** Called while a view is on screen; clears its unread counters. */
    public static synchronized void markViewed(ChatTab tab) {
        if (tab != null) {
            UNREAD_PINGS.remove(tab);
            UNREAD_OTHER.remove(tab);
            UnreadDivider divider = UNREAD_DIVIDERS.get(tab);
            if (divider != null) {
                // The divider stays while the tab is read; the next
                // unread run replaces it.
                divider.seen = true;
            }
        }
    }

    /**
     * The line the tab's unread divider stands above, or null: the first
     * line of the latest unread run.
     */
    public static synchronized Integer unreadDividerLine(ChatTab tab) {
        UnreadDivider divider = tab == null ? null
                : UNREAD_DIVIDERS.get(tab);
        return divider == null ? null : Integer.valueOf(divider.lineId);
    }

    /** The divider's date label: the day its unread run began. */
    public static synchronized String unreadDividerLabel(ChatTab tab) {
        UnreadDivider divider = tab == null ? null
                : UNREAD_DIVIDERS.get(tab);
        return divider == null ? "" : divider.label;
    }

    /**
     * Removes the tab's divider once its run has been seen: called when
     * the player moves on — the tab is deselected, or the screen closes
     * — so the divider shows while the new messages are being read and
     * is gone the next time the tab opens, the way Discord's is.
     */
    public static synchronized void dismissSeenDivider(ChatTab tab) {
        UnreadDivider divider = tab == null ? null
                : UNREAD_DIVIDERS.get(tab);
        if (divider != null && divider.seen) {
            UNREAD_DIVIDERS.remove(tab);
        }
    }

    /**
     * Removes the tab's divider outright, seen or not: sending a
     * message there says the conversation has moved on, the way it does
     * on Discord.
     */
    public static synchronized void dismissDivider(ChatTab tab) {
        if (tab != null) {
            UNREAD_DIVIDERS.remove(tab);
        }
    }

    /** As above for every tab at once; what closing the screen calls. */
    public static synchronized void dismissSeenDividers() {
        Iterator<Map.Entry<ChatTab, UnreadDivider>> iterator =
                UNREAD_DIVIDERS.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().seen) {
                iterator.remove();
            }
        }
    }

    /** Sends the view back to the newest line; it glides there. */
    public static synchronized void scrollHome(ChatTab view) {
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
    public static synchronized int waitingBelow(ChatTab view) {
        return count(WAITING_BELOW, view);
    }

    /** One tab's divider: where the latest unread run starts. */
    private static final class UnreadDivider {
        final int lineId;
        /** The run's day, formatted once when it began. */
        final String label;
        boolean seen;

        UnreadDivider(int lineId) {
            this.lineId = lineId;
            this.label = java.text.DateFormat.getDateInstance(
                    java.text.DateFormat.LONG).format(new java.util.Date());
        }
    }

    public static synchronized void markViewed(ChatChannel channel) {
        markViewed(ChatTab.of(channel));
    }

    /** Unread messages that mentioned the player, capped at MAX_UNREAD + 1. */
    public static synchronized int unreadPingCount(ChatTab tab) {
        return count(UNREAD_PINGS, tab);
    }

    /** Unread messages other than pings, capped at MAX_UNREAD + 1. */
    public static synchronized int unreadOtherCount(ChatTab tab) {
        return count(UNREAD_OTHER, tab);
    }

    /** Every unread message, pings included, capped at MAX_UNREAD + 1. */
    public static synchronized int unreadCount(ChatTab tab) {
        return Math.min(MAX_UNREAD + 1,
                unreadPingCount(tab) + unreadOtherCount(tab));
    }

    public static synchronized int unreadCount(ChatChannel channel) {
        return unreadCount(ChatTab.of(channel));
    }

    /**
     * The unread indicator every chat label carries: {@code [n]} for a
     * positive count, {@code [99+]} past the cap, nothing otherwise.
     */
    public static String counterText(int count) {
        if (count <= 0) {
            return "";
        }
        return "[" + (count > MAX_UNREAD ? MAX_UNREAD + "+"
                : String.valueOf(count)) + "]";
    }

    public static synchronized int unreadPingCount(ChatChannel channel) {
        return unreadPingCount(ChatTab.of(channel));
    }

    public static synchronized int unreadOtherCount(ChatChannel channel) {
        return unreadOtherCount(ChatTab.of(channel));
    }

    public static synchronized boolean hasUnread(ChatTab tab) {
        return unreadPingCount(tab) + unreadOtherCount(tab) > 0;
    }

    public static synchronized boolean hasUnread(ChatChannel channel) {
        return hasUnread(ChatTab.of(channel));
    }

    public static synchronized boolean hasUnreadMention(ChatTab tab) {
        return unreadPingCount(tab) > 0;
    }

    public static synchronized boolean hasUnreadMention(ChatChannel channel) {
        return hasUnreadMention(ChatTab.of(channel));
    }

    /** The recorded tab, or null for vanilla and untracked lines. */
    public static synchronized ChatTab tabOf(int chatLineId) {
        return TAB_BY_LINE_ID.get(Integer.valueOf(chatLineId));
    }

    /** The recorded channel, or null for vanilla and untracked lines. */
    public static synchronized ChatChannel channelOf(int chatLineId) {
        ChatTab tab = tabOf(chatLineId);
        return tab == null ? null : tab.getChannel();
    }

    /**
     * The lines shown for {@code view} (null meaning the combined feed), in
     * vanilla's newest-first order.
     */
    public static synchronized List<ChatLine> visibleLines(
            List<ChatLine> drawnLines, ChatTab view) {
        if (view == null) {
            return drawnLines == null
                    ? Collections.<ChatLine>emptyList() : drawnLines;
        }
        return visibleLines(drawnLines, ChatLineFilter.of(view));
    }

    public static synchronized List<ChatLine> visibleLines(
            List<ChatLine> drawnLines, ChatChannel view) {
        return visibleLines(drawnLines, ChatTab.of(view));
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
            // Untracked lines belong to the console wherever its tab lives.
            if (filter.accepts(TAB_BY_LINE_ID.get(
                    Integer.valueOf(line.getChatLineID())))) {
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
    public static synchronized double getScroll(ChatTab view, int totalLines,
                                                double roomLines) {
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

    public static synchronized double getScroll(ChatChannel view,
                                                int totalLines,
                                                double roomLines) {
        return getScroll(ChatTab.of(view), totalLines, roomLines);
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
    public static synchronized double renderedScroll(ChatTab view,
                                                     double target) {
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
        if (!LostTalesConfig.enableChatAnimations
                || scrollEasingSuppressed) {
            ease.value = target;
            ease.nanos = now;
            return target;
        }
        double elapsed = (now - ease.nanos) / 1.0E9D;
        ease.nanos = now;
        if (Math.abs(target - ease.value) <= SCROLL_SNAP_LINES) {
            ease.value = target;
        } else {
            ease.value = LostTalesChatMotion.approach(ease.value, target,
                    elapsed, LostTalesChatMotion.SCROLL_EASE_SECONDS);
        }
        while (RENDERED.size() > MAX_EASED_VIEWS) {
            Iterator<ChatTab> oldest = RENDERED.keySet().iterator();
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
    public static synchronized void scrollTo(ChatTab view, double lines,
                                             int totalLines,
                                             double roomLines) {
        if (view != null) {
            SCROLL.put(view, Double.valueOf(Math.max(0.0D, lines)));
            noteScrolled(view);
            getScroll(view, totalLines, roomLines);
        }
    }

    public static synchronized void scroll(ChatTab view, int delta,
                                           int totalLines,
                                           double roomLines) {
        if (view == null) {
            return;
        }
        SCROLL.put(view, Double.valueOf(target(view) + delta));
        noteScrolled(view);
        getScroll(view, totalLines, roomLines);
    }

    public static synchronized void scroll(ChatChannel view, int delta,
                                           int totalLines,
                                           double roomLines) {
        scroll(ChatTab.of(view), delta, totalLines, roomLines);
    }

    /** Drops every view back to the newest line. */
    public static synchronized void resetScroll() {
        SCROLL.clear();
        RENDERED.clear();
        ANCHORS.clear();
        SCROLL_REVISION.clear();
        WAITING_BELOW.clear();
    }

    private static double target(ChatTab view) {
        Double value = view == null ? null : SCROLL.get(view);
        return value == null ? 0.0D : value.doubleValue();
    }

    private static int revision(ChatTab view) {
        Integer value = view == null ? null : SCROLL_REVISION.get(view);
        return value == null ? 0 : value.intValue();
    }

    /** Records that the player has moved this view themselves. */
    private static void noteScrolled(ChatTab view) {
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

    /** Starts the history's entrance when the chat screen opens. */
    public static synchronized void noteOpened() {
        openedNanos = System.nanoTime();
        OPEN_STATE.restart();
    }

    /**
     * The opening motion for the history and the tabs: the same sampler,
     * easing, direction, duration and reduced-motion rule every other Lost
     * Tales screen animates with, so the chat arrives like the rest of the
     * interface. Only the input bars keep their own entrance.
     */
    public static synchronized LostTalesGuiAnimationSample openSample() {
        if (!LostTalesConfig.enableGuiAnimations
                || !LostTalesConfig.enableChatAnimations
                || openedNanos <= 0L) {
            return LostTalesGuiAnimationSample.SETTLED;
        }
        int duration = Math.max(10, LostTalesConfig.guiAnimationDurationMillis);
        if (LostTalesConfig.reducedGuiMotion) {
            duration = Math.min(duration, 90);
        }
        return OPEN_STATE.sample(System.nanoTime(), duration, duration,
                LostTalesConfig.reducedGuiMotion,
                LostTalesConfig.guiAnimationEasingStyle,
                LostTalesConfig.guiAnimationDirection,
                (float)LostTalesConfig.guiAnimationScale);
    }

    public static synchronized void clear() {
        TAB_BY_LINE_ID.clear();
        SCROLL.clear();
        RENDERED.clear();
        ANCHORS.clear();
        SCROLL_REVISION.clear();
        WAITING_BELOW.clear();
        UNREAD_PINGS.clear();
        UNREAD_OTHER.clear();
        UNREAD_DIVIDERS.clear();
        openedNanos = 0L;
        invalidateCache();
        ChatGroupRuns.clear();
        ClientChatMessageIds.clear();
        ClientChatMessages.clear();
        ClientChatPendingEchoes.clear();
        ChatWindowLines.clear();
        ChatWindowFrame.clear();
        ClientChatAccountRoles.clear();
        ClientChatAppearances.clear();
        ChatTabSelection.clear();
        // The history is gone with the world, and so are its conversations.
        ChatWindowLayout.closeConversations();
        ChatChannelIcons.forgetPortraits();
    }

    /** Line ids remembered: the history's capacity and a margin. */
    static int maxTrackedLines() {
        return LostTalesChatHistoryHooks.capacity() + TRACKED_LINES_MARGIN;
    }

    private static int count(Map<ChatTab, Integer> counter, ChatTab tab) {
        Integer value = tab == null ? null : counter.get(tab);
        return value == null ? 0 : value.intValue();
    }

    private static void invalidateCache() {
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

    /** Test and diagnostics hook: tracked line count. */
    static synchronized int trackedLineCount() {
        return TAB_BY_LINE_ID.size();
    }

    /** Convenience for tests: the map view of tracked lines. */
    static synchronized Map<Integer, ChatTab> trackedLines() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<Integer, ChatTab>(TAB_BY_LINE_ID));
    }
}
