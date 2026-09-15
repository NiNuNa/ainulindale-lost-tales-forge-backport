package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.gui.animation.LostTalesUiEasing;
import com.ninuna.losttales.client.gui.animation.LostTalesUiTransition;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import com.ninuna.losttales.client.gui.tooltip.LostTalesTooltipSmoothing;
import net.minecraft.client.gui.Gui;
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
 * lives here so the emoji, item, marker and quest pickers behave
 * identically. The panel never grows past the middle of the screen: a
 * list taller than that scrolls inside it (mouse wheel over the panel),
 * clipped to the body below the search row. Geometry is derived from the
 * live screen size, so GUI scale and resolution changes are handled.
 */
abstract class ChatPickerPanel {
    /** How far the button has crossed to its hovered artwork, and when. */
    private float buttonFade;
    private long buttonFadeNanos;
    static final int BUTTON_SIZE = 12;
    static final int BUTTON_MARGIN = 2;
    /** The buttons stand this far below the anchor the caller passes. */
    static final int BUTTON_ANCHOR_OFFSET = 14;
    static final int PADDING = 4;
    static final int SEARCH_HEIGHT = 12;
    /**
     * Room the search row's magnifier takes before the field: the glyph
     * and the gap an icon keeps from its label in the chat's lists.
     */
    private static final int SEARCH_ICON_RUN =
            ChatIconSheet.SEARCH.getWidth() + ChatChannelIcons.GAP;
    static final int LABEL_HEIGHT = 10;
    /** Anchor-to-panel-bottom distance: the panel ends just above the
     *  bar the buttons stand in. */
    static final int PANEL_BOTTOM_MARGIN = BUTTON_ANCHOR_OFFSET + 3;
    /** Folded sections persist for the session, per picker and label. */
    private static final Set<String> COLLAPSED = new HashSet<String>();

    /** Closer than this to the target and the drawn scroll arrives. */
    private static final double SCROLL_SNAP_PIXELS = 0.5D;

    private boolean targetOpen;
    /**
     * How far the panel has opened. A panel reopened before it has
     * finished closing sets out from the size it is showing rather than
     * from nothing, so a button clicked twice quickly never makes the
     * panel collapse and grow again.
     */
    private final LostTalesUiTransition openness =
            new LostTalesUiTransition();
    private ChatInputField searchField;
    private int buttonIndex;
    private Entry hoveredEntry;
    /**
     * Pixels the body is asked to be scrolled up by — the wheel's
     * target; clamped on every layout.
     */
    private int scroll;
    /**
     * Pixels the body is drawn scrolled up by, easing toward
     * {@link #scroll} with the same shared motion the history glides
     * with, so a wheel turn slides the rows instead of jumping them.
     * The layout is built from this, so hit testing always answers for
     * what is on screen.
     */
    private double renderedScroll;
    private long scrollNanos;
    /**
     * How far below its place the panel was last drawn, in whole pixels,
     * while it opens or closes. The layout carries it, so every hit test
     * answers for the panel where it shows.
     */
    private int drawnSlide;

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
            this.scroll = 0;
            this.renderedScroll = 0.0D;
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

    /**
     * Scrolls the body; positive moves the list up (shows later rows).
     * Only the target moves here — the drawn rows glide after it, so
     * rapid wheel turns accumulate and stay responsive.
     */
    void scrollBy(int pixels) {
        this.scroll = Math.max(0, this.scroll + pixels);
    }

    /**
     * Advances the drawn scroll toward its target, once per drawn
     * frame; with chat animations off it simply arrives. Time-based, so
     * the glide reads the same at every frame rate.
     */
    private void advanceScrollEasing() {
        long now = System.nanoTime();
        double elapsed = (now - this.scrollNanos) / 1.0E9D;
        this.scrollNanos = now;
        if (!LostTalesConfig.enableChatAnimations
                || Math.abs(this.scroll - this.renderedScroll)
                        <= SCROLL_SNAP_PIXELS) {
            this.renderedScroll = this.scroll;
            return;
        }
        this.renderedScroll = LostTalesChatMotion.approach(
                this.renderedScroll, this.scroll, elapsed,
                LostTalesChatMotion.SCROLL_EASE_SECONDS);
    }

