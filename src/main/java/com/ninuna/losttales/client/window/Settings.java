package com.ninuna.losttales.client.window;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.config.LostTalesConfigWords;
import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

/**
 * Settings: every client option, each section in its {@link Place}.
 * Window Settings, what reaches every window, opens from a window's
 * Window Options; the settings of each kind of page (Chat Settings for
 * every conversation, Quest Settings for the journal, Map Settings for
 * the map, Motion Settings for the Motion Lab) open from the page's cog
 * and its options; everything else, the keys among it, stands on the
 * Client Settings page. Each place
 * shows its sections under a search field, in the order the systems
 * added them, and Defaults last. Each option takes effect and is saved
 * the moment it changes: a switch flips on a click, a few-word option
 * steps forward on a click and back on a right-click, a number steps with
 * the chevrons beside it and is typed into where its value is clicked, a
 * colour opens the palette beside it. The game's own options read here
 * are written to the game's options, so its own screens and these always
 * agree. Restore Defaults puts every setting of its place back as the mod
 * and the game ship it, after asking in its own place. A row is acted on
 * by its id, so a list read again as it is typed into acts the same.
 */
public final class Settings {
    /** Marks a setting's row; the rest of the id is its key. */
    private static final String SETTING_PREFIX = "setting:";
    /** Marks a row that acts rather than sets; the rest of the id is its own. */
    private static final String ACTION_PREFIX = "action:";
    private static final String RESTORE = "restore";
    private static final String RESTORE_CONFIRM = "restore_confirm";
    /** Marks a palette row; the rest of the id is the palette entry's name. */
    private static final String PALETTE_PREFIX = "palette:";
    /** Marks a row of a few-word setting's words: this and the word's place. */
    private static final String WORD_PREFIX = "word:";
    /** The rows of the window a value is typed into. */
    private static final String VALUE_KEEP = "value:keep";
    private static final String VALUE_DEFAULT = "value:default";
    private static final String VALUE_CLEAR = "value:clear";
    /** Marks a name a line's field offers; the rest of the id is the name. */
    private static final String VALUE_OFFER_PREFIX = "value:offer:";
    /** The steps a share takes here: a tenth to the whole. */
    private static final int PERCENT_STEP = 10;
    /** The game's chat opacity as it ships, which Restore Defaults puts back. */
    private static final float GAME_CHAT_OPACITY = 1.0F;
    /** The longest a line setting's value reads in its row before it is cut. */
    private static final int LINE_SHOWN_LENGTH = 24;

    /** Where a setting is kept, and so what is written once it changes. */
    public enum Store {
        /** The mod's client file, {@code client/client.cfg}. */
        CLIENT_FILE,
        /** The camera's own file, {@code client/third-person.cfg}. */
        CAMERA_FILE,
        /** The game's own options. */
        GAME,
        /**
         * Nowhere: a page's own value, a waystone's name, which the page
         * keeps until it saves it its own way. Nothing is written, and
         * nothing else in Settings follows it.
         */
        NONE
    }

    /** A system's sections, added to every Settings as it is made. */
    public interface Sections {
        void addTo(Settings settings);
    }

    /**
     * Where a section stands: in a sub-window a window's Window Options or
     * a page's cog opens, or on the Client Settings page, which holds what
     * has no window or page of its own.
     */
    public enum Place {
        /** Window Settings, from a window's Window Options: what reaches every window. */
        WINDOWS("gui.losttales.window.settings.title.windows"),
        /** Chat Settings, from every conversation's cog. */
        CHAT("gui.losttales.window.settings.title.chat"),
        /**
         * The chat feed's own settings, under a conversation's three words
         * in its Chat Feed Settings: what reaches the feed of every
         * conversation.
         */
        FEED("gui.losttales.window.settings.title.feed"),
        /** Quest Settings, from the journal's cog. */
        QUESTS("gui.losttales.window.settings.title.quests"),
        /**
         * HUD Settings, from the HUD Placement page's cog: where each panel
         * stands as numbers, whether it follows the HUD key, and the
         * compass's own.
         */
        HUD("gui.losttales.window.settings.title.hud"),
        /** Map Settings, from the map's cog. */
        MAP("gui.losttales.window.settings.title.map"),
        /** Motion Settings, from the Motion Lab's cog: what every screen's and the HUD's motion answers to. */
        MOTION("gui.losttales.window.settings.title.motion"),
        /** The Client Settings page. */
        CLIENT("gui.losttales.page.client_settings");

        /** The name the place goes by, on its sub-window's strip and on the row that opens it. */
        public final String titleKey;

        Place(String titleKey) {
            this.titleKey = titleKey;
        }
    }

    /** Every system's sections, in the order the systems gave them. */
    private static final List<Sections> SYSTEMS =
            new CopyOnWriteArrayList<Sections>();

    /* ---- The kinds of setting ---- */

    /**
     * The lang key of a client option's name, the label of a row that
     * stands for that one option ({@link LostTalesConfigWords}).
     */
    public static String optionName(String key) {
        return LostTalesConfigWords.nameKey(LostTalesConfig.CATEGORY_CLIENT, key);
    }

    /** One setting: its name, what it reads now, and what a press does to it. */
    public abstract static class Setting {
        public final String key;
        private final String labelKey;

        protected Setting(String key, String labelKey) {
            this.key = key;
            this.labelKey = labelKey;
        }

        public String label() {
            return StatCollector.translateToLocal(this.labelKey);
        }

        /** The lang key of its name. */
        String labelKey() {
            return this.labelKey;
        }

        /** The words the row reads for the value now. */
        public abstract String value();

        /**
         * A click steps it on; with {@code back}, a right-click, back. A
         * setting of a few words never steps: its row opens its words.
         */
        public void step(boolean back) {}

        /**
         * A few-word setting's words, as its row reads each, in order; its
         * row opens them in a sub-window of their own. Empty for any other.
         */
        public List<String> words() {
            return Collections.emptyList();
        }

        /** Which of {@link #words} stands now; -1 for none. */
        public int wordIndex() {
            return -1;
        }

        /** Which of {@link #words} the mod or the game ships; -1 for none. */
        public int shippedWordIndex() {
            return -1;
        }

