package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The one authoritative client chat layout: which windows exist, which
 * tabs each holds in what order, which tab is in front, whether a window
 * is locked, where each window sits, and each tab's three independent
 * preferences: <em>muted</em> (its lines stay out of the closed-chat
 * feed), <em>mentions muted</em> (its mention cue is silent), and
 * <em>hidden</em> (once closed it stays closed when a message arrives).
 * A tab is a channel, or a whisper conversation with one account; a tab
 * lives in at most one window, and a plain channel in no window is
 * <em>closed</em> — gone from every window, its history untouched, and
 * restorable into any window. A closed tab reopens with its next message
 * unless it is hidden; the presentation asks {@link #isHidden} before
 * reopening. Closing is about tabs only: a closed channel keeps
 * receiving, keeps its history and unread counts, and keeps its
 * preferences. Every
 * window is equal: one that loses its last tab disappears, and the
 * layout with no windows left at all is a valid state — the chat is
 * simply not shown, every channel keeps receiving, and the screen's
 * empty state offers them back. Nothing is ever kept open to stand in
 * for one. The default layout is a console window (Console,
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
 * {@link ClientChatChannelState}. The in-game chat screen and the
 * closed-chat overlay both work from this one model. Windows are moved
 * in the chat screen alone; the HUD placement editor sets only the feed
 * position.
 * Every mutation is reported to the registered listener, which the
 * file-backed store uses to persist the layout; position changes made
 * while dragging are reported only when the caller asks for it.</p>
 */
public final class ChatWindowLayout {
    /** Bound on windows; more than this is a broken file, not a layout. */
    public static final int MAX_WINDOWS = 8;
    /** Fewest message lines a window may be resized to. */
    public static final int MIN_WINDOW_LINES = 1;
    /**
     * The height a window is given when its size is reset: whole lines,
     * so the topmost row is never a clipped one, and enough of them to
     * read a conversation back without scrolling. The width is not reset
     * to a number of its own; a window without one follows the game's
     * chat-width setting, which is the player's own answer to how wide
     * the chat should be.
     */
    public static final int DEFAULT_WINDOW_LINES = 10;
    /** Most message lines a window may be resized to. */
    public static final int MAX_WINDOW_LINES = 64;
    /**
     * Absolute floor on a stored width, in GUI pixels: vanilla's own
     * narrowest chat, so a hand-edited file cannot leave a width no
     * message could be laid out in. What a drag may actually reach is
     * {@code ChatWindowPlacement}'s readable minimum, which knows the
     * chat scale the text is drawn at.
     */
    public static final int MIN_CHAT_WIDTH = 40;
    /** Widest a window may be dragged; no screen is anywhere near this. */
    public static final int MAX_CHAT_WIDTH = 4096;
    /**
     * The screen a window is cascaded on when there is no client to
     * measure one: 854 by 480 at GUI scale two, the smallest window the
     * game opens at. Only ever used off the client.
     */
    private static final int HEADLESS_SCREEN_WIDTH = 427;
    private static final int HEADLESS_SCREEN_HEIGHT = 240;
    private static final String ID_PREFIX = "w";
    private static final List<ChatTab> CONSOLE_WINDOW_TABS =
            Collections.unmodifiableList(Arrays.asList(
                    ChatTab.of(ChatChannel.CONSOLE),
                    ChatTab.of(ChatChannel.ADMIN)));

    private static final List<ChatWindow> WINDOWS = new ArrayList<ChatWindow>();
    private static final List<ChatWindow> WINDOWS_VIEW =
            Collections.unmodifiableList(WINDOWS);
    /**
     * Muted tabs: their lines stay out of the closed-chat feed.
     *
     * <p>This and the two preference sets below hold row entries. A
     * scoped channel's conversations share the one entry the row holds,
     * so muting the Faction tab mutes the channel rather than whichever
     * faction happened to be on screen. Every accessor normalises
     * through {@link ChatTab#row}, so a caller holding a line's own tab
     * asks the same question.</p>
     */
    private static final Set<ChatTab> MUTED = new HashSet<ChatTab>();
    /** Tabs whose mention cue is silent; they still show in the feed. */
    private static final Set<ChatTab> PINGS_MUTED = new HashSet<ChatTab>();
    /**
     * Tabs a message may not reopen; they stay closed until restored. A
     * whisper tab here was closed by hand, and is closed only until
     * somebody speaks in it again: a replay may not bring it back, a
     * live line does.
     */
    private static final Set<ChatTab> HIDDEN = new HashSet<ChatTab>();
    /**
     * Whisper tabs remembered per place, by the session's server key:
     * where each was open, in order, and which were closed by hand. A
     * conversation belongs to the server it was held on, so arriving
     * elsewhere leaves it in the file for the next visit.
     */
    private static final Map<String, List<String[]>> CONVERSATIONS =
            new LinkedHashMap<String, List<String[]>>();
    private static final Map<String, Set<String>> CLOSED_CONVERSATIONS =
            new LinkedHashMap<String, Set<String>>();
    /** The place whose whisper tabs are on screen; empty before a join. */
    private static String conversationsPlace = "";
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
        PINGS_MUTED.clear();
        HIDDEN.clear();
        STACK.clear();
        CONVERSATIONS.clear();
        CLOSED_CONVERSATIONS.clear();
        conversationsPlace = "";
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

    /**
     * Gives a window its own width, in the chat's own pixels, or 0 to
     * follow the game's chat-width setting again. {@code persist} is
     * false while a resize is in progress so the file is written once,
     * on release. Widths are per window: the closed-chat feed and any
     * window without one of its own keep the game's.
     */
    public static synchronized boolean setWindowWidth(String windowId,
                                                      int width,
                                                      boolean persist) {
        ChatWindow window = window(windowId);
        if (window == null) {
            return false;
        }
        window.setWidth(clampChatWidth(width));
        if (persist) {
            changed();
        }
        return true;
    }

    /** A width inside the bounds a layout may hold; 0 stays 0. */
    static int clampChatWidth(int width) {
        if (width <= 0) {
            return 0;
        }
        return Math.max(MIN_CHAT_WIDTH, Math.min(MAX_CHAT_WIDTH, width));
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

    /** Windows in order, empty when every one has been closed. */
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

    /**
     * Sends a window behind every other one, the reverse of
     * {@link #raise}. The whole order is written down first: windows
     * that were never raised sit at the back in layout order, so a
     * window pushed under them has to be listed with them to really be
     * behind them.
     */
    public static synchronized void lower(String windowId) {
        if (window(windowId) == null) {
            return;
        }
        List<ChatWindow> order = stacked();
        STACK.clear();
        for (int index = 0; index < order.size(); index++) {
            String id = order.get(index).getId();
            if (!id.equals(windowId)) {
                STACK.add(id);
            }
        }
        STACK.add(0, windowId);
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

    /** Whether no window is left; a valid state, not an error. */
    public static synchronized boolean isEmpty() {
        return WINDOWS.isEmpty();
    }

    /** The first window, or null once every one has been closed. */
    public static synchronized ChatWindow firstWindow() {
        return isEmpty() ? null : WINDOWS.get(0);
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
        return windowOf(ChatTab.row(tab)) != null;
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
        return tab != null && MUTED.contains(ChatTab.row(tab));
    }

    public static synchronized boolean isMuted(ChatChannel channel) {
        return isMuted(ChatTab.of(channel));
    }

    /** Muted plain tabs in a stable order, for the store. */
    public static synchronized List<ChatTab> mutedTabs() {
        return plainTabsOf(MUTED);
    }

    /**
     * Muting a channel keeps its new lines out of the closed-chat feed,
     * and nothing else: the mention cue is its own preference, the tab
     * still shows everything when selected, and the history is
     * untouched.
     */
    public static synchronized void setMuted(ChatTab tab, boolean muted) {
        setPreference(MUTED, tab, muted);
    }

    public static synchronized void setMuted(ChatChannel channel,
                                            boolean muted) {
        setMuted(ChatTab.of(channel), muted);
    }

    /** Whether the tab's lines are shown in the feed: not muted. */
    public static synchronized boolean isInFeed(ChatTab tab) {
        return tab != null && !MUTED.contains(tab);
    }

    /** Whether the tab's mention cue is muted. */
    public static synchronized boolean isPingsMuted(ChatTab tab) {
        return tab != null && PINGS_MUTED.contains(tab);
    }

    public static synchronized boolean isPingsMuted(ChatChannel channel) {
        return isPingsMuted(ChatTab.of(channel));
    }

    /** Whether the tab's mention cue sounds: its mentions are not muted. */
    public static synchronized boolean isPingAudible(ChatTab tab) {
        return tab != null && !PINGS_MUTED.contains(ChatTab.row(tab));
    }

    /** Mention-muted plain tabs in a stable order, for the store. */
    public static synchronized List<ChatTab> pingsMutedTabs() {
        return plainTabsOf(PINGS_MUTED);
    }

    /**
     * Muting a channel's mentions silences its cue and nothing else:
     * the lines still show in the feed, and mentions still count and
     * highlight.
     */
    public static synchronized void setPingsMuted(ChatTab tab,
                                                  boolean muted) {
        setPreference(PINGS_MUTED, tab, muted);
    }

    /** Whether a message may not reopen the tab while it is closed. */
    public static synchronized boolean isHidden(ChatTab tab) {
        return tab != null && HIDDEN.contains(ChatTab.row(tab));
    }

    public static synchronized boolean isHidden(ChatChannel channel) {
        return isHidden(ChatTab.of(channel));
    }

    /** Hidden plain tabs in a stable order, for the store. */
    public static synchronized List<ChatTab> hiddenTabs() {
        return plainTabsOf(HIDDEN);
    }

    /**
     * Whether an arriving message may reopen the tab once it is closed:
     * a closed tab reopens on its next message unless it is hidden.
     * Hiding an open tab closes nothing and mutes nothing — it only
     * takes effect once the tab is closed, and the channel keeps
     * receiving, keeps its history and its unread counts either way.
     */
    public static synchronized void setHidden(ChatTab tab, boolean hidden) {
        setPreference(HIDDEN, tab, hidden);
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

    /** Open tabs across every window; zero once they are all closed. */
    public static synchronized int openTabCount() {
        int count = 0;
        for (int index = 0; index < WINDOWS.size(); index++) {
            count += WINDOWS.get(index).tabs().size();
        }
        return count;
    }

    /**
     * Whether {@link #close} would remove the tab: it is open and its
     * window is unlocked. A locked window keeps the tabs it has — that
     * is what locking it is for — so no cross is offered on its row and
     * no shortcut closes one either. Nothing else is refused: the last
     * tab of the last window closes like any other.
     */
    public static synchronized boolean isClosable(ChatTab tab) {
        ChatWindow window = windowOf(tab);
        return window != null && !window.isLocked();
    }

    public static synchronized boolean isClosable(ChatChannel channel) {
        return isClosable(ChatTab.of(channel));
    }

    /**
     * Removes the tab from its window; a window emptied this way is
     * dropped, and the layout may end up with no windows at all.
     * Muting is untouched: a closed tab keeps its setting for when it
     * is restored.
     */
    public static synchronized boolean close(ChatTab tab) {
        if (!isClosable(tab)) {
            return false;
        }
        removeTab(windowOf(tab), tab);
        if (isRemembered(tab)) {
            // Closed by hand: a replay may not reopen it; a live line does.
            HIDDEN.add(ChatTab.row(tab));
        }
        changed();
        return true;
    }

    public static synchronized boolean close(ChatChannel channel) {
        return close(ChatTab.of(channel));
    }

    /**
     * Closes a whole window: every tab it holds leaves it and the window
     * itself goes, windows stuck to it letting go. The channels behind
     * the tabs are untouched — they keep receiving, keep their history
     * and keep their preferences — and closing the last window is
     * allowed. A locked window is refused, as its individual tabs are.
     */
    public static synchronized boolean closeWindow(String windowId) {
        ChatWindow window = window(windowId);
        if (window == null || window.isLocked()) {
            return false;
        }
        window.tabs().clear();
        window.setActiveTab(null);
        dropWindow(window);
        changed();
        return true;
    }

    /**
     * Reopens a closed channel as the last tab of a window that will
     * have it: an unlocked one, or a new one when every window is
     * locked.
     */
    public static synchronized boolean restore(ChatChannel channel) {
        ChatTab tab = ChatTab.of(channel);
        // Asked before a window is chosen: picking one may create it,
        // and a refused restore must not leave an empty window behind.
        if (!isRestorable(tab)) {
            return false;
        }
        ChatWindow window = receivingWindow(null, tab);
        return window != null && restore(channel, window.getId());
    }

    /**
     * Whether a closed channel's tab may be reopened at all: it must
     * be a plain channel's — a whisper has no tab to restore, its
     * conversations reopen with their next line — and not open already.
     */
    private static boolean isRestorable(ChatTab tab) {
        return tab != null && !tab.isWhisper() && !isOpen(tab);
    }

    /**
     * The window a tab that opens by itself — a conversation, the
     * console answering a command — belongs in. The window it is asked
     * for takes it when that window is unlocked and has room for it;
     * otherwise the most recently used window that does, front of the
     * stack first; then, while the layout has room for another, a new
     * window cascaded from the one asked for, or from the front window.
     * With no room for another, the front-most unlocked window takes it
     * anyway, since losing the message would be worse than crowding a
     * row, and a layout of locked windows alone hands it to the first.
     *
     * <p>Null once every window has been closed: a message never opens
     * the chat back up by itself. Its channel still receives it, counts
     * it unread and shows it in the closed-chat feed; the player decides
     * when a window comes back.</p>
     */
    static synchronized ChatWindow receivingWindow(ChatWindow preferred,
                                                   ChatTab tab) {
        if (isEmpty()) {
            return null;
        }
        List<ChatTab> candidate = Collections.singletonList(tab);
        if (preferred != null && WINDOWS.contains(preferred)
                && !preferred.isLocked() && hasRoomFor(preferred, candidate)) {
            return preferred;
        }
        // A window whose row cannot hold one more tab at its least — the
        // widest tab whole, every other down to its icon — is full: the
        // window last brought to the front with room takes the tab, and
        // when every unlocked window is full a new window opens instead.
        List<ChatWindow> byRecency = byRecency();
        for (int index = 0; index < byRecency.size(); index++) {
            ChatWindow window = byRecency.get(index);
            if (!window.isLocked() && hasRoomFor(window, candidate)) {
                return window;
            }
        }
        if (WINDOWS.size() < MAX_WINDOWS) {
            ChatWindow created = newWindow();
            cascadeFrom(created, preferred != null ? preferred : frontWindow());
            WINDOWS.add(created);
            // A window that has just opened stands in front of the rest.
            raise(created.getId());
            return created;
        }
        for (int index = 0; index < byRecency.size(); index++) {
            if (!byRecency.get(index).isLocked()) {
                return byRecency.get(index);
            }
        }
        return firstWindow();
    }

    /**
     * Windows most recently brought to the front first, then the ones
     * never raised in layout order: the order a tab that opens by
     * itself asks them in.
     */
    private static List<ChatWindow> byRecency() {
        List<ChatWindow> result = new ArrayList<ChatWindow>(WINDOWS.size());
        for (int index = STACK.size() - 1; index >= 0; index--) {
            ChatWindow window = window(STACK.get(index));
            if (window != null) {
                result.add(window);
            }
        }
        for (int index = 0; index < WINDOWS.size(); index++) {
            if (!STACK.contains(WINDOWS.get(index).getId())) {
                result.add(WINDOWS.get(index));
            }
        }
        return result;
    }

    /**
     * Whether the window's row has room for these tabs besides the ones
     * it shows. The width model is the tab bar's; without a renderer to
     * measure with — headless tests, a broken frame — the answer is yes.
     */
    private static boolean hasRoomFor(ChatWindow window, List<ChatTab> tabs) {
        try {
            return ChatChannelTabBar.rowHasRoomFor(
                    net.minecraft.client.Minecraft.getMinecraft(), window,
                    tabs);
        } catch (RuntimeException unavailable) {
            return true;
        } catch (LinkageError unavailable) {
            return true;
        }
    }

    /** Reopens a closed channel as the given window's last tab. */
    public static synchronized boolean restore(ChatChannel channel,
                                               String windowId) {
        ChatWindow window = window(windowId);
        ChatTab tab = ChatTab.of(channel);
        if (!isRestorable(tab) || window == null || window.isLocked()) {
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
        return openWhisper(partner, "", preferredWindowId);
    }

    /**
     * As above for one identity of that account; empty is its own. The
     * tab is the person's row entry: which conversation it shows follows
     * the identity the chat is read as, which is who speaks in it.
     */
    public static synchronized ChatTab openWhisper(
            String partner, String identity, String preferredWindowId) {
        return openTab(ChatTab.whisper(partner, identity), preferredWindowId);
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
        // The window asked for takes the tab first; a locked or a full
        // one hands it to another with room, or to a new one cascaded
        // from it.
        ChatWindow window = receivingWindow(window(preferredWindowId), tab);
        if (window == null) {
            // No window left to open it in; the tab stays closed and its
            // channel keeps receiving.
            return null;
        }
        window.tabs().add(tab);
        if (window.getActiveTab() == null) {
            window.setActiveTab(tab);
        }
        changed();
        return tab;
    }

    /**
     * Opens a tab in a window of its own, cascaded from the front
     * window: how a channel comes back when no window is left to put it
     * in, and what the screen's empty state offers. Refused for a tab
     * that is already open and once {@link #MAX_WINDOWS} exist.
     */
    public static synchronized ChatTab openInNewWindow(ChatTab tab) {
        if (tab == null || isOpen(tab) || WINDOWS.size() >= MAX_WINDOWS) {
            return null;
        }
        ChatWindow created = newWindow();
        cascadeFrom(created, frontWindow());
        created.tabs().add(tab);
        created.setActiveTab(tab);
        WINDOWS.add(created);
        raise(created.getId());
        changed();
        return tab;
    }

    /**
     * Moves a tab to {@code index} of the target window: the place it
     * ends up at once the move is done, clamped to the row, in a window
     * that may be its own — a reorder — or another one — a dock. A
     * locked source or target refuses. A source emptied by the move
     * disappears.
     */
    public static synchronized boolean moveTab(ChatTab tab,
                                               String targetWindowId,
                                               int index) {
        return moveTabs(Collections.singletonList(tab), targetWindowId,
                index);
    }

    /**
     * Moves a run of tabs to {@code index} of the target window, keeping
     * their relative order — what dragging a group of marked tabs
     * does. The index is the place the run ends up at once the tabs have
     * been lifted out, so a non-contiguous selection lands as one run
     * and a reorder is described by where the tabs go rather than by
     * which neighbour they land beside. Every tab must be open in one
     * and the same source window; a locked source or target refuses, so
     * does a target whose row has no room for the tabs at their least,
     * and a source emptied by the move disappears.
     */
    public static synchronized boolean moveTabs(List<ChatTab> tabs,
                                                String targetWindowId,
                                                int index) {
        return moveTabs(tabs, targetWindowId, index, true);
    }

    /**
     * As above; {@code persist} is false while a drag is in progress, so
     * a tab sliding along its row writes the file once, on release,
     * rather than every time it passes a neighbour.
     */
    public static synchronized boolean moveTabs(List<ChatTab> tabs,
                                                String targetWindowId,
                                                int index, boolean persist) {
        List<ChatTab> moved = sameWindowTabs(tabs);
        ChatWindow target = window(targetWindowId);
        if (moved.isEmpty() || target == null || target.isLocked()) {
            return false;
        }
        ChatWindow source = windowOf(moved.get(0));
        if (source.isLocked()) {
            return false;
        }
        ChatTab active = source.getActiveTab();
        List<ChatTab> list = source.tabs();
        if (source == target) {
            List<ChatTab> reordered = new ArrayList<ChatTab>(list);
            reordered.removeAll(moved);
            reordered.addAll(Math.max(0,
                    Math.min(reordered.size(), index)), moved);
            if (reordered.equals(list)) {
                // The tabs are already where the drop asked for them;
                // nothing moved, so nothing is written.
                return false;
            }
            list.clear();
            list.addAll(reordered);
            source.setActiveTab(active);
            if (persist) {
                changed();
            }
            return true;
        }
        // A dock is refused where the row could not hold the tabs at
        // their least; the drag carries on and the tabs stay where they
        // are, rather than a row showing fewer tabs than it holds.
        if (!hasRoomFor(target, moved)) {
            return false;
        }
        list.removeAll(moved);
        if (list.isEmpty()) {
            dropWindow(source);
        } else if (active != null && moved.contains(active)) {
            source.setActiveTab(null);
        }
        int to = Math.max(0, Math.min(target.tabs().size(), index));
        target.tabs().addAll(to, moved);
        target.setActiveTab(moved.get(moved.size() - 1));
        if (persist) {
            changed();
        }
        return true;
    }

    /**
     * The given tabs in their window's own row order, or empty when any
     * of them is closed or they do not all live in one window. The order
     * is the window's, never the caller's, so a group keeps the order it
     * was shown in however it came to be selected.
     */
    private static List<ChatTab> sameWindowTabs(List<ChatTab> tabs) {
        List<ChatTab> result = new ArrayList<ChatTab>();
        if (tabs == null || tabs.isEmpty()) {
            return result;
        }
        ChatWindow window = windowOf(tabs.get(0));
        if (window == null) {
            return result;
        }
        for (ChatTab tab : window.tabs()) {
            if (tabs.contains(tab)) {
                result.add(tab);
            }
        }
        return result.size() == new HashSet<ChatTab>(tabs).size()
                ? result : new ArrayList<ChatTab>();
    }

    public static synchronized boolean moveTab(ChatChannel channel,
                                               String targetWindowId,
                                               int index) {
        return moveTab(ChatTab.of(channel), targetWindowId, index);
    }

    /**
     * Takes the tab out of its window into a new window at the given
     * percent position, as tall and as wide as the window it came from.
     * A window's only tab dragged out just moves that window. Refused
     * for a locked source and once {@link #MAX_WINDOWS} exist.
     */
    public static synchronized ChatWindow detach(ChatTab tab,
                                                 double offsetX,
                                                 double offsetY) {
        return detach(Collections.singletonList(tab), offsetX, offsetY);
    }

    /** As above for a group of tabs, which keep their relative order. */
    public static synchronized ChatWindow detach(List<ChatTab> tabs,
                                                 double offsetX,
                                                 double offsetY) {
        List<ChatTab> moved = sameWindowTabs(tabs);
        if (moved.isEmpty()) {
            return null;
        }
        ChatWindow source = windowOf(moved.get(0));
        if (source.isLocked()) {
            return null;
        }
        if (source.tabs().size() == moved.size()) {
            // Everything the window held: the window itself moves,
            // rather than an empty one being left behind.
            source.setOffsets(clampWindowPercent(offsetX),
                    clampWindowPercent(offsetY));
            changed();
            return source;
        }
        if (WINDOWS.size() >= MAX_WINDOWS) {
            return null;
        }
        ChatTab active = source.getActiveTab();
        source.tabs().removeAll(moved);
        if (active != null && moved.contains(active)) {
            source.setActiveTab(null);
        }
        ChatWindow window = newWindow();
        // The size the player gave the window the tabs came out of, so
        // a tab taken out of a tall window is read in a window as tall,
        // rather than in whatever the game's own chat settings say.
        window.setMaxLines(source.getMaxLines());
        window.setWidth(source.getWidth());
        window.tabs().addAll(moved);
        window.setActiveTab(moved.get(moved.size() - 1));
        window.setOffsets(clampWindowPercent(offsetX),
                clampWindowPercent(offsetY));
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

    /**
     * Lets a window fill a part of the screen — the whole of it, a half
     * or a quarter — or gives it back its own box: its position, width
     * and height are kept as they were throughout, and are what it
     * returns to. {@code persist} is false while a drag that took a
     * window out of the screen is still moving, so the file is written
     * once, on release.
     */
    public static synchronized boolean setFill(String windowId,
                                               ChatWindow.ScreenFill fill,
                                               boolean persist) {
        ChatWindow window = window(windowId);
        ChatWindow.ScreenFill wanted = fill == null
                ? ChatWindow.ScreenFill.NONE : fill;
        if (window == null || window.getFill() == wanted) {
            return false;
        }
        window.setFill(wanted);
        if (persist) {
            changed();
        }
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
        window.setOffsets(clampWindowPercent(offsetX),
                clampWindowPercent(offsetY));
        if (persist) {
            changed();
        }
        return true;
    }

    /**
     * Gives a window its own height in message lines, or 0 to follow the
     * game's chat-height setting again. The height is fractional: a
     * window keeps the exact size it was dragged to and clips its
     * topmost line rather than snapping to a whole one. {@code persist}
     * is false while a resize is in progress so the file is written
     * once, on release.
     */
    public static synchronized boolean setWindowLines(String windowId,
                                                      double lines,
                                                      boolean persist) {
        ChatWindow window = window(windowId);
        if (window == null) {
            return false;
        }
        window.setMaxLines(clampWindowLines(lines));
        if (persist) {
            changed();
        }
        return true;
    }

    /** A window height inside the bounds a layout may hold; 0 stays 0. */
    static double clampWindowLines(double lines) {
        if (!(lines > 0.0D)) {
            return 0.0D;
        }
        return Math.max(MIN_WINDOW_LINES, Math.min(MAX_WINDOW_LINES, lines));
    }

    /**
     * Links a window to another it sits directly above or below. A
     * window linked the other way round to this one lets go first, so
     * two windows never hold each other.
     */
    public static synchronized boolean link(String windowId, String targetId,
                                            boolean above) {
        return link(windowId, targetId, above
                ? ChatWindow.LinkSide.ABOVE : ChatWindow.LinkSide.BELOW);
    }

    /**
     * Sticks a window to one side of another. A window stuck the other
     * way round to this one lets go first, so two windows never hold
     * each other, and a chain never closes on itself.
     */
    public static synchronized boolean link(String windowId, String targetId,
                                            ChatWindow.LinkSide side) {
        ChatWindow window = window(windowId);
        ChatWindow target = window(targetId);
        if (window == null || target == null || window == target
                || side == null) {
            return false;
        }
        if (windowId.equals(target.getLinkTarget())) {
            target.setLink(null, ChatWindow.LinkSide.BELOW);
        }
        window.setLink(targetId, side);
        changed();
        return true;
    }

    /**
     * Every window stuck to this one, however many hops away and in
     * whichever direction the sticking runs, the window itself included.
     * A stuck group moves as one piece, so a drag carries all of them.
     */
    public static synchronized List<ChatWindow> linkedGroup(
            ChatWindow window) {
        List<ChatWindow> group = new ArrayList<ChatWindow>();
        if (window == null) {
            return group;
        }
        group.add(window);
        for (int pass = 0; pass < MAX_WINDOWS; pass++) {
            boolean grew = false;
            for (ChatWindow candidate : WINDOWS) {
                if (group.contains(candidate)) {
                    continue;
                }
                for (int index = 0; index < group.size(); index++) {
                    ChatWindow member = group.get(index);
                    if (candidate.getId().equals(member.getLinkTarget())
                            || member.getId().equals(
                                    candidate.getLinkTarget())) {
                        group.add(candidate);
                        grew = true;
                        break;
                    }
                }
            }
            if (!grew) {
                break;
            }
        }
        return group;
    }

    /**
     * The window at the head of a stuck chain — the one whose stored
     * position the others are placed from. A window that is stuck to
     * nothing is its own root, and a chain that somehow closed on itself
     * stops short of the window it started from.
     */
    public static synchronized ChatWindow linkRoot(ChatWindow window) {
        ChatWindow root = window;
        for (int step = 0; step < MAX_WINDOWS && root != null
                && root.isLinked(); step++) {
            ChatWindow target = window(root.getLinkTarget());
            if (target == null || target == window) {
                break;
            }
            root = target;
        }
        return root == null ? window : root;
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

    /** Whether a tab is a whisper the layout remembers per place; an NPC's is not. */
    private static boolean isRemembered(ChatTab tab) {
        return tab != null && tab.isWhisper() && !tab.isNpc();
    }

    /**
     * Writes a place's whisper tabs into the remembered set: the open
     * ones with their windows, in order, and the ones closed by hand.
     */
    static synchronized void rememberConversations(String serverKey) {
        if (serverKey == null || serverKey.length() == 0) {
            return;
        }
        List<String[]> open = new ArrayList<String[]>();
        for (ChatWindow window : WINDOWS) {
            for (ChatTab tab : window.getTabs()) {
                if (isRemembered(tab)) {
                    open.add(new String[] {window.getId(), tab.id()});
                }
            }
        }
        Set<String> closed = new LinkedHashSet<String>();
        for (ChatTab tab : HIDDEN) {
            if (isRemembered(tab)) {
                closed.add(tab.id());
            }
        }
        CONVERSATIONS.put(serverKey, open);
        CLOSED_CONVERSATIONS.put(serverKey, closed);
    }

    /**
     * Opens the place's remembered whisper tabs where they were, and
     * marks the ones closed by hand so a replay does not bring them
     * back. Called as the client connects, before anything is replayed;
     * a tab already open is left as it is.
     */
    public static synchronized void restoreConversations(String serverKey) {
        conversationsPlace = serverKey == null ? "" : serverKey;
        if (conversationsPlace.length() == 0) {
            return;
        }
        Set<String> closed = CLOSED_CONVERSATIONS.get(conversationsPlace);
        if (closed != null) {
            for (String id : closed) {
                ChatTab tab = ChatTab.fromId(id);
                if (isRemembered(tab)) {
                    HIDDEN.add(tab);
                }
            }
        }
        List<String[]> open = CONVERSATIONS.get(conversationsPlace);
        if (open != null) {
            for (String[] entry : open) {
                ChatTab tab = ChatTab.fromId(entry[1]);
                if (isRemembered(tab) && !isOpen(tab)) {
                    HIDDEN.remove(tab);
                    openTab(tab, entry[0]);
                }
            }
        }
    }

    /**
     * A live line reopens a whisper tab closed by hand, a player's or an
     * NPC's: it was closed only until somebody spoke in it again. Null
     * for anything else.
     */
    public static synchronized ChatTab reopenConversation(ChatTab row,
                                                          String preferredWindowId) {
        if (row == null || !row.isWhisper()) {
            return null;
        }
        HIDDEN.remove(ChatTab.row(row));
        return openTab(ChatTab.row(row), preferredWindowId);
    }

    /** Every place's remembered whisper tabs, the current place's as they stand; for the store. */
    static synchronized Map<String, List<String[]>> rememberedConversations() {
        rememberConversations(conversationsPlace);
        return new LinkedHashMap<String, List<String[]>>(CONVERSATIONS);
    }

    /** Every place's whisper tabs closed by hand, the current place's as they stand; for the store. */
    static synchronized Map<String, Set<String>> rememberedClosedConversations() {
        rememberConversations(conversationsPlace);
        return new LinkedHashMap<String, Set<String>>(CLOSED_CONVERSATIONS);
    }

    /** Takes the file's remembered whisper tabs; nothing is on screen for any place yet. */
    static synchronized void loadConversations(Map<String, List<String[]>> open,
                                               Map<String, Set<String>> closed) {
        CONVERSATIONS.clear();
        CLOSED_CONVERSATIONS.clear();
        conversationsPlace = "";
        if (open != null) {
            CONVERSATIONS.putAll(open);
        }
        if (closed != null) {
            CLOSED_CONVERSATIONS.putAll(closed);
        }
    }

    /** Writes the current state through the listener, if any. */
    public static synchronized void persist() {
        changed();
    }

    /**
     * Closes every whisper and NPC tab: a conversation ends with the
     * session it was held in, and so does its tab — the place's whisper
     * tabs are remembered first, open and closed by hand alike, and come
     * back on the next visit. A window left empty goes, the last one
     * included.
     */
    public static synchronized void closeConversations() {
        rememberConversations(conversationsPlace);
        Iterator<ChatTab> closedByHand = HIDDEN.iterator();
        while (closedByHand.hasNext()) {
            if (isRemembered(closedByHand.next())) {
                closedByHand.remove();
            }
        }
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
                    PINGS_MUTED.remove(tab);
                    HIDDEN.remove(tab);
                    changed = true;
                }
            }
            if (window.tabs().isEmpty()) {
                iterator.remove();
                for (ChatWindow other : WINDOWS) {
                    if (window.getId().equals(other.getLinkTarget())) {
                        other.setLink(null, false);
                    }
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
     * never silently lost. A file that
     * names no window and closes every channel describes the empty
     * layout and is loaded as one. The listener is not notified;
     * the caller decides whether a repaired layout is written back.
     */
    static synchronized void load(List<WindowSpec> specs,
                                  Collection<ChatChannel> closed,
                                  Collection<?> muted,
                                  double feedX, double feedY) {
        load(specs, closed, muted, null, null, feedX, feedY, false);
    }

    static synchronized void load(List<WindowSpec> specs,
                                  Collection<ChatChannel> closed,
                                  Collection<?> muted,
                                  Collection<?> pingsMuted,
                                  Collection<?> hidden,
                                  double feedX, double feedY,
                                  boolean collapsedToolbar) {
        WINDOWS.clear();
        MUTED.clear();
        PINGS_MUTED.clear();
        HIDDEN.clear();
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
                if (!isWindowId(id) || window(id) != null) {
                    continue;
                }
                ChatWindow window = new ChatWindow(id);
                for (ChatTab tab : spec.tabs) {
                    // Conversations are not layout: a whisper tab, and a
                    // scoped channel's conversation, are both dropped. The
                    // row holds the channel; which conversation it shows
                    // follows the identity being read.
                    if (tab != null && !tab.isWhisper()
                            && tab.getOwnerKey().length() == 0
                            && placed.add(tab)) {
                        window.tabs().add(tab);
                    }
                }
                if (window.tabs().isEmpty()) {
                    continue;
                }
                window.setOffsets(clampWindowPercent(spec.offsetX),
                        clampWindowPercent(spec.offsetY));
                window.setLocked(spec.locked);
                window.setFill(spec.fill);
                window.setMaxLines(clampWindowLines(spec.maxLines));
                window.setWidth(clampChatWidth(spec.width));
                window.setActiveTab(spec.activeTab);
                WINDOWS.add(window);
                if (spec.linkTarget != null) {
                    window.setLink(spec.linkTarget, spec.linkSide);
                }
            }
        }
        // A link needs its target; two windows never hold each other.
        for (ChatWindow window : WINDOWS) {
            ChatWindow target = window.isLinked()
                    ? window(window.getLinkTarget()) : null;
            if (target == null || target == window) {
                window.setLink(null, ChatWindow.LinkSide.BELOW);
            } else if (window.getId().equals(target.getLinkTarget())) {
                target.setLink(null, ChatWindow.LinkSide.BELOW);
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
            if (!unplaced.isEmpty()) {
                // Channels the file placed nowhere and did not close
                // need somewhere to live, so one window opens for them.
                // Everything closed needs no window at all.
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
        addPreferences(PINGS_MUTED, pingsMuted);
        addPreferences(HIDDEN, hidden);
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
     * tabs are left out, and so is a scoped channel's conversation:
     * conversations end with the session, and a window holding nothing
     * else is not described at all.
     */
    static synchronized List<WindowSpec> describe() {
        List<WindowSpec> result = new ArrayList<WindowSpec>(WINDOWS.size());
        for (ChatWindow window : WINDOWS) {
            List<ChatTab> tabs = new ArrayList<ChatTab>();
            for (ChatTab tab : window.tabs()) {
                if (!tab.isWhisper() && tab.getOwnerKey().length() == 0) {
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
                    window.getLinkSide(), window.getMaxLines(),
                    window.getWidth(), window.getFill()));
        }
        return result;
    }

    /** The window last brought to the front, else the first; null with none. */
    private static ChatWindow frontWindow() {
        List<ChatWindow> order = byRecency();
        return order.isEmpty() ? null : order.get(0);
    }

    /**
     * Puts a window the chat opened by itself one cascade step right and
     * down from {@code reference}, at that window's size, so windows the
     * chat opens stack the way desktop windows do and the same layout
     * always gives the same place. With no window to cascade from, the
     * first one lands where the default layout puts its conversation
     * window. Measured in the boxes the windows are drawn in when there
     * is a client to measure with, and otherwise on a screen of a fixed
     * size: the rule is the same either way, only the pixels differ.
     */
    private static void cascadeFrom(ChatWindow created, ChatWindow reference) {
        if (reference == null) {
            created.setOffsets(0.0D, 100.0D);
            return;
        }
        // The size the player gave the window it comes from, as a
        // detached tab's window takes it.
        created.setMaxLines(reference.getMaxLines());
        created.setWidth(reference.getWidth());
        net.minecraft.client.Minecraft minecraft = clientMinecraft();
        int screenWidth = HEADLESS_SCREEN_WIDTH;
        int screenHeight = HEADLESS_SCREEN_HEIGHT;
        if (minecraft != null) {
            try {
                net.minecraft.client.gui.ScaledResolution resolution =
                        new net.minecraft.client.gui.ScaledResolution(minecraft,
                                minecraft.displayWidth, minecraft.displayHeight);
                screenWidth = resolution.getScaledWidth();
                screenHeight = resolution.getScaledHeight();
            } catch (RuntimeException unavailable) {
                minecraft = null;
            }
        }
        // The new window is placed by its tab row: the corner is where
        // the row lands at the size the window opens at, the way a tab
        // torn off a row is placed, so the new window's row stands a step
        // below the reference's whatever height it opens at. A reference
        // filling the screen is measured by the box it goes back to.
        ChatWindowPlacement.Box from = ChatWindowPlacement.restingBounds(
                reference, minecraft, screenWidth, screenHeight);
        int width = ChatWindowPlacement.windowWidth(created, minecraft);
        double height = ChatWindowPlacement.currentHeight(created, minecraft);
        ChatWindowCascade.Corner corner = ChatWindowCascade.place(
                from.x, from.y, width, height, screenWidth,
                screenHeight, ChatWindowPlacement.EDGE_MARGIN,
                ChatWindowCascade.STEP);
        double baseline = ChatWindowPlacement.baselineForRowTop(
                created, minecraft, corner.y);
        created.setOffsets(
                clampWindowPercent(ChatWindowPlacement.windowPercentX(
                        created, corner.x, minecraft, screenWidth)),
                clampWindowPercent(ChatWindowPlacement.windowPercentY(
                        baseline, minecraft, screenHeight)));
    }

    /** The running client, or null headlessly or before it exists. */
    private static net.minecraft.client.Minecraft clientMinecraft() {
        try {
            return net.minecraft.client.Minecraft.getMinecraft();
        } catch (RuntimeException unavailable) {
            return null;
        } catch (LinkageError unavailable) {
            return null;
        }
    }

    private static ChatWindow newWindow() {
        return new ChatWindow(ID_PREFIX + nextWindowNumber++);
    }

    private static void removeTab(ChatWindow window, ChatTab tab) {
        window.tabs().remove(tab);
        if (window.tabs().isEmpty()) {
            dropWindow(window);
            return;
        }
        if (tab.equals(window.getActiveTab())) {
            window.setActiveTab(null);
        }
    }

    /** Takes an emptied window out of the layout; its holders let go. */
    private static void dropWindow(ChatWindow window) {
        Iterator<ChatWindow> iterator = WINDOWS.iterator();
        while (iterator.hasNext()) {
            ChatWindow other = iterator.next();
            if (other == window) {
                iterator.remove();
            } else if (window.getId().equals(other.getLinkTarget())) {
                other.setLink(null, false);
            }
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
     * A window's percent: between the margins as {@link #clampPercent},
     * and past them by up to the window's own size either way, which is
     * how far a window may hang off the screen
     * ({@link ChatWindowPlacement#position}). A safety bound; where a
     * window really stops is the screen's hold on it.
     */
    static double clampWindowPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0D;
        }
        return Math.max(-100.0D, Math.min(200.0D, value));
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
        /** Which side of its target it is stuck to. */
        final ChatWindow.LinkSide linkSide;
        /**
         * The window's own height in lines, fractions included; 0
         * follows the game setting.
         */
        final double maxLines;
        /** The window's own width; 0 follows the game setting. */
        final int width;
        /** The part of the screen the window fills; none in its own box. */
        final ChatWindow.ScreenFill fill;

        WindowSpec(String id, List<?> tabs, Object activeTab,
                   boolean locked, double offsetX, double offsetY) {
            this(id, tabs, activeTab, locked, offsetX, offsetY, null, false);
        }

        WindowSpec(String id, List<?> tabs, Object activeTab,
                   boolean locked, double offsetX, double offsetY,
                   String linkTarget, boolean linkAbove) {
            this(id, tabs, activeTab, locked, offsetX, offsetY, linkTarget,
                    linkAbove, 0);
        }

        WindowSpec(String id, List<?> tabs, Object activeTab,
                   boolean locked, double offsetX, double offsetY,
                   String linkTarget, boolean linkAbove, double maxLines) {
            this(id, tabs, activeTab, locked, offsetX, offsetY, linkTarget,
                    linkAbove, maxLines, 0);
        }

        WindowSpec(String id, List<?> tabs, Object activeTab,
                   boolean locked, double offsetX, double offsetY,
                   String linkTarget, boolean linkAbove, double maxLines,
                   int width) {
            this(id, tabs, activeTab, locked, offsetX, offsetY, linkTarget,
                    linkAbove ? ChatWindow.LinkSide.ABOVE
                            : ChatWindow.LinkSide.BELOW, maxLines, width);
        }

        WindowSpec(String id, List<?> tabs, Object activeTab,
                   boolean locked, double offsetX, double offsetY,
                   String linkTarget, ChatWindow.LinkSide linkSide,
                   double maxLines, int width) {
            this(id, tabs, activeTab, locked, offsetX, offsetY, linkTarget,
                    linkSide, maxLines, width, ChatWindow.ScreenFill.NONE);
        }

        WindowSpec(String id, List<?> tabs, Object activeTab,
                   boolean locked, double offsetX, double offsetY,
                   String linkTarget, ChatWindow.LinkSide linkSide,
                   double maxLines, int width, ChatWindow.ScreenFill fill) {
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
            this.linkSide = linkSide == null
                    ? ChatWindow.LinkSide.BELOW : linkSide;
            this.linkAbove = this.linkSide == ChatWindow.LinkSide.ABOVE;
            this.maxLines = clampWindowLines(maxLines);
            this.width = clampChatWidth(width);
            this.fill = fill == null ? ChatWindow.ScreenFill.NONE : fill;
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
