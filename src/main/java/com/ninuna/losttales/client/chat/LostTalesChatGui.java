package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMentionCandidate;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.client.mapmarker.LostTalesMapCursor;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiRegionBlur;
import com.ninuna.losttales.client.mapmarker.LostTalesLotrMapGui;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatCommandContextPacket;
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
 * {@link ChatWindowLayout}, the same model the HUD placement editor
 * edits, so the two never disagree. Every overlay registers the rectangle
 * it draws in {@link ChatPointerRegions}; hover, tooltip, and click
 * handling consult that record before touching the message stack, so
 * whatever is painted on top is also what owns the pointer.
 */
public final class LostTalesChatGui extends GuiChat {
    /**
     * Message lines one notch of the wheel moves. Short, because the
     * view glides to the new offset rather than jumping to it: a long
     * step would arrive before the eye could follow it. Shift still
     * moves one line at a time.
     */
    private static final int WHEEL_LINES = 2;
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
    /** The suggestion lists, command completion and token insertion. */
    private final ChatInputCompletion completion =
            new ChatInputCompletion(this.notices);
    /** The selection, and the verbs that open, close, lock and move tabs. */
    private final ChatTabActions tabActions = new ChatTabActions(this.bar,
            this.completion, this.composer, this.notices);
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
    private URI clickedLinkUri;
    private boolean openAnimationStarted;
    /** The tab-strip control under the pointer as the windows were drawn. */
    private ChatChannelTabBar.Hit hoveredRowHit;
    /** The empty state's + as drawn this frame; width zero while none was. */
    private int emptyPlusLeft;
    private int emptyPlusTop;
    private int emptyPlusRight;
    private int emptyPlusBottom;

    public LostTalesChatGui(String defaultText) {
        super(defaultText == null ? "" : defaultText);
    }

