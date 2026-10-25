package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.client.window.MenuWindow;
import org.lwjgl.input.Keyboard;

/**
 * A row that is on or off, as a switch's row in Settings: its name at the
 * left and the word for its state at the right end. A click anywhere on
 * the row, or space or the left and right keys while it holds the focus,
 * flips it.
 */
public final class CreatorToggle extends CreatorControl {

    /** The flag the row edits, kept by the screen. */
    public interface BooleanValue {
        boolean get();
        void set(boolean value);
    }

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
        return CreatorRows.height();
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    /**
     * Lit under the pointer and while it holds the keys, its name at the
     * left in ivory, the word for its state at the right end in the aside
     * tone, ivory under the pointer.
     */
    @Override
    public void draw(int mouseX, int mouseY) {
        boolean hovered = contains(mouseX, mouseY);
        if (hovered || isFocused()) {
            CreatorRows.light(this.context, this.y, this.y + height());
        }
        String state = this.value.get() ? this.onLabel : this.offLabel;
        int stateLeft = CreatorRows.drawValue(this.context, state, this.x,
                this.x + this.width, this.y, hovered);
        CreatorRows.drawLabel(this.context, this.label, this.x, this.y,
                stateLeft - MenuWindow.VALUE_GAP, this, this.label, hovered);
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
