package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationState;
import com.ninuna.losttales.config.LostTalesConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.ChatLine;

/**
 * Per-channel views over vanilla's single chat history. Lines stay in
 * {@code GuiNewChat}; this class only remembers which channel each Lost
 * Tales chat-line id belongs to and derives the selected channel's visible
 * subset, scroll offset, and unread counts from that. Lines without a
 * recorded channel (commands, server notices, other mods) belong to every
 * view, so nothing vanilla ever disappears. Messages are never copied.
 *
 * <p>The HUD overlay with chat closed still shows the combined feed; the
 * per-channel view applies while the chat screen is open, where the tabs
 * make the split visible. Scroll offsets survive closing the screen.</p>
 */
public final class ClientChatChannelViews {
    /** Above vanilla's 100-message history so every shown line is tracked. */
    private static final int MAX_TRACKED_LINES = 256;
    /** Unread counts stop climbing here; the badge shows "99+". */
    public static final int MAX_UNREAD = 99;
    /** The one tab that also shows vanilla and other mods' lines. */
    public static final ChatChannel SYSTEM_LINE_VIEW = ChatChannel.ALL;
    private static final LinkedHashMap<Integer, ChatChannel> CHANNEL_BY_LINE_ID =
            new LinkedHashMap<Integer, ChatChannel>();
    private static final int CHANNEL_COUNT = ChatChannel.values().length;
    private static final int[] SCROLL = new int[CHANNEL_COUNT];
    private static final int[] UNREAD = new int[CHANNEL_COUNT];
    private static final boolean[] MENTION = new boolean[CHANNEL_COUNT];

    private static long viewSwitchNanos;
    private static long openedNanos;
    private static final LostTalesGuiAnimationState OPEN_STATE =
            new LostTalesGuiAnimationState();
    private static ChatChannel lastView;

    private static List<ChatLine> cachedSource;
    private static int cachedSourceSize = -1;
    private static ChatLine cachedFirst;
    private static ChatChannel cachedView;
    private static List<ChatLine> cachedVisible = Collections.emptyList();

    private ClientChatChannelViews() {}

