package com.ninuna.losttales.chat.profanity;

import java.util.Locale;

/**
 * How a word on the profanity list is shown: as typed, as its silly
 * stand-in ({@code fuck} reads {@code flip}), or as its first letter and
 * stars ({@code f***}). The client's own choice for what it draws
 * ({@code client.chatProfanityFilter}) and the server's for what it
 * posts to Discord ({@code discord.profanityFilter}).
 */
public enum ChatProfanityMode {
    OFF,
    SILLY,
    STARS;

    /** The mode names as a config option offers them. */
    public static String[] names() {
        ChatProfanityMode[] modes = values();
        String[] names = new String[modes.length];
        for (int index = 0; index < modes.length; index++) {
            names[index] = modes[index].name();
        }
        return names;
    }

    /**
     * The mode a config value names, whatever its case; {@code fallback}
     * for anything that names none.
     */
    public static ChatProfanityMode of(String name, ChatProfanityMode fallback) {
        if (name == null) {
            return fallback;
        }
        String wanted = name.trim().toUpperCase(Locale.ROOT);
        for (ChatProfanityMode mode : values()) {
            if (mode.name().equals(wanted)) {
                return mode;
            }
        }
        return fallback;
    }
}
