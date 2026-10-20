package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiCutName;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Mouse;
import net.minecraft.client.gui.Gui;

/**
 * The few shapes every creator control is drawn from, in the mod's own
 * panel language rather than vanilla's stone buttons.
 *
 * <p>All of them are built from {@link Gui#drawRect}, which leaves
 * blending off; each puts it back through
 * {@link LostTalesSkyrimUiStyle#beginContent()} before any text, so the
 * shared half-opacity shadow never turns solid.</p>
 */
public final class CreatorWidgets {

    /** The square an arrow sits in. */
    static final int ARROW_BOX = 14;
    /** A text field's box. */
    static final int FIELD_HEIGHT = 16;
    /** What the buttons' labels name themselves to as cut names. */
    private static final Object BUTTON_LABELS = new Object();

    private CreatorWidgets() {}

    /**
     * A muted label, the way every row names itself, cut at {@code room}
     * and fading into it where it is longer (LOOK-37).
     */
    static void drawLabel(FontRenderer font, String label, int x, int y,
                          int room) {
        LostTalesSkyrimUiStyle.beginContent();
        LostTalesUiCutName.drawShadowed(font, label == null ? "" : label, x, y,
                room, LostTalesSkyrimUiStyle.TEXT_MUTED);
    }

    /**
     * An arrow box: a small framed square with a chevron pointing the
     * way it steps, dimmed when there is nothing to step to. It answers
     * the pointer through {@code motion}: the box
     * keeps its place and the chevron inside it rises, drops while the
     * arrow is held, and springs back, as every framed button in the mod
     * does. A null motion draws it still.
     */
    static void drawArrowBox(FontRenderer font, int x, int y,
                             boolean pointsRight, boolean enabled,
                             boolean hovered,
                             LostTalesUiButtonMotion motion) {
        if (motion != null) {
            boolean answers = enabled && hovered;
            motion.advance(System.nanoTime(), answers, answers,
                    answers && Mouse.isButtonDown(0));
        }
        int fill = !enabled ? LostTalesSkyrimUiStyle.withAlpha(
                        LostTalesSkyrimUiStyle.PLUM_BLACK, 0x40)
                : hovered ? LostTalesSkyrimUiStyle.PANEL_HOVER
                : LostTalesSkyrimUiStyle.PANEL_SELECTED;
        Gui.drawRect(x, y, x + ARROW_BOX, y + ARROW_BOX, fill);
        drawFrame(x, y, ARROW_BOX, ARROW_BOX, enabled
                ? LostTalesSkyrimUiStyle.BORDER
                : LostTalesSkyrimUiStyle.BORDER_DIM);
        int color = enabled
                ? (hovered ? LostTalesSkyrimUiStyle.GOLD
                        : LostTalesSkyrimUiStyle.TEXT_BRIGHT)
                : LostTalesSkyrimUiStyle.TEXT_DIM;
        // A chevron three pixels deep, drawn as stacked single pixels so
        // it stays crisp at every GUI scale.
        int centerY = y + ARROW_BOX / 2;
        int tipX = pointsRight ? x + ARROW_BOX / 2 + 1 : x + ARROW_BOX / 2 - 2;
        int step = pointsRight ? -1 : 1;
        if (motion != null) {
            LostTalesUiButton.beginPose(motion, x, y, ARROW_BOX, ARROW_BOX);
        }
        try {
            for (int i = 0; i < 3; i++) {
                int px = tipX + step * i;
                Gui.drawRect(px, centerY - i, px + 1, centerY - i + 1, color);
                Gui.drawRect(px, centerY + i, px + 1, centerY + i + 1, color);
            }
        } finally {
            if (motion != null) {
                LostTalesUiButton.endPose();
            }
        }
        LostTalesSkyrimUiStyle.beginContent();
    }

    /** The box a text field or readout sits in. */
    static void drawFieldBox(int x, int y, int width, int height,
                             boolean focused) {
        Gui.drawRect(x, y, x + width, y + height,
                LostTalesSkyrimUiStyle.withAlpha(
                        LostTalesSkyrimUiStyle.PLUM_BLACK, 0x9A));
        drawFrame(x, y, width, height, focused
                ? LostTalesSkyrimUiStyle.GOLD
                : LostTalesSkyrimUiStyle.BORDER_DIM);
        LostTalesSkyrimUiStyle.beginContent();
    }

    /**
     * A button in the panel language: framed, filled, its label centred,
     * brighter under the pointer and dimmed when it cannot be pressed. It
     * answers the pointer through {@code motion}: the frame and its
     * brackets keep their place and the label moves inside them. A null
     * motion draws it still.
     */
    public static void drawButton(FontRenderer font, int x, int y, int width,
                                  int height, String label, boolean enabled,
                                  boolean hovered,
                                  LostTalesUiButtonMotion motion) {
        if (motion != null) {
            boolean answers = enabled && hovered;
            motion.advance(System.nanoTime(), answers, answers,
                    answers && Mouse.isButtonDown(0));
        }
        int fill = !enabled
                ? LostTalesSkyrimUiStyle.withAlpha(
                        LostTalesSkyrimUiStyle.PLUM_BLACK, 0x50)
                : hovered ? LostTalesSkyrimUiStyle.PANEL_HOVER
                : LostTalesSkyrimUiStyle.PANEL_SELECTED;
        Gui.drawRect(x, y, x + width, y + height, fill);
        drawFrame(x, y, width, height, enabled
                ? LostTalesSkyrimUiStyle.BORDER
                : LostTalesSkyrimUiStyle.BORDER_DIM);
        if (enabled && hovered) {
            LostTalesSkyrimUiStyle.drawSelectionBrackets(x, y, width, height,
                    4, LostTalesSkyrimUiStyle.GOLD);
        }
        LostTalesSkyrimUiStyle.beginContent();
        String text = label == null ? "" : label;
        int room = width - 8;
        int shown = (int)LostTalesUiCutName.shownWidth(
                font.getStringWidth(text), room);
        int color = !enabled ? LostTalesSkyrimUiStyle.TEXT_DIM
                : hovered ? LostTalesSkyrimUiStyle.GOLD
                : LostTalesSkyrimUiStyle.TEXT_BRIGHT;
        if (motion != null) {
            LostTalesUiButton.beginPose(motion, x, y, width, height);
        }
        try {
            // A label too long for its button fades into the button's end
            // and slides along under the pointer (LOOK-37).
            LostTalesUiCutName.drawShadowed(font, text,
                    x + (width - shown) / 2,
                    y + (height - font.FONT_HEIGHT) / 2 + 1, room, color,
                    BUTTON_LABELS, text, enabled && hovered);
        } finally {
            if (motion != null) {
                LostTalesUiButton.endPose();
            }
        }
    }

    /** A one-pixel frame. */
    static void drawFrame(int x, int y, int width, int height, int color) {
        Gui.drawRect(x, y, x + width, y + 1, color);
        Gui.drawRect(x, y + height - 1, x + width, y + height, color);
        Gui.drawRect(x, y, x + 1, y + height, color);
        Gui.drawRect(x + width - 1, y, x + width, y + height, color);
    }

    public static boolean within(int mouseX, int mouseY, int x, int y,
                          int width, int height) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }
}
