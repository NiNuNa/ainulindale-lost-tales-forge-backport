package com.ninuna.losttales.gui.screen;

import com.ninuna.losttales.client.chat.ChatWindowLayout;
import com.ninuna.losttales.client.chat.ChatWindowLayoutStore;
import com.ninuna.losttales.client.chat.ChatWindowPlacement;
import com.ninuna.losttales.client.gui.LostTalesGuiPointerTargets;
import com.ninuna.losttales.client.gui.LostTalesPointerInteractable;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderer;
import com.ninuna.losttales.gui.hud.loot.LostTalesQuickLootHudRenderer;
import com.ninuna.losttales.gui.hud.LostTalesNotificationHud;
import com.ninuna.losttales.gui.hud.party.PartyHudLayout;
import com.ninuna.losttales.gui.hud.quest.LostTalesQuestHudRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * Direct-manipulation editor for every movable Lost Tales HUD panel. The
 * fixed panels read and write their percent offsets in the config. The
 * feed the closed chat shows is an element too: it reads and writes the
 * feed position in {@link ChatWindowLayout}, the one the closed chat
 * draws it at, and drags in fractional pixels. Chat windows are part of
 * the chat screen, not the HUD, so they are not here; they are moved and
 * resized in the chat screen itself.
 */
public class LostTalesHudPlacementGui extends GuiScreen
        implements LostTalesPointerInteractable {
    private static final int GRID_MINOR_SPACING = 10;
    private static final int GRID_MAJOR_SPACING = 50;
    private static final int CENTER_SNAP_THRESHOLD = 6;
    private static final int KEYBOARD_NUDGE = 1;
    private static final int KEYBOARD_FAST_NUDGE = 10;

    private final GuiScreen parent;
    private final ChatFeedElement feed = new ChatFeedElement();
    /** The fixed panels, then the closed-chat feed; fixed for the screen. */
    private final List<Placeable> elements = new ArrayList<Placeable>();
    private Placeable selected;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean snappedToCenterX;
    private boolean snappedToCenterY;

    public LostTalesHudPlacementGui(GuiScreen parent) {
        this.parent = parent;
        for (HudElement element : HudElement.values()) {
            this.elements.add(element);
        }
        this.elements.add(this.feed);
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
            int index = this.selected == null ? -1
                    : this.elements.indexOf(this.selected);
            this.selected = this.elements.isEmpty() ? null
                    : this.elements.get((index + 1) % this.elements.size());
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
            Placeable clicked = getElementAt(mouseX, mouseY);
            this.selected = clicked;
            this.dragging = clicked != null;
            this.snappedToCenterX = false;
            this.snappedToCenterY = false;
            if (clicked != null) {
                if (clicked.precise()) {
                    ChatWindowPlacement.Box box = clicked.preciseBounds(this);
                    this.dragOffsetX = ChatWindowPlacement.preciseMouseX(
                            this.mc, this.width) - box.x;
                    this.dragOffsetY = ChatWindowPlacement.preciseMouseY(
                            this.mc, this.height) - box.y;
                } else {
                    HudPlacementLayout.Bounds bounds = getBounds(clicked);
                    this.dragOffsetX = mouseX - bounds.x;
                    this.dragOffsetY = mouseY - bounds.y;
                }
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /**
     * A press on any element selects it and starts a drag.
     * {@link #getElementAt} decides it here as it does for the hover
     * outline and the click.
     */
    @Override
    public boolean isPointerOverInteractable(int mouseX, int mouseY) {
        return getElementAt(mouseX, mouseY) != null
                || LostTalesGuiPointerTargets.isOverEnabledButton(
                        this, mouseX, mouseY);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY,
                                  int clickedMouseButton,
                                  long timeSinceLastClick) {
        if (this.dragging && clickedMouseButton == 0
                && this.selected != null) {
            if (this.selected.precise()) {
                dragPrecise();
                return;
            }
            HudPlacementLayout.Bounds bounds = getBounds(this.selected);
            HudPlacementLayout.DragResult position =
                    HudPlacementLayout.constrainDrag(
                            mouseX - (int)Math.round(this.dragOffsetX),
                            mouseY - (int)Math.round(this.dragOffsetY),
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

    /** The feed follows the raw mouse in fractional pixels. */
    private void dragPrecise() {
        ChatWindowPlacement.Box box = this.selected.preciseBounds(this);
        HudPlacementLayout.PreciseDragResult position =
                HudPlacementLayout.constrainDrag(
                        ChatWindowPlacement.preciseMouseX(this.mc, this.width)
                                - this.dragOffsetX,
                        ChatWindowPlacement.preciseMouseY(this.mc, this.height)
                                - this.dragOffsetY,
                        box.width, (int)Math.round(box.height),
                        this.width, this.height,
                        CENTER_SNAP_THRESHOLD);
        this.snappedToCenterX = position.snappedX;
        this.snappedToCenterY = position.snappedY;
        this.selected.moveTo(position.x, position.y, this);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && this.dragging) {
            this.dragging = false;
            this.snappedToCenterX = false;
            this.snappedToCenterY = false;
            if (this.selected != null) {
                this.selected.persist();
            }
        }
        super.mouseMovedOrUp(mouseX, mouseY, mouseButton);
    }

    @Override
    public void onGuiClosed() {
        persistAll();
        super.onGuiClosed();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawPlacementGrid();

        Placeable hovered = getElementAt(mouseX, mouseY);
        for (Placeable element : this.elements) {
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
        /*
         * Anchor the grid on the centre guides instead of the top-left corner.
         * The guides sit at width / 2 and height / 2, which is a multiple of the
         * grid spacing only by coincidence, so a corner-anchored grid meets the
         * centre at an arbitrary offset and the two halves do not mirror each
         * other. Stepping outwards from the centre keeps the cells uniform
         * around the axes a panel actually snaps to, and leaves any partial cell
         * at the screen edges where it is harmless.
         */
        int centreX = this.width / 2;
        int centreY = this.height / 2;
        for (int dx = 0; centreX - dx >= 0 || centreX + dx <= this.width;
                dx += GRID_MINOR_SPACING) {
            int color = dx % GRID_MAJOR_SPACING == 0
                    ? 0x305E6875 : 0x183C4652;
            if (centreX - dx >= 0) {
                drawRect(centreX - dx, 0,
                        centreX - dx + 1, this.height, color);
            }
            if (dx > 0 && centreX + dx <= this.width) {
                drawRect(centreX + dx, 0,
                        centreX + dx + 1, this.height, color);
            }
        }
        for (int dy = 0; centreY - dy >= 0 || centreY + dy <= this.height;
                dy += GRID_MINOR_SPACING) {
            int color = dy % GRID_MAJOR_SPACING == 0
                    ? 0x305E6875 : 0x183C4652;
            if (centreY - dy >= 0) {
                drawRect(0, centreY - dy,
                        this.width, centreY - dy + 1, color);
            }
            if (dy > 0 && centreY + dy <= this.height) {
                drawRect(0, centreY + dy,
                        this.width, centreY + dy + 1, color);
            }
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

    private void drawPreviewBox(Placeable element, boolean hovered) {
        HudPlacementLayout.Bounds bounds = getBounds(element);
        boolean isSelected = element == this.selected;
        int fill = isSelected
                ? 0x99553818 : hovered ? 0x66333B44 : 0x40000000;
        int border = isSelected
                ? 0xFFFFD37A : hovered ? 0xDDFFFFFF : 0x99B8BEC6;
        drawRect(bounds.x, bounds.y,
                bounds.x + bounds.width, bounds.y + bounds.height, fill);
        drawBorder(bounds, border, isSelected ? 2 : 1);

        String label = element.displayName();
        int textY = bounds.y
                + Math.max(2,
                (bounds.height - this.fontRendererObj.FONT_HEIGHT) / 2);
        this.fontRendererObj.drawStringWithShadow(
                label,
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

    private Placeable getElementAt(int mouseX, int mouseY) {
        if (this.selected != null
                && contains(getBounds(this.selected), mouseX, mouseY)) {
            return this.selected;
        }
        for (int index = this.elements.size() - 1; index >= 0; index--) {
            Placeable element = this.elements.get(index);
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
        if (this.selected.precise()) {
            ChatWindowPlacement.Box box = this.selected.preciseBounds(this);
            HudPlacementLayout.PreciseDragResult position =
                    HudPlacementLayout.constrainDrag(
                            box.x + dx, box.y + dy, box.width,
                            (int)Math.round(box.height),
                            this.width, this.height, 0);
            this.selected.moveTo(position.x, position.y, this);
            this.selected.persist();
            return;
        }
        HudPlacementLayout.Bounds bounds = getBounds(this.selected);
        HudPlacementLayout.DragResult position =
                HudPlacementLayout.constrainDrag(
                        bounds.x + dx, bounds.y + dy,
                        bounds.width, bounds.height,
                        this.width, this.height, 0);
        applyPosition(this.selected, position.x, position.y, bounds);
        this.selected.persist();
    }

    private void applyPosition(Placeable element, int x, int y,
                               HudPlacementLayout.Bounds bounds) {
        double offsetX = HudPlacementLayout.percentForPosition(
                x, this.width, bounds.width,
                element.horizontalMode(), element.pixelOffsetX());
        double offsetY = HudPlacementLayout.percentForPosition(
                y, this.height, bounds.height,
                element.verticalMode(), element.pixelOffsetY(this));
        element.apply(offsetX, offsetY);
    }

    private HudPlacementLayout.Bounds getBounds(Placeable element) {
        if (element.precise()) {
            ChatWindowPlacement.Box box = element.preciseBounds(this);
            return HudPlacementLayout.bounds((int)Math.floor(box.x),
                    (int)Math.floor(box.y), box.width,
                    (int)Math.round(box.height));
        }
        return HudPlacementLayout.calculate(
                this.width,
                this.height,
                element.width(this),
                element.height(this),
                element.offsetX(),
                element.offsetY(),
                element.horizontalMode(),
                element.verticalMode(),
                element.pixelOffsetX(),
                element.pixelOffsetY(this));
    }

    /** Saves the config, and the feed position when the feed has moved. */
    private void persistAll() {
        LostTalesConfig.save();
        this.feed.persist();
    }

    private void closeEditor() {
        persistAll();
        this.mc.displayGuiScreen(this.parent);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * One movable box: where it is, how big, and where that is stored.
     * Config panels use the integer percent layout; the feed is
     * <em>precise</em> and supplies a fractional box of its own.
     */
    private interface Placeable {
        String displayName();
        int width(LostTalesHudPlacementGui gui);
        int height(LostTalesHudPlacementGui gui);
        double offsetX();
        double offsetY();
        HudPlacementLayout.CoordinateMode horizontalMode();
        HudPlacementLayout.CoordinateMode verticalMode();
        int pixelOffsetX();
        int pixelOffsetY(LostTalesHudPlacementGui gui);
        /** Live update while dragging; nothing is written yet. */
        void apply(double offsetX, double offsetY);
        /** Writes the position out. */
        void persist();
        /** Whether the element positions itself in fractional pixels. */
        boolean precise();
        ChatWindowPlacement.Box preciseBounds(LostTalesHudPlacementGui gui);
        /** Live fractional move of the top-left corner. */
        void moveTo(double x, double y, LostTalesHudPlacementGui gui);
    }

    /**
     * The closed-chat feed, positioned through the chat's own layout. It
     * writes the chat layout file only after this editor has moved it,
     * so opening and closing the editor leaves that file alone.
     */
    private static final class ChatFeedElement implements Placeable {
        /** Whether the feed has moved since its position was last written. */
        private boolean unsaved;

        @Override
        public String displayName() {
            return StatCollector.translateToLocal(
                    "gui.losttales.hud.placement.chat_feed");
        }

        @Override
        public int width(LostTalesHudPlacementGui gui) {
            return preciseBounds(gui).width;
        }

        @Override
        public int height(LostTalesHudPlacementGui gui) {
            return (int)Math.round(preciseBounds(gui).height);
        }

        @Override
        public double offsetX() {
            return ChatWindowLayout.feedOffsetX();
        }

        @Override
        public double offsetY() {
            return ChatWindowLayout.feedOffsetY();
        }

        @Override
        public HudPlacementLayout.CoordinateMode horizontalMode() {
            return HudPlacementLayout.CoordinateMode.AVAILABLE_SPACE_PERCENT;
        }

        @Override
        public HudPlacementLayout.CoordinateMode verticalMode() {
            return HudPlacementLayout.CoordinateMode.AVAILABLE_SPACE_PERCENT;
        }

        @Override
        public int pixelOffsetX() {
            return 0;
        }

        @Override
        public int pixelOffsetY(LostTalesHudPlacementGui gui) {
            return 0;
        }

        @Override
        public void apply(double offsetX, double offsetY) {
            double previousX = ChatWindowLayout.feedOffsetX();
            double previousY = ChatWindowLayout.feedOffsetY();
            ChatWindowLayout.setFeedPosition(offsetX, offsetY, false);
            if (ChatWindowLayout.feedOffsetX() != previousX
                    || ChatWindowLayout.feedOffsetY() != previousY) {
                this.unsaved = true;
            }
        }

        @Override
        public void persist() {
            if (!this.unsaved) {
                return;
            }
            this.unsaved = false;
            // apply() has already moved the feed; the store writes it
            // without dropping windows it could not place yet.
            ChatWindowLayoutStore.saveFeedPosition();
        }

        @Override
        public boolean precise() {
            return true;
        }

        @Override
        public ChatWindowPlacement.Box preciseBounds(
                LostTalesHudPlacementGui gui) {
            return ChatWindowPlacement.feedBounds(gui.mc, gui.width,
                    gui.height);
        }

        @Override
        public void moveTo(double x, double y, LostTalesHudPlacementGui gui) {
            ChatWindowPlacement.Box box = preciseBounds(gui);
            apply(ChatWindowPlacement.feedPercentX(x, gui.mc, gui.width),
                    ChatWindowPlacement.feedPercentY(y + box.height, gui.mc,
                            gui.height));
        }
    }

    private enum HudElement implements Placeable {
        COMPASS("Compass", "compass"),
        PARTY("Party", "party"),
        QUICK_LOOT("Quick Loot", "quickloot"),
        QUEST_TRACKER("Quest Tracker", "quest"),
        /** Quest banners, location discoveries and area names, one slot. */
        NOTIFICATIONS("Notifications", "notifications");

        private final String label;
        private final String configKey;

        HudElement(String label, String configKey) {
            this.label = label;
            this.configKey = configKey;
        }

        @Override
        public String displayName() {
            return this.label;
        }

        @Override
        public int width(LostTalesHudPlacementGui gui) {
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
            return LostTalesNotificationHud.getPlacementWidth();
        }

        @Override
        public int height(LostTalesHudPlacementGui gui) {
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
            return LostTalesNotificationHud.getPlacementHeight();
        }

        @Override
        public double offsetX() {
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
            return LostTalesConfig.notificationHudOffsetX;
        }

        @Override
        public double offsetY() {
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
            return LostTalesConfig.notificationHudOffsetY;
        }

        @Override
        public HudPlacementLayout.CoordinateMode horizontalMode() {
            if (this == PARTY || this == QUICK_LOOT
                    || this == QUEST_TRACKER) {
                return HudPlacementLayout.CoordinateMode.SCREEN_PERCENT;
            }
            return HudPlacementLayout.CoordinateMode
                    .AVAILABLE_SPACE_PERCENT;
        }

        @Override
        public HudPlacementLayout.CoordinateMode verticalMode() {
            if (this == COMPASS || this == PARTY || this == QUICK_LOOT
                    || this == QUEST_TRACKER) {
                return HudPlacementLayout.CoordinateMode.SCREEN_PERCENT;
            }
            return HudPlacementLayout.CoordinateMode
                    .AVAILABLE_SPACE_PERCENT;
        }

        @Override
        public int pixelOffsetX() {
            return 0;
        }

        @Override
        public int pixelOffsetY(LostTalesHudPlacementGui gui) {
            if (this == COMPASS) {
                return gui.fontRendererObj.FONT_HEIGHT
                        + LostTalesCompassHudRenderer
                        .MAP_MARKER_DISTANCE_LABEL_OFFSET_Y;
            }
            return 0;
        }

        @Override
        public void apply(double offsetX, double offsetY) {
            LostTalesConfig.updateHudOffset(this.configKey, offsetX, offsetY);
        }

        @Override
        public void persist() {
            LostTalesConfig.save();
        }

        @Override
        public boolean precise() {
            return false;
        }

        @Override
        public ChatWindowPlacement.Box preciseBounds(
                LostTalesHudPlacementGui gui) {
            return null;
        }

        @Override
        public void moveTo(double x, double y, LostTalesHudPlacementGui gui) {
            HudPlacementLayout.Bounds bounds = gui.getBounds(this);
            gui.applyPosition(this, (int)Math.round(x), (int)Math.round(y),
                    bounds);
        }
    }
}
