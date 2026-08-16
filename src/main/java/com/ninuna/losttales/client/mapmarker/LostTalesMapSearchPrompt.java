package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/**
 * Finds a place by name and takes the map there.
 *
 * <p>Typing narrows one list of everything the player is allowed to be told
 * about: the locations they have found, their own and shared waypoints, and
 * their party's markers. A location that has not been discovered is not in the
 * list at all — offering to fly the camera to a name the map itself refuses to
 * print would give away exactly what discovery withholds.</p>
 *
 * <p>The popup owns the search and the choice, and nothing else. Picking an
 * entry hands a marker back to the map screen, which moves the camera with the
 * same focus every other map action uses.</p>
 */
@SideOnly(Side.CLIENT)
final class LostTalesMapSearchPrompt {
    private static final int MAX_WIDTH = 240;
    private static final int SCREEN_MARGIN = 12;
    private static final int CONTENT_PADDING = 8;
    private static final int TITLE_HEIGHT = 16;
    private static final int FIELD_HEIGHT = 16;
    private static final int ROW_HEIGHT = 13;
    /** Room at the left of a row for the marker's own icon. */
    private static final int ICON_COLUMN = 14;
    private static final int VISIBLE_ROWS = 10;
    /** Nothing beyond this is worth listing; typing more narrows it. */
    private static final int MAX_RESULTS = 200;
    private static final int MAX_QUERY_LENGTH = 48;

    private final GuiTextField queryField;
    private final List<Entry> entries;
    private List<Entry> results;
    private String lastQuery = "";
    private int scroll;
    private Entry chosen;

    private LostTalesMapSearchPrompt(
            FontRenderer font, int screenWidth, int screenHeight,
            List<Entry> entries) {
        Layout layout = calculateLayout(screenWidth, screenHeight);
        this.queryField = new GuiTextField(font,
                layout.field.x + 4, layout.field.y + 4,
                Math.max(0, layout.field.width - 8),
                Math.max(0, layout.field.height - 6));
        this.queryField.setMaxStringLength(MAX_QUERY_LENGTH);
        this.queryField.setEnableBackgroundDrawing(false);
        this.queryField.setFocused(true);
        this.entries = entries;
        this.results = entries;
    }

    /** Collects every place the player may be told about, sorted by name. */
    static LostTalesMapSearchPrompt open(
            FontRenderer font, int screenWidth, int screenHeight,
            List<LostTalesMapMarkerData> markers) {
        ArrayList<Entry> entries = new ArrayList<Entry>();
        if (markers != null) {
            for (LostTalesMapMarkerData marker : markers) {
                if (marker == null
                        || LostTalesLotrWaypointText.isUndiscovered(marker)
                        || !LostTalesClientMapMarkerVisibility
                                .isMapVisible(marker)) {
                    continue;
                }
                String name = marker.getName() == null
                        ? "" : marker.getName().trim();
                if (name.length() > 0) {
                    entries.add(new Entry(marker, name));
                }
            }
        }
        Collections.sort(entries, new Comparator<Entry>() {
            @Override
            public int compare(Entry left, Entry right) {
                int byName = left.name.compareToIgnoreCase(right.name);
                return byName != 0 ? byName
                        : left.marker.getId().compareTo(
                                right.marker.getId());
            }
        });
        return new LostTalesMapSearchPrompt(
                font, screenWidth, screenHeight, entries);
    }

    /**
     * Entries whose name contains the query, most relevant first: a name that
     * starts with what was typed comes before one that merely contains it.
     */
    static List<Entry> filter(List<Entry> entries, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        if (needle.length() == 0) {
            return entries;
        }
        ArrayList<Entry> starts = new ArrayList<Entry>();
        ArrayList<Entry> contains = new ArrayList<Entry>();
        for (Entry entry : entries) {
            String name = entry.name.toLowerCase();
            if (name.startsWith(needle)) {
                starts.add(entry);
            } else if (name.contains(needle)) {
                contains.add(entry);
            }
            if (starts.size() + contains.size() >= MAX_RESULTS) {
                break;
            }
        }
        starts.addAll(contains);
        return starts;
    }

