package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMarkdown;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.StatCollector;
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
final class ChatInputBar {
    /** Height vanilla gives the chat's field, kept for the one we draw. */
    static final int FIELD_HEIGHT = 12;
    /** Clear rows between the bar's edges and what stands on it. */
    static final int CLEARANCE = 2;
    /**
     * Height of the typing well: the icon's box with the strips' wide
     * inset above and below it, one message row in its middle. The
     * bar's framed buttons — the character button and the channel
     * indicator — are the framed buttons' one height, a row inside the
     * well at either end and centred on its middle; the bar's other
     * buttons are shorter and centred on the same middle.
     */
    static final int CONTENT_HEIGHT =
            ChatChannelIcons.SIZE + 2 * ChatFramedButton.WIDE_INSET;
    /**
     * Height of the bar strip: the window's bottom rule, then what
     * stands on the bar with the clearance above and below it. The
     * window's frame runs just outside the bar, below it and beside it,
     * and counts toward neither the height nor a gap.
     */
    static final int HEIGHT = 1 + CLEARANCE + CONTENT_HEIGHT + CLEARANCE;
    /** The pickers and lists take the bar's top plus this as their floor. */
    private static final int INPUT_ANCHOR_BELOW_BAR = 14;
    /** The send button is the rightmost bar control. */
    private static final int SEND_BUTTON_INDEX = 0;
    /** The bar's divider, as tall as the row's between window controls. */
    private static final int BAR_DIVIDER_HEIGHT =
            ChatChannelTabBar.END_CONTROL_SIZE;
    private static final int COUNTER_RGB =
            LostTalesColors.rgb(LostTalesColors.SAND);
    private static final int COUNTER_FULL_RGB =
            LostTalesColors.rgb(LostTalesColors.SALMON);
    static final float NOTICE_LIFETIME_MILLIS = 1400.0F;
    /**
     * Clear space between the things standing on the bar, and before
     * the first of them. Measured in ink, like every other gap the chat
     * keeps: what the eye reads as the gap is the space between the
     * pixels that were actually drawn, not between the boxes they were
     * drawn in. The character button's square is wider than the head
     * inside it and the indicator's is wider than its label, so both
     * are asked where their ink is rather than where their box is.
     */
    static final int BAR_GAP = 3;
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
    static final int CHARACTER_BUTTON_SIZE = ChatFramedButton.HEIGHT;
    /** The chevron's frames, pointing right through upright to left. */
    private static final ChatIconSheet[] TOGGLE_FRAMES = {
            ChatIconSheet.TOGGLE_1, ChatIconSheet.TOGGLE_2,
            ChatIconSheet.TOGGLE_3, ChatIconSheet.TOGGLE_4,
            ChatIconSheet.TOGGLE_5};
    private static final ChatIconSheet[] TOGGLE_FRAMES_HOVER = {
            ChatIconSheet.TOGGLE_1_HOVER, ChatIconSheet.TOGGLE_2_HOVER,
            ChatIconSheet.TOGGLE_3_HOVER, ChatIconSheet.TOGGLE_4_HOVER,
            ChatIconSheet.TOGGLE_5_HOVER};

    private Minecraft mc;
    private FontRenderer font;
    private ChatPointerRegions regions;
    private ChatInputField field;
    private int screenHeight;

    /** When the bars' entrance began: the screen opening. */
    private long entranceNanos = System.nanoTime();
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
    /** How far the character button's frame has lit, and when it last moved. */
    private float characterFade;
    private long characterNanos;    /** How far the send button has crossed to its lit artwork, and when. */
    private float sendFade;
    private long sendFadeNanos;
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
    /** Bar slot of the fold chevron, between the emoji and the inserts. */
    private int toolbarToggleIndex;
    private final ChatIconFlipbook toolbarToggle =
            new ChatIconFlipbook(TOGGLE_FRAMES, TOGGLE_FRAMES_HOVER);

