package com.ninuna.losttales.chat;

import java.util.UUID;

/**
 * Whose presence, among one account's identities: the account itself,
 * which is what the out-of-character channels speak as, or one of its
 * characters by id. The two are told apart by kind rather than by id,
 * since the default character's id is the account's own.
 */
public final class ChatPresenceIdentity {
    /** The account speaking as itself. */
    public static final ChatPresenceIdentity ACCOUNT =
            new ChatPresenceIdentity(null);
    /** How an identity is written in text: the account by this word, a character by its id. */
    private static final String ACCOUNT_TEXT = "account";

    private final UUID characterId;

    private ChatPresenceIdentity(UUID characterId) {
        this.characterId = characterId;
    }

    /** One of the account's characters; the account itself for no id. */
    public static ChatPresenceIdentity character(UUID characterId) {
        return characterId == null ? ACCOUNT
                : new ChatPresenceIdentity(characterId);
    }

    public boolean isAccount() {
        return this.characterId == null;
    }

    /** The character's id; null for the account. */
    public UUID getCharacterId() {
        return this.characterId;
    }

    /** The identity as a word for a file: the account, or the character's id. */
    public String toText() {
        return this.characterId == null ? ACCOUNT_TEXT
                : this.characterId.toString();
    }

    /** The identity {@link #toText} wrote; null for text that names none. */
    public static ChatPresenceIdentity fromText(String text) {
        if (ACCOUNT_TEXT.equals(text)) {
            return ACCOUNT;
        }
        try {
            return text == null || text.length() != 36 ? null
                    : character(UUID.fromString(text));
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChatPresenceIdentity)) {
            return false;
        }
        UUID theirs = ((ChatPresenceIdentity)other).characterId;
        return this.characterId == null ? theirs == null
                : this.characterId.equals(theirs);
    }

    @Override
    public int hashCode() {
        return this.characterId == null ? 0 : this.characterId.hashCode();
    }

    @Override
    public String toString() {
        return toText();
    }
}