        /** Takes the word at {@code index} of {@link #words}. */
        public void pickWord(int index) {}

        /** The value as the mod or the game ships it. */
        public abstract void restore();

        /** Where it is kept: the mod's client file unless it says otherwise. */
        public Store store() {
            return Store.CLIENT_FILE;
        }

        /** What else follows it once it changed and was saved: a scale lays lines out again. */
        public void changed() {}

        /**
         * Whether it still stands: a page's own value goes once the page
         * lets it go, and the field it is typed into closes with it.
         */
        public boolean stands() {
            return true;
        }

        /** Its row: its name and what it reads now. */
        public MenuWindow.Entry row() {
            return new MenuWindow.Entry(SETTING_PREFIX + this.key, label())
                    .withValue(value());
        }
    }

    /** A setting of the mod's client file, saved there as it changes. */
    public abstract static class ModSetting extends Setting {
        protected ModSetting(String key, String labelKey) {
            super(key, labelKey);
        }

        /** The value the file writes as the mod ships it. */
        protected String shipped() {
            String value = LostTalesConfig.shippedClientValue(this.key);
            return value == null ? "" : value;
        }
    }

    /** An on-and-off option of the mod's. */
    public abstract static class ModSwitch extends ModSetting {
        protected ModSwitch(String key, String labelKey) {
            super(key, labelKey);
        }

        protected abstract boolean get();

        protected abstract void set(boolean on);

        @Override
        public String value() {
            return onOff(get());
        }

        @Override
        public void step(boolean back) {
            set(!get());
        }

        @Override
        public void restore() {
            set(Boolean.parseBoolean(shipped()));
        }
    }

    /** A few-word option of the mod's: one of {@code words}, each read by its lang key. */
    public abstract static class ModChoice extends ModSetting {
        private final String[] words;
        private final String wordKeyPrefix;

        protected ModChoice(String key, String labelKey, String[] words,
                            String wordKeyPrefix) {
            super(key, labelKey);
            this.words = words;
            this.wordKeyPrefix = wordKeyPrefix;
        }

        protected abstract String get();

        protected abstract void set(String word);

        @Override
        public String value() {
            return word(get());
        }

        private String word(String id) {
            return StatCollector.translateToLocal(this.wordKeyPrefix
                    + id.toLowerCase(Locale.ROOT));
        }

        @Override
        public List<String> words() {
            List<String> read = new ArrayList<String>(this.words.length);
            for (String id : this.words) {
                read.add(word(id));
            }
            return read;
        }

        @Override
        public int wordIndex() {
            return indexOf(this.words, get());
        }

        @Override
        public int shippedWordIndex() {
            return indexOf(this.words, shipped());
        }

        @Override
        public void pickWord(int index) {
            if (index >= 0 && index < this.words.length) {
                set(this.words[index]);
            }
        }

        @Override
        public void restore() {
            String shipped = shipped();
            set(indexOf(this.words, shipped) >= 0 ? shipped : this.words[0]);
        }
    }

    /**
     * A number: its value between two chevrons that step it, a step at a
     * time or ten with Shift, stopping at its bounds. A click on the
     * value opens a small field beside the window where a number within
     * the bounds can be typed. A click elsewhere on the row steps it on,
     * a right-click back, as a few-word setting steps.
     */
    public abstract static class Numeric extends ModSetting {
        private final double step;
        private final int decimals;

        protected Numeric(String key, String labelKey, double step,
                          int decimals) {
            super(key, labelKey);
            this.step = step;
            this.decimals = decimals;
        }

        protected abstract double get();

        protected abstract void set(double value);

        /** Its bounds as the option is defined, {min, max}; null where none is known. */
        protected double[] bounds() {
            return LostTalesConfig.shippedClientBounds(this.key);
        }

        /** The value the mod ships; NaN where none is known. */
        protected double shippedNumber() {
            return parseNumber(shipped());
        }

        /** Its arithmetic: the bounds its option is defined with, its step and places. */
        public NumberStepper stepper() {
            double[] bounds = bounds();
            return bounds == null
                    ? new NumberStepper(-Double.MAX_VALUE, Double.MAX_VALUE,
                            this.step, this.decimals)
                    : new NumberStepper(bounds[0], bounds[1], this.step,
                            this.decimals);
        }

        @Override
        public String value() {
            return stepper().format(get());
        }

        @Override
        public void step(boolean back) {
            move(!back, false);
        }

        /** One step up or down, ten with {@code fast}; answers whether it moved. */
        public boolean move(boolean up, boolean fast) {
            NumberStepper stepper = stepper();
            double now = get();
            if (!stepper.canStep(now, up)) {
                return false;
            }
            set(stepper.stepped(now, up, fast));
            return true;
        }

        /** A typed value taken; false for words that are no number within the bounds. */
        public boolean take(String typed) {
            Double value = stepper().parse(typed);
            if (value == null) {
                return false;
            }
            set(value.doubleValue());
            return true;
        }

        @Override
        public void restore() {
            double shipped = shippedNumber();
            if (!Double.isNaN(shipped)) {
                set(stepper().clamp(shipped));
            }
        }

        /**
         * Its row, a stepper given room for the wider of its bounds; with
         * no bounds known, for the value it reads.
         */
        @Override
        public MenuWindow.Entry row() {
            NumberStepper stepper = stepper();
            double now = get();
            boolean bounded = bounds() != null;
            return super.row().withStepper(stepper.canStep(now, false),
                    stepper.canStep(now, true),
                    bounded ? typeTip(stepper) : "",
                    bounded ? new String[] {stepper.format(stepper.min),
                            stepper.format(stepper.max)} : new String[0]);
        }
    }

    /**
     * A line of words, typed into the field its row opens beside the
     * window; the row reads it cut short, or None while it is empty.
     */
    public abstract static class Line extends ModSetting {
        protected Line(String key, String labelKey) {
            super(key, labelKey);
        }

        protected abstract String get();

        protected abstract void set(String text);

        /** The longest line it takes. */
        protected int maxLength() {
            return 256;
        }