    @Override
    public void initGui() {
        ClientChatChannelState.ensureAvailable();
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
        this.bar.tickPickers();
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

    /**
     * Closing without sending keeps the text for the next opening; once
     * something has been sent the draft is spent.
     */
    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        // A screen closed mid-drag ends the drag where it stands: this
        // instance is gone and nothing else would ever release it.
        this.gestures.cancelDrags();
        if (!isEmptyState()) {
            ClientChatChannelState.setDraft(
                    this.sent ? "" : this.inputField.getText());
        }
        ClientChatChannelViews.setScrollEasingSuppressed(false);
        // Every divider that was on a viewed tab has done its job.
        ClientChatChannelViews.dismissSeenDividers();
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
        // A bubble before the words, on the caps' own middle, and the
        // line itself in the asides' tone rather than the message ivory.
        ChatIconSheet bubble = ChatIconSheet.SPEECH_BUBBLE;
        bubble.drawWithShadow(x, y + (7 - bubble.getHeight()) / 2, alpha);
        int textX = x + bubble.getWidth() + TYPING_BUBBLE_GAP;
        LostTalesChatVisualStyle.drawColored(this.fontRendererObj,
                "§o" + this.fontRendererObj.trimStringToWidth(text,
                        room - (textX - x)),
                textX, y, ChatComposer.ASIDE_RGB, alpha);
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

    /**
     * Sends what is in the field — Enter and the arrow button both end
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
        // the mouse in window pixels and scaled to the screen.
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height
                / this.mc.displayHeight - 1;
        // One turn of the wheel is one distance everywhere in the chat:
        // vanilla's step in whole lines, one line with Shift, in pixels
        // — the history, a menu's rows and a picker's cells all move by
        // it, each turning it into its own units.
        int wheelPixels = (wheel > 0 ? 1 : -1)
                * LostTalesChatOverlayRenderer.LINE_HEIGHT
                * (isShiftKeyDown() ? 1 : WHEEL_LINES);
        // A wheel turn over the open menu scrolls its rows.
        if (this.menus.isOpen() && this.menus.contains(mouseX, mouseY)) {
            this.menus.scrollBy(-wheelPixels
                    / (double)ChatPopupMenu.ROW_HEIGHT);
            return;
        }
        // A wheel turn over an open picker scrolls that picker's list;
        // anywhere else it scrolls the history as before.
        ChatPickerPanel picker = this.bar.openPicker();
        if (picker != null) {
            int adjustedMouseY = mouseY
                    - Math.round(this.bar.entranceOffset());
            if (picker.isInsidePanel(mouseX, adjustedMouseY,
                    this.bar.inputBarRight(), this.bar.pickerAnchor())) {
                picker.scrollBy(-wheelPixels);
                return;
            }
        }
        // Vanilla scrolled its own (now unused) offset above; the visible
        // history scrolls per channel view instead, in the window under
        // the pointer (the main one elsewhere).
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
        this.bar.noteInputWindow();
        this.bar.refreshPickers();
        // The bars enter from below on their own curve while the rest of
        // the window rides the shared opening motion; everything drawn
        // with a bar is hit in the bar's own space.
        float entrance = this.bar.entranceOffset();
        int entranceOffset = Math.round(entrance);
        int adjustedMouseY = mouseY - entranceOffset;
        this.regions.reset();
        this.hoverTip = "";
        // The frame as drawn so far — world and HUD — is captured and
        // blurred once, before anything of the chat is on it; each
        // window then pastes its own rectangle of the result under its
        // backdrop while the rest of the screen stays sharp.
        if (LostTalesConfig.enableChatBackgroundBlur
                && LostTalesConfig.enableGuiBackgroundBlur) {
            LostTalesGuiRegionBlur.getInstance().capture(this.mc,
                    partialTicks, (float)LostTalesConfig.guiBlurStrength);
        }
        this.gestures.advance(mouseX, mouseY);
        LostTalesChatPresentation.beginFrame();
        LostTalesChatPresentation.setHoveredLine(
                hoveredMessageLine(mouseX, mouseY));
        LostTalesChatOverlayRenderer.Hit hovered =
                hoveredComponent(mouseX, mouseY);
        LostTalesChatPresentation.setHoveredComponent(
                hovered == null ? null : hovered.line,
                hovered == null ? -1 : hovered.index,
                hovered == null ? null : hovered.component);
        this.gestures.markScrollbarsWanted(mouseX, mouseY);
        drawWindows(mouseX, mouseY, partialTicks);
        landPendingJump();
        if (!this.gestures.isDragging()) {
            List<ChatWindowFrame> pillFrames = ChatWindowFrame.drawnFrames();
            for (int index = pillFrames.size() - 1; index >= 0; index--) {
                ChatWindowFrame frame = pillFrames.get(index);
                if (frame.jumpPillContains(mouseX, mouseY)) {
                    this.hoverTip = StatCollector.translateToLocal(
                            "gui.losttales.chat.jump_to_present");
                    this.hoverTipX = mouseX;
                    this.hoverTipY = mouseY;
                    break;
                }
                if (frame.contains(mouseX, mouseY)) {
                    break;
                }
            }
            // The reply chip's cross, and then the message toolbar's
            // controls, which are drawn as glyphs and say nothing on
            // their own. Asked in the order a click resolves them —
            // after the jump-to-present button, which is drawn over
            // both where they meet, and from the front window back — so
            // the tip always names what a click would actually reach.
            if (this.hoverTip.length() == 0
                    && this.composer.chipContains(mouseX, mouseY)) {
                this.hoverTip = StatCollector.translateToLocal(
                        "gui.losttales.chat.message.cancel_reply");
                this.hoverTipX = mouseX;
                this.hoverTipY = mouseY;
            }
            if (this.hoverTip.length() == 0) {
                nameToolbarControl(mouseX, mouseY);
            }
        }
        // The field follows the active window's bar as just drawn.
        this.bar.updateInputBounds();
        this.menus.refreshRestorePopup();
        this.menus.registerRegion(this.regions);
        if (isEmptyState()) {
            // Nothing is open to type into, so the screen shows what it
            // has instead of a bar with no channel behind it. The bar's
            // pickers go with the bar.
            if (this.bar.openPicker() != null) {
                this.bar.closePickers();
            }
            drawEmptyState(mouseX, mouseY);
            this.bar.drawNotice();
            this.menus.draw(this.regions, mouseX, mouseY);
            if (this.hoverTip.length() > 0 && !this.menus.isOpen()) {
                drawHoverTip();
            }
            updatePointerFeedback(mouseX, mouseY);
            return;
        }
        int barRight = this.bar.inputBarRight();
        int anchor = this.bar.inputAnchor();
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(this.bar.fractionX(),
                    this.bar.fractionY() + entrance, 0.0F);
            this.bar.drawBar(barRight);
            boolean characterHovered = this.bar.drawCharacterSelectionButton(
                    mouseX, adjustedMouseY, this.menus.isKindOpen(
                            ChatScreenMenus.POPUP_CHARACTERS));
            if (characterHovered && !this.gestures.isDragging()) {
                this.hoverTip = StatCollector.translateToLocal(
                        "gui.losttales.chat.character_selection.tip");
                this.hoverTipX = mouseX;
                this.hoverTipY = mouseY;
            }
            this.bar.drawIndicator(mouseX, adjustedMouseY);
            this.bar.drawToolbarToggle(barRight, mouseX, adjustedMouseY);
            if (this.bar.isInsideToolbarToggle(mouseX, adjustedMouseY,
                    barRight) && !this.gestures.isDragging()) {
                this.hoverTip = StatCollector.translateToLocal(
                        ChatWindowLayout.isToolbarCollapsed()
                                ? "gui.losttales.chat.toolbar.expand"
                                : "gui.losttales.chat.toolbar.collapse");
                this.hoverTipX = mouseX;
                this.hoverTipY = mouseY;
            }
            this.bar.drawPickers(barRight, mouseX, adjustedMouseY);
            this.bar.drawSendDivider(barRight);
            this.bar.drawSendButton(barRight, mouseX, adjustedMouseY);
            this.bar.drawCounter(barRight);
            this.completion.draw(anchor, this.inputField.xPosition,
                    mouseX, adjustedMouseY);
        } finally {
            GL11.glPopMatrix();
        }
        // The pointer's exact GUI position: hover resolves against the
        // same fractional coordinate the drawn cursor tip stands on, so
        // a hitbox never reads shifted by the integer conversion's
        // truncation.
        float pointerX = (float)ChatWindowPlacement.preciseMouseX(
                this.mc, this.width);
        float pointerY = (float)ChatWindowPlacement.preciseMouseY(
                this.mc, this.height);
        if (!this.regions.contains(mouseX, mouseY)) {
            drawChatLineHover(pointerX, pointerY, mouseX, mouseY);
        }
        this.bar.drawNotice();
        ChatMentionCandidate hoveredCandidate = this.completion.hoveredMention(
                mouseX, adjustedMouseY, this.bar.inputAnchor(),
                this.inputField.xPosition);
        if (hoveredCandidate != null) {
            LostTalesChatHoverCard.drawForCandidate(this.mc,
                    hoveredCandidate, mouseX, mouseY,
                    this.width, this.height);
        } else if (!this.regions.contains(mouseX, mouseY)) {
            LostTalesChatHoverCard.draw(this.mc, pointerX, pointerY,
                    this.width, this.height);
        }
        this.gestures.drawLinkHighlight();
        this.menus.draw(this.regions, mouseX, mouseY);
        ChatPopupMenu.Entry hoveredLock =
                this.menus.lockControlAt(mouseX, mouseY);
        if (hoveredLock != null && !this.gestures.isDragging()) {
            // The lock in the character selection menu answers with the
            // same tip its states answer with everywhere else.
            this.hoverTip = StatCollector.translateToLocal(
                    ClientChatAppearances.isLocked(
                            ClientChatChannelState.getSelected())
                            ? "gui.losttales.chat.character_selection.unlock"
                            : "gui.losttales.chat.character_selection.lock");
            this.hoverTipX = mouseX;
            this.hoverTipY = mouseY;
            drawHoverTip();
        }
        if (hoverTip.length() > 0 && !this.menus.isOpen()) {
            drawHoverTip();
        }
        updatePointerFeedback(mouseX, mouseY);
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
    private void drawEmptyState(int mouseX, int mouseY) {
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
                LostTalesColors.rgb(LostTalesColors.ROSE_BEIGE), 0xFF);
        this.regions.add(this.emptyPlusLeft, this.emptyPlusTop,
                this.emptyPlusRight, this.emptyPlusBottom);
        if (hovered && !this.menus.isOpen()) {
            this.hoverTip = StatCollector.translateToLocal(
                    "gui.losttales.chat.tab.restore");
            this.hoverTipX = mouseX;
            this.hoverTipY = mouseY;
        }
    }

    /** Whether the point is on the empty state's + as drawn last frame. */
    private boolean emptyStateContains(int mouseX, int mouseY) {
        return this.emptyPlusRight > this.emptyPlusLeft
                && mouseX >= this.emptyPlusLeft && mouseX < this.emptyPlusRight
                && mouseY >= this.emptyPlusTop && mouseY < this.emptyPlusBottom;
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
     * player can see. No window draws an input section of its own: there
     * is one, it belongs to the window being typed in, and it is drawn
     * once with the bar group wherever it has travelled to.
     */
    private void drawWindows(int mouseX, int mouseY, float partialTicks) {
        LostTalesGuiAnimationSample opening =
                ClientChatChannelViews.openSample();
        List<ChatWindow> windows = ChatWindowLayout.stacked();
        ChatChannelTabBar.Hit hoveredHit = null;
        ChatWindow hoveredWindow = null;
        boolean hoveredGrip = false;
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
            ChatChannelTabBar.Row row = rowFor(window, frame, opening);
            if (row == null) {
                continue;
            }
            // The row is laid out in whole pixels and shifted by the
            // window's fractional remainder, so it sits exactly where the
            // lines do while the window glides. The row is told the same
            // remainder, since its scissors are cut outside this matrix.
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(row.fractionX, row.fractionY, 0.0F);
                frame.tabBar.draw(this.fontRendererObj, this.regions, row,
                        mouseX, mouseY, opening.getOpacity());
            } finally {
                GL11.glPopMatrix();
            }
            ChatChannelTabBar.Hit hit = frame.tabBar.hitAt(
                    this.fontRendererObj, row, mouseX, mouseY);
            if (hit != null) {
                hoveredHit = hit;
                hoveredWindow = window;
                // Only the grip's own glyph offers the move tip; the
                // bare strip beside it drags without saying so.
                hoveredGrip = frame.tabBar.isOverGripHandle(
                        this.fontRendererObj, row, mouseX, mouseY);
            }
            drawTypingLine(window, frame, opening, mouseX, mouseY);
            LostTalesChatOverlayRenderer.drawBottomRule(this.mc, frame,
                    opening);
            LostTalesChatOverlayRenderer.drawWindowLeftEdge(this.mc, frame,
                    opening);
        }
        this.hoveredRowHit = hoveredHit;
        if (hoveredHit != null && !this.gestures.isDragging()) {
            this.hoverTip = tipFor(hoveredHit, hoveredWindow, hoveredGrip);
            this.hoverTipX = mouseX;
            this.hoverTipY = mouseY;
        }
    }

