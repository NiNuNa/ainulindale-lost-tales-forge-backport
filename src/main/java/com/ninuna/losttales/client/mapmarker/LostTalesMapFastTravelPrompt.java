package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/** Modal, map-local confirmation shown before an existing travel request. */
@SideOnly(Side.CLIENT)
final class LostTalesMapFastTravelPrompt {
    private static final int SCREEN_SHADE = 0x66000000;
    private static final int MAX_WIDTH = 320;
    private static final int PANEL_HEIGHT = 70;
    private static final int SCREEN_MARGIN = 8;
    private static final int CONTENT_PADDING = 10;
    private static final int BUTTON_TOP = 42;
    private static final int BUTTON_BOTTOM_MARGIN = 8;

    enum Action {
        NONE,
        YES,
        NO,
        PLACE_MARKER
    }

    private final String destinationName;
    /**
     * Localization key explaining why travel is refused, or null while it is
     * offered. The client only decides what to show; the server re-derives
     * eligibility for every request it receives.
     */
    private final String blockedReasonKey;

    LostTalesMapFastTravelPrompt(String destinationName) {
        this(destinationName, null);
    }

    LostTalesMapFastTravelPrompt(
            String destinationName, String blockedReasonKey) {
        String normalized = destinationName == null
                ? "" : destinationName.trim();
        this.destinationName = normalized.length() == 0
                ? I18n.format("gui.losttales.map.fast_travel.unknown")
                : normalized;
        this.blockedReasonKey =
                blockedReasonKey == null || blockedReasonKey.length() == 0
                        ? null : blockedReasonKey;
    }

    String getDestinationName() {
        return this.destinationName;
    }

    boolean isTravelOffered() {
        return this.blockedReasonKey == null;
    }

    void render(LostTalesLotrMapGui gui, int mouseX, int mouseY,
                boolean canPlaceMarker) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (gui == null || minecraft == null
                || minecraft.fontRenderer == null) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        Layout layout = calculateLayout(gui.width, gui.height);
        Gui.drawRect(0, 0, gui.width, gui.height, SCREEN_SHADE);
        LostTalesSkyrimUiStyle.drawPanel(
                layout.x, layout.y, layout.width, layout.height);

        String title = I18n.format(
                "gui.losttales.map.fast_travel.prompt",
                this.destinationName);
        title = LostTalesSkyrimUiStyle.trimToWidth(
                font, title, Math.max(0,
                        layout.width - CONTENT_PADDING * 2));
        font.drawStringWithShadow(
                title,
                layout.x + (layout.width
                        - font.getStringWidth(title)) / 2,
                layout.y + 14,
                LostTalesSkyrimUiStyle.TEXT_BRIGHT);

        if (this.blockedReasonKey != null) {
            String reason = LostTalesSkyrimUiStyle.trimToWidth(
                    font, I18n.format(this.blockedReasonKey),
                    Math.max(0, layout.width - CONTENT_PADDING * 2));
            font.drawStringWithShadow(
                    reason,
                    layout.x + (layout.width
                            - font.getStringWidth(reason)) / 2,
                    layout.y + 24,
                    LostTalesSkyrimUiStyle.TEXT_MUTED);
        }

        Gui.drawRect(
                layout.x + CONTENT_PADDING, layout.y + 35,
                layout.x + layout.width - CONTENT_PADDING,
                layout.y + 36,
                LostTalesSkyrimUiStyle.BORDER_DIM);
        drawButton(font, layout.yes,
                I18n.format("gui.losttales.map.fast_travel.yes"),
                layout.yes.contains(mouseX, mouseY),
                isTravelOffered());
        drawButton(font, layout.no,
                I18n.format("gui.losttales.map.fast_travel.no"),
                layout.no.contains(mouseX, mouseY), true);
        drawButton(font, layout.placeMarker,
                I18n.format(
                        "gui.losttales.map.fast_travel.place_marker"),
                layout.placeMarker.contains(mouseX, mouseY),
                canPlaceMarker);
    }

    Action mouseClicked(int screenWidth, int screenHeight,
                        int mouseX, int mouseY, int button,
                        boolean canPlaceMarker) {
        if (button != 0) {
            return Action.NONE;
        }
        Layout layout = calculateLayout(screenWidth, screenHeight);
        if (layout.yes.contains(mouseX, mouseY)) {
            return isTravelOffered() ? Action.YES : Action.NONE;
        }
        if (layout.no.contains(mouseX, mouseY)) {
            return Action.NO;
        }
        if (canPlaceMarker
                && layout.placeMarker.contains(mouseX, mouseY)) {
            return Action.PLACE_MARKER;
        }
        return Action.NONE;
    }

    Action keyTyped(int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE
                || keyCode == Keyboard.KEY_N) {
            return Action.NO;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER
                || keyCode == Keyboard.KEY_Y) {
            return isTravelOffered() ? Action.YES : Action.NONE;
        }
        return Action.NONE;
    }

    private static void drawButton(
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
        font.drawStringWithShadow(
                visible,
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
        int contentWidth = Math.max(0,
                width - CONTENT_PADDING * 2);
        int buttonY = y + Math.min(BUTTON_TOP,
                Math.max(0, height - BUTTON_BOTTOM_MARGIN));
        int buttonHeight = Math.max(0,
                y + height - BUTTON_BOTTOM_MARGIN - buttonY);
        int firstWidth = contentWidth / 4;
        int secondWidth = contentWidth / 4;
        int thirdWidth = contentWidth - firstWidth - secondWidth;
        Bounds yes = new Bounds(
                x + CONTENT_PADDING, buttonY,
                firstWidth, buttonHeight);
        Bounds no = new Bounds(
                yes.x + yes.width, buttonY,
                secondWidth, buttonHeight);
        Bounds place = new Bounds(
                no.x + no.width, buttonY,
                thirdWidth, buttonHeight);
        return new Layout(x, y, width, height, yes, no, place);
    }

    static final class Layout {
        final int x;
        final int y;
        final int width;
        final int height;
        final Bounds yes;
        final Bounds no;
        final Bounds placeMarker;

        private Layout(int x, int y, int width, int height,
                       Bounds yes, Bounds no, Bounds placeMarker) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.yes = yes;
            this.no = no;
            this.placeMarker = placeMarker;
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
