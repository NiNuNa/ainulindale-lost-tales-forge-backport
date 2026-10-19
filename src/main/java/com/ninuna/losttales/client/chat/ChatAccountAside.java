package com.ninuna.losttales.client.chat;

/**
 * The account behind a character as it stands after the character's name
 * on a line and in a member row ({@code Aldric (Steve)}) while this
 * player's Show Accounts is on (CHAR-26), and on every card whatever it
 * says. Only a character names one: an account's own line or row is the
 * account already.
 */
public final class ChatAccountAside {
    private ChatAccountAside() {}

    /**
     * {@code " (Steve)"} for a character {@code name} played by
     * {@code account}, empty for an account's own identity, for a name
     * that is the account's own, and where either is missing.
     */
    public static String suffix(String name, String account, boolean character) {
        String shownName = name == null ? "" : name.trim();
        String shownAccount = account == null ? "" : account.trim();
        if (!character || shownName.length() == 0 || shownAccount.length() == 0
                || shownName.equalsIgnoreCase(shownAccount)) {
            return "";
        }
        return " (" + shownAccount + ")";
    }
}
