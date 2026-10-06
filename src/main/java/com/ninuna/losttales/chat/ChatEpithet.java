package com.ninuna.losttales.chat;

import com.ninuna.losttales.util.LostTalesLangFile;
import net.minecraft.util.StatCollector;

/**
 * How a sender's title is said after their name, LOTR's own NPC naming:
 * {@code Aragorn, the Gondor Farmer} — the faction's people before the
 * title, the bare title when the faction is unknown, and nothing at all
 * for an untitled sender. The client composes the same pieces as
 * separate coloured runs; the Discord bridge needs them as one string,
 * so the words are decided here once and both read them, from the lang
 * file in the language of the side that asks. The title travels and is
 * kept as LOTR's lang key, and the faction as its id: each side names them
 * in its own words ({@link #titleName}).
 */
public final class ChatEpithet {
    private ChatEpithet() {}

    /**
     * A LOTR title in the words of the side that asks, from the lang key
     * it travels as: {@code lotr.title.farmer} is {@code Farmer}. A key
     * the lang file has no line for reads as it is.
     */
    public static String titleName(String titleKey) {
        String key = titleKey == null ? "" : titleKey.trim();
        if (key.length() == 0) {
            return "";
        }
        String said = StatCollector.translateToLocal(key);
        return said == null || said.trim().length() == 0 ? key : said.trim();
    }

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
        return translate("chat.losttales.title.epithet", faction, bare);
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
        return plain + translate("chat.losttales.title.suffix",
                epithet(factionName, title));
    }

    /**
     * The words a title follows a name with: {@code , the Gondor Farmer}
     * for a character's LOTR title, {@code , of The Shire} for a Discord
     * member's Discord server.
     */
    public static String titleSuffix(boolean discordMember, String epithet) {
        return discordMember
                ? translate("chat.losttales.title.discord", epithet)
                : translate("chat.losttales.title.suffix", epithet);
    }

    /**
     * {@code Nils, of The Shire}: a Discord member titled by their
     * Discord server, or the bare name while the server is not known.
     */
    public static String discordName(String name, String guildName) {
        String plain = name == null ? "" : name.trim();
        String guild = guildName == null ? "" : guildName.trim();
        return guild.length() == 0 ? plain : plain + titleSuffix(true, guild);
    }

    /**
     * The line under {@code key} with its arguments, in the language of the
     * side that asks; the mod's English line where that language has none,
     * as before the game has read its lang files; the key where neither
     * has one. One source for every word: the lang file.
     */
    public static String translate(String key, Object... arguments) {
        String format = StatCollector.canTranslate(key)
                ? StatCollector.translateToLocal(key)
                : LostTalesLangFile.english().get(key);
        if (format == null || format.length() == 0) {
            return key;
        }
        try {
            return String.format(format, arguments);
        } catch (IllegalArgumentException unformattable) {
            // A translation whose pattern does not fit the arguments reads as written.
            return format;
        }
    }
}
