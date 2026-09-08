package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/**
 * A row with a line of text to type: its name above, the field below, a
 * counter at the field's corner when the text is bounded tightly enough
 * to be worth watching. Enter is left to the screen, so it submits the
 * form from any field.
 */
public final class CreatorTextControl extends CreatorControl {

    private final String label;
    private final GuiTextField field;
    private final int maxLength;
    private final boolean showCounter;

    public CreatorTextControl(CreatorContext context, String label,
                              String text, int maxLength, boolean showCounter) {
        super(context);
        this.label = label;
        this.maxLength = maxLength;
        this.showCounter = showCounter;
        this.field = new GuiTextField(context.getFont(), 0, 0, 10,
                CreatorWidgets.FIELD_HEIGHT - 4);
        this.field.setEnableBackgroundDrawing(false);
        this.field.setMaxStringLength(maxLength);
        this.field.setTextColor(LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        this.field.setText(text == null ? "" : text);
    }

    public String getText() {
        return this.field.getText();
    }

    @Override
    public int height() {
        return LABEL_HEIGHT + CreatorWidgets.FIELD_HEIGHT + 6;
    }

    @Override
    public boolean canFocus() {
        return true;
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

    private int fieldTop() {
        return this.y + LABEL_HEIGHT;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        FontRenderer font = this.context.getFont();
        CreatorWidgets.drawLabel(font, this.label, this.x, this.y);
        CreatorWidgets.drawFieldBox(this.x, fieldTop(), this.width,
                CreatorWidgets.FIELD_HEIGHT, isFocused());
        this.field.xPosition = this.x + 4;
        this.field.yPosition = fieldTop()
                + (CreatorWidgets.FIELD_HEIGHT - font.FONT_HEIGHT) / 2 + 1;
        this.field.width = this.width - 8;
        this.field.drawTextBox();
        if (this.showCounter && this.maxLength > 0) {
            String counter = this.field.getText().length() + "/" + this.maxLength;
            font.drawStringWithShadow(counter,
                    this.x + this.width - font.getStringWidth(counter),
                    this.y + 1, LostTalesSkyrimUiStyle.TEXT_DIM);
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        // The field's own click handling places the cursor once focused.
        this.field.setFocused(true);
        this.field.mouseClicked(mouseX, mouseY, button);
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
