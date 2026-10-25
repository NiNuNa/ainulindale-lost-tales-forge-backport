package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiTheme;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.opengl.GL11;

/**
 * The card the map draws for whatever the pointer owns: a titled tip in
 * the window frame (LOOK-28), its name in the accent and its description
 * under it in ivory, broken where a tip breaks.
 *
 * <p>Replaces LOTR's own tooltip so every word on the map stands in the
 * windows' look, and so a marker can show a description LOTR has no
 * field for. Purely presentational: it draws what the caller resolved and
 * decides nothing about it.</p>
 */
@SideOnly(Side.CLIENT)
final class LostTalesMapMarkerTooltip {
    /** Gap between the marker's artwork and the top of the card. */
    static final int MARKER_CLEARANCE = 4;
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
            width = Math.max(width, WindowStyle.popupLineWidth(font, line));
        }
        int height = WindowStyle.popupLinesHeight(lines.size());

        // Kept fractional. The marker under the card moves by fractions of a
        // pixel as the map zooms; rounding the card's own position turns that
        // into a whole-pixel step every few frames, which reads as the card
        // shaking while the marker slides smoothly beneath it. The card is
        // laid out at the origin instead and the fraction is carried on the
        // matrix, so the two move together.
        float x = clamp(anchorX - width / 2.0F,
                mapXMin + EDGE_MARGIN, mapXMax - EDGE_MARGIN - width);
        float y = clamp(anchorY + MARKER_CLEARANCE,
                mapYMin + EDGE_MARGIN, mapYMax - EDGE_MARGIN - height);

        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.0F);
        try {
            WindowStyle.drawPopup(0, 0, width, height, 1.0F);
            LostTalesUiInk.beginContent();
            for (int index = 0; index < lines.size(); index++) {
                LostTalesUiInk.drawText(font, lines.get(index),
                        WindowStyle.POPUP_INSET,
                        WindowStyle.POPUP_INSET
                                + index * WindowStyle.LINE_HEIGHT,
                        index == 0 ? LostTalesUiTheme.accentRgb()
                                : LostTalesUiInk.IVORY, 255);
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** The name first, then the description broken where a tip breaks. */
    static List<String> layoutLines(
            FontRenderer font, String title, String body) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(title);
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.length() > 0) {
            lines.addAll(WindowStyle.tipLines(font, trimmed));
        }
        return lines;
    }

    static float clamp(float value, float minimum, float maximum) {
        // A card wider or taller than the viewport pins to the near edge
        // rather than inverting its bounds.
        return maximum < minimum
                ? minimum : Math.max(minimum, Math.min(maximum, value));
    }
}
