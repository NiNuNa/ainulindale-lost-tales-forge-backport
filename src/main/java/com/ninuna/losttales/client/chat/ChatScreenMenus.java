package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelSuggester;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.client.input.LostTalesKeyPress;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatDeletePacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.chat.ChatNarrator;
import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.permission.LostTalesCapability;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * Every menu the chat screen opens and what its rows do: a tab's
 * settings, a window's own menu, the tab search panel, the {@code +}
 * menu of closed channels and conversations, the menu over a message,
 * the menu over a person, and the character selection menu — plus the
 * facts the message menu reads off the drawn lines. One popup at a
 * time; the screen draws it, feeds it keys and clicks, and asks what is
 * open so the strip's controls can show it.
 */
final class ChatScreenMenus {
    static final String POPUP_MESSAGE = "message";
    static final String POPUP_PLAYER = "player";
    static final String POPUP_SETTINGS = "settings";
    static final String POPUP_CHARACTERS = "characters";
    static final String POPUP_RESTORE = "restore";
    static final String POPUP_WINDOW = "window";
    static final String POPUP_SEARCH = "search";
    /** The status line's field, opened from the head button's menu. */
    static final String POPUP_STATUS_LINE = "status_line";
    /** Marks a search row that jumps to a tab already open. */
    private static final String ENTRY_OPEN_PREFIX = "open:";
    /** The keys the search panel's field names as its own shortcut. */
    private static final int[] SEARCH_SHORTCUT_KEYS = {
            Keyboard.KEY_LCONTROL, Keyboard.KEY_LSHIFT, Keyboard.KEY_A };
    private static final String ENTRY_MESSAGE = "message_player";
    private static final String ENTRY_REPLY = "reply";
    private static final String ENTRY_REACT = "react";
    private static final String ENTRY_COPY = "copy";
    static final String ENTRY_COPY_LINK = "copy_link";
    private static final String ENTRY_EDIT = "edit";
    private static final String ENTRY_DELETE = "delete";
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
            ChatChannel.ADMIN.getDisplayColor();
    /** Operators only: the server mute, put in the bar to be completed. */
    private static final String ENTRY_MUTE_ACCOUNT = "mute_account";
    private static final String ENTRY_UNMUTE_ACCOUNT = "unmute_account";
    private static final String ENTRY_MUTE = "mute";
    private static final String ENTRY_PINGS = "pings";
    private static final String ENTRY_HIDE = "hide";
    private static final String ENTRY_DETACH = "detach";
    private static final String ENTRY_MARK_READ = "mark_read";
    private static final String ENTRY_STATUS_LINE = "characters:status_line";
    /** The start of a character's row id in the characters menu. */
    private static final String ENTRY_CHARACTER_PREFIX = "characters:char:";
    private static final String ENTRY_STATUS_LINE_KEEP = "status_line:keep";
    private static final String ENTRY_STATUS_LINE_CLEAR = "status_line:clear";
    private static final String ENTRY_JUMP_UNREAD = "jump_unread";
    private static final String ENTRY_WINDOW_UNSTICK = "window_unstick";
    private static final String ENTRY_WINDOW_RESET = "window_reset";
    /** The window menu's colour rows, each opening the palette for one surface. */
    private static final String ENTRY_WINDOW_COLOR_BACKGROUND =
            "window_color_background";
    private static final String ENTRY_WINDOW_COLOR_SELECTED =
            "window_color_selected";
    private static final String ENTRY_WINDOW_COLOR_MENTION =
            "window_color_mention";
    private static final String ENTRY_WINDOW_COLOR_SELECTED_MENTION =
            "window_color_selected_mention";
    private static final String ENTRY_WINDOW_COLOR_REPLY =
            "window_color_reply";
    /** Marks a palette row; the rest of the id is the palette entry's name. */
    private static final String ENTRY_COLOR_PREFIX = "color:";
    /** The palette menu, opened from one of the window menu's colour rows. */
    static final String POPUP_COLOR = "color";
    /** How often the open {@code +} menu re-reads its rows. */
    private static final long RESTORE_REFRESH_NANOS = 500L * 1000000L;

    /** What a click on an open menu came to. */
    static final class Click {
        /** Whether the click was the menu's and goes no further. */
        final boolean consumed;
        /** The kind of the menu this click closed; empty for none open. */
        final String closedKind;
        /**
         * The window whose search panel or {@code +} menu this click
         * closed; null for the empty screen's own {@code +} and for any
         * other menu.
         */
        final String closedWindowId;
        /** The message whose menu this click closed, by chat line id; zero for any other menu. */
        final int closedChatLineId;
        /** A command a row asked the screen to send, or null. */
        final String command;

        Click(boolean consumed, String closedKind, String closedWindowId,
              int closedChatLineId, String command) {
            this.consumed = consumed;
            this.closedKind = closedKind;
            this.closedWindowId = closedWindowId;
            this.closedChatLineId = closedChatLineId;
            this.command = command;
        }

        /**
         * Whether this click put away the menu of the message drawn on
         * {@code chatLineId}: the press on that message's own menu button
         * that must not open it again.
         */
        boolean closedMessageMenu(int chatLineId) {
            return POPUP_MESSAGE.equals(this.closedKind) && chatLineId != 0
                    && chatLineId == this.closedChatLineId;
        }

        /**
         * Whether this click put away the {@code kind} menu of the window
         * {@code windowId} (null for the empty screen): the press on its
         * own control that must not open it again.
         */
        boolean closed(String kind, String windowId) {
            return kind.equals(this.closedKind) && (windowId == null
                    ? this.closedWindowId == null
                    : windowId.equals(this.closedWindowId));
        }
    }

    private final ChatTabActions tabActions;
    private final ChatComposer composer;
    private final ChatNoticeSink notices;
    private final ChatPopupMenu popup = new ChatPopupMenu();
    private Minecraft mc;
    private FontRenderer font;
    private GuiTextField field;
    private int screenWidth;
    private int screenHeight;

    /** The message the open message menu was opened over. */
    private long menuMessageId = ChatMessageIds.NONE;
    private String menuMessageAccount = "";
    /** The identity that message was signed with: who a whisper reaches. */
    private String menuMessageIdentity = "";
    /** Whether it came over the Discord bridge, where nobody can be reached. */
    private boolean menuMessageFromDiscord;
    /**
     * Whether the head button's menu was opened on an account channel,
     * where it offers a status and nothing else: kept from the opening,
     * so the rows stay the same while the menu is up.
     */
    private boolean charactersStatusOnly;
    /** The identity the status line's field is open for. */
    private ChatPresenceIdentity statusLineIdentity;
    /** The sender's account, when the line still has its packet; else null. */
    private UUID menuMessageSenderId;
    private String menuMessageText = "";
    /** The line the message menu was opened over. */
    private int menuChatLineId;
    /** The window whose message toolbar opened the message menu, or null for a right click. */
    private ChatWindowFrame menuToolbarFrame;
    /** Window a restore popup was opened from. */
    private String restoreWindowId;
    /** Window the open window-settings menu belongs to, or null. */
    private String settingsWindowId;
    /** Where that menu was opened, so the palette opens in its place. */
    private ChatPopupMenu.Anchor settingsAnchor;
    /** Which surface the open palette menu recolours: a colour row's id. */
    private String colorRole;
    private long restoreRefreshedNanos;
    /** A command a chosen row asked for, handed to the screen with the click. */
    private String pendingCommand;
    /** A message the menu was asked to react to, until the screen takes it. */
    private long pendingReactionTarget = ChatMessageIds.NONE;

    ChatScreenMenus(ChatTabActions tabActions, ChatComposer composer,
                    ChatNoticeSink notices) {
        this.tabActions = tabActions;
        this.composer = composer;
        this.notices = notices;
    }

