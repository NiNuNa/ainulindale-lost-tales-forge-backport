package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowLayoutStore;
import com.ninuna.losttales.client.window.WindowPlacement;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WindowTab;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The chat's part of the window layout. The windows themselves are
 * {@link WindowLayout}'s; this keeps what only the chat means by them.
 *
 * <ul>
 * <li>The window a new player starts with: Global and OOC, Global in
 * front, filling the screen's bottom-left quarter.</li>
 * <li>Each conversation's two preferences: its notification choice
 * ({@link ChatNotification}: which of its lines reach the closed feed
 * and which chime) and <em>hidden</em> (once closed it stays closed when
 * a message arrives).</li>
 * <li>Closed channels: a plain channel in no window is gone from every
 * window, its history untouched, restorable into any window, and back
 * with its next message unless it is hidden. Closing is about tabs
 * only: a closed channel keeps receiving, keeps its history, its unread
 * counts and its preferences.</li>
 * <li>Whisper tabs, remembered per server.</li>
 * <li>Where the closed feed stands, whether the picker strip is folded,
 * and each window's timestamp area and member list.</li>
 * </ul>
 *
 * <p>All of it is written into the layout file as the chat's part of it.
 * Message history stays in vanilla's chat, per-line tabs and unread
 * counts in {@link ClientChatChannelViews}, channel availability in
 * {@link ClientChatChannelState}.</p>
 */
public final class ChatLayout {
    /** The tabs a new player's window holds, the first in front. */
    private static final List<ChatTab> FIRST_TABS =
            Collections.unmodifiableList(Arrays.asList(
                    ChatTab.of(ChatChannel.GLOBAL),
                    ChatTab.of(ChatChannel.OOC)));
    /**
     * The channels a new player's window leaves closed that wait to be
     * opened by hand rather than opening with their next line: staff
     * talk and the consoles, which speak often and are not conversations.
     */
    private static final List<ChatTab> FIRST_HIDDEN =
            Collections.unmodifiableList(Arrays.asList(
                    ChatTab.of(ChatChannel.OPERATOR),
                    ChatTab.of(ChatChannel.CLIENT_CONSOLE),
                    ChatTab.of(ChatChannel.SERVER_CONSOLE)));
    /**
     * Each conversation's notification choice, where it is not
     * {@link ChatNotification#EVERYTHING}.
     *
     * <p>This and the hidden set below hold row entries. A scoped
     * channel's conversations share the one entry the row holds, so a
     * choice for the Faction tab is the channel's rather than whichever
     * faction happened to be on screen. Every accessor normalises
     * through {@link ChatTab#row}, so a caller holding a line's own tab
     * asks the same question.</p>
     */
    private static final Map<ChatTab, ChatNotification> NOTIFICATIONS =
            new HashMap<ChatTab, ChatNotification>();
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
    /** Each window's timestamp area and member list, by window id. */
    private static final Map<String, View> VIEWS = new HashMap<String, View>();
    /** The channels the file said were closed, while it is read. */
    private static final Set<ChatChannel> CLOSED_READ =
            new LinkedHashSet<ChatChannel>();
    /** Closed-chat feed position, percent of its travel; vanilla's spot. */
    private static double feedOffsetX;
    private static double feedOffsetY = 100.0D;
    private static boolean toolbarCollapsed;
    private static boolean installed;

    /** A window's timestamp area and member list, as the player left them. */
    private static final class View {
        boolean areaHidden;
        boolean membersHidden;
        /** The member list's width in the chat's pixels; 0 for its own. */
        double membersWidth;
    }

    private ChatLayout() {}

    /**
     * Puts the chat into the window layout: the windows a new player
     * starts with, reading its tabs back from the layout file, and its
     * part of that file. Called before the file is read.
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        ChatFrame.install();
        WindowPlacement.setOwnLineWidths(ChatWindowLines.isAvailable());
        WindowStyle.setAsideTone(new WindowStyle.Tone() {
            @Override
            public int rgb() {
                return LostTalesChatVisualStyle.asideRgb();
            }
        });
        WindowTab.addReader(new WindowTab.Reader() {
            @Override
            public WindowTab read(String id) {
                return ChatTab.fromId(id);
            }
        });
        WindowLayout.setDefaults(DEFAULT_WINDOWS);
        WindowLayoutStore.addPart(PART);
    }

    /** Back to a new player's layout: the chat's own state and every window. */
    public static synchronized void reset() {
        install();
        PART.clear();
        WindowLayout.reset();
    }