        /** Whether it may be left empty; one that may not is never kept or cleared empty. */
        protected boolean mayBeEmpty() {
            return true;
        }

        /**
         * What its field offers for {@code typed}, each a row under Keep
         * that takes it: the players and fellowships a waystone can be
         * shared with. None unless it says otherwise.
         */
        protected List<String> offers(String typed) {
            return Collections.emptyList();
        }

        /**
         * What its field reads while nothing is typed: its name, unless
         * it stands for words of its own while empty.
         */
        protected String prompt() {
            return label();
        }

        /** What its row reads while it is empty: None, unless it stands for words of its own. */
        protected String emptyValue() {
            return StatCollector.translateToLocal(
                    "gui.losttales.window.settings.value.none");
        }

        @Override
        public String value() {
            String text = get() == null ? "" : get().trim();
            return text.length() == 0 ? emptyValue() : shown(text);
        }

        /** Words as a row reads them: their end, cut short where they are long. */
        protected static String shown(String text) {
            return text.length() <= LINE_SHOWN_LENGTH ? text
                    : "..." + text.substring(text.length()
                            - (LINE_SHOWN_LENGTH - 3));
        }

        /** A line is typed, not stepped. */
        @Override
        public void step(boolean back) {}

        @Override
        public void restore() {
            set(shipped());
        }
    }

    /** An option of the game's own, saved to its options: one that words itself. */
    public abstract static class GameSetting extends Setting {
        protected GameSetting(String key, String labelKey) {
            super(key, labelKey);
        }

        @Override
        public Store store() {
            return Store.GAME;
        }
    }

    /** An on-and-off option of the game's own. */
    public abstract static class GameSwitch extends GameSetting {
        private final boolean shipped;

        protected GameSwitch(String key, String labelKey, boolean shipped) {
            super(key, labelKey);
            this.shipped = shipped;
        }

        protected abstract boolean get(GameSettings options);

        protected abstract void set(GameSettings options, boolean on);

        @Override
        public String value() {
            return onOff(get(game()));
        }

        @Override
        public void step(boolean back) {
            set(game(), !get(game()));
        }

        @Override
        public void restore() {
            set(game(), this.shipped);
        }
    }

    /** A share of the game's own, a tenth to the whole in steps of a tenth. */
    public abstract static class GamePercent extends GameSetting {
        private final float shipped;

        protected GamePercent(String key, String labelKey, float shipped) {
            super(key, labelKey);
            this.shipped = shipped;
        }

        protected abstract float get(GameSettings options);

        protected abstract void set(GameSettings options, float share);

        @Override
        public String value() {
            return StatCollector.translateToLocalFormatted(
                    "gui.losttales.window.settings.percent",
                    Integer.valueOf(percentOf(get(game()))));
        }

        @Override
        public void step(boolean back) {
            int percent = percentOf(get(game()));
            int next = back ? percent - PERCENT_STEP : percent + PERCENT_STEP;
            if (next > 100) {
                next = PERCENT_STEP;
            } else if (next < PERCENT_STEP) {
                next = 100;
            }
            set(game(), next / 100.0F);
        }

        @Override
        public void restore() {
            set(game(), this.shipped);
        }
    }

    /**
     * A colour of the palette the mod's file keeps for one surface: its row
     * shows the colour it comes to now as a chip beside the colour's name,
     * and opens the palette beside the window. A colour may also have an
     * automatic choice, a colour worked out from another.
     */
    public abstract static class Colour extends ModSetting {
        protected Colour(String key, String labelKey) {
            super(key, labelKey);
        }

        /** The palette entry's name the file holds, or the automatic word. */
        protected abstract String current();

        protected abstract void set(String name);

        /** The colour it comes to now, for its chip. */
        protected int chipRgb() {
            return LostTalesColors.rgb(LostTalesColors.paletteColor(current(),
                    LostTalesColors.PLUM_BLACK));
        }

        /** The automatic choice's colour now; -1 for a colour with no automatic choice. */
        protected int automaticRgb() {
            return -1;
        }

        boolean isAutomatic() {
            return automaticRgb() >= 0
                    && !LostTalesColors.isPaletteName(current());
        }

        @Override
        public String value() {
            return isAutomatic() ? StatCollector.translateToLocal(
                    "gui.losttales.window.settings.color.automatic")
                    : paletteLabel(current());
        }

        /** A colour is chosen in its palette, not stepped. */
        @Override
        public void step(boolean back) {}

        @Override
        public void restore() {
            set(shipped());
        }

        @Override
        public MenuWindow.Entry row() {
            return super.row().withValueChip(chipRgb());
        }
    }

    /**
     * One section of Settings: its heading, the settings it holds and any
     * rows of its own — a channel's switches, the people ignored.
     */
    public abstract static class Section {
        /** The heading's lang key. */
        public abstract String titleKey();

        /** The settings it holds, which Restore Defaults puts back. */
        public List<Setting> settings() {
            return new ArrayList<Setting>();
        }

        /** Its rows: its settings', then any of its own. */
        public List<MenuWindow.Entry> rows() {
            List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
            for (Setting setting : settings()) {
                rows.add(setting.row());
            }
            return rows;
        }

        /** A row of its own taken; answers whether it was one of its own. */
        public boolean take(MenuWindow.Entry entry, boolean back) {
            return false;
        }

        /** Anything changed in Settings and was saved: what follows every change. */
        public void changed() {}
    }

    /**
     * A row that does something rather than hold a value: opening a
     * screen or a page. One that cannot act here stands greyed and says
     * why under the pointer.
     */
    public abstract static class Action {
        private final String id;
        private final String labelKey;

        protected Action(String id, String labelKey) {
            this.id = id;
            this.labelKey = labelKey;
        }

        /** Why it cannot act now; empty where it can. */
        protected String unavailable() {
            return "";
        }

        /** What a click on it does. */
        protected abstract void run();

        MenuWindow.Entry row() {
            return new MenuWindow.Entry(ACTION_PREFIX + this.id,
                    StatCollector.translateToLocal(this.labelKey))
                    .unavailable(unavailable());
        }
    }

