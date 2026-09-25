package com.ninuna.losttales.client.chat;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatDeliveryMark;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatReactionSummary;
import com.ninuna.losttales.chat.ChatTabIds;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.emoji.ChatForeignEmoji;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.input.LostTalesKeyPress;
import com.ninuna.losttales.client.mapmarker.LostTalesLotrMapGui;
import com.ninuna.losttales.client.quest.ClientQuestCatalog;
import com.ninuna.losttales.client.window.ScreenPart;
import com.ninuna.losttales.client.window.SubWindow;
import com.ninuna.losttales.client.window.SubWindowAnchor;
import com.ninuna.losttales.client.window.SubWindowKind;
import com.ninuna.losttales.client.window.SubWindowPlaces;
import com.ninuna.losttales.client.window.TabMark;
import com.ninuna.losttales.client.window.TabRow;
import com.ninuna.losttales.client.window.ToolStrip;
import com.ninuna.losttales.client.window.WheelStep;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowDrawing;
import com.ninuna.losttales.client.window.WindowFrame;
import com.ninuna.losttales.client.window.WindowHover;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowPlacement;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.client.window.WindowSearch;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WindowTab;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.screen.quest.QuestJournalPage;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiRules;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatCommandContextPacket;
import com.ninuna.losttales.network.packet.LostTalesChatReactPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestShareJoinPacket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatList;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * The chat's work on the window screen: its input bar and the field it
 * types into, the completion lists, the reply chip, the menus, the
 * pickers and cards, the empty state, and each conversation's lines,
 * member list, toolbar, scrollbar and jump pill. The screen asks it at
 * fixed points of everything it does ({@link ScreenPart}); the windows,
 * their rows, the pages and the snapping are the screen's.
 */
public final class ChatScreenPart extends ScreenPart {
    /** Gap between the typing line's bubble and its words. */
    private static final int TYPING_BUBBLE_GAP = 3;
    /**
     * The empty state's strip: as tall as a window's tab row, so what
     * stands where the chat would be reads as a piece of the same
     * interface, and with the same clear space around its contents the
     * row keeps around its own.
     */
    private static final int EMPTY_STATE_HEIGHT = TabRow.ROW_HEIGHT;
    private static final int EMPTY_STATE_PADDING = 4;
    /** Gap between the + and the line beside it. */
    private static final int EMPTY_STATE_GAP = 5;

    private static final ScreenPart.Maker MAKER = new ScreenPart.Maker() {
        @Override
        public ScreenPart make(WindowScreen screen) {
            return new ChatScreenPart(screen);
        }
    };

    /** Set once a message or command has gone out; the draft is then spent. */
    private boolean sent;
    /** The short notice over the bar, offered to every collaborator. */
    private final ChatNoticeSink notices = new ChatNoticeSink() {
        @Override
        public void showNotice(String message) {
            ChatScreenPart.this.showNotice(message);
        }
    };
    /** The reply or the edit the bar is composing, and its chip. */
    private final ChatComposer composer = new ChatComposer();
    /** What goes to the server: messages, edits, whispers, typing. */
    private final ChatOutbox outbox = new ChatOutbox(this.composer,
            this.notices);
    /** The input section: its place, its controls, its notice. */
    private final ChatInputBar bar = new ChatInputBar();
    /** The suggestion lists, command completion and token insertion. */
    private final ChatInputCompletion completion =
            new ChatInputCompletion(this.notices);
    /** The selection, and the verbs that open, close and move tabs. */
    private final ChatTabActions tabActions = new ChatTabActions(this.bar,
            this.completion, this.composer);
    /** Every menu the chat opens, and what their rows do. */
    private final ChatScreenMenus menus = new ChatScreenMenus(
            this.tabActions, this.composer, this.notices);
    private Minecraft mc;
    private FontRenderer fontRendererObj;
    /** The game's chat field, drawn the chat's way. */
    private ChatInputField inputField;
    private URI clickedLinkUri;
    /** The empty state's + as drawn this frame; width zero while none was. */
    private int emptyPlusLeft;
    private int emptyPlusTop;
    private int emptyPlusRight;
    private int emptyPlusBottom;
    private String composingIdentityKey;
    /**
     * The window whose bar is live as this frame's windows began: the
     * search follows it, and every other window wears a resting bar.
     */
    private String liveWindowId;

    private ChatScreenPart(WindowScreen screen) {
        super(screen);
    }

    /** Gives every window screen opened from now on the chat's part. */
    public static void install() {
        WindowScreen.addPart(MAKER);
    }

    /**
     * The run of the lines under a GUI-space point, as the screen's own
     * hover finds it: none while anything drawn above the lines has the
     * point. What the vanilla hit-test hook hands another mod asking where
     * the pointer is, so LOTR's achievement card among others stays
     * silent under a menu.
     */
    static LostTalesChatOverlayRenderer.Hit lineHitAt(WindowScreen screen,
                                                      double x, double y) {
        ChatHover under = ChatHover.of(screen.hoverAt(x, y));
        return under != null && under.is(ChatHover.Kind.LINE)
                ? under.line : null;
    }

    /* ---- Life ---- */

    @Override
    public void beforeInit() {
        ClientChatChannelState.ensureAvailable();
    }

    @Override
    public void init() {
        this.mc = this.screen.mc;
        this.fontRendererObj = this.screen.font();
        // The chat draws its own text, shadow and all; vanilla's field
        // would put a quarter-colour shadow under the one thing on the
        // bar the player is looking at. Everything else about it —
        // typing, history, the caret's own behaviour — stays vanilla's,
        // and it takes over the state the field it replaces was given.
        GuiTextField vanillaField = this.screen.inputField();
        ChatInputField styled = new ChatInputField(this.fontRendererObj,
                vanillaField.xPosition, vanillaField.yPosition,
                vanillaField.getWidth(), ChatInputBar.FIELD_HEIGHT);
        styled.setEnableBackgroundDrawing(false);
        styled.setCanLoseFocus(false);
        styled.setFocused(true);
        styled.setMaxStringLength(vanillaField.getMaxStringLength());
        styled.setText(vanillaField.getText());
        styled.setCursorPositionEnd();
        this.screen.setInputField(styled);
        this.inputField = styled;
        // A mention shows as a ping for exactly the names the @ list
        // offers.
        styled.mentionsFrom(this.completion);
        this.completion.bind(this.mc, this.fontRendererObj,
                this.screen.regions(), styled);
        this.outbox.bind(this.mc);
        this.bar.bind(this.mc, this.fontRendererObj, this.screen.regions(),
                styled, this.screen.height);
        this.inputField.setTextColor(LostTalesUiInk.IVORY);
        this.inputField.setDisabledTextColour(LostTalesUiInk.SHADOW);
        // Share tokens do not count toward the visible limit, so the field
        // must accept the longer raw form; sending re-checks it.
        this.inputField.setMaxStringLength(
                ChatMessageValidator.MAX_RAW_CHARACTERS);
        this.bar.assignButtons();
        this.bar.updateInputBounds();
        // Text typed before the screen was closed (or before a resize
        // rebuilt the field) comes back; an explicit opener such as "/"
        // or "T"'s default text takes precedence over it.
        String draft = ClientChatChannelState.getDraft();
        if (this.inputField.getText().length() == 0 && draft.length() > 0) {
            this.inputField.setText(draft);
            this.inputField.setCursorPositionEnd();
        }
        this.composingIdentityKey = ClientChatIdentities.viewIdentityKey();
        this.tabActions.bind(this.mc, styled);
        this.menus.bind(this.mc, this.fontRendererObj, styled,
                this.screen.subWindows(), this.screen.width,
                this.screen.height);
        this.tabActions.syncSelection();
    }

    @Override
    public void opened() {
        ClientChatMembers.prefetch(tabsShowingMembers());
    }

    @Override
    public void tick() {
        syncChatIdentity();
        ClientChatChannelState.ensureAvailable();
        this.tabActions.syncSelection();
        if (WindowScreen.isEmpty() || !hasField()) {
            // No field is drawn and nothing can be typed; the drafts the
            // tabs already hold are left exactly as they are.
            this.outbox.stopTyping();
            return;
        }
        if (!this.sent) {
            ClientChatChannelState.setDraft(this.inputField.getText());
        }
        this.outbox.updateTyping(this.inputField.getText(),
                ClientChatChannelState.getSelected());
    }

    /**
     * Closing without sending keeps the text for the next opening; once
     * something has been sent the draft is spent.
     */
    @Override
    public void closed() {
        if (!WindowScreen.isEmpty()) {
            ClientChatChannelState.setDraft(
                    this.sent ? "" : this.inputField.getText());
        }
        ClientChatChannelViews.setScrollEasingSuppressed(false);
        // Every divider that was on a viewed tab has done its job, and
        // how far the tabs were read is written down.
        ClientChatChannelViews.dismissSeenDividers();
        ClientChatReadMarks.save();
        this.outbox.stopTyping();
    }

    /* ---- What the chat holds ---- */

    /** The window being typed in, whose bar is the live one; null for none. */
    @Override
    public String typedWindowId() {
        Window typed = WindowLayout.windowOf(
                ClientChatChannelState.getSelected());
        return typed == null || WindowLayout.showsPage(typed) ? null
                : typed.getId();
    }

    @Override
    public String inputWindowId() {
        return this.liveWindowId;
    }

    @Override
    public Window keyWindow() {
        return WindowLayout.windowOf(ClientChatChannelState.getSelected());
    }

    /** Whether a window shows a conversation, so there is a bar to type into. */
    @Override
    public boolean hasField() {
        return this.bar.activeFrame() != null;
    }

    @Override
    public GuiTextField makeSearchField() {
        return new ChatInputField(this.screen.font(), 0, 0, 10,
                ToolStrip.FIELD_HEIGHT).plainText();
    }

    /**
     * Writes {@code token} into the field being typed in as a word of its
     * own, and gives the field the keys: a page's share, as a picker's
     * pick is written.
     */
    @Override
    public boolean insertText(String token) {
        this.completion.insertToken(token);
        this.screen.leavePage();
        this.inputField.setFocused(true);
        this.screen.syncTypingFocus();
        return true;
    }

    /* ---- What the windows ask of it ---- */

    @Override
    public boolean selectWindow(Window window) {
        this.tabActions.selectWindow(window);
        return true;
    }

    @Override
    public boolean selectTab(WindowTab tab) {
        this.tabActions.selectChannel(tab);
        return true;
    }

    @Override
    public boolean closeTab(WindowTab tab) {
        this.tabActions.closeTab(tab);
        return true;
    }

    @Override
    public boolean closeWindow(Window window) {
        this.tabActions.closeWindow(window);
        return true;
    }

    @Override
    public void windowsMoved() {
        this.bar.updateInputBounds();
    }

    @Override
    public boolean showTabMenu(WindowTab tab, SubWindowAnchor anchor,
                               boolean toggle) {
        this.menus.showTabMenu(tab, anchor, toggle);
        return true;
    }

    @Override
    public boolean showWindowMenu(Window window, SubWindowAnchor anchor,
                                  boolean toggle) {
        this.menus.showWindowMenu(window, anchor, toggle);
        return true;
    }

    @Override
    public boolean toggleRestoreMenu(Window window, SubWindowAnchor anchor) {
        this.menus.toggleChannelMenu(window, anchor, null);
        return true;
    }

    @Override
    public boolean toggleTabSearch(Window window, SubWindowAnchor anchor) {
        this.menus.toggleSearchPanel(window, anchor, null);
        return true;
    }

    @Override
    public boolean isMenuOpenFor(SubWindowKind kind, Object about) {
        return this.menus.isOpenFor(kind, about);
    }

    /** Closed channels that can be read, and people online to whisper to. */
    @Override
    public boolean hasRestorable() {
        return !ChatScreenMenus.restorableChannels().isEmpty()
                || ChatScreenMenus.hasWhisperCandidates(this.mc);
    }

    @Override
    public TabMark restorableMark() {
        return ChatScreenMenus.closedMark();
    }

    /** How many unread lines wait in the closed channels, when any do. */
    @Override
    public String restoreTip() {
        int unread = ChatScreenMenus.closedUnreadCount();
        return unread <= 0 ? null : StatCollector.translateToLocalFormatted(
                "gui.losttales.chat.tab.restore_unread",
                unread > ClientChatChannelViews.MAX_UNREAD
                        ? ClientChatChannelViews.MAX_UNREAD + "+"
                        : String.valueOf(unread));
    }

