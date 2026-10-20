package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiCutName;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Keyboard;

/**
 * A short list to choose one of, every option on its own row: the races.
 * The chosen row wears the selection mark the journal's rows wear; in a
 * window's rows it is lit, as a menu's chosen row is, and so is the row
 * under the pointer. A click chooses, and up and down walk it while it
 * holds the focus.
 */
public final class CreatorList extends CreatorControl {

    private static final int ROW = 14;
    private static final int TEXT_INSET = 16;

    private final CreatorChoice choice;

    public CreatorList(CreatorContext context, CreatorChoice choice) {
        super(context);
        this.choice = choice;
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    /** From one option's row to the next. */
    private int rowPitch() {
        return inRows() ? CreatorRows.height() : ROW;
    }

    @Override
    public int height() {
        return Math.max(rowPitch(), this.choice.count() * rowPitch());
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (inRows()) {
            drawRows(mouseX, mouseY);
            return;
        }
        FontRenderer font = this.context.getFont();
        int count = this.choice.count();
        if (count == 0) {
            LostTalesSkyrimUiStyle.beginContent();
            LostTalesUiCutName.drawShadowed(font, this.choice.emptyLabel(),
                    this.x, this.y + 3, this.width,
                    LostTalesSkyrimUiStyle.TEXT_MUTED);
            return;
        }
        int selected = this.choice.index();
        int hovered = rowAt(mouseX, mouseY);
        for (int index = 0; index < count; index++) {
            int rowY = this.y + index * ROW;
            boolean isSelected = index == selected;
            LostTalesSkyrimUiStyle.drawSelectionRow(this.x, rowY, this.width,
                    ROW, isSelected, index == hovered);
            LostTalesSkyrimUiStyle.beginContent();
            // A name too long for its row fades into the row's end and
            // slides along under the pointer (LOOK-37).
            LostTalesUiCutName.drawShadowed(font, this.choice.label(index),
                    this.x + TEXT_INSET, rowY + 3,
                    this.width - TEXT_INSET - 2,
                    isSelected ? (isFocused()
                            ? LostTalesSkyrimUiStyle.GOLD
                            : LostTalesSkyrimUiStyle.TEXT_BRIGHT)
                            : index == hovered
                            ? LostTalesSkyrimUiStyle.TEXT_BRIGHT
                            : LostTalesSkyrimUiStyle.TEXT,
                    this, Integer.valueOf(index), index == hovered);
        }
    }

    /**
     * As a window's rows: each option a row, its name in ivory, the chosen
     * one and the one under the pointer lit; with nothing to choose, why,
     * in the aside tone.
     */
    private void drawRows(int mouseX, int mouseY) {
        int count = this.choice.count();
        if (count == 0) {
            CreatorRows.drawLine(this.context, this.choice.emptyLabel(),
                    this.x, this.y, this.x + this.width,
                    WindowStyle.asideRgb());
            return;
        }
        int selected = this.choice.index();
        int hovered = rowAt(mouseX, mouseY);
        for (int index = 0; index < count; index++) {
            int rowY = this.y + index * rowPitch();
            if (index == selected || index == hovered) {
                CreatorRows.light(this.context, rowY, rowY + rowPitch());
            }
            CreatorRows.drawLabel(this.context, this.choice.label(index),
                    this.x, rowY, this.x + this.width, this,
                    Integer.valueOf(index), index == hovered);
        }
    }

    private int rowAt(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)) {
            return -1;
        }
        int index = (mouseY - this.y) / rowPitch();
        return index >= 0 && index < this.choice.count() ? index : -1;
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        int index = rowAt(mouseX, mouseY);
        if (index >= 0) {
            this.choice.choose(index);
        }
        return true;
    }

    @Override
    public boolean isPointerOverAction(int mouseX, int mouseY) {
        return rowAt(mouseX, mouseY) >= 0;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        int count = this.choice.count();
        if (count == 0) {
            return false;
        }
        int current = Math.max(0, this.choice.index());
        if (keyCode == Keyboard.KEY_UP && current > 0) {
            this.choice.choose(current - 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_DOWN && current < count - 1) {
            this.choice.choose(current + 1);
            return true;
        }
        return keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN;
    }
}
