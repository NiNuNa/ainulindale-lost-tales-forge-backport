package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/**
 * The map's modal question: a shaded screen, one panel, a line of text and
 * three actions side by side.
 *
 * <p>Both map popups ask the player exactly this shape of question, so the
 * panel, its geometry and its button strip live here and the popups only
 * decide what the three actions mean. Two copies of this layout drifted apart
 * the moment either was adjusted.</p>
 */
@SideOnly(Side.CLIENT)
final class LostTalesMapChoicePrompt {
    private static final int SCREEN_SHADE = 0x66000000;
    private static final int MAX_WIDTH = 320;
    private static final int PANEL_HEIGHT = 70;
    private static final int SCREEN_MARGIN = 8;
    private static final int CONTENT_PADDING = 10;
    private static final int TITLE_TOP = 14;
    private static final int SUBTITLE_TOP = 24;
    private static final int DIVIDER_TOP = 35;
    private static final int BUTTON_TOP = 42;
    private static final int BUTTON_BOTTOM_MARGIN = 8;
    /**
     * Gap between the panel and the map point framed beside it, in GUI
     * pixels. Enough for the marker and the name above it to clear the panel,
     * and no more: the point of framing it there is that it reads as part of
     * the question.
     */
    private static final int ANCHOR_CLEARANCE = 26;
    /** Closest the framed point may come to the edge of the screen. */
    private static final int ANCHOR_SCREEN_MARGIN = 14;

    private LostTalesMapChoicePrompt() {}

    /**
     * Where a map point this popup is about should come to rest: centred just
     * above the panel.
     *
     * <p>Derived from the panel the popup actually lays out at this screen
     * size and GUI scale, not from a pixel row measured once. Sitting it just
     * clear of the panel rather than halfway to the top of the screen is what
     * makes the marker read as the subject of the question. Only when there is
     * no room above — a short screen, a large GUI scale — does it go
     * below.</p>
     */
    static float focusAnchorY(int screenWidth, int screenHeight) {
        Layout layout = calculateLayout(screenWidth, screenHeight);
        float above = layout.y - ANCHOR_CLEARANCE;
        if (above >= ANCHOR_SCREEN_MARGIN) {
            return above;
        }
        float below = layout.y + layout.height + ANCHOR_CLEARANCE;
        float floor = Math.max(0, screenHeight - ANCHOR_SCREEN_MARGIN);
        return below <= floor ? below
                : Math.max(0.0F, Math.min(floor, above));
    }

    /** Draws everything above the buttons: shade, panel, text and divider. */
    static void renderPanel(
            FontRenderer font, Layout layout, int screenWidth,
            int screenHeight, String title, String subtitle) {
        renderPanel(font, layout, screenWidth, screenHeight,
                title, subtitle, null);
    }