    /** Called from {@code initGui}, which also runs on every resize. */
    void bind(Minecraft mc, FontRenderer font, GuiTextField field,
              int screenWidth, int screenHeight) {
        this.mc = mc;
        this.font = font;
        this.field = field;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /* ---- What is open ---- */

    boolean isOpen() {
        return this.popup.isOpen();
    }

    /**
     * The line the open message menu was opened over, or zero while no
     * message menu is open.
     */
    int messageMenuChatLineId() {
        return isOpen() && POPUP_MESSAGE.equals(kind())
                ? this.menuChatLineId : 0;
    }

    String kind() {
        return this.popup.kind();
    }

    boolean isKindOpen(String kind) {
        return this.popup.isOpen() && kind.equals(this.popup.kind());
    }

    /** Whether an open menu has a field that holds the keys. */
    boolean isTyping() {
        return this.popup.isOpen() && this.popup.isSearchable();
    }

    /** Whether this window's own search panel or {@code +} menu is out. */
    boolean isOpenFor(String kind, String windowId) {
        return isKindOpen(kind) && windowId.equals(this.restoreWindowId);
    }

    void close() {
        this.popup.close();
    }

    boolean contains(double mouseX, double mouseY) {
        return this.popup.contains(mouseX, mouseY);
    }

    /** The row of the open menu under the point that does something, or null. */
    ChatPopupMenu.Entry entryAt(double mouseX, double mouseY) {
        return this.popup.entryAt(mouseX, mouseY);
    }

    /** Why the open menu's row under the point cannot be taken, or empty. */
    String unavailableAt(double mouseX, double mouseY) {
        return this.popup.unavailableAt(mouseX, mouseY);
    }

    void scrollBy(double rows) {
        this.popup.scrollBy(rows);
    }

    void registerRegion(ChatPointerRegions regions) {
        this.popup.registerRegion(regions);
    }

    /** Draws the open menu; the pointer is {@link ChatHover#AWAY} unless the menu has it. */
    void draw(ChatPointerRegions regions, double mouseX, double mouseY) {
        this.popup.draw(this.font, regions, mouseX, mouseY);
    }

    /**
     * A list that is typed into takes the keys while it is open: what is
     * typed goes into its field, Enter takes the first row the typing
     * found, and no key reaches the chat bar the list covers. Only the
     * chat's own Ctrl shortcuts reach past it. True when the list took
     * the key.
     */
    boolean handleKeyTyped(LostTalesKeyPress press) {
        if (!this.popup.isOpen() || !this.popup.isSearchable()) {
            return false;
        }
        if (press.is(Keyboard.KEY_RETURN)
                || press.is(Keyboard.KEY_NUMPADENTER)) {
            ChatPopupMenu.Entry found = firstFound();
            if (found != null && !handlePopupEntry(found)) {
                this.popup.close();
            }
            return true;
        }
        if (press.command && !ChatPopupMenu.isFieldCommand(press)) {
            return false;
        }
        if (this.popup.handleKeyTyped(press)) {
            refreshSearchPanel();
        }
        return true;
    }

    /**
     * The row Enter takes: in the status line's field its first row,
     * which keeps what the field holds; elsewhere the first row the
     * typing found, and none while nothing is typed. The rows typing
     * does not narrow, the Narrator's and the statuses, are never
     * taken.
     */
    private ChatPopupMenu.Entry firstFound() {
        String kind = this.popup.kind();
        boolean statusLine = POPUP_STATUS_LINE.equals(kind);
        if (!statusLine && this.popup.filter().length() == 0) {
            return null;
        }
        for (ChatPopupMenu.Entry entry : this.popup.entries()) {
            if (entry.header || entry.passive) {
                continue;
            }
            if (statusLine || POPUP_SEARCH.equals(kind)
                    || entry.id.startsWith(ENTRY_CHARACTER_PREFIX)) {
                return entry;
            }
        }
        return null;
    }

    /** An entry acts and closes the menu; an outside press continues behind it. */
    Click click(double mouseX, double mouseY, int button) {
        if (!this.popup.isOpen()) {
            return new Click(false, "", null, 0, null);
        }
        String closedKind = this.popup.kind();
        String closedWindowId = POPUP_SEARCH.equals(closedKind)
                || POPUP_RESTORE.equals(closedKind)
                ? this.restoreWindowId : null;
        int closedChatLineId = POPUP_MESSAGE.equals(closedKind)
                ? this.menuChatLineId : 0;
        ChatPopupMenu.Entry entry = this.popup.entryAt(mouseX, mouseY);
        boolean inside = this.popup.contains(mouseX, mouseY);
        this.pendingCommand = null;
        if (entry != null && button == 0 && handlePopupEntry(entry)) {
            // The entry asked a question of its own and the menu is
            // showing it: closing here would close the question along
            // with the menu that asked it.
            return new Click(true, closedKind, closedWindowId,
                    closedChatLineId, this.pendingCommand);
        }
        this.popup.close();
        return new Click(inside, closedKind, closedWindowId,
                closedChatLineId, this.pendingCommand);
    }

    /**
     * Ctrl+Shift+A: the search panel of the window being typed in, or
     * away again when that very panel is out, as its control on the
     * strip is a switch too. Out for another window, it moves here.
     */
    void toggleSearchPanel(ChatWindow window, ChatPopupMenu.Anchor emptyPlus) {
        String windowId = window == null ? null : window.getId();
        if (isKindOpen(POPUP_SEARCH) && sameWindow(windowId)) {
            close();
            return;
        }
        openSearchPanel(window, null, emptyPlus);
    }

    /**
     * Ctrl+N: the {@code +} menu of the window being typed in (or the
     * empty screen's), or away again when it is already out there.
     */
    void toggleChannelMenu(ChatPopupMenu.Anchor emptyPlus) {
        ChatWindow window = emptyPlus != null ? null : ChatWindowLayout.windowOf(
                ClientChatChannelState.getSelected());
        if (isKindOpen(POPUP_RESTORE)
                && sameWindow(window == null ? null : window.getId())) {
            close();
            return;
        }
        openChannelMenu(emptyPlus);
    }

    /** Whether the open search panel or {@code +} menu belongs to {@code windowId}. */
    private boolean sameWindow(String windowId) {
        return windowId == null ? this.restoreWindowId == null
                : windowId.equals(this.restoreWindowId);
    }

    /* ---- The strip's menus ---- */

    /**
     * The tab's menu — behind the cog, and under a right-click on the
     * tab — each entry its own independent preference or action. While
     * the tab holds anything unread it opens as a messenger's channel
     * menu does, with Mark as Read (the counters and the divider gone at
     * once) and Jump to First Unread (the tab brought forward and its
     * history taken to where the unread run begins). Then Mute Channel
     * (out of the feed), Mute Mentions (cue silent), Hide Channel (stays
     * closed when messaged), and Move to its own Window. Closing is the
     * cross on the tab and nothing else: a row that only repeats the
     * button beside it is a second way to lose a tab by accident.
     */
    void openSettingsPopup(ChatTab channel, ChatPopupMenu.Anchor anchor) {
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>(6);
        if (ClientChatChannelViews.hasUnread(channel)
                || ClientChatChannelViews.unreadDividerLine(channel) != null) {
            entries.add(new ChatPopupMenu.Entry(ENTRY_MARK_READ,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.tab.mark_read")));
        }
        if (ClientChatChannelViews.unreadDividerLine(channel) != null) {
            entries.add(new ChatPopupMenu.Entry(ENTRY_JUMP_UNREAD,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.tab.jump_unread")));
        }
        entries.add(new ChatPopupMenu.Entry(ENTRY_MUTE,
                StatCollector.translateToLocal(
                        ChatWindowLayout.isMuted(channel)
                                ? "gui.losttales.chat.tab.unmute"
                                : "gui.losttales.chat.tab.mute")));
        entries.add(new ChatPopupMenu.Entry(ENTRY_PINGS,
                StatCollector.translateToLocal(
                        ChatWindowLayout.isPingsMuted(channel)
                                ? "gui.losttales.chat.tab.unmute_mentions"
                                : "gui.losttales.chat.tab.mute_mentions")));
        entries.add(new ChatPopupMenu.Entry(ENTRY_HIDE,
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
            entries.add(new ChatPopupMenu.Entry(ENTRY_DETACH,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.tab.detach")));
        }
        this.popup.open(POPUP_SETTINGS, channel, entries, this.font,
                anchor, this.screenWidth, this.screenHeight);
    }

    /**
     * The window's own menu, behind the cog at the end of its row: the
     * settings a window has that nothing else on the row offers.
     * Locking and closing are not among them — the padlock and the cross
     * stand right beside the cog, and a menu row that only repeats the
     * button next to it is a second way to reach the same thing rather
     * than a setting. Unsticking is offered while the window is stuck to
     * a neighbour, and the size entry puts the window back to the chat's
     * default shape. A locked window never reaches this menu: it offers
     * no cog, so the entries that would be refused are never shown.
     * Below them, the chat's colours: the history panel, the line under
     * the pointer, a line that mentions this player, that line under the
     * pointer and the light a reply's quote leaves, each row wearing the
     * colour it stands for and opening the palette to change it — a
     * client preference, so every window shows the choice at once.
     */
    void openWindowPopup(ChatWindow window, ChatPopupMenu.Anchor anchor) {
        if (window == null) {
            return;
        }
        this.settingsWindowId = window.getId();
        this.settingsAnchor = anchor;
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>(5);
        if (window.isLinked()) {
            entries.add(new ChatPopupMenu.Entry(ENTRY_WINDOW_UNSTICK,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.window.unstick")));
        }
        entries.add(new ChatPopupMenu.Entry(ENTRY_WINDOW_RESET,
                StatCollector.translateToLocal(
                        "gui.losttales.chat.window.reset_size")));
        entries.add(colorRow(ENTRY_WINDOW_COLOR_BACKGROUND,
                "gui.losttales.chat.window.color.background"));
        entries.add(colorRow(ENTRY_WINDOW_COLOR_SELECTED,
                "gui.losttales.chat.window.color.selected"));
        entries.add(colorRow(ENTRY_WINDOW_COLOR_MENTION,
                "gui.losttales.chat.window.color.mention"));
        entries.add(colorRow(ENTRY_WINDOW_COLOR_SELECTED_MENTION,
                "gui.losttales.chat.window.color.selected_mention"));
        entries.add(colorRow(ENTRY_WINDOW_COLOR_REPLY,
                "gui.losttales.chat.window.color.reply"));
        this.popup.open(POPUP_WINDOW, null, entries, this.font, anchor,
                this.screenWidth, this.screenHeight);
    }

    /** One of the window menu's colour rows, chipped in the colour it comes to now. */
    private static ChatPopupMenu.Entry colorRow(String id, String labelKey) {
        return new ChatPopupMenu.Entry(id,
                StatCollector.translateToLocal(labelKey), false,
                ENTRY_WINDOW_COLOR_SELECTED_MENTION.equals(id)
                        ? LostTalesChatVisualStyle.selectedMentionLineRgb()
                        : LostTalesColors.rgb(LostTalesColors.paletteColor(
                                currentColorName(id),
                                LostTalesColors.PLUM_BLACK)),
                null).asChip();
    }

    /**
     * What a colour row's option holds: a palette entry's name, or — for
     * the selected mention — automatic.
     */
    private static String currentColorName(String role) {
        if (ENTRY_WINDOW_COLOR_SELECTED.equals(role)) {
            return LostTalesConfig.chatSelectedLineColor;
        }
        if (ENTRY_WINDOW_COLOR_MENTION.equals(role)) {
            return LostTalesConfig.chatMentionLineColor;
        }
        if (ENTRY_WINDOW_COLOR_SELECTED_MENTION.equals(role)) {
            return LostTalesConfig.chatSelectedMentionColor;
        }
        if (ENTRY_WINDOW_COLOR_REPLY.equals(role)) {
            return LostTalesConfig.chatReplyHighlightColor;
        }
        return LostTalesConfig.chatBackgroundColor;
    }

    /**
     * The palette, in the window menu's place: every entry as a chip
     * beside its name, the one in use named in honey and the one the mod
     * ships marked as the default — for the selected mention, the
     * automatic choice before them, which is its default. Choosing one is
     * the whole change — the option is written to the client file and
     * every window is drawn in it from the next frame. These colours are
     * set here and nowhere else: the Config Screen leaves them to the
     * chat.
     */
    private void openColorPopup(String role) {
        this.colorRole = role;
        String current = currentColorName(role);
        String shipped = shippedColorName(role);
        String[] names = LostTalesColors.paletteNames();
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>(names.length + 1);
        if (ENTRY_WINDOW_COLOR_SELECTED_MENTION.equals(role)) {
            // Automatic first: the mention colour a shade lighter,
            // chipped in the colour that comes to now.
            ChatPopupMenu.Entry automatic = new ChatPopupMenu.Entry(
                    ENTRY_COLOR_PREFIX + LostTalesConfig.CHAT_COLOR_AUTOMATIC,
                    StatCollector.translateToLocalFormatted(
                            "gui.losttales.chat.window.color.default",
                            StatCollector.translateToLocal(
                                    "gui.losttales.chat.window.color.automatic")),
                    false,
                    LostTalesChatVisualStyle.automaticSelectedMentionRgb(),
                    null).asChip();
            if (!LostTalesColors.isPaletteName(current)) {
                automatic.withLabelColor(
                        LostTalesColors.rgb(LostTalesColors.HONEY));
            }
            entries.add(automatic);
        }
        for (int index = 0; index < names.length; index++) {
            String label = paletteLabel(names[index]);
            ChatPopupMenu.Entry entry = new ChatPopupMenu.Entry(
                    ENTRY_COLOR_PREFIX + names[index],
                    names[index].equalsIgnoreCase(shipped)
                            ? StatCollector.translateToLocalFormatted(
                                    "gui.losttales.chat.window.color.default",
                                    label)
                            : label,
                    false, LostTalesColors.rgb(LostTalesColors.paletteColor(
                            names[index], LostTalesColors.PLUM_BLACK)),
                    null).asChip();
            if (names[index].equalsIgnoreCase(current)) {
                entry.withLabelColor(LostTalesColors.rgb(LostTalesColors.HONEY));
            }
            entries.add(entry);
        }
        this.popup.open(POPUP_COLOR, null, entries, this.font,
                this.settingsAnchor, this.screenWidth, this.screenHeight);
    }

    /** The colour the mod ships for a colour row's surface: its default. */
    private static String shippedColorName(String role) {
        if (ENTRY_WINDOW_COLOR_SELECTED.equals(role)) {
            return LostTalesConfig.DEFAULT_CHAT_SELECTED_LINE_COLOR;
        }
        if (ENTRY_WINDOW_COLOR_MENTION.equals(role)) {
            return LostTalesConfig.DEFAULT_CHAT_MENTION_LINE_COLOR;
        }
        if (ENTRY_WINDOW_COLOR_SELECTED_MENTION.equals(role)) {
            return LostTalesConfig.CHAT_COLOR_AUTOMATIC;
        }
        if (ENTRY_WINDOW_COLOR_REPLY.equals(role)) {
            return LostTalesConfig.DEFAULT_CHAT_REPLY_HIGHLIGHT_COLOR;
        }
        return LostTalesConfig.DEFAULT_CHAT_BACKGROUND_COLOR;
    }

    /** A palette entry's name as the language file gives it. */
    private static String paletteLabel(String name) {
        String key = "losttales.palette." + name.toLowerCase(Locale.ROOT);
        String label = StatCollector.translateToLocal(key);
        return key.equals(label) ? name : label;
    }

    /** One row of the palette: the chosen colour becomes the surface's. */
    private void handleColorEntry(ChatPopupMenu.Entry entry) {
        if (entry == null || !entry.id.startsWith(ENTRY_COLOR_PREFIX)) {
            return;
        }
        String name = entry.id.substring(ENTRY_COLOR_PREFIX.length());
        boolean automatic = LostTalesConfig.CHAT_COLOR_AUTOMATIC.equals(name)
                && ENTRY_WINDOW_COLOR_SELECTED_MENTION.equals(this.colorRole);
        if (!automatic && !LostTalesColors.isPaletteName(name)) {
            return;
        }
        if (ENTRY_WINDOW_COLOR_SELECTED.equals(this.colorRole)) {
            LostTalesConfig.chatSelectedLineColor = name;
        } else if (ENTRY_WINDOW_COLOR_MENTION.equals(this.colorRole)) {
            LostTalesConfig.chatMentionLineColor = name;
        } else if (ENTRY_WINDOW_COLOR_SELECTED_MENTION.equals(
                this.colorRole)) {
            LostTalesConfig.chatSelectedMentionColor = name;
        } else if (ENTRY_WINDOW_COLOR_REPLY.equals(this.colorRole)) {
            LostTalesConfig.chatReplyHighlightColor = name;
        } else {
            LostTalesConfig.chatBackgroundColor = name;
        }
        LostTalesConfig.save();
    }

    /**
     * The tab search panel: every tab that is open, then the channels
     * and conversations that are not, narrowed by what is typed into
     * the field above them. Hung from the window's own search control,
     * or — when the keyboard opened it ({@code anchor} null) — from the
     * row of the window being typed in, else from the empty state's own
     * {@code +} when nothing is open ({@code emptyPlus}, or null).
     */
    void openSearchPanel(ChatWindow window, ChatPopupMenu.Anchor anchor,
                         ChatPopupMenu.Anchor emptyPlus) {
        ChatPopupMenu.Anchor at = anchor;
        if (at == null) {
            ChatWindowFrame frame = window == null ? null
                    : ChatWindowFrame.find(window.getId());
            if (frame != null && frame.drawn) {
                at = rowAnchor(frame);
            } else if (emptyPlus != null) {
                at = emptyPlus;
            } else {
                return;
            }
        }
        String windowId = window == null ? null : window.getId();
        if (isKindOpen(POPUP_SEARCH) && !sameWindow(windowId)) {
            // Moving to another window starts the search afresh: the
            // words typed for the other one are not this one's.
            close();
        }
        this.restoreWindowId = windowId;
        this.popup.open(POPUP_SEARCH, null, searchEntries(""), this.font,
                at, this.screenWidth, this.screenHeight,
                StatCollector.translateToLocal(
                        "gui.losttales.chat.search.prompt"),
                SEARCH_SHORTCUT_KEYS);
    }

    /** Narrows the open searchable list to what has been typed into it. */
    private void refreshSearchPanel() {
        List<ChatPopupMenu.Entry> entries;
        if (POPUP_SEARCH.equals(this.popup.kind())) {
            entries = searchEntries(this.popup.filter());
        } else if (POPUP_CHARACTERS.equals(this.popup.kind())) {
            entries = characterSelectionEntries(this.popup.filter());
        } else {
            return;
        }
        this.popup.replaceEntries(entries, this.font, this.screenWidth,
                this.screenHeight);
    }

    /**
     * The panel's rows: the tabs already open across every window, so a
     * search jumps to one, then the closed channels and the players a
     * conversation could be opened with, so it opens one. A filter keeps
     * the rows whose names hold it, and a section with nothing left in
     * it is dropped; a filter that matches nothing says so rather than
     * closing the panel under the hand that is typing.
     */
    List<ChatPopupMenu.Entry> searchEntries(String filter) {
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>();
        List<ChatPopupMenu.Entry> open = new ArrayList<ChatPopupMenu.Entry>();
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
                open.add(new ChatPopupMenu.Entry(
                        ENTRY_OPEN_PREFIX + tab.id(), name,
                        ChatWindowLayout.isMuted(tab),
                        ClientChatChannelState.displayColor(tab), tab));
            }
        }
        addSection(entries, "gui.losttales.chat.search.open", open);
        List<ChatPopupMenu.Entry> closed =
                new ArrayList<ChatPopupMenu.Entry>();
        for (ChatChannel channel : restorableChannels()) {
            String name = ClientChatChannelState.displayName(channel);
            if (matchesFilter(name, filter)) {
                closed.add(new ChatPopupMenu.Entry(channel.getId(), name,
                        ChatWindowLayout.isMuted(channel),
                        ClientChatChannelState.displayColor(channel),
                        ChatTab.of(channel)));
            }
        }
        addSection(entries, "gui.losttales.chat.open.channels", closed);
        List<ChatPopupMenu.Entry> players =
                new ArrayList<ChatPopupMenu.Entry>();
        for (String name : whisperCandidates(this.mc)) {
            ChatTab conversation = ChatTab.whisper(name, "");
            if (conversation != null && !ChatWindowLayout.isOpen(conversation)
                    && matchesFilter(name, filter)) {
                players.add(new ChatPopupMenu.Entry(conversation.id(),
                        name, ChatWindowLayout.isMuted(conversation), -1,
                        conversation));
            }
        }
        addSection(entries, "gui.losttales.chat.open.players", players);
        if (entries.isEmpty()) {
            entries.add(ChatPopupMenu.Entry.passive(
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.search.none")));
        }
        return entries;
    }

    /** Adds a headed section, or nothing at all when it has no rows. */
    private static void addSection(List<ChatPopupMenu.Entry> entries,
                                   String headerKey,
                                   List<ChatPopupMenu.Entry> rows) {
        if (rows.isEmpty()) {
            return;
        }
        entries.add(ChatPopupMenu.Entry.header(
                StatCollector.translateToLocal(headerKey)));
        entries.addAll(rows);
    }

    /** Whether a name holds what has been typed, however it is cased. */
    static boolean matchesFilter(String name, String filter) {
        return filter.length() == 0 || name.toLowerCase(Locale.ROOT)
                .contains(filter.toLowerCase(Locale.ROOT));
    }

    /**
     * The {@code +} menu opened from the keyboard: from the row of the
     * window being typed in, or from the empty state's own {@code +}
     * ({@code emptyPlus}, null while something is open). Nothing happens
     * when there is nothing left to open.
     */
    void openChannelMenu(ChatPopupMenu.Anchor emptyPlus) {
        if (restoreEntries().isEmpty()) {
            return;
        }
        if (emptyPlus != null) {
            openRestorePopup(null, emptyPlus);
            return;
        }
        ChatWindow window = ChatWindowLayout.windowOf(
                ClientChatChannelState.getSelected());
        ChatWindowFrame frame = window == null ? null
                : ChatWindowFrame.find(window.getId());
        if (window == null || frame == null || !frame.drawn) {
            return;
        }
        openRestorePopup(window.getId(), rowAnchor(frame));
    }

    /**
     * The {@code +} menu hung from a window's row, or from the empty
     * state's own {@code +} when {@code windowId} is null.
     */
    void openRestorePopup(String windowId, ChatPopupMenu.Anchor anchor) {
        if (isKindOpen(POPUP_RESTORE) && !sameWindow(windowId)) {
            close();
        }
        this.restoreWindowId = windowId;
        this.restoreRefreshedNanos = System.nanoTime();
        this.popup.open(POPUP_RESTORE, null, restoreEntries(), this.font,
                anchor, this.screenWidth, this.screenHeight);
    }

    /**
     * Where a menu opened from a control on a window's tab row hangs: the
     * row's band at {@code x}, where the pointer is on the control, and
     * toward the window's middle, which is under the row.
     */
    ChatPopupMenu.Anchor stripAnchor(ChatWindowFrame frame,
                                     ChatChannelTabBar.Row row, int x) {
        return ChatPopupMenu.Anchor.inward(x - 4,
                ChatChannelTabBar.rowTop(row.rowBottom), x + 4, row.rowBottom,
                frame, this.screenWidth, this.screenHeight);
    }

    /**
     * Where a menu the keyboard opened for a window hangs: the left end
     * of the window's tab row, where the row's first controls stand.
     */
    private ChatPopupMenu.Anchor rowAnchor(ChatWindowFrame frame) {
        int left = (int)Math.floor(frame.drawnLeft()) + 2;
        int rowBottom = (int)Math.floor(frame.tabRowBottom());
        return ChatPopupMenu.Anchor.inward(left,
                ChatChannelTabBar.rowTop(rowBottom), left + 8, rowBottom,
                frame, this.screenWidth, this.screenHeight);
    }

    /**
     * Where a menu opened over the lines hangs: the pointer, toward the
     * middle of the window it is in.
     */
    private ChatPopupMenu.Anchor pointerAnchor(int mouseX, int mouseY) {
        return ChatPopupMenu.Anchor.inward(mouseX, mouseY, mouseX, mouseY,
                ChatWindowFrame.drawnAt(mouseX, mouseY), this.screenWidth,
                this.screenHeight);
    }

    /**
     * Keeps the open {@code +} menu current: its rows follow players
     * joining and leaving and counts changing, rebuilt on an interval
     * rather than per frame. A menu whose rows have all gone closes.
     */
    void refreshRestorePopup() {
        if (!this.popup.isOpen()
                || !POPUP_RESTORE.equals(this.popup.kind())) {
            return;
        }
        long now = System.nanoTime();
        if (now - this.restoreRefreshedNanos < RESTORE_REFRESH_NANOS) {
            return;
        }
        this.restoreRefreshedNanos = now;
        this.popup.replaceEntries(restoreEntries(), this.font,
                this.screenWidth, this.screenHeight);
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
    List<ChatPopupMenu.Entry> restoreEntries() {
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>();
        List<ChatChannel> closed = restorableChannels();
        if (!closed.isEmpty()) {
            entries.add(ChatPopupMenu.Entry.header(
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.open.channels")));
            for (ChatChannel channel : closed) {
                entries.add(new ChatPopupMenu.Entry(channel.getId(),
                        ClientChatChannelState.displayName(channel),
                        ChatWindowLayout.isMuted(channel),
                        ClientChatChannelState.displayColor(channel),
                        ChatTab.of(channel)));
            }
        }
        List<String> players = whisperCandidates(this.mc);
        if (!players.isEmpty()) {
            entries.add(ChatPopupMenu.Entry.header(
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.open.players")));
            for (String name : players) {
                ChatTab conversation = ChatTab.whisper(name, "");
                entries.add(new ChatPopupMenu.Entry(conversation.id(),
                        name, ChatWindowLayout.isMuted(conversation), -1,
                        conversation));
            }
        }
        return entries;
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
     * The menu over a message: reply to it, or copy it. What it acts on
     * is resolved now, while the pointer is still on the line, since by
     * the time an entry is chosen the pointer has moved to the menu.
     * Nothing opens over a line that carries no message.
     */
    boolean openMessagePopup(int mouseX, int mouseY) {
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
        this.menuToolbarFrame = null;
        openMessagePopup(text, band.lines.get(band.viewIndex).getChatLineID(),
                band.lines, band.viewIndex, pointerAnchor(mouseX, mouseY));
        return true;
    }

    /**
     * The same menu opened from a message's toolbar, over the message the
     * toolbar was drawn for — its first drawn row {@code row} of
     * {@code frame}'s lines — rather than whatever lies under the pointer,
     * which may be the row above; it hangs from the control's box toward
     * the middle of the window.
     */
    boolean openToolbarMessagePopup(ChatWindowFrame frame, int chatLineId,
                                    int row, ChatPopupMenu.Anchor anchor) {
        if (frame == null || row < 0 || row >= frame.lines.size()) {
            return false;
        }
        String text = LostTalesChatClipboard.messageTextOf(frame.lines, row);
        if (text.length() == 0) {
            return false;
        }
        openMessagePopup(text, chatLineId, frame.lines, row, anchor);
        this.menuToolbarFrame = frame;
        return true;
    }

    /**
     * The window whose message toolbar opened the menu that is out, or
     * null: the toolbar's menu button stays lit while its menu is out.
     */
    ChatWindowFrame toolbarMenuFrame() {
        return messageMenuChatLineId() != 0 ? this.menuToolbarFrame : null;
    }

    /**
     * Opens the message menu for the message drawn on {@code chatLineId},
     * whose words are {@code text} and one of whose drawn rows is
     * {@code viewIndex} of {@code lines}, hanging from {@code anchor}.
     */
    private void openMessagePopup(String text, int chatLineId,
                                  List<ChatLine> lines, int viewIndex,
                                  ChatPopupMenu.Anchor anchor) {
        this.menuMessageText = text;
        this.menuChatLineId = chatLineId;
        this.menuMessageId = ClientChatMessageIds.messageIdOf(chatLineId);
        // Who the message is resolved from the packet it was built of,
        // not from the drawn rows: a grouped continuation has no header
        // row to read a name off, and its sender still owns it. Only a
        // line with no packet behind it — an adopted stray, an NPC's
        // speech — is read from what was drawn.
        ClientChatMessages.Remembered remembered =
                ClientChatMessages.get(this.menuMessageId);
        if (remembered != null) {
            this.menuMessageAccount = remembered.packet.getAccountName();
            this.menuMessageIdentity = remembered.packet.getIdentityName();
            this.menuMessageFromDiscord =
                    LostTalesChatMessagePacket.isDiscordSender(
                            remembered.packet.getSenderId());
            this.menuMessageSenderId = remembered.packet.getSenderId();
        } else {
            this.menuMessageAccount = messageAccount(lines, viewIndex,
                    chatLineId);
            this.menuMessageIdentity = messageIdentity(lines, viewIndex,
                    chatLineId);
            this.menuMessageFromDiscord = isFromDiscord(lines, chatLineId);
            this.menuMessageSenderId = null;
        }
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>();
        // Every line offers the same actions in the same places, as the
        // hover toolbar does; one that cannot be taken on this line
        // stands muted and says why. A line the server named can be
        // reacted to and linked to by its id; any line in a tab that
        // takes messages can be answered, by its id or by its words.
        entries.add(action(ENTRY_REACT, "gui.losttales.chat.message.react",
                LostTalesChatPresentation.whyNotReactable(chatLineId)));
        entries.add(action(ENTRY_REPLY, "gui.losttales.chat.message.reply",
                LostTalesChatPresentation.whyNotRepliable(chatLineId)));
        entries.add(new ChatPopupMenu.Entry(ENTRY_COPY,
                StatCollector.translateToLocal(
                        "gui.losttales.chat.message.copy")));
        entries.add(action(ENTRY_COPY_LINK,
                "gui.losttales.chat.message.copy_link",
                whyNotLinkable(chatLineId)));
        // Your own words are yours to correct or take back. The server
        // decides that too — this only offers what it would allow. An
        // operator may take anyone's words back, but never rewrite them:
        // a removal is visibly a removal, an edit would put words in
        // another's mouth. Their row wears the Operator crimson.
        if (isOwnMessage()) {
            if (ClientChatMessages.get(this.menuMessageId) != null) {
                // Editable only while this client still remembers what
                // was typed: the field is filled with the original text,
                // markup and all, not with the line as it reads.
                entries.add(new ChatPopupMenu.Entry(ENTRY_EDIT,
                        StatCollector.translateToLocal(
                                "gui.losttales.chat.message.edit")));
            }
            entries.add(new ChatPopupMenu.Entry(ENTRY_DELETE,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.message.delete")));
        } else if (canModerateMessage()) {
            entries.add(new ChatPopupMenu.Entry(ENTRY_DELETE,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.message.delete"))
                    .withLabelColor(OPERATOR_ACTION_COLOR));
        }
        this.popup.open(POPUP_MESSAGE, ClientChatChannelViews.tabOf(chatLineId),
                entries, this.font, anchor, this.screenWidth,
                this.screenHeight);
    }

    /**
     * The message the menu's Add Reaction was chosen for, handed over
     * once: the screen opens the emoji picker on it. NONE when nothing
     * is waiting.
     */
    long takeReactionTarget() {
        long target = this.pendingReactionTarget;
        this.pendingReactionTarget = ChatMessageIds.NONE;
        return target;
    }

    /** A message action's row: open, or muted with {@code why} it cannot be taken. */
    private static ChatPopupMenu.Entry action(String id, String labelKey,
                                              String why) {
        ChatPopupMenu.Entry entry = new ChatPopupMenu.Entry(id,
                StatCollector.translateToLocal(labelKey));
        return why.length() == 0 ? entry : entry.unavailable(why);
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
     * {@code #Channel/<server id>} — or null for a line the server never
     * named, or a channel a link cannot spell.
     */
    static String messageLinkFor(int chatLineId) {
        long messageId = ClientChatMessageIds.messageIdOf(chatLineId);
        ChatTab tab = ClientChatChannelViews.tabOf(chatLineId);
        return tab == null ? null
                : ChatChannelSuggester.messageLink(tab.getChannel(), messageId);
    }

    /**
     * The menu over a person — the sender's identity span or a mention:
     * message them, ignore them. The message's own menu stays with the
     * message body; this one is account and character business, so it
     * opens only over somebody who can be addressed — not an NPC, not a
     * role mention, and not yourself. A Discord member can be ignored
     * but not whispered to, so their menu offers the one entry; the
     * bridge's own nameless id is nobody and opens nothing. The person
     * is the one the screen's hit test found under the pointer, so the
     * menu opens over exactly the pixels the card answers for.
     */
    boolean openPlayerPopup(LostTalesChatHoverCard.Target person,
                            int mouseX, int mouseY) {
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
        boolean fromDiscord = LostTalesChatMessagePacket.isDiscordSender(
                person.playerId);
        this.menuMessageAccount = person.accountName;
        this.menuMessageIdentity = person.identityName;
        this.menuMessageSenderId = person.playerId;
        this.menuMessageFromDiscord = fromDiscord;
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>();
        // The header names who the menu is about, the way the card does:
        // the identity, with the account behind it when they differ.
        String name = LostTalesChatVisualStyle.removeColorCodes(
                person.identityName).trim();
        entries.add(ChatPopupMenu.Entry.header(
                name.length() > 0 && !name.equalsIgnoreCase(
                        person.accountName)
                        ? name + " (" + person.accountName + ")"
                        : person.accountName));
        if (!fromDiscord) {
            entries.add(new ChatPopupMenu.Entry(ENTRY_MESSAGE,
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
            entries.add(new ChatPopupMenu.Entry(ENTRY_IGNORE_IDENTITY,
                    StatCollector.translateToLocalFormatted(
                            ClientChatIgnores.isIgnoredIdentity(
                                    person.playerId, name)
                                    ? "gui.losttales.chat.message.unignore"
                                    : "gui.losttales.chat.message.ignore",
                            name)));
        }
        boolean accountIgnored = ClientChatIgnores.isIgnored(person.playerId);
        entries.add(new ChatPopupMenu.Entry(ENTRY_IGNORE,
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
            entries.add(new ChatPopupMenu.Entry(
                    muted ? ENTRY_UNMUTE_ACCOUNT : ENTRY_MUTE_ACCOUNT,
                    StatCollector.translateToLocalFormatted(muted
                            ? "gui.losttales.chat.message.unmute"
                            : "gui.losttales.chat.message.mute",
                            person.accountName))
                    .withLabelColor(OPERATOR_ACTION_COLOR));
        }
        this.popup.open(POPUP_PLAYER, null, entries, this.font,
                pointerAnchor(mouseX, mouseY), this.screenWidth,
                this.screenHeight);
        return true;
    }

    /**
     * The character selection menu, hung from its button, with a search
     * field over its rows that narrows them as it is typed into. On an
     * account channel, which always speaks as the account, the menu is
     * the status rows alone: nothing there chooses an identity, so it
     * carries no roster and no search field either.
     */
    void openCharacterSelectionMenu(ChatPopupMenu.Anchor anchor,
                                    boolean statusOnly) {
        this.charactersStatusOnly = statusOnly;
        this.popup.open(POPUP_CHARACTERS, null, characterSelectionEntries(""),
                this.font, anchor, this.screenWidth, this.screenHeight,
                statusOnly ? null
                        : StatCollector.translateToLocal(
                                "gui.losttales.chat.character_selection.search"),
                null);
    }

    /**
     * The selected chat identity, then the owned characters and the
     * owned lore characters, each section under its header. A filter keeps the rows
     * whose names hold it and drops a section with nothing left; one
     * that matches nothing says so under the current identity rather
     * than closing the menu under the hand that is typing.
     */
    private List<ChatPopupMenu.Entry> characterSelectionEntries(
            String filter) {
        UUID self = this.mc.thePlayer == null ? null
                : this.mc.thePlayer.getUniqueID();
        ClientChatIdentities.Identity current =
                ClientChatIdentities.viewing();
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>();
        if (this.charactersStatusOnly) {
            // The account speaking, and what it may say of itself.
            entries.add(ChatPopupMenu.Entry.passive(
                    ClientChatIdentities.accountName())
                    .withHead(self, ""));
            addSection(entries,
                    "gui.losttales.chat.character_selection.status",
                    statusRows());
            return entries;
        }
        entries.add(ChatPopupMenu.Entry.passive(ClientChatIdentities.isNarrating()
                ? ChatNarrator.NAME : current.name)
                .withHead(self, current.account ? "" : current.skinId));
        if (ClientChatChannelState.holds(LostTalesCapability.CHAT_NARRATE)) {
            // The Narrator is a voice over the identity, not one of
            // them: it stands above the roster, marked while chosen.
            entries.add(new ChatPopupMenu.Entry("characters:narrator",
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
            entries.add(ChatPopupMenu.Entry.passive(
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.character_selection.none")));
        }
        addSection(entries, "gui.losttales.chat.character_selection.status",
                statusRows());
        return entries;
    }

    /**
     * The status line's field, in place of the menu that asked for it,
     * for the identity the selected tab speaks as: what it says of itself
     * now, to be typed over. Enter or the first row keeps what the field
     * holds, the second clears the line, and Escape leaves it as it was.
     */
    private boolean openStatusLineField() {
        ChatPopupMenu.Anchor anchor = this.popup.anchor();
        this.statusLineIdentity = ClientChatPresence.speakerOf(
                ClientChatChannelState.getSelected());
        String current = ClientChatPresence.chosenLine(this.statusLineIdentity);
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>(2);
        entries.add(new ChatPopupMenu.Entry(ENTRY_STATUS_LINE_KEEP,
                StatCollector.translateToLocal(
                        "gui.losttales.chat.status_line.keep")));
        if (current.length() > 0) {
            entries.add(new ChatPopupMenu.Entry(ENTRY_STATUS_LINE_CLEAR,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.status_line.clear")));
        }
        this.popup.open(POPUP_STATUS_LINE, null, entries, this.font, anchor,
                this.screenWidth, this.screenHeight,
                StatCollector.translateToLocal(
                        "gui.losttales.chat.status_line.prompt"),
                null, LostTalesUiSheet.SPEECH_BUBBLE);
        this.popup.setFilter(current);
        return this.popup.isOpen();
    }

    /**
     * The statuses to choose from for the identity the selected tab
     * speaks as — the chat identity on a roleplaying tab, the account on
     * any other — each with the sphere it shows, lighting to the ivory
     * one under the pointer, and the one chosen for it marked; and under
     * them the identity's status line, in italics, or the way to set one.
     */
    private static List<ChatPopupMenu.Entry> statusRows() {
        ChatPresenceIdentity speaker = ClientChatPresence.speakerOf(
                ClientChatChannelState.getSelected());
        ChatPresence chosen = ClientChatPresence.chosen(speaker);
        List<ChatPopupMenu.Entry> rows = new ArrayList<ChatPopupMenu.Entry>();
        for (ChatPresence presence : ChatPresence.values()) {
            if (!presence.isChoosable()) {
                continue;
            }
            rows.add(new ChatPopupMenu.Entry("characters:status:" + presence.name(),
                    StatCollector.translateToLocal(presence.labelKey()), false,
                    chosen == presence
                            ? LostTalesColors.rgb(LostTalesColors.HONEY) : -1, null)
                    .withSprite(ChatPresenceMark.markOf(presence),
                            LostTalesUiSheet.PRESENCE_SELECTED, false));
        }
        String line = ClientChatPresence.chosenLine(speaker);
        rows.add(new ChatPopupMenu.Entry(ENTRY_STATUS_LINE,
                line.length() == 0 ? StatCollector.translateToLocal(
                        "gui.losttales.chat.status_line.set") : line,
                line.length() > 0, -1, null)
                .withEmojis()
                .withSprite(LostTalesUiSheet.SPEECH_BUBBLE,
                        LostTalesUiSheet.SPEECH_BUBBLE_HOVER, false));
        return rows;
    }

    /** One section's rows: the identities whose names hold the filter. */
    private static List<ChatPopupMenu.Entry> characterRows(
            List<ClientChatIdentities.Identity> identities,
            String filter, UUID self) {
        List<ChatPopupMenu.Entry> rows = new ArrayList<ChatPopupMenu.Entry>();
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
    private static ChatPopupMenu.Entry characterEntry(
            String id, ClientChatIdentities.Identity identity,
            UUID self) {
        boolean effective = ClientChatIdentities.isSelected(identity);
        return new ChatPopupMenu.Entry(id, identity.name, false,
                effective ? LostTalesColors.rgb(LostTalesColors.HONEY) : -1,
                null).withHead(self, identity.skinId);
    }

    /** A choice applies to every roleplaying conversation. */
    private static void handleCharacterSelectionEntry(
            ChatPopupMenu.Entry entry) {
        if ("characters:narrator".equals(entry.id)) {
            ClientChatIdentities.setNarrating(!ClientChatIdentities.isNarrating());
            return;
        }
        if (entry.id.startsWith("characters:status:")) {
            try {
                ClientChatPresence.choose(ClientChatPresence.speakerOf(
                                ClientChatChannelState.getSelected()),
                        ChatPresence.valueOf(entry.id.substring(
                                "characters:status:".length())));
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

    /* ---- What the rows do ---- */

    /** Answers the message the menu was opened over, in its own tab. */
    private void startReply() {
        ChatTab tab = this.popup.channel();
        if (tab == null) {
            return;
        }
        // A line the server named is answered by its id; a client-local
        // one by its own id here and by its words alone on the wire.
        long id = this.menuMessageId;
        // The chip and the local quote name the identity the line was
        // signed with, exactly as the server's own quote will.
        String name = this.menuMessageIdentity.length() > 0
                ? this.menuMessageIdentity : this.menuMessageAccount;
        if (!ChatMessageIds.isServerId(id)) {
            name = LostTalesChatPresentation.quoteAuthorFor(name);
        }
        // The menu resolved the message when it opened, which is also
        // the quote an NPC's conversation has to build for itself.
        String excerpt = this.menuMessageText;
        // Composing happens where the message lives, and selecting a tab
        // clears any reply, so the target is set after the move.
        this.tabActions.selectChannel(tab);
        this.composer.startReply(tab, id, name, excerpt,
                LostTalesChatPresentation.headOfLine(this.menuChatLineId));
    }

    /**
     * Whether the message the menu was opened over is this player's
     * own, and one the server can still be asked about. A line from the
     * Discord bridge never is, whatever name it carries.
     */
    private boolean isOwnMessage() {
        return isOwnMessage(this.menuMessageId, this.menuMessageFromDiscord,
                this.menuMessageAccount, this.mc.thePlayer == null ? null
                        : this.mc.thePlayer.getCommandSenderName());
    }

    /** As above, over the facts themselves. */
    static boolean isOwnMessage(long messageId, boolean fromDiscord,
                                String account, String self) {
        return ChatMessageIds.isServerId(messageId) && !fromDiscord
                && self != null && account.equalsIgnoreCase(self);
    }

    /**
     * Whether this player, as a moderator, may take the message the menu
     * was opened over back from everyone: any line the server can still
     * be asked about, another player's or a Discord member's alike. The
     * server checks the capability again on the request.
     */
    private boolean canModerateMessage() {
        return ChatMessageIds.isServerId(this.menuMessageId)
                && ClientChatChannelState.canModerate()
                && !isConsoleEntry();
    }

    /**
     * Whether the message is an entry of the Server Console: the server's
     * record of what happened, which nobody takes back.
     */
    private boolean isConsoleEntry() {
        ChatTab tab = ClientChatChannelViews.tabOf(this.menuChatLineId);
        return tab != null && tab.getChannel() == ChatChannel.SERVER_CONSOLE
                && LostTalesChatMessagePacket.isServerSender(
                        this.menuMessageSenderId);
    }

    /**
     * Puts the message back in the bar to be rewritten. What goes into
     * the field is the text that was <em>sent</em> — the markup as it
     * was typed, not the line as it reads — so editing a formatted
     * message does not quietly flatten it.
     */
    private void startEdit() {
        ChatTab tab = this.popup.channel();
        ClientChatMessages.Remembered remembered =
                ClientChatMessages.get(this.menuMessageId);
        if (!isOwnMessage() || tab == null || remembered == null) {
            return;
        }
        long id = this.menuMessageId;
        // Composing happens where the message lives, and selecting a tab
        // puts down whatever was being composed, so the target is set
        // after the move.
        this.tabActions.selectChannel(tab);
        this.composer.startEdit(tab, id);
        this.field.setText(remembered.packet.getMessage());
        ClientChatChannelState.setDraft(remembered.packet.getMessage());
    }

    /**
     * Asks before taking a message back, because it is taken back from
     * everyone who was sent it and there is no putting it there again.
     * The menu the asking came from becomes the question, so it stands
     * exactly where the pointer already is; answering it anywhere else,
     * or with the Escape that closes any menu, is declining. Answers
     * whether the menu is now showing that question.
     */
    private boolean askToDelete() {
        boolean own = isOwnMessage();
        if (!own && !canModerateMessage()) {
            return false;
        }
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>();
        ChatPopupMenu.Entry confirm = new ChatPopupMenu.Entry(
                ENTRY_DELETE_CONFIRM, StatCollector.translateToLocal(
                        "gui.losttales.chat.message.delete.confirm"));
        entries.add(own ? confirm
                : confirm.withLabelColor(OPERATOR_ACTION_COLOR));
        this.popup.replaceEntries(entries, this.font, this.screenWidth,
                this.screenHeight);
        return true;
    }

    /**
     * Asks the server to take the message back. Nothing is removed
     * here: the line goes when the server says it has gone, so every
     * screen showing it — this one included — loses it for the same
     * reason and at the same time.
     */
    private void confirmDelete() {
        if (!ChatMessageIds.isServerId(this.menuMessageId)) {
            return;
        }
        this.composer.forgetEditOf(this.menuMessageId);
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesChatDeletePacket(this.menuMessageId));
    }

    /**
     * Puts the mute command in the bar with the target filled in and
     * the caret after it, so the operator adds a duration and a reason
     * — or none — and sends it with Enter; the server does the muting
     * and answers in the console.
     */
    private void startMute() {
        this.composer.cancelReply();
        String command = "/losttales chat mute " + muteTarget() + " ";
        this.field.setText(command);
        this.field.setCursorPositionEnd();
        ClientChatChannelState.setDraft(command);
    }

    /**
     * How the mute command names whoever the menu is about: a player by
     * account, a Discord member as {@code discord:<name>}, which the
     * server resolves through the bridge's memory of who bore the name.
     */
    private String muteTarget() {
        return muteTarget(this.menuMessageFromDiscord, this.menuMessageAccount);
    }

    static String muteTarget(boolean fromDiscord, String account) {
        return fromDiscord ? "discord:" + account : account;
    }

    /**
     * Starts or stops ignoring the account behind the menu's message.
     * Existing lines stay — ignoring quiets what has not been said yet —
     * and the notice says which way it went.
     */
    private void toggleIgnore() {
        if (this.menuMessageSenderId == null) {
            return;
        }
        if (ClientChatIgnores.isIgnored(this.menuMessageSenderId)) {
            ClientChatIgnores.unignore(this.menuMessageSenderId);
            this.notices.showNotice(StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.unignored", this.menuMessageAccount));
        } else if (ClientChatIgnores.ignore(this.menuMessageSenderId,
                this.menuMessageAccount)) {
            ClientChatIgnores.rememberName(this.menuMessageSenderId,
                    this.menuMessageIdentity);
            this.notices.showNotice(StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.ignored", this.menuMessageAccount));
        } else {
            this.notices.showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.ignore_full"));
        }
    }

    /**
     * Starts or stops ignoring the one identity behind the menu's
     * message, the account's other identities still heard.
     */
    private void toggleIgnoreIdentity() {
        String name = LostTalesChatVisualStyle.removeColorCodes(
                this.menuMessageIdentity).trim();
        if (this.menuMessageSenderId == null || name.length() == 0) {
            return;
        }
        if (ClientChatIgnores.isIgnoredIdentity(this.menuMessageSenderId, name)) {
            ClientChatIgnores.unignoreIdentity(this.menuMessageSenderId, name);
            this.notices.showNotice(StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.unignored", name));
        } else if (ClientChatIgnores.ignoreIdentity(this.menuMessageSenderId,
                name)) {
            this.notices.showNotice(StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.ignored_identity", name));
        } else {
            this.notices.showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.ignore_full"));
        }
    }

    /**
     * Acts on a chosen entry, answering whether the menu should stay
     * open: all but one entry are done with the menu once they have
     * been chosen, and the caller closes it. Deleting is the exception —
     * it asks first, and the question is put in the menu the asking
     * came from. A row that runs a server command leaves it in
     * {@link #pendingCommand} for the screen to send.
     */
    private boolean handlePopupEntry(ChatPopupMenu.Entry entry) {
        if (POPUP_PLAYER.equals(this.popup.kind())) {
            if (ENTRY_MESSAGE.equals(entry.id)) {
                this.tabActions.openWhisperTab(this.menuMessageAccount,
                        this.menuMessageIdentity);
            } else if (ENTRY_IGNORE.equals(entry.id)) {
                toggleIgnore();
            } else if (ENTRY_IGNORE_IDENTITY.equals(entry.id)) {
                toggleIgnoreIdentity();
            } else if (ENTRY_MUTE_ACCOUNT.equals(entry.id)) {
                startMute();
            } else if (ENTRY_UNMUTE_ACCOUNT.equals(entry.id)) {
                this.pendingCommand = "/losttales chat unmute " + muteTarget();
            }
        } else if (POPUP_MESSAGE.equals(this.popup.kind())) {
            if (ENTRY_REACT.equals(entry.id)) {
                this.pendingReactionTarget = this.menuMessageId;
            } else if (ENTRY_REPLY.equals(entry.id)) {
                startReply();
            } else if (ENTRY_EDIT.equals(entry.id)) {
                startEdit();
            } else if (ENTRY_DELETE.equals(entry.id)) {
                return askToDelete();
            } else if (ENTRY_DELETE_CONFIRM.equals(entry.id)) {
                confirmDelete();
            } else if (ENTRY_COPY.equals(entry.id)
                    && LostTalesChatClipboard.copy(this.menuMessageText)) {
                this.notices.showNotice(StatCollector.translateToLocal(
                        "gui.losttales.chat.copied"));
            } else if (ENTRY_COPY_LINK.equals(entry.id)) {
                String link = messageLinkFor(this.menuChatLineId);
                if (link != null && LostTalesChatClipboard.copy(link)) {
                    this.notices.showNotice(StatCollector.translateToLocal(
                            "gui.losttales.chat.copied"));
                }
            }
            return false;
        }
        if (POPUP_CHARACTERS.equals(this.popup.kind())) {
            if (ENTRY_STATUS_LINE.equals(entry.id)) {
                return openStatusLineField();
            }
            handleCharacterSelectionEntry(entry);
            return false;
        }
        if (POPUP_STATUS_LINE.equals(this.popup.kind())) {
            if (ENTRY_STATUS_LINE_KEEP.equals(entry.id)) {
                ClientChatPresence.setLine(this.statusLineIdentity,
                        this.popup.filter());
            } else if (ENTRY_STATUS_LINE_CLEAR.equals(entry.id)) {
                ClientChatPresence.setLine(this.statusLineIdentity, "");
            }
            return false;
        }
        if (POPUP_SETTINGS.equals(this.popup.kind())) {
            ChatTab channel = this.popup.channel();
            if (channel == null) {
                return false;
            }
            if (ENTRY_MUTE.equals(entry.id)) {
                ChatWindowLayout.setMuted(channel,
                        !ChatWindowLayout.isMuted(channel));
            } else if (ENTRY_PINGS.equals(entry.id)) {
                ChatWindowLayout.setPingsMuted(channel,
                        !ChatWindowLayout.isPingsMuted(channel));
            } else if (ENTRY_HIDE.equals(entry.id)) {
                ChatWindowLayout.setHidden(channel,
                        !ChatWindowLayout.isHidden(channel));
            } else if (ENTRY_DETACH.equals(entry.id)) {
                this.tabActions.detachChannel(channel, this.screenWidth,
                        this.screenHeight);
            } else if (ENTRY_MARK_READ.equals(entry.id)) {
                ClientChatChannelViews.markViewed(channel);
                ClientChatChannelViews.dismissDivider(channel);
            } else if (ENTRY_JUMP_UNREAD.equals(entry.id)) {
                Integer first = ClientChatChannelViews.unreadDividerLine(
                        channel);
                this.tabActions.jumpToTab(channel);
                if (first != null) {
                    // The rows exist once the tab has been drawn; the
                    // next draw lands on the run's first line.
                    LostTalesChatPresentation.requestJump(first.intValue());
                }
            }
        } else if (POPUP_SEARCH.equals(this.popup.kind())) {
            if (entry.id.startsWith(ENTRY_OPEN_PREFIX)) {
                this.tabActions.jumpToTab(ChatTab.fromId(entry.id.substring(
                        ENTRY_OPEN_PREFIX.length())));
            } else {
                openFromRestoreMenu(entry);
            }
        } else if (POPUP_WINDOW.equals(this.popup.kind())) {
            return handleWindowEntry(entry);
        } else if (POPUP_COLOR.equals(this.popup.kind())) {
            handleColorEntry(entry);
        } else if (POPUP_RESTORE.equals(this.popup.kind())) {
            openFromRestoreMenu(entry);
        }
        return false;
    }

    /**
     * One row of the window's own menu, answering whether the menu
     * stays open: a colour row swaps the menu for the palette in its
     * place, the rest are done with it.
     */
    private boolean handleWindowEntry(ChatPopupMenu.Entry entry) {
        ChatWindow window = ChatWindowLayout.window(this.settingsWindowId);
        if (window == null) {
            return false;
        }
        if (ENTRY_WINDOW_COLOR_BACKGROUND.equals(entry.id)
                || ENTRY_WINDOW_COLOR_SELECTED.equals(entry.id)
                || ENTRY_WINDOW_COLOR_MENTION.equals(entry.id)
                || ENTRY_WINDOW_COLOR_SELECTED_MENTION.equals(entry.id)
                || ENTRY_WINDOW_COLOR_REPLY.equals(entry.id)) {
            openColorPopup(entry.id);
            return true;
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
     * One row of the {@code +} menu: the channel or conversation joins
     * the window the menu was opened from, or — when the menu was opened
     * from the empty state, or no window is left — a window of its own.
     */
    private void openFromRestoreMenu(ChatPopupMenu.Entry entry) {
        ChatWindow target = ChatWindowLayout.window(this.restoreWindowId);
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
