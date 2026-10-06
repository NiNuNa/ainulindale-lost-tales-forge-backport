package com.ninuna.losttales.config.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The words a server setting or category is shown by where the lang file
 * has none for it, made from its code name: a key split at its capitals
 * and underscores into Title Case words ({@code historyPerChannel} reads
 * "History per Channel"), a run of capitals kept whole as the key writes
 * it. Free of Minecraft.
 */
final class ServerSettingNames {
    /** Words Title Case leaves in lower case after the first. */
    private static final Set<String> SMALL_WORDS = new HashSet<String>(
            Arrays.asList("a", "an", "and", "as", "at", "by", "for", "from",
                    "in", "of", "on", "or", "per", "the", "to", "with"));

    private ServerSettingNames() {}

    /** A key's words in Title Case; empty for none. */
    static String name(String key) {
        List<String> words = words(key == null ? "" : key);
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < words.size(); index++) {
            String word = words.get(index);
            String lower = word.toLowerCase(Locale.ROOT);
            if (name.length() > 0) {
                name.append(' ');
            }
            if (index > 0 && SMALL_WORDS.contains(lower)) {
                name.append(lower);
            } else {
                name.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1));
            }
        }
        return name.toString();
    }

    /**
     * The key in camel case, as a category's lang key names it where its
     * code name has underscores: {@code combat_markers} is
     * {@code combatMarkers}.
     */
    static String camelCase(String key) {
        List<String> words = words(key == null ? "" : key);
        StringBuilder joined = new StringBuilder();
        for (String word : words) {
            joined.append(joined.length() == 0 ? word
                    : Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return joined.toString();
    }

    /**
     * The words of a key: parted at underscores, hyphens, points and
     * spaces, before a capital that follows a small letter or a digit,
     * and before the last capital of a run that a small letter follows.
     */
    static List<String> words(String key) {
        List<String> words = new ArrayList<String>();
        StringBuilder word = new StringBuilder();
        for (int index = 0; index < key.length(); index++) {
            char character = key.charAt(index);
            if (character == '_' || character == '-' || character == '.'
                    || Character.isWhitespace(character)) {
                add(words, word);
                continue;
            }
            if (Character.isUpperCase(character) && word.length() > 0) {
                char before = key.charAt(index - 1);
                boolean afterSmall = Character.isLowerCase(before)
                        || Character.isDigit(before);
                boolean endsRun = Character.isUpperCase(before)
                        && index + 1 < key.length()
                        && Character.isLowerCase(key.charAt(index + 1));
                if (afterSmall || endsRun) {
                    add(words, word);
                }
            }
            word.append(character);
        }
        add(words, word);
        return words;
    }

    private static void add(List<String> words, StringBuilder word) {
        if (word.length() > 0) {
            words.add(word.toString());
            word.setLength(0);
        }
    }
}
