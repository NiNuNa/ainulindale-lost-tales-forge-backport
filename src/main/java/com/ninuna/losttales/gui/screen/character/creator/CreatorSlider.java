package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiTextField;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/**
 * The age slider: a bent track from one year to the oldest the slider
 * reaches, a readout with the exact number beside it, and a way to type
 * an age the track cannot land on.
 *
 * <p>Dragging the thumb, or clicking the track, sets the age at that
 * point; the wheel over the row and the left and right keys while it holds
 * the focus nudge it a year, or ten with shift held. Clicking the readout
 * opens it for typing, and enter or clicking away closes it again — a
 * typed number past the slider's reach is kept as typed, up to what the
 * server accepts.</p>
 *
 * <p>In a window's rows it is one row: its name at the left, the track a
 * hairline after it with an ivory thumb, and the number at the right end,
 * which a click opens for typing in its place.</p>
 */
public final class CreatorSlider extends CreatorControl {

    /** The number the row edits, kept by the screen. */
    public interface IntValue {
        int get();
        void set(int value);
        /** The most a typed value may be; the track never reaches it. */
        int typedMax();
    }

    private static final int TRACK_HEIGHT = 4;
    private static final int THUMB_WIDTH = 5;
    private static final int THUMB_HEIGHT = 12;
    private static final int READOUT_WIDTH = 40;
    private static final int READOUT_GAP = 6;
    private static final int TICKS = 6;
    /** In a window's rows, the thumb's width; it stands the capitals' height. */
    private static final int ROW_THUMB_WIDTH = 3;
    /** In a window's rows, how far the knee's mark reaches above and under the track. */
    private static final int ROW_KNEE_REACH = 1;

    private final String label;
    private final IntValue value;
    private final String endLabel;
    private final LostTalesUiTextField typing;
    private boolean dragging;
    private boolean typingOpen;

    public CreatorSlider(CreatorContext context, String label,
                         IntValue value, String endLabel) {
        super(context);
        this.label = label;
        this.value = value;
        this.endLabel = endLabel;
        this.typing = new LostTalesUiTextField(context.getFont(), 0, 0,
                READOUT_WIDTH - 6, CreatorWidgets.FIELD_HEIGHT - 4);
        this.typing.setMaxStringLength(6);
    }

