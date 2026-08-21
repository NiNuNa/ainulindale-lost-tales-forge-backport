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
 */
public final class ChatMentionCandidate {
    private final String key;
    private final String displayName;
    private final String accountName;
    private final String characterName;
    private final List<String> aliases;

    public ChatMentionCandidate(String key, String displayName,
                                List<String> aliases) {
        this(key, displayName, displayName, "", aliases);
    }

    public ChatMentionCandidate(String key, String displayName,
                                String accountName, String characterName,
                                List<String> aliases) {
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
