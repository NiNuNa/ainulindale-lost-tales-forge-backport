package com.ninuna.losttales.progression.deed;

/**
 * A deed's words: a bundled deed is a LOTR achievement, so its name and
 * description are that achievement's lang lines
 * ({@code lotr.achievement.lt_troll_slayer.title}, {@code .desc}) and the
 * title it may unlock is LOTR's ({@code lotr.title.lt_troll_slayer}); a
 * server's own deed writes its words in its file.
 */
public final class DeedWords {
    /** The start of every deed achievement's name, and its title's. */
    public static final String NAME_PREFIX = "lt_";

    private DeedWords() {}

    /** {@code losttales:troll_slayer} is the achievement {@code lt_troll_slayer}. */
    public static String achievementName(String deedId) {
        int colon = deedId.indexOf(':');
        return NAME_PREFIX + (colon >= 0 ? deedId.substring(colon + 1) : deedId).replace('.', '_');
    }

    public static String nameKey(String deedId) {
        return "lotr.achievement." + achievementName(deedId) + ".title";
    }

    public static String descriptionKey(String deedId) {
        return "lotr.achievement." + achievementName(deedId) + ".desc";
    }

    public static String titleKey(String deedId) {
        return "lotr.title." + achievementName(deedId);
    }

    /** A category's name, as LOTR's achievement screen shows it. */
    public static String categoryKey(DeedCategory category) {
        return "lotr.achievement.category." + category.lotrName();
    }
}
