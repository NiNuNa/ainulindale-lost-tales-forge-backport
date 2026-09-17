package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatDeliveryMark;
import com.ninuna.losttales.chat.ChatMentionCandidate;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.client.gui.LostTalesPointerOwner;
import com.ninuna.losttales.client.mapmarker.LostTalesMapCursor;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiRegionBlur;
import com.ninuna.losttales.client.mapmarker.LostTalesLotrMapGui;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReactionSummary;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.emoji.ChatForeignEmoji;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatCommandContextPacket;
import com.ninuna.losttales.network.packet.LostTalesChatReactPacket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiNewChat;
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
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Vanilla chat input with a compact channel indicator, folder-style channel
 * tabs on top of every chat window, per-channel message views, and pickers
 * and completion popups for emojis, items, map markers, and quests. Tabs
 * can be dragged: along their row to reorder, away from it to detach into
 * a window of their own, onto another window's row to dock there; a
 * window is moved by its grip, its strip, or by dragging its messages —
 * a press that does not move stays the click it always was. All of that edits
 * {@link ChatWindowLayout}, the one model every window is drawn from; the
 * chat screen is the only place windows are moved. Every overlay
 * registers the rectangle it draws in {@link ChatPointerRegions}; hover,
 * tooltip, and click
 * handling consult that record before touching the message stack, so
 * whatever is painted on top is also what owns the pointer.
 */