    /**
     * A new player's window: Global and OOC, Global in front, snapped to
     * the screen's bottom-left quarter. Every other channel starts closed;
     * Proximity, Faction and Party open with their first line, and
     * Operator and the consoles wait in the {@code +} until opened by hand.
     * From then on the layout is whatever the player makes of it.
     */
    static final Runnable DEFAULT_WINDOWS = new Runnable() {
        @Override
        public void run() {
            Window window = WindowLayout.addWindow(FIRST_TABS,
                    FIRST_TABS.get(0), 0.0D, 100.0D);
            if (window != null) {
                WindowLayout.setFill(window.getId(),
                        Window.ScreenFill.BOTTOM_LEFT, false);
            }
            HIDDEN.addAll(FIRST_HIDDEN);
        }
    };

    /* ---- Where channels stand ---- */

    /** Whether the tab's row entry is in a window. */
    public static synchronized boolean isOpen(ChatTab tab) {
        return WindowLayout.windowOf(ChatTab.row(tab)) != null;
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

    /* ---- Preferences ---- */

    /**
     * The conversation's notification choice:
     * {@link ChatNotification#EVERYTHING} unless the player chose
     * another.
     */
    public static synchronized ChatNotification notification(ChatTab tab) {
        ChatNotification choice = tab == null ? null
                : NOTIFICATIONS.get(ChatTab.row(tab));
        return choice == null ? ChatNotification.EVERYTHING : choice;
    }

    /**
     * Chooses how much of the conversation reaches the player while they
     * are not reading it. Nothing else changes: the tab still shows
     * every line when selected, every line still counts unread, and the
     * history is untouched.
     */
    public static synchronized void setNotification(ChatTab tab,
                                                    ChatNotification choice) {
        ChatTab row = ChatTab.row(tab);
        if (row == null || choice == null) {
            return;
        }
        ChatNotification before = choice == ChatNotification.EVERYTHING
                ? NOTIFICATIONS.remove(row) : NOTIFICATIONS.put(row, choice);
        if ((before == null ? ChatNotification.EVERYTHING : before) != choice) {
            WindowLayout.persist();
        }
    }

    /**
     * Steps the conversation's choice on, or {@code back}, as a
     * few-word option is stepped: round the three.
     */
    public static synchronized ChatNotification stepNotification(
            ChatTab tab, boolean back) {
        ChatNotification next = notification(tab).step(back);
        setNotification(tab, next);
        return next;
    }

    /**
     * Whether the conversation's news is kept out of the way: nothing of
     * it reaches the player. Its tab writes its name in italics.
     */
    public static synchronized boolean isMuted(ChatTab tab) {
        return notification(tab) == ChatNotification.NOTHING;
    }

    public static synchronized boolean isMuted(ChatChannel channel) {
        return isMuted(ChatTab.of(channel));
    }

    /**
     * Whether a line of the conversation addressed to the player, a
     * mention or a reply, chimes; Do Not Disturb holds it still.
     */
    public static synchronized boolean isPingAudible(ChatTab tab) {
        return chimes(tab, true);
    }

    /**
     * Whether a line of the conversation chimes: one {@code addressed}
     * to the player, or any line of a whisper, as its choice allows.
     */
    public static synchronized boolean chimes(ChatTab tab, boolean addressed) {
        return tab != null && notification(tab).chimes(addressed,
                tab.isWhisper());
    }

    /**
     * The conversations with a choice of their own, the file's order: by
     * id, NPC conversations left out, since they end with the session.
     */
    static synchronized List<ChatTab> notificationTabs() {
        List<ChatTab> result = new ArrayList<ChatTab>();
        for (ChatTab tab : NOTIFICATIONS.keySet()) {
            if (!tab.isNpc()) {
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

    /** Whether a message may not reopen the tab while it is closed. */
    public static synchronized boolean isHidden(ChatTab tab) {
        return tab != null && HIDDEN.contains(ChatTab.row(tab));
    }

    /** Hidden plain tabs in a stable order, for the file. */
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
            WindowLayout.persist();
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

    /**
     * The conversations whose every line the closed feed carries, in its
     * order: every channel the player can see whose choice is
     * {@link ChatNotification#EVERYTHING}, in presentation order, whether
     * or not it has a tab — closing a tab hides the tab, not the
     * channel's messages — then every open conversation tab with the same
     * choice. Conversations are read from their open tabs only: a closed
     * one is hidden until its next message reopens it. Their typing shows
     * in the feed too.
     */
    public static List<ChatTab> feedTabs() {
        return feedTabs(ChatNotification.EVERYTHING);
    }

    /**
     * The conversations only whose lines addressed to the player the
     * closed feed carries, in the same order: those whose choice is
     * {@link ChatNotification#ONLY_MENTIONS}.
     */
    public static List<ChatTab> mentionFeedTabs() {
        return feedTabs(ChatNotification.ONLY_MENTIONS);
    }

    /** What the closed feed shows: see {@link #feedTabs} and {@link #mentionFeedTabs}. */
    static ChatLineFilter feedFilter() {
        return ChatLineFilter.of(feedTabs(), mentionFeedTabs());
    }

    private static List<ChatTab> feedTabs(ChatNotification choice) {
        List<ChatTab> audible = new ArrayList<ChatTab>();
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            ChatTab tab = ChatTab.of(channel);
            if (ClientChatChannelState.isAvailable(tab)
                    && notification(tab) == choice) {
                audible.add(tab);
            }
        }
        for (WindowTab each : WindowLayout.order()) {
            // A conversation the chat is not being read as is out of the
            // feed as well as off the row: hiding the tab and still
            // showing its lines would say two things at once.
            ChatTab tab = ChatTab.from(each);
            if (tab != null && tab.isWhisper()
                    && ClientChatChannelState.isAvailable(tab)
                    && notification(tab) == choice) {
                audible.add(tab);
            }
        }
        return audible;
    }

    /* ---- Closing and opening ---- */

    /**
     * Removes the tab from its window, as {@link WindowLayout#close}
     * does; a whisper closed by hand stays closed through a replay, and
     * comes back with the next live line. The notification choice is
     * untouched: a closed tab keeps it for when it is restored.
     */
    public static synchronized boolean close(ChatTab tab) {
        if (!WindowLayout.isClosable(tab)) {
            return false;
        }
        if (isRemembered(tab)) {
            HIDDEN.add(ChatTab.row(tab));
        }
        return WindowLayout.close(tab);
    }

    /**
     * The whisper tab with the named account as one identity of theirs,
     * empty for the account's own, opened if it is not: it joins the
     * preferred window, or the first unlocked one. The front tab is left
     * alone, so an arriving whisper does not steal the row. The tab is
     * the person's row entry: which conversation it shows follows the
     * identity the chat is read as, which is who speaks in it.
     */
    public static synchronized ChatTab openWhisper(
            String partner, String identity, String preferredWindowId) {
        return openTab(ChatTab.whisper(partner, identity), preferredWindowId);
    }

    /**
     * Opens a conversation's tab where a tab that opens by itself belongs,
     * and answers it as the layout holds it. The row holds one entry per
     * channel and per person, so a conversation held as one character
     * opens as its row entry: a line's own tab never stands in a window
     * beside the one that shows it.
     */
    public static synchronized ChatTab openTab(ChatTab tab,
                                               String preferredWindowId) {
        return ChatTab.from(WindowLayout.openTab(ChatTab.row(tab),
                preferredWindowId));
    }

    /* ---- Whisper tabs per place ---- */

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
        for (Window window : WindowLayout.windows()) {
            for (WindowTab each : window.getTabs()) {
                ChatTab tab = ChatTab.from(each);
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
        return openTab(row, preferredWindowId);
    }

    /**
     * Closes every whisper and NPC tab: a conversation ends with the
     * session it was held in, and so does its tab — the place's whisper
     * tabs are remembered first, open and closed by hand alike, and come
     * back on the next visit, each with its notification choice. An NPC
     * conversation's choice goes with it. A window left empty goes, the
     * last one included.
     */
    public static synchronized void closeConversations() {
        rememberConversations(conversationsPlace);
        Iterator<ChatTab> closedByHand = HIDDEN.iterator();
        while (closedByHand.hasNext()) {
            if (isRemembered(closedByHand.next())) {
                closedByHand.remove();
            }
        }
        List<WindowTab> removed = WindowLayout.removeTabs(
                new WindowLayout.TabFilter() {
                    @Override
                    public boolean matches(WindowTab tab) {
                        ChatTab conversation = ChatTab.from(tab);
                        return conversation != null
                                && conversation.isWhisper();
                    }
                });
        for (WindowTab tab : removed) {
            ChatTab conversation = ChatTab.from(tab);
            if (conversation != null && conversation.isNpc()) {
                NOTIFICATIONS.remove(conversation);
            }
            HIDDEN.remove(tab);
        }
        if (!removed.isEmpty()) {
            WindowLayout.persist();
        }
    }

    /* ---- The feed and the picker strip ---- */

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
            WindowLayout.persist();
        }
    }

    /**
     * Writes the feed's position alone while nothing else of the layout
     * has changed since it was read ({@link WindowLayoutStore#saveLine}).
     */
    public static synchronized void saveFeedPosition() {
        WindowLayoutStore.saveLine(FEED, feedLine());
    }

    private static String feedLine() {
        return FEED + " x=" + format(feedOffsetX) + " y=" + format(feedOffsetY);
    }

    private static double clampPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(100.0D, value));
    }

