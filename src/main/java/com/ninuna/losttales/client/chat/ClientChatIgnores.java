package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.config.LostTalesConfigFiles;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The accounts this player has chosen not to hear. An ignore is keyed by
 * the Minecraft account, so no character an ignored player speaks as
 * reaches this client, and it is a per-installation preference like the
 * emoji favorites — file-backed under {@code config/}, never synchronized,
 * never cleared between worlds. The server still delivers the lines; this
 * client drops them on arrival, so ignoring is invisible to the ignored.
 *
 * <p>Beside the stored accounts the session learns names: every dropped
 * line teaches the identity it was signed with, so typing indicators —
 * which carry a name rather than an account — can be dropped too. Learned
 * names live only for the session; the file keeps accounts alone.</p>
 */
public final class ClientChatIgnores {
    /** Under the client's config folder. */
    static final String FILE_PATH = LostTalesConfigFiles.CHAT_IGNORES;
    /** Safety bound on stored ignores; adding past it is refused. */
    public static final int MAX_IGNORES = 256;
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static File storeFile;
    private static final Map<UUID, String> ignoredAccounts =
            new LinkedHashMap<UUID, String>();
    /** Lower-cased names known to belong to ignored accounts. */
    private static final Set<String> knownNames = new HashSet<String>();
    /**
     * Identities ignored on their own — one character of an account,
     * the account's other characters still heard — keyed by the
     * account id and the identity's lower-cased name, the name as it
     * was seen kept for the file and the notices.
     */
    private static final Map<String, String> ignoredIdentities =
            new LinkedHashMap<String, String>();
    /** Lower-cased names of the ignored identities, for presence. */
    private static final Set<String> ignoredIdentityNames =
            new HashSet<String>();

    private ClientChatIgnores() {}

    public static synchronized void initialize(File configDirectory) {
        storeFile = configDirectory == null
                ? null : new File(configDirectory, FILE_PATH);
        ignoredAccounts.clear();
        knownNames.clear();
        ignoredIdentities.clear();
        ignoredIdentityNames.clear();
        load();
    }

    public static synchronized boolean isIgnored(UUID accountId) {
        return accountId != null && ignoredAccounts.containsKey(accountId);
    }

    /** Whether one identity of an account is ignored on its own. */
    public static synchronized boolean isIgnoredIdentity(UUID accountId,
                                                         String identityName) {
        String key = identityKey(accountId, identityName);
        return key != null && ignoredIdentities.containsKey(key);
    }

    /**
     * Whether the name is an identity ignored on its own — for presence,
     * which carries a name and no account.
     */
    public static synchronized boolean isIgnoredIdentityName(String name) {
        String key = nameKey(name);
        return key.length() > 0 && ignoredIdentityNames.contains(key);
    }

    /**
     * Starts ignoring one identity of an account, the account's other
     * identities still heard; false when the list is full. The list is
     * one budget for accounts and identities alike.
     */
    public static synchronized boolean ignoreIdentity(UUID accountId,
                                                      String identityName) {
        String key = identityKey(accountId, identityName);
        if (key == null) {
            return false;
        }
        if (!ignoredIdentities.containsKey(key) && count() >= MAX_IGNORES) {
            return false;
        }
        ignoredIdentities.put(key, identityName.trim());
        ignoredIdentityNames.add(nameKey(identityName));
        save();
        return true;
    }

    /** Stops ignoring one identity; false when it was not ignored. */
    public static synchronized boolean unignoreIdentity(UUID accountId,
                                                        String identityName) {
        String key = identityKey(accountId, identityName);
        if (key == null || ignoredIdentities.remove(key) == null) {
            return false;
        }
        rebuildIdentityNames();
        save();
        return true;
    }

    private static String identityKey(UUID accountId, String identityName) {
        String name = nameKey(identityName);
        return accountId == null || name.length() == 0 ? null
                : accountId + "|" + name;
    }

