package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-side emoji preferences: favorites and how often each emoji has been
 * sent. This is a per-installation presentation preference like the camera
 * presets, not world state, so it is file-backed under {@code config/} and
 * never synchronized or cleared between worlds. Unknown emoji names are kept
 * out of memory but simply dropped on save, so a registry change cannot
 * corrupt the file or crash the client.
 */
public final class ChatEmojiUsageStore {
    static final String FILE_PATH = "losttales/chat_emojis.txt";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int MAX_COUNT = 1000000;

    private static File storeFile;
    private static final Set<String> favoriteNames = new HashSet<String>();
    private static final Map<String, Integer> useCounts =
            new HashMap<String, Integer>();

    private ChatEmojiUsageStore() {}

    public static synchronized void initialize(File configDirectory) {
        storeFile = configDirectory == null
                ? null : new File(configDirectory, FILE_PATH);
        favoriteNames.clear();
        useCounts.clear();
        load();
    }

    public static synchronized boolean isFavorite(ChatEmoji emoji) {
        return emoji != null && favoriteNames.contains(emoji.getName());
    }

    public static synchronized void toggleFavorite(ChatEmoji emoji) {
        if (emoji == null) {
            return;
        }
        if (!favoriteNames.remove(emoji.getName())) {
            favoriteNames.add(emoji.getName());
        }
        save();
    }

    public static synchronized void recordUse(ChatEmoji emoji) {
        if (emoji == null) {
            return;
        }
        Integer current = useCounts.get(emoji.getName());
        int count = current == null ? 0 : current.intValue();
        useCounts.put(emoji.getName(),
                Integer.valueOf(Math.min(MAX_COUNT, count + 1)));
        save();
    }

    /** Favorited emojis in registry order, for a stable picker grid. */
    public static synchronized List<ChatEmoji> getFavorites() {
        List<ChatEmoji> result = new ArrayList<ChatEmoji>();
        for (ChatEmoji emoji : ChatEmoji.values()) {
            if (favoriteNames.contains(emoji.getName())) {
                result.add(emoji);
            }
        }
        return result;
    }

    /** Most-sent emojis, ties broken by registry order. */
    public static synchronized List<ChatEmoji> getFrequentlyUsed(int limit) {
        List<ChatEmoji> used = new ArrayList<ChatEmoji>();
        for (ChatEmoji emoji : ChatEmoji.values()) {
            if (useCounts.containsKey(emoji.getName())) {
                used.add(emoji);
            }
        }
        Collections.sort(used, new Comparator<ChatEmoji>() {
            @Override
            public int compare(ChatEmoji left, ChatEmoji right) {
                int byCount = countOf(right) - countOf(left);
                return byCount != 0
                        ? byCount : left.ordinal() - right.ordinal();
            }
        });
        return used.size() > Math.max(0, limit)
                ? used.subList(0, Math.max(0, limit)) : used;
    }

    private static int countOf(ChatEmoji emoji) {
        Integer count = useCounts.get(emoji.getName());
        return count == null ? 0 : count.intValue();
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
    }

    private static void parseLine(String line) {
        String[] parts = line.split(" ");
        if (parts.length == 2 && "favorite".equals(parts[0])
                && ChatEmoji.fromName(parts[1]) != null) {
            favoriteNames.add(parts[1]);
            return;
        }
        if (parts.length == 3 && "count".equals(parts[0])
                && ChatEmoji.fromName(parts[1]) != null) {
            try {
                int count = Integer.parseInt(parts[2]);
                if (count > 0) {
                    useCounts.put(parts[1],
                            Integer.valueOf(Math.min(MAX_COUNT, count)));
                }
            } catch (NumberFormatException ignored) {
                // Malformed counts are dropped, not fatal.
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
            writer = new OutputStreamWriter(
                    new FileOutputStream(file), UTF_8);
            for (ChatEmoji emoji : ChatEmoji.values()) {
                if (favoriteNames.contains(emoji.getName())) {
                    writer.write("favorite " + emoji.getName() + "\n");
                }
            }
            for (ChatEmoji emoji : ChatEmoji.values()) {
                Integer count = useCounts.get(emoji.getName());
                if (count != null && count.intValue() > 0) {
                    writer.write("count " + emoji.getName()
                            + " " + count + "\n");
                }
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
