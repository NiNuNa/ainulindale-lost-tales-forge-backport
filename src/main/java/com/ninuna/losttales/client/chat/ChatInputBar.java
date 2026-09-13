package com.ninuna.losttales.client.chat;

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
 * included, so everything that belongs to the bar (the character
 * button, the channel indicator, the field and the well it lies in, the
 * dividers, the toolbar and its pickers, the send arrow, the counter,
 * the completion lists hung from it) follows its window exactly however
 * that window is dragged or resized. The screen draws it inside one
 * transform shifted by the bar's fractional remainder and its entrance
 * from below. The typing line's offsets come from {@link ChatInputLine}.
 */
final class ChatInputBar {
    /** Height vanilla gives the chat's field, kept for the one we draw. */
    static final int FIELD_HEIGHT = 12;
    /** The pickers and lists take the bar's top plus this as their floor. */
    private static final int INPUT_ANCHOR_BELOW_BAR = 14;
    /** The send arrow is the rightmost bar control. */
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
     * The bar's own left frame edge: drawn on the border, one pixel
     * wide, so the gap after it starts a pixel in. It is the bar's
     * stretch of the window's frame edge, so it has that edge's width.
     */
    private static final int BAR_BORDER_WIDTH =
            ChatTimestampColumn.BORDER_WIDTH;
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
    /** The head's inset inside the character button's square, and its size. */
    private static final int CHARACTER_HEAD_INSET = 2;
    private static final int CHARACTER_HEAD_SIZE =
            LostTalesChatOverlayRenderer.HEAD_SIZE;
    /** The indicator's label is drawn a pixel inside its box. */
    private static final int INDICATOR_TEXT_INSET = 1;
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

