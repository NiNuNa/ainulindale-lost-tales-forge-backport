package com.ninuna.losttales.compat.discord;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Edits the {@code channelBindings} list the way the bind and unbind
 * commands need to: one entry per game channel key, written in the form
 * the parser reads. Pure string work; the service writes the list back
 * and the bridge restarts on it.
 */
public final class DiscordBindingEntries {

    private DiscordBindingEntries() {}

    /** {@code <key>=<direction>;channel=<id>;webhook=<url>} */
    public static String format(String key, DiscordBridgeDirection direction,
                                String discordChannelId, String webhookUrl) {
        return key.toLowerCase(Locale.ROOT) + "=" + direction.name()
                + ";channel=" + (discordChannelId == null ? "" : discordChannelId.trim())
                + ";webhook=" + (webhookUrl == null ? "" : webhookUrl.trim());
    }

    /**
     * The list with the entry for {@code key} replaced, or appended when
     * there was none. A comment line is left where it is. When the
     * existing entry names a channel or webhook the new one leaves blank,
     * the old value is kept, so a bind can change one side only.
     */
    public static List<String> upsert(String[] entries, String key,
                                      DiscordBridgeDirection direction,
                                      String discordChannelId, String webhookUrl) {
        List<String> result = new ArrayList<String>();
        boolean replaced = false;
        for (String entry : entries == null ? new String[0] : entries) {
            if (!replaced && key.equalsIgnoreCase(keyOf(entry))) {
                result.add(format(key, direction,
                        firstNonBlank(discordChannelId, optionOf(entry, "channel")),
                        firstNonBlank(webhookUrl, optionOf(entry, "webhook"))));
                replaced = true;
            } else {
                result.add(entry);
            }
        }
        if (!replaced) {
            result.add(format(key, direction, discordChannelId, webhookUrl));
        }
        return result;
    }

    /** The list without the entries for {@code key}; answers whether any went. */
    public static List<String> remove(String[] entries, String key) {
        List<String> result = new ArrayList<String>();
        for (String entry : entries == null ? new String[0] : entries) {
            if (!key.equalsIgnoreCase(keyOf(entry))) {
                result.add(entry);
            }
        }
        return result;
    }

    public static boolean contains(String[] entries, String key) {
        for (String entry : entries == null ? new String[0] : entries) {
            if (key.equalsIgnoreCase(keyOf(entry))) {
                return true;
            }
        }
        return false;
    }

    /** The game channel key an entry names: everything before its first '='. */
    public static String keyOf(String entry) {
        if (entry == null || entry.trim().startsWith("#")) {
            return "";
        }
        int equals = entry.indexOf('=');
        return (equals < 0 ? entry : entry.substring(0, equals)).trim()
                .toLowerCase(Locale.ROOT);
    }

    /** One {@code name=value} option of an entry, or empty. */
    public static String optionOf(String entry, String name) {
        if (entry == null) {
            return "";
        }
        int equals = entry.indexOf('=');
        String rest = equals < 0 ? "" : entry.substring(equals + 1);
        for (String part : rest.split(";")) {
            int split = part.indexOf('=');
            if (split > 0 && name.equalsIgnoreCase(part.substring(0, split).trim())) {
                return part.substring(split + 1).trim();
            }
        }
        return "";
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && preferred.trim().length() > 0
                ? preferred.trim() : fallback;
    }
}