    @Override
    public int height() {
        return inRows() ? CreatorRows.height()
                : LABEL_HEIGHT + CreatorWidgets.FIELD_HEIGHT + 6;
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            closeTyping(true);
        }
    }

    @Override
    public void tick() {
        this.typing.updateCursorCounter();
    }

    private int readoutX() {
        return this.x + this.width - readoutWidth();
    }

    /** The readout's room; in a window's rows the widest number it may read, with the caret's room. */
    private int readoutWidth() {
        if (!inRows()) {
            return READOUT_WIDTH;
        }
        return this.context.getFont().getStringWidth(String.valueOf(
                Math.max(this.value.typedMax(), Math.max(1, this.value.get()))))
                + LostTalesUiCaret.WIDTH;
    }

    private int trackLeft() {
        return inRows() ? rowTrackStart() + ROW_THUMB_WIDTH / 2
                : this.x + THUMB_WIDTH / 2;
    }

    private int trackRight() {
        return inRows() ? rowTrackEnd() - ROW_THUMB_WIDTH / 2 - 1
                : readoutX() - READOUT_GAP - THUMB_WIDTH / 2;
    }

    /** In a window's rows, where the track's room starts: past the name. */
    private int rowTrackStart() {
        return Math.min(this.x + this.width, this.x
                + this.context.getFont().getStringWidth(this.label)
                + MenuWindow.VALUE_GAP);
    }

    /** In a window's rows, where the track's room ends: short of the number. */
    private int rowTrackEnd() {
        return readoutX() - MenuWindow.VALUE_GAP;
    }

    private int trackY() {
        return valueTop() + (CreatorWidgets.FIELD_HEIGHT - TRACK_HEIGHT) / 2;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (inRows()) {
            drawRow(mouseX, mouseY);
            return;
        }
        FontRenderer font = this.context.getFont();
        drawLabel(this.label);
        int left = trackLeft();
        int right = trackRight();
        int trackY = trackY();
        int age = AgeSliderScale.clamp(this.value.get());
        float position = AgeSliderScale.positionOf(age);
        int thumbX = left + Math.round((right - left) * position);

        // The track, filled up to the thumb.
        Gui.drawRect(left, trackY, right, trackY + TRACK_HEIGHT,
                LostTalesSkyrimUiStyle.withAlpha(
                        LostTalesSkyrimUiStyle.PLUM_BLACK, 0xA0));
        Gui.drawRect(left, trackY, thumbX, trackY + TRACK_HEIGHT,
                LostTalesSkyrimUiStyle.withAlpha(
                        LostTalesSkyrimUiStyle.GOLD, 0x90));
        CreatorWidgets.drawFrame(left - 1, trackY - 1, right - left + 2,
                TRACK_HEIGHT + 2, LostTalesSkyrimUiStyle.BORDER_DIM);
        // Ticks below the track, with the knee marked where the track bends.
        for (int tick = 0; tick <= TICKS; tick++) {
            int tickX = left + (right - left) * tick / TICKS;
            Gui.drawRect(tickX, trackY + TRACK_HEIGHT + 2, tickX + 1,
                    trackY + TRACK_HEIGHT + 4, LostTalesSkyrimUiStyle.BORDER_DIM);
        }
        int kneeX = left + Math.round((right - left) * AgeSliderScale.KNEE_POSITION);
        Gui.drawRect(kneeX, trackY + TRACK_HEIGHT + 2, kneeX + 1,
                trackY + TRACK_HEIGHT + 5, LostTalesSkyrimUiStyle.GOLD_DARK);
        // The thumb.
        boolean overThumb = this.dragging || CreatorWidgets.within(mouseX, mouseY,
                thumbX - THUMB_WIDTH / 2 - 1, valueTop(), THUMB_WIDTH + 2,
                CreatorWidgets.FIELD_HEIGHT);
        int thumbTop = trackY + TRACK_HEIGHT / 2 - THUMB_HEIGHT / 2;
        Gui.drawRect(thumbX - THUMB_WIDTH / 2, thumbTop,
                thumbX - THUMB_WIDTH / 2 + THUMB_WIDTH, thumbTop + THUMB_HEIGHT,
                overThumb || isFocused()
                        ? LostTalesSkyrimUiStyle.GOLD
                        : LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        Gui.drawRect(thumbX - THUMB_WIDTH / 2 + 1, thumbTop + 1,
                thumbX - THUMB_WIDTH / 2 + THUMB_WIDTH - 1,
                thumbTop + THUMB_HEIGHT - 1,
                LostTalesSkyrimUiStyle.withAlpha(
                        LostTalesSkyrimUiStyle.PLUM_BLACK, 0x60));
        LostTalesSkyrimUiStyle.beginContent();
        // The far end of the track reads as "and older".
        String end = LostTalesSkyrimUiStyle.trimToWidth(font, this.endLabel, 30);
        font.drawStringWithShadow(end, right - font.getStringWidth(end) + 2,
                trackY + TRACK_HEIGHT + 6, LostTalesSkyrimUiStyle.TEXT_DIM);

        // The readout, or the field standing in for it.
        int readoutX = readoutX();
        CreatorWidgets.drawFieldBox(readoutX, valueTop(), READOUT_WIDTH,
                CreatorWidgets.FIELD_HEIGHT, this.typingOpen);
        if (this.typingOpen) {
            this.typing.xPosition = readoutX + 4;
            this.typing.yPosition = valueTop() + (CreatorWidgets.FIELD_HEIGHT
                    - font.FONT_HEIGHT) / 2 + 1;
            this.typing.drawTextBox();
        } else {
            String number = String.valueOf(Math.max(1, this.value.get()));
            font.drawStringWithShadow(number,
                    readoutX + (READOUT_WIDTH - font.getStringWidth(number)) / 2,
                    valueTop() + (CreatorWidgets.FIELD_HEIGHT - font.FONT_HEIGHT) / 2 + 1,
                    LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        }
    }

    /**
     * As a window's row: lit under the pointer, while it holds the keys
     * and while the thumb is dragged; the name at the left in ivory; the
     * track a hairline in the aside tone, honey up to the thumb, the knee
     * marked in sand, and the thumb in ivory the capitals' height; the
     * number at the right end in the aside tone, ivory under the pointer,
     * or the field standing in for it.
     */
    private void drawRow(int mouseX, int mouseY) {
        FontRenderer font = this.context.getFont();
        int alpha = this.context.alpha();
        if (contains(mouseX, mouseY) || isFocused() || this.dragging) {
            CreatorRows.light(this.context, this.y, this.y + height());
        }
        CreatorRows.drawLabel(this.context, this.label, this.x, this.y,
                this.x + this.width);
        int textTop = CreatorRows.textTop(this.y);
        int left = trackLeft();
        int right = trackRight();
        if (right > left) {
            int lineY = textTop + LostTalesUiInk.CAP_HEIGHT / 2;
            int thumbX = left + Math.round((right - left)
                    * AgeSliderScale.positionOf(AgeSliderScale.clamp(
                            this.value.get())));
            LostTalesUiInk.fillRect(thumbX, lineY, right + 1, lineY + 1,
                    LostTalesUiInk.argb(WindowStyle.asideRgb(), alpha));
            LostTalesUiInk.fillRect(left, lineY, thumbX, lineY + 1,
                    LostTalesUiInk.argb(LostTalesColors.rgb(
                            LostTalesColors.HONEY), alpha));
            int kneeX = left + Math.round((right - left)
                    * AgeSliderScale.KNEE_POSITION);
            LostTalesUiInk.fillRect(kneeX, lineY - ROW_KNEE_REACH, kneeX + 1,
                    lineY + 1 + ROW_KNEE_REACH, LostTalesUiInk.argb(
                            LostTalesColors.rgb(LostTalesColors.SAND), alpha));
            int thumbLeft = thumbX - ROW_THUMB_WIDTH / 2;
            LostTalesUiInk.fillRect(thumbLeft, textTop,
                    thumbLeft + ROW_THUMB_WIDTH,
                    textTop + LostTalesUiInk.CAP_HEIGHT,
                    LostTalesUiInk.argb(LostTalesUiInk.IVORY, alpha));
        }
        if (this.typingOpen) {
            CreatorRows.placeField(this.typing, font, readoutX(),
                    this.x + this.width, this.y);
            this.typing.drawTextBox(alpha);
        } else {
            CreatorRows.drawValue(this.context,
                    String.valueOf(Math.max(1, this.value.get())), readoutX(),
                    this.x + this.width, this.y,
                    isOverReadout(mouseX, mouseY));
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        if (isOverReadout(mouseX, mouseY)) {
            openTyping();
            return true;
        }
        closeTyping(true);
        if (isOverTrack(mouseX, mouseY)) {
            this.dragging = true;
            setFromPointer(mouseX);
        }
        return true;
    }

    private boolean isOverReadout(int mouseX, int mouseY) {
        if (inRows()) {
            return CreatorWidgets.within(mouseX, mouseY, readoutX(), this.y,
                    readoutWidth(), height());
        }
        return CreatorWidgets.within(mouseX, mouseY, readoutX(), valueTop(),
                READOUT_WIDTH, CreatorWidgets.FIELD_HEIGHT);
    }

    /**
     * The band left of the readout, where a click or a drag sets the age;
     * in a window's rows, the track's room between the name and the number.
     */
    private boolean isOverTrack(int mouseX, int mouseY) {
        if (inRows()) {
            return contains(mouseX, mouseY) && mouseX >= rowTrackStart()
                    && mouseX < rowTrackEnd();
        }
        return contains(mouseX, mouseY)
                && mouseY >= valueTop()
                && mouseY < valueTop() + CreatorWidgets.FIELD_HEIGHT
                && mouseX < readoutX();
    }

    /**
     * The whole track acts, not only the thumb that lights. The readout is
     * a field to type in, and counts no more than any other text field.
     */
    @Override
    public boolean isPointerOverAction(int mouseX, int mouseY) {
        return isOverTrack(mouseX, mouseY);
    }

    @Override
    public void mouseDragged(int mouseX, int mouseY) {
        if (this.dragging) {
            setFromPointer(mouseX);
        }
    }

    @Override
    public void mouseReleased() {
        this.dragging = false;
    }

    @Override
    public boolean mouseWheel(int notches) {
        if (notches == 0) {
            return false;
        }
        closeTyping(true);
        nudge(notches > 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (this.typingOpen) {
            if (keyCode == Keyboard.KEY_RETURN
                    || keyCode == Keyboard.KEY_NUMPADENTER
                    || keyCode == Keyboard.KEY_ESCAPE) {
                closeTyping(keyCode != Keyboard.KEY_ESCAPE);
                return true;
            }
            if (keyCode == Keyboard.KEY_TAB) {
                // Keeps what was typed and lets the screen move the focus on.
                closeTyping(true);
                return false;
            }
            if (Character.isDigit(typedChar) || keyCode == Keyboard.KEY_BACK
                    || keyCode == Keyboard.KEY_DELETE
                    || keyCode == Keyboard.KEY_LEFT
                    || keyCode == Keyboard.KEY_RIGHT
                    || keyCode == Keyboard.KEY_HOME
                    || keyCode == Keyboard.KEY_END) {
                this.typing.textboxKeyTyped(typedChar, keyCode);
            }
            // Every other key is the field's while it is open; none leaks
            // out to turn the figure or change the page.
            return true;
        }
        if (keyCode == Keyboard.KEY_LEFT) {
            nudge(-1);
            return true;
        }
        if (keyCode == Keyboard.KEY_RIGHT) {
            nudge(1);
            return true;
        }
        // A digit starts typing an exact age; Enter stays the screen's,
        // to confirm from any page.
        if (Character.isDigit(typedChar)) {
            openTyping();
            this.typing.setText(String.valueOf(typedChar));
            this.typing.setCursorPositionEnd();
            return true;
        }
        return false;
    }

    private void nudge(int direction) {
        this.value.set(AgeSliderScale.nudge(this.value.get(), direction,
                GuiScreen.isShiftKeyDown()));
    }

    private void setFromPointer(int mouseX) {
        int left = trackLeft();
        int right = trackRight();
        if (right <= left) {
            return;
        }
        float position = (mouseX - left) / (float)(right - left);
        this.value.set(AgeSliderScale.ageAt(position));
    }

    private void openTyping() {
        if (this.typingOpen) {
            return;
        }
        this.typingOpen = true;
        this.typing.setText(String.valueOf(Math.max(1, this.value.get())));
        this.typing.setFocused(true);
        this.typing.setCursorPositionEnd();
    }

    /** Closes the typed entry, keeping what was typed when asked to. */
    private void closeTyping(boolean keep) {
        if (!this.typingOpen) {
            return;
        }
        this.typingOpen = false;
        this.typing.setFocused(false);
        if (!keep) {
            return;
        }
        String text = this.typing.getText().trim();
        if (text.length() == 0) {
            return;
        }
        try {
            long typed = Long.parseLong(text);
            int max = Math.max(AgeSliderScale.MIN, this.value.typedMax());
            this.value.set((int)Math.max(AgeSliderScale.MIN,
                    Math.min(max, typed)));
        } catch (NumberFormatException notANumber) {
            // The field only ever takes digits; a value too long for a
            // long is simply not kept.
        }
    }
}