    /**
     * Takes the field the screen just built and the screen's height;
     * called from {@code initGui}, which also runs on every resize.
     * The pickers, the notice and the entrance keep their state.
     */
    void bind(Minecraft mc, FontRenderer font, ChatPointerRegions regions,
              ChatInputField field, int screenHeight) {
        this.mc = mc;
        this.font = font;
        this.regions = regions;
        this.field = field;
        this.screenHeight = screenHeight;
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
    ChatWindowFrame activeFrame() {
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

    /** Places the bar on the active window's bar strip, as just drawn. */
    void updateInputBox() {
        ChatWindowFrame frame = activeFrame();
        if (frame == null) {
            this.left = ChatWindowPlacement.EDGE_MARGIN;
            this.top = this.screenHeight - ChatWindowPlacement.EDGE_MARGIN
                    - ChatWindowPlacement.INPUT_HEIGHT;
            this.right = this.left + ChatWindowPlacement.windowWidth(this.mc);
            this.fractionX = 0.0F;
            this.fractionY = 0.0F;
            return;
        }
        placeOn(frame);
    }

    /** Places the bar on the window's bar strip, as just drawn. */
    private void placeOn(ChatWindowFrame frame) {
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
        int interior = ChatWindowPlacement.INPUT_HEIGHT - 1;
        return barTop + 1 + (interior - ChatPickerPanel.BUTTON_SIZE) / 2;
    }

    /**
     * Top of the typing well on a bar strip starting at {@code barTop}:
     * the clearance below the rule, as tall as the indicator's frame
     * beside it and level with it.
     */
    static int wellTopFor(int barTop) {
        return barTop + 1 + CLEARANCE;
    }

    /**
     * {@link #barTextTop} for a bar strip starting at {@code barTop}: a
     * message row centred in the well, and the text where a message row
     * puts it.
     */
    static int textTopFor(int barTop) {
        return wellTopFor(barTop)
                + (CONTENT_HEIGHT - LostTalesChatOverlayRenderer.LINE_HEIGHT) / 2
                + LostTalesChatOverlayRenderer.ROW_TEXT_TOP;
    }

    /** The anchor the pickers hang their buttons and panels from. */
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
                + (ChatWindowLayout.isToolbarCollapsed()
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
                        counterScale(ChatWindowFrame.displayScaleFactor())));
    }

    private int toolbarToggleLeft(int barRight) {
        return barSlotLeft(barRight, this.toolbarToggleIndex);
    }

    boolean isInsideToolbarToggle(double mouseX, double mouseY, int barRight) {
        int toggleLeft = toolbarToggleLeft(barRight);
        int controlTop = barControlTop();
        return ChatHitBox.contains(mouseX, mouseY, toggleLeft, controlTop,
                ChatPickerPanel.BUTTON_SIZE, ChatPickerPanel.BUTTON_SIZE);
    }

    /** The bars' entrance from below, timed from the screen's opening. */
    float entranceOffset() {
        if (!LostTalesConfig.enableChatAnimations) {
            return 0.0F;
        }
        long duration = Math.max(1,
                LostTalesConfig.chatInputAnimationDurationMillis)
                * 1000000L;
        float progress = Math.max(0.0F, Math.min(1.0F,
                (System.nanoTime() - this.entranceNanos)
                        / (float)duration));
        return LostTalesChatMotion.inputOffset(progress);
    }

    /* ---- The pickers ---- */

    ChatPickerPanel[] pickers() {
        return this.pickers;
    }

    ChatEmojiPicker emojiPicker() {
        return this.emojiPicker;
    }

    ChatMapMarkerPicker markerPicker() {
        return this.markerPicker;
    }

    void tickPickers() {
        for (ChatPickerPanel picker : this.pickers) {
            picker.tick();
        }
    }

    /** Re-reads the inventory and the marker cache the pickers list. */
    void refreshPickers() {
        this.itemPicker.refresh(this.mc.thePlayer);
        this.markerPicker.refresh();
        this.questPicker.refresh();
    }

    /** The picker whose panel is open, or null. */
    ChatPickerPanel openPicker() {
        for (ChatPickerPanel picker : this.pickers) {
            if (picker.isOpen()) {
                return picker;
            }
        }
        return null;
    }

    void closePickers() {
        for (ChatPickerPanel picker : this.pickers) {
            picker.setOpen(false);
        }
    }

    /** Folds the insert pickers' panels with their buttons. */
    void closeInsertPickers() {
        for (ChatPickerPanel picker : this.insertPickers) {
            picker.setOpen(false);
        }
    }

