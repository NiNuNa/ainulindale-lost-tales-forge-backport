package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
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

/**
 * Every game channel bound to a Discord channel, read once from the
 * config when the bridge starts, and the one place that answers where
 * lines go. Each entry of the {@code channelBindings} list binds one
 * game channel to one Discord channel:
 *
 * <pre>
 * all=GAME_TO_DISCORD;webhook=https://discord.com/api/webhooks/...
 * ooc=BIDIRECTIONAL;channel=123456789012345678;webhook=https://...
 * ooc=GAME_TO_DISCORD;webhook=https://discord.com/api/webhooks/...
 * faction:lotr.gondor=BIDIRECTIONAL;channel=...;webhook=...
 * </pre>
 *
 * The part before {@code =} names the channel by its wire id, the
 * Faction channel with the faction after a colon; then the direction,
 * then {@code channel} (the Discord channel the bot reads) and
 * {@code webhook} (where the bridge posts) in any order. A game channel
 * may be bound as often as it has Discord channels to go to — each
 * entry is one destination, and a line goes to every one that posts —
 * while a Discord channel belongs to one game channel only, whichever
 * way lines cross it: the second game channel to name a webhook or a
 * channel id already named is refused that webhook's posting or that
 * channel's reads, since a Discord channel holds one conversation, and
 * a game channel naming one webhook twice posts through it once. The
 * Discord channels of every guild the bot is in look alike here: a
 * channel id and a webhook name their channel on their own, whichever
 * guild holds it. An entry that asks for something it cannot have is
 * trimmed to what it can, with one warning each, and an entry for a
 * private channel is refused outright: the channel's own word on
 * whether it may be bridged is final. A fresh file offers OOC &amp;
 * Discord and Global as entries switched off; an older file's
 * single-channel keys are turned into entries once by the config and
 * then dropped, and an entry an older file names {@code discord} is
 * read as, and rewritten once to, {@code ooc}.
 */
public final class DiscordChannelBindings {
    public static final DiscordChannelBindings EMPTY =
            new DiscordChannelBindings(Collections.<DiscordChannelBinding>emptyList());

