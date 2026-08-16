package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import org.lwjgl.opengl.GL11;

/** Draws and handles the map-only marker visibility strip. */
@SideOnly(Side.CLIENT)
final class LostTalesLotrMapLegend {
    static final int HEIGHT = 46;
    static final int GAP_ABOVE_CONTROL_BAR = 4;

    private static final int OUTER_MARGIN = 6;
    private static final int PANEL_PADDING = 4;
    private static final int TILE_GAP = 2;
    private static final int PREFERRED_TILE_WIDTH = 88;
    private static final int MINIMUM_TILE_WIDTH = 58;
    private static final int ARROW_WIDTH = 14;
    private static final int ICON_CENTER_Y = 13;
    private static final int LABEL_Y = 28;

    private LostTalesLotrMapLegend() {
    }

    static int getReservedHeight(LostTalesLotrMapGui gui) {
        return gui != null && gui.isMapLegendOpen()
                && LostTalesLotrMapLayout.isControlBarVisible(gui)
                && calculateLayout(
                        gui.width, gui.height,
                        LostTalesMapLegendRegistry.getCategories().size(),
                        gui.getMapLegendScrollIndex()).visible
                ? HEIGHT + GAP_ABOVE_CONTROL_BAR : 0;
    }

    /** Height followed by fixed overlays while the legend flies into place. */
    static int getAnimatedReservedHeight(LostTalesLotrMapGui gui) {
        int target = getReservedHeight(gui);
        if (target == 0) {
            return 0;
        }
        return Math.round(target * LostTalesMapPopupAnimation.easedProgress(
                gui.getMapLegendAnimationKey()));
    }

