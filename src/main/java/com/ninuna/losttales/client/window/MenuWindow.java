package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.input.LostTalesInputBinding;
import com.ninuna.losttales.client.input.LostTalesInputIconRenderer;
import com.ninuna.losttales.client.input.LostTalesKeyPress;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;

/**
 * A menu in a sub-window of its own: the rows a control or the pointer
 * opens — a tab's menu behind the tool strip's cog, what can be opened
 * behind the {@code +}, Settings, a message's or a person's actions.
 * Each kind of menu has one window. The menu lays its rows out in the
 * window's content box, draws them, hit tests them and scrolls them;
 * what the rows are, what they are about and what they do is its
 * owner's business ({@link WindowMenus}), which hands the menu new rows
 * whenever what they say has changed. A list may carry <em>header</em>
 * rows — a section label over a hairline, never taken — and a list longer
 * than its window scrolls by the wheel, a honey hairline on an edge
 * saying more lies past it.
 *
 * <p>A menu may hold a field above its rows, which takes what is typed
 * while its window is in front and until a row is taken: a search that
 * narrows the rows, or a note or a line to be kept. The menu holds the
 * text and draws the field ({@link WindowFields}); which rows a filter
 * leaves is its owner's business.</p>
 */
public final class MenuWindow extends SubWindowContent {
    public static final int ROW_HEIGHT = 11;
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 3;
    private static final int MIN_WIDTH = 56;
    /**
     * A row tall enough for the mod's key icons at their own size, as the
     * settings' rows are, since some of them show a shortcut's keys.
     */
    public static final int TALL_ROW_HEIGHT =
            LostTalesInputIconRenderer.BASE_ICON_HEIGHT + 2;
    /** Rows a window opens with at most; a longer list scrolls behind them. */
    public static final int MAX_VISIBLE_ROWS = 12;
    /** Longest thing a field takes unless told otherwise; far past any tab's name. */
    public static final int MAX_FILTER_LENGTH = 48;
    /** Seam between a shortcut's key icons and the + joining them. */
    private static final int KEY_GAP = 2;
    private static final int[] NO_KEYS = new int[0];
    private static final Object[] NO_PARTS = new Object[0];
    /** Clear room between a row's label and its value at the least. */
    private static final int VALUE_GAP = 8;
    /** Width of the upright colour bar before a channel's name, and its gap. */
    private static final int SWATCH_WIDTH = 1;
    private static final int SWATCH_GAP = 4;
    /** Width of the colour chip before a palette entry's name. */
    private static final int CHIP_WIDTH = 7;
    /** The hairline that says the list continues past an edge. */
    private static final int MORE_RGB =
            LostTalesColors.rgb(LostTalesColors.HONEY);
    /** The hairline under a section's name: nearly opaque, a firmer line than the field's. */
    private static final int HEADER_RULE_ALPHA = 0xE0;

    /**
     * A picture standing in a row's icon column: a person's head. It is
     * drawn from the column's left and the label's capitals' top.
     */
    public interface Picture {
        void draw(Minecraft minecraft, float iconX, float labelTop,
                  int alpha);
    }

    /** How a row's label is measured and drawn where it is not plain words: emoji shortcodes as their sprites. */
    public interface Label {
        int width(FontRenderer font, String text);

        void draw(Minecraft minecraft, FontRenderer font, String text,
                  String style, int x, int y, int rgb, int alpha);
    }

    /** Who takes a menu's rows and its keys: its {@link WindowMenus}. */
    public interface Owner {
        /** A row taken; with {@code back}, pressed with the right button. */
        void take(MenuWindow menu, Entry entry, boolean back);

        /** A key for the menu's field while it holds the keys. */
        void keyTyped(MenuWindow menu, LostTalesKeyPress press);
    }

    public static final class Entry {
        public final String id;
        public final String label;
        /** A section label over a hairline; never hovered, never taken. */
        public final boolean header;
        /** A display row that cannot be taken. */
        public final boolean passive;
        /** Drawn italic: a muted channel, like its tab. */
        public final boolean dim;
        /** The channel's colour, shown as a small bar before the name; -1 for none. */
        public final int color;
        /**
         * Whether the colour is the row's subject rather than its
         * channel's mark: a palette entry shows it as a chip wide
         * enough to read as a colour, not as a hairline.
         */
        boolean chip;
        /** The tab whose icon stands before the name, or null for none. */
        public final WindowTab icon;
        /** A picture before the name, a person's head; null for none. */
        Picture picture;
        /** A sheet sprite before the name, or null. */
        LostTalesUiSheet sprite;
        /**
         * The sprite's lit artwork, or null for none: the sprite crosses
         * to it while the pointer is on the row, and rests on it while
         * the row is {@link #chosen}.
         */
        LostTalesUiSheet litSprite;
        /** Whether the row is the one chosen, which keeps its sprite lit. */
        boolean chosen;
        /** How the label is drawn where it is not plain words; null for plain words. */
        Label labelStyle;
        /**
         * The colour the label is drawn in; -1 for the menu's ivory. An
         * operator's action wears the Operator channel's crimson, so a
         * row that reaches beyond this player's own words is told apart
         * at a glance.
         */
        public int labelColor = -1;
        /**
         * Why the row's action cannot be taken here, or empty for a row
         * that can: such a row stands in its place, muted, answers no
         * click, and says why under the pointer, so every menu of its kind
         * keeps the same rows.
         */
        public String unavailable = "";
        /** What the row's setting reads now, at its right end; empty for none. */
        public String value = "";
        /** A colour chip before the value, a colour setting's; -1 for none. */
        int valueChip = -1;
        /**
         * A shortcut's keys at the row's right end: a key code for each
         * key, drawn in the mod's key icons, and words between and after
         * them; empty for none.
         */
        public Object[] keys = NO_PARTS;
        /**
         * A group's name over the rows after it — a channel's, its icon
         * before it, or a part of a system's — in its own colour, at the
         * padding as a header is; never taken.
         */
        public boolean group;

        public Entry(String id, String label) {
            this(id, label, false, false, false, -1, null);
        }

        /** The same entry showing what its setting reads now. */
        public Entry withValue(String value) {
            this.value = value == null ? "" : value;
            return this;
        }

