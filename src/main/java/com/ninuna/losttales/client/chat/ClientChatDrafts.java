package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.client.character.LostTalesClientAccount;
import com.ninuna.losttales.config.LostTalesConfigFiles;
import com.ninuna.losttales.util.LostTalesTextFiles;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The unsent text of each conversation on each server. It is kept between
 * sessions, so a crash or a lost connection does not take a long post
 * with it: one file per account under the client folder, a line per
 * server and conversation, gone once the text is sent or emptied.
 *
 * <p>A command is kept for the session only and never written, since it
 * may hold a password; so is text typed where the place has no name.
 * Nothing here is ever sent anywhere. Bounded, the oldest draft going
 * first.</p>
 */
public final class ClientChatDrafts {
    /** The folder under the client's, one file per account. */
    static final String FOLDER = LostTalesConfigFiles.CHAT_DRAFTS;
    /** Drafts kept in all; the oldest changed go first past it. */
    static final int MAX_DRAFTS = 256;
    /** The longest conversation id a line may name. */
    private static final int MAX_ID_LENGTH = 256;
    private static final char SEPARATOR = '\t';
    private static final char ESCAPE = '\\';

    private static File storeFile;
    /** By server key and conversation id, in the order last changed. */
    private static final LinkedHashMap<String, String> DRAFTS =
            new LinkedHashMap<String, String>();
    private static boolean dirty;

    private ClientChatDrafts() {}

    /**
     * Reads the signed-in account's drafts as the client starts; with no
     * account to name, nothing is read or written and a draft lives for
     * the session.
     */
    public static synchronized void initialize(File configDirectory) {
        initialize(configDirectory, configDirectory == null ? null
                : LostTalesClientAccount.templateId());
    }

    static synchronized void initialize(File configDirectory, UUID accountId) {
        storeFile = configDirectory == null || accountId == null ? null
                : new File(new File(configDirectory, FOLDER),
                        accountId.toString() + ".txt");
        DRAFTS.clear();
        dirty = false;
        List<String> lines = LostTalesTextFiles.readLines(storeFile,
                MAX_DRAFTS + 1);
        if (lines != null) {
            load(lines);
        }
    }

    /** The conversation's unsent text on that server; empty when it has none. */
    static synchronized String get(String serverKey, ChatTab tab) {
        String value = tab == null ? null : DRAFTS.get(key(serverKey, tab));
        return value == null ? "" : value;
    }

    /** Remembers the conversation's unsent text on that server; empty text forgets it. */
    static synchronized void set(String serverKey, ChatTab tab, String text) {
        if (tab == null) {
            return;
        }
        String key = key(serverKey, tab);
        String value = text == null ? "" : text;
        String held = DRAFTS.get(key);
        if (value.equals(held == null ? "" : held)) {
            return;
        }
        if (held != null && isWritten(key, held) || isWritten(key, value)) {
            dirty = true;
        }
        // Taken out first, so a changed draft is the newest again.
        DRAFTS.remove(key);
        if (value.length() > 0) {
            DRAFTS.put(key, value);
            trim();
        }
    }

    /**
     * Leaves the place: what is never written, the commands and the text
     * typed where the place has no name, is forgotten.
     */
    static synchronized void endSession() {
        Iterator<Map.Entry<String, String>> each =
                DRAFTS.entrySet().iterator();
        while (each.hasNext()) {
            Map.Entry<String, String> draft = each.next();
            if (!isWritten(draft.getKey(), draft.getValue())) {
                each.remove();
            }
        }
    }

    /** Writes the drafts when any has changed since they were last written. */
    public static synchronized void save() {
        if (!dirty || storeFile == null) {
            return;
        }
        if (LostTalesTextFiles.writeLines(storeFile, describe())) {
            dirty = false;
        }
    }

    /** The file's lines: one draft each, oldest changed first. */
    static synchronized List<String> describe() {
        List<String> lines = new ArrayList<String>(DRAFTS.size() + 1);
        lines.add("# Lost Tales chat drafts: server, conversation, unsent text.");
        for (Map.Entry<String, String> draft : DRAFTS.entrySet()) {
            if (isWritten(draft.getKey(), draft.getValue())) {
                lines.add(draft.getKey() + SEPARATOR
                        + escaped(draft.getValue()));
            }
        }
        return lines;
    }

    /** Applies the file's lines; a line that does not parse is skipped. */
    static synchronized void load(List<String> lines) {
        for (String line : lines) {
            if (line == null || line.length() == 0 || line.startsWith("#")) {
                continue;
            }
            int first = line.indexOf(SEPARATOR);
            int second = first < 0 ? -1 : line.indexOf(SEPARATOR, first + 1);
            if (first <= 0 || second <= first + 1
                    || second - first - 1 > MAX_ID_LENGTH) {
                continue;
            }
            String key = line.substring(0, second);
            String text = unescaped(line.substring(second + 1));
            if (isWritten(key, text)) {
                DRAFTS.put(key, text);
                trim();
            }
        }
    }

    /**
     * Whether a draft goes to the file: said somewhere with a name, no
     * command, and no longer than a post may be.
     */
    private static boolean isWritten(String key, String text) {
        return key.charAt(0) != SEPARATOR && text.length() > 0
                && text.length() <= ChatMessageValidator.MAX_CHARACTERS
                && text.charAt(0) != '/';
    }

    /** Past the bound the oldest drafts go, from the file too. */
    private static void trim() {
        while (DRAFTS.size() > MAX_DRAFTS) {
            Iterator<String> oldest = DRAFTS.keySet().iterator();
            oldest.next();
            oldest.remove();
            dirty = true;
        }
    }

    /** A server key that holds the separator names no place. */
    private static String key(String serverKey, ChatTab tab) {
        String server = serverKey == null
                || serverKey.indexOf(SEPARATOR) >= 0 ? "" : serverKey;
        return server + SEPARATOR + tab.id().toLowerCase(Locale.ROOT)
                .replace(SEPARATOR, ' ');
    }

    /** The text on one line: its breaks, tabs and backslashes written out. */
    private static String escaped(String text) {
        StringBuilder line = new StringBuilder(text.length() + 8);
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == ESCAPE) {
                line.append(ESCAPE).append(ESCAPE);
            } else if (character == '\n') {
                line.append(ESCAPE).append('n');
            } else if (character == '\r') {
                line.append(ESCAPE).append('r');
            } else if (character == SEPARATOR) {
                line.append(ESCAPE).append('t');
            } else {
                line.append(character);
            }
        }
        return line.toString();
    }

    private static String unescaped(String line) {
        StringBuilder text = new StringBuilder(line.length());
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character != ESCAPE || index + 1 >= line.length()) {
                text.append(character);
                continue;
            }
            char next = line.charAt(++index);
            if (next == 'n') {
                text.append('\n');
            } else if (next == 'r') {
                text.append('\r');
            } else if (next == 't') {
                text.append(SEPARATOR);
            } else {
                text.append(next);
            }
        }
        return text.toString();
    }
}