    private static void rebuildIdentityNames() {
        ignoredIdentityNames.clear();
        for (String name : ignoredIdentities.values()) {
            ignoredIdentityNames.add(nameKey(name));
        }
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
        if (!ignoredAccounts.containsKey(accountId)
                && ignoredAccounts.size() >= MAX_IGNORES) {
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
     * the identity a dropped line was signed with — so presence carrying
     * only that name can be dropped too. Nothing is learned for an
     * account that is not ignored, and nothing learned is saved.
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

    /** Every ignore kept, accounts and identities together. */
    public static synchronized int count() {
        return ignoredAccounts.size() + ignoredIdentities.size();
    }

    /** One ignore as a list shows it. */
    public static final class Ignored {
        public final UUID accountId;
        /** The name it goes by: the account's, or the one identity's. */
        public final String name;
        /** Whether it is one identity of the account rather than all of it. */
        public final boolean identity;

        Ignored(UUID accountId, String name, boolean identity) {
            this.accountId = accountId;
            this.name = name;
            this.identity = identity;
        }
    }

    /** Every ignore, the accounts first, each in the order it was laid. */
    public static synchronized List<Ignored> ignored() {
        List<Ignored> all = new ArrayList<Ignored>(count());
        for (Map.Entry<UUID, String> account : ignoredAccounts.entrySet()) {
            all.add(new Ignored(account.getKey(), account.getValue(), false));
        }
        for (Map.Entry<String, String> identity
                : ignoredIdentities.entrySet()) {
            String key = identity.getKey();
            int bar = key.indexOf('|');
            try {
                all.add(new Ignored(UUID.fromString(key.substring(0, bar)),
                        identity.getValue(), true));
            } catch (RuntimeException unreadable) {
                // Every key is made by identityKey; one that is not names
                // nobody, and the list leaves it out.
            }
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
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                parseLine(line.trim());
            }
        } catch (IOException ignored) {
            // Preferences are best-effort; starting empty is safe.
        } finally {
            closeQuietly(reader);
        }
        rebuildKnownNames();
    }

    private static void parseLine(String line) {
        String[] parts = line.split(" ");
        if (parts.length < 2 || count() >= MAX_IGNORES) {
            return;
        }
        UUID accountId;
        try {
            accountId = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException ignored) {
            // A malformed id names nobody; the line is dropped.
            return;
        }
        if ("ignore".equals(parts[0]) && parts.length <= 3) {
            ignoredAccounts.put(accountId, parts.length == 3 ? parts[2] : "");
        } else if ("ignore-identity".equals(parts[0]) && parts.length >= 3) {
            // A character's name may hold spaces: the rest of the line.
            StringBuilder name = new StringBuilder(parts[2]);
            for (int index = 3; index < parts.length; index++) {
                name.append(' ').append(parts[index]);
            }
            String key = identityKey(accountId, name.toString());
            if (key != null) {
                ignoredIdentities.put(key, name.toString().trim());
                ignoredIdentityNames.add(nameKey(name.toString()));
            }
        }
    }

    private static void save() {
        File file = storeFile;
        if (file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return;
        }
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(file), UTF_8);
            for (Map.Entry<UUID, String> entry : ignoredAccounts.entrySet()) {
                writer.write("ignore " + entry.getKey()
                        + (entry.getValue().length() > 0
                                ? " " + entry.getValue() : "") + "\n");
            }
            for (Map.Entry<String, String> entry : ignoredIdentities.entrySet()) {
                writer.write("ignore-identity "
                        + entry.getKey().substring(0, entry.getKey().indexOf('|'))
                        + " " + entry.getValue() + "\n");
            }
            for (Map.Entry<String, String> entry : ignoredIdentities.entrySet()) {
                writer.write("ignore-identity "
                        + entry.getKey().substring(0, entry.getKey().indexOf('|'))
                        + " " + entry.getValue() + "\n");
            }
        } catch (IOException ignored) {
            // Losing a preference write must never break chat.
        } finally {
            closeQuietly(writer);
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }
}
