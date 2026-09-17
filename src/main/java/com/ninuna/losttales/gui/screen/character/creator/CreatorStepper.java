package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
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
    /**
     * The two arrows, each keeping its own beat: the box stands still
     * and the chevron inside it moves, so stepping reads as a press
     * rather than as a colour change.
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
        return ROW_HEIGHT;
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    private boolean canStep() {
        return !this.choice.isFixed() && this.choice.count() > 1;
    }

    private int rightArrowX() {
        return this.x + this.width - CreatorWidgets.ARROW_BOX;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        FontRenderer font = this.context.getFont();
        drawLabel(this.label);
        int top = valueTop();
        boolean steps = canStep();
        int arrow = steps ? arrowAt(mouseX, mouseY) : 0;
        boolean overLeft = arrow < 0;
        boolean overRight = arrow > 0;
        CreatorWidgets.drawArrowBox(font, this.x, top, false, steps, overLeft,
                this.leftMotion);
        CreatorWidgets.drawArrowBox(font, rightArrowX(), top, true, steps,
                overRight, this.rightMotion);
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
        int arrow = arrowAt(mouseX, mouseY);
        if (arrow != 0) {
            step(arrow);
        }
        // Clicking anywhere on the row takes the focus, so the keys step it.
        return true;
    }

    /** -1 over the left arrow, 1 over the right one, 0 anywhere else. */
    private int arrowAt(int mouseX, int mouseY) {
        int top = valueTop();
        if (CreatorWidgets.within(mouseX, mouseY, this.x, top,
                CreatorWidgets.ARROW_BOX, CreatorWidgets.ARROW_BOX)) {
            return -1;
        }
        if (CreatorWidgets.within(mouseX, mouseY, rightArrowX(), top,
                CreatorWidgets.ARROW_BOX, CreatorWidgets.ARROW_BOX)) {
            return 1;
        }
        return 0;
    }

    /** Only the arrows act; the rest of the row takes the focus and nothing more. */
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