    /**
     * A section whose rows are laid out once, as it is made: settings,
     * rows that act, and small headings over the groups they stand in.
     */
    public abstract static class GroupedSection extends Section {
        private final List<Object> items = new ArrayList<Object>();
        private final List<Setting> held = new ArrayList<Setting>();

        /** A small heading over the rows added after it, until the next. */
        protected final void group(String labelKey) {
            this.items.add(new Heading(labelKey));
        }

        /** A setting's row; a setting that could not be made (null) is left out. */
        protected final void add(Setting setting) {
            if (setting != null) {
                this.items.add(setting);
                this.held.add(setting);
            }
        }

        /** A row that acts. */
        protected final void add(Action action) {
            if (action != null) {
                this.items.add(action);
            }
        }

        @Override
        public List<Setting> settings() {
            return Collections.unmodifiableList(this.held);
        }

        @Override
        public List<MenuWindow.Entry> rows() {
            List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>(
                    this.items.size());
            for (Object item : this.items) {
                if (item instanceof Heading) {
                    rows.add(MenuWindow.Entry.group(StatCollector
                            .translateToLocal(((Heading)item).labelKey),
                            null, -1));
                } else if (item instanceof Setting) {
                    rows.add(((Setting)item).row());
                } else if (item instanceof Action) {
                    rows.add(((Action)item).row());
                }
            }
            return rows;
        }

        /** A row that acts, taken by a click; a right-click leaves it. */
        @Override
        public boolean take(MenuWindow.Entry entry, boolean back) {
            for (Object item : this.items) {
                if (item instanceof Action && entry.id.equals(
                        ACTION_PREFIX + ((Action)item).id)) {
                    if (!back && ((Action)item).unavailable().length() == 0) {
                        ((Action)item).run();
                    }
                    return true;
                }
            }
            return false;
        }
    }

    /** A small heading inside a section. */
    private static final class Heading {
        final String labelKey;

        Heading(String labelKey) {
            this.labelKey = labelKey;
        }
    }

    private final WindowMenus menus;
    private final List<Section> sections = new ArrayList<Section>();
    /** Where each section stands, by the section. */
    private final Map<Section, Place> places =
            new IdentityHashMap<Section, Place>();
    /** The places whose Restore Defaults is asking before it restores. */
    private final Set<Place> confirmingRestore = EnumSet.noneOf(Place.class);

    Settings(WindowMenus menus) {
        this.menus = menus;
        addSection(Place.WINDOWS, new WindowsSection());
        addSection(Place.CLIENT, new ShortcutsSection());
        for (Sections system : SYSTEMS) {
            system.addTo(this);
        }
        menus.register(SubWindowKind.SETTINGS, new SettingsSource());
        menus.register(SubWindowKind.PALETTE, new PaletteSource());
        menus.register(SubWindowKind.SETTING_VALUE, new ValueSource());
        menus.register(SubWindowKind.SETTING_WORDS, new WordsSource());
    }

    /** Gives every Settings made from now on a system's sections, after those already given. */
    public static void addSections(Sections sections) {
        if (sections != null && !SYSTEMS.contains(sections)) {
            SYSTEMS.add(sections);
        }
    }

    /**
     * Adds a system's section to {@code place}, after those already
     * there; Defaults stays last. A second section under a heading already
     * there is left out.
     */
    public void addSection(Place place, Section section) {
        if (place == null || section == null
                || this.sections.contains(section)) {
            return;
        }
        for (Section held : this.sections) {
            if (held.titleKey().equals(section.titleKey())) {
                return;
            }
        }
        this.sections.add(section);
        this.places.put(section, place);
    }

    /** The sections of {@code place}, in the order they were added. */
    private List<Section> sectionsOf(Place place) {
        List<Section> of = new ArrayList<Section>();
        for (Section section : this.sections) {
            if (this.places.get(section) == place) {
                of.add(section);
            }
        }
        return of;
    }

    /**
     * The settings of a place other than the Client Settings page, in a
     * sub-window opened at {@code first} — beside the menu whose row asked
     * for them; a switch.
     */
    void toggle(Place place, WindowMenus.FirstPlace first) {
        this.menus.show(SubWindowKind.SETTINGS, place, first, true);
    }

    /** A colour's palette at {@code first}, as a colour's row opens it; a switch. */
    void openPalette(Setting setting, WindowMenus.FirstPlace first) {
        if (setting instanceof Colour) {
            this.menus.show(SubWindowKind.PALETTE, setting.key, first, true);
        }
    }

    /**
     * Opens the field a page's own number or line is typed into at
     * {@code place}, as Settings opens one of its own: the page's value
     * is handed over itself, kept where the page keeps it
     * ({@link Store#NONE}), and the field closes once the value no
     * longer {@link Setting#stands stands}. A second press on the same
     * row puts it away.
     */
    public void openValue(Setting setting, WindowMenus.FirstPlace place) {
        if (setting instanceof Numeric || setting instanceof Line) {
            this.menus.show(SubWindowKind.SETTING_VALUE, setting, place,
                    true);
        } else if (setting != null && !setting.words().isEmpty()) {
            this.menus.show(SubWindowKind.SETTING_WORDS, setting, place,
                    true);
        }
    }

    /**
     * A place's rows as its own sub-window lists them, search aside: for
     * a menu that shows them under rows of its own, as a conversation's
     * Chat Feed Settings shows the feed's.
     */
    List<MenuWindow.Entry> placeRows(Place place) {
        return rows(place, "");
    }

    /** Whether a row is one of a place's: a setting's, an action's, or Restore Defaults. */
    static boolean isPlaceRow(MenuWindow.Entry entry) {
        return entry.id.startsWith(SETTING_PREFIX)
                || entry.id.startsWith(ACTION_PREFIX)
                || RESTORE.equals(entry.id) || RESTORE_CONFIRM.equals(entry.id);
    }

