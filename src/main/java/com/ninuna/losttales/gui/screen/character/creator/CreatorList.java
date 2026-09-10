package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Keyboard;

/**
 * A short list to choose one of, every option on its own row: the races.
 * The chosen row wears the selection mark the journal's rows wear; a
 * click chooses, and up and down walk it while it holds the focus.
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

    @Override
    public int height() {
        return Math.max(ROW, this.choice.count() * ROW);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        FontRenderer font = this.context.getFont();
        int count = this.choice.count();
        if (count == 0) {
            LostTalesSkyrimUiStyle.beginContent();
            font.drawStringWithShadow(LostTalesSkyrimUiStyle.trimToWidth(font,
                    this.choice.emptyLabel(), this.width), this.x, this.y + 3,
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
            String text = LostTalesSkyrimUiStyle.trimToWidth(font,
                    this.choice.label(index), this.width - TEXT_INSET - 2);
            font.drawStringWithShadow(text, this.x + TEXT_INSET, rowY + 3,
                    isSelected ? (isFocused()
                            ? LostTalesSkyrimUiStyle.GOLD
                            : LostTalesSkyrimUiStyle.TEXT_BRIGHT)
                            : index == hovered
                            ? LostTalesSkyrimUiStyle.TEXT_BRIGHT
                            : LostTalesSkyrimUiStyle.TEXT);
        }
    }

    private int rowAt(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)) {
            return -1;
        }
        int index = (mouseY - this.y) / ROW;
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
