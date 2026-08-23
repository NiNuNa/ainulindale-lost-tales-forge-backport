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
    private static final Map<ChatTab, Integer> SCROLL =
            new HashMap<ChatTab, Integer>();
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
        for (Map.Entry<ChatTab, Integer> entry : SCROLL.entrySet()) {
            if ((tab == null || entry.getKey().equals(tab))
                    && entry.getValue().intValue() > 0) {
                entry.setValue(Integer.valueOf(
                        entry.getValue().intValue() + addedLineCount));
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

    /** Scroll offset (in lines) for the view, clamped to its content. */
    public static synchronized int getScroll(ChatTab view, int totalLines,
                                             int visibleLineCount) {
        if (view == null) {
            return 0;
        }
        int maximum = Math.max(0, totalLines - Math.max(1, visibleLineCount));
        int clamped = Math.max(0, Math.min(maximum, count(SCROLL, view)));
        if (clamped == 0) {
            SCROLL.remove(view);
        } else {
            SCROLL.put(view, Integer.valueOf(clamped));
        }
        return clamped;
    }

    public static synchronized int getScroll(ChatChannel view, int totalLines,
                                             int visibleLineCount) {
        return getScroll(ChatTab.of(view), totalLines, visibleLineCount);
    }

    public static synchronized void scroll(ChatTab view, int delta,
                                           int totalLines,
                                           int visibleLineCount) {
        if (view == null) {
            return;
        }
        SCROLL.put(view, Integer.valueOf(count(SCROLL, view) + delta));
        getScroll(view, totalLines, visibleLineCount);
    }

    public static synchronized void scroll(ChatChannel view, int delta,
                                           int totalLines,
                                           int visibleLineCount) {
        scroll(ChatTab.of(view), delta, totalLines, visibleLineCount);
    }

    /** Drops every view back to the newest line. */
    public static synchronized void resetScroll() {
        SCROLL.clear();
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
        UNREAD_PINGS.clear();
        UNREAD_OTHER.clear();
        openedNanos = 0L;
        invalidateCache();
        ChatWindowFrame.clear();
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
