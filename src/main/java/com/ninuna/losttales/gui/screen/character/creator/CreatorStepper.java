package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Keyboard;

/**
 * A row that steps through a short list: its name above, the chosen
 * option between two arrows below. The arrows, the wheel over the row and
 * the left and right keys while it holds the focus all step it, and the
 * list wraps at either end.
 */
public final class CreatorStepper extends CreatorControl {

    private final String label;
    private final CreatorChoice choice;

    public CreatorStepper(CreatorContext context, String label,
                          CreatorChoice choice) {
        super(context);
        this.label = label;
        this.choice = choice;
    }

    @Override
    public int height() {
        return ROW_HEIGHT;
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    private boolean canStep() {
        return !this.choice.isFixed() && this.choice.count() > 1;
    }

    private int valueTop() {
        return this.y + LABEL_HEIGHT;
    }

    private int rightArrowX() {
        return this.x + this.width - CreatorWidgets.ARROW_BOX;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        FontRenderer font = this.context.getFont();
        CreatorWidgets.drawLabel(font, this.label, this.x, this.y);
        int top = valueTop();
        boolean steps = canStep();
        boolean overLeft = steps && CreatorWidgets.within(mouseX, mouseY,
                this.x, top, CreatorWidgets.ARROW_BOX, CreatorWidgets.ARROW_BOX);
        boolean overRight = steps && CreatorWidgets.within(mouseX, mouseY,
                rightArrowX(), top, CreatorWidgets.ARROW_BOX,
                CreatorWidgets.ARROW_BOX);
        CreatorWidgets.drawArrowBox(font, this.x, top, false, steps, overLeft);
        CreatorWidgets.drawArrowBox(font, rightArrowX(), top, true, steps,
                overRight);
        if (isFocused()) {
            LostTalesSkyrimUiStyle.drawSelectionBrackets(
                    this.x + CreatorWidgets.ARROW_BOX + 2, top,
                    this.width - CreatorWidgets.ARROW_BOX * 2 - 4,
                    CreatorWidgets.ARROW_BOX, 3, LostTalesSkyrimUiStyle.GOLD);
            LostTalesSkyrimUiStyle.beginContent();
        }
        String value = currentLabel();
        int room = this.width - CreatorWidgets.ARROW_BOX * 2 - 8;
        String text = LostTalesSkyrimUiStyle.trimToWidth(font, value, room);
        int color = this.choice.isFixed() || this.choice.count() == 0
                ? LostTalesSkyrimUiStyle.TEXT_MUTED
                : LostTalesSkyrimUiStyle.TEXT_BRIGHT;
        font.drawStringWithShadow(text,
                this.x + (this.width - font.getStringWidth(text)) / 2,
                top + (CreatorWidgets.ARROW_BOX - font.FONT_HEIGHT) / 2 + 1,
                color);
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
        int top = valueTop();
        if (CreatorWidgets.within(mouseX, mouseY, this.x, top,
                CreatorWidgets.ARROW_BOX, CreatorWidgets.ARROW_BOX)) {
            step(-1);
        } else if (CreatorWidgets.within(mouseX, mouseY, rightArrowX(), top,
                CreatorWidgets.ARROW_BOX, CreatorWidgets.ARROW_BOX)) {
            step(1);
        }
        // Clicking anywhere on the row takes the focus, so the keys step it.
        return true;
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
