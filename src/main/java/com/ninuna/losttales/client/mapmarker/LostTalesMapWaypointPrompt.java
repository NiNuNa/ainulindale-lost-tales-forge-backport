package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/**
 * Modal, map-local editor for a personal waypoint.
 *
 * <p>Two modes share one panel. Creating collects a name and an icon colour;
 * editing adds the fellowships the waypoint is shared with, and the actions
 * that belong to a waypoint that already exists.</p>
 *
 * <p>It replaces LOTR's own overlays so the map keeps one popup style, but it
 * decides nothing: every action is handed to LOTR's own request and validated
 * by the server exactly as before. The colour is the one thing LOTR has no
 * field for, and it never leaves this client.</p>
 */
@SideOnly(Side.CLIENT)
final class LostTalesMapWaypointPrompt {
    /** LOTR's own limit on a custom waypoint name. */
    static final int MAX_NAME_LENGTH = 30;
    /** Fellowship rows the panel shows at once; the rest are scrolled to. */
    static final int VISIBLE_FELLOWSHIPS = 4;
    private static final int SCREEN_SHADE = 0x66000000;
    private static final int MAX_WIDTH = 320;
    private static final int CREATE_PANEL_HEIGHT = 134;
    private static final int EDIT_PANEL_HEIGHT = 212;
    private static final int SCREEN_MARGIN = 8;
    private static final int CONTENT_PADDING = 10;
    private static final int NAME_TOP = 28;
    private static final int NOTE_TOP = 48;
    private static final int FIELD_HEIGHT = 16;
    private static final int SWATCH_TOP = 70;
    private static final int SWATCH_SIZE = 16;
    private static final int SHARE_HEADING_TOP = 92;
    private static final int SHARE_TOP = 104;
    private static final int SHARE_ROW_HEIGHT = 12;
    private static final int BUTTON_BOTTOM_MARGIN = 8;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_ROW_GAP = 4;

    enum Action {
        NONE,
        CREATE,
        SAVE,
        DELETE,
        TRAVEL,
        CANCEL
    }

    /** One of the player's fellowships, and whether this waypoint reaches it. */
    static final class ShareTarget {
        private final String name;
        private final boolean shared;

        ShareTarget(String name, boolean shared) {
            this.name = name == null ? "" : name;
            this.shared = shared;
        }

        String getName() {
            return this.name;
        }

        boolean isShared() {
            return this.shared;
        }
    }

    private final boolean editing;
    private final GuiTextField nameField;
    private final GuiTextField noteField;
    private final String originalName;
    private List<ShareTarget> shareTargets = Collections.emptyList();
    private String colorName;
    private int shareScroll;
    private ShareTarget pendingShareToggle;

    /** Editor for a waypoint that does not exist yet. */
    static LostTalesMapWaypointPrompt forCreation(
            FontRenderer fontRenderer, int screenWidth, int screenHeight) {
        return new LostTalesMapWaypointPrompt(fontRenderer,
                screenWidth, screenHeight, false, "", "",
                LostTalesCustomWaypointStyle.DEFAULT_PERSONAL_COLOR);
    }

    /** Editor for one of the player's existing waypoints. */
    static LostTalesMapWaypointPrompt forEditing(
            FontRenderer fontRenderer, int screenWidth, int screenHeight,
            String name, String note, String colorName,
            List<ShareTarget> shareTargets) {
        LostTalesMapWaypointPrompt prompt = new LostTalesMapWaypointPrompt(
                fontRenderer, screenWidth, screenHeight, true,
                name, note, colorName);
        prompt.setShareTargets(shareTargets);
        return prompt;
    }

