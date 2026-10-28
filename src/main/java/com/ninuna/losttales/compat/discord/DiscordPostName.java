package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatEpithet;
import com.ninuna.losttales.chat.ChatNames;
import com.ninuna.losttales.faction.FactionDemonyms;
import com.ninuna.losttales.util.LostTalesWords;

/**
 * The name a webhook post goes under, worded for each linked channel in
 * the language chosen for it (DISC-30): the Narrator's, a name as it is,
 * a character with its LOTR title ({@code Aldric, the Gondor Farmer}), or
 * a Discord member with their Discord server ({@code Mira, of The
 * Shire}). The title and the faction's name are LOTR's words as the
 * server reads them; the Narrator, a people's name and the words joining
 * a title to a name are the mod's, in the channel's language.
 */
public abstract class DiscordPostName {
    /** The Narrator: its own lines and every action (DISC-08). */
    public static final DiscordPostName NARRATOR = new DiscordPostName() {
        @Override
        public String in(LostTalesWords words) {
            return ChatNames.narrator(words);
        }
    };

    private DiscordPostName() {}

    /** The name in {@code words}, the linked channel's. */
    public abstract String in(LostTalesWords words);

    /** A name that reads alike in every language: an account's, an untitled character's. */
    public static DiscordPostName plain(String name) {
        final String plain = name == null ? "" : name;
        return new DiscordPostName() {
            @Override
            public String in(LostTalesWords words) {
                return plain;
            }
        };
    }

    /**
     * A character with its title: {@code factionId} the faction's
     * normalized LOTR id and {@code factionName} its name, either empty
     * for none, and {@code title} the title in LOTR's words.
     */
    public static DiscordPostName titled(final String name, final String factionId,
                                         final String factionName, final String title) {
        return new DiscordPostName() {
            @Override
            public String in(LostTalesWords words) {
                return ChatEpithet.titledName(words, name,
                        FactionDemonyms.of(words, factionId, factionName), title);
            }
        };
    }

    /** A Discord member titled by their Discord server, carried on to another linked channel. */
    public static DiscordPostName discordMember(final String name, final String guildName) {
        return new DiscordPostName() {
            @Override
            public String in(LostTalesWords words) {
                return ChatEpithet.discordName(words, name, guildName);
            }
        };
    }
}