    /** Walks to the next match down, or the previous one up, in the window typed in, and lands on it. */
    @Override
    public boolean walkSearch(boolean forward) {
        Window typed = WindowLayout.windowOf(
                ClientChatChannelState.getSelected());
        ChatFrame frame = typed == null ? null : ChatFrame.of(typed);
        if (frame != null) {
            landSearch(frame, ChatSearch.walk(forward, this.mc, frame));
        }
        return true;
    }

    /* ---- Keys ---- */

    /**
     * Vanilla's own rule: any key ends a pending completion request, and
     * any key but Tab ends the walk through the candidates — except that
     * while the candidate popup is open, Up and Down walk it too, and
     * Escape only closes it.
     */
    @Override
    public boolean keyOverAll(LostTalesKeyPress press) {
        return this.completion.handleCommandPopupKey(press.key);
    }

    @Override
    public void keyPassing(LostTalesKeyPress press) {
        if (press.key != Keyboard.KEY_TAB) {
            this.completion.onKeyNotTab();
        }
    }

    /**
     * PageUp and PageDown move the window being typed in a page,
     * whichever field has the keys: no field uses them.
     */
    @Override
    public boolean keyForTypedWindow(LostTalesKeyPress press) {
        if (press.key != Keyboard.KEY_PRIOR && press.key != Keyboard.KEY_NEXT) {
            return false;
        }
        ChatFrame typed = this.bar.activeFrame();
        if (typed != null) {
            scrollHistory(typed, press.key == Keyboard.KEY_PRIOR
                    ? typed.pagePixels() : -typed.pagePixels());
        }
        return true;
    }

    @Override
    public boolean keyShortcut(LostTalesKeyPress press) {
        // Escape with nothing else open drops the reply before it closes
        // the chat: backing out of an answer should not cost the screen.
        if (press.key == Keyboard.KEY_ESCAPE
                && (this.composer.isReplying() || this.composer.isEditing())) {
            this.composer.cancelComposing(this.inputField);
            return true;
        }
        // Ctrl+N is the + control by keyboard, and Ctrl+Shift+A the tab
        // search: both mean something with nothing open, since both are
        // ways back to a channel, and both are switches, as their
        // controls are: pressed again, they put the window away.
        if (press.isCommand(Keyboard.KEY_A) && press.shift) {
            this.screen.leaveSearchForMenu();
            this.menus.toggleSearchPanel(WindowLayout.windowOf(
                    ClientChatChannelState.getSelected()), null,
                    emptyPlusAnchor());
            this.screen.syncTypingFocus();
            return true;
        }
        if (press.isCommand(Keyboard.KEY_N)) {
            this.screen.leaveSearchForMenu();
            this.menus.toggleChannelMenu(WindowLayout.windowOf(
                    ClientChatChannelState.getSelected()), null,
                    emptyPlusAnchor());
            this.screen.syncTypingFocus();
            return true;
        }
        // Ctrl+, is Settings from anywhere, a switch as the window menu's
        // row is.
        if (press.isCommand(Keyboard.KEY_COMMA)) {
            this.screen.leaveSearchForMenu();
            this.menus.toggleSettings();
            this.screen.syncTypingFocus();
            return true;
        }
        return false;
    }

