package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Keyboard;

/**
 * A row that steps through a short list, as a number's row in Settings:
 * its name at the left and the chosen option between a menu's stepper
 * chevrons at the right end. The chevrons, the wheel over the row and the
 * left and right keys while it holds the focus all step it, and the list
 * wraps at either end.
 */
public final class CreatorStepper extends CreatorControl {

    private final String label;
    private final CreatorChoice choice;
    /**
     * The two chevrons, each keeping its own beat, so stepping reads as a
     * press rather than as a colour change.
     */
    private final LostTalesUiButtonMotion leftMotion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
    private final LostTalesUiButtonMotion rightMotion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);

    public CreatorStepper(CreatorContext context, String label,
                          CreatorChoice choice) {
        super(context);
        this.label = label;
        this.choice = choice;
    }

    @Override
    public int height() {
        return CreatorRows.height();
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    private boolean canStep() {
        return !this.choice.isFixed() && this.choice.count() > 1;
    }

    /**
     * Lit under the pointer and while it holds the keys, its name at the
     * left in ivory, the option at the right end between the chevrons, in
     * the room the widest option takes; the chevrons stand flat while the
     * option cannot be stepped.
     */
    @Override
    public void draw(int mouseX, int mouseY) {
        FontRenderer font = this.context.getFont();
        if (contains(mouseX, mouseY) || isFocused()) {
            CreatorRows.light(this.context, this.y, this.y + height());
        }
        boolean steps = canStep();
        int arrow = steps ? arrowAt(mouseX, mouseY) : 0;
        int room = valueRoom(font);
        int right = this.x + this.width;
        MenuWindow.drawStepper(font, currentLabel(), room, right,
                CreatorRows.textTop(this.y), arrow < 0 ? MenuWindow.PART_LESS
                        : arrow > 0 ? MenuWindow.PART_MORE : null,
                steps ? this.leftMotion : null, steps ? this.rightMotion : null,
                WindowStyle.asideRgb(), this.context.alpha());
        CreatorRows.drawLabel(this.context, this.label, this.x, this.y,
                right - room - 2 * MenuWindow.STEPPER_CELL
                        - MenuWindow.VALUE_GAP, this, this.label,
                contains(mouseX, mouseY));
    }

    /**
     * The room the option takes between the chevrons: the widest it can
     * read, so the chevrons stand still as it steps, and no wider than the
     * row leaves them.
     */
    private int valueRoom(FontRenderer font) {
        int widest = font.getStringWidth(currentLabel());
        for (int index = 0; index < this.choice.count(); index++) {
            widest = Math.max(widest,
                    font.getStringWidth(this.choice.label(index)));
        }
        return Math.max(0, Math.min(widest,
                this.width - 2 * MenuWindow.STEPPER_CELL));
    }

    private String currentLabel() {
        if (this.choice.isFixed()) {
            return this.choice.fixedLabel();
        }
        int index = this.choice.index();
        if (this.choice.count() == 0 || index < 0
                || index >= this.choice.count()) {
            return this.choice.emptyLabel();
        }
        return this.choice.label(index);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        int arrow = arrowAt(mouseX, mouseY);
        if (arrow != 0) {
            step(arrow);
        }
        // Clicking anywhere on the row takes the focus, so the keys step it.
        return true;
    }

    /** -1 over the left chevron, 1 over the right one, 0 anywhere else. */
    private int arrowAt(int mouseX, int mouseY) {
        String part = contains(mouseX, mouseY)
                ? MenuWindow.stepperPartAt(mouseX, mouseY,
                        this.x + this.width, this.y, height(),
                        valueRoom(this.context.getFont()))
                : null;
        return MenuWindow.PART_LESS.equals(part) ? -1
                : MenuWindow.PART_MORE.equals(part) ? 1 : 0;
    }

    /** Only the chevrons act; the rest of the row takes the focus and nothing more. */
    @Override
    public boolean isPointerOverAction(int mouseX, int mouseY) {
        return canStep() && arrowAt(mouseX, mouseY) != 0;
    }

    @Override
    public boolean mouseWheel(int notches) {
        if (!canStep() || notches == 0) {
            return false;
        }
        step(notches > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_LEFT) {
            step(-1);
            return true;
        }
        if (keyCode == Keyboard.KEY_RIGHT) {
            step(1);
            return true;
        }
        return false;
    }

    private void step(int direction) {
        if (!canStep()) {
            return;
        }
        int count = this.choice.count();
        int next = (this.choice.index() + direction) % count;
        if (next < 0) {
            next += count;
        }
        this.choice.choose(next);
    }
}