    static boolean render(
            LostTalesLotrMapGui gui, int mouseX, int mouseY) {
        if (gui == null || !gui.isMapLegendOpen()
                || !LostTalesLotrMapLayout.isControlBarVisible(gui)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft == null ? null : minecraft.fontRenderer;
        List<LostTalesMapLegendCategory> categories =
                LostTalesMapLegendRegistry.getCategories();
        if (minecraft == null || font == null || categories.isEmpty()) {
            return false;
        }

        Layout layout = calculateLayout(
                gui.width, gui.height, categories.size(),
                gui.getMapLegendScrollIndex());
        if (!layout.visible) {
            return false;
        }
        gui.setMapLegendScrollIndex(layout.firstIndex);
        int pivotX = layout.panelX + layout.panelWidth / 2;
        int pivotY = layout.panelY + layout.panelHeight / 2;
        int localMouseX = LostTalesMapPopupAnimation.inverseMouseX(
                gui.getMapLegendAnimationKey(), mouseX, pivotX);
        int localMouseY = LostTalesMapPopupAnimation.inverseMouseY(
                gui.getMapLegendAnimationKey(), mouseY, pivotY);

        beginUntranslatedRender(gui, pivotX, pivotY);
        try {
            LostTalesSkyrimUiStyle.drawPanelSoft(
                    layout.panelX, layout.panelY,
                    layout.panelWidth, layout.panelHeight);
            if (layout.showArrows) {
                drawArrow(font, layout.leftArrowX, layout.panelY,
                        false, layout.firstIndex > 0,
                        layout.containsLeftArrow(localMouseX, localMouseY));
                drawArrow(font, layout.rightArrowX, layout.panelY,
                        true,
                        layout.firstIndex + layout.visibleCount
                                < categories.size(),
                        layout.containsRightArrow(localMouseX, localMouseY));
            }
            for (int slot = 0; slot < layout.visibleCount; slot++) {
                int categoryIndex = layout.firstIndex + slot;
                if (categoryIndex >= categories.size()) {
                    break;
                }
                LostTalesMapLegendCategory category =
                        categories.get(categoryIndex);
                int tileX = layout.tileX(slot);
                boolean hovered = layout.containsTile(
                        slot, localMouseX, localMouseY);
                drawCategory(
                        minecraft, font, category,
                        tileX, layout.tileY, layout.tileWidth,
                        layout.tileHeight, hovered);
            }
        } finally {
            endUntranslatedRender();
        }
        return true;
    }

    static boolean handleMouseClick(
            LostTalesLotrMapGui gui, int mouseX, int mouseY, int button) {
        Layout layout = currentLayout(gui);
        if (!layout.visible) {
            return false;
        }
        int pivotX = layout.panelX + layout.panelWidth / 2;
        int pivotY = layout.panelY + layout.panelHeight / 2;
        mouseX = LostTalesMapPopupAnimation.inverseMouseX(
                gui.getMapLegendAnimationKey(), mouseX, pivotX);
        mouseY = LostTalesMapPopupAnimation.inverseMouseY(
                gui.getMapLegendAnimationKey(), mouseY, pivotY);
        if (!layout.containsPanel(mouseX, mouseY)) {
            return false;
        }
        if (button != 0) {
            return true;
        }
        List<LostTalesMapLegendCategory> categories =
                LostTalesMapLegendRegistry.getCategories();
        if (layout.containsLeftArrow(mouseX, mouseY)) {
            gui.setMapLegendScrollIndex(
                    Math.max(0, layout.firstIndex - 1));
            return true;
        }
        if (layout.containsRightArrow(mouseX, mouseY)) {
            int maximum = Math.max(0,
                    categories.size() - layout.visibleCount);
            gui.setMapLegendScrollIndex(
                    Math.min(maximum, layout.firstIndex + 1));
            return true;
        }
        for (int slot = 0; slot < layout.visibleCount; slot++) {
            if (!layout.containsTile(slot, mouseX, mouseY)) {
                continue;
            }
            int categoryIndex = layout.firstIndex + slot;
            if (categoryIndex < categories.size()) {
                LostTalesMapLegendRegistry.toggleCategory(
                        categories.get(categoryIndex).getId());
                gui.onMapLegendFiltersChanged();
            }
            return true;
        }
        return true;
    }

    static boolean handleMouseWheel(
            LostTalesLotrMapGui gui, int mouseX, int mouseY, int wheel) {
        Layout layout = currentLayout(gui);
        if (wheel == 0 || !layout.visible
                || !containsAnimatedPanel(gui, layout, mouseX, mouseY)) {
            return false;
        }
        if (!layout.showArrows) {
            return true;
        }
        int maximum = Math.max(0,
                LostTalesMapLegendRegistry.getCategories().size()
                        - layout.visibleCount);
        int direction = wheel > 0 ? -1 : 1;
        gui.setMapLegendScrollIndex(Math.max(0,
                Math.min(maximum, layout.firstIndex + direction)));
        return true;
    }

    private static Layout currentLayout(LostTalesLotrMapGui gui) {
        if (gui == null || !gui.isMapLegendOpen()
                || !LostTalesLotrMapLayout.isControlBarVisible(gui)) {
            return Layout.hidden();
        }
        return calculateLayout(
                gui.width, gui.height,
                LostTalesMapLegendRegistry.getCategories().size(),
                gui.getMapLegendScrollIndex());
    }

    private static void drawCategory(
            Minecraft minecraft, FontRenderer font,
            LostTalesMapLegendCategory category,
            int x, int y, int width, int height, boolean hovered) {
        boolean enabled = LostTalesMapLegendRegistry
                .isCategoryEnabled(category.getId());
        if (hovered) {
            Gui.drawRect(x, y, x + width, y + height,
                    LostTalesSkyrimUiStyle.PANEL_HOVER);
        }
        int border = enabled
                ? LostTalesSkyrimUiStyle.GOLD_DARK
                : LostTalesSkyrimUiStyle.BORDER_DIM;
        Gui.drawRect(x, y + height - 1, x + width, y + height, border);

        LostTalesLotrMapMarkerIconOverlay.renderEditorIconPreview(
                minecraft, category.getIcon().name(),
                category.getColorName(),
                x + width / 2.0F, y + ICON_CENTER_Y);

        String label = I18n.format(category.getTranslationKey());
        label = LostTalesSkyrimUiStyle.trimToWidth(
                font, label, Math.max(0, width - 4));
        int textColor = enabled
                ? LostTalesSkyrimUiStyle.TEXT_BRIGHT
                : LostTalesSkyrimUiStyle.TEXT_DIM;
        font.drawStringWithShadow(
                label, x + (width - font.getStringWidth(label)) / 2,
                y + LABEL_Y, textColor);
        if (enabled) {
            Gui.drawRect(x + 2, y + 2, x + 4, y + 4,
                    LostTalesSkyrimUiStyle.GOLD);
        } else {
            Gui.drawRect(x, y, x + width, y + height, 0x48000000);
            font.drawStringWithShadow(
                    "x", x + width - 7, y + 2,
                    LostTalesSkyrimUiStyle.RED);
        }
    }

    private static void drawArrow(
            FontRenderer font, int x, int panelY,
            boolean right, boolean active, boolean hovered) {
        if (hovered && active) {
            Gui.drawRect(x, panelY + 2, x + ARROW_WIDTH,
                    panelY + HEIGHT - 2,
                    LostTalesSkyrimUiStyle.PANEL_HOVER);
        }
        String arrow = right ? ">" : "<";
        int color = active
                ? LostTalesSkyrimUiStyle.TEXT_BRIGHT
                : LostTalesSkyrimUiStyle.TEXT_DIM;
        font.drawStringWithShadow(
                arrow,
                x + (ARROW_WIDTH - font.getStringWidth(arrow)) / 2,
                panelY + (HEIGHT - font.FONT_HEIGHT) / 2,
                color);
    }

    static Layout calculateLayout(
            int screenWidth, int screenHeight,
            int categoryCount, int requestedFirstIndex) {
        int availableWidth = Math.max(0,
                screenWidth - OUTER_MARGIN * 2);
        int panelBottom = screenHeight
                - LostTalesLotrMapControlBar.HEIGHT
                - GAP_ABOVE_CONTROL_BAR;
        if (categoryCount <= 0 || availableWidth < 60
                || panelBottom < HEIGHT) {
            return Layout.hidden();
        }

        int allTilesWidth = categoryCount * PREFERRED_TILE_WIDTH
                + Math.max(0, categoryCount - 1) * TILE_GAP;
        int desiredWidth = allTilesWidth + PANEL_PADDING * 2;
        boolean showArrows = desiredWidth > availableWidth;
        int panelWidth = Math.min(availableWidth, desiredWidth);
        int contentWidth = panelWidth - PANEL_PADDING * 2
                - (showArrows ? ARROW_WIDTH * 2 : 0);
        if (contentWidth <= 0) {
            return Layout.hidden();
        }

        int visibleCount;
        int tileWidth;
        if (!showArrows) {
            visibleCount = categoryCount;
            tileWidth = PREFERRED_TILE_WIDTH;
        } else {
            visibleCount = Math.max(1,
                    Math.min(categoryCount,
                            (contentWidth + TILE_GAP)
                                    / (MINIMUM_TILE_WIDTH + TILE_GAP)));
            tileWidth = Math.max(1,
                    (contentWidth
                            - Math.max(0, visibleCount - 1) * TILE_GAP)
                            / visibleCount);
        }
        int maximumFirst = Math.max(0, categoryCount - visibleCount);
        int firstIndex = Math.max(0,
                Math.min(requestedFirstIndex, maximumFirst));
        int panelX = (screenWidth - panelWidth) / 2;
        int panelY = panelBottom - HEIGHT;
        int tilesX = panelX + PANEL_PADDING
                + (showArrows ? ARROW_WIDTH : 0);
        int usedTileWidth = visibleCount * tileWidth
                + Math.max(0, visibleCount - 1) * TILE_GAP;
        int slack = Math.max(0, contentWidth - usedTileWidth);
        tilesX += slack / 2;
        return new Layout(
                true, panelX, panelY, panelWidth, HEIGHT,
                tilesX, panelY + 3, tileWidth, HEIGHT - 6,
                visibleCount, firstIndex, showArrows,
                panelX + PANEL_PADDING,
                panelX + panelWidth - PANEL_PADDING - ARROW_WIDTH);
    }

    private static boolean containsAnimatedPanel(
            LostTalesLotrMapGui gui, Layout layout,
            int mouseX, int mouseY) {
        int pivotX = layout.panelX + layout.panelWidth / 2;
        int pivotY = layout.panelY + layout.panelHeight / 2;
        return layout.containsPanel(
                LostTalesMapPopupAnimation.inverseMouseX(
                        gui.getMapLegendAnimationKey(), mouseX, pivotX),
                LostTalesMapPopupAnimation.inverseMouseY(
                        gui.getMapLegendAnimationKey(), mouseY, pivotY));
    }

    private static void beginUntranslatedRender(
            LostTalesLotrMapGui gui, int pivotX, int pivotY) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_TEXTURE_BIT);
        LostTalesMapPopupAnimation.push(
                gui.getMapLegendAnimationKey(), pivotX, pivotY);
    }

    private static void endUntranslatedRender() {
        LostTalesMapPopupAnimation.pop();
        GL11.glPopAttrib();
    }

    static final class Layout {
        final boolean visible;
        final int panelX;
        final int panelY;
        final int panelWidth;
        final int panelHeight;
        final int tilesX;
        final int tileY;
        final int tileWidth;
        final int tileHeight;
        final int visibleCount;
        final int firstIndex;
        final boolean showArrows;
        final int leftArrowX;
        final int rightArrowX;

        private Layout(
                boolean visible,
                int panelX, int panelY, int panelWidth, int panelHeight,
                int tilesX, int tileY, int tileWidth, int tileHeight,
                int visibleCount, int firstIndex, boolean showArrows,
                int leftArrowX, int rightArrowX) {
            this.visible = visible;
            this.panelX = panelX;
            this.panelY = panelY;
            this.panelWidth = panelWidth;
            this.panelHeight = panelHeight;
            this.tilesX = tilesX;
            this.tileY = tileY;
            this.tileWidth = tileWidth;
            this.tileHeight = tileHeight;
            this.visibleCount = visibleCount;
            this.firstIndex = firstIndex;
            this.showArrows = showArrows;
            this.leftArrowX = leftArrowX;
            this.rightArrowX = rightArrowX;
        }

        static Layout hidden() {
            return new Layout(false, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, false, 0, 0);
        }

        int tileX(int slot) {
            return this.tilesX + slot * (this.tileWidth + TILE_GAP);
        }

        boolean containsPanel(int mouseX, int mouseY) {
            return this.visible
                    && mouseX >= this.panelX
                    && mouseX < this.panelX + this.panelWidth
                    && mouseY >= this.panelY
                    && mouseY < this.panelY + this.panelHeight;
        }

        boolean containsTile(int slot, int mouseX, int mouseY) {
            int x = tileX(slot);
            return slot >= 0 && slot < this.visibleCount
                    && mouseX >= x && mouseX < x + this.tileWidth
                    && mouseY >= this.tileY
                    && mouseY < this.tileY + this.tileHeight;
        }

        boolean containsLeftArrow(int mouseX, int mouseY) {
            return this.showArrows
                    && mouseX >= this.leftArrowX
                    && mouseX < this.leftArrowX + ARROW_WIDTH
                    && mouseY >= this.panelY
                    && mouseY < this.panelY + this.panelHeight;
        }

        boolean containsRightArrow(int mouseX, int mouseY) {
            return this.showArrows
                    && mouseX >= this.rightArrowX
                    && mouseX < this.rightArrowX + ARROW_WIDTH
                    && mouseY >= this.panelY
                    && mouseY < this.panelY + this.panelHeight;
        }
    }
}
