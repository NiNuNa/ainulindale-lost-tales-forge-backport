package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatRecipientRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every game channel bound to a Discord channel, read once from the
 * config when the bridge starts. Each entry of the {@code channelBindings}
 * list binds one channel:
 *
 * <pre>
 * all=GAME_TO_DISCORD;webhook=https://discord.com/api/webhooks/...
 * discord=BIDIRECTIONAL;channel=123456789012345678;webhook=https://...
 * faction:lotr.gondor=BIDIRECTIONAL;channel=...;webhook=...
 * </pre>
 *
 * The part before {@code =} names the channel by its wire id, the
 * Faction channel with the faction after a colon; then the direction,
 * then {@code channel} (the Discord channel the bot reads) and
 * {@code webhook} (where the bridge posts) in any order. An entry that
 * asks for something it cannot have is trimmed to what it can, with one
 * warning each, and an entry for a private channel is refused outright:
 * the channel's own word on whether it may be bridged is final. A fresh
 * file offers the Discord channel, Global and OOC as entries switched
 * off; an older file's single-channel keys are turned into entries once
 * by the config and then dropped.
 */
public final class DiscordChannelBindings {
    public static final DiscordChannelBindings EMPTY =
            new DiscordChannelBindings(Collections.<DiscordChannelBinding>emptyList());

    private static final char ENTRY_SEPARATOR = ';';
    private static final String CHANNEL_KEY = "channel";
    private static final String WEBHOOK_KEY = "webhook";

    /** Where the parser's warnings go; the bridge logs them, tests collect them. */
    public interface Warnings {
        void warn(String message);
    }

    private final List<DiscordChannelBinding> bindings;
    private final Map<String, DiscordChannelBinding> byKey;

    private DiscordChannelBindings(List<DiscordChannelBinding> bindings) {
        this.bindings = Collections.unmodifiableList(
                new ArrayList<DiscordChannelBinding>(bindings));
        LinkedHashMap<String, DiscordChannelBinding> keyed =
                new LinkedHashMap<String, DiscordChannelBinding>();
        for (DiscordChannelBinding binding : this.bindings) {
            keyed.put(binding.key(), binding);
        }
        this.byKey = Collections.unmodifiableMap(keyed);
    }

    /** Reads the config list; never throws, every fault is one warning. */
    public static DiscordChannelBindings parse(String[] entries,
                                               boolean botTokenPresent,
                                               Warnings warnings) {
        ArrayList<DiscordChannelBinding> parsed = new ArrayList<DiscordChannelBinding>();
        if (entries == null) {
            return EMPTY;
        }
        for (int index = 0; index < entries.length; index++) {
            DiscordChannelBinding binding = parseEntry(entries[index], warnings);
            if (binding != null) {
                parsed.add(binding);
            }
        }
        return validated(parsed, botTokenPresent, warnings);
    }

    /**
     * The entries the single-channel keys of an older config file
     * meant: the Discord channel both ways through its channel id and
     * webhook, and Global and OOC posted one way through the read-only
     * webhook, or the main one when there is none. What the config
     * migration writes into {@code channelBindings} once, before the old
     * keys are dropped. Empty when the old keys held nothing.
     */
    public static String[] legacyEntries(String channelId, String webhookUrl,
                                         boolean relayGameChat,
                                         boolean relayDiscordChat,
                                         boolean relayGlobalChat,
                                         boolean relayOocChat,
                                         String readOnlyWebhookUrl) {
        String channel = channelId == null ? "" : channelId.trim();
        String webhook = webhookUrl == null ? "" : webhookUrl.trim();
        String readOnly = readOnlyWebhookUrl == null ? "" : readOnlyWebhookUrl.trim();
        if (readOnly.length() == 0) {
            readOnly = webhook;
        }
        ArrayList<String> entries = new ArrayList<String>();
        if (channel.length() > 0 || webhook.length() > 0) {
            entries.add(ChatChannel.DISCORD.getId() + "="
                    + DiscordBridgeDirection.of(relayGameChat && webhook.length() > 0,
                            relayDiscordChat && channel.length() > 0).name()
                    + ENTRY_SEPARATOR + CHANNEL_KEY + "=" + channel
                    + ENTRY_SEPARATOR + WEBHOOK_KEY + "=" + webhook);
        }
        if (relayGlobalChat && readOnly.length() > 0) {
            entries.add(ChatChannel.ALL.getId() + "=" + DiscordBridgeDirection.GAME_TO_DISCORD.name()
                    + ENTRY_SEPARATOR + WEBHOOK_KEY + "=" + readOnly);
        }
        if (relayOocChat && readOnly.length() > 0) {
            entries.add(ChatChannel.OOC.getId() + "=" + DiscordBridgeDirection.GAME_TO_DISCORD.name()
                    + ENTRY_SEPARATOR + WEBHOOK_KEY + "=" + readOnly);
        }
        return entries.toArray(new String[entries.size()]);
    }