    /** The marker the player picked, or null while they are still looking. */
    LostTalesMapMarkerData takeChosenMarker() {
        Entry entry = this.chosen;
        this.chosen = null;
        return entry == null ? null : entry.marker;
    }

    void updateCursor() {
        this.queryField.updateCursorCounter();
    }

    void render(int screenWidth, int screenHeight,
                int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.fontRenderer == null) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        LostTalesMapPopupAnimation.begin(this);
        Layout layout = calculateLayout(screenWidth, screenHeight);
        refreshResults();
        int pivotX = layout.x + layout.width / 2;
        int pivotY = layout.y + layout.height / 2;
        int localMouseX = LostTalesMapPopupAnimation.inverseMouseX(
                this, mouseX, pivotX);
        int localMouseY = LostTalesMapPopupAnimation.inverseMouseY(
                this, mouseY, pivotY);
        LostTalesMapPopupAnimation.pushFixed();
        try {
            Gui.drawRect(0, 0, screenWidth, screenHeight, 0x66000000);
        } finally {
            LostTalesMapPopupAnimation.pop();
        }
        LostTalesMapPopupAnimation.push(this, pivotX, pivotY);
        try {
            LostTalesSkyrimUiStyle.drawPanel(
                    layout.x, layout.y, layout.width, layout.height);
            String title = I18n.format("gui.losttales.map.search.title");
            font.drawStringWithShadow(title,
                    layout.x + (layout.width
                            - font.getStringWidth(title)) / 2,
                    layout.y + 5, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
            LostTalesSkyrimUiStyle.drawPanelSoft(layout.field.x,
                    layout.field.y, layout.field.width, layout.field.height);
            this.queryField.drawTextBox();
            if (this.queryField.getText().length() == 0) {
                font.drawString(
                        I18n.format("gui.losttales.map.search.hint"),
                        layout.field.x + 5,
                        layout.field.y
                                + (layout.field.height - font.FONT_HEIGHT) / 2
                                + 1,
                        LostTalesSkyrimUiStyle.TEXT_DIM);
            }

            if (this.results.isEmpty()) {
                String empty = I18n.format(
                        "gui.losttales.map.search.empty");
                font.drawString(empty,
                        layout.x + (layout.width
                                - font.getStringWidth(empty)) / 2,
                        layout.rows.y + 4,
                        LostTalesSkyrimUiStyle.TEXT_MUTED);
                return;
            }
            int rows = Math.min(VISIBLE_ROWS, this.results.size());
            for (int row = 0; row < rows; row++) {
                Entry entry = this.results.get(this.scroll + row);
                Bounds bounds = layout.row(row);
                boolean hovered = bounds.contains(
                        localMouseX, localMouseY);
                if (hovered) {
                    Gui.drawRect(bounds.x, bounds.y,
                            bounds.x + bounds.width,
                            bounds.y + bounds.height,
                            LostTalesSkyrimUiStyle.PANEL_HOVER);
                }
                LostTalesLotrMapMarkerIconOverlay.renderEditorIconPreview(
                        minecraft, entry.marker.getIconName(),
                        entry.marker.getColorName(),
                        bounds.x + ICON_COLUMN / 2.0F,
                        bounds.y + bounds.height / 2.0F);
                String name = LostTalesSkyrimUiStyle.trimToWidth(
                        font, entry.name,
                        Math.max(0, bounds.width - ICON_COLUMN - 4));
                font.drawString(name, bounds.x + ICON_COLUMN,
                        bounds.y
                                + (bounds.height - font.FONT_HEIGHT) / 2 + 1,
                        hovered ? LostTalesSkyrimUiStyle.TEXT_BRIGHT
                                : LostTalesSkyrimUiStyle.TEXT);
            }
            if (this.results.size() > VISIBLE_ROWS) {
                String more = I18n.format(
                        "gui.losttales.map.search.more",
                        Integer.valueOf(
                                this.results.size() - VISIBLE_ROWS));
                font.drawString(more, layout.x + CONTENT_PADDING,
                        layout.y + layout.height - 11,
                        LostTalesSkyrimUiStyle.TEXT_DIM);
            }
        } finally {
            LostTalesMapPopupAnimation.pop();
        }
    }

