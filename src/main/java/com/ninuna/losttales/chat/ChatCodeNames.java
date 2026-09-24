package com.ninuna.losttales.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one name a conversation goes by in code and in what players type:
 * a channel's id ({@code global}, {@code ooc}, {@code operator}), and for
 * a faction's chat the faction's LOTR code name ({@code gondor},
 * {@code high_elf}, {@code unaligned}). A {@code #} link, a Discord link
 * and the {@code /losttales discord} commands all use it, so a channel
 * is only ever written one way.
 *
 * <p>The Faction channel's own id, {@code faction}, names the kind of
 * channel, not a conversation, so it is never a code name: the faction
 * is. The faction names are LOTR's and are put in once the mod has
 * started ({@link #installFactions}); until then no word names a
 * faction.</p>
 */
public final class ChatCodeNames {
    /** The longest code name: what fits behind a {@code #}. */
    public static final int MAX_LENGTH = 24;

    /** Faction code name to faction id ({@code gondor} to {@code lotr:gondor}). */
    private static volatile Map<String, String> factionIds =
            Collections.emptyMap();

    private ChatCodeNames() {}

    /** A conversation as its code name names it: the channel, and for a faction's chat the faction's id. */
    public static final class Named {
        public final ChatChannel channel;
        /** The faction's id for a faction's chat; empty for every other channel. */
        public final String scope;

        Named(ChatChannel channel, String scope) {
            this.channel = channel;
            this.scope = scope;
        }
    }

    /**
     * Puts in the factions a Faction chat can be named by, from their ids
     * ({@code lotr:gondor}). A name that is a built-in channel's id, or
     * could not be typed behind a {@code #}, is left out.
     */
    public static void installFactions(Collection<String> ids) {
        Map<String, String> installed = new LinkedHashMap<String, String>();
        if (ids != null) {
            for (String id : ids) {
                String code = codeOfId(id);
                if (code.length() > 0 && ChatChannel.fromId(code) == null) {
                    installed.put(code, id.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        factionIds = Collections.unmodifiableMap(installed);
    }

    /**
     * The code name of a conversation: the channel's id, or for a faction's
     * chat the faction's code name. Null where there is none: the Faction
     * channel without a known faction.
     */
    public static String of(ChatChannel channel, String scope) {
        if (channel == null) {
            return null;
        }
        if (channel != ChatChannel.FACTION) {
            return channel.getId();
        }
        String code = codeOfId(scope);
        return code.length() > 0 && factionIds.containsKey(code) ? code : null;
    }

    /** The conversation a code name names, whatever its case; null for none. */
    public static Named parse(String name) {
        if (name == null) {
            return null;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (key.length() == 0 || key.length() > MAX_LENGTH) {
            return null;
        }
        ChatChannel channel = ChatChannel.fromId(key);
        if (channel != null) {
            return channel == ChatChannel.FACTION ? null
                    : new Named(channel, "");
        }
        String factionId = factionIds.get(key);
        return factionId == null ? null
                : new Named(ChatChannel.FACTION, factionId);
    }

    /** Whether a word is a faction's code name, so no other channel may take it. */
    public static boolean isFaction(String name) {
        return name != null && factionIds.containsKey(
                name.trim().toLowerCase(Locale.ROOT));
    }

    /** Every faction's code name, in LOTR's order. */
    public static List<String> factionCodes() {
        return Collections.unmodifiableList(
                new ArrayList<String>(factionIds.keySet()));
    }

    /**
     * The code part of a faction id, lower-cased: what follows its
     * namespace ({@code lotr:gondor} to {@code gondor}). Empty for an id
     * that has none, or one that could not be typed behind a {@code #}.
     */
    private static String codeOfId(String id) {
        String value = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        String code = value.substring(value.indexOf(':') + 1);
        if (code.length() == 0 || code.length() > MAX_LENGTH) {
            return "";
        }
        for (int index = 0; index < code.length(); index++) {
            char character = code.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '_') {
                return "";
            }
        }
        return code;
    }
}