public final class LostTalesChatGui extends GuiChat
        implements LostTalesPointerOwner {
    /** Gap between the typing line's bubble and its words. */
    private static final int TYPING_BUBBLE_GAP = 3;
    /**
     * The empty state's strip: as tall as a window's tab row, so what
     * stands where the chat would be reads as a piece of the same
     * interface, and with the same clear space around its contents the
     * row keeps around its own.
     */
    private static final int EMPTY_STATE_HEIGHT =
            ChatChannelTabBar.ROW_HEIGHT;
    private static final int EMPTY_STATE_PADDING = 4;
    /** Gap between the + and the line beside it. */
    private static final int EMPTY_STATE_GAP = 5;

    /** Set once a message or command has gone out; the draft is then spent. */
    private boolean sent;
    /** The short notice over the bar, offered to every collaborator. */
    private final ChatNoticeSink notices = new ChatNoticeSink() {
        @Override
        public void showNotice(String message) {
            LostTalesChatGui.this.showNotice(message);
        }
    };
    /** The reply or the edit the bar is composing, and its chip. */
    private final ChatComposer composer = new ChatComposer();
    /** What goes to the server: messages, edits, whispers, typing. */
    private final ChatOutbox outbox = new ChatOutbox(this.composer,
            this.notices);
    private final ChatPointerRegions regions = new ChatPointerRegions();
    /** The input section: its place, its controls, its notice. */
    private final ChatInputBar bar = new ChatInputBar();
    private final ChatSearchBar searchBar = new ChatSearchBar();
    /** The suggestion lists, command completion and token insertion. */
    private final ChatInputCompletion completion =
            new ChatInputCompletion(this.notices);
    /** The selection, and the verbs that open, close, lock and move tabs. */
    private final ChatTabActions tabActions = new ChatTabActions(this.bar,
            this.completion, this.composer);
    /** The drags: tabs, windows, resizes and scrollbars. */
    private final ChatWindowGestures gestures = new ChatWindowGestures(
            new ChatWindowGestures.RowSource() {
                @Override
                public ChatChannelTabBar.Row rowFor(
                        ChatWindow window, ChatWindowFrame frame,
                        LostTalesGuiAnimationSample opening) {
                    return LostTalesChatGui.this.rowFor(window, frame,
                            opening);
                }
            }, this.tabActions, this.bar);
    /** Every menu the screen opens, and what their rows do. */
    private final ChatScreenMenus menus = new ChatScreenMenus(
            this.tabActions, this.composer, this.notices);
    private String hoverTip = "";
    private int hoverTipX;
    private int hoverTipY;
    /**
     * The longest gap between the two presses of a double click on a
     * window's strip: the desktop's usual half second.
     */
    private static final long DOUBLE_CLICK_NANOS = 500L * 1000000L;
    /**
     * The last press on a window's bare strip or grip — which window,
     * when, where, and which of the screen's presses it was — so a second
     * press there completes a double click.
     */
    private String stripPressWindowId;
    private long stripPressNanos;
    private int stripPressX;
    private int stripPressY;
    private int stripPressNumber;
    /** The screen's presses, counted: a double click is two in a row. */
    private int pressCount;
    private URI clickedLinkUri;
    private boolean openAnimationStarted;
    /**
     * What the pointer is on this frame, found once before anything is
     * drawn: what every highlight, tip and card of the frame and the
     * pointer's pose read.
     */
    private ChatHover hover = ChatHover.NONE;
    /** The empty state's + as drawn this frame; width zero while none was. */
    private int emptyPlusLeft;
    private int emptyPlusTop;
    private int emptyPlusRight;
    private int emptyPlusBottom;
    private String composingIdentityKey;

    public LostTalesChatGui(String defaultText) {
        super(defaultText == null ? "" : defaultText);
    }

    @Override
    public void initGui() {
        ClientChatChannelState.ensureAvailable();
        LostTalesChatHoverCard.unpin();
        super.initGui();
        // The chat draws its own text, shadow and all; vanilla's field
        // would put a quarter-colour shadow under the one thing on the
        // bar the player is looking at. Everything else about it —
        // typing, history, the caret's own behaviour — stays vanilla's,
        // and it takes over the state the field it replaces was given.
        GuiTextField vanillaField = this.inputField;
        ChatInputField styled = new ChatInputField(this.fontRendererObj,
                vanillaField.xPosition, vanillaField.yPosition,
                vanillaField.getWidth(), ChatInputBar.FIELD_HEIGHT);
        styled.setEnableBackgroundDrawing(false);
        styled.setCanLoseFocus(false);
        styled.setFocused(true);
        styled.setMaxStringLength(vanillaField.getMaxStringLength());
        styled.setText(vanillaField.getText());
        styled.setCursorPositionEnd();
        this.inputField = styled;
        this.completion.bind(this.mc, this.fontRendererObj, this.regions,
                styled);
        this.outbox.bind(this.mc);
        this.bar.bind(this.mc, this.fontRendererObj, this.regions, styled,
                this.height);
        this.searchBar.bind(this.fontRendererObj);
        this.inputField.setTextColor(LostTalesChatVisualStyle.IVORY);
        this.inputField.setDisabledTextColour(
                LostTalesChatVisualStyle.SHADOW);
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
        this.gestures.bind(this.mc, this.fontRendererObj, this.width,
                this.height);
        this.menus.bind(this.mc, this.fontRendererObj, styled, this.width,
                this.height);
        this.tabActions.syncSelection();
        // initGui also runs on resize; the entrance only plays once per
        // opening, timed from the same instant as the input bar's.
        if (!this.openAnimationStarted) {
            this.openAnimationStarted = true;
            ClientChatChannelViews.noteOpened();
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        syncChatIdentity();
        this.bar.tickPickers();
        this.searchBar.tick();
        ClientChatChannelState.ensureAvailable();
        this.tabActions.syncSelection();
        if (isEmptyState()) {
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
     * Closing without sending keeps the text for the next opening; once
     * something has been sent the draft is spent.
     */
    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        // A screen closed mid-drag ends the drag where it stands: this
        // instance is gone and nothing else would ever release it. A
        // card a click opened goes with the screen.
        this.gestures.cancelDrags();
        LostTalesChatHoverCard.unpin();
        // A search belongs to the open screen and goes with it.
        ChatSearch.close();
        if (!isEmptyState()) {
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

    /**
     * Who is typing into the window's front tab, in the gap between the
     * history and the bar: one or two names, three names, or a count
     * past that. Nothing is drawn while nobody is.
     */
    private void drawTypingLine(ChatWindow window, ChatWindowFrame frame,
                                LostTalesGuiAnimationSample opening,
                                int mouseX, int mouseY) {
        if (this.composer.drawChip(this.fontRendererObj, window, frame,
                opening, mouseX, mouseY)
                || !LostTalesConfig.showChatTypingIndicators) {
            return;
        }
        ChatTab front = ChatWindowFrame.activeTab(window,
                ChatWindowFrame.visibleTabs(window));
        List<String> names = ClientChatTypingState.namesTyping(front);
        if (names.isEmpty()) {
            return;
        }
        String text;
        if (names.size() == 1) {
            text = StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.typing.one", names.get(0));
        } else if (names.size() == 2) {
            text = StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.typing.two", names.get(0),
                    names.get(1));
        } else if (names.size() == 3) {
            text = StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.typing.three", names.get(0),
                    names.get(1), names.get(2));
        } else {
            text = StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.typing.many",
                    String.valueOf(names.size()));
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
        // chip that shares this row does.
        ChatTimestampColumn columns =
                ChatTimestampColumn.current(this.fontRendererObj);
        int inset = Math.round(columns.messageX() * frame.scale);
        int x = (int)Math.floor(frame.drawnLeft()) + inset;
        int room = (int)Math.round(frame.boxRight - frame.boxLeft)
                - inset - 6;
        // In the window's trailing strip, on the metrics a message
        // line's glyphs would take there.
        int y = (int)Math.floor(frame.drawnBaseline())
                + LostTalesChatOverlayRenderer.LINE_HEIGHT
                - LostTalesChatOverlayRenderer.TEXT_OFFSET;
        // A bubble before the words, centred in the strip as a box of
        // its own, on the row a quote's and a message link's bubble take,
        // and the line itself in the asides' tone rather than the
        // message ivory.
        ChatIconSheet bubble = ChatIconSheet.SPEECH_BUBBLE;
        bubble.drawWithShadow(x, y + LostTalesChatOverlayRenderer
                .centredBoxTop(bubble.getHeight()), alpha);
        int textX = x + bubble.getWidth() + TYPING_BUBBLE_GAP;
        LostTalesChatVisualStyle.drawColored(this.fontRendererObj,
                "§o" + this.fontRendererObj.trimStringToWidth(text,
                        room - (textX - x)),
                textX, y, LostTalesChatVisualStyle.asideRgb(), alpha);
    }

    /**
     * The window the input belongs to: the one holding the selected
     * channel, or the first drawn one before the selection has a window
     * on screen.
     */

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // Vanilla's own rule: any key ends a pending completion request,
        // and any key but Tab ends the walk through the candidates —
        // except that while the candidate popup is open, Up and Down
        // walk it too, and Escape only closes it.
        if (this.completion.handleCommandPopupKey(keyCode)) {
            return;
        }
        if (keyCode != Keyboard.KEY_TAB) {
            this.completion.onKeyNotTab();
        }
        if (keyCode == Keyboard.KEY_ESCAPE
                && LostTalesChatHoverCard.isPinned()) {
            LostTalesChatHoverCard.unpin();
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE
                && (this.menus.isOpen() || this.gestures.isDragging())) {
            this.menus.close();
            this.gestures.cancelDrags();
            return;
        }
        // A searchable list takes plain typing while it is open; the
        // shortcuts that act on the chat still reach past it.
        if (!isCtrlKeyDown()
                && this.menus.handleKeyTyped(typedChar, keyCode)) {
            return;
        }
        // Ctrl+F opens the search over the window being typed in, and
        // while its field holds the keys they are its own: Enter walks
        // the matches down and Shift+Enter up, Escape closes it, and
        // everything else is typed into it.
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_F) {
            openSearch();
            return;
        }
        if (this.searchBar.isFocused()) {
            handleSearchKey(typedChar, keyCode);
            return;
        }
        // Escape with nothing else open drops the reply before it closes
        // the chat: backing out of an answer should not cost the screen.
        if (keyCode == Keyboard.KEY_ESCAPE
                && (this.composer.isReplying() || this.composer.isEditing())) {
            this.composer.cancelComposing(this.inputField);
            return;
        }
        // Ctrl+N is the + control by keyboard, and Ctrl+Shift+A the
        // search panel: both mean something with nothing open, since
        // both are ways back to a channel.
        if (isCtrlKeyDown() && isShiftKeyDown()
                && keyCode == Keyboard.KEY_A) {
            this.menus.openSearchPanel(ChatWindowLayout.windowOf(
                    ClientChatChannelState.getSelected()), -1, -1,
                    emptyPlusAnchorX(), emptyPlusAnchorBottom());
            return;
        }
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_N) {
            this.menus.openChannelMenu(emptyPlusAnchorX(),
                    emptyPlusAnchorBottom());
            return;
        }
        if (isEmptyState()) {
            // With nothing open there is no field, no tab to walk to and
            // nothing to send: the + is the only control and Escape the
            // only key.
            if (keyCode == Keyboard.KEY_ESCAPE) {
                this.mc.displayGuiScreen(null);
            }
            return;
        }
        // Ctrl+Tab walks every window's tabs, Ctrl+Left/Right the
        // selected window's, and Ctrl+W closes the selected one: none of
        // them clashes with autocomplete, so all work with text in the
        // field (drafts belong to their tabs).
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_TAB) {
            this.tabActions.selectChannel(ClientChatChannelState.cycleAll(isShiftKeyDown()));
            return;
        }
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_RIGHT) {
            this.tabActions.selectChannel(ClientChatChannelState.cycle());
            return;
        }
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_LEFT) {
            this.tabActions.selectChannel(ClientChatChannelState.cycleBack());
            return;
        }
        // Ctrl+1 to Ctrl+8 pick the selected window's tabs by place and
        // Ctrl+9 its last, as a browser's do; the digits are the main
        // row's, which sit together in the keyboard's own numbering.
        if (isCtrlKeyDown() && keyCode >= Keyboard.KEY_1
                && keyCode <= Keyboard.KEY_9) {
            this.tabActions.selectChannel(ClientChatChannelState.selectOrdinal(
                    keyCode - Keyboard.KEY_1 + 1));
            return;
        }
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_W) {
            this.tabActions.closeMarkedOrActiveTabs();
            return;
        }
        ChatPickerPanel picker = this.bar.openPicker();
        if (picker != null && picker.handleKeyTyped(typedChar, keyCode)) {
            return;
        }
        if (this.completion.handleSuggestionKey(keyCode)) {
            return;
        }
        // Up and Down walk what was sent from the selected tab, and from
        // it alone: each tab keeps its own history. Handled here, never
        // by super, whose single history mixes every tab's lines.
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
            return;
        }
        // Tab and the arrows walk the tabs of the window being typed
        // in. All three need an empty field: with text in it the arrows
        // belong to the caret, as they do in any text field.
        if (this.inputField.getText().length() == 0
                && (keyCode == Keyboard.KEY_TAB
                        || keyCode == Keyboard.KEY_RIGHT)) {
            ClientChatChannelState.cycle();
            this.tabActions.syncSelection();
            return;
        }
        if (this.inputField.getText().length() == 0
                && keyCode == Keyboard.KEY_LEFT) {
            ClientChatChannelState.cycleBack();
            this.tabActions.syncSelection();
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            submitInput();
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            // With text in the field Tab is command completion. Handled
            // here, never by super: vanilla's completion would print the
            // candidate list as an untracked chat line, which files as
            // console output.
            this.completion.completeInput();
            return;
        }
        if (refusesCharacter(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
        maybeSpaceTypedShortcode(typedChar);
        enforceLimit();
        this.completion.refreshAfterTyping();
    }

    /** Opens the search over the window being typed in and gives it the keys. */
    private void openSearch() {
        ChatWindow typed = ChatWindowLayout.windowOf(
                ClientChatChannelState.getSelected());
        if (typed == null) {
            return;
        }
        ChatSearch.open(typed.getId());
        this.searchBar.setText(ChatSearch.query());
        focusSearch(true);
    }

    /** Closes the search: its lit matches go, and the input has the keys again. */
    private void closeSearch() {
        ChatSearch.close();
        this.searchBar.setText("");
        focusSearch(false);
    }

    /** The keys go to the search field or to the input, never to both. */
    private void focusSearch(boolean on) {
        this.searchBar.focus(on);
        this.inputField.setFocused(!on);
    }

    private void handleSearchKey(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeSearch();
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            walkSearch(!isShiftKeyDown());
            return;
        }
        if (this.searchBar.keyTyped(typedChar, keyCode)) {
            ChatSearch.setQuery(this.searchBar.text());
        }
    }

    /** Walks to the next match down, or the previous one up, and lands on it. */
    private void walkSearch(boolean forward) {
        ChatWindow typed = ChatWindowLayout.windowOf(
                ClientChatChannelState.getSelected());
        ChatWindowFrame frame = typed == null ? null : frameFor(typed);
        if (frame == null) {
            return;
        }
        landSearch(frame, ChatSearch.walk(forward, this.mc, frame));
    }

    /** Lands on a match the search named, when it named one. */
    private static void landSearch(ChatWindowFrame frame, int chatLineId) {
        if (chatLineId == 0) {
            return;
        }
        int index = firstRowOf(frame.lines, chatLineId);
        if (index >= 0) {
            landOn(frame, index, chatLineId);
        }
    }

    /** A press on the search bar: the field takes the caret, the controls act. */
    private void clickSearchBar(ChatHover press, double x, double y, int button) {
        if (button != 0 || press.frame == null || press.searchPart == null) {
            return;
        }
        this.tabActions.selectWindow(press.window);
        switch (press.searchPart) {
            case FIELD:
                focusSearch(true);
                this.searchBar.clickField(press.row, x, y);
                return;
            case PREVIOUS:
                walkSearch(false);
                return;
            case NEXT:
                walkSearch(true);
                return;
            case CLOSE:
                closeSearch();
                return;
            default:
                return;
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
                func_146403_a(text);
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
     * The completion answer, which vanilla delivers to this method by
     * name; the candidate list is filed under the tab the request was
     * typed in.
     */
    @Override
    public void func_146406_a(String[] serverCompletions) {
        this.completion.onServerCompletions(serverCompletions);
    }

    /**
     * Sends a command the server answers, however it was asked for —
     * typed, clicked in a line, chosen from a menu. The command and
     * what it answers are shown in the tab in front — the answer as a
     * line of the Server's own — and the operator console is told by
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
        func_146403_a(command);
    }

    /** Whether the field holds a command rather than a message. */
    private boolean isCommand() {
        return ChatInputRules.isCommand(this.inputField.getText());
    }

    /**
     * A printable character typed into a message already at the limit
     * is refused outright, so the counter's ceiling is a wall, not a
     * warning. Control keys, shortcuts and commands pass.
     */
    private boolean refusesCharacter(char typedChar, int keyCode) {
        if (isCommand() || !ChatAllowedCharacters.isAllowedCharacter(typedChar)
                || isCtrlKeyDown()
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
                    "gui.losttales.chat.global_requires_character"));
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

    @Override
    public void func_146403_a(String text) {
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
                return;
            }
            this.mc.thePlayer.sendChatMessage(message);
            return;
        }
        if (message.length() == 0
                || !ChatMessageValidator.isValid(message)
                || !ClientChatChannelState.canSend(tab)) {
            return;
        }
        // The history keeps the raw text, so recalling it gives back
        // exactly what was typed.
        ClientChatChannelState.recordSent(tab, message);
        this.outbox.sendMessage(tab, message);
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

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        // The wheel event carries no pointer of its own: it is read off
        // the mouse in window pixels and scaled to the screen, to the
        // same fraction the hover reads.
        double mouseX = Mouse.getEventX() * (double)this.width
                / this.mc.displayWidth;
        double mouseY = this.height - Mouse.getEventY() * (double)this.height
                / this.mc.displayHeight - 1.0D;
        // One turn of the wheel is vanilla's step in whole lines, one
        // line with Shift, counted in the unit of what it scrolls:
        // message lines for the history, rows for a menu, list lines for
        // a picker.
        int lines = ChatWheelStep.lines(wheel, isShiftKeyDown());
        // The wheel scrolls what the pointer is on, found as the hover
        // finds it: the open menu's rows, the open picker's list, else
        // the history of the window under the pointer.
        ChatHover under = resolveHover(mouseX, mouseY);
        if (under.is(ChatHover.Kind.MENU) || under.is(ChatHover.Kind.MENU_ENTRY)) {
            this.menus.scrollBy(-ChatWheelStep.menuRows(lines));
            return;
        }
        if (under.picker != null && (under.is(ChatHover.Kind.PICKER)
                || under.is(ChatHover.Kind.PICKER_CELL)
                || under.is(ChatHover.Kind.PICKER_LABEL))) {
            under.picker.scrollBy(-ChatWheelStep.pickerPixels(lines));
            return;
        }
        int wheelPixels = ChatWheelStep.historyPixels(lines);
        // super.handleMouseInput() scrolls vanilla's own offset, which is
        // unused; the visible history scrolls per channel view instead,
        // in the window under the pointer, else the one being typed into.
        ChatWindowFrame frame = ChatWindowFrame.drawnAt(mouseX, mouseY);
        if (frame == null) {
            frame = this.bar.activeFrame();
        }
        if (frame == null || frame.view == null) {
            return;
        }
        // A turn moves the page a fixed distance in pixels — vanilla's
        // step in whole lines — which the frame turns into rows, since
        // the rows are not all one height. The reach is the rows the
        // window draws, not its message lines: the two differ by the
        // unread divider's own row and the blank rows between runs.
        int rows = frame.contentRows();
        double roomLines = frame.roomLines();
        double current = ClientChatChannelViews.getScroll(frame.view, rows,
                roomLines);
        ClientChatChannelViews.scrollTo(frame.view,
                frame.rowsAfterScrolling(current, wheelPixels), rows,
                roomLines);
    }

    /**
     * Every window is one motion group: its lines, backdrop, tab row and
     * input bar all take the same opening sample, so a window enters,
     * settles and fades as one piece. Rows and bars are drawn on the
     * fractional position the window was drawn at, never on a
     * rounded one, so they never jitter against the lines.
     */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // The screen is flat overlay content and takes no part in depth
        // testing, exactly as the HUD's chat pass does not: an item
        // icon (a faction tab's banner, a shared item) is drawn at a
        // raised z and leaves its depth behind, and with the test on
        // whatever is drawn over it afterwards, the next tab included,
        // is rejected where they overlap.
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        if (depthTest) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        try {
            drawChat(mouseX, mouseY, partialTicks);
        } finally {
            if (depthTest) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            }
        }
    }

    private void drawChat(int mouseX, int mouseY, float partialTicks) {
        ClientChatChannelState.ensureAvailable();
        this.tabActions.syncSelection();
        this.bar.refreshPickers();
        // A reaction the message menu asked for opens the emoji picker
        // on that message, whichever way the menu entry was chosen.
        long reactTo = this.menus.takeReactionTarget();
        if (reactTo != ChatMessageIds.NONE) {
            openReactionPicker(reactTo);
        }
        // The bars enter from below on their own curve while the rest of
        // the window rides the shared opening motion; everything drawn
        // with a bar is hit in the bar's own space.
        float entrance = this.bar.entranceOffset();
        this.gestures.advance(mouseX, mouseY);
        // The pointer's exact GUI position, the one the drawn cursor tip
        // stands on. What is under it is found once, against what is on
        // screen — the windows, rows, lists and regions the last frame
        // drew — before any of it is drawn again, and every highlight,
        // tip and card of this frame, and the pointer's pose, read that
        // one answer: nothing lights, shows the hand or answers a press
        // on pixels the others do not name.
        double pointerX = pointerX();
        double pointerY = pointerY();
        this.hover = resolveHover(pointerX, pointerY);
        // The floating controls light from the same answer.
        ChatWindowFrame.noteHoveredControls(
                this.hover.is(ChatHover.Kind.MESSAGE_TOOLBAR)
                        ? this.hover.frame : null, this.hover.toolbarKind,
                this.hover.is(ChatHover.Kind.JUMP_PILL)
                        ? this.hover.frame : null);
        this.regions.reset();
        this.hoverTip = tipFor(this.hover);
        this.hoverTipX = mouseX;
        this.hoverTipY = mouseY;
        // The frame as drawn so far — world and HUD — is captured and
        // blurred once, before anything of the chat is on it; each
        // window then pastes its own rectangle of the result under its
        // backdrop while the rest of the screen stays sharp.
        if (LostTalesConfig.enableChatBackgroundBlur
                && LostTalesConfig.enableGuiBackgroundBlur) {
            LostTalesGuiRegionBlur.getInstance().capture(this.mc,
                    partialTicks, (float)LostTalesConfig.guiBlurStrength);
        }
        LostTalesChatPresentation.beginFrame();
        LostTalesChatPresentation.setHoveredLine(shadedLine(pointerX,
                pointerY));
        boolean onLine = this.hover.is(ChatHover.Kind.LINE);
        LostTalesChatOverlayRenderer.Hit line = onLine ? this.hover.line : null;
        LostTalesChatHoverCard.Found person = onLine ? this.hover.person : null;
        LostTalesChatPresentation.setHoveredComponent(
                line == null ? null : line.line,
                line == null ? -1 : line.index,
                line == null ? null : line.component);
        LostTalesChatPresentation.setHoveredSenderRow(
                person != null && person.sender ? person.row : null);
        this.gestures.markScrollbarsWanted(pointerX, pointerY);
        drawWindows(mouseX, mouseY, pointerX, pointerY, partialTicks);
        landPendingJump();
        // The field follows the active window's bar as just drawn.
        this.bar.updateInputBounds();
        this.menus.refreshRestorePopup();
        this.menus.registerRegion(this.regions);
        boolean onMenu = this.hover.is(ChatHover.Kind.MENU)
                || this.hover.is(ChatHover.Kind.MENU_ENTRY);
        double menuX = onMenu ? pointerX : ChatHover.AWAY;
        double menuY = onMenu ? pointerY : ChatHover.AWAY;
        if (isEmptyState()) {
            // Nothing is open to type into, so the screen shows what it
            // has instead of a bar with no channel behind it. The bar's
            // pickers go with the bar.
            if (this.bar.openPicker() != null) {
                this.bar.closePickers();
            }
            boolean onPlus = this.hover.is(ChatHover.Kind.EMPTY_PLUS);
            drawEmptyState(onPlus ? pointerX : ChatHover.AWAY,
                    onPlus ? pointerY : ChatHover.AWAY);
            this.bar.drawNotice();
            this.menus.draw(this.regions, menuX, menuY);
            drawHoverTipFor();
            return;
        }
        int barRight = this.bar.inputBarRight();
        int anchor = this.bar.inputAnchor();
        // The bar group's own space: the pointer less the bar's own
        // translation, its fraction and its entrance included. Each
        // control is handed the pointer only while it is what the
        // pointer is on.
        double barX = pointerX - this.bar.fractionX();
        double barY = pointerY - this.bar.fractionY() - entrance;
        boolean onControl = isBarControl(this.hover);
        boolean onPicker = this.hover.picker != null;
        boolean onList = this.hover.is(ChatHover.Kind.SUGGESTION)
                || this.hover.is(ChatHover.Kind.SUGGESTIONS);
        double controlX = onControl ? barX : ChatHover.AWAY;
        double controlY = onControl ? barY : ChatHover.AWAY;
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(this.bar.fractionX(),
                    this.bar.fractionY() + entrance, 0.0F);
            this.bar.drawBar(barRight);
            this.bar.drawCharacterSelectionButton(controlX, controlY,
                    this.menus.isKindOpen(ChatScreenMenus.POPUP_CHARACTERS));
            this.bar.drawIndicator(barRight, controlX, controlY);
            this.bar.drawToolbarToggle(barRight, controlX, controlY);
            this.bar.drawPickers(barRight,
                    onPicker ? barX : ChatHover.AWAY,
                    onPicker ? barY : ChatHover.AWAY,
                    mouseX, mouseY - Math.round(entrance));
            this.bar.drawDividers(barRight);
            this.bar.drawSendButton(barRight, controlX, controlY);
            this.bar.drawCounter(barRight);
            this.completion.draw(anchor, this.inputField.xPosition,
                    onList ? barX : ChatHover.AWAY,
                    onList ? barY : ChatHover.AWAY);
        } finally {
            GL11.glPopMatrix();
        }
        drawChatLineHover(line, mouseX, mouseY);
        this.bar.drawNotice();
        ChatMentionCandidate candidate =
                this.hover.is(ChatHover.Kind.SUGGESTION)
                        ? this.completion.mentionAt(this.hover.suggestion)
                        : null;
        if (candidate != null) {
            LostTalesChatHoverCard.drawForCandidate(this.mc, candidate,
                    mouseX, mouseY, this.width, this.height);
        } else if (this.hover.is(ChatHover.Kind.CHARACTER_BUTTON)
                && !this.menus.isOpen()) {
            // The head button's hover is the chosen identity's own brief
            // card: who the roleplaying channels speak as right now.
            LostTalesChatHoverCard.drawForIdentity(this.mc,
                    ClientChatChannelState.getSelected(), mouseX, mouseY,
                    this.width, this.height);
        } else {
            LostTalesChatHoverCard.draw(this.mc, person, mouseX, mouseY,
                    this.width, this.height);
        }
        // The card a click opened stands over the lines until it is
        // closed, wherever the pointer has gone since.
        LostTalesChatHoverCard.drawPinned(this.mc, this.width, this.height);
        this.gestures.drawLinkHighlight();
        this.menus.draw(this.regions, menuX, menuY);
        drawHoverTipFor();
    }

    /** The open menu owns its tooltips; controls behind it stay quiet. */
    private void drawHoverTipFor() {
        if (this.hoverTip.length() > 0 && !this.menus.isOpen()) {
            drawHoverTip();
        }
    }

    /** Whether the pointer is on one of the bar's own controls. */
    private static boolean isBarControl(ChatHover hover) {
        return hover.is(ChatHover.Kind.CHARACTER_BUTTON)
                || hover.is(ChatHover.Kind.INDICATOR)
                || hover.is(ChatHover.Kind.SEND_BUTTON)
                || hover.is(ChatHover.Kind.TOOLBAR_TOGGLE);
    }

    /**
     * The message the frame shades: the one a menu was opened over while
     * the menu stands, else the one under the pointer while the pointer
     * is on the lines rather than on anything drawn above them.
     */
    private int shadedLine(double x, double y) {
        switch (this.hover.kind) {
            case MESSAGE_TOOLBAR:
                // A toolbar's buttons reach past its message's row; on
                // them the message stays the hovered one.
                if (this.hover.frame != null
                        && !this.gestures.isDragging()) {
                    return this.hover.frame.toolbarChatLineId;
                }
                return hoveredMessageLine((float)x, (float)y);
            case LINE:
            case WINDOW:
            case NONE:
            case SCROLLBAR:
            case JUMP_PILL:
                return hoveredMessageLine((float)x, (float)y);
            default:
                return this.menus.messageMenuChatLineId();
        }
    }

    private double pointerX() {
        return ChatWindowPlacement.preciseMouseX(this.mc, this.width);
    }

    private double pointerY() {
        return ChatWindowPlacement.preciseMouseY(this.mc, this.height);
    }

    /* ---- What is under the pointer ---- */

    /**
     * What is under a GUI-space point, from the top of what is drawn
     * down, in the order a press is handled: the card a click opened,
     * the open menu, the completion lists, the open picker, a window's
     * edge, the tab rows, another window's bar strip, the bar's
     * controls, anything else painted above the lines, the jump pill,
     * the reply chip, a message's toolbar, a scrollbar, a run of the
     * lines, and the window itself. Each is asked with the one hit test
     * its highlight and its press use, so the answer is the same
     * whichever of them asks. Nothing is under the pointer while
     * something is being dragged.
     */
    ChatHover resolveHover(double x, double y) {
        if (this.gestures.isDragging()) {
            return ChatHover.NONE;
        }
        if (LostTalesChatHoverCard.isPinned()
                && LostTalesChatHoverCard.pinnedContains(x, y)) {
            return new ChatHover(ChatHover.Kind.CARD);
        }
        if (this.menus.isOpen() && this.menus.contains(x, y)) {
            ChatPopupMenu.Entry entry = this.menus.entryAt(x, y);
            ChatHover hover = new ChatHover(entry != null
                    ? ChatHover.Kind.MENU_ENTRY : ChatHover.Kind.MENU);
            hover.menuEntry = entry;
            return hover;
        }
        if (isEmptyState()) {
            return emptyStateContains(x, y)
                    ? new ChatHover(ChatHover.Kind.EMPTY_PLUS)
                    : ChatHover.NONE;
        }
        double barX = x - this.bar.fractionX();
        double barY = y - this.bar.fractionY() - this.bar.entranceOffset();
        int barRight = this.bar.inputBarRight();
        ChatInputCompletion.Slot slot = this.completion.slotAt(barX, barY,
                this.bar.inputAnchor(), this.inputField.xPosition);
        if (slot != null) {
            ChatHover hover = new ChatHover(slot.row >= 0
                    ? ChatHover.Kind.SUGGESTION : ChatHover.Kind.SUGGESTIONS);
            hover.suggestion = slot;
            return hover;
        }
        ChatPickerPanel open = this.bar.openPicker();
        int pickerAnchor = this.bar.pickerAnchor();
        if (open != null && open.isInsidePanel(barX, barY, barRight,
                pickerAnchor)) {
            ChatPickerPanel.Entry cell = open.entryAt(barX, barY, barRight,
                    pickerAnchor);
            ChatHover hover = new ChatHover(cell != null
                    ? ChatHover.Kind.PICKER_CELL
                    : open.labelAt(barX, barY, barRight, pickerAnchor) != null
                            ? ChatHover.Kind.PICKER_LABEL
                            : ChatHover.Kind.PICKER);
            hover.picker = open;
            hover.pickerEntry = cell;
            return hover;
        }
        ChatWindowGestures.ResizeTarget edge =
                ChatWindowGestures.resizeUnderPointer(x, y, this.regions);
        if (edge != null && ChatWindowLayout.window(edge.frame.windowId)
                != null) {
            ChatHover hover = new ChatHover(ChatHover.Kind.RESIZE);
            hover.resize = edge;
            hover.frame = edge.frame;
            return hover;
        }
        ChatHover row = rowHoverAt(x, y);
        if (row != null) {
            return row;
        }
        ChatWindowFrame otherBar = otherBarAt(x, barY);
        if (otherBar != null) {
            ChatHover hover = new ChatHover(ChatHover.Kind.OTHER_BAR);
            hover.frame = otherBar;
            return hover;
        }
        if (this.bar.isInsideCharacterButton(barX, barY)) {
            return new ChatHover(ChatHover.Kind.CHARACTER_BUTTON);
        }
        if (this.bar.isInsideIndicator(barX, barY, barRight)) {
            return new ChatHover(ChatHover.Kind.INDICATOR);
        }
        if (this.bar.isInsideSendButton(barX, barY, barRight)) {
            return new ChatHover(ChatHover.Kind.SEND_BUTTON);
        }
        if (this.bar.isInsideToolbarToggle(barX, barY, barRight)) {
            return new ChatHover(ChatHover.Kind.TOOLBAR_TOGGLE);
        }
        ChatPickerPanel button = this.bar.pickerButtonAt(barX, barY,
                barRight);
        if (button != null) {
            ChatHover hover = new ChatHover(ChatHover.Kind.PICKER_BUTTON);
            hover.picker = button;
            return hover;
        }
        if (this.regions.contains(x, y)) {
            return new ChatHover(ChatHover.Kind.OVERLAY);
        }
        List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
        for (int index = frames.size() - 1; index >= 0; index--) {
            ChatWindowFrame frame = frames.get(index);
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
            ChatWindowFrame frame = frames.get(index);
            int kind = frame.toolbarKindAt(x, y);
            if (kind >= 0) {
                ChatHover hover = new ChatHover(
                        ChatHover.Kind.MESSAGE_TOOLBAR);
                hover.frame = frame;
                hover.toolbarKind = kind;
                return hover;
            }
        }
        for (int index = frames.size() - 1; index >= 0; index--) {
            ChatWindowFrame frame = frames.get(index);
            if (frame.scrollbarContains(x, y)) {
                ChatHover hover = new ChatHover(ChatHover.Kind.SCROLLBAR);
                hover.frame = frame;
                return hover;
            }
        }
        // A window that is not the one being typed in answers to a press
        // by becoming it, so all of it acts; the one being typed in
        // answers by cycling the stack, where another lies under it.
        ChatWindowFrame under = ChatWindowFrame.drawnAt(x, y);
        boolean focuses = under != null && bringsForward(under);
        boolean cycles = under != null && under == this.bar.activeFrame()
                && cycleTargetAt(x, y) != null;
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
            // A stamp or a delivery mark is part of its window and
            // answers a press as the window does; resting on one reads it
            // out.
            hover.stampLineId = under.stampLineAt(x, y);
            hover.markLineId = under.markLineAt(x, y);
            return hover;
        }
        return ChatHover.NONE;
    }

    /**
     * The tab row, or the bare stretch of one, under a point: the rows
     * front to back, each asked by the one hit test its own draw asks,
     * and nothing while a tab is under the hand or a window's edge is
     * being dragged.
     */
    private ChatHover rowHoverAt(double x, double y) {
        LostTalesGuiAnimationSample opening =
                ClientChatChannelViews.openSample();
        List<ChatWindow> windows = ChatWindowLayout.stacked();
        for (int index = windows.size() - 1; index >= 0; index--) {
            ChatWindow window = windows.get(index);
            ChatWindowFrame frame = frameFor(window);
            ChatChannelTabBar.Row row = rowFor(window, frame, opening);
            if (row == null) {
                continue;
            }
            ChatChannelTabBar.Hit hit = row.dragging != null || row.resizing
                    ? null : frame.tabBar.hitAt(this.fontRendererObj, row,
                            x, y);
            ChatSearchBar.Part part = hit != null || row.dragging != null
                    || row.resizing ? null
                    : this.searchBar.partAt(frame, row, x, y);
            if (part != null) {
                ChatHover hover = new ChatHover(ChatHover.Kind.SEARCH_BAR);
                hover.window = window;
                hover.frame = frame;
                hover.row = row;
                hover.searchPart = part;
                return hover;
            }
            if (hit == null && !frame.tabBar.stripContains(
                    this.fontRendererObj, row, x, y)) {
                continue;
            }
            ChatHover hover = new ChatHover(hit != null
                    ? ChatHover.Kind.TAB_ROW : ChatHover.Kind.STRIP);
            hover.window = window;
            hover.frame = frame;
            hover.row = row;
            hover.tabHit = hit;
            // Only the grip's own glyph offers the move tip; the bare
            // strip beside it drags without saying so.
            hover.overGrip = hit != null
                    && hit.kind == ChatChannelTabBar.HitKind.GRIP
                    && frame.tabBar.isOverGripHandle(this.fontRendererObj,
                            row, x, y);
            return hover;
        }
        return null;
    }

    /** Whether a press on the window brings it forward: it is not the one being typed in. */
    private static boolean bringsForward(ChatWindowFrame frame) {
        ChatWindow window = ChatWindowLayout.window(frame.windowId);
        ChatTab front = window == null ? null
                : ChatWindowFrame.activeTab(window,
                        ChatWindowFrame.visibleTabs(window));
        return front != null
                && !front.equals(ClientChatChannelState.getSelected());
    }

    /**
     * The pointer's pose for the frame just drawn: a held edge keeps its
     * resize wherever the pointer has gone, the way a pressed control
     * keeps its look; a drag keeps the arrow; otherwise the pose of what
     * the frame found under the pointer.
     */
    @Override
    public LostTalesMapCursor.Pose pointerPose(int mouseX, int mouseY) {
        ChatWindowGestures.ResizeEdge resizing =
                this.gestures.armedResizeEdge();
        if (resizing != null) {
            return ChatWindowGestures.cursorPose(resizing);
        }
        return this.gestures.isDragging() ? LostTalesMapCursor.Pose.ARROW
                : this.hover.pose();
    }

    /** The words beside the pointer for what it rests on, empty for none. */
    private String tipFor(ChatHover hover) {
        switch (hover.kind) {
            case TAB_ROW:
                return hover.tabHit == null || hover.window == null ? ""
                        : tipFor(hover.tabHit, hover.window, hover.overGrip);
            case SEARCH_BAR:
                return ChatSearchBar.tipFor(hover.searchPart);
            case EMPTY_PLUS:
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.tab.restore");
            case REPLY_CHIP:
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.message.cancel_reply");
            case MESSAGE_TOOLBAR:
                return StatCollector.translateToLocal(
                        hover.toolbarKind
                                == LostTalesChatOverlayRenderer.TOOLBAR_REACT
                                ? "gui.losttales.chat.message.react"
                                : hover.toolbarKind
                                        == LostTalesChatOverlayRenderer.TOOLBAR_REPLY
                                        ? "gui.losttales.chat.message.reply"
                                        : "gui.losttales.chat.message.copy");
            case WINDOW:
                return hover.markLineId != 0 ? markTip(hover.markLineId)
                        : stampTip(hover.stampLineId);
            case CHARACTER_BUTTON:
                // The hover shows the chosen identity's card instead of words.
                return "";
            case TOOLBAR_TOGGLE:
                return StatCollector.translateToLocal(
                        ChatWindowLayout.isToolbarCollapsed()
                                ? "gui.losttales.chat.toolbar.expand"
                                : "gui.losttales.chat.toolbar.collapse");
            default:
                return "";
        }
    }

    /**
     * The whole date and time a stamp stands for — the day of the week
     * included, as Discord reads out a message's time — or nothing where
     * no stamp is under the pointer.
     */
    private static String stampTip(int chatLineId) {
        Long said = chatLineId == 0 ? null
                : ClientChatChannelViews.timeOf(chatLineId);
        return said == null ? ""
                : ChatTimestampFormatter.formatFull(said.longValue());
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

    private static ChatWindowFrame frameFor(ChatWindow window) {
        return ChatWindowFrame.of(window);
    }

    /**
     * Whether no window has a tab the player can see. A valid state, not
     * an error: every channel still exists, still receives and still
     * counts its unread lines — none of them is being shown. The screen
     * offers them back rather than keeping one open to stand in for the
     * rest.
     */
    private static boolean isEmptyState() {
        return !ClientChatChannelState.hasVisibleWindow();
    }

    /**
     * What the screen shows with nothing open: one strip where the chat
     * would be, carrying a {@code +} that opens a channel, and a line
     * saying so. Placed and sized from the closed-chat feed's own box,
     * so it lands where the messages do at any resolution or GUI scale.
     */
    private void drawEmptyState(double mouseX, double mouseY) {
        ChatWindowPlacement.Box box = ChatWindowPlacement.feedBounds(
                this.mc, this.width, this.height);
        int left = (int)Math.round(box.x);
        int right = left + box.width;
        int bottom = (int)Math.round(box.baseline());
        int top = bottom - EMPTY_STATE_HEIGHT;
        LostTalesChatOverlayRenderer.drawBackdropRow(left, top, right,
                bottom, LostTalesChatOverlayRenderer.backdropRowAlpha(
                        this.mc));
        LostTalesChatOverlayRenderer.drawRule(left, right, top, top + 1,
                0xFF);
        LostTalesChatOverlayRenderer.drawRule(left, right, bottom - 1,
                bottom, 0xFF);
        this.emptyPlusLeft = left + EMPTY_STATE_PADDING;
        this.emptyPlusTop = top + (EMPTY_STATE_HEIGHT
                - ChatChannelTabBar.END_CONTROL_SIZE) / 2;
        this.emptyPlusRight = this.emptyPlusLeft
                + ChatChannelTabBar.END_CONTROL_SIZE;
        this.emptyPlusBottom = this.emptyPlusTop
                + ChatChannelTabBar.END_CONTROL_SIZE;
        boolean hovered = emptyStateContains(mouseX, mouseY);
        // The rules and the band are built from filled quads, which
        // leave blending off behind them; everything drawn after one
        // turns it back on for itself.
        LostTalesChatVisualStyle.beginContent();
        ChatIconSheet plus = hovered
                ? ChatIconSheet.PLUS_HOVER : ChatIconSheet.PLUS;
        plus.drawWithShadow(this.emptyPlusLeft
                        + (ChatChannelTabBar.END_CONTROL_SIZE
                                - plus.getWidth()) / 2,
                this.emptyPlusTop + (ChatChannelTabBar.END_CONTROL_SIZE
                        - plus.getHeight()) / 2, 0xFF);
        int textX = this.emptyPlusRight + EMPTY_STATE_GAP;
        int room = Math.max(0, right - EMPTY_STATE_PADDING - textX);
        LostTalesChatVisualStyle.drawColored(this.fontRendererObj,
                "§o" + this.fontRendererObj.trimStringToWidth(
                        StatCollector.translateToLocal(
                                "gui.losttales.chat.no_channels"), room),
                textX, top + (EMPTY_STATE_HEIGHT - 8) / 2,
                LostTalesChatVisualStyle.asideRgb(), 0xFF);
        this.regions.add(this.emptyPlusLeft, this.emptyPlusTop,
                this.emptyPlusRight, this.emptyPlusBottom);
    }

    /** Whether the point is on the empty state's + as drawn last frame. */
    private boolean emptyStateContains(double mouseX, double mouseY) {
        return ChatHitBox.contains(mouseX, mouseY, this.emptyPlusLeft,
                this.emptyPlusTop, this.emptyPlusRight - this.emptyPlusLeft,
                this.emptyPlusBottom - this.emptyPlusTop);
    }

    /** Whether the box crosses any of the boxes already drawn. */
    private static boolean overlapsAny(List<ChatWindowPlacement.Box> boxes,
                                       ChatWindowPlacement.Box box) {
        for (int index = 0; index < boxes.size(); index++) {
            ChatWindowPlacement.Box other = boxes.get(index);
            if (box.x < other.x + other.width
                    && box.x + box.width > other.x
                    && box.y < other.y + other.height
                    && box.y + box.height > other.y) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every window, back to front, each complete before the next: its
     * history — drawn here rather than in the HUD pass, so the open chat
     * lies above every HUD element and a front window covers the whole
     * of one behind it — then the tab row standing on its topmost band
     * (or on one empty line) and carrying the window's top rule as its
     * last pixel row, and the bottom rule over the shade. The row is the
     * window's title strip and is there while the window has a tab the
     * player can see. Every window but the active one — the window being
     * typed in — wears a resting bar under its bottom rule, drawn here
     * with the window; the active window's bar is the live one, drawn
     * once with the bar group after every window, so its pickers and
     * lists stand over them all.
     */
    private void drawWindows(int mouseX, int mouseY, double pointerX,
                             double pointerY, float partialTicks) {
        LostTalesGuiAnimationSample opening =
                ClientChatChannelViews.openSample();
        List<ChatWindow> windows = ChatWindowLayout.stacked();
        // The live bar's window: the one holding the selected channel,
        // else the first drawn, as the bar itself places it.
        ChatWindow typed = ChatWindowLayout.windowOf(
                ClientChatChannelState.getSelected());
        String activeBarId = typed == null ? null : typed.getId();
        // The search follows the input: moved to another window, it closes.
        ChatSearch.closeUnless(activeBarId);
        if (!ChatSearch.isOpen() && this.searchBar.isFocused()) {
            focusSearch(false);
        }
        boolean blurActive = LostTalesConfig.enableChatBackgroundBlur
                && LostTalesConfig.enableGuiBackgroundBlur;
        List<ChatWindowPlacement.Box> drawnBoxes = blurActive
                ? new ArrayList<ChatWindowPlacement.Box>(windows.size())
                : null;
        for (int index = 0; index < windows.size(); index++) {
            ChatWindow window = windows.get(index);
            if (drawnBoxes != null
                    && !ChatWindowFrame.visibleTabs(window).isEmpty()) {
                // A window over another one pastes its rectangle of the
                // blurred frame; captured before any window, that
                // rectangle holds only the world and would erase what
                // was just drawn behind it. Re-capturing here puts the
                // windows already drawn into the front window's blur, so
                // an overlapped window stays visible — softened —
                // behind the one in front.
                // Measured where the window stands this frame, a window
                // gliding to or from the screen included.
                ChatWindowFrame.of(window).advanceFill(window.getFill());
                ChatWindowPlacement.Box box = ChatWindowPlacement
                        .windowBounds(window, this.mc, this.width,
                                this.height);
                if (overlapsAny(drawnBoxes, box)) {
                    LostTalesGuiRegionBlur.getInstance().capture(this.mc,
                            partialTicks,
                            (float)LostTalesConfig.guiBlurStrength);
                }
                drawnBoxes.add(box);
            }
            LostTalesChatOverlayRenderer.drawWindowForScreen(this.mc, window,
                    this.width, this.height, opening);
            ChatWindowFrame frame = frameFor(window);
            if (activeBarId == null && frame.drawn) {
                activeBarId = window.getId();
            }
            ChatChannelTabBar.Row row = rowFor(window, frame, opening);
            if (row == null) {
                continue;
            }
            // The row is laid out in whole pixels and shifted by the
            // window's fractional remainder, so it sits exactly where the
            // lines do while the window glides. The row is told the same
            // remainder, since its scissors are cut outside this matrix.
            // The search bar lays itself out first, so the strip can
            // leave its well out of the surface it paints.
            this.searchBar.prepare(this.fontRendererObj, frame, row);
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(row.fractionX, row.fractionY, 0.0F);
                // Only the row the pointer is on sees it: a row under a
                // menu, a list or another window is drawn with the
                // pointer away.
                boolean onRow = this.hover.isOnRowOf(frame);
                frame.tabBar.draw(this.fontRendererObj, this.regions, row,
                        onRow ? pointerX : ChatHover.AWAY,
                        onRow ? pointerY : ChatHover.AWAY,
                        opening.getOpacity());
                if (ChatSearch.isOpenOn(window.getId())) {
                    landSearch(frame, ChatSearch.scan(this.mc, frame));
                    this.searchBar.draw(this.fontRendererObj, frame,
                            onRow ? pointerX : ChatHover.AWAY,
                            onRow ? pointerY : ChatHover.AWAY,
                            opening.getOpacity(),
                            this.hover.is(ChatHover.Kind.SEARCH_BAR)
                                    && this.hover.frame == frame
                                    ? this.hover.searchPart : null);
                }
            } finally {
                GL11.glPopMatrix();
            }
            drawTypingLine(window, frame, opening, mouseX, mouseY);
            LostTalesChatOverlayRenderer.drawBottomRule(this.mc, frame,
                    opening);
            LostTalesChatOverlayRenderer.drawWindowFrameSurface(this.mc,
                    frame, opening);
            LostTalesChatOverlayRenderer.drawWindowLeftEdge(this.mc, frame,
                    opening);
            LostTalesChatOverlayRenderer.drawWindowTopRightEdges(this.mc,
                    frame, opening);
            if (!window.getId().equals(activeBarId)) {
                this.bar.drawRestingBar(frame, window);
            }
        }
    }

    /**
     * The row description of a window for this frame, or null when the
     * window has no row to show right now.
     */
    private ChatChannelTabBar.Row rowFor(ChatWindow window,
                                         ChatWindowFrame frame,
                                         LostTalesGuiAnimationSample opening) {
        if (!frame.drawn) {
            return null;
        }
        List<ChatTab> tabs = ChatWindowFrame.visibleTabs(window);
        if (tabs.isEmpty()) {
            return null;
        }
        ChatChannelTabBar.Row row = new ChatChannelTabBar.Row();
        row.tabs = tabs;
        row.selected = ChatWindowFrame.activeTab(window, tabs);
        row.marked = ChatTabSelection.selectedIn(window);
        // Whole-pixel geometry of the drawn (motion included) position;
        // the fractional remainder is applied when the row is drawn.
        row.rowBottom = (int)Math.floor(frame.tabRowBottom());
        row.rowBottomExact = frame.tabRowBottom();
        row.fractionX = (float)(frame.drawnLeft()
                - Math.floor(frame.drawnLeft()));
        row.fractionY = (float)(row.rowBottomExact - row.rowBottom);
        row.left = (int)Math.floor(frame.drawnLeft()) + 2;
        row.right = (int)Math.floor(frame.drawnLeft()) + (int)Math.round(
                frame.boxRight - frame.boxLeft) - 2;
        // The edge as it really stands, so the tabs follow a resize by
        // the fraction the edge moves rather than a pixel at a time.
        row.rightExact = Math.floor(frame.drawnLeft())
                + (frame.boxRight - frame.boxLeft) - 2;
        row.offsetX = 0;
        row.locked = window.isLocked();
        row.moving = this.gestures.isMovingWindow(window.getId());
        row.resizing = this.gestures.isResizingWindow(window.getId());
        row.gliding = frame.isFillGliding();
        // A locked window keeps the tabs and the size it has, so it
        // offers neither a tab cross nor the window's own controls: they
        // are all refused anyway, and would only mislead.
        row.closable = ClientChatChannelState.isClosable(row.selected);
        row.windowControls = !window.isLocked();
        row.fullscreenShare = frame.fullShare();
        row.showRestore = !window.isLocked()
                && (!ChatScreenMenus.restorableChannels().isEmpty()
                        || ChatScreenMenus.hasWhisperCandidates(this.mc));
        row.closedUnread = row.showRestore
                ? ChatScreenMenus.closedUnreadCount() : 0;
        // The chevron says whether this row's own search panel is out,
        // so the control and the panel can never disagree about it.
        row.searchOpen = this.menus.isOpenFor(ChatScreenMenus.POPUP_SEARCH,
                window.getId());
        row.restoreOpen = this.menus.isOpenFor(
                ChatScreenMenus.POPUP_RESTORE, window.getId());
        ChatWindowGestures.TabDrag tabDrag = this.gestures.activeTabDrag();
        if (tabDrag != null && window.contains(tabDrag.tab)) {
            // The tab keeps its place in the row and leans toward the
            // pointer; the row has already reordered around it, so
            // there is nothing to mark an insertion point for.
            row.dragging = tabDrag.tab;
            // Everything travelling with it, so a marked group leans,
            // changes places and stops as one long tab.
            row.draggedGroup = tabDrag.group;
            row.draggedLeft = tabDrag.pointerX - tabDrag.grabOffsetX;
        }
        return row;
    }

    /**
     * The label a hovered row control offers. A tab names itself whole
     * whether or not the row cut its name short — the marquee in the tab
     * shows the rest of a cut name too, and the tip says it plainly; the
     * grip speaks only for its own glyph, so the empty strip that also
     * drags stays silent.
     */
    private static String tipFor(ChatChannelTabBar.Hit hit,
                                 ChatWindow window, boolean overGrip) {
        switch (hit.kind) {
            case TAB:
                return ClientChatChannelState.displayName(hit.tab);
            case CLOSE:
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.tab.close");
            case SETTINGS:
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.tab.settings");
            case LOCK:
                return StatCollector.translateToLocal(window.isLocked()
                        ? "gui.losttales.chat.tab.unlock"
                        : "gui.losttales.chat.tab.lock");
            case SEARCH:
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.search.tip");
            case WINDOW_SETTINGS:
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.window.settings");
            case WINDOW_FULLSCREEN:
                return StatCollector.translateToLocal(window.isFullscreen()
                        ? "gui.losttales.chat.window.exit_fullscreen"
                        : "gui.losttales.chat.window.fullscreen");
            case WINDOW_CLOSE:
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.window.close");
            case RESTORE:
                int unread = ChatScreenMenus.closedUnreadCount();
                return unread > 0
                        ? StatCollector.translateToLocalFormatted(
                                "gui.losttales.chat.tab.restore_unread",
                                unread > ClientChatChannelViews.MAX_UNREAD
                                        ? ClientChatChannelViews.MAX_UNREAD
                                                + "+"
                                        : String.valueOf(unread))
                        : StatCollector.translateToLocal(
                                "gui.losttales.chat.tab.restore");
            case GRIP:
                return overGrip ? StatCollector.translateToLocal(
                        "gui.losttales.chat.tab.move") : "";
            default:
                return "";
        }
    }

    /** Small label beside the pointer for a hovered row control. */
    private void drawHoverTip() {
        int tipWidth = this.fontRendererObj.getStringWidth(this.hoverTip) + 8;
        int x = Math.max(2, Math.min(this.width - tipWidth - 2,
                this.hoverTipX + 8));
        int y = this.hoverTipY - 16;
        if (y < 2) {
            y = this.hoverTipY + 12;
        }
        LostTalesChatVisualStyle.drawPopup(x, y, x + tipWidth, y + 12, 1.0F);
        LostTalesChatVisualStyle.drawPlain(this.fontRendererObj,
                this.hoverTip, x + 4, y + 2, 255);
    }

    /**
     * A click on another window's resting bar moves the input to that
     * window: the bar drawn there answers nothing itself, and pressing
     * it asks for the live bar to come there, which is what it then
     * does.
     */
    private ChatWindowFrame otherBarAt(double x, double barY) {
        ChatWindowFrame active = this.bar.activeFrame();
        List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
        for (int index = frames.size() - 1; index >= 0; index--) {
            ChatWindowFrame frame = frames.get(index);
            if (frame == active) {
                continue;
            }
            double top = frame.barTop();
            if (x >= frame.boxLeft && x < frame.boxRight && barY >= top
                    && barY < top + ChatWindowPlacement.INPUT_HEIGHT) {
                return frame;
            }
        }
        return null;
    }

    /** Moves the input to the window whose bar strip was pressed. */
    private void focusBarOf(ChatWindowFrame frame) {
        ChatWindow window = ChatWindowLayout.window(frame.windowId);
        ChatTab front = window == null ? null
                : ChatWindowFrame.activeTab(window,
                        ChatWindowFrame.visibleTabs(window));
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
        if (hovered == null) {
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
                this.renderToolTip(stack, mouseX, mouseY);
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
                lines.add(EnumChatFormatting.ITALIC + quest.category);
            }
            if (quest.objective.length() > 0) lines.add(quest.objective);
            if (quest.reward.length() > 0) {
                lines.add(EnumChatFormatting.GRAY + "Rewards: " + quest.reward);
            }
            lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(
                    quest.joinable ? "gui.losttales.chat.quest.join"
                            : "gui.losttales.chat.quest.view_only"));
            LostTalesChatHoverCard.drawTextCard(this.mc, lines, mouseX, mouseY,
                    this.width, this.height);
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
                this.width, this.height);
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
                this.renderToolTip(stack, mouseX, mouseY);
            } else {
                LostTalesChatHoverCard.drawTextCard(this.mc, Collections.singletonList(
                        EnumChatFormatting.RED + "Invalid Item!"),
                        mouseX, mouseY, this.width, this.height);
            }
        } else if (hoverEvent.getAction() == HoverEvent.Action.SHOW_TEXT) {
            LostTalesChatHoverCard.drawTextCard(this.mc, Splitter.on("\n").splitToList(
                    hoverEvent.getValue().getFormattedText()),
                    mouseX, mouseY, this.width, this.height);
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
                        this.width, this.height);
            } else {
                LostTalesChatHoverCard.drawTextCard(this.mc, Collections.singletonList(
                        EnumChatFormatting.RED + "Invalid statistic/achievement!"),
                        mouseX, mouseY, this.width, this.height);
            }
        }
    }

    /**
     * A press inside an open picker's panel: its own controls first,
     * then a cell inserts its token, and a right-click on an emoji or a
     * marker cell marks it as a favourite.
     */
    private void clickPickerPanel(ChatHover press, int mouseX,
                                  int adjustedMouseY, double x, double y,
                                  int button) {
        ChatPickerPanel picker = press.picker;
        int barRight = this.bar.inputBarRight();
        int pickerAnchor = this.bar.pickerAnchor();
        double barX = x - this.bar.fractionX();
        double barY = y - this.bar.fractionY() - this.bar.entranceOffset();
        if (picker.mouseClicked(mouseX, adjustedMouseY, barX, barY, button,
                barRight, pickerAnchor)) {
            return;
        }
        ChatPickerPanel.Entry entry = press.pickerEntry;
        if (button == 0 && entry != null && picker == this.bar.emojiPicker()
                && this.bar.emojiPicker().reactionTarget() != ChatMessageIds.NONE) {
            // Opened to react to a message: the pick is the reaction,
            // and nothing is written into the field.
            ChatEmoji emoji = (ChatEmoji)entry.value;
            sendReaction(this.bar.emojiPicker().reactionTarget(), emoji, true);
            ChatEmojiUsageStore.recordUse(emoji);
            this.bar.closePickers();
        } else if (button == 0 && entry != null) {
            this.completion.insertToken(picker.insertionText(entry));
            this.inputField.setFocused(true);
        } else if (button == 1 && picker == this.bar.emojiPicker()) {
            this.bar.emojiPicker().toggleFavoriteAt(barX, barY, barRight,
                    pickerAnchor);
        } else if (button == 1 && picker == this.bar.markerPicker()) {
            this.bar.markerPicker().toggleFavoriteAt(barX, barY, barRight,
                    pickerAnchor);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        this.pressCount++;
        // What a press lands on is found at the pointer's exact position,
        // the way the hover finds it; whole pixels only for the input
        // field and for where a menu opens.
        double x = pointerX();
        double y = pointerY();
        int adjustedMouseY = mouseY - Math.round(this.bar.entranceOffset());
        // A card a click opened takes a press on itself and nothing
        // happens; a press anywhere else closes it and goes on to
        // whatever is under it, so another name opens its own card at
        // once.
        if (LostTalesChatHoverCard.isPinned()) {
            if (LostTalesChatHoverCard.pinnedContains(x, y)) {
                return;
            }
            LostTalesChatHoverCard.unpin();
        }
        // An open menu takes the press first; a press beside it closes
        // it and goes on to whatever is under it, which is told which
        // menu it closed so a switch does not reopen what it put away.
        ChatScreenMenus.Click menuClick = this.menus.click(x, y, button);
        syncChatIdentity();
        String closedPopupKind = menuClick.closedKind;
        if (menuClick.command != null) {
            sendCommand(menuClick.command);
        }
        if (menuClick.consumed) {
            return;
        }
        if (isEmptyState()) {
            // The + is the whole of the screen's furniture here; a click
            // anywhere else is the player closing what is not there.
            if (button == 0 && emptyStateContains(x, y)) {
                this.menus.openRestorePopup(null, mouseX,
                        this.emptyPlusTop - 2);
            }
            return;
        }
        ChatHover press = resolveHover(x, y);
        // A press anywhere but the search bar hands the keys back to
        // the input; the search stays open with its matches lit.
        if (this.searchBar.isFocused() && !press.is(ChatHover.Kind.SEARCH_BAR)) {
            focusSearch(false);
        }
        switch (press.kind) {
            case SEARCH_BAR:
                clickSearchBar(press, x, y, button);
                return;
            case SUGGESTION:
                if (button == 0) {
                    this.completion.accept(press.suggestion);
                }
                return;
            case SUGGESTIONS:
                return;
            case PICKER_CELL:
            case PICKER_LABEL:
            case PICKER:
                clickPickerPanel(press, mouseX, adjustedMouseY, x, y, button);
                return;
            case RESIZE:
                if (button == 0) {
                    ChatWindow edgeWindow = ChatWindowLayout.window(
                            press.resize.frame.windowId);
                    if (edgeWindow != null) {
                        this.gestures.armResize(press.resize, edgeWindow,
                                mouseX, mouseY);
                    }
                    return;
                }
                break;
            default:
                break;
        }
        // The rows: the top resize border meets the row's band along the
        // window's edge, and only a left press there resizes; any other
        // press there is the row's.
        ChatHover onRow = press.is(ChatHover.Kind.TAB_ROW)
                || press.is(ChatHover.Kind.STRIP) ? press
                : press.is(ChatHover.Kind.RESIZE) ? rowHoverAt(x, y) : null;
        if (onRow != null) {
            if (button == 0) {
                handleRowClick(onRow, mouseX, mouseY, closedPopupKind);
            } else if (button == 1) {
                handleRowRightClick(onRow, mouseX);
            } else if (button == 2) {
                closeTabFrom(onRow);
            }
            return;
        }
        if (button == 0) {
            // The marks are the row's; a press anywhere else lets them go.
            ChatTabSelection.clear();
        }
        switch (press.kind) {
            case OTHER_BAR:
                if (button == 0) {
                    focusBarOf(press.frame);
                }
                return;
            case CHARACTER_BUTTON:
                // A click on the button while its own menu was open has
                // just closed it above; only then does the click not
                // reopen it.
                if (button == 0 && !ChatScreenMenus.POPUP_CHARACTERS.equals(
                        closedPopupKind)) {
                    this.menus.openCharacterSelectionMenu(
                            this.bar.characterButtonLeft(),
                            this.bar.characterButtonTop() - 2);
                }
                return;
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
                return;
            case SEND_BUTTON:
                if (button == 0) {
                    submitInput();
                }
                return;
            case TOOLBAR_TOGGLE:
                if (button == 0) {
                    // Folding the inserts takes their panels with them;
                    // the emoji picker is outside the fold and keeps
                    // its own.
                    this.bar.closeInsertPickers();
                    ChatWindowLayout.setToolbarCollapsed(
                            !ChatWindowLayout.isToolbarCollapsed());
                }
                return;
            case PICKER_BUTTON:
                if (button == 0) {
                    boolean open = press.picker.isOpen();
                    this.bar.closePickers();
                    press.picker.setOpen(!open);
                }
                return;
            default:
                break;
        }
        // Anything past the bar closes an open picker and goes on.
        if (button == 0 && this.bar.openPicker() != null) {
            this.bar.closePickers();
        }
        if (press.is(ChatHover.Kind.OVERLAY)) {
            // An overlay owns this spot even when nothing on it was hit;
            // the message stack underneath must not receive the press.
            return;
        }
        if (button == 0) {
            switch (press.kind) {
                case JUMP_PILL:
                    ClientChatChannelViews.scrollHome(press.frame.view);
                    return;
                case REPLY_CHIP:
                    this.composer.cancelComposing(this.inputField);
                    return;
                case MESSAGE_TOOLBAR:
                    clickMessageToolbar(press);
                    return;
                case SCROLLBAR:
                    if (this.gestures.grabScrollbar(x, y)) {
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
            LostTalesChatHoverCard.Found person =
                    press.is(ChatHover.Kind.LINE) ? press.person : null;
            if (person != null) {
                if (this.menus.openPlayerPopup(person.target, mouseX, mouseY)) {
                    return;
                }
            } else if (this.menus.openMessagePopup(mouseX, mouseY)) {
                return;
            }
        }
        if (button == 0) {
            ChatWindowFrame frame = ChatWindowFrame.drawnAt(x, y);
            ChatWindow window = frame == null ? null
                    : ChatWindowLayout.window(frame.windowId);
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
                ChatWindowLayout.raise(window.getId());
                this.tabActions.selectWindow(window);
            }
            if (clickLines(press, mouseX, adjustedMouseY, button)) {
                return;
            }
            // Nothing in the window answered, and the window was already
            // the one in front: the press cycles the stack instead.
            if (alreadyInFront) {
                cycleWindowsAt(x, y);
            }
            return;
        }
        clickLines(press, mouseX, adjustedMouseY, button);
    }

    /**
     * Sends the window in front at this point to the back and brings the
     * one under it forward, moving the input with it — so pressing the
     * same empty spot again and again walks through everything stacked
     * there and comes back round.
     */
    private void cycleWindowsAt(double x, double y) {
        ChatWindowFrame front = ChatWindowFrame.drawnAt(x, y);
        ChatWindowFrame under = cycleTargetAt(x, y);
        ChatWindow behind = under == null ? null
                : ChatWindowLayout.window(under.windowId);
        if (front == null || behind == null) {
            return;
        }
        ChatWindowLayout.lower(front.windowId);
        this.tabActions.selectWindow(behind);
    }

    /**
     * The window a press at this point brings forward by cycling the
     * stack: the one under the window in front here, while the point is
     * in the front window's messages; else null, for a window standing
     * on its own or a point outside its messages. The pointer's hand and
     * the press both ask this.
     */
    private static ChatWindowFrame cycleTargetAt(double x, double y) {
        List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
        ChatWindowFrame front = null;
        for (int index = frames.size() - 1; index >= 0; index--) {
            ChatWindowFrame frame = frames.get(index);
            if (!frame.contains(x, y)) {
                continue;
            }
            if (front != null) {
                return ChatWindowLayout.window(frame.windowId) == null
                        ? null : frame;
            }
            // Only the message area cycles. The strips and the grip move
            // the window and the bar is the input; what is left is the
            // history, which is where the player is pointing when they
            // mean "the one behind this".
            if (y < frame.historyTop() || y >= frame.barTop()) {
                return null;
            }
            front = frame;
        }
        return null;
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
        if (button == 0 && press.is(ChatHover.Kind.LINE)) {
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
     * The hovered message's own controls: reply to it, or copy it. The
     * same two the message's menu offers, acting on the message the
     * toolbar was drawn for rather than on whatever lies under the
     * pointer now — the toolbar covers its own message, so the two
     * agree, but the id is the one the draw recorded either way.
     */
    private boolean clickMessageToolbar(ChatHover press) {
        ChatWindowFrame frame = press.frame;
        if (frame == null || press.toolbarKind < 0) {
            return false;
        }
        int chatLineId = frame.toolbarChatLineId;
        if (press.toolbarKind == LostTalesChatOverlayRenderer.TOOLBAR_REACT) {
            openReactionPicker(ClientChatMessageIds.messageIdOf(chatLineId));
        } else if (press.toolbarKind
                == LostTalesChatOverlayRenderer.TOOLBAR_REPLY) {
            replyToLine(frame, chatLineId);
        } else if (LostTalesChatClipboard.copy(
                messageTextOf(frame, chatLineId))) {
            showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.copied"));
        }
        return true;
    }

    /** The whole body of the message drawn on {@code chatLineId}. */
    private static String messageTextOf(ChatWindowFrame frame,
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
    private void replyToLine(ChatWindowFrame frame, int chatLineId) {
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
     * hovered while a menu is open or something is being dragged: the
     * pointer is on that, whatever lies under it.
     */
    private int hoveredMessageLine(float pointerX, float pointerY) {
        if (this.menus.isOpen()) {
            // The menu has the pointer, but the message it was opened
            // over keeps its shade while the menu stands, so what the
            // menu acts on stays in sight.
            return this.menus.messageMenuChatLineId();
        }
        if (this.gestures.isDragging()) {
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
        if (!ChatWindowLayout.isOpen(tab)) {
            tab = ChatWindowLayout.openTab(tab, band.frame.windowId);
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
    private static void landOn(ChatWindowFrame frame, int index,
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
        for (ChatWindowFrame frame : ChatWindowFrame.drawnFrames()) {
            int index = firstRowOf(frame.lines, chatLineId);
            if (index >= 0) {
                LostTalesChatPresentation.clearPendingJump();
                landOn(frame, index, chatLineId);
                return;
            }
        }
    }

    /* ---- Tab rows: clicks, drags, docking, detaching, window moves ---- */

    /**
     * A middle press on a tab closes it, the way browser tabs close;
     * anywhere else on a row is swallowed so the click does not fall
     * through to the lines. Same guarded close as the cross.
     */
    private boolean closeTabFrom(ChatHover press) {
        if (press.tabHit != null && press.tabHit.tab != null) {
            this.tabActions.closeChannel(press.tabHit.tab);
        }
        return true;
    }

    /**
     * A right press on one of the rows: on a tab — its name, its cog or
     * its cross — it opens the tab's menu where the cog would; anywhere
     * else on the strip — the bare stretch, the grip, the end controls —
     * it opens the window's own menu where the window's cog would, on
     * the same terms as that cog: a locked window offers neither. The
     * press belongs to that window like any other, and is spent on the
     * strip either way — a strip is not a line and opens no message menu.
     */
    private boolean handleRowRightClick(ChatHover press, int mouseX) {
        ChatWindow window = press.window;
        ChatChannelTabBar.Row row = press.row;
        if (window == null || row == null) {
            return false;
        }
        int anchorBottom = ChatChannelTabBar.rowTop(row.rowBottom) - 2;
        this.tabActions.selectWindow(window);
        if (press.tabHit != null && press.tabHit.tab != null) {
            this.menus.openSettingsPopup(press.tabHit.tab, mouseX,
                    anchorBottom);
        } else if (!window.isLocked()) {
            this.menus.openWindowPopup(window, mouseX, anchorBottom);
        }
        return true;
    }

    /**
     * A left press on one of the rows. Picking a tab also arms a drag;
     * the controls act at once. Shift marks the tab instead of picking
     * it, so several tabs of one row can be moved or closed together.
     * {@code closedPopupKind} names the menu this same press has just
     * closed, if any, so a control whose own menu was open reads as
     * closing it rather than reopening it.
     */
    private boolean handleRowClick(ChatHover press, int mouseX, int mouseY,
                                   String closedPopupKind) {
        ChatWindow window = press.window;
        ChatWindowFrame frame = press.frame;
        ChatChannelTabBar.Row row = press.row;
        ChatChannelTabBar.Hit hit = press.tabHit;
        if (window == null || frame == null || row == null) {
            return false;
        }
        if (hit == null) {
            // The strip itself belongs to the window, and moves it; a
            // double click on it lets the window fill the screen or gives
            // the screen back, as a title bar does.
            ChatTabSelection.clear();
            this.tabActions.selectWindow(window);
            if (!window.isLocked()) {
                pressStrip(window, frame, mouseX, mouseY);
            }
            return true;
        }
        // Anything on a window's strip is a click on that window,
        // so the input moves there whichever control was pressed.
        this.tabActions.selectWindow(window);
        switch (hit.kind) {
            case TAB:
                if (isShiftKeyDown()) {
                    // Marking a tab does not bring it forward: what
                    // is being typed stays where it was, and a set
                    // being started is seeded with it, so what is
                    // marked always includes the tab in front.
                    ChatTabSelection.toggle(window.getId(),
                            ChatWindowFrame.activeTab(window, row.tabs),
                            hit.tab);
                    return true;
                }
                // A press on a tab already in a group keeps the
                // group: the drag it may start is the group's, and
                // a press that never travels collapses it on the
                // release instead.
                List<ChatTab> group =
                        ChatWindowGestures.draggedGroup(window, hit.tab);
                boolean kept = group.size() > 1;
                // Everything the window holds — its only tab, or all
                // of them marked at once — has nothing to be taken
                // out of and nowhere to be reordered: the tabs are
                // the window's title bar, so the drag begins already
                // torn off into the window they are in and the
                // window itself is what moves. Carrying it onto
                // another window's row docks the tabs there, the way
                // a browser merges a window back into another.
                boolean wholeWindow =
                        group.size() >= window.getTabs().size();
                if (!kept) {
                    ChatTabSelection.selectOnly(window.getId(), hit.tab);
                }
                this.tabActions.selectChannel(hit.tab);
                if (!window.isLocked()) {
                    this.gestures.armTabDrag(window, frame, row,
                            hit.tab, group, mouseX, mouseY, kept,
                            wholeWindow);
                } else if (kept) {
                    // A locked row starts no drag, so the press is
                    // only ever a pick.
                    ChatTabSelection.selectOnly(window.getId(), hit.tab);
                }
                return true;
            case CLOSE:
                this.tabActions.closeChannel(hit.tab);
                return true;
            case SETTINGS:
                this.menus.openSettingsPopup(hit.tab, mouseX,
                        ChatChannelTabBar.rowTop(row.rowBottom) - 2);
                return true;
            case LOCK:
                this.tabActions.setWindowLocked(window, !window.isLocked());
                return true;
            case RESTORE:
                // A switch like the search control beside it: a
                // press with the list already out puts it away.
                if (!ChatScreenMenus.POPUP_RESTORE.equals(
                        closedPopupKind)) {
                    this.menus.openRestorePopup(window.getId(), mouseX,
                            ChatChannelTabBar.rowTop(row.rowBottom) - 2);
                }
                return true;
            case SEARCH:
                // The control is a switch: a press with its own
                // panel already out has just closed it above, and
                // only then does the press not open it again.
                if (!ChatScreenMenus.POPUP_SEARCH.equals(
                        closedPopupKind)) {
                    this.menus.openSearchPanel(window, mouseX,
                            ChatChannelTabBar.rowTop(row.rowBottom) - 2,
                            -1, -1);
                }
                return true;
            case WINDOW_SETTINGS:
                this.menus.openWindowPopup(window, mouseX,
                        ChatChannelTabBar.rowTop(row.rowBottom) - 2);
                return true;
            case WINDOW_FULLSCREEN:
                this.tabActions.setWindowFill(window, window.isFullscreen()
                        ? ChatWindow.ScreenFill.NONE
                        : ChatWindow.ScreenFill.FULL);
                return true;
            case WINDOW_CLOSE:
                this.tabActions.closeWindow(window);
                return true;
            case GRIP:
                if (!window.isLocked()) {
                    pressStrip(window, frame, mouseX, mouseY);
                }
                return true;
            default:
                return true;
        }
    }

    /**
     * A press on a window's bare strip or grip: the first of a double
     * click takes hold of the window to move it; the second — the very
     * next press, on the same window, within {@link #DOUBLE_CLICK_NANOS}
     * and a drag's threshold of the first — lets it fill the screen or
     * gives the screen back.
     */
    private void pressStrip(ChatWindow window, ChatWindowFrame frame,
                            int mouseX, int mouseY) {
        long now = System.nanoTime();
        boolean second = window.getId().equals(this.stripPressWindowId)
                && this.stripPressNumber == this.pressCount - 1
                && now - this.stripPressNanos <= DOUBLE_CLICK_NANOS
                && Math.abs(mouseX - this.stripPressX)
                        <= ChatWindowGestures.DRAG_THRESHOLD
                && Math.abs(mouseY - this.stripPressY)
                        <= ChatWindowGestures.DRAG_THRESHOLD;
        if (second) {
            // A double click is spent by its second press; a third
            // press starts another.
            this.stripPressWindowId = null;
            this.tabActions.setWindowFill(window, window.isFullscreen()
                    ? ChatWindow.ScreenFill.NONE
                    : ChatWindow.ScreenFill.FULL);
            return;
        }
        this.stripPressWindowId = window.getId();
        this.stripPressNumber = this.pressCount;
        this.stripPressNanos = now;
        this.stripPressX = mouseX;
        this.stripPressY = mouseY;
        this.gestures.armWindowDrag(frame, mouseX, mouseY, true);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY,
                                  int clickedMouseButton,
                                  long timeSinceLastClick) {
        if (clickedMouseButton == 0 && this.gestures.onDragMove(mouseX, mouseY)) {
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton,
                timeSinceLastClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            ChatWindowGestures.WindowDrag click = this.gestures.onRelease();
            if (click != null) {
                // Never moved: the press was a click on the lines.
                clickLines(resolveHover(pointerX(), pointerY()),
                        click.pressX, click.pressY
                        - Math.round(this.bar.entranceOffset()), 0);
            }
        }
        super.mouseMovedOrUp(mouseX, mouseY, mouseButton);
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
                        com.ninuna.losttales.network.LostTalesNetworkHandler.CHANNEL
                                .sendToServer(new com.ninuna.losttales.network.packet.LostTalesQuestShareJoinPacket(
                                        quest.messageId, quest.tokenIndex));
                    } else {
                        this.mc.displayGuiScreen(
                                new com.ninuna.losttales.gui.screen.LostTalesQuestJournalGui(this));
                    }
                }
                return true;
            }
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
        if (isShiftKeyDown()) {
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
                this.func_146403_a(value);
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
        LostTalesChatHoverCard.pin(person.target,
                (int)ChatWindowPlacement.preciseMouseX(this.mc, this.width),
                (int)ChatWindowPlacement.preciseMouseY(this.mc, this.height));
    }

    /** The person a hit stands on, read from the frame when it is the frame's hit. */
    private LostTalesChatHoverCard.Found personOf(
            LostTalesChatOverlayRenderer.Hit hit) {
        return LostTalesChatHoverCard.locate(this.mc, hit);
    }

    /**
     * Opens the emoji picker to react to a message: a pick is sent as a
     * reaction to it rather than written into the field. Only while the
     * picker itself is offered.
     */
    private void openReactionPicker(long messageId) {
        if (!ChatMessageIds.isServerId(messageId)
                || !this.bar.isPickerShown(this.bar.emojiPicker())) {
            return;
        }
        this.bar.closePickers();
        this.bar.emojiPicker().openForReaction(messageId);
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
                this.width, this.height);
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
            }
        }
        if (tab == null) {
            showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.channel.gone"));
            return;
        }
        if (!ChatWindowLayout.isOpen(tab)) {
            tab = ChatWindowLayout.openTab(tab,
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
                        new GuiConfirmOpenLink(this, value, 0, false));
            } else {
                browseTo(uri);
            }
        } catch (URISyntaxException ignored) {
        }
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        if (id == 0) {
            if (result && this.clickedLinkUri != null) {
                browseTo(this.clickedLinkUri);
            }
            this.clickedLinkUri = null;
            this.mc.displayGuiScreen(this);
            return;
        }
        super.confirmClicked(result, id);
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

    /**
     * The run of the lines under a GUI-space point, as the chat's own
     * hover finds it: none while anything drawn above the lines has the
     * point. What the vanilla hit-test hook hands another mod asking
     * where the pointer is, so LOTR's achievement card among others
     * stays silent under a menu.
     */
    public LostTalesChatOverlayRenderer.Hit lineHitAt(double x, double y) {
        ChatHover under = resolveHover(x, y);
        return under.is(ChatHover.Kind.LINE) ? under.line : null;
    }

    /** A short confirmation above the bar: what the bar draws. */
    private void showNotice(String text) {
        this.bar.showNotice(text);
    }

    /** Where the empty state's {@code +} stands, or -1 with a window open. */
    private int emptyPlusAnchorX() {
        return isEmptyState() ? this.emptyPlusLeft : -1;
    }

    private int emptyPlusAnchorBottom() {
        return isEmptyState() ? this.emptyPlusTop - 2 : -1;
    }
}
