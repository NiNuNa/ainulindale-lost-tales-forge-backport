package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/**
 * The hover card the map draws for whatever the pointer owns.
 *
 * <p>Replaces LOTR's own tooltip so every piece of text on the map carries
 * the shared HUD colours instead of two different sets, and so a marker can
 * show a description LOTR has no field for. Purely presentational: it renders
 * what the caller resolved and decides nothing about it.</p>
 */
@SideOnly(Side.CLIENT)
final class LostTalesMapMarkerTooltip {
    /** Gap between the marker's artwork and the top of the card. */
    static final int MARKER_CLEARANCE = 4;
    static final int PADDING = 4;
    static final int LINE_GAP = 1;
    /** Widest the card may grow before its body text wraps. */
    static final int MAX_WIDTH = 160;
    private static final int EDGE_MARGIN = 2;

    private LostTalesMapMarkerTooltip() {}

    /**
     * Draws a card centred under a point, kept inside the map viewport.
     *
     * @param anchorY bottom edge of the artwork the card belongs to
     */
    static void render(FontRenderer font, String title, String body,
                       float anchorX, float anchorY,
                       int mapXMin, int mapXMax,
                       int mapYMin, int mapYMax) {
        if (font == null || title == null || title.length() == 0) {
            return;
        }
        List<String> lines = layoutLines(font, title, body);
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.getStringWidth(line));
        }
        width += PADDING * 2;
        int height = PADDING * 2
                + lines.size() * font.FONT_HEIGHT
                + Math.max(0, lines.size() - 1) * LINE_GAP;

        int x = clamp(Math.round(anchorX - width / 2.0F),
                mapXMin + EDGE_MARGIN, mapXMax - EDGE_MARGIN - width);
        int y = clamp(Math.round(anchorY) + MARKER_CLEARANCE,
                mapYMin + EDGE_MARGIN, mapYMax - EDGE_MARGIN - height);

        LostTalesSkyrimUiStyle.drawPanel(x, y, width, height);
        int lineY = y + PADDING;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            font.drawStringWithShadow(line,
                    x + (width - font.getStringWidth(line)) / 2, lineY,
                    index == 0
                            ? LostTalesSkyrimUiStyle.TEXT_BRIGHT
                            : LostTalesSkyrimUiStyle.TEXT_MUTED);
            lineY += font.FONT_HEIGHT + LINE_GAP;
        }
    }

    /** Title first, then the body wrapped to the card's maximum width. */
    static List<String> layoutLines(
            FontRenderer font, String title, String body) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(title);
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.length() == 0) {
            return lines;
        }
        int wrapWidth = Math.max(1, MAX_WIDTH - PADDING * 2);
        for (Object wrapped : font.listFormattedStringToWidth(
                trimmed, wrapWidth)) {
            if (wrapped instanceof String
                    && ((String)wrapped).length() > 0) {
                lines.add((String)wrapped);
            }
        }
        return lines;
    }

    private static int clamp(int value, int minimum, int maximum) {
        // A card wider or taller than the viewport pins to the near edge
        // rather than inverting its bounds.
        return maximum < minimum
                ? minimum : Math.max(minimum, Math.min(maximum, value));
    }
}
