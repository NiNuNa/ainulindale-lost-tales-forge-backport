package com.ninuna.losttales.permission;

import java.util.Locale;

/**
 * One thing a player may be allowed to do that the mod decides for
 * itself. Every capability names the vanilla operator level that holds
 * it regardless of roles, so an operator is never refused anything an
 * operator could do before roles existed; a config role may grant it to
 * its holders besides ({@code grant:<id>} in {@code chat.roles}). The id
 * is the config and command surface and is permanent.
 */
public enum LostTalesCapability {
    /** Mute and unmute accounts, list the mutes, take anyone's message back. */
    CHAT_MODERATE("chat.moderate", 2,
            "mute and unmute accounts, list mutes, and remove anyone's message"),
    /** Create, restyle, delete and assign the config roles. */
    ROLES_MANAGE("roles.manage", 2,
            "create, edit, delete and assign chat roles"),
    /** Read and change every server-side config category, in game and by command. */
    SERVER_CONFIG("server.config", 2,
            "read and change the server's settings, the Discord bridge included"),
    /** Read the shared operator console: commands run, moderation, config changes. */
    CHAT_CONSOLE_READ("chat.console.read", 2,
            "read the shared operator console and talk in it");

    private final String id;
    private final int requiredOpLevel;
    private final String description;

    LostTalesCapability(String id, int requiredOpLevel, String description) {
        this.id = id;
        this.requiredOpLevel = requiredOpLevel;
        this.description = description;
    }

    /** The permanent id the config and the commands name the capability by. */
    public String getId() {
        return this.id;
    }

    /** The vanilla operator level that holds the capability without any role. */
    public int getRequiredOpLevel() {
        return this.requiredOpLevel;
    }

    /** What holding it allows, in plain words, for listings. */
    public String getDescription() {
        return this.description;
    }

    /** The capability with that id, case-insensitively; null for none. */
    public static LostTalesCapability byId(String id) {
        if (id == null) {
            return null;
        }
        String wanted = id.trim().toLowerCase(Locale.ROOT);
        for (LostTalesCapability capability : values()) {
            if (capability.id.equals(wanted)) {
                return capability;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return this.id;
    }
}
