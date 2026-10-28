package com.ninuna.losttales.compat.discord;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Edits the {@code channelLinks} list the way linking and unlinking
 * need to: an entry added for each Discord channel a game channel is
 * linked to, a link's language changed, and entries taken away by game
 * channel or by Discord channel, written in the form the parser reads. Pure string work; the
 * config service writes the list back and the bridge restarts on it.
 */
public final class DiscordLinkEntries {

    private DiscordLinkEntries() {}

    /**
     * {@code <key>=<direction>;channel=<id>;webhook=<url>}, then
     * {@code ;language=<code>} for a link whose language was chosen; an
     * empty {@code language} leaves the link in the server's default.
     */
    public static String format(String key, DiscordBridgeDirection direction,
                                String discordChannelId, String webhookUrl,
                                String language) {
        String chosen = language == null ? "" : language.trim();
        return key.toLowerCase(Locale.ROOT) + "=" + direction.name()
                + ";channel=" + (discordChannelId == null ? "" : discordChannelId.trim())
                + ";webhook=" + (webhookUrl == null ? "" : webhookUrl.trim())
                + (chosen.length() == 0 ? "" : ";language=" + chosen);
    }

    /**
     * The list with a link added: {@code key}'s game channel to the
     * Discord channel, through the webhook, in {@code language} (empty
     * for the server's default). An entry naming the same Discord channel
     * is taken out, since the new link replaces it.
     */
    public static List<String> link(String[] entries, String key,
                                    DiscordBridgeDirection direction,
                                    String discordChannelId, String webhookUrl,
                                    String language) {
        List<String> result = removeChannel(entries, discordChannelId);
        result.add(format(key, direction, discordChannelId, webhookUrl, language));
        return result;
    }

    /**
     * The list with the entries naming the Discord channel in
     * {@code language}, empty for the server's default; every other entry
     * as it was. The list is unchanged when no entry names the channel.
     */
    public static List<String> withLanguage(String[] entries, String discordChannelId,
                                            String language) {
        String chosen = language == null ? "" : language.trim();
        List<String> result = new ArrayList<String>();
        for (String entry : entries == null ? new String[0] : entries) {
            if (entry == null || !discordChannelId.equals(optionOf(entry, "channel"))) {
                result.add(entry);
                continue;
            }
            int equals = entry.indexOf('=');
            StringBuilder rewritten = new StringBuilder(entry.substring(0, equals + 1));
            boolean first = true;
            for (String part : entry.substring(equals + 1).split(";", -1)) {
                if (part.trim().toLowerCase(Locale.ROOT).startsWith("language=")) {
                    continue;
                }
                if (!first) {
                    rewritten.append(';');
                }
                rewritten.append(part);
                first = false;
            }
            if (chosen.length() > 0) {
                rewritten.append(";language=").append(chosen);
            }
            result.add(rewritten.toString());
        }
        return result;
    }

    /** The list without the entries for {@code key}. */
    public static List<String> removeKey(String[] entries, String key) {
        List<String> result = new ArrayList<String>();
        for (String entry : entries == null ? new String[0] : entries) {
            if (!key.equalsIgnoreCase(keyOf(entry))) {
                result.add(entry);
            }
        }
        return result;
    }

    /** The list without the entries naming the Discord channel. */
    public static List<String> removeChannel(String[] entries, String discordChannelId) {
        List<String> result = new ArrayList<String>();
        for (String entry : entries == null ? new String[0] : entries) {
            if (!discordChannelId.equals(optionOf(entry, "channel"))) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * The webhooks the entries taken away from {@code before} to leave
     * {@code after} named: what the bot deletes once a link is gone.
     */
    public static List<String> webhooksRemoved(String[] before, List<String> after) {
        List<String> webhooks = new ArrayList<String>();
        for (String entry : before == null ? new String[0] : before) {
            String webhook = optionOf(entry, "webhook");
            if (webhook.length() > 0 && !after.contains(entry)
                    && !webhooks.contains(webhook)) {
                webhooks.add(webhook);
            }
        }
        return webhooks;
    }

    /** The game channel key an entry names: everything before its first '='. */
    public static String keyOf(String entry) {
        if (entry == null) {
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
}
