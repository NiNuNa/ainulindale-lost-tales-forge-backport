package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
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
import com.google.common.collect.ObjectArrays;
import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatDeletePacket;
import com.ninuna.losttales.network.packet.LostTalesChatEditPacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
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
import net.minecraft.network.play.client.C14PacketTabComplete;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.ClientCommandHandler;
import org.apache.commons.lang3.StringUtils;
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
    /*
     * Leaving a row and coming back to it are two questions about one
     * pointer, so each direction gets a pull that takes a tab out and a
     * shorter reach that puts it back. Between the two lies a band where
     * neither happens; that band is the whole point. A single threshold
     * left one pixel between out and in, and a hand that never holds
     * quite still crossed it every other frame.
     *
     * The two directions are not the same size, because what they are
     * measured from is not the same thing. Above and below there is only
     * the strip, a thin thing to steer a carried window onto, and a
     * pointer a little off it is plainly aiming at it. To the sides the
     * measure is the row's room — the stretch tabs actually sit in — and
     * a tab held a little outside it is still being offered to the row.
     * Sideways therefore needs the longer reach of the two, or a tab
     * could only be brought back by laying it almost exactly on the tab
     * already sitting there.
     */

    /** How far above or below a row a dragged tab leaves it. */
    static final int DETACH_DISTANCE = 14;
    /** How far above or below a row a tab may still be put back into it. */
    static final int RETURN_DISTANCE = 7;
    /** How far past either end of a row's room a dragged tab leaves it. */
    static final int SIDE_DETACH_DISTANCE = 26;
    /** How far outside that room a tab may still be put back into it. */
    static final int SIDE_RETURN_DISTANCE = 16;
    /** Horizontal slack around a row that still counts as dropping on it. */
    private static final int DOCK_SLACK = 24;
    /** Distance from another window's edge at which a drag snaps and links. */
    private static final int LINK_SNAP = 6;
    /** Height vanilla gives the chat's field, kept for the one we draw. */
    private static final int FIELD_HEIGHT = 12;
    /** How far outside its edge a window still answers to a resize. */
    private static final int RESIZE_BORDER = 4;
    /** How far along an edge from a corner still counts as that corner. */
    private static final int RESIZE_CORNER = 12;
    /** The send arrow is the rightmost bar control. */
    private static final int SEND_BUTTON_INDEX = 0;
    /** The bar's divider, as tall as the row's between window controls. */
    private static final int BAR_DIVIDER_HEIGHT =
            ChatChannelTabBar.END_CONTROL_SIZE;
    /** Gap between the typing line's bubble and its words. */
    private static final int TYPING_BUBBLE_GAP = 3;
    private static final float COUNTER_SCALE = 0.75F;
    private static final int COUNTER_RGB =
            LostTalesColors.rgb(LostTalesColors.SAND);
    private static final int COUNTER_FULL_RGB =
            LostTalesColors.rgb(LostTalesColors.SALMON);
    private static final int LINK_HIGHLIGHT_RGB =
            LostTalesColors.rgb(LostTalesColors.HONEY);
    /** The quiet mauve the reply line is named in. */
    /** The chat's asides — the reply chip, the typing line, the
     *  timestamps — all wear this one quiet tone. */
    private static final int REPLY_CHIP_RGB =
            LostTalesColors.rgb(LostTalesColors.ROSE_GRAY);
    private static final String POPUP_MESSAGE = "message";
    private static final String POPUP_SETTINGS = "settings";
    private static final String POPUP_CHARACTERS = "characters";
    private static final String POPUP_RESTORE = "restore";
    private static final String POPUP_WINDOW = "window";
    private static final String POPUP_SEARCH = "search";
    /** Marks a search row that jumps to a tab already open. */
    private static final String ENTRY_OPEN_PREFIX = "open:";
    /** The keys the search panel's field names as its own shortcut. */
    private static final int[] SEARCH_SHORTCUT_KEYS = {
            Keyboard.KEY_LCONTROL, Keyboard.KEY_LSHIFT, Keyboard.KEY_A };
    private static final String ENTRY_MESSAGE = "message_player";
    private static final String ENTRY_REPLY = "reply";
    private static final String ENTRY_COPY = "copy";
    private static final String ENTRY_EDIT = "edit";
    private static final String ENTRY_DELETE = "delete";
    /** The second half of deleting: the row that is the confirmation. */
    private static final String ENTRY_DELETE_CONFIRM = "delete_confirm";
    private static final String ENTRY_MUTE = "mute";
    private static final String ENTRY_PINGS = "pings";
    private static final String ENTRY_HIDE = "hide";
    private static final String ENTRY_DETACH = "detach";
    private static final String ENTRY_CLOSE = "close";
    private static final String ENTRY_WINDOW_UNSTICK = "window_unstick";
    private static final String ENTRY_WINDOW_RESET = "window_reset";
    /** Where a detached window's row lands: a little below its old one. */
    private static final int DETACH_DROP = 40;
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

    private static final java.lang.reflect.Field SENT_HISTORY_CURSOR =
            findSentHistoryCursor();
    /**
     * When the input bar's entrance began: the screen opening, and again
     * whenever the bar arrives in another window.
     */
    private long barEntranceNanos = System.nanoTime();
    /** Set once a message or command has gone out; the draft is then spent. */
    private boolean sent;
    /** How often a typing player repeats itself to the server. */
    private static final long TYPING_HEARTBEAT_NANOS = 2500L * 1000000L;
    /**
     * How long after the last keystroke a player still counts as
     * typing. Typing is what somebody is <em>doing</em>, not what their
     * input bar happens to hold: a message left half-written while they
     * walk away said "typing" for as long as they were gone. Shorter
     * than the receiving side's own time to live, so the explicit stop
     * always arrives first.
     */
    private static final long TYPING_IDLE_NANOS = 4000L * 1000000L;
    /** The message the open message menu was opened over. */
    private long menuMessageId = ChatMessageIds.NONE;
    private String menuMessageAccount = "";
    /** The identity that message was signed with: who a whisper reaches. */
    private String menuMessageIdentity = "";
    /** Whether it came over the Discord bridge, where nobody can be reached. */
    private boolean menuMessageFromDiscord;
    private String menuMessageText = "";
    /** Where the menu was opened, so the delete confirmation lands there too. */
    private int menuX;
    private int menuY;
    private int menuChatLineId;
    /**
     * The message being replied to, the name it is shown under, and the
     * tab it is being answered in. Cleared when the reply is sent, when
     * the tab changes, and on Escape — a reply belongs to the message in
     * front of you, so it never survives moving away from it.
     */
    private long replyToMessageId = ChatMessageIds.NONE;
    private String replyToName = "";
    /**
     * What the answered message said, taken off the line when the reply
     * was started. The server resolves its own quote and this goes
     * unused there; an NPC's conversation has no server, so this is the
     * only record the quote can be built from.
     */
    private String replyToExcerpt = "";
    private ChatTab replyTab;
    /**
     * The message being rewritten, and the tab it was said in. Editing
     * and replying are the same gesture aimed at different ends of a
     * message, so they share the strip above the bar and never both
     * hold it: starting one puts the other down.
     */
    private long editingMessageId = ChatMessageIds.NONE;
    private ChatTab editingTab;
    /** The reply chip drawn this frame, for the click that dismisses it. */
    private int composerChipLeft;
    private int composerChipTop;
    private int composerChipRight;
    private int composerChipBottom;
    /** The tab the server last heard this player typing into, or null. */
    private ChatTab typingTab;
    private long typingSentNanos;
    /** The field's text as the typing check last saw it, and when. */
    private String typedText = "";
    private long typedNanos;
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
    /** The chevron's frames, pointing right through upright to left. */
    private static final ChatIconSheet[] TOGGLE_FRAMES = {
            ChatIconSheet.TOGGLE_1, ChatIconSheet.TOGGLE_2,
            ChatIconSheet.TOGGLE_3, ChatIconSheet.TOGGLE_4,
            ChatIconSheet.TOGGLE_5};
    private static final ChatIconSheet[] TOGGLE_FRAMES_HOVER = {
            ChatIconSheet.TOGGLE_1_HOVER, ChatIconSheet.TOGGLE_2_HOVER,
            ChatIconSheet.TOGGLE_3_HOVER, ChatIconSheet.TOGGLE_4_HOVER,
            ChatIconSheet.TOGGLE_5_HOVER};
    private final ChatIconFlipbook toolbarToggle =
            new ChatIconFlipbook(TOGGLE_FRAMES, TOGGLE_FRAMES_HOVER);
    private final ChatEmojiSuggestionBox emojiSuggestions =
            new ChatEmojiSuggestionBox();
    private final ChatNameSuggestionBox nameSuggestions =
            new ChatNameSuggestionBox();
    private final ChatShareSuggestionBox shareSuggestions =
            new ChatShareSuggestionBox();
    /**
     * Command tab completion, owned here rather than left to vanilla:
     * the suggestions belong to the input they were asked from, so the
     * candidate list is filed under the tab that was selected when Tab
     * was pressed instead of falling into the console as an untracked
     * line. The states mirror {@code GuiChat}'s private completion
     * fields, which a subclass cannot reach.
     */
    private final List<String> completionCandidates = new ArrayList<String>();
    /** Index of the candidate standing in the field; -1 for none yet. */
    private int completionCycleIndex = -1;
    /** Set while further Tabs walk {@link #completionCandidates}. */
    private boolean completionCycling;
    /** Set between sending a completion request and its answer. */
    private boolean completionWaiting;
    /** The tab whose input the pending or walked completion belongs to. */
    private ChatTab completionTab;
    /** The candidates as a popup over the input, never a chat line. */
    private final ChatCommandSuggestionBox commandSuggestions =
            new ChatCommandSuggestionBox();
    private final ChatPopupMenu popup = new ChatPopupMenu();
    private TabDrag tabDrag;
    private WindowDrag windowDrag;
    private WindowResize windowResize;
    /** Window a restore popup was opened from. */
    private String restoreWindowId;
    /** Window the open window-settings menu belongs to, or null. */
    private String settingsWindowId;
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
    /**
     * The window the bar is in, so its arrival in another one can be
     * told from a redraw of the one it is already in.
     */
    private String barWindowId;
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
        if (isEmptyState()) {
            // No field is drawn and nothing can be typed; the drafts the
            // tabs already hold are left exactly as they are.
            stopTyping();
            return;
        }
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
        // A screen closed mid-drag ends the drag where it stands: this
        // instance is gone and nothing else would ever release it.
        cancelDrags();
        if (!isEmptyState()) {
            ClientChatChannelState.setDraft(
                    this.sent ? "" : this.inputField.getText());
        }
        ClientChatChannelViews.setScrollEasingSuppressed(false);
        // Every divider that was on a viewed tab has done its job.
        ClientChatChannelViews.dismissSeenDividers();
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
        long now = System.nanoTime();
        String text = this.inputField.getText();
        if (!text.equals(this.typedText)) {
            this.typedText = text;
            this.typedNanos = now;
        }
        boolean typing = LostTalesConfig.sendChatTypingStatus
                && text.trim().length() > 0
                && now - this.typedNanos < TYPING_IDLE_NANOS
                && !isCommand() && !selected.isNpc()
                && ClientChatChannelState.canSend(selected);
        if (!typing) {
            stopTyping();
            return;
        }
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
     * What the bar is composing, in the gap between the history and the
     * bar: {@code Replying to Name} or {@code Editing message}, with a
     * cross to drop it. It stands where the typing line does and takes
     * the strip while it shows — what you are answering, or correcting,
     * matters more while you are doing it than who else is talking.
     * Returns whether it drew, so the typing line knows to stand down.
     */
    private boolean drawComposerChip(ChatWindow window, ChatWindowFrame frame,
                                  LostTalesGuiAnimationSample opening,
                                  int mouseX, int mouseY) {
        this.composerChipRight = 0;
        ChatTab composing = isEditing() ? this.editingTab
                : isReplying() ? this.replyTab : null;
        if (composing == null || !composing.equals(
                ChatWindowFrame.activeTab(window,
                        ChatWindowFrame.visibleTabs(window)))) {
            return false;
        }
        int alpha = Math.round(255.0F * opening.getOpacity());
        if (alpha < 4) {
            return false;
        }
        // The line begins where the messages above it do: past the
        // timestamp column, not across it.
        ChatTimestampColumn columns =
                ChatTimestampColumn.current(this.fontRendererObj);
        int inset = Math.round(columns.messageX() * frame.scale);
        int x = (int)Math.floor(frame.drawnLeft()) + inset;
        int room = (int)Math.round(frame.boxRight - frame.boxLeft)
                - inset - 6;
        int y = (int)Math.floor(frame.drawnBaseline())
                + LostTalesChatOverlayRenderer.LINE_HEIGHT
                - LostTalesChatOverlayRenderer.TEXT_OFFSET;
        String label = this.fontRendererObj.trimStringToWidth(
                isEditing()
                        ? StatCollector.translateToLocal(
                                "gui.losttales.chat.editing")
                        : StatCollector.translateToLocalFormatted(
                                "gui.losttales.chat.message.replying",
                                this.replyToName),
                Math.max(20, room - ChatIconSheet.CLOSE.getWidth() - 6));
        int width = this.fontRendererObj.getStringWidth(label);
        // The cross is the control, so its own box is what answers to
        // the pointer — a little wider than the sprite, so a five-pixel
        // glyph is not a five-pixel target.
        int crossX = x + width + 3;
        int crossY = y + 1;
        this.composerChipLeft = crossX - 2;
        this.composerChipTop = crossY - 2;
        this.composerChipRight = crossX + ChatIconSheet.CLOSE.getWidth() + 2;
        this.composerChipBottom = crossY + ChatIconSheet.CLOSE.getHeight() + 2;
        LostTalesChatVisualStyle.drawColored(this.fontRendererObj, label,
                x, y, REPLY_CHIP_RGB, alpha);
        ChatIconSheet cross = composerChipContains(mouseX, mouseY)
                ? ChatIconSheet.CLOSE_HOVER : ChatIconSheet.CLOSE;
        cross.drawWithShadow(crossX, crossY, alpha);
        return true;
    }

    /** Whether the point lies on the reply chip drawn this frame. */
    private boolean composerChipContains(int x, int y) {
        return this.composerChipRight > this.composerChipLeft
                && x >= this.composerChipLeft && x < this.composerChipRight
                && y >= this.composerChipTop && y < this.composerChipBottom;
    }

    /**
     * Who is typing into the window's front tab, in the gap between the
     * history and the bar: one or two names, three names, or a count
     * past that. Nothing is drawn while nobody is.
     */
    private void drawTypingLine(ChatWindow window, ChatWindowFrame frame,
                                LostTalesGuiAnimationSample opening,
                                int mouseX, int mouseY) {
        if (drawComposerChip(window, frame, opening, mouseX, mouseY)
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
                textX, y, REPLY_CHIP_RGB, alpha);
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
     * Places the one input section. Its home is the active window's bar
     * — the strip below that window's newest line, as wide as the window
     * — read from the frame the window pass drew, motion included;
     * everything that belongs to the bar (indicator, field, toolbar,
     * pickers, completion lists, notices) is placed from it, so the bar
     * follows its window exactly however that window is dragged or
     * resized.
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

    /**
     * Notices the bar changing hands. It is not carried between windows:
     * it goes with the window it is leaving and arrives in the new one on
     * the same entrance from below it plays when the screen opens — the
     * bar alone, since the window it lands on is already there.
     */
    private void noteInputWindow() {
        ChatWindowFrame frame = activeFrame();
        String windowId = frame == null ? null : frame.windowId;
        if (windowId != null && this.barWindowId != null
                && !windowId.equals(this.barWindowId)) {
            this.barEntranceNanos = System.nanoTime();
        }
        this.barWindowId = windowId;
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
     * right while they are out (they fold back toward it), and playing
     * the sheet's frames between the two when it is flipped, on the same
     * duration and easing as the chat's other motion. Lifted a pixel
     * under the pointer like the buttons beside it, with the sheet's
     * hover state.
     */
    private void drawToolbarToggle(int barRight, int mouseX, int mouseY) {
        boolean collapsed = ChatWindowLayout.isToolbarCollapsed();
        boolean hovered = isInsideToolbarToggle(mouseX, mouseY, barRight);
        int left = toolbarToggleLeft(barRight);
        this.toolbarToggle.advance(collapsed, hovered);
        this.toolbarToggle.draw(left,
                barControlTop() + (hovered ? 0 : 1),
                ChatPickerPanel.BUTTON_SIZE, ChatPickerPanel.BUTTON_SIZE,
                255);
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
        // A set of marks is anchored on the tab being typed in and
        // always holds it: a tab that has gone is forgotten, and moving
        // the input off the set — to another tab, or another window —
        // ends it.
        ChatTabSelection.prune();
        if (ChatTabSelection.windowId() != null
                && !ChatTabSelection.isSelected(selected)) {
            ChatTabSelection.clear();
        }
        // The selected tab is always the front tab of its window, whether
        // the selection just changed or the layout came back from its
        // file with another tab in front; setting what is already set
        // changes nothing.
        ChatWindowLayout.setActiveTab(selected);
        if (!selected.equals(this.lastSelected)) {
            ChatTab previous = this.lastSelected;
            this.lastSelected = selected;
            // The window being typed in comes to the front, a completion
            // walked in the tab just left is over, and so is its read
            // unread divider: it was there to be seen, and it was.
            dismissCompletion();
            ClientChatChannelViews.dismissSeenDivider(previous);
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
        // Vanilla's own rule: any key ends a pending completion request,
        // and any key but Tab ends the walk through the candidates —
        // except that while the candidate popup is open, Up and Down
        // walk it too, and Escape only closes it.
        this.completionWaiting = false;
        if (this.commandSuggestions.isActive()) {
            if (keyCode == Keyboard.KEY_UP) {
                stepCompletion(-1);
                return;
            }
            if (keyCode == Keyboard.KEY_DOWN) {
                stepCompletion(1);
                return;
            }
            if (keyCode == Keyboard.KEY_ESCAPE) {
                dismissCompletion();
                return;
            }
        }
        if (keyCode != Keyboard.KEY_TAB) {
            this.completionCycling = false;
            this.commandSuggestions.clear();
        }
        if (keyCode == Keyboard.KEY_ESCAPE
                && (this.popup.isOpen() || isDragging())) {
            this.popup.close();
            cancelDrags();
            return;
        }
        // A searchable list takes plain typing while it is open; the
        // shortcuts that act on the chat still reach past it.
        if (this.popup.isSearchable() && !isCtrlKeyDown()
                && this.popup.handleKeyTyped(typedChar, keyCode)) {
            refreshSearchPanel();
            return;
        }
        // Escape with nothing else open drops the reply before it closes
        // the chat: backing out of an answer should not cost the screen.
        if (keyCode == Keyboard.KEY_ESCAPE
                && (isReplying() || isEditing())) {
            cancelComposing();
            return;
        }
        // Ctrl+N is the + control by keyboard, and Ctrl+Shift+A the
        // search panel: both mean something with nothing open, since
        // both are ways back to a channel.
        if (isCtrlKeyDown() && isShiftKeyDown()
                && keyCode == Keyboard.KEY_A) {
            openSearchPanel(ChatWindowLayout.windowOf(
                    ClientChatChannelState.getSelected()), -1, -1);
            return;
        }
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_N) {
            openChannelMenu();
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
            closeMarkedOrActiveTabs();
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
        if (keyCode == Keyboard.KEY_TAB) {
            // With text in the field Tab is command completion. Handled
            // here, never by super: vanilla's completion would print the
            // candidate list as an untracked chat line, which files as
            // console output.
            completeInput();
            return;
        }
        if (refusesCharacter(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
        maybeSpaceTypedShortcode(typedChar);
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
            // Speaking moves the conversation on: the unread divider
            // has done its job, exactly as it does on Discord.
            ClientChatChannelViews.dismissDivider(
                    ClientChatChannelState.getSelected());
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
        String text = this.inputField.getText();
        int cursor = this.inputField.getCursorPosition();
        if (cursor < 3 || cursor > text.length()
                || text.charAt(cursor - 1) != ':'
                || (cursor < text.length() && text.charAt(cursor) == ' ')) {
            return;
        }
        int open = cursor - 2;
        while (open > 0 && isShortcodeNameCharacter(text.charAt(open))) {
            open--;
        }
        if (text.charAt(open) != ':' || open >= cursor - 2) {
            return;
        }
        if (ChatEmoji.fromName(text.substring(open + 1, cursor - 1))
                != null) {
            this.inputField.writeText(" ");
        }
    }

    /** The shortcode alphabet, exactly as the parser scans it. */
    private static boolean isShortcodeNameCharacter(char character) {
        return (character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9')
                || character == '_';
    }

    /**
     * Tab in a non-empty field: walk an answered candidate list, or ask
     * for one. The request path is vanilla's — client commands complete
     * locally through {@code ClientCommandHandler}, everything else is
     * asked of the server — but the answer comes back to
     * {@link #func_146406_a}, which files the candidate list under the
     * tab the request was typed in.
     */
    private void completeInput() {
        if (this.completionCycling && !this.completionCandidates.isEmpty()) {
            if (ClientChatChannelState.getSelected().equals(
                    this.completionTab)) {
                insertCompletion((this.completionCycleIndex + 1)
                        % this.completionCandidates.size());
                return;
            }
            // The walked list belongs to another tab's input; the field
            // now holds this tab's draft, so start over on it.
            dismissCompletion();
        }
        String beforeCursor = this.inputField.getText().substring(0,
                this.inputField.getCursorPosition());
        if (beforeCursor.length() < 1 || this.mc.thePlayer == null
                || this.mc.thePlayer.sendQueue == null) {
            return;
        }
        int wordStart = this.inputField.func_146197_a(-1,
                this.inputField.getCursorPosition(), false);
        ClientCommandHandler.instance.autoComplete(beforeCursor,
                this.inputField.getText().substring(wordStart)
                        .toLowerCase(Locale.ROOT));
        this.mc.thePlayer.sendQueue.addToSendQueue(
                new C14PacketTabComplete(beforeCursor));
        this.completionWaiting = true;
        this.completionTab = ClientChatChannelState.getSelected();
    }

    /**
     * The completion answer. Inserts the candidates' common prefix, or —
     * when the word already is that prefix — starts walking the list and
     * shows it as a popup over the input. An answer arriving after the
     * selection moved is dropped: the field holds another tab's draft,
     * and splicing into that would corrupt it.
     */
    @Override
    public void func_146406_a(String[] serverCompletions) {
        if (!this.completionWaiting || serverCompletions == null) {
            return;
        }
        this.completionWaiting = false;
        this.completionCycling = false;
        this.completionCandidates.clear();
        this.completionCycleIndex = -1;
        this.commandSuggestions.clear();
        ChatTab owner = this.completionTab;
        if (owner == null
                || !owner.equals(ClientChatChannelState.getSelected())) {
            return;
        }
        String[] merged = serverCompletions;
        String[] client = ClientCommandHandler.instance.latestAutoComplete;
        if (client != null) {
            merged = ObjectArrays.concat(client, serverCompletions,
                    String.class);
        }
        for (String candidate : merged) {
            if (candidate != null && candidate.length() > 0) {
                this.completionCandidates.add(candidate);
            }
        }
        // Vanilla's shape: the prefix is the server candidates' alone.
        String word = this.inputField.getText().substring(
                this.inputField.func_146197_a(-1,
                        this.inputField.getCursorPosition(), false));
        String prefix = EnumChatFormatting.getTextWithoutFormattingCodes(
                StringUtils.getCommonPrefix(serverCompletions));
        if (prefix != null && prefix.length() > 0
                && !word.equalsIgnoreCase(prefix)) {
            this.inputField.deleteFromCursor(
                    this.inputField.func_146197_a(-1,
                            this.inputField.getCursorPosition(), false)
                            - this.inputField.getCursorPosition());
            this.inputField.writeText(prefix);
            // The prefix leaves several ways forward; the popup shows
            // them, nothing highlighted until the walk starts.
            if (this.completionCandidates.size() > 1) {
                this.commandSuggestions.show(this.completionCandidates);
            }
        } else if (!this.completionCandidates.isEmpty()) {
            this.completionCycling = true;
            if (this.completionCandidates.size() > 1) {
                this.commandSuggestions.show(this.completionCandidates);
            }
            insertCompletion(0);
        }
    }

    /** Replaces the word at the cursor with the indexed candidate. */
    private void insertCompletion(int index) {
        this.inputField.deleteFromCursor(
                this.inputField.func_146197_a(-1,
                        this.inputField.getCursorPosition(), false)
                        - this.inputField.getCursorPosition());
        this.inputField.writeText(
                EnumChatFormatting.getTextWithoutFormattingCodes(
                        this.completionCandidates.get(index)));
        this.completionCycleIndex = index;
        this.commandSuggestions.setSelected(index);
    }

    /** Up or Down while the popup is open: walk the list either way. */
    private void stepCompletion(int delta) {
        if (this.completionCandidates.isEmpty()) {
            return;
        }
        this.completionCycling = true;
        int size = this.completionCandidates.size();
        int index = this.completionCycleIndex < 0
                ? (delta > 0 ? 0 : size - 1)
                : ((this.completionCycleIndex + delta) % size + size) % size;
        insertCompletion(index);
    }

    /** Closes the popup and ends the walk; the field keeps its text. */
    private void dismissCompletion() {
        this.completionCycling = false;
        this.commandSuggestions.clear();
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
        if (entry == null || query == null
                || refusesAnotherShareToken(entry.token())) {
            return;
        }
        replaceAtCursor(query.openIndex, entry.token() + " ");
        refreshShareSuggestions();
    }

    /**
     * The share-token ceiling is a wall like the character counter's:
     * the server attaches at most {@link ChatShareTokenParser#MAX_TOKENS}
     * showcases to a message and delivers anything beyond them as the
     * literal text, so a pick that would become dead text is refused
     * with a notice instead of inserted. Only complete tokens count —
     * the opener being completed is not one yet — and only share tokens
     * are walled; an emoji is no showcase.
     */
    private boolean refusesAnotherShareToken(String insertion) {
        if (insertion == null
                || ChatShareTokenParser.parse(insertion).isEmpty()) {
            return false;
        }
        if (ChatShareTokenParser.parse(this.inputField.getText()).size()
                < ChatShareTokenParser.MAX_TOKENS) {
            return false;
        }
        showNotice(StatCollector.translateToLocalFormatted(
                "gui.losttales.chat.too_many_shares",
                Integer.valueOf(ChatShareTokenParser.MAX_TOKENS)));
        return true;
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
        if (word.length() == 0 || refusesAnotherShareToken(word)) {
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
        // never buried under a list of names. They answer in every
        // channel — an operator is worth calling wherever the call is
        // made.
        for (ChatAccountRole role : ChatAccountRole.mentionable()) {
            String name = StatCollector.translateToLocal(
                    role.getNameKey());
            if (name.length() > 0 && !name.equals(role.getNameKey())) {
                result.add(ChatMentionCandidate.role(
                        "role:" + role.name().toLowerCase(Locale.ROOT),
                        name, role.getColor()));
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

    /**
     * Replaces the {@code :prefix} at the cursor with the full
     * shortcode, a space behind it like every other inserted token, so
     * the next word stands apart from the emoji.
     */
    private void acceptSuggestion(ChatEmoji emoji) {
        ChatEmojiSuggester.Query query = emojiSuggestions.getQuery();
        if (emoji == null || query == null) {
            return;
        }
        replaceAtCursor(query.colonIndex, emoji.getShortcode() + " ");
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
        // A message being rewritten is not a new one: it goes back to
        // the server as a correction to the line it came from, and the
        // bar returns to composing from scratch.
        if (isEditing()) {
            long edited = this.editingMessageId;
            cancelEdit();
            LostTalesNetworkHandler.CHANNEL.sendToServer(
                    new LostTalesChatEditPacket(edited, outgoing));
            return;
        }
        if (tab.isNpc()) {
            // Nobody is on the other end of an NPC's conversation: the
            // line is shown here as if whispered, and that is all. What
            // the player shared is resolved here too, since no server
            // will do it for them.
            LostTalesChatPresentation.echoToNpc(tab, outgoing,
                    resolveLocalShowcases(outgoing),
                    isReplying()
                            ? ChatReplyReference.of(this.replyToMessageId,
                                    this.replyToName, this.replyToExcerpt)
                            : ChatReplyReference.NONE);
            cancelReply();
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
        // Shown before it is sent, and named so the copy that comes
        // back replaces it rather than arriving underneath it.
        long echoNonce = LostTalesChatPresentation.echoPending(tab, outgoing,
                resolveLocalShowcases(outgoing),
                isReplying()
                        ? ChatReplyReference.of(this.replyToMessageId,
                                this.replyToName, this.replyToExcerpt)
                        : ChatReplyReference.NONE);
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesChatSendPacket(tab.getChannel(), outgoing,
                        resolveShareReferences(outgoing), tab.getPartner(),
                        ClientChatAppearances.wireKind(),
                        ClientChatAppearances.wireCharacterId(),
                        isReplying() && ChatMessageIds.isServerId(
                                this.replyToMessageId)
                                        ? this.replyToMessageId
                                        : ChatMessageIds.NONE,
                        tab.isWhisper() ? tab.getPartnerIdentity() : "",
                        echoNonce));
        // Answered: the next message is a message of its own.
        cancelReply();
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
            // Shown before it is sent, exactly as a message typed into
            // the tab is: the command is only another way of saying it.
            long echoNonce = LostTalesChatPresentation.echoPending(tab,
                    outgoing, resolveLocalShowcases(outgoing),
                    ChatReplyReference.NONE);
            LostTalesNetworkHandler.CHANNEL.sendToServer(
                    new LostTalesChatSendPacket(ChatChannel.WHISPER, outgoing,
                            resolveShareReferences(outgoing),
                            tab.getPartner(),
                            ClientChatAppearances.wireKind(),
                            ClientChatAppearances.wireCharacterId(),
                            ChatMessageIds.NONE,
                            tab.getPartnerIdentity(), echoNonce));
        }
        return true;
    }

    /**
     * The whisper tab with the named account, opened in the window the
     * player is typing in if it is not yet, and selected. The player's
     * own name opens nothing.
     */
    private ChatTab openWhisperTab(String account) {
        return openWhisperTab(account, "");
    }

    /** As above with one identity of that account; empty is its own. */
    private ChatTab openWhisperTab(String account, String identity) {
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
        ChatTab tab = ChatWindowLayout.openWhisper(name, identity,
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
        // A page is the window's own message room, fractions of a line
        // included, so scrolling to either end lands on a whole message.
        // The reach is the rows the window draws, not its message lines:
        // the two differ by the unread divider's own row.
        ClientChatChannelViews.scroll(frame.view, step,
                frame.contentRows(), frame.roomLines());
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
        noteInputWindow();
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
        // re-read from the pointer every drawn frame so the window and
        // the dock target stay under the cursor. While a resize runs the
        // scroll follows its clamp rigidly instead of gliding after it.
        ClientChatChannelViews.setScrollEasingSuppressed(
                this.windowResize != null && this.windowResize.active);
        if (this.windowResize != null && this.windowResize.active) {
            updateResize(mouseX, mouseY);
        } else if (this.windowDrag != null && this.windowDrag.active) {
            moveDraggedWindow(mouseX, mouseY);
        } else if (this.tabDrag != null && this.tabDrag.active) {
            dragTabs(this.tabDrag, mouseX, mouseY);
        }
        LostTalesChatPresentation.setHoveredLine(
                hoveredMessageLine(mouseX, mouseY));
        markScrollbarsWanted(mouseX, mouseY);
        drawWindows(mouseX, mouseY, partialTicks);
        if (!isDragging()) {
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
                    && composerChipContains(mouseX, mouseY)) {
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
        updateInputBounds();
        refreshRestorePopup();
        this.popup.registerRegion(this.regions);
        if (isEmptyState()) {
            // Nothing is open to type into, so the screen shows what it
            // has instead of a bar with no channel behind it. The bar's
            // pickers go with the bar.
            if (openPicker() != null) {
                closePickers();
            }
            drawEmptyState(mouseX, mouseY);
            drawNotice();
            this.popup.draw(this.fontRendererObj, this.regions, mouseX,
                    mouseY);
            if (this.hoverTip.length() > 0 && !this.popup.isOpen()) {
                drawHoverTip();
            }
            updatePointerFeedback(mouseX, mouseY);
            return;
        }
        int barRight = inputBarRight();
        int anchor = inputAnchor();
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(this.barFracX, this.barFracY + entrance, 0.0F);
            drawInputBar(barRight);
            drawCharacterSelectionButton(mouseX, adjustedMouseY);
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
            drawSendDivider(barRight);
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
            commandSuggestions.draw(this.fontRendererObj, this.regions,
                    anchor, this.inputField.xPosition,
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
            LostTalesChatHoverCard.draw(this.mc, pointerX, pointerY,
                    this.width, this.height);
        }
        drawLinkHighlight();
        this.popup.draw(this.fontRendererObj, this.regions, mouseX, mouseY);
        ChatPopupMenu.Entry hoveredLock =
                this.popup.lockControlAt(mouseX, mouseY);
        if (hoveredLock != null && !isDragging()) {
            // The lock in the character selection menu answers with the
            // same tip its states answer with everywhere else.
            this.hoverTip = StatCollector.translateToLocal(
                    ClientChatAppearances.isLocked()
                            ? "gui.losttales.chat.character_selection.unlock"
                            : "gui.losttales.chat.character_selection.lock");
            this.hoverTipX = mouseX;
            this.hoverTipY = mouseY;
            drawHoverTip();
        }
        if (hoverTip.length() > 0 && !this.popup.isOpen()) {
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
        if (hovered && !this.popup.isOpen()) {
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
            drawTypingLine(window, frame, opening, mouseX, mouseY);
            LostTalesChatOverlayRenderer.drawBottomRule(this.mc, frame,
                    opening);
            LostTalesChatOverlayRenderer.drawWindowLeftEdge(this.mc, frame,
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
        row.marked = ChatTabSelection.selectedIn(window);
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
        row.resizing = this.windowResize != null && this.windowResize.active
                && window.getId().equals(this.windowResize.windowId);
        // A locked window keeps the tabs and the size it has, so it
        // offers neither a tab cross nor the window's own controls: they
        // are all refused anyway, and would only mislead.
        row.closable = ClientChatChannelState.isClosable(row.selected);
        row.windowControls = !window.isLocked();
        row.showRestore = !window.isLocked()
                && (!restorableChannels().isEmpty()
                        || hasWhisperCandidates());
        row.closedUnread = row.showRestore ? closedUnreadCount() : 0;
        // The chevron says whether this row's own search panel is out,
        // so the control and the panel can never disagree about it.
        row.searchOpen = this.popup.isOpen()
                && POPUP_SEARCH.equals(this.popup.kind())
                && window.getId().equals(this.restoreWindowId);
        row.restoreOpen = this.popup.isOpen()
                && POPUP_RESTORE.equals(this.popup.kind())
                && window.getId().equals(this.restoreWindowId);
        if (this.tabDrag != null && this.tabDrag.active) {
            if (window.contains(this.tabDrag.tab)) {
                // The tab keeps its place in the row and leans toward
                // the pointer; the row has already reordered around it,
                // so there is nothing to mark an insertion point for.
                row.dragging = this.tabDrag.tab;
                // Everything travelling with it, so a marked group
                // leans, changes places and stops as one long tab.
                row.draggedGroup = this.tabDrag.group;
                row.draggedLeft = this.tabDrag.pointerX
                        - this.tabDrag.grabOffsetX;
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
     * A click in the strip below another window's bottom rule moves the
     * input to that window. Nothing is drawn there — the input section
     * belongs to the window being typed in and to no other — but the
     * room is still part of the window's box, and pressing it asks for
     * the bar to come there, which is what the bar then does.
     */
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
        // The window's frame edges beside and under the bar are the
        // bar's own: drawn over its fill, so nothing darkens them, and
        // arriving with the bar's fly-in.
        drawBarLeftEdge(activeFrame(), this.barLeft, this.barTop);
        LostTalesChatOverlayRenderer.drawBarBottomEdge(this.barLeft,
                barRight, this.barTop + ChatWindowPlacement.INPUT_HEIGHT
                        - 1, 255);
        this.inputField.drawTextBox();
    }

    /**
     * The bar's stretch of the window's left frame edge, on the same
     * ramp as the window's own stretch above it.
     */
    private static void drawBarLeftEdge(ChatWindowFrame frame, int barLeft,
                                        int barTop) {
        if (frame == null) {
            return;
        }
        float rampBottom = barTop + ChatWindowPlacement.INPUT_HEIGHT - 1;
        LostTalesChatOverlayRenderer.drawLeftEdgeSegment(barLeft, barTop,
                rampBottom, rampBottom,
                (float)(frame.boxBottom - frame.boxTop) - 1.0F, 255);
    }

    /**
     * Hover cards for item, text, and achievement components reproduced
     * from vanilla, plus the tooltips of shared items and markers, drawn
     * after the popups so they layer above everything in the stack. The
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
            if (button == 0
                    && this.popup.lockControlAt(mouseX, mouseY) != null) {
                // The lock is a switch, not a pick: the menu stays open,
                // its rows refreshed, so the padlock answers in place.
                ClientChatAppearances.toggleLocked(
                        ClientChatChannelState.getSelected());
                this.popup.replaceEntries(characterSelectionEntries(),
                        this.fontRendererObj, this.width, this.height);
                return;
            }
            ChatPopupMenu.Entry entry = this.popup.entryAt(mouseX, mouseY);
            boolean inside = this.popup.contains(mouseX, mouseY);
            if (entry != null && button == 0
                    && handlePopupEntry(entry)) {
                // The entry asked a question of its own and the menu is
                // showing it: closing here would close the question
                // along with the menu that asked it.
                return;
            }
            this.popup.close();
            if (inside) {
                return;
            }
        }
        if (isEmptyState()) {
            // The + is the whole of the screen's furniture here; a click
            // anywhere else is the player closing what is not there.
            if (button == 0 && emptyStateContains(mouseX, mouseY)) {
                this.restoreWindowId = null;
                openRestorePopup(mouseX, this.emptyPlusTop - 2);
            }
            return;
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
        if (button == 0 && commandSuggestions.isActive()) {
            int candidate = commandSuggestions.candidateAt(
                    this.fontRendererObj, mouseX, adjustedMouseY,
                    anchor, this.inputField.xPosition);
            if (candidate >= 0) {
                this.completionCycling = true;
                insertCompletion(candidate);
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
        if (button == 0) {
            if (handleRowClick(mouseX, mouseY, closedPopupKind)) {
                return;
            }
            // The marks are the row's; a press anywhere else lets them go.
            ChatTabSelection.clear();
        }
        if (button == 2 && closeTabAt(mouseX, mouseY)) {
            return;
        }
        if (button == 0 && handleBarClick(mouseX, mouseY)) {
            return;
        }
        if (button == 0 && isInsideCharacterButton(mouseX, adjustedMouseY)) {
            // A click on the button while its own menu was open has just
            // closed it above; only then does the click not reopen it.
            if (!POPUP_CHARACTERS.equals(closedPopupKind)) {
                openCharacterSelectionMenu();
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
            if (composerChipContains(mouseX, mouseY)) {
                cancelComposing();
                return;
            }
            if (clickMessageToolbar(mouseX, mouseY)) {
                return;
            }
            if (grabScrollbar(mouseX, mouseY)) {
                return;
            }
        }
        // A right-click opens the message's own menu — reply, copy —
        // not the people in it: over the sender's identity or a mention
        // — wherever the card shows — the click belongs to the person
        // and the menu does not open.
        if (button == 1
                && !LostTalesChatHoverCard.isPointerOnPerson(this.mc,
                        mouseX, mouseY)
                && openMessagePopup(mouseX, mouseY)) {
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
            // Whether this press lands on the window that was already
            // both in front here and the one being typed into — read
            // before the raise below, which would make it so anyway.
            boolean alreadyInFront = frame != null && frame == activeFrame();
            if (window != null) {
                ChatWindowLayout.raise(window.getId());
                selectWindow(window);
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
        selectWindow(behind);
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
        // testing, which no longer matches the 11px layout; only the input
        // field still needs the vanilla click path.
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
    private String messageTextOf(ChatWindowFrame frame, int chatLineId) {
        if (frame.lines == null) {
            return "";
        }
        for (int index = 0; index < frame.lines.size(); index++) {
            if (frame.lines.get(index) != null && frame.lines.get(index)
                    .getChatLineID() == chatLineId) {
                return LostTalesChatClipboard.messageTextOf(
                        frame.lines, index);
            }
        }
        return "";
    }

    /**
     * The identity the message was signed with: the name that answers to
     * the pointer first after the head, which is the sender's own. Empty
     * when the message names nobody.
     */
    private String messageIdentity(List<ChatLine> lines, int index,
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
    private static String identityOfLine(IChatComponent line) {
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
            ClickEvent click = part.getChatStyle() == null ? null
                    : part.getChatStyle().getChatClickEvent();
            if (click != null && click.getValue() != null
                    && click.getValue().startsWith("/msg ")) {
                return part.getUnformattedTextForChat().trim();
            }
        }
        return "";
    }

    /** Whether the message came over the Discord bridge. */
    private static boolean isFromDiscord(List<ChatLine> lines, int index,
                                         int chatLineId) {
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
        if (frame.lines != null) {
            for (int index = 0; index < frame.lines.size(); index++) {
                if (frame.lines.get(index) != null && frame.lines.get(index)
                        .getChatLineID() == chatLineId) {
                    name = messageAccount(frame.lines, index, chatLineId);
                    excerpt = LostTalesChatClipboard.messageTextOf(
                            frame.lines, index);
                    break;
                }
            }
        }
        // The identity the line was signed with beats the drawn rows: a
        // grouped continuation has no header to read a name off, and
        // the server's quote will name the identity anyway.
        ClientChatMessages.Remembered remembered = ClientChatMessages.get(id);
        if (remembered != null) {
            name = remembered.packet.getIdentityName();
        }
        // Composing happens where the message lives, and selecting a tab
        // clears any reply, so the target is set after the move.
        selectChannel(tab);
        this.replyToMessageId = id;
        this.replyToName = name;
        this.replyToExcerpt = excerpt;
        this.replyTab = tab;
    }

    /**
     * Which window's scrollbar should be showing: the one the pointer is
     * in, or the one whose bar is being dragged, so the bar does not
     * fade out from under a drag that has wandered off it. Set before
     * the windows draw, since the draw is what eases it in and out.
     */
    private void markScrollbarsWanted(int mouseX, int mouseY) {
        // Only the window the pointer is really in shows its bar: one
        // covered by another is not being read, whatever its box says.
        ChatWindowFrame pointed = this.scrollbarDrag == null
                ? frameAt(mouseX, mouseY) : null;
        for (ChatWindowFrame frame : ChatWindowFrame.drawnFrames()) {
            frame.scrollbarWanted = this.scrollbarDrag != null
                    ? frame.windowId.equals(this.scrollbarDrag.windowId)
                    : frame == pointed;
        }
    }

    /** A scrollbar being dragged: which window, and where it was grabbed. */
    private static final class ScrollbarDrag {
        final String windowId;
        /** Pointer offset inside the thumb when it was grabbed. */
        final float grabOffset;

        ScrollbarDrag(String windowId, float grabOffset) {
            this.windowId = windowId;
            this.grabOffset = grabOffset;
        }
    }

    private ScrollbarDrag scrollbarDrag;

    /**
     * Grabs a scrollbar. Pressing the thumb carries it from where it was
     * taken hold of; pressing the track above or below jumps to there
     * and then carries it, the way a scrollbar anywhere else does.
     */
    private boolean grabScrollbar(int mouseX, int mouseY) {
        List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
        for (int index = frames.size() - 1; index >= 0; index--) {
            ChatWindowFrame frame = frames.get(index);
            if (!frame.scrollbarContains(mouseX, mouseY)) {
                continue;
            }
            float thumbHeight = frame.scrollbarThumbBottom
                    - frame.scrollbarThumbTop;
            float offset = mouseY >= frame.scrollbarThumbTop
                    && mouseY < frame.scrollbarThumbBottom
                            ? mouseY - frame.scrollbarThumbTop
                            : thumbHeight / 2.0F;
            this.scrollbarDrag =
                    new ScrollbarDrag(frame.windowId, offset);
            dragScrollbar(mouseY);
            return true;
        }
        return false;
    }

    /**
     * Maps the pointer onto the history: where the thumb's top sits in
     * the travel it has is where the view sits in what it can reach.
     */
    private void dragScrollbar(int mouseY) {
        ChatWindowFrame frame = this.scrollbarDrag == null ? null
                : ChatWindowFrame.find(this.scrollbarDrag.windowId);
        if (frame == null || frame.view == null || frame.lines == null) {
            return;
        }
        float thumbHeight = frame.scrollbarThumbBottom
                - frame.scrollbarThumbTop;
        float travel = (frame.scrollbarTrackBottom
                - frame.scrollbarTrackTop) - thumbHeight;
        if (travel <= 0.0F) {
            return;
        }
        float top = mouseY - this.scrollbarDrag.grabOffset;
        float taken = (frame.scrollbarTrackBottom - thumbHeight - top)
                / travel;
        double roomLines = frame.roomLines();
        int rows = frame.contentRows();
        double reach = Math.max(0.0D, rows - roomLines);
        ClientChatChannelViews.scrollTo(frame.view,
                reach * Math.max(0.0F, Math.min(1.0F, taken)),
                rows, roomLines);
    }

    /**
     * The message the pointer rests on, by chat line id, or zero for
     * none. Resolved against the bands the last frame recorded, which is
     * what every other pointer question here asks; a hover a frame
     * behind the pointer is not something an eye can catch. Nothing is
     * hovered while a menu is open or something is being dragged: the
     * pointer is on that, whatever lies under it.
     */
    private int hoveredMessageLine(int mouseX, int mouseY) {
        if (this.popup.isOpen() || isDragging()) {
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
        long messageId = hit == null ? 0L
                : ChatReplyMarker.messageIdOf(hit.component);
        if (messageId == 0L) {
            return false;
        }
        LostTalesChatOverlayRenderer.Band band =
                LostTalesChatOverlayRenderer.bandAt(this.mc,
                        mouseX + 0.5F, mouseY + 0.5F);
        Integer target = ClientChatMessageIds.chatLineIdOf(messageId);
        if (band == null || band.lines == null || target == null) {
            showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.message.gone"));
            return true;
        }
        int index = -1;
        for (int at = 0; at < band.lines.size(); at++) {
            if (band.lines.get(at) != null && band.lines.get(at)
                    .getChatLineID() == target.intValue()) {
                index = at;
                break;
            }
        }
        if (index < 0) {
            // Named, but not in this view: the message is in another
            // channel, or below what this window shows.
            showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.message.gone"));
            return true;
        }
        double roomLines = band.frame.roomLines();
        // Landed near the middle of the window rather than at its edge,
        // so what was said around it is readable too. The offset counts
        // rows, so the line's index is translated through the divider.
        ClientChatChannelViews.scrollTo(band.frame.view,
                LostTalesChatOverlayRenderer.rowOfLine(index,
                        band.frame.dividerLineIndex) - roomLines / 2.0D,
                band.frame.contentRows(), roomLines);
        LostTalesChatPresentation.flashLine(target.intValue());
        return true;
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
        // A reply, and an edit, belong to the tab they were started
        // in; moving away from that tab abandons them rather than
        // carrying them along.
        if (this.replyTab != null && !this.replyTab.equals(tab)) {
            cancelReply();
        }
        if (this.editingTab != null && !this.editingTab.equals(tab)) {
            cancelEdit();
        }
        ClientChatChannelState.select(tab);
        syncSelection();
        closePickers();
        this.inputField.setFocused(true);
        // Mention candidates are shaped per channel identity.
        this.mentionBuiltNanos = 0L;
    }

    /* ---- Tab rows: clicks, drags, docking, detaching, window moves ---- */

    /**
     * A press on a tab that may become a drag; tabs move in raw space.
     * The drag carries every tab that moves with it — the marked group
     * when the pressed tab is one of them, the pressed tab alone
     * otherwise — in the row order they will keep at the destination.
     *
     * <p>A press on a tab that is part of a group leaves the group
     * marked: collapsing to the one tab pressed would take the group
     * away before the drag it is meant to carry could begin, so the
     * collapse waits for the button to come up on a press that never
     * travelled, exactly as a list of files behaves.</p>
     *
     * <p>Carried clear of every row the tabs become a window of their
     * own at once rather than on release, the way a browser tears a tab
     * off; {@link #detachedWindowId} names that window from then on,
     * and it is what follows the pointer instead of the ghost.</p>
     *
     * <p>A window holding one tab starts its drag already torn off, the
     * window it is in being the one it was torn off into: there is
     * nothing to reorder and nothing to take out, so that window is
     * what follows the pointer from the first pixel, and carrying it
     * onto another window's row docks the tab there like any other.</p>
     */
    private static final class TabDrag {
        final ChatTab tab;
        final List<ChatTab> group;
        final String sourceWindowId;
        final int pressX;
        final int pressY;
        final int grabOffsetX;
        /**
         * Where in the <em>window</em> the pressed tab was taken hold
         * of: the same point of the same tab, measured from the left
         * edge of whatever window is carrying it. A row begins its tabs
         * past the search control, so a tab torn off into a window of
         * its own does not start where it did in the row it left; this
         * is what keeps the cursor on the pixel it closed on across
         * that hand-over.
         */
        final int grabOffsetInWindowX;
        /** Where in the row's height the tab was taken hold of. */
        final int grabOffsetY;
        /** Whether releasing without a drag leaves only the pressed tab. */
        final boolean collapsesOnRelease;
        boolean active;
        /**
         * The row the tabs were last thrown out of, or null before they
         * have been thrown out of any. That row asks a little more of
         * them before taking them back — see the return distance — so
         * the two answers about one pointer cannot argue at its edges.
         * It is the row they <em>left</em> rather than the one the drag
         * began in: a tab carried into another window and pulled out of
         * it again needs the same guard there, and a row a tab has never
         * left needs none at all.
         */
        String leftRowId;
        /** Window the tabs were torn off into, or null while in a row. */
        String detachedWindowId;
        /** Where the pointer is, so the row can lean the tab toward it. */
        int pointerX;
        /** Window row the tab would dock into at the current pointer. */
        String targetWindowId;
        int targetIndex = -1;

        TabDrag(ChatTab tab, List<ChatTab> group, String sourceWindowId,
                int pressX, int pressY, int grabOffsetX,
                int grabOffsetInWindowX, int grabOffsetY,
                boolean collapsesOnRelease) {
            this.tab = tab;
            this.group = group;
            this.collapsesOnRelease = collapsesOnRelease;
            this.sourceWindowId = sourceWindowId;
            this.pressX = pressX;
            this.pressY = pressY;
            this.grabOffsetX = grabOffsetX;
            this.grabOffsetInWindowX = grabOffsetInWindowX;
            this.grabOffsetY = grabOffsetY;
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
     * keeps its anchors — the edges the drag does not touch — and the
     * window itself follows the pointer: the temporary size is written
     * into the layout without persisting it, so the history reflows and
     * redraws live, the file is written once on release, and Escape
     * puts the remembered dimensions back exactly.
     */
    private static final class WindowResize {
        final String windowId;
        final ResizeEdge edge;
        /** The box the drag started from, in screen pixels. */
        final double startLeft;
        final double startRight;
        final double startTop;
        final double startBottom;
        /** The stored (committed) state to restore when it is cancelled. */
        final double storedLines;
        final int storedWidth;
        final double storedOffsetX;
        final double storedOffsetY;
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
                     double grabY, int pressX, int pressY, double lines,
                     ChatWindow window) {
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
            this.storedLines = window.getMaxLines();
            this.storedWidth = window.getWidth();
            this.storedOffsetX = window.getOffsetX();
            this.storedOffsetY = window.getOffsetY();
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
        ChatWindowFrame frame = frameAt(mouseX, mouseY);
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
        if (ChatReplyMarker.isMarker(component)) {
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
                ChatWindowPlacement.currentLines(window, this.mc), window);
    }

    /**
     * Follows the pointer with the window itself: the edges the drag
     * holds move, the opposite ones stay, and neither size may leave the
     * screen or fall below what a window needs to be readable. Height is
     * as continuous as width — the window keeps the pixel height it is
     * dragged to and clips its topmost line rather than snapping to a
     * whole one. The size and position are written into the layout
     * without persisting, so the window reflows live and nothing is
     * saved or synchronized until the drag commits.
     */
    private void updateResize(int mouseX, int mouseY) {
        WindowResize resize = this.windowResize;
        ChatWindow window = ChatWindowLayout.window(resize.windowId);
        if (window == null || window.isLocked()) {
            this.windowResize = null;
            return;
        }
        int margin = HudPlacementLayout.SCREEN_MARGIN;
        // The floor follows the window's own tabs, so an edge stops
        // where the row would otherwise start hiding one.
        double minWidth = ChatWindowPlacement.minBoxWidth(this.mc, window);
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
        }
        resize.lines = ChatWindowPlacement.currentLines(window, this.mc);
        resize.top = resize.startTop;
        resize.bottom = resize.startBottom;
        if (resize.edge.vertical) {
            // The screen bounds the height the way it bounds the width.
            double room = resize.edge.fromTop
                    ? resize.startBottom - margin
                    : this.height - margin - resize.startTop;
            double height = Math.min(room, resize.edge.fromTop
                    ? resize.startBottom - pointerY
                    : pointerY - resize.startTop);
            // The dragged edge follows the pointer continuously between
            // the smallest and largest window; only the far edge is laid
            // on the rounded room, so nothing under the pointer steps or
            // jitters while the drag runs.
            double stride = ChatWindowPlacement.lineStride(this.mc);
            double chrome = ChatWindowPlacement.rowHeight(this.mc)
                    + ChatWindowPlacement.HISTORY_TOP_MARGIN
                    + ChatWindowPlacement.barHeight(this.mc);
            height = Math.max(chrome
                            + ChatWindowLayout.MIN_WINDOW_LINES * stride,
                    Math.min(height, chrome
                            + ChatWindowLayout.MAX_WINDOW_LINES * stride));
            resize.lines = ChatWindowPlacement.linesForHeight(height,
                    this.mc);
            if (resize.edge.fromTop) {
                resize.top = resize.startBottom - height;
            } else {
                resize.bottom = resize.startTop + height;
            }
        }
        applyLiveResize(resize, window);
    }

    /**
     * Gives the window the dimensions under the pointer, unpersisted, so
     * the next frame draws and reflows the real window at them.
     */
    private void applyLiveResize(WindowResize resize, ChatWindow window) {
        if (resize.edge.vertical) {
            ChatWindowLayout.setWindowLines(resize.windowId, resize.lines,
                    false);
        }
        // The width goes first: a window's stored position is a percent
        // of the travel its own width leaves, so the percent has to be
        // worked out against the width the window is about to have.
        if (resize.edge.horizontal) {
            if (ChatWindowLines.isAvailable()) {
                ChatWindowLayout.setWindowWidth(resize.windowId,
                        ChatWindowPlacement.chatWidthForBox(
                                resize.right - resize.left, this.mc),
                        false);
            } else {
                ChatWindowLines.logUnavailableOnce();
            }
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
                        this.height), false);
        updateInputBounds();
    }

    /** Writes the dimensions the drag ends on down, once. */
    private void commitResize(WindowResize resize) {
        ChatWindow window = ChatWindowLayout.window(resize.windowId);
        if (window == null) {
            return;
        }
        applyLiveResize(resize, window);
        ChatWindowLayout.persist();
    }

    /** Puts the dimensions from before the resize back, cancelling it. */
    private void restoreResize(WindowResize resize) {
        ChatWindow window = ChatWindowLayout.window(resize.windowId);
        if (window == null) {
            return;
        }
        ChatWindowLayout.setWindowLines(resize.windowId, resize.storedLines,
                false);
        ChatWindowLayout.setWindowWidth(resize.windowId, resize.storedWidth,
                false);
        ChatWindowLayout.setPosition(resize.windowId, resize.storedOffsetX,
                resize.storedOffsetY, false);
        updateInputBounds();
    }

    private boolean isDragging() {
        return (this.tabDrag != null && this.tabDrag.active)
                || (this.windowDrag != null && this.windowDrag.active)
                || (this.windowResize != null && this.windowResize.active);
    }

    private void cancelDrags() {
        if (this.tabDrag != null && this.tabDrag.active) {
            // The tabs are already where the drag left them — in a row
            // they slid into, or in a window of their own — so ending
            // the carry writes that down rather than putting the row
            // back together. Leaving it unwritten was the one way the
            // layout on screen and the layout on disk could disagree.
            ChatWindowLayout.persist();
        }
        this.tabDrag = null;
        this.scrollbarDrag = null;
        if (this.windowResize != null) {
            WindowResize resize = this.windowResize;
            this.windowResize = null;
            if (resize.active) {
                // Escape means "as it was": the unpersisted live
                // dimensions give way to the stored ones, and nothing
                // is written.
                restoreResize(resize);
            }
        }
        if (this.windowDrag != null) {
            if (this.windowDrag.active) {
                ChatWindowLayout.persist();
            }
            this.windowDrag = null;
        }
    }

    /** Arms a window drag from the pointer's current position. */
    /**
     * A drag of the tabs a press took hold of, remembering where in the
     * pressed tab the hand closed on it so the tab goes on sitting
     * under that same point wherever it is carried.
     */
    private TabDrag tabDragFrom(ChatWindow window, ChatWindowFrame frame,
                                ChatChannelTabBar.Row row, ChatTab pressed,
                                List<ChatTab> group, int mouseX, int mouseY,
                                boolean collapsesOnRelease) {
        ChatWindowLayout.raise(window.getId());
        int grabX = 0;
        int pressedX = Integer.MIN_VALUE;
        int firstOfGroupX = Integer.MIN_VALUE;
        List<ChatChannelTabBar.Tab> tabs =
                frame.tabBar.layout(this.fontRendererObj, row);
        for (ChatChannelTabBar.Tab tab : tabs) {
            if (tab.tab.equals(pressed)) {
                grabX = mouseX - (row.offsetX + tab.x);
                pressedX = tab.x;
            }
            if (firstOfGroupX == Integer.MIN_VALUE
                    && group.contains(tab.tab)) {
                firstOfGroupX = tab.x;
            }
        }
        // Where the pressed tab will stand in a window of its own: the
        // window's first tab begins past the search control, and the
        // tabs carried with it keep their order in front of it.
        int withinRun = pressedX == Integer.MIN_VALUE
                || firstOfGroupX == Integer.MIN_VALUE
                ? 0 : Math.max(0, pressedX - firstOfGroupX);
        return new TabDrag(pressed, group, window.getId(), mouseX, mouseY,
                grabX,
                ChatChannelTabBar.tabRunLeftInset() + withinRun + grabX,
                mouseY - ChatChannelTabBar.rowTop(row.rowBottom),
                collapsesOnRelease);
    }

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
                    List<ChatTab> group = draggedGroup(window, hit.tab);
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
                    selectChannel(hit.tab);
                    if (!window.isLocked()) {
                        this.tabDrag = tabDragFrom(window, frame, row,
                                hit.tab, group, mouseX, mouseY, kept);
                        if (wholeWindow) {
                            this.tabDrag.detachedWindowId = window.getId();
                        }
                    } else if (kept) {
                        // A locked row starts no drag, so the press is
                        // only ever a pick.
                        ChatTabSelection.selectOnly(window.getId(), hit.tab);
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
                    // A switch like the search control beside it: a
                    // press with the list already out puts it away.
                    if (!POPUP_RESTORE.equals(closedPopupKind)) {
                        this.restoreWindowId = window.getId();
                        openRestorePopup(mouseX,
                                ChatChannelTabBar.rowTop(row.rowBottom) - 2);
                    }
                    return true;
                case SEARCH:
                    // The control is a switch: a press with its own
                    // panel already out has just closed it above, and
                    // only then does the press not open it again.
                    if (!POPUP_SEARCH.equals(closedPopupKind)) {
                        openSearchPanel(window, mouseX,
                                ChatChannelTabBar.rowTop(row.rowBottom) - 2);
                    }
                    return true;
                case WINDOW_SETTINGS:
                    openWindowPopup(window, mouseX,
                            ChatChannelTabBar.rowTop(row.rowBottom) - 2);
                    return true;
                case WINDOW_CLOSE:
                    closeWindow(window);
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
     * The tabs a press on {@code tab} carries: the window's marked group
     * when the pressed tab is one of them, and the pressed tab alone
     * otherwise — which is also what an unmarked press has just left
     * marked.
     */
    private static List<ChatTab> draggedGroup(ChatWindow window,
                                              ChatTab tab) {
        List<ChatTab> marked = ChatTabSelection.selectedIn(window);
        return marked.size() > 1 && marked.contains(tab) ? marked
                : Collections.singletonList(tab);
    }

    /**
     * Closes a whole window: its tabs leave it and the window goes. The
     * channels behind them keep receiving, so nothing is lost — they are
     * offered back by the {@code +} control and by the empty state.
     */
    private void closeWindow(ChatWindow window) {
        if (window == null
                || !ChatWindowLayout.closeWindow(window.getId())) {
            return;
        }
        ChatTabSelection.prune();
        syncSelection();
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
            // Closing one tab ends the group it was marked with: what
            // is left would be anchored on a tab that has gone.
            ChatTabSelection.clear();
            syncSelection();
        }
    }

    /**
     * What Ctrl+W closes: the marked tabs while more than one is marked,
     * and the tab being typed in otherwise. The marks always hold the
     * tab in front, so a group closes that one with the rest. A locked
     * window refuses its tabs either way, so a group holding nothing
     * closable closes nothing.
     */
    private void closeMarkedOrActiveTabs() {
        if (!ChatTabSelection.isGroup()) {
            closeChannel(ClientChatChannelState.getSelected());
            return;
        }
        List<ChatTab> marked = ChatTabSelection.selectedIn(
                ChatWindowLayout.window(ChatTabSelection.windowId()));
        boolean closed = false;
        for (int index = 0; index < marked.size(); index++) {
            closed |= ClientChatChannelState.close(marked.get(index));
        }
        if (closed) {
            ChatTabSelection.clear();
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
        ChatTabSelection.clear();
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
     */
    private void openWindowPopup(ChatWindow window, int anchorX,
                                 int anchorBottom) {
        if (window == null) {
            return;
        }
        this.settingsWindowId = window.getId();
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>(2);
        if (window.isLinked()) {
            entries.add(new ChatPopupMenu.Entry(ENTRY_WINDOW_UNSTICK,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.window.unstick")));
        }
        entries.add(new ChatPopupMenu.Entry(ENTRY_WINDOW_RESET,
                StatCollector.translateToLocal(
                        "gui.losttales.chat.window.reset_size")));
        this.popup.open(POPUP_WINDOW, null, entries, this.fontRendererObj,
                anchorX - 4, anchorBottom, this.width, this.height);
    }

    /**
     * The tab search panel: every tab that is open, then the channels
     * and conversations that are not, narrowed by what is typed into
     * the field above them. Anchored over the window's own search
     * control, or over the window being typed in when the keyboard
     * opened it.
     */
    private void openSearchPanel(ChatWindow window, int anchorX,
                                 int anchorBottom) {
        int x = anchorX;
        int bottom = anchorBottom;
        if (x < 0 || bottom < 0) {
            ChatWindowFrame frame = window == null ? null
                    : ChatWindowFrame.find(window.getId());
            if (frame != null && frame.drawn) {
                x = (int)Math.floor(frame.drawnLeft()) + 2;
                bottom = ChatChannelTabBar.rowTop(
                        (int)Math.floor(frame.tabRowBottom())) - 2;
            } else if (isEmptyState()) {
                x = this.emptyPlusLeft;
                bottom = this.emptyPlusTop - 2;
            } else {
                return;
            }
        }
        this.restoreWindowId = window == null ? null : window.getId();
        this.popup.open(POPUP_SEARCH, null, searchEntries(""),
                this.fontRendererObj, x - 4, bottom, this.width, this.height,
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
                this.fontRendererObj, this.width, this.height);
    }

    /**
     * The panel's rows: the tabs already open across every window, so a
     * search jumps to one, then the closed channels and the players a
     * conversation could be opened with, so it opens one. A filter keeps
     * the rows whose names hold it, and a section with nothing left in
     * it is dropped; a filter that matches nothing says so rather than
     * closing the panel under the hand that is typing.
     */
    private List<ChatPopupMenu.Entry> searchEntries(String filter) {
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
        for (String name : whisperCandidates()) {
            ChatTab conversation = ChatTab.whisper(name);
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
    private static boolean matchesFilter(String name, String filter) {
        return filter.length() == 0 || name.toLowerCase(Locale.ROOT)
                .contains(filter.toLowerCase(Locale.ROOT));
    }

    /**
     * The {@code +} menu opened from the keyboard: over the row of the
     * window being typed in, or over the empty state's own {@code +}
     * when nothing is open. Nothing happens when there is nothing left
     * to open.
     */
    private void openChannelMenu() {
        if (restoreEntries().isEmpty()) {
            return;
        }
        if (isEmptyState()) {
            this.restoreWindowId = null;
            openRestorePopup(this.emptyPlusLeft, this.emptyPlusTop - 2);
            return;
        }
        ChatWindow window = ChatWindowLayout.windowOf(
                ClientChatChannelState.getSelected());
        ChatWindowFrame frame = window == null ? null
                : ChatWindowFrame.find(window.getId());
        if (window == null || frame == null || !frame.drawn) {
            return;
        }
        this.restoreWindowId = window.getId();
        openRestorePopup((int)Math.floor(frame.drawnLeft()) + 2,
                ChatChannelTabBar.rowTop(
                        (int)Math.floor(frame.tabRowBottom())) - 2);
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

    /**
     * The menu over a message: reply to it, or copy it. What it acts on
     * is resolved now, while the pointer is still on the line, since by
     * the time an entry is chosen the pointer has moved to the menu.
     * Nothing opens over a line that carries no message.
     */
    private boolean openMessagePopup(int mouseX, int mouseY) {
        String text = LostTalesChatClipboard.messageTextAt(
                this.mc.ingameGUI.getChatGUI(), this.mc, mouseX, mouseY);
        if (text.length() == 0) {
            return false;
        }
        LostTalesChatOverlayRenderer.Band band =
                LostTalesChatOverlayRenderer.bandAt(this.mc,
                        mouseX + 0.5F, mouseY + 0.5F);
        int chatLineId = band == null || band.lines == null
                || band.viewIndex >= band.lines.size()
                || band.lines.get(band.viewIndex) == null
                ? 0 : band.lines.get(band.viewIndex).getChatLineID();
        this.menuMessageText = text;
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
                    LostTalesChatMessagePacket.DISCORD_SENDER_ID.equals(
                            remembered.packet.getSenderId());
        } else {
            this.menuMessageAccount = band == null ? ""
                    : messageAccount(band.lines, band.viewIndex, chatLineId);
            this.menuMessageIdentity = band == null ? ""
                    : messageIdentity(band.lines, band.viewIndex, chatLineId);
            this.menuMessageFromDiscord = band != null
                    && isFromDiscord(band.lines, band.viewIndex, chatLineId);
        }
        this.menuChatLineId = chatLineId;
        this.menuX = mouseX;
        this.menuY = mouseY;
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>();
        // Only a message the server named can be replied to: a console
        // notice, an adopted stray and this client's own NPC lines are
        // nobody's to answer.
        if (LostTalesChatPresentation.isRepliable(chatLineId)) {
            entries.add(new ChatPopupMenu.Entry(ENTRY_REPLY,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.message.reply")));
        }
        entries.add(new ChatPopupMenu.Entry(ENTRY_COPY,
                StatCollector.translateToLocal(
                        "gui.losttales.chat.message.copy")));
        // Your own words are yours to correct or take back. The server
        // decides that too — this only offers what it would allow.
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
        }
        // Opening a conversation with the sender lives here now: a name
        // in the chat is a person to read, not a link to press.
        // Not for yourself, and not for Discord: a name on the bridge
        // belongs to somebody this server cannot reach, so offering to
        // message them would offer something that cannot work.
        if (this.menuMessageAccount.length() > 0
                && !this.menuMessageFromDiscord
                && (this.mc.thePlayer == null
                        || !this.menuMessageAccount.equalsIgnoreCase(
                                this.mc.thePlayer.getCommandSenderName()))) {
            entries.add(new ChatPopupMenu.Entry(ENTRY_MESSAGE,
                    StatCollector.translateToLocalFormatted(
                            "gui.losttales.chat.message.whisper",
                            this.menuMessageIdentity.length() > 0
                                    ? this.menuMessageIdentity
                                    : this.menuMessageAccount)));
        }
        this.popup.open(POPUP_MESSAGE,
                ClientChatChannelViews.tabOf(chatLineId), entries,
                this.fontRendererObj, mouseX, mouseY, this.width,
                this.height);
        return true;
    }

    /**
     * The account behind the message the band names: the reply target
     * every part of a sender's name carries, looked for over the whole
     * message rather than the one wrapped line that was clicked.
     */
    private String messageAccount(List<ChatLine> lines, int index,
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

    /** Answers the message the menu was opened over, in its own tab. */
    private void startReply() {
        ChatTab tab = this.popup.channel();
        if (!ChatMessageIds.isServerId(this.menuMessageId) || tab == null) {
            return;
        }
        long id = this.menuMessageId;
        // The chip and the local quote name the identity the line was
        // signed with, exactly as the server's own quote will.
        String name = this.menuMessageIdentity.length() > 0
                ? this.menuMessageIdentity : this.menuMessageAccount;
        // The menu resolved the message when it opened, which is also
        // the quote an NPC's conversation has to build for itself.
        String excerpt = this.menuMessageText;
        // Composing happens where the message lives, and selecting a tab
        // clears any reply, so the target is set after the move.
        selectChannel(tab);
        this.replyToMessageId = id;
        this.replyToName = name;
        this.replyToExcerpt = excerpt;
        this.replyTab = tab;
    }

    /**
     * Whether the message the menu was opened over is this player's
     * own, and one the server can still be asked about. A line from the
     * Discord bridge never is, whatever name it carries.
     */
    private boolean isOwnMessage() {
        return ChatMessageIds.isServerId(this.menuMessageId)
                && !this.menuMessageFromDiscord
                && this.mc.thePlayer != null
                && this.menuMessageAccount.equalsIgnoreCase(
                        this.mc.thePlayer.getCommandSenderName());
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
        selectChannel(tab);
        cancelReply();
        this.editingMessageId = id;
        this.editingTab = tab;
        this.inputField.setText(remembered.packet.getMessage());
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
        if (!isOwnMessage()) {
            return false;
        }
        List<ChatPopupMenu.Entry> entries =
                new ArrayList<ChatPopupMenu.Entry>();
        entries.add(new ChatPopupMenu.Entry(ENTRY_DELETE_CONFIRM,
                StatCollector.translateToLocal(
                        "gui.losttales.chat.message.delete.confirm")));
        this.popup.replaceEntries(entries, this.fontRendererObj,
                this.width, this.height);
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
        if (this.editingMessageId == this.menuMessageId) {
            cancelEdit();
        }
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesChatDeletePacket(this.menuMessageId));
    }

    /** Whether a message is being rewritten in the tab now selected. */
    private boolean isEditing() {
        return ChatMessageIds.isServerId(this.editingMessageId)
                && this.editingTab != null
                && this.editingTab.equals(
                        ClientChatChannelState.getSelected());
    }

    /** Forgets the message being rewritten; the field keeps what is in it. */
    private void cancelEdit() {
        this.editingMessageId = ChatMessageIds.NONE;
        this.editingTab = null;
    }

    /** Puts down whichever of the two the strip is holding. */
    private void cancelComposing() {
        if (isEditing()) {
            cancelEdit();
            this.inputField.setText("");
            ClientChatChannelState.setDraft("");
            return;
        }
        cancelReply();
    }

    /** Forgets the message being replied to; the chip goes with it. */
    private void cancelReply() {
        this.replyToMessageId = ChatMessageIds.NONE;
        this.replyToName = "";
        this.replyToExcerpt = "";
        this.replyTab = null;
    }

    /** Whether a reply is being composed in the tab now selected. */
    private boolean isReplying() {
        return ChatMessageIds.isServerId(this.replyToMessageId)
                && this.replyTab != null
                && this.replyTab.equals(
                        ClientChatChannelState.getSelected());
    }

    /**
     * Acts on a chosen entry, answering whether the menu should stay
     * open: all but one entry are done with the menu once they have
     * been chosen, and the caller closes it. Deleting is the exception —
     * it asks first, and the question is put in the menu the asking
     * came from.
     */
    private boolean handlePopupEntry(ChatPopupMenu.Entry entry) {
        if (POPUP_MESSAGE.equals(this.popup.kind())) {
            if (ENTRY_REPLY.equals(entry.id)) {
                startReply();
            } else if (ENTRY_MESSAGE.equals(entry.id)) {
                openWhisperTab(this.menuMessageAccount,
                        this.menuMessageIdentity);
            } else if (ENTRY_EDIT.equals(entry.id)) {
                startEdit();
            } else if (ENTRY_DELETE.equals(entry.id)) {
                return askToDelete();
            } else if (ENTRY_DELETE_CONFIRM.equals(entry.id)) {
                confirmDelete();
            } else if (ENTRY_COPY.equals(entry.id)
                    && LostTalesChatClipboard.copy(this.menuMessageText)) {
                showNotice(StatCollector.translateToLocal(
                        "gui.losttales.chat.copied"));
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
                detachChannel(channel);
            } else if (ENTRY_CLOSE.equals(entry.id)) {
                closeChannel(channel);
            }
        } else if (POPUP_SEARCH.equals(this.popup.kind())) {
            if (entry.id.startsWith(ENTRY_OPEN_PREFIX)) {
                jumpToTab(ChatTab.fromId(entry.id.substring(
                        ENTRY_OPEN_PREFIX.length())));
            } else {
                openFromRestoreMenu(entry);
            }
        } else if (POPUP_WINDOW.equals(this.popup.kind())) {
            handleWindowEntry(entry);
        } else if (POPUP_RESTORE.equals(this.popup.kind())) {
            openFromRestoreMenu(entry);
        }
        return false;
    }

    /**
     * Brings a tab that is already open to the front of its own window
     * and moves the input there: what picking an open row in the search
     * panel does.
     */
    private void jumpToTab(ChatTab tab) {
        ChatWindow window = tab == null ? null
                : ChatWindowLayout.windowOf(tab);
        if (window == null) {
            return;
        }
        ChatWindowLayout.raise(window.getId());
        selectChannel(tab);
    }

    /** One row of the window's own menu. */
    private void handleWindowEntry(ChatPopupMenu.Entry entry) {
        ChatWindow window = ChatWindowLayout.window(this.settingsWindowId);
        if (window == null) {
            return;
        }
        if (ENTRY_WINDOW_UNSTICK.equals(entry.id)) {
            ChatWindowLayout.unlink(window.getId());
        } else if (ENTRY_WINDOW_RESET.equals(entry.id)) {
            // The chat's own default: a whole number of message lines,
            // so the topmost row is never a clipped one, and just wide
            // enough to show every one of the window's tabs whole. A
            // row that cannot be measured leaves the width alone, and
            // the window keeps following the game's chat-width setting.
            ChatWindowLayout.setWindowLines(window.getId(),
                    ChatWindowLayout.DEFAULT_WINDOW_LINES, false);
            ChatWindowLayout.setWindowWidth(window.getId(),
                    ChatChannelTabBar.chatWidthForWholeRow(this.mc, window),
                    true);
        }
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
            selectChannel(opened);
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
        if (this.scrollbarDrag != null) {
            dragScrollbar(mouseY);
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
                dragTabs(this.tabDrag, mouseX, mouseY);
            }
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton,
                timeSinceLastClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            this.scrollbarDrag = null;
            if (this.windowResize != null) {
                WindowResize resize = this.windowResize;
                this.windowResize = null;
                if (resize.active) {
                    commitResize(resize);
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
                TabDrag drag = this.tabDrag;
                this.tabDrag = null;
                if (drag.active) {
                    dropTab();
                } else if (drag.collapsesOnRelease) {
                    // Pressed and released without travelling: the press
                    // was a pick after all, so the group gives way to it.
                    ChatTabSelection.selectOnly(drag.sourceWindowId,
                            drag.tab);
                }
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
        ChatWindowPlacement.Anchor snapped = snapToNeighbour(window, anchor);
        ChatWindowLayout.setPosition(window.getId(),
                ChatWindowPlacement.windowPercentX(window, snapped.x, this.mc,
                        this.width),
                ChatWindowPlacement.windowPercentY(snapped.baseline, this.mc,
                        this.height), false);
    }

    /**
     * Snaps the dragged window to another window's top or bottom edge
     * when it comes within a few pixels of it, a margin apart, and
     * remembers that edge so the release links the two; the snapped
     * baseline is returned.
     */
    private ChatWindowPlacement.Anchor snapToNeighbour(ChatWindow window,
                                   ChatWindowPlacement.Anchor anchor) {
        int margin = HudPlacementLayout.SCREEN_MARGIN;
        int width = ChatWindowPlacement.windowWidth(window, this.mc);
        double height = ChatWindowPlacement.currentHeight(window, this.mc);
        int barHeight = ChatWindowPlacement.barHeight(this.mc);
        double top = anchor.baseline - (height - barHeight);
        double bottom = anchor.baseline + barHeight;
        this.windowDrag.snapTargetId = null;
        double best = Double.MAX_VALUE;
        double baseline = anchor.baseline;
        double x = anchor.x;
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
                    x = anchor.x;
                    this.windowDrag.snapTargetId = other.getId();
                    this.windowDrag.snapSide = ChatWindow.LinkSide.ABOVE;
                }
                double belowGap = Math.abs(top - (frame.boxBottom + margin));
                if (belowGap <= LINK_SNAP && belowGap < best) {
                    best = belowGap;
                    baseline = frame.boxBottom + margin + (height - barHeight);
                    x = anchor.x;
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
                // A side snap moves the window onto the edge it is
                // catching, exactly as a top or bottom snap does; the
                // baseline it already has is the level it keeps.
                x = frame.boxLeft - margin - width;
                baseline = anchor.baseline;
                this.windowDrag.snapTargetId = other.getId();
                this.windowDrag.snapSide = ChatWindow.LinkSide.LEFT;
            }
            double rightGap = Math.abs(
                    anchor.x - (frame.boxRight + margin));
            if (rightGap <= LINK_SNAP && rightGap < best) {
                best = rightGap;
                x = frame.boxRight + margin;
                baseline = anchor.baseline;
                this.windowDrag.snapTargetId = other.getId();
                this.windowDrag.snapSide = ChatWindow.LinkSide.RIGHT;
            }
        }
        return ChatWindowPlacement.constrainWindow(window, this.mc, x,
                baseline, this.width, this.height);
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
    private void updateDropTarget(TabDrag drag, int mouseX, int mouseY) {
        drag.targetWindowId = null;
        drag.targetIndex = -1;
        LostTalesGuiAnimationSample opening =
                ClientChatChannelViews.openSample();
        List<ChatWindow> windows = ChatWindowLayout.stacked();
        for (int index = windows.size() - 1; index >= 0; index--) {
            ChatWindow window = windows.get(index);
            // A torn-off window rides under the pointer, so its own row
            // is always there: docking into it would mean nothing.
            if (window.isLocked()
                    || window.getId().equals(drag.detachedWindowId)) {
                continue;
            }
            ChatWindowFrame frame = frameFor(window);
            ChatChannelTabBar.Row row = rowFor(window, frame, opening);
            if (row == null || mouseY < ChatChannelTabBar.rowTop(
                    row.rowBottom) - RETURN_DISTANCE
                    || mouseY >= row.rowBottom + RETURN_DISTANCE) {
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
            // The row a tab was last thrown out of — whichever row that
            // is — asks one thing more of it before taking it back: that
            // it has really come back inside the room. The reach back in is shorter than the pull that
            // tore it out, so the two cannot argue about one pointer and
            // a tab torn off at that row's edge is not handed straight
            // back on the next frame, flashing a window up and taking it
            // away again.
            //
            // Only that row asks. A row a tab has never left has
            // nothing to guard against — and asking anyway made a row
            // hard to drop a tab into from the left, since a tab held by
            // its right-hand end had to be carried most of its own width
            // into the row before the row would admit it was being
            // offered one.
            if (window.getId().equals(drag.leftRowId)
                    && frame.tabBar.overrunAt(this.fontRendererObj, row,
                            mouseX - drag.grabOffsetX - row.offsetX,
                            carriedTabWidth(drag, opening))
                                    >= SIDE_RETURN_DISTANCE) {
                continue;
            }
            drag.targetWindowId = window.getId();
            drag.targetIndex = frame.tabBar.dropIndexAt(
                    this.fontRendererObj, row, mouseX);
            return;
        }
    }

    /**
     * How wide the carried run is drawn: measured in the row that holds
     * it right now — its own torn-off window while it is being carried
     * — so every row it is offered to is asked about a run of the size
     * it would actually take.
     */
    private int carriedTabWidth(TabDrag drag,
                                LostTalesGuiAnimationSample opening) {
        ChatWindow holder = ChatWindowLayout.windowOf(drag.tab);
        if (holder == null) {
            return 0;
        }
        ChatWindowFrame frame = frameFor(holder);
        ChatChannelTabBar.Row row = rowFor(holder, frame, opening);
        return row == null ? 0
                : frame.tabBar.carriedRunWidth(this.fontRendererObj, row);
    }

    /**
     * One frame of a tab drag. The tabs never leave the strip until they
     * leave it for good: along their own row they slide between their
     * neighbours as the pointer passes them, and only once the pointer
     * is carried clear of the row altogether do they become a window of
     * their own, which then follows the pointer.
     */
    private void dragTabs(TabDrag drag, int mouseX, int mouseY) {
        drag.pointerX = mouseX;
        updateDropTarget(drag, mouseX, mouseY);
        ChatWindow window = ChatWindowLayout.windowOf(drag.tab);
        if (window == null) {
            return;
        }
        if (drag.targetWindowId != null
                && !window.getId().equals(drag.targetWindowId)
                && dockInto(drag, drag.targetWindowId)) {
            return;
        }
        // A dock that could not be made changes nothing, and the drag
        // goes on as it was: a window carried over a row that will not
        // take the tabs still follows the pointer rather than sticking
        // there with nothing happening.
        ChatWindow detached = ChatWindowLayout.window(drag.detachedWindowId);
        if (detached != null) {
            carryWindow(drag, detached, mouseX, mouseY);
            return;
        }
        if (hasLeftItsRow(drag, mouseY)) {
            tearOff(drag, mouseX, mouseY);
            return;
        }
        // Still in its row, wherever the pointer has wandered: a tab on
        // its way out of the strip goes on changing places until the
        // moment it leaves, rather than freezing the instant the pointer
        // steps off the row.
        slideAlongRow(drag, window);
    }

    /**
     * Joins the dragged tabs to another window's row where the pointer
     * has reached it: the reverse of tearing them off, and just as
     * immediate. A window they had been torn off into empties and goes
     * with them, so the strip they join is the only place they are.
     * Answers whether the row took them; a row that would not is left
     * alone and the drag carries on unchanged.
     */
    private boolean dockInto(TabDrag drag, String targetWindowId) {
        ChatWindow target = ChatWindowLayout.window(targetWindowId);
        if (target == null || !ChatWindowLayout.moveTabs(drag.group,
                targetWindowId,
                listPosition(target, Collections.<ChatTab>emptyList(),
                        drag.targetIndex), false)) {
            return false;
        }
        drag.detachedWindowId = null;
        ChatWindowLayout.raise(targetWindowId);
        ChatTabSelection.selectAll(targetWindowId, drag.group);
        selectChannel(drag.tab);
        return true;
    }

    /**
     * Puts the dragged tabs where the pointer has carried them in their
     * own row, so the row reads as it will once the button comes up.
     * Nothing is written while the drag runs; the file is saved once, on
     * release.
     */
    private void slideAlongRow(TabDrag drag, ChatWindow window) {
        ChatWindowFrame frame = frameFor(window);
        ChatChannelTabBar.Row row = rowFor(window, frame,
                ClientChatChannelViews.openSample());
        if (row == null) {
            return;
        }
        int slot = frame.tabBar.slideIndexAt(this.fontRendererObj, row,
                drag.group);
        if (slot >= 0) {
            ChatWindowLayout.moveTabs(drag.group, window.getId(),
                    listPosition(window, drag.group, slot), false);
        }
    }

    /**
     * Where in a window's own tab list a place counted among its visible
     * tabs falls. The list also holds open tabs the player cannot
     * currently see (Party outside a party, Faction without one),
     * sitting between them, so a place counted in visible tabs has to be
     * translated or the move lands beside the wrong neighbour. Tabs in
     * {@code moving} are left out of both counts: they are on their way
     * and are not what the place is measured against, which is also what
     * makes the answer the position the run ends up at once they have
     * been lifted out.
     */
    private static int listPosition(ChatWindow window,
                                    List<ChatTab> moving,
                                    int visibleSlot) {
        List<ChatTab> all = window.getTabs();
        int position = 0;
        int seen = 0;
        for (int index = 0; index < all.size(); index++) {
            ChatTab tab = all.get(index);
            if (moving.contains(tab)) {
                continue;
            }
            if (ClientChatChannelState.isAvailable(tab)) {
                if (seen == visibleSlot) {
                    return position;
                }
                seen++;
            }
            position++;
        }
        return position;
    }

    /**
     * Whether the pointer has carried the tabs clear of the row they are
     * still in. A row that is not on screen counts as left behind.
     */
    private boolean hasLeftItsRow(TabDrag drag, int mouseY) {
        ChatWindow window = ChatWindowLayout.windowOf(drag.tab);
        ChatWindowFrame frame = window == null ? null : frameFor(window);
        ChatChannelTabBar.Row row = window == null ? null
                : rowFor(window, frame, ClientChatChannelViews.openSample());
        if (row == null) {
            return true;
        }
        int rowTop = ChatChannelTabBar.rowTop(row.rowBottom);
        int distance = mouseY < rowTop ? rowTop - mouseY
                : mouseY >= row.rowBottom ? mouseY - row.rowBottom : 0;
        if (distance >= DETACH_DISTANCE) {
            return true;
        }
        // Carried past either end of the row a tab is just as plainly on
        // its way out as carried above or below it, and as often as the
        // hand asks: leaving costs the full pull, coming back a much
        // shorter reach, and between the two lies a band where nothing
        // happens at all. That band is what keeps the two answers from
        // arguing about one pointer — not a rule that a tab may only
        // leave once, which merely stopped it leaving again.
        return frame.tabBar.draggedOverrun(this.fontRendererObj, row)
                >= SIDE_DETACH_DISTANCE;
    }

    /**
     * Tears the dragged tabs off into a window of their own, placed so
     * its row lands under the pointer. Refused at the window cap, where
     * the tabs stay in their row and the drag goes on as a ghost.
     */
    private void tearOff(TabDrag drag, int mouseX, int mouseY) {
        // Placed for the window it is about to become, which is as tall
        // and as wide as the one it is leaving. Measuring it as a window
        // of the smallest possible size — which is what asking for no
        // window at all answers — put it a whole window's height out for
        // the one frame before the carry corrected it.
        ChatWindow source = ChatWindowLayout.windowOf(drag.tab);
        ChatWindowPlacement.Anchor anchor = carriedAnchor(source, drag,
                mouseX, mouseY);
        ChatWindow window = ChatWindowLayout.detach(drag.group,
                ChatWindowPlacement.windowPercentX(source, anchor.x, this.mc,
                        this.width),
                ChatWindowPlacement.windowPercentY(anchor.baseline, this.mc,
                        this.height));
        if (window == null) {
            return;
        }
        drag.leftRowId = source == null ? drag.sourceWindowId
                : source.getId();
        drag.detachedWindowId = window.getId();
        ChatWindowLayout.raise(window.getId());
        ChatTabSelection.selectAll(window.getId(), drag.group);
        selectChannel(drag.tab);
    }

    /** Keeps a torn-off window's row under the pointer as it moves. */
    private void carryWindow(TabDrag drag, ChatWindow window, int mouseX,
                             int mouseY) {
        ChatWindowPlacement.Anchor anchor = carriedAnchor(window, drag,
                mouseX, mouseY);
        ChatWindowLayout.setPosition(window.getId(),
                ChatWindowPlacement.windowPercentX(window, anchor.x, this.mc,
                        this.width),
                ChatWindowPlacement.windowPercentY(anchor.baseline, this.mc,
                        this.height), false);
    }

    /**
     * Where a window carrying the dragged tabs sits: its row under the
     * pointer, held where the tab was taken hold of, and kept on screen.
     * An empty window's row stands one line above its baseline.
     */
    private ChatWindowPlacement.Anchor carriedAnchor(ChatWindow window,
                                                     TabDrag drag,
                                                     int mouseX, int mouseY) {
        int rowTop = mouseY - drag.grabOffsetY;
        return ChatWindowPlacement.constrainWindow(window, this.mc,
                mouseX - drag.grabOffsetInWindowX,
                ChatWindowPlacement.baselineForRowTop(window, this.mc,
                        rowTop),
                this.width, this.height);
    }

    /**
     * Releases a dragged tab. The tabs have been where they are all
     * along — in a row they slid into, or in a window of their own —
     * so letting go decides nothing and moves nothing: only the resting
     * layout is written.
     *
     * <p>The drag is deliberately <em>not</em> asked once more here. It
     * has already run for every frame and every pointer event of the
     * carry, so it can have nothing left to say; asking again only gave
     * it one last chance to hand the tabs to whichever window the
     * pointer happened to be near, taking back a window the player could
     * plainly see they had just pulled out.</p>
     */
    private void dropTab() {
        ChatWindowLayout.persist();
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
                || ChatBodyMarker.isMarker(hit.component)) {
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
            // The sender's own name and brackets: a person, not a link.
            return true;
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

    /**
     * The bar's own left frame edge: drawn on the border, one pixel
     * wide, so the gap after it starts a pixel in.
     */
    private static final int BAR_BORDER_WIDTH = 1;
    /**
     * Clear space between the things standing on the bar, and before
     * the first of them. Measured in ink, like every other gap the chat
     * keeps: what the eye reads as the gap is the space between the
     * pixels that were actually drawn, not between the boxes they were
     * drawn in. The character button's square is wider than the head
     * inside it and the indicator's is wider than its label, so both
     * are asked where their ink is rather than where their box is.
     */
    private static final int BAR_GAP = 3;
    /** The head's inset inside the character button's square, and its size. */
    private static final int CHARACTER_HEAD_INSET = 2;
    private static final int CHARACTER_HEAD_SIZE = 8;
    /** The indicator's label is drawn a pixel inside its box. */
    private static final int INDICATOR_TEXT_INSET = 1;

    /** Left edge of the character-selection button, the bar's first
     *  control. */
    private int characterButtonLeft() {
        return this.barLeft + BAR_BORDER_WIDTH + BAR_GAP
                - CHARACTER_HEAD_INSET;
    }

    /** Past the last pixel of the character button's head. */
    private int characterButtonInkRight() {
        return characterButtonLeft() + CHARACTER_HEAD_INSET
                + CHARACTER_HEAD_SIZE;
    }

    /** Left edge of the channel indicator, past the character button. */
    private int indicatorLeft() {
        return characterButtonInkRight() + BAR_GAP - INDICATOR_TEXT_INSET;
    }

    /**
     * Past the last pixel of the indicator's label. Its own width is a
     * click target and carries padding on both sides; this is where the
     * writing stops. The last glyph's width includes a column of
     * spacing after it, which is not ink.
     */
    private int indicatorInkRight() {
        return indicatorLeft() + INDICATOR_TEXT_INSET
                + this.fontRendererObj.getStringWidth(indicatorLabel(
                        ClientChatChannelState.getSelected())) - 1;
    }

    private boolean isInsideCharacterButton(int mouseX, int mouseY) {
        int left = characterButtonLeft();
        int top = barControlTop();
        return mouseX >= left && mouseX < left + ChatPickerPanel.BUTTON_SIZE
                && mouseY >= top && mouseY < top + ChatPickerPanel.BUTTON_SIZE;
    }

    /**
     * The character-selection button: the head of whoever the selected
     * tab would currently speak as, over the project's flat shadow like
     * every other head in the chat, lifted a pixel under the pointer
     * like the picker buttons, with the padlock in its corner while the
     * choice is locked. Clicking opens the character selection menu.
     */
    private void drawCharacterSelectionButton(int mouseX, int mouseY) {
        int left = characterButtonLeft();
        int top = barControlTop();
        boolean open = this.popup.isOpen()
                && POPUP_CHARACTERS.equals(this.popup.kind());
        boolean hovered = isInsideCharacterButton(mouseX, mouseY);
        int lift = open || hovered ? 1 : 0;
        ClientChatAppearances.Appearance shown =
                ClientChatAppearances.effectiveFor(
                        ClientChatChannelState.getSelected());
        UUID self = this.mc.thePlayer == null ? null
                : this.mc.thePlayer.getUniqueID();
        if (self != null) {
            float headX = left + 2;
            // A pixel above the button's arithmetic centre: the head is
            // one row taller than the text caps beside it, so this is
            // what reads as level with them.
            float headY = top + 1 - lift;
            boolean account = shown.account || shown.skinId.length() == 0;
            drawButtonHeadShadow(self, account, shown.skinId, headX, headY);
            if (account) {
                LostTalesCharacterHeadIconRenderer.drawAccountHead(
                        this.mc, self, headX, headY, 8.0F, 1.0F, 1.0F);
            } else {
                LostTalesCharacterHeadIconRenderer.drawSnapshotHead(
                        this.mc, self, shown.skinId, headX, headY, 8.0F,
                        1.0F, 1.0F);
            }
        }
        if (ClientChatAppearances.isLocked()) {
            ChatLockAnimation.drawShut(
                    left + ChatPickerPanel.BUTTON_SIZE
                            - ChatLockAnimation.SHUT_WIDTH,
                    top + ChatPickerPanel.BUTTON_SIZE
                            - ChatLockAnimation.HEIGHT, 255);
        }
        if (hovered && !isDragging()) {
            this.hoverTip = StatCollector.translateToLocal(
                    "gui.losttales.chat.character_selection.tip");
            this.hoverTipX = mouseX;
            this.hoverTipY = mouseY;
        }
        this.regions.add(left, top, left + ChatPickerPanel.BUTTON_SIZE,
                top + ChatPickerPanel.BUTTON_SIZE);
    }

    /**
     * The head's flat silhouette shadow, one pixel down-right at half
     * opacity: the treatment every comparable head in the chat carries.
     */
    private void drawButtonHeadShadow(UUID self, boolean account,
                                      String skinId, float x, float y) {
        LostTalesSilhouetteRenderState.begin(LostTalesChatVisualStyle.SHADOW);
        try {
            if (account) {
                LostTalesCharacterHeadIconRenderer.drawTintedAccountHeadBase(
                        this.mc, self,
                        x + LostTalesChatVisualStyle.SHADOW_OFFSET,
                        y + LostTalesChatVisualStyle.SHADOW_OFFSET, 8.0F,
                        1.0F, 1.0F, 1.0F,
                        LostTalesChatVisualStyle.SHADOW_OPACITY);
            } else {
                LostTalesCharacterHeadIconRenderer.drawTintedSnapshotHeadBase(
                        this.mc, self, skinId,
                        x + LostTalesChatVisualStyle.SHADOW_OFFSET,
                        y + LostTalesChatVisualStyle.SHADOW_OFFSET, 8.0F,
                        1.0F, 1.0F, 1.0F,
                        LostTalesChatVisualStyle.SHADOW_OPACITY);
            }
        } finally {
            LostTalesSilhouetteRenderState.end();
        }
    }

    /** The character selection menu, anchored above its button. */
    private void openCharacterSelectionMenu() {
        this.popup.open(POPUP_CHARACTERS, null, characterSelectionEntries(),
                this.fontRendererObj, characterButtonLeft(),
                barControlTop() - 2, this.width, this.height);
    }

    /**
     * The menu's rows: the selected character on top — the identity the
     * tab currently speaks as, with the lock control beside it — then
     * the account and every roster character to choose from.
     */
    private List<ChatPopupMenu.Entry> characterSelectionEntries() {
        ChatTab selected = ClientChatChannelState.getSelected();
        UUID self = this.mc.thePlayer == null ? null
                : this.mc.thePlayer.getUniqueID();
        boolean locked = ClientChatAppearances.isLocked();
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
    private ChatPopupMenu.Entry characterEntry(
            String id, ClientChatAppearances.Appearance appearance,
            ChatTab selected, UUID self) {
        boolean effective = ClientChatAppearances.isEffective(
                appearance, selected);
        return new ChatPopupMenu.Entry(id, appearance.name, false,
                effective ? LostTalesColors.rgb(LostTalesColors.HONEY) : -1,
                null).withHead(self, appearance.skinId);
    }

    private void handleCharacterSelectionEntry(ChatPopupMenu.Entry entry) {
        if ("characters:account".equals(entry.id)) {
            ClientChatAppearances.select(
                    ClientChatAppearances.accountAppearance());
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
                (System.nanoTime() - this.barEntranceNanos)
                        / (float)duration));
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
     * The sheet's send arrow, centred in the button square at the bar's
     * right end and lifted a pixel while hovered; a flat mauve while
     * there is nothing to send. The sheet holds one arrow, so hovering
     * is told by the lift alone.
     */
    /**
     * The hairline between the send arrow and the buttons that put
     * something into the message: the same divider the tab row uses
     * between a window's controls, in the gap the bar's slots already
     * leave, so sending reads as its own act rather than as one more
     * insert.
     */
    private void drawSendDivider(int barRight) {
        int insertRight = barSlotLeft(barRight, SEND_BUTTON_INDEX + 1)
                + ChatPickerPanel.BUTTON_SIZE;
        int sendLeft = barSlotLeft(barRight, SEND_BUTTON_INDEX);
        LostTalesChatVisualStyle.drawDivider(
                (insertRight + sendLeft) / 2,
                barControlTop() + (ChatPickerPanel.BUTTON_SIZE
                        - BAR_DIVIDER_HEIGHT) / 2,
                BAR_DIVIDER_HEIGHT, LostTalesChatVisualStyle.DIVIDER_ALPHA);
    }

    private void drawSendButton(int barRight, int mouseX, int mouseY) {
        int left = sendButtonLeft(barRight);
        int top = barControlTop();
        boolean hovered = isInsideSendButton(mouseX, mouseY, barRight);
        boolean ready = this.inputField.getText().trim().length() > 0;
        int x = left + (ChatPickerPanel.BUTTON_SIZE
                - ChatIconSheet.SEND.getWidth()) / 2;
        int y = top + (ChatPickerPanel.BUTTON_SIZE
                - ChatIconSheet.SEND.getHeight()) / 2 - (hovered ? 1 : 0);
        // Text and glyphs are always fully opaque; the arrow says
        // what it can do with its colour, not by fading out.
        if (hovered || ready) {
            ChatIconSheet.SEND.drawWithShadow(x, y, 255);
        } else {
            ChatIconSheet.SEND.drawSilhouetteWithShadow(
                    LostTalesColors.rgb(LostTalesColors.MAUVE), x, y, 255);
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

    /**
     * The counter's scale, snapped so every font pixel is a whole number
     * of display pixels: as close to the wanted three quarters as the
     * display can draw without mixed-size pixels — half at GUI scale 2,
     * two thirds at 3, three quarters at 4, and full size at 1, which
     * has no smaller whole step at all.
     */
    private float counterScale() {
        int factor = ChatWindowFrame.displayScaleFactor();
        return Math.max(1, (int)Math.floor(COUNTER_SCALE * factor))
                / (float)factor;
    }

    private int counterLeft(int barRight) {
        String text = counterText();
        int width = text.length() == 0 ? 0
                : (int)Math.ceil(this.fontRendererObj.getStringWidth(text)
                        * counterScale()) + 3;
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
        // A footnote beside the arrow, centred on the bar like the
        // full-size text was.
        float scale = counterScale();
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(counterLeft(barRight), barTextTop() + 1,
                    0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            LostTalesChatVisualStyle.drawColored(this.fontRendererObj, text,
                    0, 0, full ? COUNTER_FULL_RGB : COUNTER_RGB, 255,
                    scale);
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
        int left = indicatorInkRight() + BAR_GAP;
        // The counter sits left of the send arrow; the field ends before it.
        int right = counterLeft(inputBarRight()) - 3;
        this.inputField.xPosition = left;
        this.inputField.yPosition = barTextTop();
        this.inputField.width = Math.max(20, right - left);
    }
}
