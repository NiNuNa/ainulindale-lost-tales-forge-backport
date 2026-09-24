package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatCodeNames;
import com.ninuna.losttales.chat.ChatRecipientRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Every game channel bound to a Discord channel, read once from the
 * config when the bridge starts, and the one place that answers where
 * lines go. Each entry of the {@code channelBindings} list binds one
 * game channel to one Discord channel:
 *
 * <pre>
 * global=GAME_TO_DISCORD;webhook=https://discord.com/api/webhooks/...
 * ooc=BIDIRECTIONAL;channel=123456789012345678;webhook=https://...
 * ooc=GAME_TO_DISCORD;webhook=https://discord.com/api/webhooks/...
 * gondor=BIDIRECTIONAL;channel=...;webhook=...
 * </pre>
 *
 * The part before {@code =} is the game channel's code name
 * ({@link ChatCodeNames}), a faction's for that faction's chat; then the
 * direction, then {@code channel} (the Discord channel the bot reads)
 * and {@code webhook} (where the bridge posts) in any order. A game
 * channel may be bound as often as it has Discord channels to go to —
 * each entry is one destination, and a line goes to every one that
 * posts — while a Discord channel belongs to one game channel only,
 * whichever way lines cross it: the second game channel to name a
 * Discord channel another already reads or posts into is refused
 * outright, and the second to name a webhook another posts through is
 * refused that posting, since a Discord channel holds one conversation;
 * a game channel naming one webhook twice posts through it once. The
 * Discord channels of every guild the bot is in look alike here: a
 * channel id and a webhook name their channel on their own, whichever
 * guild holds it. A webhook must be a Discord webhook address, since
 * the bridge sends the game's lines wherever it points. An entry that
 * asks for something it cannot have is trimmed to what it can, with one
 * warning each, and an entry for a private channel is refused outright:
 * the channel's own word on whether it may be bridged is final. A fresh
 * file holds no link: links are made with a pairing code
 * ({@link DiscordLinkCodes}).
 */
public final class DiscordChannelBindings {
    public static final DiscordChannelBindings EMPTY =
            new DiscordChannelBindings(Collections.<DiscordChannelBinding>emptyList());

    private static final char ENTRY_SEPARATOR = ';';
    private static final String CHANNEL_KEY = "channel";
    private static final String WEBHOOK_KEY = "webhook";
    /**
     * A Discord webhook's address: Discord's own host (the canary and
     * test builds' too), an optional API version, the webhook's id and
     * its token, nothing after.
     */
    private static final Pattern DISCORD_WEBHOOK = Pattern.compile(
            "https://(?:(?:canary|ptb)\\.)?discord(?:app)?\\.com/api(?:/v\\d{1,2})?"
                    + "/webhooks/\\d{1,24}/[A-Za-z0-9_-]{1,128}");

    /**
     * Where the parser's findings go; the bridge logs them, tests
     * collect them. A warning is an entry trimmed to what it can do; a
     * refusal is an entry that broke the one rule the bridge keeps —
     * one game channel per Discord channel — and lost its reads or its
     * posting for it.
     */
    public interface Warnings {
        void warn(String message);

        void refuse(String message);
    }

    /** Every binding, in the order the config names them. */
    private final List<DiscordChannelBinding> bindings;
    private final Map<String, DiscordChannelBinding> byId;
    /** The bindings of each game channel key, in config order. */
    private final Map<String, List<DiscordChannelBinding>> byKey;
    /** The bindings that read a Discord channel, one per channel, in config order. */
    private final List<DiscordChannelBinding> reading;
    /**
     * Where the bridge posts, once per webhook: the first binding to
     * post through each. What a notice for everyone is sent to.
     */
    private final List<DiscordChannelBinding> destinations;
    /** Every Discord channel a binding that reads or posts names, once each. */
    private final List<String> channels;

