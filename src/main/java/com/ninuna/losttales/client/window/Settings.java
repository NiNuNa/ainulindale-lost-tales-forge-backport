package com.ninuna.losttales.client.window;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * Settings: every option in one sub-window, in sections under a search
 * field — the window system's own Windows section first, what reaches
 * every window, then each system's sections in the order its part added
 * them, and Defaults last. Each option takes effect and is saved the
 * moment it changes: a switch flips on a click, a few-word option steps
 * forward on a click and back on a right-click, a colour opens the
 * palette beside the window. The game's own options read here are
 * written to the game's options, so its own screens and this window
 * always agree. Restore Defaults puts every setting of every section back
 * as the mod and the game ship it, after asking in its own place. A row
 * is acted on by its id, so a list read again as it is typed into acts
 * the same.
 */
public final class Settings {
    /** Marks a setting's row; the rest of the id is its key. */
    private static final String SETTING_PREFIX = "setting:";
    private static final String RESTORE = "restore";
    private static final String RESTORE_CONFIRM = "restore_confirm";
    /** Marks a palette row; the rest of the id is the palette entry's name. */
    private static final String PALETTE_PREFIX = "palette:";
    /** The steps a share takes here: a tenth to the whole. */
    private static final int PERCENT_STEP = 10;
    /** The game's chat opacity as it ships, which Restore Defaults puts back. */
    private static final float GAME_CHAT_OPACITY = 1.0F;

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

        /** Whether it is kept in the game's options rather than the mod's file. */
        boolean isGame() {
            return false;
        }

        /** What else follows it once it changed and was saved: a scale lays lines out again. */
        public void changed() {}

        /** Its row: its name and what it reads now. */
        MenuWindow.Entry row() {
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

    /** An option of the game's own, saved to its options: one that words itself. */
    public abstract static class GameSetting extends Setting {
        protected GameSetting(String key, String labelKey) {
            super(key, labelKey);
        }

        @Override
        boolean isGame() {
            return true;
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
        MenuWindow.Entry row() {
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

    private final WindowMenus menus;
    private final List<Section> sections = new ArrayList<Section>();
    /** Whether Restore Defaults is asking before it restores. */
    private boolean confirmingRestore;

    Settings(WindowMenus menus) {
        this.menus = menus;
        this.sections.add(new WindowsSection());
        menus.register(SubWindowKind.SETTINGS, new SettingsSource());
        menus.register(SubWindowKind.PALETTE, new PaletteSource());
    }

    /** Adds a system's section after those already there; Defaults stays last. */
    public void addSection(Section section) {
        if (section != null && !this.sections.contains(section)) {
            this.sections.add(section);
        }
    }

    /** Settings, opened beside a sub-window or in the middle of a window; a switch. */
    void toggle(WindowMenus.FirstPlace place) {
        this.menus.show(SubWindowKind.SETTINGS, null, place, true);
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
     * steps a few-word setting back. Answers the colour whose palette a
     * colour row asks to be opened beside the window, or null.
     */
    private Colour take(MenuWindow.Entry entry, boolean back) {
        String id = entry.id;
        if (!RESTORE.equals(id)) {
            this.confirmingRestore = false;
        }
        if (id.startsWith(SETTING_PREFIX)) {
            Setting setting = find(id.substring(SETTING_PREFIX.length()));
            if (setting instanceof Colour) {
                return back ? null : (Colour)setting;
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
        for (Section section : this.sections) {
            for (Setting setting : section.settings()) {
                setting.restore();
                setting.changed();
            }
        }
        LostTalesConfig.save();
        saveGame();
        changed();
    }

    /**
     * Writes what changed where it is kept — the mod's client file, or the
     * game's options, which tell the server the chat's visibility — and
     * lets it and every section follow.
     */
    private void applied(Setting setting) {
        if (setting.isGame()) {
            saveGame();
        } else {
            LostTalesConfig.save();
        }
        setting.changed();
        changed();
    }

    private void changed() {
        for (Section section : this.sections) {
            section.changed();
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

        /** Every row leaves Settings standing; a colour's opens its palette beside it. */
        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            Colour colour = take(entry, back);
            if (colour != null) {
                Settings.this.menus.show(SubWindowKind.PALETTE, colour.key,
                        WindowMenus.besideWindow(window), true);
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
