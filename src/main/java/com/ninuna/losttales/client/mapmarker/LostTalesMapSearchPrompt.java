package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.TabIcons;
import com.ninuna.losttales.client.window.WheelStep;
import com.ninuna.losttales.client.window.WindowFields;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import com.ninuna.losttales.gui.style.LostTalesUiClip;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * Finds a place by name and takes the map there.
 *
 * <p>Typing narrows one list of everything the player is allowed to be told
 * about: the locations they have found, their own and shared waypoints, and
 * their fellowship's markers. A location that has not been discovered is not in the
 * list at all — offering to fly the camera to a name the map itself refuses to
 * print would give away exactly what discovery withholds.</p>
 *
 * <p>The popup owns the search and the choice, and nothing else. Picking an
 * entry hands a marker back to the map screen, which moves the camera with the
 * same focus every other map action uses.</p>
 *
 * <p>It is drawn as a menu of the windows is: a popup's surface in the
 * window frame, its name as a heading, the windows' field, and the places
 * found as menu rows, each its marker's icon before its name. The row
 * under the pointer is lit, else the one the arrows chose, which Enter
 * takes; the rows glide by whole rows and say at their edges where more
 * of them waits.</p>
 */
@SideOnly(Side.CLIENT)
final class LostTalesMapSearchPrompt {
    private static final int MAX_WIDTH = 240;
    private static final int SCREEN_MARGIN = 12;
    /** The window frame a popup wears inside its footprint. */
    private static final int FRAME = WindowStyle.POPUP_FRAME;
    private static final int PADDING_X = MenuWindow.PADDING_X;
    private static final int PADDING_Y = MenuWindow.PADDING_Y;
    private static final int VISIBLE_ROWS = 10;
    /** Nothing beyond this is worth listing; typing more narrows it. */
    private static final int MAX_RESULTS = 200;
    private static final int MAX_QUERY_LENGTH = 48;
    /** Closer than this to its target and the drawn scroll arrives. */
    private static final double SCROLL_SNAP_PIXELS = 0.1D;

    private final GuiTextField queryField;
    private final List<Entry> entries;
    private List<Entry> results;
    private String lastQuery = "";
    /** The row Enter takes: the first found until an arrow moves it. */
    private int chosenRow;
    /** Whether the list scrolls the chosen row into view on its next draw. */
    private boolean revealChosen;
    /** How far down the list the wheel or the arrows asked for, in pixels. */
    private double scrollPixels;
    /**
     * The offset the rows are drawn at, gliding after
     * {@link #scrollPixels} with the windows' scroll motion; the hit tests
     * read it too, so they answer for what is on screen.
     */
    private double renderedScrollPixels;
    private long scrollNanos;
    private Entry picked;

    private LostTalesMapSearchPrompt(List<Entry> entries) {
        this.queryField = WindowFields.make(LostTalesUiCaret.HEIGHT,
                MAX_QUERY_LENGTH, false);
        this.queryField.setFocused(true);
        this.entries = entries;
        this.results = entries;
    }

    /** Collects every place the player may be told about, sorted by name. */
    static LostTalesMapSearchPrompt open(
            List<LostTalesMapMarkerData> markers) {
        return new LostTalesMapSearchPrompt(places(markers));
    }

    /**
     * Every place the player may be told about, sorted by name: what Find
     * Location searches, in its popup or in a window's well.
     */
    static List<Entry> places(List<LostTalesMapMarkerData> markers) {
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
        return entries;
    }