    /**
     * A key for the field of the sub-window in front: a menu's goes to
     * the menus, which may send a command; Enter in a picker's search
     * takes the first cell it found; anything else is the field's own.
     */
    @Override
    public boolean keyIntoSubWindow(SubWindow front, LostTalesKeyPress press) {
        if (front.content instanceof ChatMenu) {
            String command = this.menus.keyTyped(front, press);
            syncChatIdentity();
            if (command != null) {
                sendCommand(command);
            }
            return true;
        }
        if ((press.key == Keyboard.KEY_RETURN
                || press.key == Keyboard.KEY_NUMPADENTER)
                && front.content instanceof ChatPickerPanel) {
            ChatPickerPanel picker = (ChatPickerPanel)front.content;
            ChatPickerPanel.Entry found = picker.firstFound();
            if (found != null) {
                choosePickerEntry(picker, found);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyTyped(LostTalesKeyPress press) {
        int keyCode = press.key;
        // Ctrl+Tab walks every window's tabs, Ctrl+Left/Right the
        // selected window's, and Ctrl+W closes the selected one: none of
        // them clashes with autocomplete, so all work with text in the
        // field (drafts belong to their tabs).
        if (press.isCommand(Keyboard.KEY_TAB)) {
            this.tabActions.selectChannel(
                    ClientChatChannelState.cycleAll(press.shift));
            return true;
        }
        if (press.isCommand(Keyboard.KEY_RIGHT)) {
            this.tabActions.selectChannel(ClientChatChannelState.cycle());
            return true;
        }
        if (press.isCommand(Keyboard.KEY_LEFT)) {
            this.tabActions.selectChannel(ClientChatChannelState.cycleBack());
            return true;
        }
        // Ctrl+1 to Ctrl+8 pick the selected window's tabs by place and
        // Ctrl+9 its last, as a browser's do; the digits are the main
        // row's, which sit together in the keyboard's own numbering.
        if (press.command && keyCode >= Keyboard.KEY_1
                && keyCode <= Keyboard.KEY_9) {
            this.tabActions.selectChannel(ClientChatChannelState.selectOrdinal(
                    keyCode - Keyboard.KEY_1 + 1));
            return true;
        }
        if (press.isCommand(Keyboard.KEY_W)) {
            // The page in front of the keys is the tab Ctrl+W means.
            if (this.screen.focusedPage() != null) {
                this.screen.closeTab(this.screen.focusedPage());
            } else {
                this.tabActions.closeMarkedOrActiveTabs();
            }
            return true;
        }
        if (this.completion.handleSuggestionKey(keyCode)) {
            return true;
        }
        // Up and Down walk what was sent from the selected tab, and from
        // it alone: each tab keeps its own history. Handled here, never
        // by vanilla, whose single history mixes every tab's lines.
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            String recalled = ClientChatChannelState.recallSent(
                    ClientChatChannelState.getSelected(),
                    keyCode == Keyboard.KEY_UP ? -1 : 1,
                    this.inputField.getText());
            if (recalled != null) {
                this.inputField.setText(recalled);
                this.inputField.setCursorPositionEnd();
                enforceLimit();
            }
            return true;
        }
        // Tab and the arrows walk the tabs of the window being typed
        // in. All three need an empty field: with text in it the arrows
        // belong to the caret, as they do in any text field.
        if (this.inputField.getText().length() == 0
                && (keyCode == Keyboard.KEY_TAB
                        || keyCode == Keyboard.KEY_RIGHT)) {
            ClientChatChannelState.cycle();
            this.tabActions.syncSelection();
            return true;
        }
        if (this.inputField.getText().length() == 0
                && keyCode == Keyboard.KEY_LEFT) {
            ClientChatChannelState.cycleBack();
            this.tabActions.syncSelection();
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            submitInput();
            return true;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            // With text in the field Tab is command completion. Handled
            // here, never by vanilla: its completion would print the
            // candidate list as an untracked chat line, which files as
            // console output.
            this.completion.completeInput();
            return true;
        }
        if (refusesCharacter(press)) {
            return true;
        }
        this.screen.vanillaKeyTyped(press.character, keyCode);
        maybeSpaceTypedShortcode(press.character);
        enforceLimit();
        this.completion.refreshAfterTyping();
        return true;
    }

    /* ---- The pointer ---- */

    @Override
    public WindowHover hoverEmpty(double x, double y) {
        return emptyStateContains(x, y)
                ? new ChatHover(ChatHover.Kind.EMPTY_PLUS) : null;
    }

    /** The completion lists, which hang from the bar over everything. */
    @Override
    public WindowHover hoverOverAll(double x, double y) {
        if (!hasField()) {
            return null;
        }
        double barX = x - this.bar.fractionX();
        double barY = y - this.bar.fractionY() - this.bar.entranceOffset();
        ChatInputCompletion.Slot slot = this.completion.slotAt(barX, barY,
                this.bar.inputAnchor(), this.inputField.xPosition);
        if (slot == null) {
            return null;
        }
        ChatHover hover = new ChatHover(slot.row >= 0
                ? ChatHover.Kind.SUGGESTION : ChatHover.Kind.SUGGESTIONS);
        hover.suggestion = slot;
        return hover;
    }

    /** Another window's bar strip, then the live bar's own controls. */
    @Override
    public WindowHover hoverUnderRows(double x, double y) {
        double barX = x - this.bar.fractionX();
        double barY = y - this.bar.fractionY() - this.bar.entranceOffset();
        int barRight = this.bar.inputBarRight();
        // With only pages shown there is no bar, and nothing of one answers.
        boolean liveBar = hasField();
        ChatFrame otherBar = otherBarAt(x, barY);
        if (otherBar != null) {
            ChatHover hover = new ChatHover(ChatHover.Kind.OTHER_BAR);
            hover.frame = otherBar;
            return hover;
        }
        if (liveBar && this.bar.isInsideCharacterButton(barX, barY)) {
            return new ChatHover(ChatHover.Kind.CHARACTER_BUTTON);
        }
        if (liveBar && this.bar.isInsideIndicator(barX, barY, barRight)) {
            return new ChatHover(ChatHover.Kind.INDICATOR);
        }
        if (liveBar && this.bar.isInsideSendButton(barX, barY, barRight)) {
            return new ChatHover(ChatHover.Kind.SEND_BUTTON);
        }
        if (liveBar && this.bar.isInsideToolbarToggle(barX, barY,
                barRight)) {
            return new ChatHover(ChatHover.Kind.TOOLBAR_TOGGLE);
        }
        ChatPickerPanel button = !liveBar ? null
                : this.bar.pickerButtonAt(barX, barY, barRight);
        if (button != null) {
            ChatHover hover = new ChatHover(ChatHover.Kind.PICKER_BUTTON);
            hover.picker = button;
            return hover;
        }
        return null;
    }

    /**
     * A conversation's own controls and lines: the jump pill, the reply
     * chip, a message's toolbar, the member list, a scrollbar, a run of
     * the lines, and the window itself.
     */
    @Override
    public WindowHover hoverInWindows(double x, double y) {
        List<ChatFrame> frames = ChatFrame.drawn();
        for (int index = frames.size() - 1; index >= 0; index--) {
            ChatFrame frame = frames.get(index);
            if (frame.jumpPillContains(x, y)) {
                ChatHover hover = new ChatHover(ChatHover.Kind.JUMP_PILL);
                hover.frame = frame;
                return hover;
            }
            if (frame.contains(x, y)) {
                break;
            }
        }
        if (this.composer.chipContains(x, y)) {
            return new ChatHover(ChatHover.Kind.REPLY_CHIP);
        }
        for (int index = frames.size() - 1; index >= 0; index--) {
            ChatFrame frame = frames.get(index);
            int kind = frame.toolbarKindAt(x, y);
            if (kind >= 0) {
                ChatHover hover = new ChatHover(
                        ChatHover.Kind.MESSAGE_TOOLBAR);
                hover.frame = frame;
                hover.toolbarKind = kind;
                return hover;
            }
            if (frame.contains(x, y)) {
                break;
            }
        }
        // A window's member list, where it stands: its left edge resizes
        // it — while the list stands whole in an unlocked window — a
        // member's row opens their card, and the list around them
        // answers nothing.
        ChatFrame listed = ChatFrame.drawnAt(x, y);
        if (listed != null && listed.membersShare() >= 1.0F
                && ChatMemberList.edgeContains(listed.members, x, y)) {
            Window listWindow = WindowLayout.window(listed.windowId);
            if (listWindow != null && !listWindow.isLocked()) {
                ChatHover hover = new ChatHover(
                        ChatHover.Kind.MEMBER_LIST_EDGE);
                hover.frame = listed;
                hover.window = listWindow;
                return hover;
            }
        }
        if (listed != null && ChatMemberList.contains(listed.members, x, y)) {
            ChatHover hover = new ChatHover(ChatHover.Kind.MEMBER_LIST);
            hover.frame = listed;
            hover.member = ChatMemberList.memberAt(listed.members, x, y);
            hover.acts = hover.member != null;
            return hover;
        }
        for (int index = frames.size() - 1; index >= 0; index--) {
            ChatFrame frame = frames.get(index);
            if (frame.scrollbarContains(x, y)) {
                ChatHover hover = new ChatHover(ChatHover.Kind.SCROLLBAR);
                hover.frame = frame;
                return hover;
            }
            if (frame.contains(x, y)) {
                break;
            }
        }
        // A window that is not the one being typed in answers to a press
        // by becoming it, so all of it acts; the one being typed in
        // answers by cycling the stack, where another lies under it.
        ChatFrame under = ChatFrame.drawnAt(x, y);
        boolean focuses = under != null && bringsForward(under);
        boolean cycles = under != null && under == this.bar.activeFrame()
                && WindowScreen.cycleTargetAt(x, y) != null;
        LostTalesChatOverlayRenderer.Hit hit =
                LostTalesChatOverlayRenderer.hitAt(this.mc, (float)x, (float)y);
        if (hit != null) {
            ChatHover hover = new ChatHover(ChatHover.Kind.LINE);
            hover.line = hit;
            hover.frame = hit.band != null ? hit.band.frame : under;
            hover.person = LostTalesChatHoverCard.locate(this.mc, hit);
            hover.acts = hover.person != null || focuses || cycles
                    || ChatInteractions.isClick(ChatInteractions.actionOf(
                            hit.component, this.mc.gameSettings.chatLinks));
            return hover;
        }
        if (under != null) {
            ChatHover hover = new ChatHover(ChatHover.Kind.WINDOW);
            hover.frame = under;
            hover.acts = focuses || cycles;
            // A delivery mark is part of its window and answers a press
            // as the window does; resting on one reads it out.
            hover.markLineId = under.markLineAt(x, y);
            return hover;
        }
        return null;
    }

    /**
     * The floating controls light from the frame's one answer, and so
     * does a member list's row; a toolbar's menu button stays lit while
     * the menu it opened is out.
     */
    @Override
    public void hovered(WindowHover hover) {
        ChatHover chat = ChatHover.of(hover);
        boolean onToolbar = chat != null
                && chat.is(ChatHover.Kind.MESSAGE_TOOLBAR);
        ChatFrame.noteHoveredControls(
                onToolbar ? chat.chatFrame() : this.menus.toolbarMenuFrame(),
                onToolbar ? chat.toolbarKind
                        : LostTalesChatOverlayRenderer.TOOLBAR_MORE,
                chat != null && chat.is(ChatHover.Kind.JUMP_PILL)
                        ? chat.chatFrame() : null);
        for (ChatFrame frame : ChatFrame.drawn()) {
            frame.members.hovered = chat != null
                    && chat.is(ChatHover.Kind.MEMBER_LIST)
                    && chat.frame == frame ? chat.member : null;
        }
    }

    /** The words beside the pointer for what of the chat it rests on. */
    @Override
    public String tipFor(WindowHover hover) {
        ChatHover chat = ChatHover.of(hover);
        if (chat == null) {
            return null;
        }
        switch (chat.chatKind) {
            case MENU:
                // A row the menu keeps in its place but cannot take says
                // why.
                return chat.menuTip;
            case EMPTY_PLUS:
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.tab.restore");
            case REPLY_CHIP:
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.message.cancel_reply");
            case MESSAGE_TOOLBAR: {
                // A control that cannot be taken on this message says why.
                String why = chat.frame == null ? ""
                        : chat.chatFrame().toolbarWhy(chat.toolbarKind);
                return why.length() > 0 ? why
                        : StatCollector.translateToLocal(
                                toolbarLabelKey(chat.toolbarKind));
            }
            case LINE:
                // The button beside a message's reactions says what the
                // toolbar's own React says.
                return chat.line != null
                        && ChatReactionMarker.isAddButton(chat.line.component)
                        ? StatCollector.translateToLocal(
                                "gui.losttales.chat.message.react")
                        : "";
            case WINDOW:
                return chat.markLineId != 0 ? markTip(chat.markLineId) : "";
            case MEMBER_LIST:
                // A list narrowed to its heads shows no name at all; the
                // row says whose head it is as a tab says its name.
                return chat.member != null && chat.frame != null
                        && !chat.chatFrame().members.namesShown
                        ? chat.member.getName() : "";
            case TOOLBAR_TOGGLE:
                return StatCollector.translateToLocal(
                        ChatLayout.isToolbarCollapsed()
                                ? "gui.losttales.chat.toolbar.expand"
                                : "gui.losttales.chat.toolbar.collapse");
            default:
                // The head button's hover shows the chosen identity's card
                // instead of words.
                return "";
        }
    }

    /**
     * The message the frame shades: the one under the pointer while the
     * pointer is on the lines rather than on anything drawn above them,
     * else the one a message menu is about while that menu stands, so
     * what it acts on stays in sight.
     */
    private int shadedLine(double x, double y) {
        WindowHover hover = this.screen.hover();
        ChatHover chat = ChatHover.of(hover);
        if (chat == null) {
            if (!hover.is(WindowHover.Kind.NONE)) {
                return this.menus.messageMenuChatLineId();
            }
            int line = hoveredMessageLine((float)x, (float)y);
            return line != 0 ? line : this.menus.messageMenuChatLineId();
        }
        switch (chat.chatKind) {
            case MESSAGE_TOOLBAR:
                // A toolbar's buttons reach past its message's row; on
                // them the message stays the hovered one.
                if (chat.frame != null
                        && !this.screen.gestures().isDragging()) {
                    return chat.chatFrame().toolbarChatLineId;
                }
                return hoveredMessageLine((float)x, (float)y);
            case LINE:
            case WINDOW:
            case SCROLLBAR:
            case JUMP_PILL: {
                int line = hoveredMessageLine((float)x, (float)y);
                return line != 0 ? line : this.menus.messageMenuChatLineId();
            }
            default:
                return this.menus.messageMenuChatLineId();
        }
    }

    /** Whether the pointer is on one of the bar's own controls. */
    private static boolean isBarControl(WindowHover hover) {
        return ChatHover.is(hover, ChatHover.Kind.CHARACTER_BUTTON)
                || ChatHover.is(hover, ChatHover.Kind.INDICATOR)
                || ChatHover.is(hover, ChatHover.Kind.SEND_BUTTON)
                || ChatHover.is(hover, ChatHover.Kind.TOOLBAR_TOGGLE);
    }

    @Override
    public boolean pressEmpty(WindowHover press, int mouseX, int mouseY,
                              int button) {
        // The + is the whole of the screen's furniture here, a switch
        // like every +.
        if (button == 0 && ChatHover.is(press, ChatHover.Kind.EMPTY_PLUS)) {
            this.menus.toggleChannelMenu(null, emptyPlusAnchor(), null);
        }
        return true;
    }

    /**
     * A press on a menu or a picker, which has just come in front: a
     * menu's row is taken, a picker's cell chosen, a field's list row
     * taken, a menu's field given the caret.
     */
    @Override
    public boolean pressSubWindow(WindowHover hover, double x, double y,
                                  int button) {
        ChatHover press = ChatHover.of(hover);
        if (press == null) {
            return false;
        }
        switch (press.chatKind) {
            case MENU_ENTRY:
                if (button == 0) {
                    String command = this.menus.take(press.subWindow,
                            press.menuEntry);
                    syncChatIdentity();
                    if (command != null) {
                        sendCommand(command);
                    }
                } else if (button == 1) {
                    this.menus.takeBack(press.subWindow, press.menuEntry);
                }
                return true;
            case PICKER_CELL:
            case PICKER_LABEL:
            case PICKER:
                clickPickerWindow(press, x, y, button);
                return true;
            case FIELD_SUGGESTION:
                if (button == 0 && press.fieldSuggestion >= 0
                        && press.subWindow.content instanceof ChatMenu) {
                    ((ChatMenu)press.subWindow.content).takeListRow(
                            press.fieldSuggestion);
                }
                return true;
            case MENU:
                // A press on a menu's field puts its caret there.
                if (button == 0 && press.subWindow.content instanceof ChatMenu) {
                    ((ChatMenu)press.subWindow.content).pressField(
                            x - press.subWindow.fractionX,
                            y - press.subWindow.fractionY);
                }
                return true;
            default:
                return false;
        }
    }

    /** A press on the bar's controls, a completion list, or a member list. */
    @Override
    public boolean pressControl(WindowHover hover, double x, double y,
                                int mouseX, int mouseY, int button) {
        ChatHover press = ChatHover.of(hover);
        if (press == null) {
            return false;
        }
        switch (press.chatKind) {
            case MEMBER_LIST_EDGE:
                if (button == 0) {
                    ChatWindowDrags.armMembersResize(this.screen.gestures(),
                            this.tabActions, press.chatFrame(), press.window,
                            WindowPlacement.preciseMouseX(this.mc,
                                    this.screen.width));
                }
                return true;
            case MEMBER_LIST:
                // A member's row answers as their name in a message does:
                // a click opens their card, a right-click their menu.
                if (press.member != null) {
                    LostTalesChatHoverCard.Target target =
                            LostTalesChatHoverCard.forMember(this.mc,
                                    press.member);
                    if (button == 0) {
                        openCard(target, mouseX, mouseY);
                    } else if (button == 1) {
                        this.menus.openPersonMenu(target, mouseX, mouseY);
                    }
                }
                return true;
            case SUGGESTION:
                if (button == 0) {
                    this.completion.accept(press.suggestion);
                }
                return true;
            case SUGGESTIONS:
                return true;
            case OTHER_BAR:
                if (button == 0) {
                    focusBarOf(press.chatFrame());
                }
                return true;
            case CHARACTER_BUTTON:
                // A switch: pressed with its menu out, it puts it away.
                if (button == 0) {
                    this.menus.toggleCharacterMenu(characterButtonAnchor());
                    this.screen.syncTypingFocus();
                }
                return true;
            case INDICATOR:
                // A left click walks the window's tabs forward and a
                // right click back, as Ctrl+Right and Ctrl+Left do.
                if (button == 0) {
                    this.tabActions.selectChannel(
                            ClientChatChannelState.cycle());
                } else if (button == 1) {
                    this.tabActions.selectChannel(
                            ClientChatChannelState.cycleBack());
                }
                return true;
            case SEND_BUTTON:
                if (button == 0) {
                    submitInput();
                }
                return true;
            case TOOLBAR_TOGGLE:
                if (button == 0) {
                    // Folding the inserts folds their buttons; their
                    // windows stay where they are.
                    ChatLayout.setToolbarCollapsed(
                            !ChatLayout.isToolbarCollapsed());
                }
                return true;
            case PICKER_BUTTON:
                if (button == 0) {
                    togglePicker(press.picker);
                }
                return true;
            default:
                return false;
        }
    }

    /**
     * A press on a conversation: its own controls, a person's or a
     * message's menu on a right-click, and on a left one the window it
     * lands in comes forward and takes the keys, a run of the lines
     * answers, and a window already in front cycles the stack.
     */
    @Override
    public void pressWindows(WindowHover hover, double x, double y,
                             int mouseX, int mouseY, int button) {
        ChatHover press = ChatHover.of(hover);
        int adjustedMouseY = mouseY - Math.round(this.bar.entranceOffset());
        if (button == 0 && press != null) {
            switch (press.chatKind) {
                case JUMP_PILL:
                    ClientChatChannelViews.scrollHome(press.chatFrame().view);
                    return;
                case REPLY_CHIP:
                    this.composer.cancelComposing(this.inputField);
                    return;
                case MESSAGE_TOOLBAR:
                    clickMessageToolbar(press);
                    return;
                case SCROLLBAR:
                    if (ChatWindowDrags.grabScrollbar(this.screen.gestures(),
                            x, y)) {
                        return;
                    }
                    break;
                default:
                    break;
            }
        }
        // A right-click on a person — the sender's identity span or a
        // mention, wherever the card shows — opens that person's own
        // menu: message them, ignore them. Anywhere else on a line it
        // opens the message's menu: reply, copy, edit, delete.
        if (button == 1) {
            LostTalesChatHoverCard.Found person = press != null
                    && press.is(ChatHover.Kind.LINE) ? press.person : null;
            if (person != null) {
                if (this.menus.openPersonMenu(person.target, mouseX, mouseY)) {
                    return;
                }
            } else if (this.menus.openMessageMenu(mouseX, mouseY)) {
                return;
            }
        }
        if (button == 0) {
            ChatFrame frame = ChatFrame.drawnAt(x, y);
            Window window = frame == null ? null
                    : WindowLayout.window(frame.windowId);
            // Typing follows the pointer: a press anywhere in a window —
            // its messages, its strip, its bar — moves the input there,
            // the way clicking a window focuses it anywhere else. The
            // messages themselves do not move the window: only its strip
            // and its grip do, so a click on a line stays a click.
            // Whether this press lands on the window that was already
            // both in front here and the one being typed into — read
            // before the raise below, which would make it so anyway.
            boolean alreadyInFront = frame != null
                    && frame == this.bar.activeFrame();
            if (window != null) {
                WindowLayout.raise(window.getId());
                this.tabActions.selectWindow(window);
            }
            if (clickLines(press, mouseX, adjustedMouseY, button)) {
                return;
            }
            // Nothing in the window answered, and the window was already
            // the one in front: the press cycles the stack instead.
            if (alreadyInFront) {
                this.screen.cycleWindowsAt(x, y);
            }
            return;
        }
        clickLines(press, mouseX, adjustedMouseY, button);
    }

    /**
     * Component clicks on the lines, then the input field's own click.
     * Answers whether something on the lines took the press.
     */
    private boolean clickLines(ChatHover press, int mouseX,
                               int adjustedMouseY, int button) {
        // The press lands on the run the hover found at the pointer, so
        // what was lit is what answers. The quote a reply opens with
        // goes first: it is the one click that acts on the window rather
        // than on what it points at, and it answers whatever the
        // chat-links option says.
        if (button == 0 && press != null && press.is(ChatHover.Kind.LINE)) {
            if (jumpToQuotedMessage(press.line)
                    || handleComponentClick(press.line)) {
                return true;
            }
        }
        // GuiChat's own component handling relies on GuiNewChat's 9px hit
        // testing, which does not match this chat's taller rows
        // (LostTalesChatOverlayRenderer.LINE_HEIGHT); only the input
        // field needs the vanilla click path.
        this.inputField.mouseClicked(mouseX, adjustedMouseY, button);
        return false;
    }

    /**
     * The wheel over a member list scrolls it on its own, a line's step
     * at a time; anywhere else the history of the window under the
     * pointer scrolls, else the one being typed into's.
     */
    @Override
    public boolean wheel(WindowHover under, double x, double y, int lines) {
        int wheelPixels = WheelStep.pixels(lines,
                LostTalesChatOverlayRenderer.LINE_HEIGHT);
        ChatHover chat = ChatHover.of(under);
        if (chat != null && chat.is(ChatHover.Kind.MEMBER_LIST)
                && chat.frame != null) {
            ChatMemberList.scrollBy(chat.chatFrame().members, -wheelPixels);
            return true;
        }
        // The game's own scroll offset is unused; the visible history
        // scrolls per channel view instead.
        ChatFrame frame = ChatFrame.drawnAt(x, y);
        if (frame == null) {
            frame = this.bar.activeFrame();
        }
        if (frame != null) {
            scrollHistory(frame, wheelPixels);
        }
        return true;
    }

    /* ---- A frame ---- */

    @Override
    public void beginFrame() {
        ClientChatChannelState.ensureAvailable();
        this.tabActions.syncSelection();
        this.bar.refreshPickers();
    }

    @Override
    public void beforeHover() {
        // A reaction the message menu asked for opens the emoji picker
        // on that message, whichever way the menu entry was chosen.
        long reactTo = this.menus.takeReactionTarget();
        if (reactTo != ChatMessageIds.NONE) {
            openReactionPicker(reactTo);
        }
        // While a window's edge is dragged its history follows the resize
        // rigidly instead of gliding after it.
        ClientChatChannelViews.setScrollEasingSuppressed(
                this.screen.gestures().isResizing());
    }

    @Override
    public void beforeWindows(double pointerX, double pointerY) {
        ChatFrame live = this.bar.activeFrame();
        this.liveWindowId = live == null ? null : live.windowId;
        ChatHover chat = ChatHover.of(this.screen.hover());
        LostTalesChatPresentation.beginFrame();
        LostTalesChatPresentation.setHoveredLine(shadedLine(pointerX,
                pointerY));
        boolean onLine = chat != null && chat.is(ChatHover.Kind.LINE);
        LostTalesChatOverlayRenderer.Hit line = onLine ? chat.line : null;
        LostTalesChatHoverCard.Found person = onLine ? chat.person : null;
        LostTalesChatPresentation.setHoveredComponent(
                line == null ? null : line.line,
                line == null ? -1 : line.index,
                line == null ? null : line.component);
        LostTalesChatPresentation.setHoveredSenderRow(
                person != null && person.sender ? person.row : null);
        ChatWindowDrags.markScrollbarsWanted(this.screen.gestures(),
                pointerX, pointerY);
    }

    /**
     * A conversation's history, drawn here rather than in the HUD pass,
     * so the open chat lies above every HUD element and a front window
     * covers the whole of one behind it.
     */
    @Override
    public boolean drawWindow(Window window,
                              LostTalesGuiAnimationSample shown) {
        LostTalesChatOverlayRenderer.drawWindowForScreen(this.mc, window,
                this.screen.width, this.screen.height, shown);
        return true;
    }

    /** A search over the window reads its lines, and lands on a new match. */
    @Override
    public void searchWindow(Window window, WindowFrame frame) {
        if (WindowSearch.isOpenOn(window.getId())) {
            landSearch((ChatFrame)frame, ChatSearch.scan(this.mc,
                    (ChatFrame)frame));
        }
    }

    /**
     * Under the lines: who is typing, the bottom rule over the shade, the
     * frame's edges down to the bar, and — for every window but the one
     * typed in — a resting bar. The live bar is drawn once, over every
     * window, so its pickers and lists stand over them all.
     */
    @Override
    public void drawWindowFoot(Window window, WindowFrame frame,
                               LostTalesGuiAnimationSample shown,
                               int mouseX, int mouseY) {
        ChatFrame chatFrame = (ChatFrame)frame;
        drawTypingLine(window, chatFrame, shown, mouseX, mouseY);
        WindowDrawing.drawBottomRule(this.mc, frame, shown);
        WindowDrawing.drawFrameEdges(this.mc, frame, shown);
        if (!window.getId().equals(this.liveWindowId)) {
            this.bar.drawRestingBar(chatFrame, window);
        }
    }

    @Override
    public void afterWindows() {
        landPendingJump();
        // The field follows the active window's bar as just drawn.
        this.bar.updateInputBounds();
    }

    /**
     * The chat's sub-windows open as the screen last closed come back
     * where they stood: pickers whose buttons the bar still offers, the
     * Reactions window while the chat's emoji are on, every card, and
     * every menu whose subject still stands.
     */
    @Override
    public boolean reopen(SubWindowPlaces.Reopening open) {
        if (open.state instanceof ChatMenu) {
            this.menus.restore(open);
            return true;
        }
        if (open.kind == SubWindowKind.CARD) {
            if (open.state instanceof LostTalesChatHoverCard.Target) {
                this.screen.subWindows().reopen(open, new ChatPersonCard(
                        (LostTalesChatHoverCard.Target)open.state));
            }
            return true;
        }
        if (open.kind == SubWindowKind.REACTIONS) {
            if (open.state instanceof Long
                    && LostTalesConfig.enableChatEmojis) {
                this.screen.subWindows().reopen(open,
                        this.bar.reactionPicker());
                this.bar.reactionPicker().aimAt(
                        ((Long)open.state).longValue());
            }
            return true;
        }
        ChatPickerPanel picker = this.bar.pickerOf(open.kind);
        if (picker == null) {
            return false;
        }
        if (this.bar.isPickerShown(picker)) {
            this.screen.subWindows().reopen(open, picker);
        }
        return true;
    }

    /**
     * What stands over every window, under the sub-windows: with nothing
     * open, what the screen shows instead of a bar with no channel
     * behind it; with only pages, the notice; else the live bar.
     */
    @Override
    public void drawUnderSubWindows(boolean empty, boolean typing,
                                    double pointerX, double pointerY) {
        this.menus.refresh();
        WindowHover hover = this.screen.hover();
        if (empty) {
            boolean onPlus = ChatHover.is(hover, ChatHover.Kind.EMPTY_PLUS);
            drawEmptyState(onPlus ? pointerX : WindowHover.AWAY,
                    onPlus ? pointerY : WindowHover.AWAY);
            this.bar.drawNotice();
            return;
        }
        if (!typing) {
            // Every window shows a page: there is no bar to type into.
            this.bar.drawNotice();
            return;
        }
        int barRight = this.bar.inputBarRight();
        float entrance = this.bar.entranceOffset();
        // The bar group's own space: the pointer less the bar's own
        // translation, its fraction and its entrance included. Each
        // control is handed the pointer only while it is what the
        // pointer is on.
        double barX = pointerX - this.bar.fractionX();
        double barY = pointerY - this.bar.fractionY() - entrance;
        boolean onControl = isBarControl(hover);
        boolean onPicker = ChatHover.is(hover, ChatHover.Kind.PICKER_BUTTON);
        double controlX = onControl ? barX : WindowHover.AWAY;
        double controlY = onControl ? barY : WindowHover.AWAY;
        GL11.glPushMatrix();
        // The live bar fades in with a window still appearing: one just
        // made from tabs carried out of their row.
        ChatFrame barFrame = this.bar.activeFrame();
        ChatInputBar.beginFade(barFrame == null ? 1.0F
                : barFrame.shownShare());
        try {
            GL11.glTranslatef(this.bar.fractionX(),
                    this.bar.fractionY() + entrance, 0.0F);
            this.bar.drawBar(barRight);
            this.bar.drawCharacterSelectionButton(controlX, controlY,
                    this.menus.isOpen(SubWindowKind.CHARACTERS));
            this.bar.drawIndicator(barRight, controlX, controlY);
            this.bar.drawToolbarToggle(barRight, controlX, controlY);
            this.bar.drawPickerButtons(barRight,
                    onPicker ? barX : WindowHover.AWAY,
                    onPicker ? barY : WindowHover.AWAY);
            this.bar.drawDividers(barRight);
            this.bar.drawSendButton(barRight, controlX, controlY);
            this.bar.drawCounter(barRight);
        } finally {
            ChatInputBar.endFade();
            GL11.glPopMatrix();
        }
    }

    /**
     * Over the sub-windows: the completion lists following the caret, in
     * the bar's own space, a line's hover card, the notice, and the head
     * button's card.
     */
    @Override
    public void drawOverSubWindows(boolean typing, double pointerX,
                                   double pointerY, int mouseX, int mouseY) {
        if (!typing) {
            return;
        }
        WindowHover hover = this.screen.hover();
        float entrance = this.bar.entranceOffset();
        double barX = pointerX - this.bar.fractionX();
        double barY = pointerY - this.bar.fractionY() - entrance;
        boolean onList = ChatHover.is(hover, ChatHover.Kind.SUGGESTION)
                || ChatHover.is(hover, ChatHover.Kind.SUGGESTIONS);
        ChatFrame barFrame = this.bar.activeFrame();
        GL11.glPushMatrix();
        ChatInputBar.beginFade(barFrame == null ? 1.0F
                : barFrame.shownShare());
        try {
            GL11.glTranslatef(this.bar.fractionX(),
                    this.bar.fractionY() + entrance, 0.0F);
            this.completion.draw(this.bar.inputAnchor(),
                    this.inputField.xPosition,
                    onList ? barX : WindowHover.AWAY,
                    onList ? barY : WindowHover.AWAY);
        } finally {
            ChatInputBar.endFade();
            GL11.glPopMatrix();
        }
        ChatHover chat = ChatHover.of(hover);
        drawChatLineHover(chat != null && chat.is(ChatHover.Kind.LINE)
                ? chat.line : null, mouseX, mouseY);
        this.bar.drawNotice();
        // A person's or a role's card opens on a click, not under the
        // pointer; only the head button's hover shows one: the chosen
        // identity's own brief card, who the roleplaying channels speak
        // as right now, while the button's own menu is not out.
        if (ChatHover.is(hover, ChatHover.Kind.CHARACTER_BUTTON)
                && !this.menus.isOpen(SubWindowKind.CHARACTERS)) {
            LostTalesChatHoverCard.drawForIdentity(this.mc,
                    ClientChatChannelState.getSelected(), mouseX, mouseY,
                    this.screen.width, this.screen.height);
        }
    }

    /* ---- The game's chat screen underneath ---- */

    /**
     * The completion answer; the candidate list is filed under the tab
     * the request was typed in.
     */
    @Override
    public boolean serverCompletions(String[] completions) {
        this.completion.onServerCompletions(completions);
        return true;
    }

    /**
     * A line to send: a command goes to the server as the command it is,
     * a whisper verb opens its conversation, and a message goes to the
     * tab it was typed in.
     */
    @Override
    public boolean send(String text) {
        String message = text == null ? "" : text.trim();
        this.sent = true;
        ClientChatChannelState.setDraft("");
        // The tab the line was typed in, taken before a whisper command
        // moves the selection to the conversation it opens.
        ChatTab tab = ClientChatChannelState.getSelected();
        if (ChatInputRules.isCommand(message)) {
            // A command is recalled from the tab it was typed in, like
            // anything else typed there, and goes to the server as the
            // command it is. The console is brought forward by whatever
            // asked for the send, once it has emptied the field.
            ClientChatChannelState.recordSent(tab, message);
            if (ChatInputRules.isWhisperCommand(message)) {
                sendWhisperCommand(message);
                return true;
            }
            this.mc.thePlayer.sendChatMessage(message);
            return true;
        }
        if (message.length() == 0
                || !ChatMessageValidator.isValid(message)
                || !ClientChatChannelState.canSend(tab)) {
            return true;
        }
        // The history keeps the raw text, so recalling it gives back
        // exactly what was typed.
        ClientChatChannelState.recordSent(tab, message);
        this.outbox.sendMessage(tab, message);
        return true;
    }

    /** The answer to the question whether to open a link. */
    @Override
    public boolean confirmClicked(boolean result, int id) {
        if (id != 0) {
            return false;
        }
        if (result && this.clickedLinkUri != null) {
            browseTo(this.clickedLinkUri);
        }
        this.clickedLinkUri = null;
        this.mc.displayGuiScreen(this.screen);
        return true;
    }

    /**
     * The tabs of every window whose member list stands, each window's
     * tab in front first: the lists worth asking for before any is shown.
     */
    private static List<ChatTab> tabsShowingMembers() {
        List<ChatTab> fronts = new ArrayList<ChatTab>();
        List<ChatTab> rest = new ArrayList<ChatTab>();
        for (Window window : WindowLayout.windows()) {
            if (ChatLayout.isMembersHidden(window)) {
                continue;
            }
            ChatTab front = ChatTab.frontOf(window);
            if (front != null) {
                fronts.add(front);
            }
            for (WindowTab each : ChatFrame.visibleTabs(window)) {
                ChatTab tab = ChatTab.from(each);
                if (tab != null && !tab.equals(front)) {
                    rest.add(tab);
                }
            }
        }
        fronts.addAll(rest);
        return fronts;
    }

    /** A reply or edit belongs to the identity that started composing it. */
    private void syncChatIdentity() {
        String identity = ClientChatIdentities.viewIdentityKey();
        if (identity.equals(this.composingIdentityKey)) { return; }
        this.composingIdentityKey = identity;
        this.outbox.stopTyping();
        this.composer.cancelComposing(this.inputField);
        ClientChatIdentitySelection.update();
        this.tabActions.syncSelection();
    }

    /**
     * Who is typing into the window's front tab, in the gap between the
     * history and the bar: one to three names, or a count past that,
     * closed by the pulsing dots ({@link ChatTypingLine}). Nothing is
     * drawn while nobody is.
     */
    private void drawTypingLine(Window window, ChatFrame frame,
                                LostTalesGuiAnimationSample opening,
                                int mouseX, int mouseY) {
        if (this.composer.drawChip(this.fontRendererObj, window, frame,
                opening, mouseX, mouseY)
                || !LostTalesConfig.showChatTypingIndicators) {
            return;
        }
        ChatTab front = ChatTab.from(ChatFrame.activeTab(window,
                ChatFrame.visibleTabs(window)));
        List<String> names = ClientChatTypingState.namesTyping(front);
        if (names.isEmpty()) {
            return;
        }
        // The trailing strip belongs to the typing line only while the
        // view rests on the newest message; a view scrolled back hands
        // the strip to the history, and the line fades out with the
        // first turn of scroll rather than mixing into the older lines.
        float presence = 1.0F - (float)Math.min(1.0D,
                Math.max(0.0D, frame.renderedScrollLines));
        int alpha = Math.round(255.0F * opening.getOpacity() * presence);
        if (alpha < 4) {
            return;
        }
        // The line begins where the messages above it do, as the reply
        // chip that shares this row does, and rides their origin's exact
        // place in a moved matrix, so it slides with them as the
        // timestamp area goes.
        ChatTrailingStrip strip = ChatTrailingStrip.of(frame,
                this.fontRendererObj);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(strip.fractionX, strip.fractionY, 0.0F);
            // A bubble before the words, centred in the strip as a box of
            // its own, on the row a quote's and a message link's bubble
            // take, and the line itself in the asides' tone rather than
            // the message ivory.
            LostTalesUiSheet bubble = LostTalesUiSheet.SPEECH_BUBBLE;
            bubble.drawWithShadow(strip.x, strip.y + WindowStyle
                    .centredBoxTop(bubble.getHeight()), alpha);
            int textX = strip.x + bubble.getWidth() + TYPING_BUBBLE_GAP;
            ChatTypingLine.draw(this.fontRendererObj,
                    ChatTypingLine.words(names), textX, strip.y,
                    strip.room - (textX - strip.x), alpha, System.nanoTime());
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * Sends what is in the field — Enter and the send button both end
     * here. Vanilla closes the screen on Enter; here the screen stays
     * open for the next message and only Escape (or the player) closes
     * it, swapping back to the feed.
     */
    private void submitInput() {
        if (refuseUnsendableMessage()) {
            return;
        }
        String text = this.inputField.getText().trim();
        if (text.length() > 0) {
            // Taken before the send: a whisper verb moves the selection
            // to the conversation it opens.
            ChatTab typedIn = ClientChatChannelState.getSelected();
            if (ChatInputRules.isServerCommand(text)) {
                sendCommand(text);
            } else {
                send(text);
            }
            // Speaking moves the conversation on: the unread divider of
            // the tab spoken in has done its job, exactly as it does on
            // Discord.
            ClientChatChannelViews.dismissDivider(typedIn);
        }
        this.inputField.setText("");
        this.sent = false;
        ClientChatChannelState.setDraft("");
        ClientChatChannelState.endSentBrowse();
        this.outbox.stopTyping();
    }

    /**
     * A hand-typed closing colon that completes a canonical shortcode
     * gets the same trailing space every inserted token gets — from the
     * pickers, the completion lists and the emoji list alike — so the
     * next word stands apart from the emoji however it was put there.
     * Only the exact typed colon triggers this; nothing rewrites text
     * that is already standing.
     */
    private void maybeSpaceTypedShortcode(char typedChar) {
        if (typedChar != ':' || !LostTalesConfig.enableChatEmojis
                || isCommand()) {
            return;
        }
        if (ChatInputRules.shortcodeJustClosed(this.inputField.getText(),
                this.inputField.getCursorPosition())) {
            this.inputField.writeText(" ");
        }
    }

    /**
     * Sends a command the server answers, however it was asked for —
     * typed, clicked in a line, chosen from a menu. The command and
     * what it answers are shown in the tab in front — the answer as a
     * line of the Server's own — and the Server Console is told by
     * the server who ran what, and where. The tab in front stays in
     * front.
     */
    private void sendCommand(String command) {
        ChatTab typedIn = ClientChatChannelState.getSelected();
        LostTalesChatPresentation.expectCommandOutput(typedIn);
        LostTalesChatPresentation.echoCommand(typedIn, command);
        // The tab goes ahead of the command on the same connection, so
        // the console's entry about the command can say where it was
        // typed; the command itself still travels vanilla's own way.
        if (typedIn != null) {
            try {
                LostTalesNetworkHandler.CHANNEL.sendToServer(
                        new LostTalesChatCommandContextPacket(typedIn.id()));
            } catch (IllegalArgumentException unsendable) {
                // A tab id the packet cannot carry: the entry names no
                // tab, and the command goes out all the same.
            }
        }
        send(command);
    }

    /** Whether the field holds a command rather than a message. */
    private boolean isCommand() {
        return ChatInputRules.isCommand(this.inputField.getText());
    }

    /**
     * A character typed into a message already at the limit is refused
     * outright, so the counter's ceiling is a wall, not a warning.
     * Shortcuts, commands and a press over a selection pass.
     */
    private boolean refusesCharacter(LostTalesKeyPress press) {
        if (isCommand() || !press.types
                || !ChatAllowedCharacters.isAllowedCharacter(press.character)
                || this.inputField.getSelectedText().length() > 0) {
            return false;
        }
        return ChatInputRules.atMessageLimit(this.inputField.getText());
    }

    /**
     * Cuts a message that got past the limit anyway (a paste) back to
     * it, so the field never holds more than can be sent.
     */
    private void enforceLimit() {
        String text = this.inputField.getText();
        String trimmed = ChatInputRules.trimToLimit(text);
        if (!trimmed.equals(text)) {
            this.inputField.setText(trimmed);
            this.inputField.setCursorPositionEnd();
        }
    }

    /**
     * Vanilla closes the screen as soon as Enter is pressed, which would
     * discard a message that cannot go out; refusing here keeps the text so
     * the player can shorten it or switch channel. Commands keep their own
     * limits.
     */
    private boolean refuseUnsendableMessage() {
        String message = this.inputField.getText().trim();
        if (message.length() == 0 || ChatInputRules.isCommand(message)) {
            return false;
        }
        if (!ClientChatChannelState.canSend(
                ClientChatChannelState.getSelected())) {
            showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.send_refused"));
            return true;
        }
        if (ChatMessageValidator.isValid(message)) {
            return false;
        }
        showNotice(StatCollector.translateToLocalFormatted(
                "gui.losttales.chat.too_long",
                Integer.valueOf(ChatMessageValidator.visibleLength(message)),
                Integer.valueOf(ChatMessageValidator.MAX_CHARACTERS)));
        return true;
    }

    /**
     * {@code /msg}, {@code /tell} and {@code /w} are the chat's own: the
     * name opens (and selects) that whisper tab, and any text after it is
     * sent there as a whisper rather than as a vanilla command.
     */
    private void sendWhisperCommand(String command) {
        String[] parts = ChatInputRules.whisperParts(command);
        if (parts.length < 2 || parts[1].length() == 0) {
            showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.whisper.name_required"));
            return;
        }
        ChatTab tab = this.tabActions.openWhisperTab(parts[1]);
        if (tab != null) {
            this.outbox.sendWhisper(tab, parts.length > 2
                    ? parts[2].trim() : "");
        }
    }

    /**
     * Moves a window's history {@code pixels} of stack toward its older
     * lines, or back toward the newest for a negative distance: a fixed
     * distance the frame turns into rows, since the rows are not all one
     * height. The reach is the rows the window draws, not its message
     * lines: the two differ by the unread divider's own row and the
     * blank rows between runs.
     */
    private static void scrollHistory(ChatFrame frame, double pixels) {
        if (frame.view == null) {
            return;
        }
        int rows = frame.contentRows();
        double roomLines = frame.roomLines();
        double current = ClientChatChannelViews.getScroll(frame.view, rows,
                roomLines);
        ClientChatChannelViews.scrollTo(frame.view,
                frame.rowsAfterScrolling(current, pixels), rows, roomLines);
    }

    /** Lands on a match the search named, when it named one. */
    private static void landSearch(ChatFrame frame, int chatLineId) {
        if (chatLineId == 0) {
            return;
        }
        int index = firstRowOf(frame.lines, chatLineId);
        if (index >= 0) {
            landOn(frame, index, chatLineId);
        }
    }

    /** The words the message menu names a toolbar control by. */
    private static String toolbarLabelKey(int kind) {
        switch (kind) {
            case LostTalesChatOverlayRenderer.TOOLBAR_REACT:
                return "gui.losttales.chat.message.react";
            case LostTalesChatOverlayRenderer.TOOLBAR_REPLY:
                return "gui.losttales.chat.message.reply";
            case LostTalesChatOverlayRenderer.TOOLBAR_LINK:
                return "gui.losttales.chat.message.copy_link";
            case LostTalesChatOverlayRenderer.TOOLBAR_MORE:
                return "gui.losttales.chat.message.more";
            default:
                return "gui.losttales.chat.message.copy";
        }
    }

    /**
     * What a delivery mark says under the pointer: that Discord still has
     * the line to come, or never took it, and why.
     */
    private static String markTip(int chatLineId) {
        ChatDeliveryMark.State state =
                ClientChatDeliveryMarks.stateOf(chatLineId);
        if (state == ChatDeliveryMark.State.NONE) {
            return "";
        }
        String tip = StatCollector.translateToLocal(state.langKey());
        String why = ClientChatDeliveryMarks.reasonOf(chatLineId).langKey();
        return why.length() == 0 ? tip
                : tip + ": " + StatCollector.translateToLocal(why);
    }

    /**
     * What the screen shows with nothing open: one strip where the chat
     * would be, carrying a {@code +} that opens a channel, and a line
     * saying so. Placed and sized from the closed-chat feed's own box,
     * so it lands where the messages do at any resolution or GUI scale.
     */
    private void drawEmptyState(double mouseX, double mouseY) {
        WindowPlacement.Box box = ChatFeedPlacement.bounds(
                this.mc, this.screen.width, this.screen.height);
        int left = (int)Math.round(box.x);
        int right = left + box.width;
        int bottom = (int)Math.round(box.baseline());
        int top = bottom - EMPTY_STATE_HEIGHT;
        LostTalesChatOverlayRenderer.drawBackdropRow(left, top, right,
                bottom, LostTalesChatOverlayRenderer.backdropRowAlpha(
                        this.mc));
        LostTalesUiRules.drawRule(left, right, top, top + 1,
                0xFF);
        LostTalesUiRules.drawRule(left, right, bottom - 1,
                bottom, 0xFF);
        this.emptyPlusLeft = left + EMPTY_STATE_PADDING;
        this.emptyPlusTop = top + (EMPTY_STATE_HEIGHT
                - TabRow.END_CONTROL_SIZE) / 2;
        this.emptyPlusRight = this.emptyPlusLeft
                + TabRow.END_CONTROL_SIZE;
        this.emptyPlusBottom = this.emptyPlusTop
                + TabRow.END_CONTROL_SIZE;
        boolean hovered = emptyStateContains(mouseX, mouseY);
        // The rules and the band are built from filled quads, which
        // leave blending off behind them; everything drawn after one
        // turns it back on for itself.
        LostTalesUiInk.beginContent();
        LostTalesUiSheet plus = hovered
                ? LostTalesUiSheet.PLUS_HOVER : LostTalesUiSheet.PLUS;
        plus.drawWithShadow(this.emptyPlusLeft
                        + (TabRow.END_CONTROL_SIZE
                                - plus.getWidth()) / 2,
                this.emptyPlusTop + (TabRow.END_CONTROL_SIZE
                        - plus.getHeight()) / 2, 0xFF);
        int textX = this.emptyPlusRight + EMPTY_STATE_GAP;
        int room = Math.max(0, right - EMPTY_STATE_PADDING - textX);
        LostTalesUiInk.drawText(this.fontRendererObj,
                "§o" + this.fontRendererObj.trimStringToWidth(
                        StatCollector.translateToLocal(
                                "gui.losttales.chat.no_channels"), room),
                textX, top + LostTalesUiInk.centredStart(EMPTY_STATE_HEIGHT,
                        LostTalesUiInk.CAP_HEIGHT),
                LostTalesChatVisualStyle.asideRgb(), 0xFF);
        this.screen.regions().add(this.emptyPlusLeft, this.emptyPlusTop,
                this.emptyPlusRight, this.emptyPlusBottom);
    }

    /** Whether the point is on the empty state's + as drawn last frame. */
    private boolean emptyStateContains(double mouseX, double mouseY) {
        return LostTalesUiHitBox.contains(mouseX, mouseY, this.emptyPlusLeft,
                this.emptyPlusTop, this.emptyPlusRight - this.emptyPlusLeft,
                this.emptyPlusBottom - this.emptyPlusTop);
    }

    /** Whether a press on the window brings it forward: it is not the one being typed in. */
    private static boolean bringsForward(ChatFrame frame) {
        Window window = WindowLayout.window(frame.windowId);
        WindowTab front = window == null ? null
                : ChatFrame.activeTab(window,
                        ChatFrame.visibleTabs(window));
        return front != null
                && !front.equals(ClientChatChannelState.getSelected());
    }

    /**
     * A click on another window's resting bar moves the input to that
     * window: the bar drawn there answers nothing itself, and pressing
     * it asks for the live bar to come there, which is what it then
     * does.
     */
    private ChatFrame otherBarAt(double x, double barY) {
        ChatFrame active = this.bar.activeFrame();
        List<ChatFrame> frames = ChatFrame.drawn();
        for (int index = frames.size() - 1; index >= 0; index--) {
            ChatFrame frame = frames.get(index);
            if (frame == active || frame.page != null) {
                continue;
            }
            double top = frame.barTop();
            if (x >= frame.boxLeft && x < frame.boxRight && barY >= top
                    && barY < top + WindowPlacement.BAR_STRIP_HEIGHT) {
                return frame;
            }
        }
        return null;
    }

    /** Moves the input to the window whose bar strip was pressed. */
    private void focusBarOf(ChatFrame frame) {
        Window window = WindowLayout.window(frame.windowId);
        WindowTab front = window == null ? null
                : ChatFrame.activeTab(window,
                        ChatFrame.visibleTabs(window));
        if (front != null) {
            this.tabActions.selectChannel(front);
        }
    }

    /**
     * Hover cards for item, text, and achievement components, plus the
     * tooltips of shared items and markers, drawn after the popups so
     * they layer above everything in the stack. An item keeps the game's
     * own item tooltip, the one every inventory shows; text and
     * achievement cards are drawn as the chat's cards are, in the
     * palette, so they read beside the line rather than against it. The
     * hit position is the pointer's fractional coordinate; the tooltip
     * itself anchors on the whole-pixel one.
     */
    private void drawChatLineHover(LostTalesChatOverlayRenderer.Hit hovered,
                                   int drawMouseX, int drawMouseY) {
        if (hovered == null || ChatInteractions.actionOf(hovered.component,
                this.mc.gameSettings.chatLinks)
                == ChatInteractions.Action.PERSON) {
            // A name, a head, a mention or a role reads out nothing under
            // the pointer; a click opens its card.
            return;
        }
        ChatReactionMarker.Data chip =
                ChatReactionMarker.decode(hovered.component);
        if (chip != null) {
            drawReactionTooltip(chip, drawMouseX, drawMouseY);
            return;
        }
        ChatShowcaseMarker.Data share =
                ChatShowcaseMarker.decode(hovered.component);
        if (share != null) {
            drawShareTooltip(share, drawMouseX, drawMouseY);
            return;
        }
        if (hovered.component.getChatStyle().getChatHoverEvent() != null) {
            drawComponentHoverCard(hovered.component.getChatStyle()
                    .getChatHoverEvent(), drawMouseX, drawMouseY);
            GL11.glDisable(GL11.GL_LIGHTING);
        }
    }

    private void drawShareTooltip(ChatShowcaseMarker.Data share,
                                  int mouseX, int mouseY) {
        if (share.kind == ChatShareKind.ITEM) {
            ItemStack stack = ClientChatShowcaseStore.getItem(share.showcaseId);
            if (stack != null) {
                this.screen.drawItemTooltip(stack, mouseX, mouseY);
                GL11.glDisable(GL11.GL_LIGHTING);
            }
            return;
        }
        if (share.kind == ChatShareKind.QUEST) {
            ClientChatShowcaseStore.Quest quest =
                    ClientChatShowcaseStore.getQuest(share.showcaseId);
            if (quest == null) return;
            List<String> lines = new ArrayList<String>(6);
            lines.add(EnumChatFormatting.GOLD + quest.title);
            if (quest.category.length() > 0) {
                lines.add(EnumChatFormatting.ITALIC
                        + ClientQuestCatalog.categoryName(quest.category));
            }
            if (quest.objective.length() > 0) lines.add(quest.objective);
            if (quest.reward.length() > 0) {
                lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                        "gui.losttales.chat.quest.rewards", quest.reward));
            }
            lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(
                    quest.joinable ? "gui.losttales.chat.quest.join"
                            : "gui.losttales.chat.quest.view_only"));
            LostTalesChatHoverCard.drawTextCard(this.mc, lines, mouseX, mouseY,
                    this.screen.width, this.screen.height);
            GL11.glDisable(GL11.GL_LIGHTING);
            return;
        }
        ClientChatShowcaseStore.Marker marker =
                ClientChatShowcaseStore.getMarker(share.showcaseId);
        if (marker == null) {
            return;
        }
        List<String> lines = new ArrayList<String>(4);
        lines.add(marker.name);
        lines.add(EnumChatFormatting.ITALIC + StatCollector.translateToLocal(
                "gui.losttales.chat.marker.category"));
        lines.add("X " + Math.round(marker.x) + "   Z " + Math.round(marker.z));
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(
                "gui.losttales.chat.marker.open"));
        LostTalesChatHoverCard.drawTextCard(this.mc, lines, mouseX, mouseY,
                this.screen.width, this.screen.height);
        GL11.glDisable(GL11.GL_LIGHTING);
    }

    private void drawComponentHoverCard(HoverEvent hoverEvent,
                                        int mouseX, int mouseY) {
        if (hoverEvent.getAction() == HoverEvent.Action.SHOW_ITEM) {
            ItemStack stack = null;
            try {
                NBTBase nbt = JsonToNBT.func_150315_a(
                        hoverEvent.getValue().getUnformattedText());
                if (nbt instanceof NBTTagCompound) {
                    stack = ItemStack.loadItemStackFromNBT(
                            (NBTTagCompound)nbt);
                }
            } catch (NBTException ignored) {
            }
            if (stack != null) {
                this.screen.drawItemTooltip(stack, mouseX, mouseY);
            } else {
                LostTalesChatHoverCard.drawTextCard(this.mc, Collections.singletonList(
                        EnumChatFormatting.RED + "Invalid Item!"),
                        mouseX, mouseY, this.screen.width, this.screen.height);
            }
        } else if (hoverEvent.getAction() == HoverEvent.Action.SHOW_TEXT) {
            LostTalesChatHoverCard.drawTextCard(this.mc, Splitter.on("\n").splitToList(
                    hoverEvent.getValue().getFormattedText()),
                    mouseX, mouseY, this.screen.width, this.screen.height);
        } else if (hoverEvent.getAction()
                == HoverEvent.Action.SHOW_ACHIEVEMENT) {
            StatBase stat = StatList.func_151177_a(
                    hoverEvent.getValue().getUnformattedText());
            if (stat != null) {
                IChatComponent title = stat.func_150951_e();
                ChatComponentTranslation type =
                        new ChatComponentTranslation("stats.tooltip.type."
                                + (stat.isAchievement()
                                        ? "achievement" : "statistic"));
                type.getChatStyle().setItalic(Boolean.TRUE);
                String description = stat instanceof Achievement
                        ? ((Achievement)stat).getDescription() : null;
                ArrayList<String> lines = Lists.newArrayList(
                        title.getFormattedText(),
                        type.getFormattedText());
                if (description != null) {
                    @SuppressWarnings("unchecked")
                    List<String> wrapped = this.fontRendererObj
                            .listFormattedStringToWidth(description, 150);
                    lines.addAll(wrapped);
                }
                LostTalesChatHoverCard.drawTextCard(this.mc, lines, mouseX, mouseY,
                        this.screen.width, this.screen.height);
            } else {
                LostTalesChatHoverCard.drawTextCard(this.mc, Collections.singletonList(
                        EnumChatFormatting.RED + "Invalid statistic/achievement!"),
                        mouseX, mouseY, this.screen.width, this.screen.height);
            }
        }
    }

    /**
     * A press inside a picker's window: its own controls first, then a
     * cell inserts its token, and a right-click on an emoji or a marker
     * cell marks it as a favourite.
     */
    private void clickPickerWindow(ChatHover press, double x, double y,
                                   int button) {
        SubWindow window = press.subWindow;
        ChatPickerPanel picker = press.picker;
        LostTalesUiHitBox box = window.wholeContentBox();
        double pickerX = x - window.fractionX;
        double pickerY = y - window.fractionY;
        if (picker.mouseClicked(box, (int)Math.floor(pickerX),
                (int)Math.floor(pickerY), pickerX, pickerY, button)) {
            return;
        }
        ChatPickerPanel.Entry entry = press.pickerEntry;
        if (button == 0 && entry != null) {
            choosePickerEntry(picker, entry);
        } else if (button == 1 && picker instanceof ChatEmojiPicker) {
            ((ChatEmojiPicker)picker).toggleFavoriteAt(box, pickerX, pickerY);
        } else if (button == 1 && picker == this.bar.markerPicker()) {
            this.bar.markerPicker().toggleFavoriteAt(box, pickerX, pickerY);
        }
    }

    /**
     * A picker's cell chosen, by a click or by Enter in its search: the
     * Reactions window's pick is sent as a reaction to the message it is
     * aimed at and writes nothing, and any other pick is written into the
     * input, which then has the keys again. The window stays open either
     * way, for the next pick.
     */
    private void choosePickerEntry(ChatPickerPanel picker,
                                   ChatPickerPanel.Entry entry) {
        if (picker == this.bar.reactionPicker()) {
            long target = this.bar.reactionPicker().reactionTarget();
            if (target != ChatMessageIds.NONE) {
                ChatEmoji emoji = (ChatEmoji)entry.value;
                sendReaction(target, emoji, true);
                ChatEmojiUsageStore.recordUse(emoji);
            }
            return;
        }
        this.completion.insertToken(picker.insertionText(entry));
        picker.releaseKeys();
        this.inputField.setFocused(true);
    }

    /** A picker's button: its window opens where it last stood, or closes. */
    private void togglePicker(ChatPickerPanel picker) {
        this.screen.subWindows().toggle(this.bar.kindOf(picker), "", picker,
                typedWindowId(), this.bar.firstPickerBox(picker));
        this.screen.syncTypingFocus();
    }

    /**
     * Opens a person's card in a window of its own, beside the pointer
     * where a card always opened until the player placed one; a click on
     * the person whose card is open closes it again.
     */
    private void openCard(LostTalesChatHoverCard.Target target, int mouseX,
                          int mouseY) {
        if (target == null) {
            return;
        }
        ChatPersonCard card = new ChatPersonCard(target);
        int width = card.naturalWidth();
        int height = card.naturalHeight(width) + SubWindow.STRIP_HEIGHT;
        int x = LostTalesChatHoverCard.cardX(mouseX, width, this.screen.width);
        int y = LostTalesChatHoverCard.cardY(mouseY, height, this.screen.height);
        this.screen.subWindows().toggle(SubWindowKind.CARD, target.key(), card,
                windowIdAt(mouseX, mouseY),
                new LostTalesUiHitBox(x, y + SubWindow.STRIP_HEIGHT,
                        width, height - SubWindow.STRIP_HEIGHT));
    }

    /**
     * The hovered message's own controls: react to it, reply to it, copy
     * it or copy a link to it — the four the message's menu leads with —
     * or open that menu, hanging from the control. Each acts on the
     * message the toolbar was drawn for rather than on whatever lies
     * under the pointer now, by the id the draw recorded. A control that
     * cannot be taken on the message does nothing; its tip says why. The
     * menu control is a switch: a press with that message's menu out
     * puts it away.
     */
    private boolean clickMessageToolbar(ChatHover press) {
        ChatFrame frame = press.chatFrame();
        if (frame == null || press.toolbarKind < 0) {
            return false;
        }
        if (frame.toolbarWhy(press.toolbarKind).length() > 0) {
            return true;
        }
        int chatLineId = frame.toolbarChatLineId;
        String copied = null;
        switch (press.toolbarKind) {
            case LostTalesChatOverlayRenderer.TOOLBAR_REACT:
                openReactionPicker(ClientChatMessageIds.messageIdOf(chatLineId));
                break;
            case LostTalesChatOverlayRenderer.TOOLBAR_REPLY:
                replyToLine(frame, chatLineId);
                break;
            case LostTalesChatOverlayRenderer.TOOLBAR_LINK:
                copied = ChatScreenMenus.messageLinkFor(chatLineId);
                break;
            case LostTalesChatOverlayRenderer.TOOLBAR_MORE: {
                float cellLeft = frame.toolbarCellLeft(press.toolbarKind);
                this.menus.toggleToolbarMessageMenu(frame, chatLineId,
                        firstRowOf(frame.lines, chatLineId),
                        SubWindowAnchor.inward((int)Math.floor(cellLeft),
                                (int)Math.floor(frame.toolbarTop),
                                (int)Math.ceil(cellLeft
                                        + frame.toolbarCellWidth),
                                (int)Math.ceil(frame.toolbarBottom),
                                frame, this.screen.width, this.screen.height));
                break;
            }
            default:
                copied = messageTextOf(frame, chatLineId);
                break;
        }
        if (copied != null && LostTalesChatClipboard.copy(copied)) {
            showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.copied"));
        }
        return true;
    }

    /** The whole body of the message drawn on {@code chatLineId}. */
    private static String messageTextOf(ChatFrame frame,
                                        int chatLineId) {
        int index = firstRowOf(frame.lines, chatLineId);
        return index < 0 ? ""
                : LostTalesChatClipboard.messageTextOf(frame.lines, index);
    }

    /**
     * The index of the first drawn row that belongs to a chat line id,
     * or -1 when none of the rows does.
     */
    private static int firstRowOf(List<ChatLine> lines, int chatLineId) {
        if (lines == null) {
            return -1;
        }
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index) != null
                    && lines.get(index).getChatLineID() == chatLineId) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Answers the message on a line, in the tab that line lives in. The
     * frame is the one the line was drawn in, so the sender is read from
     * the very lines that were on screen.
     */
    private void replyToLine(ChatFrame frame, int chatLineId) {
        ChatTab tab = ClientChatChannelViews.tabOf(chatLineId);
        if (!LostTalesChatPresentation.isRepliable(chatLineId)
                || tab == null) {
            return;
        }
        long id = ClientChatMessageIds.messageIdOf(chatLineId);
        String name = "";
        String excerpt = "";
        int index = firstRowOf(frame.lines, chatLineId);
        if (index >= 0) {
            name = ChatScreenMenus.messageAccount(frame.lines, index,
                    chatLineId);
            excerpt = LostTalesChatClipboard.messageTextOf(frame.lines,
                    index);
        }
        // The identity the line was signed with beats the drawn rows: a
        // grouped continuation has no header to read a name off, and
        // the server's quote will name the identity anyway.
        ClientChatMessages.Remembered remembered = ClientChatMessages.get(id);
        if (remembered != null) {
            name = remembered.packet.getIdentityName();
        }
        // A line the server never named is quoted by its words alone;
        // one this client named keeps its own id, for the jump.
        if (!ChatMessageIds.isServerId(id)) {
            name = LostTalesChatPresentation.quoteAuthorFor(name);
        }
        // Composing happens where the message lives, and selecting a tab
        // clears any reply, so the target is set after the move.
        this.tabActions.selectChannel(tab);
        this.composer.startReply(tab, id, name, excerpt,
                LostTalesChatPresentation.headOfLine(chatLineId));
    }

    /**
     * The message the pointer rests on, by chat line id, or zero for
     * none. Resolved against the bands the last frame recorded, which is
     * what every other pointer question here asks; a hover a frame
     * behind the pointer is not something an eye can catch. Nothing is
     * hovered while something is being dragged: the pointer is on that,
     * whatever lies under it.
     */
    private int hoveredMessageLine(float pointerX, float pointerY) {
        if (this.screen.gestures().isDragging()) {
            return 0;
        }
        LostTalesChatOverlayRenderer.Band band =
                LostTalesChatOverlayRenderer.bandAt(this.mc, pointerX,
                        pointerY);
        if (band == null || band.lines == null
                || band.viewIndex >= band.lines.size()
                || band.lines.get(band.viewIndex) == null) {
            return 0;
        }
        return band.lines.get(band.viewIndex).getChatLineID();
    }

    /**
     * A click on a reply's quote takes the view to the message it
     * quotes, and lights it so the eye finds where the view landed.
     * The quote names the message by the server's id; which line that
     * is drawn on — if it is still drawn at all — is this client's own
     * business, and a message the history has trimmed past says so
     * rather than moving the view somewhere arbitrary.
     *
     * <p>Returns whether the click was a quote's, spent or not: a quote
     * that leads nowhere still belongs to the quote.</p>
     */
    private boolean jumpToQuotedMessage(LostTalesChatOverlayRenderer.Hit hit) {
        if (hit == null || !ChatReplyMarker.isMarker(hit.component)) {
            return false;
        }
        LostTalesChatOverlayRenderer.Band band = hit.band;
        if (band == null || band.lines == null) {
            showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.message.gone"));
            return true;
        }
        // A quote naming a message — by the server's id or by this
        // client's own — leads to the line named. One quoting words
        // alone leads to the newest older line saying those words under
        // that name, which is the line it was made from.
        long messageId = ChatReplyMarker.messageIdOf(hit.component);
        Integer target = messageId == 0L ? null
                : ClientChatMessageIds.chatLineIdOf(messageId);
        if (target == null) {
            target = LostTalesChatPresentation.quotedLineByWords(
                    band.lines, band.viewIndex);
        }
        if (target == null || !jumpToLine(band, target.intValue())) {
            showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.message.gone"));
        }
        return true;
    }

    /**
     * Lands the view on a line: in the window under the pointer when
     * the line is among its rows, else by bringing the line's own tab
     * forward and asking the next draw to land on it, since a tab's
     * rows exist only once it has been drawn. False when the line is
     * nowhere any more.
     */
    private boolean jumpToLine(LostTalesChatOverlayRenderer.Band band,
                               int chatLineId) {
        int index = firstRowOf(band.lines, chatLineId);
        if (index >= 0) {
            landOn(band.frame, index, chatLineId);
            return true;
        }
        ChatTab tab = ClientChatChannelViews.tabOf(chatLineId);
        if (tab == null) {
            return false;
        }
        if (!ChatLayout.isOpen(tab)) {
            tab = ChatLayout.openTab(tab, band.frame.windowId);
            if (tab == null) {
                return false;
            }
        }
        this.tabActions.selectChannel(tab);
        LostTalesChatPresentation.requestJump(chatLineId);
        return true;
    }

    /**
     * Scrolls a window to the row a line starts on and lights the line.
     * Landed near the middle of the window rather than at its edge, so
     * what was said around it is readable too. The offset counts rows,
     * so the line's index is translated through the divider.
     */
    private static void landOn(ChatFrame frame, int index,
                               int chatLineId) {
        double roomLines = frame.roomLines();
        ClientChatChannelViews.scrollTo(frame.view,
                LostTalesChatOverlayRenderer.rowOfLine(index,
                        frame.dividerLineIndex) - roomLines / 2.0D,
                frame.contentRows(), roomLines);
        LostTalesChatPresentation.flashLine(chatLineId);
    }

    /**
     * Lands a jump that was waiting for its tab to be drawn: the first
     * drawn window whose rows hold the line takes it.
     */
    private static void landPendingJump() {
        int chatLineId = LostTalesChatPresentation.pendingJump();
        if (chatLineId == 0) {
            return;
        }
        for (ChatFrame frame : ChatFrame.drawn()) {
            int index = firstRowOf(frame.lines, chatLineId);
            if (index >= 0) {
                LostTalesChatPresentation.clearPendingJump();
                landOn(frame, index, chatLineId);
                return;
            }
        }
    }

    /**
     * A click on a run of a line, resolved through the one table the
     * hand cursor and the underline read: a spoiler reveals, a channel
     * link brings its tab forward, a person opens their card, an
     * achievement opens its screen, a marker flies the map, a link
     * opens, a suggestion is taken into the field, a command runs. The
     * chat's own metadata spends the click and does nothing. Answers
     * whether the click was taken.
     */
    private boolean handleComponentClick(
            LostTalesChatOverlayRenderer.Hit hit) {
        if (hit == null) {
            return false;
        }
        IChatComponent part = hit.component;
        ChatInteractions.Action action = ChatInteractions.actionOf(part,
                this.mc.gameSettings.chatLinks);
        switch (action) {
            case SPOILER:
                ChatSpoilerMarker.reveal(part);
                return true;
            case CHANNEL_LINK:
                openChannelLink(ChatChannelLinkMarker.decode(part));
                return true;
            case PERSON:
                // A click on the head, the name, its brackets and title,
                // or a mention opens the person's card in full, the way
                // a messenger opens a profile from a name. What to do
                // about them stays with the person menu on a right-click.
                openPersonCard(personOf(hit));
                return true;
            case REPLY_JUMP:
                // The quote was asked first; a click that reached here
                // belongs to it all the same.
                return true;
            case REACTION: {
                // A chip adds the reader's own reaction with its emoji,
                // or takes it back when it is already theirs; a foreign
                // emoji's chip as well, by its key. A chip's click is
                // never counted as a use of the emoji.
                ChatReactionMarker.Data chip = ChatReactionMarker.decode(part);
                if (chip != null) {
                    sendReaction(chip.messageId, chip.key, !chip.mine);
                }
                return true;
            }
            case ADD_REACTION:
                // The button a reaction row ends on: the picker, aimed at
                // the message, as the toolbar's React opens it.
                openReactionPicker(ChatReactionMarker.addButtonMessageId(part));
                return true;
            case ACHIEVEMENT:
                ChatAchievementScreens.open(this.mc, part);
                return true;
            case MARKER_SHARE: {
                ChatShowcaseMarker.Data share = ChatShowcaseMarker.decode(part);
                ClientChatShowcaseStore.Marker marker = share == null ? null
                        : ClientChatShowcaseStore.getMarker(share.showcaseId);
                if (marker != null) {
                    LostTalesLotrMapGui.openFocusedOn(marker.id,
                            marker.dimensionId, marker.x, marker.z);
                }
                return true;
            }
            case QUEST_SHARE: {
                ChatShowcaseMarker.Data share = ChatShowcaseMarker.decode(part);
                ClientChatShowcaseStore.Quest quest = share == null ? null
                        : ClientChatShowcaseStore.getQuest(share.showcaseId);
                if (quest != null) {
                    if (quest.joinable) {
                        LostTalesNetworkHandler.CHANNEL.sendToServer(
                                new LostTalesQuestShareJoinPacket(
                                        quest.messageId, quest.tokenIndex));
                    } else {
                        WindowScreen.openPage(QuestJournalPage.PAGE_ID);
                    }
                }
                return true;
            }
            case PARTY_INVITATION:
                ChatPartyInvitationAnswers.answer(
                        ChatInteractions.invitationAnswer(part));
                return true;
            case CONSUMED:
                return true;
            case LINK:
            case SUGGESTION:
            case COMMAND:
                break;
            default:
                return false;
        }
        ClickEvent event = part.getChatStyle().getChatClickEvent();
        if (GuiScreen.isShiftKeyDown()) {
            this.inputField.writeText(part.getUnformattedTextForChat());
            return true;
        }
        if (action == ChatInteractions.Action.SUGGESTION) {
            this.inputField.setText(event.getValue());
        } else if (action == ChatInteractions.Action.COMMAND) {
            // A line's click may run a command or say something: a
            // command is echoed as a typed one is, anything else goes
            // out as the message it is.
            String value = event.getValue() == null ? ""
                    : event.getValue().trim();
            if (ChatInputRules.isServerCommand(value)) {
                sendCommand(value);
            } else if (value.length() > 0) {
                send(value);
            }
        } else {
            openChatLink(event.getValue());
        }
        return true;
    }

    /**
     * Opens the full card of whoever the pointer rests on, exactly where
     * the hover card shows; a click that lands on nobody — the gap after
     * a closing bracket — opens nothing.
     */
    private void openPersonCard(LostTalesChatHoverCard.Found person) {
        if (person == null) {
            return;
        }
        openCard(person.target,
                (int)WindowPlacement.preciseMouseX(this.mc, this.screen.width),
                (int)WindowPlacement.preciseMouseY(this.mc, this.screen.height));
    }

    /** The chat window drawn in front at a point; null for none. */
    private static String windowIdAt(double x, double y) {
        ChatFrame frame = ChatFrame.drawnAt(x, y);
        return frame == null ? null : frame.windowId;
    }

    /** The person a hit stands on, read from the frame when it is the frame's hit. */
    private LostTalesChatHoverCard.Found personOf(
            LostTalesChatOverlayRenderer.Hit hit) {
        return LostTalesChatHoverCard.locate(this.mc, hit);
    }

    /**
     * Opens the Reactions window aimed at a message, or aims the one
     * already open at it and brings it forward: every pick is sent as a
     * reaction to it rather than written into the field. Only while the
     * chat's emoji are on.
     */
    private void openReactionPicker(long messageId) {
        if (!ChatMessageIds.isServerId(messageId)
                || !LostTalesConfig.enableChatEmojis) {
            return;
        }
        ChatEmojiPicker picker = this.bar.reactionPicker();
        // In the window the reaction was asked for in: the message's, under
        // the pointer, else the one typed in.
        String at = windowIdAt(this.screen.pointerX(), this.screen.pointerY());
        this.screen.subWindows().open(SubWindowKind.REACTIONS, "", picker,
                at != null ? at : typedWindowId(),
                this.bar.firstPickerBox(picker));
        picker.aimAt(messageId);
    }

    /** Asks the server to add a reaction, or take one back; its answer redraws the chips. */
    private static void sendReaction(long messageId, ChatEmoji emoji,
                                     boolean add) {
        if (emoji != null) {
            sendReaction(messageId, emoji.getName(), add);
        }
    }

    /** As above, by the emoji's reaction key. */
    private static void sendReaction(long messageId, String emoji,
                                     boolean add) {
        if (!ChatForeignEmoji.isReactionKey(emoji)
                || !ChatMessageIds.isServerId(messageId)) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesChatReactPacket(messageId, emoji, add));
    }

    /**
     * A chip's card: the emoji by name, who reacted with it — the first
     * few, then how many more — and what a click does. An emoji the
     * registry lacks is named as Discord names it.
     */
    private void drawReactionTooltip(ChatReactionMarker.Data chip,
                                     int mouseX, int mouseY) {
        List<String> lines = new ArrayList<String>(3);
        lines.add(chip.label());
        ClientChatMessages.Remembered held =
                ClientChatMessages.get(chip.messageId);
        ChatReactionSummary.Reaction reaction = held == null ? null
                : held.packet.getReactions().find(chip.key);
        if (reaction != null && !reaction.names.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (int index = 0; index < reaction.names.size(); index++) {
                if (index > 0) {
                    names.append(", ");
                }
                names.append(reaction.names.get(index));
            }
            lines.add(reaction.others() > 0
                    ? StatCollector.translateToLocalFormatted(
                            "gui.losttales.chat.reaction.by_more",
                            names.toString(),
                            Integer.toString(reaction.others()))
                    : StatCollector.translateToLocalFormatted(
                            "gui.losttales.chat.reaction.by",
                            names.toString()));
        }
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(
                chip.mine ? "gui.losttales.chat.reaction.remove"
                        : "gui.losttales.chat.reaction.add"));
        LostTalesChatHoverCard.drawTextCard(this.mc, lines, mouseX, mouseY,
                this.screen.width, this.screen.height);
        GL11.glDisable(GL11.GL_LIGHTING);
    }

    /**
     * Follows a channel link: the tab it names comes forward — opened
     * again in the selected tab's window if it was closed — and the
     * line it names, if any, is landed on once the tab is drawn. A tab
     * that is nobody's any more says so. A link to a message names it
     * by the server's id: the message is landed on in the tab it is
     * filed under when this client still holds it, and the link's own
     * tab comes forward with a word that it is gone when it does not.
     */
    private void openChannelLink(ChatChannelLinkMarker.Data link) {
        int chatLineId = link.chatLineId;
        ChatTab tab = ChatTab.fromId(link.tabId);
        if (link.messageId != ChatMessageIds.NONE) {
            Integer held = ClientChatMessageIds.chatLineIdOf(link.messageId);
            chatLineId = held == null ? 0 : held.intValue();
            ChatTab filedUnder = held == null ? null
                    : ClientChatChannelViews.tabOf(chatLineId);
            if (filedUnder != null) {
                tab = filedUnder;
            } else if (link.tabId.startsWith(ChatTabIds.WHISPER_PREFIX)) {
                // A whisper link names a conversation only its two
                // people hold; there is no whisper tab to open for it.
                showNotice(StatCollector.translateToLocal(
                        "gui.losttales.chat.message.gone"));
                return;
            }
        }
        if (tab == null) {
            showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.channel.gone"));
            return;
        }
        if (tab.getChannel() == ChatChannel.FACTION
                && tab.getOwnerKey().length() > 0
                && !tab.getOwnerKey().equals(ClientChatChannelState
                        .scopeKeyRead(ChatChannel.FACTION))) {
            // Another faction's chat: the Faction tab shows the one the
            // chat character is in, never this one.
            showNotice(StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.channel.not_yours",
                    "#" + ClientChatChannelState.displayName(tab)));
            return;
        }
        if (!ChatLayout.isOpen(tab)) {
            tab = ChatLayout.openTab(tab,
                    LostTalesChatPresentation.windowIdOfSelection());
            if (tab == null) {
                showNotice(StatCollector.translateToLocal(
                        "gui.losttales.chat.channel.gone"));
                return;
            }
        }
        this.tabActions.selectChannel(tab);
        if (chatLineId != 0) {
            LostTalesChatPresentation.requestJump(chatLineId);
        } else if (link.messageId != ChatMessageIds.NONE) {
            showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.message.gone"));
        }
    }

    private void openChatLink(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? ""
                    : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return;
            }
            if (this.mc.gameSettings.chatLinksPrompt) {
                this.clickedLinkUri = uri;
                this.mc.displayGuiScreen(
                        new GuiConfirmOpenLink(this.screen, value, 0, false));
            } else {
                browseTo(uri);
            }
        } catch (URISyntaxException ignored) {
        }
    }

    /** Vanilla's reflective desktop-browse, minus the private plumbing. */
    private static void browseTo(URI uri) {
        try {
            Class<?> desktop = Class.forName("java.awt.Desktop");
            Object instance = desktop.getMethod("getDesktop").invoke(null);
            desktop.getMethod("browse", URI.class).invoke(instance, uri);
        } catch (Throwable ignored) {
        }
    }

    /** A short confirmation above the bar: what the bar draws. */
    private void showNotice(String text) {
        this.bar.showNotice(text);
    }

    /**
     * Where a menu the empty state's {@code +} opens hangs: the {@code +}
     * itself, toward the middle of the screen, which is all there is;
     * null while something is open.
     */
    private SubWindowAnchor emptyPlusAnchor() {
        return WindowScreen.isEmpty() ? SubWindowAnchor.inward(
                this.emptyPlusLeft, this.emptyPlusTop, this.emptyPlusRight,
                this.emptyPlusBottom, 0.0D, 0.0D, this.screen.width, this.screen.height,
                null) : null;
    }

    /**
     * Where the character menu hangs: the head button, toward the middle
     * of the window it is typed in, which is above the bar.
     */
    private SubWindowAnchor characterButtonAnchor() {
        int left = this.bar.characterButtonLeft();
        int top = this.bar.characterButtonTop();
        Window window = WindowLayout.windowOf(
                ClientChatChannelState.getSelected());
        return SubWindowAnchor.inward(left, top,
                left + ChatInputBar.CHARACTER_BUTTON_SIZE,
                top + ChatInputBar.CHARACTER_BUTTON_SIZE,
                window == null ? null : ChatFrame.find(window.getId()),
                this.screen.width, this.screen.height);
    }
}