    /** Whether the picker strip above the input bar is folded away. */
    public static synchronized boolean isToolbarCollapsed() {
        return toolbarCollapsed;
    }

    public static synchronized void setToolbarCollapsed(boolean collapsed) {
        if (toolbarCollapsed != collapsed) {
            toolbarCollapsed = collapsed;
            WindowLayout.persist();
        }
    }

    /* ---- A window's timestamp area and member list ---- */

    private static View view(String windowId) {
        View view = VIEWS.get(windowId);
        if (view == null) {
            view = new View();
            VIEWS.put(windowId, view);
        }
        return view;
    }

    /** Whether the window's timestamp area is driven out. */
    public static synchronized boolean isAreaHidden(Window window) {
        View view = window == null ? null : VIEWS.get(window.getId());
        return view != null && view.areaHidden;
    }

    /** Whether the window's member list is put away. */
    public static synchronized boolean isMembersHidden(Window window) {
        View view = window == null ? null : VIEWS.get(window.getId());
        return view != null && view.membersHidden;
    }

    /** The member list's width the player chose, in the chat's pixels; 0 for its own. */
    public static synchronized double getMembersWidth(Window window) {
        View view = window == null ? null : VIEWS.get(window.getId());
        return view == null ? 0.0D : view.membersWidth;
    }

