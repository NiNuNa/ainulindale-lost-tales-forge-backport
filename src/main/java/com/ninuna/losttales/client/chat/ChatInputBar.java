package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.IconFlipbook;
import com.ninuna.losttales.client.window.PointerRegions;
import com.ninuna.losttales.client.window.SubWindowKind;
import com.ninuna.losttales.client.window.TabIcons;
import com.ninuna.losttales.client.window.TabRow;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowBar;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowOpening;
import com.ninuna.losttales.client.window.WindowPlacement;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesDisplayPixels;
import com.ninuna.losttales.gui.style.LostTalesUiCornerMark;
import com.ninuna.losttales.gui.style.LostTalesUiFading;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.client.motion.Motions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * The one input section of the chat screen: where it stands, what
 * stands on it, and the notice above it. Its home is the active
 * window's bar — the strip below that window's newest line, as wide as
 * the window — read from the frame the window pass drew, motion
 * included, so everything that belongs to the bar (the channel
 * indicator, the character button, the field and the well it lies in, the
 * dividers, the toolbar and its pickers, the send button, the counter,
 * the completion lists hung from it) follows its window exactly however
 * that window is dragged or resized. The screen draws it inside one
 * transform shifted by the bar's fractional remainder and its entrance
 * from below. The typing line's offsets come from {@link ChatInputLine}.
 */
public final class ChatInputBar {
    /** Height vanilla gives the chat's field, kept for the one we draw. */
    static final int FIELD_HEIGHT = 12;
    /** Clear rows between the bar's edges and what stands on it. */
    static final int CLEARANCE = 2;
    /**
     * Height of what stands on the bar: the framed buttons' one height,
     * the channel indicator and the character button. The typing well
     * stands in its middle, and the bar's other buttons are shorter and
     * centred on the same middle.
     */
    static final int CONTENT_HEIGHT = LostTalesUiFramedButton.HEIGHT;
    /**
     * Height of the typing well: one message row, as every chat input box
     * is (Nils), so what is typed stands exactly as it will once it is
     * sent.
     */
    static final int WELL_HEIGHT = LostTalesChatOverlayRenderer.LINE_HEIGHT;
    /** The pickers and lists take the bar's top plus this as their floor. */
    private static final int INPUT_ANCHOR_BELOW_BAR = 14;
    /** The send button is the rightmost bar control. */
    private static final int SEND_BUTTON_INDEX = 0;
    /** The divider before the send button, as tall as the tab row's between window controls. */
    private static final int BAR_DIVIDER_HEIGHT =
            TabRow.END_CONTROL_SIZE;
    private static final int COUNTER_RGB =
            LostTalesColors.rgb(LostTalesColors.SAND);
    private static final int COUNTER_FULL_RGB =
            LostTalesColors.rgb(LostTalesColors.SALMON);
    static final float NOTICE_LIFETIME_MILLIS = 1400.0F;
    /**
     * Clear space between the things standing on the bar, and before
     * the first of them, save between its two framed buttons
     * ({@link #BUTTON_GAP}). Measured in ink, like every other gap the
     * chat keeps: what the eye reads as the gap is the space between the
     * pixels that were actually drawn, not between the boxes they were
     * drawn in. The character button's square is wider than the head
     * inside it and the indicator's is wider than its label, so both
     * are asked where their ink is rather than where their box is.
     */
    static final int BAR_GAP = 3;
    /**
     * Clear space between the channel indicator and the character button
     * beside it, from frame to frame: the two read as one pair.
     */
    static final int BUTTON_GAP = 2;
    /**
     * The field is never narrower than this, however little room a
     * narrow window leaves between the indicator and the counter.
     */
    private static final int MIN_FIELD_WIDTH = 20;
    /**
     * Room the field keeps before the channel indicator gives up any of
     * its name for it: about a dozen letters.
     */
    static final int COMFORTABLE_FIELD_WIDTH = 64;
    /**
     * The character button: a framed button at the framed buttons' one
     * height and as wide, square, the head in its middle exactly with
     * the strips' wide inset round it on every side.
     */
    private static final int CHARACTER_HEAD_SIZE =
            LostTalesChatOverlayRenderer.HEAD_SIZE;
    static final int CHARACTER_BUTTON_SIZE = LostTalesUiFramedButton.HEIGHT;
    /** The chevron's frames, pointing right through upright to left. */
    private static final LostTalesUiSheet[] TOGGLE_FRAMES = {
            LostTalesUiSheet.TOGGLE_1, LostTalesUiSheet.TOGGLE_2,
            LostTalesUiSheet.TOGGLE_3, LostTalesUiSheet.TOGGLE_4,
            LostTalesUiSheet.TOGGLE_5};
    private static final LostTalesUiSheet[] TOGGLE_FRAMES_HOVER = {
            LostTalesUiSheet.TOGGLE_1_HOVER, LostTalesUiSheet.TOGGLE_2_HOVER,
            LostTalesUiSheet.TOGGLE_3_HOVER, LostTalesUiSheet.TOGGLE_4_HOVER,
            LostTalesUiSheet.TOGGLE_5_HOVER};

    private Minecraft mc;
    private FontRenderer font;
    private PointerRegions regions;
    private ChatInputField field;
    /** Draws a resting bar's draft as the live field would show it. */
    private ChatInputField restingField;
    private int screenHeight;