    /** Whether the picker's button (and panel) is on the bar right now. */
    boolean isPickerShown(ChatPickerPanel picker) {
        if (picker == this.emojiPicker) {
            return LostTalesConfig.enableChatEmojis;
        }
        return !ChatWindowLayout.isToolbarCollapsed();
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
        float opacity = LostTalesConfig.enableChatAnimations
                ? noticeOpacity(ageMillis) : 1.0F;
        int alpha = Math.max(LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA,
                Math.min(255, Math.round(255.0F * opacity)));
        int popupWidth = this.font.getStringWidth(this.noticeText) + 10;
        // Centred over the input bar, which is as wide as the history.
        int x = Math.max(2, (this.left + inputBarRight() - popupWidth) / 2);
        int y = this.top - 17 + Math.round((1.0F - opacity) * 3.0F);
        LostTalesChatVisualStyle.drawPopup(x, y, x + popupWidth, y + 13,
                opacity);
        LostTalesChatVisualStyle.drawPlain(this.font, this.noticeText, x + 5,
                y + 2, alpha);
    }

    /** A quick fade in and a slower fade out over the notice's life. */
    static float noticeOpacity(float ageMillis) {
        return Math.min(1.0F, ageMillis / 100.0F)
                * Math.min(1.0F, (NOTICE_LIFETIME_MILLIS - ageMillis) / 250.0F);
    }

    /* ---- Drawing ---- */

    /**
     * The active window's input bar in the chat's surface — the
     * history's own colour at the same half opacity as the message
     * backdrop, one flat stretch of it — exactly as wide as the window,
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
     * its surface ring and the white edges over it, arriving with the
     * bar's fly-in.
     */
    private void drawBarSurface(ChatWindowFrame frame, IndicatorFit fit,
                                ChatInputLine line, int barRight) {
        int wellTop = wellTopFor(this.top);
        int wellBottom = wellTop + CONTENT_HEIGHT;
        int barBottom = this.top + ChatWindowPlacement.INPUT_HEIGHT;
        int frameTop = indicatorFrameTop();
        float opacity = LostTalesChatVisualStyle.chatOpacity(this.mc);
        int surface = LostTalesChatVisualStyle.surfaceArgb(opacity);
        // Up to the indicator's frame plain; the indicator's frame, then
        // up to the divider around the character button's frame where
        // the tab has one — each frame's corner pixels are the bar's,
        // outside the frame's rounding; from the divider on, around the
        // typing well.
        LostTalesChatOverlayRenderer.fillRect(this.left, this.top + 1,
                fit.frameLeft, barBottom, surface);
        LostTalesChatVisualStyle.fillAround(fit.frameLeft, this.top + 1,
                fit.frameRight, barBottom, fit.frameLeft, frameTop,
                fit.frameRight, frameTop + ChatFramedButton.HEIGHT, surface);
        ChatFramedButton.fillCorners(fit.frameLeft, frameTop,
                fit.frameRight - fit.frameLeft, ChatFramedButton.HEIGHT,
                surface);
        if (hasCharacterButton(fit.channel)) {
            int characterLeft = characterButtonLeft(fit);
            int characterTop = characterButtonTop();
            LostTalesChatVisualStyle.fillAround(fit.frameRight, this.top + 1,
                    line.leftDividerX, barBottom, characterLeft, characterTop,
                    characterLeft + CHARACTER_BUTTON_SIZE,
                    characterTop + CHARACTER_BUTTON_SIZE, surface);
            ChatFramedButton.fillCorners(characterLeft, characterTop,
                    CHARACTER_BUTTON_SIZE, CHARACTER_BUTTON_SIZE, surface);
        } else {
            LostTalesChatOverlayRenderer.fillRect(fit.frameRight, this.top + 1,
                    line.leftDividerX, barBottom, surface);
        }
        LostTalesChatVisualStyle.fillAround(line.leftDividerX, this.top + 1,
                barRight, barBottom, line.wellLeft, wellTop, line.wellRight,
                wellBottom, surface);
        drawWell(line, wellTop, wellBottom,
                LostTalesChatVisualStyle.insetArgb(opacity));
        // The frame's surface beside and under the bar, a frame wide, in
        // the bar's own surface; the white edges lie over it.
        int ring = ChatWindowPlacement.FRAME_WIDTH;
        LostTalesChatOverlayRenderer.fillRect(this.left - ring, this.top,
                this.left, barBottom, surface);
        LostTalesChatOverlayRenderer.fillRect(barRight, this.top,
                barRight + ring, barBottom, surface);
        LostTalesChatOverlayRenderer.fillRect(this.left - ring, barBottom,
                barRight + ring, barBottom + ring - 1, surface);
        // The ring's outermost corner pixels lie outside the frame's
        // rounding, as a framed button's footprint corners do.
        LostTalesChatOverlayRenderer.fillRect(this.left - ring + 1,
                barBottom + ring - 1, barRight + ring - 1, barBottom + ring,
                surface);
        drawBarLeftEdge(frame, this.left, this.top);
        drawBarRightEdge(frame, barRight, this.top);
        // The frame's white bottom row, just below the bar, and its
        // brightest corner closed as a lit framed button's is, rounded and
        // shaded over the two edges' ends.
        LostTalesChatOverlayRenderer.drawBarBottomEdge(this.left, barRight,
                barBottom, 255);
        ChatFramedButton.drawCornerInk(ChatIconSheet.FRAME_LIT_BOTTOM_LEFT,
                this.left - ring, barBottom + ring - ChatFramedButton.CORNER,
                255);
    }

