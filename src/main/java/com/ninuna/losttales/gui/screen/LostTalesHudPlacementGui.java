package com.ninuna.losttales.gui.screen;

import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderer;
import com.ninuna.losttales.gui.hud.loot.LostTalesQuickLootHudRenderer;
import com.ninuna.losttales.gui.hud.mapmarker.LostTalesMapMarkerHudRenderer;
import com.ninuna.losttales.gui.hud.party.PartyHudLayout;
import com.ninuna.losttales.gui.hud.quest.LostTalesQuestHudRenderer;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/** Direct-manipulation editor for every movable Lost Tales HUD panel. */
public class LostTalesHudPlacementGui extends GuiScreen {
    private static final int GRID_MINOR_SPACING = 10;
    private static final int GRID_MAJOR_SPACING = 50;
    private static final int CENTER_SNAP_THRESHOLD = 6;
    private static final int KEYBOARD_NUDGE = 1;
    private static final int KEYBOARD_FAST_NUDGE = 10;

    private final GuiScreen parent;
    private HudElement selected;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean snappedToCenterX;
    private boolean snappedToCenterY;

    public LostTalesHudPlacementGui(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.dragging = false;
        this.snappedToCenterX = false;
        this.snappedToCenterY = false;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeEditor();
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            this.selected = this.selected == null
                    ? HudElement.COMPASS : this.selected.next();
            return;
        }
        if (this.selected == null) {
            return;
        }

