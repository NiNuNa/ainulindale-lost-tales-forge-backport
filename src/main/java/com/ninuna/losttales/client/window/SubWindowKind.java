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

    /** A tab's menu, behind the cog on the tool strip. */
    public static final SubWindowKind TAB = register("tab",
            "gui.losttales.window.sub.tab");
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
    /** A question before an action that cannot be undone ({@link QuestionWindow}). */
    public static final SubWindowKind QUESTION = register("question",
            "gui.losttales.window.sub.question");

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
