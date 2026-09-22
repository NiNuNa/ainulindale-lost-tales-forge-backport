package com.ninuna.losttales.compat.discord;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Edits the {@code channelBindings} list the way linking and unlinking
 * need to: an entry added for each Discord channel a game channel is
 * linked to, and entries taken away by game channel or by Discord
 * channel, written in the form the parser reads. Pure string work; the
 * config service writes the list back and the bridge restarts on it.
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
     * The list with a link added: {@code key}'s game channel to the
     * Discord channel, through the webhook. An entry of the same key that
     * names neither a channel nor a webhook — a placeholder a fresh file
     * may hold — is taken out, and so is any entry naming the same
     * Discord channel, which the new link replaces. A comment line is
     * left where it is.
     */
    public static List<String> link(String[] entries, String key,
                                    DiscordBridgeDirection direction,
                                    String discordChannelId, String webhookUrl) {
        List<String> result = new ArrayList<String>();
        for (String entry : entries == null ? new String[0] : entries) {
            boolean comment = keyOf(entry).length() == 0;
            boolean placeholder = !comment && key.equalsIgnoreCase(keyOf(entry))
                    && optionOf(entry, "channel").length() == 0
                    && optionOf(entry, "webhook").length() == 0;
            boolean sameChannel = !comment
                    && discordChannelId.equals(optionOf(entry, "channel"));
            if (!placeholder && !sameChannel) {
                result.add(entry);
            }
        }
        result.add(format(key, direction, discordChannelId, webhookUrl));
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
            if (keyOf(entry).length() == 0
                    || !discordChannelId.equals(optionOf(entry, "channel"))) {
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
}