    private LostTalesMapWaypointPrompt(
            FontRenderer fontRenderer, int screenWidth, int screenHeight,
            boolean editing, String name, String note, String colorName) {
        this.editing = editing;
        this.originalName = name == null ? "" : name.trim();
        this.colorName = LostTalesCustomWaypointStyle.isKnownColor(colorName)
                ? colorName
                : LostTalesCustomWaypointStyle.DEFAULT_PERSONAL_COLOR;
        Layout layout = calculateLayout(
                screenWidth, screenHeight, editing);
        this.nameField = new GuiTextField(fontRenderer,
                layout.nameField.x, layout.nameField.y,
                layout.nameField.width, layout.nameField.height);
        this.nameField.setMaxStringLength(MAX_NAME_LENGTH);
        this.nameField.setText(this.originalName);
        this.nameField.setFocused(true);
        this.noteField = new GuiTextField(fontRenderer,
                layout.noteField.x, layout.noteField.y,
                layout.noteField.width, layout.noteField.height);
        this.noteField.setMaxStringLength(
                LostTalesCustomWaypointStyle.MAX_NOTE_LENGTH);
        this.noteField.setText(note == null ? "" : note);
    }

    boolean isEditing() {
        return this.editing;
    }

    String getName() {
        return this.nameField.getText() == null
                ? "" : this.nameField.getText().trim();
    }

    String getOriginalName() {
        return this.originalName;
    }

    String getColorName() {
        return this.colorName;
    }

    /** The description shown under the name in the waypoint's tooltip. */
    String getNote() {
        return this.noteField.getText() == null
                ? "" : this.noteField.getText().trim();
    }

    /** True when the player changed the name of an existing waypoint. */
    boolean isRenamed() {
        return this.editing && isValidName(getName())
                && !getName().equals(this.originalName);
    }

    /**
     * The fellowship whose sharing the player just toggled, consumed by the
     * caller so one click sends exactly one request.
     */
    ShareTarget takePendingShareToggle() {
        ShareTarget pending = this.pendingShareToggle;
        this.pendingShareToggle = null;
        return pending;
    }

    void setShareTargets(List<ShareTarget> targets) {
        this.shareTargets = targets == null
                ? Collections.<ShareTarget>emptyList()
                : new ArrayList<ShareTarget>(targets);
        this.shareScroll = clampShareScroll(this.shareScroll);
    }

    /** A name LOTR will accept: present, and within its length limit. */
    static boolean isValidName(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.length() > 0
                && trimmed.length() <= MAX_NAME_LENGTH;
    }

    boolean canConfirm(boolean withinWaypointLimit) {
        if (!isValidName(getName())) {
            return false;
        }
        return this.editing || withinWaypointLimit;
    }