    /** Button left edge; {@code anchorRight} is the input bar's right edge. */
    int buttonLeft(int anchorRight) {
        return anchorRight - BUTTON_MARGIN
                - (this.buttonIndex + 1) * (BUTTON_SIZE + BUTTON_MARGIN)
                + BUTTON_MARGIN;
    }

    static int buttonTop(int anchorY) {
        return anchorY - BUTTON_ANCHOR_OFFSET;
    }

    boolean isInsideButton(double mouseX, double mouseY,
                           int anchorRight, int anchorY) {
        int left = buttonLeft(anchorRight);
        int top = buttonTop(anchorY);
        return mouseX >= left && mouseX < left + BUTTON_SIZE
                && mouseY >= top && mouseY < top + BUTTON_SIZE;
    }

    boolean isInsidePanel(double mouseX, double mouseY,
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
     * A press inside the panel: the search field takes it in whole
     * pixels, as a text field does, and a folding section's label folds
     * or unfolds. True when a label took the press.
     */
    boolean mouseClicked(int mouseX, int mouseY, double pointerX,
                         double pointerY, int button, int anchorRight,
                         int screenHeight) {
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
        String key = labelAt(layout, pointerX, pointerY);
        if (key == null) {
            return false;
        }
        toggleCollapsed(key);
        return true;
    }

    /** The key of the folding section label under the point, or null. */
    String labelAt(double x, double y, int anchorRight, int screenHeight) {
        return this.targetOpen
                ? labelAt(buildLayout(anchorRight, screenHeight), x, y) : null;
    }

    private String labelAt(Layout layout, double x, double y) {
        for (Label label : layout.labels) {
            if (label.collapsible && x >= layout.left
                    && x < layout.left + panelWidth()
                    && y >= label.y && y < label.y + LABEL_HEIGHT
                    && layout.showsRow(label.y, LABEL_HEIGHT)) {
                return label.key;
            }
        }
        return null;
    }

    /**
     * The cell under the point while the picker is open, else null: the
     * one test the cell's highlight, a press and the pointer all ask.
     */
    Entry entryAt(double x, double y, int anchorRight, int screenHeight) {
        if (!this.targetOpen) {
            return null;
        }
        Cell cell = cellAt(buildLayout(anchorRight, screenHeight), x, y);
        return cell == null ? null : cell.entry;
    }

    /** The cell under the point where the layout puts it, or null. */
    private Cell cellAt(Layout layout, double x, double y) {
        for (Cell cell : layout.cells) {
            if (cellContains(layout, cell, x, y)) {
                return cell;
            }
        }
        return null;
    }

    /**
     * Whether the point is on the cell as it shows: inside the cell and
     * inside the body the list is clipped to.
     */
    private boolean cellContains(Layout layout, Cell cell, double x,
                                 double y) {
        return x >= cell.x && x < cell.x + cellWidth()
                && y >= cell.y && y < cell.y + cellHeight()
                && y >= layout.bodyTop && y < layout.bodyBottom
                && layout.showsRow(cell.y, cellHeight());
    }

    /**
     * Draws the button and, while open, the panel. {@code pointerX}/
     * {@code pointerY} is the pointer while the picker has it, else
     * {@link ChatHover#AWAY}; the tooltip stands beside the whole-pixel
     * {@code tipX}/{@code tipY}.
     */
    void draw(Minecraft minecraft, ChatPointerRegions regions,
              int anchorRight, int screenHeight, double pointerX,
              double pointerY, int tipX, int tipY) {
        this.hoveredEntry = null;
        drawButton(regions, anchorRight, screenHeight, pointerX, pointerY);
        drawPanel(minecraft, regions, anchorRight, screenHeight,
                pointerX, pointerY);
        drawTooltip(minecraft.fontRenderer, tipX, tipY, anchorRight);
    }

    /**
     * The button's glyph at rest, where the bar it stands on is not the
     * one being typed in: no lift, no lit artwork, nothing registered
     * for the pointer.
     */
    void drawRestingButton(int anchorRight, int anchorY) {
        int left = buttonLeft(anchorRight);
        int top = buttonTop(anchorY);
        ChatIconSheet glyph = buttonGlyph();
        ChatIconSheet.drawPairWithShadow(glyph, buttonGlyphLit(), 0.0F,
                left + (BUTTON_SIZE - glyph.getWidth()) / 2,
                top + (BUTTON_SIZE - glyph.getHeight()) / 2, 255);
    }

    private void drawButton(ChatPointerRegions regions, int anchorRight,
                            int screenHeight, double mouseX, double mouseY) {
        int left = buttonLeft(anchorRight);
        int top = buttonTop(screenHeight);
        boolean lifted = this.targetOpen || isInsideButton(mouseX, mouseY,
                anchorRight, screenHeight);
        long now = System.nanoTime();
        double elapsed = this.buttonFadeNanos == 0L ? 0.0D
                : (now - this.buttonFadeNanos) / 1.0E9D;
        this.buttonFadeNanos = now;
        this.buttonFade = LostTalesChatVisualStyle.hoverFade(this.buttonFade,
                lifted, elapsed);
        // A bare glyph with the shared shadow, centred in the button's
        // square; hover and open states lift it a pixel rather than
        // painting a backdrop, and the glyph crosses to its lit artwork
        // rather than swapping to it.
        ChatIconSheet glyph = buttonGlyph();
        ChatIconSheet.drawPairWithShadow(glyph, buttonGlyphLit(),
                this.buttonFade,
                left + (BUTTON_SIZE - glyph.getWidth()) / 2,
                top - (lifted ? 1 : 0)
                        + (BUTTON_SIZE - glyph.getHeight()) / 2, 255);
        regions.add(left, top, left + BUTTON_SIZE, top + BUTTON_SIZE);
    }

    private void drawPanel(Minecraft minecraft, ChatPointerRegions regions,
                           int anchorRight, int screenHeight,
                           double mouseX, double mouseY) {
        float progress = openProgress();
        if (progress <= 0.0F) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        ensureSearchField(font);
        advanceScrollEasing();
        // The panel rises the last few pixels into place as it opens.
        this.drawnSlide = Math.round((1.0F - progress) * 5.0F);
        Layout layout = buildLayout(anchorRight, screenHeight);
        positionSearchField(layout);
        int right = layout.left + panelWidth();
        int bottom = layout.top + layout.height;
        regions.add(layout.left, layout.top, right, bottom);
        // The hovered cell is lit in the panel's surface rather than over
        // it, cut to the body the list is clipped to.
        Cell lit = this.targetOpen ? cellAt(layout, mouseX, mouseY) : null;
        this.hoveredEntry = lit == null ? null : lit.entry;
        if (lit == null) {
            LostTalesChatVisualStyle.drawPopup(layout.left, layout.top,
                    right, bottom, progress);
        } else {
            LostTalesChatVisualStyle.drawPopup(layout.left, layout.top,
                    right, bottom, progress, lit.x,
                    Math.max(layout.bodyTop, lit.y), lit.x + cellWidth(),
                    Math.min(layout.bodyBottom, lit.y + cellHeight()));
        }

        int textAlpha = Math.max(LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA,
                Math.min(255, Math.round(255.0F * progress)));
        drawSearchRow(font, layout, textAlpha);
        // Rows are clipped to the body so a scrolled list never paints
        // over the search row or past the panel's bottom edge.
        boolean clipped = beginBodyClip(minecraft, layout);
        try {
            for (Label label : layout.labels) {
                if (!layout.showsRow(label.y, LABEL_HEIGHT)) {
                    continue;
                }
                String glyph = label.collapsible
                        ? (isCollapsed(label.key) ? "+ " : "- ") : "";
                LostTalesChatVisualStyle.drawPlain(font, glyph + label.text,
                        label.x, label.y, textAlpha);
            }
            for (Cell cell : layout.cells) {
                if (!layout.showsRow(cell.y, cellHeight())) {
                    continue;
                }
                drawEntry(minecraft, cell.entry, cell.x, cell.y, textAlpha,
                        cell == lit);
            }
        } finally {
            endBodyClip(clipped);
        }
        drawScrollbar(layout, textAlpha);
    }

    /**
     * A thin track at the right edge while the list is longer than the
     * body, the thumb standing in it rather than over it.
     */
    private void drawScrollbar(Layout layout, int alpha) {
        if (layout.maxScroll <= 0) {
            return;
        }
        int bodyHeight = layout.bodyBottom - layout.bodyTop;
        int contentHeight = bodyHeight + layout.maxScroll;
        int thumbHeight = Math.max(4, bodyHeight * bodyHeight / contentHeight);
        // The thumb follows the drawn offset, so it glides with the rows.
        int thumbTop = layout.bodyTop
                + (bodyHeight - thumbHeight)
                        * (int)Math.round(this.renderedScroll)
                        / layout.maxScroll;
        int thumbBottom = thumbTop + thumbHeight;
        int x = layout.left + panelWidth() - 2;
        int track = LostTalesChatVisualStyle.argb(
                LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB,
                Math.min(alpha, 120));
        Gui.drawRect(x, layout.bodyTop, x + 1, thumbTop, track);
        Gui.drawRect(x, thumbBottom, x + 1, layout.bodyBottom, track);
        Gui.drawRect(x, thumbTop, x + 1, thumbBottom,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.IVORY, Math.min(alpha, 200)));
    }

