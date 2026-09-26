package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelSuggester;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatNarrator;
import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.chat.ChatReportReason;
import com.ninuna.losttales.chat.ChatRoleplayStatus;
import com.ninuna.losttales.chat.ChatStatusLine;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.SubWindow;
import com.ninuna.losttales.client.window.SubWindowAnchor;
import com.ninuna.losttales.client.window.SubWindowKind;
import com.ninuna.losttales.client.window.SubWindows;
import com.ninuna.losttales.client.window.TabMark;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowMenus;
import com.ninuna.losttales.client.window.WindowPlacement;
import com.ninuna.losttales.client.window.WindowTab;
import com.ninuna.losttales.gui.screen.character.CharactersPage;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiFlatLayers;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatDeletePacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.network.packet.LostTalesChatReportPacket;
import com.ninuna.losttales.permission.LostTalesCapability;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

/**
 * The chat's own menus, each in a sub-window the screen's menus open
 * ({@link WindowMenus}): the menu over a message and its report, the menu
 * over a person, and the character menu with its status line. Besides
 * them, what the chat offers under the window's {@code +} and in its tab
 * search, and the facts the message menu reads off the drawn lines.
 */
final class ChatMenus {
    private static final String ENTRY_MESSAGE = "message_player";
    private static final String ENTRY_VIEW_PROFILE = "view_profile";
    private static final String ENTRY_REPLY = "reply";
    private static final String ENTRY_FORWARD = "forward";
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
    private static final String ENTRY_NARRATOR = "characters:narrator";
    /** The start of a status's row id in the character menu. */
    private static final String ENTRY_STATUS_PREFIX = "characters:status:";
    private static final String ENTRY_STATUS_LINE = "characters:status_line";
    /** The start of a role-play status's row id in the character menu. */
    private static final String ENTRY_ROLEPLAY_PREFIX = "characters:roleplay:";
    /** The start of a character's row id in the character menu. */
    private static final String ENTRY_CHARACTER_PREFIX = "characters:char:";
    private static final String ENTRY_STATUS_LINE_KEEP = "status_line:keep";
    private static final String ENTRY_STATUS_LINE_CLEAR = "status_line:clear";

    /** What the chat's part does for its menus. */
    interface Host {
        /** Sends a command a row asked for, as a typed one is sent. */
        void sendCommand(String command);

        /** A row chose who the chat speaks as. */
        void identityChosen();

        /** A row chose where a message is forwarded. */
        void forward(ChatTab tab, long messageId);
    }

    /**
     * The message a message menu or a report is about, resolved as the
     * menu opened, while the pointer was still on the line: by the time a
     * row is taken the pointer is on the window. It is the same message
     * by its line, whatever was read off it.
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

        @Override
        public boolean equals(Object other) {
            return other instanceof MessageAim
                    && ((MessageAim)other).chatLineId == this.chatLineId;
        }

        @Override
        public int hashCode() {
            return this.chatLineId;
        }
    }

    private final WindowMenus menus;
    private final SubWindows windows;
    private final ChatTabActions tabActions;
    private final ChatComposer composer;
    private final ChatNoticeSink notices;
    private final Host host;
    private Minecraft mc;
    private GuiTextField field;
    private int screenWidth;
    private int screenHeight;
    /** A message the menu was asked to react to, until the screen takes it. */
    private long pendingReactionTarget = ChatMessageIds.NONE;

    ChatMenus(WindowMenus menus, SubWindows windows, ChatTabActions tabActions,
              ChatComposer composer, ChatNoticeSink notices, Host host) {
        this.menus = menus;
        this.windows = windows;
        this.tabActions = tabActions;
        this.composer = composer;
        this.notices = notices;
        this.host = host;
        menus.register(ChatSubWindows.MESSAGE, new MessageSource());
        menus.register(ChatSubWindows.REPORT, new ReportSource());
        menus.register(ChatSubWindows.FORWARD, new ForwardSource());
        menus.register(ChatSubWindows.PERSON, new PersonSource());
        menus.register(ChatSubWindows.CHARACTERS, new CharacterSource());
        menus.register(ChatSubWindows.STATUS_LINE, new StatusLineSource());
    }