        /** The same entry with a colour chip before its value. */
        public Entry withValueChip(int rgb) {
            this.valueChip = rgb;
            return this;
        }

        /** The same entry showing a shortcut's keys; see {@link #keys}. */
        public Entry withKeys(Object... keys) {
            this.keys = keys == null ? NO_PARTS : keys;
            return this;
        }

        /** The same entry, muted and closed for {@code reason}. */
        public Entry unavailable(String reason) {
            this.unavailable = reason == null ? "" : reason;
            return this;
        }

        /** The same entry with its label in the given colour. */
        public Entry withLabelColor(int color) {
            this.labelColor = color;
            return this;
        }

        /** The same entry showing its colour as a chip; a palette row. */
        public Entry asChip() {
            this.chip = true;
            return this;
        }

        /** The same entry drawing its label in {@code style}: a status line's, its emoji as sprites. */
        public Entry withLabel(Label style) {
            this.labelStyle = style;
            return this;
        }

        public Entry(String id, String label, boolean dim, int color,
                     WindowTab icon) {
            this(id, label, false, false, dim, color, icon);
        }

        private Entry(String id, String label, boolean header,
                      boolean passive, boolean dim, int color, WindowTab icon) {
            this.id = id;
            this.label = label == null ? "" : label;
            this.header = header;
            this.passive = passive;
            this.dim = dim;
            this.color = color;
            this.icon = icon;
        }

        /** A section label: {@code Channels}, {@code Direct Messages}. */
        public static Entry header(String label) {
            return new Entry("", label, true, false, false, -1, null);
        }

        /** A display row that cannot be taken. */
        public static Entry passive(String label) {
            return new Entry("", label, false, true, false, -1, null);
        }

        /** A group's name in {@code rgb}, a channel's {@code icon} before it or none. */
        public static Entry group(String label, WindowTab icon, int rgb) {
            Entry entry = new Entry("", label, false, true, false, -1, icon);
            entry.group = true;
            entry.labelColor = rgb;
            return entry;
        }

        /** The same entry with a picture before its name. */
        public Entry withPicture(Picture drawn) {
            this.picture = drawn;
            return this;
        }

        public Entry withSprite(LostTalesUiSheet sprite) {
            this.sprite = sprite;
            return this;
        }

        /**
         * The same entry with a sprite that lights: under the pointer,
         * and for as long as the row is {@code chosen}.
         */
        public Entry withSprite(LostTalesUiSheet sprite,
                                LostTalesUiSheet litSprite, boolean chosen) {
            this.sprite = sprite;
            this.litSprite = litSprite;
            this.chosen = chosen;
            return this;
        }

        /** The same row under another id, everything else as it is: a page's row in the quick switcher. */
        Entry renamed(String newId) {
            Entry copy = new Entry(newId, this.label, this.header,
                    this.passive, this.dim, this.color, this.icon);
            copy.chip = this.chip;
            copy.picture = this.picture;
            copy.sprite = this.sprite;
            copy.litSprite = this.litSprite;
            copy.chosen = this.chosen;
            copy.labelStyle = this.labelStyle;
            copy.labelColor = this.labelColor;
            copy.unavailable = this.unavailable;
            copy.value = this.value;
            copy.valueChip = this.valueChip;
            copy.keys = this.keys;
            copy.group = this.group;
            return copy;
        }

        /** Whether a press on the row does something. */
        public boolean isTakeable() {
            return !this.header && !this.passive
                    && this.unavailable.length() == 0;
        }
    }


    /** Where the field and the rows stand in a content box, in whole pixels. */
    private static final class Layout {
        final int left;
        final int top;
        final int width;
        final int height;
        final int rowsTop;
        final int rowsBottom;
        final int rowHeight;

        Layout(LostTalesUiHitBox box, int fieldHeight, int rowHeight) {
            this.left = (int)Math.floor(box.left);
            this.top = (int)Math.floor(box.top);
            this.width = (int)Math.floor(box.width);
            this.height = (int)Math.floor(box.height);
            this.rowsTop = this.top + PADDING_Y + fieldHeight;
            this.rowsBottom = Math.max(this.rowsTop,
                    this.top + this.height - PADDING_Y);
            this.rowHeight = rowHeight;
        }

        /** Rows the box shows, the last of them perhaps in part. */
        double shownRows() {
            return (this.rowsBottom - this.rowsTop) / (double)this.rowHeight;
        }
    }

    public final SubWindowKind kind;
    /** The menus of the screen it stands on; a menu that comes back with a new screen takes that screen's. */
    private Owner owner;
    /**
     * What the menu is about, its owner's own: the message, the person,
     * the tab or the window it was opened for; null for a menu about
     * nothing in particular.
     */
    private Object about;
    private String title = "";
    private LostTalesUiSheet icon;
    private List<Entry> entries = Collections.emptyList();
    /** How tall a row is: a menu's own, or {@link #TALL_ROW_HEIGHT}. */
    private int rowHeight = ROW_HEIGHT;
    /** Width of the colour column: a bar, or a chip when a row is a colour. */
    private int swatchWidth = SWATCH_WIDTH;
    /** Left edge of the labels inside the box, past any swatch column. */
    private int labelX = PADDING_X;
    /** First row asked for — the wheel's target; rows above it lie past the top edge. */
    private double scrollRows;
    /**
     * The row offset the list is drawn at, easing toward
     * {@link #scrollRows} with the windows' shared scroll motion so a
     * wheel turn glides the rows instead of jumping them. Hit testing
     * reads this too, so it always answers for what is on screen.
     */
    private double renderedScrollRows;
    private long scrollNanos;
    /**
     * The field above the rows — the windows' one text field, caret,
     * selection and clipboard and all — or null for a menu without one.
     * It holds the keys from its window coming in front until a row is
     * taken.
     */
    private GuiTextField field;
    /** What the empty field reads while nothing has been typed. */
    private String filterPrompt = "";
    /** The icon the field opens with: the magnifier for a search. */
    private LostTalesUiSheet fieldIcon = LostTalesUiSheet.SEARCH;
    /** The keys of the shortcut shown beside it, or empty for none. */
    private int[] filterHint = NO_KEYS;
    /** The list the field opens as it is typed in — a status line's emoji list — or null. */
    private WindowFields.FieldList fieldList;
    /** Where the field's row stood as it was last drawn: its list hangs above it. */
    private int fieldRowLeft;
    private int fieldRowTop;
    /**
     * How far each lighting row has crossed to its lit look, by the row's
     * id — a sprite to its lit artwork, a tab's name to the tab's colour
     * — so a list handed over again carries on from what is on screen;
     * and when the crossfades last stepped.
     */
    private final Map<String, Float> spriteFades = new HashMap<String, Float>();
    private final Map<String, Float> labelFades = new HashMap<String, Float>();
    private long spriteNanos;

