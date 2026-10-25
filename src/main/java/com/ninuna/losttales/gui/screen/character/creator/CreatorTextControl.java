package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiTextField;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Keyboard;

/**
 * A row with a line of text to type, over a hairline: its name at the
 * left, the count beside it while the field holds the keys when the text
 * is bounded tightly enough to be worth watching, and the text ending at
 * the row's right end. Enter is left to the screen, so it submits the
 * form from any field.
 */
public final class CreatorTextControl extends CreatorControl {

    private final String label;
    private final LostTalesUiTextField field;
    private final int maxLength;
    private final boolean showCounter;

    public CreatorTextControl(CreatorContext context, String label,
                              String text, int maxLength, boolean showCounter) {
        super(context);
        this.label = label;
        this.maxLength = maxLength;
        this.showCounter = showCounter;
        this.field = new LostTalesUiTextField(context.getFont(), 0, 0, 10,
                CreatorRows.FIELD_LINE);
        this.field.setCanLoseFocus(false);
        this.field.setFocused(false);
        this.field.setMaxStringLength(maxLength);
        this.field.setText(text == null ? "" : text);
    }

    public String getText() {
        return this.field.getText();
    }

    /** An example shown faintly while the field is empty. */
    public void setHint(String hint) {
        this.field.setHint(hint);
    }

    @Override
    public int height() {
        return CreatorRows.fieldHeight();
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
        this.field.setFocused(focused);
        if (focused) {
            this.field.setCursorPositionEnd();
        }
    }

    @Override
    public void tick() {
        this.field.updateCursorCounter();
    }

    private String counter() {
        return this.field.getText().length() + "/" + this.maxLength;
    }

    /** Whether the row shows the count: while the field holds the keys. */
    private boolean showsCount() {
        return this.showCounter && this.maxLength > 0 && isFocused();
    }

    /** Where the field's part of the row starts: past the name, and the count while it shows. */
    private int fieldLeft() {
        FontRenderer font = this.context.getFont();
        int left = this.x + font.getStringWidth(this.label);
        if (showsCount()) {
            left += font.getStringWidth(" " + counter());
        }
        return Math.min(this.x + this.width, left + MenuWindow.VALUE_GAP);
    }

    /**
     * Lit under the pointer, the name at the left in ivory, the count
     * after it in the aside tone while the field holds the keys, the
     * field's text ending at the row's right end, and the hairline under
     * it all.
     */
    @Override
    public void draw(int mouseX, int mouseY) {
        FontRenderer font = this.context.getFont();
        int rowBottom = this.y + CreatorRows.height();
        int right = this.x + this.width;
        if (contains(mouseX, mouseY)) {
            CreatorRows.light(this.context, this.y, rowBottom);
        }
        CreatorRows.drawLabel(this.context, this.label, this.x, this.y, right,
                this, this.label, contains(mouseX, mouseY));
        if (showsCount()) {
            int countX = this.x + font.getStringWidth(this.label + " ");
            LostTalesUiInk.drawText(font, counter(), countX,
                    CreatorRows.textTop(this.y), WindowStyle.asideRgb(),
                    this.context.alpha());
        }
        CreatorRows.placeField(this.field, font, fieldLeft(), right, this.y);
        this.field.drawTextBox(this.context.alpha());
        CreatorRows.drawHairline(this.context, this.x, right, rowBottom);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        // The field's own click handling places the cursor once focused.
        this.field.setFocused(true);
        if (mouseX >= this.field.xPosition) {
            // On the text's part of the row; the field answers on its
            // line, whatever height of the row the press landed on.
            this.field.mouseClicked(mouseX, Math.max(this.field.yPosition,
                    Math.min(mouseY, this.field.yPosition
                            + this.field.height - 1)), button);
        }
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER
                || keyCode == Keyboard.KEY_TAB || keyCode == Keyboard.KEY_ESCAPE) {
            return false;
        }
        return this.field.textboxKeyTyped(typedChar, keyCode)
                // A printable character is the field's even when it refused
                // it for length, so it never leaks out as a shortcut.
                || typedChar >= ' ';
    }
}
