package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.PointerRegions;
import com.ninuna.losttales.client.window.SubWindow;
import com.ninuna.losttales.client.window.SubWindowContent;
import com.ninuna.losttales.client.window.TabIcons;
import com.ninuna.losttales.client.window.WheelStep;
import com.ninuna.losttales.client.window.WindowHover;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiTheme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import com.ninuna.losttales.client.gui.tooltip.LostTalesTooltipSmoothing;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.motion.MotionIds;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.StatCollector;

/**
 * What the share pickers toggled from the small buttons beside the chat
 * input have in common: the button on the bar, and a sub-window holding
 * a search field on top and collapsible sections of cells below.
 * Subclasses supply the sections for a query, draw one cell, and say what
 * a chosen cell inserts; everything else — search input, section folding,
 * scrolling, hit testing, the hover tooltip — lives here, so the emoji,
 * item, marker and quest pickers behave alike. A grid gains columns as
 * its window widens and a list's rows stretch with it; a list taller than
 * the window scrolls inside it (mouse wheel over the list), clipped to
 * the body below the search row.
 */
abstract class ChatPickerPanel extends SubWindowContent {
    /**
     * The toggle's own beat. It stays risen while its window is out, as
     * well as under the pointer, so the button reads as held down by
     * what it opened.
     */
    private final LostTalesUiButtonMotion buttonMotion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
    /** A bar button's square, round its ten-pixel glyph. */
    static final int BUTTON_SIZE = 12;
    /** Clear pixels round a ten-pixel glyph inside its square. */
    private static final int SQUARE_INSET = (BUTTON_SIZE - TabIcons.SIZE) / 2;
    /** Between two squares, so the glyphs in them stand a button's gap apart. */
    static final int BUTTON_MARGIN = WindowStyle.BUTTON_GAP - 2 * SQUARE_INSET;
    /** From the bar's right edge to the send button's square: an edge's gap to its ink. */
    private static final int EDGE_MARGIN = WindowStyle.EDGE_GAP - SQUARE_INSET;
    /**
     * Between the send button's square and the next: the hairline that
     * parts sending from the inserts, an edge's gap from each glyph.
     */
    static final int SEND_ROOM = 2 * EDGE_MARGIN + WindowStyle.DIVIDER_WIDTH;
    /** The buttons stand this far below the anchor the caller passes. */
    static final int BUTTON_ANCHOR_OFFSET = 14;
    /** The menus' clear room round the search and the rows, across and down. */
    private static final int PADDING_X = MenuWindow.PADDING_X;
    private static final int PADDING_Y = MenuWindow.PADDING_Y;
    /** The search's press box: a line of text tall. */
    private static final int SEARCH_LINE = 9;
    /**
     * Room the search row's magnifier takes before the field: the glyph
     * and the gap an icon keeps from its label in the menus.
     */
    private static final int SEARCH_ICON_RUN =
            LostTalesUiSheet.SEARCH.getWidth() + TabIcons.GAP;
    /** A list picker's row icon: the menus' icon column and the label after it. */
    static final int ICON_LEFT = PADDING_X;
    static final int LABEL_LEFT = PADDING_X + TabIcons.SLOT + TabIcons.GAP;
    /**
     * Anchor-to-window-bottom distance where a picker first opens: just
     * above the bar the buttons stand in.
     */
    static final int PANEL_BOTTOM_MARGIN = BUTTON_ANCHOR_OFFSET + 3;
    /** The search row, a menu's field: a row and the hairline under it. */
    private static int searchHeight() {
        return MenuWindow.rowHeight() + 1;
    }

    /** A section's heading: a menu's row. */
    private static int labelHeight() {
        return MenuWindow.rowHeight();
    }

    /** The search row and the padding round it and the list. */
    private static int frameHeight() {
        return PADDING_Y + searchHeight() + PADDING_Y;
    }
    /** Folded sections persist for the session, per picker and label. */
    private static final Set<String> COLLAPSED = new HashSet<String>();

    /** Closer than this to the target and the drawn scroll arrives. */
    private static final double SCROLL_SNAP_PIXELS = 0.5D;