    MenuWindow(SubWindowKind kind, Owner owner) {
        this.kind = kind;
        this.owner = owner;
    }

    /* ---- What its owner hands it ---- */

    /** What the menu is about; null for nothing in particular. */
    public Object about() {
        return this.about;
    }

    void setAbout(Object subject) {
        this.about = subject;
    }

    void setOwner(Owner menus) {
        this.owner = menus;
    }

    /** The name and the glyph on the window's strip. */
    public void setTitle(String title, LostTalesUiSheet icon) {
        this.title = title == null ? "" : title;
        this.icon = icon;
    }

    /** How tall every row is: a menu's own, or taller to hold key icons. */
    public void setRowHeight(int height) {
        this.rowHeight = Math.max(ROW_HEIGHT, height);
    }

    /**
     * Hands the menu its rows in place of the ones it had, the scroll and
     * each row's light carried on, so a list handed over again as it is
     * typed into, or as what it says changes, does not jump.
     */
    public void setRows(List<Entry> rows) {
        this.entries = rows == null ? Collections.<Entry>emptyList()
                : new ArrayList<Entry>(rows);
        boolean swatches = false;
        boolean chips = false;
        boolean icons = false;
        for (Entry entry : this.entries) {
            if (entry.group) {
                continue;
            }
            swatches |= entry.color >= 0;
            chips |= entry.color >= 0 && entry.chip;
            icons |= entry.icon != null || entry.picture != null
                    || entry.sprite != null;
        }
        // One swatch column and one icon column for the whole list, so
        // the names line up; headers and groups hang left of them with
        // the padding.
        this.swatchWidth = chips ? CHIP_WIDTH : SWATCH_WIDTH;
        this.labelX = PADDING_X + (swatches ? this.swatchWidth + SWATCH_GAP : 0)
                + (icons ? TabIcons.SLOT + TabIcons.GAP : 0);
    }

    /** The rows, top first. */
    public List<Entry> entries() {
        return Collections.unmodifiableList(this.entries);
    }

    /** Starts the list over at its top with no row lit: the menu opened, or turned to something else. */
    void restart() {
        this.scrollRows = 0.0D;
        this.renderedScrollRows = 0.0D;
        this.spriteFades.clear();
        this.labelFades.clear();
        this.spriteNanos = 0L;
    }

    /**
     * Gives the menu a field above its rows with nothing typed in it: it
     * reads {@code prompt} while it is empty, opens with {@code icon},
     * shows {@code hint} — the keys of the shortcut that opens the menu,
     * drawn as the mod's own key icons — at its right end while it is
     * empty, and holds {@code limit} characters. A field that
     * {@code showsEmoji} draws a complete shortcode as its sprite, as a
     * status line is drawn once set; any other shows what is typed as it
     * is.
     */
    public void openField(String prompt, int[] hint, LostTalesUiSheet icon,
                          int limit, boolean showsEmoji) {
        this.field = WindowFields.make(LostTalesUiCaret.HEIGHT,
                Math.max(1, limit), showsEmoji);
        this.field.setFocused(false);
        this.fieldList = WindowFields.listFor(this.field, showsEmoji);
        this.filterPrompt = prompt == null ? "" : prompt;
        this.filterHint = hint == null ? NO_KEYS : hint;
        this.fieldIcon = icon == null ? LostTalesUiSheet.SEARCH : icon;
    }

    /** Takes the field away: the rows alone. */
    public void closeField() {
        this.field = null;
        this.fieldList = null;
        this.filterPrompt = "";
        this.filterHint = NO_KEYS;
    }

    public boolean hasField() {
        return this.field != null;
    }

    /** What has been typed into the field; empty when nothing has, or there is none. */
    public String filter() {
        return this.field == null ? "" : this.field.getText();
    }

    /**
     * Puts {@code text} in the field, as far as it holds, the caret after
     * it: what a field for editing something opens with.
     */
    public void setFilter(String text) {
        if (this.field == null) {
            return;
        }
        this.field.setText(text == null ? "" : text);
        this.field.setCursorPositionEnd();
    }

    /**
     * Offers a press to the field, as the input bar's field takes one —
     * typing, the caret's keys, selecting, the clipboard — and answers
     * whether it changed what the field holds.
     */
    boolean edit(LostTalesKeyPress press) {
        if (this.field == null) {
            return false;
        }
        String before = this.field.getText();
        this.field.textboxKeyTyped(press.character, press.key);
        refreshList();
        return !before.equals(this.field.getText());
    }

    /** Brings the field's list up to date with the field. */
    private void refreshList() {
        if (this.fieldList != null && this.field != null) {
            this.fieldList.update(this.field);
        }
    }

    /**
     * Offers a press to the field's list while it is out: Up and Down
     * walk it, Tab and Enter take the row chosen. Answers whether the
     * list took the press.
     */
    boolean serveList(LostTalesKeyPress press) {
        refreshList();
        return this.fieldList != null && this.fieldList.isActive()
                && this.fieldList.serve(this.field, press);
    }

    @Override
    public boolean dismissPopup() {
        refreshList();
        if (this.fieldList == null || !this.fieldList.isActive()) {
            return false;
        }
        this.fieldList.dismiss();
        return true;
    }

    /** The field's list, over everything, a clear pixel above the field's row. */
    @Override
    public void drawPopups(Minecraft minecraft, PointerRegions regions,
                    double pointerX, double pointerY) {
        refreshList();
        if (this.fieldList != null && this.fieldList.isActive()) {
            this.fieldList.draw(minecraft, regions, this.fieldRowLeft,
                    this.fieldRowTop, pointerX, pointerY);
        }
    }

