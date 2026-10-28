package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.character.LostTalesClientAccount;
import com.ninuna.losttales.config.LostTalesConfigFiles;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.network.packet.LostTalesChatTypingSyncPacket;
import com.ninuna.losttales.util.LostTalesTextFiles;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who this player has chosen not to hear (CHAT-61): whole accounts, or
 * one character of an account, the account's other characters still
 * heard. A character is kept by its id, never its name, so a rename keeps
 * the ignore and a namesake is never hidden. Lines, typing and the dots
 * over a head all ask here by the same ids. It is a per-installation
 * preference like the emoji favorites — file-backed under {@code config/},
 * never synchronized, never cleared between worlds. The server still
 * delivers the lines; this client drops them on arrival, so ignoring is
 * invisible to the ignored.
 *
 * <p>A Discord member's typing names no id, only a name; every dropped
 * line of an ignored Discord member teaches the session the name it was
 * signed with, so that typing is dropped too. Learned names live only for
 * the session; the file keeps ids alone.</p>
 */
public final class ClientChatIgnores {
    /** The folder under the client's, one file per account. */
    static final String FOLDER = LostTalesConfigFiles.CHAT_IGNORES;
    /** Safety bound on stored ignores; adding past it is refused. */
    public static final int MAX_IGNORES = 256;
    /** What a line of the file ignoring an account begins with. */
    private static final String ACCOUNT_LINE = "ignore";
    /** What a line of the file ignoring one character begins with. */
    private static final String CHARACTER_LINE = "ignore-character";

    private static File storeFile;
    private static final Map<UUID, String> ignoredAccounts =
            new LinkedHashMap<UUID, String>();
    /** Characters ignored on their own, by id: their account and the name they were last seen by. */
    private static final Map<UUID, IgnoredCharacter> ignoredCharacters =
            new LinkedHashMap<UUID, IgnoredCharacter>();
    /** Lower-cased names known to belong to ignored accounts, for typing that names no id. */
    private static final Set<String> knownNames = new HashSet<String>();

    /** One character ignored on its own. */
    private static final class IgnoredCharacter {
        final UUID accountId;
        final String name;

        IgnoredCharacter(UUID accountId, String name) {
            this.accountId = accountId;
            this.name = name == null ? "" : name.trim();
        }
    }

    private ClientChatIgnores() {}

    /**
     * Reads the signed-in account's file as the client starts; with no
     * account to name, nothing is read or written.
     */
    public static synchronized void initialize(File configDirectory) {
        initialize(configDirectory, configDirectory == null ? null
                : LostTalesClientAccount.signedInId());
    }

    static synchronized void initialize(File configDirectory, UUID accountId) {
        storeFile = configDirectory == null || accountId == null ? null
                : new File(new File(configDirectory, FOLDER), accountId + ".txt");
        ignoredAccounts.clear();
        knownNames.clear();
        ignoredCharacters.clear();
        load();
    }

    public static synchronized boolean isIgnored(UUID accountId) {
        return accountId != null && ignoredAccounts.containsKey(accountId);
    }

    /** Whether one character is ignored on its own, by its id. */
    public static synchronized boolean isIgnoredCharacter(UUID characterId) {
        return characterId != null && ignoredCharacters.containsKey(characterId);
    }

    /**
     * Whether a line arriving is dropped: one said by an ignored account,
     * or worn by an ignored character. The player's own lines, {@code
     * localAccount}'s, are never dropped, and nor is a line that names
     * nobody (the Narrator's, an unseen voice's).
     */
    public static synchronized boolean isIgnoredLine(
            LostTalesChatMessagePacket packet, UUID localAccount) {
        if (packet == null || packet.getSenderId() == null
                || packet.getSenderId().equals(localAccount)) {
            return false;
        }
        return isIgnored(packet.getSenderId())
                || isIgnoredCharacter(packet.getIdentityCharacterId());
    }

    /**
     * Whether a typing notice is dropped, by the same ids a line is: the
     * typist's account or the character they type as. A notice naming no
     * id — a Discord member's — goes by the names this session learned
     * from that member's dropped lines; the Narrator and an unseen voice
     * name nobody and are never dropped.
     */
    public static synchronized boolean isIgnoredTyping(
            LostTalesChatTypingSyncPacket packet) {
        if (packet == null || packet.isNarrator() || packet.isUnseen()) {
            return false;
        }
        if (packet.getAccountId() != null) {
            return isIgnored(packet.getAccountId())
                    || isIgnoredCharacter(packet.getCharacterId());
        }
        return isIgnoredName(packet.getIdentityName());
    }

    /**
     * Starts ignoring one character of an account, the account's other
     * characters still heard; false when the list is full. The list is
     * one budget for accounts and characters alike.
     */
    public static synchronized boolean ignoreCharacter(UUID accountId,
                                                       UUID characterId,
                                                       String name) {
        if (accountId == null || characterId == null) {
            return false;
        }
        if (!ignoredCharacters.containsKey(characterId) && count() >= MAX_IGNORES) {
            return false;
        }
        ignoredCharacters.put(characterId, new IgnoredCharacter(accountId, name));
        save();
        return true;
    }

    /** Stops ignoring one character; false when it was not ignored. */
    public static synchronized boolean unignoreCharacter(UUID characterId) {
        if (characterId == null || ignoredCharacters.remove(characterId) == null) {
            return false;
        }
        save();
        return true;
    }

    /** Whether the name is known to belong to an ignored account. */
    public static synchronized boolean isIgnoredName(String name) {
        String key = nameKey(name);
        return key.length() > 0 && knownNames.contains(key);
    }