    /** Called from {@code initGui}, which also runs on every resize. */
    void bind(Minecraft mc, GuiTextField field, int screenWidth,
              int screenHeight) {
        this.mc = mc;
        this.field = field;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /* ---- What is open ---- */

    boolean isOpen(SubWindowKind kind) {
        return this.menus.isOpen(kind);
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
    ChatFrame toolbarMenuFrame() {
        MessageAim aim = openMessageAim();
        return aim == null || aim.toolbarWindowId == null ? null
                : ChatFrame.find(aim.toolbarWindowId);
    }

    private MessageAim openMessageAim() {
        return this.menus.isOpen(ChatSubWindows.MESSAGE)
                ? messageOf(this.menus.menu(ChatSubWindows.MESSAGE)) : null;
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

    private static MessageAim messageOf(MenuWindow menu) {
        return menu.about() instanceof MessageAim ? (MessageAim)menu.about()
                : null;
    }

    private static LostTalesChatHoverCard.Target targetOf(MenuWindow menu) {
        return menu.about() instanceof LostTalesChatHoverCard.Target
                ? (LostTalesChatHoverCard.Target)menu.about() : null;
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

    /* ---- What the chat offers to open ---- */

    /**
     * The chat's part of the {@code +} and of the tab search: the closed
     * channels, each wearing the icon its tab would wear, its unread mark
     * included — a closed channel keeps receiving, and the mark is the one
     * the tab shows once restored; a muted one reads italic, like its tab
     * would. Then the online players, each opening (or selecting) the
     * conversation with them, wearing the head its tab wears and the
     * conversation's mark; the search leaves out a conversation already
     * open, which it lists among the open tabs. Every row whose name
     * holds {@code filter}, and a section with none left is left out.
     */
    static void addOpenable(Minecraft mc, List<MenuWindow.Entry> entries,
                            String filter, boolean search) {
        List<MenuWindow.Entry> closed = new ArrayList<MenuWindow.Entry>();
        for (ChatChannel channel : restorableChannels()) {
            String name = ClientChatChannelState.displayName(channel);
            if (WindowMenus.matchesFilter(name, filter)) {
                ChatTab tab = ChatTab.of(channel);
                closed.add(new MenuWindow.Entry(tab.id(), name,
                        ChatLayout.isMuted(channel),
                        ClientChatChannelState.displayColor(channel), tab));
            }
        }
        WindowMenus.addSection(entries, StatCollector.translateToLocal(
                "gui.losttales.chat.open.channels"), closed);
        List<MenuWindow.Entry> players = new ArrayList<MenuWindow.Entry>();
        for (String name : whisperCandidates(mc)) {
            ChatTab conversation = ChatTab.whisper(name, "");
            if (conversation != null
                    && !(search && ChatLayout.isOpen(conversation))
                    && WindowMenus.matchesFilter(name, filter)) {
                players.add(new MenuWindow.Entry(conversation.id(), name,
                        ChatLayout.isMuted(conversation), -1, conversation));
            }
        }
        WindowMenus.addSection(entries, StatCollector.translateToLocal(
                "gui.losttales.chat.open.players"), players);
    }

    /** Closed channels the player could see if they were open. */
    static List<ChatChannel> restorableChannels() {
        List<ChatChannel> closed = ChatLayout.closedChannels();
        List<ChatChannel> result = new ArrayList<ChatChannel>(closed.size());
        for (ChatChannel channel : closed) {
            if (ClientChatChannelState.isAvailable(channel)) {
                result.add(channel);
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
            for (String kept : result) {
                if (kept.equalsIgnoreCase(name)) {
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
    static TabMark closedMark() {
        List<ChatTab> closed = new ArrayList<ChatTab>();
        for (ChatChannel channel : restorableChannels()) {
            closed.add(ChatTab.of(channel));
        }
        return TabMark.combined(closed);
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
                        (float)WindowPlacement.preciseMouseX(this.mc,
                                this.screenWidth),
                        (float)WindowPlacement.preciseMouseY(this.mc,
                                this.screenHeight));
        if (band == null || band.lines == null
                || band.viewIndex >= band.lines.size()
                || band.lines.get(band.viewIndex) == null) {
            return false;
        }
        this.menus.show(ChatSubWindows.MESSAGE, messageAim(text,
                        band.lines.get(band.viewIndex).getChatLineID(),
                        band.lines, band.viewIndex, null),
                WindowMenus.hangingFrom(pointerAnchor(mouseX, mouseY)), false);
        return true;
    }

    /**
     * The same menu from a message's toolbar, about the message the
     * toolbar was drawn for — its first drawn row {@code row} of
     * {@code frame}'s lines — rather than whatever lies under the pointer,
     * which may be the row above; it hangs from the control's box toward
     * the middle of the window. A switch, as the control is.
     */
    boolean toggleToolbarMessageMenu(ChatFrame frame, int chatLineId,
                                     int row, SubWindowAnchor anchor) {
        if (frame == null || row < 0 || row >= frame.lines.size()) {
            return false;
        }
        String text = LostTalesChatClipboard.messageTextOf(frame.lines, row);
        if (text.length() == 0) {
            return false;
        }
        this.menus.show(ChatSubWindows.MESSAGE, messageAim(text, chatLineId,
                        frame.lines, row, frame.windowId),
                WindowMenus.hangingFrom(anchor), true);
        return true;
    }

    /**
     * Where a menu opened over the lines hangs: the pointer, toward the
     * middle of the window it is in.
     */
    private SubWindowAnchor pointerAnchor(int mouseX, int mouseY) {
        return SubWindowAnchor.inward(mouseX, mouseY, mouseX, mouseY,
                ChatFrame.drawnAt(mouseX, mouseY), this.screenWidth,
                this.screenHeight);
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

    /** A message's actions. */
    private final class MessageSource extends WindowMenus.Source {
        /** The line still held, and its tab — where it has one — still open in some window. */
        @Override
        public boolean stillStands(MenuWindow menu) {
            MessageAim aim = messageOf(menu);
            return aim != null
                    && (aim.tab == null || ChatLayout.isOpen(aim.tab))
                    && stillHeld(aim.chatLineId);
        }

        @Override
        public void rebuild(MenuWindow menu) {
            menu.setTitle(null, LostTalesUiSheet.MORE);
            menu.setRows(messageRows(messageOf(menu)));
        }

        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            return actOnMessage(menu, entry, window);
        }
    }

    /**
     * The message menu's rows. Every line offers the same actions in the
     * same places, as the hover toolbar does; one that cannot be taken on
     * this line stands muted and says why. A line the server named can be
     * reacted to and linked to by its id; any line in a tab that takes
     * messages can be answered, by its id or by its words. Asked to take
     * the message back, the menu becomes the question.
     */
    private List<MenuWindow.Entry> messageRows(MessageAim aim) {
        List<MenuWindow.Entry> entries = new ArrayList<MenuWindow.Entry>();
        if (aim.confirmingDelete) {
            MenuWindow.Entry confirm = new MenuWindow.Entry(
                    ENTRY_DELETE_CONFIRM, StatCollector.translateToLocal(
                            "gui.losttales.chat.message.delete.confirm"));
            entries.add(isOwnMessage(aim) ? confirm
                    : confirm.withLabelColor(OPERATOR_ACTION_COLOR));
            return entries;
        }
        entries.add(action(ENTRY_REACT, "gui.losttales.chat.message.react",
                LostTalesChatPresentation.whyNotReactable(aim.chatLineId)));
        entries.add(action(ENTRY_REPLY, "gui.losttales.chat.message.reply",
                LostTalesChatPresentation.whyNotRepliable(aim.chatLineId)));
        entries.add(action(ENTRY_FORWARD, "gui.losttales.chat.message.forward",
                LostTalesChatPresentation.whyNotForwardable(aim.chatLineId)));
        entries.add(new MenuWindow.Entry(ENTRY_COPY,
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
                entries.add(new MenuWindow.Entry(ENTRY_EDIT,
                        StatCollector.translateToLocal(
                                "gui.losttales.chat.message.edit")));
            }
            entries.add(new MenuWindow.Entry(ENTRY_DELETE,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.message.delete")));
        } else if (canModerateMessage(aim)) {
            entries.add(new MenuWindow.Entry(ENTRY_DELETE,
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
    private boolean actOnMessage(MenuWindow menu, MenuWindow.Entry entry,
                                 SubWindow window) {
        MessageAim aim = messageOf(menu);
        if (ENTRY_DELETE.equals(entry.id)) {
            if (!isOwnMessage(aim) && !canModerateMessage(aim)) {
                return false;
            }
            aim.confirmingDelete = true;
            menu.setRows(messageRows(aim));
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
            this.menus.show(ChatSubWindows.REPORT, aim,
                    WindowMenus.hangingFrom(WindowMenus.inPlaceOf(window)),
                    false);
        } else if (ENTRY_FORWARD.equals(entry.id)
                && ChatMessageIds.isServerId(aim.messageId)) {
            this.menus.show(ChatSubWindows.FORWARD,
                    Long.valueOf(aim.messageId),
                    WindowMenus.hangingFrom(WindowMenus.inPlaceOf(window)),
                    false);
        }
        return false;
    }

    /**
     * The conversations to forward the server's message {@code messageId}
     * to, hung from the toolbar's control: a switch, as the control is.
     */
    void toggleForward(long messageId, SubWindowAnchor anchor) {
        if (ChatMessageIds.isServerId(messageId)) {
            this.menus.show(ChatSubWindows.FORWARD, Long.valueOf(messageId),
                    WindowMenus.hangingFrom(anchor), true);
        }
    }

    /**
     * Forward: the conversations a message can be carried on to, narrowed
     * as the quick switcher's list is, as it is typed. A row forwards the
     * message there and the list is done.
     */
    private final class ForwardSource extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            return menu.about() instanceof Long;
        }

        @Override
        public void prepare(MenuWindow menu) {
            menu.openField(StatCollector.translateToLocal(
                            "gui.losttales.chat.forward.prompt"),
                    null, LostTalesUiSheet.FORWARD,
                    MenuWindow.MAX_FILTER_LENGTH, false);
        }

        @Override
        public void rebuild(MenuWindow menu) {
            menu.setTitle(null, LostTalesUiSheet.FORWARD);
            menu.setRows(forwardRows(menu.filter()));
        }

        @Override
        public boolean readsAsTyped() {
            return true;
        }

        @Override
        public MenuWindow.Entry firstFound(MenuWindow menu) {
            return WindowMenus.firstTyped(menu);
        }

        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            ChatTab tab = ChatTab.from(WindowTab.fromId(entry.id));
            if (tab != null && menu.about() instanceof Long) {
                ChatMenus.this.host.forward(tab,
                        ((Long)menu.about()).longValue());
            }
            return false;
        }
    }

    /**
     * The conversations a message can be forwarded to, as the quick
     * switcher lists them: the open ones, then the channels and the
     * people that could be opened; only those this player may speak in.
     */
    private List<MenuWindow.Entry> forwardRows(String filter) {
        List<MenuWindow.Entry> entries = new ArrayList<MenuWindow.Entry>();
        List<MenuWindow.Entry> open = new ArrayList<MenuWindow.Entry>();
        for (WindowTab each : WindowLayout.order()) {
            ChatTab tab = ChatTab.from(each);
            if (tab != null && forwardsInto(tab)
                    && WindowMenus.matchesFilter(tab.title(), filter)) {
                open.add(new MenuWindow.Entry(tab.id(), tab.title(),
                        tab.isMuted(), tab.tone(), tab));
            }
        }
        WindowMenus.addSection(entries, StatCollector.translateToLocal(
                "gui.losttales.window.search.open"), open);
        List<MenuWindow.Entry> channels = new ArrayList<MenuWindow.Entry>();
        for (ChatChannel channel : restorableChannels()) {
            ChatTab tab = ChatTab.of(channel);
            String name = ClientChatChannelState.displayName(channel);
            if (forwardsInto(tab) && WindowMenus.matchesFilter(name, filter)) {
                channels.add(new MenuWindow.Entry(tab.id(), name,
                        ChatLayout.isMuted(channel),
                        ClientChatChannelState.displayColor(channel), tab));
            }
        }
        WindowMenus.addSection(entries, StatCollector.translateToLocal(
                "gui.losttales.chat.open.channels"), channels);
        List<MenuWindow.Entry> players = new ArrayList<MenuWindow.Entry>();
        for (String name : whisperCandidates(this.mc)) {
            ChatTab conversation = ChatTab.whisper(name, "");
            if (conversation != null && !ChatLayout.isOpen(conversation)
                    && forwardsInto(conversation)
                    && WindowMenus.matchesFilter(name, filter)) {
                players.add(new MenuWindow.Entry(conversation.id(), name,
                        ChatLayout.isMuted(conversation), -1, conversation));
            }
        }
        WindowMenus.addSection(entries, StatCollector.translateToLocal(
                "gui.losttales.chat.open.players"), players);
        if (entries.isEmpty()) {
            entries.add(MenuWindow.Entry.passive(StatCollector.translateToLocal(
                    "gui.losttales.window.search.none")));
        }
        return entries;
    }

    /** Whether a message may be forwarded into a conversation: one this player may speak in, never an NPC's. */
    private static boolean forwardsInto(ChatTab tab) {
        return !tab.isNpc() && ClientChatChannelState.canSend(tab);
    }

    /** A message action's row: open, or muted with {@code why} it cannot be taken. */
    private static MenuWindow.Entry action(String id, String labelKey,
                                           String why) {
        MenuWindow.Entry entry = new MenuWindow.Entry(id,
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

    /** A message's report: a note and a reason, the reason sending it. */
    private final class ReportSource extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            return messageOf(menu) != null;
        }

        @Override
        public void prepare(MenuWindow menu) {
            menu.openField(StatCollector.translateToLocal(
                            "gui.losttales.chat.report.prompt"),
                    null, LostTalesUiSheet.EXCLAMATION,
                    ChatConsoleEvent.Report.MAX_NOTE_LENGTH, false);
        }

        @Override
        public void rebuild(MenuWindow menu) {
            menu.setTitle(null, LostTalesUiSheet.EXCLAMATION);
            menu.setRows(reportRows());
        }

        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            sendReport(messageOf(menu), entry.id, menu.filter());
            return false;
        }
    }

    /** The report's rows: a reason each, which sends the report. */
    private static List<MenuWindow.Entry> reportRows() {
        List<MenuWindow.Entry> entries = new ArrayList<MenuWindow.Entry>();
        for (ChatReportReason reason : ChatReportReason.values()) {
            entries.add(new MenuWindow.Entry(
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

    /* ---- The menu over a person ---- */

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
        this.menus.show(ChatSubWindows.PERSON, person,
                WindowMenus.hangingFrom(pointerAnchor(mouseX, mouseY)), false);
        return true;
    }

    /** A person's rows: message them, ignore them, and a moderator's mute. */
    private final class PersonSource extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            return targetOf(menu) != null;
        }

        @Override
        public void rebuild(MenuWindow menu) {
            LostTalesChatHoverCard.Target target = targetOf(menu);
            menu.setTitle(target.windowTitle(), null);
            menu.setRows(personRows(target));
        }

        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            return actOnPerson(targetOf(menu), entry);
        }
    }

    /** The name a person's rows speak of: the identity, else the account. */
    private static String personName(LostTalesChatHoverCard.Target person) {
        return LostTalesChatVisualStyle.removeColorCodes(
                person.identityName).trim();
    }

    private static List<MenuWindow.Entry> personRows(
            LostTalesChatHoverCard.Target person) {
        List<MenuWindow.Entry> entries = new ArrayList<MenuWindow.Entry>();
        String name = personName(person);
        if (person.hasProfile()) {
            entries.add(new MenuWindow.Entry(ENTRY_VIEW_PROFILE,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.card.view_profile")));
        }
        if (!LostTalesChatMessagePacket.isDiscordSender(person.playerId)) {
            entries.add(new MenuWindow.Entry(ENTRY_MESSAGE,
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
            entries.add(new MenuWindow.Entry(ENTRY_IGNORE_IDENTITY,
                    StatCollector.translateToLocalFormatted(
                            ClientChatIgnores.isIgnoredIdentity(
                                    person.playerId, name)
                                    ? "gui.losttales.chat.message.unignore"
                                    : "gui.losttales.chat.message.ignore",
                            name)));
        }
        boolean accountIgnored = ClientChatIgnores.isIgnored(person.playerId);
        entries.add(new MenuWindow.Entry(ENTRY_IGNORE,
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
            entries.add(new MenuWindow.Entry(
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
                                MenuWindow.Entry entry) {
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
        if (ENTRY_VIEW_PROFILE.equals(entry.id) && person.hasProfile()) {
            CharactersPage.visit(person.visit());
        } else if (ENTRY_MESSAGE.equals(entry.id)) {
            this.tabActions.openWhisperTab(person.accountName,
                    person.identityName);
        } else if (ENTRY_MUTE_ACCOUNT.equals(entry.id)) {
            startMute(muteTarget(fromDiscord, person.accountName));
        } else if (ENTRY_UNMUTE_ACCOUNT.equals(entry.id)) {
            this.host.sendCommand("/losttales chat unmute "
                    + muteTarget(fromDiscord, person.accountName));
        }
        return false;
    }

    /* ---- The character menu and the status line ---- */

    /**
     * The character menu, hung from the head button — a switch, as the
     * button is — with a search field over its rows that narrows them as
     * it is typed into. It follows the tab being typed in: on an account
     * channel, which always speaks as the account, it is the status rows
     * alone, since nothing there chooses an identity, with no roster and
     * no search field.
     */
    void toggleCharacterMenu(SubWindowAnchor anchor) {
        this.menus.show(ChatSubWindows.CHARACTERS, null,
                WindowMenus.hangingFrom(anchor), true);
    }

    /** Who the chat speaks as and what it says of itself. */
    private final class CharacterSource extends WindowMenus.Source {
        /** The field, the name and the rows, for the tab typed in now. */
        @Override
        public void rebuild(MenuWindow menu) {
            boolean statusOnly = !ClientChatIdentities.speaksInCharacter(
                    ClientChatChannelState.getSelected());
            if (statusOnly && menu.hasField()) {
                menu.closeField();
            } else if (!statusOnly && !menu.hasField()) {
                menu.openField(StatCollector.translateToLocal(
                                "gui.losttales.chat.character_selection.search"),
                        null, LostTalesUiSheet.SEARCH,
                        MenuWindow.MAX_FILTER_LENGTH, false);
            }
            menu.setTitle(StatCollector.translateToLocal(statusOnly
                    ? "gui.losttales.chat.character_selection.status"
                    : "gui.losttales.chat.sub.characters"), null);
            menu.setRows(characterSelectionEntries(menu.filter(),
                    statusOnly));
        }

        @Override
        public boolean readsAsTyped() {
            return true;
        }

        /**
         * The first character the typing found; none while nothing is
         * typed. The rows typing does not narrow, the Narrator's and the
         * statuses, are never taken by Enter.
         */
        @Override
        public MenuWindow.Entry firstFound(MenuWindow menu) {
            if (menu.filter().length() == 0) {
                return null;
            }
            for (MenuWindow.Entry entry : menu.entries()) {
                if (entry.isTakeable()
                        && entry.id.startsWith(ENTRY_CHARACTER_PREFIX)) {
                    return entry;
                }
            }
            return null;
        }

        /** A choice stays; the status line's row opens its field beside the menu. */
        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            if (ENTRY_STATUS_LINE.equals(entry.id)) {
                ChatMenus.this.menus.show(ChatSubWindows.STATUS_LINE,
                        ClientChatPresence.speakerOf(
                                ClientChatChannelState.getSelected()),
                        WindowMenus.besideWindow(window), true);
            } else {
                chooseIdentityOrStatus(entry);
                ChatMenus.this.host.identityChosen();
            }
            return true;
        }
    }

    /**
     * The selected chat identity, then the owned characters and the owned
     * lore characters, each section under its header. A filter keeps the
     * rows whose names hold it and drops a section with nothing left; one
     * that matches nothing says so under the current identity rather than
     * closing the menu under the hand that is typing.
     */
    private List<MenuWindow.Entry> characterSelectionEntries(String filter,
                                                             boolean statusOnly) {
        UUID self = this.mc.thePlayer == null ? null
                : this.mc.thePlayer.getUniqueID();
        ClientChatIdentities.Identity current = ClientChatIdentities.viewing();
        List<MenuWindow.Entry> entries = new ArrayList<MenuWindow.Entry>();
        if (statusOnly) {
            // The account speaking, and what it may say of itself.
            entries.add(MenuWindow.Entry.passive(
                    ClientChatIdentities.accountName())
                    .withPicture(head(self, "")));
            WindowMenus.addSection(entries, StatCollector.translateToLocal(
                    "gui.losttales.chat.character_selection.status"),
                    statusRows());
            WindowMenus.addSection(entries, StatCollector.translateToLocal(
                    "gui.losttales.chat.character_selection.roleplay"),
                    roleplayRows());
            return entries;
        }
        entries.add(MenuWindow.Entry.passive(ClientChatIdentities.isNarrating()
                ? ChatNarrator.NAME : current.name)
                .withPicture(head(self, current.account ? ""
                        : current.skinId)));
        if (ClientChatChannelState.holds(LostTalesCapability.CHAT_NARRATE)) {
            // The Narrator is a voice over the identity, not one of
            // them: it stands above the roster, marked while chosen.
            entries.add(new MenuWindow.Entry(ENTRY_NARRATOR,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.character_selection.narrator"),
                    false, ClientChatIdentities.isNarrating()
                            ? LostTalesColors.rgb(LostTalesColors.HONEY) : -1, null)
                    .withSprite(LostTalesUiSheet.SPEECH_BUBBLE,
                            LostTalesUiSheet.SPEECH_BUBBLE_HOVER,
                            ClientChatIdentities.isNarrating()));
        }
        WindowMenus.addSection(entries, StatCollector.translateToLocal(
                "gui.losttales.chat.character_selection.characters"),
                characterRows(ClientChatIdentities.characterIdentities(),
                        filter, self));
        WindowMenus.addSection(entries, StatCollector.translateToLocal(
                "gui.losttales.chat.character_selection.lore"),
                characterRows(ClientChatIdentities.loreIdentities(),
                        filter, self));
        if (entries.size() == 1) {
            entries.add(MenuWindow.Entry.passive(
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.character_selection.none")));
        }
        WindowMenus.addSection(entries, StatCollector.translateToLocal(
                "gui.losttales.chat.character_selection.status"),
                statusRows());
        WindowMenus.addSection(entries, StatCollector.translateToLocal(
                "gui.losttales.chat.character_selection.roleplay"),
                roleplayRows());
        return entries;
    }

    /**
     * The role-play statuses to choose from for the identity the selected
     * tab speaks as (P4 a), each with its mark, the one it has marked.
     */
    private static List<MenuWindow.Entry> roleplayRows() {
        ChatPresenceIdentity speaker = ClientChatPresence.speakerOf(
                ClientChatChannelState.getSelected());
        ChatRoleplayStatus chosen = ClientChatPresence.chosenRoleplay(speaker);
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        for (final ChatRoleplayStatus status : ChatRoleplayStatus.values()) {
            rows.add(new MenuWindow.Entry(ENTRY_ROLEPLAY_PREFIX + status.name(),
                    StatCollector.translateToLocal(status.labelKey()), false,
                    chosen == status
                            ? LostTalesColors.rgb(LostTalesColors.HONEY) : -1, null)
                    .withPicture(new MenuWindow.Picture() {
                        @Override
                        public void draw(Minecraft minecraft, float iconX,
                                         float labelTop, int alpha) {
                            ChatRoleplayMark.draw(status, iconX
                                    + LostTalesUiInk.centredStart(
                                            LostTalesUiInk.ICON_SIZE,
                                            ChatRoleplayMark.SIZE),
                                    labelTop + LostTalesUiInk.centredStart(
                                            LostTalesUiInk.CAP_HEIGHT,
                                            ChatRoleplayMark.SIZE), alpha);
                        }
                    }));
        }
        return rows;
    }

    /**
     * The statuses to choose from for the identity the selected tab
     * speaks as — the chat identity on a roleplaying tab, the account on
     * any other — each with the sphere it shows, lighting to the ivory
     * one under the pointer, and the one chosen for it marked; and under
     * them the identity's status line, in italics, or the way to set one.
     */
    private static List<MenuWindow.Entry> statusRows() {
        ChatPresenceIdentity speaker = ClientChatPresence.speakerOf(
                ClientChatChannelState.getSelected());
        ChatPresence chosen = ClientChatPresence.chosen(speaker);
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        for (ChatPresence presence : ChatPresence.values()) {
            if (!presence.isChoosable()) {
                continue;
            }
            rows.add(new MenuWindow.Entry(ENTRY_STATUS_PREFIX + presence.name(),
                    StatCollector.translateToLocal(presence.labelKey()), false,
                    chosen == presence
                            ? LostTalesColors.rgb(LostTalesColors.HONEY) : -1, null)
                    .withSprite(ChatPresenceMark.markOf(presence),
                            LostTalesUiSheet.PRESENCE_SELECTED, false));
        }
        String line = ClientChatPresence.chosenLine(speaker);
        rows.add(new MenuWindow.Entry(ENTRY_STATUS_LINE,
                line.length() == 0 ? StatCollector.translateToLocal(
                        "gui.losttales.chat.status_line.set") : line,
                line.length() > 0, -1, null)
                .withLabel(INLINE_TEXT)
                .withSprite(LostTalesUiSheet.SPEECH_BUBBLE,
                        LostTalesUiSheet.SPEECH_BUBBLE_HOVER, false));
        return rows;
    }

    /** One section's rows: the identities whose names hold the filter. */
    private static List<MenuWindow.Entry> characterRows(
            List<ClientChatIdentities.Identity> identities,
            String filter, UUID self) {
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        for (ClientChatIdentities.Identity identity : identities) {
            if (WindowMenus.matchesFilter(identity.name, filter)) {
                rows.add(characterEntry(
                        ENTRY_CHARACTER_PREFIX + identity.characterId,
                        identity, self));
            }
        }
        return rows;
    }

    /** One choosable identity: its head, its name, and the mention honey as the swatch of the shared chat identity. */
    private static MenuWindow.Entry characterEntry(
            String id, ClientChatIdentities.Identity identity,
            UUID self) {
        boolean effective = ClientChatIdentities.isSelected(identity);
        return new MenuWindow.Entry(id, identity.name, false,
                effective ? LostTalesColors.rgb(LostTalesColors.HONEY) : -1,
                null).withPicture(head(self, identity.skinId));
    }

    /** A choice applies to every roleplaying conversation. */
    private static void chooseIdentityOrStatus(MenuWindow.Entry entry) {
        if (ENTRY_NARRATOR.equals(entry.id)) {
            ClientChatIdentities.setNarrating(!ClientChatIdentities.isNarrating());
            return;
        }
        if (entry.id.startsWith(ENTRY_ROLEPLAY_PREFIX)) {
            try {
                ClientChatPresence.chooseRoleplay(ClientChatPresence.speakerOf(
                                ClientChatChannelState.getSelected()),
                        ChatRoleplayStatus.valueOf(entry.id.substring(
                                ENTRY_ROLEPLAY_PREFIX.length())));
            } catch (IllegalArgumentException ignored) {
                // A row this build never made names no status.
            }
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
     * The status line's field and rows, for the identity it was opened
     * for: Enter or the first row keeps what the field holds, the second
     * clears the line, and closing the window leaves it as it was.
     */
    private final class StatusLineSource extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            return menu.about() instanceof ChatPresenceIdentity;
        }

        @Override
        public void prepare(MenuWindow menu) {
            menu.openField(StatCollector.translateToLocal(
                            "gui.losttales.chat.status_line.prompt"),
                    null, LostTalesUiSheet.SPEECH_BUBBLE,
                    ChatStatusLine.MAX_CHARACTERS, true);
            menu.setFilter(ClientChatPresence.chosenLine(
                    (ChatPresenceIdentity)menu.about()));
        }

        @Override
        public void rebuild(MenuWindow menu) {
            ChatPresenceIdentity identity = (ChatPresenceIdentity)menu.about();
            menu.setTitle(null, LostTalesUiSheet.SPEECH_BUBBLE);
            List<MenuWindow.Entry> entries = new ArrayList<MenuWindow.Entry>(2);
            entries.add(new MenuWindow.Entry(ENTRY_STATUS_LINE_KEEP,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.status_line.keep")));
            if (ClientChatPresence.chosenLine(identity).length() > 0) {
                entries.add(new MenuWindow.Entry(ENTRY_STATUS_LINE_CLEAR,
                        StatCollector.translateToLocal(
                                "gui.losttales.chat.status_line.clear")));
            }
            menu.setRows(entries);
        }

        /** Enter keeps what the field holds, whatever is typed. */
        @Override
        public MenuWindow.Entry firstFound(MenuWindow menu) {
            for (MenuWindow.Entry entry : menu.entries()) {
                if (entry.isTakeable()) {
                    return entry;
                }
            }
            return null;
        }

        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            ChatPresenceIdentity identity = (ChatPresenceIdentity)menu.about();
            if (ENTRY_STATUS_LINE_KEEP.equals(entry.id)) {
                ClientChatPresence.setLine(identity, menu.filter());
            } else if (ENTRY_STATUS_LINE_CLEAR.equals(entry.id)) {
                ClientChatPresence.setLine(identity, "");
            }
            return false;
        }
    }

    /* ---- How the chat's rows are drawn ---- */

    /** A status line's label: its emoji as their sprites, as the line reads once set. */
    static final MenuWindow.Label INLINE_TEXT = new MenuWindow.Label() {
        @Override
        public int width(FontRenderer font, String text) {
            return ChatInlineText.width(font, text, "");
        }

        @Override
        public void draw(Minecraft minecraft, FontRenderer font, String text,
                         String style, int x, int y, int rgb, int alpha) {
            ChatInlineText.draw(minecraft, font, text, style, x, y, rgb,
                    alpha);
        }
    };

    /**
     * A person's head in a row's icon column, drawn as the tabs draw
     * theirs: eight pixels, centred across the icon's box, fading as one
     * picture — an account's own, or a character skin's.
     */
    static MenuWindow.Picture head(final UUID owner, String skinId) {
        final String skin = skinId == null ? "" : skinId;
        return new MenuWindow.Picture() {
            @Override
            public void draw(final Minecraft minecraft, float iconX,
                             float labelTop, int alpha) {
                final float headX = iconX + 1.0F;
                final float headY = labelTop
                        + LostTalesChatOverlayRenderer.HEAD_TOP_OFFSET;
                final float opacity = alpha / 255.0F;
                LostTalesUiFlatLayers.draw(alpha, headX - 1.0F, headY - 1.0F,
                        headX + 10.0F, headY + 10.0F,
                        new LostTalesUiFlatLayers.Layers() {
                            @Override
                            public void draw() {
                                if (skin.length() == 0) {
                                    LostTalesCharacterHeadIconRenderer
                                            .drawAccountHead(minecraft, owner,
                                                    headX, headY, 8.0F, 1.0F,
                                                    opacity);
                                } else {
                                    LostTalesCharacterHeadIconRenderer
                                            .drawSnapshotHead(minecraft, owner,
                                                    skin, headX, headY, 8.0F,
                                                    1.0F, opacity);
                                }
                            }
                        });
            }
        };
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
        for (ChatLine line : lines) {
            if (line == null || line.getChatLineID() != chatLineId) {
                continue;
            }
            for (Object value : line.func_151461_a()) {
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
