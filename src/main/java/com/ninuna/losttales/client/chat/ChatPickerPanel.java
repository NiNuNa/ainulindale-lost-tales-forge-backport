package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.config.LostTalesConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * Shared frame for the share pickers toggled from the small buttons beside
 * the chat input: a search field on top and collapsible sections of cells
 * below, anchored above the input at its right edge. Subclasses supply the
 * sections for a query, draw one cell, and say what a chosen cell inserts;
 * everything else — open/close easing, search input, section folding,
 * scrolling, hit testing, hover tooltip, pointer-region registration —
 * lives here so the emote, item, marker and quest pickers behave
 * identically. The panel never grows past the middle of the screen: a
 * list taller than that scrolls inside it (mouse wheel over the panel),
 * clipped to the body below the search row. Geometry is derived from the
 * live screen size, so GUI scale and resolution changes are handled.
 */
abstract class ChatPickerPanel {
    static final int BUTTON_SIZE = 12;
    static final int BUTTON_MARGIN = 2;
    static final int PADDING = 4;
    static final int SEARCH_HEIGHT = 12;
    static final int LABEL_HEIGHT = 10;
    /** Gap between the panel's bottom edge and the input row. */
    static final int PANEL_BOTTOM_MARGIN = 15;
    /** Folded sections persist for the session, per picker and label. */
    private static final Set<String> COLLAPSED = new HashSet<String>();

    private boolean targetOpen;
    private long transitionNanos;
    private GuiTextField searchField;
    private int buttonIndex;
    private Entry hoveredEntry;
    /** Pixels the body is scrolled up by; clamped on every layout. */
    private int scroll;

    /** Position from the right edge: 0 is the rightmost button. */
    void setButtonIndex(int buttonIndex) {
        this.buttonIndex = Math.max(0, buttonIndex);
    }

    boolean isOpen() {
        return this.targetOpen;
    }

    void setOpen(boolean open) {
        if (this.targetOpen != open) {
            this.targetOpen = open;
            this.transitionNanos = System.nanoTime();
            this.scroll = 0;
            if (this.searchField != null) {
                this.searchField.setText("");
                this.searchField.setFocused(false);
            }
        }
    }

    boolean isSearchFocused() {
        return this.targetOpen && this.searchField != null
                && this.searchField.isFocused();
    }

    /** Ticks the search caret blink; called from the screen's updateScreen. */
    void tick() {
        if (this.searchField != null) {
            this.searchField.updateCursorCounter();
        }
    }

    /**
     * Consumes keys owned by the picker: ESC closes it, and everything else
     * is routed into the search field while that is focused.
     */
    boolean handleKeyTyped(char typedChar, int keyCode) {
        if (!this.targetOpen) {
            return false;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            setOpen(false);
            return true;
        }
        return this.searchField != null
                && this.searchField.textboxKeyTyped(typedChar, keyCode);
    }

    /** Scrolls the body; positive moves the list up (shows later rows). */
    void scrollBy(int pixels) {
        this.scroll = Math.max(0, this.scroll + pixels);
    }

    /** Button left edge; {@code anchorRight} is the input bar's right edge. */
    int buttonLeft(int anchorRight) {
        return anchorRight - BUTTON_MARGIN
                - (this.buttonIndex + 1) * (BUTTON_SIZE + BUTTON_MARGIN)
                + BUTTON_MARGIN;
    }

    static int buttonTop(int screenHeight) {
        return screenHeight - 14;
    }

    boolean isInsideButton(int mouseX, int mouseY,
                           int anchorRight, int screenHeight) {
        int left = buttonLeft(anchorRight);
        int top = buttonTop(screenHeight);
        return mouseX >= left && mouseX < left + BUTTON_SIZE
                && mouseY >= top && mouseY < top + BUTTON_SIZE;
    }