    /**
     * Drives a window's timestamp area out, or back in: the window keeps
     * its size and its words take the area's room. Written to the file.
     */
    public static synchronized boolean setAreaHidden(String windowId,
                                                     boolean hidden) {
        Window window = WindowLayout.window(windowId);
        if (window == null || isAreaHidden(window) == hidden) {
            return false;
        }
        view(windowId).areaHidden = hidden;
        WindowLayout.persist();
        return true;
    }

    /**
     * Puts a window's member list away, or brings it out: the window
     * keeps its size and its words take the list's room. Written to the
     * file.
     */
    public static synchronized boolean setMembersHidden(String windowId,
                                                        boolean hidden) {
        Window window = WindowLayout.window(windowId);
        if (window == null || isMembersHidden(window) == hidden) {
            return false;
        }
        view(windowId).membersHidden = hidden;
        WindowLayout.persist();
        return true;
    }

    /**
     * Gives a window's member list the width its edge was dragged to, in
     * the chat's pixels, written to the file when {@code persist} says so
     * — once, as the drag ends.
     */
    public static synchronized boolean setMembersWidth(String windowId,
                                                       double width,
                                                       boolean persist) {
        if (WindowLayout.window(windowId) == null) {
            return false;
        }
        view(windowId).membersWidth = clampMembersWidth(width);
        if (persist) {
            WindowLayout.persist();
        }
        return true;
    }

    /**
     * A member list's stored width: zero for the list's own, anything
     * else bounded as a window's is. Where it really stops is the
     * window it stands in.
     */
    static double clampMembersWidth(double width) {
        if (Double.isNaN(width) || Double.isInfinite(width) || width <= 0.0D) {
            return 0.0D;
        }
        return Math.min(WindowLayout.MAX_WINDOW_SIZE, width);
    }

