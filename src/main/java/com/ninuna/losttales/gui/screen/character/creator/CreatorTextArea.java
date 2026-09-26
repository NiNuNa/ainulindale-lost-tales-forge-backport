package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiTextArea;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/**
 * A row with several lines of text to type, for a profile's longer texts:
 * its name above, a box of lines below that wraps the words and scrolls
 * by whole lines, and the counter at the row's corner. Shift+Return
 * starts a paragraph; Return itself is left to the screen, as a line's
 * field leaves it, so it saves the form from any row.
 */
public final class CreatorTextArea extends CreatorControl {

    private final String label;
    private final LostTalesUiTextArea area;
    private final int limit;
    private final int lines;

    public CreatorTextArea(CreatorContext context, String label, String text,
                           int limit, int lines) {
        super(context);
        this.label = label;
        this.limit = limit;
        this.lines = Math.max(1, lines);
        this.area = new LostTalesUiTextArea(context.getFont(), limit);
        this.area.setText(text);
    }

    public String getText() {
        return this.area.getText();
    }

    private int boxHeight() {
        return this.lines * LostTalesUiTextArea.LINE_HEIGHT + 6;
    }

    @Override
    public int height() {
        return LABEL_HEIGHT + boxHeight() + 6;
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        this.area.setFocused(focused);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        FontRenderer font = this.context.getFont();
        drawLabel(this.label);
        CreatorWidgets.drawFieldBox(this.x, valueTop(), this.width,
                boxHeight(), isFocused());
        this.area.place(this.x + 4, valueTop() + 4, this.width - 8,
                this.lines * LostTalesUiTextArea.LINE_HEIGHT);
        this.area.draw(null, 255);
        String counter = this.area.getText().length() + "/" + this.limit;
        font.drawStringWithShadow(counter,
                this.x + this.width - font.getStringWidth(counter),
                this.y + 1, LostTalesSkyrimUiStyle.TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        this.area.mouseClicked(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseWheel(int notches) {
        this.area.scrollBy(-notches);
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_TAB || keyCode == Keyboard.KEY_ESCAPE) {
            return false;
        }
        if ((keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER)
                && !GuiScreen.isShiftKeyDown()) {
            return false;
        }
        return this.area.keyTyped(typedChar, keyCode)
                // A printable character is the box's even when it refused
                // it for length, so it never leaks out as a shortcut.
                || typedChar >= ' ';
    }
}