    /**
     * The indicator's marquee, as a tab's: how long the pointer has
     * rested on it, how far its cut name is slid left, and when the
     * slide last moved.
     */
    private double indicatorHoverSeconds;
    private float indicatorMarquee;
    private long indicatorNanos;
    /** How far the indicator's frame has lit under the pointer. */
    private float indicatorFade;
    /**
     * The bar's own icon buttons, each keeping its own beat. The head
     * rises inside its frame and never tilts, since a tilted face reads
     * as a mistake; the send glyph tips as it throws; the toolbar
     * chevron rises with its fold.
     */
    private final LostTalesUiButtonMotion characterMotion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
    private final LostTalesUiButtonMotion sendMotion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.TURN);
    private final LostTalesUiButtonMotion toolbarToggleMotion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
    /**
     * The pose a resting bar's buttons are drawn in: never stepped, so
     * it stays unlit and exactly where it was laid out. A bar the player
     * is not typing in shows its controls as they sit and answers
     * nothing, so it has no beat of its own to keep.
     */
    private final LostTalesUiButtonMotion restingMotion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
    /**
     * The bar being drawn this frame — the active window's, the window
     * holding the selected channel, except while another window's
     * resting bar borrows the geometry — in whole pixels plus the
     * fractional remainder the bar group is drawn with, so it lands
     * exactly on the window.
     */
    private int left = 2;
    private int top;
    private int right;
    private float fractionX;
    private float fractionY;
    private long noticeNanos;
    private String noticeText = "";

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
    /**
     * The Reactions window's emoji picker, aimed at a message: it has no
     * button of its own, a message's React opens it.
     */
    private final ChatEmojiPicker reactionPicker = new ChatEmojiPicker();
    /** Bar slot of the fold chevron, between the emoji and the inserts. */
    private int toolbarToggleIndex;
    private final IconFlipbook toolbarToggle =
            new IconFlipbook(TOGGLE_FRAMES, TOGGLE_FRAMES_HOVER);

    /**
     * Takes the field the screen just built and the screen's height;
     * called from {@code initGui}, which also runs on every resize.
     * The pickers, the notice and the entrance keep their state.
     */
    void bind(Minecraft mc, FontRenderer font, PointerRegions regions,
              ChatInputField field, int screenHeight) {
        this.mc = mc;
        this.font = font;
        this.regions = regions;
        this.field = field;
        this.screenHeight = screenHeight;
        this.restingField = new ChatInputField(font, 0, 0, MIN_FIELD_WIDTH,
                FIELD_HEIGHT);
        this.restingField.setEnableBackgroundDrawing(false);
        this.restingField.setMaxStringLength(
                ChatMessageValidator.MAX_RAW_CHARACTERS);
        this.restingField.mentionsFrom(field.mentionSource());
    }

    /**
     * Gives the bar's controls their slots, right to left: the send
     * arrow, the emoji picker, the fold chevron, then the insert pickers
     * — items, markers, quests — which the chevron folds away to the
     * left. The emoji picker stands outside the fold.
     */
    void assignButtons() {
        int index = SEND_BUTTON_INDEX + 1;
        if (LostTalesConfig.enableChatEmojis) {
            this.emojiPicker.setButtonIndex(index++);
        }
        this.toolbarToggleIndex = index++;
        this.itemPicker.setButtonIndex(index++);
        this.markerPicker.setButtonIndex(index++);
        this.questPicker.setButtonIndex(index);
    }

    /* ---- Where the bar is ---- */

    /** The frame of the window being typed into, else the first drawn. */
    ChatFrame activeFrame() {
        Window window = WindowLayout.windowOf(
                ClientChatChannelState.getSelected());
        if (WindowLayout.showsPage(window)) {
            // The input waits behind a page: there is no live bar.
            return null;
        }
        ChatFrame frame = window == null ? null
                : ChatFrame.find(window.getId());
        if (frame != null && frame.drawn) {
            return frame;
        }
        for (ChatFrame drawn : ChatFrame.drawn()) {
            if (drawn.page == null) {
                return drawn;
            }
        }
        return null;
    }

    /** Places the bar on the active window's bar strip, as just drawn. */
    void updateInputBox() {
        ChatFrame frame = activeFrame();
        if (frame == null) {
            this.left = WindowPlacement.EDGE_MARGIN;
            this.top = this.screenHeight - WindowPlacement.EDGE_MARGIN
                    - WindowPlacement.BAR_STRIP_HEIGHT;
            this.right = this.left + WindowPlacement.windowWidth(this.mc);
            this.fractionX = 0.0F;
            this.fractionY = 0.0F;
            return;
        }
        placeOn(frame);
    }

    /** Places the bar on the window's bar strip, as just drawn. */
    private void placeOn(ChatFrame frame) {
        double boxLeft = frame.boxLeft;
        double barTop = frame.barTop();
        this.left = (int)Math.floor(boxLeft);
        this.top = (int)Math.floor(barTop);
        this.fractionX = (float)(boxLeft - this.left);
        this.fractionY = (float)(barTop - this.top);
        this.right = this.left + (int)Math.round(
                frame.boxRight - frame.boxLeft);
    }

    int left() {
        return this.left;
    }

    int top() {
        return this.top;
    }

    /** The fractional remainder the bar group is drawn shifted by. */
    float fractionX() {
        return this.fractionX;
    }

    float fractionY() {
        return this.fractionY;
    }

    /** The bar's right edge, placed afresh from the active window. */
    int inputBarRight() {
        updateInputBox();
        return this.right;
    }

    /**
     * The y the pickers and completion lists take as their floor: the
     * bar's top plus fourteen. Their layouts measure up from a floor
     * fourteen pixels under the bar's top, so this keeps their shape
     * wherever the bar is.
     */
    int inputAnchor() {
        return this.top + INPUT_ANCHOR_BELOW_BAR;
    }

    /**
     * Top of the bar's square controls, centred in the rows between the
     * bottom rule and the bar's bottom frame edge: the strip's first
     * row is the rule and its last the edge, so the bar's own furniture
     * lives in the rows between them.
     */
    int barControlTop() {
        return controlTopFor(this.top);
    }

    /**
     * The text row's top inside the bar: where a message row puts its
     * text, in the well that is one message row, so what is typed
     * stands exactly as it will once it is sent.
     */
    int barTextTop() {
        return textTopFor(this.top);
    }

    /** {@link #barControlTop} for a bar strip starting at {@code barTop}. */
    static int controlTopFor(int barTop) {
        int interior = WindowPlacement.BAR_STRIP_HEIGHT - 1;
        return barTop + 1 + (interior - ChatPickerPanel.BUTTON_SIZE) / 2;
    }

    /**
     * Top of what stands on a bar strip starting at {@code barTop}, the
     * framed buttons: the clearance below the rule.
     */
    static int contentTopFor(int barTop) {
        return barTop + 1 + CLEARANCE;
    }

    /**
     * Top of the typing well on a bar strip starting at {@code barTop}:
     * one message row, centred on the framed buttons beside it.
     */
    static int wellTopFor(int barTop) {
        return contentTopFor(barTop) + (CONTENT_HEIGHT - WELL_HEIGHT) / 2;
    }

    /**
     * {@link #barTextTop} for a bar strip starting at {@code barTop}: the
     * text where a message row puts it, the well being one.
     */
    static int textTopFor(int barTop) {
        return wellTopFor(barTop) + WindowStyle.ROW_TEXT_TOP;
    }

    /** The anchor the pickers hang their buttons from, and first open their windows by. */
    int pickerAnchor() {
        return barControlTop() + ChatPickerPanel.BUTTON_ANCHOR_OFFSET;
    }

    /** Left edge of the bar control in the given slot from the right. */
    static int barSlotLeft(int barRight, int index) {
        return barRight - ChatPickerPanel.BUTTON_MARGIN
                - (index + 1) * (ChatPickerPanel.BUTTON_SIZE
                        + ChatPickerPanel.BUTTON_MARGIN)
                + ChatPickerPanel.BUTTON_MARGIN;
    }

    /**
     * Left edge of the leftmost bar control; the counter's slot ends a
     * gap before it.
     */
    private int controlsLeft(int barRight) {
        int leftmost = this.toolbarToggleIndex
                + (ChatLayout.isToolbarCollapsed()
                        ? 0 : this.insertPickers.length);
        return barSlotLeft(barRight, leftmost);
    }

    /**
     * The typing line on the bar ending at {@code barRight}, as it
     * stands this frame: after the indicator's ink, before the
     * counter's slot, which is as wide as the widest count.
     */
    private ChatInputLine inputLine(int barRight) {
        return lineAfter(indicatorFit(barRight).controlsRight, barRight);
    }

    /** The typing line after the bar's leading controls, ending at {@code controlsRight}. */
    private ChatInputLine lineAfter(int controlsRight, int barRight) {
        return ChatInputLine.between(controlsRight, controlsLeft(barRight),
                counterSlotWidth(widestCounterInk(),
                        counterScale(LostTalesDisplayPixels.scaleFactor())));
    }

    private int toolbarToggleLeft(int barRight) {
        return barSlotLeft(barRight, this.toolbarToggleIndex);
    }

    boolean isInsideToolbarToggle(double mouseX, double mouseY, int barRight) {
        int toggleLeft = toolbarToggleLeft(barRight);
        int controlTop = barControlTop();
        return LostTalesUiHitBox.contains(mouseX, mouseY, toggleLeft, controlTop,
                ChatPickerPanel.BUTTON_SIZE, ChatPickerPanel.BUTTON_SIZE);
    }

    /**
     * How much of its opacity the bar being drawn shows: all of it, but
     * for the bar of a window still fading in — one just made from tabs
     * carried out of their row — which fades in with its window. Set
     * round one bar's drawing and put back after it
     * ({@link #beginFade}, {@link #endFade}).
     */
    private static float fadeShare = 1.0F;

    /** Draws what follows at {@code share} of the bar's opacity, until {@link #endFade}. */
    static void beginFade(float share) {
        fadeShare = Math.max(0.0F, Math.min(1.0F, share));
    }

    static void endFade() {
        fadeShare = 1.0F;
    }

    /** {@code alpha} at the share of it the bar being drawn shows. */
    static int faded(int alpha) {
        return Math.round(alpha * fadeShare);
    }

    /** An opacity at the share of it the bar being drawn shows. */
    static float fadedShare(float opacity) {
        return opacity * fadeShare;
    }

    /** The bars' entrance from below, every window's alike. */
    float entranceOffset() {
        return WindowOpening.barOffset();
    }

    /* ---- The pickers ---- */

    ChatEmojiPicker reactionPicker() {
        return this.reactionPicker;
    }

    ChatMapMarkerPicker markerPicker() {
        return this.markerPicker;
    }

    /** Re-reads the inventory and the marker cache the pickers list. */
    void refreshPickers() {
        this.itemPicker.refresh(this.mc.thePlayer);
        this.markerPicker.refresh();
        this.questPicker.refresh();
    }

    /** The kind of window a picker opens in. */
    SubWindowKind kindOf(ChatPickerPanel picker) {
        if (picker == this.reactionPicker) {
            return ChatSubWindows.REACTIONS;
        }
        if (picker == this.itemPicker) {
            return ChatSubWindows.ITEMS;
        }
        if (picker == this.markerPicker) {
            return ChatSubWindows.MARKERS;
        }
        if (picker == this.questPicker) {
            return ChatSubWindows.QUESTS;
        }
        return ChatSubWindows.EMOJI;
    }

    /** The picker a kind of window holds; null for a kind no picker opens in. */
    ChatPickerPanel pickerOf(SubWindowKind kind) {
        if (kind == ChatSubWindows.EMOJI) {
            return this.emojiPicker;
        }
        if (kind == ChatSubWindows.REACTIONS) {
            return this.reactionPicker;
        }
        if (kind == ChatSubWindows.ITEMS) {
            return this.itemPicker;
        }
        if (kind == ChatSubWindows.MARKERS) {
            return this.markerPicker;
        }
        return kind == ChatSubWindows.QUESTS ? this.questPicker : null;
    }

    /**
     * Where a picker's window opens before the player has placed it: on
     * the bar being typed in, at its right edge, as the panel always did.
     */
    LostTalesUiHitBox firstPickerBox(ChatPickerPanel picker) {
        return picker.firstContentBox(inputBarRight(), pickerAnchor());
    }

    /** Whether the picker's button is on the bar right now. */
    boolean isPickerShown(ChatPickerPanel picker) {
        if (picker == this.emojiPicker) {
            return LostTalesConfig.enableChatEmojis;
        }
        return !ChatLayout.isToolbarCollapsed();
    }

    /** The picker whose button the point is on, or null. */
    ChatPickerPanel pickerButtonAt(double mouseX, double mouseY, int barRight) {
        int anchor = pickerAnchor();
        for (ChatPickerPanel candidate : this.pickers) {
            if (isPickerShown(candidate) && candidate.isInsideButton(
                    mouseX, mouseY, barRight, anchor)) {
                return candidate;
            }
        }
        return null;
    }

    /* ---- The notice ---- */

    void showNotice(String text) {
        this.noticeText = text == null ? "" : text;
        this.noticeNanos = System.nanoTime();
    }

    /** Short centred confirmation above the input bar (copy, too long). */
    void drawNotice() {
        if (this.noticeNanos <= 0L || this.noticeText.length() == 0) {
            return;
        }
        float ageMillis = (System.nanoTime() - this.noticeNanos)
                / 1000000.0F;
        if (ageMillis >= NOTICE_LIFETIME_MILLIS) {
            this.noticeNanos = 0L;
            return;
        }
        float opacity = Motions.enabled() ? noticeOpacity(ageMillis) : 1.0F;
        int popupWidth = WindowStyle.popupLineWidth(this.font,
                this.noticeText);
        // Centred over the input bar, which is as wide as the history,
        // four pixels clear of it, settling the last three as it comes.
        int x = Math.max(2, (this.left + inputBarRight() - popupWidth) / 2);
        int y = this.top - 4 - WindowStyle.POPUP_LINE_HEIGHT
                + Math.round((1.0F - opacity) * 3.0F);
        WindowStyle.drawPopupLine(this.font, this.noticeText, x,
                y, opacity);
    }

    /** A quick fade in and a slower fade out over the notice's life. */
    static float noticeOpacity(float ageMillis) {
        return Math.min(1.0F, ageMillis / 100.0F)
                * Math.min(1.0F, (NOTICE_LIFETIME_MILLIS - ageMillis) / 250.0F);
    }

    /* ---- Drawing ---- */

    /**
     * The active window's input bar in the tool strip's surface — the
     * tab in front's plum grey at two thirds, one flat stretch of it, so
     * the history stands between two bands of one tone (Nils) — exactly
     * as wide as the window,
     * with holes cut in it for the channel indicator's frame and the
     * typing well, and the empty field's hint in the well. The strip's
     * first row is the window's bottom rule, drawn with the window, so
     * the bar's own paint begins one row below it and never darkens the
     * rule.
     */
    void drawBar(int barRight) {
        IndicatorFit fit = indicatorFit(barRight);
        ChatInputLine line = lineAfter(fit.controlsRight, barRight);
        drawBarSurface(activeFrame(), fit, line, barRight);
        drawHint(line, this.field.getText(), fit.channel);
        this.field.drawTextBox();
    }

    /**
     * The bar's surface with its holes, the well in them, and the
     * window's frame beside and under the bar, which is the bar's own:
     * its surface ring and the edges over it and its two bottom
     * corners, arriving with the bar's fly-in.
     */
    private void drawBarSurface(ChatFrame frame, IndicatorFit fit,
                                ChatInputLine line, int barRight) {
        int wellTop = wellTopFor(this.top);
        int wellBottom = wellTop + WELL_HEIGHT;
        int barBottom = this.top + WindowPlacement.BAR_STRIP_HEIGHT;
        int frameTop = indicatorFrameTop();
        float opacity = fadedShare(WindowStyle.opacity(this.mc));
        int surface = LostTalesUiInk.argb(
                LostTalesUiInk.SURFACE_HIGHLIGHT_RGB,
                Math.round(WindowStyle.INSET_ALPHA * opacity));
        // Holes for the indicator's frame, the character button's where
        // the tab has one, and the typing well; each frame's corner pixels
        // are the bar's, outside the frame's rounding.
        List<int[]> holes = new ArrayList<int[]>();
        holes.add(new int[] {fit.frameLeft, frameTop, fit.frameRight,
                frameTop + LostTalesUiFramedButton.HEIGHT, 1});
        if (hasCharacterButton(fit.channel)) {
            int characterLeft = characterButtonLeft(fit);
            int characterTop = characterButtonTop();
            holes.add(new int[] {characterLeft, characterTop,
                    characterLeft + CHARACTER_BUTTON_SIZE,
                    characterTop + CHARACTER_BUTTON_SIZE, 1});
        }
        holes.add(new int[] {line.wellLeft, wellTop, line.wellRight,
                wellBottom, 0});
        WindowBar.fillWithHoles(this.left, this.top + 1, barRight, barBottom,
                holes, surface);
        drawWell(line, wellTop, wellBottom,
                WindowStyle.insetArgb(opacity));
        // The frame's surface beside and under the bar and its edges over
        // it, on the whole window's ramps: the window's box reaches as far
        // above the bar's foot here as it does on screen.
        WindowBar.drawFoot(this.left, this.top, barRight, surface,
                frame == null ? 0.0F : (float)(frame.boxBottom - frame.boxTop),
                faded(255));
    }

    /**
     * Another drawn window's bar: the same bar the active window wears,
     * at rest — its surface, its well holding the front tab's unsent
     * draft or the hint, that tab's channel indicator and speaker, the
     * buttons unlit — so every window reads as a place to type, while
     * only the active window's bar is typed in. Nothing here answers
     * the pointer beyond the strip itself; a press on it moves the
     * input to this window. Drawn where the live bar's geometry would
     * put it, which is borrowed for the draw and given back. It fades in
     * with a window that is still appearing.
     */
    void drawRestingBar(ChatFrame frame, Window window) {
        ChatTab tab = ChatTab.from(ChatFrame.activeTab(window,
                ChatFrame.visibleTabs(window)));
        if (tab == null || frame == null || !frame.drawn) {
            return;
        }
        beginFade(frame.shownShare());
        try {
            drawRestingBar(frame, tab);
        } finally {
            endFade();
        }
    }

    private void drawRestingBar(ChatFrame frame, ChatTab tab) {
        int liveLeft = this.left;
        int liveTop = this.top;
        int liveRight = this.right;
        float liveFractionX = this.fractionX;
        float liveFractionY = this.fractionY;
        placeOn(frame);
        int barRight = this.right;
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(this.fractionX, this.fractionY + entranceOffset(),
                    0.0F);
            String draft = ClientChatChannelState.getDraft(tab);
            IndicatorFit fit = indicatorFit(barRight, tab);
            ChatInputLine line = lineAfter(fit.controlsRight, barRight);
            drawBarSurface(frame, fit, line, barRight);
            drawHint(line, draft, tab);
            drawDraft(line, draft);
            if (hasCharacterButton(tab)) {
                drawCharacterButton(tab, this.restingMotion);
            }
            drawIndicatorFrame(fit, 0.0F, false);
            int toggleLeft = toolbarToggleLeft(barRight);
            this.toolbarToggle.drawSettled(ChatLayout.isToolbarCollapsed(),
                    toggleLeft, barControlTop() + 1, ChatPickerPanel.BUTTON_SIZE,
                    ChatPickerPanel.BUTTON_SIZE, faded(255));
            for (ChatPickerPanel picker : this.pickers) {
                if (isPickerShown(picker)) {
                    picker.drawRestingButton(barRight, pickerAnchor());
                }
            }
            drawDividers(line, barRight);
            drawSendGlyph(barRight, this.restingMotion);
            drawCounter(line, draft);
        } finally {
            GL11.glPopMatrix();
            this.regions.addWindow(this.left, this.top, barRight,
                    this.top + WindowPlacement.BAR_STRIP_HEIGHT);
            this.left = liveLeft;
            this.top = liveTop;
            this.right = liveRight;
            this.fractionX = liveFractionX;
            this.fractionY = liveFractionY;
        }
    }

    /**
     * A resting bar's unsent draft, where the live field would show it
     * being typed, and drawn by a field of its own that never holds the
     * keys, so its emoji, shares, pings, links, markup and a command read
     * exactly as they do in the live field. It shows from its start.
     */
    private void drawDraft(ChatInputLine line, String draft) {
        if (draft.length() == 0 || this.restingField == null) {
            return;
        }
        this.restingField.xPosition = line.fieldLeft;
        this.restingField.yPosition = barTextTop();
        this.restingField.width = Math.max(MIN_FIELD_WIDTH,
                line.fieldRight - line.fieldLeft);
        if (!draft.equals(this.restingField.getText())) {
            this.restingField.setText(draft);
        }
        this.restingField.setCursorPosition(0);
        this.restingField.drawTextBox();
    }

    /**
     * The typing well: the stretch between the two dividers, two clear
     * pixels inside each, in a surface of its own a step darker than the
     * bar, the way the timestamp column is a step darker than the panel.
     * It fills the hole the bar leaves for it rather than lying over the
     * bar, so the line being typed reads as a place of its own in one
     * flat colour. It is as tall as the indicator's frame beside it and
     * holds one message row in its middle, laying out everything the
     * field draws — the text, the caret, the selection wash and the
     * previews — as a message row does.
     */
    private static void drawWell(ChatInputLine line, int top, int bottom,
                                 int argb) {
        if (line.wellRight > line.wellLeft) {
            Gui.drawRect(line.wellLeft, top, line.wellRight, bottom, argb);
        }
    }

    /**
     * The empty field's hint, in the chat's aside tone and in italics,
     * as an empty window's invitation is: what the field is for, or, in
     * a channel the player cannot talk in, that nothing typed there will
     * be sent — said before Enter has to say it. It stands a pixel clear
     * of the caret waiting at the field's start and goes with the first
     * character typed.
     */
    private void drawHint(ChatInputLine line, String typed, ChatTab tab) {
        if (typed.length() > 0) {
            return;
        }
        String key = ClientChatChannelState.canSend(tab)
                ? "gui.losttales.chat.input.hint"
                : "gui.losttales.chat.input.hint.read_only";
        int x = line.fieldLeft + LostTalesUiCaret.WIDTH + 1;
        String hint = LostTalesSkyrimUiStyle.trimToWidth(this.font,
                StatCollector.translateToLocal(key), line.fieldRight - x);
        LostTalesUiInk.drawText(this.font, "§o" + hint, x,
                barTextTop(), LostTalesChatVisualStyle.asideRgb(), faded(255));
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
    void drawToolbarToggle(int barRight, double mouseX, double mouseY) {
        boolean collapsed = ChatLayout.isToolbarCollapsed();
        boolean hovered = isInsideToolbarToggle(mouseX, mouseY, barRight);
        int toggleLeft = toolbarToggleLeft(barRight);
        this.toolbarToggle.advance(collapsed, hovered);
        this.toolbarToggleMotion.advance(System.nanoTime(), hovered);
        LostTalesUiButton.beginPose(this.toolbarToggleMotion, toggleLeft,
                barControlTop(), ChatPickerPanel.BUTTON_SIZE,
                ChatPickerPanel.BUTTON_SIZE);
        try {
            this.toolbarToggle.draw(toggleLeft, barControlTop(),
                    ChatPickerPanel.BUTTON_SIZE, ChatPickerPanel.BUTTON_SIZE,
                    faded(255));
        } finally {
            LostTalesUiButton.endPose();
        }
        this.regions.add(toggleLeft, barControlTop(),
                toggleLeft + ChatPickerPanel.BUTTON_SIZE,
                barControlTop() + ChatPickerPanel.BUTTON_SIZE);
    }

    /** Every picker's button, in bar space; their windows are the screen's. */
    void drawPickerButtons(int barRight, double mouseX, double mouseY) {
        for (ChatPickerPanel picker : this.pickers) {
            if (isPickerShown(picker)) {
                picker.drawButton(this.regions, barRight, pickerAnchor(),
                        mouseX, mouseY);
            }
        }
    }

    /**
     * The channel indicator: a framed button holding the selected
     * channel's icon and name, as its tab shows them, centred in the
     * frame; the name in the channel's colour, crossing to ivory and the
     * frame to its lit artwork under the pointer, as the main menu's
     * buttons light. Where a narrow window leaves it less room the name
     * is cut, and resting the pointer on it reads it whole with the tabs'
     * own marquee.
     */
    void drawIndicator(int barRight, double mouseX, double mouseY) {
        IndicatorFit fit = indicatorFit(barRight);
        boolean hovered = isInsideIndicator(mouseX, mouseY, barRight);
        long now = System.nanoTime();
        double elapsed = this.indicatorNanos == 0L ? 0.0D
                : (now - this.indicatorNanos) / 1.0E9D;
        this.indicatorNanos = now;
        this.indicatorFade = WindowStyle.hoverFade(
                this.indicatorFade, hovered, elapsed);
        // The marquee runs on the clock while the pointer rests on a cut
        // name and glides home once it leaves, as a tab's does.
        int overflow = fit.labelWidth - fit.labelRoom;
        if (hovered && overflow > 0 && fit.labelRoom > 0
                && Motions.enabled()) {
            this.indicatorHoverSeconds += elapsed;
            this.indicatorMarquee = (float)TabRow.marqueeOffset(
                    this.indicatorHoverSeconds, overflow);
        } else {
            this.indicatorHoverSeconds = 0.0D;
            this.indicatorMarquee = TabRow.eased(
                    this.indicatorMarquee, 0.0F, elapsed);
        }
        drawIndicatorFrame(fit, this.indicatorFade, true);
        this.regions.add(fit.frameLeft, this.top,
                ChatInputLine.dividerAfter(fit.controlsRight),
                this.top + WindowPlacement.BAR_STRIP_HEIGHT);
    }

    /**
     * The indicator's framed button as laid out in {@code fit}, lit as
     * far as {@code lit}: its surface in the hole the bar left for it,
     * the channel's icon, its name — slid by the marquee only on the
     * live bar — and the frame's ink.
     */
    private void drawIndicatorFrame(IndicatorFit fit, float lit,
                                    boolean marquee) {
        int frameTop = indicatorFrameTop();
        int frameWidth = fit.frameRight - fit.frameLeft;
        LostTalesUiFramedButton.drawSurface(fit.frameLeft, frameTop, frameWidth,
                LostTalesUiFramedButton.HEIGHT, lit,
                Math.round(WindowStyle.INSET_ALPHA
                        * fadedShare(WindowStyle.opacity(this.mc))));
        int textTop = barTextTop();
        if (fit.iconLeft >= 0) {
            // The icon's box stands in the frame's middle, the inset
            // above and below it, the name's capitals half a pixel
            // above the box's.
            LostTalesUiInk.beginContent();
            ChatChannelIcons.draw(this.mc, fit.channel, fit.iconLeft,
                    frameTop + LostTalesUiFramedButton.INSET, faded(255));
        }
        if (fit.labelRoom > 0) {
            drawIndicatorLabel(fit, textTop, lit, marquee);
        }
        LostTalesUiFramedButton.drawInk(fit.frameLeft, frameTop, frameWidth,
                LostTalesUiFramedButton.HEIGHT, lit, faded(255));
    }

    /**
     * The indicator's name: whole in the room it keeps, or cut at the
     * room's end and slid by the marquee, its offset laid on a display
     * pixel so the glyphs stay on theirs. A cut name sinks into the edges
     * it is cut at the way a tab's does, in the frame's own tone, across
     * the frame's inside.
     */
    private void drawIndicatorLabel(IndicatorFit fit, int textTop,
                                    float lit, boolean marquee) {
        // A read-only channel reads italic rather than faint: text is
        // always at full opacity.
        String text = ClientChatChannelState.canSend(fit.channel)
                ? fit.label : "§o" + fit.label;
        int color = LostTalesUiInk.blend(
                ClientChatChannelState.displayColor(fit.channel),
                LostTalesUiInk.IVORY, lit);
        if (fit.labelRoom >= fit.labelWidth) {
            LostTalesUiInk.drawText(this.font, text,
                    fit.labelLeft, textTop, color, faded(255));
            return;
        }
        if (!marquee) {
            // A resting bar's cut name simply ends where its room does.
            LostTalesUiInk.drawText(this.font,
                    this.font.trimStringToWidth(text, fit.labelRoom),
                    fit.labelLeft, textTop, color, faded(255));
            return;
        }
        // A cut name thins out into the edge it is cut at, as far as
        // it has gone past it, the words themselves fading; the scissor
        // is in screen space, and the bar group is drawn shifted by its
        // fraction.
        double offset = TabRow.snapped(this.indicatorMarquee,
                TabRow.displayStep());
        float depth = LostTalesUiFading.sideFadeDepth(fit.labelRoom);
        LostTalesUiFading.drawFadingText(this.mc, this.font, text,
                fit.labelLeft, (float)-offset, textTop, color, faded(255),
                fit.labelLeft + this.fractionX,
                fit.labelLeft + fit.labelRoom + this.fractionX, Double.NaN,
                depth,
                LostTalesUiFading.sideFadeStrength(offset, depth),
                LostTalesUiFading.sideFadeStrength(
                        fit.labelWidth - offset - fit.labelRoom, depth));
    }

    /**
     * Left edge of the character button's frame on the selected tab's
     * bar: {@link #BUTTON_GAP} past the channel indicator's frame.
     */
    int characterButtonLeft() {
        return characterButtonLeft(indicatorFit(this.right));
    }

    /** As above on the bar laid out in {@code fit}. */
    private static int characterButtonLeft(IndicatorFit fit) {
        return fit.frameRight + BUTTON_GAP;
    }

    /** Top of the character button's frame: the bar's framed buttons' top. */
    int characterButtonTop() {
        return framedButtonTop();
    }

    /** Past the character button's frame. */
    private int characterButtonRight() {
        return characterButtonLeft() + CHARACTER_BUTTON_SIZE;
    }

    /**
     * Left edge of the channel indicator's frame, the bar's first
     * control: the bar gap in from the window's edge, the frame standing
     * just outside it.
     */
    private int indicatorLeft() {
        return this.left + BAR_GAP;
    }

    /**
     * Top of the indicator's frame: the clearance below the rule, level
     * with the typing well.
     */
    private int indicatorFrameTop() {
        return framedButtonTop();
    }

    /** Top of the bar's framed buttons, the typing well centred on them. */
    private int framedButtonTop() {
        return contentTopFor(this.top);
    }

    /**
     * The channel indicator as it stands on the bar ending at
     * {@code barRight}: a framed button holding the channel's icon, then
     * its name, centred in the frame. While the field beside it keeps
     * {@link #COMFORTABLE_FIELD_WIDTH} the name stands whole; a narrower
     * window takes what the field lacks from the name, then from the gap
     * after the icon, down to the icon alone — the way a tab gives its
     * name up — and the name is cut at the room it keeps, the frame
     * narrowing with it. A channel without an icon keeps its name whole.
     */
    private IndicatorFit indicatorFit(int barRight) {
        return indicatorFit(barRight, ClientChatChannelState.getSelected());
    }

    /** {@link #indicatorFit} for the bar of a window whose front tab is {@code channel}. */
    private IndicatorFit indicatorFit(int barRight, ChatTab channel) {
        int frameLeft = indicatorLeft();
        // What stands between the indicator and the field: the
        // character button, a button gap past the frame, where the tab
        // has one.
        int trailing = hasCharacterButton(channel)
                ? BUTTON_GAP + CHARACTER_BUTTON_SIZE : 0;
        int iconLeft = frameLeft + LostTalesUiFramedButton.WIDE_INSET;
        boolean icon = ChatChannelIcons.iconOf(channel) != null;
        int iconRight = icon ? iconLeft + TabIcons.SIZE : iconLeft;
        int gap = icon ? TabIcons.GAP : 0;
        String label = indicatorLabel(channel);
        int labelWidth = this.font.getStringWidth(label);
        int whole = gap + labelWidth;
        // The last glyph's width includes a column of spacing after it,
        // which is not ink.
        int wholeContentRight = iconRight + whole - 1;
        ChatInputLine wholeLine = lineAfter(wholeContentRight
                + LostTalesUiFramedButton.WIDE_INSET + trailing, barRight);
        int shown = icon ? indicatorShown(whole,
                wholeLine.fieldRight - wholeLine.fieldLeft) : whole;
        int contentRight = shown >= whole ? wholeContentRight
                : iconRight + shown;
        int frameRight = contentRight + LostTalesUiFramedButton.WIDE_INSET;
        return new IndicatorFit(channel, label, icon ? iconLeft : -1,
                iconRight + gap, labelWidth, Math.max(0, shown - gap),
                frameLeft, frameRight, frameRight + trailing);
    }

    /**
     * How much of the gap after the indicator's icon and of the name
     * after it stands, out of the {@code whole} the two take, where all
     * of it would leave the field {@code fieldWidth} wide: all of it
     * while the field keeps {@link #COMFORTABLE_FIELD_WIDTH}, and else
     * what the field lacks of that less — and the whole name's last
     * column less, which is spacing rather than ink and so wins the
     * field nothing — so the field keeps that width until nothing is
     * left but the icon.
     */
    static int indicatorShown(int whole, int fieldWidth) {
        int lack = COMFORTABLE_FIELD_WIDTH - fieldWidth;
        return lack <= 0 ? whole : Math.max(0, whole - 1 - lack);
    }

    /** The indicator laid out for one bar, as drawn and as hit. */
    private static final class IndicatorFit {
        final ChatTab channel;
        final String label;
        /** Left edge of the channel's icon; -1 for a channel without one. */
        final int iconLeft;
        final int labelLeft;
        /** The whole name's width, and the room it keeps of that. */
        final int labelWidth;
        final int labelRoom;
        /** The frame's box: what is drawn, and what answers the pointer. */
        final int frameLeft;
        final int frameRight;
        /**
         * Just past the last of the bar's leading controls — the
         * character button's frame where the tab has one, else the
         * indicator's: where the gap before the divider is measured
         * from, as every gap along the line is measured from what
         * stands beside it.
         */
        final int controlsRight;

        IndicatorFit(ChatTab channel, String label, int iconLeft,
                     int labelLeft, int labelWidth, int labelRoom,
                     int frameLeft, int frameRight, int controlsRight) {
            this.channel = channel;
            this.label = label;
            this.iconLeft = iconLeft;
            this.labelLeft = labelLeft;
            this.labelWidth = labelWidth;
            this.labelRoom = labelRoom;
            this.frameLeft = frameLeft;
            this.frameRight = frameRight;
            this.controlsRight = controlsRight;
        }
    }

    /**
     * Whether the point is on the character button's frame: a framed
     * button answers on its frame and nowhere else.
     */
    boolean isInsideCharacterButton(double mouseX, double mouseY) {
        return hasCharacterButton(ClientChatChannelState.getSelected())
                && LostTalesUiHitBox.contains(mouseX, mouseY, characterButtonLeft(),
                characterButtonTop(), CHARACTER_BUTTON_SIZE,
                CHARACTER_BUTTON_SIZE);
    }

    /**
     * The character-selection button: a framed button like the tab
     * search's, with the chat identity's head centred in it over the
     * project's flat shadow like every other head in the chat, the
     * frame lit under the pointer and while its menu is out. An account
     * channel has no button: it always speaks as the account, so there
     * is nothing to choose, and the indicator stands where the button
     * would.
     */
    void drawCharacterSelectionButton(double mouseX, double mouseY,
                                      boolean menuOpen) {
        ChatTab tab = ClientChatChannelState.getSelected();
        if (!hasCharacterButton(tab)) {
            return;
        }
        boolean hovered = isInsideCharacterButton(mouseX, mouseY);
        this.characterMotion.advance(System.nanoTime(), hovered || menuOpen,
                hovered, hovered && Mouse.isButtonDown(0));
        drawCharacterButton(tab, this.characterMotion);
        this.regions.add(characterButtonLeft(), characterButtonTop(),
                characterButtonRight(),
                characterButtonTop() + CHARACTER_BUTTON_SIZE);
    }

    /**
     * Whether the bar of a window whose front tab is {@code tab} wears
     * the head button. Every bar does: on a roleplaying channel it
     * chooses the chat identity as well as the status, and on an account
     * channel — which always speaks as the account — it is the player's
     * own head and their status alone.
     */
    static boolean hasCharacterButton(ChatTab tab) {
        return true;
    }

    /**
     * The Narrator's mark where the head stands while the voice is
     * chosen, with the chat's shadow under it as a head has.
     */
    private void drawNarratorMark(float headX, float headY) {
        ChatInlineIcons.drawEmoji(this.mc, ChatHeadMarker.NARRATOR_MARK,
                headX + LostTalesUiInk.SHADOW_OFFSET,
                headY + LostTalesUiInk.SHADOW_OFFSET,
                CHARACTER_HEAD_SIZE,
                LostTalesUiInk.shadowAlpha(faded(255)), true);
        ChatInlineIcons.drawEmoji(this.mc, ChatHeadMarker.NARRATOR_MARK,
                headX, headY, CHARACTER_HEAD_SIZE, faded(255), false);
    }

    /**
     * The chat identity's head, inside the shared head button, wearing
     * the sphere of the status everyone else sees of the identity the tab
     * speaks as. The head and its sphere are one icon, centred in the
     * button with the framed buttons' two clear pixels round it; the
     * Narrator's mark wears none and keeps the wide inset. The frame
     * keeps its place and the head moves inside it, so the button reads
     * as a socket holding a face rather than the whole thing sliding.
     */
    private void drawCharacterButton(ChatTab tab,
                                     LostTalesUiButtonMotion motion) {
        float lit = motion.lit();
        int left = characterButtonLeft(indicatorFit(this.right, tab));
        int top = characterButtonTop();
        LostTalesUiFramedButton.drawSurface(left, top, CHARACTER_BUTTON_SIZE,
                CHARACTER_BUTTON_SIZE, lit,
                Math.round(WindowStyle.INSET_ALPHA
                        * fadedShare(WindowStyle.opacity(this.mc))));
        ClientChatIdentities.Identity shown =
                ClientChatIdentities.effectiveFor(tab);
        UUID self = this.mc.thePlayer == null ? null
                : this.mc.thePlayer.getUniqueID();
        if (self != null) {
            boolean account = shown.account || shown.skinId.length() == 0;
            // The Narrator is a voice for roleplaying; an account
            // channel shows the player's own head whatever is chosen
            // elsewhere.
            boolean narrating = ClientChatIdentities.isNarrating()
                    && ClientChatIdentities.speaksInCharacter(tab);
            int iconWidth = narrating ? CHARACTER_HEAD_SIZE
                    : CHARACTER_HEAD_SIZE + LostTalesUiCornerMark.OVERHANG_X;
            int iconHeight = narrating ? CHARACTER_HEAD_SIZE
                    : CHARACTER_HEAD_SIZE + LostTalesUiCornerMark.OVERHANG_Y;
            // Centred, the odd pixel up and to the left.
            float headX = left + (CHARACTER_BUTTON_SIZE - iconWidth) / 2;
            float headY = top + (CHARACTER_BUTTON_SIZE - iconHeight) / 2;
            LostTalesUiInk.beginContent();
            LostTalesUiButton.beginPose(motion, headX, headY, iconWidth,
                    iconHeight);
            try {
                if (narrating) {
                    drawNarratorMark(headX, headY);
                } else {
                    ChatPresence presence = ClientChatPresence.presenceOf(
                            self, ClientChatPresence.speakerOf(tab));
                    ChatPresenceMark.beginShadowCut(headX, headY,
                            CHARACTER_HEAD_SIZE);
                    try {
                        drawButtonHeadShadow(self, account, shown.skinId,
                                headX, headY);
                    } finally {
                        ChatPresenceMark.endHeadCut();
                    }
                    ChatPresenceMark.beginHeadCut(headX, headY,
                            CHARACTER_HEAD_SIZE);
                    try {
                        if (account) {
                            LostTalesCharacterHeadIconRenderer.drawAccountHead(
                                    this.mc, self, headX, headY,
                                    CHARACTER_HEAD_SIZE, 1.0F, fadedShare(1.0F));
                        } else {
                            LostTalesCharacterHeadIconRenderer.drawSnapshotHead(
                                    this.mc, self, shown.skinId, headX, headY,
                                    CHARACTER_HEAD_SIZE, 1.0F, fadedShare(1.0F));
                        }
                    } finally {
                        ChatPresenceMark.endHeadCut();
                    }
                    ChatPresenceMark.draw(headX, headY, CHARACTER_HEAD_SIZE,
                            presence, faded(255));
                }
            } finally {
                LostTalesUiButton.endPose();
            }
        }
        LostTalesUiFramedButton.drawInk(left, top, CHARACTER_BUTTON_SIZE,
                CHARACTER_BUTTON_SIZE, lit, faded(255));
    }

    /**
     * The head's flat silhouette shadow, one pixel down-right at two
     * thirds opacity: the treatment every comparable head in the chat
     * carries.
     */
    private void drawButtonHeadShadow(UUID self, boolean account,
                                      String skinId, float x, float y) {
        LostTalesSilhouetteRenderState.begin(LostTalesUiInk.SHADOW);
        try {
            if (account) {
                LostTalesCharacterHeadIconRenderer.drawTintedAccountHeadBase(
                        this.mc, self,
                        x + LostTalesUiInk.SHADOW_OFFSET,
                        y + LostTalesUiInk.SHADOW_OFFSET,
                        CHARACTER_HEAD_SIZE, 1.0F, 1.0F, 1.0F,
                        fadedShare(LostTalesUiInk.SHADOW_OPACITY));
            } else {
                LostTalesCharacterHeadIconRenderer.drawTintedSnapshotHeadBase(
                        this.mc, self, skinId,
                        x + LostTalesUiInk.SHADOW_OFFSET,
                        y + LostTalesUiInk.SHADOW_OFFSET,
                        CHARACTER_HEAD_SIZE, 1.0F, 1.0F, 1.0F,
                        fadedShare(LostTalesUiInk.SHADOW_OPACITY));
            }
        } finally {
            LostTalesSilhouetteRenderState.end();
        }
    }

    /**
     * Whether the point is on the indicator's frame: a framed button
     * answers on its frame, as the bar's other buttons answer on their
     * squares.
     */
    boolean isInsideIndicator(double mouseX, double mouseY, int barRight) {
        IndicatorFit fit = indicatorFit(barRight);
        int frameTop = indicatorFrameTop();
        return LostTalesUiHitBox.contains(mouseX, mouseY, fit.frameLeft, frameTop,
                fit.frameRight - fit.frameLeft, LostTalesUiFramedButton.HEIGHT);
    }

    /** The channel as the indicator names it: its shown name, as its tab does. */
    static String indicatorLabel(ChatTab tab) {
        return ClientChatChannelState.displayName(tab);
    }

    /* ---- The dividers, the send button and the character counter ---- */

    private static int sendButtonLeft(int barRight) {
        return barSlotLeft(barRight, SEND_BUTTON_INDEX);
    }

    boolean isInsideSendButton(double mouseX, double mouseY, int barRight) {
        int buttonLeft = sendButtonLeft(barRight);
        int controlTop = barControlTop();
        return LostTalesUiHitBox.contains(mouseX, mouseY, buttonLeft, controlTop,
                ChatPickerPanel.BUTTON_SIZE, ChatPickerPanel.BUTTON_SIZE);
    }

    /**
     * The bar's dividers, each the hairline the tab row parts a window's
     * controls with: one on either side of the typing well — after the
     * character button and before the counter — from the well's top to
     * its bottom, as the timestamp area's separator runs down its panel;
     * and one between the send button and the buttons that put something
     * into the message, in the gap the bar's slots already leave, at the
     * tab row's height, so sending reads as its own act rather than as
     * one more insert.
     */
    void drawDividers(int barRight) {
        drawDividers(inputLine(barRight), barRight);
    }

    private void drawDividers(ChatInputLine line, int barRight) {
        int insertRight = barSlotLeft(barRight, SEND_BUTTON_INDEX + 1)
                + ChatPickerPanel.BUTTON_SIZE;
        int sendLeft = barSlotLeft(barRight, SEND_BUTTON_INDEX);
        int wellTop = wellTopFor(this.top);
        drawBarDivider(line.leftDividerX, wellTop, WELL_HEIGHT);
        drawBarDivider(line.rightDividerX, wellTop, WELL_HEIGHT);
        // One pixel in the gap between the inserts and the send button,
        // the odd pixel left, centred on the buttons.
        drawBarDivider(insertRight + LostTalesUiInk.centredStart(
                        sendLeft - insertRight, 1),
                barControlTop() + (ChatPickerPanel.BUTTON_SIZE
                        - BAR_DIVIDER_HEIGHT) / 2, BAR_DIVIDER_HEIGHT);
    }

    /** One of the bar's dividers, {@code height} rows from {@code top}. */
    private void drawBarDivider(int x, int top, int height) {
        WindowStyle.drawDivider(x, top, height,
                faded(WindowStyle.DIVIDER_ALPHA));
    }

    /**
     * The sheet's send button, centred in the button square at the bar's
     * right end, lifted a pixel and crossing to its lit artwork while
     * hovered, as the picker buttons beside it do.
     */
    void drawSendButton(int barRight, double mouseX, double mouseY) {
        int buttonLeft = sendButtonLeft(barRight);
        int controlTop = barControlTop();
        boolean hovered = isInsideSendButton(mouseX, mouseY, barRight);
        this.sendMotion.advance(System.nanoTime(), hovered);
        drawSendGlyph(barRight, this.sendMotion);
        this.regions.add(buttonLeft, controlTop,
                buttonLeft + ChatPickerPanel.BUTTON_SIZE,
                controlTop + ChatPickerPanel.BUTTON_SIZE);
    }

    /**
     * The send glyph in its square, posed by its own beat: it rises,
     * tips as it throws, and drops onto the surface while it is held.
     * Text and glyphs are always fully opaque.
     */
    private void drawSendGlyph(int barRight,
                               LostTalesUiButtonMotion motion) {
        int x = sendButtonLeft(barRight) + (ChatPickerPanel.BUTTON_SIZE
                - LostTalesUiSheet.SEND.getWidth()) / 2;
        int y = barControlTop() + (ChatPickerPanel.BUTTON_SIZE
                - LostTalesUiSheet.SEND.getHeight()) / 2;
        LostTalesUiButton.drawGlyph(LostTalesUiSheet.SEND,
                LostTalesUiSheet.SEND_HOVER, motion, x, y, faded(255));
    }

    /** {@code 37/256} for a message, {@code 0/256} for none yet; nothing for a command. */
    private static String counterText(String text) {
        if (ChatInputRules.isCommand(text)) {
            return "";
        }
        return ChatMessageValidator.visibleLength(text)
                + "/" + ChatMessageValidator.MAX_CHARACTERS;
    }

    /**
     * The counter's scale: small text, one display pixel less per font
     * pixel than the text beside it
     * ({@link LostTalesChatVisualStyle#smallTextScale}).
     */
    static float counterScale(int displayScaleFactor) {
        return LostTalesChatVisualStyle.smallTextScale(displayScaleFactor);
    }

    /**
     * Ink width of the widest count the counter can show, every digit
     * the font's widest: the counter's slot is sized for it, as the
     * timestamp column is sized for the widest time, so neither divider
     * nor the field moves while the count grows.
     */
    private int widestCounterInk() {
        String limit = String.valueOf(ChatMessageValidator.MAX_CHARACTERS);
        return LostTalesChatVisualStyle.widestDigitWidth(this.font)
                * limit.length() + this.font.getCharWidth('/')
                + this.font.getStringWidth(limit) - 1;
    }

    /**
     * The counter's slot: its widest ink at the counter's scale, in
     * whole pixels.
     */
    static int counterSlotWidth(int widestInk, float scale) {
        return (int)Math.ceil(Math.max(0, widestInk) * scale);
    }

    /**
     * The counter in sand, salmon once the limit is hit, at the start
     * of its slot a gap past the divider. A command shows no count and
     * leaves the slot empty, so typing one moves nothing.
     */
    void drawCounter(int barRight) {
        drawCounter(inputLine(barRight), this.field.getText());
    }

    private void drawCounter(ChatInputLine line, String typed) {
        String text = counterText(typed);
        if (text.length() == 0) {
            return;
        }
        boolean full = ChatInputRules.atMessageLimit(typed);
        // Its capitals centred on the full-size text's, and its shadow a
        // pixel of its own size away, as the timestamps' are.
        int factor = LostTalesDisplayPixels.scaleFactor();
        float scale = counterScale(factor);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(line.counterLeft, barTextTop()
                    + LostTalesChatVisualStyle.smallTextTopOffset(factor),
                    0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            LostTalesUiInk.drawText(this.font, text,
                    0, 0, full ? COUNTER_FULL_RGB : COUNTER_RGB, faded(255));
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * Places the field in its well: past the left divider, a caret's
     * width and a gap short of the right one. The field's position is
     * the bar's whole-pixel part, and the group it is drawn in is
     * translated by the fractional remainder. Both must come from the
     * same reading of the window's box: mixing one frame's whole pixels
     * with the next frame's fraction makes the text jump about while
     * the window is dragged.
     */
    public void updateInputBounds() {
        if (this.field == null) {
            return;
        }
        ChatInputLine line = inputLine(inputBarRight());
        this.field.xPosition = line.fieldLeft;
        this.field.yPosition = barTextTop();
        this.field.width = Math.max(MIN_FIELD_WIDTH,
                line.fieldRight - line.fieldLeft);
    }
}
