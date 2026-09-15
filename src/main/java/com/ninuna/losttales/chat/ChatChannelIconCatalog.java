package com.ninuna.losttales.chat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The icons this server puts on its channels, by channel id: what the
 * channels file names, installed with the rest of the file and sent to
 * every client with its chat access. A channel not named here wears the
 * emoji the code gives it. Config-owned like {@link ChatChannelGates}:
 * every load of the file replaces the whole set.
 */
public final class ChatChannelIconCatalog {
    /** The most channels an icon may be sent for: the wire's channel count. */
    public static final int MAX_ICONS = 64;

    private static Map<String, ChatChannelIconSpec> current =
            Collections.emptyMap();

    private ChatChannelIconCatalog() {}

    /** The icons in force, by channel id; never null. */
    public static synchronized Map<String, ChatChannelIconSpec> current() {
        return current;
    }

    /**
     * Puts the given icons in force, in the order given, keyed by the
     * channel id in lower case; entries that name nothing, and any past
     * the bound, are left out.
     */
    public static synchronized void install(
            Map<String, ChatChannelIconSpec> icons) {
        Map<String, ChatChannelIconSpec> kept =
                new LinkedHashMap<String, ChatChannelIconSpec>();
        if (icons != null) {
            for (Map.Entry<String, ChatChannelIconSpec> entry
                    : icons.entrySet()) {
                String id = entry.getKey() == null ? ""
                        : entry.getKey().trim().toLowerCase(Locale.ROOT);
                if (id.length() == 0 || entry.getValue() == null
                        || kept.containsKey(id) || kept.size() >= MAX_ICONS) {
                    continue;
                }
                kept.put(id, entry.getValue());
            }
        }
        current = Collections.unmodifiableMap(kept);
    }

    /** No icon chosen for any channel: what stands before a file is read. */
    public static synchronized void resetToDefaults() {
        current = Collections.emptyMap();
    }
}