    /**
     * Entries whose name contains the query, most relevant first: a name that
     * starts with what was typed comes before one that merely contains it.
     * A place is found by the name this game shows, by the words it was
     * given and by its id, so a name typed in another language finds it too.
     */
    static List<Entry> filter(List<Entry> entries, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        if (needle.length() == 0) {
            return entries;
        }
        ArrayList<Entry> starts = new ArrayList<Entry>();
        ArrayList<Entry> contains = new ArrayList<Entry>();
        for (Entry entry : entries) {
            if (entry.startsWith(needle)) {
                starts.add(entry);
            } else if (entry.contains(needle)) {
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
        Entry entry = this.picked;
        this.picked = null;
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
        if (this.revealChosen) {
            this.revealChosen = false;
            revealChosenRow(layout);
        }
        clampScroll(layout);
        advanceScroll();
        int pivotX = layout.x + layout.width / 2;
        int pivotY = layout.y + layout.height / 2;
        int localMouseX = LostTalesMapPopupAnimation.inverseMouseX(
                this, mouseX, pivotX);
        int localMouseY = LostTalesMapPopupAnimation.inverseMouseY(
                this, mouseY, pivotY);
        int hovered = rowAt(layout, localMouseX, localMouseY);
        int lit = hovered >= 0 ? hovered
                : this.results.isEmpty() ? -1 : this.chosenRow;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        LostTalesMapPopupAnimation.push(this, pivotX, pivotY);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            // The lit row is the surface in the highlight's tone, cut to
            // the band the rows glide in, the frame's ring beside it with it.
            int litTop = layout.rowsTop;
            int litBottom = layout.rowsTop;
            if (lit >= 0) {
                int rowY = listTop(layout) + lit * layout.rowHeight;
                litTop = Math.max(layout.rowsTop, rowY);
                litBottom = Math.min(layout.rowsBottom,
                        rowY + layout.rowHeight);
            }
            WindowStyle.drawPopup(layout.x, layout.y,
                    layout.x + layout.width, layout.y + layout.height, 1.0F,
                    layout.boxLeft, litTop, layout.boxRight,
                    Math.max(litTop, litBottom));
            int left = layout.boxLeft + PADDING_X;
            int right = layout.boxRight - PADDING_X;
            WindowLists.drawHeading(font,
                    I18n.format("gui.losttales.map.search.title"), left,
                    left, right, layout.titleTop, layout.rowHeight, false,
                    255);
            WindowLists.drawField(font, this.queryField,
                    LostTalesUiSheet.SEARCH,
                    I18n.format("gui.losttales.map.search.hint"), left,
                    layout.fieldTop, right, right, 255);
            if (this.results.isEmpty()) {
                LostTalesUiInk.drawText(font, trimmed(font,
                                I18n.format("gui.losttales.map.search.empty"),
                                right - left), left,
                        labelTop(layout.rowsTop, layout.rowHeight),
                        WindowStyle.asideRgb(), 255);
                return;
            }
            drawRows(minecraft, font, layout);
            // The fades hang from the field's hairline the rows pass
            // under, and from the frame.
            WindowLists.drawScroll(layout.boxLeft, layout.rowsTop,
                    layout.boxRight, layout.boxBottom, layout.rowsTop,
                    layout.rowsBottom, this.renderedScrollPixels,
                    maxScroll(layout), 255);
        } finally {
            LostTalesMapPopupAnimation.pop();
            GL11.glPopAttrib();
        }
    }

    /**
     * The places found that the band shows, cut to it where they glide
     * past its edges, each its marker's icon centred in the icon box and
     * on the name's capitals, then its name in ivory.
     */
    private void drawRows(Minecraft minecraft, FontRenderer font,
                          Layout layout) {
        boolean clipped = LostTalesUiClip.beginLocal(minecraft,
                layout.boxLeft, layout.rowsTop, layout.boxRight,
                layout.rowsBottom);
        try {
            int listTop = listTop(layout);
            int iconX = layout.boxLeft + PADDING_X;
            int labelLeft = iconX + TabIcons.SLOT + TabIcons.GAP;
            int labelRight = layout.boxRight - PADDING_X;
            int icon = LostTalesLotrMapMarkerIconOverlay.EDITOR_ICON_SIZE;
            float half = icon / 2.0F;
            int first = Math.max(0, (layout.rowsTop - listTop)
                    / layout.rowHeight);
            for (int index = first; index < this.results.size(); index++) {
                int rowY = listTop + index * layout.rowHeight;
                if (rowY >= layout.rowsBottom) {
                    break;
                }
                Entry entry = this.results.get(index);
                int labelTop = labelTop(rowY, layout.rowHeight);
                LostTalesLotrMapMarkerIconOverlay.renderEditorIconPreview(
                        minecraft, entry.marker.getIconName(),
                        entry.marker.getColorName(),
                        iconX + LostTalesUiInk.centredStart(TabIcons.SIZE,
                                icon) + half,
                        labelTop + LostTalesUiInk.centredStart(
                                LostTalesUiInk.CAP_HEIGHT, icon) + half,
                        1.0F);
                LostTalesUiInk.drawText(font, trimmed(font, entry.name,
                                labelRight - labelLeft), labelLeft, labelTop,
                        LostTalesUiInk.IVORY, 255);
            }
        } finally {
            LostTalesUiClip.end(clipped);
        }
    }

    /**
     * @return true when the popup consumed the click: every click while
     *         it is open
     */
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
        if (layout.onField(mouseX, mouseY)) {
            // The caret goes where the press landed, kept inside the field
            // so the field keeps the keys.
            GuiTextField field = this.queryField;
            field.mouseClicked(Math.max(field.xPosition, Math.min(
                            field.xPosition + field.width - 1, mouseX)),
                    field.yPosition, button);
            return true;
        }
        refreshResults();
        int row = rowAt(layout, mouseX, mouseY);
        if (row >= 0) {
            this.picked = this.results.get(row);
        }
        return true;
    }

