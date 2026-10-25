package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.client.window.WindowStyle;
import org.lwjgl.input.Keyboard;

/**
 * A short list to choose one of, every option a row of its own: the
 * races. The chosen row is lit, as a menu's chosen row is, and so is the
 * row under the pointer. A click chooses, and up and down walk it while
 * it holds the focus.
 */
public final class CreatorList extends CreatorControl {

    private final CreatorChoice choice;

    public CreatorList(CreatorContext context, CreatorChoice choice) {
        super(context);
        this.choice = choice;
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    @Override
    public int height() {
        int rowHeight = CreatorRows.height();
        return Math.max(rowHeight, this.choice.count() * rowHeight);
    }

    /**
     * Each option a row, its name in ivory, the chosen one and the one
     * under the pointer lit; with nothing to choose, why, in the aside
     * tone.
     */
    @Override
    public void draw(int mouseX, int mouseY) {
        int count = this.choice.count();
        if (count == 0) {
            CreatorRows.drawLine(this.context, this.choice.emptyLabel(),
                    this.x, this.y, this.x + this.width,
                    WindowStyle.asideRgb());
            return;
        }
        int rowHeight = CreatorRows.height();
        int selected = this.choice.index();
        int hovered = rowAt(mouseX, mouseY);
        for (int index = 0; index < count; index++) {
            int rowY = this.y + index * rowHeight;
            if (index == selected || index == hovered) {
                CreatorRows.light(this.context, rowY, rowY + rowHeight);
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
        int index = (mouseY - this.y) / CreatorRows.height();
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
