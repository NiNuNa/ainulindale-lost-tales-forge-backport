package com.ninuna.losttales.chat;

/**
 * The operator role as a fresh roles file defines it, for tests that
 * need a config role beside the team mark. The code itself knows no
 * operator role; this is the file's default, parsed.
 */
public final class ChatRoleFixtures {
    public static final String OPERATOR_ID = "operator";
    /** The default operator entry, with the bit the catalogue gives it (the first after the team mark). */
    public static final ChatAccountRole OPERATOR = catalogue().byId(OPERATOR_ID);

    private ChatRoleFixtures() {}

    /** The team mark and the default operator role, as a fresh server reads them. */
    public static ChatRoleCatalog catalogue() {
        return ChatRoleConfig.parse(new String[] {ChatRoleConfig.DEFAULT_OPERATOR_ENTRY},
                null, ChatRoleConfig.SILENT);
    }
}