    /**
     * A row of {@code place} taken in another menu, {@code window}: done as
     * in the place's own sub-window, and what it opens — a palette, a
     * value's field, a setting's words — standing beside that menu.
     */
    void takeInPlace(Place place, MenuWindow.Entry entry, String part,
                     SubWindow window, boolean back) {
        Setting opened = take(place, entry, part, back);
        if (opened instanceof Colour) {
            this.menus.show(SubWindowKind.PALETTE, opened.key,
                    WindowMenus.besideWindow(window), true);
        } else if (opened != null && !opened.words().isEmpty()) {
            this.menus.show(SubWindowKind.SETTING_WORDS, opened,
                    WindowMenus.besideWindow(window), true);
        } else if (opened != null) {
            this.menus.show(SubWindowKind.SETTING_VALUE, opened.key,
                    WindowMenus.besideWindow(window), true);
        }
    }

    /** The headings of a place's sections, in the order they stand. */
    List<String> sectionTitleKeys(Place place) {
        List<String> keys = new ArrayList<String>();
        for (Section section : sectionsOf(place)) {
            keys.add(section.titleKey());
        }
        return keys;
    }

    /** Every setting of every section, in the order they stand. */
    List<Setting> allSettings() {
        List<Setting> all = new ArrayList<Setting>();
        for (Section section : this.sections) {
            all.addAll(section.settings());
        }
        return all;
    }

    /* ---- The Windows section ---- */

    /**
     * Every key of every page, each a row that is read, not taken; each
     * page's help lists its own ({@link PageHelp}).
     */
    private static final class ShortcutsSection extends Section {
        @Override
        public String titleKey() {
            return "gui.losttales.window.settings.section.shortcuts";
        }

        @Override
        public List<MenuWindow.Entry> rows() {
            return PageKeys.rows(PageKeys.everyArea(), "");
        }
    }

    /** The windows' three colours, then what else reaches every window. */
    private static final class WindowsSection extends Section {
        @Override
        public String titleKey() {
            return "gui.losttales.window.settings.section.windows";
        }

        @Override
        public List<Setting> settings() {
            List<Setting> windows = new ArrayList<Setting>();
            windows.add(new Colour("windowPrimaryColor",
                    optionName("windowPrimaryColor")) {
                @Override
                protected String current() {
                    return LostTalesConfig.windowPrimaryColor;
                }

                @Override
                protected void set(String name) {
                    LostTalesConfig.windowPrimaryColor = name;
                }
            });
            windows.add(new Colour("windowSecondaryColor",
                    optionName("windowSecondaryColor")) {
                @Override
                protected String current() {
                    return LostTalesConfig.windowSecondaryColor;
                }

                @Override
                protected void set(String name) {
                    LostTalesConfig.windowSecondaryColor = name;
                }
            });
            windows.add(new Colour("windowAccentColor",
                    optionName("windowAccentColor")) {
                @Override
                protected String current() {
                    return LostTalesConfig.windowAccentColor;
                }

                @Override
                protected void set(String name) {
                    LostTalesConfig.windowAccentColor = name;
                }
            });
            // Window Opacity is the game's own chat opacity underneath,
            // so the game's Chat Settings screen agrees with it.
            windows.add(new GamePercent("chatOpacity",
                    "gui.losttales.window.settings.opacity",
                    GAME_CHAT_OPACITY) {
                @Override
                protected float get(GameSettings options) {
                    return options.chatOpacity;
                }

                @Override
                protected void set(GameSettings options, float share) {
                    options.chatOpacity = share;
                }
            });
            windows.add(new ModSwitch("windowBackgroundBlur",
                    optionName("windowBackgroundBlur")) {
                @Override
                protected boolean get() {
                    return LostTalesConfig.windowBackgroundBlur;
                }

                @Override
                protected void set(boolean on) {
                    LostTalesConfig.windowBackgroundBlur = on;
                }
            });
            windows.add(new ModSwitch("hideHudWithWindows",
                    optionName("hideHudWithWindows")) {
                @Override
                protected boolean get() {
                    return LostTalesConfig.hideHudWithWindows;
                }

                @Override
                protected void set(boolean on) {
                    LostTalesConfig.hideHudWithWindows = on;
                }
            });
            windows.add(new Numeric("pinnedWindowOpacity",
                    optionName("pinnedWindowOpacity"), 5.0D, 0) {
                @Override
                protected double get() {
                    return LostTalesConfig.pinnedWindowOpacity;
                }

                @Override
                protected void set(double value) {
                    LostTalesConfig.pinnedWindowOpacity =
                            (int)Math.round(value);
                }
            });
            return windows;
        }
    }

    /* ---- The rows ---- */

    /**
     * A place's rows for what has been typed into its search: every row
     * whose name, value, group or section holds the words, a group kept
     * whole where its name holds them, a section's header over what is
     * left of it, and a line saying so where nothing is.
     */
    List<MenuWindow.Entry> rows(Place place, String filter) {
        String wanted = filter == null ? ""
                : filter.trim().toLowerCase(Locale.ROOT);
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        for (Section section : sectionsOf(place)) {
            section(rows, StatCollector.translateToLocal(section.titleKey()),
                    section.rows(), wanted);
        }
        section(rows, StatCollector.translateToLocal(
                "gui.losttales.window.settings.section.defaults"),
                restoreRows(place), wanted);
        if (rows.isEmpty()) {
            rows.add(MenuWindow.Entry.passive(StatCollector.translateToLocal(
                    "gui.losttales.window.settings.none")));
        }
        return rows;
    }

    /**
     * Adds a section's header and the rows of it the search keeps: all
     * of them where the section's own name holds the words; else, of each
     * group, all of it where its name holds them, or the rows that do
     * under the group's name.
     */
    static void section(List<MenuWindow.Entry> rows, String title,
                        List<MenuWindow.Entry> members, String wanted) {
        List<MenuWindow.Entry> kept = new ArrayList<MenuWindow.Entry>();
        boolean whole = wanted.length() == 0 || holds(title, wanted);
        MenuWindow.Entry group = null;
        boolean groupWhole = false;
        boolean groupShown = false;
        for (MenuWindow.Entry member : members) {
            if (member.group) {
                group = member;
                groupWhole = holds(member.label, wanted);
                groupShown = false;
                if (whole || groupWhole) {
                    kept.add(member);
                    groupShown = true;
                }
                continue;
            }
            if (whole || groupWhole || holds(member.label + " "
                    + member.value + " " + keyWords(member.keys), wanted)) {
                if (group != null && !groupShown) {
                    kept.add(group);
                    groupShown = true;
                }
                kept.add(member);
            }
        }
        if (kept.isEmpty()) {
            return;
        }
        rows.add(MenuWindow.Entry.header(title));
        rows.addAll(kept);
    }