    private DiscordChannelBindings(List<DiscordChannelBinding> parsed) {
        ArrayList<DiscordChannelBinding> named =
                new ArrayList<DiscordChannelBinding>(parsed.size());
        HashMap<String, Integer> ordinals = new HashMap<String, Integer>();
        for (DiscordChannelBinding binding : parsed) {
            Integer seen = ordinals.get(binding.key());
            int ordinal = seen == null ? 1 : seen.intValue() + 1;
            ordinals.put(binding.key(), Integer.valueOf(ordinal));
            named.add(binding.withOrdinal(ordinal));
        }
        this.bindings = Collections.unmodifiableList(named);
        LinkedHashMap<String, DiscordChannelBinding> ids =
                new LinkedHashMap<String, DiscordChannelBinding>();
        LinkedHashMap<String, List<DiscordChannelBinding>> keyed =
                new LinkedHashMap<String, List<DiscordChannelBinding>>();
        ArrayList<DiscordChannelBinding> reads = new ArrayList<DiscordChannelBinding>();
        LinkedHashMap<String, DiscordChannelBinding> posting =
                new LinkedHashMap<String, DiscordChannelBinding>();
        LinkedHashSet<String> known = new LinkedHashSet<String>();
        for (DiscordChannelBinding binding : this.bindings) {
            ids.put(binding.id(), binding);
            List<DiscordChannelBinding> ofKey = keyed.get(binding.key());
            if (ofKey == null) {
                ofKey = new ArrayList<DiscordChannelBinding>(2);
                keyed.put(binding.key(), ofKey);
            }
            ofKey.add(binding);
            if (binding.readsFromDiscord()) {
                reads.add(binding);
            }
            if (binding.sendsToDiscord()
                    && !posting.containsKey(binding.getWebhookUrl())) {
                posting.put(binding.getWebhookUrl(), binding);
            }
            // A switched-off entry binds its Discord channel to nothing,
            // so the bridge leaves that channel alone.
            if (binding.getDiscordChannelId().length() > 0
                    && (binding.readsFromDiscord() || binding.sendsToDiscord())) {
                known.add(binding.getDiscordChannelId());
            }
        }
        for (Map.Entry<String, List<DiscordChannelBinding>> entry : keyed.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        this.byId = Collections.unmodifiableMap(ids);
        this.byKey = Collections.unmodifiableMap(keyed);
        this.reading = Collections.unmodifiableList(reads);
        this.destinations = Collections.unmodifiableList(
                new ArrayList<DiscordChannelBinding>(posting.values()));
        this.channels = Collections.unmodifiableList(new ArrayList<String>(known));
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
        ChatCodeNames.Named named = ChatCodeNames.parse(target);
        if (named == null) {
            warn(warnings, "Discord binding for unknown channel '" + target
                    + "'; ignored");
            return null;
        }
        ChatChannel channel = named.channel;
        String scope = named.scope;
        if (!channel.isBridgeable()) {
            warn(warnings, "Discord binding for the " + channel.getDisplayName()
                    + " channel refused: that channel is private and never leaves the game");
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
        if (webhook.length() > 0 && !isDiscordWebhook(webhook)) {
            // Never repeated in the log: an address is as good as a
            // password to whoever reads it.
            warn(warnings, "Discord binding '" + target + "' has a webhook that is"
                    + " not a Discord webhook address; posting is off");
            webhook = "";
        }
        return new DiscordChannelBinding(channel, scope, discordChannel, webhook,
                direction);
    }

    /**
     * Trims every binding to what it can do, drops an entry of a game
     * channel that names a webhook or a Discord channel the same game
     * channel names already, and refuses a second game channel a
     * Discord channel another has: the whole entry when it names a
     * Discord channel another game channel reads or posts into, its
     * posting when it posts through a webhook another posts through. A
     * Discord channel belongs to one game channel.
     */
    private static DiscordChannelBindings validated(List<DiscordChannelBinding> parsed,
                                                    boolean botTokenPresent,
                                                    Warnings warnings) {
        ArrayList<DiscordChannelBinding> kept = new ArrayList<DiscordChannelBinding>();
        Set<String> seen = new HashSet<String>();
        Map<String, String> owners = new HashMap<String, String>();
        Map<String, String> posters = new HashMap<String, String>();
        Map<String, Integer> ordinals = new HashMap<String, Integer>();
        for (int index = 0; index < parsed.size(); index++) {
            DiscordChannelBinding binding = parsed.get(index);
            String key = binding.key();
            boolean webhookSeen = binding.getWebhookUrl().length() > 0
                    && !seen.add(key + "\nwebhook\n" + binding.getWebhookUrl());
            boolean channelSeen = binding.getDiscordChannelId().length() > 0
                    && !seen.add(key + "\nchannel\n" + binding.getDiscordChannelId());
            if (webhookSeen || channelSeen || (binding.getWebhookUrl().length() == 0
                    && binding.getDiscordChannelId().length() == 0
                    && !seen.add(key + "\nnothing"))) {
                warn(warnings, "Discord binding '" + key + "' names a "
                        + (webhookSeen ? "webhook" : channelSeen
                                ? "Discord channel" : "placeholder")
                        + " it names already; the first stands");
                continue;
            }
            Integer count = ordinals.get(key);
            int ordinal = count == null ? 1 : count.intValue() + 1;
            ordinals.put(key, Integer.valueOf(ordinal));
            String id = binding.withOrdinal(ordinal).id();
            DiscordBridgeDirection direction = binding.getDirection();
            if (direction.sendsToDiscord() && binding.getWebhookUrl().length() == 0) {
                warn(warnings, "Discord binding '" + id
                        + "' posts to Discord but names no webhook; posting is off");
                direction = direction.withoutSends();
            }
            if (binding.getWebhookUrl().length() > 0) {
                String poster = posters.get(binding.getWebhookUrl());
                if (poster != null && !poster.equals(key)) {
                    refuse(warnings, "Discord binding '" + id + "' posts through"
                            + " the webhook '" + posters.get(
                                    binding.getWebhookUrl() + "\nid")
                            + "' posts through: a Discord channel belongs to"
                            + " one game channel; posting is off for '" + id + "'");
                    direction = direction.withoutSends();
                } else if (poster == null && direction.sendsToDiscord()) {
                    posters.put(binding.getWebhookUrl(), key);
                    posters.put(binding.getWebhookUrl() + "\nid", id);
                }
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
                }
                if (why != null) {
                    warn(warnings, "Discord binding '" + id + "' " + why
                            + "; reading is off");
                    direction = direction.withoutReads();
                }
            }
            String discordChannel = binding.getDiscordChannelId();
            if (discordChannel.length() > 0 && (direction.readsFromDiscord()
                    || (direction.sendsToDiscord()
                            && binding.getWebhookUrl().length() > 0))) {
                String owner = owners.get(discordChannel);
                if (owner != null && !owner.equals(key)) {
                    refuse(warnings, "Discord binding '" + id + "' names Discord"
                            + " channel " + discordChannel + ", which '" + owner
                            + "' has already: a Discord channel belongs to one"
                            + " game channel; '" + id + "' is off");
                    direction = DiscordBridgeDirection.DISABLED;
                } else {
                    owners.put(discordChannel, key);
                }
            }
            kept.add(binding.withDirection(direction));
        }
        return new DiscordChannelBindings(kept);
    }

    /**
     * The game channel key that owns a Discord channel by naming its id
     * in an entry that reads or posts, or empty. What the bridge asks
     * once it has learnt which channel a webhook posts into, so a
     * webhook posting into a channel another game channel reads is
     * caught as well. A switched-off entry owns nothing, as it binds its
     * channel to nothing.
     */
    public String ownerOfChannel(String discordChannelId) {
        if (discordChannelId == null || discordChannelId.length() == 0) {
            return "";
        }
        for (DiscordChannelBinding binding : this.bindings) {
            if ((binding.readsFromDiscord() || binding.sendsToDiscord())
                    && binding.getDiscordChannelId().equals(discordChannelId)) {
                return binding.key();
            }
        }
        return "";
    }

    /** Whether the text is a Discord webhook's address and nothing else. */
    static boolean isDiscordWebhook(String url) {
        return url != null && url.length() <= 256
                && DISCORD_WEBHOOK.matcher(url).matches();
    }

    /** Whether the text is a Discord id: a number of at most 24 digits. */
    static boolean isSnowflake(String value) {
        if (value == null || value.length() == 0 || value.length() > 24) {
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

    private static void refuse(Warnings warnings, String message) {
        if (warnings != null) {
            warnings.refuse(message);
        }
    }

    /** An entry as it may be shown in a log: never past its first '=' value. */
    private static String describe(String entry) {
        return entry.length() > 40 ? entry.substring(0, 40) + "..." : entry;
    }

    /**
     * The bindings a game line goes out through, in config order: for the
     * Faction channel, those of the line's own faction, compared
     * case-insensitively; for any other, the channel's own; else none.
     */
    public List<DiscordChannelBinding> forGame(ChatChannel channel, String factionId) {
        String key = DiscordChannelBinding.keyOf(channel, factionId);
        List<DiscordChannelBinding> own = key == null ? null : this.byKey.get(key);
        return own == null ? Collections.<DiscordChannelBinding>emptyList() : own;
    }

    /** The binding with this id, or null. */
    public DiscordChannelBinding byId(String id) {
        return id == null ? null : this.byId.get(id);
    }

    /**
     * The binding that reads that Discord channel, or null: at most one
     * binding reads a Discord channel. What word arriving from Discord is
     * matched by, since a binding's id is not stable across a reordering.
     */
    public DiscordChannelBinding readerOf(String discordChannelId) {
        if (discordChannelId == null || discordChannelId.length() == 0) {
            return null;
        }
        for (DiscordChannelBinding binding : this.reading) {
            if (discordChannelId.equals(binding.getDiscordChannelId())) {
                return binding;
            }
        }
        return null;
    }

    public List<DiscordChannelBinding> all() {
        return this.bindings;
    }

    /** The bindings that read a Discord channel, one per channel. */
    public List<DiscordChannelBinding> reading() {
        return this.reading;
    }

    /**
     * Where the bridge posts, once per webhook: the first binding to
     * post through each, in config order. A notice for everyone goes to
     * each of these exactly once.
     */
    public List<DiscordChannelBinding> destinations() {
        return this.destinations;
    }

    /**
     * Every Discord channel a binding that reads or posts names by id,
     * once each, in config order: the channels whose topic is kept. A
     * channel named only by a switched-off entry is not among them.
     */
    public List<String> channels() {
        return this.channels;
    }

    public boolean readsAnything() {
        return !this.reading.isEmpty();
    }

    public boolean sendsAnything() {
        return !this.destinations.isEmpty();
    }

    /** The ids and directions, for the start-up log; never a secret. */
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