    boolean isInsidePanel(int mouseX, int mouseY,
                          int anchorRight, int screenHeight) {
        if (!this.targetOpen) {
            return false;
        }
        Layout layout = buildLayout(anchorRight, screenHeight);
        return mouseX >= layout.left && mouseX < layout.left + panelWidth()
                && mouseY >= layout.top
                && mouseY < layout.top + layout.height;
    }

    /**
     * Click handling inside the panel: search focus and section folding.
     * Returns true when a section header consumed the click.
     */
    boolean mouseClicked(int mouseX, int mouseY, int button,
                         int anchorRight, int screenHeight) {
        if (!this.targetOpen) {
            return false;
        }
        Layout layout = buildLayout(anchorRight, screenHeight);
        if (this.searchField != null) {
            positionSearchField(layout);
            this.searchField.mouseClicked(mouseX, mouseY, button);
        }
        if (button != 0) {
            return false;
        }
        for (Label label : layout.labels) {
            if (label.collapsible && mouseX >= layout.left
                    && mouseX < layout.left + panelWidth()
                    && mouseY >= label.y && mouseY < label.y + LABEL_HEIGHT
                    && layout.showsRow(label.y, LABEL_HEIGHT)) {
                toggleCollapsed(label.key);
                return true;
            }
        }
        return false;
    }

    /** The cell under the mouse while the picker is open, else null. */
    Entry entryAt(int mouseX, int mouseY, int anchorRight, int screenHeight) {
        if (!this.targetOpen) {
            return null;
        }
        Layout layout = buildLayout(anchorRight, screenHeight);
        for (Cell cell : layout.cells) {
            if (mouseX >= cell.x && mouseX < cell.x + cellWidth()
                    && mouseY >= cell.y && mouseY < cell.y + cellHeight()
                    && layout.showsRow(cell.y, cellHeight())) {
                return cell.entry;
            }
        }
        return null;
    }

    void draw(Minecraft minecraft, ChatPointerRegions regions,
              int anchorRight, int screenHeight, int mouseX, int mouseY) {
        this.hoveredEntry = null;
        drawButton(minecraft, regions, anchorRight, screenHeight,
                mouseX, mouseY);
        drawPanel(minecraft, regions, anchorRight, screenHeight,
                mouseX, mouseY);
        drawTooltip(minecraft.fontRenderer, mouseX, mouseY, anchorRight);
    }

    private void drawButton(Minecraft minecraft, ChatPointerRegions regions,
                            int anchorRight, int screenHeight,
                            int mouseX, int mouseY) {
        int left = buttonLeft(anchorRight);
        int top = buttonTop(screenHeight);
        boolean lifted = this.targetOpen || isInsideButton(mouseX, mouseY,
                anchorRight, screenHeight);
        // A bare icon with the shared shadow; hover and open states lift it
        // a pixel rather than painting a backdrop.
        drawButtonIcon(minecraft, left, top - (lifted ? 1 : 0), lifted);
        regions.add(left, top, left + BUTTON_SIZE, top + BUTTON_SIZE);
    }