    /** Whether its window is open; what the button lights by. */
    private boolean shown;
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

    /** Position from the right edge: 0 is the rightmost button. */
    void setButtonIndex(int buttonIndex) {
        this.buttonIndex = Math.max(0, buttonIndex);
    }

    /** Whether the picker's window is open. */
    boolean isOpen() {
        return this.shown;
    }

    @Override
    public void opened() {
        this.shown = true;
    }

    /** A picker closes empty: the next opening starts at the top, nothing typed. */
    @Override
    public void closed() {
        this.shown = false;
        this.scroll = 0;
        this.renderedScroll = 0.0D;
        if (this.searchField != null) {
            this.searchField.setText("");
        }
        releaseKeys();
    }

    @Override
    public boolean holdsKeys() {
        return this.shown && this.searchField != null
                && this.searchField.isFocused();
    }

    /** A pick is written into the input, so the window waits for a bar. */
    @Override
    public boolean needsInputBar() {
        return true;
    }

    /**
     * While its search holds the keys every key is the search's, as a
     * menu's field keeps them: what the field cannot use goes no
     * further, so Enter never sends the message behind it and the arrows
     * never walk what was sent. The chat's own Ctrl shortcuts are
     * handled before the picker is asked.
     */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!holdsKeys()) {
            return false;
        }
        this.searchField.textboxKeyTyped(typedChar, keyCode);
        return true;
    }

    /**
     * The cell Enter takes from the search: the first one the typing
     * found, and none while nothing is typed, as a menu's search does.
     */
    Entry firstFound() {
        if (!this.shown || searchQuery().length() == 0) {
            return null;
        }
        for (Section section : buildSections(searchQuery())) {
            if (!section.entries.isEmpty()) {
                return section.entries.get(0);
            }
        }
        return null;
    }

    /** The search gives the keys back, as the screen moves them elsewhere. */
    @Override
    public void releaseKeys() {
        if (this.searchField != null) {
            this.searchField.setFocused(false);
        }
    }

    /**
     * Scrolls the body by wheel lines; positive shows later rows. Only
     * the target moves here — the drawn rows glide after it, so rapid
     * wheel turns accumulate and stay responsive.
     */
    @Override
    public void scrollBy(int lines) {
        this.scroll = Math.max(0,
                this.scroll + WheelStep.pixels(lines, MenuWindow.rowHeight()));
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
        if (Math.abs(this.scroll - this.renderedScroll)
                <= SCROLL_SNAP_PIXELS) {
            this.renderedScroll = this.scroll;
            return;
        }
        this.renderedScroll = Motions.followTravel(MotionIds.WINDOW_SCROLL,
                this.renderedScroll, this.scroll, elapsed);
    }

    /* ---- The button on the bar ---- */

    /** Button left edge; {@code anchorRight} is the input bar's right edge. */
    int buttonLeft(int anchorRight) {
        return slotLeft(anchorRight, this.buttonIndex);
    }

    /**
     * Left edge of the bar button in slot {@code index}, counted from the
     * bar's right edge {@code barRight}: slot 0 the send button, an
     * edge's gap from the frame, then the hairline and the rest a
     * button's gap apart, ink to ink.
     */
    static int slotLeft(int barRight, int index) {
        int left = barRight - EDGE_MARGIN - BUTTON_SIZE;
        if (index > 0) {
            left -= SEND_ROOM + BUTTON_SIZE
                    + (index - 1) * (BUTTON_SIZE + BUTTON_MARGIN);
        }
        return left;
    }

    static int buttonTop(int anchorY) {
        return anchorY - BUTTON_ANCHOR_OFFSET;
    }

    boolean isInsideButton(double mouseX, double mouseY,
                           int anchorRight, int anchorY) {
        int left = buttonLeft(anchorRight);
        int top = buttonTop(anchorY);
        return LostTalesUiHitBox.contains(mouseX, mouseY, left, top, BUTTON_SIZE,
                BUTTON_SIZE);
    }

    /**
     * The button's glyph at rest, where the bar it stands on is not the
     * one being typed in: no lift, no lit artwork, nothing registered
     * for the pointer.
     */
    void drawRestingButton(int anchorRight, int anchorY) {
        int left = buttonLeft(anchorRight);
        int top = buttonTop(anchorY);
        LostTalesUiSheet glyph = buttonGlyph();
        LostTalesUiSheet.drawPairWithShadow(glyph, buttonGlyphLit(), 0.0F,
                left + (BUTTON_SIZE - glyph.getWidth()) / 2,
                top + (BUTTON_SIZE - glyph.getHeight()) / 2,
                ChatInputBar.faded(255));
    }

    /**
     * The button on the live bar. {@code pointerX}/{@code pointerY} is
     * the pointer while the button has it, else {@link WindowHover#AWAY}.
     * It stands risen while its window is out.
     */
    void drawButton(PointerRegions regions, int anchorRight,
                    int anchorY, double pointerX, double pointerY) {
        int left = buttonLeft(anchorRight);
        int top = buttonTop(anchorY);
        boolean inside = isInsideButton(pointerX, pointerY, anchorRight,
                anchorY);
        boolean lifted = this.shown || inside;
        this.buttonMotion.advance(System.nanoTime(), lifted, lifted,
                inside && org.lwjgl.input.Mouse.isButtonDown(0));
        // A bare glyph with the shared shadow, centred in the button's
        // square; hover and open states lift it rather than painting a
        // backdrop, and the glyph crosses to its lit artwork rather than
        // swapping to it.
        LostTalesUiSheet glyph = buttonGlyph();
        LostTalesUiButton.drawGlyph(glyph, buttonGlyphLit(),
                this.buttonMotion, left + (BUTTON_SIZE - glyph.getWidth()) / 2,
                top + (BUTTON_SIZE - glyph.getHeight()) / 2,
                ChatInputBar.faded(255));
        regions.add(left, top, left + BUTTON_SIZE, top + BUTTON_SIZE);
    }

    /**
     * Where the picker's window opens before the player has placed it:
     * its content box at the natural size, standing on the bar at the
     * bar's right edge, where the panel always hung.
     */
    LostTalesUiHitBox firstContentBox(int anchorRight, int anchorY) {
        int width = naturalWidth();
        int height = Math.min(naturalHeight(width),
                Math.max(minHeight(), anchorY - PANEL_BOTTOM_MARGIN
                        - SubWindow.STRIP_HEIGHT - 2));
        return new LostTalesUiHitBox(anchorRight - width - EDGE_MARGIN,
                anchorY - PANEL_BOTTOM_MARGIN - height, width, height);
    }

    /* ---- The window's content ---- */

    @Override
    public LostTalesUiSheet stripIcon() {
        return buttonGlyph();
    }

    @Override
    public int naturalWidth() {
        return naturalColumns() * cellWidth() + PADDING_X * 2;
    }

    /**
     * The whole list's height at {@code width} — the search row, every
     * section open or folded as it stands — up to half the real screen,
     * beyond which it scrolls.
     */
    @Override
    public int naturalHeight(int width) {
        int columns = columnsFor(width);
        int body = 0;
        for (Section section : buildSections(searchQuery())) {
            if (section.label != null) {
                body += labelHeight();
                if (section.collapsible
                        && isCollapsed(collapseKey(section.label))) {
                    continue;
                }
            }
            body += rowsOf(section, columns) * cellHeight();
        }
        return Math.max(minHeight(), Math.min(frameHeight() + body,
                screenCapHeight()));
    }

    @Override
    public int minWidth() {
        return stretchesCells() ? MIN_LIST_WIDTH
                : MIN_GRID_COLUMNS * cellWidth() + PADDING_X * 2;
    }

    @Override
    public int minHeight() {
        return frameHeight() + labelHeight() + cellHeight();
    }

    /** A list's narrowest window: room for a short name beside its icon. */
    private static final int MIN_LIST_WIDTH = 80;
    /** A grid's narrowest window, in columns. */
    private static final int MIN_GRID_COLUMNS = 3;

    /** Cells a row holds in a box {@code width} wide. */
    private int columnsFor(int width) {
        return stretchesCells() ? 1
                : Math.max(1, (width - PADDING_X * 2) / cellWidth());
    }

    /**
     * A cell's width in a box {@code width} wide: a list's rows stretch
     * the whole width, as a menu's do, and keep the padding inside them.
     */
    private int cellWidthFor(int width) {
        return stretchesCells() ? Math.max(1, width) : cellWidth();
    }

    @Override
    public ChatHover hoverAt(LostTalesUiHitBox box, double x, double y) {
        Layout layout = buildLayout(box);
        Cell cell = cellAt(layout, x, y);
        ChatHover hover = new ChatHover(cell != null
                ? ChatHover.Kind.PICKER_CELL
                : labelAt(layout, x, y) != null ? ChatHover.Kind.PICKER_LABEL
                        : ChatHover.Kind.PICKER);
        hover.picker = this;
        hover.pickerEntry = cell == null ? null : cell.entry;
        return hover;
    }

    /**
     * A press inside the window's content: the search field takes it in
     * whole pixels, as a text field does, and a folding section's label
     * folds or unfolds. True when a label took the press.
     */
    boolean mouseClicked(LostTalesUiHitBox box, int mouseX, int mouseY,
                         double pointerX, double pointerY, int button) {
        Layout layout = buildLayout(box);
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

    private String labelAt(Layout layout, double x, double y) {
        for (Label label : layout.labels) {
            if (label.collapsible && x >= layout.left
                    && x < layout.left + layout.width
                    && y >= label.y && y < label.y + labelHeight()
                    && layout.showsRow(label.y, labelHeight())) {
                return label.key;
            }
        }
        return null;
    }

    /**
     * The cell under the point in the window's content box, else null:
     * the one test the cell's highlight, a press and the pointer all ask.
     */
    Entry entryAt(LostTalesUiHitBox box, double x, double y) {
        Cell cell = cellAt(buildLayout(box), x, y);
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
        return x >= cell.x && x < cell.x + layout.cellWidth
                && y >= cell.y && y < cell.y + cellHeight()
                && y >= layout.bodyTop && y < layout.bodyBottom
                && layout.showsRow(cell.y, cellHeight());
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
              double clipY, double pointerX, double pointerY, int alpha,
              int surfaceAlpha) {
        this.hoveredEntry = null;
        FontRenderer font = minecraft.fontRenderer;
        ensureSearchField(font);
        advanceScrollEasing();
        Layout layout = buildLayout(box);
        positionSearchField(layout);
        // The hovered cell, cut to the body the list is clipped to, before
        // anything lands on it: a list's row as a menu's is, the frame's
        // ring beside it with it; a grid's cell alone.
        Cell lit = cellAt(layout, pointerX, pointerY);
        this.hoveredEntry = lit == null ? null : lit.entry;
        if (lit != null) {
            WindowLists.drawLitRow(layout.left, layout.left + layout.width,
                    lit.x, Math.max(layout.bodyTop, lit.y),
                    lit.x + layout.cellWidth,
                    Math.min(layout.bodyBottom, lit.y + cellHeight()),
                    surfaceAlpha);
        }
        String litLabel = labelAt(layout, pointerX, pointerY);
        drawSearchRow(font, layout, alpha);
        // Rows are clipped to the body so a scrolled list never paints
        // over the search row or past the window's bottom edge.
        boolean clipped = beginClip(minecraft,
                layout.left + clipX - box.left,
                layout.bodyTop + clipY - box.top, layout.width,
                Math.max(0, layout.bodyBottom - layout.bodyTop));
        try {
            LostTalesUiInk.beginContent();
            for (Label label : layout.labels) {
                if (!layout.showsRow(label.y, labelHeight())) {
                    continue;
                }
                String glyph = label.collapsible
                        ? (isCollapsed(label.key) ? "+ " : "- ") : "";
                WindowLists.drawHeading(font, glyph + label.text, label.x,
                        label.x, layout.left + layout.width - PADDING_X,
                        label.y, labelHeight(),
                        label.collapsible && label.key.equals(litLabel),
                        alpha);
            }
            for (Cell cell : layout.cells) {
                if (!layout.showsRow(cell.y, cellHeight())) {
                    continue;
                }
                drawEntry(minecraft, cell.entry, cell.x, cell.y,
                        layout.cellWidth, alpha, cell == lit);
            }
        } finally {
            endClip(clipped);
        }
        WindowLists.drawScroll(layout.left, layout.bodyTop,
                layout.left + layout.width, layout.top + layout.height,
                layout.bodyTop, layout.bodyBottom, this.renderedScroll,
                layout.maxScroll, alpha);
    }

    /**
     * The search row as a menu's field is: the magnifier it opens with,
     * standing on the capitals of what is typed beside it; what has been
     * typed, in the chat's own text field; while it is empty, the prompt
     * in the aside tone and italics, a pixel clear of the caret; and a
     * hairline under it that parts it from the list.
     */
    private void drawSearchRow(FontRenderer font, Layout layout, int alpha) {
        Gui.drawRect(layout.left + PADDING_X, layout.bodyTop - 1,
                layout.left + layout.width - PADDING_X, layout.bodyTop,
                LostTalesUiInk.argb(
                        LostTalesUiTheme.secondaryRgb(),
                        Math.min(alpha, 0xA0)));
        LostTalesUiInk.beginContent();
        LostTalesUiSheet.SEARCH.drawWithShadow(layout.left + PADDING_X,
                layout.searchY + WindowStyle.centredBoxTop(
                        LostTalesUiSheet.SEARCH.getHeight()), alpha);
        if (this.searchField == null) {
            return;
        }
        if (this.searchField.getText().length() == 0) {
            int x = this.searchField.xPosition + LostTalesUiCaret.WIDTH
                    + 1;
            String prompt = LostTalesSkyrimUiStyle.trimToWidth(font,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.share.search"),
                    this.searchField.xPosition + this.searchField.getWidth()
                            - x);
            LostTalesUiInk.drawText(font, "§o" + prompt, x,
                    layout.searchY, LostTalesChatVisualStyle.asideRgb(),
                    alpha);
        }
        this.searchField.drawTextBox();
    }

    @Override
    public String tipKey() {
        String label = this.hoveredEntry == null ? null : tooltip(this.hoveredEntry);
        return label == null ? "" : label;
    }

    /** The hovered cell's name, as the chat's pointer tip is drawn. */
    @Override
    public void drawTip(Minecraft minecraft, int tipX, int tipY, int screenWidth,
                        float share) {
        Entry entry = this.hoveredEntry;
        String label = entry == null ? null : tooltip(entry);
        if (label == null || label.length() == 0) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        int width = WindowStyle.popupLineWidth(font, label);
        int x = Math.max(2, Math.min(screenWidth - width - 2,
                tipX - width / 2));
        int y = tipY - 3 - WindowStyle.POPUP_LINE_HEIGHT;
        // Beside the pointer, so it keeps pace with the cursor rather
        // than with the interface grid, as every other tooltip does.
        LostTalesTooltipSmoothing.begin(tipX, tipY);
        try {
            WindowStyle.drawPopupLine(font, label, x, y, share);
        } finally {
            LostTalesTooltipSmoothing.end();
        }
    }

    private void ensureSearchField(FontRenderer font) {
        if (this.searchField == null && font != null) {
            this.searchField = new ChatInputField(font, 0, 0, 10,
                    SEARCH_LINE).plainText();
            this.searchField.setMaxStringLength(32);
            this.searchField.setEnableBackgroundDrawing(false);
            this.searchField.setTextColor(LostTalesUiInk.IVORY);
        }
    }

    /**
     * The box a press lands on the search field in, read from the numbers
     * {@link #positionSearchField} gives the field: what the field's own
     * press test asks, so the magnifier lights on the pixels a press
     * would put the caret from.
     */
    private LostTalesUiHitBox searchFieldBox() {
        return new LostTalesUiHitBox(this.searchField.xPosition,
                this.searchField.yPosition, this.searchField.width,
                this.searchField.height);
    }

    /**
     * The field after the magnifier, as a menu's field stands, leaving
     * the caret its column at the far end.
     */
    private void positionSearchField(Layout layout) {
        if (this.searchField != null) {
            this.searchField.xPosition = layout.left + PADDING_X
                    + SEARCH_ICON_RUN;
            this.searchField.yPosition = layout.searchY;
            this.searchField.width = Math.max(1, layout.width - PADDING_X * 2
                    - LostTalesUiCaret.WIDTH - SEARCH_ICON_RUN);
            this.searchField.height = SEARCH_LINE;
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
     * Sections and cell positions in the content box for the current
     * search, fold and scroll state; beyond the box's height the body
     * scrolls. Rebuilt on demand; candidate counts are small enough that
     * this costs nothing measurable per frame.
     */
    private Layout buildLayout(LostTalesUiHitBox box) {
        Layout layout = new Layout();
        layout.left = (int)Math.floor(box.left);
        layout.top = (int)Math.floor(box.top);
        layout.width = (int)Math.floor(box.width);
        layout.height = (int)Math.floor(box.height);
        int columns = columnsFor(layout.width);
        layout.cellWidth = cellWidthFor(layout.width);
        List<Section> sections = buildSections(searchQuery());
        int bodyHeight = 0;
        for (Section section : sections) {
            if (section.label != null) {
                bodyHeight += labelHeight();
                if (section.collapsible
                        && isCollapsed(collapseKey(section.label))) {
                    continue;
                }
            }
            bodyHeight += rowsOf(section, columns) * cellHeight();
        }
        layout.searchY = layout.top + PADDING_Y + LostTalesUiInk.centredStart(
                searchHeight() - 1, LostTalesUiInk.CAP_HEIGHT);
        layout.bodyTop = layout.top + PADDING_Y + searchHeight();
        layout.bodyBottom = layout.top + layout.height - PADDING_Y;
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
                        layout.left + PADDING_X, cursorY));
                cursorY += labelHeight();
                if (section.collapsible
                        && isCollapsed(collapseKey(section.label))) {
                    continue;
                }
            }
            for (int index = 0; index < section.entries.size(); index++) {
                layout.cells.add(new Cell(section.entries.get(index),
                        layout.left + (stretchesCells() ? 0 : PADDING_X)
                                + (index % columns) * layout.cellWidth,
                        cursorY + (index / columns) * cellHeight()));
            }
            cursorY += rowsOf(section, columns) * cellHeight();
        }
        return layout;
    }

    /**
     * Rows a section occupies. A labelled section with nothing in it is
     * its label alone (an empty-state line); an unlabelled empty result
     * list still keeps one blank row so the window has a body.
     */
    private static int rowsOf(Section section, int columns) {
        if (section.entries.isEmpty()) {
            return section.label == null ? 1 : 0;
        }
        return (section.entries.size() + columns - 1) / columns;
    }

    /** Half the real screen: the tallest a picker first opens. */
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

    /** Cells per row when the window first opens; one for a list. */
    abstract int naturalColumns();

    /** Whether a cell is a row as wide as the window, as a list's are. */
    boolean stretchesCells() {
        return false;
    }

    /** A cell's width; a list's rows stretch past it with the window. */
    abstract int cellWidth();

    abstract int cellHeight();

    /** Sections for the lowercased query; empty query lists everything. */
    abstract List<Section> buildSections(String query);

    /** Draws one cell {@code width} wide at {@code x}, {@code y}. */
    abstract void drawEntry(Minecraft minecraft, Entry entry, int x, int y,
                            int width, int alpha, boolean hovered);

    /** Hover label for a cell, or null for none. */
    abstract String tooltip(Entry entry);

    /** The text a chosen cell inserts at the input cursor. */
    abstract String insertionText(Entry entry);

    /** The button's glyph, a cell of the chat's sheet. */
    abstract LostTalesUiSheet buttonGlyph();

    /** The same glyph lit, which the button crosses to under the pointer. */
    abstract LostTalesUiSheet buttonGlyphLit();

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
        int width;
        int height;
        int cellWidth;
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
