package com.ninuna.losttales.chat;

import net.minecraft.util.StatCollector;

/**
 * How a sender's title is said after their name, LOTR's own NPC naming:
 * {@code Aragorn, the Gondor Farmer} — the faction's people before the
 * title, the bare title when the faction is unknown, and nothing at all
 * for an untitled sender. The client composes the same pieces as
 * separate coloured runs; the Discord bridge needs them as one string,
 * so the words are decided here once and both read them. Localised
 * through the same keys with an English fallback, on whichever side
 * asks.
 */
public final class ChatEpithet {
    private ChatEpithet() {}

    /**
     * {@code Gondor Farmer}: the faction name before the title, or the
     * bare title when the sender's faction is unknown.
     */
    public static String epithet(String factionName, String title) {
        String faction = factionName == null ? "" : factionName.trim();
        String bare = title == null ? "" : title.trim();
        if (faction.length() == 0) {
            return bare;
        }
        return translate("chat.losttales.title.epithet", "%s %s",
                faction, bare);
    }

    /**
     * {@code Aragorn, the Gondor Farmer}, or the bare name when the
     * sender has no title.
     */
    public static String titledName(String name, String factionName,
                                    String title) {
        String plain = name == null ? "" : name.trim();
        if (title == null || title.trim().length() == 0) {
            return plain;
        }
        return plain + translate("chat.losttales.title.suffix", ", the %s",
                epithet(factionName, title));
    }

    /**
     * A localized format with an English fallback, so the line is still
     * right when the language file does not carry the key.
     */
    public static String translate(String key, String fallback,
                                   Object... arguments) {
        String format = StatCollector.translateToLocal(key);
        if (format == null || format.length() == 0 || format.equals(key)) {
            format = fallback;
        }
        try {
            return String.format(format, arguments);
        } catch (IllegalArgumentException ignored) {
            return String.format(fallback, arguments);
        }
    }
}