    /**
     * The pointer says what a press would do. Asked once the whole chat
     * has been drawn, so it reads the same complete set of regions
     * {@link #mouseClicked} does and cannot promise something a press
     * would not start. A resize in progress keeps saying so wherever the
     * pointer has gone, the way a pressed control keeps its look.
     */
    private void updatePointerFeedback(int mouseX, int mouseY) {
        ChatWindowGestures.ResizeEdge resizing =
                this.gestures.armedResizeEdge();
        if (resizing != null) {
            LostTalesMapCursor.requestPose(
                    ChatWindowGestures.cursorPose(resizing));
            return;
        }
        if (this.gestures.isDragging()) {
            return;
        }
        ChatWindowGestures.ResizeTarget target =
                ChatWindowGestures.resizeUnderPointer(mouseX, mouseY,
                        this.regions);
        if (target != null) {
            LostTalesMapCursor.requestPose(
                    ChatWindowGestures.cursorPose(target.edge));
        } else if (isOverInteractable(mouseX, mouseY, this.hoveredRowHit)) {
            // The chat's own controls are drawn, not vanilla widgets, so
            // it says for itself what answers to a click.
            LostTalesMapCursor.requestPose(LostTalesMapCursor.Pose.HAND);
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
        // A locked window keeps the tabs and the size it has, so it
        // offers neither a tab cross nor the window's own controls: they
        // are all refused anyway, and would only mislead.
        row.closable = ClientChatChannelState.isClosable(row.selected);
        row.windowControls = !window.isLocked();
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
        drawRect(x, y, x + tipWidth, y + 12,
                LostTalesChatVisualStyle.SURFACE);
        LostTalesChatVisualStyle.drawPlain(this.fontRendererObj,
                this.hoverTip, x + 4, y + 2, 255);
    }

    /**
     * A click in the strip below another window's bottom rule moves the
     * input to that window. Nothing is drawn there — the input section
     * belongs to the window being typed in and to no other — but the
     * room is still part of the window's box, and pressing it asks for
     * the bar to come there, which is what the bar then does.
     */
    private boolean handleBarClick(int mouseX, int rawMouseY) {
        int mouseY = rawMouseY - Math.round(this.bar.entranceOffset());
        ChatWindowFrame active = this.bar.activeFrame();
        List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
        for (int index = frames.size() - 1; index >= 0; index--) {
            ChatWindowFrame frame = frames.get(index);
            if (frame == active) {
                continue;
            }
            double left = frame.boxLeft;
            double top = frame.barTop();
            if (mouseX >= left && mouseX < frame.boxRight
                    && mouseY >= top
                    && mouseY < top + ChatWindowPlacement.INPUT_HEIGHT) {
                ChatWindow window = ChatWindowLayout.window(frame.windowId);
                ChatTab front = window == null ? null
                        : ChatWindowFrame.activeTab(window,
                                ChatWindowFrame.visibleTabs(window));
                if (front != null) {
                    this.tabActions.selectChannel(front);
                }
                return true;
            }
        }
        return false;
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
    private void drawChatLineHover(float hitX, float hitY,
                                   int drawMouseX, int drawMouseY) {
        LostTalesChatOverlayRenderer.Hit hovered =
                LostTalesChatOverlayRenderer.hitAt(this.mc, hitX, hitY);
        if (hovered == null) {
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
    private void clickPickerPanel(ChatPickerPanel picker, int mouseX,
                                  int adjustedMouseY, int button) {
        int barRight = this.bar.inputBarRight();
        int pickerAnchor = this.bar.pickerAnchor();
        if (picker.mouseClicked(mouseX, adjustedMouseY, button, barRight,
                pickerAnchor)) {
            return;
        }
        ChatPickerPanel.Entry entry = picker.entryAt(mouseX, adjustedMouseY,
                barRight, pickerAnchor);
        if (button == 0 && entry != null) {
            this.completion.insertToken(picker.insertionText(entry));
            this.inputField.setFocused(true);
        } else if (button == 1 && picker == this.bar.emojiPicker()) {
            this.bar.emojiPicker().toggleFavoriteAt(mouseX, adjustedMouseY,
                    barRight, pickerAnchor);
        } else if (button == 1 && picker == this.bar.markerPicker()) {
            this.bar.markerPicker().toggleFavoriteAt(mouseX, adjustedMouseY,
                    barRight, pickerAnchor);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        int adjustedMouseY = mouseY - Math.round(this.bar.entranceOffset());
        // An open menu takes the press first; a press beside it closes
        // it and goes on to whatever is under it, which is told which
        // menu it closed so a switch does not reopen what it put away.
        ChatScreenMenus.Click menuClick = this.menus.click(mouseX, mouseY,
                button);
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
            if (button == 0 && emptyStateContains(mouseX, mouseY)) {
                this.menus.openRestorePopup(null, mouseX,
                        this.emptyPlusTop - 2);
            }
            return;
        }
        int barRight = this.bar.inputBarRight();
        int anchor = this.bar.inputAnchor();
        if (this.completion.click(mouseX, adjustedMouseY, button, anchor,
                this.inputField.xPosition)) {
            return;
        }
        ChatPickerPanel picker = this.bar.openPicker();
        if (picker != null && picker.isInsidePanel(mouseX, adjustedMouseY,
                barRight, this.bar.pickerAnchor())) {
            clickPickerPanel(picker, mouseX, adjustedMouseY, button);
            return;
        }
        // Pickers and completion lists paint above the tab rows, so they
        // are asked first. The resize border comes next: it overlaps the
        // tab strip along the window's top edge, and the pointer shows a
        // resize there, so a press there must resize.
        if (button == 0) {
            ChatWindowGestures.ResizeTarget edge =
                    ChatWindowGestures.resizeUnderPointer(mouseX, mouseY,
                            this.regions);
            ChatWindow edgeWindow = edge == null ? null
                    : ChatWindowLayout.window(edge.frame.windowId);
            if (edgeWindow != null) {
                this.gestures.armResize(edge, edgeWindow, mouseX, mouseY);
                return;
            }
        }
        if (button == 0) {
            if (handleRowClick(mouseX, mouseY, closedPopupKind)) {
                return;
            }
            // The marks are the row's; a press anywhere else lets them go.
            ChatTabSelection.clear();
        }
        // A right-click on a tab opens the tab's own menu, the one the
        // cog opens. Asked here, beside the left press on the rows and
        // before the overlay guard below: the strip registers the
        // rectangle it painted, so a press on it never reaches the
        // message menus further down.
        if (button == 1 && handleRowRightClick(mouseX, mouseY)) {
            return;
        }
        if (button == 2 && closeTabAt(mouseX, mouseY)) {
            return;
        }
        if (button == 0 && handleBarClick(mouseX, mouseY)) {
            return;
        }
        if (button == 0 && this.bar.isInsideCharacterButton(mouseX,
                adjustedMouseY)) {
            // A click on the button while its own menu was open has just
            // closed it above; only then does the click not reopen it.
            if (!ChatScreenMenus.POPUP_CHARACTERS.equals(closedPopupKind)) {
                this.menus.openCharacterSelectionMenu(
                        this.bar.characterButtonLeft(),
                        this.bar.barControlTop() - 2);
            }
            return;
        }
        if (button == 0 && this.bar.isInsideIndicator(mouseX, adjustedMouseY)) {
            this.tabActions.selectChannel(ClientChatChannelState.cycle());
            return;
        }
        if (button == 0 && this.bar.isInsideSendButton(mouseX, adjustedMouseY,
                barRight)) {
            submitInput();
            return;
        }
        if (button == 0 && this.bar.isInsideToolbarToggle(mouseX,
                adjustedMouseY, barRight)) {
            // Folding the inserts takes their panels with them; the
            // emoji picker is outside the fold and keeps its own.
            this.bar.closeInsertPickers();
            ChatWindowLayout.setToolbarCollapsed(
                    !ChatWindowLayout.isToolbarCollapsed());
            return;
        }
        if (button == 0) {
            ChatPickerPanel candidate = this.bar.pickerButtonAt(mouseX,
                    adjustedMouseY, barRight);
            if (candidate != null) {
                boolean open = candidate.isOpen();
                this.bar.closePickers();
                candidate.setOpen(!open);
                return;
            }
        }
        if (button == 0 && picker != null) {
            // Clicks inside the panel were consumed above; anything else
            // closes the picker and is processed normally.
            this.bar.closePickers();
        }
        if (this.regions.contains(mouseX, mouseY)) {
            // An overlay owns this spot even when no row was hit; the
            // message stack underneath must not receive the click.
            return;
        }
        if (button == 0) {
            // The jump-to-newest pill floats over the stack; a click on
            // it glides the scrolled view home. Front to back, so the
            // front window's pill wins where windows overlap.
            List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
            for (int index = frames.size() - 1; index >= 0; index--) {
                ChatWindowFrame frame = frames.get(index);
                if (frame.jumpPillContains(mouseX, mouseY)) {
                    ClientChatChannelViews.scrollHome(frame.view);
                    return;
                }
                if (frame.contains(mouseX, mouseY)) {
                    break;
                }
            }
            if (this.composer.chipContains(mouseX, mouseY)) {
                this.composer.cancelComposing(this.inputField);
                return;
            }
            if (clickMessageToolbar(mouseX, mouseY)) {
                return;
            }
            if (this.gestures.grabScrollbar(mouseX, mouseY)) {
                return;
            }
        }
        // A right-click on a person — the sender's identity span or a
        // mention, wherever the card shows — opens that person's own
        // menu: message them, ignore them. Anywhere else on a line it
        // opens the message's menu: reply, copy, edit, delete.
        if (button == 1) {
            if (LostTalesChatHoverCard.isPointerOnPerson(this.mc,
                    mouseX, mouseY)) {
                if (this.menus.openPlayerPopup(mouseX, mouseY)) {
                    return;
                }
            } else if (this.menus.openMessagePopup(mouseX, mouseY)) {
                return;
            }
        }
        if (button == 0) {
            ChatWindowFrame frame = ChatWindowFrame.drawnAt(mouseX, mouseY);
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
            if (clickLines(mouseX, mouseY, adjustedMouseY, button)) {
                return;
            }
            // Nothing in the window answered, and the window was already
            // the one in front: the press cycles the stack instead.
            if (alreadyInFront) {
                cycleWindowsAt(mouseX, mouseY);
            }
            return;
        }
        clickLines(mouseX, mouseY, adjustedMouseY, button);
    }

    /**
     * Sends the window in front at this point to the back and brings the
     * one under it forward, moving the input with it — so pressing the
     * same empty spot again and again walks through everything stacked
     * there and comes back round. Answers whether there was a stack to
     * cycle: a window standing on its own, or a point outside its
     * messages, is left alone.
     */
    private boolean cycleWindowsAt(int mouseX, int mouseY) {
        List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
        ChatWindowFrame front = null;
        ChatWindowFrame under = null;
        for (int index = frames.size() - 1; index >= 0; index--) {
            ChatWindowFrame frame = frames.get(index);
            if (!frame.contains(mouseX, mouseY)) {
                continue;
            }
            if (front == null) {
                front = frame;
            } else {
                under = frame;
                break;
            }
        }
        // Only the message area cycles. The strip and the grip move the
        // window, the bar is the input, and both have already had this
        // press; what is left is the history, which is where the player
        // is pointing when they mean "the one behind this".
        if (front == null || under == null
                || mouseY < front.tabRowBottom()
                || mouseY >= front.barTop()) {
            return false;
        }
        ChatWindow behind = ChatWindowLayout.window(under.windowId);
        if (behind == null) {
            return false;
        }
        ChatWindowLayout.lower(front.windowId);
        this.tabActions.selectWindow(behind);
        return true;
    }

    /**
     * Component clicks on the lines, then the input field's own click.
     * Answers whether something on the lines took the press.
     */
    private boolean clickLines(int mouseX, int mouseY, int adjustedMouseY,
                               int button) {
        // Whole-pixel press coordinates sample the pixel's centre, the
        // best estimate of where inside it the pointer actually was.
        // The quote a reply opens with goes first: it is the one click
        // that acts on the window rather than on what it points at, and
        // it answers whatever the chat-links option says.
        if (button == 0 && jumpToQuotedMessage(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && handleComponentClick(
                LostTalesChatOverlayRenderer.hitAt(
                        this.mc, mouseX + 0.5F, mouseY + 0.5F))) {
            return true;
        }
        // GuiChat's own component handling relies on GuiNewChat's 9px hit
        // testing, which does not match the 11px layout; only the input
        // field needs the vanilla click path.
        this.inputField.mouseClicked(mouseX, adjustedMouseY, button);
        return false;
    }

    /**
     * Names the toolbar control under the pointer, if it is on one: what
     * a glyph would say if it could. The same words the message's menu
     * offers, so the two ways to the same action read the same.
     */
    private void nameToolbarControl(int mouseX, int mouseY) {
        List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
        for (int index = frames.size() - 1; index >= 0; index--) {
            int kind = frames.get(index).toolbarKindAt(mouseX, mouseY);
            if (kind < 0) {
                continue;
            }
            this.hoverTip = StatCollector.translateToLocal(
                    kind == LostTalesChatOverlayRenderer.TOOLBAR_REPLY
                            ? "gui.losttales.chat.message.reply"
                            : "gui.losttales.chat.message.copy");
            this.hoverTipX = mouseX;
            this.hoverTipY = mouseY;
            return;
        }
    }

    /**
     * The hovered message's own controls: reply to it, or copy it. The
     * same two the message's menu offers, acting on the message the
     * toolbar was drawn for rather than on whatever lies under the
     * pointer now — the toolbar covers its own message, so the two
     * agree, but the id is the one the draw recorded either way.
     */
    private boolean clickMessageToolbar(int mouseX, int mouseY) {
        List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
        for (int index = frames.size() - 1; index >= 0; index--) {
            ChatWindowFrame frame = frames.get(index);
            if (!frame.toolbarContains(mouseX, mouseY)) {
                continue;
            }
            int chatLineId = frame.toolbarChatLineId;
            int kind = frame.toolbarKindAt(mouseX, mouseY);
            if (kind == LostTalesChatOverlayRenderer.TOOLBAR_REPLY) {
                replyToLine(frame, chatLineId);
            } else if (LostTalesChatClipboard.copy(
                    messageTextOf(frame, chatLineId))) {
                showNotice(StatCollector.translateToLocal(
                        "gui.losttales.chat.copied"));
            }
            return true;
        }
        return false;
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
        this.composer.startReply(tab, id, name, excerpt);
    }

    /**
     * The message the pointer rests on, by chat line id, or zero for
     * none. Resolved against the bands the last frame recorded, which is
     * what every other pointer question here asks; a hover a frame
     * behind the pointer is not something an eye can catch. Nothing is
     * hovered while a menu is open or something is being dragged: the
     * pointer is on that, whatever lies under it.
     */
    /**
     * The component under the pointer, or null: read the same way the
     * hover card is, so what is underlined is exactly what a click
     * would reach.
     */
    private LostTalesChatOverlayRenderer.Hit hoveredComponent(int mouseX,
                                                             int mouseY) {
        if (this.menus.isOpen() || this.gestures.isDragging()) {
            return null;
        }
        return LostTalesChatOverlayRenderer.hitAt(this.mc,
                mouseX + 0.5F, mouseY + 0.5F);
    }

    private int hoveredMessageLine(int mouseX, int mouseY) {
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
                LostTalesChatOverlayRenderer.bandAt(this.mc,
                        mouseX + 0.5F, mouseY + 0.5F);
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
    private boolean jumpToQuotedMessage(int mouseX, int mouseY) {
        LostTalesChatOverlayRenderer.Hit hit =
                LostTalesChatOverlayRenderer.hitAt(this.mc,
                        mouseX + 0.5F, mouseY + 0.5F);
        if (hit == null || !ChatReplyMarker.isMarker(hit.component)) {
            return false;
        }
        LostTalesChatOverlayRenderer.Band band =
                LostTalesChatOverlayRenderer.bandAt(this.mc,
                        mouseX + 0.5F, mouseY + 0.5F);
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
     * Whether the point is on something the chat answers to: a tab or
     * one of its controls, the lock, the restore control, the grip, the
     * channel indicator, the send arrow, a picker button or an open
     * overlay of one. The input bar's own field is not a control — a
     * caret belongs there — but everything the player clicks is.
     *
     * <p>A window's own furniture is not taken as interactive merely
     * because it was painted: the strip of a locked window is drawn like
     * any other and answers to nothing, and its grip does not drag it.
     * What is asked here is what a press would actually do, in the same
     * order {@link #mouseClicked} resolves it, so the pointer cannot
     * promise a grab the window would refuse.</p>
     */
    private boolean isOverInteractable(int mouseX, int mouseY,
                                       ChatChannelTabBar.Hit hovered) {
        if (hovered != null) {
            return true;
        }
        // Only what is drawn above the windows counts as painted-is-live;
        // the windows themselves are asked about below.
        if (this.regions.containsOverlay(mouseX, mouseY)) {
            return true;
        }
        int barRight = this.bar.inputBarRight();
        if (this.bar.isInsideIndicator(mouseX, mouseY)
                || this.bar.isInsideSendButton(mouseX, mouseY, barRight)) {
            return true;
        }
        int anchor = this.bar.pickerAnchor();
        for (ChatPickerPanel picker : this.bar.pickers()) {
            if (this.bar.isPickerShown(picker) && picker.isInsideButton(
                    mouseX, mouseY, barRight, anchor)) {
                return true;
            }
        }
        if (this.bar.isInsideToolbarToggle(mouseX, mouseY, barRight)) {
            return true;
        }
        // The furniture the history draws over itself: the pill that
        // takes a scrolled view home, the hovered message's own
        // controls, and the scrollbar a drag takes hold of. All three
        // are recorded from the draw, so asking them here is asking the
        // same question the click asks.
        List<ChatWindowFrame> drawnFrames = ChatWindowFrame.drawnFrames();
        for (int index = drawnFrames.size() - 1; index >= 0; index--) {
            ChatWindowFrame drawnFrame = drawnFrames.get(index);
            if (drawnFrame.jumpPillContains(mouseX, mouseY)
                    || drawnFrame.toolbarContains(mouseX, mouseY)
                    || drawnFrame.scrollbarContains(mouseX, mouseY)) {
                return true;
            }
            if (drawnFrame.contains(mouseX, mouseY)) {
                break;
            }
        }
        // What the lines themselves act on: a reply's quote jumps to the
        // message it names, a covered spoiler reveals, a shared marker
        // flies the map, a link opens. The hit is the very one a click
        // resolves, so the pointer promises exactly what a press would
        // do — and nothing for spans that only consume the click, a
        // name or a mention, whose answer is the hover card already
        // showing.
        LostTalesChatOverlayRenderer.Hit lineHit =
                LostTalesChatOverlayRenderer.hitAt(this.mc, mouseX + 0.5F,
                        mouseY + 0.5F);
        if (lineHit != null && actsOnClick(lineHit.component)) {
            return true;
        }
        // A window that is not the one being typed in answers to a
        // click by becoming it, so the pointer says so over all of it.
        ChatWindowFrame frame = ChatWindowFrame.drawnAt(mouseX, mouseY);
        if (frame == null) {
            return false;
        }
        ChatWindow window = ChatWindowLayout.window(frame.windowId);
        ChatTab front = window == null ? null
                : ChatWindowFrame.activeTab(window,
                        ChatWindowFrame.visibleTabs(window));
        if (front != null
                && !front.equals(ClientChatChannelState.getSelected())) {
            return true;
        }
        // The window already being typed in: its strip is a handle only
        // while the window can be moved by it. A locked window's strip,
        // grip and all, is inert, and the pointer says so.
        return window != null && !window.isLocked()
                && isOnWindowStrip(frame, mouseY);
    }

    /**
     * Whether a click on the run would actually do something, which is
     * what earns the pointer's hand: the checks mirror
     * {@link #handleComponentClick} and {@link #jumpToQuotedMessage},
     * leaving out the spans that only consume the press.
     */
    private boolean actsOnClick(IChatComponent component) {
        if (component == null) {
            return false;
        }
        if (ChatReplyMarker.isMarker(component)
                || ChatChannelLinkMarker.isMarker(component)) {
            return true;
        }
        if (ChatSpoilerMarker.isMarker(component)
                && !ChatSpoilerMarker.isRevealed(component)) {
            return true;
        }
        ChatShowcaseMarker.Data share = ChatShowcaseMarker.decode(component);
        if (share != null && share.kind == ChatShareKind.MARKER) {
            return this.mc.gameSettings.chatLinks;
        }
        ClickEvent event = component.getChatStyle() == null ? null
                : component.getChatStyle().getChatClickEvent();
        return event != null
                && event.getAction() == ClickEvent.Action.OPEN_URL
                && this.mc.gameSettings.chatLinks;
    }

    /** Whether a screen y falls in the window's tab strip as drawn. */
    private static boolean isOnWindowStrip(ChatWindowFrame frame,
                                           int mouseY) {
        int rowBottom = (int)Math.floor(frame.tabRowBottom());
        return mouseY >= ChatChannelTabBar.rowTop(rowBottom)
                && mouseY < rowBottom;
    }

    /**
     * A middle press on a tab closes it, the way browser tabs close;
     * anywhere else on a row is swallowed so the click does not fall
     * through to the lines. Same guarded close as the cross.
     */
    private boolean closeTabAt(int mouseX, int mouseY) {
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
            ChatChannelTabBar.Hit hit = frame.tabBar.hitAt(
                    this.fontRendererObj, row, mouseX, mouseY);
            if (hit != null && hit.tab != null) {
                this.tabActions.closeChannel(hit.tab);
                return true;
            }
            if (hit != null || (ChatChannelTabBar.inRowBand(row, mouseY)
                    && mouseX >= frame.boxLeft
                    && mouseX < frame.boxRight)) {
                return true;
            }
        }
        return false;
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
    private boolean handleRowRightClick(int mouseX, int mouseY) {
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
            ChatChannelTabBar.Hit hit = frame.tabBar.hitAt(
                    this.fontRendererObj, row, mouseX, mouseY);
            if (hit == null && !(ChatChannelTabBar.inRowBand(row, mouseY)
                    && mouseX >= frame.boxLeft
                    && mouseX < frame.boxRight)) {
                continue;
            }
            int anchorBottom = ChatChannelTabBar.rowTop(row.rowBottom) - 2;
            this.tabActions.selectWindow(window);
            if (hit != null && hit.tab != null) {
                this.menus.openSettingsPopup(hit.tab, mouseX, anchorBottom);
            } else if (!window.isLocked()) {
                this.menus.openWindowPopup(window, mouseX, anchorBottom);
            }
            return true;
        }
        return false;
    }

    /**
     * A left press on one of the rows. Picking a tab also arms a drag;
     * the controls act at once. Shift marks the tab instead of picking
     * it, so several tabs of one row can be moved or closed together.
     * {@code closedPopupKind} names the menu this same press has just
     * closed, if any, so a control whose own menu was open reads as
     * closing it rather than reopening it.
     */
    private boolean handleRowClick(int mouseX, int mouseY,
                                   String closedPopupKind) {
        LostTalesGuiAnimationSample opening =
                ClientChatChannelViews.openSample();
        List<ChatWindow> windows = ChatWindowLayout.stacked();
        // Front to back: the window drawn on top is hit first.
        for (int index = windows.size() - 1; index >= 0; index--) {
            ChatWindow window = windows.get(index);
            ChatWindowFrame frame = frameFor(window);
            ChatChannelTabBar.Row row = rowFor(window, frame, opening);
            if (row == null) {
                continue;
            }
            ChatChannelTabBar.Hit hit = frame.tabBar.hitAt(
                    this.fontRendererObj, row, mouseX, mouseY);
            if (hit == null) {
                if (ChatChannelTabBar.inRowBand(row, mouseY)
                        && mouseX >= frame.boxLeft
                        && mouseX < frame.boxRight) {
                    // The strip itself belongs to the window, and moves it.
                    ChatTabSelection.clear();
                    this.tabActions.selectWindow(window);
                    if (!window.isLocked()) {
                        this.gestures.armWindowDrag(frame, mouseX, mouseY,
                                true);
                    }
                    return true;
                }
                continue;
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
                case WINDOW_CLOSE:
                    this.tabActions.closeWindow(window);
                    return true;
                case GRIP:
                    if (!window.isLocked()) {
                        this.gestures.armWindowDrag(frame, mouseX, mouseY,
                                true);
                    }
                    return true;
                default:
                    return true;
            }
        }
        return false;
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
                clickLines(click.pressX, click.pressY, click.pressY
                        - Math.round(this.bar.entranceOffset()), 0);
            }
        }
        super.mouseMovedOrUp(mouseX, mouseY, mouseButton);
    }

    /**
     * Vanilla component-click behaviour resolved against this mod's line
     * layout. The head marker acts as the sender like the visible name
     * does; a shared marker opens the map on its location; colour, emoji,
     * and item markers are internal metadata and never a user-facing
     * action.
     */
    private boolean handleComponentClick(
            LostTalesChatOverlayRenderer.Hit hit) {
        if (hit == null) {
            return false;
        }
        // A spoiler answers before anything else, and whatever the
        // chat-links option says: revealing covered text is the chat's
        // own furniture, not a link. The click is spent either way, so
        // the marker's carrier can never fall through to the input.
        if (ChatSpoilerMarker.isMarker(hit.component)) {
            ChatSpoilerMarker.reveal(hit.component);
            return true;
        }
        // A channel named as a link is the chat's own furniture too: the
        // tab comes forward, and the line it names is landed on.
        ChatChannelLinkMarker.Data channelLink =
                ChatChannelLinkMarker.decode(hit.component);
        if (channelLink != null) {
            openChannelLink(channelLink);
            return true;
        }
        if (!this.mc.gameSettings.chatLinks) {
            return false;
        }
        if (ChatHeadMarker.isMarker(hit.component)) {
            // A person is not a link: hovering names them, and what to
            // do about them is the message menu's to offer.
            return true;
        }
        if (ChatMentionMarker.decode(hit.component) != null) {
            return true;
        }
        ChatShowcaseMarker.Data share =
                ChatShowcaseMarker.decode(hit.component);
        if (share != null) {
            if (share.kind == ChatShareKind.MARKER) {
                ClientChatShowcaseStore.Marker marker =
                        ClientChatShowcaseStore.getMarker(share.showcaseId);
                if (marker != null) {
                    LostTalesLotrMapGui.openFocusedOn(marker.id,
                            marker.dimensionId, marker.x, marker.z);
                }
            }
            return true;
        }
        if (ChatColorMarker.isMarker(hit.component)
                || ChatPrefixMarker.isMarker(hit.component)
                || ChatEmojiMarker.isMarker(hit.component)
                || ChatTitleMarker.isMarker(hit.component)
                || ChatReplyMarker.isMarker(hit.component)
                // The chevron a body opens with is the chat's own
                // punctuation: it is drawn, but it answers to nothing.
                || ChatBodyMarker.isMarker(hit.component)
                // The indent a wrapped line hangs under, and the gap an
                // icon reserves: both are drawn, both are wide enough to
                // be hit, and neither is anything to act on. Without
                // them the click below would read their payload as a
                // suggestion and paste it over whatever was being typed.
                || ChatLayoutMarker.isMarker(hit.component)
                || ChatSpacerMarker.isMarker(hit.component)) {
            return true;
        }
        ClickEvent event =
                hit.component.getChatStyle().getChatClickEvent();
        if (event == null) {
            return false;
        }
        if (isShiftKeyDown()) {
            this.inputField.writeText(
                    hit.component.getUnformattedTextForChat());
            return true;
        }
        if (event.getAction() == ClickEvent.Action.SUGGEST_COMMAND
                && event.getValue().startsWith(ChatSenderSpan.WHISPER_PREFIX)) {
            // The sender's own name and brackets: a person, not a link.
            return true;
        } else if (event.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
            this.inputField.setText(event.getValue());
        } else if (event.getAction() == ClickEvent.Action.RUN_COMMAND) {
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
        } else if (event.getAction() == ClickEvent.Action.OPEN_URL) {
            openChatLink(event.getValue());
        }
        return true;
    }

    /**
     * Follows a channel link: the tab it names comes forward — opened
     * again in the selected tab's window if it was closed — and the
     * line it names, if any, is landed on once the tab is drawn. A tab
     * that is nobody's any more says so.
     */
    private void openChannelLink(ChatChannelLinkMarker.Data link) {
        ChatTab tab = ChatTab.fromId(link.tabId);
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
        if (link.chatLineId != 0) {
            LostTalesChatPresentation.requestJump(link.chatLineId);
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
     * Whether an overlay drawn this frame owns the given GUI-space point.
     * Used by the vanilla hit-test hook so third-party chat hover cards
     * (LOTR's achievement card among them) stay silent under popups.
     */
    public boolean isPointerOwnedByOverlay(int mouseX, int mouseY) {
        return this.regions.contains(mouseX, mouseY);
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
