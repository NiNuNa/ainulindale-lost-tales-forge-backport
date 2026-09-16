package com.ninuna.losttales.chat;

import java.util.List;
import java.util.Locale;

/**
 * A player a server line names, as the server knew them when the line
 * was said: the account, the identity it was playing, and the colour
 * that identity's name is drawn in.
 *
 * <p>A live client turns an account name inside an achievement, a join
 * or a death into the player's character the way every line of theirs
 * is signed, from the appearances the server syncs for everyone
 * online. A line replayed from the history names players who may be
 * long gone, so the server records the answer beside the line, and the
 * replay reads it from there.</p>
 */
public final class ChatNamedPlayer {
    /** The most players one line names; a death names two. */
    public static final int MAX_PER_LINE = 8;
    public static final int MAX_ACCOUNT_BYTES = 64;
    public static final int MAX_IDENTITY_BYTES = 256;

    private final String account;
    private final String identityName;
    private final int nameColor;

    public ChatNamedPlayer(String account, String identityName,
                           int nameColor) {
        this.account = account == null ? "" : account.trim();
        String identity = identityName == null ? "" : identityName.trim();
        this.identityName = identity.length() == 0 ? this.account : identity;
        this.nameColor = nameColor & 0xFFFFFF;
    }

    public String getAccount() {
        return this.account;
    }

    /** The name the player's lines were signed with when the line was said. */
    public String getIdentityName() {
        return this.identityName;
    }

    public int getNameColor() {
        return this.nameColor;
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
        return this.account.equals(that.account)
                && this.identityName.equals(that.identityName)
                && this.nameColor == that.nameColor;
    }

    @Override
    public int hashCode() {
        return (this.account.hashCode() * 31 + this.identityName.hashCode())
                * 31 + this.nameColor;
    }

    @Override
    public String toString() {
        return this.account + " as " + this.identityName;
    }
}
