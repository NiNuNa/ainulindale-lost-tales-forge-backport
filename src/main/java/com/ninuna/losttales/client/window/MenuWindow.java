package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.input.LostTalesInputBinding;
import com.ninuna.losttales.client.input.LostTalesInputIconRenderer;
import com.ninuna.losttales.client.input.LostTalesKeyPress;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesDisplayPixels;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
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
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * A menu in a sub-window of its own: the rows a control or the pointer
 * opens — a tab's options behind the tool strip's three dots, what can
 * be opened behind the {@code +}, Settings, a page's help, a message's or
 * a person's actions.
 * Each kind of menu has one window. The menu lays its rows out in the
 * window's content box, draws them, hit tests them and scrolls them;
 * what the rows are, what they are about and what they do is its
 * owner's business ({@link WindowMenus}), which hands the menu new rows
 * whenever what they say has changed. A list may carry <em>header</em>
 * rows — a section label over a hairline, taken only by one that folds its
 * section away, as the pickers' sections fold — and
 * <em>separators</em>, a lone hairline between two groups of rows that
 * belong together; a list longer than its window scrolls by the wheel, a
 * honey hairline on an edge saying more lies past it.
 *
 * <p>A menu may hold a field above its rows, which takes what is typed
 * while its window is in front and until a row is taken: a search that
 * narrows the rows, or a note, a line or a number to be kept. The menu
 * holds the text and draws the field ({@link WindowFields}); which rows a
 * filter leaves, and what the field may hold, is its owner's business.</p>
 *
 * <p>A number's row is a stepper: its value between two chevrons, each
 * of which a press answers on its own, as it does on the value
 * ({@link #PART_LESS}, {@link #PART_VALUE}, {@link #PART_MORE}).</p>
 *
 * <p>The same rows stand in a page as well ({@link PageRows}): a menu of
 * no kind, drawn in the page's box on the page's surface.</p>
 */
public final class MenuWindow extends SubWindowContent {
    /** Every menu's row, a plain row of words that holds a key icon too. */
    public static final int ROW_HEIGHT = 11;
    /** Clear room round a menu's field and rows, across (an edge's gap) and down; every list keeps it. */
    public static final int PADDING_X = WindowStyle.EDGE_GAP;
    public static final int PADDING_Y = 3;
    private static final int MIN_WIDTH = 56;
    /** Rows a window opens with at most; a longer list scrolls behind them. */
    public static final int MAX_VISIBLE_ROWS = 12;
    /** A separator's room; see {@link WindowLists#SEPARATOR_HEIGHT}. */
    static final int SEPARATOR_HEIGHT = WindowLists.SEPARATOR_HEIGHT;
    /** The widest a note makes its menu; a wider menu's notes take its width. */
    private static final int NOTE_WIDTH = 220;
    /** A note's lines, from one line's top to the next's. */
    public static final int NOTE_LINE = 10;
    /** Clear room above a note's first line and under its last. */
    public static final int NOTE_PADDING = 2;
    /** Longest thing a field takes unless told otherwise; far past any tab's name. */
    public static final int MAX_FILTER_LENGTH = 48;
    /** Seam between a shortcut's key icons and the + joining them. */
    private static final int KEY_GAP = 2;
    private static final int[] NO_KEYS = new int[0];
    private static final Object[] NO_PARTS = new Object[0];
    /** Clear room between a row's label and its value at the least; every list keeps it. */
    public static final int VALUE_GAP = 8;
    /** Width of the upright colour bar before a channel's name, and its gap. */
    private static final int SWATCH_WIDTH = 1;
    private static final int SWATCH_GAP = 4;
    /** Width of the colour chip before a palette entry's name. */
    private static final int CHIP_WIDTH = 7;
    /** A stepper's parts, as a press on a number's row names them. */
    public static final String PART_LESS = "less";
    public static final String PART_VALUE = "value";
    public static final String PART_MORE = "more";
    /**
     * The cell a stepper's chevron answers in: its three pixels of ink
     * with three clear on either side, the clear space between the
     * chevron and the number included.
     */
    public static final int STEPPER_CELL = 9;
    private static final LostTalesUiSheet LESS = LostTalesUiSheet.TOGGLE_5;
    private static final LostTalesUiSheet LESS_LIT = LostTalesUiSheet.TOGGLE_5_HOVER;
    private static final LostTalesUiSheet MORE = LostTalesUiSheet.TOGGLE_1;
    private static final LostTalesUiSheet MORE_LIT = LostTalesUiSheet.TOGGLE_1_HOVER;
    private static final String[] NO_WORDS = new String[0];
    /** A folding header's sign while its section shows; a subsection stands in by its width. */
    private static final String OPEN_SIGN = "- ";

    /**
     * A picture standing in a row's icon column: a person's head. It is
     * drawn from the column's left and the label's capitals' top.
     */
    public interface Picture {
        void draw(Minecraft minecraft, float iconX, float labelTop,
                  int alpha);
    }

    /** What a menu's field may hold: a number's field takes only the characters a number can. */
    public interface FieldFilter {
        boolean accepts(String text);
    }

    /** How a row's label is measured and drawn where it is not plain words: emoji shortcodes as their sprites. */
    public interface Label {
        int width(FontRenderer font, String text);

        void draw(Minecraft minecraft, FontRenderer font, String text,
                  String style, int x, int y, int rgb, int alpha);
    }

    /** Who takes a menu's rows and its keys: its {@link WindowMenus}. */
    public interface Owner {
        /**
         * A row taken; with {@code back}, pressed with the right button.
         * On a stepper, {@code part} names the part pressed; null for the
         * row as a whole.
         */
        void take(MenuWindow menu, Entry entry, String part, boolean back);

        /** A key for the menu's field while it holds the keys. */
        void keyTyped(MenuWindow menu, LostTalesKeyPress press);
    }

    public static final class Entry {
        public final String id;
        public final String label;
        /** A section label over a hairline; never hovered, never taken unless it folds. */
        public final boolean header;
        /**
         * Whether the header folds its section away and back, a press at a
         * time, as the pickers' sections do: {@code -} before its name
         * while the section shows, {@code +} while it is folded.
         */
        boolean foldable;
        /** Whether a folding header's section is folded away. */
        boolean folded;
        /** How deep a header stands: a subsection's under its section's name, one sign in. */
        int depth;
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
        public final WindowPage icon;
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
        /**
         * Whether the row is the one chosen: it stays lit as the row under
         * the pointer is, its sprite with it.
         */
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
        /**
         * Whether the row stands muted with nothing said: a row a locked
         * window holds back, which the padlock explains by itself.
         */
        boolean held;
        /** What the row's setting reads now, at its right end; empty for none. */
        public String value = "";
        /** A colour chip before the value, a colour setting's; -1 for none. */
        int valueChip = -1;
        /** A picture before the value, a marker's icon; null for none. */
        Picture valuePicture;
        /** The room the value's picture takes across. */
        int valuePictureWidth;
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
        /** A hairline between two groups of rows; never hovered, never taken. */
        public boolean separator;
        /**
         * A paragraph that is read, not taken: its words wrapped to the
         * menu's width over as many lines as they need; a page's help.
         */
        public boolean note;
        /** Whether the row is a number's: its value between two chevrons. */
        boolean stepper;
        /** Whether each chevron still moves the number; one at its bound is muted. */
        boolean canLess;
        boolean canMore;
        /** What the number's value says under the pointer. */
        String stepperTip = "";
        /**
         * The widest values the number can read, measured so its chevrons
         * stand still as it changes.
         */
        String[] stepperWidest = NO_WORDS;
        /** What the row says under the pointer while it can be taken: what its setting does; empty for nothing. */
        String tip = "";
        /**
         * The colour the value is drawn in; -1 for the aside tone. A
         * value changed but not yet saved stands in honey.
         */
        int valueColor = -1;

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

        /**
         * The same entry with a picture before its value, {@code width}
         * across, as a colour's chip stands: a marker icon's row shows the
         * icon it names. It is drawn from the value's left and the label's
         * capitals' top.
         */
        public Entry withValuePicture(Picture picture, int width) {
            this.valuePicture = picture;
            this.valuePictureWidth = picture == null ? 0 : Math.max(0, width);
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

        /** The same entry, muted and closed with nothing said under the pointer: held by a padlock. */
        public Entry held() {
            this.held = true;
            return this;
        }

        /**
         * The same entry as a number's stepper: its value between two
         * chevrons, each of which moves it while it can; the value says
         * {@code tip} under the pointer, and is given room for the widest
         * of {@code widest}.
         */
        public Entry withStepper(boolean less, boolean more, String tip,
                                 String... widest) {
            this.stepper = true;
            this.canLess = less;
            this.canMore = more;
            this.stepperTip = tip == null ? "" : tip;
            this.stepperWidest = widest == null ? NO_WORDS : widest;
            return this;
        }

        /** The same entry saying {@code words} under the pointer while it can be taken. */
        public Entry withTip(String words) {
            this.tip = words == null ? "" : words;
            return this;
        }

        /** The same entry with its value in {@code rgb}: a change waiting to be saved. */
        public Entry withValueColor(int rgb) {
            this.valueColor = rgb;
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
                     WindowPage icon) {
            this(id, label, false, false, dim, color, icon);
        }

        private Entry(String id, String label, boolean header,
                      boolean passive, boolean dim, int color, WindowPage icon) {
            this.id = id;
            this.label = label == null ? "" : label;
            this.header = header;
            this.passive = passive;
            this.dim = dim;
            this.color = color;
            this.icon = icon;
        }

        /** A section label: {@code Channels}, {@code Whispers}. */
        public static Entry header(String label) {
            return new Entry("", label, true, false, false, -1, null);
        }

        /**
         * A section label that folds its section away and back, taken as
         * {@code id}; {@code depth} 1 for a subsection.
         */
        public static Entry fold(String id, String label, boolean folded,
                                 int depth) {
            Entry entry = new Entry(id, label, true, false, false, -1, null);
            entry.foldable = true;
            entry.folded = folded;
            entry.depth = Math.max(0, depth);
            return entry;
        }

        /** A display row that cannot be taken. */
        public static Entry passive(String label) {
            return new Entry("", label, false, true, false, -1, null);
        }

        /** A hairline parting one group of rows from the next. */
        public static Entry separator() {
            Entry entry = new Entry("", "", false, true, false, -1, null);
            entry.separator = true;
            return entry;
        }

        /** A paragraph, wrapped to the menu's width; see {@link #note}. */
        public static Entry note(String text) {
            Entry entry = new Entry("", text, false, true, false, -1, null);
            entry.note = true;
            return entry;
        }

        /** A group's name in {@code rgb}, a channel's {@code icon} before it or none. */
        public static Entry group(String label, WindowPage icon, int rgb) {
            Entry entry = new Entry("", label, false, true, false, -1, icon);
            entry.group = true;
            entry.labelColor = rgb;
            return entry;
        }

        /** The same entry, chosen or not: a chosen row stays lit. */
        public Entry chosen(boolean on) {
            this.chosen = on;
            return this;
        }

        /** The same entry with a picture before its name. */
        public Entry withPicture(Picture drawn) {
            this.picture = drawn;
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
            copy.held = this.held;
            copy.value = this.value;
            copy.valueChip = this.valueChip;
            copy.valuePicture = this.valuePicture;
            copy.valuePictureWidth = this.valuePictureWidth;
            copy.keys = this.keys;
            copy.group = this.group;
            copy.separator = this.separator;
            copy.note = this.note;
            copy.stepper = this.stepper;
            copy.canLess = this.canLess;
            copy.canMore = this.canMore;
            copy.stepperTip = this.stepperTip;
            copy.stepperWidest = this.stepperWidest;
            copy.tip = this.tip;
            copy.valueColor = this.valueColor;
            copy.foldable = this.foldable;
            copy.folded = this.folded;
            copy.depth = this.depth;
            return copy;
        }

        /** Whether a press on the row does something. */
        public boolean isTakeable() {
            return (!this.header || this.foldable) && !this.passive
                    && !this.held && this.unavailable.length() == 0;
        }

        /** What a header writes before its name: a folding one's sign. */
        String headerPrefix() {
            return !this.foldable ? "" : this.folded ? "+ " : OPEN_SIGN;
        }

        /** How far a header stands in: a subsection's sign under its section's name. */
        int headerIndent(FontRenderer font) {
            return this.depth * font.getStringWidth(OPEN_SIGN);
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

        Layout(LostTalesUiHitBox box, int fieldHeight) {
            this.left = (int)Math.floor(box.left);
            this.top = (int)Math.floor(box.top);
            this.width = (int)Math.floor(box.width);
            this.height = (int)Math.floor(box.height);
            this.rowsTop = this.top + PADDING_Y + fieldHeight;
            this.rowsBottom = Math.max(this.rowsTop,
                    this.top + this.height - PADDING_Y);
        }

        /** How tall the band the rows glide in is. */
        int band() {
            return this.rowsBottom - this.rowsTop;
        }
    }

    /** The kind of window it stands in; null for a menu standing in a page. */
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
    /** Rows the window opens with at most: {@link #MAX_VISIBLE_ROWS}, or a menu's own. */
    private int visibleRows = MAX_VISIBLE_ROWS;
    /** The width the notes are wrapped to: the rows' room in the box last laid out. */
    private int noteWidth = NOTE_WIDTH - PADDING_X * 2;
    /** Each note's lines at {@link #noteWidth}, by its words. */
    private final Map<String, List<String>> noteLines =
            new HashMap<String, List<String>>();
    private int noteLinesWidth = -1;
    /** Width of the colour column: a bar, or a chip when a row is a colour. */
    private int swatchWidth = SWATCH_WIDTH;
    /** Left edge of the labels inside the box, past any swatch column. */
    private int labelX = PADDING_X;
    /** How far down the list the wheel asked for, in pixels; what lies above it is past the top edge. */
    private double scrollPixels;
    /**
     * The offset the list is drawn at, easing toward
     * {@link #scrollPixels} with the windows' shared scroll motion so a
     * wheel turn glides the rows instead of jumping them. Hit testing
     * reads this too, so it always answers for what is on screen.
     */
    private double renderedScrollPixels;
    private long scrollNanos;
    /** Whether the chosen row is to be scrolled into the band as the menu is next drawn. */
    private boolean revealChosen;
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
    /** What the field may hold; null for anything. */
    private FieldFilter fieldFilter;
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
    /** Each stepper chevron's glyph motion, by its row's id and its side. */
    private final Map<String, LostTalesUiButtonMotion> chevrons =
            new HashMap<String, LostTalesUiButtonMotion>();

    MenuWindow(SubWindowKind kind, Owner owner) {
        this.kind = kind;
        this.owner = owner;
    }

    /** A menu standing in a page rather than in a window of its own ({@link PageRows}): it has no kind. */
    MenuWindow(Owner owner) {
        this(null, owner);
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

    /**
     * How tall every row of every menu is: {@link #ROW_HEIGHT}, which holds
     * a key icon at the windows' size; taller, for every menu alike, only
     * where the GUI's scale leaves the keys taller than a row (GUI scale 1).
     */
    public static int rowHeight() {
        return Math.max(ROW_HEIGHT, (int)Math.ceil(
                LostTalesInputIconRenderer.windowHeight()));
    }

    /** How many rows the window opens with at most; a page's help opens taller than a menu. */
    public void setVisibleRows(int rows) {
        this.visibleRows = Math.max(1, rows);
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
            if (entry.group || entry.separator) {
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
        this.scrollPixels = 0.0D;
        this.renderedScrollPixels = 0.0D;
        this.spriteFades.clear();
        this.labelFades.clear();
        this.chevrons.clear();
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
        this.fieldFilter = null;
        this.filterPrompt = prompt == null ? "" : prompt;
        this.filterHint = hint == null ? NO_KEYS : hint;
        this.fieldIcon = icon == null ? LostTalesUiSheet.SEARCH : icon;
    }

    /** Takes the field away: the rows alone. */
    public void closeField() {
        this.field = null;
        this.fieldList = null;
        this.fieldFilter = null;
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

    /** What the field may hold from now on; null for anything. */
    public void setFieldFilter(FieldFilter filter) {
        this.fieldFilter = filter;
    }

    /** Chooses all the field holds, so what is typed next replaces it. */
    public void selectField() {
        if (this.field != null) {
            this.field.setCursorPositionEnd();
            this.field.setSelectionPos(0);
        }
    }

    /**
     * Offers a press to the field, as the input bar's field takes one —
     * typing, the caret's keys, selecting, the clipboard — and answers
     * whether it changed what the field holds. A press that would leave
     * the field holding what it may not is undone, caret and all.
     */
    boolean edit(LostTalesKeyPress press) {
        if (this.field == null) {
            return false;
        }
        String before = this.field.getText();
        int caret = this.field.getCursorPosition();
        int selection = this.field.getSelectionEnd();
        this.field.textboxKeyTyped(press.character, press.key);
        if (this.fieldFilter != null
                && !this.fieldFilter.accepts(this.field.getText())) {
            this.field.setText(before);
            this.field.setCursorPosition(caret);
            this.field.setSelectionPos(selection);
        }
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
                this.owner.take(this, hover.menuEntry, hover.part,
                        button == 1);
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
                    + SWATCH_GAP + (int)Math.ceil(hintWidth(minecraft))
                    + PADDING_X);
        }
        String name = stripTitle();
        if (name == null && this.kind != null) {
            name = this.kind.title();
        }
        return Math.max(widest, SubWindowStrip.widthFor(font,
                name != null ? name : "", this.icon != null));
    }

    /** The width a row takes whole: from the window's edge to its value's end. */
    private int rowWidth(Minecraft minecraft, FontRenderer font, Entry entry) {
        if (entry.separator) {
            return 0;
        }
        if (entry.note) {
            // A note wraps: it asks for no more than a note's width.
            return PADDING_X + Math.min(font.getStringWidth(entry.label),
                    NOTE_WIDTH - PADDING_X * 2) + PADDING_X;
        }
        int label = entry.labelStyle != null
                ? entry.labelStyle.width(font, entry.label)
                : font.getStringWidth(entry.label);
        if (entry.header) {
            return PADDING_X + entry.headerIndent(font)
                    + font.getStringWidth(entry.headerPrefix())
                    + label + PADDING_X;
        }
        if (entry.group) {
            return PADDING_X + (entry.icon != null
                    ? TabIcons.SLOT + TabIcons.GAP : 0)
                    + label + PADDING_X;
        }
        int value = (int)Math.ceil(valueWidth(minecraft, font, entry));
        return this.labelX + label + (value > 0 ? VALUE_GAP + value : 0)
                + PADDING_X;
    }

    /** Every row up to {@link #visibleRows}, the field above them, {@code width} wide. */
    @Override
    public int naturalHeight(int width) {
        wrapNotesTo(width);
        return PADDING_Y * 2 + fieldHeight() + heightOfRows(Math.max(1,
                Math.min(rowCount(), this.visibleRows)));
    }

    /**
     * How many rows the list counts as: every row but the separators, a
     * note as many as its lines take of the menu's rows.
     */
    private int rowCount() {
        int rows = 0;
        for (Entry entry : this.entries) {
            rows += rowsOf(entry);
        }
        return rows;
    }

    /** How many of the menu's rows an entry counts as: none for a separator. */
    private int rowsOf(Entry entry) {
        if (entry.separator) {
            return 0;
        }
        return entry.note ? Math.max(1, (heightOf(entry) + rowHeight() - 1)
                / rowHeight()) : 1;
    }

    /**
     * How tall a row stands: a separator its hairline's room, a note its
     * lines, any other the menu's row.
     */
    private int heightOf(Entry entry) {
        if (entry.separator) {
            return SEPARATOR_HEIGHT;
        }
        return entry.note ? NOTE_PADDING * 2
                + linesOf(entry).size() * NOTE_LINE : rowHeight();
    }

    /**
     * How tall the list's first {@code rows} rows stand, with the
     * separators between them, a note cut at the rows it has room for; a
     * list shorter than that is as tall as it is, and never shorter than
     * one row.
     */
    private int heightOfRows(int rows) {
        int height = 0;
        int counted = 0;
        for (Entry entry : this.entries) {
            if (counted >= rows) {
                break;
            }
            int of = rowsOf(entry);
            height += counted + of <= rows ? heightOf(entry)
                    : (rows - counted) * rowHeight();
            counted += of;
        }
        return Math.max(rowHeight(), height);
    }

    /** The notes wrapped to a content box {@code width} wide from now on. */
    private void wrapNotesTo(int width) {
        this.noteWidth = Math.max(1, width - PADDING_X * 2);
    }

    /** A note's words over the lines they take at {@link #noteWidth}. */
    private List<String> linesOf(Entry entry) {
        if (this.noteLinesWidth != this.noteWidth) {
            this.noteLines.clear();
            this.noteLinesWidth = this.noteWidth;
        }
        List<String> lines = this.noteLines.get(entry.label);
        if (lines == null) {
            lines = new ArrayList<String>();
            for (Object line : Minecraft.getMinecraft().fontRenderer
                    .listFormattedStringToWidth(entry.label, this.noteWidth)) {
                lines.add(String.valueOf(line));
            }
            this.noteLines.put(entry.label, lines);
        }
        return lines;
    }

    /**
     * Where each row's top stands under the first row's, and at the end
     * how tall the whole list is.
     */
    private int[] rowTops() {
        int[] tops = new int[this.entries.size() + 1];
        for (int index = 0; index < this.entries.size(); index++) {
            tops[index + 1] = tops[index] + heightOf(this.entries.get(index));
        }
        return tops;
    }

    @Override
    public int minWidth() {
        return MIN_WIDTH;
    }

    /** One row under the field. */
    @Override
    public int minHeight() {
        return PADDING_Y * 2 + fieldHeight() + rowHeight();
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
        wrapNotesTo(width);
        int roomLeft = (int)Math.ceil(room.left);
        int roomTop = (int)Math.ceil(room.top);
        int roomRight = (int)Math.floor(room.left + room.width);
        int roomBottom = (int)Math.floor(room.top + room.height);
        int frame = SubWindow.STRIP_HEIGHT + PADDING_Y * 2
                + fieldHeight();
        int wanted = Math.max(1, Math.min(rowCount(), this.visibleRows));
        int roomBelow = rowsIn(roomBottom - anchor.bottom - SubWindowAnchor.REACH
                - frame);
        int roomAbove = rowsIn(anchor.top - SubWindowAnchor.REACH - roomTop - frame);
        boolean below = anchor.below
                ? roomBelow >= wanted || roomBelow >= roomAbove
                : !(roomAbove >= wanted || roomAbove >= roomBelow);
        int rows = Math.max(1, Math.min(wanted,
                below ? roomBelow : roomAbove));
        int height = frame + heightOfRows(rows);
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
        wrapNotesTo(width);
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
        int wanted = Math.max(1, Math.min(rowCount(), this.visibleRows));
        int rows = Math.max(1, Math.min(wanted,
                rowsIn((int)Math.floor(roomBottom - top) - frame)));
        int height = frame + heightOfRows(rows);
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
        wrapNotesTo(width);
        int frame = SubWindow.STRIP_HEIGHT + PADDING_Y * 2
                + fieldHeight();
        int wanted = Math.max(1, Math.min(rowCount(), this.visibleRows));
        int rows = Math.max(1, Math.min(wanted,
                rowsIn((int)Math.floor(room.height) - frame)));
        int height = frame + heightOfRows(rows);
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
        return Math.max(0, pixels / rowHeight());
    }

    /** Room the field takes above the rows; none without one: a row and its hairline. */
    private int fieldHeight() {
        return this.field == null ? 0 : WindowLists.fieldHeight();
    }

    /** The field's icon and the gap after it. */
    private int fieldIconRun() {
        return this.fieldIcon.getWidth() + TabIcons.GAP;
    }

    /** The row under the point, takeable or not; null for none. */
    public Entry rowAt(LostTalesUiHitBox box, double x, double y) {
        Layout at = layOut(box);
        clampScroll(at);
        int index = rowIndexAt(at, x, y);
        return index < 0 ? null : this.entries.get(index);
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
        hover.tip = takes ? row.tip : "";
        hover.greyedWhy = row == null || takes ? "" : row.unavailable;
        if (takes && row.stepper) {
            // A stepper's parts answer where they are drawn, measured as
            // the draw measures them.
            hover.part = stepperPartAt(x, y, at.left + at.width - PADDING_X,
                    listTop(at) + rowTops()[index],
                    rowHeight(), stepperTextWidth(
                            Minecraft.getMinecraft().fontRenderer, row));
            if (PART_VALUE.equals(hover.part)) {
                hover.tip = row.stepperTip;
            }
        }
        return hover;
    }

    /** Scrolls the first chosen row into the band as the menu is next drawn: the keys walked to it. */
    void revealChosen() {
        this.revealChosen = true;
    }

    /** Moves the target just far enough that the first chosen row stands whole in the band. */
    private void reveal(Layout at) {
        int[] tops = rowTops();
        for (int index = 0; index < this.entries.size(); index++) {
            if (!this.entries.get(index).chosen) {
                continue;
            }
            if (tops[index] < this.scrollPixels) {
                this.scrollPixels = tops[index];
            } else if (tops[index + 1] > this.scrollPixels + at.band()) {
                this.scrollPixels = tops[index + 1] - at.band();
            }
            this.scrollPixels = Math.max(0.0D,
                    Math.min(maxScroll(at), this.scrollPixels));
            return;
        }
    }

    /**
     * Moves the list's target by whole rows' height; beyond either end it
     * stays put. The drawn rows glide after the target.
     */
    @Override
    public void scrollBy(int lines) {
        this.scrollPixels += WheelStep.pixels(WheelStep.menuRows(lines),
                rowHeight());
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
        if (this.revealChosen) {
            this.revealChosen = false;
            reveal(at);
        }
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
        // Rows are laid out from the drawn offset and clipped to the band
        // they glide in; only those the band shows are drawn.
        int[] tops = rowTops();
        int listTop = listTop(at);
        // The chosen rows and the hovered one, cut to the band, before
        // anything lands on them: each lit alike.
        for (int index = 0; index < this.entries.size(); index++) {
            if (index != hoveredIndex && !this.entries.get(index).chosen) {
                continue;
            }
            int litTop = Math.max(at.rowsTop, listTop + tops[index]);
            int litBottom = Math.min(at.rowsBottom, listTop + tops[index + 1]);
            if (litBottom > litTop) {
                WindowLists.drawLitRow(at.left, at.left + at.width, at.left,
                        litTop, at.left + at.width, litBottom, surfaceAlpha);
            }
        }
        drawField(minecraft, font, at, alpha);
        boolean clipped = beginClip(minecraft, clipX + (at.left - box.left),
                clipY + (at.rowsTop - box.top), at.width,
                at.rowsBottom - at.rowsTop);
        try {
            for (int index = 0; index < this.entries.size(); index++) {
                int rowY = listTop + tops[index];
                if (rowY >= at.rowsBottom) {
                    break;
                }
                if (listTop + tops[index + 1] > at.rowsTop) {
                    drawRow(minecraft, font, at, this.entries.get(index), rowY,
                            index == hoveredIndex, pointerX, pointerY,
                            elapsed, alpha);
                }
            }
        } finally {
            endClip(clipped);
        }
        // The fades hang from the frame, or from the field's hairline the
        // rows pass under.
        WindowLists.drawScroll(at.left, this.field == null ? at.top
                        : at.rowsTop, at.left + at.width, at.top + at.height,
                at.rowsTop, at.rowsBottom, this.renderedScrollPixels,
                maxScroll(at), alpha);
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
                         double pointerX, double pointerY, double elapsed,
                         int alpha) {
        if (entry.separator) {
            WindowLists.drawSeparator(at.left + PADDING_X,
                    at.left + at.width - PADDING_X, rowY, alpha);
            return;
        }
        if (entry.note) {
            // A paragraph: its lines at the padding, in the chat's ivory,
            // or the colour the note was given.
            int y = rowY + NOTE_PADDING;
            int rgb = entry.labelColor >= 0 ? entry.labelColor
                    : LostTalesUiInk.IVORY;
            for (String line : linesOf(entry)) {
                LostTalesUiInk.drawText(font, line, at.left + PADDING_X,
                        y + LostTalesUiInk.centredStart(NOTE_LINE,
                                LostTalesUiInk.CAP_HEIGHT), rgb, alpha);
                y += NOTE_LINE;
            }
            return;
        }
        int labelTop = rowY + LostTalesUiInk.centredStart(rowHeight(),
                LostTalesUiInk.CAP_HEIGHT);
        if (entry.header) {
            // The section's name over a hairline, in the sand the
            // timestamps wear, so it reads as a label, not a row; one that
            // folds wears its sign before it, and lights to ivory under
            // the pointer as a row's name does.
            WindowLists.drawHeading(font, entry.headerPrefix() + entry.label,
                    at.left + PADDING_X,
                    at.left + PADDING_X + entry.headerIndent(font),
                    at.left + at.width - PADDING_X, rowY, rowHeight(),
                    hovered, alpha);
            return;
        }
        if (entry.group) {
            drawGroup(minecraft, font, at, entry, labelTop, alpha);
            return;
        }
        // A row that cannot be taken is greyed as every control is: its
        // colour, its icon and its words at half strength, each in its
        // own colour, and nothing lights under the pointer.
        boolean greyed = entry.unavailable.length() > 0 || entry.held;
        int markAlpha = greyed
                ? Math.round(alpha * WindowStyle.UNAVAILABLE_OPACITY) : alpha;
        if (entry.color >= 0) {
            // The channel's colour as a one-pixel upright bar the height
            // of the row's text; a palette row's as a chip the width of
            // the column.
            Gui.drawRect(at.left + PADDING_X, rowY + 1,
                    at.left + PADDING_X
                            + (entry.chip ? this.swatchWidth : SWATCH_WIDTH),
                    rowY + rowHeight() - 1,
                    LostTalesUiInk.argb(entry.color, markAlpha));
        }
        drawRowIcon(minecraft, at, entry, labelTop, hovered && !greyed,
                elapsed, markAlpha);
        int labelRgb = entry.labelColor >= 0 ? entry.labelColor
                : LostTalesUiInk.IVORY;
        if (entry.icon != null) {
            labelRgb = LostTalesUiInk.blend(labelRgb,
                    entry.icon.tone(),
                    labelFade(entry, hovered && !greyed, elapsed));
        }
        int labelLeft = at.left + this.labelX;
        int right = at.left + at.width - PADDING_X;
        float value = valueWidth(minecraft, font, entry);
        if (entry.stepper) {
            drawStepper(font, entry, right, labelTop, hovered
                    ? stepperPartAt(pointerX, pointerY, right, rowY,
                            rowHeight(), stepperTextWidth(font, entry))
                    : null, markAlpha);
            right = (int)Math.floor(right - value) - VALUE_GAP;
        } else if (value > 0.0F) {
            drawValue(minecraft, font, entry, right - value, rowY, labelTop,
                    markAlpha);
            right = (int)Math.floor(right - value) - VALUE_GAP;
        }
        if (entry.labelStyle != null) {
            // A styled label cannot be cut by the letter; the clip cuts a
            // line longer than the window at its edge.
            entry.labelStyle.draw(minecraft, font, entry.label,
                    entry.dim ? "§o" : "", labelLeft, labelTop, labelRgb,
                    markAlpha);
        } else {
            String label = trimmed(font, entry.label, right - labelLeft);
            LostTalesUiInk.drawText(font,
                    entry.dim ? "§o" + label : label, labelLeft, labelTop,
                    labelRgb, markAlpha);
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

    /**
     * How wide a row's value is: its chip, its words and its keys, which
     * may end between the GUI's pixels; a stepper's, its chevrons and the
     * room its number takes.
     */
    private static float valueWidth(Minecraft minecraft, FontRenderer font,
                                    Entry entry) {
        if (entry.stepper) {
            return STEPPER_CELL * 2 + stepperTextWidth(font, entry);
        }
        float width = 0.0F;
        if (entry.valuePicture != null) {
            width += entry.valuePictureWidth
                    + (entry.value.length() > 0 ? SWATCH_GAP : 0);
        }
        if (entry.valueChip >= 0) {
            width += CHIP_WIDTH + (entry.value.length() > 0 ? SWATCH_GAP : 0);
        }
        width += font.getStringWidth(entry.value);
        for (Object part : entry.keys) {
            width += part instanceof Integer
                    ? LostTalesInputIconRenderer.windowWidth(minecraft,
                            ((Integer)part).intValue())
                    : KEY_GAP + font.getStringWidth(String.valueOf(part))
                            + KEY_GAP;
        }
        return width;
    }

    /**
     * A row's value from {@code start}: its picture, a colour's chip, a
     * square on the words' capitals, then the words in the aside tone,
     * then a shortcut's keys in the mod's own key icons a size smaller
     * than the GUI's on the row's middle, words between them a seam's
     * width clear. The keys end on display pixels, so the run may start
     * between the GUI's pixels.
     */
    private void drawValue(Minecraft minecraft, FontRenderer font,
                           Entry entry, float start, int rowY, int labelTop,
                           int alpha) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(start, 0.0F, 0.0F);
            drawValueRun(minecraft, font, entry, rowY, labelTop, alpha);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private void drawValueRun(Minecraft minecraft, FontRenderer font,
                              Entry entry, int rowY, int labelTop,
                              int alpha) {
        int x = 0;
        int aside = WindowStyle.asideRgb();
        int valueRgb = entry.valueColor >= 0 ? entry.valueColor : aside;
        if (entry.valuePicture != null) {
            entry.valuePicture.draw(minecraft, x, labelTop, alpha);
            x += entry.valuePictureWidth
                    + (entry.value.length() > 0 ? SWATCH_GAP : 0);
        }
        if (entry.valueChip >= 0) {
            Gui.drawRect(x, labelTop, x + CHIP_WIDTH,
                    labelTop + LostTalesUiInk.CAP_HEIGHT,
                    LostTalesUiInk.argb(entry.valueChip, alpha));
            x += CHIP_WIDTH + (entry.value.length() > 0 ? SWATCH_GAP : 0);
        }
        if (entry.value.length() > 0) {
            LostTalesUiInk.drawText(font, entry.value, x,
                    labelTop, valueRgb, alpha);
            x += font.getStringWidth(entry.value);
        }
        float keyX = x;
        float keyY = LostTalesInputIconRenderer.windowTop(rowY,
                rowHeight());
        for (Object part : entry.keys) {
            if (part instanceof Integer) {
                int keyCode = ((Integer)part).intValue();
                LostTalesUiInk.beginContent();
                LostTalesInputIconRenderer.drawInput(minecraft,
                        LostTalesInputBinding.Type.KEYBOARD, keyCode, keyX,
                        keyY, LostTalesInputIconRenderer.windowScale(),
                        alpha / 255.0F);
                keyX += LostTalesInputIconRenderer.windowWidth(minecraft,
                        keyCode);
            } else {
                String word = String.valueOf(part);
                keyX += KEY_GAP;
                drawTextAt(font, word, keyX, labelTop, aside, alpha);
                keyX += font.getStringWidth(word) + KEY_GAP;
            }
        }
    }

    /** Words at a place that may lie between the GUI's pixels, on a display pixel, as a key's edge does. */
    private static void drawTextAt(FontRenderer font, String text, float x,
                                   float y, int rgb, int alpha) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, y, 0.0F);
            LostTalesUiInk.drawText(font, text, 0, 0, rgb, alpha);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** The room a stepper's number takes: its widest value, whatever it reads now. */
    private static int stepperTextWidth(FontRenderer font, Entry entry) {
        int width = font.getStringWidth(entry.value);
        for (String widest : entry.stepperWidest) {
            width = Math.max(width, font.getStringWidth(widest));
        }
        return width;
    }

    /**
     * Which part of a stepper whose value ends at {@code right}, in a row
     * {@code rowHeight} tall from {@code rowTop}, lies under a point: the
     * chevron down, the number, the chevron up, or null for none. Each
     * answers across the row's height, the number across the room its
     * widest value takes.
     */
    public static String stepperPartAt(double x, double y, int right,
                                       int rowTop, int rowHeight,
                                       int textWidth) {
        int moreLeft = right - STEPPER_CELL;
        int textLeft = moreLeft - textWidth;
        int lessLeft = textLeft - STEPPER_CELL;
        if (LostTalesUiHitBox.contains(x, y, moreLeft, rowTop, STEPPER_CELL,
                rowHeight)) {
            return PART_MORE;
        }
        if (LostTalesUiHitBox.contains(x, y, textLeft, rowTop, textWidth,
                rowHeight)) {
            return PART_VALUE;
        }
        if (LostTalesUiHitBox.contains(x, y, lessLeft, rowTop, STEPPER_CELL,
                rowHeight)) {
            return PART_LESS;
        }
        return null;
    }

    /** A number's row's stepper, its chevrons moved by the motions the menu keeps for the row. */
    private void drawStepper(FontRenderer font, Entry entry, int right,
                             int labelTop, String pointed, int alpha) {
        drawStepper(font, entry.value, stepperTextWidth(font, entry), right,
                labelTop, pointed, chevronMotion(entry, LESS, entry.canLess),
                chevronMotion(entry, MORE, entry.canMore),
                entry.valueColor >= 0 ? entry.valueColor
                        : WindowStyle.asideRgb(), alpha);
    }

    /**
     * A stepper's value between its chevrons, the whole ending at
     * {@code right}: the value centred in {@code textWidth}, the room its
     * widest value takes, in {@code valueRgb} and in ivory while
     * {@code pointed} names it; each chevron a glyph button moved by its
     * motion, and at the bound it cannot pass, a null motion, a flat shape
     * in the aside tone that nothing moves. Every list's stepper is drawn
     * here, and {@link #stepperPartAt} answers for it.
     */
    public static void drawStepper(FontRenderer font, String value,
                                   int textWidth, int right, int labelTop,
                                   String pointed,
                                   LostTalesUiButtonMotion less,
                                   LostTalesUiButtonMotion more,
                                   int valueRgb, int alpha) {
        int moreLeft = right - STEPPER_CELL;
        int textLeft = moreLeft - textWidth;
        int lessLeft = textLeft - STEPPER_CELL;
        int chevronY = labelTop + Math.floorDiv(
                LostTalesUiInk.CAP_HEIGHT - LESS.getHeight(), 2);
        drawChevron(LESS, LESS_LIT, less, PART_LESS.equals(pointed),
                lessLeft, chevronY, alpha);
        drawChevron(MORE, MORE_LIT, more, PART_MORE.equals(pointed),
                moreLeft, chevronY, alpha);
        LostTalesUiInk.drawText(font, value,
                textLeft + LostTalesUiInk.centredStart(textWidth,
                        font.getStringWidth(value)),
                labelTop, PART_VALUE.equals(pointed) ? LostTalesUiInk.IVORY
                        : valueRgb, alpha);
    }

    /** The motion a row's chevron keeps while it can move; null at its bound. */
    private LostTalesUiButtonMotion chevronMotion(Entry entry,
                                                  LostTalesUiSheet sprite,
                                                  boolean moves) {
        if (!moves) {
            return null;
        }
        String key = entry.id + ":" + sprite.name();
        LostTalesUiButtonMotion motion = this.chevrons.get(key);
        if (motion == null) {
            motion = new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
            this.chevrons.put(key, motion);
        }
        return motion;
    }

    /**
     * One of a stepper's chevrons, centred in its cell, rising and
     * lighting under the pointer and dropping as it is pressed, as every
     * glyph button does; with no motion, at its bound, a flat shape.
     */
    private static void drawChevron(final LostTalesUiSheet sprite,
                                    LostTalesUiSheet lit,
                                    LostTalesUiButtonMotion motion,
                                    boolean pointed, int cellLeft,
                                    final int top, final int alpha) {
        final int x = cellLeft + LostTalesUiInk.centredStart(STEPPER_CELL,
                sprite.getWidth());
        LostTalesUiInk.beginContent();
        if (motion != null) {
            motion.advance(System.nanoTime(), pointed, pointed, pointed
                    && (Mouse.isButtonDown(0) || Mouse.isButtonDown(1)));
            LostTalesUiButton.drawGlyph(sprite, lit, motion, x, top, alpha);
            return;
        }
        // At its bound the chevron is greyed as every control is: still,
        // at half strength, over its shadow as one picture.
        LostTalesUiSheet.drawPairWithShadow(sprite, sprite, 0.0F, x, top,
                Math.round(alpha * WindowStyle.UNAVAILABLE_OPACITY));
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
        int right = at.left + at.width - PADDING_X;
        this.fieldRowLeft = at.left + PADDING_X;
        this.fieldRowTop = top;
        int promptX = at.left + PADDING_X + fieldIconRun()
                + LostTalesUiCaret.WIDTH + 1;
        float hintWidth = this.field.getText().length() == 0
                ? hintWidth(minecraft) : 0.0F;
        boolean hinted = hintWidth > 0.0F
                && right - hintWidth - SWATCH_GAP > promptX;
        WindowLists.drawField(font, this.field, this.fieldIcon,
                this.filterPrompt, at.left + PADDING_X, top, right,
                hinted ? right - (int)Math.ceil(hintWidth) - SWATCH_GAP
                        : right, alpha);
        if (hinted) {
            drawHint(minecraft, font, right - hintWidth, top, fieldHeight(),
                    alpha);
        }
    }

    /**
     * The shortcut as the keys themselves, in the mod's own key icons a
     * size smaller than the GUI's, which press and spring back as the keys
     * are held, a {@code +} between each pair on the keys' middle.
     */
    private void drawHint(Minecraft minecraft, FontRenderer font, float keyX,
                          int top, int height, int alpha) {
        float keyY = LostTalesInputIconRenderer.windowTop(top, height - 1);
        String joiner = keyJoiner();
        int factor = LostTalesDisplayPixels.scaleFactor();
        float joinerY = keyY + LostTalesUiInk.centredStart(
                Math.round(LostTalesInputIconRenderer.windowHeight() * factor),
                LostTalesUiInk.CAP_HEIGHT * factor) / (float)factor;
        int quiet = LostTalesColors.rgb(LostTalesColors.SAND);
        LostTalesUiInk.beginContent();
        for (int index = 0; index < this.filterHint.length; index++) {
            if (index > 0) {
                keyX += KEY_GAP;
                drawTextAt(font, joiner, keyX, joinerY, quiet, alpha);
                keyX += font.getStringWidth(joiner) + KEY_GAP;
                LostTalesUiInk.beginContent();
            }
            LostTalesInputIconRenderer.drawInput(minecraft,
                    LostTalesInputBinding.Type.KEYBOARD,
                    this.filterHint[index], keyX, keyY,
                    LostTalesInputIconRenderer.windowScale(), alpha / 255.0F);
            keyX += LostTalesInputIconRenderer.windowWidth(minecraft,
                    this.filterHint[index]);
        }
    }

    /**
     * Room the shortcut takes: its key icons a size smaller than the
     * GUI's, unrounded, with a {@code +} between each pair of them.
     */
    private float hintWidth(Minecraft minecraft) {
        float width = 0.0F;
        for (int index = 0; index < this.filterHint.length; index++) {
            if (index > 0) {
                width += KEY_GAP + minecraft.fontRenderer.getStringWidth(
                        keyJoiner()) + KEY_GAP;
            }
            width += LostTalesInputIconRenderer.windowWidth(minecraft,
                    this.filterHint[index]);
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
        return Math.max(0.0D, rowTops()[this.entries.size()] - at.band());
    }

    /** Keeps both the target and the drawn offset within the list as the box shows it. */
    private void clampScroll(Layout at) {
        double max = maxScroll(at);
        this.scrollPixels = Math.max(0.0D, Math.min(max, this.scrollPixels));
        this.renderedScrollPixels = Math.max(0.0D,
                Math.min(max, this.renderedScrollPixels));
    }

    /**
     * Advances the drawn offset toward its target, once per drawn frame;
     * with animations off it simply arrives.
     */
    private void advanceScrollEasing() {
        long now = System.nanoTime();
        double elapsed = (now - this.scrollNanos) / 1.0E9D;
        this.scrollNanos = now;
        if (Math.abs(this.scrollPixels - this.renderedScrollPixels) <= 0.1D) {
            this.renderedScrollPixels = this.scrollPixels;
            return;
        }
        this.renderedScrollPixels = Motions.followTravel(
                MotionIds.WINDOW_SCROLL, this.renderedScrollPixels,
                this.scrollPixels, elapsed);
    }

    /**
     * The field and the rows in a content box, at this menu's row height,
     * its notes wrapped to the box.
     */
    private Layout layOut(LostTalesUiHitBox box) {
        Layout at = new Layout(box, fieldHeight());
        wrapNotesTo(at.width);
        return at;
    }

    /** Where the list's first row is drawn: the drawn offset slides it up past the band. */
    private int listTop(Layout at) {
        return at.rowsTop - (int)Math.round(this.renderedScrollPixels);
    }

    /**
     * Where the row under a point is drawn in a content box, the box's
     * whole width across: what a window opened from the row hangs from.
     * Null off every row.
     */
    LostTalesUiHitBox rowBoxAt(LostTalesUiHitBox box, double x, double y) {
        Layout at = layOut(box);
        clampScroll(at);
        int index = rowIndexAt(at, x, y);
        if (index < 0) {
            return null;
        }
        int[] tops = rowTops();
        return new LostTalesUiHitBox(at.left, listTop(at) + tops[index],
                at.width, tops[index + 1] - tops[index]);
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
        double offset = y - listTop(at);
        int[] tops = rowTops();
        for (int index = 0; index < this.entries.size(); index++) {
            if (offset >= tops[index] && offset < tops[index + 1]) {
                return index;
            }
        }
        return -1;
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
        return stepFade(fades, entry.id, lit, elapsed);
    }

    /** As above, for the crossfade kept under {@code id}: a row's, or a part of one. */
    private static float stepFade(Map<String, Float> fades, String id,
                                  boolean lit, double elapsed) {
        Float kept = fades.get(id);
        float fade = kept == null ? (lit ? 1.0F : 0.0F)
                : WindowStyle.hoverFade(kept.floatValue(), lit,
                        elapsed);
        fades.put(id, Float.valueOf(fade));
        return fade;
    }
}
