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
    /** The level vanilla gives an operator; what every built-in capability asks but two. */
    private static final int OPERATOR = 2;
    /**
     * The level that holds the server's own settings and the roles
     * (DATA-07): an operator of level 2 does not rewrite the server's
     * settings, its Discord token or who holds which power.
     */
    private static final int SERVER_ADMINISTRATOR = 3;

    /* ---- Chat ---- */

    /** Mute and unmute accounts, list the mutes, take anyone's message back. */
    public static final LostTalesCapability CHAT_MODERATE = register("chat.moderate", OPERATOR);
    /** Read the Server Log: commands run, moderation, config changes. */
    public static final LostTalesCapability CHAT_SERVER_CONSOLE_READ =
            register("chat.server_console.read", OPERATOR);
    /** Speak as the Narrator in the roleplaying channels and whispers. */
    public static final LostTalesCapability CHAT_NARRATE = register("chat.narrate", OPERATOR);
    /**
     * Copy the ids the server's files and commands name a message, an
     * account or a character by, from their menus, as Discord's developer
     * mode does.
     */
    public static final LostTalesCapability CHAT_COPY_IDS = register("chat.copy_ids", OPERATOR);

    /* ---- The server's own settings ---- */

    /** Create, restyle, delete and assign the config roles and permissions. */
    public static final LostTalesCapability ROLES_MANAGE =
            register("roles.manage", SERVER_ADMINISTRATOR);
    /**
     * Read and change every server-side config category, in game and by
     * command; read every kind of content again at once
     * ({@code /losttales reload all}).
     */
    public static final LostTalesCapability SERVER_CONFIG =
            register("server.config", SERVER_ADMINISTRATOR);

    /* ---- Administration of characters, quests, fellowships, markers and the HUD ---- */

    /**
     * Inspect, restore and purge the roleplay characters of any account;
     * read the race, cape, starting kit and lore character files again
     * ({@code /losttales reload}).
     */
    public static final LostTalesCapability CHARACTER_ADMIN =
            register("character.admin", OPERATOR);
    /**
     * Grant, revoke and inspect quest progress; read the quest files and
     * the missive templates again ({@code /losttales reload}).
     */
    public static final LostTalesCapability QUEST_ADMIN = register("quest.admin", OPERATOR);
    /**
     * Check and repair the fellowship stores, and clear the combat markers
     * (/losttales fellowship); make a fellowship on the page while LOTR's
     * fellowship creation is off (FEL-30).
     */
    public static final LostTalesCapability FELLOWSHIP_ADMIN = register("fellowship.admin", OPERATOR);
    /**
     * Inspect players' map markers, discover, forget, track or untrack
     * them, and place failed waystones again (/losttales mapmarker); read
     * the marker files again (/losttales reload markers); see, edit,
     * break, make public and hand over any waystone.
     */
    public static final LostTalesCapability MAPMARKER_MANAGE =
            register("mapmarker.manage", OPERATOR);
    /** Ban an account, a character or everyone from spawning LOTR's structures (/losttales structures). */
    public static final LostTalesCapability STRUCTURES_BAN =
            register("structures.ban", OPERATOR);
    /**
     * Read the skill and deed files again (/losttales reload skills), see
     * and give skill experience (/losttales skills), and place and break
     * the statues that reset a skill's perks (PROG-12).
     */
    public static final LostTalesCapability PROGRESSION_ADMIN =
            register("progression.admin", OPERATOR);

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
