package com.ninuna.losttales.client.gui.tooltip;

import com.ninuna.losttales.client.input.LostTalesInputIconRenderer;
import com.ninuna.losttales.gui.style.LostTalesColors;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Draws the tooltips that carry a key icon or item slots, in place of the
 * vanilla one.
 *
 * <p>Called from the head of {@code GuiScreen.drawHoveringText} by the
 * coremod: a tooltip with no {@link LostTalesTooltipIcons} or
 * {@link LostTalesTooltipSlots} mark in it is declined and vanilla draws it
 * exactly as before, so the only tooltips that come through here are Lost
 * Tales' own.</p>
 *
 * <p>Taking the whole tooltip rather than overlaying the icon afterwards is
 * what keeps the box honest: a line's width counts its keys, and where a
 * key is taller than a line of text its line is measured to the key, as a
 * row of slots is measured to the slots. Measurement and drawing are the
 * same pass here, and cannot disagree.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesTooltipHooks {
    /** A plain line of text, and the step to the next one; vanilla's. */
    private static final int TEXT_LINE_HEIGHT = 10;
    /** The first line is measured short, and the gap under it added once. */
    private static final int FIRST_LINE_HEIGHT = 8;
    private static final int TITLE_GAP = 2;
    /** Under a row of slots, the two clear rows a line of text leaves under its words. */
    private static final int SLOTS_BELOW = TEXT_LINE_HEIGHT - FIRST_LINE_HEIGHT;

    /** Vanilla's tooltip frame, in the palette entries nearest its colours. */
    private static final int BACKGROUND_COLOR =
            LostTalesColors.withAlpha(LostTalesColors.PLUM_BLACK, 0xF0);
    private static final int BORDER_TOP_COLOR =
            LostTalesColors.withAlpha(LostTalesColors.SLATE_BLUE, 0x50);
    private static final int BORDER_BOTTOM_COLOR =
            LostTalesColors.withAlpha(LostTalesColors.INDIGO, 0x50);
    private static final float TOOLTIP_Z = 300.0F;

    private static final Canvas CANVAS = new Canvas();

    private LostTalesTooltipHooks() {}

    /**
     * @return true when this tooltip was drawn here and vanilla must not draw
     *         it again
     */
    public static boolean drawHoveringText(
            GuiScreen gui, List lines, int mouseX, int mouseY,
            FontRenderer font) {
        try {
            if (gui == null || font == null
                    || lines == null || lines.isEmpty()
                    || !containsMark(lines)) {
                return false;
            }
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null) {
                return false;
            }
            draw(minecraft, gui, rows(lines), mouseX, mouseY, font);
            return true;
        } catch (Throwable throwable) {
            // A tooltip that cannot be drawn here is still vanilla's to draw,
            // and a broken one must not take the screen down with it.
            return false;
        }
    }

    private static boolean containsMark(List lines) {
        for (int index = 0; index < lines.size(); index++) {
            Object value = lines.get(index);
            if (value instanceof String
                    && (LostTalesTooltipIcons.hasIcon((String)value)
                    || LostTalesTooltipSlots.inBlock((String)value))) {
                return true;
            }
        }
        return false;
    }

    /** One row of the tooltip: a line of text, or slots. */
    static final class Row {
        final String text;
        final LostTalesTooltipSlots.Slots slots;

        Row(String text, LostTalesTooltipSlots.Slots slots) {
            this.text = text;
            this.slots = slots;
        }
    }

    /**
     * The rows the lines are drawn as. A marked block whose slots are
     * still kept becomes one row of slots, and the line counting the
     * items past them where there are any; a block whose slots are not is
     * its words, line by line, as any other renderer shows it.
     */
    static List<Row> rows(List lines) {
        List<Row> rows = new ArrayList<Row>(lines.size());
        boolean drawnAsSlots = false;
        for (int index = 0; index < lines.size(); index++) {
            String line = lineAt(lines, index);
            if (!LostTalesTooltipSlots.inBlock(line)) {
                drawnAsSlots = false;
                rows.add(new Row(line, null));
                continue;
            }
            LostTalesTooltipSlots.Slots slots =
                    LostTalesTooltipSlots.slotsOf(line);
            if (slots != null) {
                drawnAsSlots = true;
                rows.add(new Row(null, slots));
                if (slots.more != null) {
                    rows.add(new Row(slots.more, null));
                }
            } else if (!drawnAsSlots) {
                rows.add(new Row(LostTalesTooltipSlots.words(line), null));
            }
        }
        return rows;
    }

    private static void draw(
            Minecraft minecraft, GuiScreen gui, List<Row> rows,
            int mouseX, int mouseY, FontRenderer font) {
        int rowCount = rows.size();
        int width = 0;
        int height = 0;
        for (int index = 0; index < rowCount; index++) {
            Row row = rows.get(index);
            width = Math.max(width, rowWidth(minecraft, font, row));
            height += rowHeight(row, index);
        }
        if (rowCount > 1) {
            height += TITLE_GAP;
        }

        // Vanilla's placement, kept to the pixel: the tooltip has to sit where
        // players expect it, and flip against the same edges.
        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + width > gui.width) {
            x -= 28 + width;
        }
        if (y + height + 6 > gui.height) {
            y = gui.height - height - 6;
        }

        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        try {
            CANVAS.drawFrame(x, y, width, height);
            int rowY = y;
            for (int index = 0; index < rowCount; index++) {
                Row row = rows.get(index);
                if (row.slots != null) {
                    LostTalesTooltipSlots.draw(minecraft, font, row.slots,
                            x, rowY);
                } else {
                    drawLine(minecraft, font, row.text, x, rowY);
                }
                rowY += rowHeight(row, index);
                if (index == 0 && rowCount > 1) {
                    rowY += TITLE_GAP;
                }
            }
        } finally {
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            RenderHelper.enableStandardItemLighting();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        }
    }

    private static String lineAt(List lines, int index) {
        Object value = lines.get(index);
        return value instanceof String ? (String)value : String.valueOf(value);
    }

    private static int rowWidth(Minecraft minecraft, FontRenderer font,
                                Row row) {
        return row.slots != null ? row.slots.width
                : LostTalesTooltipIcons.measureLine(minecraft, font, row.text);
    }

    private static int rowHeight(Row row, int index) {
        if (row.slots != null) {
            return row.slots.height + SLOTS_BELOW;
        }
        if (LostTalesTooltipIcons.hasIcon(row.text)) {
            return iconLineHeight();
        }
        return index == 0 ? FIRST_LINE_HEIGHT : TEXT_LINE_HEIGHT;
    }

    /**
     * A line with a key in it: a line of text, or the key with a pixel
     * clear above and below it where that is taller (GUI scale 1, where
     * the windows' key size is the GUI's own, and a pixel at 3 and 4).
     */
    private static int iconLineHeight() {
        return Math.max(TEXT_LINE_HEIGHT, (int)Math.ceil(
                LostTalesInputIconRenderer.windowHeight()) + 2);
    }

    /**
     * Draws one line, icons in place of their marked spans.
     *
     * <p>Each text run is drawn as its own string, so the formatting in force
     * where the icon interrupted is re-applied to what follows it.</p>
     */
    private static void drawLine(
            Minecraft minecraft, FontRenderer font,
            String line, int x, int y) {
        if (!LostTalesTooltipIcons.hasIcon(line)) {
            font.drawStringWithShadow(line, x, y, LostTalesColors.IVORY);
            return;
        }
        int textY = y + (iconLineHeight() - font.FONT_HEIGHT) / 2;
        int cursorX = x;
        int index = 0;
        String format = "";
        while (index < line.length()) {
            int start = line.indexOf(
                    LostTalesTooltipIcons.SPAN_START, index);
            if (start < 0) {
                drawSegment(font, format, line.substring(index),
                        cursorX, textY);
                return;
            }
            String before = line.substring(index, start);
            cursorX += drawSegment(font, format, before, cursorX, textY);
            format = LostTalesTooltipIcons.carryFormat(format, before);
            int end = line.indexOf(LostTalesTooltipIcons.SPAN_END, start);
            if (end < 0 || start + 1 >= line.length()) {
                // An unclosed span is text, and is drawn as the text it is.
                drawSegment(font, format, line.substring(start),
                        cursorX, textY);
                return;
            }
            int keyCode = LostTalesTooltipIcons.decodeKeyCode(
                    line.charAt(start + 1));
            cursorX += LostTalesTooltipIcons.drawIcon(
                    minecraft, keyCode, cursorX, textY);
            index = end + 1;
        }
    }

    private static int drawSegment(
            FontRenderer font, String format, String text, int x, int y) {
        if (text.isEmpty()) {
            return 0;
        }
        String drawn = format + text;
        font.drawStringWithShadow(drawn, x, y, LostTalesColors.IVORY);
        return font.getStringWidth(drawn);
    }

    /**
     * The vanilla tooltip frame, drawn from a {@link Gui} of our own because
     * its rectangle helpers and its depth belong to the instance drawing them.
     */
    private static final class Canvas extends Gui {
        void drawFrame(int x, int y, int width, int height) {
            this.zLevel = TOOLTIP_Z;
            drawGradientRect(x - 3, y - 4, x + width + 3, y - 3,
                    BACKGROUND_COLOR, BACKGROUND_COLOR);
            drawGradientRect(x - 3, y + height + 3,
                    x + width + 3, y + height + 4,
                    BACKGROUND_COLOR, BACKGROUND_COLOR);
            drawGradientRect(x - 3, y - 3, x + width + 3, y + height + 3,
                    BACKGROUND_COLOR, BACKGROUND_COLOR);
            drawGradientRect(x - 4, y - 3, x - 3, y + height + 3,
                    BACKGROUND_COLOR, BACKGROUND_COLOR);
            drawGradientRect(x + width + 3, y - 3,
                    x + width + 4, y + height + 3,
                    BACKGROUND_COLOR, BACKGROUND_COLOR);
            drawGradientRect(x - 3, y - 2, x - 2, y + height + 2,
                    BORDER_TOP_COLOR, BORDER_BOTTOM_COLOR);
            drawGradientRect(x + width + 2, y - 2,
                    x + width + 3, y + height + 2,
                    BORDER_TOP_COLOR, BORDER_BOTTOM_COLOR);
            drawGradientRect(x - 3, y - 3, x + width + 3, y - 2,
                    BORDER_TOP_COLOR, BORDER_TOP_COLOR);
            drawGradientRect(x - 3, y + height + 2,
                    x + width + 3, y + height + 3,
                    BORDER_BOTTOM_COLOR, BORDER_BOTTOM_COLOR);
            this.zLevel = 0.0F;
        }
    }
}
