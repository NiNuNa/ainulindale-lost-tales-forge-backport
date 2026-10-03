package com.ninuna.losttales.client.window;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.util.StatCollector;

/**
 * A kind of sub-window: one of each is open at most, but a card for each
 * person and a question for each page. The window system's own kinds are
 * here; every system registers its own ({@link #register}) before the
 * layout file is read, since the file remembers each kind's place by its
 * id.
 */
public final class SubWindowKind {
    private static final Map<String, SubWindowKind> KINDS =
            new LinkedHashMap<String, SubWindowKind>();

    /** A tab's options, behind the three dots on the tab. */
    public static final SubWindowKind TAB = register("tab",
            "gui.losttales.window.sub.tab");
    /** The options of a tab its tool strip has no room for, behind the strip's overflow button. */
    public static final SubWindowKind OVERFLOW = register("overflow",
            "gui.losttales.window.sub.overflow");
    /** The few words one of a tab's options picks from: a conversation's Notifications. */
    public static final SubWindowKind PICK = register("pick",
            "gui.losttales.window.sub.pick");
    /** A tab's split view: the pages that can stand beside it, or its split's rows. */
    public static final SubWindowKind SPLIT = register("split",
            "gui.losttales.window.sub.split");
    /** A window's own menu, behind the three dots at the end of its row. */
    public static final SubWindowKind WINDOW = register("window",
            "gui.losttales.window.sub.window");
    /** The palette a colour setting opens. */
    public static final SubWindowKind PALETTE = register("palette",
            "gui.losttales.window.sub.palette");
    /** The closed tabs to open, behind the {@code +}. */
    public static final SubWindowKind OPEN = register("open",
            "gui.losttales.window.sub.open");
    /** The tab search. */
    public static final SubWindowKind TAB_SEARCH = register("tab_search",
            "gui.losttales.window.sub.tab_search");
    /** The quick switcher: every tab, and what the pages find. */
    public static final SubWindowKind SWITCHER = register("switcher",
            "gui.losttales.window.sub.switcher");
    /** Every setting, in sections. */
    public static final SubWindowKind SETTINGS = register("settings",
            "gui.losttales.window.sub.settings");
    /** The words a few-word setting picks from. */
    public static final SubWindowKind SETTING_WORDS = register(
            "setting_words", "gui.losttales.window.sub.setting_words");
    /** The field a number or a line of Settings is typed into. */
    public static final SubWindowKind SETTING_VALUE = register(
            "setting_value", "gui.losttales.window.sub.setting_value");
    /** A question before an action that cannot be undone ({@link QuestionWindow}). */
    public static final SubWindowKind QUESTION = register("question",
            "gui.losttales.window.sub.question");
    /** A page's help: what it is for, and its keys ({@link PageHelp}). */
    public static final SubWindowKind HELP = register("help",
            "gui.losttales.window.sub.help");

    /** What the layout file remembers the kind's place by. */
    public final String id;
    private final String titleKey;

    private SubWindowKind(String id, String titleKey) {
        this.id = id;
        this.titleKey = titleKey;
    }

    /**
     * The kind with this id, made the first time it is asked for; its
     * strip reads {@code titleKey} where its content names nothing.
     */
    public static synchronized SubWindowKind register(String id,
                                                      String titleKey) {
        String key = normalise(id);
        SubWindowKind kind = KINDS.get(key);
        if (kind == null) {
            kind = new SubWindowKind(key, titleKey);
            KINDS.put(key, kind);
        }
        return kind;
    }

    /** The name on the window's strip. */
    public String title() {
        return StatCollector.translateToLocal(this.titleKey);
    }

    /** The kind the layout file names, or null for a word no system registered. */
    static synchronized SubWindowKind fromId(String id) {
        return KINDS.get(normalise(id));
    }

    /** Every kind, in the order they were registered. */
    static synchronized List<SubWindowKind> all() {
        return new ArrayList<SubWindowKind>(KINDS.values());
    }

    private static String normalise(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return this.id;
    }
}