    /* ---- The chat's part of the layout file ---- */

    private static final String FEED = "feed";
    private static final String TOOLBAR = "toolbar";
    private static final String CLOSED = "closed";
    private static final String NOTIFY = "notify";
    private static final String HIDDEN_LINE = "hidden";
    private static final String CONVERSATION = "conversation";
    private static final String CLOSED_CONVERSATION = "closedconversation";
    /** What a window line says of an area or member list put away. */
    private static final String PUT_AWAY = "hidden";

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * The chat's lines in the layout file:
     *
     * <pre>
     * window w2 ... area=hidden members=hidden members_width=90.00 ...
     * feed x=0.00 y=100.00
     * toolbar collapsed=false
     * closed faction
     * notify mentions ooc
     * notify nothing whisper:Steve|Aldric
     * hidden operator
     * </pre>
     *
     * <p>A conversation's notification choice, where it is not
     * {@code everything}, stands on a {@code notify} line: the choice
     * ({@code mentions} or {@code nothing}) and the tab id, whispers
     * included and NPC conversations not. A whisper tab is remembered per
     * place on a line of its own: {@code conversation}, the place, the
     * window and the tab id for one that was open;
     * {@code closedconversation}, the place and the tab id for one closed
     * by hand. These lines are tab-separated, since a name or a place may
     * hold spaces.</p>
     */
    private static final WindowLayoutStore.Part PART = new WindowLayoutStore.Part() {
        @Override
        public void clear() {
            NOTIFICATIONS.clear();
            HIDDEN.clear();
            CONVERSATIONS.clear();
            CLOSED_CONVERSATIONS.clear();
            conversationsPlace = "";
            VIEWS.clear();
            CLOSED_READ.clear();
            feedOffsetX = 0.0D;
            feedOffsetY = 100.0D;
            toolbarCollapsed = false;
        }

        @Override
        public boolean read(String line) {
            if (line.startsWith(CONVERSATION + "\t")
                    || line.startsWith(CLOSED_CONVERSATION + "\t")) {
                readConversation(line.split("\t"));
                return true;
            }
            if (line.startsWith(NOTIFY + "\t")) {
                readNotification(line.split("\t"));
                return true;
            }
            String[] parts = line.split("\\s+");
            String key = parts[0];
            if (parts.length == 2 && CLOSED.equals(key)) {
                ChatChannel channel = ChatChannel.fromId(parts[1]);
                if (channel != null) {
                    CLOSED_READ.add(channel);
                }
                return true;
            }
            if (parts.length == 2 && HIDDEN_LINE.equals(key)) {
                ChatTab tab = ChatTab.fromId(parts[1]);
                if (tab != null) {
                    HIDDEN.add(tab);
                }
                return true;
            }
            if (FEED.equals(key)) {
                for (int index = 1; index < parts.length; index++) {
                    if (parts[index].startsWith("x=")) {
                        feedOffsetX = clampPercent(
                                WindowLayoutStore.parseDouble(parts[index].substring(2)));
                    } else if (parts[index].startsWith("y=")) {
                        feedOffsetY = clampPercent(
                                WindowLayoutStore.parseDouble(parts[index].substring(2)));
                    }
                }
                return true;
            }
            if (TOOLBAR.equals(key)) {
                for (int index = 1; index < parts.length; index++) {
                    if (parts[index].startsWith("collapsed=")) {
                        toolbarCollapsed = "true".equalsIgnoreCase(
                                parts[index].substring(10));
                    }
                }
                return true;
            }
            return false;
        }

        /** A {@code notify} line: the choice, then the tab it is for. */
        private void readNotification(String[] fields) {
            if (fields.length != 3) {
                return;
            }
            ChatNotification choice = ChatNotification.fromId(fields[1]);
            ChatTab tab = ChatTab.fromId(fields[2]);
            if (choice != null && tab != null && !tab.isNpc()
                    && choice != ChatNotification.EVERYTHING) {
                NOTIFICATIONS.put(ChatTab.row(tab), choice);
            }
        }

        private void readConversation(String[] fields) {
            if (CONVERSATION.equals(fields[0]) && fields.length == 4) {
                List<String[]> open = CONVERSATIONS.get(fields[1]);
                if (open == null) {
                    open = new ArrayList<String[]>();
                    CONVERSATIONS.put(fields[1], open);
                }
                open.add(new String[] {fields[2], fields[3]});
            } else if (CLOSED_CONVERSATION.equals(fields[0])
                    && fields.length == 3) {
                Set<String> closedHere = CLOSED_CONVERSATIONS.get(fields[1]);
                if (closedHere == null) {
                    closedHere = new LinkedHashSet<String>();
                    CLOSED_CONVERSATIONS.put(fields[1], closedHere);
                }
                closedHere.add(fields[2]);
            }
        }

        @Override
        public boolean readWindow(String windowId, String key, String value) {
            if ("area".equals(key)) {
                view(windowId).areaHidden = PUT_AWAY.equalsIgnoreCase(value);
                return true;
            }
            if ("members".equals(key)) {
                view(windowId).membersHidden = PUT_AWAY.equalsIgnoreCase(value);
                return true;
            }
            if ("members_width".equals(key)) {
                view(windowId).membersWidth = clampMembersWidth(
                        WindowLayoutStore.parseDouble(value));
                return true;
            }
            return false;
        }

        /**
         * Every plain channel the file neither placed nor closed goes to
         * the first window, so a channel added after the file was written
         * is never silently lost; with no window at all, one opens for
         * them. Staff talk and the consoles are the exception: they wait
         * in the {@code +}, hidden, as they do for a new player. A file
         * that names no window and closes every channel describes the
         * empty layout and is loaded as one.
         */
        @Override
        public void loaded() {
            List<ChatTab> unplaced = new ArrayList<ChatTab>();
            for (ChatChannel channel : ChatChannel.presentationOrder()) {
                ChatTab tab = ChatTab.of(channel);
                if (WindowLayout.isOpen(tab) || CLOSED_READ.contains(channel)) {
                    continue;
                }
                if (FIRST_HIDDEN.contains(tab)) {
                    HIDDEN.add(tab);
                } else {
                    unplaced.add(tab);
                }
            }
            CLOSED_READ.clear();
            if (unplaced.isEmpty()) {
                return;
            }
            if (WindowLayout.isEmpty()) {
                ChatTab global = ChatTab.of(ChatChannel.GLOBAL);
                WindowLayout.addWindow(unplaced, unplaced.contains(global)
                        ? global : unplaced.get(0), 0.0D, 100.0D);
            } else {
                WindowLayout.appendTabs(WindowLayout.firstWindow(), unplaced);
            }
        }

        @Override
        public void describeWindow(Window window, StringBuilder line) {
            if (isAreaHidden(window)) {
                line.append(" area=").append(PUT_AWAY);
            }
            if (isMembersHidden(window)) {
                line.append(" members=").append(PUT_AWAY);
            }
            if (getMembersWidth(window) > 0.0D) {
                line.append(" members_width=")
                        .append(format(getMembersWidth(window)));
            }
        }

        @Override
        public void describe(List<String> lines) {
            lines.add(feedLine());
            lines.add(TOOLBAR + " collapsed=" + toolbarCollapsed);
            for (ChatChannel channel : closedChannels()) {
                lines.add(CLOSED + " " + channel.getId());
            }
            for (ChatTab tab : notificationTabs()) {
                lines.add(NOTIFY + "\t" + notification(tab).id() + "\t"
                        + tab.id());
            }
            for (ChatTab tab : hiddenTabs()) {
                lines.add(HIDDEN_LINE + " " + tab.id());
            }
            rememberConversations(conversationsPlace);
            for (Map.Entry<String, List<String[]>> place
                    : CONVERSATIONS.entrySet()) {
                for (String[] entry : place.getValue()) {
                    lines.add(CONVERSATION + "\t" + place.getKey() + "\t"
                            + entry[0] + "\t" + entry[1]);
                }
            }
            for (Map.Entry<String, Set<String>> place
                    : CLOSED_CONVERSATIONS.entrySet()) {
                for (String id : place.getValue()) {
                    lines.add(CLOSED_CONVERSATION + "\t" + place.getKey()
                            + "\t" + id);
                }
            }
        }
    };
}