    @Override
    public WindowHover popupHoverAt(double x, double y) {
        if (this.fieldList == null || !this.fieldList.isActive()) {
            return null;
        }
        int row = this.fieldList.rowAt(x, y, this.fieldRowLeft,
                this.fieldRowTop);
        if (row < -1) {
            return null;
        }
        WindowHover hover = new WindowHover(WindowHover.Kind.SUB_WINDOW);
        hover.listRow = row;
        hover.acts = row >= 0;
        return hover;
    }

    /**
     * A press on the field puts the caret where it landed; answers
     * whether the press was on it.
     */
    private boolean pressField(double x, double y) {
        if (this.field == null || !this.field.isFocused()
                || !LostTalesUiHitBox.contains(x, y, this.field.xPosition,
                        this.field.yPosition - 2, this.field.width,
                        WindowStyle.LINE_HEIGHT)) {
            return false;
        }
        this.field.mouseClicked((int)Math.floor(x), this.field.yPosition, 0);
        return true;
    }

    /**
     * A row taken, a row of the field's list taken into the field, or the
     * field given the caret where the press landed; a right-click takes a
     * row back where the owner steps settings back.
     */
    @Override
    public boolean pressed(WindowHover hover, double x, double y,
                           int button) {
        if (hover.listRow >= 0) {
            if (button == 0 && this.fieldList != null) {
                this.fieldList.take(this.field, hover.listRow);
                refreshList();
            }
            return true;
        }
        if (hover.menuEntry != null) {
            if (button == 0 || button == 1) {
                this.owner.take(this, hover.menuEntry, button == 1);
            }
            return true;
        }
        if (button == 0) {
            pressField(x, y);
        }
        return true;
    }

