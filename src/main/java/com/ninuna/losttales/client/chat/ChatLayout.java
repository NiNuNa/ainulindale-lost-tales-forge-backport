package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.window.PinnedWindows;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowFrame;
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
 * front, in the middle of the screen at two thirds of it.</li>
 * <li>Each conversation's two choices ({@link ChatLineChoice}):
 * Notifications, which of its lines chime, and Show in Feed, which of its
 * lines reach the closed feed.</li>
 * <li>The NPC conversations of the session: only the last few stay open
 * by themselves, and the {@code +} opens the others again.</li>
 * <li>Closed channels: a plain channel in no window is gone from every
 * window, its history untouched, restorable into any window, and back
 * with its next message, but for Operator Chat and the consoles, which
 * open only by hand ({@link #opensByItself}). Closing is about tabs
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
     * The channels that never open by themselves, only by hand: staff talk
     * and the consoles, which speak often and are not conversations. They
     * wait in the {@code +}.
     */
    private static final List<ChatTab> OPENED_BY_HAND =
            Collections.unmodifiableList(Arrays.asList(
                    ChatTab.of(ChatChannel.OPERATOR),
                    ChatTab.of(ChatChannel.CLIENT_CONSOLE),
                    ChatTab.of(ChatChannel.SERVER_CONSOLE)));
    /**
     * Each conversation's Notifications choice, where it is not the
     * conversation's default ({@link #defaultNotification}).
     *
     * <p>This and the feed choices below hold row entries. A scoped channel's conversations share the one entry the
     * row holds, so a choice for the Faction tab is the channel's rather
     * than whichever faction happened to be on screen. Every accessor
     * normalises through {@link ChatTab#row}, so a caller holding a
     * line's own tab asks the same question.</p>
     */
    private static final Map<ChatTab, ChatLineChoice> NOTIFICATIONS =
            new HashMap<ChatTab, ChatLineChoice>();
    /** Each conversation's Show in Feed choice, where it is not Everything. */
    private static final Map<ChatTab, ChatLineChoice> FEED_CHOICES =
            new HashMap<ChatTab, ChatLineChoice>();
    /** How many NPC conversations the session remembers having spoken. */
    static final int MAX_NPC_CONVERSATIONS = 64;
    /**
     * The NPC conversations that have spoken this session, the one quiet
     * longest first, each with the turn it last spoke on; the
     * {@link #MAX_NPC_CONVERSATIONS} most recent at most. For the
     * session only.
     */
    private static final Map<ChatTab, Long> NPC_SPOKEN =
            new LinkedHashMap<ChatTab, Long>();
    /** The turn the last NPC line of the session spoke on. */
    private static long npcTurn;
    /**
     * The whispers closed by hand. Each is closed only until somebody
     * speaks in it again: a replay may not bring it back, a live line
     * does ({@link #reopenConversation}).
     */
    private static final Set<ChatTab> CLOSED_BY_HAND = new HashSet<ChatTab>();
    /**
     * Whisper and fellowship tabs remembered per place, by the session's
     * server key: where each was open, in order, and which were closed by
     * hand. A conversation belongs to the server it was held on, so
     * arriving elsewhere leaves it in the file for the next visit.
     */
    private static final Map<String, List<String[]>> CONVERSATIONS =
            new LinkedHashMap<String, List<String[]>>();
    private static final Map<String, Set<String>> CLOSED_CONVERSATIONS =
            new LinkedHashMap<String, Set<String>>();
    /** The place whose whisper and fellowship tabs are on screen; empty before a join. */
    private static String conversationsPlace = "";
    /** Each window's timestamp area and member list, by window id. */
    private static final Map<String, View> VIEWS = new HashMap<String, View>();
    /** The channels the file said were closed, while it is read. */
    private static final Set<ChatChannel> CLOSED_READ =
            new LinkedHashSet<ChatChannel>();
    /**
     * Conversations that tried to open by themselves while no window held
     * a conversation: the chat's next opening brings them in its first
     * window. For the session only.
     */
    private static final Set<ChatTab> WAITING = new LinkedHashSet<ChatTab>();
    /**
     * The window the chat last opened for a conversation because none
     * would take it: the ones after it join it, and the chat's key brings
     * it forward while anything in it waits unread. For the session only;
     * null for none.
     */
    private static String collectingWindowId;
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
        // A conversation's window pinned to the screen while playing is
        // drawn as the screen draws it.
        PinnedWindows.setConversationPainter(
                new PinnedWindows.ConversationPainter() {
                    @Override
                    public void draw(net.minecraft.client.Minecraft minecraft,
                                     Window window, int screenWidth,
                                     int screenHeight,
                                     LostTalesGuiAnimationSample shown) {
                        LostTalesChatOverlayRenderer.drawWindowForScreen(
                                minecraft, window, screenWidth, screenHeight,
                                shown);
                    }
                });
    }

    /** Back to a new player's layout: the chat's own state and every window. */
    public static synchronized void reset() {
        install();
        PART.clear();
        WAITING.clear();
        collectingWindowId = null;
        forgetNpcConversations();
        WindowLayout.reset();
    }

    /**
     * A new player's window: Global and OOC, Global in front, in the
     * middle of the screen at two thirds of it and locked, the one window
     * that opens locked. Every other channel starts closed; Proximity, Faction
     * and Fellowship open with their first line, and Operator and the consoles
     * wait in the {@code +} until opened by hand. From then on the layout
     * is whatever the player makes of it.
     */
    static final Runnable DEFAULT_WINDOWS = new Runnable() {
        @Override
        public void run() {
            WindowLayout.addWindow(FIRST_TABS, FIRST_TABS.get(0));
        }
    };

    /** Whether a conversation is on screen: in front of a window or not, in a row the view shows. */
    public static synchronized boolean showsConversation() {
        for (Window window : WindowLayout.windows()) {
            for (WindowTab tab : WindowFrame.visibleTabs(window)) {
                if (ChatTab.from(tab) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether any window holds a conversation. */
    public static synchronized boolean hasConversationWindow() {
        return firstConversationWindow() != null;
    }

    /** The first window, in layout order, holding a conversation; null for none. */
    private static Window firstConversationWindow() {
        for (Window window : WindowLayout.windows()) {
            for (WindowTab tab : window.getTabs()) {
                if (ChatTab.from(tab) != null) {
                    return window;
                }
            }
        }
        return null;
    }

    /**
     * The chat's first window again, as a new player's opens: Global and
     * OOC, Global in front, with every conversation that tried to open
     * while no window held one. What the chat opens with once every
     * conversation window was closed. Null when all of them are open
     * already.
     */
    public static synchronized Window openFirstWindow() {
        List<ChatTab> tabs = new ArrayList<ChatTab>(FIRST_TABS);
        for (ChatTab waiting : WAITING) {
            if (!tabs.contains(waiting)) {
                tabs.add(waiting);
            }
        }
        WAITING.clear();
        Window window = WindowLayout.addWindow(tabs, FIRST_TABS.get(0));
        if (window != null) {
            WindowLayout.raise(window.getId());
            WindowLayout.persist();
        }
        return window;
    }

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
     * The Notifications choice a conversation starts with: Everything for
     * a whisper, with a player or an NPC, and Only Mentions for every
     * channel, the consoles and Operator included.
     */
    static ChatLineChoice defaultNotification(ChatTab tab) {
        return tab != null && tab.isWhisper()
                ? ChatLineChoice.EVERYTHING : ChatLineChoice.ONLY_MENTIONS;
    }

    /** The conversation's Notifications choice: its default unless the player chose another. */
    public static synchronized ChatLineChoice notification(ChatTab tab) {
        ChatTab row = ChatTab.row(tab);
        ChatLineChoice choice = row == null ? null : NOTIFICATIONS.get(row);
        return choice == null ? defaultNotification(row) : choice;
    }

    /**
     * Chooses which of the conversation's new lines chime. Nothing else
     * changes: every line still counts unread, the feed still shows what
     * Show in Feed lets through, and the history is untouched.
     */
    public static synchronized void setNotification(ChatTab tab,
                                                    ChatLineChoice choice) {
        ChatTab row = ChatTab.row(tab);
        if (row == null || choice == null || notification(row) == choice) {
            return;
        }
        if (choice == defaultNotification(row)) {
            NOTIFICATIONS.remove(row);
        } else {
            NOTIFICATIONS.put(row, choice);
        }
        WindowLayout.persist();
    }

    /** The conversation's Show in Feed choice: All Messages unless the player chose another. */
    public static synchronized ChatLineChoice feedChoice(ChatTab tab) {
        ChatTab row = ChatTab.row(tab);
        ChatLineChoice choice = row == null ? null : FEED_CHOICES.get(row);
        return choice == null ? ChatLineChoice.EVERYTHING : choice;
    }

    /**
     * Chooses which of the conversation's lines reach the closed feed.
     * Nothing else changes: every line still counts unread and chimes as
     * Notifications says.
     */
    public static synchronized void setFeedChoice(ChatTab tab,
                                                  ChatLineChoice choice) {
        ChatTab row = ChatTab.row(tab);
        if (row == null || choice == null || feedChoice(row) == choice) {
            return;
        }
        if (choice == ChatLineChoice.EVERYTHING) {
            FEED_CHOICES.remove(row);
        } else {
            FEED_CHOICES.put(row, choice);
        }
        WindowLayout.persist();
    }

    /**
     * Whether the conversation is muted: none of its lines chime. Its
     * tab writes its name in italics.
     */
    public static synchronized boolean isMuted(ChatTab tab) {
        return notification(tab) == ChatLineChoice.NOTHING;
    }

    public static synchronized boolean isMuted(ChatChannel channel) {
        return isMuted(ChatTab.of(channel));
    }

    /**
     * Whether a new line of the conversation chimes as its Notifications
     * choice allows: any line where it is Everything, one
     * {@code addressed} to the player, a mention or a reply, where it is
     * Only Mentions. The caller leaves out the player's own lines and
     * replayed ones; Do Not Disturb holds the chime still.
     */
    public static synchronized boolean chimes(ChatTab tab, boolean addressed) {
        return tab != null && notification(tab).lets(addressed);
    }

    /**
     * The conversations whose Notifications is not their default, the
     * file's order: by id, NPC conversations left out, since they end
     * with the session.
     */
    static synchronized List<ChatTab> notificationTabs() {
        return writtenTabs(NOTIFICATIONS.keySet());
    }

    /** The conversations whose Show in Feed is not All Messages, in the same order. */
    static synchronized List<ChatTab> feedChoiceTabs() {
        return writtenTabs(FEED_CHOICES.keySet());
    }

    private static List<ChatTab> writtenTabs(Set<ChatTab> tabs) {
        List<ChatTab> result = new ArrayList<ChatTab>();
        for (ChatTab tab : tabs) {
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

    /**
     * Whether a line arriving may open the tab while it is closed. Operator
     * Chat and the consoles never do: they open by hand. Nor does a whisper
     * closed by hand, which a live line opens again
     * ({@link #reopenConversation}) and a replay does not.
     */
    public static synchronized boolean opensByItself(ChatTab tab) {
        ChatTab row = tab == null ? null : ChatTab.row(tab);
        return row != null && !OPENED_BY_HAND.contains(row)
                && !CLOSED_BY_HAND.contains(row);
    }

    /**
     * The conversations whose every line the closed feed carries, in its
     * order: every channel the player can see whose Show in Feed is
     * {@link ChatLineChoice#ALL}, in presentation order, whether or not
     * it has a tab — closing a tab hides the tab, not the channel's
     * messages — then every open conversation tab with the same choice.
     * Conversations are read from their open tabs only: a closed one is
     * hidden until its next message reopens it. Their typing shows in the
     * feed too.
     */
    public static List<ChatTab> feedTabs() {
        return feedTabs(ChatLineChoice.EVERYTHING);
    }

    /**
     * The conversations only whose lines addressed to the player the
     * closed feed carries, in the same order: those whose Show in Feed
     * is {@link ChatLineChoice#ONLY_MENTIONS}.
     */
    public static List<ChatTab> mentionFeedTabs() {
        return feedTabs(ChatLineChoice.ONLY_MENTIONS);
    }

    /** What the closed feed shows: see {@link #feedTabs} and {@link #mentionFeedTabs}. */
    static ChatLineFilter feedFilter() {
        return ChatLineFilter.of(feedTabs(), mentionFeedTabs());
    }

    private static List<ChatTab> feedTabs(ChatLineChoice choice) {
        List<ChatTab> shown = new ArrayList<ChatTab>();
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            ChatTab tab = ChatTab.of(channel);
            if (ClientChatChannelState.isAvailable(tab)
                    && feedChoice(tab) == choice
                    && !PinnedWindows.shows(tab)) {
                shown.add(tab);
            }
        }
        for (WindowTab each : WindowLayout.order()) {
            // A conversation the chat is not being read as is out of the
            // feed as well as off the row: hiding the tab and still
            // showing its lines would say two things at once.
            ChatTab tab = ChatTab.from(each);
            if (tab != null && (tab.isWhisper() || tab.isFellowship())
                    && ClientChatChannelState.isAvailable(tab)
                    && feedChoice(tab) == choice
                    && !PinnedWindows.shows(tab)) {
                shown.add(tab);
            }
        }
        return shown;
    }

    /* ---- Closing and opening ---- */

    /**
     * Removes the tab from its window, as {@link WindowLayout#close}
     * does; a whisper closed by hand stays closed through a replay, and
     * comes back with the next live line. The tab's choices are
     * untouched: a closed tab keeps them for when it is restored.
     */
    public static synchronized boolean close(ChatTab tab) {
        if (!WindowLayout.isClosable(tab)) {
            return false;
        }
        if (isRemembered(tab)) {
            CLOSED_BY_HAND.add(ChatTab.row(tab));
        }
        return WindowLayout.close(tab);
    }

    /**
     * The whisper tab with the named account as one identity of theirs,
     * empty for the account's own, opened if it is not, as the player asked
     * for it ({@link #openHere}). The front tab is left alone. The tab is
     * the person's row entry: which conversation it shows follows the
     * identity the chat is read as, which is who speaks in it.
     */
    public static synchronized ChatTab openWhisper(
            String partner, String identity, String preferredWindowId) {
        return openHere(ChatTab.whisper(partner, identity), preferredWindowId);
    }

    /**
     * Opens a conversation's tab where it belongs
     * ({@link WindowLayout#openTab}): the window asked for, else another
     * that holds conversations, never a locked one, else a window of its
     * own; and answers it as the layout holds it. The row holds one entry
     * per channel and per person, so a
     * conversation held as one character opens as its row entry: a line's
     * own tab never stands in a window beside the one that shows it. Null
     * while no window holds a conversation: the tab waits for the chat's
     * next opening.
     */
    public static synchronized ChatTab openTab(ChatTab tab,
                                               String preferredWindowId) {
        ChatTab row = ChatTab.row(tab);
        int windows = WindowLayout.windows().size();
        ChatTab opened = ChatTab.from(WindowLayout.openTab(row,
                preferredWindowId));
        if (opened == null && row != null) {
            WAITING.add(row);
        }
        if (opened != null && WindowLayout.windows().size() > windows) {
            // No window would take it, so it has one of its own, unlocked:
            // the next that finds no room joins it.
            collectingWindowId = WindowLayout.windowOf(opened).getId();
        }
        return opened;
    }

    /**
     * The conversation the chat's key shows first: one waiting unread in
     * the window the chat opened for conversations that found no room,
     * its front one if that waits, else the first that does. Null while
     * nothing waits there, or that window is gone.
     */
    public static synchronized ChatTab waitingInOwnWindow() {
        Window window = WindowLayout.window(collectingWindowId);
        if (window == null) {
            return null;
        }
        ChatTab front = ChatTab.from(window.getActiveTab());
        if (waits(front)) {
            return front;
        }
        for (WindowTab each : window.getTabs()) {
            ChatTab tab = ChatTab.from(each);
            if (waits(tab)) {
                return tab;
            }
        }
        return null;
    }

    private static boolean waits(ChatTab tab) {
        return ClientChatChannelState.isSelectable(tab)
                && ClientChatChannelViews.hasUnread(tab);
    }

    /**
     * Opens a conversation the player asked for — a whisper, a link, a
     * jump to a line — as {@link #openTab} does; when no window holds a
     * conversation, the chat's first window opens with it, the
     * conversation in a window of its own beside it, since the first
     * window opens locked.
     */
    public static synchronized ChatTab openHere(ChatTab tab,
                                                String preferredWindowId) {
        ChatTab opened = openTab(tab, preferredWindowId);
        if (opened != null || tab == null) {
            return opened;
        }
        Window first = openFirstWindow();
        return first == null ? null : openTab(tab, first.getId());
    }

    /* ---- Whisper and fellowship tabs per place ---- */

    /** Whether a tab is a conversation the layout remembers per place: a player's whisper or a fellowship's. */
    private static boolean isRemembered(ChatTab tab) {
        return tab != null && tab.isPlaceConversation();
    }

    /**
     * Writes a place's whisper and fellowship tabs into the remembered set: the open
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
        for (ChatTab tab : CLOSED_BY_HAND) {
            closed.add(tab.id());
        }
        CONVERSATIONS.put(serverKey, open);
        CLOSED_CONVERSATIONS.put(serverKey, closed);
    }

    /**
     * Opens the place's remembered whisper and fellowship tabs where they were, and
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
                    CLOSED_BY_HAND.add(tab);
                }
            }
        }
        List<String[]> open = CONVERSATIONS.get(conversationsPlace);
        if (open != null) {
            for (String[] entry : open) {
                ChatTab tab = ChatTab.fromId(entry[1]);
                if (isRemembered(tab) && !isOpen(tab)) {
                    CLOSED_BY_HAND.remove(tab);
                    putBack(tab, entry[0]);
                }
            }
        }
    }

    /**
     * Puts a remembered conversation back in the window it stood in, locked or
     * not: that is the layout as the player left it, not a tab arriving.
     * With that window gone it opens as any conversation does.
     */
    private static void putBack(ChatTab tab, String windowId) {
        Window window = WindowLayout.window(windowId);
        if (window == null) {
            openTab(tab, null);
        } else {
            WindowLayout.appendTabs(window, Collections.singletonList(tab));
        }
    }

    /**
     * A live line reopens a conversation closed by hand, a whisper (a
     * player's or an NPC's) or a fellowship's: it was closed only until
     * somebody spoke in it again. Null for anything else.
     */
    public static synchronized ChatTab reopenConversation(ChatTab row,
                                                          String preferredWindowId) {
        if (row == null || !(row.isWhisper() || row.isFellowship())) {
            return null;
        }
        CLOSED_BY_HAND.remove(ChatTab.row(row));
        return openTab(row, preferredWindowId);
    }

    /**
     * Closes every whisper, NPC and fellowship tab: a conversation ends
     * with the session it was held in, and so does its tab — the place's
     * whisper and fellowship tabs are remembered first, open and closed by
     * hand alike, and come back on the next visit, each with its choices. The NPC
     * conversations of the session go, with their choices. A window left
     * empty goes, the last one included.
     */
    public static synchronized void closeConversations() {
        rememberConversations(conversationsPlace);
        WAITING.clear();
        collectingWindowId = null;
        forgetNpcConversations();
        CLOSED_BY_HAND.clear();
        List<WindowTab> removed = WindowLayout.removeTabs(
                new WindowLayout.TabFilter() {
                    @Override
                    public boolean matches(WindowTab tab) {
                        ChatTab conversation = ChatTab.from(tab);
                        return conversation != null
                                && (conversation.isWhisper()
                                        || conversation.isFellowship());
                    }
                });
        if (!removed.isEmpty()) {
            WindowLayout.persist();
        }
    }

    /* ---- NPC conversations ---- */

    /**
     * Notes that an NPC conversation spoke: the session keeps the
     * {@link #MAX_NPC_CONVERSATIONS} that spoke last, so the {@code +}
     * can open one again once it is closed.
     */
    public static synchronized void noteNpcSpoke(ChatTab tab) {
        if (tab == null || !tab.isNpc()) {
            return;
        }
        NPC_SPOKEN.remove(tab);
        NPC_SPOKEN.put(tab, Long.valueOf(++npcTurn));
        Iterator<ChatTab> quietest = NPC_SPOKEN.keySet().iterator();
        while (NPC_SPOKEN.size() > MAX_NPC_CONVERSATIONS && quietest.hasNext()) {
            quietest.next();
            quietest.remove();
        }
    }

    /**
     * The NPC conversations of the session in no window, the one that
     * spoke last first: what the {@code +} offers to open again.
     */
    static synchronized List<ChatTab> closedNpcConversations() {
        List<ChatTab> result = new ArrayList<ChatTab>();
        for (ChatTab tab : NPC_SPOKEN.keySet()) {
            if (!isOpen(tab)) {
                result.add(tab);
            }
        }
        Collections.reverse(result);
        return result;
    }

    /**
     * Whether an NPC conversation of the session is in no window. Asked
     * per frame for the {@code +}, so it only scans.
     */
    static synchronized boolean hasClosedNpcConversation() {
        for (ChatTab tab : NPC_SPOKEN.keySet()) {
            if (!isOpen(tab)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Closes NPC pages while more are open than {@code limit} allows,
     * the ones quiet longest first, after {@code spoke}'s page opened by
     * itself. Kept open: the page that spoke, a page in front of its
     * window and a page holding a draft. A page leaves as one that ends
     * by itself does, from a locked window too, and the {@code +} opens
     * it again.
     */
    public static synchronized void closeQuietNpcConversations(ChatTab spoke,
                                                               int limit) {
        Map<ChatTab, Long> open = new LinkedHashMap<ChatTab, Long>();
        Set<ChatTab> exempt = new HashSet<ChatTab>();
        if (spoke != null) {
            exempt.add(spoke);
        }
        for (Window window : WindowLayout.windows()) {
            for (WindowTab each : window.getTabs()) {
                ChatTab tab = ChatTab.from(each);
                if (tab == null || !tab.isNpc()) {
                    continue;
                }
                Long turn = NPC_SPOKEN.get(tab);
                open.put(tab, turn == null ? Long.valueOf(0L) : turn);
                if (each.equals(window.getActiveTab())
                        || ClientChatChannelState.getDraft(tab).length() > 0) {
                    exempt.add(tab);
                }
            }
        }
        final List<ChatTab> closing = npcPagesToClose(open, exempt, limit);
        if (closing.isEmpty()) {
            return;
        }
        WindowLayout.removeTabs(new WindowLayout.TabFilter() {
            @Override
            public boolean matches(WindowTab tab) {
                return closing.contains(tab);
            }
        });
        WindowLayout.persist();
    }

    /**
     * The NPC pages to close so that at most {@code limit} stay open:
     * {@code open} holds each open page with when it last spoke, and the
     * ones quiet longest close first, never one {@code exempt}. Fewer
     * close when the exempt pages alone pass the limit.
     */
    static List<ChatTab> npcPagesToClose(Map<ChatTab, Long> open,
                                         Set<ChatTab> exempt, int limit) {
        List<ChatTab> closing = new ArrayList<ChatTab>();
        int excess = open.size() - Math.max(0, limit);
        if (excess <= 0) {
            return closing;
        }
        List<Map.Entry<ChatTab, Long>> quietFirst =
                new ArrayList<Map.Entry<ChatTab, Long>>(open.entrySet());
        Collections.sort(quietFirst, new Comparator<Map.Entry<ChatTab, Long>>() {
            @Override
            public int compare(Map.Entry<ChatTab, Long> a,
                               Map.Entry<ChatTab, Long> b) {
                return a.getValue().compareTo(b.getValue());
            }
        });
        for (Map.Entry<ChatTab, Long> entry : quietFirst) {
            if (closing.size() >= excess) {
                break;
            }
            if (!exempt.contains(entry.getKey())) {
                closing.add(entry.getKey());
            }
        }
        return closing;
    }

    /** Forgets the session's NPC conversations and their choices. */
    private static void forgetNpcConversations() {
        NPC_SPOKEN.clear();
        npcTurn = 0L;
        Iterator<ChatTab> notified = NOTIFICATIONS.keySet().iterator();
        while (notified.hasNext()) {
            if (notified.next().isNpc()) {
                notified.remove();
            }
        }
        Iterator<ChatTab> fed = FEED_CHOICES.keySet().iterator();
        while (fed.hasNext()) {
            if (fed.next().isNpc()) {
                fed.remove();
            }
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
    /**
     * A Show in Feed line. Not {@code feed}: the feed's position line is
     * written alone under that word, which takes every line it begins
     * with ({@link WindowLayoutStore#saveLine}).
     */
    private static final String FEED_CHOICE = "feedchoice";
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
     * notify everything ooc
     * notify mentions whisper:Steve|Aldric
     * feedchoice nothing server_console
     * </pre>
     *
     * <p>A conversation's Notifications, where it is not the
     * conversation's default, stands on a {@code notify} line: the choice
     * ({@code everything}, {@code mentions} or {@code nothing}) and the
     * tab id. Its Show in Feed, where it is not {@code everything}, stands on a
     * {@code feedchoice} line the same way ({@code mentions} or
     * {@code nothing}). Both name whispers and fellowships too, never an
     * NPC conversation, whose choices end with the session. A whisper or
     * fellowship tab is remembered per place on a line of its own: {@code conversation}, the place, the
     * window and the tab id for one that was open;
     * {@code closedconversation}, the place and the tab id for one closed
     * by hand. These lines are tab-separated, since a name or a place may
     * hold spaces.</p>
     */
    private static final WindowLayoutStore.Part PART = new WindowLayoutStore.Part() {
        @Override
        public void clear() {
            NOTIFICATIONS.clear();
            FEED_CHOICES.clear();
            CLOSED_BY_HAND.clear();
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
            if (line.startsWith(FEED_CHOICE + "\t")) {
                readFeedChoice(line.split("\t"));
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
            ChatLineChoice choice = ChatLineChoice.fromId(fields[1]);
            ChatTab row = ChatTab.row(ChatTab.fromId(fields[2]));
            if (choice != null && row != null && !row.isNpc()
                    && choice != defaultNotification(row)) {
                NOTIFICATIONS.put(row, choice);
            }
        }

        /** A {@code feedchoice} line: the choice, then the tab it is for. */
        private void readFeedChoice(String[] fields) {
            if (fields.length != 3) {
                return;
            }
            ChatLineChoice choice = ChatLineChoice.fromId(fields[1]);
            ChatTab row = ChatTab.row(ChatTab.fromId(fields[2]));
            if (choice != null && row != null && !row.isNpc()
                    && choice != ChatLineChoice.EVERYTHING) {
                FEED_CHOICES.put(row, choice);
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
         * the first window holding a conversation, so a channel added
         * after the file was written is never silently lost; with no such
         * window, one opens for them. Staff talk and the consoles are the
         * exception: they open only by hand, and wait in the {@code +}. A file
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
                if (!OPENED_BY_HAND.contains(tab)) {
                    unplaced.add(tab);
                }
            }
            CLOSED_READ.clear();
            if (unplaced.isEmpty()) {
                return;
            }
            Window holding = firstConversationWindow();
            if (holding == null) {
                ChatTab global = ChatTab.of(ChatChannel.GLOBAL);
                WindowLayout.addWindow(unplaced, unplaced.contains(global)
                        ? global : unplaced.get(0));
            } else {
                WindowLayout.appendTabs(holding, unplaced);
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
            for (ChatTab tab : feedChoiceTabs()) {
                lines.add(FEED_CHOICE + "\t" + feedChoice(tab).id() + "\t"
                        + tab.id());
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