        int step = LostTalesKeyBindings.isModifierKeyDown()
                ? KEYBOARD_FAST_NUDGE : KEYBOARD_NUDGE;
        if (keyCode == Keyboard.KEY_UP) {
            nudgeSelected(0, -step);
        } else if (keyCode == Keyboard.KEY_DOWN) {
            nudgeSelected(0, step);
        } else if (keyCode == Keyboard.KEY_LEFT) {
            nudgeSelected(-step, 0);
        } else if (keyCode == Keyboard.KEY_RIGHT) {
            nudgeSelected(step, 0);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            HudElement clicked = getElementAt(mouseX, mouseY);
            this.selected = clicked;
            this.dragging = clicked != null;
            this.snappedToCenterX = false;
            this.snappedToCenterY = false;
            if (clicked != null) {
                HudPlacementLayout.Bounds bounds = getBounds(clicked);
                this.dragOffsetX = mouseX - bounds.x;
                this.dragOffsetY = mouseY - bounds.y;
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY,
                                  int clickedMouseButton,
                                  long timeSinceLastClick) {
        if (this.dragging && clickedMouseButton == 0
                && this.selected != null) {
            HudPlacementLayout.Bounds bounds = getBounds(this.selected);
            HudPlacementLayout.DragResult position =
                    HudPlacementLayout.constrainDrag(
                            mouseX - this.dragOffsetX,
                            mouseY - this.dragOffsetY,
                            bounds.width,
                            bounds.height,
                            this.width,
                            this.height,
                            CENTER_SNAP_THRESHOLD);
            this.snappedToCenterX = position.snappedX;
            this.snappedToCenterY = position.snappedY;
            applyPosition(this.selected, position.x, position.y, bounds);
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton,
                timeSinceLastClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && this.dragging) {
            this.dragging = false;
            this.snappedToCenterX = false;
            this.snappedToCenterY = false;
            LostTalesConfig.save();
        }
        super.mouseMovedOrUp(mouseX, mouseY, mouseButton);
    }

    @Override
    public void onGuiClosed() {
        LostTalesConfig.save();
        super.onGuiClosed();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawPlacementGrid();

        HudElement hovered = getElementAt(mouseX, mouseY);
        for (HudElement element : HudElement.values()) {
            if (element != this.selected) {
                drawPreviewBox(element, element == hovered);
            }
        }
        if (this.selected != null) {
            drawPreviewBox(this.selected, this.selected == hovered);
        }

        drawSnapGuides();
        drawTitle();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPlacementGrid() {
        for (int x = 0; x <= this.width; x += GRID_MINOR_SPACING) {
            int color = x % GRID_MAJOR_SPACING == 0
                    ? 0x305E6875 : 0x183C4652;
            drawRect(x, 0, x + 1, this.height, color);
        }
        for (int y = 0; y <= this.height; y += GRID_MINOR_SPACING) {
            int color = y % GRID_MAJOR_SPACING == 0
                    ? 0x305E6875 : 0x183C4652;
            drawRect(0, y, this.width, y + 1, color);
        }
        drawRect(this.width / 2, 0,
                this.width / 2 + 1, this.height, 0x667F8790);
        drawRect(0, this.height / 2,
                this.width, this.height / 2 + 1, 0x667F8790);
    }

    private void drawSnapGuides() {
        if (!this.dragging) {
            return;
        }
        if (this.snappedToCenterX) {
            drawRect(this.width / 2 - 1, 0,
                    this.width / 2 + 1, this.height, 0xCCFFD37A);
        }
        if (this.snappedToCenterY) {
            drawRect(0, this.height / 2 - 1,
                    this.width, this.height / 2 + 1, 0xCCFFD37A);
        }
    }

    private void drawTitle() {
        String title = "Lost Tales HUD Placement";
        int titleWidth = this.fontRendererObj.getStringWidth(title);
        int left = (this.width - titleWidth) / 2 - 7;
        int right = (this.width + titleWidth) / 2 + 7;
        drawRect(left, 5, right, 21, 0xB0000000);
        drawCenteredString(this.fontRendererObj, title,
                this.width / 2, 9, 0xFFD37A);
    }

    private void drawPreviewBox(HudElement element, boolean hovered) {
        HudPlacementLayout.Bounds bounds = getBounds(element);
        boolean isSelected = element == this.selected;
        int fill = isSelected
                ? 0x99553818 : hovered ? 0x66333B44 : 0x40000000;
        int border = isSelected
                ? 0xFFFFD37A : hovered ? 0xDDFFFFFF : 0x99B8BEC6;
        drawRect(bounds.x, bounds.y,
                bounds.x + bounds.width, bounds.y + bounds.height, fill);
        drawBorder(bounds, border, isSelected ? 2 : 1);

        int textY = bounds.y
                + Math.max(2,
                (bounds.height - this.fontRendererObj.FONT_HEIGHT) / 2);
        this.fontRendererObj.drawStringWithShadow(
                element.displayName,
                bounds.x + 5,
                textY,
                isSelected ? 0xFFD37A : 0xFFFFFF);
    }

    private void drawBorder(HudPlacementLayout.Bounds bounds,
                            int color, int thickness) {
        drawRect(bounds.x, bounds.y,
                bounds.x + bounds.width, bounds.y + thickness, color);
        drawRect(bounds.x, bounds.y + bounds.height - thickness,
                bounds.x + bounds.width, bounds.y + bounds.height, color);
        drawRect(bounds.x, bounds.y,
                bounds.x + thickness, bounds.y + bounds.height, color);
        drawRect(bounds.x + bounds.width - thickness, bounds.y,
                bounds.x + bounds.width, bounds.y + bounds.height, color);
    }

    private HudElement getElementAt(int mouseX, int mouseY) {
        if (this.selected != null
                && contains(getBounds(this.selected), mouseX, mouseY)) {
            return this.selected;
        }
        HudElement[] elements = HudElement.values();
        for (int index = elements.length - 1; index >= 0; index--) {
            HudElement element = elements[index];
            if (element != this.selected
                    && contains(getBounds(element), mouseX, mouseY)) {
                return element;
            }
        }
        return null;
    }

    private static boolean contains(HudPlacementLayout.Bounds bounds,
                                    int x, int y) {
        return x >= bounds.x && x < bounds.x + bounds.width
                && y >= bounds.y && y < bounds.y + bounds.height;
    }

    private void nudgeSelected(int dx, int dy) {
        HudPlacementLayout.Bounds bounds = getBounds(this.selected);
        HudPlacementLayout.DragResult position =
                HudPlacementLayout.constrainDrag(
                        bounds.x + dx, bounds.y + dy,
                        bounds.width, bounds.height,
                        this.width, this.height, 0);
        applyPosition(this.selected, position.x, position.y, bounds);
        LostTalesConfig.save();
    }

    private void applyPosition(HudElement element, int x, int y,
                               HudPlacementLayout.Bounds bounds) {
        double offsetX = HudPlacementLayout.percentForPosition(
                x, this.width, bounds.width,
                element.horizontalMode(), element.pixelOffsetX());
        double offsetY = HudPlacementLayout.percentForPosition(
                y, this.height, bounds.height,
                element.verticalMode(), element.pixelOffsetY(this));
        LostTalesConfig.updateHudOffset(
                element.configKey, offsetX, offsetY);
    }

    private HudPlacementLayout.Bounds getBounds(HudElement element) {
        return HudPlacementLayout.calculate(
                this.width,
                this.height,
                element.width(),
                element.height(),
                element.offsetX(),
                element.offsetY(),
                element.horizontalMode(),
                element.verticalMode(),
                element.pixelOffsetX(),
                element.pixelOffsetY(this));
    }

    private void closeEditor() {
        LostTalesConfig.save();
        this.mc.displayGuiScreen(this.parent);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private enum HudElement {
        COMPASS("Compass", "compass"),
        PARTY("Party", "party"),
        QUICK_LOOT("Quick Loot", "quickloot"),
        QUEST_TRACKER("Quest Tracker", "quest"),
        QUEST_NOTIFICATIONS("Quest Notifications", "questnotifications"),
        LOCATION_DISCOVERY("Location Discovery", "mapdiscovery"),
        AREA_NAME("Area Name", "areanotice");

        private final String displayName;
        private final String configKey;

        HudElement(String displayName, String configKey) {
            this.displayName = displayName;
            this.configKey = configKey;
        }

        private HudElement next() {
            HudElement[] elements = values();
            return elements[(ordinal() + 1) % elements.length];
        }

        private int width() {
            if (this == COMPASS) {
                return LostTalesCompassHudRenderer.getPlacementWidth();
            }
            if (this == PARTY) {
                return PartyHudLayout.PANEL_WIDTH;
            }
            if (this == QUICK_LOOT) {
                return LostTalesQuickLootHudRenderer.getPlacementWidth();
            }
            if (this == QUEST_TRACKER) {
                return LostTalesQuestHudRenderer.getTrackerPlacementWidth();
            }
            if (this == QUEST_NOTIFICATIONS) {
                return LostTalesQuestHudRenderer
                        .getNotificationPlacementWidth();
            }
            if (this == LOCATION_DISCOVERY) {
                return LostTalesMapMarkerHudRenderer
                        .getDiscoveryPlacementWidth();
            }
            return LostTalesMapMarkerHudRenderer.getAreaPlacementWidth();
        }

        private int height() {
            if (this == COMPASS) {
                return LostTalesCompassHudRenderer.getPlacementHeight();
            }
            if (this == PARTY) {
                return PartyHudLayout.PANEL_PADDING * 2
                        + PartyHudLayout.ROW_HEIGHT * 3;
            }
            if (this == QUICK_LOOT) {
                return LostTalesQuickLootHudRenderer.getPlacementHeight();
            }
            if (this == QUEST_TRACKER) {
                return LostTalesQuestHudRenderer.getTrackerPlacementHeight();
            }
            if (this == QUEST_NOTIFICATIONS) {
                return LostTalesQuestHudRenderer
                        .getNotificationPlacementHeight();
            }
            if (this == LOCATION_DISCOVERY) {
                return LostTalesMapMarkerHudRenderer
                        .getDiscoveryPlacementHeight();
            }
            return LostTalesMapMarkerHudRenderer.getAreaPlacementHeight();
        }

        private double offsetX() {
            if (this == COMPASS) {
                return LostTalesConfig.compassHudOffsetX;
            }
            if (this == PARTY) {
                return LostTalesConfig.partyHudOffsetX;
            }
            if (this == QUICK_LOOT) {
                return LostTalesConfig.quickLootHudOffsetX;
            }
            if (this == QUEST_TRACKER) {
                return LostTalesConfig.questHudOffsetX;
            }
            if (this == QUEST_NOTIFICATIONS) {
                return LostTalesConfig.questNotificationHudOffsetX;
            }
            if (this == LOCATION_DISCOVERY) {
                return LostTalesConfig.mapDiscoveryHudOffsetX;
            }
            return LostTalesConfig.areaNoticeHudOffsetX;
        }

        private double offsetY() {
            if (this == COMPASS) {
                return LostTalesConfig.compassHudOffsetY;
            }
            if (this == PARTY) {
                return LostTalesConfig.partyHudOffsetY;
            }
            if (this == QUICK_LOOT) {
                return LostTalesConfig.quickLootHudOffsetY;
            }
            if (this == QUEST_TRACKER) {
                return LostTalesConfig.questHudOffsetY;
            }
            if (this == QUEST_NOTIFICATIONS) {
                return LostTalesConfig.questNotificationHudOffsetY;
            }
            if (this == LOCATION_DISCOVERY) {
                return LostTalesConfig.mapDiscoveryHudOffsetY;
            }
            return LostTalesConfig.areaNoticeHudOffsetY;
        }

        private HudPlacementLayout.CoordinateMode horizontalMode() {
            if (this == PARTY || this == QUICK_LOOT
                    || this == QUEST_TRACKER) {
                return HudPlacementLayout.CoordinateMode.SCREEN_PERCENT;
            }
            return HudPlacementLayout.CoordinateMode
                    .AVAILABLE_SPACE_PERCENT;
        }

        private HudPlacementLayout.CoordinateMode verticalMode() {
            if (this == COMPASS || this == PARTY || this == QUICK_LOOT
                    || this == QUEST_TRACKER) {
                return HudPlacementLayout.CoordinateMode.SCREEN_PERCENT;
            }
            return HudPlacementLayout.CoordinateMode
                    .AVAILABLE_SPACE_PERCENT;
        }

        private int pixelOffsetX() {
            return 0;
        }

        private int pixelOffsetY(LostTalesHudPlacementGui gui) {
            if (this == COMPASS) {
                return gui.fontRendererObj.FONT_HEIGHT
                        + LostTalesCompassHudRenderer
                        .MAP_MARKER_DISTANCE_LABEL_OFFSET_Y;
            }
            return 0;
        }
    }
}
