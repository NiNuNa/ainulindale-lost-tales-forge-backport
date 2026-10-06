package com.ninuna.losttales.config.client;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.NumberStepper;
import com.ninuna.losttales.client.window.PageRows;
import com.ninuna.losttales.client.window.Settings;
import com.ninuna.losttales.config.LostTalesConfigWords;
import com.ninuna.losttales.config.server.ServerConfigChangeValidator;
import com.ninuna.losttales.config.server.ServerConfigEntry;
import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.StatCollector;

/**
 * The Server Settings page's rows: a header per category, then each
 * setting as Settings shows one of its kind — a switch, a few-word
 * option stepping through its values, a number between chevrons, a
 * typed line, a secret that says only whether it is set — and each list
 * last in its category, its lines as a group with Add a Line under them.
 * A setting whose change waits shows its new value in honey, a list its
 * name and its new lines. Every setting is named, explained in its row's
 * tip and given its words by the lang file, in the player's language
 * ({@link LostTalesConfigWords}). Numbers and lines are typed in Settings'
 * own value window and kept by the page ({@link Settings.Store#NONE}).
 */
final class ServerSettingsRows {
    private static final String LANG = "gui.losttales.server_settings.";
    /** What a category's lang key starts with. */
    private static final String CATEGORY_LANG = "losttales.config.category.";
    /** The ids of the rows the page acts on itself: switches and few-word options. */
    private static final String OWN_ROW = "server:";
    /** The ids of a list's lines and of its Add a Line row. */
    private static final String ITEM_ROW = "item:";
    private static final String ADD_ROW = "add:";

    /** What the rows read from: the page. */
    interface Host {
        ServerSettingsDraft draft();

        /** Counts the snapshots the page has read: rows made for an earlier one no longer stand. */
        int generation();

        /** Whether the page still shows the snapshot {@code generation} counts, its tab open. */
        boolean stands(int generation);
    }

    private final Host host;
    /** The typed rows' settings by their row's id, made as their rows first are. */
    private final Map<String, Settings.Setting> typed =
            new HashMap<String, Settings.Setting>();
    /** The single values' typed settings by the setting's name. */
    private final Map<String, Settings.Setting> values =
            new HashMap<String, Settings.Setting>();
    /** The snapshot the typed settings were made for. */
    private int madeFor = -1;

    ServerSettingsRows(Host host) {
        this.host = host;
    }

    private ServerSettingsDraft draft() {
        return this.host.draft();
    }

    /** The setting a typed or picked row stands for, a number, a line or a few words; null for any other row. */
    Settings.Setting typedFor(String rowId) {
        return this.typed.get(rowId);
    }

    /** The setting a switch's or a few-word option's row stands for; null for any other row. */
    ServerConfigEntry entryFor(String rowId) {
        return rowId != null && rowId.startsWith(OWN_ROW)
                ? draft().find(rowId.substring(OWN_ROW.length())) : null;
    }

    /**
     * Every row for the settings as they stand on the page, with what the
     * search's {@code words} keep of them; none before a snapshot is read.
     */
    List<MenuWindow.Entry> build(String words) {
        if (this.madeFor != this.host.generation()) {
            this.typed.clear();
            this.values.clear();
            this.madeFor = this.host.generation();
        }
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        String category = null;
        List<MenuWindow.Entry> singles = new ArrayList<MenuWindow.Entry>();
        List<MenuWindow.Entry> lists = new ArrayList<MenuWindow.Entry>();
        for (ServerConfigEntry entry : draft().entries()) {
            if (!entry.getCategory().equals(category)) {
                addSection(rows, category, singles, lists, words);
                category = entry.getCategory();
                singles = new ArrayList<MenuWindow.Entry>();
                lists = new ArrayList<MenuWindow.Entry>();
            }
            if (entry.isList()) {
                addList(lists, entry);
            } else {
                singles.add(row(entry));
            }
        }
        addSection(rows, category, singles, lists, words);
        return rows;
    }

    /** A category's header over its single values, then its lists, which read as groups. */
    private static void addSection(List<MenuWindow.Entry> rows, String category,
                                   List<MenuWindow.Entry> singles,
                                   List<MenuWindow.Entry> lists, String words) {
        if (category == null) {
            return;
        }
        List<MenuWindow.Entry> members = new ArrayList<MenuWindow.Entry>(singles);
        members.addAll(lists);
        PageRows.addSection(rows, categoryName(category), members, words);
    }

