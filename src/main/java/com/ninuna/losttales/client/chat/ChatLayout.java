package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatFellowship;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.window.PageCategory;
import com.ninuna.losttales.client.window.PinnedWindows;
import com.ninuna.losttales.client.window.View;
import com.ninuna.losttales.client.window.ViewDefaults;
import com.ninuna.losttales.client.window.Views;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowLayoutStore;
import com.ninuna.losttales.client.window.WindowMenus;
import com.ninuna.losttales.client.window.WindowPlacement;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WindowPage;
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
 * <li>The pages the Chat view opens with: Global and OOC, Global in
 * front, and the conversations waiting for a window; and the Fellowships
 * view's conversations ({@link ViewDefaults}).</li>
 * <li>Each conversation's two choices ({@link ChatLineChoice}):
 * which of its lines chime, and which reach the feed.</li>
 * <li>The NPC conversations of the session: only the last few stay open
 * by themselves, and the {@code +} opens the others again.</li>
 * <li>Closed channels: a plain channel in no window is gone from every
 * window, its history untouched, restorable into any window, and back
 * with its next message, but for Operator Chat and the consoles, which
 * open only by hand ({@link #opensByItself}). Closing is about pages
 * only: a closed channel keeps receiving, keeps its history, its unread
 * counts and its preferences.</li>
 * <li>Whisper pages, remembered per server.</li>
 * <li>Where the feed stands, whether the picker strip is folded,
 * and each conversation's timestamp area and member list.</li>
 * </ul>
 *
 * <p>All of it is written into the layout file as the chat's part of it.
 * Message history stays in vanilla's chat, per-line pages and unread
 * counts in {@link ClientChatChannelViews}, channel availability in
 * {@link ClientChatChannelState}.</p>
 */
public final class ChatLayout {
    /** The pages the Chat view's window opens with, the first in front. */
    private static final List<ConversationPage> FIRST_PAGES =
            Collections.unmodifiableList(Arrays.asList(
                    ConversationPage.of(ChatChannel.GLOBAL),
                    ConversationPage.of(ChatChannel.OOC)));
    /** The two consoles, in the order the Consoles view's window holds them. */
    private static final List<ConversationPage> CONSOLES =
            Collections.unmodifiableList(Arrays.asList(
                    ConversationPage.of(ChatChannel.CONSOLE),
                    ConversationPage.of(ChatChannel.SERVER_LOG)));
    /**
     * The channels that never open by themselves, only by hand: staff talk
     * and the consoles, which speak often and are not conversations. They
     * wait on the New Page.
     */
    private static final List<ConversationPage> OPENED_BY_HAND =
            Collections.unmodifiableList(Arrays.asList(
                    ConversationPage.of(ChatChannel.OPERATOR),
                    ConversationPage.of(ChatChannel.CONSOLE),
                    ConversationPage.of(ChatChannel.SERVER_LOG)));
    /**
     * Each conversation's notification choice, where it is not the
     * conversation's default ({@link #defaultNotification}).
     *
     * <p>This and the feed choices below hold row entries. A scoped channel's conversations share the one entry the
     * row holds, so a choice for the Faction page is the channel's rather
     * than whichever faction happened to be on screen. Every accessor
     * normalises through {@link ConversationPage#row}, so a caller holding a
     * line's own page asks the same question.</p>
     */
    private static final Map<ConversationPage, ChatLineChoice> NOTIFICATIONS =
            new HashMap<ConversationPage, ChatLineChoice>();
    /** Each conversation's feed choice, where it is not Everything. */
    private static final Map<ConversationPage, ChatLineChoice> FEED_CHOICES =
            new HashMap<ConversationPage, ChatLineChoice>();
    /** How many NPC conversations the session remembers having spoken. */
    static final int MAX_NPC_CONVERSATIONS = 64;
    /**
     * The NPC conversations that have spoken this session, the one quiet
     * longest first, each with the turn it last spoke on; the
     * {@link #MAX_NPC_CONVERSATIONS} most recent at most. For the
     * session only.
     */
    private static final Map<ConversationPage, Long> NPC_SPOKEN =
            new LinkedHashMap<ConversationPage, Long>();
    /** The turn the last NPC line of the session spoke on. */
    private static long npcTurn;
    /**
     * The whispers closed by hand. Each is closed only until somebody
     * speaks in it again: a replay may not bring it back, a live line
     * does ({@link #reopenConversation}).
     */
    private static final Set<ConversationPage> CLOSED_BY_HAND = new HashSet<ConversationPage>();
    /**
     * Whisper and fellowship pages remembered per place, by the session's
     * server key: where each was open, in order, and which were closed by
     * hand. A conversation belongs to the server it was held on, so
     * arriving elsewhere leaves it in the file for the next visit.
     */
    private static final Map<String, List<String[]>> CONVERSATIONS =
            new LinkedHashMap<String, List<String[]>>();
    private static final Map<String, Set<String>> CLOSED_CONVERSATIONS =
            new LinkedHashMap<String, Set<String>>();
    /** The place whose whisper and fellowship pages are on screen; empty before a join. */
    private static String conversationsPlace = "";
    /**
     * Each conversation's timestamp area and member list, by its id, only
     * where they differ from how they first are.
     */
    private static final Map<String, Panels> PANELS_BY_PAGE = new HashMap<String, Panels>();
    /**
     * Conversations that tried to open by themselves while the Chat view
     * had no window: the view's window brings them as it opens. For the
     * session only.
     */
    private static final Set<ConversationPage> WAITING = new LinkedHashSet<ConversationPage>();
    /**
     * The window the chat last opened for a conversation because none
     * would take it: the ones after it join it, and the chat's key brings
     * it forward while anything in it waits unread. For the session only;
     * null for none.
     */
    private static String collectingWindowId;
    /**
     * The chat feed's place once the player has placed it, percent of its
     * travel; until then ({@link #feedPlaced} false) it stands at its
     * default place, centred over the hotbar's rows.
     */
    private static double feedOffsetX = 50.0D;
    private static double feedOffsetY = 100.0D;
    private static boolean feedPlaced;
    private static boolean toolbarCollapsed;
    private static boolean installed;

    /** A conversation's timestamp area and member list, as the player left them. */
    private static final class Panels {
        boolean areaHidden;
        boolean membersHidden;
        /** The member list's width in the chat's pixels; 0 for its own. */
        double membersWidth;

        boolean isAsFirst() {
            return !this.areaHidden && !this.membersHidden
                    && this.membersWidth <= 0.0D;
        }
    }

    private ChatLayout() {}

    /**
     * Puts the chat into the window layout: the windows a new player
     * starts with, reading its pages back from the layout file, and its
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
        WindowPage.addReader(new WindowPage.Reader() {
            @Override
            public WindowPage read(String id) {
                return ConversationPage.fromId(id);
            }
        });
        WindowLayout.setDefaults(DEFAULT_WINDOWS);
        WindowMenus.registerShared(ChatSubWindows.INBOX, ChatInbox.SOURCE);
        ViewDefaults.addPages(ViewDefaults.CHAT, new ViewDefaults.Pages() {
            @Override
            public List<? extends WindowPage> pages() {
                return chatViewPages();
            }
        });
        ViewDefaults.addPages(ViewDefaults.FELLOWSHIP_CHATS,
                new ViewDefaults.Pages() {
                    @Override
                    public List<? extends WindowPage> pages() {
                        return fellowshipConversations();
                    }
                });
        // The Consoles view opens with both consoles the player may read:
        // the Server Log only with its capability.
        WindowLayout.addViewPages(new WindowLayout.ViewPages() {
            @Override
            public List<? extends WindowPage> pagesOf(PageCategory category) {
                List<ConversationPage> pages = new ArrayList<ConversationPage>();
                if (category == PageCategory.CONSOLES) {
                    for (ConversationPage console : CONSOLES) {
                        if (ClientChatChannelState.isAvailable(console)) {
                            pages.add(console);
                        }
                    }
                }
                return pages;
            }
        });
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
     * A new player's windows: the Chat view's, Global and OOC at the
     * default place, locked. Every other channel starts closed;
     * Proximity, Faction and Fellowship open with their first line, and
     * Operator and the consoles wait in the {@code +} until opened by hand.
     * From then on the layout is whatever the player makes of it.
     */
    static final Runnable DEFAULT_WINDOWS = new Runnable() {
        @Override
        public void run() {
            WindowLayout.buildView(Views.of(PageCategory.CHANNELS));
        }
    };

    /**
     * What the Chat view's window opens with: Global and OOC, then every
     * conversation that tried to open while the view had no window.
     */
    private static synchronized List<ConversationPage> chatViewPages() {
        List<ConversationPage> pages = new ArrayList<ConversationPage>(FIRST_PAGES);
        Iterator<ConversationPage> waiting = WAITING.iterator();
        while (waiting.hasNext()) {
            ConversationPage page = waiting.next();
            if (WindowLayout.isOpen(page)) {
                waiting.remove();
            } else if (!pages.contains(page)) {
                pages.add(page);
            }
        }
        return pages;
    }

    /** The conversation of every fellowship of the character played, as the Fellowships view opens with them. */
    private static List<ConversationPage> fellowshipConversations() {
        List<ConversationPage> conversations = new ArrayList<ConversationPage>();
        for (ChatFellowship fellowship : ClientChatIdentitySelection.fellowships()) {
            ConversationPage page = ConversationPage.of(ChatChannel.FELLOWSHIP,
                    ConversationPage.ownerKeyOf(fellowship.getId()));
            if (page != null && page.isFellowship()) {
                conversations.add(page);
            }
        }
        return conversations;
    }

    /**
     * The conversation in front of the window of {@code view} brought to
     * the front last that has one in front; null for none.
     */
    public static synchronized ConversationPage frontIn(View view) {
        List<Window> stacked = WindowLayout.stacked();
        for (int index = stacked.size() - 1; index >= 0; index--) {
            Window window = stacked.get(index);
            ConversationPage front = ConversationPage.frontOf(window);
            if (WindowLayout.viewOf(window) == view && front != null
                    && ClientChatChannelState.isSelectable(front)) {
                return front;
            }
        }
        return null;
    }

    /* ---- Where channels stand ---- */

    /** Whether the page's row entry is in a window. */
    public static synchronized boolean isOpen(ConversationPage page) {
        return WindowLayout.windowOf(ConversationPage.row(page)) != null;
    }

    public static synchronized boolean isOpen(ChatChannel channel) {
        return isOpen(ConversationPage.of(channel));
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
     * The notification choice a conversation starts with: Everything for
     * a whisper, with a player or an NPC, and Only Mentions for every
     * channel, the consoles and Operator included.
     */
    static ChatLineChoice defaultNotification(ConversationPage page) {
        return page != null && page.isWhisper()
                ? ChatLineChoice.EVERYTHING : ChatLineChoice.ONLY_MENTIONS;
    }

    /**
     * The row entry what is opened and closed by hand is kept by: the
     * same for every copy of the conversation.
     */
    private static ConversationPage sharedRow(ConversationPage page) {
        ConversationPage row = ConversationPage.row(page);
        return row == null ? null : row.conversation();
    }

    /**
     * The conversation's notification choice, one for all its copies
     * (CHAT-02): its default unless the player chose another in a copy.
     */
    public static synchronized ChatLineChoice notification(ConversationPage page) {
        ConversationPage row = sharedRow(page);
        ChatLineChoice choice = row == null ? null : NOTIFICATIONS.get(row);
        return choice == null ? defaultNotification(row) : choice;
    }

    /**
     * Chooses which of the conversation's new lines chime. Nothing else
     * changes: every line still counts unread, the feed still shows what
     * its feed choice lets through, and the history is untouched.
     */
    public static synchronized void setNotification(ConversationPage page,
                                                    ChatLineChoice choice) {
        ConversationPage row = sharedRow(page);
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

    /** The conversation's feed choice, one for all its copies: Everything unless the player chose another. */
    public static synchronized ChatLineChoice feedChoice(ConversationPage page) {
        ConversationPage row = sharedRow(page);
        ChatLineChoice choice = row == null ? null : FEED_CHOICES.get(row);
        return choice == null ? ChatLineChoice.EVERYTHING : choice;
    }

    /**
     * Chooses which of the conversation's lines reach the feed.
     * Nothing else changes: every line still counts unread and chimes as
     * its notification choice says.
     */
    public static synchronized void setFeedChoice(ConversationPage page,
                                                  ChatLineChoice choice) {
        ConversationPage row = sharedRow(page);
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
     * page writes its name in italics.
     */
    public static synchronized boolean isMuted(ConversationPage page) {
        return notification(page) == ChatLineChoice.NOTHING;
    }

    /**
     * Whether a new line of the conversation chimes: when its Notifications
     * choice lets it through — any line where it is Everything, one
     * {@code addressed} to the player, a mention or a reply, where it is
     * Only Mentions. The caller leaves out the player's own lines and
     * replayed ones; Do Not Disturb holds the chime still.
     */
    public static synchronized boolean chimes(ConversationPage page, boolean addressed) {
        return page != null && notification(page).lets(addressed);
    }

    /**
     * Every open copy showing {@code conversation}: a line's own
     * conversation, held as one of this player's identities. The copies
     * of a plain channel all show it; a Faction copy shows it while it
     * reads that faction, a whisper copy while it speaks as the identity
     * the conversation is held as.
     */
    static synchronized List<ConversationPage> readersOf(ConversationPage conversation) {
        List<ConversationPage> readers = new ArrayList<ConversationPage>();
        ConversationPage filed = ConversationPage.viewedConversation(conversation);
        if (filed == null) {
            return readers;
        }
        for (WindowPage each : WindowLayout.order()) {
            ConversationPage copy = ConversationPage.from(each);
            if (copy != null && filed.equals(ConversationPage.viewedConversation(copy))) {
                readers.add(copy);
            }
        }
        return readers;
    }

    /**
     * The open copy showing {@code conversation}, the one used last; else
     * one opened for it, as a line arriving or a link followed opens it:
     * the row entry where no copy of it stands, else a new copy beside the
     * others, either speaking as the identity the conversation is held as
     * ({@link ClientChatIdentities#holdAs}). One the player {@code asked}
     * for opens in the view the screen shows ({@link #openHere}); a line's
     * opens in the Chat view, or waits for it to open ({@link #openTab}).
     * Null where no window takes it.
     */
    public static synchronized ConversationPage openReader(ConversationPage conversation,
                                                           String windowId,
                                                           boolean asked) {
        List<ConversationPage> readers = readersOf(conversation);
        if (!readers.isEmpty()) {
            ConversationPage used = ConversationPage.from(
                    WindowLayout.lastUsed(readers.get(0)));
            return used != null && readers.contains(used) ? used : readers.get(0);
        }
        ConversationPage row = ConversationPage.row(conversation);
        ConversationPage opened = WindowLayout.isOpen(row)
                ? ConversationPage.from(WindowLayout.openCopy(row, windowId))
                : asked ? openHere(row, windowId) : openTab(row, windowId);
        String identity = identityHolding(conversation);
        if (opened != null && identity != null) {
            ClientChatIdentities.holdAs(opened, identity);
        }
        return opened;
    }

    /**
     * Which of this player's identities a copy speaks as to show
     * {@code conversation}: a whisper's own, a faction's one of the
     * identities read in it, the one played first; null to follow the
     * character played.
     */
    private static String identityHolding(ConversationPage conversation) {
        if (conversation.isWhisper()) {
            return conversation.getOwnerKey();
        }
        if (conversation.getChannel() == null || !conversation.getChannel().isScoped()
                || conversation.getOwnerKey().length() == 0) {
            return null;
        }
        if (conversation.getOwnerKey().equals(ClientChatChannelState
                .playedScopeKey(conversation.getChannel()))) {
            return null;
        }
        for (ClientChatIdentities.Identity identity : ClientChatIdentities.inUse()) {
            String key = ConversationPage.ownerKeyOf(identity.characterId);
            if (conversation.getOwnerKey().equals(ClientChatChannelState
                    .scopeOfIdentity(conversation.getChannel(), key))) {
                return key;
            }
        }
        return null;
    }

    /**
     * A copy opening anew keeps none of an earlier copy's view; the
     * conversation's two settings stay, shared by its copies.
     */
    static synchronized void forgetCopy(ConversationPage copy) {
        ConversationPage row = ConversationPage.row(copy);
        if (row != null) {
            PANELS_BY_PAGE.remove(row.id());
        }
    }

    /**
     * The conversations whose notification choice is not their default, the
     * file's order: by id, NPC conversations left out, since they end
     * with the session.
     */
    static synchronized List<ConversationPage> notificationPages() {
        return writtenPages(NOTIFICATIONS.keySet());
    }

    /** The conversations whose feed choice is not Everything, in the same order. */
    static synchronized List<ConversationPage> feedChoicePages() {
        return writtenPages(FEED_CHOICES.keySet());
    }

    private static List<ConversationPage> writtenPages(Set<ConversationPage> pages) {
        List<ConversationPage> result = new ArrayList<ConversationPage>();
        for (ConversationPage page : pages) {
            if (!page.isNpc()) {
                result.add(page);
            }
        }
        Collections.sort(result, new Comparator<ConversationPage>() {
            @Override
            public int compare(ConversationPage a, ConversationPage b) {
                return a.id().compareTo(b.id());
            }
        });
        return result;
    }

    /**
     * Whether a line arriving may open the page while it is closed. Operator
     * Chat and the consoles never do: they open by hand. Nor does a whisper
     * closed by hand, which a live line opens again
     * ({@link #reopenConversation}) and a replay does not.
     */
    public static synchronized boolean opensByItself(ConversationPage page) {
        ConversationPage row = page == null ? null : sharedRow(page);
        return row != null && !OPENED_BY_HAND.contains(row)
                && !CLOSED_BY_HAND.contains(row);
    }

    /**
     * The conversations whose every line the feed carries, in its
     * order, each as the character played reads it, whoever the copies
     * speak as: every channel the player can see whose feed choice lets it through
     * with {@link ChatLineChoice#EVERYTHING}, in presentation order, whether or
     * not it has a page — closing a page hides the page, not the channel's
     * messages — then every open whisper and fellowship conversation the
     * same way. Conversations are read from their open pages only: a closed
     * one is hidden until its next message reopens it. Their typing shows
     * in the feed too.
     */
    public static List<ConversationPage> feedPages() {
        return feedPages(ChatLineChoice.EVERYTHING);
    }

    /**
     * The conversations only whose lines addressed to the player the
     * feed carries, in the same order: those whose feed choice
     * is {@link ChatLineChoice#ONLY_MENTIONS}.
     */
    public static List<ConversationPage> mentionFeedPages() {
        return feedPages(ChatLineChoice.ONLY_MENTIONS);
    }

    /** What the feed shows: see {@link #feedPages} and {@link #mentionFeedPages}. */
    static ChatLineFilter feedFilter() {
        return ChatLineFilter.of(feedPages(), mentionFeedPages());
    }

    private static List<ConversationPage> feedPages(ChatLineChoice choice) {
        Set<ConversationPage> shown = new LinkedHashSet<ConversationPage>();
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            ConversationPage page = ConversationPage.of(channel);
            if (ClientChatChannelState.isAvailable(page)) {
                addToFeed(shown, ConversationPage.playedConversation(page), choice);
            }
        }
        for (WindowPage each : WindowLayout.order()) {
            ConversationPage page = ConversationPage.from(each);
            if (page != null && (page.isWhisper() || page.isFellowship())
                    && ClientChatChannelState.isAvailable(page)) {
                addToFeed(shown, ConversationPage.playedConversation(page), choice);
            }
        }
        return new ArrayList<ConversationPage>(shown);
    }

    /**
     * Adds the conversation where its feed choice is {@code choice} and no
     * window pinned to the HUD shows a copy of it.
     */
    private static void addToFeed(Set<ConversationPage> shown,
                                  ConversationPage conversation,
                                  ChatLineChoice choice) {
        if (conversation == null || shown.contains(conversation)) {
            return;
        }
        for (ConversationPage reader : readersOf(conversation)) {
            if (PinnedWindows.shows(reader)) {
                return;
            }
        }
        if (feedChoice(conversation) == choice) {
            shown.add(conversation);
        }
    }

    /* ---- Closing and opening ---- */

    /**
     * Removes the page from its window, as {@link WindowLayout#close}
     * does; a whisper closed by hand, its last copy, stays closed through
     * a replay, and comes back with the next live line. The page's choices
     * are untouched: a closed page keeps them for when it is restored.
     */
    public static synchronized boolean close(ConversationPage page) {
        if (!WindowLayout.close(page)) {
            return false;
        }
        if (isRemembered(page) && !isOpen(page)) {
            CLOSED_BY_HAND.add(sharedRow(page));
        }
        if (page.instance() > 1) {
            // A copy past the first ends with its page, its draft with it.
            ClientChatChannelState.setDraft(page, "");
        }
        return true;
    }

    /**
     * The whisper page with the named account as one identity of theirs,
     * empty for the account's own, opened if it is not, as the player asked
     * for it ({@link #openHere}). The front page is left alone. The page is
     * the person's row entry: which conversation it shows follows the
     * identity the chat is read as, which is who speaks in it.
     */
    public static synchronized ConversationPage openWhisper(
            String partner, String identity, String preferredWindowId) {
        return openHere(ConversationPage.whisper(partner, identity), preferredWindowId);
    }

    /**
     * Opens a conversation's page where it belongs
     * ({@link WindowLayout#openTab}): the window asked for, else another
     * of the Chat view, never a locked one, else a window of its own; and
     * answers it as the layout holds it. The row holds one entry per
     * channel and per person, so a conversation held as one character
     * opens as its row entry: a line's own page never stands in a window
     * beside the one that shows it. Null while the Chat view has no
     * window: the page waits for the view to open.
     */
    public static synchronized ConversationPage openTab(ConversationPage page,
                                               String preferredWindowId) {
        ConversationPage row = ConversationPage.row(page);
        int windows = WindowLayout.windows().size();
        ConversationPage opened = ConversationPage.from(WindowLayout.openTab(row,
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
    public static synchronized ConversationPage waitingInOwnWindow() {
        Window window = WindowLayout.window(collectingWindowId);
        if (window == null) {
            return null;
        }
        ConversationPage front = ConversationPage.from(window.getActiveTab());
        if (waits(front)) {
            return front;
        }
        for (WindowPage each : window.getTabs()) {
            ConversationPage page = ConversationPage.from(each);
            if (waits(page)) {
                return page;
            }
        }
        return null;
    }

    private static boolean waits(ConversationPage page) {
        return ClientChatChannelState.isSelectable(page)
                && ClientChatChannelViews.hasUnread(page);
    }

    /**
     * Opens a console as the command key does, in the Consoles view: the
     * view opening with its defaults where it has no window, and the
     * console opening there again where the view no longer holds it
     * ({@link WindowLayout#openInView}).
     */
    public static synchronized ConversationPage openConsoles(
            ConversationPage console) {
        return ConversationPage.from(WindowLayout.openInView(
                ConversationPage.row(console),
                Views.of(PageCategory.CONSOLES)));
    }

    /**
     * Opens a conversation the player asked for — a whisper, a link, a
     * jump to a line — where a page opened by hand opens: in the view the
     * screen shows ({@link WindowLayout#openByHand}).
     */
    public static synchronized ConversationPage openHere(ConversationPage page,
                                                String preferredWindowId) {
        return ConversationPage.from(WindowLayout.openByHand(
                ConversationPage.row(page), preferredWindowId));
    }

    /* ---- Whisper and fellowship pages per place ---- */

    /** Whether a page is a conversation the layout remembers per place: a player's whisper or a fellowship's. */
    private static boolean isRemembered(ConversationPage page) {
        return page != null && page.isPlaceConversation();
    }

    /**
     * Writes a place's whisper and fellowship pages into the remembered set: the open
     * ones with their windows, in order, and the ones closed by hand.
     */
    static synchronized void rememberConversations(String serverKey) {
        if (serverKey == null || serverKey.length() == 0) {
            return;
        }
        List<String[]> open = new ArrayList<String[]>();
        for (Window window : WindowLayout.windows()) {
            for (WindowPage each : window.getTabs()) {
                ConversationPage page = ConversationPage.from(each);
                if (isRemembered(page)) {
                    open.add(new String[] {window.getId(), page.id()});
                }
            }
        }
        Set<String> closed = new LinkedHashSet<String>();
        for (ConversationPage page : CLOSED_BY_HAND) {
            closed.add(page.id());
        }
        CONVERSATIONS.put(serverKey, open);
        CLOSED_CONVERSATIONS.put(serverKey, closed);
        ClientChatIdentities.rememberVoices(serverKey);
    }

    /**
     * Opens the place's remembered whisper and fellowship pages where they were, and
     * marks the ones closed by hand so a replay does not bring them
     * back. Called as the client connects, before anything is replayed;
     * a page already open is left as it is.
     */
    public static synchronized void restoreConversations(String serverKey) {
        conversationsPlace = serverKey == null ? "" : serverKey;
        if (conversationsPlace.length() == 0) {
            return;
        }
        Set<String> closed = CLOSED_CONVERSATIONS.get(conversationsPlace);
        if (closed != null) {
            for (String id : closed) {
                ConversationPage page = ConversationPage.fromId(id);
                if (isRemembered(page)) {
                    CLOSED_BY_HAND.add(page);
                }
            }
        }
        List<String[]> open = CONVERSATIONS.get(conversationsPlace);
        if (open != null) {
            for (String[] entry : open) {
                ConversationPage page = ConversationPage.fromId(entry[1]);
                if (isRemembered(page) && !WindowLayout.holds(page)) {
                    CLOSED_BY_HAND.remove(page.conversation());
                    putBack(page, entry[0]);
                }
            }
        }
        ClientChatIdentities.restoreVoices(conversationsPlace);
    }

    /**
     * Puts a remembered conversation back in the window it stood in, locked or
     * not: that is the layout as the player left it, not a page arriving.
     * With that window gone it opens as any conversation does.
     */
    private static void putBack(ConversationPage page, String windowId) {
        Window window = WindowLayout.window(windowId);
        if (window == null) {
            openTab(page, null);
        } else {
            WindowLayout.appendTabs(window, Collections.singletonList(page));
        }
    }

    /**
     * A live line reopens a conversation closed by hand, a whisper (a
     * player's or an NPC's) or a fellowship's: it was closed only until
     * somebody spoke in it again, and opens speaking as the identity the
     * line's conversation is held as. Null for anything else.
     */
    public static synchronized ConversationPage reopenConversation(ConversationPage page,
                                                          String preferredWindowId) {
        if (page == null || !(page.isWhisper() || page.isFellowship())) {
            return null;
        }
        CLOSED_BY_HAND.remove(sharedRow(page));
        return openReader(page, preferredWindowId, false);
    }

    /**
     * Closes every whisper, NPC and fellowship page: a conversation ends
     * with the session it was held in, and so does its page — the place's
     * whisper and fellowship pages are remembered first, open and closed by
     * hand alike, and come back on the next visit, each with its choices. The NPC
     * conversations of the session go, with their choices. A window left
     * empty goes, the last one included. The place is left behind with
     * them, so the save that follows keeps what was remembered of it.
     */
    public static synchronized void closeConversations() {
        rememberConversations(conversationsPlace);
        conversationsPlace = "";
        WAITING.clear();
        collectingWindowId = null;
        forgetNpcConversations();
        CLOSED_BY_HAND.clear();
        List<WindowPage> removed = WindowLayout.removeTabs(
                new WindowLayout.TabFilter() {
                    @Override
                    public boolean matches(WindowPage page) {
                        ConversationPage conversation = ConversationPage.from(page);
                        return conversation != null
                                && (conversation.isWhisper()
                                        || conversation.isFellowship());
                    }
                });
        if (!removed.isEmpty()) {
            WindowLayout.persist();
        }
    }

    /**
     * Takes the conversation of every fellowship no character of the
     * account is in any more out of every window, locked or not, each of
     * its copies, and so out of the layout file with what it kept there:
     * its panels, its two settings, a close by hand (WIN-18). While any
     * character of the account is still in it, it stays, hidden unless the
     * character played is in it.
     */
    public static synchronized void dropFellowshipsLeft() {
        List<WindowPage> removed = WindowLayout.removeTabs(
                new WindowLayout.TabFilter() {
                    @Override
                    public boolean matches(WindowPage page) {
                        return isLeftFellowship(ConversationPage.from(page));
                    }
                });
        boolean changed = !removed.isEmpty();
        for (WindowPage page : removed) {
            ConversationPage copy = ConversationPage.from(page);
            copy.forgetCopy();
            ConversationPage row = ConversationPage.row(copy);
            if (row != null) {
                PANELS_BY_PAGE.remove(row.id());
            }
        }
        Iterator<ConversationPage> closed = CLOSED_BY_HAND.iterator();
        while (closed.hasNext()) {
            if (isLeftFellowship(closed.next())) {
                closed.remove();
                changed = true;
            }
        }
        Iterator<ConversationPage> waiting = WAITING.iterator();
        while (waiting.hasNext()) {
            if (isLeftFellowship(waiting.next())) {
                waiting.remove();
            }
        }
        changed |= forgetLeft(NOTIFICATIONS.keySet().iterator());
        changed |= forgetLeft(FEED_CHOICES.keySet().iterator());
        if (changed) {
            WindowLayout.persist();
        }
    }

    /** Whether a page is the conversation of a fellowship no character of the account is in. */
    private static boolean isLeftFellowship(ConversationPage page) {
        return page != null && page.isFellowship()
                && !ClientChatIdentitySelection.keepsFellowship(page.getOwnerKey());
    }

    /** Takes every left fellowship's conversation out of a setting's rows; answers whether any went. */
    private static boolean forgetLeft(Iterator<ConversationPage> rows) {
        boolean any = false;
        while (rows.hasNext()) {
            if (isLeftFellowship(rows.next())) {
                rows.remove();
                any = true;
            }
        }
        return any;
    }

    /* ---- NPC conversations ---- */

    /**
     * Notes that an NPC conversation spoke: the session keeps the
     * {@link #MAX_NPC_CONVERSATIONS} that spoke last, so the {@code +}
     * can open one again once it is closed.
     */
    public static synchronized void noteNpcSpoke(ConversationPage page) {
        if (page == null || !page.isNpc()) {
            return;
        }
        NPC_SPOKEN.remove(page);
        NPC_SPOKEN.put(page, Long.valueOf(++npcTurn));
        Iterator<ConversationPage> quietest = NPC_SPOKEN.keySet().iterator();
        while (NPC_SPOKEN.size() > MAX_NPC_CONVERSATIONS && quietest.hasNext()) {
            quietest.next();
            quietest.remove();
        }
    }

    /**
     * The NPC conversations of the session, the one that spoke last first:
     * what the New Page offers; with {@code closedOnly}, those no
     * window holds a copy of.
     */
    static synchronized List<ConversationPage> npcConversations(boolean closedOnly) {
        List<ConversationPage> result = new ArrayList<ConversationPage>();
        for (ConversationPage page : NPC_SPOKEN.keySet()) {
            if (!(closedOnly && isOpen(page))) {
                result.add(page);
            }
        }
        Collections.reverse(result);
        return result;
    }

    /**
     * Closes NPC pages while more are open than {@code limit} allows,
     * the ones quiet longest first, after {@code spoke}'s page opened by
     * itself. Kept open: the page that spoke, a page in front of its
     * window and a page holding a draft. A page leaves as one that ends
     * by itself does, from a locked window too, and the {@code +} opens
     * it again.
     */
    public static synchronized void closeQuietNpcConversations(ConversationPage spoke,
                                                               int limit) {
        Map<ConversationPage, Long> open = new LinkedHashMap<ConversationPage, Long>();
        Set<ConversationPage> exempt = new HashSet<ConversationPage>();
        if (spoke != null) {
            exempt.add(spoke);
        }
        for (Window window : WindowLayout.windows()) {
            for (WindowPage each : window.getTabs()) {
                ConversationPage page = ConversationPage.from(each);
                if (page == null || !page.isNpc()) {
                    continue;
                }
                Long turn = NPC_SPOKEN.get(page.conversation());
                open.put(page, turn == null ? Long.valueOf(0L) : turn);
                if (each.equals(window.getActiveTab())
                        || ClientChatChannelState.getDraft(page).length() > 0) {
                    exempt.add(page);
                }
            }
        }
        final List<ConversationPage> closing = npcPagesToClose(open, exempt, limit);
        if (closing.isEmpty()) {
            return;
        }
        WindowLayout.removeTabs(new WindowLayout.TabFilter() {
            @Override
            public boolean matches(WindowPage page) {
                return closing.contains(page);
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
    static List<ConversationPage> npcPagesToClose(Map<ConversationPage, Long> open,
                                         Set<ConversationPage> exempt, int limit) {
        List<ConversationPage> closing = new ArrayList<ConversationPage>();
        int excess = open.size() - Math.max(0, limit);
        if (excess <= 0) {
            return closing;
        }
        List<Map.Entry<ConversationPage, Long>> quietFirst =
                new ArrayList<Map.Entry<ConversationPage, Long>>(open.entrySet());
        Collections.sort(quietFirst, new Comparator<Map.Entry<ConversationPage, Long>>() {
            @Override
            public int compare(Map.Entry<ConversationPage, Long> a,
                               Map.Entry<ConversationPage, Long> b) {
                return a.getValue().compareTo(b.getValue());
            }
        });
        for (Map.Entry<ConversationPage, Long> entry : quietFirst) {
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
        Iterator<ConversationPage> notified = NOTIFICATIONS.keySet().iterator();
        while (notified.hasNext()) {
            if (notified.next().isNpc()) {
                notified.remove();
            }
        }
        Iterator<ConversationPage> fed = FEED_CHOICES.keySet().iterator();
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

    /**
     * Whether the player has placed the chat feed; until then it stands at
     * its default place ({@link ChatFeedPlacement}).
     */
    public static synchronized boolean isFeedPlaced() {
        return feedPlaced;
    }

    /** Places the chat feed; {@code persist} false while dragging. */
    public static synchronized void setFeedPosition(double offsetX,
                                                    double offsetY,
                                                    boolean persist) {
        feedOffsetX = clampPercent(offsetX);
        feedOffsetY = clampPercent(offsetY);
        feedPlaced = true;
        if (persist) {
            WindowLayout.persist();
        }
    }

    /** Puts the chat feed back at its default place, and writes it so. */
    public static synchronized void resetFeedPlace() {
        if (feedPlaced) {
            feedPlaced = false;
            feedOffsetX = 50.0D;
            feedOffsetY = 100.0D;
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

    /** The panels as their line writes them: what is put away, then the list's width. */
    private static String describePanels(Panels panels) {
        StringBuilder line = new StringBuilder();
        if (panels.areaHidden) {
            line.append(" area=").append(PUT_AWAY);
        }
        if (panels.membersHidden) {
            line.append(" members=").append(PUT_AWAY);
        }
        if (panels.membersWidth > 0.0D) {
            line.append(" members_width=").append(format(panels.membersWidth));
        }
        return line.toString().trim();
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

    /* ---- A conversation's timestamp area and member list ---- */

    /** The most conversations whose area and list are kept apart from how they first are. */
    static final int MAX_PANELS = 256;

    /** The conversation's panels to change, made where it has none; null for no conversation. */
    private static Panels panelsToChange(ConversationPage page) {
        if (page == null) {
            return null;
        }
        Panels panels = PANELS_BY_PAGE.get(panelsKey(page));
        if (panels == null) {
            if (PANELS_BY_PAGE.size() >= MAX_PANELS) {
                return null;
            }
            panels = new Panels();
            PANELS_BY_PAGE.put(panelsKey(page), panels);
        }
        return panels;
    }

    /**
     * What a conversation's panels are kept by: the id of the page its window
     * holds, so a faction's conversation keeps one set of panels whichever
     * identity reads it.
     */
    private static String panelsKey(ConversationPage page) {
        return ConversationPage.row(page).id();
    }

    /** A conversation's panels as they stand; null while they stand as they first were. */
    private static Panels panelsOf(ConversationPage page) {
        return page == null ? null : PANELS_BY_PAGE.get(panelsKey(page));
    }

    /** Drops panels that stand as they first were, so only changed ones are kept. */
    private static void settle(ConversationPage page, Panels panels) {
        if (panels.isAsFirst()) {
            PANELS_BY_PAGE.remove(panelsKey(page));
        }
    }

    /** Whether the conversation's timestamp area is driven out. */
    public static synchronized boolean isAreaHidden(ConversationPage page) {
        Panels panels = panelsOf(page);
        return panels != null && panels.areaHidden;
    }

    /** Whether the conversation's member list is put away. */
    public static synchronized boolean isMembersHidden(ConversationPage page) {
        Panels panels = panelsOf(page);
        return panels != null && panels.membersHidden;
    }

    /** The width the player gave the conversation's member list, in the chat's pixels; 0 for its own. */
    public static synchronized double getMembersWidth(ConversationPage page) {
        Panels panels = panelsOf(page);
        return panels == null ? 0.0D : panels.membersWidth;
    }

    /**
     * Drives a conversation's timestamp area out, or back in: its window
     * keeps its size and the words take the area's room. Only this
     * conversation's: the one beside it in a split keeps its own. Written
     * to the file.
     */
    public static synchronized boolean setAreaHidden(ConversationPage page,
                                                     boolean hidden) {
        Panels panels = isAreaHidden(page) == hidden ? null : panelsToChange(page);
        if (panels == null) {
            return false;
        }
        panels.areaHidden = hidden;
        settle(page, panels);
        WindowLayout.persist();
        return true;
    }

    /**
     * Puts a conversation's member list away, or brings it out: its window
     * keeps its size and the words take the list's room. Only this
     * conversation's. Written to the file.
     */
    public static synchronized boolean setMembersHidden(ConversationPage page,
                                                        boolean hidden) {
        Panels panels = isMembersHidden(page) == hidden ? null : panelsToChange(page);
        if (panels == null) {
            return false;
        }
        panels.membersHidden = hidden;
        settle(page, panels);
        WindowLayout.persist();
        return true;
    }

    /**
     * A conversation's timestamp area and member list as they first are:
     * both out, the list at its own width. Written to the file.
     */
    public static synchronized void resetPanels(ConversationPage page) {
        if (page != null && PANELS_BY_PAGE.remove(panelsKey(page)) != null) {
            WindowLayout.persist();
        }
    }

    /**
     * Gives a conversation's member list the width its edge was dragged
     * to, in the chat's pixels, written to the file when {@code persist}
     * says so — once, as the drag ends.
     */
    public static synchronized boolean setMembersWidth(ConversationPage page,
                                                       double width,
                                                       boolean persist) {
        Panels panels = panelsToChange(page);
        if (panels == null) {
            return false;
        }
        panels.membersWidth = clampMembersWidth(width);
        settle(page, panels);
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
    private static final String NOTIFY = "notify";
    /**
     * A feed choice line. Not {@code feed}: the feed's position line is
     * written alone under that word, which takes every line it begins
     * with ({@link WindowLayoutStore#saveLine}).
     */
    private static final String FEED_CHOICE = "feedchoice";
    private static final String CONVERSATION = "conversation";
    private static final String VOICE = ClientChatIdentities.VOICE_LINE;
    private static final String CLOSED_CONVERSATION = "closedconversation";
    /** A conversation's timestamp area and member list, where they differ from how they first are. */
    private static final String PANELS = "panels";
    /** What a {@code panels} line says of an area or member list put away. */
    private static final String PUT_AWAY = "hidden";

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * The chat's lines in the layout file:
     *
     * <pre>
     * panels	global	area=hidden members=hidden members_width=90.00
     * feed x=50.00 y=88.00
     * toolbar collapsed=false
     * notify	everything	ooc
     * notify	mentions	whisper:Steve|Aldric
     * feedchoice	nothing	server_log
     * </pre>
     *
     * <p>A channel in no window is closed: no line names it, and it opens
     * with its next line like any other closed channel.</p>
     *
     * <p>A conversation's timestamp area and member list, where either
     * differs from how it first is, stand on a {@code panels} line: the page
     * id, then the area or the list put away and the list's width. Never
     * an NPC conversation's, whose view ends with the session.</p>
     *
     * <p>The {@code feed} line stands only once the player has placed the
     * chat feed; without it the feed stands at its default place.</p>
     *
     * <p>A conversation's notification choice, one for all its copies, where
     * it is not the conversation's default, stands on a {@code notify} line: the choice
     * ({@code everything}, {@code mentions} or {@code nothing}) and the
     * page id. Its feed choice, where it is not {@code everything}, stands on a
     * {@code feedchoice} line the same way ({@code mentions} or
     * {@code nothing}). Both name whispers and fellowships too, never an
     * NPC conversation, whose choices end with the session. A whisper or
     * fellowship page is remembered per place on a line of its own: {@code conversation}, the place, the
     * window and the page id for one that was open;
     * {@code closedconversation}, the place and the page id for one closed
     * by hand. Who a copy speaks as stands on a {@code voice} line per
     * place: the place, the copy's page id, the character's id or {@code -},
     * and {@code narrator} or {@code -} ({@link ClientChatIdentities#describeVoices}).
     * These lines and the {@code panels}, {@code notify} and
     * {@code feedchoice} lines are page-separated, since a name or a place
     * may hold spaces; {@code feed} and {@code toolbar} lines are
     * space-separated.</p>
     */
    private static final WindowLayoutStore.Part PART = new WindowLayoutStore.Part() {
        @Override
        public void clear() {
            NOTIFICATIONS.clear();
            FEED_CHOICES.clear();
            CLOSED_BY_HAND.clear();
            CONVERSATIONS.clear();
            CLOSED_CONVERSATIONS.clear();
            ClientChatIdentities.clearKept();
            conversationsPlace = "";
            PANELS_BY_PAGE.clear();
            feedOffsetX = 50.0D;
            feedOffsetY = 100.0D;
            feedPlaced = false;
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
            if (line.startsWith(VOICE + "\t")) {
                ClientChatIdentities.readVoice(line.split("\t", -1));
                return true;
            }
            if (line.startsWith(FEED_CHOICE + "\t")) {
                readFeedChoice(line.split("\t"));
                return true;
            }
            if (line.startsWith(PANELS + "\t")) {
                readView(line.split("\t"));
                return true;
            }
            String[] parts = line.split("\\s+");
            String key = parts[0];
            if (FEED.equals(key)) {
                feedPlaced = true;
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

        /** A {@code notify} line: the choice, then the page it is for. */
        private void readNotification(String[] fields) {
            if (fields.length != 3) {
                return;
            }
            ChatLineChoice choice = ChatLineChoice.fromId(fields[1]);
            ConversationPage row = sharedRow(ConversationPage.fromId(fields[2]));
            if (choice != null && row != null && !row.isNpc()
                    && choice != defaultNotification(row)) {
                NOTIFICATIONS.put(row, choice);
            }
        }

        /** A {@code panels} line: the page it is for, then its area, its list and the list's width. */
        private void readView(String[] fields) {
            ConversationPage page = fields.length == 3
                    ? ConversationPage.row(ConversationPage.fromId(fields[1])) : null;
            Panels panels = page == null || page.isNpc() ? null : panelsToChange(page);
            if (panels == null) {
                return;
            }
            for (String part : fields[2].split("\\s+")) {
                int equals = part.indexOf('=');
                String key = equals < 0 ? part : part.substring(0, equals);
                String value = equals < 0 ? "" : part.substring(equals + 1);
                if ("area".equals(key)) {
                    panels.areaHidden = PUT_AWAY.equalsIgnoreCase(value);
                } else if ("members".equals(key)) {
                    panels.membersHidden = PUT_AWAY.equalsIgnoreCase(value);
                } else if ("members_width".equals(key)) {
                    panels.membersWidth = clampMembersWidth(
                            WindowLayoutStore.parseDouble(value));
                }
            }
            settle(page, panels);
        }

        /** A {@code feedchoice} line: the choice, then the page it is for. */
        private void readFeedChoice(String[] fields) {
            if (fields.length != 3) {
                return;
            }
            ChatLineChoice choice = ChatLineChoice.fromId(fields[1]);
            ConversationPage row = sharedRow(ConversationPage.fromId(fields[2]));
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
        public void describe(List<String> lines) {
            if (feedPlaced) {
                lines.add(feedLine());
            }
            lines.add(TOOLBAR + " collapsed=" + toolbarCollapsed);
            for (ConversationPage page : notificationPages()) {
                lines.add(NOTIFY + "\t" + notification(page).id() + "\t"
                        + page.id());
            }
            for (ConversationPage page : feedChoicePages()) {
                lines.add(FEED_CHOICE + "\t" + feedChoice(page).id() + "\t"
                        + page.id());
            }
            for (Map.Entry<String, Panels> each : PANELS_BY_PAGE.entrySet()) {
                ConversationPage page = ConversationPage.fromId(each.getKey());
                if (page != null && !page.isNpc()) {
                    lines.add(PANELS + "\t" + each.getKey() + "\t"
                            + describePanels(each.getValue()));
                }
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
            ClientChatIdentities.describeVoices(lines);
        }
    };
}
