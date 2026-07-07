package com.ninuna.losttales.gui.style;

import java.util.Locale;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
/**
 * Small primitive-drawn UI style used by the quest journal/HUD.
 *
 * The goal is an old-Forge friendly, Skyrim-inspired flat dark interface:
 * translucent charcoal panels, thin ivory rules, muted gold accents, and
 * diamond objective indicators. No external Skyrim assets are copied here.
 */
public final class LostTalesSkyrimUiStyle {
    public static final int PANEL_FILL = 0xBC07090C;
    public static final int PANEL_FILL_SOFT = 0x9007090C;
    public static final int PANEL_HOVER = 0x4AFFFFFF;
    public static final int PANEL_SELECTED = 0x642C3440;
    public static final int BORDER = 0x82D8D1C3;
    public static final int BORDER_DIM = 0x446D6A63;
    public static final int TEXT = 0xFFEEE8D8;
    public static final int TEXT_BRIGHT = 0xFFFFF8EC;
    public static final int TEXT_MUTED = 0xFF9F9A8D;
    public static final int TEXT_DIM = 0xFF706D65;
    public static final int GOLD = 0xFFD8B36A;
    public static final int GOLD_DARK = 0xFF8E7240;
    public static final int BLUE = 0xFFAFCFE1;
    public static final int GREEN = 0xFF9ECA91;
    public static final int RED = 0xFFC98778;
    public static final int PURPLE = 0xFFCBB3E6;
    public static final int BLACK_SHADOW = 0xC0000000;

    private LostTalesSkyrimUiStyle() {}

    public static void drawScreenShade(int width, int height) {
        Gui.drawRect(0, 0, width, height, 0x88000000);
        Gui.drawRect(0, 0, width, 28, 0x72000000);
        Gui.drawRect(0, height - 32, width, height, 0x72000000);
    }

    public static void drawPanel(int x, int y, int width, int height) {
        drawPanel(x, y, width, height, PANEL_FILL);
    }

    public static void drawPanelSoft(int x, int y, int width, int height) {
        drawPanel(x, y, width, height, PANEL_FILL_SOFT);
    }

    public static void drawPanel(int x, int y, int width, int height, int fillColor) {
        Gui.drawRect(x + 1, y + 1, x + width + 1, y + height + 1, 0x78000000);
        Gui.drawRect(x, y, x + width, y + height, fillColor);
        Gui.drawRect(x, y, x + width, y + 1, BORDER);
        Gui.drawRect(x, y + height - 1, x + width, y + height, BORDER_DIM);
        Gui.drawRect(x, y, x + 1, y + height, BORDER_DIM);
        Gui.drawRect(x + width - 1, y, x + width, y + height, BORDER_DIM);
        drawCorner(x, y, 1, 1);
        drawCorner(x + width - 1, y, -1, 1);
        drawCorner(x, y + height - 1, 1, -1);
        drawCorner(x + width - 1, y + height - 1, -1, -1);
    }

    private static void drawCorner(int x, int y, int sx, int sy) {
        int hx0 = Math.min(x, x + sx * 12);
        int hx1 = Math.max(x, x + sx * 12);
        int hy0 = Math.min(y, y + sy);
        int hy1 = Math.max(y, y + sy);
        int vx0 = Math.min(x, x + sx);
        int vx1 = Math.max(x, x + sx);
        int vy0 = Math.min(y, y + sy * 8);
        int vy1 = Math.max(y, y + sy * 8);
        Gui.drawRect(hx0, hy0, hx1 + 1, hy1 + 1, BORDER);
        Gui.drawRect(vx0, vy0, vx1 + 1, vy1 + 1, BORDER);
    }

    public static void drawSectionHeader(FontRenderer font, String text, int x, int y, int width) {
        String label = uppercase(text);
        font.drawStringWithShadow(label, x, y, GOLD);
        int lineX = x + font.getStringWidth(label) + 8;
        int lineY = y + 4;
        if (lineX < x + width) {
            Gui.drawRect(lineX, lineY, x + width, lineY + 1, BORDER_DIM);
        }
    }

