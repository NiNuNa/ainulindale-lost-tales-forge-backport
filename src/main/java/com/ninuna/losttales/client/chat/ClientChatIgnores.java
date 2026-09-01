package com.ninuna.losttales.client.chat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    static final String FILE_PATH = "losttales/chat_ignores.txt";
    /** Safety bound on stored ignores; adding past it is refused. */
    public static final int MAX_IGNORES = 256;
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static File storeFile;
    private static final Map<UUID, String> ignoredAccounts =
            new LinkedHashMap<UUID, String>();
    /** Lower-cased names known to belong to ignored accounts. */
    private static final Set<String> knownNames = new HashSet<String>();

    private ClientChatIgnores() {}

    public static synchronized void initialize(File configDirectory) {
        storeFile = configDirectory == null
                ? null : new File(configDirectory, FILE_PATH);
        ignoredAccounts.clear();
        knownNames.clear();
        load();
    }

    public static synchronized boolean isIgnored(UUID accountId) {
        return accountId != null && ignoredAccounts.containsKey(accountId);
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

    public static synchronized int count() {
        return ignoredAccounts.size();
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
        if (parts.length < 2 || parts.length > 3
                || !"ignore".equals(parts[0])
                || ignoredAccounts.size() >= MAX_IGNORES) {
            return;
        }
        UUID accountId;
        try {
            accountId = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException ignored) {
            // A malformed id names nobody; the line is dropped.
            return;
        }
        ignoredAccounts.put(accountId, parts.length == 3 ? parts[2] : "");
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