    private void drawPanel(Minecraft minecraft, ChatPointerRegions regions,
                           int anchorRight, int screenHeight,
                           int mouseX, int mouseY) {
        float progress = openProgress();
        if (progress <= 0.0F) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        ensureSearchField(font);
        Layout layout = buildLayout(anchorRight, screenHeight);
        positionSearchField(layout);
        int slide = Math.round((1.0F - progress) * 5.0F);
        int top = layout.top + slide;
        regions.add(layout.left, top, layout.left + panelWidth(),
                top + layout.height);
        int backgroundAlpha = Math.max(0, Math.min(255,
                Math.round(230.0F * progress)));
        Gui.drawRect(layout.left, top, layout.left + panelWidth(),
                top + layout.height, (backgroundAlpha << 24)
                        | LostTalesChatVisualStyle.SURFACE_RGB);

        int textAlpha = Math.max(LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA,
                Math.min(255, Math.round(255.0F * progress)));
        drawSearchRow(font, layout, slide, textAlpha);
        // Rows are clipped to the body so a scrolled list never paints
        // over the search row or past the panel's bottom edge.
        boolean clipped = beginBodyClip(minecraft, layout, slide);
        try {
            for (Label label : layout.labels) {
                if (!layout.showsRow(label.y, LABEL_HEIGHT)) {
                    continue;
                }
                String glyph = label.collapsible
                        ? (isCollapsed(label.key) ? "+ " : "- ") : "";
                LostTalesChatVisualStyle.drawPlain(font, glyph + label.text,
                        label.x, label.y + slide, textAlpha);
            }
            for (Cell cell : layout.cells) {
                if (!layout.showsRow(cell.y, cellHeight())) {
                    continue;
                }
                boolean hovered = this.targetOpen && slide == 0
                        && mouseX >= cell.x && mouseX < cell.x + cellWidth()
                        && mouseY >= cell.y && mouseY < cell.y + cellHeight()
                        && mouseY >= layout.bodyTop
                        && mouseY < layout.bodyBottom;
                if (hovered) {
                    this.hoveredEntry = cell.entry;
                    Gui.drawRect(cell.x, cell.y, cell.x + cellWidth(),
                            cell.y + cellHeight(), (backgroundAlpha << 24)
                                    | LostTalesChatVisualStyle
                                            .SURFACE_HIGHLIGHT_RGB);
                }
                drawEntry(minecraft, cell.entry, cell.x, cell.y + slide,
                        textAlpha, hovered);
            }
        } finally {
            endBodyClip(clipped);
        }
        drawScrollbar(layout, slide, textAlpha);
    }