    /**
     * When the bar's entrance began: the screen opening, and again
     * whenever the bar arrives in another window.
     */
    private long entranceNanos = System.nanoTime();
    /**
     * The indicator's marquee, as a tab's: how long the pointer has
     * rested on it, how far its cut name is slid left, and when the
     * slide last moved.
     */
    private double indicatorHoverSeconds;
    private float indicatorMarquee;
    private long indicatorNanos;
    /**
     * The active window's bar this frame — the window holding the
     * selected channel — in whole pixels plus the fractional remainder
     * the bar group is drawn with, so it lands exactly on the window.
     */
    private int left = 2;
    private int top;
    private int right;
    private float fractionX;
    private float fractionY;
    /**
     * The window the bar is in, so its arrival in another one can be
     * told from a redraw of the one it is already in.
     */
    private String windowId;
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
            this.left = HudPlacementLayout.SCREEN_MARGIN;
            this.top = this.screenHeight - HudPlacementLayout.SCREEN_MARGIN
                    - ChatWindowPlacement.INPUT_HEIGHT;
            this.right = this.left + ChatWindowPlacement.windowWidth(this.mc);
            this.fractionX = 0.0F;
            this.fractionY = 0.0F;
            return;
        }
        double boxLeft = frame.boxLeft;
        double barTop = frame.barTop();
        this.left = (int)Math.floor(boxLeft);
        this.top = (int)Math.floor(barTop);
        this.fractionX = (float)(boxLeft - this.left);
        this.fractionY = (float)(barTop - this.top);
        this.right = this.left + (int)Math.round(
                frame.boxRight - frame.boxLeft);
    }

    /**
     * Notices the bar changing hands. It is not carried between windows:
     * it goes with the window it is leaving and arrives in the new one on
     * the same entrance from below it plays when the screen opens — the
     * bar alone, since the window it lands on is already there.
     */
    void noteInputWindow() {
        ChatWindowFrame frame = activeFrame();
        String current = frame == null ? null : frame.windowId;
        if (current != null && this.windowId != null
                && !current.equals(this.windowId)) {
            this.entranceNanos = System.nanoTime();
        }
        this.windowId = current;
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
     * Top of the bar's square controls, centred in the rows below the
     * bottom rule: the strip's first row is the rule, so the bar's own
     * furniture lives in the rows after it.
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
        return barTop + 1 + (ChatWindowPlacement.INPUT_HEIGHT - 1
                - ChatPickerPanel.BUTTON_SIZE) / 2;
    }

    /**
     * Top of the typing well on a bar strip starting at {@code barTop}:
     * one message row, centred in the rows below the rule like the
     * controls, which are as tall.
     */
    static int wellTopFor(int barTop) {
        return barTop + 1 + (ChatWindowPlacement.INPUT_HEIGHT - 1
                - LostTalesChatOverlayRenderer.LINE_HEIGHT) / 2;
    }

    /** {@link #barTextTop} for a bar strip starting at {@code barTop}. */
    static int textTopFor(int barTop) {
        return wellTopFor(barTop) + LostTalesChatOverlayRenderer.ROW_TEXT_TOP;
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
        return lineAfter(indicatorFit(barRight).inkRight, barRight);
    }

    /** The typing line after indicator ink ending at {@code inkRight}. */
    private ChatInputLine lineAfter(int inkRight, int barRight) {
        return ChatInputLine.between(inkRight, controlsLeft(barRight),
                counterSlotWidth(widestCounterInk(),
                        counterScale(ChatWindowFrame.displayScaleFactor())));
    }

    private int toolbarToggleLeft(int barRight) {
        return barSlotLeft(barRight, this.toolbarToggleIndex);
    }

    boolean isInsideToolbarToggle(double mouseX, double mouseY, int barRight) {
        int toggleLeft = toolbarToggleLeft(barRight);
        int controlTop = barControlTop();
        return mouseX >= toggleLeft
                && mouseX < toggleLeft + ChatPickerPanel.BUTTON_SIZE
                && mouseY >= controlTop
                && mouseY < controlTop + ChatPickerPanel.BUTTON_SIZE;
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
     * palette's plum black at the same half opacity as the message
     * backdrop — exactly as wide as the window, with a hole cut in it
     * for the typing well and the empty field's hint in the well. The
     * strip's first row is the window's bottom rule, drawn with the
     * window, so the bar's own paint begins one row below it and never
     * darkens the rule.
     */
    void drawBar(int barRight) {
        ChatInputLine line = inputLine(barRight);
        int wellTop = wellTopFor(this.top);
        int wellBottom = wellTop + LostTalesChatOverlayRenderer.LINE_HEIGHT;
        LostTalesChatVisualStyle.fillAround(this.left, this.top + 1,
                barRight, this.top + ChatWindowPlacement.INPUT_HEIGHT,
                line.wellLeft, wellTop, line.wellRight, wellBottom,
                LostTalesChatVisualStyle.SURFACE);
        drawWell(line, wellTop, wellBottom);
        // The window's frame edges beside and under the bar are the
        // bar's own: drawn over its fill, so nothing darkens them, and
        // arriving with the bar's fly-in.
        drawBarLeftEdge(activeFrame(), this.left, this.top);
        LostTalesChatOverlayRenderer.drawBarBottomEdge(this.left, barRight,
                this.top + ChatWindowPlacement.INPUT_HEIGHT - 1, 255);
        drawHint(line);
        this.field.drawTextBox();
    }

    /**
     * The typing well: the stretch between the two dividers, in a
     * surface of its own a step darker than the bar, the way the
     * timestamp column is a step darker than the panel. It fills the
     * hole the bar leaves for it rather than lying over the bar, so the
     * line being typed reads as a place of its own in one flat colour.
     * It is one message row, level with the bar's buttons, and lays out
     * everything the field draws — the text, the caret, the selection
     * wash and the previews — as a message row does.
     */
    private static void drawWell(ChatInputLine line, int top, int bottom) {
        if (line.wellRight > line.wellLeft) {
            Gui.drawRect(line.wellLeft, top, line.wellRight, bottom,
                    LostTalesChatVisualStyle.SURFACE_INSET);
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
    private void drawHint(ChatInputLine line) {
        if (this.field.getText().length() > 0) {
            return;
        }
        String key = ClientChatChannelState.canSend(
                ClientChatChannelState.getSelected())
                ? "gui.losttales.chat.input.hint"
                : "gui.losttales.chat.input.hint.read_only";
        int x = line.fieldLeft + ChatInputField.CARET_WIDTH + 1;
        String hint = LostTalesSkyrimUiStyle.trimToWidth(this.font,
                StatCollector.translateToLocal(key), line.fieldRight - x);
        LostTalesChatVisualStyle.drawColored(this.font, "§o" + hint, x,
                barTextTop(), LostTalesChatVisualStyle.asideRgb(), 255);
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
     * The channel indicator: the selected channel's icon and name, as its
     * tab shows them, the name in the channel's colour and ivory under
     * the pointer. Where a narrow window leaves it less room the name is
     * cut, and resting the pointer on it reads it whole with the tabs'
     * own marquee.
     */
    void drawIndicator(int barRight, double mouseX, double mouseY) {
        IndicatorFit fit = indicatorFit(barRight);
        boolean hovered = isInsideIndicator(mouseX, mouseY, barRight);
        long now = System.nanoTime();
        double elapsed = this.indicatorNanos == 0L ? 0.0D
                : (now - this.indicatorNanos) / 1.0E9D;
        this.indicatorNanos = now;
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
        int textTop = barTextTop();
        if (fit.iconLeft >= 0) {
            // The icon's box stands where a message row stands an emoji.
            LostTalesChatVisualStyle.beginContent();
            ChatChannelIcons.draw(this.mc, fit.channel, fit.iconLeft,
                    textTop + LostTalesChatOverlayRenderer.centredBoxTop(
                            ChatChannelIcons.SIZE), 255);
        }
        if (fit.labelRoom > 0) {
            drawIndicatorLabel(fit, textTop, hovered);
        }
        this.regions.add(indicatorLeft(), this.top,
                ChatInputLine.dividerAfter(fit.inkRight),
                this.top + ChatWindowPlacement.INPUT_HEIGHT);
    }

    /**
     * The indicator's name: whole in the room it keeps, or cut at the
     * room's end and slid by the marquee, its offset laid on a display
     * pixel so the glyphs stay on theirs. A cut name sinks into the edges
     * it is cut at the way a tab's does, in the bar's own tone, across
     * the rows of the line the bar's text stands on.
     */
    private void drawIndicatorLabel(IndicatorFit fit, int textTop,
                                    boolean hovered) {
        // A read-only channel reads italic rather than faint: text is
        // always at full opacity.
        String text = ClientChatChannelState.canSend(fit.channel)
                ? fit.label : "§o" + fit.label;
        int color = hovered ? LostTalesChatVisualStyle.IVORY
                : ClientChatChannelState.displayColor(fit.channel);
        if (fit.labelRoom >= fit.labelWidth) {
            LostTalesChatVisualStyle.drawColored(this.font, text,
                    fit.labelLeft, textTop, color, 255);
            return;
        }
        // The scissor is in screen space, and the bar group is drawn
        // shifted by its fraction.
        boolean clipped = LostTalesChatOverlayRenderer.beginClip(this.mc,
                fit.labelLeft + this.fractionX,
                fit.labelLeft + fit.labelRoom + this.fractionX,
                Double.NaN, Double.NaN, true);
        if (!clipped) {
            LostTalesChatVisualStyle.drawColored(this.font,
                    this.font.trimStringToWidth(text, fit.labelRoom),
                    fit.labelLeft, textTop, color, 255);
            return;
        }
        double offset = ChatChannelTabBar.snapped(this.indicatorMarquee,
                ChatChannelTabBar.displayStep());
        try {
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef((float)-offset, 0.0F, 0.0F);
                LostTalesChatVisualStyle.drawColored(this.font, text,
                        fit.labelLeft, textTop, color, 255);
            } finally {
                GL11.glPopMatrix();
            }
            float left = fit.labelLeft;
            float right = fit.labelLeft + fit.labelRoom;
            float depth = LostTalesChatOverlayRenderer.sideFadeDepth(
                    fit.labelRoom);
            int top = wellTopFor(this.top);
            int bottom = top + LostTalesChatOverlayRenderer.LINE_HEIGHT;
            LostTalesChatOverlayRenderer.drawSideFade(left, right, top,
                    bottom, depth, LostTalesChatVisualStyle.SURFACE_RGB,
                    ChatChannelTabBar.sideFadeAlpha(offset, depth, 255));
            LostTalesChatOverlayRenderer.drawSideFade(right, left, top,
                    bottom, depth, LostTalesChatVisualStyle.SURFACE_RGB,
                    ChatChannelTabBar.sideFadeAlpha(
                            fit.labelWidth - offset - fit.labelRoom, depth,
                            255));
        } finally {
            LostTalesChatOverlayRenderer.endVerticalClip(true);
        }
    }

    /** Left edge of the character-selection button, the bar's first control. */
    int characterButtonLeft() {
        return this.left + BAR_BORDER_WIDTH + BAR_GAP - CHARACTER_HEAD_INSET;
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
     * The channel indicator as it stands on the bar ending at
     * {@code barRight}: the channel's icon, then its name. While the
     * field beside it keeps {@link #COMFORTABLE_FIELD_WIDTH} the name
     * stands whole; a narrower window takes what the field lacks from
     * the name, then from the gap after the icon, down to the icon alone
     * — the way a tab gives its name up — and the name is cut at the
     * room it keeps. A channel without an icon keeps its name whole.
     */
    private IndicatorFit indicatorFit(int barRight) {
        ChatTab channel = ClientChatChannelState.getSelected();
        int iconLeft = indicatorLeft() + INDICATOR_TEXT_INSET;
        boolean icon = ChatChannelIcons.iconOf(channel) != null;
        int iconRight = icon ? iconLeft + ChatChannelIcons.SIZE : iconLeft;
        int gap = icon ? ChatChannelIcons.GAP : 0;
        String label = indicatorLabel(channel);
        int labelWidth = this.font.getStringWidth(label);
        int whole = gap + labelWidth;
        // The last glyph's width includes a column of spacing after it,
        // which is not ink.
        int wholeInkRight = iconRight + whole - 1;
        ChatInputLine wholeLine = lineAfter(wholeInkRight, barRight);
        int shown = icon ? indicatorShown(whole,
                wholeLine.fieldRight - wholeLine.fieldLeft) : whole;
        return new IndicatorFit(channel, label, icon ? iconLeft : -1,
                iconRight + gap, labelWidth, Math.max(0, shown - gap),
                shown >= whole ? wholeInkRight : iconRight + shown);
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
        /**
         * Just past the indicator's last pixel of ink. Its box is a
         * click target and carries padding on both sides; this is where
         * the drawing stops.
         */
        final int inkRight;

        IndicatorFit(ChatTab channel, String label, int iconLeft,
                     int labelLeft, int labelWidth, int labelRoom,
                     int inkRight) {
            this.channel = channel;
            this.label = label;
            this.iconLeft = iconLeft;
            this.labelLeft = labelLeft;
            this.labelWidth = labelWidth;
            this.labelRoom = labelRoom;
            this.inkRight = inkRight;
        }
    }

    boolean isInsideCharacterButton(double mouseX, double mouseY) {
        int buttonLeft = characterButtonLeft();
        int controlTop = barControlTop();
        return mouseX >= buttonLeft
                && mouseX < buttonLeft + ChatPickerPanel.BUTTON_SIZE
                && mouseY >= controlTop
                && mouseY < controlTop + ChatPickerPanel.BUTTON_SIZE;
    }

    /**
     * The character-selection button: the head of whoever the selected
     * tab would currently speak as, over the project's flat shadow like
     * every other head in the chat, lifted a pixel under the pointer
     * like the picker buttons, with the padlock in its corner while the
     * choice is locked. Returns whether the pointer is on it, for the
     * tip the screen offers.
     */
    boolean drawCharacterSelectionButton(double mouseX, double mouseY,
                                         boolean menuOpen) {
        int buttonLeft = characterButtonLeft();
        int controlTop = barControlTop();
        boolean hovered = isInsideCharacterButton(mouseX, mouseY);
        int lift = menuOpen || hovered ? 1 : 0;
        ClientChatAppearances.Appearance shown =
                ClientChatAppearances.effectiveFor(
                        ClientChatChannelState.getSelected());
        UUID self = this.mc.thePlayer == null ? null
                : this.mc.thePlayer.getUniqueID();
        if (self != null) {
            float headX = buttonLeft + CHARACTER_HEAD_INSET;
            // A pixel above the button's arithmetic centre: the head is
            // one row taller than the text caps beside it, so this is
            // what reads as level with them.
            float headY = controlTop + 1 - lift;
            boolean account = shown.account || shown.skinId.length() == 0;
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
        if (ClientChatAppearances.isLocked(
                ClientChatChannelState.getSelected())) {
            ChatLockAnimation.drawShut(
                    buttonLeft + ChatPickerPanel.BUTTON_SIZE
                            - ChatLockAnimation.SHUT_WIDTH,
                    controlTop + ChatPickerPanel.BUTTON_SIZE
                            - ChatLockAnimation.HEIGHT, 255);
        }
        this.regions.add(buttonLeft, controlTop,
                buttonLeft + ChatPickerPanel.BUTTON_SIZE,
                controlTop + ChatPickerPanel.BUTTON_SIZE);
        return hovered;
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
     * Whether the point is on the indicator's box: from a pixel before
     * its icon to the divider after it, so the divider parts the
     * indicator from the well for the pointer as it does for the eye.
     */
    boolean isInsideIndicator(double mouseX, double mouseY, int barRight) {
        return mouseX >= indicatorLeft()
                && mouseX < ChatInputLine.dividerAfter(
                        indicatorFit(barRight).inkRight)
                && mouseY >= this.top
                && mouseY < this.top + ChatWindowPlacement.INPUT_HEIGHT;
    }

    /** The channel as the indicator names it: its shown name, as its tab does. */
    static String indicatorLabel(ChatTab tab) {
        return ClientChatChannelState.displayName(tab);
    }

    /* ---- The dividers, the send arrow and the character counter ---- */

    private static int sendButtonLeft(int barRight) {
        return barSlotLeft(barRight, SEND_BUTTON_INDEX);
    }

    boolean isInsideSendButton(double mouseX, double mouseY, int barRight) {
        int buttonLeft = sendButtonLeft(barRight);
        int controlTop = barControlTop();
        return mouseX >= buttonLeft
                && mouseX < buttonLeft + ChatPickerPanel.BUTTON_SIZE
                && mouseY >= controlTop
                && mouseY < controlTop + ChatPickerPanel.BUTTON_SIZE;
    }

    /**
     * The bar's dividers, each the hairline the tab row parts a window's
     * controls with, at the same height: one on either side of the
     * typing well — after the channel indicator and before the counter
     * — and one between the send arrow and the buttons that put
     * something into the message, in the gap the bar's slots already
     * leave, so sending reads as its own act rather than as one more
     * insert.
     */
    void drawDividers(int barRight) {
        ChatInputLine line = inputLine(barRight);
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
     * The sheet's send arrow, centred in the button square at the bar's
     * right end and lifted a pixel while hovered; a flat mauve while
     * there is nothing to send. The sheet holds one arrow, so hovering
     * is told by the lift alone.
     */
    void drawSendButton(int barRight, double mouseX, double mouseY) {
        int buttonLeft = sendButtonLeft(barRight);
        int controlTop = barControlTop();
        boolean hovered = isInsideSendButton(mouseX, mouseY, barRight);
        boolean ready = this.field.getText().trim().length() > 0;
        int x = buttonLeft + (ChatPickerPanel.BUTTON_SIZE
                - ChatIconSheet.SEND.getWidth()) / 2;
        int y = controlTop + (ChatPickerPanel.BUTTON_SIZE
                - ChatIconSheet.SEND.getHeight()) / 2 - (hovered ? 1 : 0);
        // Text and glyphs are always fully opaque; the arrow says
        // what it can do with its colour, not by fading out.
        if (hovered || ready) {
            ChatIconSheet.SEND.drawWithShadow(x, y, 255);
        } else {
            ChatIconSheet.SEND.drawSilhouetteWithShadow(
                    LostTalesColors.rgb(LostTalesColors.MAUVE), x, y, 255);
        }
        this.regions.add(buttonLeft, controlTop,
                buttonLeft + ChatPickerPanel.BUTTON_SIZE,
                controlTop + ChatPickerPanel.BUTTON_SIZE);
    }

    /** {@code 37/256} for a message; nothing for a command. */
    private String counterText() {
        String text = this.field.getText();
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
        String text = counterText();
        if (text.length() == 0) {
            return;
        }
        boolean full = ChatInputRules.atMessageLimit(this.field.getText());
        // Its capitals centred on the full-size text's, and its shadow a
        // pixel of its own size away, as the timestamps' are.
        int factor = ChatWindowFrame.displayScaleFactor();
        float scale = counterScale(factor);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(inputLine(barRight).counterLeft, barTextTop()
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