    private static DiscordChannelBinding parseEntry(String entry, Warnings warnings) {
        String text = entry == null ? "" : entry.trim();
        if (text.length() == 0 || text.startsWith("#")) {
            return null;
        }
        int equals = text.indexOf('=');
        if (equals <= 0) {
            warn(warnings, "Discord binding '" + describe(text)
                    + "' has no '=' between the channel and its direction; ignored");
            return null;
        }
        String target = text.substring(0, equals).trim().toLowerCase(Locale.ROOT);
        String[] parts = text.substring(equals + 1).split(
                String.valueOf(ENTRY_SEPARATOR));
        String channelId = target;
        String scope = "";
        int colon = target.indexOf(DiscordChannelBinding.SCOPE_SEPARATOR);
        if (colon >= 0) {
            channelId = target.substring(0, colon).trim();
            scope = target.substring(colon + 1).trim();
        }
        ChatChannel channel = ChatChannel.fromId(channelId);
        if (channel == null) {
            warn(warnings, "Discord binding for unknown channel '" + channelId
                    + "'; ignored");
            return null;
        }
        if (!channel.isBridgeable()) {
            warn(warnings, "Discord binding for the " + channel.getDisplayName()
                    + " channel refused: that channel is private and never leaves the game");
            return null;
        }
        if (scope.length() > 0 && channel != ChatChannel.FACTION) {
            warn(warnings, "Discord binding '" + target + "': only the faction channel"
                    + " takes a scope; ignored");
            return null;
        }
        DiscordBridgeDirection direction = DiscordBridgeDirection.parse(
                parts.length == 0 ? "" : parts[0]);
        if (direction == null) {
            warn(warnings, "Discord binding '" + target + "' names no direction"
                    + " (DISABLED, GAME_TO_DISCORD, DISCORD_TO_GAME or BIDIRECTIONAL); ignored");
            return null;
        }
        String discordChannel = "";
        String webhook = "";
        for (int index = 1; index < parts.length; index++) {
            String part = parts[index].trim();
            int split = part.indexOf('=');
            if (split <= 0) {
                if (part.length() > 0) {
                    warn(warnings, "Discord binding '" + target
                            + "' has an option without a value; ignored that option");
                }
                continue;
            }
            String key = part.substring(0, split).trim().toLowerCase(Locale.ROOT);
            String value = part.substring(split + 1).trim();
            if (CHANNEL_KEY.equals(key)) {
                discordChannel = value;
            } else if (WEBHOOK_KEY.equals(key)) {
                webhook = value;
            } else {
                warn(warnings, "Discord binding '" + target + "' has an unknown option '"
                        + key + "'; ignored that option");
            }
        }
        if (discordChannel.length() > 0 && !isSnowflake(discordChannel)) {
            warn(warnings, "Discord binding '" + target
                    + "' has a channel id that is not a number; reading it is off");
            discordChannel = "";
        }
        return new DiscordChannelBinding(channel, scope, discordChannel, webhook,
                direction);
    }

    /** Trims every binding to what it can do and drops duplicates, first wins. */
    private static DiscordChannelBindings validated(List<DiscordChannelBinding> parsed,
                                                    boolean botTokenPresent,
                                                    Warnings warnings) {
        ArrayList<DiscordChannelBinding> kept = new ArrayList<DiscordChannelBinding>();
        Set<String> keys = new HashSet<String>();
        Set<String> readChannels = new HashSet<String>();
        for (int index = 0; index < parsed.size(); index++) {
            DiscordChannelBinding binding = parsed.get(index);
            String key = binding.key();
            if (!keys.add(key)) {
                warn(warnings, "Discord binding '" + key
                        + "' is given twice; the first stands");
                continue;
            }
            DiscordBridgeDirection direction = binding.getDirection();
            if (direction.sendsToDiscord() && binding.getWebhookUrl().length() == 0) {
                warn(warnings, "Discord binding '" + key
                        + "' posts to Discord but names no webhook; posting is off");
                direction = direction.withoutSends();
            }
            if (direction.readsFromDiscord()) {
                String why = null;
                if (binding.getDiscordChannelId().length() == 0) {
                    why = "names no Discord channel";
                } else if (!botTokenPresent) {
                    why = "needs the bot token to read";
                } else if (binding.getChannel().getRecipientRule()
                        == ChatRecipientRule.PROXIMITY) {
                    why = "is the proximity channel, which has no place on Discord to read from";
                } else if (binding.getChannel() == ChatChannel.FACTION
                        && binding.getFactionScope().length() == 0) {
                    why = "is the faction channel without a faction; name one as faction:<id>";
                } else if (!readChannels.add(binding.getDiscordChannelId())) {
                    why = "reads a Discord channel another binding already reads";
                }
                if (why != null) {
                    warn(warnings, "Discord binding '" + key + "' " + why
                            + "; reading is off");
                    direction = direction.withoutReads();
                }
            }
            kept.add(binding.withDirection(direction));
        }
        return new DiscordChannelBindings(kept);
    }