    /**
     * A category's name: its lang words, under its code name or the same
     * in camel case, else its code name in words.
     */
    static String categoryName(String category) {
        String[] keys = {CATEGORY_LANG + category,
                CATEGORY_LANG + ServerSettingNames.camelCase(category)};
        for (String key : keys) {
            if (StatCollector.canTranslate(key)) {
                return StatCollector.translateToLocal(key);
            }
        }
        return ServerSettingNames.name(category);
    }

    /**
     * A setting's name: its lang line in the player's language, else its
     * key in words, as for a setting this game's lang file does not know.
     */
    static String settingName(String category, String key) {
        String langKey = LostTalesConfigWords.nameKey(category, key);
        return StatCollector.canTranslate(langKey)
                ? StatCollector.translateToLocal(langKey)
                : ServerSettingNames.name(key);
    }

    private static String settingName(ServerConfigEntry entry) {
        return settingName(entry.getCategory(), entry.getKey());
    }

    /** What a setting does, its tip line in the player's language; none where the lang file has none. */
    static String settingTip(ServerConfigEntry entry) {
        String langKey = LostTalesConfigWords.tipKey(entry.getCategory(),
                entry.getKey());
        return StatCollector.canTranslate(langKey)
                ? StatCollector.translateToLocal(langKey) : "";
    }

    /** One of a few-word setting's words in the player's language, else as the file writes it. */
    static String settingWord(ServerConfigEntry entry, String word) {
        String langKey = LostTalesConfigWords.wordKey(entry.getCategory(),
                entry.getKey(), word);
        return StatCollector.canTranslate(langKey)
                ? StatCollector.translateToLocal(langKey) : word;
    }

    /** A single value's row, by its kind. */
    private MenuWindow.Entry row(ServerConfigEntry entry) {
        String name = entry.qualifiedName();
        MenuWindow.Entry row;
        if (entry.getType() == ServerConfigEntry.Type.BOOLEAN) {
            row = new MenuWindow.Entry(OWN_ROW + name,
                    settingName(entry)).withValue(
                            Settings.onOff(isOn(draft().value(entry))));
        } else if (!entry.getValidValues().isEmpty() && !entry.isSecret()) {
            if (!this.typed.containsKey(OWN_ROW + name)) {
                this.typed.put(OWN_ROW + name, new WordsValue(name));
            }
            row = new MenuWindow.Entry(OWN_ROW + name, settingName(entry))
                    .withValue(settingWord(entry, draft().value(entry)));
        } else {
            row = typedRow(entry);
        }
        MenuWindow.Entry tipped = row.withTip(settingTip(entry));
        return draft().isChanged(entry) ? tipped.withValueColor(honey())
                : tipped;
    }

    /**
     * A list as a group under its name, honey while a change to it
     * waits: a row for each of its lines, a line the server does not
     * hold in honey, and Add a Line, greyed once the list is full.
     */
    private void addList(List<MenuWindow.Entry> rows, ServerConfigEntry entry) {
        String name = entry.qualifiedName();
        String tip = settingTip(entry);
        rows.add(MenuWindow.Entry.group(settingName(entry),
                null, draft().isChanged(entry) ? honey() : -1));
        List<String> items = draft().values(entry);
        for (int index = 0; index < items.size(); index++) {
            String id = ITEM_ROW + name + "#" + index;
            if (!this.typed.containsKey(id)) {
                this.typed.put(id, new Item(name, index));
            }
            MenuWindow.Entry item = new MenuWindow.Entry(id,
                    items.get(index)).withTip(tip);
            // A line is its own value: one the server does not hold yet
            // stands in honey whole.
            rows.add(entry.getValues().contains(items.get(index)) ? item
                    : item.withLabelColor(honey()));
        }
        String id = ADD_ROW + name;
        if (!this.typed.containsKey(id)) {
            this.typed.put(id, new Addition(name));
        }
        rows.add(new MenuWindow.Entry(id, word("add_line")).withTip(tip)
                .unavailable(draft().canAdd(entry) ? ""
                        : StatCollector.translateToLocalFormatted(
                                LANG + "add_line.full", Integer.valueOf(
                                        ServerConfigChangeValidator.MAX_LIST_ITEMS))));
    }

