package com.ninuna.losttales.permission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One thing a player may be allowed to do that the mod itself performs.
 * A capability is code, not configuration: it exists because somewhere
 * there is Java that carries the action out. What a server decides is
 * which of them a role reaches, through the permissions its config
 * defines ({@link LostTalesPermissionCatalog}).
 *
 * <p>The set is open. Every capability below is registered when this
 * class is first read, so the catalogue is complete from the first
 * question asked of it, and anything else may {@link #register} its own
 * — a capability added later needs no change here and no change to the
 * roles. Each names the vanilla operator level that holds it whatever
 * the roles say, so an operator is never refused something an operator
 * could do before roles existed. The id is the config and command
 * surface and is permanent.</p>
 */
public final class LostTalesCapability {

    private static final Map<String, LostTalesCapability> BY_ID =
            new LinkedHashMap<String, LostTalesCapability>();
    /** The level vanilla gives an operator; what every built-in capability asks. */
    private static final int OPERATOR = 2;

    /* ---- Chat ---- */

    /** Mute and unmute accounts, list the mutes, take anyone's message back. */
    public static final LostTalesCapability CHAT_MODERATE = register("chat.moderate", OPERATOR);
    /** Read the Server Console: commands run, moderation, config changes. */
    public static final LostTalesCapability CHAT_SERVER_CONSOLE_READ =
            register("chat.server_console.read", OPERATOR);
    /** Speak as the Narrator in the roleplaying channels and whispers. */
    public static final LostTalesCapability CHAT_NARRATE = register("chat.narrate", OPERATOR);

    /* ---- The server's own settings ---- */

    /** Create, restyle, delete and assign the config roles and permissions. */
    public static final LostTalesCapability ROLES_MANAGE = register("roles.manage", OPERATOR);
    /** Read and change every server-side config category, in game and by command. */
    public static final LostTalesCapability SERVER_CONFIG = register("server.config", OPERATOR);

    /* ---- Administration of characters, quests, fellowships, markers and the HUD ---- */

    /** Inspect, restore and purge the roleplay characters of any account. */
    public static final LostTalesCapability CHARACTER_ADMIN =
            register("character.admin", OPERATOR);
    /** Grant, revoke and inspect quest progress. */
    public static final LostTalesCapability QUEST_ADMIN = register("quest.admin", OPERATOR);
    /** Check and repair the fellowship stores, and clear the combat markers (/losttales fellowship). */
    public static final LostTalesCapability FELLOWSHIP_ADMIN = register("fellowship.admin", OPERATOR);
    /** Place, move and remove the shared map markers. */
    public static final LostTalesCapability MAPMARKER_MANAGE =
            register("mapmarker.manage", OPERATOR);
    /** Place public waystones and change their settings. */
    public static final LostTalesCapability WAYSTONE_MANAGE =
            register("waystone.manage", OPERATOR);
    /** Change the HUD defaults the server ships. */
    public static final LostTalesCapability HUD_ADMIN = register("hud.admin", OPERATOR);

    private final String id;
    private final int requiredOpLevel;

    private LostTalesCapability(String id, int requiredOpLevel) {
        this.id = id;
        this.requiredOpLevel = requiredOpLevel;
    }

    /**
     * Declares a capability and answers with it, or with the one already
     * registered under that id — registering twice is how a class that
     * is read again states the same capability, never a way to change
     * one. The id is what a permission names it by and is permanent:
     * letters, digits, dots and underscores.
     */
    public static synchronized LostTalesCapability register(String id, int requiredOpLevel) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() == 0) {
            throw new IllegalArgumentException("a capability needs an id");
        }
        LostTalesCapability existing = BY_ID.get(normalized);
        if (existing != null) {
            return existing;
        }
        LostTalesCapability capability = new LostTalesCapability(normalized,
                Math.max(0, Math.min(4, requiredOpLevel)));
        BY_ID.put(normalized, capability);
        return capability;
    }

    /** The permanent id the config and the commands name the capability by. */
    public String getId() {
        return this.id;
    }

    /** The vanilla operator level that holds the capability without any role. */
    public int getRequiredOpLevel() {
        return this.requiredOpLevel;
    }

    /** The capability with that id, case-insensitively; null for none. */
    public static synchronized LostTalesCapability byId(String id) {
        return id == null ? null : BY_ID.get(id.trim().toLowerCase(Locale.ROOT));
    }

    /** Every capability registered, in the order they were declared. */
    public static synchronized List<LostTalesCapability> all() {
        return Collections.unmodifiableList(
                new ArrayList<LostTalesCapability>(BY_ID.values()));
    }

    @Override
    public String toString() {
        return this.id;
    }
}
