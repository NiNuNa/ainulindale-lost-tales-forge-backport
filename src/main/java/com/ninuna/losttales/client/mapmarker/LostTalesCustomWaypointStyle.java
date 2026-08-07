package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.config.LostTalesConfig;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Icon colour and note a player has chosen for each of their own waypoints.
 *
 * <p>LOTR stores neither on a custom waypoint, so both are kept client-side
 * and are presentation only: nothing here reaches the server or another
 * player. Entries are keyed by the waypoint's name because that is the only
 * identity the client knows before the server has assigned an ID, and it is
 * what the player typed into the popup.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesCustomWaypointStyle {
    /** Colours offered by the creation popup, in the order they are drawn. */
    static final String[] PALETTE = {
            "white", "red", "orange", "yellow",
            "green", "blue", "purple", "gray"
    };
    static final String DEFAULT_PERSONAL_COLOR = "white";
    static final String DEFAULT_SHARED_COLOR = "blue";
    static final int MAX_NOTE_LENGTH = 80;
    private static final int MAX_ENTRIES = 256;
    private static final int MAX_KEY_LENGTH = 64;
    private static final char SEPARATOR = '=';

    private LostTalesCustomWaypointStyle() {}

    /** The colour chosen for a waypoint, or the default for its kind. */
    static String getColor(String waypointName, boolean shared) {
        String key = normalizeKey(waypointName);
        String stored = key.length() == 0 ? null : entries().get(key);
        return stored != null ? stored
                : shared ? DEFAULT_SHARED_COLOR : DEFAULT_PERSONAL_COLOR;
    }

    /**
     * Remembers a colour for a waypoint name. Storing the default clears the
     * entry rather than filling the file with values that change nothing.
     */
    static void setColor(String waypointName, String colorName) {
        String key = normalizeKey(waypointName);
        String color = normalizeColor(colorName);
        if (key.length() == 0) {
            return;
        }
        Map<String, String> entries = entries();
        boolean changed = DEFAULT_PERSONAL_COLOR.equals(color)
                ? entries.remove(key) != null
                : !color.equals(entries.put(key, color));
        if (!changed) {
            return;
        }
        LostTalesConfig.customWaypointColors = encode(entries);
        LostTalesConfig.save();
    }

    private static String[] encode(Map<String, String> entries) {
        List<String> encoded = new ArrayList<String>(entries.size());
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (encoded.size() >= MAX_ENTRIES) {
                break;
            }
            encoded.add(entry.getKey() + SEPARATOR + entry.getValue());
        }
        return encoded.toArray(new String[encoded.size()]);
    }

    /** The note shown under a waypoint's name, or an empty string. */
    static String getNote(String waypointName) {
        String key = normalizeKey(waypointName);
        String stored = key.length() == 0 ? null : notes().get(key);
        return stored == null ? "" : stored;
    }

    static void setNote(String waypointName, String note) {
        String key = normalizeKey(waypointName);
        String trimmed = normalizeNote(note);
        if (key.length() == 0) {
            return;
        }
        Map<String, String> notes = notes();
        boolean changed = trimmed.length() == 0
                ? notes.remove(key) != null
                : !trimmed.equals(notes.put(key, trimmed));
        if (changed) {
            LostTalesConfig.customWaypointNotes = encode(notes);
            LostTalesConfig.save();
        }
    }

    /** Drops everything stored for a waypoint, after a rename or deletion. */
    static void forget(String waypointName) {
        setColor(waypointName, DEFAULT_PERSONAL_COLOR);
        setNote(waypointName, "");
    }

    static boolean isKnownColor(String colorName) {
        String normalized = normalizeColor(colorName);
        for (String candidate : PALETTE) {
            if (candidate.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> entries() {
        return decode(LostTalesConfig.customWaypointColors, true);
    }

    private static Map<String, String> notes() {
        return decode(LostTalesConfig.customWaypointNotes, false);
    }

    private static Map<String, String> decode(
            String[] configured, boolean colors) {
        LinkedHashMap<String, String> entries =
                new LinkedHashMap<String, String>();
        if (configured == null) {
            return entries;
        }
        for (String value : configured) {
            if (entries.size() >= MAX_ENTRIES) {
                break;
            }
            int separator = value == null
                    ? -1 : value.indexOf(SEPARATOR);
            if (separator <= 0) {
                continue;
            }
            String key = normalizeKey(value.substring(0, separator));
            if (key.length() == 0) {
                continue;
            }
            String stored = value.substring(separator + 1);
            if (colors) {
                String color = normalizeColor(stored);
                if (isKnownColor(color)) {
                    entries.put(key, color);
                }
            } else {
                String note = normalizeNote(stored);
                if (note.length() > 0) {
                    entries.put(key, note);
                }
            }
        }
        return entries;
    }

    /** A note the config file round-trips safely, within its length limit. */
    static String normalizeNote(String note) {
        String trimmed = note == null ? "" : note.trim();
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NOTE_LENGTH);
        }
        StringBuilder safe = new StringBuilder(trimmed.length());
        for (int index = 0; index < trimmed.length(); index++) {
            char character = trimmed.charAt(index);
            if (character >= ' ' && character != '"'
                    && character != SEPARATOR && character != '\\') {
                safe.append(character);
            }
        }
        return safe.toString().trim();
    }

    /**
     * Waypoint names are compared case-insensitively and only in characters
     * the config file round-trips safely; anything else has no stored colour
     * and simply falls back to the default.
     */
    static String normalizeKey(String waypointName) {
        String normalized = waypointName == null
                ? "" : waypointName.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() == 0
                || normalized.length() > MAX_KEY_LENGTH) {
            return "";
        }
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character == SEPARATOR || character < ' '
                    || character == '"' || character == '\\') {
                return "";
            }
        }
        return normalized;
    }

    private static String normalizeColor(String colorName) {
        return colorName == null ? ""
                : colorName.trim().toLowerCase(Locale.ROOT);
    }
}
