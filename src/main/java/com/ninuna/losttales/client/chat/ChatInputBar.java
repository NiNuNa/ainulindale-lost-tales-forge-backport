package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.BarLead;
import com.ninuna.losttales.client.window.IconFlipbook;
import com.ninuna.losttales.client.window.PointerRegions;
import com.ninuna.losttales.client.window.SubWindowKind;
import com.ninuna.losttales.client.window.TabRow;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowBar;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowOpening;
import com.ninuna.losttales.client.window.WindowPlacement;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WindowPage;
import com.ninuna.losttales.gui.style.LostTalesDisplayPixels;
import com.ninuna.losttales.gui.style.LostTalesUiCornerMark;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import com.ninuna.losttales.gui.style.LostTalesUiClip;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiRules;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.gui.style.LostTalesUiTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
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
 * transform shifted by the bar's fractional remainder, and by its own
 * entrance from below where the motion files give it one. The typing
 * line's offsets come from {@link ChatInputLine}.
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
     * is, so what is typed stands exactly as it will once it is
     * sent.
     */
    static final int WELL_HEIGHT = LostTalesChatOverlayRenderer.LINE_HEIGHT;
    /**
     * The most rows the typing well grows to as a long message wraps; the
     * bar grows up by a row for each, and past the last the field scrolls.
     */
    static final int MAX_FIELD_ROWS = 5;
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
     * Clear space inside the well and beside the counter's words. The
     * buttons keep a button's gap between them and an edge's gap from the
     * frame and the hairlines ({@link WindowStyle#BUTTON_GAP},
     * {@link WindowStyle#EDGE_GAP}). Measured in ink, like every other gap
     * the chat keeps: what the eye reads as the gap is the space between
     * the pixels that were actually drawn, not between the boxes they
     * were drawn in.
     */
    static final int BAR_GAP = 3;
    /**
     * The field is never narrower than this, however little room a
     * narrow window leaves between the tab button and the counter.
     */
    private static final int MIN_FIELD_WIDTH = 20;
    /**
     * Room the field keeps before the tab button gives up any of its
     * name for it: about a dozen letters.
     */
    static final int COMFORTABLE_FIELD_WIDTH = 64;
    /** The head the identity button holds. */
    private static final int CHARACTER_HEAD_SIZE =
            LostTalesChatOverlayRenderer.HEAD_SIZE;
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

    /** The live bar's tab and identity buttons: their light, marquee and beat. */
    private final BarLead lead = new BarLead();
    /**
     * The bar's own glyph buttons, each keeping its own beat: the send
     * glyph tips as it throws; the toolbar chevron rises with its fold.
     */
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
    /**
     * How far the bar reaches above {@link #top} as it grows for the rows
     * typed in it: its controls stay on its last row, where {@link #top}
     * places them, and the well, its dividers and its surface reach up.
     */
    private float growth;
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
            this.growth = 0.0F;
            return;
        }
        placeOn(frame);
    }

    /** Places the bar on the window's bar strip, as just drawn. */
    private void placeOn(ChatFrame frame) {
        double boxLeft = frame.barLeft();
        double barTop = frame.barRestTop();
        this.growth = (float)frame.barGrowth();
        this.left = (int)Math.floor(boxLeft);
        this.top = (int)Math.floor(barTop);
        this.fractionX = (float)(boxLeft - this.left);
        this.fractionY = (float)(barTop - this.top);
        this.right = this.left + (int)Math.round(
                frame.boxRight - frame.boxLeft);
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
        return this.top - growthWhole() + INPUT_ANCHOR_BELOW_BAR;
    }

    /** The bar's growth in whole pixels, rounded up: the rows are drawn that far up and lowered by the rest. */
    private int growthWhole() {
        return (int)Math.ceil(this.growth - 1.0E-4F);
    }

    /**
     * How far the active window's bar should reach above its resting top
     * for the rows its field shows: a row's height for each past the
     * first.
     */
    double growthWanted() {
        return (this.field.shownRows() - 1) * ChatInputField.ROW_HEIGHT;
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
        return ChatPickerPanel.slotLeft(barRight, index);
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
        return lineAfter(leadFit(barRight).right, barRight);
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

    /** How far below its place the bar stands now, while bars come up on their own; every window's alike. */
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
        int y = this.top - growthWhole() - 4 - WindowStyle.POPUP_LINE_HEIGHT
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
     * tab in front's secondary colour at two thirds, one flat stretch of it, so
     * the history stands between two bands of one tone — exactly
     * as wide as the window,
     * with holes cut in it for the tab and identity buttons' frames and
     * the typing well, and the empty field's hint in the well. The strip's
     * first row is the window's bottom rule, drawn with the window, so
     * the bar's own paint begins one row below it and never darkens the
     * rule.
     */
    void drawBar(int barRight) {
        BarLead.Fit fit = leadFit(barRight);
        ChatInputLine line = lineAfter(fit.right, barRight);
        drawBarSurface(activeFrame(), fit, line, barRight);
        drawHint(line, this.field.getText(),
                ClientChatChannelState.getSelected());
        // The rows stand in the well as far as it has grown, lowered by
        // the growth's fraction so they travel with it; a row still on
        // its way in stays hidden below the well.
        int whole = growthWhole();
        float lowered = whole - this.growth;
        int wellTop = wellTopFor(this.top);
        GL11.glPushMatrix();
        boolean clipped = false;
        try {
            GL11.glTranslatef(0.0F, lowered, 0.0F);
            clipped = LostTalesUiClip.beginLocal(this.mc, line.wellLeft,
                    wellTop - whole, line.wellRight,
                    wellTop + WELL_HEIGHT - lowered);
            this.field.drawTextBox();
        } finally {
            LostTalesUiClip.end(clipped);
            GL11.glPopMatrix();
        }
    }

    /**
     * The bar's surface with its holes, the well in them, and the
     * window's frame beside and under the bar, which is the bar's own:
     * its surface ring and the edges over it and its two bottom
     * corners, arriving with the bar.
     */
    private void drawBarSurface(ChatFrame frame, BarLead.Fit fit,
                                ChatInputLine line, int barRight) {
        int wellTop = wellTopFor(this.top);
        int wellBottom = wellTop + WELL_HEIGHT;
        boolean grown = this.growth > 0.0F;
        int barBottom = this.top + WindowPlacement.BAR_STRIP_HEIGHT;
        int frameTop = framedButtonTop();
        float opacity = fadedShare(WindowStyle.opacity(this.mc));
        int surface = LostTalesUiInk.argb(
                LostTalesUiTheme.secondaryRgb(),
                Math.round(WindowStyle.INSET_ALPHA * opacity));
        // Holes for the tab and identity buttons' frames and the typing
        // well; each frame's corner pixels are the bar's, outside the
        // frame's rounding.
        List<int[]> holes = new ArrayList<int[]>();
        holes.add(new int[] {fit.frameLeft, frameTop, fit.frameRight,
                frameTop + LostTalesUiFramedButton.HEIGHT, 1});
        holes.add(new int[] {fit.identityLeft, frameTop, fit.right,
                frameTop + BarLead.IDENTITY_SIZE, 1});
        // A grown well runs up through the bar's first rows, which the
        // surface above it fills instead.
        holes.add(new int[] {line.wellLeft, grown ? this.top + 1 : wellTop,
                line.wellRight, wellBottom, 0});
        WindowBar.fillWithHoles(this.left, this.top + 1, barRight, barBottom,
                holes, surface);
        if (grown) {
            // The rows the bar has grown by: its surface beside the well,
            // and over the well as far as the well's own clear rows.
            float grownTop = this.top + 1 - this.growth;
            LostTalesUiInk.fillRect(this.left, grownTop, line.wellLeft,
                    this.top + 1, surface);
            LostTalesUiInk.fillRect(line.wellRight, grownTop, barRight,
                    this.top + 1, surface);
            LostTalesUiInk.fillRect(line.wellLeft, grownTop, line.wellRight,
                    wellTop - this.growth, surface);
        }
        drawWell(line, wellTop - this.growth, wellBottom,
                WindowStyle.insetArgb(opacity));
        // The frame's surface beside and under the bar and its edges over
        // it, on the whole window's ramps: the window's box reaches as far
        // above the bar's foot here as it does on screen.
        WindowBar.drawFoot(this.left, this.top, this.growth, barRight,
                surface,
                frame == null ? 0.0F : (float)(frame.boxBottom - frame.boxTop),
                faded(255), frame == null || !frame.isFilledByPage());
    }

    /**
     * Another drawn window's bar: the same bar the active window wears,
     * at rest — its surface, its well holding the front tab's unsent
     * draft or the hint, that tab's tab and identity buttons, the
     * buttons unlit — so every window reads as a place to type, while
     * only the active window's bar is typed in. Nothing here answers
     * the pointer beyond the strip itself; a press on it moves the
     * input to this window. Drawn where the live bar's geometry would
     * put it, which is borrowed for the draw and given back. It fades in
     * with a window that is still appearing.
     */
    void drawRestingBar(ChatFrame frame, Window window) {
        ConversationPage tab = ConversationPage.from(ChatFrame.activeTab(window,
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

    private void drawRestingBar(ChatFrame frame, ConversationPage tab) {
        int liveLeft = this.left;
        int liveTop = this.top;
        int liveRight = this.right;
        float liveFractionX = this.fractionX;
        float liveFractionY = this.fractionY;
        float liveGrowth = this.growth;
        placeOn(frame);
        int barRight = this.right;
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(this.fractionX, this.fractionY + entranceOffset(),
                    0.0F);
            String draft = ClientChatChannelState.getDraft(tab);
            BarLead.Fit fit = leadFit(barRight, tab);
            ChatInputLine line = lineAfter(fit.right, barRight);
            drawBarSurface(frame, fit, line, barRight);
            drawHint(line, draft, tab);
            drawDraft(line, draft);
            BarLead.drawIdentityAtRest(fit, framedButtonTop(), faceFor(tab),
                    surfaceAlpha(), faded(255));
            BarLead.drawTabAtRest(this.mc, this.font, fit, framedButtonTop(),
                    barTextTop(), surfaceAlpha(), faded(255));
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
            this.regions.addWindow(this.left, this.top - growthWhole(),
                    barRight, this.top + WindowPlacement.BAR_STRIP_HEIGHT);
            this.left = liveLeft;
            this.top = liveTop;
            this.right = liveRight;
            this.fractionX = liveFractionX;
            this.fractionY = liveFractionY;
            this.growth = liveGrowth;
        }
    }

    /**
     * A resting bar's unsent draft, where the live field would show it
     * being typed, and drawn by a field of its own that never holds the
     * keys, so its emoji, shares, pings, links, markup and a command read
     * exactly as they do in the live field. It shows from its start, on
     * one row, its paragraphs run together.
     */
    private void drawDraft(ChatInputLine line, String draft) {
        if (draft.length() == 0 || this.restingField == null) {
            return;
        }
        this.restingField.xPosition = line.fieldLeft;
        this.restingField.yPosition = barTextTop();
        this.restingField.width = Math.max(MIN_FIELD_WIDTH,
                line.fieldRight - line.fieldLeft);
        String shown = ChatMessageValidator.oneLine(draft);
        if (!shown.equals(this.restingField.getText())) {
            this.restingField.setText(shown);
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
    private static void drawWell(ChatInputLine line, float top, int bottom,
                                 int argb) {
        if (line.wellRight > line.wellLeft) {
            LostTalesUiInk.fillRect(line.wellLeft, top, line.wellRight,
                    bottom, argb);
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
    private void drawHint(ChatInputLine line, String typed, ConversationPage tab) {
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
     * The bar's tab button: the channel in front's icon and name in its
     * colour, lit under the pointer, a cut name read whole by the marquee
     * ({@link BarLead}).
     */
    void drawIndicator(int barRight, double mouseX, double mouseY) {
        BarLead.Fit fit = leadFit(barRight);
        this.lead.advanceTab(fit, fit.onTab(mouseX, mouseY, framedButtonTop()));
        this.lead.drawTab(this.mc, this.font, fit, framedButtonTop(),
                barTextTop(), this.fractionX, surfaceAlpha(), faded(255));
        this.regions.add(fit.frameLeft, this.top,
                ChatInputLine.dividerAfter(fit.right),
                this.top + WindowPlacement.BAR_STRIP_HEIGHT);
    }

    /** Left edge of the identity button's frame on the selected tab's bar. */
    int characterButtonLeft() {
        return leadFit(this.right).identityLeft;
    }

    /** Top of the identity button's frame: the bar's framed buttons' top. */
    int characterButtonTop() {
        return framedButtonTop();
    }

    /** Top of the bar's framed buttons, the typing well centred on them. */
    private int framedButtonTop() {
        return contentTopFor(this.top);
    }

    /** The framed buttons' surface on this bar, at the share of it the bar shows. */
    private int surfaceAlpha() {
        return Math.round(WindowStyle.INSET_ALPHA
                * fadedShare(WindowStyle.opacity(this.mc)));
    }

    /** The bar's tab and identity buttons for the selected channel. */
    private BarLead.Fit leadFit(int barRight) {
        return leadFit(barRight, ClientChatChannelState.getSelected());
    }

    /**
     * The tab and identity buttons on the bar of a window whose front tab
     * is {@code channel}, an edge's gap in from the window's edge: the name
     * whole while the field beside them keeps
     * {@link #COMFORTABLE_FIELD_WIDTH}, else giving the field what it
     * lacks of that, down to the icon alone.
     */
    private BarLead.Fit leadFit(int barRight, ConversationPage channel) {
        WindowBar.Measure measure = WindowBar.measure(this.font);
        int left = this.left + WindowStyle.EDGE_GAP;
        int whole = BarLead.wholeWidth(channel, measure);
        ChatInputLine wholeLine = lineAfter(left + whole, barRight);
        return BarLead.fit(channel, left, leadWidth(whole,
                wholeLine.fieldRight - wholeLine.fieldLeft), measure);
    }

    /**
     * How wide the tab and identity buttons stand, {@code whole} wide with
     * the whole name, beside a field that would be {@code fieldWidth}
     * wide: all of it while the field keeps
     * {@link #COMFORTABLE_FIELD_WIDTH}, else what the field lacks of that
     * less, so each pixel it lacks moves the divider by one.
     */
    static int leadWidth(int whole, int fieldWidth) {
        int lack = COMFORTABLE_FIELD_WIDTH - fieldWidth;
        return lack <= 0 ? whole : whole - lack;
    }

    /**
     * Whether the point is on the identity button's frame: a framed
     * button answers on its frame and nowhere else.
     */
    boolean isInsideCharacterButton(double mouseX, double mouseY) {
        return leadFit(this.right).onIdentity(mouseX, mouseY,
                framedButtonTop());
    }

    /**
     * The identity button, lit under the pointer and while its menu is
     * out, holding who the player is on the channel in front.
     */
    void drawCharacterSelectionButton(double mouseX, double mouseY,
                                      boolean menuOpen) {
        BarLead.Fit fit = leadFit(this.right);
        boolean hovered = isInsideCharacterButton(mouseX, mouseY);
        this.lead.advanceIdentity(hovered, menuOpen,
                hovered && Mouse.isButtonDown(0));
        this.lead.drawIdentity(fit, framedButtonTop(),
                faceFor(ClientChatChannelState.getSelected()), surfaceAlpha(),
                faded(255));
        this.regions.add(fit.identityLeft, framedButtonTop(), fit.right,
                framedButtonTop() + BarLead.IDENTITY_SIZE);
    }

    /**
     * Who the player is on {@code tab}, as its identity button shows it:
     * the head of the identity the tab shows, wearing the sphere of the
     * status everyone else sees of it, over the chat's flat shadow like
     * every other head in the chat; or the Narrator's mark while that
     * voice is taken up on a tab that speaks in character. The head and
     * its sphere are one icon. Null with no player to show.
     */
    BarLead.Face faceFor(final WindowPage tab) {
        if (this.mc == null || this.mc.thePlayer == null) {
            return null;
        }
        final Minecraft minecraft = this.mc;
        final UUID self = minecraft.thePlayer.getUniqueID();
        final boolean narrating = ClientChatIdentities.narratesOn(tab);
        final ClientChatIdentities.Identity shown =
                ClientChatIdentities.effectiveFor(tab);
        return new BarLead.Face() {
            @Override
            public int width() {
                return narrating ? CHARACTER_HEAD_SIZE
                        : CHARACTER_HEAD_SIZE + LostTalesUiCornerMark.OVERHANG_X;
            }

            @Override
            public int height() {
                return narrating ? CHARACTER_HEAD_SIZE
                        : CHARACTER_HEAD_SIZE + LostTalesUiCornerMark.OVERHANG_Y;
            }

            @Override
            public void draw(float x, float y, int alpha) {
                if (narrating) {
                    drawNarratorMark(minecraft, x, y, alpha);
                    return;
                }
                boolean account = shown.account || shown.skinId.length() == 0;
                float share = alpha / 255.0F;
                ChatPresence presence = ClientChatPresence.presenceOf(self,
                        ClientChatPresence.speakerOf(tab));
                ChatPresenceMark.beginShadowCut(x, y, CHARACTER_HEAD_SIZE);
                try {
                    drawHeadShadow(minecraft, self, account, shown.skinId, x,
                            y, share);
                } finally {
                    ChatPresenceMark.endHeadCut();
                }
                ChatPresenceMark.beginHeadCut(x, y, CHARACTER_HEAD_SIZE);
                try {
                    if (account) {
                        LostTalesCharacterHeadIconRenderer.drawAccountHead(
                                minecraft, self, x, y, CHARACTER_HEAD_SIZE,
                                1.0F, share);
                    } else {
                        LostTalesCharacterHeadIconRenderer.drawSnapshotHead(
                                minecraft, self, shown.skinId, x, y,
                                CHARACTER_HEAD_SIZE, 1.0F, share);
                    }
                } finally {
                    ChatPresenceMark.endHeadCut();
                }
                ChatPresenceMark.draw(x, y, CHARACTER_HEAD_SIZE, presence,
                        alpha);
            }
        };
    }

    /**
     * The Narrator's mark where the head stands while the voice is
     * chosen, with the chat's shadow under it as a head has.
     */
    private static void drawNarratorMark(Minecraft minecraft, float x,
                                         float y, int alpha) {
        ChatInlineIcons.drawEmoji(minecraft, ChatHeadMarker.NARRATOR_MARK,
                x + LostTalesUiInk.SHADOW_OFFSET,
                y + LostTalesUiInk.SHADOW_OFFSET, CHARACTER_HEAD_SIZE,
                LostTalesUiInk.shadowAlpha(alpha), true);
        ChatInlineIcons.drawEmoji(minecraft, ChatHeadMarker.NARRATOR_MARK,
                x, y, CHARACTER_HEAD_SIZE, alpha, false);
    }

    /**
     * The head's flat silhouette shadow, one pixel down-right at two
     * thirds opacity: the treatment every comparable head in the chat
     * carries.
     */
    private static void drawHeadShadow(Minecraft minecraft, UUID self,
                                       boolean account, String skinId,
                                       float x, float y, float share) {
        LostTalesSilhouetteRenderState.begin(LostTalesUiInk.SHADOW);
        try {
            if (account) {
                LostTalesCharacterHeadIconRenderer.drawTintedAccountHeadBase(
                        minecraft, self, x + LostTalesUiInk.SHADOW_OFFSET,
                        y + LostTalesUiInk.SHADOW_OFFSET, CHARACTER_HEAD_SIZE,
                        1.0F, 1.0F, 1.0F,
                        LostTalesUiInk.SHADOW_OPACITY * share);
            } else {
                LostTalesCharacterHeadIconRenderer.drawTintedSnapshotHeadBase(
                        minecraft, self, skinId,
                        x + LostTalesUiInk.SHADOW_OFFSET,
                        y + LostTalesUiInk.SHADOW_OFFSET, CHARACTER_HEAD_SIZE,
                        1.0F, 1.0F, 1.0F,
                        LostTalesUiInk.SHADOW_OPACITY * share);
            }
        } finally {
            LostTalesSilhouetteRenderState.end();
        }
    }

    /** Whether the point is on the tab button's frame. */
    boolean isInsideIndicator(double mouseX, double mouseY, int barRight) {
        return leadFit(barRight).onTab(mouseX, mouseY, framedButtonTop());
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
        // Beside the well as far up as it has grown.
        float wellTop = wellTopFor(this.top) - this.growth;
        drawBarDivider(line.leftDividerX, wellTop, WELL_HEIGHT + this.growth);
        drawBarDivider(line.rightDividerX, wellTop, WELL_HEIGHT + this.growth);
        // One pixel in the gap between the inserts and the send button,
        // the odd pixel left, centred on the buttons.
        drawBarDivider(insertRight + LostTalesUiInk.centredStart(
                        sendLeft - insertRight, 1),
                barControlTop() + (ChatPickerPanel.BUTTON_SIZE
                        - BAR_DIVIDER_HEIGHT) / 2, BAR_DIVIDER_HEIGHT);
    }

    /** One of the bar's dividers, {@code height} rows from {@code top}. */
    private void drawBarDivider(int x, float top, float height) {
        LostTalesUiRules.drawVerticalRule(x, x + WindowStyle.DIVIDER_WIDTH,
                top, top + height, faded(WindowStyle.DIVIDER_ALPHA));
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

    /** {@code 37/1024} for a message, {@code 0/1024} for none yet; nothing for a command. */
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
        ChatFrame frame = activeFrame();
        // As many rows as the window leaves room for above the bar, and
        // the top one where the well has grown to.
        this.field.roomForRows(frame == null ? 1 : 1 + (int)Math.floor(
                frame.barGrowthLimit() / ChatInputField.ROW_HEIGHT + 1.0E-6D));
        this.field.xPosition = line.fieldLeft;
        this.field.yPosition = barTextTop() - growthWhole();
        this.field.width = Math.max(MIN_FIELD_WIDTH,
                line.fieldRight - line.fieldLeft);
    }
}