    public static void drawCenteredHeader(FontRenderer font, String title, String subtitle, int width, int y) {
        String titleText = uppercase(title);
        int titleWidth = font.getStringWidth(titleText);
        int centerX = width / 2;
        Gui.drawRect(centerX - titleWidth / 2 - 32, y + 7, centerX - titleWidth / 2 - 8, y + 8, BORDER_DIM);
        Gui.drawRect(centerX + titleWidth / 2 + 8, y + 7, centerX + titleWidth / 2 + 32, y + 8, BORDER_DIM);
        font.drawStringWithShadow(titleText, centerX - titleWidth / 2, y + 2, TEXT_BRIGHT);
        if (subtitle != null && subtitle.length() > 0) {
            String subtitleText = uppercase(subtitle);
            font.drawStringWithShadow(subtitleText, centerX - font.getStringWidth(subtitleText) / 2, y + 15, TEXT_MUTED);
        }
    }

    public static void drawSelectionRow(int x, int y, int width, int height, boolean selected, boolean hovered) {
        if (selected) {
            Gui.drawRect(x, y, x + width, y + height, PANEL_SELECTED);
            Gui.drawRect(x, y, x + 2, y + height, GOLD);
            drawDiamond(x + 8, y + height / 2, GOLD);
        } else if (hovered) {
            Gui.drawRect(x, y, x + width, y + height, 0x362B2D31);
            Gui.drawRect(x, y, x + 1, y + height, BORDER_DIM);
        }
    }

    public static void drawDiamond(int centerX, int centerY, int color) {
        Gui.drawRect(centerX, centerY - 3, centerX + 1, centerY + 4, color);
        Gui.drawRect(centerX - 2, centerY - 1, centerX + 3, centerY + 2, color);
        Gui.drawRect(centerX - 1, centerY - 2, centerX + 2, centerY + 3, color);
    }

    public static void drawObjectiveIndicator(int x, int y, boolean complete, boolean active) {
        int color = complete ? GREEN : active ? GOLD : TEXT_DIM;
        drawDiamond(x, y, color);
        if (complete) {
            Gui.drawRect(x - 1, y, x, y + 1, TEXT_BRIGHT);
            Gui.drawRect(x, y + 1, x + 3, y + 2, TEXT_BRIGHT);
            Gui.drawRect(x + 2, y - 2, x + 3, y + 1, TEXT_BRIGHT);
        }
    }

    public static void drawProgressBar(int x, int y, int width, int current, int target, boolean complete) {
        if (target <= 1 || width <= 4) {
            return;
        }
        int clampedTarget = Math.max(1, target);
        int clampedCurrent = Math.max(0, Math.min(current, clampedTarget));
        int fill = width * clampedCurrent / clampedTarget;
        Gui.drawRect(x, y, x + width, y + 2, 0x553D3A33);
        if (fill > 0) {
            Gui.drawRect(x, y, x + fill, y + 2, complete ? GREEN : GOLD);
        }
        Gui.drawRect(x, y + 2, x + width, y + 3, 0x55000000);
    }

    public static void drawCompassFrame(int x, int y, int width, int height) {
        int barY = y + 10;
        Gui.drawRect(x + 4, barY - 3, x + width - 4, barY + 4, 0x5A000000);
        Gui.drawRect(x + 8, barY, x + width - 8, barY + 1, 0xB8D8D1C3);
        Gui.drawRect(x + width / 2 - 10, barY - 6, x + width / 2 + 10, barY - 5, BORDER_DIM);
        Gui.drawRect(x + width / 2 - 1, barY - 8, x + width / 2 + 1, barY + 6, TEXT_BRIGHT);
        drawDiamond(x + width / 2, barY - 9, GOLD);
    }

    public static String trimToWidth(FontRenderer font, String text, int width) {
        if (text == null || width <= 0) {
            return "";
        }
        if (font.getStringWidth(text) <= width) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = font.getStringWidth(ellipsis);
        String trimmed = text;
        while (trimmed.length() > 0 && font.getStringWidth(trimmed) + ellipsisWidth > width) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    public static String uppercase(String text) {
        return text == null ? "" : text.toUpperCase(Locale.ENGLISH);
    }
}