    /**
     * @param litArea {@code {x, y, width, height}} left out of the shade, or
     *                null to shade the whole screen
     */
    static void renderPanel(
            FontRenderer font, Layout layout, int screenWidth,
            int screenHeight, String title, String subtitle,
            int[] litArea) {
        drawShade(screenWidth, screenHeight, litArea);
        LostTalesSkyrimUiStyle.drawPanel(
                layout.x, layout.y, layout.width, layout.height);
        int contentWidth = Math.max(0,
                layout.width - CONTENT_PADDING * 2);
        drawCentered(font, layout, title, layout.y + TITLE_TOP,
                contentWidth, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        if (subtitle != null && subtitle.length() > 0) {
            drawCentered(font, layout, subtitle, layout.y + SUBTITLE_TOP,
                    contentWidth, LostTalesSkyrimUiStyle.TEXT_MUTED);
        }
        Gui.drawRect(
                layout.x + CONTENT_PADDING, layout.y + DIVIDER_TOP,
                layout.x + layout.width - CONTENT_PADDING,
                layout.y + DIVIDER_TOP + 1,
                LostTalesSkyrimUiStyle.BORDER_DIM);
    }

    /**
     * Dims the map behind the popup, leaving one rectangle at full strength.
     *
     * <p>The marker the question is about is inside that rectangle, so it
     * stays as readable as it was on the open map while everything around it
     * steps back. Drawn as the four bands around the opening rather than as a
     * hole punched through, which keeps it to plain rectangles.</p>
     */
    private static void drawShade(
            int screenWidth, int screenHeight, int[] litArea) {
        if (litArea == null || litArea.length < 4
                || litArea[2] <= 0 || litArea[3] <= 0) {
            Gui.drawRect(0, 0, screenWidth, screenHeight, SCREEN_SHADE);
            return;
        }
        int left = Math.max(0, Math.min(screenWidth, litArea[0]));
        int top = Math.max(0, Math.min(screenHeight, litArea[1]));
        int right = Math.max(left,
                Math.min(screenWidth, litArea[0] + litArea[2]));
        int bottom = Math.max(top,
                Math.min(screenHeight, litArea[1] + litArea[3]));
        Gui.drawRect(0, 0, screenWidth, top, SCREEN_SHADE);
        Gui.drawRect(0, bottom, screenWidth, screenHeight, SCREEN_SHADE);
        Gui.drawRect(0, top, left, bottom, SCREEN_SHADE);
        Gui.drawRect(right, top, screenWidth, bottom, SCREEN_SHADE);
    }

    private static void drawCentered(
            FontRenderer font, Layout layout, String text, int y,
            int contentWidth, int color) {
        String visible = LostTalesSkyrimUiStyle.trimToWidth(
                font, text, contentWidth);
        font.drawStringWithShadow(visible,
                layout.x + (layout.width
                        - font.getStringWidth(visible)) / 2,
                y, color);
    }

    static void drawButton(
            FontRenderer font, Bounds bounds, String label,
            boolean hovered, boolean enabled) {
        if (hovered && enabled) {
            Gui.drawRect(bounds.x, bounds.y,
                    bounds.x + bounds.width,
                    bounds.y + bounds.height,
                    LostTalesSkyrimUiStyle.PANEL_HOVER);
            Gui.drawRect(bounds.x, bounds.y,
                    bounds.x + bounds.width, bounds.y + 1,
                    LostTalesSkyrimUiStyle.BORDER);
        }
        String visible = LostTalesSkyrimUiStyle.trimToWidth(
                font, label, Math.max(0, bounds.width - 6));
        int color = enabled
                ? hovered
                        ? LostTalesSkyrimUiStyle.TEXT_BRIGHT
                        : LostTalesSkyrimUiStyle.TEXT
                : LostTalesSkyrimUiStyle.TEXT_DIM;
        font.drawStringWithShadow(visible,
                bounds.x + (bounds.width
                        - font.getStringWidth(visible)) / 2,
                bounds.y + (bounds.height - font.FONT_HEIGHT) / 2,
                color);
    }

    static Layout calculateLayout(int screenWidth, int screenHeight) {
        int availableWidth = Math.max(0,
                screenWidth - SCREEN_MARGIN * 2);
        int width = Math.min(MAX_WIDTH, availableWidth);
        int availableHeight = Math.max(0,
                screenHeight - SCREEN_MARGIN * 2);
        int height = Math.min(PANEL_HEIGHT, availableHeight);
        int x = Math.max(0, (screenWidth - width) / 2);
        int y = Math.max(0, (screenHeight - height) / 2);
        int contentWidth = Math.max(0, width - CONTENT_PADDING * 2);
        int buttonY = y + Math.min(BUTTON_TOP,
                Math.max(0, height - BUTTON_BOTTOM_MARGIN));
        int buttonHeight = Math.max(0,
                y + height - BUTTON_BOTTOM_MARGIN - buttonY);
        int firstWidth = contentWidth / 4;
        int secondWidth = contentWidth / 4;
        int thirdWidth = contentWidth - firstWidth - secondWidth;
        Bounds first = new Bounds(
                x + CONTENT_PADDING, buttonY, firstWidth, buttonHeight);
        Bounds second = new Bounds(
                first.x + first.width, buttonY,
                secondWidth, buttonHeight);
        Bounds third = new Bounds(
                second.x + second.width, buttonY,
                thirdWidth, buttonHeight);
        return new Layout(x, y, width, height, first, second, third);
    }

    /** Panel rectangle and the three action bounds inside it. */
    static final class Layout {
        final int x;
        final int y;
        final int width;
        final int height;
        final Bounds first;
        final Bounds second;
        final Bounds third;

        private Layout(int x, int y, int width, int height,
                       Bounds first, Bounds second, Bounds third) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.first = first;
            this.second = second;
            this.third = third;
        }
    }

    static final class Bounds {
        final int x;
        final int y;
        final int width;
        final int height;

        private Bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
        }

        boolean contains(int pointX, int pointY) {
            return this.width > 0 && this.height > 0
                    && pointX >= this.x && pointX < this.x + this.width
                    && pointY >= this.y && pointY < this.y + this.height;
        }
    }
}