    /** A key for the field while it holds the keys: its owner's. */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        this.owner.keyTyped(this, LostTalesKeyPress.read(typedChar, keyCode));
        return true;
    }

    /* ---- The window ---- */

    /** The name the menu was given, else its kind's. */
    @Override
    public String stripTitle() {
        return this.title.length() == 0 ? null : this.title;
    }

    @Override
    public LostTalesUiSheet stripIcon() {
        return this.icon;
    }

    /**
     * Wide enough for every row whole — its label beside its swatch and
     * icon, and its value — the field's prompt and shortcut, and the
     * strip's name.
     */
    @Override
    public int naturalWidth() {
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft.fontRenderer;
        int widest = MIN_WIDTH;
        for (Entry entry : this.entries) {
            widest = Math.max(widest, rowWidth(minecraft, font, entry));
        }
        if (this.field != null) {
            // The field's prompt and the shortcut beside it are content
            // too: a list narrower than they are would cut them off.
            widest = Math.max(widest, PADDING_X + fieldIconRun()
                    + LostTalesUiCaret.WIDTH + 1
                    + font.getStringWidth(this.filterPrompt)
                    + SWATCH_GAP + hintWidth(minecraft) + PADDING_X);
        }
        String name = stripTitle();
        return Math.max(widest, TabRow.loneTabWidth(font,
                name != null ? name : this.kind.title(), this.icon != null));
    }

    /** The width a row takes whole: from the window's edge to its value's end. */
    private int rowWidth(Minecraft minecraft, FontRenderer font, Entry entry) {
        int label = entry.labelStyle != null
                ? entry.labelStyle.width(font, entry.label)
                : font.getStringWidth(entry.label);
        if (entry.header) {
            return PADDING_X + label + PADDING_X;
        }
        if (entry.group) {
            return PADDING_X + (entry.icon != null
                    ? TabIcons.SLOT + TabIcons.GAP : 0)
                    + label + PADDING_X;
        }
        int value = valueWidth(minecraft, font, entry);
        return this.labelX + label + (value > 0 ? VALUE_GAP + value : 0)
                + PADDING_X;
    }

    /** Every row up to {@link #MAX_VISIBLE_ROWS}, the field above them. */
    @Override
    public int naturalHeight(int width) {
        return PADDING_Y * 2 + fieldHeight() + this.rowHeight * Math.max(1,
                Math.min(this.entries.size(), MAX_VISIBLE_ROWS));
    }

    @Override
    public int minWidth() {
        return MIN_WIDTH;
    }

    /** One row under the field. */
    @Override
    public int minHeight() {
        return PADDING_Y * 2 + fieldHeight() + this.rowHeight;
    }

    /**
     * The content box the menu's window first opens round in
     * {@code room}, hung from {@code anchor}: toward the middle of the
     * window the control belongs to, as many rows as the room on that
     * side holds up to {@link #MAX_VISIBLE_ROWS}, and on the other side
     * only where that holds more of them.
     */
    public LostTalesUiHitBox firstContentBox(SubWindowAnchor anchor,
                                             LostTalesUiHitBox room) {
        return firstContentBox(anchor, naturalWidth(), room);
    }

    /** As above for a window {@code width} wide. */
    public LostTalesUiHitBox firstContentBox(SubWindowAnchor anchor, int width,
                                             LostTalesUiHitBox room) {
        int roomLeft = (int)Math.ceil(room.left);
        int roomTop = (int)Math.ceil(room.top);
        int roomRight = (int)Math.floor(room.left + room.width);
        int roomBottom = (int)Math.floor(room.top + room.height);
        int frame = SubWindow.STRIP_HEIGHT + PADDING_Y * 2
                + fieldHeight();
        int wanted = Math.max(1, Math.min(this.entries.size(),
                MAX_VISIBLE_ROWS));
        int roomBelow = rowsIn(roomBottom - anchor.bottom - SubWindowAnchor.REACH
                - frame);
        int roomAbove = rowsIn(anchor.top - SubWindowAnchor.REACH - roomTop - frame);
        boolean below = anchor.below
                ? roomBelow >= wanted || roomBelow >= roomAbove
                : !(roomAbove >= wanted || roomAbove >= roomBelow);
        int rows = Math.max(1, Math.min(wanted,
                below ? roomBelow : roomAbove));
        int height = frame + rows * this.rowHeight;
        int left = Math.max(roomLeft, Math.min(roomRight - width,
                anchor.fromRight ? anchor.right - width : anchor.left));
        int top = Math.max(roomTop, Math.min(roomBottom - height,
                below ? anchor.bottom + SubWindowAnchor.REACH
                        : anchor.top - SubWindowAnchor.REACH - height));
        return new LostTalesUiHitBox(left,
                top + SubWindow.STRIP_HEIGHT, width,
                height - SubWindow.STRIP_HEIGHT);
    }

    /**
     * The content box the menu's window first opens round in
     * {@code room} beside {@code sibling}, the sub-window whose row
     * opened it: to its right with the gap windows keep, or to its left
     * where the right has no room, its top on the other's, as many rows
     * as the room below holds up to {@link #MAX_VISIBLE_ROWS}.
     */
    public LostTalesUiHitBox firstContentBoxBeside(LostTalesUiHitBox sibling,
                                            LostTalesUiHitBox room) {
        int width = naturalWidth();
        double roomRight = room.left + room.width;
        double roomBottom = room.top + room.height;
        int gap = WindowPlacement.WINDOW_GAP;
        int frame = SubWindow.STRIP_HEIGHT + PADDING_Y * 2
                + fieldHeight();
        double right = sibling.left + sibling.width + gap;
        double left = right + width <= roomRight ? right
                : sibling.left - gap - width;
        left = Math.max(room.left, Math.min(roomRight - width, left));
        double top = Math.max(room.top, sibling.top);
        int wanted = Math.max(1, Math.min(this.entries.size(),
                MAX_VISIBLE_ROWS));
        int rows = Math.max(1, Math.min(wanted,
                rowsIn((int)Math.floor(roomBottom - top) - frame)));
        int height = frame + rows * this.rowHeight;
        top = Math.max(room.top, Math.min(roomBottom - height, top));
        return new LostTalesUiHitBox(left,
                top + SubWindow.STRIP_HEIGHT, width,
                height - SubWindow.STRIP_HEIGHT);
    }

    /**
     * The content box the menu's window first opens round in the middle
     * of {@code room}, as many rows as the room holds up to
     * {@link #MAX_VISIBLE_ROWS}: what a keyboard shortcut opens where
     * no control stands to hang it from.
     */
    public LostTalesUiHitBox firstContentBoxCentred(LostTalesUiHitBox room) {
        int width = naturalWidth();
        int frame = SubWindow.STRIP_HEIGHT + PADDING_Y * 2
                + fieldHeight();
        int wanted = Math.max(1, Math.min(this.entries.size(),
                MAX_VISIBLE_ROWS));
        int rows = Math.max(1, Math.min(wanted,
                rowsIn((int)Math.floor(room.height) - frame)));
        int height = frame + rows * this.rowHeight;
        double left = room.left + Math.max(0, LostTalesUiInk.centredStart(
                (int)Math.floor(room.width), width));
        double top = room.top + Math.max(0, LostTalesUiInk.centredStart(
                (int)Math.floor(room.height), height));
        return new LostTalesUiHitBox(left,
                top + SubWindow.STRIP_HEIGHT, width,
                height - SubWindow.STRIP_HEIGHT);
    }

    /** Whole rows in {@code pixels}. */
    private int rowsIn(int pixels) {
        return Math.max(0, pixels / this.rowHeight);
    }

    /**
     * Room the field takes above the rows; none without one. As tall as
     * the key icons it carries, so they are drawn at their own size
     * rather than shrunk into a text row.
     */
    private int fieldHeight() {
        return this.field == null ? 0
                : Math.max(ROW_HEIGHT,
                        LostTalesInputIconRenderer.BASE_ICON_HEIGHT) + 1;
    }

    /** The field's icon and the gap after it. */
    private int fieldIconRun() {
        return this.fieldIcon.getWidth() + TabIcons.GAP;
    }

    /**
     * A row the menu takes, or the menu's bare content, which says why a
     * row it keeps in its place cannot be taken.
     */
    @Override
    public WindowHover hoverAt(LostTalesUiHitBox box, double x, double y) {
        Layout at = layOut(box);
        clampScroll(at);
        int index = rowIndexAt(at, x, y);
        Entry row = index < 0 ? null : this.entries.get(index);
        boolean takes = row != null && row.isTakeable();
        WindowHover hover = new WindowHover(WindowHover.Kind.SUB_WINDOW);
        hover.menuEntry = takes ? row : null;
        hover.acts = takes;
        hover.tip = row == null ? "" : row.unavailable;
        return hover;
    }

    /**
     * Moves the list's target by whole rows; beyond either end it stays
     * put. The drawn rows glide after the target.
     */
    @Override
    public void scrollBy(int lines) {
        this.scrollRows += WheelStep.menuRows(lines);
    }

    @Override
    public boolean holdsKeys() {
        return this.field != null && this.field.isFocused();
    }

    /** The field takes the keys as its window comes in front, the caret lit at once. */
    @Override
    public void takeKeys() {
        if (this.field != null) {
            this.field.setFocused(true);
        }
    }

    @Override
    public void releaseKeys() {
        if (this.field != null) {
            this.field.setFocused(false);
        }
    }

    @Override
    public void closed() {
        releaseKeys();
    }

    /** The menu itself comes back with the screen: what it is about, its field and its place in the list. */
    @Override
    public Object sessionState() {
        return this;
    }

    /* ---- Drawing ---- */

    /**
     * The rows under the field, the hovered one lit in the window's
     * surface, clipped to the band they glide in.
     */
    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
              double clipY, double pointerX, double pointerY, int alpha,
              int surfaceAlpha) {
        FontRenderer font = minecraft.fontRenderer;
        Layout at = layOut(box);
        clampScroll(at);
        advanceScrollEasing();
        long now = System.nanoTime();
        double elapsed = this.spriteNanos == 0L ? 0.0D
                : (now - this.spriteNanos) / 1.0E9D;
        this.spriteNanos = now;
        int hoveredIndex = rowIndexAt(at, pointerX, pointerY);
        if (hoveredIndex >= 0
                && !this.entries.get(hoveredIndex).isTakeable()) {
            hoveredIndex = -1;
        }
        // Rows are laid out from the drawn offset — whole rows pick where
        // the list starts, the fraction slides it — and clipped to the
        // band they glide in; one extra row fills the gap the slide opens.
        int firstRow = (int)Math.floor(this.renderedScrollRows);
        int firstY = firstRowY(at, firstRow);
        // The hovered row is the window's surface recoloured, cut to the
        // band, before anything lands on it.
        if (hoveredIndex >= 0) {
            int litTop = firstY + (hoveredIndex - firstRow) * this.rowHeight;
            WindowStyle.recolourFlat(at.left,
                    Math.max(at.rowsTop, litTop), at.left + at.width,
                    Math.min(at.rowsBottom, litTop + this.rowHeight),
                    surfaceAlpha, LostTalesUiInk.SURFACE_RGB,
                    LostTalesUiInk.SURFACE_HIGHLIGHT_RGB);
        }
        drawField(minecraft, font, at, alpha);
        int last = Math.min(this.entries.size(),
                firstRow + (int)Math.ceil(at.shownRows()) + 1);
        boolean clipped = beginClip(minecraft, clipX + (at.left - box.left),
                clipY + (at.rowsTop - box.top), at.width,
                at.rowsBottom - at.rowsTop);
        try {
            int rowY = firstY;
            for (int index = Math.max(0, firstRow); index < last; index++) {
                drawRow(minecraft, font, at, this.entries.get(index), rowY,
                        index == hoveredIndex, elapsed, alpha);
                rowY += this.rowHeight;
            }
        } finally {
            endClip(clipped);
        }
        // A hairline on an edge the list continues past.
        int more = LostTalesUiInk.argb(MORE_RGB, alpha);
        if (this.scrollRows > 0.0D) {
            Gui.drawRect(at.left + 1, at.top + 1, at.left + at.width - 1,
                    at.top + 2, more);
        }
        if (this.scrollRows < maxScroll(at) - 0.01D) {
            Gui.drawRect(at.left + 1, at.top + at.height - 2,
                    at.left + at.width - 1, at.top + at.height - 1, more);
        }
    }

    /**
     * One row at {@code rowY}, its words on the row's middle. Text at the
     * window's opacity always; a muted channel is italic, the hovered row
     * is told by its light, and a row naming a tab takes the tab's colour
     * as it lights, as the tab's own name does. A row's value stands at
     * its right end, and its label gives way before it.
     */
    private void drawRow(Minecraft minecraft, FontRenderer font, Layout at,
                         Entry entry, int rowY, boolean hovered,
                         double elapsed, int alpha) {
        int labelTop = rowY + LostTalesUiInk.centredStart(this.rowHeight,
                LostTalesUiInk.CAP_HEIGHT);
        if (entry.header) {
            // The section's name over a hairline, in the sand the
            // timestamps wear, so it reads as a label, not a row.
            LostTalesUiInk.drawText(font, trimmed(font,
                            entry.label, at.width - PADDING_X * 2),
                    at.left + PADDING_X, rowY + this.rowHeight - 10,
                    LostTalesColors.rgb(LostTalesColors.SAND), alpha);
            Gui.drawRect(at.left + PADDING_X, rowY + this.rowHeight - 1,
                    at.left + at.width - PADDING_X, rowY + this.rowHeight,
                    LostTalesUiInk.argb(
                            LostTalesUiInk.SURFACE_HIGHLIGHT_RGB,
                            Math.round(HEADER_RULE_ALPHA * alpha / 255.0F)));
            return;
        }
        if (entry.group) {
            drawGroup(minecraft, font, at, entry, labelTop, alpha);
            return;
        }
        if (entry.color >= 0) {
            // The channel's colour as a one-pixel upright bar the height
            // of the row's text; a palette row's as a chip the width of
            // the column.
            Gui.drawRect(at.left + PADDING_X, rowY + 1,
                    at.left + PADDING_X
                            + (entry.chip ? this.swatchWidth : SWATCH_WIDTH),
                    rowY + this.rowHeight - 1,
                    LostTalesUiInk.argb(entry.color, alpha));
        }
        drawRowIcon(minecraft, at, entry, labelTop, hovered, elapsed, alpha);
        int labelRgb = entry.unavailable.length() > 0
                ? WindowStyle.asideRgb()
                : entry.labelColor >= 0 ? entry.labelColor
                : LostTalesUiInk.IVORY;
        if (entry.icon != null) {
            labelRgb = LostTalesUiInk.blend(labelRgb,
                    entry.icon.tone(),
                    labelFade(entry, hovered, elapsed));
        }
        int labelLeft = at.left + this.labelX;
        int right = at.left + at.width - PADDING_X;
        int value = valueWidth(minecraft, font, entry);
        if (value > 0) {
            drawValue(minecraft, font, entry, right - value, rowY, labelTop,
                    alpha);
            right -= value + VALUE_GAP;
        }
        if (entry.labelStyle != null) {
            // A styled label cannot be cut by the letter; the clip cuts a
            // line longer than the window at its edge.
            entry.labelStyle.draw(minecraft, font, entry.label,
                    entry.dim ? "§o" : "", labelLeft, labelTop, labelRgb,
                    alpha);
        } else {
            String label = trimmed(font, entry.label, right - labelLeft);
            LostTalesUiInk.drawText(font,
                    entry.dim ? "§o" + label : label, labelLeft, labelTop,
                    labelRgb, alpha);
        }
    }

    /**
     * A group's name at the padding, as a header's is, a channel's icon
     * before it, in the group's own colour — sand for a part of a system.
     */
    private void drawGroup(Minecraft minecraft, FontRenderer font, Layout at,
                           Entry entry, int labelTop, int alpha) {
        int x = at.left + PADDING_X;
        if (entry.icon != null) {
            entry.icon.drawIcon(minecraft, x,
                    labelTop + TabIcons.CONTENT_TOP_OFFSET, alpha,
                    TabMark.of(entry.icon));
            x += TabIcons.SLOT + TabIcons.GAP;
        }
        LostTalesUiInk.drawText(font, trimmed(font, entry.label,
                        at.left + at.width - PADDING_X - x), x, labelTop,
                entry.labelColor >= 0 ? entry.labelColor
                        : LostTalesColors.rgb(LostTalesColors.SAND), alpha);
    }

    /** How wide a row's value is: its chip, its words and its keys. */
    private static int valueWidth(Minecraft minecraft, FontRenderer font,
                                  Entry entry) {
        int width = 0;
        if (entry.valueChip >= 0) {
            width += CHIP_WIDTH + (entry.value.length() > 0 ? SWATCH_GAP : 0);
        }
        width += font.getStringWidth(entry.value);
        for (Object part : entry.keys) {
            width += part instanceof Integer
                    ? LostTalesInputIconRenderer.measureInput(minecraft,
                            LostTalesInputBinding.Type.KEYBOARD,
                            ((Integer)part).intValue(), 1.0F)
                    : KEY_GAP + font.getStringWidth(String.valueOf(part))
                            + KEY_GAP;
        }
        return width;
    }

    /**
     * A row's value from {@code x}: a colour's chip, a square on the
     * words' capitals, then the words in the aside tone, then a
     * shortcut's keys in the mod's own key icons on the row's middle,
     * words between them a seam's width clear.
     */
    private void drawValue(Minecraft minecraft, FontRenderer font,
                           Entry entry, int x, int rowY, int labelTop,
                           int alpha) {
        int aside = WindowStyle.asideRgb();
        if (entry.valueChip >= 0) {
            Gui.drawRect(x, labelTop, x + CHIP_WIDTH,
                    labelTop + LostTalesUiInk.CAP_HEIGHT,
                    LostTalesUiInk.argb(entry.valueChip, alpha));
            x += CHIP_WIDTH + (entry.value.length() > 0 ? SWATCH_GAP : 0);
        }
        if (entry.value.length() > 0) {
            LostTalesUiInk.drawText(font, entry.value, x,
                    labelTop, aside, alpha);
            x += font.getStringWidth(entry.value);
        }
        int keyY = rowY + LostTalesUiInk.centredStart(this.rowHeight,
                LostTalesInputIconRenderer.BASE_ICON_HEIGHT);
        for (Object part : entry.keys) {
            if (part instanceof Integer) {
                LostTalesUiInk.beginContent();
                x += LostTalesInputIconRenderer.drawInput(minecraft,
                        LostTalesInputBinding.Type.KEYBOARD,
                        ((Integer)part).intValue(), x, keyY, 1.0F,
                        alpha / 255.0F);
            } else {
                String word = String.valueOf(part);
                x += KEY_GAP;
                LostTalesUiInk.drawText(font, word, x, labelTop,
                        aside, alpha);
                x += font.getStringWidth(word) + KEY_GAP;
            }
        }
    }

    /**
     * An icon or a picture beside the label as it stands in a message
     * row: centred on the label's capitals by the one rule, on whole
     * pixels, since both are pixel art. A row naming a tab wears the
     * tab's own icon, its unread mark in its corner, as the tab does.
     */
    private void drawRowIcon(Minecraft minecraft, Layout at, Entry entry,
                             int labelTop, boolean hovered, double elapsed,
                             int alpha) {
        int iconX = at.left + this.labelX - TabIcons.SLOT
                - TabIcons.GAP;
        if (entry.icon != null) {
            entry.icon.drawIcon(minecraft, iconX,
                    labelTop + TabIcons.CONTENT_TOP_OFFSET, alpha,
                    TabMark.of(entry.icon));
        } else if (entry.picture != null) {
            entry.picture.draw(minecraft, iconX, labelTop, alpha);
        } else if (entry.sprite != null) {
            // Centred in the icon column and on the label's capitals, the
            // odd pixel left and up.
            int spriteX = iconX + LostTalesUiInk.centredStart(
                    TabIcons.SIZE, entry.sprite.getWidth());
            int spriteY = labelTop + Math.floorDiv(
                    LostTalesUiInk.CAP_HEIGHT
                            - entry.sprite.getHeight(), 2);
            LostTalesUiInk.beginContent();
            if (entry.litSprite == null) {
                entry.sprite.drawWithShadow(spriteX, spriteY, alpha);
            } else {
                LostTalesUiSheet.drawPairWithShadow(entry.sprite,
                        entry.litSprite, spriteFade(entry, hovered, elapsed),
                        spriteX, spriteY, alpha);
            }
        }
    }

    /**
     * The field above the rows: the icon it opens with; what has been
     * typed, or while it is empty the prompt in the aside tone and
     * italics, as an input bar's hint is; the shortcut that opens the
     * menu at the right end while it is empty and there is room; and the
     * one caret blinking after the text while the field holds the keys.
     * A hairline under it parts it from the rows.
     */
    private void drawField(Minecraft minecraft, FontRenderer font, Layout at,
                           int alpha) {
        if (this.field == null) {
            return;
        }
        int top = at.top + PADDING_Y;
        int height = fieldHeight();
        int right = at.left + at.width - PADDING_X;
        this.fieldRowLeft = at.left + PADDING_X;
        this.fieldRowTop = top;
        int textY = top + LostTalesUiInk.centredStart(height - 1,
                LostTalesUiInk.CAP_HEIGHT);
        // The icon stands on the capitals of what is typed beside it, as
        // every icon in a message row does.
        LostTalesUiInk.beginContent();
        this.fieldIcon.drawWithShadow(at.left + PADDING_X,
                textY + WindowStyle.centredBoxTop(
                        this.fieldIcon.getHeight()), alpha);
        int textX = at.left + PADDING_X + fieldIconRun();
        boolean empty = this.field.getText().length() == 0;
        int hintWidth = empty ? hintWidth(minecraft) : 0;
        int promptX = textX + LostTalesUiCaret.WIDTH + 1;
        boolean hinted = hintWidth > 0
                && right - hintWidth - SWATCH_GAP > promptX;
        if (empty) {
            // A pixel clear of the caret waiting at the field's start.
            String prompt = trimmed(font, this.filterPrompt, right - promptX
                    - (hinted ? hintWidth + SWATCH_GAP : 0));
            LostTalesUiInk.drawText(font, "§o" + prompt,
                    promptX, textY, WindowStyle.asideRgb(), alpha);
        }
        if (hinted) {
            drawHint(minecraft, font, right - hintWidth, top, height, alpha);
        }
        // The field itself, the windows' own, at the window's fade: it
        // scrolls to its caret as a bar's does.
        this.field.xPosition = textX;
        this.field.yPosition = textY;
        this.field.width = Math.max(1, right - textX - LostTalesUiCaret.WIDTH);
        WindowFields.draw(this.field, alpha);
        Gui.drawRect(at.left + PADDING_X, top + height - 1, right,
                top + height, LostTalesUiInk.argb(
                        LostTalesUiInk.SURFACE_HIGHLIGHT_RGB,
                        Math.min(alpha, 0xA0)));
    }

    /**
     * The shortcut as the keys themselves, in the mod's own key icons,
     * which press and spring back as the keys are held, a {@code +}
     * between each pair.
     */
    private void drawHint(Minecraft minecraft, FontRenderer font, int keyX,
                          int top, int height, int alpha) {
        int keyY = top + (height - 1
                - LostTalesInputIconRenderer.BASE_ICON_HEIGHT) / 2;
        String joiner = keyJoiner();
        int joinerY = keyY + LostTalesUiInk.centredStart(
                LostTalesInputIconRenderer.BASE_ICON_HEIGHT,
                LostTalesUiInk.CAP_HEIGHT);
        int quiet = LostTalesColors.rgb(LostTalesColors.SAND);
        LostTalesUiInk.beginContent();
        for (int index = 0; index < this.filterHint.length; index++) {
            if (index > 0) {
                keyX += KEY_GAP;
                LostTalesUiInk.drawText(font, joiner, keyX,
                        joinerY, quiet, alpha);
                keyX += font.getStringWidth(joiner) + KEY_GAP;
                LostTalesUiInk.beginContent();
            }
            keyX += LostTalesInputIconRenderer.drawInput(minecraft,
                    LostTalesInputBinding.Type.KEYBOARD,
                    this.filterHint[index], keyX, keyY, 1.0F, alpha / 255.0F);
        }
    }

    /**
     * Room the shortcut takes: its key icons at their own size, with a
     * {@code +} between each pair of them.
     */
    private int hintWidth(Minecraft minecraft) {
        int width = 0;
        for (int index = 0; index < this.filterHint.length; index++) {
            if (index > 0) {
                width += KEY_GAP + minecraft.fontRenderer.getStringWidth(
                        keyJoiner()) + KEY_GAP;
            }
            width += LostTalesInputIconRenderer.measureInput(minecraft,
                    LostTalesInputBinding.Type.KEYBOARD,
                    this.filterHint[index], 1.0F);
        }
        return width;
    }

    /**
     * What stands between two keys of a shortcut, saying they are held
     * together rather than pressed in turn. The same glyph the quick
     * loot hints join their keys with, from the one place it is named.
     */
    private static String keyJoiner() {
        return StatCollector.translateToLocal("quickLootHud.losttales.plus");
    }

    /** {@code text} cut to {@code width}, whole when it fits. */
    private static String trimmed(FontRenderer font, String text, int width) {
        return font.getStringWidth(text) <= width ? text
                : LostTalesSkyrimUiStyle.trimToWidth(font, text,
                        Math.max(0, width));
    }

    /* ---- Scrolling and hit testing ---- */

    /** The furthest the list scrolls in {@code at}: its last row whole at the bottom. */
    private double maxScroll(Layout at) {
        return Math.max(0.0D, this.entries.size() - at.shownRows());
    }

    /** Keeps both the target and the drawn offset within the list as the box shows it. */
    private void clampScroll(Layout at) {
        double max = maxScroll(at);
        this.scrollRows = Math.max(0.0D, Math.min(max, this.scrollRows));
        this.renderedScrollRows = Math.max(0.0D,
                Math.min(max, this.renderedScrollRows));
    }

    /**
     * Advances the drawn offset toward its target, once per drawn frame;
     * with animations off it simply arrives.
     */
    private void advanceScrollEasing() {
        long now = System.nanoTime();
        double elapsed = (now - this.scrollNanos) / 1.0E9D;
        this.scrollNanos = now;
        if (Math.abs(this.scrollRows - this.renderedScrollRows) <= 0.01D) {
            this.renderedScrollRows = this.scrollRows;
            return;
        }
        this.renderedScrollRows = Motions.followTravel(MotionIds.CHAT_SCROLL,
                this.renderedScrollRows, this.scrollRows, elapsed);
    }

    /** The field and the rows in a content box, at this menu's row height. */
    private Layout layOut(LostTalesUiHitBox box) {
        return new Layout(box, fieldHeight(), this.rowHeight);
    }

    /** Where the row {@code firstRow} is drawn: the fraction of the drawn offset slides it up. */
    private int firstRowY(Layout at, int firstRow) {
        return at.rowsTop - (int)Math.round(
                (this.renderedScrollRows - firstRow) * this.rowHeight);
    }

    /**
     * The index of the row drawn under the point, whatever kind it is, or
     * -1 for none: resolved against the drawn offset, as the rows are
     * drawn, so a gliding list answers for what is on screen.
     */
    private int rowIndexAt(Layout at, double x, double y) {
        if (!(x >= at.left && x < at.left + at.width && y >= at.rowsTop
                && y < at.rowsBottom)) {
            return -1;
        }
        int firstRow = (int)Math.floor(this.renderedScrollRows);
        int index = firstRow + (int)Math.floor(
                (y - firstRowY(at, firstRow)) / (double)this.rowHeight);
        return index >= 0 && index < this.entries.size() ? index : -1;
    }

    /**
     * One step of a lighting sprite's crossfade, toward its lit artwork
     * while its row is hovered or chosen. A row drawn for the first time
     * starts where it is headed, so a menu opening on the chosen row
     * shows its sprite lit instead of lighting it up.
     */
    private float spriteFade(Entry entry, boolean hovered, double elapsed) {
        return stepFade(this.spriteFades, entry, hovered || entry.chosen,
                elapsed);
    }

    /**
     * One step of the crossfade a row naming a tab takes its name to the
     * tab's colour on, while the row is hovered: the tab's own name does
     * the same under the pointer.
     */
    private float labelFade(Entry entry, boolean hovered, double elapsed) {
        return stepFade(this.labelFades, entry, hovered, elapsed);
    }

    /** One step of a row's crossfade in {@code fades}, a new row starting where it is headed. */
    private static float stepFade(Map<String, Float> fades, Entry entry,
                                  boolean lit, double elapsed) {
        Float kept = fades.get(entry.id);
        float fade = kept == null ? (lit ? 1.0F : 0.0F)
                : WindowStyle.hoverFade(kept.floatValue(), lit,
                        elapsed);
        fades.put(entry.id, Float.valueOf(fade));
        return fade;
    }
}