    /** Remembers a new Lost Tales line's channel and counts it unread elsewhere. */
    public static synchronized void record(int chatLineId, ChatChannel channel,
                                           ChatChannel selected,
                                           boolean mentionsLocalPlayer) {
        if (channel == null) {
            return;
        }
        CHANNEL_BY_LINE_ID.put(Integer.valueOf(chatLineId), channel);
        while (CHANNEL_BY_LINE_ID.size() > MAX_TRACKED_LINES) {
            Iterator<Integer> iterator = CHANNEL_BY_LINE_ID.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        invalidateCache();
        if (channel != selected) {
            int ordinal = channel.ordinal();
            UNREAD[ordinal] = Math.min(MAX_UNREAD + 1, UNREAD[ordinal] + 1);
            MENTION[ordinal] |= mentionsLocalPlayer;
        }
    }

    /** Keeps a scrolled-up view stable when lines are inserted above it. */
    public static synchronized void onLinesAdded(ChatChannel channel,
                                                 int addedLineCount) {
        if (addedLineCount <= 0) {
            return;
        }
        for (ChatChannel view : ChatChannel.values()) {
            if ((channel == null || view == channel)
                    && SCROLL[view.ordinal()] > 0) {
                SCROLL[view.ordinal()] += addedLineCount;
            }
        }
    }

    /** Called while a view is on screen; clears its unread markers. */
    public static synchronized void markViewed(ChatChannel channel) {
        if (channel != null) {
            UNREAD[channel.ordinal()] = 0;
            MENTION[channel.ordinal()] = false;
        }
    }

    /** Messages received since the channel was last viewed, capped. */
    public static synchronized int unreadCount(ChatChannel channel) {
        return channel == null ? 0 : UNREAD[channel.ordinal()];
    }

    public static synchronized boolean hasUnread(ChatChannel channel) {
        return unreadCount(channel) > 0;
    }

    public static synchronized boolean hasUnreadMention(ChatChannel channel) {
        return channel != null && MENTION[channel.ordinal()];
    }

    /** The recorded channel, or null for vanilla and untracked lines. */
    public static synchronized ChatChannel channelOf(int chatLineId) {
        return CHANNEL_BY_LINE_ID.get(Integer.valueOf(chatLineId));
    }

    /**
     * The lines shown for {@code view} (null meaning the combined feed), in
     * vanilla's newest-first order. Cached until the drawn-line list, its
     * head, or the view changes, so per-frame callers pay one comparison.
     */
    public static synchronized List<ChatLine> visibleLines(
            List<ChatLine> drawnLines, ChatChannel view) {
        if (drawnLines == null || drawnLines.isEmpty()) {
            return Collections.emptyList();
        }
        if (view == null) {
            return drawnLines;
        }
        ChatLine first = drawnLines.get(0);
        if (drawnLines == cachedSource && drawnLines.size() == cachedSourceSize
                && first == cachedFirst && view == cachedView) {
            return cachedVisible;
        }
        List<ChatLine> visible = new ArrayList<ChatLine>(drawnLines.size());
        for (int index = 0; index < drawnLines.size(); index++) {
            ChatLine line = drawnLines.get(index);
            if (line == null) {
                continue;
            }
            ChatChannel channel = CHANNEL_BY_LINE_ID.get(
                    Integer.valueOf(line.getChatLineID()));
            // Untracked lines (achievements, commands, other mods) live in
            // the Global tab only, so the role-play and OOC tabs stay clean.
            if (channel == view
                    || (channel == null && view == SYSTEM_LINE_VIEW)) {
                visible.add(line);
            }
        }
        cachedSource = drawnLines;
        cachedSourceSize = drawnLines.size();
        cachedFirst = first;
        cachedView = view;
        cachedVisible = Collections.unmodifiableList(visible);
        return cachedVisible;
    }

    /** Scroll offset (in lines) for the view, clamped to its content. */
    public static synchronized int getScroll(ChatChannel view,
                                             int totalLines,
                                             int visibleLineCount) {
        if (view == null) {
            return 0;
        }
        int maximum = Math.max(0, totalLines - Math.max(1, visibleLineCount));
        int clamped = Math.max(0, Math.min(maximum, SCROLL[view.ordinal()]));
        SCROLL[view.ordinal()] = clamped;
        return clamped;
    }

    public static synchronized void scroll(ChatChannel view, int delta,
                                           int totalLines,
                                           int visibleLineCount) {
        if (view == null) {
            return;
        }
        SCROLL[view.ordinal()] += delta;
        getScroll(view, totalLines, visibleLineCount);
    }

    /** Drops every view back to the newest line. */
    public static synchronized void resetScroll() {
        for (int index = 0; index < SCROLL.length; index++) {
            SCROLL[index] = 0;
        }
    }

    /** Starts the view transition when the selected channel changes. */
    public static synchronized void noteView(ChatChannel view) {
        if (view != lastView) {
            if (lastView != null) {
                viewSwitchNanos = System.nanoTime();
            }
            lastView = view;
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
     * interface. Only the input bar keeps its own entrance.
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

    /** 0 while a switch transition starts, 1 once settled; 1 when disabled. */
    public static synchronized float viewTransitionProgress() {
        if (!LostTalesConfig.enableChatAnimations || viewSwitchNanos <= 0L) {
            return 1.0F;
        }
        long duration = Math.max(1,
                LostTalesConfig.chatSelectorAnimationDurationMillis)
                * 1000000L;
        return Math.max(0.0F, Math.min(1.0F,
                (System.nanoTime() - viewSwitchNanos) / (float)duration));
    }

    public static synchronized void clear() {
        CHANNEL_BY_LINE_ID.clear();
        for (int index = 0; index < CHANNEL_COUNT; index++) {
            SCROLL[index] = 0;
            UNREAD[index] = 0;
            MENTION[index] = false;
        }
        viewSwitchNanos = 0L;
        openedNanos = 0L;
        lastView = null;
        invalidateCache();
    }

    private static void invalidateCache() {
        cachedSource = null;
        cachedSourceSize = -1;
        cachedFirst = null;
        cachedView = null;
        cachedVisible = Collections.emptyList();
    }

    /** Test and diagnostics hook: tracked line count. */
    static synchronized int trackedLineCount() {
        return CHANNEL_BY_LINE_ID.size();
    }

    /** Convenience for tests: the map view of tracked lines. */
    static synchronized Map<Integer, ChatChannel> trackedLines() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<Integer, ChatChannel>(CHANNEL_BY_LINE_ID));
    }
}