    /**
     * A number's or a line's row as Settings makes it, its setting kept
     * for the page to open; a secret is a line.
     */
    private MenuWindow.Entry typedRow(ServerConfigEntry entry) {
        String name = entry.qualifiedName();
        Settings.Setting setting = this.values.get(name);
        if (setting == null) {
            setting = !entry.isSecret()
                    && (entry.getType() == ServerConfigEntry.Type.INTEGER
                            || entry.getType() == ServerConfigEntry.Type.DOUBLE)
                    ? new NumberValue(name, ServerSettingsDraft.stepper(entry))
                    : new LineValue(name);
            this.values.put(name, setting);
        }
        MenuWindow.Entry row = setting.row();
        this.typed.put(row.id, setting);
        return row;
    }

    private static int honey() {
        return LostTalesColors.rgb(LostTalesColors.HONEY);
    }

    /** Whether a switch's value reads on. */
    static boolean isOn(String value) {
        return value != null && "true".equalsIgnoreCase(value.trim());
    }

    static String word(String key) {
        return StatCollector.translateToLocal(LANG + key);
    }

    /* ---- The typed settings ---- */

    /** The setting named, as it stands on the page; null once it is gone. */
    private ServerConfigEntry entry(String name) {
        return draft().find(name);
    }

    /**
     * A line of words, typed in Settings' value window: a setting's value,
     * or a secret, which the window never shows and an empty value leaves
     * as it is.
     */
    private final class LineValue extends Settings.Line {
        private final String name;
        private final int made = ServerSettingsRows.this.host.generation();

        LineValue(String name) {
            super(name, "");
            this.name = name;
        }

        @Override
        public String label() {
            ServerConfigEntry entry = entry(this.name);
            return entry == null ? "" : settingName(entry);
        }

        @Override
        public String value() {
            ServerConfigEntry entry = entry(this.name);
            if (entry != null && entry.isSecret()) {
                return word(draft().isChanged(entry) || entry.isSecretSet()
                        ? "set" : "not_set");
            }
            return super.value();
        }

        @Override
        protected String get() {
            ServerConfigEntry entry = entry(this.name);
            return entry == null || entry.isSecret() ? "" : draft().value(entry);
        }

        @Override
        protected void set(String text) {
            draft().set(entry(this.name), text);
        }

        @Override
        protected int maxLength() {
            return ServerConfigChangeValidator.MAX_VALUE_LENGTH;
        }

        /** A secret is only ever replaced, so its window keeps nothing empty. */
        @Override
        protected boolean mayBeEmpty() {
            ServerConfigEntry entry = entry(this.name);
            return entry == null || !entry.isSecret();
        }

        @Override
        public Settings.Store store() {
            return Settings.Store.NONE;
        }

        @Override
        public boolean stands() {
            return ServerSettingsRows.this.host.stands(this.made)
                    && entry(this.name) != null;
        }
    }

    /** A line of a list, changed in Settings' value window; its Clear, or an empty value, takes it out. */
    private final class Item extends Settings.Line {
        private final String name;
        private final int index;
        private final int made = ServerSettingsRows.this.host.generation();

        Item(String name, int index) {
            super(name + "#" + index, "");
            this.name = name;
            this.index = index;
        }

        @Override
        public String label() {
            ServerConfigEntry entry = entry(this.name);
            return entry == null ? "" : settingName(entry);
        }

        @Override
        protected String get() {
            ServerConfigEntry entry = entry(this.name);
            List<String> items = entry == null ? null : draft().values(entry);
            return items == null || this.index >= items.size() ? ""
                    : items.get(this.index);
        }

        @Override
        protected void set(String text) {
            draft().setItem(entry(this.name), this.index, text);
        }

        @Override
        protected int maxLength() {
            return ServerConfigChangeValidator.MAX_VALUE_LENGTH;
        }

        @Override
        public Settings.Store store() {
            return Settings.Store.NONE;
        }

        @Override
        public boolean stands() {
            ServerConfigEntry entry = entry(this.name);
            return ServerSettingsRows.this.host.stands(this.made) && entry != null
                    && this.index < draft().values(entry).size();
        }
    }

    /** A new line for a list, typed in an empty value window and added at the list's end. */
    private final class Addition extends Settings.Line {
        private final String name;
        private final int made = ServerSettingsRows.this.host.generation();

        Addition(String name) {
            super(name + "#add", "");
            this.name = name;
        }

