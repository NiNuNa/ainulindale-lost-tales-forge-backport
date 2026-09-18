package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.client.character.LostTalesClientAccount;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The status this account last chose for each of its identities on each
 * server, kept between sessions, so a character left on Do Not Disturb or
 * Invisible is still so on the next visit. Online is the resting state
 * and is kept as no line.
 *
 * <p>A choice is the chooser's own, so it lives with the client's other
 * preferences: one file per account under the client folder, a line per
 * server and identity, written as the choice is made. Bounded, the
 * choices touched longest ago going first.</p>
 */
public final class ClientChatPresenceChoices {
    /** The folder under the client's, one file per account. */
    static final String FOLDER = LostTalesConfigFiles.CHAT_PRESENCE;
    /** Choices kept in all; the oldest touched go first past it. */
    static final int MAX_CHOICES = 512;
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final char SEPARATOR = '\t';

    private static File storeFile;
    /**
     * By server key and identity, in the order last touched, so trimming
     * drops what was chosen longest ago.
     */
    private static final LinkedHashMap<String, ChatPresence> CHOICES =
            new LinkedHashMap<String, ChatPresence>(32, 0.75F, true);

    private ClientChatPresenceChoices() {}

    /**
     * Reads the signed-in account's choices as the client starts; with no
     * account to name, nothing is read or written and every identity
     * starts Online.
     */
    public static synchronized void initialize(File configDirectory) {
        initialize(configDirectory, configDirectory == null ? null
                : LostTalesClientAccount.templateId());
    }

    /** As above, for a named account; visible for tests. */
    static synchronized void initialize(File configDirectory, UUID accountId) {
        storeFile = configDirectory == null || accountId == null ? null
                : new File(new File(configDirectory, FOLDER),
                        accountId.toString() + ".txt");
        CHOICES.clear();
        List<String> lines = readLines(storeFile);
        if (lines != null) {
            load(lines);
        }
    }

    /** The choices kept for one server, by identity. */
    static synchronized Map<ChatPresenceIdentity, ChatPresence> forPlace(
            String serverKey) {
        Map<ChatPresenceIdentity, ChatPresence> found =
                new LinkedHashMap<ChatPresenceIdentity, ChatPresence>();
        if (!isUsableKey(serverKey)) {
            return found;
        }
        String prefix = serverKey + SEPARATOR;
        for (Map.Entry<String, ChatPresence> entry : CHOICES.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                ChatPresenceIdentity identity = ChatPresenceIdentity.fromText(
                        entry.getKey().substring(prefix.length()));
                if (identity != null) {
                    found.put(identity, entry.getValue());
                }
            }
        }
        return found;
    }

    /** Keeps a choice for one identity on one server, and writes the file. */
    static synchronized void remember(String serverKey,
                                      ChatPresenceIdentity identity,
                                      ChatPresence presence) {
        if (!isUsableKey(serverKey) || identity == null || presence == null
                || !presence.isChoosable()) {
            return;
        }
        String key = serverKey + SEPARATOR + identity.toText();
        if (presence == ChatPresence.ONLINE) {
            CHOICES.remove(key);
        } else {
            CHOICES.put(key, presence);
            trim();
        }
        save();
    }

    /** The file's lines: one choice each, oldest touched first. */
    static synchronized List<String> describe() {
        List<String> lines = new ArrayList<String>(CHOICES.size() + 1);
        lines.add("# Lost Tales chat statuses: server, identity, status.");
        for (Map.Entry<String, ChatPresence> choice : CHOICES.entrySet()) {
            lines.add(choice.getKey() + SEPARATOR + choice.getValue().getId());
        }
        return lines;
    }

    /** Applies the file's lines; a line that does not parse is skipped. */
    static synchronized void load(List<String> lines) {
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.length() == 0 || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split(String.valueOf(SEPARATOR));
            if (parts.length != 3 || !isUsableKey(parts[0])) {
                continue;
            }
            ChatPresenceIdentity identity =
                    ChatPresenceIdentity.fromText(parts[1]);
            ChatPresence presence = ChatPresence.fromId(parts[2]);
            if (identity != null && presence != null && presence.isChoosable()
                    && presence != ChatPresence.ONLINE) {
                CHOICES.put(parts[0] + SEPARATOR + identity.toText(), presence);
                trim();
            }
        }
    }

    /** Forgets every choice without touching the file; for tests. */
    static synchronized void clear() {
        CHOICES.clear();
    }

    private static boolean isUsableKey(String serverKey) {
        return serverKey != null && serverKey.length() > 0
                && serverKey.indexOf(SEPARATOR) < 0;
    }

    private static void trim() {
        while (CHOICES.size() > MAX_CHOICES) {
            Iterator<String> oldest = CHOICES.keySet().iterator();
            oldest.next();
            oldest.remove();
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
            for (String line : describe()) {
                writer.write(line);
                writer.write('\n');
            }
        } catch (IOException ignored) {
            // Losing a remembered status must never break chat.
        } finally {
            closeQuietly(writer);
        }
    }

    private static List<String> readLines(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), UTF_8));
            List<String> lines = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        } catch (IOException ignored) {
            return null;
        } finally {
            closeQuietly(reader);
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