    /** The query field and the results a click would choose. */
    boolean isPointerOverAction(int screenWidth, int screenHeight,
                                int mouseX, int mouseY) {
        Layout layout = calculateLayout(screenWidth, screenHeight);
        int pivotX = layout.x + layout.width / 2;
        int pivotY = layout.y + layout.height / 2;
        mouseX = LostTalesMapPopupAnimation.inverseMouseX(
                this, mouseX, pivotX);
        mouseY = LostTalesMapPopupAnimation.inverseMouseY(
                this, mouseY, pivotY);
        return layout.onField(mouseX, mouseY)
                || rowAt(layout, mouseX, mouseY) >= 0;
    }

    /** A turn of the wheel moves the list by whole rows, as a menu's does. */
    void mouseWheel(int wheel) {
        if (wheel == 0) {
            return;
        }
        this.scrollPixels -= WheelStep.pixels(WheelStep.menuRows(
                WheelStep.lines(wheel, GuiScreen.isShiftKeyDown())),
                MenuWindow.rowHeight());
    }

    /**
     * @return true while the popup is still open; false once the player has
     *         asked to close it
     */
    boolean keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            return false;
        }
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            refreshResults();
            if (!this.results.isEmpty()) {
                this.chosenRow = Math.max(0, Math.min(
                        this.results.size() - 1, this.chosenRow
                                + (keyCode == Keyboard.KEY_UP ? -1 : 1)));
                this.revealChosen = true;
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            refreshResults();
            // Enter takes the chosen row: the first result until an arrow
            // moves it, which is what the list is ordered to put under the
            // query the player typed.
            if (!this.results.isEmpty()) {
                this.picked = this.results.get(this.chosenRow);
            }
            return true;
        }
        this.queryField.textboxKeyTyped(typedChar, keyCode);
        refreshResults();
        return true;
    }

    /** New words find a new list: it starts at its top, its first row chosen. */
    private void refreshResults() {
        String query = this.queryField.getText();
        if (!query.equals(this.lastQuery)) {
            this.lastQuery = query;
            this.results = filter(this.entries, query);
            this.chosenRow = 0;
            this.scrollPixels = 0.0D;
            this.renderedScrollPixels = 0.0D;
        }
        this.chosenRow = Math.max(0, Math.min(
                this.results.size() - 1, this.chosenRow));
    }

    /** Scrolls just far enough for the chosen row to stand whole in the band. */
    private void revealChosenRow(Layout layout) {
        double top = this.chosenRow * (double)layout.rowHeight;
        double band = layout.rowsBottom - layout.rowsTop;
        if (top < this.scrollPixels) {
            this.scrollPixels = top;
        } else if (top + layout.rowHeight > this.scrollPixels + band) {
            this.scrollPixels = top + layout.rowHeight - band;
        }
    }

    /** The furthest the list scrolls: its last row whole at the band's foot. */
    private double maxScroll(Layout layout) {
        return Math.max(0.0D, this.results.size() * (double)layout.rowHeight
                - (layout.rowsBottom - layout.rowsTop));
    }

    /** Keeps the target and the drawn offset within the list as the band shows it. */
    private void clampScroll(Layout layout) {
        double max = maxScroll(layout);
        this.scrollPixels = Math.max(0.0D, Math.min(max, this.scrollPixels));
        this.renderedScrollPixels = Math.max(0.0D,
                Math.min(max, this.renderedScrollPixels));
    }

    /** Glides the drawn offset toward its target, once per drawn frame. */
    private void advanceScroll() {
        long now = System.nanoTime();
        double elapsed = (now - this.scrollNanos) / 1.0E9D;
        this.scrollNanos = now;
        if (Math.abs(this.scrollPixels - this.renderedScrollPixels)
                <= SCROLL_SNAP_PIXELS) {
            this.renderedScrollPixels = this.scrollPixels;
            return;
        }
        this.renderedScrollPixels = Motions.followTravel(
                MotionIds.WINDOW_SCROLL, this.renderedScrollPixels,
                this.scrollPixels, elapsed);
    }

    /** Where the first found place's row is drawn: the drawn offset slides it up past the band. */
    private int listTop(Layout layout) {
        return layout.rowsTop - (int)Math.round(this.renderedScrollPixels);
    }

    /**
     * The found place whose row is drawn under a point of the popup's own
     * space, the drawn offset taken in; -1 off every row.
     */
    private int rowAt(Layout layout, int pointX, int pointY) {
        if (this.results.isEmpty() || !LostTalesUiHitBox.contains(pointX,
                pointY, layout.boxLeft, layout.rowsTop,
                layout.boxRight - layout.boxLeft,
                layout.rowsBottom - layout.rowsTop)) {
            return -1;
        }
        int row = Math.floorDiv(pointY - listTop(layout), layout.rowHeight);
        return row >= 0 && row < this.results.size() ? row : -1;
    }

    /** Where a row's words stand: their capitals centred in it. */
    private static int labelTop(int rowY, int rowHeight) {
        return rowY + LostTalesUiInk.centredStart(rowHeight,
                LostTalesUiInk.CAP_HEIGHT);
    }

    /** {@code text} cut to {@code width}, whole when it fits. */
    private static String trimmed(FontRenderer font, String text, int width) {
        return font.getStringWidth(text) <= width ? text
                : LostTalesSkyrimUiStyle.trimToWidth(font, text,
                        Math.max(0, width));
    }

    /**
     * The popup in the middle of a map {@code screenWidth} by
     * {@code screenHeight}: as many rows as the map has room for, up to
     * {@link #VISIBLE_ROWS}, under its heading and its field.
     */
    static Layout calculateLayout(int screenWidth, int screenHeight) {
        int rowHeight = MenuWindow.rowHeight();
        int fieldHeight = WindowLists.fieldHeight();
        int width = Math.min(MAX_WIDTH,
                Math.max(0, screenWidth - SCREEN_MARGIN * 2));
        int fixed = FRAME * 2 + PADDING_Y * 2 + rowHeight + fieldHeight;
        int room = Math.max(0, screenHeight - SCREEN_MARGIN * 2) - fixed;
        int rows = Math.max(1, Math.min(VISIBLE_ROWS, room / rowHeight));
        int height = fixed + rows * rowHeight;
        int x = Math.max(0, LostTalesUiInk.centredStart(screenWidth, width));
        int y = Math.max(0, LostTalesUiInk.centredStart(screenHeight,
                height));
        return new Layout(x, y, width, height, rowHeight, fieldHeight);
    }

    /** One searchable place: the marker itself and the name it is found by. */
    static final class Entry {
        private final LostTalesMapMarkerData marker;
        private final String name;
        /** The name, the words the marker was given and its id, lowered, for a search. */
        private final String[] searched;

        Entry(LostTalesMapMarkerData marker, String name) {
            this.marker = marker;
            this.name = name == null ? "" : name;
            this.searched = marker == null
                    ? new String[] {this.name.toLowerCase()}
                    : new String[] {this.name.toLowerCase(),
                            lower(marker.getGivenName()), lower(marker.getId())};
        }

        private static String lower(String value) {
            return value == null ? "" : value.trim().toLowerCase();
        }

        /** Whether a name starts with the query; an id only ever contains it, as most ids start alike. */
        boolean startsWith(String needle) {
            for (int index = 0; index < Math.min(2, this.searched.length); index++) {
                if (this.searched[index].startsWith(needle)) {
                    return true;
                }
            }
            return false;
        }

        boolean contains(String needle) {
            for (String text : this.searched) {
                if (text.contains(needle)) {
                    return true;
                }
            }
            return false;
        }

        LostTalesMapMarkerData getMarker() {
            return this.marker;
        }
    }

    /** Where the popup's parts stand, in whole pixels of the map's own space. */
    static final class Layout {
        /** The popup's footprint, the frame inside it. */
        final int x;
        final int y;
        final int width;
        final int height;
        final int rowHeight;
        /** The box inside the frame, which the lit rows run across. */
        final int boxLeft;
        final int boxTop;
        final int boxRight;
        final int boxBottom;
        /** The heading's row, the field's row and the band the rows glide in. */
        final int titleTop;
        final int fieldTop;
        final int fieldHeight;
        final int rowsTop;
        final int rowsBottom;

        private Layout(int x, int y, int width, int height, int rowHeight,
                       int fieldHeight) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.rowHeight = rowHeight;
            this.boxLeft = x + FRAME;
            this.boxTop = y + FRAME;
            this.boxRight = x + width - FRAME;
            this.boxBottom = y + height - FRAME;
            this.titleTop = this.boxTop + PADDING_Y;
            this.fieldTop = this.titleTop + rowHeight;
            this.fieldHeight = fieldHeight;
            this.rowsTop = this.fieldTop + fieldHeight;
            this.rowsBottom = Math.max(this.rowsTop,
                    this.boxBottom - PADDING_Y);
        }

        /** Whether a point is on the field's row, from the padding in. */
        boolean onField(int pointX, int pointY) {
            return LostTalesUiHitBox.contains(pointX, pointY,
                    this.boxLeft + PADDING_X, this.fieldTop,
                    this.boxRight - this.boxLeft - PADDING_X * 2,
                    this.fieldHeight);
        }
    }
}
