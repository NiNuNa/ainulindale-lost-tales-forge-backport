package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelSuggester;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatNarrator;
import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.chat.ChatReportReason;
import com.ninuna.losttales.chat.ChatStatusLine;
import com.ninuna.losttales.client.input.LostTalesKeyPress;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatDeletePacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.network.packet.LostTalesChatReportPacket;
import com.ninuna.losttales.permission.LostTalesCapability;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * Every menu the chat screen opens, each in a small window of its own, and
 * what its rows do: a tab's settings, a window's own menu and the palette
 * its colour rows open, the tab search, the {@code +} menu of closed
 * channels and conversations, the menu over a message and its report, the
 * menu over a person, and the character menu with its status line — plus
 * the facts the message menu reads off the drawn lines. Each kind has one
 * window. A control opens it where the popup it replaced hung, or where
 * the player left its kind, and turns it to what it was pressed for while
 * it stands; the same control pressed again puts it away. A row that acts
 * closes its window; a switch, a pick, a question, or a row that opens
 * another window leaves it standing.
 */
final class ChatScreenMenus {
    /** Marks a search row that jumps to a tab already open. */
    private static final String ENTRY_OPEN_PREFIX = "open:";
    private static final String ENTRY_MESSAGE = "message_player";
    private static final String ENTRY_REPLY = "reply";
    private static final String ENTRY_REACT = "react";
    private static final String ENTRY_COPY = "copy";
    static final String ENTRY_COPY_LINK = "copy_link";
    private static final String ENTRY_EDIT = "edit";
    private static final String ENTRY_DELETE = "delete";
    private static final String ENTRY_REPORT = "report";
    /** A reason's row in the report: this and the reason's name. */
    private static final String ENTRY_REPORT_REASON_PREFIX = "report:";
    /** The second half of deleting: the row that is the confirmation. */
    private static final String ENTRY_DELETE_CONFIRM = "delete_confirm";
    private static final String ENTRY_IGNORE = "ignore_account";
    private static final String ENTRY_IGNORE_IDENTITY = "ignore_identity";
    /**
     * What an operator's rows are drawn in: the Operator channel's own
     * crimson, so an action that reaches beyond this player's words is
     * told apart from the rest of the menu at a glance.
     */
    private static final int OPERATOR_ACTION_COLOR =
            ChatChannel.OPERATOR.getDisplayColor();
    /** Operators only: the server mute, put in the bar to be completed. */
    private static final String ENTRY_MUTE_ACCOUNT = "mute_account";
    private static final String ENTRY_UNMUTE_ACCOUNT = "unmute_account";
    private static final String ENTRY_MUTE = "mute";
    private static final String ENTRY_PINGS = "pings";
    private static final String ENTRY_HIDE = "hide";
    private static final String ENTRY_DETACH = "detach";
    private static final String ENTRY_MARK_READ = "mark_read";
    private static final String ENTRY_NARRATOR = "characters:narrator";
    /** The start of a status's row id in the character menu. */
    private static final String ENTRY_STATUS_PREFIX = "characters:status:";
    private static final String ENTRY_STATUS_LINE = "characters:status_line";
    /** The start of a character's row id in the character menu. */
    private static final String ENTRY_CHARACTER_PREFIX = "characters:char:";
    private static final String ENTRY_STATUS_LINE_KEEP = "status_line:keep";
    private static final String ENTRY_STATUS_LINE_CLEAR = "status_line:clear";
    private static final String ENTRY_JUMP_UNREAD = "jump_unread";
    private static final String ENTRY_WINDOW_UNSTICK = "window_unstick";
    private static final String ENTRY_WINDOW_RESET = "window_reset";
    /** The window menu's last row, which opens Chat Settings beside it. */
    private static final String ENTRY_CHAT_SETTINGS = "chat_settings";
    /** How often an open menu reads its rows again. */
    private static final long REFRESH_NANOS = 500L * 1000000L;

    /**
     * The message a message menu or a report is about, resolved as the
     * menu opened, while the pointer was still on the line: by the time a
     * row is taken the pointer is on the window.
     */
    static final class MessageAim {
        final int chatLineId;
        final long messageId;
        /** The message's words, as the line reads. */
        final String text;
        final String account;
        /** The identity the message was signed with: who a whisper reaches. */
        final String identity;
        /** Whether it came over the Discord bridge, where nobody can be reached. */
        final boolean fromDiscord;
        /** The sender's account, when the line still has its packet; else null. */
        final UUID senderId;
        /** The tab the line lives in. */
        final ChatTab tab;
        /** The window whose message toolbar opened the menu; null for a right click. */
        final String toolbarWindowId;
        /** Whether the menu is asking before the message is taken back. */
        boolean confirmingDelete;

        MessageAim(int chatLineId, long messageId, String text, String account,
                   String identity, boolean fromDiscord, UUID senderId,
                   ChatTab tab, String toolbarWindowId) {
            this.chatLineId = chatLineId;
            this.messageId = messageId;
            this.text = text;
            this.account = account;
            this.identity = identity;
            this.fromDiscord = fromDiscord;
            this.senderId = senderId;
            this.tab = tab;
            this.toolbarWindowId = toolbarWindowId;
        }
    }

    private final ChatTabActions tabActions;
    private final ChatComposer composer;
    private final ChatNoticeSink notices;
    /** What the Chat Settings window's rows are and do. */
    private final ChatSettings settings;
    /** Each kind's menu; one that came back with the chat is the one it was. */
    private final Map<ChatSmallWindowKind, ChatMenu> menus =
            new EnumMap<ChatSmallWindowKind, ChatMenu>(ChatSmallWindowKind.class);
    private ChatSmallWindows windows;
    private Minecraft mc;
    private FontRenderer font;
    private GuiTextField field;
    private int screenWidth;
    private int screenHeight;
    private long refreshedNanos;
    /** A command a taken row asked for, handed to the screen as the row is taken. */
    private String pendingCommand;
    /** A message the menu was asked to react to, until the screen takes it. */
    private long pendingReactionTarget = ChatMessageIds.NONE;

    ChatScreenMenus(ChatTabActions tabActions, ChatComposer composer,
                    ChatNoticeSink notices) {
        this.tabActions = tabActions;
        this.composer = composer;
        this.notices = notices;
        this.settings = new ChatSettings(notices);
    }