    private static boolean holds(String text, String wanted) {
        return wanted.length() == 0
                || text.toLowerCase(Locale.ROOT).contains(wanted);
    }

    /** A shortcut's keys as words the search finds them by, typed text without its italics. */
    static String keyWords(Object[] keys) {
        StringBuilder words = new StringBuilder();
        for (Object part : keys) {
            words.append(' ').append(part instanceof Integer
                    ? WindowKeys.keyName(((Integer)part).intValue())
                    : EnumChatFormatting.getTextWithoutFormattingCodes(
                            String.valueOf(part)));
        }
        return words.toString();
    }

    /** A place's Restore Defaults, or the question it asks in its own place. */
    private List<MenuWindow.Entry> restoreRows(Place place) {
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>(1);
        rows.add((this.confirmingRestore.contains(place)
                ? new MenuWindow.Entry(RESTORE_CONFIRM,
                        StatCollector.translateToLocal(
                                "gui.losttales.window.settings.restore.confirm"))
                : new MenuWindow.Entry(RESTORE, StatCollector.translateToLocal(
                        "gui.losttales.window.settings.restore")))
                .withSprite(LostTalesUiSheet.RESET,
                        LostTalesUiSheet.RESET_DISCARD, false));
        return rows;
    }

    /* ---- Taking a row ---- */

    /**
     * A row of {@code place} taken: a click, or with {@code back} a
     * right-click, which steps a setting back; on a number's row,
     * {@code part} says which of its chevrons or its value the press
     * landed on. Answers the setting whose own window a row asks to be
     * opened beside it — a colour's palette, the field a number or a line
     * is typed into — or null.
     */
    Setting take(Place place, MenuWindow.Entry entry, String part,
                 boolean back) {
        String id = entry.id;
        if (!RESTORE.equals(id)) {
            this.confirmingRestore.remove(place);
        }
        if (id.startsWith(SETTING_PREFIX)) {
            Setting setting = find(id.substring(SETTING_PREFIX.length()));
            if (setting instanceof Colour || setting instanceof Line
                    || (setting != null && !setting.words().isEmpty())) {
                return back ? null : setting;
            }
            if (setting instanceof Numeric) {
                return takeNumber((Numeric)setting, part, back);
            }
            if (setting != null) {
                setting.step(back);
                applied(setting);
            }
            return null;
        }
        if (RESTORE.equals(id)) {
            if (back) {
                this.confirmingRestore.remove(place);
            } else {
                this.confirmingRestore.add(place);
            }
            return null;
        }
        if (RESTORE_CONFIRM.equals(id)) {
            this.confirmingRestore.remove(place);
            if (!back) {
                restoreAll(place);
            }
            return null;
        }
        for (Section section : sectionsOf(place)) {
            if (section.take(entry, back)) {
                changed();
                return null;
            }
        }
        return null;
    }

    /**
     * A press on a number's row: a chevron steps its own way, ten steps
     * with Shift; a click on the value asks for its field; anywhere else
     * a click steps it on and a right-click back.
     */
    private Setting takeNumber(Numeric number, String part, boolean back) {
        if (MenuWindow.PART_VALUE.equals(part) && !back) {
            return number;
        }
        boolean up = MenuWindow.PART_MORE.equals(part)
                || !MenuWindow.PART_LESS.equals(part) && !back;
        if (number.move(up, GuiScreen.isShiftKeyDown())) {
            applied(number);
        }
        return null;
    }

    private Setting find(String key) {
        for (Section section : this.sections) {
            for (Setting setting : section.settings()) {
                if (setting.key.equals(key)) {
                    return setting;
                }
            }
        }
        return null;
    }

    /** Every setting of a place's sections back as the mod and the game ship it. */
    private void restoreAll(Place place) {
        Set<Store> stores = EnumSet.noneOf(Store.class);
        for (Section section : sectionsOf(place)) {
            for (Setting setting : section.settings()) {
                setting.restore();
                setting.changed();
                stores.add(setting.store());
            }
        }
        for (Store store : stores) {
            save(store);
        }
        changed();
    }

    /**
     * Writes what changed where it is kept — the mod's client file, the
     * camera's file, or the game's options, which tell the server the
     * chat's visibility — and lets it and every section follow.
     */
    private void applied(Setting setting) {
        save(setting.store());
        setting.changed();
        if (setting.store() != Store.NONE) {
            changed();
        }
    }

    private void changed() {
        for (Section section : this.sections) {
            section.changed();
        }
    }

    private static void save(Store store) {
        switch (store) {
            case CAMERA_FILE:
                LostTalesThirdPersonConfig.save();
                break;
            case GAME:
                saveGame();
                break;
            case NONE:
                break;
            default:
                LostTalesConfig.save();
                break;
        }
    }