    void render(int screenWidth, int screenHeight,
                int mouseX, int mouseY,
                int usedWaypoints, int maxWaypoints) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.fontRenderer == null) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        LostTalesMapPopupAnimation.begin(this);
        Layout layout = calculateLayout(
                screenWidth, screenHeight, this.editing);
        int pivotX = layout.x + layout.width / 2;
        int pivotY = layout.y + layout.height / 2;
        int localMouseX = LostTalesMapPopupAnimation.inverseMouseX(
                this, mouseX, pivotX);
        int localMouseY = LostTalesMapPopupAnimation.inverseMouseY(
                this, mouseY, pivotY);
        LostTalesMapPopupAnimation.pushFixed();
        try {
            Gui.drawRect(0, 0, screenWidth, screenHeight, SCREEN_SHADE);
        } finally {
            LostTalesMapPopupAnimation.pop();
        }
        LostTalesMapPopupAnimation.push(this, pivotX, pivotY);
        try {
            LostTalesSkyrimUiStyle.drawPanel(
                    layout.x, layout.y, layout.width, layout.height);

        drawCentered(font, layout,
                I18n.format(this.editing
                        ? "gui.losttales.map.waypoint.edit_title"
                        : "gui.losttales.map.waypoint.title"),
                layout.y + 8, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        if (!this.editing) {
            drawCentered(font, layout,
                    I18n.format("gui.losttales.map.waypoint.used",
                            Integer.valueOf(usedWaypoints),
                            Integer.valueOf(maxWaypoints)),
                    layout.y + 19, LostTalesSkyrimUiStyle.TEXT_MUTED);
        }

        this.nameField.drawTextBox();
        this.noteField.drawTextBox();
        if (getName().length() == 0) {
            drawFieldHint(font, layout.nameField,
                    I18n.format("gui.losttales.map.waypoint.name_hint"));
        }
        if (getNote().length() == 0) {
            drawFieldHint(font, layout.noteField,
                    I18n.format("gui.losttales.map.waypoint.note_hint"));
        }
        drawSwatches(minecraft, layout, localMouseX, localMouseY);
        if (this.editing) {
            drawShareTargets(font, layout, localMouseX, localMouseY);
        }

        boolean confirmable = canConfirm(usedWaypoints < maxWaypoints);
        drawButton(font, layout.confirm,
                I18n.format(this.editing
                        ? "gui.losttales.map.waypoint.save"
                        : "gui.losttales.map.waypoint.create"),
                layout.confirm.contains(localMouseX, localMouseY),
                confirmable);
        if (this.editing) {
            drawButton(font, layout.travel,
                    I18n.format("gui.losttales.map.waypoint.travel"),
                    layout.travel.contains(localMouseX, localMouseY), true);
            drawButton(font, layout.delete,
                    I18n.format("gui.losttales.map.waypoint.delete"),
                    layout.delete.contains(localMouseX, localMouseY), true);
        }
        drawButton(font, layout.cancel,
                I18n.format("gui.losttales.map.waypoint.cancel"),
                layout.cancel.contains(localMouseX, localMouseY), true);
        } finally {
            LostTalesMapPopupAnimation.pop();
        }
    }

    /** Placeholder text for an empty field, so its purpose is visible. */
    private static void drawFieldHint(
            FontRenderer font, Bounds field, String hint) {
        String visible = LostTalesSkyrimUiStyle.trimToWidth(
                font, hint, Math.max(0, field.width - 10));
        font.drawString(visible, field.x + 5,
                field.y + (field.height - font.FONT_HEIGHT) / 2 + 1,
                LostTalesSkyrimUiStyle.TEXT_DIM);
    }

    private static void drawCentered(
            FontRenderer font, Layout layout, String text, int y,
            int color) {
        String visible = LostTalesSkyrimUiStyle.trimToWidth(font, text,
                Math.max(0, layout.width - CONTENT_PADDING * 2));
        font.drawStringWithShadow(visible,
                layout.x + (layout.width
                        - font.getStringWidth(visible)) / 2,
                y, color);
    }

    /**
     * Draws the palette as the marker artwork itself, so the player picks the
     * icon they will see on the map rather than a coloured square.
     */
    private void drawSwatches(Minecraft minecraft, Layout layout,
                              int mouseX, int mouseY) {
        for (int index = 0;
             index < LostTalesCustomWaypointStyle.PALETTE.length;
             index++) {
            Bounds bounds = layout.swatch(index);
            String color = LostTalesCustomWaypointStyle.PALETTE[index];
            boolean selected = color.equals(this.colorName);
            if (selected || bounds.contains(mouseX, mouseY)) {
                Gui.drawRect(bounds.x, bounds.y,
                        bounds.x + bounds.width,
                        bounds.y + bounds.height,
                        selected
                                ? LostTalesSkyrimUiStyle.PANEL_SELECTED
                                : LostTalesSkyrimUiStyle.PANEL_HOVER);
            }
            if (selected) {
                Gui.drawRect(bounds.x, bounds.y,
                        bounds.x + bounds.width, bounds.y + 1,
                        LostTalesSkyrimUiStyle.GOLD);
            }
            LostTalesLotrMapMarkerIconOverlay.renderEditorIconPreview(
                    minecraft, LostTalesCompassMarkerIcon.PERSONAL.name(),
                    color,
                    bounds.x + bounds.width / 2.0F,
                    bounds.y + bounds.height / 2.0F);
        }
    }

    private void drawShareTargets(
            FontRenderer font, Layout layout, int mouseX, int mouseY) {
        font.drawStringWithShadow(
                I18n.format("gui.losttales.map.waypoint.shared_with"),
                layout.x + CONTENT_PADDING,
                layout.y + SHARE_HEADING_TOP,
                LostTalesSkyrimUiStyle.TEXT_MUTED);
        if (this.shareTargets.isEmpty()) {
            font.drawStringWithShadow(
                    I18n.format(
                            "gui.losttales.map.waypoint.no_fellowships"),
                    layout.x + CONTENT_PADDING, layout.y + SHARE_TOP,
                    LostTalesSkyrimUiStyle.TEXT_DIM);
            return;
        }
        int rows = Math.min(VISIBLE_FELLOWSHIPS,
                this.shareTargets.size());
        for (int row = 0; row < rows; row++) {
            ShareTarget target =
                    this.shareTargets.get(this.shareScroll + row);
            Bounds bounds = layout.shareRow(row);
            boolean hovered = bounds.contains(mouseX, mouseY);
            LostTalesSkyrimUiStyle.drawSelectionRow(
                    bounds.x, bounds.y, bounds.width, bounds.height,
                    target.isShared(), hovered);
            String label = LostTalesSkyrimUiStyle.trimToWidth(font,
                    target.getName(),
                    Math.max(0, bounds.width - 24));
            font.drawStringWithShadow(label,
                    bounds.x + 18,
                    bounds.y + (bounds.height - font.FONT_HEIGHT) / 2 + 1,
                    target.isShared()
                            ? LostTalesSkyrimUiStyle.TEXT_BRIGHT
                            : LostTalesSkyrimUiStyle.TEXT_MUTED);
        }
        if (this.shareTargets.size() > VISIBLE_FELLOWSHIPS) {
            String more = I18n.format(
                    "gui.losttales.map.waypoint.more_fellowships",
                    Integer.valueOf(this.shareTargets.size()
                            - VISIBLE_FELLOWSHIPS));
            font.drawStringWithShadow(more,
                    layout.x + layout.width - CONTENT_PADDING
                            - font.getStringWidth(more),
                    layout.y + SHARE_HEADING_TOP,
                    LostTalesSkyrimUiStyle.TEXT_DIM);
        }
    }

    Action mouseClicked(int screenWidth, int screenHeight,
                        int mouseX, int mouseY, int button,
                        boolean withinWaypointLimit) {
        if (button != 0) {
            return Action.NONE;
        }
        Layout layout = calculateLayout(
                screenWidth, screenHeight, this.editing);
        int pivotX = layout.x + layout.width / 2;
        int pivotY = layout.y + layout.height / 2;
        mouseX = LostTalesMapPopupAnimation.inverseMouseX(
                this, mouseX, pivotX);
        mouseY = LostTalesMapPopupAnimation.inverseMouseY(
                this, mouseY, pivotY);
        this.nameField.mouseClicked(mouseX, mouseY, button);
        this.noteField.mouseClicked(mouseX, mouseY, button);
        for (int index = 0;
             index < LostTalesCustomWaypointStyle.PALETTE.length;
             index++) {
            if (layout.swatch(index).contains(mouseX, mouseY)) {
                this.colorName =
                        LostTalesCustomWaypointStyle.PALETTE[index];
                return Action.NONE;
            }
        }
        if (this.editing) {
            int rows = Math.min(VISIBLE_FELLOWSHIPS,
                    this.shareTargets.size());
            for (int row = 0; row < rows; row++) {
                if (layout.shareRow(row).contains(mouseX, mouseY)) {
                    this.pendingShareToggle = this.shareTargets.get(
                            this.shareScroll + row);
                    return Action.NONE;
                }
            }
            if (layout.travel.contains(mouseX, mouseY)) {
                return Action.TRAVEL;
            }
            if (layout.delete.contains(mouseX, mouseY)) {
                return Action.DELETE;
            }
        }
        if (layout.cancel.contains(mouseX, mouseY)) {
            return Action.CANCEL;
        }
        if (layout.confirm.contains(mouseX, mouseY)) {
            return canConfirm(withinWaypointLimit)
                    ? this.editing ? Action.SAVE : Action.CREATE
                    : Action.NONE;
        }
        return Action.NONE;
    }

    /**
     * Whether a click here would land on something.
     *
     * <p>Written out rather than asked of {@link #mouseClicked}: the fields,
     * the swatches and the fellowship rows all take a click and all report no
     * action, so the click test alone would leave the pointer plain over half
     * the popup.</p>
     */
    boolean isPointerOverAction(int screenWidth, int screenHeight,
                                int mouseX, int mouseY,
                                boolean withinWaypointLimit) {
        Layout layout = calculateLayout(
                screenWidth, screenHeight, this.editing);
        int pivotX = layout.x + layout.width / 2;
        int pivotY = layout.y + layout.height / 2;
        mouseX = LostTalesMapPopupAnimation.inverseMouseX(
                this, mouseX, pivotX);
        mouseY = LostTalesMapPopupAnimation.inverseMouseY(
                this, mouseY, pivotY);
        if (layout.nameField.contains(mouseX, mouseY)
                || layout.noteField.contains(mouseX, mouseY)) {
            return true;
        }
        for (int index = 0;
             index < LostTalesCustomWaypointStyle.PALETTE.length;
             index++) {
            if (layout.swatch(index).contains(mouseX, mouseY)) {
                return true;
            }
        }
        if (this.editing) {
            int rows = Math.min(VISIBLE_FELLOWSHIPS,
                    this.shareTargets.size());
            for (int row = 0; row < rows; row++) {
                if (layout.shareRow(row).contains(mouseX, mouseY)) {
                    return true;
                }
            }
            if (layout.travel.contains(mouseX, mouseY)
                    || layout.delete.contains(mouseX, mouseY)) {
                return true;
            }
        }
        if (layout.cancel.contains(mouseX, mouseY)) {
            return true;
        }
        return layout.confirm.contains(mouseX, mouseY)
                && canConfirm(withinWaypointLimit);
    }

    /** Scrolls the fellowship list; the map behind stays where it is. */
    void mouseWheel(int wheel) {
        if (wheel == 0) {
            return;
        }
        this.shareScroll = clampShareScroll(
                this.shareScroll + (wheel > 0 ? -1 : 1));
    }

    Action keyTyped(char typedChar, int keyCode,
                    boolean withinWaypointLimit) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            return Action.CANCEL;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (!canConfirm(withinWaypointLimit)) {
                return Action.NONE;
            }
            return this.editing ? Action.SAVE : Action.CREATE;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            // One field at a time holds the caret, so typing always has one
            // unambiguous destination.
            boolean toNote = this.nameField.isFocused();
            this.nameField.setFocused(!toNote);
            this.noteField.setFocused(toNote);
            return Action.NONE;
        }
        // Everything else is text: a field owns the keyboard while this
        // popup is open, so no map shortcut can fire mid-word.
        this.nameField.textboxKeyTyped(typedChar, keyCode);
        this.noteField.textboxKeyTyped(typedChar, keyCode);
        return Action.NONE;
    }

    void updateCursor() {
        this.nameField.updateCursorCounter();
        this.noteField.updateCursorCounter();
    }

    private int clampShareScroll(int scroll) {
        int maximum = Math.max(0,
                this.shareTargets.size() - VISIBLE_FELLOWSHIPS);
        return Math.max(0, Math.min(maximum, scroll));
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
        font.drawStringWithShadow(visible,
                bounds.x + (bounds.width
                        - font.getStringWidth(visible)) / 2,
                bounds.y + (bounds.height - font.FONT_HEIGHT) / 2,
                color);
    }

    static Layout calculateLayout(
            int screenWidth, int screenHeight, boolean editing) {
        int panelHeight = editing
                ? EDIT_PANEL_HEIGHT : CREATE_PANEL_HEIGHT;
        int width = Math.min(MAX_WIDTH,
                Math.max(0, screenWidth - SCREEN_MARGIN * 2));
        int height = Math.min(panelHeight,
                Math.max(0, screenHeight - SCREEN_MARGIN * 2));
        int x = Math.max(0, (screenWidth - width) / 2);
        int y = Math.max(0, (screenHeight - height) / 2);
        int contentWidth = Math.max(0, width - CONTENT_PADDING * 2);
        Bounds nameField = new Bounds(
                x + CONTENT_PADDING,
                y + Math.min(NAME_TOP, Math.max(0, height - 1)),
                contentWidth,
                Math.min(FIELD_HEIGHT,
                        Math.max(0, height - NAME_TOP)));
        Bounds noteField = new Bounds(
                x + CONTENT_PADDING,
                y + Math.min(NOTE_TOP, Math.max(0, height - 1)),
                contentWidth,
                Math.min(FIELD_HEIGHT,
                        Math.max(0, height - NOTE_TOP)));
        int swatchCount = LostTalesCustomWaypointStyle.PALETTE.length;
        int swatchWidth = swatchCount == 0 ? 0
                : Math.min(contentWidth, SWATCH_SIZE * swatchCount)
                        / swatchCount;
        Bounds swatchRow = new Bounds(
                x + (width - swatchWidth * swatchCount) / 2,
                y + Math.min(SWATCH_TOP, Math.max(0, height - 1)),
                swatchWidth * swatchCount,
                Math.min(SWATCH_SIZE,
                        Math.max(0, height - SWATCH_TOP)));
        Bounds shareRows = new Bounds(
                x + CONTENT_PADDING,
                y + Math.min(SHARE_TOP, Math.max(0, height - 1)),
                contentWidth,
                Math.min(SHARE_ROW_HEIGHT * VISIBLE_FELLOWSHIPS,
                        Math.max(0, height - SHARE_TOP)));

        // Editing has four actions, which is more than one row can carry
        // legibly: what the waypoint can do sits above what happens to it.
        // Dropping the party marker on it is not offered here — travelling to
        // it opens the popup that already carries that action.
        int lowerY = Math.max(y,
                y + height - BUTTON_BOTTOM_MARGIN - BUTTON_HEIGHT);
        int lowerHeight = Math.max(0,
                y + height - BUTTON_BOTTOM_MARGIN - lowerY);
        int upperY = Math.max(y,
                lowerY - BUTTON_ROW_GAP - BUTTON_HEIGHT);
        int upperHeight = editing
                ? Math.max(0, lowerY - BUTTON_ROW_GAP - upperY) : 0;
        Bounds travel = new Bounds(x + CONTENT_PADDING, upperY,
                editing ? contentWidth : 0, upperHeight);
        int lowerSlots = editing ? 3 : 2;
        int slotWidth = contentWidth / lowerSlots;
        Bounds confirm = new Bounds(x + CONTENT_PADDING, lowerY,
                slotWidth, lowerHeight);
        Bounds delete = new Bounds(
                x + CONTENT_PADDING + slotWidth, lowerY,
                editing ? slotWidth : 0, lowerHeight);
        int cancelX = x + CONTENT_PADDING + slotWidth
                + (editing ? slotWidth : 0);
        Bounds cancel = new Bounds(cancelX, lowerY,
                x + CONTENT_PADDING + contentWidth - cancelX,
                lowerHeight);
        return new Layout(x, y, width, height, nameField, noteField,
                swatchRow, swatchWidth, shareRows,
                travel, confirm, delete, cancel);
    }

    static final class Layout {
        final int x;
        final int y;
        final int width;
        final int height;
        final Bounds nameField;
        final Bounds noteField;
        final Bounds swatchRow;
        final int swatchWidth;
        final Bounds shareRows;
        final Bounds travel;
        final Bounds confirm;
        final Bounds delete;
        final Bounds cancel;

        private Layout(int x, int y, int width, int height,
                       Bounds nameField, Bounds noteField,
                       Bounds swatchRow,
                       int swatchWidth, Bounds shareRows,
                       Bounds travel,
                       Bounds confirm, Bounds delete, Bounds cancel) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.nameField = nameField;
            this.noteField = noteField;
            this.swatchRow = swatchRow;
            this.swatchWidth = swatchWidth;
            this.shareRows = shareRows;
            this.travel = travel;
            this.confirm = confirm;
            this.delete = delete;
            this.cancel = cancel;
        }

        Bounds swatch(int index) {
            return new Bounds(
                    this.swatchRow.x + this.swatchWidth * index,
                    this.swatchRow.y,
                    this.swatchWidth, this.swatchRow.height);
        }

        Bounds shareRow(int row) {
            return new Bounds(this.shareRows.x,
                    this.shareRows.y + SHARE_ROW_HEIGHT * row,
                    this.shareRows.width,
                    Math.min(SHARE_ROW_HEIGHT,
                            Math.max(0, this.shareRows.height
                                    - SHARE_ROW_HEIGHT * row)));
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
