package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMessageIds;
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
 * How far this account has read each conversation on each server: the
 * newest message id it was shown in a view, kept between sessions. A
 * server replays the recent history to a joining player; the marks say
 * which of those lines were seen before, so the unread divider stands
 * over the first line that was not, the way a messenger's does, rather
 * than over the whole of the replay or nowhere.
 *
 * <p>Read state is the reader's own, so it lives with the client's other
 * preferences: one file per account under the client folder, a line per
 * server and view. Nothing here is ever sent anywhere. Bounded, oldest
 * marks going first, so a player who visits many servers never grows a
 * file without end.</p>
 */
public final class ClientChatReadMarks {
    /** The folder under the client's, one file per account. */
    static final String FOLDER = LostTalesConfigFiles.CHAT_READ_MARKS;
    /** Marks kept in all; the oldest touched go first past it. */
    static final int MAX_MARKS = 1024;
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final char SEPARATOR = '\t';

    private static File storeFile;
    /**
     * By server key and view id, in the order last touched: a mark
     * moved forward goes to the end, so trimming drops what was read
     * longest ago.
     */
    private static final LinkedHashMap<String, Long> MARKS =
            new LinkedHashMap<String, Long>(64, 0.75F, true);
    private static boolean dirty;

    private ClientChatReadMarks() {}

    /**
     * Reads the signed-in account's marks as the client starts, the way
     * the layout is read; with no account to name, nothing is read or
     * written and every replayed line reads as new.
     */
    public static synchronized void initialize(File configDirectory) {
        initialize(configDirectory, configDirectory == null ? null
                : LostTalesClientAccount.templateId());
    }

    /** As above, for a named account; visible for tests. */
    static synchronized void initialize(File configDirectory, UUID accountId) {
        storeFile = fileFor(configDirectory, accountId);
        MARKS.clear();
        dirty = false;
        List<String> lines = readLines(storeFile);
        if (lines != null) {
            load(lines);
        }
    }

    static File fileFor(File configDirectory, UUID accountId) {
        if (configDirectory == null || accountId == null) {
            return null;
        }
        return new File(new File(configDirectory, FOLDER),
                accountId.toString() + ".txt");
    }

    /**
     * The newest message this account was shown in the view on that
     * server, or {@link ChatMessageIds#NONE} for a view never read there.
     */
    static synchronized long lastRead(String serverKey, ChatTab view) {
        String key = key(serverKey, view);
        if (key == null) {
            return ChatMessageIds.NONE;
        }
        Long mark = MARKS.get(key);
        return mark == null ? ChatMessageIds.NONE : mark.longValue();
    }

    /**
     * Moves the view's mark on that server forward to {@code messageId};
     * a mark never moves back, and an id the server never gave moves
     * nothing.
     */
    static synchronized void markRead(String serverKey, ChatTab view,
                                      long messageId) {
        String key = key(serverKey, view);
        if (key == null || !ChatMessageIds.isServerId(messageId)) {
            return;
        }
        Long current = MARKS.get(key);
        if (current != null && current.longValue() >= messageId) {
            return;
        }
        MARKS.put(key, Long.valueOf(messageId));
        dirty = true;
        while (MARKS.size() > MAX_MARKS) {
            Iterator<String> oldest = MARKS.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /** Writes the marks when any has moved since they were last written. */
    public static synchronized void save() {
        if (!dirty) {
            return;
        }
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
            dirty = false;
        } catch (IOException ignored) {
            // Losing a read mark must never break chat.
        } finally {
            closeQuietly(writer);
        }
    }

    /** The file's lines: one mark each, oldest touched first. */
    static synchronized List<String> describe() {
        List<String> lines = new ArrayList<String>(MARKS.size() + 1);
        lines.add("# Lost Tales chat read marks: server, view, newest message read.");
        for (Map.Entry<String, Long> mark : MARKS.entrySet()) {
            lines.add(mark.getKey() + SEPARATOR + mark.getValue());
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
            if (parts.length != 3 || parts[0].length() == 0
                    || parts[1].length() == 0) {
                continue;
            }
            try {
                long messageId = Long.parseLong(parts[2]);
                if (ChatMessageIds.isServerId(messageId)) {
                    MARKS.put(parts[0] + SEPARATOR + parts[1],
                            Long.valueOf(messageId));
                }
            } catch (NumberFormatException ignored) {
                // A mark nobody can read is a mark nobody had.
            }
            while (MARKS.size() > MAX_MARKS) {
                Iterator<String> oldest = MARKS.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
        }
    }

    /** Forgets every mark without touching the file; for tests. */
    static synchronized void clear() {
        MARKS.clear();
        dirty = false;
    }

    static synchronized int size() {
        return MARKS.size();
    }

    private static String key(String serverKey, ChatTab view) {
        if (serverKey == null || serverKey.length() == 0 || view == null) {
            return null;
        }
        String viewId = ChatTab.viewed(view).id();
        if (viewId.indexOf(SEPARATOR) >= 0
                || serverKey.indexOf(SEPARATOR) >= 0) {
            return null;
        }
        return serverKey + SEPARATOR + viewId;
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