    /** @return true when the popup consumed the click */
    boolean mouseClicked(int screenWidth, int screenHeight,
                         int mouseX, int mouseY, int button) {
        if (button != 0) {
            return true;
        }
        Layout layout = calculateLayout(screenWidth, screenHeight);
        int pivotX = layout.x + layout.width / 2;
        int pivotY = layout.y + layout.height / 2;
        mouseX = LostTalesMapPopupAnimation.inverseMouseX(
                this, mouseX, pivotX);
        mouseY = LostTalesMapPopupAnimation.inverseMouseY(
                this, mouseY, pivotY);
        this.queryField.mouseClicked(mouseX, mouseY, button);
        refreshResults();
        int rows = Math.min(VISIBLE_ROWS, this.results.size());
        for (int row = 0; row < rows; row++) {
            if (layout.row(row).contains(mouseX, mouseY)) {
                this.chosen = this.results.get(this.scroll + row);
                return true;
            }
        }
        return true;
    }

    void mouseWheel(int wheel) {
        if (wheel == 0) {
            return;
        }
        setScroll(this.scroll + (wheel > 0 ? -1 : 1));
    }

    /**
     * @return true while the popup is still open; false once the player has
     *         asked to close it
     */
    boolean keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            return false;
        }
        if (keyCode == Keyboard.KEY_UP) {
            setScroll(this.scroll - 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            setScroll(this.scroll + 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            refreshResults();
            // Enter takes the first result, which is what the list is
            // ordered to put under the query the player typed.
            if (!this.results.isEmpty()) {
                this.chosen = this.results.get(0);
            }
            return true;
        }
        this.queryField.textboxKeyTyped(typedChar, keyCode);
        refreshResults();
        return true;
    }

    private void refreshResults() {
        String query = this.queryField.getText();
        if (!query.equals(this.lastQuery)) {
            this.lastQuery = query;
            this.results = filter(this.entries, query);
            this.scroll = 0;
        }
        setScroll(this.scroll);
    }

    private void setScroll(int value) {
        int maximum = Math.max(0, this.results.size() - VISIBLE_ROWS);
        this.scroll = Math.max(0, Math.min(maximum, value));
    }

    static Layout calculateLayout(int screenWidth, int screenHeight) {
        int width = Math.min(MAX_WIDTH,
                Math.max(0, screenWidth - SCREEN_MARGIN * 2));
        int height = Math.min(
                Math.max(0, screenHeight - SCREEN_MARGIN * 2),
                TITLE_HEIGHT + FIELD_HEIGHT + CONTENT_PADDING * 2
                        + ROW_HEIGHT * VISIBLE_ROWS + 12);
        int x = Math.max(0, (screenWidth - width) / 2);
        int y = Math.max(0, (screenHeight - height) / 2);
        Bounds field = new Bounds(x + CONTENT_PADDING,
                y + TITLE_HEIGHT, width - CONTENT_PADDING * 2,
                FIELD_HEIGHT);
        Bounds rows = new Bounds(x + CONTENT_PADDING,
                field.y + field.height + 4,
                width - CONTENT_PADDING * 2,
                Math.max(0, y + height - 12
                        - (field.y + field.height + 4)));
        return new Layout(x, y, width, height, field, rows);
    }

    /** One searchable place: the marker itself and the name it is found by. */
    static final class Entry {
        private final LostTalesMapMarkerData marker;
        private final String name;

        Entry(LostTalesMapMarkerData marker, String name) {
            this.marker = marker;
            this.name = name == null ? "" : name;
        }

        String getName() {
            return this.name;
        }

        LostTalesMapMarkerData getMarker() {
            return this.marker;
        }
    }

    static final class Layout {
        final int x;
        final int y;
        final int width;
        final int height;
        final Bounds field;
        final Bounds rows;

        private Layout(int x, int y, int width, int height,
                       Bounds field, Bounds rows) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.field = field;
            this.rows = rows;
        }

        Bounds row(int index) {
            return new Bounds(this.rows.x,
                    this.rows.y + ROW_HEIGHT * index,
                    this.rows.width,
                    Math.min(ROW_HEIGHT, Math.max(0,
                            this.rows.height - ROW_HEIGHT * index)));
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