        @Override
        public String label() {
            ServerConfigEntry entry = entry(this.name);
            return entry == null ? "" : settingName(entry);
        }

        @Override
        protected String get() {
            return "";
        }

        @Override
        protected void set(String text) {
            draft().addItem(entry(this.name), text);
        }

        @Override
        protected int maxLength() {
            return ServerConfigChangeValidator.MAX_VALUE_LENGTH;
        }

        @Override
        protected boolean mayBeEmpty() {
            return false;
        }

        @Override
        public Settings.Store store() {
            return Settings.Store.NONE;
        }

        @Override
        public boolean stands() {
            return ServerSettingsRows.this.host.stands(this.made)
                    && draft().canAdd(entry(this.name));
        }
    }

    /**
     * A number between its chevrons, a step at a time or ten with Shift,
     * within the bounds the server names; its value is typed in Settings'
     * value window, whose Use the Default is the server's shipped value.
     */
    /**
     * A few-word option of the server's, its words picked in a sub-window
     * of their own as every few-word setting's are, into the page's
     * waiting changes.
     */
    private final class WordsValue extends Settings.Setting {
        private final String name;
        private final int made = ServerSettingsRows.this.host.generation();

        WordsValue(String name) {
            super(name, "");
            this.name = name;
        }

        @Override
        public String label() {
            ServerConfigEntry entry = entry(this.name);
            return entry == null ? "" : settingName(entry);
        }

        @Override
        public String value() {
            ServerConfigEntry entry = entry(this.name);
            return entry == null ? "" : settingWord(entry, draft().value(entry));
        }

        /** Its words as the player reads them, in the order the server names them. */
        @Override
        public List<String> words() {
            ServerConfigEntry entry = entry(this.name);
            List<String> read = new ArrayList<String>();
            if (entry != null) {
                for (String word : entry.getValidValues()) {
                    read.add(settingWord(entry, word));
                }
            }
            return read;
        }

        @Override
        public int wordIndex() {
            ServerConfigEntry entry = entry(this.name);
            return entry == null ? -1
                    : entry.getValidValues().indexOf(draft().value(entry));
        }

        @Override
        public int shippedWordIndex() {
            ServerConfigEntry entry = entry(this.name);
            return entry == null ? -1
                    : entry.getValidValues().indexOf(entry.getDefault());
        }

        @Override
        public void pickWord(int index) {
            ServerConfigEntry entry = entry(this.name);
            List<String> words = entry == null
                    ? java.util.Collections.<String>emptyList()
                    : entry.getValidValues();
            if (entry != null && index >= 0 && index < words.size()) {
                draft().set(entry, words.get(index));
            }
        }

        @Override
        public void restore() {
            ServerConfigEntry entry = entry(this.name);
            if (entry != null) {
                draft().set(entry, entry.getDefault());
            }
        }

        @Override
        public Settings.Store store() {
            return Settings.Store.NONE;
        }

        @Override
        public boolean stands() {
            return ServerSettingsRows.this.host.stands(this.made)
                    && entry(this.name) != null;
        }
    }

    private final class NumberValue extends Settings.Numeric {
        private final String name;
        private final int made = ServerSettingsRows.this.host.generation();

        NumberValue(String name, NumberStepper shape) {
            super(name, "", shape.step, shape.decimals);
            this.name = name;
        }

        @Override
        public String label() {
            ServerConfigEntry entry = entry(this.name);
            return entry == null ? "" : settingName(entry);
        }

        @Override
        protected double get() {
            ServerConfigEntry entry = entry(this.name);
            return entry == null ? Double.NaN
                    : Settings.parseNumber(draft().value(entry));
        }

        @Override
        protected void set(double value) {
            ServerConfigEntry entry = entry(this.name);
            if (entry != null) {
                draft().set(entry, stepper().format(value));
            }
        }

        @Override
        protected double[] bounds() {
            ServerConfigEntry entry = entry(this.name);
            return entry == null ? null : ServerSettingsDraft.bounds(entry);
        }

        @Override
        protected double shippedNumber() {
            ServerConfigEntry entry = entry(this.name);
            return entry == null ? Double.NaN
                    : Settings.parseNumber(entry.getDefault());
        }

        @Override
        public Settings.Store store() {
            return Settings.Store.NONE;
        }

        @Override
        public boolean stands() {
            return ServerSettingsRows.this.host.stands(this.made)
                    && entry(this.name) != null;
        }
    }
}