    /** Starts ignoring an account; false when the list is full. */
    public static synchronized boolean ignore(UUID accountId,
                                              String accountName) {
        if (accountId == null) {
            return false;
        }
        if (!ignoredAccounts.containsKey(accountId) && count() >= MAX_IGNORES) {
            return false;
        }
        String name = accountName == null ? "" : accountName.trim();
        ignoredAccounts.put(accountId, name);
        String key = nameKey(name);
        if (key.length() > 0) {
            knownNames.add(key);
        }
        save();
        return true;
    }

    /** Stops ignoring an account; false when it was not ignored. */
    public static synchronized boolean unignore(UUID accountId) {
        if (accountId == null
                || ignoredAccounts.remove(accountId) == null) {
            return false;
        }
        rebuildKnownNames();
        save();
        return true;
    }

    /**
     * Teaches the session a name an ignored account was seen wearing —
     * the name a dropped line was signed with — so typing that names no
     * id can be dropped too. Nothing is learned for an account that is
     * not ignored, and nothing learned is saved.
     */
    public static synchronized void rememberName(UUID accountId,
                                                 String name) {
        String key = nameKey(name);
        if (key.length() > 0 && isIgnored(accountId)) {
            knownNames.add(key);
        }
    }

    /**
     * Forgets the names learned this session, keeping the stored account
     * names. Cleared on disconnect with the other session caches: a name
     * belongs to a server, and on another one it may be somebody else's.
     */
    public static synchronized void clearSessionNames() {
        rebuildKnownNames();
    }

    /** Every ignore kept, accounts and characters together. */
    public static synchronized int count() {
        return ignoredAccounts.size() + ignoredCharacters.size();
    }

    /** One ignore as a list shows it. */
    public static final class Ignored {
        public final UUID accountId;
        /** The character ignored on its own; null for the whole account. */
        public final UUID characterId;
        /** The name it goes by: the account's, or the character's as last seen. */
        public final String name;
        /** Whether it is one character of the account rather than all of it. */
        public final boolean identity;

        Ignored(UUID accountId, UUID characterId, String name) {
            this.accountId = accountId;
            this.characterId = characterId;
            this.name = name;
            this.identity = characterId != null;
        }
    }

    /** Every ignore, the accounts first, each in the order it was laid. */
    public static synchronized List<Ignored> ignored() {
        List<Ignored> all = new ArrayList<Ignored>(count());
        for (Map.Entry<UUID, String> account : ignoredAccounts.entrySet()) {
            all.add(new Ignored(account.getKey(), null, account.getValue()));
        }
        for (Map.Entry<UUID, IgnoredCharacter> character
                : ignoredCharacters.entrySet()) {
            all.add(new Ignored(character.getValue().accountId,
                    character.getKey(), character.getValue().name));
        }
        return all;
    }

    private static void rebuildKnownNames() {
        knownNames.clear();
        for (String name : ignoredAccounts.values()) {
            String key = nameKey(name);
            if (key.length() > 0) {
                knownNames.add(key);
            }
        }
    }

    private static String nameKey(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static void load() {
        File file = storeFile;
        if (file == null || !file.isFile()) {
            return;
        }
        // Unreadable, the list starts empty.
        List<String> lines = LostTalesTextFiles.readLines(file, 2 * MAX_IGNORES);
        if (lines != null) {
            for (String line : lines) {
                parseLine(line.trim());
            }
        }
        rebuildKnownNames();
    }

    /**
     * One line of the file: {@code ignore <account> [name]} for an account,
     * {@code ignore-character <account> <character> [name]} for one
     * character, its name the rest of the line. A line that says anything
     * else, or names no id, is dropped.
     */
    static void parseLine(String line) {
        String[] parts = line.split(" ");
        if (parts.length < 2 || count() >= MAX_IGNORES) {
            return;
        }
        UUID accountId = idOf(parts[1]);
        if (accountId == null) {
            return;
        }
        if (ACCOUNT_LINE.equals(parts[0]) && parts.length <= 3) {
            ignoredAccounts.put(accountId, parts.length == 3 ? parts[2] : "");
        } else if (CHARACTER_LINE.equals(parts[0]) && parts.length >= 3) {
            UUID characterId = idOf(parts[2]);
            if (characterId == null) {
                return;
            }
            // A character's name may hold spaces: the rest of the line.
            StringBuilder name = new StringBuilder();
            for (int index = 3; index < parts.length; index++) {
                if (name.length() > 0) {
                    name.append(' ');
                }
                name.append(parts[index]);
            }
            ignoredCharacters.put(characterId,
                    new IgnoredCharacter(accountId, name.toString()));
        }
    }

    private static UUID idOf(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /** The file's lines, accounts first: what {@link #parseLine} reads back. */
    static synchronized List<String> describe() {
        List<String> lines = new ArrayList<String>(count());
        for (Map.Entry<UUID, String> entry : ignoredAccounts.entrySet()) {
            lines.add(ACCOUNT_LINE + " " + entry.getKey()
                    + (entry.getValue().length() > 0
                            ? " " + entry.getValue() : ""));
        }
        for (Map.Entry<UUID, IgnoredCharacter> entry
                : ignoredCharacters.entrySet()) {
            IgnoredCharacter character = entry.getValue();
            lines.add(CHARACTER_LINE + " " + character.accountId + " "
                    + entry.getKey()
                    + (character.name.length() > 0 ? " " + character.name : ""));
        }
        return lines;
    }

    private static void save() {
        File file = storeFile;
        if (file == null) {
            return;
        }
        LostTalesTextFiles.writeLines(file, describe());
    }
}
