package com.ninuna.losttales.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One mentionable player as the completion list sees it: a stable key so the
 * same player never appears twice, the name that is displayed and inserted
 * for the current channel, and every alias that may find the player while
 * typing. In OOC the displayed name is the Minecraft account and the active
 * character name is only an alias; in role-play channels it is the other way
 * round. Which identity is displayed is presentation only — mention
 * detection on the receiving side still checks both names. The account and
 * character names are kept separately so a hover card can describe the
 * player the same way a chat line's name does.
 *
 * <p>A candidate is either a player or a role. A player carries the
 * account id its face is drawn from; a role carries the colour it is
 * named in and reaches everyone holding it. Roles are listed first, so
 * addressing a whole group is never buried under a list of names.</p>
 */
public final class ChatMentionCandidate {
    private final String key;
    private final String displayName;
    private final String accountName;
    private final String characterName;
    private final String accountId;
    private final int roleColor;
    private final List<String> aliases;

    public ChatMentionCandidate(String key, String displayName,
                                List<String> aliases) {
        this(key, displayName, displayName, "", aliases);
    }

    public ChatMentionCandidate(String key, String displayName,
                                String accountName, String characterName,
                                List<String> aliases) {
        this(key, displayName, accountName, characterName, aliases, "", -1);
    }

    /**
     * A role the whole server can be addressed by: named in its own
     * colour, with no face of its own.
     */
    public static ChatMentionCandidate role(String key, String name,
                                            int color) {
        return new ChatMentionCandidate(key, name, "", "", null, "",
                color & 0xFFFFFF);
    }

    /** A player, with the account id their face is drawn from. */
    public static ChatMentionCandidate player(String key, String displayName,
                                              String accountName,
                                              String characterName,
                                              String accountId,
                                              List<String> aliases) {
        return new ChatMentionCandidate(key, displayName, accountName,
                characterName, aliases, accountId, -1);
    }

    private ChatMentionCandidate(String key, String displayName,
                                 String accountName, String characterName,
                                 List<String> aliases, String accountId,
                                 int roleColor) {
        String trimmedDisplay = displayName == null ? "" : displayName.trim();
        this.key = key == null || key.trim().length() == 0
                ? trimmedDisplay.toLowerCase(Locale.ROOT) : key.trim();
        this.displayName = trimmedDisplay;
        this.accountName = accountName == null ? "" : accountName.trim();
        this.characterName = characterName == null ? ""
                : characterName.trim();
        List<String> normalized = new ArrayList<String>(2);
        addAlias(normalized, trimmedDisplay);
        if (aliases != null) {
            for (int index = 0; index < aliases.size(); index++) {
                addAlias(normalized, aliases.get(index));
            }
        }
        this.aliases = Collections.unmodifiableList(normalized);
        this.accountId = accountId == null ? "" : accountId.trim();
        this.roleColor = roleColor;
    }

    /**
     * The account id this candidate's face is drawn from, or empty when
     * there is none to draw — a role, or a player the client has not
     * placed yet.
     */
    public String getAccountId() {
        return this.accountId;
    }

    /** Whether this names a role rather than one player. */
    public boolean isRole() {
        return this.roleColor >= 0;
    }

    /** The colour a role is named in; -1 for a player. */
    public int getRoleColor() {
        return this.roleColor;
    }

    /** Convenience for a player known by a single name. */
    public static ChatMentionCandidate single(String key, String name) {
        return new ChatMentionCandidate(key, name, null);
    }

    private static void addAlias(List<String> target, String alias) {
        String trimmed = alias == null ? "" : alias.trim();
        if (trimmed.length() == 0) {
            return;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!target.contains(lower)) {
            target.add(lower);
        }
    }

    /** Stable identity used for deduplication, never shown to the player. */
    public String getKey() {
        return this.key;
    }

    /** Name shown in the list and inserted after the {@code @}. */
    public String getDisplayName() {
        return this.displayName;
    }

    /** Minecraft account name; equals the display name when nothing else is known. */
    public String getAccountName() {
        return this.accountName;
    }

    /** Active character name, or empty when the player has none. */
    public String getCharacterName() {
        return this.characterName;
    }

    /** Lowercased searchable names, the display name first. */
    public List<String> getAliases() {
        return this.aliases;
    }

    public boolean isUsable() {
        return this.displayName.length() > 0;
    }

    /** True when any alias starts with the lowercased prefix. */
    public boolean matches(String lowercasePrefix) {
        if (lowercasePrefix == null) {
            return false;
        }
        for (int index = 0; index < this.aliases.size(); index++) {
            if (this.aliases.get(index).startsWith(lowercasePrefix)) {
                return true;
            }
        }
        return false;
    }
}