    /** A thin track at the right edge while the list is longer than the body. */
    private void drawScrollbar(Layout layout, int slide, int alpha) {
        if (layout.maxScroll <= 0) {
            return;
        }
        int bodyHeight = layout.bodyBottom - layout.bodyTop;
        int contentHeight = bodyHeight + layout.maxScroll;
        int thumbHeight = Math.max(4, bodyHeight * bodyHeight / contentHeight);
        int thumbTop = layout.bodyTop + slide
                + (bodyHeight - thumbHeight) * this.scroll / layout.maxScroll;
        int x = layout.left + panelWidth() - 2;
        Gui.drawRect(x, layout.bodyTop + slide, x + 1,
                layout.bodyBottom + slide,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB,
                        Math.min(alpha, 120)));
        Gui.drawRect(x, thumbTop, x + 1, thumbTop + thumbHeight,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.IVORY, Math.min(alpha, 200)));
    }

    private void drawSearchRow(FontRenderer font, Layout layout,
                               int slide, int alpha) {
        Gui.drawRect(layout.left + PADDING, layout.searchY + slide - 1,
                layout.left + panelWidth() - PADDING,
                layout.searchY + slide + SEARCH_HEIGHT - 3,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB,
                        Math.min(alpha, 120)));
        if (this.searchField == null) {
            return;
        }
        if (this.searchField.getText().length() == 0
                && !this.searchField.isFocused()) {
            LostTalesChatVisualStyle.drawPlain(font,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.share.search"),
                    layout.left + PADDING + 3, layout.searchY + slide,
                    Math.min(alpha, 140));
        } else {
            this.searchField.drawTextBox();
        }
    }

    private void drawTooltip(FontRenderer font, int mouseX, int mouseY,
                             int anchorRight) {
        Entry entry = this.hoveredEntry;
        String label = entry == null ? null : tooltip(entry);
        if (label == null || label.length() == 0) {
            return;
        }
        int width = font.getStringWidth(label) + 6;
        int x = Math.max(2, Math.min(anchorRight - width,
                mouseX - width / 2));
        int y = mouseY - 14;
        Gui.drawRect(x, y, x + width, y + 11,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SURFACE_RGB, 0xE6));
        LostTalesChatVisualStyle.drawPlain(font, label, x + 3, y + 2, 255);
    }

    /** Scissors the body rectangle in window pixels; false if unavailable. */
    private boolean beginBodyClip(Minecraft minecraft, Layout layout,
                                  int slide) {
        try {
            ScaledResolution resolution = new ScaledResolution(minecraft,
                    minecraft.displayWidth, minecraft.displayHeight);
            int factor = Math.max(1, resolution.getScaleFactor());
            GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_ENABLE_BIT);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(layout.left * factor,
                    (resolution.getScaledHeight() - (layout.bodyBottom + slide))
                            * factor,
                    panelWidth() * factor,
                    Math.max(0, layout.bodyBottom - layout.bodyTop) * factor);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void endBodyClip(boolean clipped) {
        if (clipped) {
            GL11.glPopAttrib();
        }
    }

    private void ensureSearchField(FontRenderer font) {
        if (this.searchField == null && font != null) {
            this.searchField = new GuiTextField(font, 0, 0, 10,
                    SEARCH_HEIGHT);
            this.searchField.setMaxStringLength(32);
            this.searchField.setEnableBackgroundDrawing(false);
            this.searchField.setTextColor(LostTalesChatVisualStyle.IVORY);
        }
    }

    private void positionSearchField(Layout layout) {
        if (this.searchField != null) {
            this.searchField.xPosition = layout.left + PADDING + 3;
            this.searchField.yPosition = layout.searchY;
            this.searchField.width = panelWidth() - PADDING * 2 - 6;
            this.searchField.height = SEARCH_HEIGHT - 3;
        }
    }

    String searchQuery() {
        return this.searchField == null ? ""
                : this.searchField.getText().trim()
                        .toLowerCase(Locale.ROOT);
    }

    private String collapseKey(String label) {
        return getClass().getSimpleName() + ':' + label;
    }

    private boolean isCollapsed(String key) {
        return COLLAPSED.contains(key);
    }

    private void toggleCollapsed(String key) {
        if (!COLLAPSED.remove(key)) {
            COLLAPSED.add(key);
        }
    }

    /**
     * Sections and cell positions for the current search, fold and scroll
     * state. The panel takes its natural height up to the screen's middle;
     * beyond that the body scrolls. Rebuilt on demand; candidate counts are
     * small enough that this costs nothing measurable per frame.
     */
    private Layout buildLayout(int anchorRight, int screenHeight) {
        Layout layout = new Layout();
        List<Section> sections = buildSections(searchQuery());
        int bodyHeight = 0;
        for (Section section : sections) {
            if (section.label != null) {
                bodyHeight += LABEL_HEIGHT;
                if (section.collapsible
                        && isCollapsed(collapseKey(section.label))) {
                    continue;
                }
            }
            bodyHeight += rowsOf(section) * cellHeight();
        }
        int frame = PADDING + SEARCH_HEIGHT + PADDING;
        int maxHeight = Math.max(frame + cellHeight(),
                screenHeight - PANEL_BOTTOM_MARGIN - screenHeight / 2);
        layout.height = Math.min(frame + bodyHeight, maxHeight);
        layout.left = anchorRight - panelWidth() - BUTTON_MARGIN;
        layout.top = screenHeight - PANEL_BOTTOM_MARGIN - layout.height;
        layout.searchY = layout.top + PADDING + 1;
        layout.bodyTop = layout.top + PADDING + SEARCH_HEIGHT;
        layout.bodyBottom = layout.top + layout.height - PADDING;
        layout.maxScroll = Math.max(0,
                bodyHeight - (layout.bodyBottom - layout.bodyTop));
        this.scroll = Math.max(0, Math.min(layout.maxScroll, this.scroll));

        int cursorY = layout.bodyTop - this.scroll;
        for (Section section : sections) {
            if (section.label != null) {
                layout.labels.add(new Label(section.label,
                        collapseKey(section.label), section.collapsible,
                        layout.left + PADDING + 1, cursorY));
                cursorY += LABEL_HEIGHT;
                if (section.collapsible
                        && isCollapsed(collapseKey(section.label))) {
                    continue;
                }
            }
            for (int index = 0; index < section.entries.size(); index++) {
                layout.cells.add(new Cell(section.entries.get(index),
                        layout.left + PADDING
                                + (index % columns()) * cellWidth(),
                        cursorY + (index / columns()) * cellHeight()));
            }
            cursorY += rowsOf(section) * cellHeight();
        }
        return layout;
    }

    /**
     * Rows a section occupies. A labelled section with nothing in it is
     * its label alone (an empty-state line); an unlabelled empty result
     * list still keeps one blank row so the panel has a body.
     */
    private int rowsOf(Section section) {
        if (section.entries.isEmpty()) {
            return section.label == null ? 1 : 0;
        }
        return (section.entries.size() + columns() - 1) / columns();
    }

    private float openProgress() {
        if (!LostTalesConfig.enableChatAnimations) {
            return this.targetOpen ? 1.0F : 0.0F;
        }
        long duration = Math.max(1,
                LostTalesConfig.chatSelectorAnimationDurationMillis)
                * 1000000L;
        float elapsed = Math.min(1.0F,
                (System.nanoTime() - this.transitionNanos)
                        / (float)duration);
        float eased = LostTalesChatMotion.menuProgress(elapsed);
        return this.targetOpen ? eased : 1.0F - eased;
    }

    int panelWidth() {
        return columns() * cellWidth() + PADDING * 2;
    }

    /** Cells per row; one for list-style pickers. */
    abstract int columns();

    abstract int cellWidth();

    abstract int cellHeight();

    /** Sections for the lowercased query; empty query lists everything. */
    abstract List<Section> buildSections(String query);

    abstract void drawEntry(Minecraft minecraft, Entry entry, int x, int y,
                            int alpha, boolean hovered);

    /** Hover label for a cell, or null for none. */
    abstract String tooltip(Entry entry);

    /** The text a chosen cell inserts at the input cursor. */
    abstract String insertionText(Entry entry);

    /** The button's icon; {@code lifted} while hovered or open. */
    abstract void drawButtonIcon(Minecraft minecraft, int left, int top,
                                 boolean lifted);

    static final class Section {
        final String label;
        final boolean collapsible;
        final List<Entry> entries;

        Section(String label, boolean collapsible, List<Entry> entries) {
            this.label = label;
            this.collapsible = collapsible;
            this.entries = entries == null
                    ? Collections.<Entry>emptyList() : entries;
        }
    }

    /** One selectable cell; the payload is the subclass's own type. */
    static final class Entry {
        final Object value;

        Entry(Object value) {
            this.value = value;
        }
    }

    private static final class Cell {
        final Entry entry;
        final int x;
        final int y;

        Cell(Entry entry, int x, int y) {
            this.entry = entry;
            this.x = x;
            this.y = y;
        }
    }

    private static final class Label {
        final String text;
        final String key;
        final boolean collapsible;
        final int x;
        final int y;

        Label(String text, String key, boolean collapsible, int x, int y) {
            this.text = text;
            this.key = key;
            this.collapsible = collapsible;
            this.x = x;
            this.y = y;
        }
    }

    private static final class Layout {
        final List<Cell> cells = new ArrayList<Cell>();
        final List<Label> labels = new ArrayList<Label>();
        int left;
        int top;
        int height;
        int searchY;
        /** Visible body: rows are clipped to [bodyTop, bodyBottom). */
        int bodyTop;
        int bodyBottom;
        int maxScroll;

        /** Whether any part of a row at {@code y} lies inside the body. */
        boolean showsRow(int y, int rowHeight) {
            return y + rowHeight > this.bodyTop && y < this.bodyBottom;
        }
    }
}
