package com.ninuna.losttales.quest;

import java.util.Locale;

/**
 * The one rule for a quest's category, read by the journal and written on
 * a quest card alike. A category is a word, and its name is the lang key
 * {@code gui.losttales.quest.category.<word>}. A Lost Tales quest is filed
 * by the first part of its id's path: {@code tutorial/}, {@code path/},
 * {@code missive/}, {@code faction/}, {@code regional/} or {@code story/};
 * a world quest under {@link #WORLD}; anything else under {@link #MISC}.
 */
public final class LostTalesQuestCategory {
    public static final String TUTORIALS = "tutorials";
    public static final String PATHS = "paths";
    public static final String MISSIVES = "missives";
    public static final String FACTIONS = "factions";
    public static final String REGIONAL = "regional";
    public static final String MAIN_STORY = "main_story";
    public static final String WORLD = "world";
    public static final String MISC = "misc";

    /** Where the lang keys of the categories' names start. */
    public static final String KEY_PREFIX = "gui.losttales.quest.category.";

    private LostTalesQuestCategory() {}

    /** The category the quest is filed under. */
    public static String of(LostTalesQuestDefinition quest) {
        if (quest == null) {
            return MISC;
        }
        if (quest.isWorldQuest()) {
            return WORLD;
        }
        String id = quest.getId() == null ? "" : quest.getId();
        int colon = id.indexOf(':');
        String path = (colon >= 0 ? id.substring(colon + 1) : id)
                .toLowerCase(Locale.ROOT);
        if (path.startsWith("tutorial/")) {
            return TUTORIALS;
        }
        if (path.startsWith("path/")) {
            return PATHS;
        }
        if (path.startsWith("missive/")) {
            return MISSIVES;
        }
        if (path.startsWith("faction/")) {
            return FACTIONS;
        }
        if (path.startsWith("regional/")) {
            return REGIONAL;
        }
        if (path.startsWith("story/")) {
            return MAIN_STORY;
        }
        return MISC;
    }

    /** The lang key a category's name is under; the misc key for none. */
    public static String key(String category) {
        String word = category == null ? ""
                : category.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return KEY_PREFIX + (word.length() == 0 ? MISC : word);
    }
}
