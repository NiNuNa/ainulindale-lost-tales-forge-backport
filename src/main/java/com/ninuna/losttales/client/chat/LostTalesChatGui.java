package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatIdentityType;
import com.ninuna.losttales.chat.ChatMentionCandidate;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatNameSuggester;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareReference;
import com.ninuna.losttales.chat.share.ChatShareSuggester;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.mapmarker.LostTalesMapCursor;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiRegionBlur;
import com.ninuna.losttales.client.mapmarker.LostTalesLotrMapGui;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.emoji.ChatEmojiParser;
import com.ninuna.losttales.chat.emoji.ChatEmojiSuggester;
import com.ninuna.losttales.chat.emoji.ChatEmoticonConverter;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import com.ninuna.losttales.network.packet.LostTalesChatTypingPacket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiPlayerInfo;
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
    /** Mention candidates are rebuilt at most this often while typing. */
    private static final long MENTION_REFRESH_NANOS = 500L * 1000000L;
    private static final float NOTICE_LIFETIME_MILLIS = 1400.0F;

    /** The pickers and lists take the bar's top plus this as their floor. */
    private static final int INPUT_ANCHOR_BELOW_BAR = 14;
    /** Pointer travel before a press on a tab becomes a drag. */
    private static final int DRAG_THRESHOLD = 4;
    /**
     * Message lines one notch of the wheel moves. Short, because the
     * view glides to the new offset rather than jumping to it: a long
     * step would arrive before the eye could follow it. Shift still
     * moves one line at a time.
     */
    private static final int WHEEL_LINES = 2;
    /** Vertical distance from a row beyond which a dropped tab detaches. */
    private static final int DETACH_DISTANCE = 14;
    /** Horizontal slack around a row that still counts as dropping on it. */
    private static final int DOCK_SLACK = 24;
    /** Distance from another window's edge at which a drag snaps and links. */
    private static final int LINK_SNAP = 6;
    /** Height vanilla gives the chat's field, kept for the one we draw. */
    private static final int FIELD_HEIGHT = 12;
    /** How far outside its edge a window still answers to a resize. */
    private static final int RESIZE_BORDER = 4;
    /**
     * How close (in pixels) a resize preview must come to the size the
     * drag started from for the axis to snap back to it, each axis on
     * its own. Small enough that deliberate one-line resizes never
     * fight it; the line stride is eleven.
     */
    private static final int RESIZE_START_SNAP = 4;
    /** How far along an edge from a corner still counts as that corner. */
    private static final int RESIZE_CORNER = 12;
    /** The send arrow is the rightmost bar control. */
    private static final int SEND_BUTTON_INDEX = 0;
    private static final float COUNTER_SCALE = 0.75F;
    private static final int COUNTER_RGB =
            LostTalesColors.rgb(LostTalesColors.SAND);
    private static final int COUNTER_FULL_RGB =
            LostTalesColors.rgb(LostTalesColors.SALMON);
    private static final int LINK_HIGHLIGHT_RGB =
            LostTalesColors.rgb(LostTalesColors.HONEY);
    private static final String POPUP_SETTINGS = "settings";
    private static final String POPUP_RESTORE = "restore";
    private static final String ENTRY_MUTE = "mute";
    private static final String ENTRY_PINGS = "pings";
    private static final String ENTRY_HIDE = "hide";
    private static final String ENTRY_DETACH = "detach";
    private static final String ENTRY_CLOSE = "close";
    /** Where a detached window's row lands: a little below its old one. */
    private static final int DETACH_DROP = 40;

    private static final java.lang.reflect.Field SENT_HISTORY_CURSOR =
            findSentHistoryCursor();
    private final long openedAtNanos = System.nanoTime();
    /** Set once a message or command has gone out; the draft is then spent. */
    private boolean sent;
    /** How often a typing player repeats itself to the server. */
    private static final long TYPING_HEARTBEAT_NANOS = 2500L * 1000000L;
    /** The tab the server last heard this player typing into, or null. */
    private ChatTab typingTab;
    private long typingSentNanos;
    private long noticeNanos;
    private String noticeText = "";
    private final ChatPointerRegions regions = new ChatPointerRegions();
    private final ChatEmojiPicker emojiPicker = new ChatEmojiPicker();
    private final ChatItemPicker itemPicker = new ChatItemPicker();
    private final ChatMapMarkerPicker markerPicker = new ChatMapMarkerPicker();
    private final ChatQuestPicker questPicker = new ChatQuestPicker();
    private final ChatPickerPanel[] pickers = new ChatPickerPanel[] {
            this.emojiPicker, this.itemPicker, this.markerPicker,
            this.questPicker};
    /** The pickers the chevron folds away; the emoji picker stands apart. */
    private final ChatPickerPanel[] insertPickers = new ChatPickerPanel[] {
            this.itemPicker, this.markerPicker, this.questPicker};
    /** Bar slot of the fold chevron, between the emoji and the inserts. */
    private int toolbarToggleIndex;
    private final ChatEmojiSuggestionBox emojiSuggestions =
            new ChatEmojiSuggestionBox();
    private final ChatNameSuggestionBox nameSuggestions =
            new ChatNameSuggestionBox();
    private final ChatShareSuggestionBox shareSuggestions =
            new ChatShareSuggestionBox();
    private final ChatPopupMenu popup = new ChatPopupMenu();
    private TabDrag tabDrag;
    private WindowDrag windowDrag;
    private WindowResize windowResize;
    /** Window a restore popup was opened from. */
    private String restoreWindowId;
    /** How often the open {@code +} menu re-reads its rows. */
    private static final long RESTORE_REFRESH_NANOS = 500L * 1000000L;
    private long restoreRefreshedNanos;
    /**
     * The active window's input bar this frame — the window holding the
     * selected channel — in whole pixels plus the fractional remainder
     * the bar group is drawn with, so it lands exactly on the window.
     */
    private int barLeft = 2;
    private int barTop;
    private int barRight;
    private float barFracX;
    private float barFracY;
    private String hoverTip = "";
    private int hoverTipX;
    private int hoverTipY;
    private URI clickedLinkUri;
    private List<ChatMentionCandidate> mentionCandidates =
            Collections.emptyList();
    private int mentionRevision;
    private long mentionBuiltNanos;
    private ChatChannel mentionChannel;
    private ChatTab lastSelected;
    private boolean openAnimationStarted;
    /** The tab-strip control under the pointer as the windows were drawn. */
    private ChatChannelTabBar.Hit hoveredRowHit;

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
                vanillaField.getWidth(), FIELD_HEIGHT);
        styled.setEnableBackgroundDrawing(false);
        styled.setCanLoseFocus(false);
        styled.setFocused(true);
        styled.setMaxStringLength(vanillaField.getMaxStringLength());
        styled.setText(vanillaField.getText());
        styled.setCursorPositionEnd();
        this.inputField = styled;
        this.inputField.setTextColor(LostTalesChatVisualStyle.IVORY);
        this.inputField.setDisabledTextColour(
                LostTalesChatVisualStyle.SHADOW);
        // Share tokens do not count toward the visible limit, so the field
        // must accept the longer raw form; sending re-checks it.
        this.inputField.setMaxStringLength(
                ChatMessageValidator.MAX_RAW_CHARACTERS);
        // The bar's controls sit right to left: the send arrow, the
        // emoji picker, the fold chevron, then the insert pickers —
        // items, markers, quests — which the chevron folds away to the
        // left. The emoji picker stands outside the fold.
        int index = SEND_BUTTON_INDEX + 1;
        if (LostTalesConfig.enableChatEmojis) {
            this.emojiPicker.setButtonIndex(index++);
        }
        this.toolbarToggleIndex = index++;
        this.itemPicker.setButtonIndex(index++);
        this.markerPicker.setButtonIndex(index++);
        this.questPicker.setButtonIndex(index);
        updateInputBounds();
        // Text typed before the screen was closed (or before a resize
        // rebuilt the field) comes back; an explicit opener such as "/"
        // or "T"'s default text takes precedence over it.
        String draft = ClientChatChannelState.getDraft();
        if (this.inputField.getText().length() == 0 && draft.length() > 0) {
            this.inputField.setText(draft);
            this.inputField.setCursorPositionEnd();
        }
        this.lastSelected = ClientChatChannelState.getSelected();
        syncSelection();
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
        for (ChatPickerPanel picker : this.pickers) {
            picker.tick();
        }
        ClientChatChannelState.ensureAvailable();
        syncSelection();
        if (!this.sent) {
            ClientChatChannelState.setDraft(this.inputField.getText());
        }
        updateTypingStatus();
    }

    /**
     * Closing without sending keeps the text for the next opening; once
     * something has been sent the draft is spent.
     */
    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        ClientChatChannelState.setDraft(
                this.sent ? "" : this.inputField.getText());
        stopTyping();
    }

    /**
     * Tells the server, once every heartbeat while the field holds a
     * message, that this player is typing into the selected tab — and
     * that they have stopped when the field empties, the tab changes,
     * the message goes out or the screen closes. Commands are not
     * messages and say nothing; an NPC conversation has nobody to tell.
     */
    private void updateTypingStatus() {
        ChatTab selected = ClientChatChannelState.getSelected();
        boolean typing = LostTalesConfig.sendChatTypingStatus
                && this.inputField.getText().trim().length() > 0
                && !isCommand() && !selected.isNpc()
                && ClientChatChannelState.canSend(selected);
        if (!typing) {
            stopTyping();
            return;
        }
        long now = System.nanoTime();
        if (selected.equals(this.typingTab)
                && now - this.typingSentNanos < TYPING_HEARTBEAT_NANOS) {
            return;
        }
        if (this.typingTab != null && !selected.equals(this.typingTab)) {
            sendTyping(this.typingTab, false);
        }
        sendTyping(selected, true);
        this.typingTab = selected;
        this.typingSentNanos = now;
    }

    private void stopTyping() {
        if (this.typingTab != null) {
            sendTyping(this.typingTab, false);
            this.typingTab = null;
        }
    }

    private static void sendTyping(ChatTab tab, boolean typing) {
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesChatTypingPacket(tab.getChannel(),
                        tab.isWhisper() ? tab.getPartner() : "", typing));
    }

    /**
     * Who is typing into the window's front tab, in the gap between the
     * history and the bar: one or two names, three names, or a count
     * past that. Nothing is drawn while nobody is.
     */
    private void drawTypingLine(ChatWindow window, ChatWindowFrame frame,
                                LostTalesGuiAnimationSample opening) {
        if (!LostTalesConfig.showChatTypingIndicators) {
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
        int room = (int)Math.round(frame.boxRight - frame.boxLeft) - 6;
        int x = (int)Math.floor(frame.drawnLeft()) + 3;
        // In the window's trailing strip, on the metrics a message
        // line's glyphs would take there.
        int y = (int)Math.floor(frame.drawnBaseline())
                + LostTalesChatOverlayRenderer.LINE_HEIGHT
                - LostTalesChatOverlayRenderer.TEXT_OFFSET;
        LostTalesChatVisualStyle.drawPlain(this.fontRendererObj,
                "§o" + this.fontRendererObj.trimStringToWidth(text, room),
                x, y, alpha);
    }

    /**
     * The window the input belongs to: the one holding the selected
     * channel, or the first drawn one before the selection has a window
     * on screen.
     */
    private ChatWindowFrame activeFrame() {
        ChatWindow window = ChatWindowLayout.windowOf(
                ClientChatChannelState.getSelected());
        ChatWindowFrame frame = window == null ? null
                : ChatWindowFrame.find(window.getId());
        if (frame != null && frame.drawn) {
            return frame;
        }
        List<ChatWindowFrame> drawn = ChatWindowFrame.drawnFrames();
        return drawn.isEmpty() ? null : drawn.get(0);
    }

    /**
     * Reads the active window's bar — the strip below its newest line,
     * as wide as the window — from the frame the window pass drew, motion
     * included. Everything that belongs to the bar — indicator, field,
     * toolbar, pickers, completion lists, notices — is placed from it.
     */
    private void updateInputBox() {
        ChatWindowFrame frame = activeFrame();
        if (frame == null) {
            this.barLeft = HudPlacementLayout.SCREEN_MARGIN;
            this.barTop = this.height - HudPlacementLayout.SCREEN_MARGIN
                    - ChatWindowPlacement.INPUT_HEIGHT;
            this.barRight = this.barLeft
                    + ChatWindowPlacement.windowWidth(this.mc);
            this.barFracX = 0.0F;
            this.barFracY = 0.0F;
            return;
        }
        double left = frame.boxLeft;
        double top = frame.barTop();
        this.barLeft = (int)Math.floor(left);
        this.barTop = (int)Math.floor(top);
        this.barFracX = (float)(left - this.barLeft);
        this.barFracY = (float)(top - this.barTop);
        this.barRight = this.barLeft + (int)Math.round(
                frame.boxRight - frame.boxLeft);
    }

    private int inputBarRight() {
        updateInputBox();
        return this.barRight;
    }

    /**
     * The y the pickers and completion lists take as their floor: they
     * were laid out against the screen bottom with the bar fourteen
     * pixels above it, so the bar's top plus fourteen keeps that shape
     * wherever the bar is.
     */
    private int inputAnchor() {
        return this.barTop + INPUT_ANCHOR_BELOW_BAR;
    }

    /**
     * Top of the bar's square controls, centred in the rows below the
     * bottom rule: the strip's first row is the rule, so the bar's own
     * furniture lives in the rows after it.
     */
    private int barControlTop() {
        return this.barTop + 1 + (ChatWindowPlacement.INPUT_HEIGHT - 1
                - ChatPickerPanel.BUTTON_SIZE) / 2;
    }

    /** The text row's top inside the bar, centred like the controls. */
    private int barTextTop() {
        return this.barTop + 1
                + (ChatWindowPlacement.INPUT_HEIGHT - 1 - 8) / 2;
    }

    /** The anchor the pickers hang their buttons and panels from. */
    private int pickerAnchor() {
        return barControlTop() + ChatPickerPanel.BUTTON_ANCHOR_OFFSET;
    }

    /** Left edge of the bar control in the given slot from the right. */
    private static int barSlotLeft(int barRight, int index) {
        return barRight - ChatPickerPanel.BUTTON_MARGIN
                - (index + 1) * (ChatPickerPanel.BUTTON_SIZE
                        + ChatPickerPanel.BUTTON_MARGIN)
                + ChatPickerPanel.BUTTON_MARGIN;
    }

    /** Left edge of the leftmost bar control; the counter ends here. */
    private int controlsLeft(int barRight) {
        int leftmost = this.toolbarToggleIndex
                + (ChatWindowLayout.isToolbarCollapsed()
                        ? 0 : this.insertPickers.length);
        return barSlotLeft(barRight, leftmost);
    }

    private int toolbarToggleLeft(int barRight) {
        return barSlotLeft(barRight, this.toolbarToggleIndex);
    }

    private boolean isInsideToolbarToggle(int mouseX, int mouseY,
                                          int barRight) {
        int left = toolbarToggleLeft(barRight);
        int top = barControlTop();
        return mouseX >= left
                && mouseX < left + ChatPickerPanel.BUTTON_SIZE
                && mouseY >= top
                && mouseY < top + ChatPickerPanel.BUTTON_SIZE;
    }

    /**
     * The chevron between the emoji button and the insert buttons —
     * pointing left while they are folded away (they open leftward),
     * right while they are out (they fold back toward it). Lifted a
     * pixel under the pointer like the buttons beside it.
     */
    private void drawToolbarToggle(int barRight, int mouseX, int mouseY) {
        boolean collapsed = ChatWindowLayout.isToolbarCollapsed();
        boolean hovered = isInsideToolbarToggle(mouseX, mouseY, barRight);
        int color = LostTalesChatVisualStyle.argb(
                LostTalesChatVisualStyle.IVORY, hovered ? 255 : 0xB0);
        int left = toolbarToggleLeft(barRight);
        int x = left + (ChatPickerPanel.BUTTON_SIZE - 3) / 2;
        int y = barControlTop() + (ChatPickerPanel.BUTTON_SIZE - 5) / 2
                + (hovered ? 0 : 1);
        for (int step = 0; step < 5; step++) {
            int reach = step < 3 ? step : 4 - step;
            int px = collapsed ? x + 2 - reach : x + reach;
            drawRect(px, y + step, px + 1, y + step + 1, color);
        }
        this.regions.add(left, barControlTop(),
                left + ChatPickerPanel.BUTTON_SIZE,
                barControlTop() + ChatPickerPanel.BUTTON_SIZE);
    }

    /** Folds the insert pickers' panels with their buttons. */
    private void closeInsertPickers() {
        for (ChatPickerPanel picker : this.insertPickers) {
            picker.setOpen(false);
        }
    }

    /** Whether the picker's button (and panel) is on the bar right now. */
    private boolean isPickerShown(ChatPickerPanel picker) {
        if (picker == this.emojiPicker) {
            return LostTalesConfig.enableChatEmojis;
        }
        return !ChatWindowLayout.isToolbarCollapsed();
    }

    /**
     * Text belongs to the tab it was typed in: what the field holds goes
     * back to the tab just left, and the tab coming to the front brings
     * its own unsent text, if any.
     */
    private void swapDraft(ChatTab previous, ChatTab selected) {
        if (this.inputField == null) {
            return;
        }
        if (previous != null) {
            ClientChatChannelState.setDraft(previous,
                    this.inputField.getText());
        }
        this.inputField.setText(ClientChatChannelState.getDraft(selected));
        this.inputField.setCursorPositionEnd();
    }

    /**
     * Reacts to any selection change, whatever path caused it: the
     * selected channel comes to the front of its window, and every
     * window's front tab counts as read while the screen is open.
     */
    private void syncSelection() {
        ChatTab selected = ClientChatChannelState.getSelected();
        // The selected tab is always the front tab of its window, whether
        // the selection just changed or the layout came back from its
        // file with another tab in front; setting what is already set
        // changes nothing.
        ChatWindowLayout.setActiveTab(selected);
        if (!selected.equals(this.lastSelected)) {
            ChatTab previous = this.lastSelected;
            this.lastSelected = selected;
            // The window being typed in comes to the front.
            ChatWindow selectedWindow = ChatWindowLayout.windowOf(selected);
            if (selectedWindow != null) {
                ChatWindowLayout.raise(selectedWindow.getId());
            }
            updateInputBounds();
            swapDraft(previous, selected);
            // An unlocked appearance falls back to the new channel's
            // default; a locked one rides along.
            ClientChatAppearances.onChannelSwitched();
        }
        List<ChatWindow> windows = ChatWindowLayout.windows();
        for (int index = 0; index < windows.size(); index++) {
            ChatWindow window = windows.get(index);
            ClientChatChannelViews.markViewed(ChatWindowFrame.activeTab(
                    window, ChatWindowFrame.visibleTabs(window)));
        }
    }

    private ChatPickerPanel openPicker() {
        for (ChatPickerPanel picker : this.pickers) {
            if (picker.isOpen()) {
                return picker;
            }
        }
        return null;
    }

    private void closePickers() {
        for (ChatPickerPanel picker : this.pickers) {
            picker.setOpen(false);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE
                && (this.popup.isOpen() || isDragging())) {
            this.popup.close();
            cancelDrags();
            return;
        }
        // Ctrl+Tab walks every window's tabs, Ctrl+Left/Right the
        // selected window's, and Ctrl+W closes the selected one: none of
        // them clashes with autocomplete, so all work with text in the
        // field (drafts belong to their tabs).
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_TAB) {
            selectChannel(ClientChatChannelState.cycleAll(isShiftKeyDown()));
            return;
        }
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_RIGHT) {
            selectChannel(ClientChatChannelState.cycle());
            return;
        }
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_LEFT) {
            selectChannel(ClientChatChannelState.cycleBack());
            return;
        }
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_W) {
            closeChannel(ClientChatChannelState.getSelected());
            return;
        }
        ChatPickerPanel picker = openPicker();
        if (picker != null && picker.handleKeyTyped(typedChar, keyCode)) {
            return;
        }
        if (LostTalesConfig.enableChatEmojis) {
            emojiSuggestions.update(this.inputField.getText(),
                    this.inputField.getCursorPosition());
            if (emojiSuggestions.isActive()
                    && handleSuggestionKey(keyCode)) {
                return;
            }
        }
        if (LostTalesConfig.enableChatPings) {
            refreshNameSuggestions();
            if (nameSuggestions.isActive()
                    && handleNameSuggestionKey(keyCode)) {
                return;
            }
        }
        refreshShareSuggestions();
        if (shareSuggestions.isActive() && handleShareSuggestionKey(keyCode)) {
            return;
        }
        // Tab and the arrows walk the tabs of the window being typed
        // in. All three need an empty field: with text in it the arrows
        // belong to the caret, as they do in any text field.
        if (this.inputField.getText().length() == 0
                && (keyCode == Keyboard.KEY_TAB
                        || keyCode == Keyboard.KEY_RIGHT)) {
            ClientChatChannelState.cycle();
            syncSelection();
            return;
        }
        if (this.inputField.getText().length() == 0
                && keyCode == Keyboard.KEY_LEFT) {
            ClientChatChannelState.cycleBack();
            syncSelection();
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            submitInput();
            return;
        }
        if (refusesCharacter(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
        enforceLimit();
        if (LostTalesConfig.enableChatEmojis) {
            emojiSuggestions.update(this.inputField.getText(),
                    this.inputField.getCursorPosition());
        }
        if (LostTalesConfig.enableChatPings) {
            refreshNameSuggestions();
        }
        refreshShareSuggestions();
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
        boolean command = text.length() > 0 && isServerCommand(text);
        if (text.length() > 0) {
            func_146403_a(text);
        }
        this.inputField.setText("");
        this.sent = false;
        ClientChatChannelState.setDraft("");
        resetSentHistoryCursor();
        stopTyping();
        if (command) {
            // Whatever the command answers is console output, so the
            // console comes forward and takes the input before the
            // answer arrives. Only once the field has been emptied:
            // moving the selection swaps drafts between tabs, and the
            // command that has just gone out is not one.
            showConsole();
        }
    }

    /** Whether the field holds a command rather than a message. */
    private boolean isCommand() {
        return this.inputField.getText().trim().startsWith("/");
    }

    /**
     * Whether the text is a command the server will answer, rather than
     * a message or one of the chat's own whisper verbs, which never
     * reach the server as commands.
     */
    private static boolean isServerCommand(String text) {
        return text.startsWith("/") && !isWhisperCommand(text);
    }

    /** {@code /msg}, {@code /tell} and {@code /w}, whatever follows. */
    private static boolean isWhisperCommand(String text) {
        String verb = text.trim().split("\\s+", 2)[0]
                .toLowerCase(Locale.ROOT);
        return "/msg".equals(verb) || "/tell".equals(verb)
                || "/w".equals(verb);
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
        return ChatMessageValidator.visibleLength(this.inputField.getText())
                >= ChatMessageValidator.MAX_CHARACTERS;
    }

    /**
     * Trims a message that got past the limit anyway (a paste) back to
     * it, one character at a time from the end, so the field never
     * holds more than can be sent.
     */
    private void enforceLimit() {
        if (isCommand()) {
            return;
        }
        String text = this.inputField.getText();
        boolean trimmed = false;
        while (text.length() > 0 && ChatMessageValidator.visibleLength(text)
                > ChatMessageValidator.MAX_CHARACTERS) {
            text = text.substring(0, text.length() - 1);
            trimmed = true;
        }
        if (trimmed) {
            this.inputField.setText(text);
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
        if (message.length() == 0 || message.startsWith("/")) {
            return false;
        }
        if (ClientChatChannelState.getSelectedChannel()
                == ChatChannel.CONSOLE) {
            // The console is for commands; plain text has nowhere to go.
            showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.console_commands_only"));
            return true;
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

    private void refreshNameSuggestions() {
        nameSuggestions.update(this.inputField.getText(),
                this.inputField.getCursorPosition(),
                mentionCandidates(), this.mentionRevision);
    }

    private void refreshShareSuggestions() {
        shareSuggestions.update(this.inputField.getText(),
                this.inputField.getCursorPosition(), this.mc.thePlayer);
    }

    /** Keys owned by the mention completion list while it is visible. */
    private boolean handleNameSuggestionKey(int keyCode) {
        if (keyCode == Keyboard.KEY_UP) {
            nameSuggestions.moveSelection(-1);
            return true;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            nameSuggestions.moveSelection(1);
            return true;
        }
        if (keyCode == Keyboard.KEY_TAB
                || keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            acceptNameSuggestion(nameSuggestions.getSelected());
            return true;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            nameSuggestions.dismiss();
            return true;
        }
        return false;
    }

    /** Replaces the {@code @prefix} at the cursor with the full mention. */
    private void acceptNameSuggestion(ChatMentionCandidate candidate) {
        ChatNameSuggester.Query query = nameSuggestions.getQuery();
        if (candidate == null || query == null) {
            return;
        }
        replaceAtCursor(query.atIndex, "@" + candidate.getDisplayName() + " ");
        refreshNameSuggestions();
    }

    /** Keys owned by the share completion list while it is visible. */
    private boolean handleShareSuggestionKey(int keyCode) {
        if (keyCode == Keyboard.KEY_UP) {
            shareSuggestions.moveSelection(-1);
            return true;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            shareSuggestions.moveSelection(1);
            return true;
        }
        if (keyCode == Keyboard.KEY_TAB
                || keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            acceptShareSuggestion(shareSuggestions.getSelected());
            return true;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            shareSuggestions.dismiss();
            return true;
        }
        return false;
    }

    /** Replaces the open share opener at the cursor with a full token. */
    private void acceptShareSuggestion(ChatShareCandidates.Entry entry) {
        ChatShareSuggester.Query query = shareSuggestions.getQuery();
        if (entry == null || query == null) {
            return;
        }
        replaceAtCursor(query.openIndex, entry.token() + " ");
        refreshShareSuggestions();
    }

    /**
     * Inserts a token picked from a menu — an emoji, an item or a
     * marker share — as a word of its own: a space is put before it
     * unless the text before the cursor is empty or already ends in one,
     * and one follows it, so two picks in a row never run together and
     * the next typed word stands apart.
     */
    private void insertToken(String token) {
        String word = token == null ? "" : token.trim();
        if (word.length() == 0) {
            return;
        }
        String text = this.inputField.getText();
        int cursor = Math.max(0, Math.min(
                this.inputField.getCursorPosition(), text.length()));
        boolean separated = cursor == 0
                || Character.isWhitespace(text.charAt(cursor - 1));
        this.inputField.writeText((separated ? "" : " ") + word + " ");
    }

    /** Replaces the text from {@code from} to the cursor with a completion. */
    private void replaceAtCursor(int from, String replacement) {
        String text = this.inputField.getText();
        int start = Math.max(0, Math.min(from, text.length()));
        int cursor = Math.max(start, Math.min(
                this.inputField.getCursorPosition(), text.length()));
        this.inputField.setText(text.substring(0, start)
                + replacement + text.substring(cursor));
        this.inputField.setCursorPosition(Math.min(
                this.inputField.getText().length(),
                start + replacement.length()));
    }

    /**
     * One candidate per online player, shaped for the selected channel:
     * OOC displays and inserts the account name, role-play channels the
     * active character name; both names remain searchable aliases. The
     * stable key is the player's UUID from the appearance sync where one is
     * known, so an account and its character never appear as two entries.
     * Rebuilt on an interval, not per keystroke or frame.
     */
    private List<ChatMentionCandidate> mentionCandidates() {
        ChatChannel channel = ClientChatChannelState.getSelectedChannel();
        long now = System.nanoTime();
        if (channel == this.mentionChannel && this.mentionBuiltNanos != 0L
                && now - this.mentionBuiltNanos < MENTION_REFRESH_NANOS) {
            return this.mentionCandidates;
        }
        this.mentionBuiltNanos = now;
        this.mentionChannel = channel;
        List<ChatMentionCandidate> built = buildMentionCandidates(channel);
        if (!sameCandidates(built, this.mentionCandidates)) {
            this.mentionCandidates = built;
            this.mentionRevision++;
        }
        return this.mentionCandidates;
    }

    private List<ChatMentionCandidate> buildMentionCandidates(
            ChatChannel channel) {
        List<ChatMentionCandidate> result =
                new ArrayList<ChatMentionCandidate>();
        if (this.mc.thePlayer == null) {
            return result;
        }
        boolean accountIdentity =
                channel.getIdentityType() == ChatIdentityType.ACCOUNT;
        // Roles stand above the players: addressing a whole group is
        // never buried under a list of names. They are an account fact,
        // so they are offered on the account channels alone.
        if (accountIdentity) {
            for (ChatAccountRole role : ChatAccountRole.mentionable()) {
                String name = StatCollector.translateToLocal(
                        role.getNameKey());
                if (name.length() > 0 && !name.equals(role.getNameKey())) {
                    result.add(ChatMentionCandidate.role(
                            "role:" + role.name().toLowerCase(Locale.ROOT),
                            name, role.getColor()));
                }
            }
        }
        Map<String, CharacterAppearance> byAccount =
                new HashMap<String, CharacterAppearance>();
        for (CharacterAppearance appearance
                : ClientCharacterAppearanceCache.snapshot().values()) {
            if (appearance != null && appearance.isPresent()
                    && appearance.getAccountName().length() > 0) {
                byAccount.put(appearance.getAccountName()
                        .toLowerCase(Locale.ROOT), appearance);
            }
        }

        String selfAccount = this.mc.thePlayer.getCommandSenderName();
        CharacterRosterSnapshot snapshot =
                ClientCharacterRosterCache.getSnapshot();
        CharacterSummary active = snapshot == null
                ? null : snapshot.getActiveCharacter();
        String selfCharacter = active == null ? "" : active.getName();
        UUID selfId = this.mc.thePlayer.getUniqueID();
        result.add(candidate(selfId == null ? "self" : selfId.toString(),
                selfAccount, selfCharacter, accountIdentity,
                selfId == null ? "" : selfId.toString()));

        List<ChatMentionCandidate> others =
                new ArrayList<ChatMentionCandidate>();
        if (this.mc.thePlayer.sendQueue != null
                && this.mc.thePlayer.sendQueue.playerInfoList != null) {
            for (Object value
                    : this.mc.thePlayer.sendQueue.playerInfoList) {
                if (!(value instanceof GuiPlayerInfo)) {
                    continue;
                }
                String account = ((GuiPlayerInfo)value).name;
                if (account == null || account.trim().length() == 0
                        || account.equalsIgnoreCase(selfAccount)) {
                    continue;
                }
                CharacterAppearance appearance = byAccount.get(
                        account.toLowerCase(Locale.ROOT));
                String key = appearance == null
                        ? "account:" + account.toLowerCase(Locale.ROOT)
                        : appearance.getPlayerId().toString();
                others.add(candidate(key, account, appearance == null
                        ? "" : appearance.getCharacterName(),
                        accountIdentity, appearance == null ? ""
                                : appearance.getPlayerId().toString()));
            }
        }
        Collections.sort(others, new Comparator<ChatMentionCandidate>() {
            @Override
            public int compare(ChatMentionCandidate left,
                               ChatMentionCandidate right) {
                return left.getDisplayName().compareToIgnoreCase(
                        right.getDisplayName());
            }
        });
        result.addAll(others);
        return result;
    }

    private static ChatMentionCandidate candidate(
            String key, String account, String character,
            boolean accountIdentity, String accountId) {
        String display = accountIdentity || character == null
                || character.trim().length() == 0 ? account : character;
        return ChatMentionCandidate.player(key, display, account, character,
                accountId, Arrays.asList(account, character));
    }

    private static boolean sameCandidates(List<ChatMentionCandidate> left,
                                          List<ChatMentionCandidate> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            ChatMentionCandidate a = left.get(index);
            ChatMentionCandidate b = right.get(index);
            if (!a.getKey().equals(b.getKey())
                    || !a.getDisplayName().equals(b.getDisplayName())
                    || !a.getAliases().equals(b.getAliases())) {
                return false;
            }
        }
        return true;
    }

    /** Keys owned by the completion list while it is visible. */
    private boolean handleSuggestionKey(int keyCode) {
        if (keyCode == Keyboard.KEY_UP) {
            emojiSuggestions.moveSelection(-1);
            return true;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            emojiSuggestions.moveSelection(1);
            return true;
        }
        if (keyCode == Keyboard.KEY_TAB
                || keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            acceptSuggestion(emojiSuggestions.getSelected());
            return true;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            emojiSuggestions.dismiss();
            return true;
        }
        return false;
    }

    /** Replaces the {@code :prefix} at the cursor with the full shortcode. */
    private void acceptSuggestion(ChatEmoji emoji) {
        ChatEmojiSuggester.Query query = emojiSuggestions.getQuery();
        if (emoji == null || query == null) {
            return;
        }
        replaceAtCursor(query.colonIndex, emoji.getShortcode());
        emojiSuggestions.update(this.inputField.getText(),
                this.inputField.getCursorPosition());
    }

    @Override
    public void func_146403_a(String text) {
        String message = text == null ? "" : text.trim();
        this.sent = true;
        ClientChatChannelState.setDraft("");
        if (message.startsWith("/")) {
            if (sendWhisperCommand(message)) {
                return;
            }
            // The console is brought forward by whatever asked for the
            // send, once it has emptied the field.
            super.func_146403_a(message);
            return;
        }
        ChatTab tab = ClientChatChannelState.getSelected();
        if (message.length() == 0
                || !ChatMessageValidator.isValid(message)
                || tab.getChannel() == ChatChannel.CONSOLE
                || !ClientChatChannelState.canSend(tab)) {
            return;
        }
        this.mc.ingameGUI.getChatGUI().addToSentMessages(message);
        // The sent history above keeps the raw text, so recalling it
        // gives back exactly what was typed; what goes out may have its
        // emoticons converted.
        String outgoing = outgoingMessage(message);
        if (tab.isNpc()) {
            // Nobody is on the other end of an NPC's conversation: the
            // line is shown here as if whispered, and that is all. What
            // the player shared is resolved here too, since no server
            // will do it for them.
            LostTalesChatPresentation.echoToNpc(tab, outgoing,
                    resolveLocalShowcases(outgoing));
            return;
        }
        if (LostTalesConfig.enableChatEmojis) {
            for (ChatEmojiParser.Segment segment
                    : ChatEmojiParser.split(outgoing)) {
                if (segment.isEmoji()) {
                    ChatEmojiUsageStore.recordUse(segment.getEmoji());
                }
            }
        }
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesChatSendPacket(tab.getChannel(), outgoing,
                        resolveShareReferences(outgoing), tab.getPartner(),
                        ClientChatAppearances.wireKind(),
                        ClientChatAppearances.wireCharacterId()));
    }

    /**
     * The message as it goes out: whole-token emoticons become their
     * canonical shortcodes when the setting asks for it and the longer
     * text still fits the limit; otherwise exactly what was typed.
     */
    private static String outgoingMessage(String message) {
        if (!LostTalesConfig.enableChatEmojis
                || !LostTalesConfig.convertChatEmoticons) {
            return message;
        }
        String converted = ChatEmoticonConverter.convert(message);
        return ChatMessageValidator.isValid(converted) ? converted : message;
    }

    /**
     * Brings the console forward and puts the input in it: its window
     * comes to the front, the console becomes that window's front tab,
     * and the selection follows. A console the player has closed is
     * reopened first. Purely local presentation — the command itself has
     * already gone to the server, and nothing here depends on the
     * answer, so it behaves the same in single player and on a server.
     */
    private void showConsole() {
        ChatTab console = ChatTab.of(ChatChannel.CONSOLE);
        if (!ClientChatChannelState.isAvailable(console)) {
            return;
        }
        if (!ChatWindowLayout.isOpen(console)
                && !ChatWindowLayout.restore(ChatChannel.CONSOLE)) {
            return;
        }
        ChatWindow window = ChatWindowLayout.windowOf(console);
        if (window != null) {
            ChatWindowLayout.raise(window.getId());
        }
        ChatWindowLayout.setActiveTab(console);
        selectChannel(console);
    }

    /**
     * {@code /msg}, {@code /tell} and {@code /w} are the chat's own: the
     * name opens (and selects) that whisper tab, and any text after it is
     * sent there as a whisper rather than as a vanilla command.
     */
    private boolean sendWhisperCommand(String command) {
        if (!isWhisperCommand(command)) {
            return false;
        }
        String[] parts = command.trim().split("\\s+", 3);
        if (parts.length < 2 || parts[1].length() == 0) {
            showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.whisper.name_required"));
            return true;
        }
        ChatTab tab = openWhisperTab(parts[1]);
        if (tab == null) {
            return true;
        }
        String text = parts.length > 2 ? parts[2].trim() : "";
        if (text.length() > 0 && ChatMessageValidator.isValid(text)) {
            this.mc.ingameGUI.getChatGUI().addToSentMessages(command);
            String outgoing = outgoingMessage(text);
            LostTalesNetworkHandler.CHANNEL.sendToServer(
                    new LostTalesChatSendPacket(ChatChannel.WHISPER, outgoing,
                            resolveShareReferences(outgoing),
                            tab.getPartner(),
                            ClientChatAppearances.wireKind(),
                            ClientChatAppearances.wireCharacterId()));
        }
        return true;
    }

    /**
     * The whisper tab with the named account, opened in the window the
     * player is typing in if it is not yet, and selected. The player's
     * own name opens nothing.
     */
    private ChatTab openWhisperTab(String account) {
        String name = account == null ? "" : account.trim();
        if (name.length() == 0) {
            return null;
        }
        if (this.mc.thePlayer != null && name.equalsIgnoreCase(
                this.mc.thePlayer.getCommandSenderName())) {
            showNotice(StatCollector.translateToLocal(
                    "chat.losttales.whisper.self"));
            return null;
        }
        ChatWindow current = ChatWindowLayout.windowOf(
                ClientChatChannelState.getSelected());
        ChatTab tab = ChatWindowLayout.openWhisper(name,
                current == null ? null : current.getId());
        if (tab != null) {
            selectChannel(tab);
        }
        return tab;
    }

    /**
     * Resolves each share token to the reference the server will re-check:
     * an item token to the n-th slot currently holding a stack with that
     * display name, a marker token to the id of the n-th visible marker
     * with that name, scanning in the same order the pickers list them.
     * Only slot indices and ids leave the client.
     */
    /**
     * The things a message shares, resolved from this client alone: the
     * stack in the slot the token names and the marker the token names,
     * both checked against the typed name exactly as the server checks
     * them. Only an NPC conversation uses these — every other channel is
     * the server's to validate.
     */
    private List<ChatShowcase> resolveLocalShowcases(String message) {
        List<ChatShareTokenParser.Token> tokens =
                ChatShareTokenParser.parse(message);
        if (tokens.isEmpty() || this.mc.thePlayer == null) {
            return null;
        }
        List<ChatShareCandidates.ItemEntry> items =
                ChatShareCandidates.items(this.mc.thePlayer);
        List<ChatShareCandidates.MarkerEntry> markers =
                ChatShareCandidates.markers();
        List<ChatShowcase> showcases = new ArrayList<ChatShowcase>();
        int index = 0;
        for (ChatShareTokenParser.Token token : tokens) {
            if (index >= ChatShareTokenParser.MAX_TOKENS) {
                break;
            }
            if (token.kind == ChatShareKind.ITEM) {
                for (ChatShareCandidates.ItemEntry entry : items) {
                    if (!entry.matchesToken(token)) {
                        continue;
                    }
                    byte[] encoded = ChatShowcase.encodeStack(
                            entry.stack.copy());
                    if (encoded != null) {
                        showcases.add(ChatShowcase.item(index, encoded));
                    }
                    break;
                }
            } else {
                for (ChatShareCandidates.MarkerEntry entry : markers) {
                    if (!entry.matchesToken(token)) {
                        continue;
                    }
                    LostTalesMapMarkerData marker = entry.marker;
                    ChatShowcase showcase = ChatShowcase.marker(index,
                            marker.getId(), marker.getName(),
                            marker.getIconName(), marker.getColorName(),
                            marker.getDimensionId(), marker.getX(),
                            marker.getZ());
                    if (showcase != null) {
                        showcases.add(showcase);
                    }
                    break;
                }
            }
            index++;
        }
        return showcases.isEmpty() ? null : showcases;
    }

    private List<ChatShareReference> resolveShareReferences(String message) {
        List<ChatShareTokenParser.Token> tokens =
                ChatShareTokenParser.parse(message);
        if (tokens.isEmpty()) {
            return null;
        }
        List<ChatShareCandidates.ItemEntry> items =
                ChatShareCandidates.items(this.mc.thePlayer);
        List<ChatShareCandidates.MarkerEntry> markers =
                ChatShareCandidates.markers();
        List<ChatShareReference> references =
                new ArrayList<ChatShareReference>(tokens.size());
        for (ChatShareTokenParser.Token token : tokens) {
            ChatShareReference reference =
                    ChatShareReference.unresolved(token.kind);
            if (token.kind == ChatShareKind.ITEM) {
                for (ChatShareCandidates.ItemEntry entry : items) {
                    if (entry.matchesToken(token)) {
                        reference = ChatShareReference.item(entry.slot);
                        break;
                    }
                }
            } else {
                for (ChatShareCandidates.MarkerEntry entry : markers) {
                    if (entry.matchesToken(token)) {
                        reference = ChatShareReference.marker(
                                entry.marker.getId());
                        break;
                    }
                }
            }
            references.add(reference);
        }
        return references;
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        // A wheel turn over the open menu scrolls its rows.
        if (this.popup.isOpen()) {
            int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height
                    / this.mc.displayHeight - 1;
            if (this.popup.contains(mouseX, mouseY)) {
                this.popup.scrollBy(wheel > 0 ? -1 : 1);
                return;
            }
        }
        // A wheel turn over an open picker scrolls that picker's list;
        // anywhere else it scrolls the history as before.
        ChatPickerPanel picker = openPicker();
        if (picker != null) {
            int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height
                    / this.mc.displayHeight - 1;
            int adjustedMouseY = mouseY - Math.round(inputEntranceOffset());
            if (picker.isInsidePanel(mouseX, adjustedMouseY,
                    inputBarRight(), pickerAnchor())) {
                picker.scrollBy((wheel > 0 ? -1 : 1) * picker.cellHeight());
                return;
            }
        }
        // Vanilla scrolled its own (now unused) offset above; the visible
        // history scrolls per channel view instead, with vanilla's step,
        // in the window under the pointer (the main one elsewhere).
        int step = wheel > 0 ? 1 : -1;
        if (!isShiftKeyDown()) {
            step *= WHEEL_LINES;
        }
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height
                / this.mc.displayHeight - 1;
        ChatWindowFrame frame = frameAt(mouseX, mouseY);
        if (frame == null) {
            frame = activeFrame();
        }
        if (frame == null || frame.view == null) {
            return;
        }
        List<ChatLine> lines = frame.lines;
        // A page is the window's own message room, fractions of a line
        // included, so scrolling to either end lands on a whole message.
        ClientChatChannelViews.scroll(frame.view, step,
                lines == null ? 0 : lines.size(),
                frame.room / (double)(LostTalesChatOverlayRenderer.LINE_HEIGHT
                        * frame.scale));
    }

    /** The frontmost drawn window under a screen point, or null. */
    private static ChatWindowFrame frameAt(int mouseX, int mouseY) {
        List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
        for (int index = frames.size() - 1; index >= 0; index--) {
            if (frames.get(index).contains(mouseX, mouseY)) {
                return frames.get(index);
            }
        }
        return null;
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
        ClientChatChannelState.ensureAvailable();
        syncSelection();
        this.itemPicker.refresh(this.mc.thePlayer);
        this.markerPicker.refresh();
        // The bars enter from below on their own curve while the rest of
        // the window rides the shared opening motion; everything drawn
        // with a bar is hit in the bar's own space.
        float entrance = inputEntranceOffset();
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
        // Mouse events reach mouseClickMove at tick rate; a live drag is
        // re-read from the pointer every drawn frame so the window, the
        // outline and the dock target stay under the cursor.
        if (this.windowResize != null && this.windowResize.active) {
            updateResize(mouseX, mouseY);
        } else if (this.windowDrag != null && this.windowDrag.active) {
            moveDraggedWindow(mouseX, mouseY);
        } else if (this.tabDrag != null && this.tabDrag.active) {
            updateDropTarget(mouseX, mouseY);
        }
        drawWindows(mouseX, mouseY, entrance);
        // The field follows the active window's bar as just drawn.
        updateInputBounds();
        refreshRestorePopup();
        this.popup.registerRegion(this.regions);
        int barRight = inputBarRight();
        int anchor = inputAnchor();
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(this.barFracX, this.barFracY + entrance, 0.0F);
            drawInputBar(barRight);
            drawAppearanceButton(mouseX, adjustedMouseY);
            drawIndicator(mouseX, adjustedMouseY);
            drawToolbarToggle(barRight, mouseX, adjustedMouseY);
            if (isInsideToolbarToggle(mouseX, adjustedMouseY, barRight)
                    && !isDragging()) {
                this.hoverTip = StatCollector.translateToLocal(
                        ChatWindowLayout.isToolbarCollapsed()
                                ? "gui.losttales.chat.toolbar.expand"
                                : "gui.losttales.chat.toolbar.collapse");
                this.hoverTipX = mouseX;
                this.hoverTipY = mouseY;
            }
            for (ChatPickerPanel picker : this.pickers) {
                if (isPickerShown(picker)) {
                    picker.draw(this.mc, this.regions, barRight,
                            pickerAnchor(), mouseX, adjustedMouseY);
                }
            }
            drawSendButton(barRight, mouseX, adjustedMouseY);
            drawCounter(barRight);
            if (LostTalesConfig.enableChatEmojis) {
                emojiSuggestions.update(this.inputField.getText(),
                        this.inputField.getCursorPosition());
                emojiSuggestions.draw(this.mc, this.fontRendererObj,
                        this.regions, anchor,
                        this.inputField.xPosition, mouseX, adjustedMouseY);
            }
            if (LostTalesConfig.enableChatPings) {
                refreshNameSuggestions();
                nameSuggestions.draw(this.mc, this.fontRendererObj,
                        this.regions, anchor, this.inputField.xPosition,
                        mouseX, adjustedMouseY);
            }
            refreshShareSuggestions();
            shareSuggestions.draw(this.mc, this.fontRendererObj,
                    this.regions, anchor, this.inputField.xPosition,
                    mouseX, adjustedMouseY);
        } finally {
            GL11.glPopMatrix();
        }
        if (!this.regions.contains(mouseX, mouseY)) {
            drawChatLineHover(mouseX, mouseY, mouseY);
        }
        drawNotice();
        ChatMentionCandidate hoveredCandidate = LostTalesConfig.enableChatPings
                ? nameSuggestions.suggestionAt(this.fontRendererObj, mouseX,
                        adjustedMouseY, inputAnchor(),
                        this.inputField.xPosition)
                : null;
        if (hoveredCandidate != null) {
            LostTalesChatHoverCard.drawForCandidate(this.mc,
                    hoveredCandidate, mouseX, mouseY,
                    this.width, this.height);
        } else if (!this.regions.contains(mouseX, mouseY)) {
            LostTalesChatHoverCard.draw(this.mc, mouseX, mouseY,
                    this.width, this.height);
        }
        drawLinkHighlight();
        this.popup.draw(this.fontRendererObj, this.regions, mouseX, mouseY);
        if (this.tabDrag != null && this.tabDrag.active) {
            // The ghost follows the raw pointer at display-pixel
            // granularity like the cursor: laid out in whole pixels and
            // shifted by the fractional remainder, so it never steps by
            // whole GUI pixels against the cursor carrying it.
            double ghostX = ChatWindowFrame.snapToDisplayPixels(
                    ChatWindowPlacement.preciseMouseX(this.mc, this.width)
                            - this.tabDrag.grabOffsetX);
            double ghostY = ChatWindowFrame.snapToDisplayPixels(
                    ChatWindowPlacement.preciseMouseY(this.mc, this.height));
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef((float)(ghostX - Math.floor(ghostX)),
                        (float)(ghostY - Math.floor(ghostY)), 0.0F);
                frameFor(ChatWindowLayout.firstWindow()).tabBar.drawGhost(
                        this.fontRendererObj, this.tabDrag.tab,
                        (int)Math.floor(ghostX), (int)Math.floor(ghostY));
            } finally {
                GL11.glPopMatrix();
            }
        } else if (hoverTip.length() > 0 && !this.popup.isOpen()) {
            drawHoverTip();
        }
        updatePointerFeedback(mouseX, mouseY);
        // Last of everything the chat draws: the edge being dragged is
        // never covered by the window it belongs to.
        drawResizePreview();
    }

    private static ChatWindowFrame frameFor(ChatWindow window) {
        return ChatWindowFrame.of(window);
    }

    /**
     * Every window, back to front, each complete before the next: its
     * history — drawn here rather than in the HUD pass, so the open chat
     * lies above every HUD element and a front window covers the whole
     * of one behind it — then the tab row standing on its topmost band
     * (or on one empty line) and carrying the window's top rule as its
     * last pixel row, its bar unless it is the active window's, which is
     * drawn with the input group, and the bottom rule over the shade.
     * The row is the window's title strip and is there while the window
     * has a tab the player can see.
     */
    private void drawWindows(int mouseX, int mouseY, float entrance) {
        LostTalesGuiAnimationSample opening =
                ClientChatChannelViews.openSample();
        ChatWindowFrame active = activeFrame();
        List<ChatWindow> windows = ChatWindowLayout.stacked();
        ChatChannelTabBar.Hit hoveredHit = null;
        ChatWindow hoveredWindow = null;
        boolean hoveredGrip = false;
        for (int index = 0; index < windows.size(); index++) {
            ChatWindow window = windows.get(index);
            LostTalesChatOverlayRenderer.drawWindowForScreen(this.mc, window,
                    this.width, this.height, opening);
            ChatWindowFrame frame = frameFor(window);
            ChatChannelTabBar.Row row = rowFor(window, frame, opening);
            if (row == null) {
                continue;
            }
            // The row is laid out in whole pixels and shifted by the
            // window's fractional remainder, so it sits exactly where the
            // lines do while the window glides.
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef((float)(frame.drawnLeft() - Math.floor(
                        frame.drawnLeft())), (float)(frame.tabRowBottom()
                        - Math.floor(frame.tabRowBottom())), 0.0F);
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
            if (frame != active) {
                drawInactiveBar(window, frame, entrance);
            }
            drawTypingLine(window, frame, opening);
            LostTalesChatOverlayRenderer.drawBottomRule(this.mc, frame,
                    opening);
        }
        this.hoveredRowHit = hoveredHit;
        if (hoveredHit != null && !isDragging()) {
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
        WindowResize resizing = this.windowResize;
        if (resizing != null) {
            LostTalesMapCursor.requestPose(cursorPose(resizing.edge));
            return;
        }
        if (isDragging()) {
            return;
        }
        ResizeTarget target = resizeUnderPointer(mouseX, mouseY);
        if (target != null) {
            LostTalesMapCursor.requestPose(cursorPose(target.edge));
        } else if (isOverInteractable(mouseX, mouseY, this.hoveredRowHit)) {
            // The chat's own controls are drawn, not vanilla widgets, so
            // it says for itself what answers to a click.
            LostTalesMapCursor.requestPose(LostTalesMapCursor.Pose.HAND);
        }
    }

    /**
     * The window edge a press at this point would take hold of, or null.
     * One classification, in one order: what is drawn above the windows
     * — the pickers, the completion lists, the settings menu, the input
     * bar group — owns the pointer first; the resize border comes next,
     * ahead of the tab strip it overlaps along the window's top edge;
     * the strip and the windows themselves come last. Hover, the cursor,
     * mouse-down and the drag it starts all ask this same question, so
     * what the pointer shows is what the press does.
     */
    private ResizeTarget resizeUnderPointer(int mouseX, int mouseY) {
        if (this.regions.containsOverlay(mouseX, mouseY)) {
            return null;
        }
        return resizeTargetAt(mouseX, mouseY);
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
        // Whole-pixel geometry of the drawn (motion included) position;
        // the fractional remainder is applied when the row is drawn.
        row.rowBottom = (int)Math.floor(frame.tabRowBottom());
        row.rowBottomExact = frame.tabRowBottom();
        row.left = (int)Math.floor(frame.drawnLeft()) + 2;
        row.right = (int)Math.floor(frame.drawnLeft()) + (int)Math.round(
                frame.boxRight - frame.boxLeft) - 2;
        row.offsetX = 0;
        row.locked = window.isLocked();
        row.moving = this.windowDrag != null && this.windowDrag.active
                && window.getId().equals(this.windowDrag.windowId);
        // The last tab the player could see offers no cross: the close
        // itself is refused anyway, so the control would only mislead.
        row.closable = ClientChatChannelState.isClosable(row.selected);
        row.showRestore = !window.isLocked()
                && (!restorableChannels().isEmpty()
                        || hasWhisperCandidates());
        row.closedUnread = row.showRestore ? closedUnreadCount() : 0;
        if (this.tabDrag != null && this.tabDrag.active) {
            if (window.contains(this.tabDrag.tab)) {
                row.dragging = this.tabDrag.tab;
            }
            if (this.tabDrag.targetWindowId != null
                    && this.tabDrag.targetWindowId.equals(window.getId())) {
                row.dropIndex = this.tabDrag.targetIndex;
            }
        }
        return row;
    }

    /** Closed channels the player could see if they were open. */
    private static List<ChatChannel> restorableChannels() {
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
     * The label a hovered row control offers. A tab names itself in
     * full, whatever the row had room to draw; the grip speaks only for
     * its own glyph, so the empty strip that also drags stays silent.
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
            case RESTORE:
                int unread = closedUnreadCount();
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
     * A window that is not the active one shows its own bar — the same
     * strip, with the name of its front tab dimmed — so a click in it
     * moves the input there. The active window's bar is drawn with the
     * input group.
     */
    private void drawInactiveBar(ChatWindow window, ChatWindowFrame frame,
                                 float entrance) {
        double left = frame.boxLeft;
        double top = frame.barTop();
        int x = (int)Math.floor(left);
        int y = (int)Math.floor(top);
        int width = (int)Math.round(frame.boxRight - frame.boxLeft);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef((float)(left - x), (float)(top - y)
                    + entrance, 0.0F);
            // The strip's first row is the window's bottom rule; the
            // bar's paint begins one row below it, like the active bar's.
            drawRect(x, y + 1, x + width,
                    y + ChatWindowPlacement.INPUT_HEIGHT,
                    LostTalesSkyrimUiStyle.withAlpha(
                            LostTalesSkyrimUiStyle.PLUM_BLACK, 0x80));
            ChatTab front = ChatWindowFrame.activeTab(window,
                    ChatWindowFrame.visibleTabs(window));
            if (front != null) {
                // Italic says "not the input"; the text stays at full
                // opacity like every other.
                String label = "§o" + indicatorLabel(front);
                int labelY = y + 1
                        + (ChatWindowPlacement.INPUT_HEIGHT - 1 - 8) / 2;
                LostTalesChatVisualStyle.drawColored(this.fontRendererObj,
                        label, x + 3, labelY,
                        ClientChatChannelState.displayColor(front), 255);
                // Unsent text stays on show where it was typed; only
                // the controls leave with the input.
                String draft = ClientChatChannelState.getDraft(front);
                if (draft.length() > 0) {
                    int textLeft = x + 3
                            + this.fontRendererObj.getStringWidth(label)
                            + 4;
                    int room = x + width - 3 - textLeft;
                    if (room > 0) {
                        LostTalesChatVisualStyle.drawColored(
                                this.fontRendererObj,
                                this.fontRendererObj.trimStringToWidth(
                                        draft, room),
                                textLeft, labelY,
                                LostTalesChatVisualStyle.IVORY, 255);
                    }
                }
            }
        } finally {
            GL11.glPopMatrix();
        }
        this.regions.addWindow(x, y + Math.round(entrance), x + width,
                y + Math.round(entrance) + ChatWindowPlacement.INPUT_HEIGHT);
    }

    /** A click in another window's bar moves the input to that window. */
    private boolean handleBarClick(int mouseX, int rawMouseY) {
        int mouseY = rawMouseY - Math.round(inputEntranceOffset());
        ChatWindowFrame active = activeFrame();
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
                    selectChannel(front);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * The active window's input bar in the palette's plum black at the
     * same half opacity as the message backdrop, exactly as wide as the
     * window. The strip's first row is the window's bottom rule, drawn
     * with the window, so the bar's own paint begins one row below it
     * and never darkens the rule. GuiScreen's button pass is skipped
     * because this screen registers no buttons.
     */
    private void drawInputBar(int barRight) {
        drawRect(this.barLeft, this.barTop + 1, barRight,
                this.barTop + ChatWindowPlacement.INPUT_HEIGHT,
                LostTalesSkyrimUiStyle.withAlpha(
                        LostTalesSkyrimUiStyle.PLUM_BLACK, 0x80));
        this.inputField.drawTextBox();
    }

    /**
     * Hover cards for item, text, and achievement components reproduced
     * from vanilla, plus the tooltips of shared items and markers, drawn
     * after the popups so they layer above everything in the stack.
     */
    private void drawChatLineHover(int mouseX, int hitMouseY,
                                   int drawMouseY) {
        LostTalesChatOverlayRenderer.Hit hovered =
                LostTalesChatOverlayRenderer.hitAt(
                        this.mc, mouseX, hitMouseY);
        if (hovered == null) {
            return;
        }
        ChatShowcaseMarker.Data share =
                ChatShowcaseMarker.decode(hovered.component);
        if (share != null) {
            drawShareTooltip(share, mouseX, drawMouseY);
            return;
        }
        if (hovered.component.getChatStyle().getChatHoverEvent() != null) {
            drawComponentHoverCard(hovered.component.getChatStyle()
                    .getChatHoverEvent(), mouseX, drawMouseY);
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
        this.func_146283_a(lines, mouseX, mouseY);
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
                this.drawCreativeTabHoveringText(
                        EnumChatFormatting.RED + "Invalid Item!",
                        mouseX, mouseY);
            }
        } else if (hoverEvent.getAction() == HoverEvent.Action.SHOW_TEXT) {
            this.func_146283_a(Splitter.on("\n").splitToList(
                    hoverEvent.getValue().getFormattedText()),
                    mouseX, mouseY);
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
                this.func_146283_a(lines, mouseX, mouseY);
            } else {
                this.drawCreativeTabHoveringText(EnumChatFormatting.RED
                        + "Invalid statistic/achievement!",
                        mouseX, mouseY);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        int adjustedMouseY = mouseY - Math.round(inputEntranceOffset());
        String closedPopupKind = "";
        if (this.popup.isOpen()) {
            closedPopupKind = this.popup.kind();
            ChatPopupMenu.Entry entry = this.popup.entryAt(mouseX, mouseY);
            boolean inside = this.popup.contains(mouseX, mouseY);
            if (entry != null && button == 0) {
                handlePopupEntry(entry);
            }
            this.popup.close();
            if (entry != null && button == 0
                    && "appearance:lock".equals(entry.id)) {
                // The lock is a switch, not a pick: the menu stays open,
                // its rows refreshed, so the padlock answers in place.
                openAppearancePopup();
            }
            if (inside) {
                return;
            }
        }
        int barRight = inputBarRight();
        int anchor = inputAnchor();
        if (LostTalesConfig.enableChatEmojis && button == 0
                && emojiSuggestions.isActive()) {
            ChatEmoji suggested = emojiSuggestions.suggestionAt(
                    this.fontRendererObj, mouseX, adjustedMouseY,
                    anchor, this.inputField.xPosition);
            if (suggested != null) {
                acceptSuggestion(suggested);
                return;
            }
        }
        if (LostTalesConfig.enableChatPings && button == 0
                && nameSuggestions.isActive()) {
            ChatMentionCandidate suggested = nameSuggestions.suggestionAt(
                    this.fontRendererObj, mouseX, adjustedMouseY,
                    anchor, this.inputField.xPosition);
            if (suggested != null) {
                acceptNameSuggestion(suggested);
                return;
            }
        }
        if (button == 0 && shareSuggestions.isActive()) {
            ChatShareCandidates.Entry suggested =
                    shareSuggestions.suggestionAt(this.fontRendererObj,
                            mouseX, adjustedMouseY, anchor,
                            this.inputField.xPosition);
            if (suggested != null) {
                acceptShareSuggestion(suggested);
                return;
            }
        }
        ChatPickerPanel picker = openPicker();
        if (picker != null && picker.isInsidePanel(mouseX, adjustedMouseY,
                barRight, pickerAnchor())) {
            if (picker.mouseClicked(mouseX, adjustedMouseY, button,
                    barRight, pickerAnchor())) {
                return;
            }
            ChatPickerPanel.Entry entry = picker.entryAt(
                    mouseX, adjustedMouseY, barRight, pickerAnchor());
            if (button == 0 && entry != null) {
                insertToken(picker.insertionText(entry));
                this.inputField.setFocused(true);
            } else if (button == 1 && picker == this.emojiPicker) {
                this.emojiPicker.toggleFavoriteAt(
                        mouseX, adjustedMouseY, barRight, pickerAnchor());
            } else if (button == 1 && picker == this.markerPicker) {
                this.markerPicker.toggleFavoriteAt(
                        mouseX, adjustedMouseY, barRight, pickerAnchor());
            }
            return;
        }
        // Pickers and completion lists paint above the tab rows, so they
        // are asked first. The resize border comes next: it overlaps the
        // tab strip along the window's top edge, and the pointer shows a
        // resize there, so a press there must resize.
        if (button == 0) {
            ResizeTarget edge = resizeUnderPointer(mouseX, mouseY);
            ChatWindow edgeWindow = edge == null ? null
                    : ChatWindowLayout.window(edge.frame.windowId);
            if (edgeWindow != null) {
                this.windowResize = resizeFrom(edge, edgeWindow,
                        mouseX, mouseY);
                return;
            }
        }
        if (button == 0 && handleRowClick(mouseX, mouseY)) {
            return;
        }
        if (button == 2 && closeTabAt(mouseX, mouseY)) {
            return;
        }
        if (button == 0 && handleBarClick(mouseX, mouseY)) {
            return;
        }
        if (button == 0 && isInsideAppearanceButton(mouseX, adjustedMouseY)) {
            // A click on the button while its own menu was open has just
            // closed it above; only then does the click not reopen it.
            if (!"appearance".equals(closedPopupKind)) {
                openAppearancePopup();
            }
            return;
        }
        if (button == 0 && isInsideIndicator(mouseX, adjustedMouseY)) {
            selectChannel(ClientChatChannelState.cycle());
            return;
        }
        if (button == 0 && isInsideSendButton(mouseX, adjustedMouseY,
                barRight)) {
            submitInput();
            return;
        }
        if (button == 0 && isInsideToolbarToggle(mouseX, adjustedMouseY,
                barRight)) {
            // Folding the inserts takes their panels with them; the
            // emoji picker is outside the fold and keeps its own.
            closeInsertPickers();
            ChatWindowLayout.setToolbarCollapsed(
                    !ChatWindowLayout.isToolbarCollapsed());
            return;
        }
        if (button == 0) {
            for (ChatPickerPanel candidate : this.pickers) {
                if (isPickerShown(candidate) && candidate.isInsideButton(
                        mouseX, adjustedMouseY, barRight, pickerAnchor())) {
                    boolean open = candidate.isOpen();
                    closePickers();
                    candidate.setOpen(!open);
                    return;
                }
            }
        }
        if (button == 0 && picker != null) {
            // Clicks inside the panel were consumed above; anything else
            // closes the picker and is processed normally.
            closePickers();
        }
        if (this.regions.contains(mouseX, mouseY)) {
            // An overlay owns this spot even when no row was hit; the
            // message stack underneath must not receive the click.
            return;
        }
        if (button == 1 && LostTalesChatClipboard.copy(
                this.mc.ingameGUI.getChatGUI(), this.mc, mouseX, mouseY)) {
            showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.copied"));
            return;
        }
        if (button == 0) {
            ChatWindowFrame frame = frameAt(mouseX, mouseY);
            ChatWindow window = frame == null ? null
                    : ChatWindowLayout.window(frame.windowId);
            // Typing follows the pointer: a press anywhere in a window —
            // its messages, its strip, its bar — moves the input there,
            // the way clicking a window focuses it anywhere else. The
            // messages themselves do not move the window: only its strip
            // and its grip do, so a click on a line stays a click.
            if (window != null) {
                ChatWindowLayout.raise(window.getId());
                selectWindow(window);
            }
        }
        clickLines(mouseX, mouseY, adjustedMouseY, button);
    }

    /** Component clicks on the lines, then the input field's own click. */
    private void clickLines(int mouseX, int mouseY, int adjustedMouseY,
                            int button) {
        if (button == 0 && handleComponentClick(
                LostTalesChatOverlayRenderer.hitAt(
                        this.mc, mouseX, mouseY))) {
            return;
        }
        // GuiChat's own component handling relies on GuiNewChat's 9px hit
        // testing, which no longer matches the 11px layout; only the input
        // field still needs the vanilla click path.
        this.inputField.mouseClicked(mouseX, adjustedMouseY, button);
    }

    /**
     * Moves the input to a window: its front tab becomes the selected
     * one, so what is typed goes where the player just clicked. Does
     * nothing for the window that already has it.
     */
    private void selectWindow(ChatWindow window) {
        if (window == null) {
            return;
        }
        ChatTab front = ChatWindowFrame.activeTab(window,
                ChatWindowFrame.visibleTabs(window));
        if (front != null && !front.equals(
                ClientChatChannelState.getSelected())) {
            selectChannel(front);
        }
    }

    private void selectChannel(ChatChannel channel) {
        selectChannel(ChatTab.of(channel));
    }

    private void selectChannel(ChatTab tab) {
        ClientChatChannelState.select(tab);
        syncSelection();
        closePickers();
        this.inputField.setFocused(true);
        // Mention candidates are shaped per channel identity.
        this.mentionBuiltNanos = 0L;
    }

    /* ---- Tab rows: clicks, drags, docking, detaching, window moves ---- */

    /** A press on a tab that may become a drag; tabs move in raw space. */
    private static final class TabDrag {
        final ChatTab tab;
        final String sourceWindowId;
        final int pressX;
        final int pressY;
        final int grabOffsetX;
        boolean active;
        /** Window row the tab would dock into at the current pointer. */
        String targetWindowId;
        int targetIndex = -1;

        TabDrag(ChatTab tab, String sourceWindowId, int pressX,
                int pressY, int grabOffsetX) {
            this.tab = tab;
            this.sourceWindowId = sourceWindowId;
            this.pressX = pressX;
            this.pressY = pressY;
            this.grabOffsetX = grabOffsetX;
        }
    }

    /**
     * A window being moved. From the grip the drag is live at once; from
     * the messages it is armed by the press and becomes a drag only once
     * the pointer travels, so a plain click on a line still acts on the
     * line when the button comes up. Offsets are fractional: the drag
     * follows the raw mouse so the window glides instead of stepping by
     * whole GUI pixels.
     */
    private static final class WindowDrag {
        final String windowId;
        final double grabOffsetX;
        final double grabOffsetY;
        final int pressX;
        final int pressY;
        boolean active;
        /** Window whose edge the drag is snapped to right now, or null. */
        String snapTargetId;
        /** Which side of that target the dragged window sits on. */
        ChatWindow.LinkSide snapSide = ChatWindow.LinkSide.BELOW;

        WindowDrag(String windowId, double grabOffsetX, double grabOffsetY,
                   int pressX, int pressY, boolean active) {
            this.windowId = windowId;
            this.grabOffsetX = grabOffsetX;
            this.grabOffsetY = grabOffsetY;
            this.pressX = pressX;
            this.pressY = pressY;
            this.active = active;
        }
    }

    /**
     * The edge or corner of a window the pointer is on. A window is
     * resized the way any window is: the edges the drag does not touch
     * stay exactly where they are, so the opposite edge is the anchor.
     */
    enum ResizeEdge {
        LEFT(true, false, true, false),
        RIGHT(true, false, false, false),
        TOP(false, true, false, true),
        BOTTOM(false, true, false, false),
        TOP_LEFT(true, true, true, true),
        TOP_RIGHT(true, true, false, true),
        BOTTOM_LEFT(true, true, true, false),
        BOTTOM_RIGHT(true, true, false, false);

        /** Which sizes this edge carries. */
        final boolean horizontal;
        final boolean vertical;
        /** Which side follows the pointer; the other side is the anchor. */
        final boolean fromLeft;
        final boolean fromTop;

        ResizeEdge(boolean horizontal, boolean vertical, boolean fromLeft,
                   boolean fromTop) {
            this.horizontal = horizontal;
            this.vertical = vertical;
            this.fromLeft = fromLeft;
            this.fromTop = fromTop;
        }

        boolean isCorner() {
            return this.horizontal && this.vertical;
        }
    }

    /**
     * A window being resized by one of its edges or corners. The window
     * keeps its anchors — the edges the drag does not touch — and only
     * an outline follows the pointer: the size is applied on release,
     * once, because a width change lays the whole history out again.
     */
    private static final class WindowResize {
        final String windowId;
        final ResizeEdge edge;
        /** The box the drag started from, in screen pixels. */
        final double startLeft;
        final double startRight;
        final double startTop;
        final double startBottom;
        /** Pointer's offset from the edge it took hold of. */
        final double grabX;
        final double grabY;
        final int pressX;
        final int pressY;
        boolean active;
        /** The box under the pointer right now. */
        double left;
        double right;
        double top;
        double bottom;
        /** Message lines the box asks for, fractions included. */
        double lines;

        WindowResize(String windowId, ResizeEdge edge, double left,
                     double right, double top, double bottom, double grabX,
                     double grabY, int pressX, int pressY, double lines) {
            this.windowId = windowId;
            this.edge = edge;
            this.startLeft = left;
            this.startRight = right;
            this.startTop = top;
            this.startBottom = bottom;
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
            this.grabX = grabX;
            this.grabY = grabY;
            this.pressX = pressX;
            this.pressY = pressY;
            this.lines = lines;
        }
    }

    /** A window and the edge of it the pointer is on. */
    private static final class ResizeTarget {
        final ChatWindowFrame frame;
        final ResizeEdge edge;

        ResizeTarget(ChatWindowFrame frame, ResizeEdge edge) {
            this.frame = frame;
            this.edge = edge;
        }
    }

    /**
     * The edge of an unlocked window under the pointer, or null. The
     * band lies just outside the window, where nothing is drawn, so a
     * tab, a control or the input field never loses a click to it; the
     * frontmost window wins where two overlap.
     */
    private static ResizeTarget resizeTargetAt(int mouseX, int mouseY) {
        List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
        for (int index = frames.size() - 1; index >= 0; index--) {
            ChatWindowFrame frame = frames.get(index);
            if (coversPoint(frame, mouseX, mouseY)) {
                // The window in front owns everything inside it, so a
                // window behind it never takes a click through it.
                return null;
            }
            ChatWindow window = ChatWindowLayout.window(frame.windowId);
            if (window == null || window.isLocked()) {
                continue;
            }
            ResizeEdge edge = edgeAt(frame, mouseX, mouseY);
            if (edge != null) {
                return new ResizeTarget(frame, edge);
            }
        }
        return null;
    }

    /**
     * Whether the point lies within the window as it was drawn — its
     * strip, its messages and its bar — rather than on the border
     * outside it.
     */
    private static boolean coversPoint(ChatWindowFrame frame, int mouseX,
                                       int mouseY) {
        double left = frame.drawnLeft();
        double right = left + (frame.boxRight - frame.boxLeft);
        double top = frame.boxTop + frame.motionY;
        double bottom = frame.boxBottom + frame.motionY;
        return mouseX > left && mouseX < right
                && mouseY > top && mouseY < bottom;
    }

    /** Which edge or corner of one window's box a point lies on. */
    private static ResizeEdge edgeAt(ChatWindowFrame frame, int mouseX,
                                     int mouseY) {
        double left = frame.drawnLeft();
        double right = left + (frame.boxRight - frame.boxLeft);
        double top = frame.boxTop + frame.motionY;
        double bottom = frame.boxBottom + frame.motionY;
        if (mouseX < left - RESIZE_BORDER || mouseX > right + RESIZE_BORDER
                || mouseY < top - RESIZE_BORDER
                || mouseY > bottom + RESIZE_BORDER) {
            return null;
        }
        boolean onLeft = mouseX <= left;
        boolean onRight = mouseX >= right;
        boolean onTop = mouseY <= top;
        boolean onBottom = mouseY >= bottom;
        if (!onLeft && !onRight && !onTop && !onBottom) {
            // Inside the window: its tabs, messages and bar own this.
            return null;
        }
        // A corner reaches a little way along both of its edges, so it
        // is as easy to catch as it is in any window manager.
        boolean nearLeft = mouseX <= left + RESIZE_CORNER;
        boolean nearRight = mouseX >= right - RESIZE_CORNER;
        boolean nearTop = mouseY <= top + RESIZE_CORNER;
        boolean nearBottom = mouseY >= bottom - RESIZE_CORNER;
        if ((onTop || onLeft) && nearTop && nearLeft) {
            return ResizeEdge.TOP_LEFT;
        }
        if ((onTop || onRight) && nearTop && nearRight) {
            return ResizeEdge.TOP_RIGHT;
        }
        if ((onBottom || onLeft) && nearBottom && nearLeft) {
            return ResizeEdge.BOTTOM_LEFT;
        }
        if ((onBottom || onRight) && nearBottom && nearRight) {
            return ResizeEdge.BOTTOM_RIGHT;
        }
        if (onLeft) {
            return ResizeEdge.LEFT;
        }
        if (onRight) {
            return ResizeEdge.RIGHT;
        }
        return onTop ? ResizeEdge.TOP : ResizeEdge.BOTTOM;
    }

    /**
     * Whether the point is on something the chat answers to: a tab or
     * one of its controls, the lock, the restore control, the grip, the
     * channel indicator, the send arrow, a picker button or an open
     * overlay of one. The input bar's own field is not a control — a
     * caret belongs there — but everything the player clicks is.
     */
    private boolean isOverInteractable(int mouseX, int mouseY,
                                       ChatChannelTabBar.Hit hovered) {
        if (hovered != null) {
            return true;
        }
        if (this.regions.contains(mouseX, mouseY)) {
            return true;
        }
        int barRight = inputBarRight();
        if (isInsideIndicator(mouseX, mouseY)
                || isInsideSendButton(mouseX, mouseY, barRight)) {
            return true;
        }
        int anchor = pickerAnchor();
        for (ChatPickerPanel picker : this.pickers) {
            if (isPickerShown(picker) && picker.isInsideButton(
                    mouseX, mouseY, barRight, anchor)) {
                return true;
            }
        }
        if (isInsideToolbarToggle(mouseX, mouseY, barRight)) {
            return true;
        }
        // A window that is not the one being typed in answers to a
        // click by becoming it, so the pointer says so over all of it.
        ChatWindowFrame frame = frameAt(mouseX, mouseY);
        if (frame == null) {
            return false;
        }
        ChatWindow window = ChatWindowLayout.window(frame.windowId);
        ChatTab front = window == null ? null
                : ChatWindowFrame.activeTab(window,
                        ChatWindowFrame.visibleTabs(window));
        return front != null
                && !front.equals(ClientChatChannelState.getSelected());
    }

    /** The pointer an edge is shown with: across it, or along its corner. */
    private static LostTalesMapCursor.Pose cursorPose(ResizeEdge edge) {
        switch (edge) {
            case LEFT:
            case RIGHT:
                return LostTalesMapCursor.Pose.RESIZE_HORIZONTAL;
            case TOP:
            case BOTTOM:
                return LostTalesMapCursor.Pose.RESIZE_VERTICAL;
            case TOP_RIGHT:
            case BOTTOM_LEFT:
                // The sheet's diagonal runs bottom-left to top-right.
                return LostTalesMapCursor.Pose.RESIZE_DIAGONAL;
            default:
                return LostTalesMapCursor.Pose.RESIZE_ANTI_DIAGONAL;
        }
    }

    /**
     * The outline of the size the pointer is asking for, in the chat's
     * own ivory. Drawn last of everything the chat puts on screen, so no
     * part of the window it belongs to — its bar least of all — covers
     * the edge the player is dragging.
     */
    private void drawResizePreview() {
        WindowResize resize = this.windowResize;
        if (resize == null) {
            return;
        }
        // Shown from the press, so the size the window would take is
        // there before the pointer has travelled anywhere. The rules lie
        // just outside the box, so no edge of the window pokes out past
        // the outline while it is being dragged. The edges follow the
        // pointer at display-pixel granularity, like the cursor and the
        // window itself, so nothing steps by whole GUI pixels.
        double left = ChatWindowFrame.snapToDisplayPixels(resize.left);
        double right = ChatWindowFrame.snapToDisplayPixels(resize.right);
        double top = ChatWindowFrame.snapToDisplayPixels(resize.top);
        double bottom = ChatWindowFrame.snapToDisplayPixels(resize.bottom);
        int color = LostTalesChatVisualStyle.argb(
                LostTalesChatVisualStyle.IVORY, 0xC0);
        fillFractional(left - 1, top - 1, right + 1, top, color);
        fillFractional(left - 1, bottom, right + 1, bottom + 1, color);
        fillFractional(left - 1, top, left, bottom, color);
        fillFractional(right, top, right + 1, bottom, color);
    }

    /** A flat quad at fractional edges; {@code drawRect} takes whole ones. */
    private static void fillFractional(double left, double top, double right,
                                       double bottom, int argb) {
        int alpha = argb >>> 24;
        if (alpha <= 0 || right <= left || bottom <= top) {
            return;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f((argb >> 16 & 0xFF) / 255.0F,
                (argb >> 8 & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F,
                alpha / 255.0F);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Arms a resize from the pointer's position on an edge. */
    private WindowResize resizeFrom(ResizeTarget target, ChatWindow window,
                                    int mouseX, int mouseY) {
        ChatWindowFrame frame = target.frame;
        ChatWindowLayout.raise(frame.windowId);
        selectWindow(window);
        double left = frame.drawnLeft();
        double right = left + (frame.boxRight - frame.boxLeft);
        double top = frame.boxTop + frame.motionY;
        double bottom = frame.boxBottom + frame.motionY;
        double pointerX = ChatWindowPlacement.preciseMouseX(this.mc, this.width);
        double pointerY = ChatWindowPlacement.preciseMouseY(this.mc, this.height);
        return new WindowResize(frame.windowId, target.edge, left, right, top,
                bottom,
                pointerX - (target.edge.fromLeft ? left : right),
                pointerY - (target.edge.fromTop ? top : bottom),
                mouseX, mouseY,
                ChatWindowPlacement.currentLines(window, this.mc));
    }

    /**
     * Follows the pointer with the outline: the edges the drag holds
     * move, the opposite ones stay, and neither size may leave the
     * screen or fall below what a window needs to be readable. Height is
     * as continuous as width — the window keeps the pixel height it is
     * dragged to and clips its topmost line rather than snapping to a
     * whole one.
     */
    private void updateResize(int mouseX, int mouseY) {
        WindowResize resize = this.windowResize;
        ChatWindow window = ChatWindowLayout.window(resize.windowId);
        if (window == null || window.isLocked()) {
            this.windowResize = null;
            return;
        }
        int margin = HudPlacementLayout.SCREEN_MARGIN;
        double minWidth = ChatWindowPlacement.minBoxWidth(this.mc);
        double pointerX = ChatWindowPlacement.preciseMouseX(this.mc, this.width)
                - resize.grabX;
        double pointerY = ChatWindowPlacement.preciseMouseY(this.mc, this.height)
                - resize.grabY;
        resize.left = resize.startLeft;
        resize.right = resize.startRight;
        if (resize.edge.horizontal) {
            // The margin bounds a window's width exactly as it bounds
            // its position: it may reach the margin and go no further.
            if (resize.edge.fromLeft) {
                resize.left = Math.max(margin, Math.min(
                        resize.startRight - minWidth, pointerX));
            } else {
                resize.right = Math.min(this.width - margin, Math.max(
                        resize.startLeft + minWidth, pointerX));
            }
            // Back within a few pixels of the width the drag started
            // from, the moving edge snaps home, so a nudge that was
            // never meant as a resize undoes itself exactly.
            if (Math.abs(resize.right - resize.left
                    - (resize.startRight - resize.startLeft))
                    <= RESIZE_START_SNAP) {
                resize.left = resize.startLeft;
                resize.right = resize.startRight;
            }
        }
        resize.lines = ChatWindowPlacement.currentLines(window, this.mc);
        resize.top = resize.startTop;
        resize.bottom = resize.startBottom;
        if (resize.edge.vertical) {
            // The margin bounds the height the way it bounds the width.
            double room = resize.edge.fromTop
                    ? resize.startBottom - margin
                    : this.height - margin - resize.startTop;
            double height = Math.min(room, resize.edge.fromTop
                    ? resize.startBottom - pointerY
                    : pointerY - resize.startTop);
            // The same snap on this axis, independent of the other: a
            // corner drag can give one axis back and keep the other.
            if (Math.abs(height - (resize.startBottom - resize.startTop))
                    <= RESIZE_START_SNAP) {
                height = resize.startBottom - resize.startTop;
            }
            resize.lines = Math.max(ChatWindowLayout.MIN_WINDOW_LINES,
                    Math.min(ChatWindowLayout.MAX_WINDOW_LINES,
                            ChatWindowPlacement.linesForHeight(height,
                                    this.mc)));
            int applied = ChatWindowPlacement.heightForLines(resize.lines,
                    this.mc);
            if (resize.edge.fromTop) {
                resize.top = resize.startBottom - applied;
            } else {
                resize.bottom = resize.startTop + applied;
            }
        }
    }

    /** Gives the window the size the outline shows and writes it down. */
    private void applyResize(WindowResize resize) {
        ChatWindow window = ChatWindowLayout.window(resize.windowId);
        if (window == null) {
            return;
        }
        if (resize.edge.vertical) {
            ChatWindowLayout.setWindowLines(resize.windowId, resize.lines,
                    true);
        }
        // The width goes first: a window's stored position is a percent
        // of the travel its own width leaves, so the percent has to be
        // worked out against the width the window is about to have.
        if (resize.edge.horizontal) {
            ChatWindowPlacement.applyWindowWidth(this.mc, window,
                    ChatWindowPlacement.chatWidthForBox(
                            resize.right - resize.left, this.mc));
        }
        // The window keeps the corner the drag did not hold: its left
        // edge and its baseline are what the layout stores, so a drag
        // from the left or the bottom moves them by exactly as much as
        // the box grew.
        double baseline = resize.bottom
                - ChatWindowPlacement.barHeight(this.mc);
        ChatWindowLayout.setPosition(resize.windowId,
                ChatWindowPlacement.windowPercentX(window, resize.left,
                        this.mc, this.width),
                ChatWindowPlacement.windowPercentY(baseline, this.mc,
                        this.height), true);
        updateInputBounds();
    }

    private boolean isDragging() {
        return (this.tabDrag != null && this.tabDrag.active)
                || (this.windowDrag != null && this.windowDrag.active)
                || (this.windowResize != null && this.windowResize.active);
    }

    private void cancelDrags() {
        this.tabDrag = null;
        this.windowResize = null;
        if (this.windowDrag != null) {
            if (this.windowDrag.active) {
                ChatWindowLayout.persist();
            }
            this.windowDrag = null;
        }
    }

    /** Arms a window drag from the pointer's current position. */
    private WindowDrag windowDragFrom(ChatWindowFrame frame, int mouseX,
                                      int mouseY, boolean active) {
        // A window taken hold of comes to the front, dragged or not.
        ChatWindowLayout.raise(frame.windowId);
        return new WindowDrag(frame.windowId,
                ChatWindowPlacement.preciseMouseX(this.mc, this.width)
                        - frame.boxLeft,
                ChatWindowPlacement.preciseMouseY(this.mc, this.height)
                        - frame.baseline,
                mouseX, mouseY, active);
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
                closeChannel(hit.tab);
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
     * A left press on one of the rows. Selecting a tab also arms a drag;
     * the controls act at once.
     */
    private boolean handleRowClick(int mouseX, int mouseY) {
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
                    selectWindow(window);
                    if (!window.isLocked()) {
                        this.windowDrag = windowDragFrom(frame, mouseX,
                                mouseY, true);
                    }
                    return true;
                }
                continue;
            }
            // Anything on a window's strip is a click on that window,
            // so the input moves there whichever control was pressed.
            selectWindow(window);
            switch (hit.kind) {
                case TAB:
                    selectChannel(hit.tab);
                    if (!window.isLocked()) {
                        List<ChatChannelTabBar.Tab> tabs =
                                frame.tabBar.layout(this.fontRendererObj, row);
                        int grab = 0;
                        for (ChatChannelTabBar.Tab tab : tabs) {
                            if (tab.tab.equals(hit.tab)) {
                                grab = mouseX - (row.offsetX + tab.x);
                            }
                        }
                        this.tabDrag = new TabDrag(hit.tab,
                                window.getId(), mouseX, mouseY, grab);
                    }
                    return true;
                case CLOSE:
                    closeChannel(hit.tab);
                    return true;
                case SETTINGS:
                    openSettingsPopup(hit.tab, mouseX,
                            ChatChannelTabBar.rowTop(row.rowBottom) - 2);
                    return true;
                case LOCK:
                    setWindowLocked(window, !window.isLocked());
                    return true;
                case RESTORE:
                    this.restoreWindowId = window.getId();
                    openRestorePopup(mouseX,
                            ChatChannelTabBar.rowTop(row.rowBottom) - 2);
                    return true;
                case GRIP:
                    if (!window.isLocked()) {
                        this.windowDrag = windowDragFrom(frame, mouseX,
                                mouseY, true);
                    }
                    return true;
                default:
                    return true;
            }
        }
        return false;
    }

    /**
     * Locks or unlocks a window, and with it whether it is stuck to a
     * neighbour: a window locked while it touches another sticks to it
     * and moves with it from then on, and unlocking lets go again. The
     * locked window is the one that follows, since a locked window is
     * the one that cannot be dragged.
     */
    private void setWindowLocked(ChatWindow window, boolean locked) {
        if (window == null) {
            return;
        }
        ChatWindowLayout.setLocked(window.getId(), locked);
        if (!locked) {
            ChatWindowLayout.unlink(window.getId());
            return;
        }
        ChatWindowFrame frame = ChatWindowFrame.find(window.getId());
        if (frame == null || !frame.drawn) {
            return;
        }
        ChatWindow neighbour = touchingWindow(window, frame);
        if (neighbour != null
                // The window it touches may already be stuck to this one;
                // two windows never hold each other.
                && !window.getId().equals(neighbour.getLinkTarget())) {
            ChatWindowLayout.link(window.getId(), neighbour.getId(),
                    touchingSide(frame, neighbour));
        }
    }

    /** The window this one is resting against, or null. */
    private ChatWindow touchingWindow(ChatWindow window,
                                      ChatWindowFrame frame) {
        List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
        for (int index = 0; index < frames.size(); index++) {
            ChatWindowFrame other = frames.get(index);
            if (other == frame) {
                continue;
            }
            ChatWindow candidate = ChatWindowLayout.window(other.windowId);
            if (candidate != null && touchingSide(frame, candidate) != null) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Which of a neighbour's edges this window is resting against, or
     * null when it is against none of them. The same margin a snap uses,
     * so a window that showed the touch highlight is one that sticks.
     */
    private ChatWindow.LinkSide touchingSide(ChatWindowFrame frame,
                                             ChatWindow neighbour) {
        ChatWindowFrame other = ChatWindowFrame.find(neighbour.getId());
        if (other == null || !other.drawn) {
            return null;
        }
        int margin = HudPlacementLayout.SCREEN_MARGIN;
        boolean overlapsColumn = frame.boxLeft < other.boxRight + margin
                && frame.boxRight + margin > other.boxLeft;
        boolean overlapsRow = frame.boxTop < other.boxBottom + margin
                && frame.boxBottom + margin > other.boxTop;
        if (overlapsColumn) {
            if (Math.abs(other.boxTop - margin - frame.boxBottom)
                    <= LINK_SNAP) {
                return ChatWindow.LinkSide.ABOVE;
            }
            if (Math.abs(frame.boxTop - (other.boxBottom + margin))
                    <= LINK_SNAP) {
                return ChatWindow.LinkSide.BELOW;
            }
        }
        if (overlapsRow) {
            if (Math.abs(frame.boxRight + margin - other.boxLeft)
                    <= LINK_SNAP) {
                return ChatWindow.LinkSide.LEFT;
            }
            if (Math.abs(frame.boxLeft - (other.boxRight + margin))
                    <= LINK_SNAP) {
                return ChatWindow.LinkSide.RIGHT;
            }
        }
        return null;
    }

    private void closeChannel(ChatTab channel) {
        if (ClientChatChannelState.close(channel)) {
            syncSelection();
        }
    }

    /**
     * The cog menu's way of giving a channel a window of its own: the
     * new window lands a little below its old row, kept on screen, and
     * the channel stays selected there.
     */
    private void detachChannel(ChatTab channel) {
        ChatWindow window = ChatWindowLayout.windowOf(channel);
        if (window == null) {
            return;
        }
        ChatWindowFrame frame = frameFor(window);
        ChatWindowPlacement.Anchor anchor = ChatWindowPlacement.constrainWindow(
                null, this.mc, frame.boxLeft,
                frame.tabRowBottom() + DETACH_DROP
                        + ChatWindowPlacement.lineHeight(this.mc),
                this.width, this.height);
        if (ChatWindowLayout.detach(channel,
                ChatWindowPlacement.windowPercentX(anchor.x, this.mc,
                        this.width),
                ChatWindowPlacement.windowPercentY(anchor.baseline, this.mc,
                        this.height)) != null) {
            selectChannel(channel);
        }
    }

    /**
     * The cog menu's five entries, each its own independent preference
     * or action: Mute Channel (out of the feed), Mute Mentions (cue
     * silent), Hide Channel (stays closed when messaged), Move to its
     * own Window, and Close Channel.
     */
    private void openSettingsPopup(ChatTab channel, int anchorX,
                                   int anchorBottom) {
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>(5);
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
        // Layout actions the row may have no room for: a window of its
        // own, and closing, offered whenever the layout would allow it.
        ChatWindow window = ChatWindowLayout.windowOf(channel);
        if (window != null && !window.isLocked()
                && window.getTabs().size() > 1
                && ChatWindowLayout.windows().size()
                        < ChatWindowLayout.MAX_WINDOWS) {
            entries.add(new ChatPopupMenu.Entry(ENTRY_DETACH,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.tab.detach")));
        }
        if (ClientChatChannelState.isClosable(channel)) {
            entries.add(new ChatPopupMenu.Entry(ENTRY_CLOSE,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.tab.close")));
        }
        this.popup.open(POPUP_SETTINGS, channel, entries,
                this.fontRendererObj, anchorX - 4, anchorBottom,
                this.width, this.height);
    }

    private void openRestorePopup(int anchorX, int anchorBottom) {
        this.restoreRefreshedNanos = System.nanoTime();
        this.popup.open(POPUP_RESTORE, null, restoreEntries(),
                this.fontRendererObj, anchorX - 4, anchorBottom,
                this.width, this.height);
    }

    /**
     * Keeps the open {@code +} menu current: its rows follow players
     * joining and leaving and counts changing, rebuilt on an interval
     * rather than per frame. A menu whose rows have all gone closes.
     */
    private void refreshRestorePopup() {
        if (!this.popup.isOpen()
                || !POPUP_RESTORE.equals(this.popup.kind())) {
            return;
        }
        long now = System.nanoTime();
        if (now - this.restoreRefreshedNanos < RESTORE_REFRESH_NANOS) {
            return;
        }
        this.restoreRefreshedNanos = now;
        this.popup.replaceEntries(restoreEntries(), this.fontRendererObj,
                this.width, this.height);
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
    private List<ChatPopupMenu.Entry> restoreEntries() {
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
        List<String> players = whisperCandidates();
        if (!players.isEmpty()) {
            entries.add(ChatPopupMenu.Entry.header(
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.open.players")));
            for (String name : players) {
                ChatTab conversation = ChatTab.whisper(name);
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
    private static String withCounter(String name, int unread) {
        String counter = ClientChatChannelViews.counterText(unread);
        return counter.length() == 0 ? name : name + " " + counter;
    }

    /**
     * Whether any other account is online to open a conversation with.
     * Asked per frame for the {@code +} control, so it only scans; the
     * menu itself builds the sorted list.
     */
    private boolean hasWhisperCandidates() {
        if (this.mc.thePlayer == null
                || this.mc.thePlayer.sendQueue == null
                || this.mc.thePlayer.sendQueue.playerInfoList == null) {
            return false;
        }
        String self = this.mc.thePlayer.getCommandSenderName();
        for (Object value : this.mc.thePlayer.sendQueue.playerInfoList) {
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
    private List<String> whisperCandidates() {
        List<String> result = new ArrayList<String>();
        if (this.mc.thePlayer == null
                || this.mc.thePlayer.sendQueue == null
                || this.mc.thePlayer.sendQueue.playerInfoList == null) {
            return result;
        }
        String self = this.mc.thePlayer.getCommandSenderName();
        for (Object value : this.mc.thePlayer.sendQueue.playerInfoList) {
            if (!(value instanceof GuiPlayerInfo)) {
                continue;
            }
            String account = ((GuiPlayerInfo)value).name;
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
    private static int closedUnreadCount() {
        int total = 0;
        for (ChatChannel channel : restorableChannels()) {
            total += ClientChatChannelViews.unreadCount(channel);
        }
        return Math.min(ClientChatChannelViews.MAX_UNREAD + 1, total);
    }

    private void handlePopupEntry(ChatPopupMenu.Entry entry) {
        if ("appearance".equals(this.popup.kind())) {
            handleAppearanceEntry(entry);
            return;
        }
        if (POPUP_SETTINGS.equals(this.popup.kind())) {
            ChatTab channel = this.popup.channel();
            if (channel == null) {
                return;
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
                detachChannel(channel);
            } else if (ENTRY_CLOSE.equals(entry.id)) {
                closeChannel(channel);
            }
        } else if (POPUP_RESTORE.equals(this.popup.kind())) {
            String target = this.restoreWindowId != null
                    ? this.restoreWindowId
                    : ChatWindowLayout.firstWindow().getId();
            ChatChannel channel = ChatChannel.fromId(entry.id);
            if (channel != null) {
                if (ChatWindowLayout.restore(channel, target)) {
                    selectChannel(channel);
                }
                return;
            }
            // A player row opens (or finds) the conversation with them,
            // in the window whose row the menu was opened from.
            ChatTab conversation = ChatTab.fromId(entry.id);
            if (conversation != null && conversation.isWhisper()
                    && !conversation.isNpc()) {
                ChatTab tab = ChatWindowLayout.openTab(conversation, target);
                if (tab != null) {
                    selectChannel(tab);
                }
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY,
                                  int clickedMouseButton,
                                  long timeSinceLastClick) {
        if (clickedMouseButton != 0) {
            super.mouseClickMove(mouseX, mouseY, clickedMouseButton,
                    timeSinceLastClick);
            return;
        }
        if (this.windowResize != null) {
            if (!this.windowResize.active
                    && (Math.abs(mouseX - this.windowResize.pressX)
                    >= DRAG_THRESHOLD
                    || Math.abs(mouseY - this.windowResize.pressY)
                            >= DRAG_THRESHOLD)) {
                this.windowResize.active = true;
                closePickers();
            }
            if (this.windowResize.active) {
                updateResize(mouseX, mouseY);
            }
            return;
        }
        if (this.windowDrag != null) {
            if (!this.windowDrag.active
                    && (Math.abs(mouseX - this.windowDrag.pressX)
                    >= DRAG_THRESHOLD
                    || Math.abs(mouseY - this.windowDrag.pressY)
                            >= DRAG_THRESHOLD)) {
                this.windowDrag.active = true;
                closePickers();
            }
            if (this.windowDrag.active) {
                moveDraggedWindow(mouseX, mouseY);
            }
            return;
        }
        if (this.tabDrag != null) {
            if (!this.tabDrag.active
                    && (Math.abs(mouseX - this.tabDrag.pressX) >= DRAG_THRESHOLD
                    || Math.abs(mouseY - this.tabDrag.pressY)
                            >= DRAG_THRESHOLD)) {
                this.tabDrag.active = true;
                closePickers();
            }
            if (this.tabDrag.active) {
                updateDropTarget(mouseX, mouseY);
            }
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton,
                timeSinceLastClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            if (this.windowResize != null) {
                WindowResize resize = this.windowResize;
                this.windowResize = null;
                if (resize.active) {
                    applyResize(resize);
                }
            }
            if (this.windowDrag != null) {
                WindowDrag drag = this.windowDrag;
                this.windowDrag = null;
                if (drag.active) {
                    // Touching only shows what a window would stick to;
                    // locking it is what sticks it.
                    ChatWindowLayout.persist();
                } else {
                    // Never moved: the press was a click on the lines.
                    clickLines(drag.pressX, drag.pressY, drag.pressY
                            - Math.round(inputEntranceOffset()), 0);
                }
            }
            if (this.tabDrag != null) {
                if (this.tabDrag.active) {
                    dropTab(mouseX, mouseY);
                }
                this.tabDrag = null;
            }
        }
        super.mouseMovedOrUp(mouseX, mouseY, mouseButton);
    }

    /**
     * Keeps the dragged window's percent position under the pointer,
     * from the raw mouse so the motion is as fine as the display.
     */
    private void moveDraggedWindow(int mouseX, int mouseY) {
        ChatWindow window = ChatWindowLayout.window(this.windowDrag.windowId);
        if (window == null || window.isLocked()) {
            this.windowDrag = null;
            return;
        }
        ChatWindowPlacement.Anchor anchor = ChatWindowPlacement.constrainWindow(
                window, this.mc,
                ChatWindowPlacement.preciseMouseX(this.mc, this.width)
                        - this.windowDrag.grabOffsetX,
                ChatWindowPlacement.preciseMouseY(this.mc, this.height)
                        - this.windowDrag.grabOffsetY,
                this.width, this.height);
        List<ChatWindow> group = ChatWindowLayout.linkedGroup(window);
        if (group.size() > 1) {
            // Stuck windows move as one piece: every one of them takes
            // the same step the dragged one took, in both directions, so
            // the group keeps its shape whichever way it is carried.
            ChatWindowFrame frame = ChatWindowFrame.find(window.getId());
            if (frame == null) {
                return;
            }
            double deltaX = anchor.x - frame.boxLeft;
            double deltaY = anchor.baseline - frame.baseline;
            for (int index = 0; index < group.size(); index++) {
                ChatWindow member = group.get(index);
                ChatWindowFrame memberFrame =
                        ChatWindowFrame.find(member.getId());
                if (memberFrame == null) {
                    continue;
                }
                ChatWindowPlacement.Anchor moved =
                        ChatWindowPlacement.constrainWindow(member, this.mc,
                                memberFrame.boxLeft + deltaX,
                                memberFrame.baseline + deltaY,
                                this.width, this.height);
                ChatWindowLayout.setPosition(member.getId(),
                        ChatWindowPlacement.windowPercentX(member, moved.x,
                                this.mc, this.width),
                        ChatWindowPlacement.windowPercentY(moved.baseline,
                                this.mc, this.height), false);
            }
            return;
        }
        double baseline = snapToNeighbour(window, anchor);
        ChatWindowLayout.setPosition(window.getId(),
                ChatWindowPlacement.windowPercentX(window, anchor.x, this.mc,
                        this.width),
                ChatWindowPlacement.windowPercentY(baseline, this.mc,
                        this.height), false);
    }

    /**
     * Snaps the dragged window to another window's top or bottom edge
     * when it comes within a few pixels of it, a margin apart, and
     * remembers that edge so the release links the two; the snapped
     * baseline is returned.
     */
    private double snapToNeighbour(ChatWindow window,
                                   ChatWindowPlacement.Anchor anchor) {
        int margin = HudPlacementLayout.SCREEN_MARGIN;
        int width = ChatWindowPlacement.windowWidth(window, this.mc);
        int height = ChatWindowPlacement.currentHeight(window, this.mc);
        int barHeight = ChatWindowPlacement.barHeight(this.mc);
        double top = anchor.baseline - (height - barHeight);
        double bottom = anchor.baseline + barHeight;
        this.windowDrag.snapTargetId = null;
        double best = Double.MAX_VALUE;
        double baseline = anchor.baseline;
        List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
        for (int index = 0; index < frames.size(); index++) {
            ChatWindowFrame frame = frames.get(index);
            ChatWindow other = ChatWindowLayout.window(frame.windowId);
            if (other == null || other == window
                    || window.getId().equals(other.getLinkTarget())) {
                continue;
            }
            boolean overlapsColumn = anchor.x < frame.boxRight + margin
                    && anchor.x + width + margin > frame.boxLeft;
            if (overlapsColumn) {
                double aboveGap = Math.abs(frame.boxTop - margin - bottom);
                if (aboveGap <= LINK_SNAP && aboveGap < best) {
                    best = aboveGap;
                    baseline = frame.boxTop - margin - barHeight;
                    this.windowDrag.snapTargetId = other.getId();
                    this.windowDrag.snapSide = ChatWindow.LinkSide.ABOVE;
                }
                double belowGap = Math.abs(top - (frame.boxBottom + margin));
                if (belowGap <= LINK_SNAP && belowGap < best) {
                    best = belowGap;
                    baseline = frame.boxBottom + margin + (height - barHeight);
                    this.windowDrag.snapTargetId = other.getId();
                    this.windowDrag.snapSide = ChatWindow.LinkSide.BELOW;
                }
            }
            // A side snap wants the two windows level with one another,
            // the way a top or bottom snap wants them in one column.
            boolean overlapsRow = top < frame.boxBottom + margin
                    && bottom + margin > frame.boxTop;
            if (!overlapsRow) {
                continue;
            }
            double leftGap = Math.abs(
                    anchor.x + width + margin - frame.boxLeft);
            if (leftGap <= LINK_SNAP && leftGap < best) {
                best = leftGap;
                this.windowDrag.snapTargetId = other.getId();
                this.windowDrag.snapSide = ChatWindow.LinkSide.LEFT;
            }
            double rightGap = Math.abs(
                    anchor.x - (frame.boxRight + margin));
            if (rightGap <= LINK_SNAP && rightGap < best) {
                best = rightGap;
                this.windowDrag.snapTargetId = other.getId();
                this.windowDrag.snapSide = ChatWindow.LinkSide.RIGHT;
            }
        }
        return baseline;
    }

    /** The edge a drag is about to link to, lit along its whole width. */
    private void drawLinkHighlight() {
        if (this.windowDrag == null || !this.windowDrag.active
                || this.windowDrag.snapTargetId == null) {
            return;
        }
        ChatWindowFrame target = ChatWindowFrame.find(
                this.windowDrag.snapTargetId);
        if (target == null || !target.drawn) {
            return;
        }
        int left = (int)Math.floor(target.boxLeft);
        int right = (int)Math.round(target.boxRight);
        int top = (int)Math.floor(target.boxTop);
        int bottom = (int)Math.round(target.boxBottom);
        int colour = LostTalesChatVisualStyle.argb(LINK_HIGHLIGHT_RGB, 0xFF);
        // The edge the window would stick to, lit along its whole length.
        switch (this.windowDrag.snapSide) {
            case ABOVE:
                drawRect(left, top - 1, right, top + 1, colour);
                return;
            case BELOW:
                drawRect(left, bottom - 1, right, bottom + 1, colour);
                return;
            case LEFT:
                drawRect(left - 1, top, left + 1, bottom, colour);
                return;
            default:
                drawRect(right - 1, top, right + 1, bottom, colour);
        }
    }

    /**
     * Finds the row the dragged tab would dock into at the pointer: a
     * row whose band the pointer is in (with some slack around its tabs)
     * and whose window is not locked.
     */
    private void updateDropTarget(int mouseX, int mouseY) {
        this.tabDrag.targetWindowId = null;
        this.tabDrag.targetIndex = -1;
        LostTalesGuiAnimationSample opening =
                ClientChatChannelViews.openSample();
        List<ChatWindow> windows = ChatWindowLayout.stacked();
        for (int index = windows.size() - 1; index >= 0; index--) {
            ChatWindow window = windows.get(index);
            if (window.isLocked()) {
                continue;
            }
            ChatWindowFrame frame = frameFor(window);
            ChatChannelTabBar.Row row = rowFor(window, frame, opening);
            if (row == null || !ChatChannelTabBar.inRowBand(row, mouseY)) {
                continue;
            }
            // The whole strip docks, end controls and grip included: a
            // tab carried to the row's far end lands after the last tab
            // rather than the drag being cancelled there.
            int left = row.offsetX + row.left - DOCK_SLACK;
            int right = row.offsetX + row.right + DOCK_SLACK;
            if (mouseX < left || mouseX >= right) {
                continue;
            }
            this.tabDrag.targetWindowId = window.getId();
            this.tabDrag.targetIndex = frame.tabBar.dropIndexAt(
                    this.fontRendererObj, row, mouseX);
            return;
        }
    }

    /**
     * Releases a dragged tab: onto a row it docks (or reorders), far from
     * its own row it detaches into a new window under the pointer, and
     * anywhere else the drag is simply cancelled.
     */
    private void dropTab(int mouseX, int mouseY) {
        updateDropTarget(mouseX, mouseY);
        TabDrag drag = this.tabDrag;
        ChatWindow source = ChatWindowLayout.windowOf(drag.tab);
        if (source == null || !source.getId().equals(drag.sourceWindowId)) {
            return;
        }
        if (drag.targetWindowId != null) {
            ChatWindow target = ChatWindowLayout.window(drag.targetWindowId);
            if (target == null) {
                return;
            }
            // The drop index counts the row's visible tabs; the window's
            // own list also holds open tabs the player cannot currently
            // see (Party outside a party, Faction without one), sitting
            // between them. The insertion point is translated onto the
            // full list, or the move lands beside the wrong neighbour.
            List<ChatTab> visible = ChatWindowFrame.visibleTabs(target);
            List<ChatTab> all = target.getTabs();
            int index;
            if (visible.isEmpty()) {
                index = all.size();
            } else if (drag.targetIndex >= visible.size()) {
                index = all.indexOf(visible.get(visible.size() - 1)) + 1;
            } else {
                index = all.indexOf(visible.get(drag.targetIndex));
            }
            if (drag.targetWindowId.equals(source.getId())) {
                int from = all.indexOf(drag.tab);
                if (index > from) {
                    index--;
                }
            }
            ChatWindowLayout.moveTab(drag.tab, drag.targetWindowId,
                    index);
            selectChannel(drag.tab);
            return;
        }
        ChatWindowFrame sourceFrame = frameFor(source);
        ChatChannelTabBar.Row sourceRow = rowFor(source, sourceFrame,
                ClientChatChannelViews.openSample());
        if (sourceRow != null) {
            int rowTop = ChatChannelTabBar.rowTop(sourceRow.rowBottom);
            int distance = mouseY < rowTop ? rowTop - mouseY
                    : mouseY >= sourceRow.rowBottom
                            ? mouseY - sourceRow.rowBottom : 0;
            if (distance < DETACH_DISTANCE) {
                return;
            }
        }
        // The new window's row lands under the pointer: an empty window's
        // row stands one line above its baseline.
        int rowTop = mouseY - ChatChannelTabBar.ROW_HEIGHT / 2;
        ChatWindowPlacement.Anchor anchor = ChatWindowPlacement.constrainWindow(
                null, this.mc, mouseX - drag.grabOffsetX - 2,
                rowTop + ChatChannelTabBar.ROW_HEIGHT
                        + ChatWindowPlacement.lineHeight(this.mc),
                this.width, this.height);
        ChatWindow window = ChatWindowLayout.detach(drag.tab,
                ChatWindowPlacement.windowPercentX(anchor.x, this.mc,
                        this.width),
                ChatWindowPlacement.windowPercentY(anchor.baseline, this.mc,
                        this.height));
        if (window != null) {
            selectChannel(drag.tab);
        }
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
        if (hit == null || !this.mc.gameSettings.chatLinks) {
            return false;
        }
        if (ChatHeadMarker.isMarker(hit.component)) {
            // The head, like the name, opens a whisper with the sender.
            ClickEvent reply = findReplySuggestion(hit.line);
            if (reply != null) {
                openWhisperTab(replyAccount(reply.getValue()));
            }
            return true;
        }
        ChatMentionMarker.Data mention =
                ChatMentionMarker.decode(hit.component);
        if (mention != null) {
            // A mention answers like a name: it opens the conversation
            // with whoever it reaches.
            openWhisperTab(mention.account);
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
                || ChatTitleMarker.isMarker(hit.component)) {
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
                && event.getValue().startsWith("/msg ")) {
            openWhisperTab(replyAccount(event.getValue()));
        } else if (event.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
            this.inputField.setText(event.getValue());
        } else if (event.getAction() == ClickEvent.Action.RUN_COMMAND) {
            this.func_146403_a(event.getValue());
        } else if (event.getAction() == ClickEvent.Action.OPEN_URL) {
            openChatLink(event.getValue());
        }
        return true;
    }

    /** The line's {@code /msg} suggestion, shared by name and head. */
    /** The account named by a {@code /msg Account } suggestion. */
    private static String replyAccount(String suggestion) {
        return suggestion == null || !suggestion.startsWith("/msg ") ? ""
                : suggestion.substring("/msg ".length()).trim();
    }

    private static ClickEvent findReplySuggestion(IChatComponent line) {
        if (line == null) {
            return null;
        }
        for (Object value : line) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            ClickEvent event = part.getChatStyle() == null
                    ? null : part.getChatStyle().getChatClickEvent();
            if (event != null
                    && event.getAction() == ClickEvent.Action.SUGGEST_COMMAND
                    && event.getValue() != null
                    && event.getValue().startsWith("/msg ")) {
                return event;
            }
        }
        return null;
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
     * Bare {@code [Channel]} label in the channel's colour with the shared
     * shadow and no backdrop; hovering brightens it to ivory so the cycle
     * affordance is still visible, and a channel the player can only read
     * is drawn muted.
     */
    private void drawIndicator(int mouseX, int mouseY) {
        ChatTab channel = ClientChatChannelState.getSelected();
        int width = indicatorWidth();
        int left = indicatorLeft();
        int top = this.barTop;
        boolean hovered = isInsideIndicator(mouseX, mouseY);
        // A read-only channel reads italic rather than faint: text is
        // always at full opacity.
        String label = ClientChatChannelState.canSend(channel)
                ? indicatorLabel(channel) : "§o" + indicatorLabel(channel);
        int color = hovered ? LostTalesChatVisualStyle.IVORY
                : ClientChatChannelState.displayColor(channel);
        LostTalesChatVisualStyle.drawColored(this.fontRendererObj,
                label, left + 1, barTextTop(), color, 255);
        this.regions.add(left, top, left + width,
                top + ChatWindowPlacement.INPUT_HEIGHT);
    }

    /** Left edge of the appearance button, the bar's first control. */
    private int appearanceButtonLeft() {
        return this.barLeft + 2;
    }

    /** Left edge of the channel indicator, past the appearance button. */
    private int indicatorLeft() {
        return this.barLeft + 2 + ChatPickerPanel.BUTTON_SIZE + 2;
    }

    private boolean isInsideAppearanceButton(int mouseX, int mouseY) {
        int left = appearanceButtonLeft();
        int top = barControlTop();
        return mouseX >= left && mouseX < left + ChatPickerPanel.BUTTON_SIZE
                && mouseY >= top && mouseY < top + ChatPickerPanel.BUTTON_SIZE;
    }

    /**
     * The appearance button: the head of whoever the selected tab would
     * currently speak as, lifted a pixel under the pointer like the
     * picker buttons, with the padlock in its corner while the choice is
     * locked. Clicking opens the appearance menu.
     */
    private void drawAppearanceButton(int mouseX, int mouseY) {
        int left = appearanceButtonLeft();
        int top = barControlTop();
        boolean open = this.popup.isOpen()
                && "appearance".equals(this.popup.kind());
        boolean hovered = isInsideAppearanceButton(mouseX, mouseY);
        int lift = open || hovered ? 1 : 0;
        ClientChatAppearances.Appearance shown =
                ClientChatAppearances.effectiveFor(
                        ClientChatChannelState.getSelected());
        UUID self = this.mc.thePlayer == null ? null
                : this.mc.thePlayer.getUniqueID();
        if (self != null) {
            float headX = left + 2;
            float headY = top + 2 - lift;
            if (shown.account || shown.skinId.length() == 0) {
                LostTalesCharacterHeadIconRenderer.drawAccountHead(
                        this.mc, self, headX, headY, 8.0F, 1.0F, 1.0F);
            } else {
                LostTalesCharacterHeadIconRenderer.drawSnapshotHead(
                        this.mc, self, shown.skinId, headX, headY, 8.0F,
                        1.0F, 1.0F);
            }
        }
        if (ClientChatAppearances.isLocked()) {
            ChatIconSheet.LOCKED.drawWithShadow(
                    left + ChatPickerPanel.BUTTON_SIZE
                            - ChatIconSheet.LOCKED.getWidth(),
                    top + ChatPickerPanel.BUTTON_SIZE
                            - ChatIconSheet.LOCKED.getHeight(), 255);
        }
        if (hovered && !isDragging()) {
            this.hoverTip = StatCollector.translateToLocal(
                    "gui.losttales.chat.appearance.tip");
            this.hoverTipX = mouseX;
            this.hoverTipY = mouseY;
        }
        this.regions.add(left, top, left + ChatPickerPanel.BUTTON_SIZE,
                top + ChatPickerPanel.BUTTON_SIZE);
    }

    /** The appearance menu, anchored above its button. */
    private void openAppearancePopup() {
        this.popup.open("appearance", null, appearanceEntries(),
                this.fontRendererObj, appearanceButtonLeft(),
                barControlTop() - 2, this.width, this.height);
    }

    private List<ChatPopupMenu.Entry> appearanceEntries() {
        ChatTab selected = ClientChatChannelState.getSelected();
        UUID self = this.mc.thePlayer == null ? null
                : this.mc.thePlayer.getUniqueID();
        boolean locked = ClientChatAppearances.isLocked();
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>();
        entries.add(new ChatPopupMenu.Entry("appearance:lock",
                StatCollector.translateToLocal(locked
                        ? "gui.losttales.chat.appearance.unlock"
                        : "gui.losttales.chat.appearance.lock"))
                .withSprite(locked ? ChatIconSheet.LOCKED
                        : ChatIconSheet.UNLOCKED));
        entries.add(ChatPopupMenu.Entry.header(
                StatCollector.translateToLocal(
                        "gui.losttales.chat.appearance.account")));
        entries.add(appearanceEntry("appearance:account",
                ClientChatAppearances.accountAppearance(), selected, self));
        List<ClientChatAppearances.Appearance> characters =
                ClientChatAppearances.characterAppearances();
        if (!characters.isEmpty()) {
            entries.add(ChatPopupMenu.Entry.header(
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.appearance.characters")));
            for (ClientChatAppearances.Appearance appearance : characters) {
                entries.add(appearanceEntry(
                        "appearance:char:" + appearance.characterId,
                        appearance, selected, self));
            }
        }
        List<ClientChatAppearances.Appearance> lore =
                ClientChatAppearances.loreAppearances();
        if (!lore.isEmpty()) {
            entries.add(ChatPopupMenu.Entry.header(
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.appearance.lore")));
            for (ClientChatAppearances.Appearance appearance : lore) {
                entries.add(appearanceEntry(
                        "appearance:char:" + appearance.characterId,
                        appearance, selected, self));
            }
        }
        return entries;
    }

    /** One choosable identity: its head, its name, and the mention honey
     *  as the swatch of the one the tab currently speaks as. */
    private ChatPopupMenu.Entry appearanceEntry(
            String id, ClientChatAppearances.Appearance appearance,
            ChatTab selected, UUID self) {
        boolean effective = ClientChatAppearances.isEffective(
                appearance, selected);
        return new ChatPopupMenu.Entry(id, appearance.name, false,
                effective ? LostTalesColors.rgb(LostTalesColors.HONEY) : -1,
                null).withHead(self, appearance.skinId);
    }

    private void handleAppearanceEntry(ChatPopupMenu.Entry entry) {
        if ("appearance:lock".equals(entry.id)) {
            ClientChatAppearances.toggleLocked(
                    ClientChatChannelState.getSelected());
            return;
        }
        if ("appearance:account".equals(entry.id)) {
            ClientChatAppearances.select(
                    ClientChatAppearances.accountAppearance());
            return;
        }
        if (!entry.id.startsWith("appearance:char:")) {
            return;
        }
        UUID characterId;
        try {
            characterId = UUID.fromString(
                    entry.id.substring("appearance:char:".length()));
        } catch (IllegalArgumentException ignored) {
            return;
        }
        for (ClientChatAppearances.Appearance appearance
                : ClientChatAppearances.characterAppearances()) {
            if (characterId.equals(appearance.characterId)) {
                ClientChatAppearances.select(appearance);
                return;
            }
        }
        for (ClientChatAppearances.Appearance appearance
                : ClientChatAppearances.loreAppearances()) {
            if (characterId.equals(appearance.characterId)) {
                ClientChatAppearances.select(appearance);
                return;
            }
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

    /** The bars' entrance from below, timed from the screen's opening. */
    private float inputEntranceOffset() {
        if (!LostTalesConfig.enableChatAnimations) {
            return 0.0F;
        }
        long duration = Math.max(1,
                LostTalesConfig.chatInputAnimationDurationMillis)
                * 1000000L;
        float progress = Math.max(0.0F, Math.min(1.0F,
                (System.nanoTime() - openedAtNanos) / (float)duration));
        return LostTalesChatMotion.inputOffset(progress);
    }

    /**
     * Puts vanilla's up/down history cursor back after the newest sent
     * message, as its {@code initGui} does, since the screen is not
     * rebuilt between messages any more. The field is private; a Forge
     * layout without it only costs the cursor position.
     */
    private void resetSentHistoryCursor() {
        if (SENT_HISTORY_CURSOR == null) {
            return;
        }
        try {
            SENT_HISTORY_CURSOR.setInt(this,
                    this.mc.ingameGUI.getChatGUI().getSentMessages().size());
        } catch (IllegalAccessException ignored) {
        } catch (RuntimeException ignored) {
        }
    }

    private static java.lang.reflect.Field findSentHistoryCursor() {
        try {
            java.lang.reflect.Field field =
                    GuiChat.class.getDeclaredField("sentHistoryCursor");
            if (field.getType() != int.class) {
                return null;
            }
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void showNotice(String text) {
        this.noticeText = text == null ? "" : text;
        this.noticeNanos = System.nanoTime();
    }

    /** Short centred confirmation above the input bar (copy, too long). */
    private void drawNotice() {
        if (this.noticeNanos <= 0L || this.noticeText.length() == 0) {
            return;
        }
        float ageMillis = (System.nanoTime() - this.noticeNanos)
                / 1000000.0F;
        if (ageMillis >= NOTICE_LIFETIME_MILLIS) {
            this.noticeNanos = 0L;
            return;
        }
        float opacity = 1.0F;
        if (LostTalesConfig.enableChatAnimations) {
            opacity = Math.min(1.0F, ageMillis / 100.0F)
                    * Math.min(1.0F,
                            (NOTICE_LIFETIME_MILLIS - ageMillis) / 250.0F);
        }
        int alpha = Math.max(LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA,
                Math.min(255, Math.round(255.0F * opacity)));
        int popupWidth = this.fontRendererObj.getStringWidth(
                this.noticeText) + 10;
        // Centred over the input bar, which is as wide as the history.
        int x = Math.max(2, (this.barLeft + inputBarRight() - popupWidth) / 2);
        int y = this.barTop - 17 + Math.round((1.0F - opacity) * 3.0F);
        drawRect(x, y, x + popupWidth, y + 13,
                (Math.min(220, alpha) << 24)
                        | LostTalesChatVisualStyle.SURFACE_RGB);
        LostTalesChatVisualStyle.drawPlain(this.fontRendererObj,
                this.noticeText, x + 5, y + 2, alpha);
    }

    private boolean isInsideIndicator(int mouseX, int mouseY) {
        int top = this.barTop;
        int left = indicatorLeft();
        return mouseX >= left
                && mouseX < left + indicatorWidth()
                && mouseY >= top && mouseY < top + ChatWindowPlacement.INPUT_HEIGHT;
    }

    private int indicatorWidth() {
        return this.fontRendererObj.getStringWidth(
                indicatorLabel(ClientChatChannelState.getSelected())) + 6;
    }

    private static String indicatorLabel(ChatTab tab) {
        return "[" + ClientChatChannelState.displayName(tab) + "]";
    }

    /* ---- The bar's send arrow and character counter ---- */

    private static int sendButtonLeft(int barRight) {
        return barRight - ChatPickerPanel.BUTTON_MARGIN
                - (SEND_BUTTON_INDEX + 1)
                * (ChatPickerPanel.BUTTON_SIZE + ChatPickerPanel.BUTTON_MARGIN)
                + ChatPickerPanel.BUTTON_MARGIN;
    }

    private boolean isInsideSendButton(int mouseX, int mouseY, int barRight) {
        int left = sendButtonLeft(barRight);
        int top = barControlTop();
        return mouseX >= left && mouseX < left + ChatPickerPanel.BUTTON_SIZE
                && mouseY >= top && mouseY < top + ChatPickerPanel.BUTTON_SIZE;
    }

    /**
     * A right-pointing arrow at the bar's right end, drawn from
     * rectangles like the tab controls, lifted a pixel while hovered;
     * dim while there is nothing to send.
     */
    private void drawSendButton(int barRight, int mouseX, int mouseY) {
        int left = sendButtonLeft(barRight);
        int top = barControlTop();
        boolean hovered = isInsideSendButton(mouseX, mouseY, barRight);
        boolean ready = this.inputField.getText().trim().length() > 0;
        // Text and glyphs are always fully opaque; the arrow says
        // what it can do with its colour, not by fading out.
        int alpha = 255;
        int y = top + (hovered ? 0 : 1);
        int x = left + 2;
        int color = LostTalesChatVisualStyle.argb(
                hovered || ready ? LostTalesChatVisualStyle.IVORY
                        : LostTalesColors.rgb(LostTalesColors.MAUVE),
                alpha);
        int shadow = LostTalesChatVisualStyle.argb(
                LostTalesChatVisualStyle.SHADOW,
                LostTalesChatVisualStyle.shadowAlpha(alpha));
        for (int pass = 0; pass < 2; pass++) {
            int c = pass == 0 ? shadow : color;
            int ox = x + (pass == 0 ? 1 : 0);
            int oy = y + (pass == 0 ? 1 : 0);
            // Shaft, then a head of three shrinking columns.
            drawRect(ox, oy + 5, ox + 5, oy + 7, c);
            drawRect(ox + 4, oy + 2, ox + 5, oy + 10, c);
            drawRect(ox + 5, oy + 3, ox + 6, oy + 9, c);
            drawRect(ox + 6, oy + 4, ox + 7, oy + 8, c);
            drawRect(ox + 7, oy + 5, ox + 8, oy + 7, c);
        }
        this.regions.add(left, top, left + ChatPickerPanel.BUTTON_SIZE,
                top + ChatPickerPanel.BUTTON_SIZE);
    }

    /** {@code 37/100} for a message; nothing for a command. */
    private String counterText() {
        if (isCommand()) {
            return "";
        }
        return ChatMessageValidator.visibleLength(this.inputField.getText())
                + "/" + ChatMessageValidator.MAX_CHARACTERS;
    }

    private int counterLeft(int barRight) {
        String text = counterText();
        int width = text.length() == 0 ? 0
                : (int)Math.ceil(this.fontRendererObj.getStringWidth(text)
                        * COUNTER_SCALE) + 3;
        return controlsLeft(barRight) - width;
    }

    /** The counter in the timestamp's sand, salmon once the limit is hit. */
    private void drawCounter(int barRight) {
        String text = counterText();
        if (text.length() == 0) {
            return;
        }
        boolean full = ChatMessageValidator.visibleLength(
                this.inputField.getText())
                >= ChatMessageValidator.MAX_CHARACTERS;
        // Drawn at three quarters: a footnote beside the arrow, centred
        // on the bar like the full-size text was.
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(counterLeft(barRight), barTextTop() + 1,
                    0.0F);
            GL11.glScalef(COUNTER_SCALE, COUNTER_SCALE, 1.0F);
            LostTalesChatVisualStyle.drawColored(this.fontRendererObj, text,
                    0, 0, full ? COUNTER_FULL_RGB : COUNTER_RGB, 255,
                    COUNTER_SCALE);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private void updateInputBounds() {
        if (this.inputField == null) {
            return;
        }
        // The field's position is the bar's whole-pixel part, and the
        // group it is drawn in is translated by the fractional
        // remainder. Both must come from the same reading of the
        // window's box: mixing one frame's whole pixels with the next
        // frame's fraction makes the text jump about while the window
        // is dragged.
        updateInputBox();
        int left = indicatorLeft() + indicatorWidth() + 3;
        // The counter sits left of the send arrow; the field ends before it.
        int right = counterLeft(inputBarRight()) - 3;
        this.inputField.xPosition = left;
        this.inputField.yPosition = barTextTop();
        this.inputField.width = Math.max(20, right - left);
    }
}
