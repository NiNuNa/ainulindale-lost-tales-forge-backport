package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * The one authoritative client chat layout: which windows exist, which
 * tabs each holds in what order, which tab is in front, whether a window
 * is locked, where each window sits, and each tab's notification
 * preferences — muted (out of the feed and silent), or only one half of
 * that: hidden from the feed, or its mention cue silenced. A tab is
 * a channel, or a whisper conversation with one account; a tab lives in
 * at most one window, and a plain channel in no window is <em>closed</em>
 * — hidden from every window, its history untouched, and restorable into
 * any window (a closed whisper simply reopens with the next message).
 * Closing is about tabs only: a closed channel keeps receiving, keeps
 * its history and unread counts, and keeps its mute setting. Every
 * window is equal: one that loses its last tab disappears, and the
 * last open tab of all can never be closed, so there is always
 * somewhere to type. The default layout is a console window (Console,
 * Admin) in the top-left corner and a conversation window with every
 * other channel in the bottom-left corner. A window dropped against
 * another's top or bottom edge <em>links</em> to it and from then on
 * keeps that gap as the other grows, shrinks or moves; a link is one
 * window's, and dragging the linked window away breaks it. The layout
 * also places the <em>feed</em>: the one stack that shows every unmuted
 * channel's messages, open or closed, while the chat is closed,
 * positioned on its own, independently of the windows.
 *
 * <p>This is layout and preference state only: message history stays in
 * vanilla's chat, per-line tabs and unread counts in
 * {@link ClientChatChannelViews}, channel availability in
 * {@link ClientChatChannelState}. The in-game chat screen, the closed-chat
 * overlay and the HUD placement editor all read and write this same
 * model, so a window moved in one place is where the others find it.
 * Every mutation is reported to the registered listener, which the
 * file-backed store uses to persist the layout; position changes made
 * while dragging are reported only when the caller asks for it.</p>
 */
public final class ChatWindowLayout {
    /** Bound on windows; more than this is a broken file, not a layout. */
    public static final int MAX_WINDOWS = 8;
    /** Window id of layouts written before every window was equal. */
    static final String LEGACY_MAIN_ID = "main";
    private static final String ID_PREFIX = "w";
    private static final List<ChatTab> CONSOLE_WINDOW_TABS =
            Collections.unmodifiableList(Arrays.asList(
                    ChatTab.of(ChatChannel.CONSOLE),
                    ChatTab.of(ChatChannel.ADMIN)));

    private static final List<ChatWindow> WINDOWS = new ArrayList<ChatWindow>();
    private static final List<ChatWindow> WINDOWS_VIEW =
            Collections.unmodifiableList(WINDOWS);
    private static final Set<ChatTab> MUTED = new HashSet<ChatTab>();
    /** Tabs left out of the closed-chat feed; their cue still sounds. */
    private static final Set<ChatTab> FEED_HIDDEN = new HashSet<ChatTab>();
    /** Tabs whose mention cue is silent; they still show in the feed. */
    private static final Set<ChatTab> PINGS_SILENCED = new HashSet<ChatTab>();
    /**
     * Stacking order, back to front, by window id: the window last
     * brought to the front draws last and is hit first. Session state,
     * never stored; windows not listed sit at the back in layout order.
     */
    private static final List<String> STACK = new ArrayList<String>();
    private static int nextWindowNumber = 1;
    private static Runnable changeListener;
    /** Closed-chat feed position, percent of its travel; vanilla's spot. */
    private static double feedOffsetX;
    private static double feedOffsetY = 100.0D;
    private static boolean toolbarCollapsed;

    static {
        reset();
    }

    private ChatWindowLayout() {}

    /** Called after every persisted mutation; the store saves here. */
    static synchronized void setChangeListener(Runnable listener) {
        changeListener = listener;
    }

