package com.ninuna.losttales.chat;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A player a line names, as the server knew them when the line was
 * said: the account and its id, and the identity it was playing — the
 * character's id and skin, or none for the account. A Discord member is
 * named the same way, by the id the chat knows them by and their
 * nickname.
 *
 * <p>The server works out whom a line names — the players an
 * achievement, a join or a death is about, whom a message's mentions
 * reach — and sends the answer with the line, live and in every replay,
 * so every client reads it from there: the name to draw, whether the
 * line pings its reader, and the head and names a card about them shows,
 * whether or not they are still here. A mention of a player wears the
 * one mention colour whoever it names, so the record carries no
 * colour.</p>
 */
public final class ChatNamedPlayer {
    /** The most players one line names; a death names two. */
    public static final int MAX_PER_LINE = 8;
    public static final int MAX_ACCOUNT_BYTES = 64;
    public static final int MAX_IDENTITY_BYTES = 256;
    /** A skin snapshot id, as bounded wherever a line carries one. */
    public static final int MAX_SKIN_ID_BYTES = 128;

    private final UUID playerId;
    private final String account;
    private final UUID characterId;
    private final String identityName;
    private final String skinId;

    /**
     * {@code characterId} is null, and {@code skinId} empty, for a player
     * who was playing as the account.
     */
    public ChatNamedPlayer(UUID playerId, String account, UUID characterId,
                           String identityName, String skinId) {
        this.playerId = playerId;
        this.account = account == null ? "" : account.trim();
        this.characterId = characterId;
        String identity = identityName == null ? "" : identityName.trim();
        this.identityName = identity.length() == 0 ? this.account : identity;
        this.skinId = characterId == null || skinId == null ? ""
                : skinId.trim();
    }

    /** The account as an out-of-character line names it: itself, with no character. */
    public static ChatNamedPlayer account(UUID playerId, String account) {
        return new ChatNamedPlayer(playerId, account, null, account, "");
    }

    /** The account's id; null only where nobody could name it. */
    public UUID getPlayerId() {
        return this.playerId;
    }

    public String getAccount() {
        return this.account;
    }

    /** The character the player was playing; null for the account. */
    public UUID getCharacterId() {
        return this.characterId;
    }

    /** The name the player's lines were signed with when the line was said. */
    public String getIdentityName() {
        return this.identityName;
    }

    /** The character's skin snapshot; empty for the account's own skin. */
    public String getSkinId() {
        return this.skinId;
    }

    /** Whether the entry names anybody at all. */
    public boolean isValid() {
        return this.account.length() > 0;
    }

    /**
     * The entry naming {@code name} — an account, or the identity it was
     * playing, since a line the server writes names the character — or
     * null; the name is matched whole, whatever its case.
     */
    public static ChatNamedPlayer find(List<ChatNamedPlayer> named,
                                       String name) {
        if (named == null || name == null) {
            return null;
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        if (wanted.length() == 0) {
            return null;
        }
        for (int index = 0; index < named.size(); index++) {
            ChatNamedPlayer entry = named.get(index);
            if (entry != null && (entry.account.toLowerCase(Locale.ROOT)
                    .equals(wanted) || entry.identityName
                    .toLowerCase(Locale.ROOT).equals(wanted))) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Whether the text names the account: the name whole, with nothing
     * of a name's own characters on either side of it, so {@code Sam}
     * is not named by {@code Samwise}.
     */
    public static boolean names(String text, String account) {
        if (text == null || account == null || account.length() == 0) {
            return false;
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        String needle = account.toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) {
                return false;
            }
            int end = at + needle.length();
            boolean startsClean = at == 0
                    || !isNameCharacter(haystack.charAt(at - 1));
            boolean endsClean = end >= haystack.length()
                    || !isNameCharacter(haystack.charAt(end));
            if (startsClean && endsClean) {
                return true;
            }
            from = at + 1;
        }
    }

    private static boolean isNameCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ChatNamedPlayer)) {
            return false;
        }
        ChatNamedPlayer that = (ChatNamedPlayer)other;
        return same(this.playerId, that.playerId)
                && this.account.equals(that.account)
                && same(this.characterId, that.characterId)
                && this.identityName.equals(that.identityName)
                && this.skinId.equals(that.skinId);
    }

    private static boolean same(UUID one, UUID other) {
        return one == null ? other == null : one.equals(other);
    }

    @Override
    public int hashCode() {
        int hash = this.playerId == null ? 0 : this.playerId.hashCode();
        hash = hash * 31 + this.account.hashCode();
        hash = hash * 31 + (this.characterId == null ? 0
                : this.characterId.hashCode());
        hash = hash * 31 + this.identityName.hashCode();
        return hash * 31 + this.skinId.hashCode();
    }

    @Override
    public String toString() {
        return this.account + " as " + this.identityName;
    }
}
