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
import org.lwjgl.opengl.GL11;

/**
 * The one input section of the chat screen: where it stands, what
 * stands on it, and the notice above it. Its home is the active
 * window's bar — the strip below that window's newest line, as wide as
 * the window — read from the frame the window pass drew, motion
 * included, so everything that belongs to the bar (the character
 * button, the channel indicator, the field, the toolbar and its
 * pickers, the send arrow, the counter, the completion lists hung from
 * it) follows its window exactly however that window is dragged or
 * resized. The screen draws it inside one transform shifted by the
 * bar's fractional remainder and its entrance from below.
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
    private static final float COUNTER_SCALE = 0.75F;
    private static final int COUNTER_RGB =
            LostTalesColors.rgb(LostTalesColors.SAND);
    private static final int COUNTER_FULL_RGB =
            LostTalesColors.rgb(LostTalesColors.SALMON);
    static final float NOTICE_LIFETIME_MILLIS = 1400.0F;
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
     * The y the pickers and completion lists take as their floor: they
     * were laid out against the screen bottom with the bar fourteen
     * pixels above it, so the bar's top plus fourteen keeps that shape
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
        return this.top + 1 + (ChatWindowPlacement.INPUT_HEIGHT - 1
                - ChatPickerPanel.BUTTON_SIZE) / 2;
    }

    /** The text row's top inside the bar, centred like the controls. */
    int barTextTop() {
        return this.top + 1
                + (ChatWindowPlacement.INPUT_HEIGHT - 1 - 8) / 2;
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
        Gui.drawRect(x, y, x + popupWidth, y + 13,
                (Math.min(220, alpha) << 24)
                        | LostTalesChatVisualStyle.SURFACE_RGB);
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
     * The active window's input bar in the palette's plum black at the
     * same half opacity as the message backdrop, exactly as wide as the
     * window. The strip's first row is the window's bottom rule, drawn
     * with the window, so the bar's own paint begins one row below it
     * and never darkens the rule.
     */
    void drawBar(int barRight) {
        Gui.drawRect(this.left, this.top + 1, barRight,
                this.top + ChatWindowPlacement.INPUT_HEIGHT,
                LostTalesSkyrimUiStyle.withAlpha(
                        LostTalesSkyrimUiStyle.PLUM_BLACK, 0x80));
        // The window's frame edges beside and under the bar are the
        // bar's own: drawn over its fill, so nothing darkens them, and
        // arriving with the bar's fly-in.
        drawBarLeftEdge(activeFrame(), this.left, this.top);
        LostTalesChatOverlayRenderer.drawBarBottomEdge(this.left, barRight,
                this.top + ChatWindowPlacement.INPUT_HEIGHT - 1, 255);
        this.field.drawTextBox();
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

    /** The {@code [Channel]} indicator, in the tab's colour, ivory under the pointer. */
    void drawIndicator(double mouseX, double mouseY) {
        ChatTab channel = ClientChatChannelState.getSelected();
        int width = indicatorWidth();
        int indicatorLeft = indicatorLeft();
        boolean hovered = isInsideIndicator(mouseX, mouseY);
        // A read-only channel reads italic rather than faint: text is
        // always at full opacity.
        String label = ClientChatChannelState.canSend(channel)
                ? indicatorLabel(channel) : "§o" + indicatorLabel(channel);
        int color = hovered ? LostTalesChatVisualStyle.IVORY
                : ClientChatChannelState.displayColor(channel);
        LostTalesChatVisualStyle.drawColored(this.font, label,
                indicatorLeft + 1, barTextTop(), color, 255);
        this.regions.add(indicatorLeft, this.top, indicatorLeft + width,
                this.top + ChatWindowPlacement.INPUT_HEIGHT);
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
     * Past the last pixel of the indicator's label. Its own width is a
     * click target and carries padding on both sides; this is where the
     * writing stops. The last glyph's width includes a column of
     * spacing after it, which is not ink.
     */
    private int indicatorInkRight() {
        return indicatorLeft() + INDICATOR_TEXT_INSET
                + this.font.getStringWidth(indicatorLabel(
                        ClientChatChannelState.getSelected())) - 1;
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
            float headX = buttonLeft + 2;
            // A pixel above the button's arithmetic centre: the head is
            // one row taller than the text caps beside it, so this is
            // what reads as level with them.
            float headY = controlTop + 1 - lift;
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

    boolean isInsideIndicator(double mouseX, double mouseY) {
        int indicatorLeft = indicatorLeft();
        return mouseX >= indicatorLeft
                && mouseX < indicatorLeft + indicatorWidth()
                && mouseY >= this.top
                && mouseY < this.top + ChatWindowPlacement.INPUT_HEIGHT;
    }

    private int indicatorWidth() {
        return this.font.getStringWidth(
                indicatorLabel(ClientChatChannelState.getSelected())) + 6;
    }

    static String indicatorLabel(ChatTab tab) {
        return "[" + ClientChatChannelState.displayName(tab) + "]";
    }

    /* ---- The send arrow and the character counter ---- */

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
     * The hairline between the send arrow and the buttons that put
     * something into the message: the same divider the tab row uses
     * between a window's controls, in the gap the bar's slots already
     * leave, so sending reads as its own act rather than as one more
     * insert.
     */
    void drawSendDivider(int barRight) {
        int insertRight = barSlotLeft(barRight, SEND_BUTTON_INDEX + 1)
                + ChatPickerPanel.BUTTON_SIZE;
        int sendLeft = barSlotLeft(barRight, SEND_BUTTON_INDEX);
        LostTalesChatVisualStyle.drawDivider(
                (insertRight + sendLeft) / 2,
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

    /** {@code 37/100} for a message; nothing for a command. */
    private String counterText() {
        String text = this.field.getText();
        if (ChatInputRules.isCommand(text)) {
            return "";
        }
        return ChatMessageValidator.visibleLength(text)
                + "/" + ChatMessageValidator.MAX_CHARACTERS;
    }

    /**
     * The counter's scale, snapped so every font pixel is a whole number
     * of display pixels: as close to the wanted three quarters as the
     * display can draw without mixed-size pixels — half at GUI scale 2,
     * two thirds at 3, three quarters at 4, and full size at 1, which
     * has no smaller whole step at all.
     */
    static float counterScale(int displayScaleFactor) {
        return Math.max(1, (int)Math.floor(COUNTER_SCALE * displayScaleFactor))
                / (float)displayScaleFactor;
    }

    private int counterLeft(int barRight) {
        String text = counterText();
        int width = text.length() == 0 ? 0
                : (int)Math.ceil(this.font.getStringWidth(text)
                        * counterScale(ChatWindowFrame.displayScaleFactor()))
                        + 3;
        return controlsLeft(barRight) - width;
    }

    /** The counter in the timestamp's sand, salmon once the limit is hit. */
    void drawCounter(int barRight) {
        String text = counterText();
        if (text.length() == 0) {
            return;
        }
        boolean full = ChatInputRules.atMessageLimit(this.field.getText());
        // A footnote beside the arrow, centred on the bar like the
        // full-size text was.
        float scale = counterScale(ChatWindowFrame.displayScaleFactor());
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(counterLeft(barRight), barTextTop() + 1,
                    0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            LostTalesChatVisualStyle.drawColored(this.font, text,
                    0, 0, full ? COUNTER_FULL_RGB : COUNTER_RGB, 255,
                    scale);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * Places the field on the bar: after the indicator, before the
     * counter. The field's position is the bar's whole-pixel part, and
     * the group it is drawn in is translated by the fractional
     * remainder. Both must come from the same reading of the window's
     * box: mixing one frame's whole pixels with the next frame's
     * fraction makes the text jump about while the window is dragged.
     */
    void updateInputBounds() {
        if (this.field == null) {
            return;
        }
        updateInputBox();
        int fieldLeft = indicatorInkRight() + BAR_GAP;
        // The counter sits left of the send arrow; the field ends before it.
        int fieldRight = counterLeft(inputBarRight()) - 3;
        this.field.xPosition = fieldLeft;
        this.field.yPosition = barTextTop();
        this.field.width = Math.max(20, fieldRight - fieldLeft);
    }
}
