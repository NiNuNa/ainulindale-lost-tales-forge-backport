package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiTextArea;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/**
 * Several lines of text to type, for a profile's longer texts: the name
 * at the left of a row of its own with the count at its right end, and
 * under it the lines, standing as a field does, at a note's pitch over a
 * hairline; they wrap the words and scroll by whole lines. Shift+Return
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

    /** The lines' room: a note's clear room above and under them. */
    private int linesHeight() {
        return 2 * MenuWindow.NOTE_PADDING
                + this.lines * LostTalesUiTextArea.LINE_HEIGHT;
    }

    @Override
    public int height() {
        return CreatorRows.height() + linesHeight() + 1;
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    /** Typed in while it holds the focus. */
    @Override
    public boolean holdsKeys() {
        return isFocused();
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        this.area.setFocused(focused);
    }

    private String counter() {
        return this.area.getText().length() + "/" + this.limit;
    }

    /**
     * The name at the left of a row of its own, the count at its right
     * end; under it the lines, each centred on a note's line, and the
     * hairline under them.
     */
    @Override
    public void draw(int mouseX, int mouseY) {
        int right = this.x + this.width;
        int countLeft = CreatorRows.drawValue(this.context, counter(), this.x,
                right, this.y, false);
        CreatorRows.drawLabel(this.context, this.label, this.x, this.y,
                countLeft - MenuWindow.VALUE_GAP);
        int linesTop = this.y + CreatorRows.height();
        this.area.place(this.x, linesTop + MenuWindow.NOTE_PADDING
                        + LostTalesUiInk.centredStart(
                                LostTalesUiTextArea.LINE_HEIGHT,
                                LostTalesUiInk.CAP_HEIGHT),
                this.width, this.lines * LostTalesUiTextArea.LINE_HEIGHT);
        this.area.draw(null, this.context.alpha());
        CreatorRows.drawHairline(this.context, this.x, right,
                linesTop + linesHeight());
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        this.area.mouseClicked(mouseX, mouseY);
        return true;
    }

    /** The wheel scrolls the lines, and is taken only while they move, so the form scrolls past their ends. */
    @Override
    public boolean mouseWheel(int notches) {
        return this.area.scrollBy(-notches);
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
