package com.ninuna.losttales.client.gui.tooltip;

import com.ninuna.losttales.client.input.LostTalesInputIconRenderer;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Draws the tooltips that carry a key icon, in place of the vanilla one.
 *
 * <p>Called from the head of {@code GuiScreen.drawHoveringText} by the
 * coremod: a tooltip with no {@link LostTalesTooltipIcons} marker in it is
 * declined and vanilla draws it exactly as before, so the only tooltips that
 * come through here are Lost Tales' own.</p>
 *
 * <p>Taking the whole tooltip rather than overlaying the icon afterwards is
 * what keeps the box honest. The icon is a pixel taller than a line of text,
 * so the line it sits on has to be measured taller and the box grown to match
 * — measurement and drawing are the same pass here, and cannot disagree.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesTooltipHooks {
    /** A plain line of text, and the step to the next one; vanilla's. */
    private static final int TEXT_LINE_HEIGHT = 10;
    /** The first line is measured short, and the gap under it added once. */
    private static final int FIRST_LINE_HEIGHT = 8;
    private static final int TITLE_GAP = 2;
    /** An icon line is as tall as the artwork, with a pixel either side. */
    private static final int ICON_LINE_PADDING = 1;
    private static final int ICON_LINE_HEIGHT = Math.max(TEXT_LINE_HEIGHT,
            LostTalesInputIconRenderer.BASE_ICON_HEIGHT
                    + ICON_LINE_PADDING * 2);

    private static final int BACKGROUND_COLOR = 0xF0100010;
    private static final int BORDER_TOP_COLOR = 0x505000FF;
    private static final int BORDER_BOTTOM_COLOR =
            (BORDER_TOP_COLOR & 0xFEFEFE) >> 1
                    | BORDER_TOP_COLOR & 0xFF000000;
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
                    || !containsIcon(lines)) {
                return false;
            }
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null) {
                return false;
            }
            draw(minecraft, gui, lines, mouseX, mouseY, font);
            return true;
        } catch (Throwable throwable) {
            // A tooltip that cannot be drawn here is still vanilla's to draw,
            // and a broken one must not take the screen down with it.
            return false;
        }
    }

    private static boolean containsIcon(List lines) {
        for (int index = 0; index < lines.size(); index++) {
            Object value = lines.get(index);
            if (value instanceof String
                    && LostTalesTooltipIcons.hasIcon((String)value)) {
                return true;
            }
        }
        return false;
    }

    private static void draw(
            Minecraft minecraft, GuiScreen gui, List lines,
            int mouseX, int mouseY, FontRenderer font) {
        int lineCount = lines.size();
        int width = 0;
        int height = 0;
        for (int index = 0; index < lineCount; index++) {
            String line = lineAt(lines, index);
            width = Math.max(width, LostTalesTooltipIcons.measureLine(
                    minecraft, font, line));
            height += lineHeight(line, index);
        }
        if (lineCount > 1) {
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
            int lineY = y;
            for (int index = 0; index < lineCount; index++) {
                String line = lineAt(lines, index);
                drawLine(minecraft, font, line, x, lineY);
                lineY += lineHeight(line, index);
                if (index == 0 && lineCount > 1) {
                    lineY += TITLE_GAP;
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

    private static int lineHeight(String line, int index) {
        if (LostTalesTooltipIcons.hasIcon(line)) {
            return ICON_LINE_HEIGHT;
        }
        return index == 0 ? FIRST_LINE_HEIGHT : TEXT_LINE_HEIGHT;
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
            font.drawStringWithShadow(line, x, y, -1);
            return;
        }
        int textY = y + (ICON_LINE_HEIGHT - font.FONT_HEIGHT) / 2;
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
                    minecraft, keyCode, cursorX, y + ICON_LINE_PADDING);
            index = end + 1;
        }
    }

    private static int drawSegment(
            FontRenderer font, String format, String text, int x, int y) {
        if (text.isEmpty()) {
            return 0;
        }
        String drawn = format + text;
        font.drawStringWithShadow(drawn, x, y, -1);
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
