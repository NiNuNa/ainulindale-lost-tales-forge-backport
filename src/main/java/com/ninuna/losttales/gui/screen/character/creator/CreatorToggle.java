package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Keyboard;

/**
 * A row that is on or off: its name at the left, a small switch at the
 * right; in a window's rows, as a switch's row in Settings, the word for
 * its state at the right end in place of the switch. A click anywhere on
 * the row, or space or enter while it holds the focus, flips it.
 */
public final class CreatorToggle extends CreatorControl {

    /** The flag the row edits, kept by the screen. */
    public interface BooleanValue {
        boolean get();
        void set(boolean value);
    }

    private static final int SWITCH_WIDTH = 22;
    private static final int SWITCH_HEIGHT = 10;
    private static final int KNOB = 8;

    private final String label;
    private final String onLabel;
    private final String offLabel;
    private final BooleanValue value;

    public CreatorToggle(CreatorContext context, String label,
                         String onLabel, String offLabel, BooleanValue value) {
        super(context);
        this.label = label;
        this.onLabel = onLabel;
        this.offLabel = offLabel;
        this.value = value;
    }

    @Override
    public int height() {
        return inRows() ? CreatorRows.height() : 18;
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (inRows()) {
            drawRow(mouseX, mouseY);
            return;
        }
        FontRenderer font = this.context.getFont();
        boolean on = this.value.get();
        boolean hovered = contains(mouseX, mouseY);
        int switchX = this.x + this.width - SWITCH_WIDTH;
        int switchY = this.y + (height() - SWITCH_HEIGHT) / 2;
        Gui.drawRect(switchX, switchY, switchX + SWITCH_WIDTH,
                switchY + SWITCH_HEIGHT, on
                        ? LostTalesSkyrimUiStyle.withAlpha(
                                LostTalesSkyrimUiStyle.GOLD, 0x70)
                        : LostTalesSkyrimUiStyle.withAlpha(
                                LostTalesSkyrimUiStyle.PLUM_BLACK, 0x90));
        CreatorWidgets.drawFrame(switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT,
                hovered || isFocused()
                        ? LostTalesSkyrimUiStyle.GOLD
                        : LostTalesSkyrimUiStyle.BORDER_DIM);
        int knobX = on ? switchX + SWITCH_WIDTH - 1 - KNOB : switchX + 1;
        Gui.drawRect(knobX, switchY + 1, knobX + KNOB, switchY + 1 + KNOB,
                on ? LostTalesSkyrimUiStyle.GOLD
                        : LostTalesSkyrimUiStyle.TEXT_MUTED);
        LostTalesSkyrimUiStyle.beginContent();
        String state = on ? this.onLabel : this.offLabel;
        int stateX = switchX - 4 - font.getStringWidth(state);
        font.drawStringWithShadow(state, stateX,
                this.y + (height() - font.FONT_HEIGHT) / 2 + 1,
                on ? LostTalesSkyrimUiStyle.GOLD : LostTalesSkyrimUiStyle.TEXT_MUTED);
        String text = LostTalesSkyrimUiStyle.trimToWidth(font, this.label,
                Math.max(20, stateX - this.x - 4));
        font.drawStringWithShadow(text, this.x,
                this.y + (height() - font.FONT_HEIGHT) / 2 + 1,
                hovered || isFocused()
                        ? LostTalesSkyrimUiStyle.TEXT_BRIGHT
                        : LostTalesSkyrimUiStyle.TEXT);
    }

    /**
     * As a window's row: lit under the pointer and while it holds the
     * keys, its name at the left in ivory, the word for its state at the
     * right end in the aside tone, ivory under the pointer.
     */
    private void drawRow(int mouseX, int mouseY) {
        boolean hovered = contains(mouseX, mouseY);
        if (hovered || isFocused()) {
            CreatorRows.light(this.context, this.y, this.y + height());
        }
        String state = this.value.get() ? this.onLabel : this.offLabel;
        int stateLeft = CreatorRows.drawValue(this.context, state, this.x,
                this.x + this.width, this.y, hovered);
        CreatorRows.drawLabel(this.context, this.label, this.x, this.y,
                stateLeft - MenuWindow.VALUE_GAP);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        this.value.set(!this.value.get());
        return true;
    }

    @Override
    public boolean isPointerOverAction(int mouseX, int mouseY) {
        return contains(mouseX, mouseY);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        // Enter is the screen's, to confirm from any page.
        if (keyCode == Keyboard.KEY_SPACE
                || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT) {
            this.value.set(!this.value.get());
            return true;
        }
        return false;
    }
}
