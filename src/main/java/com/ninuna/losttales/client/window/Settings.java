package com.ninuna.losttales.client.window;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * Settings: every option in one sub-window, in sections under a search
 * field — the window system's own Windows section first, what reaches
 * every window, then each system's sections in the order it added them,
 * and Defaults last. Each option takes effect and is saved the moment it
 * changes: a switch flips on a click, a few-word option steps forward on
 * a click and back on a right-click, a number steps with the chevrons
 * beside it and is typed into where its value is clicked, a colour opens
 * the palette beside the window. The game's own options read here are
 * written to the game's options, so its own screens and this window
 * always agree. Restore Defaults puts every setting of every section back
 * as the mod and the game ship it, after asking in its own place. A row
 * is acted on by its id, so a list read again as it is typed into acts
 * the same.
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

    /** Every system's sections, in the order the systems gave them. */
    private static final List<Sections> SYSTEMS =
            new CopyOnWriteArrayList<Sections>();

    /* ---- The kinds of setting ---- */

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

        /** The words the row reads for the value now. */
        public abstract String value();

        /** A click steps it on; with {@code back}, a right-click, back. */
        public abstract void step(boolean back);

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
            return StatCollector.translateToLocal(this.wordKeyPrefix
                    + get().toLowerCase(Locale.ROOT));
        }

        @Override
        public void step(boolean back) {
            set(this.words[nextIndex(indexOf(this.words, get()),
                    this.words.length, back)]);
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

        @Override
        public String value() {
            String text = get() == null ? "" : get().trim();
            if (text.length() == 0) {
                return StatCollector.translateToLocal(
                        "gui.losttales.window.settings.value.none");
            }
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
    /** Whether Restore Defaults is asking before it restores. */
    private boolean confirmingRestore;

    Settings(WindowMenus menus) {
        this.menus = menus;
        this.sections.add(new WindowsSection());
        for (Sections system : SYSTEMS) {
            system.addTo(this);
        }
        menus.register(SubWindowKind.SETTINGS, new SettingsSource());
        menus.register(SubWindowKind.PALETTE, new PaletteSource());
        menus.register(SubWindowKind.SETTING_VALUE, new ValueSource());
    }

    /** Gives every Settings made from now on a system's sections, after those already given. */
    public static void addSections(Sections sections) {
        if (sections != null && !SYSTEMS.contains(sections)) {
            SYSTEMS.add(sections);
        }
    }

    /**
     * Adds a system's section after those already there; Defaults stays
     * last. A second section under a heading already there is left out.
     */
    public void addSection(Section section) {
        if (section == null || this.sections.contains(section)) {
            return;
        }
        for (Section held : this.sections) {
            if (held.titleKey().equals(section.titleKey())) {
                return;
            }
        }
        this.sections.add(section);
    }

    /** Settings, opened beside a sub-window or in the middle of a window; a switch. */
    void toggle(WindowMenus.FirstPlace place) {
        this.menus.show(SubWindowKind.SETTINGS, null, place, true);
    }

    /** Settings brought in front, opened at {@code place} where it is not out. */
    void show(WindowMenus.FirstPlace place) {
        this.menus.show(SubWindowKind.SETTINGS, null, place, false);
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
        }
    }

    /** Every section's heading, in the order they stand. */
    List<String> sectionTitleKeys() {
        List<String> keys = new ArrayList<String>(this.sections.size());
        for (Section section : this.sections) {
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

    /** The windows' colour, then what else reaches every window. */
    private static final class WindowsSection extends Section {
        @Override
        public String titleKey() {
            return "gui.losttales.window.settings.section.windows";
        }

        @Override
        public List<Setting> settings() {
            List<Setting> windows = new ArrayList<Setting>();
            windows.add(new Colour("chatBackgroundColor",
                    "gui.losttales.window.settings.color.background") {
                @Override
                protected String current() {
                    return LostTalesConfig.chatBackgroundColor;
                }

                @Override
                protected void set(String name) {
                    LostTalesConfig.chatBackgroundColor = name;
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
            windows.add(new ModSwitch("enableChatBackgroundBlur",
                    "gui.losttales.window.settings.blur") {
                @Override
                protected boolean get() {
                    return LostTalesConfig.enableChatBackgroundBlur;
                }

                @Override
                protected void set(boolean on) {
                    LostTalesConfig.enableChatBackgroundBlur = on;
                }
            });
            windows.add(new ModSwitch("hideHudWhileChatting",
                    "gui.losttales.window.settings.hide_hud") {
                @Override
                protected boolean get() {
                    return LostTalesConfig.hideHudWhileChatting;
                }

                @Override
                protected void set(boolean on) {
                    LostTalesConfig.hideHudWhileChatting = on;
                }
            });
            return windows;
        }
    }

    /* ---- The rows ---- */

    /**
     * The window's rows for what has been typed into its search: every
     * row whose name, value, group or section holds the words, a group
     * kept whole where its name holds them, a section's header over what
     * is left of it, and a line saying so where nothing is.
     */
    List<MenuWindow.Entry> rows(String filter) {
        String wanted = filter == null ? ""
                : filter.trim().toLowerCase(Locale.ROOT);
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        for (Section section : this.sections) {
            section(rows, StatCollector.translateToLocal(section.titleKey()),
                    section.rows(), wanted);
        }
        section(rows, StatCollector.translateToLocal(
                "gui.losttales.window.settings.section.defaults"),
                restoreRows(), wanted);
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

    /** Restore Defaults, or the question it asks in its own place. */
    private List<MenuWindow.Entry> restoreRows() {
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>(1);
        rows.add(this.confirmingRestore
                ? new MenuWindow.Entry(RESTORE_CONFIRM,
                        StatCollector.translateToLocal(
                                "gui.losttales.window.settings.restore.confirm"))
                : new MenuWindow.Entry(RESTORE, StatCollector.translateToLocal(
                        "gui.losttales.window.settings.restore")));
        return rows;
    }

    /* ---- Taking a row ---- */

    /**
     * A row taken: a click, or with {@code back} a right-click, which
     * steps a setting back; on a number's row, {@code part} says which of
     * its chevrons or its value the press landed on. Answers the setting
     * whose own window a row asks to be opened beside Settings — a
     * colour's palette, the field a number or a line is typed into — or
     * null.
     */
    private Setting take(MenuWindow.Entry entry, String part, boolean back) {
        String id = entry.id;
        if (!RESTORE.equals(id)) {
            this.confirmingRestore = false;
        }
        if (id.startsWith(SETTING_PREFIX)) {
            Setting setting = find(id.substring(SETTING_PREFIX.length()));
            if (setting instanceof Colour || setting instanceof Line) {
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
            this.confirmingRestore = !back;
            return null;
        }
        if (RESTORE_CONFIRM.equals(id)) {
            this.confirmingRestore = false;
            if (!back) {
                restoreAll();
            }
            return null;
        }
        for (Section section : this.sections) {
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

    /** Every setting of every section back as the mod and the game ship it. */
    private void restoreAll() {
        Set<Store> stores = EnumSet.noneOf(Store.class);
        for (Section section : this.sections) {
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
     * the one in use named in honey and the one the mod ships marked as
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
            if (colour.isAutomatic()) {
                automatic.withLabelColor(
                        LostTalesColors.rgb(LostTalesColors.HONEY));
            }
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
            if (name.equalsIgnoreCase(current)) {
                entry.withLabelColor(LostTalesColors.rgb(LostTalesColors.HONEY));
            }
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

    /** Settings itself: its search, its sections, Defaults. */
    private final class SettingsSource extends WindowMenus.Source {
        @Override
        public void prepare(MenuWindow menu) {
            menu.openField(StatCollector.translateToLocal(
                            "gui.losttales.window.settings.search"),
                    WindowKeys.withCommand(Keyboard.KEY_COMMA),
                    LostTalesUiSheet.SEARCH, MenuWindow.MAX_FILTER_LENGTH,
                    false);
            Settings.this.confirmingRestore = false;
        }

        @Override
        public void rebuild(MenuWindow menu) {
            menu.setTitle(null, LostTalesUiSheet.COG);
            menu.setRowHeight(MenuWindow.TALL_ROW_HEIGHT);
            menu.setRows(rows(menu.filter()));
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
            Setting opened = take(entry, part, back);
            if (opened instanceof Colour) {
                Settings.this.menus.show(SubWindowKind.PALETTE, opened.key,
                        WindowMenus.besideWindow(window), true);
            } else if (opened != null) {
                Settings.this.menus.show(SubWindowKind.SETTING_VALUE,
                        opened.key, WindowMenus.besideWindow(window), true);
            }
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
                menu.openField(line.label(), null, LostTalesUiSheet.DRAFT,
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
