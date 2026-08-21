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
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * Shared frame for the share pickers toggled from the small buttons beside
 * the chat input: a search field on top and collapsible sections of cells
 * below, anchored above the input at the right edge. Subclasses supply the
 * sections for a query, draw one cell, and say what a chosen cell inserts;
 * everything else — open/close easing, search input, section folding, hit
 * testing, hover tooltip, pointer-region registration — lives here so the
 * emote, item and marker pickers behave identically. Geometry is derived
 * from the live screen size, so GUI scale and resolution changes are
 * handled.
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

    int buttonLeft(int screenWidth) {
        return screenWidth - BUTTON_MARGIN
                - (this.buttonIndex + 1) * (BUTTON_SIZE + BUTTON_MARGIN)
                + BUTTON_MARGIN;
    }

    static int buttonTop(int screenHeight) {
        return screenHeight - 14;
    }

    boolean isInsideButton(int mouseX, int mouseY,
                           int screenWidth, int screenHeight) {
        int left = buttonLeft(screenWidth);
        int top = buttonTop(screenHeight);
        return mouseX >= left && mouseX < left + BUTTON_SIZE
                && mouseY >= top && mouseY < top + BUTTON_SIZE;
    }

    boolean isInsidePanel(int mouseX, int mouseY,
                          int screenWidth, int screenHeight) {
        if (!this.targetOpen) {
            return false;
        }
        Layout layout = buildLayout(screenWidth, screenHeight);
        return mouseX >= layout.left && mouseX < layout.left + panelWidth()
                && mouseY >= layout.top
                && mouseY < layout.top + layout.height;
    }

    /**
     * Click handling inside the panel: search focus and section folding.
     * Returns true when a section header consumed the click.
     */
    boolean mouseClicked(int mouseX, int mouseY, int button,
                         int screenWidth, int screenHeight) {
        if (!this.targetOpen) {
            return false;
        }
        Layout layout = buildLayout(screenWidth, screenHeight);
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
                    && mouseY >= label.y && mouseY < label.y + LABEL_HEIGHT) {
                toggleCollapsed(label.key);
                return true;
            }
        }
        return false;
    }

    /** The cell under the mouse while the picker is open, else null. */
    Entry entryAt(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!this.targetOpen) {
            return null;
        }
        Layout layout = buildLayout(screenWidth, screenHeight);
        for (Cell cell : layout.cells) {
            if (mouseX >= cell.x && mouseX < cell.x + cellWidth()
                    && mouseY >= cell.y && mouseY < cell.y + cellHeight()) {
                return cell.entry;
            }
        }
        return null;
    }

    void draw(Minecraft minecraft, ChatPointerRegions regions,
              int screenWidth, int screenHeight, int mouseX, int mouseY) {
        this.hoveredEntry = null;
        drawButton(minecraft, regions, screenWidth, screenHeight,
                mouseX, mouseY);
        drawPanel(minecraft, regions, screenWidth, screenHeight,
                mouseX, mouseY);
        drawTooltip(minecraft.fontRenderer, mouseX, mouseY, screenWidth);
    }

    private void drawButton(Minecraft minecraft, ChatPointerRegions regions,
                            int screenWidth, int screenHeight,
                            int mouseX, int mouseY) {
        int left = buttonLeft(screenWidth);
        int top = buttonTop(screenHeight);
        boolean lifted = this.targetOpen || isInsideButton(mouseX, mouseY,
                screenWidth, screenHeight);
        // A bare icon with the shared shadow; hover and open states lift it
        // a pixel rather than painting a backdrop.
        drawButtonIcon(minecraft, left, top - (lifted ? 1 : 0));
        regions.add(left, top, left + BUTTON_SIZE, top + BUTTON_SIZE);
    }

    private void drawPanel(Minecraft minecraft, ChatPointerRegions regions,
                           int screenWidth, int screenHeight,
                           int mouseX, int mouseY) {
        float progress = openProgress();
        if (progress <= 0.0F) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        ensureSearchField(font);
        Layout layout = buildLayout(screenWidth, screenHeight);
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
        for (Label label : layout.labels) {
            String glyph = label.collapsible
                    ? (isCollapsed(label.key) ? "+ " : "- ") : "";
            LostTalesChatVisualStyle.drawPlain(font, glyph + label.text,
                    label.x, label.y + slide, Math.min(textAlpha, 170));
        }
        for (Cell cell : layout.cells) {
            boolean hovered = this.targetOpen && slide == 0
                    && mouseX >= cell.x && mouseX < cell.x + cellWidth()
                    && mouseY >= cell.y && mouseY < cell.y + cellHeight();
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
                             int screenWidth) {
        Entry entry = this.hoveredEntry;
        String label = entry == null ? null : tooltip(entry);
        if (label == null || label.length() == 0) {
            return;
        }
        int width = font.getStringWidth(label) + 6;
        int x = Math.max(2, Math.min(screenWidth - width - 2,
                mouseX - width / 2));
        int y = mouseY - 14;
        Gui.drawRect(x, y, x + width, y + 11,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SURFACE_RGB, 0xE6));
        LostTalesChatVisualStyle.drawPlain(font, label, x + 3, y + 2, 255);
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
     * Sections and resting cell positions for the current search and fold
     * state. Rebuilt on demand; candidate counts are small enough that
     * this costs nothing measurable per frame.
     */
    private Layout buildLayout(int screenWidth, int screenHeight) {
        Layout layout = new Layout();
        List<Section> sections = buildSections(searchQuery());
        int height = PADDING + SEARCH_HEIGHT;
        for (Section section : sections) {
            if (section.label != null) {
                height += LABEL_HEIGHT;
                if (section.collapsible
                        && isCollapsed(collapseKey(section.label))) {
                    continue;
                }
            }
            height += rowsOf(section) * cellHeight();
        }
        height += PADDING;

        layout.height = height;
        layout.left = screenWidth - panelWidth() - BUTTON_MARGIN;
        layout.top = screenHeight - PANEL_BOTTOM_MARGIN - height;
        layout.searchY = layout.top + PADDING + 1;

        int cursorY = layout.top + PADDING + SEARCH_HEIGHT;
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

    private int rowsOf(Section section) {
        return Math.max(1, (section.entries.size() + columns() - 1)
                / columns());
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

    abstract void drawButtonIcon(Minecraft minecraft, int left, int top);

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
    }
}