    /**
     * The default layout: the console window (Console, Admin) top-left
     * and the conversation window with every other channel bottom-left.
     */
    public static synchronized void reset() {
        WINDOWS.clear();
        MUTED.clear();
        FEED_HIDDEN.clear();
        PINGS_SILENCED.clear();
        STACK.clear();
        nextWindowNumber = 1;
        feedOffsetX = 0.0D;
        feedOffsetY = 100.0D;
        toolbarCollapsed = false;
        ChatWindow console = newWindow();
        console.tabs().addAll(CONSOLE_WINDOW_TABS);
        console.setActiveTab(ChatTab.of(ChatChannel.CONSOLE));
        console.setOffsets(0.0D, 0.0D);
        WINDOWS.add(console);
        ChatWindow conversation = newWindow();
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            if (!CONSOLE_WINDOW_TABS.contains(ChatTab.of(channel))) {
                conversation.tabs().add(ChatTab.of(channel));
            }
        }
        conversation.setActiveTab(ChatTab.of(ChatChannel.ALL));
        conversation.setOffsets(0.0D, 100.0D);
        WINDOWS.add(conversation);
    }

    /** Whether the picker strip above the input bar is folded away. */
    public static synchronized boolean isToolbarCollapsed() {
        return toolbarCollapsed;
    }

    public static synchronized void setToolbarCollapsed(boolean collapsed) {
        if (toolbarCollapsed != collapsed) {
            toolbarCollapsed = collapsed;
            changed();
        }
    }

    public static synchronized double feedOffsetX() {
        return feedOffsetX;
    }

    public static synchronized double feedOffsetY() {
        return feedOffsetY;
    }

    /** Positions the closed-chat feed; {@code persist} false while dragging. */
    public static synchronized void setFeedPosition(double offsetX,
                                                    double offsetY,
                                                    boolean persist) {
        feedOffsetX = clampPercent(offsetX);
        feedOffsetY = clampPercent(offsetY);
        if (persist) {
            changed();
        }
    }

    /** Windows in order. Live, read-only view; never empty. */
    public static synchronized List<ChatWindow> windows() {
        return WINDOWS_VIEW;
    }

    /**
     * Windows back to front: the ones never raised first, in layout
     * order, then the raised ones, the most recent last. Draw in this
     * order; hit test in reverse.
     */
    public static synchronized List<ChatWindow> stacked() {
        List<ChatWindow> result = new ArrayList<ChatWindow>(WINDOWS.size());
        for (int index = 0; index < WINDOWS.size(); index++) {
            if (!STACK.contains(WINDOWS.get(index).getId())) {
                result.add(WINDOWS.get(index));
            }
        }
        for (int index = 0; index < STACK.size(); index++) {
            ChatWindow window = window(STACK.get(index));
            if (window != null) {
                result.add(window);
            }
        }
        return result;
    }

    /** Brings a window to the front of the stack; not a layout change. */
    public static synchronized void raise(String windowId) {
        if (window(windowId) == null) {
            return;
        }
        STACK.remove(windowId);
        STACK.add(windowId);
        // Ids of windows that have since gone are dropped here.
        Iterator<String> iterator = STACK.iterator();
        while (iterator.hasNext()) {
            if (window(iterator.next()) == null) {
                iterator.remove();
            }
        }
    }

    /** The first window: where restored and unplaced channels land. */
    public static synchronized ChatWindow firstWindow() {
        return WINDOWS.get(0);
    }

    public static synchronized ChatWindow window(String id) {
        for (int index = 0; index < WINDOWS.size(); index++) {
            if (WINDOWS.get(index).getId().equals(id)) {
                return WINDOWS.get(index);
            }
        }
        return null;
    }

    /** The window holding the tab, or null when it is closed. */
    public static synchronized ChatWindow windowOf(ChatTab tab) {
        if (tab == null) {
            return null;
        }
        for (int index = 0; index < WINDOWS.size(); index++) {
            if (WINDOWS.get(index).contains(tab)) {
                return WINDOWS.get(index);
            }
        }
        return null;
    }

    public static synchronized ChatWindow windowOf(ChatChannel channel) {
        return windowOf(ChatTab.of(channel));
    }

    public static synchronized boolean isOpen(ChatTab tab) {
        return windowOf(tab) != null;
    }

    public static synchronized boolean isOpen(ChatChannel channel) {
        return isOpen(ChatTab.of(channel));
    }

    /** Plain channels in no window, in presentation order. */
    public static synchronized List<ChatChannel> closedChannels() {
        List<ChatChannel> result = new ArrayList<ChatChannel>();
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            if (!isOpen(channel)) {
                result.add(channel);
            }
        }
        return result;
    }

    /** Every open tab in window order, each window's tabs in row order. */
    public static synchronized List<ChatTab> order() {
        List<ChatTab> result = new ArrayList<ChatTab>();
        for (int index = 0; index < WINDOWS.size(); index++) {
            result.addAll(WINDOWS.get(index).tabs());
        }
        return result;
    }

    /** The channels of {@link #order()}. */
    public static synchronized List<ChatChannel> orderChannels() {
        List<ChatChannel> result = new ArrayList<ChatChannel>();
        for (ChatTab tab : order()) {
            result.add(tab.getChannel());
        }
        return result;
    }

    public static synchronized boolean isMuted(ChatTab tab) {
        return tab != null && MUTED.contains(tab);
    }

    public static synchronized boolean isMuted(ChatChannel channel) {
        return isMuted(ChatTab.of(channel));
    }

    /** Muted plain tabs in a stable order, for the store. */
    public static synchronized List<ChatTab> mutedTabs() {
        return plainTabsOf(MUTED);
    }

    /**
     * Mute is a notification preference, the whole of it: the tab's lines
     * stay out of the feed and its mention cue is silent. The history is
     * untouched.
     */
    public static synchronized void setMuted(ChatTab tab, boolean muted) {
        setPreference(MUTED, tab, muted);
    }

    public static synchronized void setMuted(ChatChannel channel,
                                            boolean muted) {
        setMuted(ChatTab.of(channel), muted);
    }

    /** Whether the tab is kept out of the feed on its own, mute aside. */
    public static synchronized boolean isFeedHidden(ChatTab tab) {
        return tab != null && FEED_HIDDEN.contains(tab);
    }

    public static synchronized boolean isFeedHidden(ChatChannel channel) {
        return isFeedHidden(ChatTab.of(channel));
    }

    /** Whether the tab's lines are shown in the feed: neither muted nor hidden. */
    public static synchronized boolean isInFeed(ChatTab tab) {
        return tab != null && !MUTED.contains(tab) && !FEED_HIDDEN.contains(tab);
    }

    /** Feed-hidden plain tabs in a stable order, for the store. */
    public static synchronized List<ChatTab> feedHiddenTabs() {
        return plainTabsOf(FEED_HIDDEN);
    }

    /** The feed half of a mute: the cue still sounds. */
    public static synchronized void setFeedHidden(ChatTab tab,
                                                  boolean hidden) {
        setPreference(FEED_HIDDEN, tab, hidden);
    }

    /** Whether the tab's mention cue is silenced on its own, mute aside. */
    public static synchronized boolean isPingSilenced(ChatTab tab) {
        return tab != null && PINGS_SILENCED.contains(tab);
    }

    public static synchronized boolean isPingSilenced(ChatChannel channel) {
        return isPingSilenced(ChatTab.of(channel));
    }

    /** Whether the tab's mention cue sounds: neither muted nor silenced. */
    public static synchronized boolean isPingAudible(ChatTab tab) {
        return tab != null && !MUTED.contains(tab)
                && !PINGS_SILENCED.contains(tab);
    }

    /** Ping-silenced plain tabs in a stable order, for the store. */
    public static synchronized List<ChatTab> pingSilencedTabs() {
        return plainTabsOf(PINGS_SILENCED);
    }

    /** The cue half of a mute: the lines still show in the feed. */
    public static synchronized void setPingSilenced(ChatTab tab,
                                                    boolean silenced) {
        setPreference(PINGS_SILENCED, tab, silenced);
    }

    private static void setPreference(Set<ChatTab> set, ChatTab tab,
                                      boolean on) {
        if (tab == null) {
            return;
        }
        boolean changed = on ? set.add(tab) : set.remove(tab);
        if (changed) {
            changed();
        }
    }

    private static List<ChatTab> plainTabsOf(Set<ChatTab> set) {
        List<ChatTab> result = new ArrayList<ChatTab>();
        for (ChatTab tab : set) {
            if (!tab.isWhisper()) {
                result.add(tab);
            }
        }
        Collections.sort(result, new Comparator<ChatTab>() {
            @Override
            public int compare(ChatTab a, ChatTab b) {
                return a.id().compareTo(b.id());
            }
        });
        return result;
    }

    /** Open tabs across every window; never below one. */
    public static synchronized int openTabCount() {
        int count = 0;
        for (int index = 0; index < WINDOWS.size(); index++) {
            count += WINDOWS.get(index).tabs().size();
        }
        return count;
    }

    /**
     * Whether {@link #close} would remove the tab: it is open and not
     * the last open tab of all. The lock guards movement, not this.
     */
    public static synchronized boolean isClosable(ChatTab tab) {
        return isOpen(tab) && openTabCount() > 1;
    }

    public static synchronized boolean isClosable(ChatChannel channel) {
        return isClosable(ChatTab.of(channel));
    }

    /**
     * Removes the tab from its window; a window emptied this way is
     * dropped. The last open tab of all is refused, so the layout never
     * reaches zero open tabs. Muting is untouched: a closed tab keeps
     * its setting for when it is restored.
     */
    public static synchronized boolean close(ChatTab tab) {
        if (!isClosable(tab)) {
            return false;
        }
        removeTab(windowOf(tab), tab);
        changed();
        return true;
    }

    public static synchronized boolean close(ChatChannel channel) {
        return close(ChatTab.of(channel));
    }

    /** Reopens a closed channel as the first window's last tab. */
    public static synchronized boolean restore(ChatChannel channel) {
        return restore(channel, firstWindow().getId());
    }

    /** Reopens a closed channel as the given window's last tab. */
    public static synchronized boolean restore(ChatChannel channel,
                                               String windowId) {
        ChatWindow window = window(windowId);
        ChatTab tab = ChatTab.of(channel);
        if (tab == null || channel == ChatChannel.WHISPER || isOpen(tab)
                || window == null || window.isLocked()) {
            return false;
        }
        window.tabs().add(tab);
        window.setActiveTab(tab);
        changed();
        return true;
    }

    /**
     * The whisper tab with the named account, opened if it is not: it
     * joins the preferred window, or the first unlocked one. The front
     * tab is left alone, so an arriving whisper does not steal the row.
     */
    public static synchronized ChatTab openWhisper(String partner,
                                                   String preferredWindowId) {
        return openTab(ChatTab.whisper(partner), preferredWindowId);
    }

    /** Opens a conversation tab (a player's or an NPC's) the same way. */
    public static synchronized ChatTab openTab(ChatTab tab,
                                               String preferredWindowId) {
        if (tab == null) {
            return null;
        }
        ChatWindow existing = windowOf(tab);
        if (existing != null) {
            // The tab as first opened, with the name's original casing.
            return existing.getTabs().get(existing.getTabs().indexOf(tab));
        }
        ChatWindow window = window(preferredWindowId);
        if (window == null || window.isLocked()) {
            window = null;
            for (int index = 0; index < WINDOWS.size() && window == null;
                 index++) {
                if (!WINDOWS.get(index).isLocked()) {
                    window = WINDOWS.get(index);
                }
            }
        }
        if (window == null) {
            window = firstWindow();
        }
        window.tabs().add(tab);
        if (window.getActiveTab() == null) {
            window.setActiveTab(tab);
        }
        changed();
        return tab;
    }

    /**
     * Moves a tab to {@code index} of the target window (clamped), which
     * may be its own window — a reorder — or another one — a dock. A
     * locked source or target refuses. A source emptied by the move
     * disappears.
     */
    public static synchronized boolean moveTab(ChatTab tab,
                                               String targetWindowId,
                                               int index) {
        ChatWindow source = windowOf(tab);
        ChatWindow target = window(targetWindowId);
        if (source == null || target == null || source.isLocked()
                || target.isLocked()) {
            return false;
        }
        if (source == target) {
            List<ChatTab> tabs = source.tabs();
            int from = tabs.indexOf(tab);
            int to = Math.max(0, Math.min(tabs.size() - 1, index));
            if (from == to) {
                return false;
            }
            tabs.remove(from);
            tabs.add(to, tab);
            changed();
            return true;
        }
        removeTab(source, tab);
        int to = Math.max(0, Math.min(target.tabs().size(), index));
        target.tabs().add(to, tab);
        target.setActiveTab(tab);
        changed();
        return true;
    }

    public static synchronized boolean moveTab(ChatChannel channel,
                                               String targetWindowId,
                                               int index) {
        return moveTab(ChatTab.of(channel), targetWindowId, index);
    }

    /**
     * Takes the tab out of its window into a new window at the given
     * percent position. A window's only tab dragged out just moves that
     * window. Refused for a locked source and once {@link #MAX_WINDOWS}
     * exist.
     */
    public static synchronized ChatWindow detach(ChatTab tab,
                                                 double offsetX,
                                                 double offsetY) {
        ChatWindow source = windowOf(tab);
        if (source == null || source.isLocked()) {
            return null;
        }
        if (source.tabs().size() == 1) {
            source.setOffsets(clampPercent(offsetX), clampPercent(offsetY));
            changed();
            return source;
        }
        if (WINDOWS.size() >= MAX_WINDOWS) {
            return null;
        }
        removeTab(source, tab);
        ChatWindow window = newWindow();
        window.tabs().add(tab);
        window.setActiveTab(tab);
        window.setOffsets(clampPercent(offsetX), clampPercent(offsetY));
        WINDOWS.add(window);
        changed();
        return window;
    }

    public static synchronized ChatWindow detach(ChatChannel channel,
                                                 double offsetX,
                                                 double offsetY) {
        return detach(ChatTab.of(channel), offsetX, offsetY);
    }

    public static synchronized boolean setLocked(String windowId,
                                                 boolean locked) {
        ChatWindow window = window(windowId);
        if (window == null || window.isLocked() == locked) {
            return false;
        }
        window.setLocked(locked);
        changed();
        return true;
    }

    /** Brings a tab to the front of its own window; not a layout change. */
    public static synchronized boolean setActiveTab(ChatTab tab) {
        ChatWindow window = windowOf(tab);
        if (window == null || tab.equals(window.getActiveTab())) {
            return false;
        }
        window.setActiveTab(tab);
        changed();
        return true;
    }

    public static synchronized boolean setActiveTab(ChatChannel channel) {
        return setActiveTab(ChatTab.of(channel));
    }

    /**
     * Positions a window. {@code persist} is false while a drag is in
     * progress so the file is written once, on release.
     */
    public static synchronized boolean setPosition(String windowId,
                                                   double offsetX,
                                                   double offsetY,
                                                   boolean persist) {
        ChatWindow window = window(windowId);
        if (window == null) {
            return false;
        }
        window.setOffsets(clampPercent(offsetX), clampPercent(offsetY));
        if (persist) {
            changed();
        }
        return true;
    }

    /**
     * Links a window to another it sits directly above or below. A
     * window linked the other way round to this one lets go first, so
     * two windows never hold each other.
     */
    public static synchronized boolean link(String windowId, String targetId,
                                            boolean above) {
        ChatWindow window = window(windowId);
        ChatWindow target = window(targetId);
        if (window == null || target == null || window == target) {
            return false;
        }
        if (windowId.equals(target.getLinkTarget())) {
            target.setLink(null, false);
        }
        window.setLink(targetId, above);
        changed();
        return true;
    }

    public static synchronized boolean unlink(String windowId) {
        ChatWindow window = window(windowId);
        if (window == null || !window.isLinked()) {
            return false;
        }
        window.setLink(null, false);
        changed();
        return true;
    }

    /** Windows linked to the given one, which follow it when it moves. */
    public static synchronized List<ChatWindow> linkedTo(String windowId) {
        List<ChatWindow> result = new ArrayList<ChatWindow>();
        for (int index = 0; index < WINDOWS.size(); index++) {
            if (windowId != null
                    && windowId.equals(WINDOWS.get(index).getLinkTarget())) {
                result.add(WINDOWS.get(index));
            }
        }
        return result;
    }

    /** Writes the current state through the listener, if any. */
    public static synchronized void persist() {
        changed();
    }

    /**
     * Closes every whisper and NPC tab: a conversation ends with the
     * session it was held in, and so does its tab. A window left empty
     * goes; the very last window keeps the console instead.
     */
    public static synchronized void closeConversations() {
        boolean changed = false;
        Iterator<ChatWindow> iterator = WINDOWS.iterator();
        while (iterator.hasNext()) {
            ChatWindow window = iterator.next();
            Iterator<ChatTab> tabs = window.tabs().iterator();
            while (tabs.hasNext()) {
                ChatTab tab = tabs.next();
                if (tab.isWhisper()) {
                    tabs.remove();
                    MUTED.remove(tab);
                    FEED_HIDDEN.remove(tab);
                    PINGS_SILENCED.remove(tab);
                    changed = true;
                }
            }
            if (window.tabs().isEmpty()) {
                if (WINDOWS.size() > 1) {
                    iterator.remove();
                    for (ChatWindow other : WINDOWS) {
                        if (window.getId().equals(other.getLinkTarget())) {
                            other.setLink(null, false);
                        }
                    }
                } else {
                    window.tabs().add(ChatTab.of(ChatChannel.CONSOLE));
                }
            }
            if (window.getActiveTab() == null
                    || !window.tabs().contains(window.getActiveTab())) {
                window.setActiveTab(null);
            }
        }
        if (changed) {
            changed();
        }
    }

    /**
     * Rebuilds the layout from a loaded description, recovering from
     * anything stale: unknown tabs and duplicate windows are ignored, a
     * tab listed twice keeps its first place, empty windows and windows
     * past the cap are dropped, percents are clamped, and every plain
     * channel that is neither placed nor listed as closed is appended to
     * the first window so a channel added after the file was written is
     * never silently lost. A file from before every window was equal
     * names one {@code main}; it becomes an ordinary window. With nothing
     * usable, the default layout is used. The listener is not notified;
     * the caller decides whether a repaired layout is written back.
     */
    static synchronized void load(List<WindowSpec> specs,
                                  Collection<ChatChannel> closed,
                                  Collection<?> muted,
                                  double feedX, double feedY) {
        load(specs, closed, muted, feedX, feedY, false);
    }

    static synchronized void load(List<WindowSpec> specs,
                                  Collection<ChatChannel> closed,
                                  Collection<?> muted,
                                  double feedX, double feedY,
                                  boolean collapsedToolbar) {
        load(specs, closed, muted, null, null, feedX, feedY,
                collapsedToolbar);
    }

    static synchronized void load(List<WindowSpec> specs,
                                  Collection<ChatChannel> closed,
                                  Collection<?> muted,
                                  Collection<?> feedHidden,
                                  Collection<?> pingSilenced,
                                  double feedX, double feedY,
                                  boolean collapsedToolbar) {
        WINDOWS.clear();
        MUTED.clear();
        FEED_HIDDEN.clear();
        PINGS_SILENCED.clear();
        STACK.clear();
        toolbarCollapsed = collapsedToolbar;
        feedOffsetX = clampPercent(feedX);
        feedOffsetY = clampPercent(feedY);
        Set<ChatTab> placed = new HashSet<ChatTab>();
        int highestNumber = 0;
        if (specs != null) {
            for (WindowSpec spec : specs) {
                if (spec != null) {
                    highestNumber = Math.max(highestNumber,
                            windowNumber(spec.id));
                }
            }
        }
        nextWindowNumber = highestNumber + 1;
        if (specs != null) {
            for (WindowSpec spec : specs) {
                if (spec == null || WINDOWS.size() >= MAX_WINDOWS) {
                    continue;
                }
                String id = spec.id;
                if (LEGACY_MAIN_ID.equals(id)) {
                    id = ID_PREFIX + nextWindowNumber++;
                } else if (!isWindowId(id) || window(id) != null) {
                    continue;
                }
                ChatWindow window = new ChatWindow(id);
                for (ChatTab tab : spec.tabs) {
                    // Conversations are not layout: a whisper tab from an
                    // older file is dropped.
                    if (tab != null && !tab.isWhisper() && placed.add(tab)) {
                        window.tabs().add(tab);
                    }
                }
                if (window.tabs().isEmpty()) {
                    continue;
                }
                window.setOffsets(clampPercent(spec.offsetX),
                        clampPercent(spec.offsetY));
                window.setLocked(spec.locked);
                window.setActiveTab(spec.activeTab);
                WINDOWS.add(window);
                if (spec.linkTarget != null) {
                    window.setLink(LEGACY_MAIN_ID.equals(spec.linkTarget)
                            ? null : spec.linkTarget, spec.linkAbove);
                }
            }
        }
        // A link needs its target; two windows never hold each other.
        for (ChatWindow window : WINDOWS) {
            ChatWindow target = window.isLinked()
                    ? window(window.getLinkTarget()) : null;
            if (target == null || target == window) {
                window.setLink(null, false);
            } else if (window.getId().equals(target.getLinkTarget())) {
                target.setLink(null, false);
            }
        }
        List<ChatTab> unplaced = new ArrayList<ChatTab>();
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            ChatTab tab = ChatTab.of(channel);
            if (!placed.contains(tab)
                    && (closed == null || !closed.contains(channel))) {
                unplaced.add(tab);
            }
        }
        if (WINDOWS.isEmpty()) {
            if (unplaced.isEmpty()) {
                // Every channel closed is not a usable layout.
                reset();
            } else {
                ChatWindow window = newWindow();
                window.tabs().addAll(unplaced);
                ChatTab global = ChatTab.of(ChatChannel.ALL);
                window.setActiveTab(unplaced.contains(global)
                        ? global : unplaced.get(0));
                window.setOffsets(0.0D, 100.0D);
                WINDOWS.add(window);
            }
        } else if (!unplaced.isEmpty()) {
            firstWindow().tabs().addAll(unplaced);
        }
        addPreferences(MUTED, muted);
        addPreferences(FEED_HIDDEN, feedHidden);
        addPreferences(PINGS_SILENCED, pingSilenced);
    }

    private static void addPreferences(Set<ChatTab> set,
                                       Collection<?> values) {
        if (values == null) {
            return;
        }
        for (Object value : values) {
            ChatTab tab = value instanceof ChatTab ? (ChatTab)value
                    : value instanceof ChatChannel
                            ? ChatTab.of((ChatChannel)value) : null;
            if (tab != null) {
                set.add(tab);
            }
        }
    }

    /**
     * A serialisable description of the current layout. Whisper and NPC
     * tabs are left out: conversations end with the session, and a
     * window holding nothing else is not described at all.
     */
    static synchronized List<WindowSpec> describe() {
        List<WindowSpec> result = new ArrayList<WindowSpec>(WINDOWS.size());
        for (ChatWindow window : WINDOWS) {
            List<ChatTab> tabs = new ArrayList<ChatTab>();
            for (ChatTab tab : window.tabs()) {
                if (!tab.isWhisper()) {
                    tabs.add(tab);
                }
            }
            if (tabs.isEmpty()) {
                continue;
            }
            ChatTab active = window.getActiveTab();
            result.add(new WindowSpec(window.getId(), tabs,
                    active != null && active.isWhisper() ? null : active,
                    window.isLocked(), window.getOffsetX(),
                    window.getOffsetY(), window.getLinkTarget(),
                    window.isLinkedAbove()));
        }
        return result;
    }

    private static ChatWindow newWindow() {
        return new ChatWindow(ID_PREFIX + nextWindowNumber++);
    }

    private static void removeTab(ChatWindow window, ChatTab tab) {
        window.tabs().remove(tab);
        if (window.tabs().isEmpty()) {
            Iterator<ChatWindow> iterator = WINDOWS.iterator();
            while (iterator.hasNext()) {
                ChatWindow other = iterator.next();
                if (other == window) {
                    iterator.remove();
                } else if (window.getId().equals(other.getLinkTarget())) {
                    other.setLink(null, false);
                }
            }
            return;
        }
        if (tab.equals(window.getActiveTab())) {
            window.setActiveTab(null);
        }
    }

    private static void changed() {
        Runnable listener = changeListener;
        if (listener != null) {
            listener.run();
        }
    }

    static boolean isWindowId(String id) {
        return windowNumber(id) > 0;
    }

    private static int windowNumber(String id) {
        if (id == null || !id.startsWith(ID_PREFIX)
                || id.length() <= ID_PREFIX.length()
                || id.length() > ID_PREFIX.length() + 6) {
            return -1;
        }
        try {
            int number = Integer.parseInt(id.substring(ID_PREFIX.length()));
            return number > 0 ? number : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static double clampPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(100.0D, value));
    }

    /**
     * Plain description of one window, used by load and describe. Tabs
     * may be given as {@link ChatTab}s or as plain {@link ChatChannel}s.
     */
    static final class WindowSpec {
        final String id;
        final List<ChatTab> tabs;
        final ChatTab activeTab;
        final boolean locked;
        final double offsetX;
        final double offsetY;
        final String linkTarget;
        final boolean linkAbove;

        WindowSpec(String id, List<?> tabs, Object activeTab,
                   boolean locked, double offsetX, double offsetY) {
            this(id, tabs, activeTab, locked, offsetX, offsetY, null, false);
        }

        WindowSpec(String id, List<?> tabs, Object activeTab,
                   boolean locked, double offsetX, double offsetY,
                   String linkTarget, boolean linkAbove) {
            this.id = id;
            List<ChatTab> converted = new ArrayList<ChatTab>();
            if (tabs != null) {
                for (Object tab : tabs) {
                    ChatTab value = toTab(tab);
                    if (value != null) {
                        converted.add(value);
                    }
                }
            }
            this.tabs = converted;
            this.activeTab = toTab(activeTab);
            this.locked = locked;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.linkTarget = linkTarget;
            this.linkAbove = linkAbove;
        }

        private static ChatTab toTab(Object value) {
            if (value instanceof ChatTab) {
                return (ChatTab)value;
            }
            if (value instanceof ChatChannel) {
                return ChatTab.of((ChatChannel)value);
            }
            return null;
        }
    }
}