    /** Called from {@code initGui}, which also runs on every resize. */
    void bind(Minecraft mc, FontRenderer font, GuiTextField field,
              ChatSmallWindows windows, int screenWidth, int screenHeight) {
        this.mc = mc;
        this.font = font;
        this.field = field;
        this.windows = windows;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /* ---- What is open ---- */

    /** Whether the window of the kind's menu is out. */
    boolean isOpen(ChatSmallWindowKind kind) {
        ChatSmallWindow window = this.windows == null ? null
                : this.windows.find(kind, "");
        return window != null && window.isOpen();
    }

    /**
     * Whether the {@code +} menu or the tab search is out for the window
     * {@code windowId}, null standing for the empty screen: the control
     * that opened it shows it.
     */
    boolean isOpenFor(ChatSmallWindowKind kind, String windowId) {
        return isOpen(kind) && sameAbout(menu(kind).about, windowId);
    }

    /** The line the open message menu is about, or zero while none is out. */
    int messageMenuChatLineId() {
        MessageAim aim = openMessageAim();
        return aim == null ? 0 : aim.chatLineId;
    }

    /**
     * The window whose message toolbar opened the message menu that is
     * out, or null: the toolbar's menu button stays lit while its menu is.
     */
    ChatWindowFrame toolbarMenuFrame() {
        MessageAim aim = openMessageAim();
        return aim == null || aim.toolbarWindowId == null ? null
                : ChatWindowFrame.find(aim.toolbarWindowId);
    }

    private MessageAim openMessageAim() {
        return isOpen(ChatSmallWindowKind.MESSAGE)
                ? messageOf(menu(ChatSmallWindowKind.MESSAGE)) : null;
    }

    /**
     * The message the menu's Add Reaction was chosen for, handed over
     * once: the screen opens the Reactions window on it. NONE when
     * nothing is waiting.
     */
    long takeReactionTarget() {
        long target = this.pendingReactionTarget;
        this.pendingReactionTarget = ChatMessageIds.NONE;
        return target;
    }

    /* ---- Keeping them current ---- */

    /**
     * Keeps every open menu current, asked once a frame: on an interval
     * rather than per frame, each reads its rows again — following
     * players joining and leaving, counts changing and choices made
     * elsewhere — and one whose subject has gone, or whose rows have all
     * gone, closes: a tab closed, a window closed or locked, a message
     * taken back.
     */
    void refresh() {
        long now = System.nanoTime();
        if (this.windows == null || now - this.refreshedNanos < REFRESH_NANOS) {
            return;
        }
        this.refreshedNanos = now;
        for (ChatMenu menu : new ArrayList<ChatMenu>(this.menus.values())) {
            ChatSmallWindow window = this.windows.find(menu.kind, "");
            if (window == null || !window.isOpen()) {
                continue;
            }
            if (!stillStands(menu)) {
                this.windows.close(window);
                continue;
            }
            rebuild(menu);
            if (menu.entries().isEmpty()) {
                this.windows.close(window);
            }
        }
    }

    /**
     * A menu open as the chat last closed comes back where it stood,
     * about what it was while that still stands and with what was typed
     * into its field; one whose subject has gone stays closed.
     */
    void restore(ChatSmallWindowPlacements.Reopening open) {
        if (!(open.state instanceof ChatMenu)) {
            return;
        }
        ChatMenu menu = (ChatMenu)open.state;
        if (menu.kind != open.kind || !stillStands(menu)) {
            return;
        }
        this.menus.put(menu.kind, menu);
        rebuild(menu);
        if (!menu.entries().isEmpty()) {
            this.windows.reopen(open, menu);
        }
    }

    /** Whether what the menu is about still stands. */
    private boolean stillStands(ChatMenu menu) {
        switch (menu.kind) {
            case MESSAGE: {
                // The line still held, and its tab — where it has one —
                // still open in some window.
                MessageAim aim = messageOf(menu);
                return aim != null
                        && (aim.tab == null || ChatWindowLayout.isOpen(aim.tab))
                        && stillHeld(aim.chatLineId);
            }
            case REPORT:
                return messageOf(menu) != null;
            case PERSON:
                return targetOf(menu) != null;
            case TAB:
                return menu.about instanceof ChatTab
                        && ChatWindowLayout.isOpen((ChatTab)menu.about);
            case WINDOW: {
                ChatWindow window = menu.about instanceof String
                        ? ChatWindowLayout.window((String)menu.about) : null;
                return window != null && !window.isLocked();
            }
            case PALETTE:
                return menu.about instanceof String;
            case STATUS_LINE:
                return menu.about instanceof ChatPresenceIdentity;
            default:
                return true;
        }
    }

    /**
     * Whether the game's message list still holds the line; a list that
     * cannot be read is taken to, so a menu never closes on a guess.
     */
    private boolean stillHeld(int chatLineId) {
        GuiNewChat chat = this.mc == null || this.mc.ingameGUI == null ? null
                : this.mc.ingameGUI.getChatGUI();
        return ChatWindowLines.messageHistory(chat) == null
                || ChatWindowLines.holdsLine(chat, chatLineId);
    }

    /* ---- Taking a row ---- */

    /**
     * A row taken, by a click or by Enter in its field; answers the
     * command it asks the screen to send, or null. A row that acts closes
     * its window. A switch, a pick, a question or a row opening another
     * window leaves it standing with its rows read again, and its field
     * hands the keys back to the bar, as a picker does after a pick.
     */
    String take(ChatSmallWindow window, ChatMenu.Entry entry) {
        return take(window, entry, false);
    }

    /**
     * A row pressed with the right button: in Chat Settings a few-word
     * setting steps back; everywhere else it does nothing.
     */
    void takeBack(ChatSmallWindow window, ChatMenu.Entry entry) {
        if (window != null && window.content instanceof ChatMenu
                && ((ChatMenu)window.content).kind
                        == ChatSmallWindowKind.SETTINGS) {
            take(window, entry, true);
        }
    }

    private String take(ChatSmallWindow window, ChatMenu.Entry entry,
                        boolean back) {
        this.pendingCommand = null;
        if (window == null || !(window.content instanceof ChatMenu)
                || entry == null || !entry.isTakeable()) {
            return null;
        }
        ChatMenu menu = (ChatMenu)window.content;
        if (!stillStands(menu)) {
            this.windows.close(window);
            return null;
        }
        if (act(menu, entry, window, back)) {
            if (window.isOpen()) {
                rebuild(menu);
                menu.releaseKeys();
            }
        } else {
            this.windows.close(window);
        }
        String command = this.pendingCommand;
        this.pendingCommand = null;
        return command;
    }

    /**
     * A key for the field of the menu in front: Enter takes the row the
     * typing found, and anything else is typed into the field, a search
     * narrowing its rows as it goes. Answers a command for the screen to
     * send, or null.
     */
    String keyTyped(ChatSmallWindow window, LostTalesKeyPress press) {
        if (window == null || !(window.content instanceof ChatMenu)) {
            return null;
        }
        ChatMenu menu = (ChatMenu)window.content;
        if (press.is(Keyboard.KEY_RETURN) || press.is(Keyboard.KEY_NUMPADENTER)) {
            ChatMenu.Entry found = firstFound(menu);
            return found == null ? null : take(window, found);
        }
        if (menu.edit(press) && (menu.kind == ChatSmallWindowKind.TAB_SEARCH
                || menu.kind == ChatSmallWindowKind.CHARACTERS
                || menu.kind == ChatSmallWindowKind.SETTINGS)) {
            rebuild(menu);
        }
        return null;
    }

    /**
     * The row Enter takes: in the status line's field its first row,
     * which keeps what the field holds; elsewhere the first row the typing
     * found, and none while nothing is typed. The rows typing does not
     * narrow, the Narrator's and the statuses, are never taken, and a
     * report is sent by its reasons alone.
     */
    private static ChatMenu.Entry firstFound(ChatMenu menu) {
        boolean statusLine = menu.kind == ChatSmallWindowKind.STATUS_LINE;
        if (!statusLine && menu.filter().length() == 0) {
            return null;
        }
        for (ChatMenu.Entry entry : menu.entries()) {
            if (!entry.isTakeable()) {
                continue;
            }
            if (statusLine || menu.kind == ChatSmallWindowKind.TAB_SEARCH
                    || menu.kind == ChatSmallWindowKind.SETTINGS
                    || entry.id.startsWith(ENTRY_CHARACTER_PREFIX)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Acts on a taken row — with {@code back}, one pressed with the right
     * button — answering whether its window stays open.
     */
    private boolean act(ChatMenu menu, ChatMenu.Entry entry,
                        ChatSmallWindow window, boolean back) {
        switch (menu.kind) {
            case SETTINGS: {
                String role = this.settings.take(entry, back);
                if (role != null) {
                    show(ChatSmallWindowKind.PALETTE, role, besideWindow(window),
                            true);
                }
                return true;
            }
            case MESSAGE:
                return actOnMessage(menu, entry, window);
            case REPORT:
                sendReport(messageOf(menu), entry.id, menu.filter());
                return false;
            case PERSON:
                return actOnPerson(targetOf(menu), entry);
            case TAB:
                return actOnTab((ChatTab)menu.about, entry);
            case WINDOW:
                return actOnWindow((String)menu.about, entry, window);
            case PALETTE:
                ChatSettings.chooseColor((String)menu.about, entry);
                rebuildIfOpen(ChatSmallWindowKind.SETTINGS);
                return true;
            case OPEN:
                openFromRestoreMenu((String)menu.about, entry);
                return false;
            case TAB_SEARCH:
                if (entry.id.startsWith(ENTRY_OPEN_PREFIX)) {
                    this.tabActions.jumpToTab(ChatTab.fromId(entry.id.substring(
                            ENTRY_OPEN_PREFIX.length())));
                } else {
                    openFromRestoreMenu((String)menu.about, entry);
                }
                return false;
            case CHARACTERS:
                if (ENTRY_STATUS_LINE.equals(entry.id)) {
                    show(ChatSmallWindowKind.STATUS_LINE,
                            ClientChatPresence.speakerOf(
                                    ClientChatChannelState.getSelected()),
                            besideWindow(window), true);
                } else {
                    chooseIdentityOrStatus(entry);
                }
                return true;
            case STATUS_LINE:
                if (ENTRY_STATUS_LINE_KEEP.equals(entry.id)) {
                    ClientChatPresence.setLine(
                            (ChatPresenceIdentity)menu.about, menu.filter());
                } else if (ENTRY_STATUS_LINE_CLEAR.equals(entry.id)) {
                    ClientChatPresence.setLine(
                            (ChatPresenceIdentity)menu.about, "");
                }
                return false;
            default:
                return false;
        }
    }

    /** Reads the kind's rows again at once where its window is out: what one window changed shows in another. */
    private void rebuildIfOpen(ChatSmallWindowKind kind) {
        if (isOpen(kind)) {
            rebuild(menu(kind));
        }
    }

    /* ---- Showing a menu ---- */

    /** The kind's menu, made the first time it is asked for. */
    private ChatMenu menu(ChatSmallWindowKind kind) {
        ChatMenu menu = this.menus.get(kind);
        if (menu == null) {
            menu = new ChatMenu(kind);
            this.menus.put(kind, menu);
        }
        return menu;
    }

    /** Where a menu's window first opens, before the player has placed its kind. */
    private interface FirstPlace {
        /** The content box the window opens round, for {@code menu} as it stands. */
        LostTalesUiHitBox contentBox(ChatMenu menu, int screenWidth,
                                     int screenHeight);
    }

    /** Hung from a control or the pointer; none for no anchor. */
    private static FirstPlace hangingFrom(final ChatMenu.Anchor anchor) {
        return anchor == null ? null : new FirstPlace() {
            @Override
            public LostTalesUiHitBox contentBox(ChatMenu menu, int screenWidth,
                                                int screenHeight) {
                return menu.firstContentBox(anchor, screenWidth, screenHeight);
            }
        };
    }

    /** Beside the window whose row opened it. */
    private static FirstPlace besideWindow(final ChatSmallWindow window) {
        return new FirstPlace() {
            @Override
            public LostTalesUiHitBox contentBox(ChatMenu menu, int screenWidth,
                                                int screenHeight) {
                return menu.firstContentBoxBeside(window.box(), screenWidth,
                        screenHeight);
            }
        };
    }

    /** In the middle of the screen: what a shortcut opens with no control to hang from. */
    private static final FirstPlace CENTRED = new FirstPlace() {
        @Override
        public LostTalesUiHitBox contentBox(ChatMenu menu, int screenWidth,
                                            int screenHeight) {
            return menu.firstContentBoxCentred(screenWidth, screenHeight);
        }
    };

    /**
     * Shows the kind's menu about {@code about}. Out already about the
     * same thing, a {@code toggle} — its control pressed again — puts it
     * away and anything else brings it in front. Out about something
     * else, it turns to {@code about} where it stands. Not out, it opens
     * where the player left its kind, else at its {@code place}; with no
     * place it does not open. A menu with no rows to show does not open.
     */
    private void show(ChatSmallWindowKind kind, Object about,
                      FirstPlace place, boolean toggle) {
        if (this.windows == null) {
            return;
        }
        ChatMenu menu = menu(kind);
        ChatSmallWindow window = this.windows.find(kind, "");
        boolean out = window != null && window.isOpen();
        if (out && sameAbout(menu.about, about)) {
            if (toggle) {
                this.windows.close(window);
            } else {
                this.windows.focus(window);
            }
            return;
        }
        if (!out && place == null) {
            return;
        }
        menu.about = about;
        menu.restart();
        prepare(menu);
        rebuild(menu);
        if (menu.entries().isEmpty()) {
            if (out) {
                this.windows.close(window);
            }
            return;
        }
        if (out) {
            this.windows.focus(window);
            this.windows.refit(window);
            return;
        }
        boolean fading = window != null;
        ChatSmallWindow opened = this.windows.open(kind, "", menu,
                place.contentBox(menu, this.screenWidth, this.screenHeight));
        if (fading) {
            this.windows.refit(opened);
        }
    }

    /**
     * What a menu opening, or turning to something else, starts with in
     * its field: nothing typed, or for the status line the line it has.
     * The tab search's and Chat Settings' fields name the shortcut that
     * opens them. The character menu makes its field as it reads its
     * rows.
     */
    private void prepare(ChatMenu menu) {
        menu.closeField();
        switch (menu.kind) {
            case TAB_SEARCH:
                menu.openField(StatCollector.translateToLocal(
                                "gui.losttales.chat.search.prompt"),
                        ChatShortcuts.withCommand(Keyboard.KEY_LSHIFT,
                                Keyboard.KEY_A), LostTalesUiSheet.SEARCH,
                        ChatMenu.MAX_FILTER_LENGTH);
                break;
            case STATUS_LINE:
                menu.openField(StatCollector.translateToLocal(
                                "gui.losttales.chat.status_line.prompt"),
                        null, LostTalesUiSheet.SPEECH_BUBBLE,
                        ChatStatusLine.MAX_CHARACTERS);
                menu.setFilter(ClientChatPresence.chosenLine(
                        (ChatPresenceIdentity)menu.about));
                break;
            case REPORT:
                menu.openField(StatCollector.translateToLocal(
                                "gui.losttales.chat.report.prompt"),
                        null, LostTalesUiSheet.EXCLAMATION,
                        ChatConsoleEvent.Report.MAX_NOTE_LENGTH);
                break;
            case SETTINGS:
                menu.openField(StatCollector.translateToLocal(
                                "gui.losttales.chat.settings.search"),
                        ChatShortcuts.withCommand(Keyboard.KEY_COMMA),
                        LostTalesUiSheet.SEARCH,
                        ChatMenu.MAX_FILTER_LENGTH);
                this.settings.reset();
                break;
            default:
                break;
        }
    }

    /** Reads the menu's name and rows again from what it is about. */
    private void rebuild(ChatMenu menu) {
        switch (menu.kind) {
            case MESSAGE:
                menu.setTitle(null, LostTalesUiSheet.MORE);
                menu.setRows(messageRows(messageOf(menu)));
                break;
            case REPORT:
                menu.setTitle(null, LostTalesUiSheet.EXCLAMATION);
                menu.setRows(reportRows());
                break;
            case PERSON: {
                LostTalesChatHoverCard.Target target = targetOf(menu);
                menu.setTitle(target.windowTitle(), null);
                menu.setRows(personRows(target));
                break;
            }
            case TAB: {
                ChatTab tab = (ChatTab)menu.about;
                menu.setTitle(ClientChatChannelState.displayName(tab),
                        LostTalesUiSheet.COG);
                menu.setRows(tabRows(tab));
                break;
            }
            case WINDOW: {
                ChatWindow window = ChatWindowLayout.window((String)menu.about);
                menu.setTitle(windowTitle(window), LostTalesUiSheet.COG);
                menu.setRows(windowRows(window));
                break;
            }
            case PALETTE:
                menu.setTitle(StatCollector.translateToLocal(
                        ChatSettings.colorLabelKey((String)menu.about)), null);
                menu.setRows(ChatSettings.paletteRows((String)menu.about));
                break;
            case SETTINGS:
                menu.setTitle(null, LostTalesUiSheet.COG);
                menu.setRowHeight(ChatMenu.TALL_ROW_HEIGHT);
                menu.setRows(this.settings.rows(menu.filter()));
                break;
            case OPEN:
                menu.setTitle(null, LostTalesUiSheet.PLUS);
                menu.setRows(restoreEntries());
                break;
            case TAB_SEARCH:
                menu.setTitle(null, LostTalesUiSheet.SEARCH);
                menu.setRows(searchEntries(menu.filter()));
                break;
            case CHARACTERS:
                rebuildCharacters(menu);
                break;
            case STATUS_LINE:
                menu.setTitle(null, LostTalesUiSheet.SPEECH_BUBBLE);
                menu.setRows(statusLineRows(
                        (ChatPresenceIdentity)menu.about));
                break;
            default:
                break;
        }
    }

    private static MessageAim messageOf(ChatMenu menu) {
        return menu.about instanceof MessageAim ? (MessageAim)menu.about
                : null;
    }

    private static LostTalesChatHoverCard.Target targetOf(ChatMenu menu) {
        return menu.about instanceof LostTalesChatHoverCard.Target
                ? (LostTalesChatHoverCard.Target)menu.about : null;
    }

    /**
     * Whether two subjects are the same one: a message by its line, a
     * person by who they are, anything else by what it equals — the empty
     * screen's null included.
     */
    static boolean sameAbout(Object one, Object other) {
        if (one instanceof MessageAim && other instanceof MessageAim) {
            return ((MessageAim)one).chatLineId
                    == ((MessageAim)other).chatLineId;
        }
        if (one instanceof LostTalesChatHoverCard.Target
                && other instanceof LostTalesChatHoverCard.Target) {
            return ((LostTalesChatHoverCard.Target)one).key().equals(
                    ((LostTalesChatHoverCard.Target)other).key());
        }
        return one == null ? other == null : one.equals(other);
    }

    /* ---- The strip's menus ---- */

    /**
     * The tab's menu, behind its cog — a switch, as the cog is — or under
     * a right-click on the tab, which only opens it or turns it to the
     * tab. Each row is its own independent preference or action. While
     * the tab holds anything unread it opens as a messenger's channel menu
     * does, with Mark as Read (the counters and the divider gone at once)
     * and Jump to First Unread (the tab brought forward and its history
     * taken to where the unread run begins). Then Mute Channel (out of
     * the feed), Mute Mentions (cue silent), Hide Channel (stays closed
     * when messaged), and Move to its own Window. Closing is the cross on
     * the tab and nothing else: a row that only repeats the button beside
     * it is a second way to lose a tab by accident.
     */
    void showTabMenu(ChatTab tab, ChatMenu.Anchor anchor, boolean toggle) {
        if (tab != null) {
            show(ChatSmallWindowKind.TAB, tab, hangingFrom(anchor), toggle);
        }
    }

    private static List<ChatMenu.Entry> tabRows(ChatTab channel) {
        List<ChatMenu.Entry> entries = new ArrayList<ChatMenu.Entry>(6);
        if (ClientChatChannelViews.hasUnread(channel)
                || ClientChatChannelViews.unreadDividerLine(channel) != null) {
            entries.add(new ChatMenu.Entry(ENTRY_MARK_READ,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.tab.mark_read")));
        }
        if (ClientChatChannelViews.unreadDividerLine(channel) != null) {
            entries.add(new ChatMenu.Entry(ENTRY_JUMP_UNREAD,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.tab.jump_unread")));
        }
        entries.add(new ChatMenu.Entry(ENTRY_MUTE,
                StatCollector.translateToLocal(
                        ChatWindowLayout.isMuted(channel)
                                ? "gui.losttales.chat.tab.unmute"
                                : "gui.losttales.chat.tab.mute")));
        entries.add(new ChatMenu.Entry(ENTRY_PINGS,
                StatCollector.translateToLocal(
                        ChatWindowLayout.isPingsMuted(channel)
                                ? "gui.losttales.chat.tab.unmute_mentions"
                                : "gui.losttales.chat.tab.mute_mentions")));
        entries.add(new ChatMenu.Entry(ENTRY_HIDE,
                StatCollector.translateToLocal(
                        ChatWindowLayout.isHidden(channel)
                                ? "gui.losttales.chat.tab.unhide"
                                : "gui.losttales.chat.tab.hide")));
        // A layout action the row may have no room for: a window of its
        // own, offered whenever the layout would allow it.
        ChatWindow window = ChatWindowLayout.windowOf(channel);
        if (window != null && !window.isLocked()
                && window.getTabs().size() > 1
                && ChatWindowLayout.windows().size()
                        < ChatWindowLayout.MAX_WINDOWS) {
            entries.add(new ChatMenu.Entry(ENTRY_DETACH,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.tab.detach")));
        }
        return entries;
    }

    /** One row of the tab's menu: the switches stay, the actions are done with it. */
    private boolean actOnTab(ChatTab channel, ChatMenu.Entry entry) {
        if (ENTRY_MUTE.equals(entry.id)) {
            ChatWindowLayout.setMuted(channel, !ChatWindowLayout.isMuted(channel));
            return true;
        }
        if (ENTRY_PINGS.equals(entry.id)) {
            ChatWindowLayout.setPingsMuted(channel,
                    !ChatWindowLayout.isPingsMuted(channel));
            return true;
        }
        if (ENTRY_HIDE.equals(entry.id)) {
            ChatWindowLayout.setHidden(channel,
                    !ChatWindowLayout.isHidden(channel));
            return true;
        }
        if (ENTRY_DETACH.equals(entry.id)) {
            this.tabActions.detachChannel(channel, this.screenWidth,
                    this.screenHeight);
        } else if (ENTRY_MARK_READ.equals(entry.id)) {
            ClientChatChannelViews.markViewed(channel);
            ClientChatChannelViews.dismissDivider(channel);
        } else if (ENTRY_JUMP_UNREAD.equals(entry.id)) {
            Integer first = ClientChatChannelViews.unreadDividerLine(channel);
            this.tabActions.jumpToTab(channel);
            if (first != null) {
                // The rows exist once the tab has been drawn; the next
                // draw lands on the run's first line.
                LostTalesChatPresentation.requestJump(first.intValue());
            }
        }
        return false;
    }

    /**
     * The window's own menu, behind the cog at the end of its row — a
     * switch, as the cog is — or under a right-click on the strip: the
     * settings a window has that nothing else on the row offers. Locking
     * and closing are not among them — the padlock and the cross stand
     * right beside the cog, and a menu row that only repeats the button
     * next to it is a second way to reach the same thing rather than a
     * setting. Unsticking is offered while the window is stuck to a
     * neighbour, and the size entry puts the window back to the chat's
     * default shape. A locked window offers no cog, so it never reaches
     * this menu, and one locked while the menu stands closes it. Its last
     * row opens Chat Settings beside it: the colours and every other chat
     * preference are the chat's, not one window's.
     */
    void showWindowMenu(ChatWindow window, ChatMenu.Anchor anchor,
                        boolean toggle) {
        if (window != null && !window.isLocked()) {
            show(ChatSmallWindowKind.WINDOW, window.getId(),
                    hangingFrom(anchor), toggle);
        }
    }

    /**
     * {@code Ctrl+,}: Chat Settings, from anywhere in the chat, opening in
     * the middle of the screen until the player has placed it; a switch,
     * as the window menu's row is.
     */
    void toggleChatSettings() {
        show(ChatSmallWindowKind.SETTINGS, null, CENTRED, true);
    }

    /** The window menu's name: the window's tab in front, which names it on its row. */
    private static String windowTitle(ChatWindow window) {
        ChatTab front = window == null ? null : ChatWindowFrame.activeTab(
                window, ChatWindowFrame.visibleTabs(window));
        return front == null ? null : StatCollector.translateToLocalFormatted(
                "gui.losttales.chat.small_window.window_of",
                ClientChatChannelState.displayName(front));
    }

    private static List<ChatMenu.Entry> windowRows(ChatWindow window) {
        List<ChatMenu.Entry> entries = new ArrayList<ChatMenu.Entry>(3);
        if (window == null) {
            return entries;
        }
        if (window.isLinked()) {
            entries.add(new ChatMenu.Entry(ENTRY_WINDOW_UNSTICK,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.window.unstick")));
        }
        entries.add(new ChatMenu.Entry(ENTRY_WINDOW_RESET,
                StatCollector.translateToLocal(
                        "gui.losttales.chat.window.reset_size")));
        entries.add(new ChatMenu.Entry(ENTRY_CHAT_SETTINGS,
                StatCollector.translateToLocal(
                        "gui.losttales.chat.window.chat_settings"))
                .withSprite(LostTalesUiSheet.COG, LostTalesUiSheet.COG_HOVER,
                        false));
        return entries;
    }

    /**
     * One row of the window's own menu, answering whether the menu stays
     * open: Chat Settings opens beside it, or goes away when it is out,
     * and the menu stays; the rest are done with it.
     */
    private boolean actOnWindow(String windowId, ChatMenu.Entry entry,
                                ChatSmallWindow menuWindow) {
        if (ENTRY_CHAT_SETTINGS.equals(entry.id)) {
            show(ChatSmallWindowKind.SETTINGS, null, besideWindow(menuWindow),
                    true);
            return true;
        }
        ChatWindow window = ChatWindowLayout.window(windowId);
        if (window == null) {
            return false;
        }
        if (ENTRY_WINDOW_UNSTICK.equals(entry.id)) {
            ChatWindowLayout.unlink(window.getId());
        } else if (ENTRY_WINDOW_RESET.equals(entry.id)) {
            // The chat's own default: a whole number of message lines,
            // so the topmost row is never a clipped one, and just wide
            // enough to show every one of the window's tabs whole. A
            // row that cannot be measured leaves the width alone, and
            // the window keeps following the game's chat-width setting.
            // A window filling a part of the screen takes the size it
            // is given, as the entry says.
            ChatWindowLayout.setFill(window.getId(),
                    ChatWindow.ScreenFill.NONE, false);
            ChatWindowLayout.setWindowLines(window.getId(),
                    ChatWindowLayout.DEFAULT_WINDOW_LINES, false);
            ChatWindowLayout.setWindowWidth(window.getId(),
                    ChatChannelTabBar.chatWidthForWholeRow(this.mc, window),
                    true);
        }
        return false;
    }

    /**
     * The tab search: every tab that is open, then the channels and
     * conversations that are not, narrowed by what is typed into the
     * field above them. Hung from a window's own search control, or —
     * from the keyboard ({@code anchor} null) — from the row of the
     * window being typed in, else from the empty state's own {@code +}
     * ({@code emptyPlus}, null while something is open). A switch, as its
     * control is: out for this window already, it goes away; out for
     * another, it turns to this one and starts afresh, since the words
     * typed for the other are not this one's.
     */
    void toggleSearchPanel(ChatWindow window, ChatMenu.Anchor anchor,
                           ChatMenu.Anchor emptyPlus) {
        String windowId = emptyPlus != null || window == null ? null
                : window.getId();
        show(ChatSmallWindowKind.TAB_SEARCH, windowId, hangingFrom(
                anchor != null ? anchor : keyboardAnchor(windowId, emptyPlus)),
                true);
    }

    /**
     * The {@code +} menu, hung from a window's own control, or — from the
     * keyboard ({@code anchor} null) — from the row of the window being
     * typed in, else from the empty state's own {@code +}; a switch like
     * its control. Nothing opens while there is nothing to open.
     */
    void toggleChannelMenu(ChatWindow window, ChatMenu.Anchor anchor,
                           ChatMenu.Anchor emptyPlus) {
        String windowId = emptyPlus != null || window == null ? null
                : window.getId();
        show(ChatSmallWindowKind.OPEN, windowId, hangingFrom(
                anchor != null ? anchor : keyboardAnchor(windowId, emptyPlus)),
                true);
    }

    /**
     * Where a menu the keyboard opens for a window hangs: the left end of
     * its tab row, where the row's first controls stand; the empty
     * state's {@code +} for the empty screen.
     */
    private ChatMenu.Anchor keyboardAnchor(String windowId,
                                           ChatMenu.Anchor emptyPlus) {
        if (windowId == null) {
            return emptyPlus;
        }
        ChatWindowFrame frame = ChatWindowFrame.find(windowId);
        if (frame == null || !frame.drawn) {
            return null;
        }
        int left = (int)Math.floor(frame.drawnLeft()) + 2;
        int rowBottom = (int)Math.floor(frame.tabRowBottom());
        return ChatMenu.Anchor.inward(left,
                ChatChannelTabBar.rowTop(rowBottom), left + 8, rowBottom,
                frame, this.screenWidth, this.screenHeight);
    }

    /**
     * The tab search's rows: the tabs already open across every window,
     * so a search jumps to one, then the closed channels and the players
     * a conversation could be opened with, so it opens one. A filter keeps
     * the rows whose names hold it, and a section with nothing left in it
     * is dropped; a filter that matches nothing says so rather than
     * closing the window under the hand that is typing.
     */
    List<ChatMenu.Entry> searchEntries(String filter) {
        List<ChatMenu.Entry> entries = new ArrayList<ChatMenu.Entry>();
        List<ChatMenu.Entry> open = new ArrayList<ChatMenu.Entry>();
        List<ChatWindow> windows = ChatWindowLayout.windows();
        for (int index = 0; index < windows.size(); index++) {
            List<ChatTab> tabs = ChatWindowFrame.visibleTabs(
                    windows.get(index));
            for (int at = 0; at < tabs.size(); at++) {
                ChatTab tab = tabs.get(at);
                String name = ClientChatChannelState.displayName(tab);
                if (!matchesFilter(name, filter)) {
                    continue;
                }
                open.add(new ChatMenu.Entry(
                        ENTRY_OPEN_PREFIX + tab.id(), name,
                        ChatWindowLayout.isMuted(tab),
                        ClientChatChannelState.displayColor(tab), tab));
            }
        }
        addSection(entries, "gui.losttales.chat.search.open", open);
        List<ChatMenu.Entry> closed = new ArrayList<ChatMenu.Entry>();
        for (ChatChannel channel : restorableChannels()) {
            String name = ClientChatChannelState.displayName(channel);
            if (matchesFilter(name, filter)) {
                closed.add(new ChatMenu.Entry(channel.getId(), name,
                        ChatWindowLayout.isMuted(channel),
                        ClientChatChannelState.displayColor(channel),
                        ChatTab.of(channel)));
            }
        }
        addSection(entries, "gui.losttales.chat.open.channels", closed);
        List<ChatMenu.Entry> players = new ArrayList<ChatMenu.Entry>();
        for (String name : whisperCandidates(this.mc)) {
            ChatTab conversation = ChatTab.whisper(name, "");
            if (conversation != null && !ChatWindowLayout.isOpen(conversation)
                    && matchesFilter(name, filter)) {
                players.add(new ChatMenu.Entry(conversation.id(),
                        name, ChatWindowLayout.isMuted(conversation), -1,
                        conversation));
            }
        }
        addSection(entries, "gui.losttales.chat.open.players", players);
        if (entries.isEmpty()) {
            entries.add(ChatMenu.Entry.passive(
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.search.none")));
        }
        return entries;
    }

    /** Adds a headed section, or nothing at all when it has no rows. */
    private static void addSection(List<ChatMenu.Entry> entries,
                                   String headerKey,
                                   List<ChatMenu.Entry> rows) {
        if (rows.isEmpty()) {
            return;
        }
        entries.add(ChatMenu.Entry.header(
                StatCollector.translateToLocal(headerKey)));
        entries.addAll(rows);
    }

    /** Whether a name holds what has been typed, however it is cased. */
    static boolean matchesFilter(String name, String filter) {
        return filter.length() == 0 || name.toLowerCase(Locale.ROOT)
                .contains(filter.toLowerCase(Locale.ROOT));
    }

    /**
     * Where a menu opened from a control on a window's tab row hangs: the
     * row's band at {@code x}, where the pointer is on the control, and
     * toward the window's middle, which is under the row.
     */
    ChatMenu.Anchor stripAnchor(ChatWindowFrame frame,
                                ChatChannelTabBar.Row row, int x) {
        return ChatMenu.Anchor.inward(x - 4,
                ChatChannelTabBar.rowTop(row.rowBottom), x + 4, row.rowBottom,
                frame, this.screenWidth, this.screenHeight);
    }

    /**
     * Where a menu opened over the lines hangs: the pointer, toward the
     * middle of the window it is in.
     */
    private ChatMenu.Anchor pointerAnchor(int mouseX, int mouseY) {
        return ChatMenu.Anchor.inward(mouseX, mouseY, mouseX, mouseY,
                ChatWindowFrame.drawnAt(mouseX, mouseY), this.screenWidth,
                this.screenHeight);
    }

    /**
     * Where a window opens that takes another's place as that one
     * closes: its top left on the other's.
     */
    private static ChatMenu.Anchor inPlaceOf(ChatSmallWindow window) {
        int left = (int)Math.round(window.left);
        int top = (int)Math.round(window.top) - ChatMenu.Anchor.REACH;
        return new ChatMenu.Anchor(left, top, left, top, true, false);
    }

    /**
     * The {@code +} menu: a channel-opening list in two sections. The
     * closed channels come first, each wearing the icon its tab would
     * wear, its unread mark included — a closed channel keeps receiving,
     * and the mark is the one the tab shows once restored; a muted one
     * reads italic, like its tab would. The online players follow, each
     * opening (or selecting) the whisper conversation with them, wearing
     * the same head its tab wears and the conversation's mark. A section
     * absent of rows is left out altogether.
     */
    List<ChatMenu.Entry> restoreEntries() {
        List<ChatMenu.Entry> entries = new ArrayList<ChatMenu.Entry>();
        List<ChatChannel> closed = restorableChannels();
        if (!closed.isEmpty()) {
            entries.add(ChatMenu.Entry.header(
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.open.channels")));
            for (ChatChannel channel : closed) {
                entries.add(new ChatMenu.Entry(channel.getId(),
                        ClientChatChannelState.displayName(channel),
                        ChatWindowLayout.isMuted(channel),
                        ClientChatChannelState.displayColor(channel),
                        ChatTab.of(channel)));
            }
        }
        List<String> players = whisperCandidates(this.mc);
        if (!players.isEmpty()) {
            entries.add(ChatMenu.Entry.header(
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.open.players")));
            for (String name : players) {
                ChatTab conversation = ChatTab.whisper(name, "");
                entries.add(new ChatMenu.Entry(conversation.id(),
                        name, ChatWindowLayout.isMuted(conversation), -1,
                        conversation));
            }
        }
        return entries;
    }

    /**
     * One row of the {@code +} menu or of the tab search: the channel or
     * conversation joins the window the menu was opened for, or — for the
     * empty screen's, or when that window is gone — the first window, or
     * a window of its own when none is left.
     */
    private void openFromRestoreMenu(String windowId, ChatMenu.Entry entry) {
        ChatWindow target = ChatWindowLayout.window(windowId);
        if (target == null) {
            target = ChatWindowLayout.firstWindow();
        }
        ChatChannel channel = ChatChannel.fromId(entry.id);
        ChatTab tab = channel != null ? ChatTab.of(channel)
                : ChatTab.fromId(entry.id);
        if (tab == null || (channel == null
                && (!tab.isWhisper() || tab.isNpc()))) {
            return;
        }
        ChatTab opened = target == null
                ? ChatWindowLayout.openInNewWindow(tab)
                : ChatWindowLayout.openTab(tab, target.getId());
        if (opened != null) {
            this.tabActions.selectChannel(opened);
        }
    }

    /** Closed channels the player could see if they were open. */
    static List<ChatChannel> restorableChannels() {
        List<ChatChannel> closed = ChatWindowLayout.closedChannels();
        List<ChatChannel> result = new ArrayList<ChatChannel>(closed.size());
        for (int index = 0; index < closed.size(); index++) {
            if (ClientChatChannelState.isAvailable(closed.get(index))) {
                result.add(closed.get(index));
            }
        }
        return result;
    }

    /**
     * Whether any other account is online to open a conversation with.
     * Asked per frame for the {@code +} control, so it only scans; the
     * menu itself builds the sorted list.
     */
    static boolean hasWhisperCandidates(Minecraft mc) {
        if (mc == null || mc.thePlayer == null || mc.thePlayer.sendQueue == null
                || mc.thePlayer.sendQueue.playerInfoList == null) {
            return false;
        }
        String self = mc.thePlayer.getCommandSenderName();
        for (Object value : mc.thePlayer.sendQueue.playerInfoList) {
            if (value instanceof GuiPlayerInfo) {
                String account = ((GuiPlayerInfo)value).name;
                if (account != null && account.trim().length() > 0
                        && !account.equalsIgnoreCase(self)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The accounts a conversation could be opened with: every other
     * online player, alphabetically. Read fresh on every build, so the
     * menu follows joins and leaves while it is open.
     */
    private static List<String> whisperCandidates(Minecraft mc) {
        if (mc == null || mc.thePlayer == null || mc.thePlayer.sendQueue == null
                || mc.thePlayer.sendQueue.playerInfoList == null) {
            return new ArrayList<String>();
        }
        List<String> accounts = new ArrayList<String>();
        for (Object value : mc.thePlayer.sendQueue.playerInfoList) {
            if (value instanceof GuiPlayerInfo) {
                accounts.add(((GuiPlayerInfo)value).name);
            }
        }
        return whisperCandidates(mc.thePlayer.getCommandSenderName(),
                accounts);
    }

    /** As above over a given list: everyone but oneself, once, sorted. */
    static List<String> whisperCandidates(String self, List<String> accounts) {
        List<String> result = new ArrayList<String>();
        for (String account : accounts) {
            if (account == null || account.trim().length() == 0
                    || account.equalsIgnoreCase(self)) {
                continue;
            }
            String name = account.trim();
            boolean seen = false;
            for (int index = 0; index < result.size(); index++) {
                if (result.get(index).equalsIgnoreCase(name)) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                result.add(name);
            }
        }
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    /**
     * The mark after the {@code +}: the channels it would list together,
     * their pings on the tile, else the white sphere while any holds
     * something unread.
     */
    static ChatIconMark closedMark() {
        List<ChatTab> closed = new ArrayList<ChatTab>();
        for (ChatChannel channel : restorableChannels()) {
            closed.add(ChatTab.of(channel));
        }
        return ChatIconMark.combined(closed);
    }

    /** Sum of the unread counts of the channels the {@code +} would list. */
    static int closedUnreadCount() {
        int total = 0;
        for (ChatChannel channel : restorableChannels()) {
            total += ClientChatChannelViews.unreadCount(channel);
        }
        return Math.min(ClientChatChannelViews.MAX_UNREAD + 1, total);
    }

    /* ---- The menus over the lines ---- */

    /**
     * The menu over a message, under a right-click on its line: what it
     * acts on is resolved now, while the pointer is still on the line.
     * Nothing opens over a line that carries no message.
     */
    boolean openMessageMenu(int mouseX, int mouseY) {
        String text = LostTalesChatClipboard.messageTextAt(
                this.mc.ingameGUI.getChatGUI(), this.mc, mouseX, mouseY);
        if (text.length() == 0) {
            return false;
        }
        // The pointer's exact position, the one the hover shaded the
        // message by, so the menu is about the message that was lit.
        LostTalesChatOverlayRenderer.Band band =
                LostTalesChatOverlayRenderer.bandAt(this.mc,
                        (float)ChatWindowPlacement.preciseMouseX(this.mc,
                                this.screenWidth),
                        (float)ChatWindowPlacement.preciseMouseY(this.mc,
                                this.screenHeight));
        if (band == null || band.lines == null
                || band.viewIndex >= band.lines.size()
                || band.lines.get(band.viewIndex) == null) {
            return false;
        }
        show(ChatSmallWindowKind.MESSAGE, messageAim(text,
                        band.lines.get(band.viewIndex).getChatLineID(),
                        band.lines, band.viewIndex, null),
                hangingFrom(pointerAnchor(mouseX, mouseY)), false);
        return true;
    }

    /**
     * The same menu from a message's toolbar, about the message the
     * toolbar was drawn for — its first drawn row {@code row} of
     * {@code frame}'s lines — rather than whatever lies under the pointer,
     * which may be the row above; it hangs from the control's box toward
     * the middle of the window. A switch, as the control is.
     */
    boolean toggleToolbarMessageMenu(ChatWindowFrame frame, int chatLineId,
                                     int row, ChatMenu.Anchor anchor) {
        if (frame == null || row < 0 || row >= frame.lines.size()) {
            return false;
        }
        String text = LostTalesChatClipboard.messageTextOf(frame.lines, row);
        if (text.length() == 0) {
            return false;
        }
        show(ChatSmallWindowKind.MESSAGE, messageAim(text, chatLineId,
                frame.lines, row, frame.windowId), hangingFrom(anchor), true);
        return true;
    }

    /**
     * The message drawn on {@code chatLineId}, whose words are
     * {@code text} and one of whose drawn rows is {@code viewIndex} of
     * {@code lines}. Who sent it is resolved from the packet it was built
     * of, not from the drawn rows: a grouped continuation has no header
     * row to read a name off, and its sender still owns it. Only a line
     * with no packet behind it — an adopted stray, an NPC's speech — is
     * read from what was drawn.
     */
    private static MessageAim messageAim(String text, int chatLineId,
                                         List<ChatLine> lines, int viewIndex,
                                         String toolbarWindowId) {
        long messageId = ClientChatMessageIds.messageIdOf(chatLineId);
        ClientChatMessages.Remembered remembered =
                ClientChatMessages.get(messageId);
        ChatTab tab = ClientChatChannelViews.tabOf(chatLineId);
        if (remembered != null) {
            return new MessageAim(chatLineId, messageId, text,
                    remembered.packet.getAccountName(),
                    remembered.packet.getIdentityName(),
                    LostTalesChatMessagePacket.isDiscordSender(
                            remembered.packet.getSenderId()),
                    remembered.packet.getSenderId(), tab, toolbarWindowId);
        }
        return new MessageAim(chatLineId, messageId, text,
                messageAccount(lines, viewIndex, chatLineId),
                messageIdentity(lines, viewIndex, chatLineId),
                isFromDiscord(lines, chatLineId), null, tab, toolbarWindowId);
    }

    /**
     * The message menu's rows. Every line offers the same actions in the
     * same places, as the hover toolbar does; one that cannot be taken on
     * this line stands muted and says why. A line the server named can be
     * reacted to and linked to by its id; any line in a tab that takes
     * messages can be answered, by its id or by its words. Asked to take
     * the message back, the menu becomes the question.
     */
    private List<ChatMenu.Entry> messageRows(MessageAim aim) {
        List<ChatMenu.Entry> entries = new ArrayList<ChatMenu.Entry>();
        if (aim.confirmingDelete) {
            ChatMenu.Entry confirm = new ChatMenu.Entry(ENTRY_DELETE_CONFIRM,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.message.delete.confirm"));
            entries.add(isOwnMessage(aim) ? confirm
                    : confirm.withLabelColor(OPERATOR_ACTION_COLOR));
            return entries;
        }
        entries.add(action(ENTRY_REACT, "gui.losttales.chat.message.react",
                LostTalesChatPresentation.whyNotReactable(aim.chatLineId)));
        entries.add(action(ENTRY_REPLY, "gui.losttales.chat.message.reply",
                LostTalesChatPresentation.whyNotRepliable(aim.chatLineId)));
        entries.add(new ChatMenu.Entry(ENTRY_COPY,
                StatCollector.translateToLocal(
                        "gui.losttales.chat.message.copy")));
        entries.add(action(ENTRY_COPY_LINK,
                "gui.losttales.chat.message.copy_link",
                whyNotLinkable(aim.chatLineId)));
        // Your own words are yours to correct or take back. The server
        // decides that too — this only offers what it would allow. An
        // operator may take anyone's words back, but never rewrite them:
        // a removal is visibly a removal, an edit would put words in
        // another's mouth. Their row wears the Operator crimson.
        if (isOwnMessage(aim)) {
            if (ClientChatMessages.get(aim.messageId) != null) {
                // Editable only while this client still remembers what
                // was typed: the field is filled with the original text,
                // markup and all, not with the line as it reads.
                entries.add(new ChatMenu.Entry(ENTRY_EDIT,
                        StatCollector.translateToLocal(
                                "gui.losttales.chat.message.edit")));
            }
            entries.add(new ChatMenu.Entry(ENTRY_DELETE,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.message.delete")));
        } else if (canModerateMessage(aim)) {
            entries.add(new ChatMenu.Entry(ENTRY_DELETE,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.message.delete"))
                    .withLabelColor(OPERATOR_ACTION_COLOR));
        }
        // Reporting stands last, in crimson behind its mark, as a
        // messenger's does; on a line it cannot take it says why.
        entries.add(action(ENTRY_REPORT, "gui.losttales.chat.message.report",
                whyNotReportable(aim))
                .withLabelColor(OPERATOR_ACTION_COLOR)
                .withSprite(LostTalesUiSheet.EXCLAMATION,
                        LostTalesUiSheet.EXCLAMATION_LIT, false));
        return entries;
    }

    /**
     * One row of the message menu, answering whether the menu stays:
     * deleting asks first, the question standing in the menu's place
     * until it is answered or the window closed; reporting opens the
     * report where the menu stood; the rest are done with it.
     */
    private boolean actOnMessage(ChatMenu menu, ChatMenu.Entry entry,
                                 ChatSmallWindow window) {
        MessageAim aim = messageOf(menu);
        if (ENTRY_DELETE.equals(entry.id)) {
            if (!isOwnMessage(aim) && !canModerateMessage(aim)) {
                return false;
            }
            aim.confirmingDelete = true;
            rebuild(menu);
            this.windows.refit(window);
            return true;
        }
        if (ENTRY_REACT.equals(entry.id)) {
            this.pendingReactionTarget = aim.messageId;
        } else if (ENTRY_REPLY.equals(entry.id)) {
            startReply(aim);
        } else if (ENTRY_EDIT.equals(entry.id)) {
            startEdit(aim);
        } else if (ENTRY_DELETE_CONFIRM.equals(entry.id)) {
            confirmDelete(aim);
        } else if (ENTRY_COPY.equals(entry.id)) {
            if (LostTalesChatClipboard.copy(aim.text)) {
                this.notices.showNotice(StatCollector.translateToLocal(
                        "gui.losttales.chat.copied"));
            }
        } else if (ENTRY_COPY_LINK.equals(entry.id)) {
            String link = messageLinkFor(aim.chatLineId);
            if (link != null && LostTalesChatClipboard.copy(link)) {
                this.notices.showNotice(StatCollector.translateToLocal(
                        "gui.losttales.chat.copied"));
            }
        } else if (ENTRY_REPORT.equals(entry.id)) {
            show(ChatSmallWindowKind.REPORT, aim,
                    hangingFrom(inPlaceOf(window)), false);
        }
        return false;
    }

    /** A message action's row: open, or muted with {@code why} it cannot be taken. */
    private static ChatMenu.Entry action(String id, String labelKey,
                                         String why) {
        ChatMenu.Entry entry = new ChatMenu.Entry(id,
                StatCollector.translateToLocal(labelKey));
        return why.length() == 0 ? entry : entry.unavailable(why);
    }

    /**
     * Why the message cannot be reported, or empty where it can: a line
     * the server never named, this player's own, or one nobody wrote —
     * the Server's, the Client's, an NPC's. The server decides again when
     * the report arrives.
     */
    private String whyNotReportable(MessageAim aim) {
        String unnamed = LostTalesChatPresentation.unnamedReason(aim.chatLineId);
        if (unnamed.length() > 0) {
            return unnamed;
        }
        if (isOwnMessage(aim)) {
            return StatCollector.translateToLocal("gui.losttales.chat.report.own");
        }
        if (aim.senderId == null
                || LostTalesChatMessagePacket.isSystemSender(aim.senderId)) {
            return StatCollector.translateToLocal(
                    "gui.losttales.chat.report.not_player");
        }
        return "";
    }

    /** The report's rows: a reason each, which sends the report. */
    private static List<ChatMenu.Entry> reportRows() {
        List<ChatMenu.Entry> entries = new ArrayList<ChatMenu.Entry>();
        for (ChatReportReason reason : ChatReportReason.values()) {
            entries.add(new ChatMenu.Entry(
                    ENTRY_REPORT_REASON_PREFIX + reason.name(),
                    StatCollector.translateToLocal(reason.langKey())));
        }
        return entries;
    }

    /** Sends the report of the message for the reason a row names, with the note typed. */
    private static void sendReport(MessageAim aim, String entryId,
                                   String note) {
        ChatReportReason reason = null;
        for (ChatReportReason known : ChatReportReason.values()) {
            if ((ENTRY_REPORT_REASON_PREFIX + known.name()).equals(entryId)) {
                reason = known;
            }
        }
        if (reason == null || aim == null
                || !ChatMessageIds.isServerId(aim.messageId)) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesChatReportPacket(aim.messageId, reason, note));
    }

    /**
     * Why the message drawn on {@code chatLineId} cannot be linked to, or
     * empty where it can: a line the server never named, or one of a
     * channel a link cannot spell.
     */
    static String whyNotLinkable(int chatLineId) {
        if (messageLinkFor(chatLineId) != null) {
            return "";
        }
        String unnamed = LostTalesChatPresentation.unnamedReason(chatLineId);
        return unnamed.length() > 0 ? unnamed : StatCollector.translateToLocal(
                "gui.losttales.chat.message.not_linkable");
    }

    /**
     * The link that names the message drawn on {@code chatLineId} —
     * {@code #ooc/<server id>}, {@code #gondor/<server id>} — or null for
     * a line the server never named, or a conversation without a code name.
     */
    static String messageLinkFor(int chatLineId) {
        long messageId = ClientChatMessageIds.messageIdOf(chatLineId);
        ChatTab tab = ClientChatChannelViews.tabOf(chatLineId);
        return tab == null ? null : ChatChannelSuggester.messageLink(
                tab.getChannel(), tab.getOwnerKey(), messageId);
    }

    /**
     * The menu over a person — the sender's identity span, a mention or a
     * member's row: message them, ignore them. The message's own menu
     * stays with the message body; this one is account and character
     * business, so it opens only over somebody who can be addressed — not
     * an NPC, not a role mention, and not yourself. A Discord member can
     * be ignored but not whispered to, so their menu offers no message
     * row; the bridge's own nameless id is nobody and opens nothing. Its
     * strip names who it is about, as their card's does.
     */
    boolean openPersonMenu(LostTalesChatHoverCard.Target person, int mouseX,
                           int mouseY) {
        if (person == null || person.role != null || person.npcIdentity
                || person.playerId == null
                || person.accountName.length() == 0
                || LostTalesChatMessagePacket.DISCORD_SENDER_ID.equals(
                        person.playerId)
                || LostTalesChatMessagePacket.isSystemSender(person.playerId)
                || (this.mc.thePlayer != null && person.playerId.equals(
                        this.mc.thePlayer.getUniqueID()))) {
            return false;
        }
        show(ChatSmallWindowKind.PERSON, person,
                hangingFrom(pointerAnchor(mouseX, mouseY)), false);
        return true;
    }

    /** The name a person's rows speak of: the identity, else the account. */
    private static String personName(LostTalesChatHoverCard.Target person) {
        return LostTalesChatVisualStyle.removeColorCodes(
                person.identityName).trim();
    }

    private static List<ChatMenu.Entry> personRows(
            LostTalesChatHoverCard.Target person) {
        List<ChatMenu.Entry> entries = new ArrayList<ChatMenu.Entry>();
        String name = personName(person);
        if (!LostTalesChatMessagePacket.isDiscordSender(person.playerId)) {
            entries.add(new ChatMenu.Entry(ENTRY_MESSAGE,
                    StatCollector.translateToLocalFormatted(
                            "gui.losttales.chat.message.whisper",
                            name.length() > 0 ? name : person.accountName)));
        }
        // A character can be ignored on its own, the account's other
        // characters still heard; the account row then says it is the
        // account. Both rows flip to lifting what they laid.
        boolean identityRow = name.length() > 0
                && !name.equalsIgnoreCase(person.accountName);
        if (identityRow) {
            entries.add(new ChatMenu.Entry(ENTRY_IGNORE_IDENTITY,
                    StatCollector.translateToLocalFormatted(
                            ClientChatIgnores.isIgnoredIdentity(
                                    person.playerId, name)
                                    ? "gui.losttales.chat.message.unignore"
                                    : "gui.losttales.chat.message.ignore",
                            name)));
        }
        boolean accountIgnored = ClientChatIgnores.isIgnored(person.playerId);
        entries.add(new ChatMenu.Entry(ENTRY_IGNORE,
                StatCollector.translateToLocalFormatted(
                        identityRow
                                ? accountIgnored
                                        ? "gui.losttales.chat.message.unignore_account"
                                        : "gui.losttales.chat.message.ignore_account"
                                : accountIgnored
                                        ? "gui.losttales.chat.message.unignore"
                                        : "gui.losttales.chat.message.ignore",
                        person.accountName)));
        if (ClientChatChannelState.canModerate()) {
            // The server's mute, for moderators: the row offers to lift
            // the mute the server says is in force, else to lay one. The
            // server tells moderators the muted set with their access and
            // again whenever it changes, and decides for itself anyway.
            boolean muted = ClientChatChannelState.isMutedSender(
                    person.playerId);
            entries.add(new ChatMenu.Entry(
                    muted ? ENTRY_UNMUTE_ACCOUNT : ENTRY_MUTE_ACCOUNT,
                    StatCollector.translateToLocalFormatted(muted
                            ? "gui.losttales.chat.message.unmute"
                            : "gui.losttales.chat.message.mute",
                            person.accountName))
                    .withLabelColor(OPERATOR_ACTION_COLOR));
        }
        return entries;
    }

    /** One row of a person's menu: the ignores are switches and stay; the rest are done with it. */
    private boolean actOnPerson(LostTalesChatHoverCard.Target person,
                                ChatMenu.Entry entry) {
        boolean fromDiscord = LostTalesChatMessagePacket.isDiscordSender(
                person.playerId);
        if (ENTRY_IGNORE.equals(entry.id)) {
            toggleIgnore(person);
            return true;
        }
        if (ENTRY_IGNORE_IDENTITY.equals(entry.id)) {
            toggleIgnoreIdentity(person);
            return true;
        }
        if (ENTRY_MESSAGE.equals(entry.id)) {
            this.tabActions.openWhisperTab(person.accountName,
                    person.identityName);
        } else if (ENTRY_MUTE_ACCOUNT.equals(entry.id)) {
            startMute(muteTarget(fromDiscord, person.accountName));
        } else if (ENTRY_UNMUTE_ACCOUNT.equals(entry.id)) {
            this.pendingCommand = "/losttales chat unmute "
                    + muteTarget(fromDiscord, person.accountName);
        }
        return false;
    }

    /**
     * The character menu, hung from the head button — a switch, as the
     * button is — with a search field over its rows that narrows them as
     * it is typed into. It follows the tab being typed in: on an account
     * channel, which always speaks as the account, it is the status rows
     * alone, since nothing there chooses an identity, with no roster and
     * no search field.
     */
    void toggleCharacterMenu(ChatMenu.Anchor anchor) {
        show(ChatSmallWindowKind.CHARACTERS, null, hangingFrom(anchor), true);
    }

    /** The character menu's field, name and rows, for the tab typed in now. */
    private void rebuildCharacters(ChatMenu menu) {
        boolean statusOnly = !ClientChatIdentities.speaksInCharacter(
                ClientChatChannelState.getSelected());
        if (statusOnly && menu.hasField()) {
            menu.closeField();
        } else if (!statusOnly && !menu.hasField()) {
            menu.openField(StatCollector.translateToLocal(
                            "gui.losttales.chat.character_selection.search"),
                    null, LostTalesUiSheet.SEARCH, ChatMenu.MAX_FILTER_LENGTH);
        }
        menu.setTitle(StatCollector.translateToLocal(statusOnly
                ? "gui.losttales.chat.character_selection.status"
                : "gui.losttales.chat.small_window.characters"), null);
        menu.setRows(characterSelectionEntries(menu.filter(), statusOnly));
    }

    /**
     * The selected chat identity, then the owned characters and the owned
     * lore characters, each section under its header. A filter keeps the
     * rows whose names hold it and drops a section with nothing left; one
     * that matches nothing says so under the current identity rather than
     * closing the menu under the hand that is typing.
     */
    private List<ChatMenu.Entry> characterSelectionEntries(String filter,
                                                           boolean statusOnly) {
        UUID self = this.mc.thePlayer == null ? null
                : this.mc.thePlayer.getUniqueID();
        ClientChatIdentities.Identity current = ClientChatIdentities.viewing();
        List<ChatMenu.Entry> entries = new ArrayList<ChatMenu.Entry>();
        if (statusOnly) {
            // The account speaking, and what it may say of itself.
            entries.add(ChatMenu.Entry.passive(
                    ClientChatIdentities.accountName())
                    .withHead(self, ""));
            addSection(entries,
                    "gui.losttales.chat.character_selection.status",
                    statusRows());
            return entries;
        }
        entries.add(ChatMenu.Entry.passive(ClientChatIdentities.isNarrating()
                ? ChatNarrator.NAME : current.name)
                .withHead(self, current.account ? "" : current.skinId));
        if (ClientChatChannelState.holds(LostTalesCapability.CHAT_NARRATE)) {
            // The Narrator is a voice over the identity, not one of
            // them: it stands above the roster, marked while chosen.
            entries.add(new ChatMenu.Entry(ENTRY_NARRATOR,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.character_selection.narrator"),
                    false, ClientChatIdentities.isNarrating()
                            ? LostTalesColors.rgb(LostTalesColors.HONEY) : -1, null)
                    .withSprite(LostTalesUiSheet.SPEECH_BUBBLE,
                            LostTalesUiSheet.SPEECH_BUBBLE_HOVER,
                            ClientChatIdentities.isNarrating()));
        }
        addSection(entries,
                "gui.losttales.chat.character_selection.characters",
                characterRows(ClientChatIdentities.characterIdentities(),
                        filter, self));
        addSection(entries, "gui.losttales.chat.character_selection.lore",
                characterRows(ClientChatIdentities.loreIdentities(),
                        filter, self));
        if (entries.size() == 1) {
            entries.add(ChatMenu.Entry.passive(
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.character_selection.none")));
        }
        addSection(entries, "gui.losttales.chat.character_selection.status",
                statusRows());
        return entries;
    }

    /**
     * The statuses to choose from for the identity the selected tab
     * speaks as — the chat identity on a roleplaying tab, the account on
     * any other — each with the sphere it shows, lighting to the ivory
     * one under the pointer, and the one chosen for it marked; and under
     * them the identity's status line, in italics, or the way to set one.
     */
    private static List<ChatMenu.Entry> statusRows() {
        ChatPresenceIdentity speaker = ClientChatPresence.speakerOf(
                ClientChatChannelState.getSelected());
        ChatPresence chosen = ClientChatPresence.chosen(speaker);
        List<ChatMenu.Entry> rows = new ArrayList<ChatMenu.Entry>();
        for (ChatPresence presence : ChatPresence.values()) {
            if (!presence.isChoosable()) {
                continue;
            }
            rows.add(new ChatMenu.Entry(ENTRY_STATUS_PREFIX + presence.name(),
                    StatCollector.translateToLocal(presence.labelKey()), false,
                    chosen == presence
                            ? LostTalesColors.rgb(LostTalesColors.HONEY) : -1, null)
                    .withSprite(ChatPresenceMark.markOf(presence),
                            LostTalesUiSheet.PRESENCE_SELECTED, false));
        }
        String line = ClientChatPresence.chosenLine(speaker);
        rows.add(new ChatMenu.Entry(ENTRY_STATUS_LINE,
                line.length() == 0 ? StatCollector.translateToLocal(
                        "gui.losttales.chat.status_line.set") : line,
                line.length() > 0, -1, null)
                .withEmojis()
                .withSprite(LostTalesUiSheet.SPEECH_BUBBLE,
                        LostTalesUiSheet.SPEECH_BUBBLE_HOVER, false));
        return rows;
    }

    /** One section's rows: the identities whose names hold the filter. */
    private static List<ChatMenu.Entry> characterRows(
            List<ClientChatIdentities.Identity> identities,
            String filter, UUID self) {
        List<ChatMenu.Entry> rows = new ArrayList<ChatMenu.Entry>();
        for (ClientChatIdentities.Identity identity : identities) {
            if (matchesFilter(identity.name, filter)) {
                rows.add(characterEntry(
                        ENTRY_CHARACTER_PREFIX + identity.characterId,
                        identity, self));
            }
        }
        return rows;
    }

    /** One choosable identity: its head, its name, and the mention honey
     *  as the swatch of the shared chat identity. */
    private static ChatMenu.Entry characterEntry(
            String id, ClientChatIdentities.Identity identity,
            UUID self) {
        boolean effective = ClientChatIdentities.isSelected(identity);
        return new ChatMenu.Entry(id, identity.name, false,
                effective ? LostTalesColors.rgb(LostTalesColors.HONEY) : -1,
                null).withHead(self, identity.skinId);
    }

    /** A choice applies to every roleplaying conversation. */
    private static void chooseIdentityOrStatus(ChatMenu.Entry entry) {
        if (ENTRY_NARRATOR.equals(entry.id)) {
            ClientChatIdentities.setNarrating(!ClientChatIdentities.isNarrating());
            return;
        }
        if (entry.id.startsWith(ENTRY_STATUS_PREFIX)) {
            try {
                ClientChatPresence.choose(ClientChatPresence.speakerOf(
                                ClientChatChannelState.getSelected()),
                        ChatPresence.valueOf(entry.id.substring(
                                ENTRY_STATUS_PREFIX.length())));
            } catch (IllegalArgumentException ignored) {
                // A row this build never made names no status.
            }
            return;
        }
        if (!entry.id.startsWith(ENTRY_CHARACTER_PREFIX)) {
            return;
        }
        UUID characterId;
        try {
            characterId = UUID.fromString(
                    entry.id.substring(ENTRY_CHARACTER_PREFIX.length()));
        } catch (IllegalArgumentException ignored) {
            return;
        }
        for (ClientChatIdentities.Identity identity
                : ClientChatIdentities.characterIdentities()) {
            if (characterId.equals(identity.characterId)) {
                ClientChatIdentities.select(identity);
                return;
            }
        }
        for (ClientChatIdentities.Identity identity
                : ClientChatIdentities.loreIdentities()) {
            if (characterId.equals(identity.characterId)) {
                ClientChatIdentities.select(identity);
                return;
            }
        }
    }

    /**
     * The status line's rows under its field, for the identity it was
     * opened for: Enter or the first row keeps what the field holds, the
     * second clears the line, and closing the window leaves it as it was.
     */
    private static List<ChatMenu.Entry> statusLineRows(
            ChatPresenceIdentity identity) {
        List<ChatMenu.Entry> entries = new ArrayList<ChatMenu.Entry>(2);
        entries.add(new ChatMenu.Entry(ENTRY_STATUS_LINE_KEEP,
                StatCollector.translateToLocal(
                        "gui.losttales.chat.status_line.keep")));
        if (ClientChatPresence.chosenLine(identity).length() > 0) {
            entries.add(new ChatMenu.Entry(ENTRY_STATUS_LINE_CLEAR,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.status_line.clear")));
        }
        return entries;
    }

    /* ---- What the message rows do ---- */

    /** Answers the message, in its own tab. */
    private void startReply(MessageAim aim) {
        if (aim.tab == null) {
            return;
        }
        // A line the server named is answered by its id; a client-local
        // one by its own id here and by its words alone on the wire. The
        // chip and the local quote name the identity the line was signed
        // with, exactly as the server's own quote will.
        String name = aim.identity.length() > 0 ? aim.identity : aim.account;
        if (!ChatMessageIds.isServerId(aim.messageId)) {
            name = LostTalesChatPresentation.quoteAuthorFor(name);
        }
        // Composing happens where the message lives, and selecting a tab
        // clears any reply, so the target is set after the move.
        this.tabActions.selectChannel(aim.tab);
        this.composer.startReply(aim.tab, aim.messageId, name, aim.text,
                LostTalesChatPresentation.headOfLine(aim.chatLineId));
    }

    /**
     * Whether the message is this player's own, and one the server can
     * still be asked about. A line from the Discord bridge never is,
     * whatever name it carries.
     */
    private boolean isOwnMessage(MessageAim aim) {
        return isOwnMessage(aim.messageId, aim.fromDiscord, aim.account,
                this.mc.thePlayer == null ? null
                        : this.mc.thePlayer.getCommandSenderName());
    }

    /** As above, over the facts themselves. */
    static boolean isOwnMessage(long messageId, boolean fromDiscord,
                                String account, String self) {
        return ChatMessageIds.isServerId(messageId) && !fromDiscord
                && self != null && account.equalsIgnoreCase(self);
    }

    /**
     * Whether this player, as a moderator, may take the message back from
     * everyone: any line the server can still be asked about, another
     * player's or a Discord member's alike, but no entry of the Server
     * Console — the server's record of what happened, which nobody takes
     * back. The server checks the capability again on the request.
     */
    private static boolean canModerateMessage(MessageAim aim) {
        return ChatMessageIds.isServerId(aim.messageId)
                && ClientChatChannelState.canModerate()
                && !(aim.tab != null
                        && aim.tab.getChannel() == ChatChannel.SERVER_CONSOLE
                        && LostTalesChatMessagePacket.isServerSender(
                                aim.senderId));
    }

    /**
     * Puts the message back in the bar to be rewritten. What goes into
     * the field is the text that was <em>sent</em> — the markup as it was
     * typed, not the line as it reads — so editing a formatted message
     * does not quietly flatten it.
     */
    private void startEdit(MessageAim aim) {
        ClientChatMessages.Remembered remembered =
                ClientChatMessages.get(aim.messageId);
        if (!isOwnMessage(aim) || aim.tab == null || remembered == null) {
            return;
        }
        // Composing happens where the message lives, and selecting a tab
        // puts down whatever was being composed, so the target is set
        // after the move.
        this.tabActions.selectChannel(aim.tab);
        this.composer.startEdit(aim.tab, aim.messageId);
        this.field.setText(remembered.packet.getMessage());
        ClientChatChannelState.setDraft(remembered.packet.getMessage());
    }

    /**
     * Asks the server to take the message back. Nothing is removed here:
     * the line goes when the server says it has gone, so every screen
     * showing it — this one included — loses it for the same reason and
     * at the same time.
     */
    private void confirmDelete(MessageAim aim) {
        if (!ChatMessageIds.isServerId(aim.messageId)) {
            return;
        }
        this.composer.forgetEditOf(aim.messageId);
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesChatDeletePacket(aim.messageId));
    }

    /**
     * Puts the mute command in the bar with {@code target} filled in and
     * the caret after it, so the operator adds a duration and a reason —
     * or none — and sends it with Enter; the server does the muting and
     * answers in the console.
     */
    private void startMute(String target) {
        this.composer.cancelReply();
        String command = "/losttales chat mute " + target + " ";
        this.field.setText(command);
        this.field.setCursorPositionEnd();
        ClientChatChannelState.setDraft(command);
    }

    /**
     * How the mute command names a person: a player by account, a
     * Discord member as {@code discord:<name>}, which the server resolves
     * through the bridge's memory of who bore the name.
     */
    static String muteTarget(boolean fromDiscord, String account) {
        return fromDiscord ? "discord:" + account : account;
    }

    /**
     * Starts or stops ignoring the account behind the person. Existing
     * lines stay — ignoring quiets what has not been said yet — and the
     * notice says which way it went.
     */
    private void toggleIgnore(LostTalesChatHoverCard.Target person) {
        if (ClientChatIgnores.isIgnored(person.playerId)) {
            ClientChatIgnores.unignore(person.playerId);
            this.notices.showNotice(StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.unignored", person.accountName));
        } else if (ClientChatIgnores.ignore(person.playerId,
                person.accountName)) {
            ClientChatIgnores.rememberName(person.playerId,
                    person.identityName);
            this.notices.showNotice(StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.ignored", person.accountName));
        } else {
            this.notices.showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.ignore_full"));
        }
    }

    /**
     * Starts or stops ignoring the one identity of the person, the
     * account's other identities still heard.
     */
    private void toggleIgnoreIdentity(LostTalesChatHoverCard.Target person) {
        String name = personName(person);
        if (name.length() == 0) {
            return;
        }
        if (ClientChatIgnores.isIgnoredIdentity(person.playerId, name)) {
            ClientChatIgnores.unignoreIdentity(person.playerId, name);
            this.notices.showNotice(StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.unignored", name));
        } else if (ClientChatIgnores.ignoreIdentity(person.playerId, name)) {
            this.notices.showNotice(StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.ignored_identity", name));
        } else {
            this.notices.showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.ignore_full"));
        }
    }

    /* ---- Reading the drawn lines ---- */

    /**
     * The account behind the message the band names: the reply target
     * every part of a sender's name carries, looked for over the whole
     * message rather than the one wrapped line that was clicked.
     */
    static String messageAccount(List<ChatLine> lines, int index,
                                 int chatLineId) {
        if (lines == null) {
            return "";
        }
        for (int step = 0; step < lines.size(); step++) {
            for (int side = 0; side < 2; side++) {
                int at = side == 0 ? index - step : index + step;
                if (at < 0 || at >= lines.size() || lines.get(at) == null
                        || lines.get(at).getChatLineID() != chatLineId) {
                    continue;
                }
                ClickEvent reply = findReplySuggestion(
                        lines.get(at).func_151461_a());
                if (reply != null) {
                    return replyAccount(reply.getValue());
                }
            }
        }
        return "";
    }

    /**
     * The identity the message was signed with: the name that answers to
     * the pointer first after the head, which is the sender's own. Empty
     * when the message names nobody.
     */
    static String messageIdentity(List<ChatLine> lines, int index,
                                  int chatLineId) {
        if (lines == null) {
            return "";
        }
        for (int step = 0; step < lines.size(); step++) {
            for (int side = 0; side < 2; side++) {
                int at = side == 0 ? index - step : index + step;
                if (at < 0 || at >= lines.size() || lines.get(at) == null
                        || lines.get(at).getChatLineID() != chatLineId) {
                    continue;
                }
                String name = identityOfLine(lines.get(at).func_151461_a());
                if (name.length() > 0) {
                    return name;
                }
            }
        }
        return "";
    }

    /** The first name-run after the head marker on one drawn line. */
    static String identityOfLine(IChatComponent line) {
        boolean afterHead = false;
        for (Object value : line) {
            IChatComponent part = (IChatComponent)value;
            if (ChatHeadMarker.decode(part) != null) {
                afterHead = true;
                continue;
            }
            if (!afterHead) {
                continue;
            }
            if (ChatSenderSpan.isSenderName(part)) {
                return part.getUnformattedTextForChat().trim();
            }
        }
        return "";
    }

    /** Whether the message came over the Discord bridge. */
    static boolean isFromDiscord(List<ChatLine> lines, int chatLineId) {
        for (int at = 0; at < lines.size(); at++) {
            if (lines.get(at) == null
                    || lines.get(at).getChatLineID() != chatLineId) {
                continue;
            }
            for (Object value : lines.get(at).func_151461_a()) {
                ChatHeadMarker.Data head = ChatHeadMarker.decode(
                        (IChatComponent)value);
                if (head != null) {
                    return head.isDiscordSender();
                }
            }
        }
        return false;
    }

    /** The account named by a {@code /msg Account } suggestion. */
    static String replyAccount(String suggestion) {
        return ChatSenderSpan.accountOf(suggestion);
    }

    /** The line's {@code /msg} suggestion, shared by name and head. */
    static ClickEvent findReplySuggestion(IChatComponent line) {
        return ChatSenderSpan.findSuggestion(line);
    }
}
