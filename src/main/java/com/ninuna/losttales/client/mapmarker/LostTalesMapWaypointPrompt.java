package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.OptionGlyph;
import com.ninuna.losttales.client.window.TabIcons;
import com.ninuna.losttales.client.window.WindowFields;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WordButton;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import com.ninuna.losttales.gui.style.LostTalesUiCutName;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiTheme;
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
import org.lwjgl.opengl.GL11;

/**
 * Modal, map-local editor for a personal waypoint: a popup in the window
 * frame (LOOK-28) holding a heading, the windows' fields, the colours, and
 * framed word buttons, as a window's form holds them (LOOK-24).
 *
 * <p>Two modes share one popup. Creating collects a name and an icon colour;
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
    /** Fellowship rows the popup shows at once; the rest are scrolled to. */
    static final int VISIBLE_FELLOWSHIPS = 4;
    private static final int SCREEN_SHADE =
            LostTalesColors.withAlpha(LostTalesColors.PLUM_BLACK, 0x66);
    private static final int MAX_WIDTH = 240;
    private static final int SCREEN_MARGIN = 8;
    private static final int FRAME = WindowStyle.POPUP_FRAME;
    /** The menus' clear room round what the popup holds, across and down. */
    private static final int PADDING_X = MenuWindow.PADDING_X;
    private static final int PADDING_Y = MenuWindow.PADDING_Y;
    /** The count of waypoints used: a menu note's one line. */
    private static final int NOTE_HEIGHT = MenuWindow.NOTE_PADDING * 2
            + MenuWindow.NOTE_LINE;
    private static final int SWATCH_SIZE = 16;
    private static final int BUTTON_HEIGHT = LostTalesUiFramedButton.HEIGHT;
    private static final int BUTTON_GAP = WindowStyle.BUTTON_GAP;
    /** Before a fellowship's row: struck through while the waypoint does not reach it. */
    private static final OptionGlyph SHARE_GLYPH = OptionGlyph.sprite(
            LostTalesUiSheet.MEMBERS, LostTalesUiSheet.MEMBERS_HOVER);

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
    private final LostTalesUiButtonMotion confirmMotion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
    private final LostTalesUiButtonMotion travelMotion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
    private final LostTalesUiButtonMotion deleteMotion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
    private final LostTalesUiButtonMotion cancelMotion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);

    /** Editor for a waypoint that does not exist yet. */
    static LostTalesMapWaypointPrompt forCreation() {
        return new LostTalesMapWaypointPrompt(false, "", "",
                LostTalesCustomWaypointStyle.DEFAULT_PERSONAL_COLOR);
    }

    /** Editor for one of the player's existing waypoints. */
    static LostTalesMapWaypointPrompt forEditing(
            String name, String note, String colorName,
            List<ShareTarget> shareTargets) {
        LostTalesMapWaypointPrompt prompt = new LostTalesMapWaypointPrompt(
                true, name, note, colorName);
        prompt.setShareTargets(shareTargets);
        return prompt;
    }

    private LostTalesMapWaypointPrompt(
            boolean editing, String name, String note, String colorName) {
        this.editing = editing;
        this.originalName = name == null ? "" : name.trim();
        this.colorName = LostTalesCustomWaypointStyle.isKnownColor(colorName)
                ? colorName
                : LostTalesCustomWaypointStyle.DEFAULT_PERSONAL_COLOR;
        this.nameField = WindowFields.make(LostTalesUiCaret.HEIGHT,
                MAX_NAME_LENGTH, false);
        this.nameField.setText(this.originalName);
        this.nameField.setFocused(true);
        this.noteField = WindowFields.make(LostTalesUiCaret.HEIGHT,
                LostTalesCustomWaypointStyle.MAX_NOTE_LENGTH, false);
        this.noteField.setText(note == null ? "" : note);
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

    /** The description shown under the name in the waypoint's card. */
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
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        LostTalesMapPopupAnimation.push(this, pivotX, pivotY);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            int surfaceAlpha = WindowStyle.drawPopupSurface(layout.x,
                    layout.y, layout.x + layout.width,
                    layout.y + layout.height, 1.0F);
            lightChosen(layout, localMouseX, localMouseY, surfaceAlpha);
            drawContents(minecraft, font, layout, localMouseX, localMouseY,
                    usedWaypoints, maxWaypoints, surfaceAlpha);
            WindowStyle.drawPopupEdges(layout.x, layout.y,
                    layout.x + layout.width, layout.y + layout.height, 1.0F);
        } finally {
            LostTalesMapPopupAnimation.pop();
            GL11.glPopAttrib();
        }
    }

    /**
     * Every swatch and fellowship row a press would find lit, the colour
     * chosen, the fellowships reached and whatever is under the pointer: the
     * popup's surface recoloured before anything lands on it, a row reaching
     * the frame taking the ring beside it with it.
     */
    private void lightChosen(Layout layout, int mouseX, int mouseY,
                             int surfaceAlpha) {
        if (surfaceAlpha <= 0) {
            return;
        }
        for (int index = 0;
             index < LostTalesCustomWaypointStyle.PALETTE.length;
             index++) {
            Bounds swatch = layout.swatch(index);
            if (LostTalesCustomWaypointStyle.PALETTE[index].equals(
                    this.colorName) || swatch.contains(mouseX, mouseY)) {
                recolour(swatch.x, swatch.y, swatch.x + swatch.width,
                        swatch.y + swatch.height, surfaceAlpha);
            }
        }
        if (!this.editing) {
            return;
        }
        for (int row = 0; row < shownShareRows(); row++) {
            Bounds bounds = layout.shareRow(row);
            if (this.shareTargets.get(this.shareScroll + row).isShared()
                    || bounds.contains(mouseX, mouseY)) {
                recolour(bounds.x, bounds.y, bounds.x + bounds.width,
                        bounds.y + bounds.height, surfaceAlpha);
                WindowStyle.recolourRingBeside(layout.boxLeft(),
                        layout.boxRight(), bounds.x, bounds.y,
                        bounds.x + bounds.width, bounds.y + bounds.height,
                        surfaceAlpha, LostTalesUiTheme.primaryRgb(),
                        LostTalesUiTheme.secondaryRgb());
            }
        }
    }

    private static void recolour(int left, int top, int right, int bottom,
                                 int surfaceAlpha) {
        WindowStyle.recolourFlat(left, top, right, bottom, surfaceAlpha,
                LostTalesUiTheme.primaryRgb(),
                LostTalesUiTheme.secondaryRgb());
    }

    private void drawContents(Minecraft minecraft, FontRenderer font,
                              Layout layout, int mouseX, int mouseY,
                              int usedWaypoints, int maxWaypoints,
                              int surfaceAlpha) {
        int left = layout.contentLeft();
        int right = layout.contentRight();
        WindowLists.drawHeading(font, I18n.format(this.editing
                        ? "gui.losttales.map.waypoint.edit_title"
                        : "gui.losttales.map.waypoint.title"),
                left, left, right, layout.headingTop, layout.rowHeight,
                false, 255);
        if (!this.editing) {
            drawNote(font, I18n.format("gui.losttales.map.waypoint.used",
                    Integer.valueOf(usedWaypoints),
                    Integer.valueOf(maxWaypoints)), left, layout.noteTop,
                    right - left);
        }
        WindowLists.drawField(font, this.nameField, LostTalesUiSheet.MAP_MARKER,
                I18n.format("gui.losttales.map.waypoint.name_hint"), left,
                layout.nameField.y, right, right, 255);
        WindowLists.drawField(font, this.noteField, LostTalesUiSheet.DRAFT,
                I18n.format("gui.losttales.map.waypoint.note_hint"), left,
                layout.noteField.y, right, right, 255);
        drawSwatches(minecraft, layout);
        if (this.editing) {
            drawShareTargets(font, layout, mouseX, mouseY);
        }

        boolean confirmable = canConfirm(usedWaypoints < maxWaypoints);
        drawButton(font, layout.confirm, I18n.format(this.editing
                        ? "gui.losttales.map.waypoint.save"
                        : "gui.losttales.map.waypoint.create"),
                layout.confirm.contains(mouseX, mouseY), confirmable, false,
                this.confirmMotion, surfaceAlpha);
        if (this.editing) {
            drawButton(font, layout.travel,
                    I18n.format("gui.losttales.map.waypoint.travel"),
                    layout.travel.contains(mouseX, mouseY), true, false,
                    this.travelMotion, surfaceAlpha);
            // Deleting the waypoint ends something: red until it lights.
            drawButton(font, layout.delete,
                    I18n.format("gui.losttales.map.waypoint.delete"),
                    layout.delete.contains(mouseX, mouseY), true, true,
                    this.deleteMotion, surfaceAlpha);
        }
        drawButton(font, layout.cancel,
                I18n.format("gui.losttales.map.waypoint.cancel"),
                layout.cancel.contains(mouseX, mouseY), true, false,
                this.cancelMotion, surfaceAlpha);
    }

    /** A line of words in the aside tone, as a menu's note stands. */
    private static void drawNote(FontRenderer font, String text, int left,
                                 int top, int room) {
        LostTalesUiCutName.drawText(font, text, left, top
                        + MenuWindow.NOTE_PADDING + LostTalesUiInk.centredStart(
                                MenuWindow.NOTE_LINE, LostTalesUiInk.CAP_HEIGHT),
                room, WindowStyle.asideRgb(), 255);
    }

    /**
     * Draws the palette as the marker artwork itself, so the player picks the
     * icon they will see on the map rather than a coloured square.
     */
    private void drawSwatches(Minecraft minecraft, Layout layout) {
        for (int index = 0;
             index < LostTalesCustomWaypointStyle.PALETTE.length;
             index++) {
            Bounds bounds = layout.swatch(index);
            LostTalesLotrMapMarkerIconOverlay.renderEditorIconPreview(
                    minecraft, LostTalesCompassMarkerIcon.PERSONAL.name(),
                    LostTalesCustomWaypointStyle.PALETTE[index],
                    bounds.x + bounds.width / 2.0F,
                    bounds.y + bounds.height / 2.0F);
        }
        LostTalesUiInk.beginContent();
    }

    /**
     * The fellowships under their heading, as the legend's kinds stand:
     * each one's glyph struck through while the waypoint does not reach it,
     * then its name in ivory, fading into the row's end where it is cut and
     * sliding along while the pointer rests on it (LOOK-37).
     */
    private void drawShareTargets(
            FontRenderer font, Layout layout, int mouseX, int mouseY) {
        int left = layout.contentLeft();
        int right = layout.contentRight();
        String more = this.shareTargets.size() > VISIBLE_FELLOWSHIPS
                ? I18n.format("gui.losttales.map.waypoint.more_fellowships",
                        Integer.valueOf(this.shareTargets.size()
                                - VISIBLE_FELLOWSHIPS))
                : "";
        int moreWidth = more.length() == 0 ? 0 : font.getStringWidth(more);
        int headingRight = moreWidth == 0 ? right
                : right - moreWidth - BUTTON_GAP;
        WindowLists.drawHeading(font,
                I18n.format("gui.losttales.map.waypoint.shared_with"), left,
                left, headingRight, right, layout.shareHeadingTop,
                layout.rowHeight, false, 255, null, null, false);
        if (moreWidth > 0) {
            LostTalesUiInk.drawText(font, more, right - moreWidth,
                    WindowLists.headingTextTop(layout.shareHeadingTop,
                            layout.rowHeight),
                    WindowStyle.asideRgb(), 255);
        }
        if (this.shareTargets.isEmpty()) {
            LostTalesUiCutName.drawText(font,
                    I18n.format("gui.losttales.map.waypoint.no_fellowships"),
                    left, labelTop(layout.shareRow(0).y, layout.rowHeight),
                    right - left, WindowStyle.asideRgb(), 255);
            return;
        }
        int labelLeft = left + TabIcons.SLOT + TabIcons.GAP;
        for (int row = 0; row < shownShareRows(); row++) {
            ShareTarget target =
                    this.shareTargets.get(this.shareScroll + row);
            Bounds bounds = layout.shareRow(row);
            boolean hovered = bounds.contains(mouseX, mouseY);
            int labelTop = labelTop(bounds.y, layout.rowHeight);
            OptionGlyph glyph = target.isShared() ? SHARE_GLYPH
                    : SHARE_GLYPH.struck();
            glyph.draw(left + (TabIcons.SLOT - glyph.width()) / 2,
                    labelTop + LostTalesUiInk.centredStart(
                            LostTalesUiInk.CAP_HEIGHT, glyph.height()),
                    0.0F, 255);
            LostTalesUiCutName.drawText(font, target.getName(), labelLeft,
                    labelTop, right - labelLeft, LostTalesUiInk.IVORY, 255,
                    this, Integer.valueOf(this.shareScroll + row), hovered);
        }
    }

    /** Where a row's words stand: their capitals centred in the row. */
    private static int labelTop(int rowY, int rowHeight) {
        return rowY + LostTalesUiInk.centredStart(rowHeight,
                LostTalesUiInk.CAP_HEIGHT);
    }

    private int shownShareRows() {
        return Math.min(VISIBLE_FELLOWSHIPS, this.shareTargets.size());
    }

    private static void drawButton(
            FontRenderer font, Bounds bounds, String label,
            boolean hovered, boolean enabled, boolean ending,
            LostTalesUiButtonMotion motion, int surfaceAlpha) {
        if (bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        WordButton.draw(font, new LostTalesUiHitBox(bounds.x, bounds.y,
                        bounds.width, bounds.height), label, ending, enabled,
                hovered, motion, 255, surfaceAlpha);
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
        // One field at a time holds the caret; a press elsewhere leaves it
        // where it is, so typing always has a field to go to.
        if (layout.nameField.contains(mouseX, mouseY)) {
            pressField(this.nameField, this.noteField, mouseX, button);
            return Action.NONE;
        }
        if (layout.noteField.contains(mouseX, mouseY)) {
            pressField(this.noteField, this.nameField, mouseX, button);
            return Action.NONE;
        }
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
            for (int row = 0; row < shownShareRows(); row++) {
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
     * The caret goes where the press landed, kept inside the field so the
     * field keeps the keys; the other field lets them go.
     */
    private static void pressField(GuiTextField pressed, GuiTextField other,
                                   int mouseX, int button) {
        other.setFocused(false);
        pressed.setFocused(true);
        pressed.mouseClicked(Math.max(pressed.xPosition, Math.min(
                        pressed.xPosition + pressed.width - 1, mouseX)),
                pressed.yPosition, button);
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
            for (int row = 0; row < shownShareRows(); row++) {
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

    /**
     * The popup in the middle of a map {@code screenWidth} by
     * {@code screenHeight}, top down: its heading, the count of waypoints
     * used while creating, the two fields, the colours, while editing the
     * fellowships under their heading, then the buttons at its foot, what
     * the waypoint can do above what happens to it.
     */
    static Layout calculateLayout(
            int screenWidth, int screenHeight, boolean editing) {
        int rowHeight = MenuWindow.rowHeight();
        int fieldHeight = WindowLists.fieldHeight();
        int buttonRows = editing ? 2 : 1;
        int natural = FRAME * 2 + PADDING_Y * 3 + rowHeight
                + (editing ? 0 : NOTE_HEIGHT) + fieldHeight * 2
                + SWATCH_SIZE
                + (editing ? PADDING_Y + rowHeight * (1 + VISIBLE_FELLOWSHIPS)
                        : 0)
                + PADDING_Y + buttonRows * BUTTON_HEIGHT
                + (buttonRows - 1) * BUTTON_GAP;
        int width = Math.min(MAX_WIDTH,
                Math.max(0, screenWidth - SCREEN_MARGIN * 2));
        int height = Math.min(natural,
                Math.max(0, screenHeight - SCREEN_MARGIN * 2));
        int x = Math.max(0, LostTalesUiInk.centredStart(screenWidth, width));
        int y = Math.max(0, LostTalesUiInk.centredStart(screenHeight,
                height));
        int contentLeft = x + FRAME + PADDING_X;
        int contentWidth = Math.max(0, width - (FRAME + PADDING_X) * 2);

        int headingTop = y + FRAME + PADDING_Y;
        int noteTop = headingTop + rowHeight;
        int nameTop = noteTop + (editing ? 0 : NOTE_HEIGHT);
        Bounds nameField = new Bounds(contentLeft, nameTop, contentWidth,
                fieldHeight);
        Bounds noteField = new Bounds(contentLeft, nameTop + fieldHeight,
                contentWidth, fieldHeight);
        int swatchCount = LostTalesCustomWaypointStyle.PALETTE.length;
        int swatchWidth = swatchCount == 0 ? 0
                : Math.min(contentWidth, SWATCH_SIZE * swatchCount)
                        / swatchCount;
        Bounds swatchRow = new Bounds(
                x + LostTalesUiInk.centredStart(width,
                        swatchWidth * swatchCount),
                noteField.y + fieldHeight + PADDING_Y,
                swatchWidth * swatchCount, SWATCH_SIZE);
        int shareHeadingTop = swatchRow.y + SWATCH_SIZE + PADDING_Y;
        // The fellowships' rows run across the whole popup inside its
        // frame, as a list's rows do, so a lit one reaches the frame.
        Bounds shareRows = new Bounds(x + FRAME,
                shareHeadingTop + rowHeight, Math.max(0, width - FRAME * 2),
                editing ? rowHeight * VISIBLE_FELLOWSHIPS : 0);

        int lowerY = Math.max(y,
                y + height - FRAME - PADDING_Y - BUTTON_HEIGHT);
        int upperY = Math.max(y, lowerY - BUTTON_GAP - BUTTON_HEIGHT);
        Bounds travel = new Bounds(contentLeft, upperY,
                editing ? contentWidth : 0, editing ? BUTTON_HEIGHT : 0);
        int lowerSlots = editing ? 3 : 2;
        int slotWidth = Math.max(0, (contentWidth
                - BUTTON_GAP * (lowerSlots - 1)) / lowerSlots);
        Bounds confirm = new Bounds(contentLeft, lowerY, slotWidth,
                BUTTON_HEIGHT);
        Bounds delete = new Bounds(confirm.x + slotWidth + BUTTON_GAP, lowerY,
                editing ? slotWidth : 0, BUTTON_HEIGHT);
        int cancelX = editing ? delete.x + slotWidth + BUTTON_GAP
                : confirm.x + slotWidth + BUTTON_GAP;
        Bounds cancel = new Bounds(cancelX, lowerY,
                contentLeft + contentWidth - cancelX, BUTTON_HEIGHT);
        return new Layout(x, y, width, height, rowHeight, headingTop,
                noteTop, nameField, noteField, swatchRow, swatchWidth,
                shareHeadingTop, shareRows, travel, confirm, delete, cancel);
    }

    static final class Layout {
        /** The popup's footprint, the frame inside it. */
        final int x;
        final int y;
        final int width;
        final int height;
        final int rowHeight;
        final int headingTop;
        final int noteTop;
        final Bounds nameField;
        final Bounds noteField;
        final Bounds swatchRow;
        final int swatchWidth;
        final int shareHeadingTop;
        final Bounds shareRows;
        final Bounds travel;
        final Bounds confirm;
        final Bounds delete;
        final Bounds cancel;

        private Layout(int x, int y, int width, int height, int rowHeight,
                       int headingTop, int noteTop, Bounds nameField,
                       Bounds noteField, Bounds swatchRow, int swatchWidth,
                       int shareHeadingTop, Bounds shareRows, Bounds travel,
                       Bounds confirm, Bounds delete, Bounds cancel) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.rowHeight = rowHeight;
            this.headingTop = headingTop;
            this.noteTop = noteTop;
            this.nameField = nameField;
            this.noteField = noteField;
            this.swatchRow = swatchRow;
            this.swatchWidth = swatchWidth;
            this.shareHeadingTop = shareHeadingTop;
            this.shareRows = shareRows;
            this.travel = travel;
            this.confirm = confirm;
            this.delete = delete;
            this.cancel = cancel;
        }

        /** The box inside the frame, which the lit rows run across. */
        int boxLeft() {
            return this.x + FRAME;
        }

        int boxRight() {
            return this.x + this.width - FRAME;
        }

        /** What the popup holds stands the menus' padding inside the box. */
        int contentLeft() {
            return boxLeft() + PADDING_X;
        }

        int contentRight() {
            return boxRight() - PADDING_X;
        }

        Bounds swatch(int index) {
            return new Bounds(
                    this.swatchRow.x + this.swatchWidth * index,
                    this.swatchRow.y,
                    this.swatchWidth, this.swatchRow.height);
        }

        Bounds shareRow(int row) {
            return new Bounds(this.shareRows.x,
                    this.shareRows.y + this.rowHeight * row,
                    this.shareRows.width,
                    Math.min(this.rowHeight,
                            Math.max(0, this.shareRows.height
                                    - this.rowHeight * row)));
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
            return LostTalesUiHitBox.contains(pointX, pointY, this.x, this.y,
                    this.width, this.height);
        }
    }
}
