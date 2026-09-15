package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelSuggester;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatDeletePacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
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
        /** A command a row asked the screen to send, or null. */
        final String command;

        Click(boolean consumed, String closedKind, String command) {
            this.consumed = consumed;
            this.closedKind = closedKind;
            this.command = command;
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
    /** The sender's account, when the line still has its packet; else null. */
    private UUID menuMessageSenderId;
    private String menuMessageText = "";
    /** The line the message menu was opened over. */
    private int menuChatLineId;
    /** Window a restore popup was opened from. */
    private String restoreWindowId;
    /** Window the open window-settings menu belongs to, or null. */
    private String settingsWindowId;
    /** Where that menu was opened, so the palette opens in its place. */
    private int settingsAnchorX;
    private int settingsAnchorBottom;
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

    void scrollBy(double rows) {
        this.popup.scrollBy(rows);
    }

    ChatPopupMenu.Entry lockControlAt(double mouseX, double mouseY) {
        return this.popup.lockControlAt(mouseX, mouseY);
    }

    void registerRegion(ChatPointerRegions regions) {
        this.popup.registerRegion(regions);
    }

    /** Draws the open menu; the pointer is {@link ChatHover#AWAY} unless the menu has it. */
    void draw(ChatPointerRegions regions, double mouseX, double mouseY) {
        this.popup.draw(this.font, regions, mouseX, mouseY);
    }

    /**
     * A searchable list takes plain typing while it is open; true when
     * the key went into it.
     */
    boolean handleKeyTyped(char typedChar, int keyCode) {
        if (!this.popup.isSearchable()
                || !this.popup.handleKeyTyped(typedChar, keyCode)) {
            return false;
        }
        refreshSearchPanel();
        return true;
    }

    /**
     * A press while a menu is open. The lock in the character selection
     * menu is a switch, not a pick: the menu stays open, its rows
     * refreshed, so the padlock answers in place. An entry acts and the
     * menu closes — unless the entry asked a question of its own and
     * the menu is showing it. A press outside closes the menu and goes
     * on to whatever is under it, told which menu it closed.
     */
    Click click(double mouseX, double mouseY, int button) {
        if (!this.popup.isOpen()) {
            return new Click(false, "", null);
        }
        String closedKind = this.popup.kind();
        if (button == 0 && this.popup.lockControlAt(mouseX, mouseY) != null) {
            ClientChatAppearances.toggleLocked(
                    ClientChatChannelState.getSelected());
            this.popup.replaceEntries(characterSelectionEntries(), this.font,
                    this.screenWidth, this.screenHeight);
            return new Click(true, closedKind, null);
        }
        ChatPopupMenu.Entry entry = this.popup.entryAt(mouseX, mouseY);
        boolean inside = this.popup.contains(mouseX, mouseY);
        this.pendingCommand = null;
        if (entry != null && button == 0 && handlePopupEntry(entry)) {
            // The entry asked a question of its own and the menu is
            // showing it: closing here would close the question along
            // with the menu that asked it.
            return new Click(true, closedKind, this.pendingCommand);
        }
        this.popup.close();
        return new Click(inside, closedKind, this.pendingCommand);
    }

    /* ---- The strip's menus ---- */

    /**
     * The tab's menu — behind the cog, and under a right-click on the
     * tab — four entries, each its own independent preference or
     * action: Mute Channel (out of the feed), Mute Mentions (cue
     * silent), Hide Channel (stays closed when messaged), and Move to
     * its own Window. Closing is the cross on the tab and nothing else:
     * a row that only repeats the button beside it is a second way to
     * lose a tab by accident.
     */
    void openSettingsPopup(ChatTab channel, int anchorX, int anchorBottom) {
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>(4);
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
                anchorX - 4, anchorBottom, this.screenWidth,
                this.screenHeight);
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
    void openWindowPopup(ChatWindow window, int anchorX, int anchorBottom) {
        if (window == null) {
            return;
        }
        this.settingsWindowId = window.getId();
        this.settingsAnchorX = anchorX;
        this.settingsAnchorBottom = anchorBottom;
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
        this.popup.open(POPUP_WINDOW, null, entries, this.font,
                anchorX - 4, anchorBottom, this.screenWidth,
                this.screenHeight);
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
     * beside its name, the one in use named in honey — for the selected
     * mention, the automatic choice before them. Choosing one is the
     * whole change — the option is written to the client file and every
     * window is drawn in it from the next frame.
     */
    private void openColorPopup(String role) {
        this.colorRole = role;
        String current = currentColorName(role);
        String[] names = LostTalesColors.paletteNames();
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>(names.length + 1);
        if (ENTRY_WINDOW_COLOR_SELECTED_MENTION.equals(role)) {
            // Automatic first: the mention colour a shade lighter,
            // chipped in the colour that comes to now.
            ChatPopupMenu.Entry automatic = new ChatPopupMenu.Entry(
                    ENTRY_COLOR_PREFIX + LostTalesConfig.CHAT_COLOR_AUTOMATIC,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.window.color.automatic"),
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
            ChatPopupMenu.Entry entry = new ChatPopupMenu.Entry(
                    ENTRY_COLOR_PREFIX + names[index], paletteLabel(names[index]),
                    false, LostTalesColors.rgb(LostTalesColors.paletteColor(
                            names[index], LostTalesColors.PLUM_BLACK)),
                    null).asChip();
            if (names[index].equalsIgnoreCase(current)) {
                entry.withLabelColor(LostTalesColors.rgb(LostTalesColors.HONEY));
            }
            entries.add(entry);
        }
        this.popup.open(POPUP_COLOR, null, entries, this.font,
                this.settingsAnchorX - 4, this.settingsAnchorBottom,
                this.screenWidth, this.screenHeight);
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
     * the field above them. Anchored over the window's own search
     * control, or — when the keyboard opened it — over the window being
     * typed in, else over the empty state's own {@code +} when nothing
     * is open ({@code emptyPlusX} and {@code emptyPlusBottom} name it,
     * or are negative).
     */
    void openSearchPanel(ChatWindow window, int anchorX, int anchorBottom,
                         int emptyPlusX, int emptyPlusBottom) {
        int x = anchorX;
        int bottom = anchorBottom;
        if (x < 0 || bottom < 0) {
            ChatWindowFrame frame = window == null ? null
                    : ChatWindowFrame.find(window.getId());
            if (frame != null && frame.drawn) {
                x = (int)Math.floor(frame.drawnLeft()) + 2;
                bottom = ChatChannelTabBar.rowTop(
                        (int)Math.floor(frame.tabRowBottom())) - 2;
            } else if (emptyPlusX >= 0 && emptyPlusBottom >= 0) {
                x = emptyPlusX;
                bottom = emptyPlusBottom;
            } else {
                return;
            }
        }
        this.restoreWindowId = window == null ? null : window.getId();
        this.popup.open(POPUP_SEARCH, null, searchEntries(""), this.font,
                x - 4, bottom, this.screenWidth, this.screenHeight,
                StatCollector.translateToLocal(
                        "gui.losttales.chat.search.prompt"),
                SEARCH_SHORTCUT_KEYS);
    }

    /** Narrows the open panel to what has been typed into it. */
    private void refreshSearchPanel() {
        if (!POPUP_SEARCH.equals(this.popup.kind())) {
            return;
        }
        this.popup.replaceEntries(searchEntries(this.popup.filter()),
                this.font, this.screenWidth, this.screenHeight);
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
                        ENTRY_OPEN_PREFIX + tab.id(),
                        withCounter(name,
                                ClientChatChannelViews.unreadCount(tab)),
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
                closed.add(new ChatPopupMenu.Entry(channel.getId(),
                        withCounter(name,
                                ClientChatChannelViews.unreadCount(channel)),
                        ChatWindowLayout.isMuted(channel),
                        ClientChatChannelState.displayColor(channel),
                        ChatTab.of(channel)));
            }
        }
        addSection(entries, "gui.losttales.chat.open.channels", closed);
        List<ChatPopupMenu.Entry> players =
                new ArrayList<ChatPopupMenu.Entry>();
        for (String name : whisperCandidates(this.mc)) {
            ChatTab conversation = ChatTab.whisper(name, "",
                    ClientChatAppearances.viewIdentityKey());
            if (conversation != null && !ChatWindowLayout.isOpen(conversation)
                    && matchesFilter(name, filter)) {
                players.add(new ChatPopupMenu.Entry(conversation.id(),
                        withCounter(name,
                                ClientChatChannelViews.unreadCount(
                                        conversation)),
                        ChatWindowLayout.isMuted(conversation), -1,
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
     * The {@code +} menu opened from the keyboard: over the row of the
     * window being typed in, or over the empty state's own {@code +}
     * ({@code emptyPlusX}, {@code emptyPlusBottom}, negative while
     * something is open). Nothing happens when there is nothing left to
     * open.
     */
    void openChannelMenu(int emptyPlusX, int emptyPlusBottom) {
        if (restoreEntries().isEmpty()) {
            return;
        }
        if (emptyPlusX >= 0 && emptyPlusBottom >= 0) {
            openRestorePopup(null, emptyPlusX, emptyPlusBottom);
            return;
        }
        ChatWindow window = ChatWindowLayout.windowOf(
                ClientChatChannelState.getSelected());
        ChatWindowFrame frame = window == null ? null
                : ChatWindowFrame.find(window.getId());
        if (window == null || frame == null || !frame.drawn) {
            return;
        }
        openRestorePopup(window.getId(),
                (int)Math.floor(frame.drawnLeft()) + 2,
                ChatChannelTabBar.rowTop(
                        (int)Math.floor(frame.tabRowBottom())) - 2);
    }

    /**
     * The {@code +} menu over a window's row, or over the empty state's
     * own {@code +} when {@code windowId} is null.
     */
    void openRestorePopup(String windowId, int anchorX, int anchorBottom) {
        this.restoreWindowId = windowId;
        this.restoreRefreshedNanos = System.nanoTime();
        this.popup.open(POPUP_RESTORE, null, restoreEntries(), this.font,
                anchorX - 4, anchorBottom, this.screenWidth,
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
     * closed channels come first, each with the unread indicator its tab
     * would carry ({@code Trade [3]}) — a closed channel keeps
     * receiving, and the count is the one the tab shows once restored; a
     * muted one reads italic, like its tab would. The online players
     * follow, each opening (or selecting) the whisper conversation with
     * them, wearing the same head its tab wears and the conversation's
     * unread count. A section absent of rows is left out altogether.
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
                        withCounter(
                                ClientChatChannelState.displayName(channel),
                                ClientChatChannelViews.unreadCount(channel)),
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
                ChatTab conversation = ChatTab.whisper(name, "",
                    ClientChatAppearances.viewIdentityKey());
                entries.add(new ChatPopupMenu.Entry(conversation.id(),
                        withCounter(name,
                                ClientChatChannelViews.unreadCount(
                                        conversation)),
                        ChatWindowLayout.isMuted(conversation),
                        -1, conversation));
            }
        }
        return entries;
    }

    /** {@code Name [3]} while anything is unread; the bare name otherwise. */
    static String withCounter(String name, int unread) {
        String counter = ClientChatChannelViews.counterText(unread);
        return counter.length() == 0 ? name : name + " " + counter;
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
        int chatLineId = band == null || band.lines == null
                || band.viewIndex >= band.lines.size()
                || band.lines.get(band.viewIndex) == null
                ? 0 : band.lines.get(band.viewIndex).getChatLineID();
        this.menuMessageText = text;
        this.menuChatLineId = chatLineId;
        this.menuMessageId = band == null ? ChatMessageIds.NONE
                : ClientChatMessageIds.messageIdOf(chatLineId);
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
            this.menuMessageAccount = band == null ? ""
                    : messageAccount(band.lines, band.viewIndex, chatLineId);
            this.menuMessageIdentity = band == null ? ""
                    : messageIdentity(band.lines, band.viewIndex, chatLineId);
            this.menuMessageFromDiscord = band != null
                    && isFromDiscord(band.lines, chatLineId);
            this.menuMessageSenderId = null;
        }
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>();
        // Any line in a tab that takes messages can be answered: one
        // the server named by its id, any other by its words.
        // A message the server named can be reacted to, as on Discord.
        if (LostTalesChatPresentation.isReactable(chatLineId)) {
            entries.add(new ChatPopupMenu.Entry(ENTRY_REACT,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.message.react")));
        }
        if (LostTalesChatPresentation.isRepliable(chatLineId)) {
            entries.add(new ChatPopupMenu.Entry(ENTRY_REPLY,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.message.reply")));
        }
        entries.add(new ChatPopupMenu.Entry(ENTRY_COPY,
                StatCollector.translateToLocal(
                        "gui.losttales.chat.message.copy")));
        // A message the server named can be linked to from any other
        // message, by its id, the way a messenger's message link is
        // pasted; a whisper's cannot, since nobody else may follow it.
        if (messageLinkFor(chatLineId) != null) {
            entries.add(new ChatPopupMenu.Entry(ENTRY_COPY_LINK,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.message.copy_link")));
        }
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
                entries, this.font, mouseX, mouseY, this.screenWidth,
                this.screenHeight);
        return true;
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

    /**
     * The link that names the message drawn on {@code chatLineId} —
     * {@code #Channel/<server id>} — or null for a line the server never
     * named, a whisper, or a channel a link cannot spell.
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
        this.popup.open(POPUP_PLAYER, null, entries, this.font, mouseX,
                mouseY, this.screenWidth, this.screenHeight);
        return true;
    }

    /** The character selection menu, anchored above its button. */
    void openCharacterSelectionMenu(int anchorX, int anchorBottom) {
        this.popup.open(POPUP_CHARACTERS, null, characterSelectionEntries(),
                this.font, anchorX, anchorBottom, this.screenWidth,
                this.screenHeight);
    }

    /**
     * The menu's rows: the selected character on top — the identity the
     * tab currently speaks as, with the lock control beside it, which
     * locks this tab and this tab alone — then the account and every
     * roster character to choose from.
     */
    private List<ChatPopupMenu.Entry> characterSelectionEntries() {
        ChatTab selected = ClientChatChannelState.getSelected();
        UUID self = this.mc.thePlayer == null ? null
                : this.mc.thePlayer.getUniqueID();
        boolean locked = ClientChatAppearances.isLocked(selected);
        ClientChatAppearances.Appearance current =
                ClientChatAppearances.effectiveFor(selected);
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>();
        entries.add(ChatPopupMenu.Entry.passive(current.name)
                .withHead(self, current.account ? "" : current.skinId)
                .withLockControl(locked));
        entries.add(ChatPopupMenu.Entry.header(
                StatCollector.translateToLocal(
                        "gui.losttales.chat.character_selection.account")));
        entries.add(characterEntry("characters:account",
                ClientChatAppearances.accountAppearance(), selected, self));
        List<ClientChatAppearances.Appearance> characters =
                ClientChatAppearances.characterAppearances();
        if (!characters.isEmpty()) {
            entries.add(ChatPopupMenu.Entry.header(
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.character_selection"
                                    + ".characters")));
            for (ClientChatAppearances.Appearance appearance : characters) {
                entries.add(characterEntry(
                        "characters:char:" + appearance.characterId,
                        appearance, selected, self));
            }
        }
        List<ClientChatAppearances.Appearance> lore =
                ClientChatAppearances.loreAppearances();
        if (!lore.isEmpty()) {
            entries.add(ChatPopupMenu.Entry.header(
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.character_selection.lore")));
            for (ClientChatAppearances.Appearance appearance : lore) {
                entries.add(characterEntry(
                        "characters:char:" + appearance.characterId,
                        appearance, selected, self));
            }
        }
        return entries;
    }

    /** One choosable identity: its head, its name, and the mention honey
     *  as the swatch of the one the tab currently speaks as. */
    private static ChatPopupMenu.Entry characterEntry(
            String id, ClientChatAppearances.Appearance appearance,
            ChatTab selected, UUID self) {
        boolean effective = ClientChatAppearances.isEffective(
                appearance, selected);
        return new ChatPopupMenu.Entry(id, appearance.name, false,
                effective ? LostTalesColors.rgb(LostTalesColors.HONEY) : -1,
                null).withHead(self, appearance.skinId);
    }

    /** A choice applies to the selected tab: as its lock if it has one, else until the next switch. */
    private static void handleCharacterSelectionEntry(
            ChatPopupMenu.Entry entry) {
        ChatTab selected = ClientChatChannelState.getSelected();
        if ("characters:account".equals(entry.id)) {
            ClientChatAppearances.select(
                    ClientChatAppearances.accountAppearance(), selected);
            return;
        }
        if (!entry.id.startsWith("characters:char:")) {
            return;
        }
        UUID characterId;
        try {
            characterId = UUID.fromString(
                    entry.id.substring("characters:char:".length()));
        } catch (IllegalArgumentException ignored) {
            return;
        }
        for (ClientChatAppearances.Appearance appearance
                : ClientChatAppearances.characterAppearances()) {
            if (characterId.equals(appearance.characterId)) {
                ClientChatAppearances.select(appearance, selected);
                return;
            }
        }
        for (ClientChatAppearances.Appearance appearance
                : ClientChatAppearances.loreAppearances()) {
            if (characterId.equals(appearance.characterId)) {
                ClientChatAppearances.select(appearance, selected);
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
                && ClientChatChannelState.canModerate();
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
            handleCharacterSelectionEntry(entry);
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
