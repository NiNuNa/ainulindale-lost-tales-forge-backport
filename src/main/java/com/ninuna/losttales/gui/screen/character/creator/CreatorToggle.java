package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Keyboard;

/**
 * A row that is on or off: its name at the left, a small switch at the
 * right. A click anywhere on the row, or space or enter while it holds the
 * focus, flips it.
 */
public final class CreatorToggle extends CreatorControl {

    /** The flag the row edits, kept by the screen. */
    public interface BooleanValue {
        boolean get();
        void set(boolean value);
    }

    private static final int SWITCH_WIDTH = 22;
    private static final int SWITCH_HEIGHT = 10;
    private static final int KNOB = 8;

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
        return 18;
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        FontRenderer font = this.context.getFont();
        boolean on = this.value.get();
        boolean hovered = contains(mouseX, mouseY);
        int switchX = this.x + this.width - SWITCH_WIDTH;
        int switchY = this.y + (height() - SWITCH_HEIGHT) / 2;
        Gui.drawRect(switchX, switchY, switchX + SWITCH_WIDTH,
                switchY + SWITCH_HEIGHT, on
                        ? LostTalesSkyrimUiStyle.withAlpha(
                                LostTalesSkyrimUiStyle.GOLD, 0x70)
                        : LostTalesSkyrimUiStyle.withAlpha(
                                LostTalesSkyrimUiStyle.PLUM_BLACK, 0x90));
        CreatorWidgets.drawFrame(switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT,
                hovered || isFocused()
                        ? LostTalesSkyrimUiStyle.GOLD
                        : LostTalesSkyrimUiStyle.BORDER_DIM);
        int knobX = on ? switchX + SWITCH_WIDTH - 1 - KNOB : switchX + 1;
        Gui.drawRect(knobX, switchY + 1, knobX + KNOB, switchY + 1 + KNOB,
                on ? LostTalesSkyrimUiStyle.GOLD
                        : LostTalesSkyrimUiStyle.TEXT_MUTED);
        LostTalesSkyrimUiStyle.beginContent();
        String state = on ? this.onLabel : this.offLabel;
        int stateX = switchX - 4 - font.getStringWidth(state);
        font.drawStringWithShadow(state, stateX,
                this.y + (height() - font.FONT_HEIGHT) / 2 + 1,
                on ? LostTalesSkyrimUiStyle.GOLD : LostTalesSkyrimUiStyle.TEXT_MUTED);
        String text = LostTalesSkyrimUiStyle.trimToWidth(font, this.label,
                Math.max(20, stateX - this.x - 4));
        font.drawStringWithShadow(text, this.x,
                this.y + (height() - font.FONT_HEIGHT) / 2 + 1,
                hovered || isFocused()
                        ? LostTalesSkyrimUiStyle.TEXT_BRIGHT
                        : LostTalesSkyrimUiStyle.TEXT);
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
