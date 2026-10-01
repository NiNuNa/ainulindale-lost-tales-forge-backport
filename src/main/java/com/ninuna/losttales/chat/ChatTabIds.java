package com.ninuna.losttales.chat;

import java.util.Locale;
import java.util.UUID;

/**
 * The grammar of a tab id, the part of it both sides read: the client's
 * {@code ChatTab} writes and parses ids in full, and the server reads
 * only which channel an id names, for the command answers it keeps
 * under the tab they were typed in. A plain channel's id is the channel
 * id, followed by {@code |} and a conversation or an identity; a whisper
 * is {@code whisper:Name...} and an NPC conversation {@code npc:Name},
 * which name no channel. No Minecraft dependency.
 */
public final class ChatTabIds {
    public static final String WHISPER_PREFIX = "whisper:";
    public static final String NPC_PREFIX = "npc:";
    /** Between a tab id's channel and whatever qualifies it. */
    public static final char SEPARATOR = '|';
    /**
     * The last segment of a whisper id, after a separator, when the
     * conversation is held as one of the player's characters: the
     * character's id, which no name can be mistaken for.
     */
    public static final String OWNER_MARK = "own:";
    /**
     * The last segment of a scoped plain channel's id, after a separator:
     * the conversation the tab is, a faction or a fellowship.
     */
    public static final String SCOPE_MARK = "in:";

    private ChatTabIds() {}

    /**
     * The conversation a plain channel's tab id names after its
     * {@link #SCOPE_MARK}: a faction, or a fellowship's id. Empty for any
     * other id.
     */
    public static String scopeOf(String tabId) {
        String trimmed = tabId == null ? "" : tabId.trim();
        if (channelOf(trimmed) == null) {
            return "";
        }
        int separator = trimmed.indexOf(SEPARATOR);
        String rest = separator < 0 ? "" : trimmed.substring(separator + 1);
        return rest.toLowerCase(Locale.ROOT).startsWith(SCOPE_MARK)
                ? rest.substring(SCOPE_MARK.length()) : "";
    }

    /**
     * The id of one whisper conversation, as the client's tab writes it:
     * the partner's account, their identity where it is not the
     * account's own, and the character of this side the conversation is
     * held as, where it is held as one. Both sides build it, so a request
     * for a conversation's older lines names the same thing its tab
     * shows. {@code ownerKey} is the character's id lower-cased, or empty
     * for the account.
     */
    public static String whisperConversationId(String partner, String identity,
                                               String ownerKey) {
        String account = partner == null ? "" : partner.trim();
        String named = identity == null ? "" : identity.trim();
        if (named.length() == 0) {
            named = account;
        }
        String owner = ownerKey == null ? "" : ownerKey.trim().toLowerCase(Locale.ROOT);
        StringBuilder id = new StringBuilder(WHISPER_PREFIX).append(account);
        if (!named.equalsIgnoreCase(account) || owner.length() > 0) {
            id.append(SEPARATOR).append(named);
        }
        if (owner.length() > 0) {
            id.append(SEPARATOR).append(OWNER_MARK).append(owner);
        }
        return id.toString();
    }

    /** As above, held as the character with that id, or as the account for null. */
    public static String whisperConversationId(String partner, String identity,
                                               UUID ownCharacterId) {
        return whisperConversationId(partner, identity,
                ownCharacterId == null ? "" : ownCharacterId.toString());
    }

    /**
     * The channel a tab id names, or null for a whisper, an NPC
     * conversation or an id naming no channel this build has.
     */
    public static ChatChannel channelOf(String tabId) {
        if (tabId == null) {
            return null;
        }
        String trimmed = tabId.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith(WHISPER_PREFIX) || lower.startsWith(NPC_PREFIX)) {
            return null;
        }
        int separator = trimmed.indexOf(SEPARATOR);
        String channelPart = separator < 0 ? trimmed
                : trimmed.substring(0, separator);
        ChatChannel channel = ChatChannel.fromId(channelPart);
        return channel == ChatChannel.WHISPER ? null : channel;
    }
}
