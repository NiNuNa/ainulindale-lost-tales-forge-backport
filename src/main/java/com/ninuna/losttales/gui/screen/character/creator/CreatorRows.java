package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiTextField;
import net.minecraft.client.gui.FontRenderer;

import java.util.List;

/**
 * The shapes a control is drawn from when it stands as a window's row, as
 * Settings' rows stand: a menu's row high, its label at the left in
 * ivory and its value at the right end in the aside tone; a field over
 * its hairline; a note at a menu note's pitch; the row under the pointer
 * lit by recolouring the window's surface. The creator's boxed shapes are
 * {@link CreatorWidgets}.
 */
public final class CreatorRows {

    private CreatorRows() {}

    /** A row's height: a menu's. */
    public static int height() {
        return MenuWindow.rowHeight();
    }

    /** A field's row: the row and the hairline under it. */
    public static int fieldHeight() {
        return WindowLists.fieldHeight();
    }

    /** A note of {@code lines} lines: a menu note's pitch, its clear room above and under them. */
    public static int noteHeight(int lines) {
        return 2 * MenuWindow.NOTE_PADDING + lines * MenuWindow.NOTE_LINE;
    }

    /** The top of a row's capitals, the row standing from {@code rowY}. */
    public static int textTop(int rowY) {
        return rowY + LostTalesUiInk.centredStart(height(),
                LostTalesUiInk.CAP_HEIGHT);
    }

    /**
     * Lights rows from {@code top} to {@code bottom} across the window's
     * box, the frame's ring beside them with them. Drawn before anything
     * lands on them.
     */
    public static void light(CreatorContext context, int top, int bottom) {
        light(context, context.rowLeft(), top, context.rowRight(), bottom);
    }

    /** Lights a stretch inside the rows: a tile, an emoji's place. */
    public static void light(CreatorContext context, int left, int top,
                             int right, int bottom) {
        if (right > left && bottom > top) {
            WindowLists.drawLitRow(context.rowLeft(), context.rowRight(),
                    left, top, right, bottom, context.surfaceAlpha());
        }
    }

    /** A row's label from {@code x}, in ivory, cut at {@code right}. */
    static void drawLabel(CreatorContext context, String label, int x,
                          int rowY, int right) {
        FontRenderer font = context.getFont();
        LostTalesUiInk.drawText(font, trimmed(font, label, right - x), x,
                textTop(rowY), LostTalesUiInk.IVORY, context.alpha());
    }

    /**
     * A row's value ending at {@code right}, cut where it would pass
     * {@code left}: in the aside tone, in ivory while {@code lit}.
     * Answers where it starts.
     */
    static int drawValue(CreatorContext context, String value, int left,
                         int right, int rowY, boolean lit) {
        FontRenderer font = context.getFont();
        String text = trimmed(font, value, right - left);
        int x = right - font.getStringWidth(text);
        LostTalesUiInk.drawText(font, text, x, textTop(rowY),
                lit ? LostTalesUiInk.IVORY : WindowStyle.asideRgb(),
                context.alpha());
        return x;
    }

    /** A field's hairline, from {@code left} to {@code right} at {@code y}. */
    static void drawHairline(CreatorContext context, int left, int right,
                             int y) {
        WindowLists.drawFieldRule(left, y, right, context.alpha());
    }

    /**
     * Places a one-line field in a row so what it holds ends at
     * {@code right}, the caret's room after it, and starts no further left
     * than {@code left}: past that it scrolls to its caret.
     */
    static void placeField(LostTalesUiTextField field, FontRenderer font,
                           int left, int right, int rowY) {
        int room = Math.max(1, right - left);
        int wanted = font.getStringWidth(field.getText())
                + LostTalesUiCaret.WIDTH;
        field.width = Math.max(1, Math.min(room, wanted));
        field.xPosition = right - field.width;
        field.yPosition = textTop(rowY);
    }

    /** A note's lines from {@code top}, at a menu note's pitch, in {@code rgb}. */
    public static void drawNote(CreatorContext context, List<String> lines,
                                int x, int top, int rgb) {
        int lineTop = top + MenuWindow.NOTE_PADDING;
        for (String line : lines) {
            LostTalesUiInk.drawText(context.getFont(), line, x,
                    lineTop + LostTalesUiInk.centredStart(MenuWindow.NOTE_LINE,
                            LostTalesUiInk.CAP_HEIGHT), rgb, context.alpha());
            lineTop += MenuWindow.NOTE_LINE;
        }
    }

    /** {@code text} cut to {@code width}, whole when it fits. */
    static String trimmed(FontRenderer font, String text, int width) {
        return font.getStringWidth(text) <= width ? text
                : LostTalesSkyrimUiStyle.trimToWidth(font, text,
                        Math.max(0, width));
    }
}
