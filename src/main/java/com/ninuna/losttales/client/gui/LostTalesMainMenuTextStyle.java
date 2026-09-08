package com.ninuna.losttales.client.gui;

import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import java.util.regex.Pattern;

/** Palette text and translucent shadows for vanilla and LOTR's main menus. */
public final class LostTalesMainMenuTextStyle {
    private static final Pattern COLOR_CODES = Pattern.compile("(?i)\u00a7[0-9a-fr]");

    private LostTalesMainMenuTextStyle() {}

    /** Replaces Gui.drawString at main-menu call sites only. */
    public static void drawString(Gui gui, FontRenderer font, String text,
                                  int x, int y, int color) {
        if (font == null || text == null) {
            return;
        }
        String label = COLOR_CODES.matcher(text).replaceAll("");
        int alpha = (color & 0xFC000000) == 0 ? 255 : color >>> 24;
        int shadowAlpha = Math.round(alpha * LostTalesColors.SHADOW_OPACITY);
        if (shadowAlpha >= 4) {
            LostTalesSkyrimUiStyle.beginContent();
            font.drawString(label, x + 1, y + 1,
                    LostTalesColors.withAlpha(LostTalesColors.HUD_SHADOW, shadowAlpha));
        }
        LostTalesSkyrimUiStyle.beginContent();
        font.drawString(label, x, y,
                LostTalesColors.withAlpha(textColor(color), alpha));
    }

    /** Keeps vanilla's centered splash centered after palette substitution. */
    public static void drawCenteredString(Gui gui, FontRenderer font, String text,
                                          int x, int y, int color) {
        if (font != null && text != null) {
            String label = COLOR_CODES.matcher(text).replaceAll("");
            drawString(gui, font, label, x - font.getStringWidth(label) / 2, y, color);
        }
    }

    private static int textColor(int color) {
        // Vanilla's yellow splash keeps its emphasis in the shared palette.
        return (color & 0xFFFFFF) == 0xFFFF00
                ? LostTalesColors.HONEY : LostTalesColors.IVORY;
    }
}