    private static void saveGame() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.gameSettings != null) {
            minecraft.gameSettings.saveOptions();
        }
    }

    /* ---- The palette ---- */

    /** A palette entry's name as the language file gives it. */
    static String paletteLabel(String name) {
        String key = "losttales.palette." + name.toLowerCase(Locale.ROOT);
        String label = StatCollector.translateToLocal(key);
        return key.equals(label) ? name : label;
    }

    /**
     * The palette for one colour: every entry as a chip beside its name,
     * the one in use named in the accent and the one the mod ships marked as
     * the default — for a colour with an automatic choice, that choice
     * before them. Choosing one is the whole change: the option is written
     * to the client file and every window is drawn in it from the next
     * frame, and the palette stays for the next.
     */
    static List<MenuWindow.Entry> paletteRows(Colour colour) {
        String current = colour.current();
        String shipped = colour.shipped();
        String[] names = LostTalesColors.paletteNames();
        List<MenuWindow.Entry> entries =
                new ArrayList<MenuWindow.Entry>(names.length + 1);
        if (colour.automaticRgb() >= 0) {
            MenuWindow.Entry automatic = new MenuWindow.Entry(
                    PALETTE_PREFIX + LostTalesConfig.CHAT_COLOR_AUTOMATIC,
                    defaultLabel(StatCollector.translateToLocal(
                            "gui.losttales.window.settings.color.automatic")),
                    false, colour.automaticRgb(), null).asChip();
            automatic.chosen(colour.isAutomatic());
            entries.add(automatic);
        }
        for (String name : names) {
            String label = paletteLabel(name);
            MenuWindow.Entry entry = new MenuWindow.Entry(
                    PALETTE_PREFIX + name,
                    name.equalsIgnoreCase(shipped) ? defaultLabel(label) : label,
                    false, LostTalesColors.rgb(LostTalesColors.paletteColor(
                            name, LostTalesColors.PLUM_BLACK)),
                    null).asChip();
            entry.chosen(name.equalsIgnoreCase(current));
            entries.add(entry);
        }
        return entries;
    }

    private static String defaultLabel(String label) {
        return StatCollector.translateToLocalFormatted(
                "gui.losttales.window.settings.color.default", label);
    }

    /** One row of the palette: the chosen colour becomes the surface's. */
    private void choose(Colour colour, MenuWindow.Entry entry) {
        if (!entry.id.startsWith(PALETTE_PREFIX)) {
            return;
        }
        String name = entry.id.substring(PALETTE_PREFIX.length());
        boolean automatic = LostTalesConfig.CHAT_COLOR_AUTOMATIC.equals(name)
                && colour.automaticRgb() >= 0;
        if (!automatic && !LostTalesColors.isPaletteName(name)) {
            return;
        }
        colour.set(name);
        applied(colour);
    }

    /* ---- The sources ---- */

    /** A place's settings in a sub-window, about the place: its search, its sections, Defaults. */
    private final class SettingsSource extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            return menu.about() instanceof Place;
        }

        @Override
        public void prepare(MenuWindow menu) {
            menu.openField(StatCollector.translateToLocal(
                            "gui.losttales.window.settings.search"),
                    null, LostTalesUiSheet.SEARCH,
                    MenuWindow.MAX_FILTER_LENGTH, false);
            Settings.this.confirmingRestore.remove(menu.about());
        }

        @Override
        public void rebuild(MenuWindow menu) {
            Place place = (Place)menu.about();
            menu.setTitle(StatCollector.translateToLocal(place.titleKey),
                    LostTalesUiSheet.COG);
            menu.setRows(rows(place, menu.filter()));
        }

        @Override
        public boolean readsAsTyped() {
            return true;
        }

        @Override
        public MenuWindow.Entry firstFound(MenuWindow menu) {
            return WindowMenus.firstTyped(menu);
        }

        @Override
        public boolean takesBack() {
            return true;
        }

        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            return act(menu, entry, null, window, back);
        }

        /**
         * Every row leaves Settings standing; a colour's opens its palette
         * beside it, and a number's value or a line the field it is typed
         * into.
         */
        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           String part, SubWindow window, boolean back) {
            takeInPlace((Place)menu.about(), entry, part, window, back);
            return true;
        }
    }

    /** A colour's palette, about the colour's key. */
    private final class PaletteSource extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            return colourOf(menu) != null;
        }

        @Override
        public void rebuild(MenuWindow menu) {
            Colour colour = colourOf(menu);
            menu.setTitle(colour.label(), null);
            menu.setRows(paletteRows(colour));
        }

        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            choose(colourOf(menu), entry);
            Settings.this.menus.rebuildIfOpen(SubWindowKind.SETTINGS);
            return true;
        }

        private Colour colourOf(MenuWindow menu) {
            Setting setting = menu.about() instanceof String
                    ? find((String)menu.about()) : null;
            return setting instanceof Colour ? (Colour)setting : null;
        }
    }

    /**
     * A few-word setting's words, about the setting itself, named as its
     * row is: each word a row, the one standing marked in the accent and the
     * one shipped marked as the default. A word taken stands at once, and
     * the window stays for another try, as a page option's words do. It
     * closes once the setting no longer stands.
     */
    private final class WordsSource extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            return menu.about() instanceof Setting
                    && ((Setting)menu.about()).stands();
        }

        @Override
        public void rebuild(MenuWindow menu) {
            Setting setting = (Setting)menu.about();
            menu.setTitle(setting.label(), null);
            List<String> words = setting.words();
            List<MenuWindow.Entry> rows =
                    new ArrayList<MenuWindow.Entry>(words.size());
            for (int index = 0; index < words.size(); index++) {
                String label = index == setting.shippedWordIndex()
                        ? defaultLabel(words.get(index)) : words.get(index);
                MenuWindow.Entry row = new MenuWindow.Entry(
                        WORD_PREFIX + index, label);
                rows.add(row.chosen(index == setting.wordIndex()));
            }
            menu.setRows(rows);
        }

        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            Setting setting = (Setting)menu.about();
            if (!back && entry.id.startsWith(WORD_PREFIX)) {
                setting.pickWord(Integer.parseInt(
                        entry.id.substring(WORD_PREFIX.length())));
                applied(setting);
                Settings.this.menus.rebuildIfOpen(SubWindowKind.SETTINGS);
            }
            return true;
        }
    }

    /**
     * The field a number or a line is typed into, about the setting's
     * key, or about a page's own setting handed over itself: it opens
     * holding the value, chosen whole so typing replaces it, and takes
     * only what the setting can hold. Enter or Keep takes what is typed,
     * a number only once it lies within its bounds and a line that may
     * not be empty only once something is typed; Use the Default puts
     * back what the mod ships, Clear empties a line that may be empty,
     * and a name the line offers is taken as it is. Each closes the
     * window; closing it otherwise leaves the value as it was.
     */
    private final class ValueSource extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            Setting setting = settingOf(menu);
            return setting != null && setting.stands();
        }

        @Override
        public void prepare(MenuWindow menu) {
            Setting setting = settingOf(menu);
            if (setting instanceof Numeric) {
                Numeric number = (Numeric)setting;
                final NumberStepper stepper = number.stepper();
                menu.openField(number.bounds() == null ? number.label()
                                : StatCollector.translateToLocalFormatted(
                                        "gui.losttales.window.settings.value.range",
                                        stepper.format(stepper.min),
                                        stepper.format(stepper.max)),
                        null, LostTalesUiSheet.DRAFT,
                        NumberStepper.MAX_TYPED_LENGTH, false);
                menu.setFieldFilter(new MenuWindow.FieldFilter() {
                    @Override
                    public boolean accepts(String text) {
                        return stepper.mayBecome(text);
                    }
                });
                menu.setFilter(stepper.format(((Numeric)setting).get()));
            } else if (setting instanceof Line) {
                Line line = (Line)setting;
                menu.openField(line.prompt(), null, LostTalesUiSheet.DRAFT,
                        line.maxLength(), false);
                menu.setFilter(line.get());
            }
            menu.selectField();
        }

        @Override
        public void rebuild(MenuWindow menu) {
            Setting setting = settingOf(menu);
            menu.setTitle(setting.label(), null);
            List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>(2);
            MenuWindow.Entry keep = new MenuWindow.Entry(VALUE_KEEP,
                    StatCollector.translateToLocal(
                            "gui.losttales.window.settings.value.keep"));
            rows.add(keep);
            if (setting instanceof Numeric) {
                Numeric number = (Numeric)setting;
                NumberStepper stepper = number.stepper();
                if (stepper.parse(menu.filter()) == null) {
                    keep.unavailable(number.bounds() == null
                            ? StatCollector.translateToLocal(
                                    "gui.losttales.window.settings.value.type_number")
                            : typeTip(stepper));
                }
                double shipped = number.shippedNumber();
                if (!Double.isNaN(shipped)) {
                    rows.add(new MenuWindow.Entry(VALUE_DEFAULT,
                            StatCollector.translateToLocal(
                                    "gui.losttales.window.settings.value.default"))
                            .withValue(stepper.format(stepper.clamp(shipped))));
                }
            } else {
                Line line = (Line)setting;
                if (!line.mayBeEmpty()
                        && menu.filter().trim().length() == 0) {
                    keep.unavailable(StatCollector.translateToLocal(
                            "gui.losttales.window.settings.value.required"));
                }
                if (line.mayBeEmpty() && line.get() != null
                        && line.get().length() > 0) {
                    rows.add(new MenuWindow.Entry(VALUE_CLEAR,
                            StatCollector.translateToLocal(
                                    "gui.losttales.window.settings.value.clear")));
                }
                for (String offer : line.offers(menu.filter())) {
                    rows.add(new MenuWindow.Entry(VALUE_OFFER_PREFIX + offer,
                            offer));
                }
            }
            menu.setRows(rows);
        }

        @Override
        public boolean readsAsTyped() {
            return true;
        }

        /** Enter keeps what is typed, once it can be kept. */
        @Override
        public MenuWindow.Entry firstFound(MenuWindow menu) {
            for (MenuWindow.Entry entry : menu.entries()) {
                if (VALUE_KEEP.equals(entry.id)) {
                    return entry.isTakeable() ? entry : null;
                }
            }
            return null;
        }

        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            Setting setting = settingOf(menu);
            if (back) {
                return true;
            }
            if (VALUE_KEEP.equals(entry.id)) {
                if (setting instanceof Numeric) {
                    if (!((Numeric)setting).take(menu.filter())) {
                        return true;
                    }
                } else {
                    String typed = menu.filter().trim();
                    if (typed.length() == 0
                            && !((Line)setting).mayBeEmpty()) {
                        return true;
                    }
                    ((Line)setting).set(typed);
                }
            } else if (VALUE_DEFAULT.equals(entry.id)) {
                setting.restore();
            } else if (VALUE_CLEAR.equals(entry.id)
                    && setting instanceof Line) {
                ((Line)setting).set("");
            } else if (entry.id.startsWith(VALUE_OFFER_PREFIX)
                    && setting instanceof Line) {
                ((Line)setting).set(entry.id.substring(
                        VALUE_OFFER_PREFIX.length()));
            } else {
                return true;
            }
            applied(setting);
            Settings.this.menus.rebuildIfOpen(SubWindowKind.SETTINGS);
            return false;
        }

        private Setting settingOf(MenuWindow menu) {
            Object about = menu.about();
            Setting setting = about instanceof Setting ? (Setting)about
                    : about instanceof String ? find((String)about) : null;
            return setting instanceof Numeric || setting instanceof Line
                    ? setting : null;
        }
    }

    /* ---- Small helpers ---- */

    private static GameSettings game() {
        return Minecraft.getMinecraft().gameSettings;
    }

    /** On or Off, as every switch reads. */
    public static String onOff(boolean on) {
        return StatCollector.translateToLocal(on
                ? "gui.losttales.window.settings.on"
                : "gui.losttales.window.settings.off");
    }

    /** What a number's value says under the pointer, and why a typed one cannot be kept. */
    static String typeTip(NumberStepper stepper) {
        return StatCollector.translateToLocalFormatted(
                "gui.losttales.window.settings.value.type",
                stepper.format(stepper.min), stepper.format(stepper.max));
    }

    /** A number as its option's file writes it; NaN for words that are none. */
    public static double parseNumber(String text) {
        if (text == null || text.trim().length() == 0) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException unreadable) {
            return Double.NaN;
        }
    }

    /** A share as the whole percent a tenth's step lands on. */
    static int percentOf(float share) {
        int percent = Math.round(share * 100.0F / PERCENT_STEP) * PERCENT_STEP;
        return Math.max(PERCENT_STEP, Math.min(100, percent));
    }

    /** The index a step lands on among {@code count}, round from either end. */
    public static int nextIndex(int index, int count, boolean back) {
        if (count <= 0) {
            return 0;
        }
        int start = index < 0 ? (back ? 0 : count - 1) : index;
        return ((back ? start - 1 : start + 1) % count + count) % count;
    }

    private static int indexOf(String[] words, String word) {
        for (int index = 0; index < words.length; index++) {
            if (words[index].equalsIgnoreCase(word)) {
                return index;
            }
        }
        return -1;
    }
}
