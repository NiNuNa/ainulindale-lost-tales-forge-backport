package com.ninuna.losttales.chat;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * Lines sent as words for each player's game to translate: lang keys under
 * {@link #PREFIX} and their arguments, in a Server line or in a Discord
 * member's line the server built. A game reads such a line in its own
 * language and then as plain words, so a link among them
 * ({@code [m:Weathertop]}) still opens what it names and nothing in them
 * is read as a mention.
 */
public final class ChatTranslatedWords {
    /** What the lang key of a line of translated words begins with. */
    public static final String PREFIX = "chat.losttales.words.";

    /**
     * How deep translated words may nest: a translation among a line's
     * runs, an argument of it, and a little room besides.
     */
    private static final int MAX_DEPTH = 4;

    private ChatTranslatedWords() {}

    /** Whether a lang key names a line of translated words. */
    public static boolean isWords(String key) {
        return key != null && key.startsWith(PREFIX)
                && key.length() > PREFIX.length();
    }

    /**
     * Whether a component is translated words and nothing else: plain text
     * and translations whose keys are words keys, at least one of them,
     * with no style, link or hover anywhere; a translation's arguments are
     * plain words or such components in turn. Anything else is not read
     * as translated words.
     */
    public static boolean isWordsComponent(IChatComponent component) {
        int[] translations = new int[1];
        return holdsOnlyWords(component, 0, translations) && translations[0] > 0;
    }

    private static boolean holdsOnlyWords(IChatComponent component, int depth,
                                          int[] translations) {
        if (component == null || depth > MAX_DEPTH
                || !component.getChatStyle().isEmpty()) {
            return false;
        }
        if (component instanceof ChatComponentTranslation) {
            ChatComponentTranslation translation = (ChatComponentTranslation)component;
            if (!isWords(translation.getKey())) {
                return false;
            }
            translations[0]++;
            Object[] arguments = translation.getFormatArgs();
            for (int index = 0; arguments != null && index < arguments.length; index++) {
                Object argument = arguments[index];
                if (!(argument instanceof String)
                        && !(argument instanceof IChatComponent
                                && holdsOnlyWords((IChatComponent)argument,
                                        depth + 1, translations))) {
                    return false;
                }
            }
        } else if (!(component instanceof ChatComponentText)) {
            return false;
        }
        for (Object sibling : component.getSiblings()) {
            if (!(sibling instanceof IChatComponent)
                    || !holdsOnlyWords((IChatComponent)sibling, depth + 1,
                            translations)) {
                return false;
            }
        }
        return true;
    }
}