    /**
     * Another drawn window's bar: the same bar the active window wears,
     * at rest — its surface, its well holding the front tab's unsent
     * draft or the hint, that tab's channel indicator and speaker, the
     * buttons unlit — so every window reads as a place to type, while
     * only the active window's bar is typed in. Nothing here answers
     * the pointer beyond the strip itself; a press on it moves the
     * input to this window. Drawn where the live bar's geometry would
     * put it, which is borrowed for the draw and given back.
     */
    void drawRestingBar(ChatWindowFrame frame, ChatWindow window) {
        ChatTab tab = ChatWindowFrame.activeTab(window,
                ChatWindowFrame.visibleTabs(window));
        if (tab == null || frame == null || !frame.drawn) {
            return;
        }
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
                drawCharacterButton(tab, 0.0F);
            }
            drawIndicatorFrame(fit, 0.0F, false);
            int toggleLeft = toolbarToggleLeft(barRight);
            this.toolbarToggle.drawSettled(ChatWindowLayout.isToolbarCollapsed(),
                    toggleLeft, barControlTop() + 1, ChatPickerPanel.BUTTON_SIZE,
                    ChatPickerPanel.BUTTON_SIZE, 255);
            for (ChatPickerPanel picker : this.pickers) {
                if (isPickerShown(picker)) {
                    picker.drawRestingButton(barRight, pickerAnchor());
                }
            }
            drawDividers(line, barRight);
            drawSendGlyph(barRight, 0.0F, false);
            drawCounter(line, draft);
        } finally {
            GL11.glPopMatrix();
            this.regions.addWindow(this.left, this.top, barRight,
                    this.top + ChatWindowPlacement.INPUT_HEIGHT);
            this.left = liveLeft;
            this.top = liveTop;
            this.right = liveRight;
            this.fractionX = liveFractionX;
            this.fractionY = liveFractionY;
        }
    }

    /**
     * A resting bar's unsent draft, cut to the field, where the live
     * field would show it being typed: plain in the field's own ivory,
     * save a command, which is the chat's inline code here as it is in
     * the field, up to the words a whisper verb sends.
     */
    private void drawDraft(ChatInputLine line, String draft) {
        if (draft.length() == 0) {
            return;
        }
        int x = line.fieldLeft + ChatInputField.CARET_WIDTH + 1;
        String shown = this.font.trimStringToWidth(draft, line.fieldRight - x);
        int code = Math.min(ChatInputStyles.commandCodeLength(draft),
                shown.length());
        if (code > 0) {
            String command = shown.substring(0, code);
            LostTalesChatVisualStyle.drawColored(this.font,
                    ChatInputStyles.prefixOf(ChatMarkdown.Span.CODE) + command,
                    x, barTextTop(), ChatInputStyles.colorOf(
                            ChatMarkdown.Span.CODE,
                            LostTalesChatVisualStyle.IVORY), 255);
            x += this.font.getStringWidth(command);
        }
        if (code < shown.length()) {
            LostTalesChatVisualStyle.drawColored(this.font,
                    shown.substring(code), x, barTextTop(),
                    LostTalesChatVisualStyle.IVORY, 255);
        }
    }

    /**
     * The typing well: the stretch between the two dividers, in a
     * surface of its own a step darker than the bar, the way the
     * timestamp column is a step darker than the panel. It fills the
     * hole the bar leaves for it rather than lying over the bar, so the
     * line being typed reads as a place of its own in one flat colour.
     * It is as tall as the indicator's frame beside it and holds one
     * message row in its middle, laying out everything the field draws
     * — the text, the caret, the selection wash and the previews — as a
     * message row does.
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
        int x = line.fieldLeft + ChatInputField.CARET_WIDTH + 1;
        String hint = LostTalesSkyrimUiStyle.trimToWidth(this.font,
                StatCollector.translateToLocal(key), line.fieldRight - x);
        LostTalesChatVisualStyle.drawColored(this.font, "§o" + hint, x,
                barTextTop(), LostTalesChatVisualStyle.asideRgb(), 255);
    }

    /**
     * The bar's stretch of the window's left frame edge, just outside the
     * bar, on the same ramp as the window's own stretch above it: full on
     * the frame's bottom row, gone on its top row.
     */
    private static void drawBarLeftEdge(ChatWindowFrame frame, int barLeft,
                                        int barTop) {
        if (frame == null) {
            return;
        }
        float barBottom = barTop + ChatWindowPlacement.INPUT_HEIGHT;
        LostTalesChatOverlayRenderer.drawLeftEdgeSegment(
                barLeft - ChatWindowPlacement.FRAME_EDGE_WIDTH, barTop,
                barBottom, barBottom, (float)(frame.boxBottom - frame.boxTop)
                        + ChatWindowPlacement.FRAME_EDGE_WIDTH, 255);
    }

    /**
     * The bar's stretch of the window's right frame edge, just outside
     * the bar, on the same ramp as the window's own stretch above it: the
     * ramp runs down from the frame's top-right corner, so beside the bar
     * it is at its faintest, and gone on the frame's bottom row.
     */
    private static void drawBarRightEdge(ChatWindowFrame frame, int barRight,
                                         int barTop) {
        if (frame == null) {
            return;
        }
        float rampSpan = (float)(frame.boxBottom - frame.boxTop)
                + ChatWindowPlacement.FRAME_EDGE_WIDTH;
        float barBottom = barTop + ChatWindowPlacement.INPUT_HEIGHT;
        LostTalesChatOverlayRenderer.drawRightEdgeSegment(barRight, barTop,
                barBottom, barBottom - rampSpan, rampSpan, 255);
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
        boolean collapsed = ChatWindowLayout.isToolbarCollapsed();
        boolean hovered = isInsideToolbarToggle(mouseX, mouseY, barRight);
        int toggleLeft = toolbarToggleLeft(barRight);
        this.toolbarToggle.advance(collapsed, hovered);
        this.toolbarToggle.draw(toggleLeft,
                barControlTop() + (hovered ? 0 : 1),
                ChatPickerPanel.BUTTON_SIZE, ChatPickerPanel.BUTTON_SIZE,
                255);
        this.regions.add(toggleLeft, barControlTop(),
                toggleLeft + ChatPickerPanel.BUTTON_SIZE,
                barControlTop() + ChatPickerPanel.BUTTON_SIZE);
    }

    /** Every picker's button and open panel, in bar space. */
    void drawPickers(int barRight, double mouseX, double mouseY, int tipX,
                     int tipY) {
        for (ChatPickerPanel picker : this.pickers) {
            if (isPickerShown(picker)) {
                picker.draw(this.mc, this.regions, barRight, pickerAnchor(),
                        mouseX, mouseY, tipX, tipY);
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
        this.indicatorFade = LostTalesChatVisualStyle.hoverFade(
                this.indicatorFade, hovered, elapsed);
        // The marquee runs on the clock while the pointer rests on a cut
        // name and glides home once it leaves, as a tab's does.
        int overflow = fit.labelWidth - fit.labelRoom;
        if (hovered && overflow > 0 && fit.labelRoom > 0
                && LostTalesConfig.enableChatAnimations) {
            this.indicatorHoverSeconds += elapsed;
            this.indicatorMarquee = (float)ChatChannelTabBar.marqueeOffset(
                    this.indicatorHoverSeconds, overflow);
        } else {
            this.indicatorHoverSeconds = 0.0D;
            this.indicatorMarquee = ChatChannelTabBar.eased(
                    this.indicatorMarquee, 0.0F, elapsed);
        }
        drawIndicatorFrame(fit, this.indicatorFade, true);
        this.regions.add(fit.frameLeft, this.top,
                ChatInputLine.dividerAfter(fit.controlsRight),
                this.top + ChatWindowPlacement.INPUT_HEIGHT);
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
        ChatFramedButton.drawSurface(fit.frameLeft, frameTop, frameWidth,
                ChatFramedButton.HEIGHT, lit,
                Math.round(LostTalesChatVisualStyle.INSET_ALPHA
                        * LostTalesChatVisualStyle.chatOpacity(this.mc)));
        int textTop = barTextTop();
        if (fit.iconLeft >= 0) {
            // The icon's box stands in the frame's middle, the inset
            // above and below it, the name's capitals half a pixel
            // above the box's.
            LostTalesChatVisualStyle.beginContent();
            ChatChannelIcons.draw(this.mc, fit.channel, fit.iconLeft,
                    frameTop + ChatFramedButton.INSET, 255);
        }
        if (fit.labelRoom > 0) {
            drawIndicatorLabel(fit, textTop, lit, marquee);
        }
        ChatFramedButton.drawInk(fit.frameLeft, frameTop, frameWidth,
                ChatFramedButton.HEIGHT, lit, 255);
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
        int color = LostTalesChatVisualStyle.blend(
                ClientChatChannelState.displayColor(fit.channel),
                LostTalesChatVisualStyle.IVORY, lit);
        if (fit.labelRoom >= fit.labelWidth) {
            LostTalesChatVisualStyle.drawColored(this.font, text,
                    fit.labelLeft, textTop, color, 255);
            return;
        }
        if (!marquee) {
            // A resting bar's cut name simply ends where its room does.
            LostTalesChatVisualStyle.drawColored(this.font,
                    this.font.trimStringToWidth(text, fit.labelRoom),
                    fit.labelLeft, textTop, color, 255);
            return;
        }
        // A cut name thins out into the edge it is cut at, as far as
        // it has gone past it, the words themselves fading; the scissor
        // is in screen space, and the bar group is drawn shifted by its
        // fraction.
        double offset = ChatChannelTabBar.snapped(this.indicatorMarquee,
                ChatChannelTabBar.displayStep());
        float depth = LostTalesChatOverlayRenderer.sideFadeDepth(fit.labelRoom);
        LostTalesChatOverlayRenderer.drawFadingText(this.mc, this.font, text,
                fit.labelLeft, (float)-offset, textTop, color, 255,
                fit.labelLeft + this.fractionX,
                fit.labelLeft + fit.labelRoom + this.fractionX, Double.NaN,
                depth,
                LostTalesChatOverlayRenderer.sideFadeStrength(offset, depth),
                LostTalesChatOverlayRenderer.sideFadeStrength(
                        fit.labelWidth - offset - fit.labelRoom, depth));
    }

    /**
     * Left edge of the character button's frame on the selected tab's
     * bar: a bar gap past the channel indicator's frame.
     */
    int characterButtonLeft() {
        return characterButtonLeft(indicatorFit(this.right));
    }

    /** As above on the bar laid out in {@code fit}. */
    private static int characterButtonLeft(IndicatorFit fit) {
        return fit.frameRight + BAR_GAP;
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

    /**
     * Top of the bar's framed buttons: centred on the typing well, a
     * row inside it at either end.
     */
    private int framedButtonTop() {
        return wellTopFor(this.top)
                + (CONTENT_HEIGHT - ChatFramedButton.HEIGHT) / 2;
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
        // character button, a bar gap past the frame, where the tab
        // has one.
        int trailing = hasCharacterButton(channel)
                ? BAR_GAP + CHARACTER_BUTTON_SIZE : 0;
        int iconLeft = frameLeft + ChatFramedButton.WIDE_INSET;
        boolean icon = ChatChannelIcons.iconOf(channel) != null;
        int iconRight = icon ? iconLeft + ChatChannelIcons.SIZE : iconLeft;
        int gap = icon ? ChatChannelIcons.GAP : 0;
        String label = indicatorLabel(channel);
        int labelWidth = this.font.getStringWidth(label);
        int whole = gap + labelWidth;
        // The last glyph's width includes a column of spacing after it,
        // which is not ink.
        int wholeContentRight = iconRight + whole - 1;
        ChatInputLine wholeLine = lineAfter(wholeContentRight
                + ChatFramedButton.WIDE_INSET + trailing, barRight);
        int shown = icon ? indicatorShown(whole,
                wholeLine.fieldRight - wholeLine.fieldLeft) : whole;
        int contentRight = shown >= whole ? wholeContentRight
                : iconRight + shown;
        int frameRight = contentRight + ChatFramedButton.WIDE_INSET;
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
                && ChatHitBox.contains(mouseX, mouseY, characterButtonLeft(),
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
            this.characterFade = 0.0F;
            this.characterNanos = 0L;
            return;
        }
        boolean hovered = isInsideCharacterButton(mouseX, mouseY);
        long now = System.nanoTime();
        double elapsed = this.characterNanos == 0L ? 0.0D
                : (now - this.characterNanos) / 1.0E9D;
        this.characterNanos = now;
        this.characterFade = LostTalesChatVisualStyle.hoverFade(
                this.characterFade, hovered || menuOpen, elapsed);
        drawCharacterButton(tab, this.characterFade);
        this.regions.add(characterButtonLeft(), characterButtonTop(),
                characterButtonRight(),
                characterButtonTop() + CHARACTER_BUTTON_SIZE);
    }

    /**
     * Whether the bar of a window whose front tab is {@code tab} wears
     * the head button: on a roleplaying channel, where it chooses the
     * chat identity. An account channel always speaks as the account.
     */
    static boolean hasCharacterButton(ChatTab tab) {
        return ClientChatIdentities.speaksInCharacter(tab);
    }

    /**
     * The Narrator's mark where the head stands while the voice is
     * chosen, with the chat's shadow under it as a head has.
     */
    private void drawNarratorMark(float headX, float headY) {
        ChatInlineIcons.drawEmoji(this.mc, ChatHeadMarker.NARRATOR_MARK,
                headX + LostTalesChatVisualStyle.SHADOW_OFFSET,
                headY + LostTalesChatVisualStyle.SHADOW_OFFSET,
                CHARACTER_HEAD_SIZE, LostTalesChatVisualStyle.shadowAlpha(255), true);
        ChatInlineIcons.drawEmoji(this.mc, ChatHeadMarker.NARRATOR_MARK,
                headX, headY, CHARACTER_HEAD_SIZE, 255, false);
    }

    /** The chat identity's head, inside the shared head button. */
    private void drawCharacterButton(ChatTab tab, float lit) {
        int left = characterButtonLeft(indicatorFit(this.right, tab));
        int top = characterButtonTop();
        ChatFramedButton.drawSurface(left, top, CHARACTER_BUTTON_SIZE,
                CHARACTER_BUTTON_SIZE, lit,
                Math.round(LostTalesChatVisualStyle.INSET_ALPHA
                        * LostTalesChatVisualStyle.chatOpacity(this.mc)));
        ClientChatIdentities.Identity shown =
                ClientChatIdentities.effectiveFor(tab);
        UUID self = this.mc.thePlayer == null ? null
                : this.mc.thePlayer.getUniqueID();
        if (self != null) {
            float headX = left + ChatFramedButton.WIDE_INSET;
            float headY = top + ChatFramedButton.WIDE_INSET;
            boolean account = shown.account || shown.skinId.length() == 0;
            LostTalesChatVisualStyle.beginContent();
            if (ClientChatIdentities.isNarrating()) {
                drawNarratorMark(headX, headY);
            } else {
                drawButtonHeadShadow(self, account, shown.skinId, headX, headY);
                if (account) {
                    LostTalesCharacterHeadIconRenderer.drawAccountHead(
                            this.mc, self, headX, headY, CHARACTER_HEAD_SIZE,
                            1.0F, 1.0F);
                } else {
                    LostTalesCharacterHeadIconRenderer.drawSnapshotHead(
                            this.mc, self, shown.skinId, headX, headY,
                            CHARACTER_HEAD_SIZE, 1.0F, 1.0F);
                }
            }
        }
        ChatFramedButton.drawInk(left, top, CHARACTER_BUTTON_SIZE,
                CHARACTER_BUTTON_SIZE, lit, 255);
    }

    /**
     * The head's flat silhouette shadow, one pixel down-right at two
     * thirds opacity: the treatment every comparable head in the chat
     * carries.
     */
    private void drawButtonHeadShadow(UUID self, boolean account,
                                      String skinId, float x, float y) {
        LostTalesSilhouetteRenderState.begin(LostTalesChatVisualStyle.SHADOW);
        try {
            if (account) {
                LostTalesCharacterHeadIconRenderer.drawTintedAccountHeadBase(
                        this.mc, self,
                        x + LostTalesChatVisualStyle.SHADOW_OFFSET,
                        y + LostTalesChatVisualStyle.SHADOW_OFFSET,
                        CHARACTER_HEAD_SIZE, 1.0F, 1.0F, 1.0F,
                        LostTalesChatVisualStyle.SHADOW_OPACITY);
            } else {
                LostTalesCharacterHeadIconRenderer.drawTintedSnapshotHeadBase(
                        this.mc, self, skinId,
                        x + LostTalesChatVisualStyle.SHADOW_OFFSET,
                        y + LostTalesChatVisualStyle.SHADOW_OFFSET,
                        CHARACTER_HEAD_SIZE, 1.0F, 1.0F, 1.0F,
                        LostTalesChatVisualStyle.SHADOW_OPACITY);
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
        return ChatHitBox.contains(mouseX, mouseY, fit.frameLeft, frameTop,
                fit.frameRight - fit.frameLeft, ChatFramedButton.HEIGHT);
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
        return ChatHitBox.contains(mouseX, mouseY, buttonLeft, controlTop,
                ChatPickerPanel.BUTTON_SIZE, ChatPickerPanel.BUTTON_SIZE);
    }

    /**
     * The bar's dividers, each the hairline the tab row parts a window's
     * controls with, at the same height: one on either side of the
     * typing well — after the channel indicator and before the counter
     * — and one between the send button and the buttons that put
     * something into the message, in the gap the bar's slots already
     * leave, so sending reads as its own act rather than as one more
     * insert.
     */
    void drawDividers(int barRight) {
        drawDividers(inputLine(barRight), barRight);
    }

    private void drawDividers(ChatInputLine line, int barRight) {
        int insertRight = barSlotLeft(barRight, SEND_BUTTON_INDEX + 1)
                + ChatPickerPanel.BUTTON_SIZE;
        int sendLeft = barSlotLeft(barRight, SEND_BUTTON_INDEX);
        drawBarDivider(line.leftDividerX);
        drawBarDivider(line.rightDividerX);
        drawBarDivider((insertRight + sendLeft) / 2);
    }

    /** One of the bar's dividers, centred on its controls. */
    private void drawBarDivider(int x) {
        LostTalesChatVisualStyle.drawDivider(x,
                barControlTop() + (ChatPickerPanel.BUTTON_SIZE
                        - BAR_DIVIDER_HEIGHT) / 2,
                BAR_DIVIDER_HEIGHT, LostTalesChatVisualStyle.DIVIDER_ALPHA);
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
        long now = System.nanoTime();
        double elapsed = this.sendFadeNanos == 0L ? 0.0D
                : (now - this.sendFadeNanos) / 1.0E9D;
        this.sendFadeNanos = now;
        this.sendFade = LostTalesChatVisualStyle.hoverFade(this.sendFade,
                hovered, elapsed);
        drawSendGlyph(barRight, this.sendFade, hovered);
        this.regions.add(buttonLeft, controlTop,
                buttonLeft + ChatPickerPanel.BUTTON_SIZE,
                controlTop + ChatPickerPanel.BUTTON_SIZE);
    }

    /**
     * The send glyph in its square: the sheet's send artwork as drawn,
     * crossed to its lit artwork as far as {@code lit} and lifted a
     * pixel while {@code hovered}. Text and glyphs are always fully
     * opaque.
     */
    private void drawSendGlyph(int barRight, float lit, boolean hovered) {
        int x = sendButtonLeft(barRight) + (ChatPickerPanel.BUTTON_SIZE
                - ChatIconSheet.SEND.getWidth()) / 2;
        int y = barControlTop() + (ChatPickerPanel.BUTTON_SIZE
                - ChatIconSheet.SEND.getHeight()) / 2 - (hovered ? 1 : 0);
        ChatIconSheet.drawPairWithShadow(ChatIconSheet.SEND,
                ChatIconSheet.SEND_HOVER, lit, x, y, 255);
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
        int factor = ChatWindowFrame.displayScaleFactor();
        float scale = counterScale(factor);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(line.counterLeft, barTextTop()
                    + LostTalesChatVisualStyle.smallTextTopOffset(factor),
                    0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            LostTalesChatVisualStyle.drawColored(this.font, text,
                    0, 0, full ? COUNTER_FULL_RGB : COUNTER_RGB, 255);
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
    void updateInputBounds() {
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
