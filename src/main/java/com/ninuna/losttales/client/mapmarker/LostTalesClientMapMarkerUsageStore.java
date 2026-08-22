package com.ninuna.losttales.client.mapmarker;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Client-side map marker preferences keyed by marker id: the markers the
 * player has favourited and the destinations they most recently travelled
 * to. Like the emote preferences this is a per-installation presentation
 * preference under {@code config/}, never world state, never synchronized
 * and never cleared between worlds; an id that names no visible marker on
 * the current server is simply kept and not shown. Travel is recorded
 * when the player confirms a fast-travel destination on the map.
 */
public final class LostTalesClientMapMarkerUsageStore {
    static final String FILE_PATH = "losttales/map_markers.txt";
    static final int MAX_FAVORITES = 64;
    static final int MAX_RECENT = 8;
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String FAVORITE = "favorite";
    private static final String RECENT = "recent";

    private static File storeFile;
    private static final LinkedHashSet<String> FAVORITE_IDS =
            new LinkedHashSet<String>();
    /** Most recent destination first. */
    private static final ArrayList<String> RECENT_IDS = new ArrayList<String>();

    private LostTalesClientMapMarkerUsageStore() {}

    public static synchronized void initialize(File configDirectory) {
        storeFile = configDirectory == null
                ? null : new File(configDirectory, FILE_PATH);
        FAVORITE_IDS.clear();
        RECENT_IDS.clear();
        load();
    }

    public static synchronized boolean isFavorite(String markerId) {
        return markerId != null && FAVORITE_IDS.contains(markerId);
    }

    /** Toggles a favourite; true when the marker is a favourite afterwards. */
    public static synchronized boolean toggleFavorite(String markerId) {
        if (markerId == null || markerId.length() == 0) {
            return false;
        }
        boolean favorite;
        if (FAVORITE_IDS.remove(markerId)) {
            favorite = false;
        } else {
            FAVORITE_IDS.add(markerId);
            while (FAVORITE_IDS.size() > MAX_FAVORITES) {
                FAVORITE_IDS.remove(FAVORITE_IDS.iterator().next());
            }
            favorite = true;
        }
        save();
        return favorite;
    }

    /** Moves a destination to the head of the recent list. */
    public static synchronized void recordTravel(String markerId) {
        if (markerId == null || markerId.length() == 0) {
            return;
        }
        RECENT_IDS.remove(markerId);
        RECENT_IDS.add(0, markerId);
        while (RECENT_IDS.size() > MAX_RECENT) {
            RECENT_IDS.remove(RECENT_IDS.size() - 1);
        }
        save();
    }

    /** Favourite marker ids in the order they were added. */
    public static synchronized List<String> getFavorites() {
        return Collections.unmodifiableList(
                new ArrayList<String>(FAVORITE_IDS));
    }

    /** Recently travelled-to marker ids, most recent first. */
    public static synchronized List<String> getRecentlyTravelled() {
        return Collections.unmodifiableList(new ArrayList<String>(RECENT_IDS));
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

    /** {@code favorite <id>} or {@code recent <id>}; ids may contain spaces. */
    private static void parseLine(String line) {
        int space = line.indexOf(' ');
        if (space <= 0 || space == line.length() - 1) {
            return;
        }
        String kind = line.substring(0, space);
        String id = line.substring(space + 1).trim();
        if (id.length() == 0) {
            return;
        }
        if (FAVORITE.equals(kind) && FAVORITE_IDS.size() < MAX_FAVORITES) {
            FAVORITE_IDS.add(id);
        } else if (RECENT.equals(kind) && RECENT_IDS.size() < MAX_RECENT
                && !RECENT_IDS.contains(id)) {
            RECENT_IDS.add(id);
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
            for (String id : FAVORITE_IDS) {
                writer.write(FAVORITE + " " + id + "\n");
            }
            for (String id : RECENT_IDS) {
                writer.write(RECENT + " " + id + "\n");
            }
        } catch (IOException ignored) {
            // Losing a preference write must never break the map or chat.
        } finally {
            closeQuietly(writer);
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }
}
