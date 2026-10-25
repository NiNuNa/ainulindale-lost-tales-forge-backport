package com.ninuna.losttales.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The server options that only the next start puts in force (DATA-12): a
 * save writes them to the file at once, but what reads them reads them as
 * the game or the server starts. The recipes are registered as the game
 * loads; the mod's own missive templates are read as the server starts and
 * on {@code /losttales reload missives}; the chat history is tied to the
 * world save, and the audit log's folder opened, as the server starts.
 * The Server Settings page marks their rows, and every answer to a save
 * says they wait for the next start.
 */
public final class NextStartOptions {

    /** Each option as {@code category.key}, in the file's own spelling. */
    private static final Set<String> KEYS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList(
                    LostTalesConfig.CATEGORY_WAYSTONES + ".enableWaystoneRecipe",
                    LostTalesConfig.CATEGORY_WAYSTONES + ".waystoneRecipeCornerIngredient",
                    LostTalesConfig.CATEGORY_WAYSTONES + ".waystoneRecipeEdgeIngredient",
                    LostTalesConfig.CATEGORY_WAYSTONES + ".waystoneRecipeCenterIngredient",
                    LostTalesConfig.CATEGORY_MISSIVES + ".enableMissiveBoardRecipe",
                    LostTalesConfig.CATEGORY_MISSIVES + ".missiveBoardRecipeCornerIngredient",
                    LostTalesConfig.CATEGORY_MISSIVES + ".missiveBoardRecipeEdgeIngredient",
                    LostTalesConfig.CATEGORY_MISSIVES + ".missiveBoardRecipeCenterIngredient",
                    LostTalesConfig.CATEGORY_MISSIVES + ".bundledMissiveTemplates",
                    LostTalesConfig.CATEGORY_CHAT + ".historyPersisted",
                    LostTalesConfig.CATEGORY_CHAT + ".auditLog")));
    /** The same, in lower case, for a name given in any case. */
    private static final Set<String> LOWER = lowerCase(KEYS);

    private NextStartOptions() {}

    /** Every option the next start puts in force, as {@code category.key}. */
    public static Set<String> keys() {
        return KEYS;
    }

    /** Whether only the next start puts this option in force. */
    public static boolean waitsForNextStart(String category, String key) {
        return category != null && key != null && waitsForNextStart(category + "." + key);
    }

    /** The same for an option named {@code category.key}, in any case. */
    public static boolean waitsForNextStart(String qualifiedName) {
        return qualifiedName != null
                && LOWER.contains(qualifiedName.trim().toLowerCase(Locale.ROOT));
    }

    private static Set<String> lowerCase(Set<String> names) {
        Set<String> lower = new LinkedHashSet<String>();
        for (String name : names) {
            lower.add(name.toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(lower);
    }
}