    private static boolean isSnowflake(String value) {
        if (value.length() == 0 || value.length() > 24) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    private static void warn(Warnings warnings, String message) {
        if (warnings != null) {
            warnings.warn(message);
        }
    }

    /** An entry as it may be shown in a log: never past its first '=' value. */
    private static String describe(String entry) {
        return entry.length() > 40 ? entry.substring(0, 40) + "..." : entry;
    }

    /**
     * The binding a game line goes out through: the Faction channel's
     * binding for the sender's faction, else the channel's own, else
     * none. Faction ids compare case-insensitively.
     */
    public DiscordChannelBinding forGame(ChatChannel channel, String factionId) {
        if (channel == null) {
            return null;
        }
        if (channel == ChatChannel.FACTION && factionId != null
                && factionId.trim().length() > 0) {
            DiscordChannelBinding scoped = this.byKey.get(DiscordChannelBinding.keyOf(
                    channel, factionId.trim().toLowerCase(Locale.ROOT)));
            if (scoped != null) {
                return scoped;
            }
        }
        return this.byKey.get(channel.getId());
    }

    /**
     * Whether another binding posts to, or reads, the same Discord
     * channel as this one: then a posted line carries its channel's tag
     * so the Discord side can tell the channels apart, and a channel of
     * its own needs no tag.
     */
    public boolean sharesDiscordChannel(DiscordChannelBinding binding) {
        if (binding == null) {
            return false;
        }
        for (DiscordChannelBinding other : this.bindings) {
            if (other == binding || other.key().equals(binding.key())) {
                continue;
            }
            if (binding.getWebhookUrl().length() > 0
                    && binding.getWebhookUrl().equals(other.getWebhookUrl())) {
                return true;
            }
            if (binding.getDiscordChannelId().length() > 0
                    && binding.getDiscordChannelId().equals(other.getDiscordChannelId())) {
                return true;
            }
        }
        return false;
    }

    /** The binding that reads the given Discord channel, or null. */
    public DiscordChannelBinding forDiscordChannel(String discordChannelId) {
        if (discordChannelId == null || discordChannelId.length() == 0) {
            return null;
        }
        for (DiscordChannelBinding binding : this.bindings) {
            if (binding.readsFromDiscord()
                    && binding.getDiscordChannelId().equals(discordChannelId)) {
                return binding;
            }
        }
        return null;
    }

    public DiscordChannelBinding byKey(String key) {
        return key == null ? null : this.byKey.get(key);
    }

    /** The game's own Discord channel's binding, or null when it has none. */
    public DiscordChannelBinding discordChannel() {
        return this.byKey.get(ChatChannel.DISCORD.getId());
    }

    public List<DiscordChannelBinding> all() {
        return this.bindings;
    }

    public List<DiscordChannelBinding> reading() {
        ArrayList<DiscordChannelBinding> result = new ArrayList<DiscordChannelBinding>();
        for (DiscordChannelBinding binding : this.bindings) {
            if (binding.readsFromDiscord()) {
                result.add(binding);
            }
        }
        return result;
    }

    public boolean readsAnything() {
        return !reading().isEmpty();
    }

    public boolean sendsAnything() {
        for (DiscordChannelBinding binding : this.bindings) {
            if (binding.sendsToDiscord()) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return this.bindings.isEmpty();
    }

    /**
     * Where the server's own notices go: the Discord channel's webhook,
     * else the first webhook any binding posts through, else nowhere.
     */
    public String noticeWebhookUrl() {
        DiscordChannelBinding discord = discordChannel();
        if (discord != null && discord.getWebhookUrl().length() > 0) {
            return discord.getWebhookUrl();
        }
        for (DiscordChannelBinding binding : this.bindings) {
            if (binding.sendsToDiscord()) {
                return binding.getWebhookUrl();
            }
        }
        return "";
    }

    /** The keys and directions, for the start-up log; never a secret. */
    public String describeForLog() {
        StringBuilder text = new StringBuilder();
        for (DiscordChannelBinding binding : this.bindings) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(binding.toString());
        }
        return text.length() == 0 ? "none" : text.toString();
    }
}