    private static final char ENTRY_SEPARATOR = ';';
    private static final String CHANNEL_KEY = "channel";
    private static final String WEBHOOK_KEY = "webhook";

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
    /** The one binding that reads each Discord channel, by its id. */
    private final Map<String, DiscordChannelBinding> readerByChannel;
    private final List<DiscordChannelBinding> reading;
    /**
     * Where the bridge posts, once per webhook: the first binding to
     * post through each. What a notice for everyone is sent to.
     */
    private final List<DiscordChannelBinding> destinations;
    /** Every Discord channel any binding names, once each. */
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
        LinkedHashMap<String, DiscordChannelBinding> readers =
                new LinkedHashMap<String, DiscordChannelBinding>();
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
                readers.put(binding.getDiscordChannelId(), binding);
                reads.add(binding);
            }
            if (binding.sendsToDiscord()
                    && !posting.containsKey(binding.getWebhookUrl())) {
                posting.put(binding.getWebhookUrl(), binding);
            }
            if (binding.getDiscordChannelId().length() > 0) {
                known.add(binding.getDiscordChannelId());
            }
        }
        for (Map.Entry<String, List<DiscordChannelBinding>> entry : keyed.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        this.byId = Collections.unmodifiableMap(ids);
        this.byKey = Collections.unmodifiableMap(keyed);
        this.readerByChannel = Collections.unmodifiableMap(readers);
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

    /**
     * The entries the single-channel keys of an older config file
     * meant: OOC &amp; Discord — then a Discord channel of its own — both
     * ways through its channel id and webhook, and Global and OOC posted
     * one way through the read-only webhook, or the main one when there
     * is none. What the config migration writes into
     * {@code channelBindings} once, before the old keys are dropped.
     * Empty when the old keys held nothing.
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
            entries.add(ChatChannel.OOC.getId() + "="
                    + DiscordBridgeDirection.of(relayGameChat && webhook.length() > 0,
                            relayDiscordChat && channel.length() > 0).name()
                    + ENTRY_SEPARATOR + CHANNEL_KEY + "=" + channel
                    + ENTRY_SEPARATOR + WEBHOOK_KEY + "=" + webhook);
        }
        if (relayGlobalChat && readOnly.length() > 0) {
            // Global through the pair's own webhook is Global in a Discord
            // channel OOC & Discord has, which a Discord channel cannot
            // be: the entry is written with a webhook to fill in instead,
            // and says so at start until one is.
            entries.add(ChatChannel.ALL.getId() + "=" + DiscordBridgeDirection.GAME_TO_DISCORD.name()
                    + ENTRY_SEPARATOR + WEBHOOK_KEY + "="
                    + (readOnly.equals(webhook) ? "" : readOnly));
        }
        if (relayOocChat && readOnly.length() > 0 && !readOnly.equals(webhook)) {
            // A second webhook for OOC lines is a second destination of
            // its own; the same webhook again would post every line twice.
            entries.add(ChatChannel.OOC.getId() + "=" + DiscordBridgeDirection.GAME_TO_DISCORD.name()
                    + ENTRY_SEPARATOR + WEBHOOK_KEY + "=" + readOnly);
        }
        return entries.toArray(new String[entries.size()]);
    }

    /**
     * The entries as today's build writes them: an older file's
     * {@code discord=} entry names the channel OOC &amp; Discord took in
     * and is rewritten to {@code ooc=}, its scope and options kept, and
     * a game channel naming one webhook twice keeps the entry that says
     * more — the one with a channel id, else the first — since the
     * second would only post every line a second time (which is how an
     * older file's Discord pair and its OOC relay read once they name
     * one channel). A renamed entry that asks for nothing —
     * {@code DISABLED} with no channel and no webhook, the placeholder a
     * fresh file of that build offered — is dropped when the list already
     * names its key, so an older build's defaults collapse to today's
     * rather than standing twice. What the config writes back once; the
     * same array when nothing changes.
     */
    public static String[] renameLegacyKeys(String[] entries) {
        if (entries == null) {
            return null;
        }
        ArrayList<String> renamed = new ArrayList<String>(entries.length);
        boolean changed = false;
        for (int index = 0; index < entries.length; index++) {
            String entry = entries[index] == null ? "" : entries[index];
            String key = keyOf(entry);
            String current = currentKey(key);
            if (current.equals(key)) {
                renamed.add(entry);
                continue;
            }
            changed = true;
            String rest = entry.substring(entry.indexOf('=') + 1);
            if (isPlaceholder(rest) && countNaming(entries, current) > 1) {
                continue;
            }
            renamed.add(current + "=" + rest);
        }
        // One webhook per game channel: of the entries of one key that
        // name the same webhook, the one with a channel id stands, else
        // the first, and the rest go.
        for (int index = 0; index < renamed.size(); index++) {
            String entry = renamed.get(index);
            String webhook = optionOf(entry, WEBHOOK_KEY);
            if (webhook.length() == 0) {
                continue;
            }
            int kept = index;
            for (int other = index + 1; other < renamed.size(); other++) {
                String candidate = renamed.get(other);
                if (!keyOf(candidate).equals(keyOf(entry))
                        || !optionOf(candidate, WEBHOOK_KEY).equals(webhook)) {
                    continue;
                }
                if (optionOf(renamed.get(kept), CHANNEL_KEY).length() == 0
                        && optionOf(candidate, CHANNEL_KEY).length() > 0) {
                    kept = other;
                }
            }
            for (int other = renamed.size() - 1; other > index; other--) {
                String candidate = renamed.get(other);
                if (other != kept && keyOf(candidate).equals(keyOf(entry))
                        && optionOf(candidate, WEBHOOK_KEY).equals(webhook)) {
                    renamed.remove(other);
                    changed = true;
                }
            }
            if (kept != index) {
                renamed.set(index, renamed.get(kept));
                renamed.remove(kept);
                changed = true;
            }
        }
        return changed ? renamed.toArray(new String[renamed.size()]) : entries;
    }

    /** The value of an entry's option, trimmed; empty when it has none. */
    private static String optionOf(String entry, String option) {
        int equals = entry.indexOf('=');
        if (equals < 0) {
            return "";
        }
        String[] parts = entry.substring(equals + 1).split(
                String.valueOf(ENTRY_SEPARATOR));
        for (int index = 1; index < parts.length; index++) {
            int split = parts[index].indexOf('=');
            if (split > 0 && option.equals(
                    parts[index].substring(0, split).trim().toLowerCase(Locale.ROOT))) {
                return parts[index].substring(split + 1).trim();
            }
        }
        return "";
    }

    /** The key before an entry's first '=', trimmed and lower-cased; empty for none. */
    private static String keyOf(String entry) {
        int equals = entry.indexOf('=');
        return equals <= 0 ? ""
                : entry.substring(0, equals).trim().toLowerCase(Locale.ROOT);
    }

    /** {@code key} with its channel id resolved to the id in use today. */
    private static String currentKey(String key) {
        if (key.length() == 0) {
            return key;
        }
        int colon = key.indexOf(DiscordChannelBinding.SCOPE_SEPARATOR);
        String id = colon < 0 ? key : key.substring(0, colon);
        ChatChannel channel = ChatChannel.fromId(id);
        if (channel == null || channel.getId().equals(id)) {
            return key;
        }
        return colon < 0 ? channel.getId()
                : channel.getId() + key.substring(colon);
    }

    /** How many entries, renamed or not, name {@code key}. */
    private static int countNaming(String[] entries, String key) {
        int count = 0;
        for (int index = 0; index < entries.length; index++) {
            String entry = entries[index] == null ? "" : entries[index];
            if (currentKey(keyOf(entry)).equals(key)) {
                count++;
            }
        }
        return count;
    }

    /** Whether the part after the key is DISABLED with every option empty. */
    private static boolean isPlaceholder(String rest) {
        String[] parts = rest.split(String.valueOf(ENTRY_SEPARATOR));
        if (parts.length == 0 || DiscordBridgeDirection.parse(parts[0].trim())
                != DiscordBridgeDirection.DISABLED) {
            return false;
        }
        for (int index = 1; index < parts.length; index++) {
            int split = parts[index].indexOf('=');
            if (split >= 0 && parts[index].substring(split + 1).trim().length() > 0) {
                return false;
            }
        }
        return true;
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

    /**
     * Trims every binding to what it can do, drops an entry of a game
     * channel that names a webhook or a Discord channel the same game
     * channel names already, and refuses a second game channel a
     * Discord channel another has: its posting through a webhook the
     * other posts through, its reads of a channel the other reads. A
     * Discord channel belongs to one game channel.
     */
    private static DiscordChannelBindings validated(List<DiscordChannelBinding> parsed,
                                                    boolean botTokenPresent,
                                                    Warnings warnings) {
        ArrayList<DiscordChannelBinding> kept = new ArrayList<DiscordChannelBinding>();
        Set<String> seen = new HashSet<String>();
        Map<String, String> readers = new HashMap<String, String>();
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
                } else if (binding.getChannel() == ChatChannel.FACTION
                        && binding.getFactionScope().length() == 0) {
                    why = "is the faction channel without a faction; name one as faction:<id>";
                }
                if (why != null) {
                    warn(warnings, "Discord binding '" + id + "' " + why
                            + "; reading is off");
                    direction = direction.withoutReads();
                } else {
                    String reader = readers.get(binding.getDiscordChannelId());
                    if (reader != null) {
                        refuse(warnings, "Discord binding '" + id
                                + "' reads Discord channel "
                                + binding.getDiscordChannelId() + ", which '"
                                + reader + "' reads already: a Discord channel"
                                + " belongs to one game channel; reading is off"
                                + " for '" + id + "'");
                        direction = direction.withoutReads();
                    } else {
                        readers.put(binding.getDiscordChannelId(), id);
                    }
                }
            }
            kept.add(binding.withDirection(direction));
        }
        return new DiscordChannelBindings(kept);
    }

    /**
     * The game channel key that owns a Discord channel by naming its id
     * in any entry, or empty. What the bridge asks once it has learnt
     * which channel a webhook posts into, so a webhook posting into a
     * channel another game channel reads is caught as well.
     */
    public String ownerOfChannel(String discordChannelId) {
        if (discordChannelId == null || discordChannelId.length() == 0) {
            return "";
        }
        for (DiscordChannelBinding binding : this.bindings) {
            if (binding.getDiscordChannelId().equals(discordChannelId)) {
                return binding.key();
            }
        }
        return "";
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
     * The bindings a game line goes out through, in config order: the
     * Faction channel's bindings for the sender's faction, else the
     * channel's own, else none. Faction ids compare case-insensitively.
     */
    public List<DiscordChannelBinding> forGame(ChatChannel channel, String factionId) {
        if (channel == null) {
            return Collections.emptyList();
        }
        if (channel == ChatChannel.FACTION && factionId != null
                && factionId.trim().length() > 0) {
            List<DiscordChannelBinding> scoped = this.byKey.get(DiscordChannelBinding.keyOf(
                    channel, factionId.trim().toLowerCase(Locale.ROOT)));
            if (scoped != null) {
                return scoped;
            }
        }
        List<DiscordChannelBinding> own = this.byKey.get(channel.getId());
        return own == null ? Collections.<DiscordChannelBinding>emptyList() : own;
    }

    /** The one binding that reads the given Discord channel, or null. */
    public DiscordChannelBinding readerOf(String discordChannelId) {
        return discordChannelId == null ? null
                : this.readerByChannel.get(discordChannelId);
    }

    /** The binding with this id, or null. */
    public DiscordChannelBinding byId(String id) {
        return id == null ? null : this.byId.get(id);
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

    /** Every Discord channel any binding names by id, once each, in config order. */
    public List<String> channels() {
        return this.channels;
    }

    public boolean readsAnything() {
        return !this.reading.isEmpty();
    }

    public boolean sendsAnything() {
        return !this.destinations.isEmpty();
    }

    public boolean isEmpty() {
        return this.bindings.isEmpty();
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
