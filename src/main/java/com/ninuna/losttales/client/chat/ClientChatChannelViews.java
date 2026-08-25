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
        if (!tab.equals(selected)) {
            Map<ChatTab, Integer> counter = mentionsLocalPlayer
                    ? UNREAD_PINGS : UNREAD_OTHER;
            counter.put(tab, Integer.valueOf(
                    Math.min(MAX_UNREAD + 1, count(counter, tab) + 1)));
        }
    }

    public static synchronized void record(int chatLineId, ChatChannel channel,
                                           ChatChannel selected,
                                           boolean mentionsLocalPlayer) {
        record(chatLineId, ChatTab.of(channel), ChatTab.of(selected),
                mentionsLocalPlayer);
    }

    /** Keeps a scrolled-up view stable when lines are inserted above it. */
    public static synchronized void onLinesAdded(ChatTab tab,
                                                 int addedLineCount) {
        if (addedLineCount <= 0) {
            return;
        }
        for (Map.Entry<ChatTab, Double> entry : SCROLL.entrySet()) {
            if ((tab == null || entry.getKey().equals(tab))
                    && entry.getValue().doubleValue() > 0.0D) {
                entry.setValue(Double.valueOf(
                        entry.getValue().doubleValue() + addedLineCount));
                // The view stays on the same message, so what is drawn
                // must move with it rather than easing across the gap.
                Ease ease = RENDERED.get(entry.getKey());
                if (ease != null) {
                    ease.value += addedLineCount;
                }
            }
        }
    }

    public static synchronized void onLinesAdded(ChatChannel channel,
                                                 int addedLineCount) {
        onLinesAdded(ChatTab.of(channel), addedLineCount);
    }

    /** Called while a view is on screen; clears its unread counters. */
    public static synchronized void markViewed(ChatTab tab) {
        if (tab != null) {
            UNREAD_PINGS.remove(tab);
            UNREAD_OTHER.remove(tab);
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

    public static synchronized void scroll(ChatTab view, int delta,
                                           int totalLines,
                                           double roomLines) {
        if (view == null) {
            return;
        }
        SCROLL.put(view, Double.valueOf(target(view) + delta));
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
    }

    private static double target(ChatTab view) {
        Double value = view == null ? null : SCROLL.get(view);
        return value == null ? 0.0D : value.doubleValue();
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
        UNREAD_PINGS.clear();
        UNREAD_OTHER.clear();
        openedNanos = 0L;
        invalidateCache();
        ChatWindowLines.clear();
        ChatWindowFrame.clear();
        ClientChatAccountRoles.clear();
        ClientChatAppearances.clear();
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