    /**
     * The search row as the chat's menus draw theirs: the magnifier it
     * opens with; what has been typed, in the chat's own text field;
     * while it is empty, the prompt in the chat's aside tone and
     * italics, as the input bar's hint is, a pixel clear of the caret;
     * and a hairline under it that parts it from the list.
     */
    private void drawSearchRow(FontRenderer font, Layout layout, int alpha) {
        Gui.drawRect(layout.left + PADDING, layout.bodyTop - 1,
                layout.left + panelWidth() - PADDING, layout.bodyTop,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB,
                        Math.min(alpha, 0xA0)));
        // The magnifier stands on the capitals of what is typed beside
        // it, as every icon in a chat row does.
        ChatIconSheet.SEARCH.drawWithShadow(layout.left + PADDING + 1,
                layout.searchY + LostTalesChatOverlayRenderer.centredBoxTop(
                        ChatIconSheet.SEARCH.getHeight()), alpha);
        if (this.searchField == null) {
            return;
        }
        if (this.searchField.getText().length() == 0) {
            int x = this.searchField.xPosition + ChatInputField.CARET_WIDTH
                    + 1;
            String prompt = LostTalesSkyrimUiStyle.trimToWidth(font,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.share.search"),
                    this.searchField.xPosition + this.searchField.getWidth()
                            - x);
            LostTalesChatVisualStyle.drawColored(font, "§o" + prompt, x,
                    layout.searchY, LostTalesChatVisualStyle.asideRgb(),
                    alpha);
        }
        this.searchField.drawTextBox();
    }

    /** The hovered cell's name, as the chat's pointer tip is drawn. */
    private void drawTooltip(FontRenderer font, int mouseX, int mouseY,
                             int anchorRight) {
        Entry entry = this.hoveredEntry;
        String label = entry == null ? null : tooltip(entry);
        if (label == null || label.length() == 0) {
            return;
        }
        int width = font.getStringWidth(label) + 8;
        int x = Math.max(2, Math.min(anchorRight - width,
                mouseX - width / 2));
        int y = mouseY - 15;
        // Beside the pointer, so it keeps pace with the cursor rather
        // than with the interface grid, as every other tooltip does.
        LostTalesTooltipSmoothing.begin(mouseX, mouseY);
        try {
            LostTalesChatVisualStyle.drawPopup(x, y, x + width, y + 12,
                    1.0F);
            LostTalesChatVisualStyle.drawPlain(font, label, x + 4, y + 2, 255);
        } finally {
            LostTalesTooltipSmoothing.end();
        }
    }

    /** Scissors the body rectangle in window pixels; false if unavailable. */
    private boolean beginBodyClip(Minecraft minecraft, Layout layout) {
        try {
            ScaledResolution resolution = new ScaledResolution(minecraft,
                    minecraft.displayWidth, minecraft.displayHeight);
            int factor = Math.max(1, resolution.getScaleFactor());
            GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_ENABLE_BIT);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(layout.left * factor,
                    (resolution.getScaledHeight() - layout.bodyBottom)
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
            this.searchField = new ChatInputField(font, 0, 0, 10,
                    SEARCH_HEIGHT).plainText();
            this.searchField.setMaxStringLength(32);
            this.searchField.setEnableBackgroundDrawing(false);
            this.searchField.setTextColor(LostTalesChatVisualStyle.IVORY);
        }
    }

    /**
     * The field after the magnifier, which stands in line with the
     * section labels under it, leaving the caret its column at the far
     * end.
     */
    private void positionSearchField(Layout layout) {
        if (this.searchField != null) {
            this.searchField.xPosition = layout.left + PADDING + 1
                    + SEARCH_ICON_RUN;
            this.searchField.yPosition = layout.searchY;
            this.searchField.width = panelWidth() - PADDING * 2 - 2
                    - ChatInputField.CARET_WIDTH - SEARCH_ICON_RUN;
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
    private Layout buildLayout(int anchorRight, int anchorY) {
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
        int minHeight = frame + cellHeight();
        // The cap is a share of the real screen — the same size wherever
        // the window's bar happens to sit; only the room actually left
        // above the bar may shrink the panel below it.
        int cap = Math.max(minHeight, screenCapHeight());
        int room = Math.max(minHeight, anchorY - PANEL_BOTTOM_MARGIN - 2);
        layout.height = Math.min(frame + bodyHeight, Math.min(cap, room));
        layout.left = anchorRight - panelWidth() - BUTTON_MARGIN;
        layout.top = anchorY - PANEL_BOTTOM_MARGIN - layout.height
                + this.drawnSlide;
        layout.searchY = layout.top + PADDING + 1;
        layout.bodyTop = layout.top + PADDING + SEARCH_HEIGHT;
        layout.bodyBottom = layout.top + layout.height - PADDING;
        layout.maxScroll = Math.max(0,
                bodyHeight - (layout.bodyBottom - layout.bodyTop));
        this.scroll = Math.max(0, Math.min(layout.maxScroll, this.scroll));
        // The drawn offset is bounded too, so shrinking content — a
        // narrower search, a folded section — never leaves the rows
        // gliding through space the list no longer has.
        this.renderedScroll = Math.max(0.0D,
                Math.min(layout.maxScroll, this.renderedScroll));

        int cursorY = layout.bodyTop - (int)Math.round(this.renderedScroll);
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

    /** Half the real screen: one panel size at every bar position. */
    private static int screenCapHeight() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            ScaledResolution resolution = new ScaledResolution(minecraft,
                    minecraft.displayWidth, minecraft.displayHeight);
            return resolution.getScaledHeight() / 2;
        } catch (RuntimeException unavailable) {
            return 120;
        }
    }

    private float openProgress() {
        return this.openness.advance(System.nanoTime(), this.targetOpen,
                LostTalesConfig.enableChatAnimations
                        ? Math.max(1, LostTalesConfig
                                .chatSelectorAnimationDurationMillis)
                        : 0,
                LostTalesUiEasing.SETTLE);
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

    /** The button's glyph, a cell of the chat's sheet. */
    abstract ChatIconSheet buttonGlyph();

    /** The same glyph lit, which the button crosses to under the pointer. */
    abstract ChatIconSheet buttonGlyphLit();

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
