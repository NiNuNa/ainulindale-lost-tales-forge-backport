package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatCodeNames;
import com.ninuna.losttales.util.LostTalesLanguages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Every game channel linked to a Discord channel, read once from the
 * config when the bridge starts, and the one place that answers where
 * lines go. The {@code channelLinks} list is written by the link
 * commands alone ({@link DiscordLinkEntries#format}), and each of its
 * entries binds one game channel to one Discord channel in that one
 * form:
 *
 * <pre>
 * ooc=BIDIRECTIONAL;channel=123456789012345678;webhook=https://discord.com/api/webhooks/...
 * gondor=GAME_TO_DISCORD;channel=...;webhook=...;language=de_DE
 * </pre>
 *
 * The part before {@code =} is the game channel's code name
 * ({@link ChatCodeNames}), a faction's for that faction's chat; then the
 * direction, {@code channel} (the Discord channel the bot reads and the
 * webhook posts into), {@code webhook} (where the bridge posts) and, for a
 * link whose language was chosen, {@code language} (DISC-30); without it
 * the link writes in the server's default. An entry of any other form is
 * refused with a warning and left in the file; a language the mod ships
 * no lang file for is the server's default, with a warning. A game channel may be linked as often as it has Discord channels
 * to go to — each entry is one destination, and a line goes to every
 * one that posts — while a Discord channel belongs to one game channel
 * only, whichever way lines cross it: the second game channel to name a
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
 * the channel's own word on whether it may be bridged is final. So is an
 * entry for a channel heard only nearby, whatever its word (DISC-03), and
 * one for a channel whose gate lets nobody read it, by the gates in
 * force as the bridge starts; a change to the gates restarts it. A fresh
 * file holds no link: links are made with a pairing code
 * ({@link DiscordLinkCodes}).
 */
public final class DiscordChannelLinks {
    public static final DiscordChannelLinks EMPTY =
            new DiscordChannelLinks(Collections.<DiscordChannelLink>emptyList());

    private static final char ENTRY_SEPARATOR = ';';
    private static final String CHANNEL_KEY = "channel";
    private static final String WEBHOOK_KEY = "webhook";
    private static final String LANGUAGE_KEY = "language";
    /** The most digits a Discord id has: it is a 64-bit number. */
    static final int MAX_SNOWFLAKE_DIGITS = 20;
    /**
     * A Discord webhook's address: Discord's own host (the canary and
     * test builds' too), an optional API version, the webhook's id and
     * its token, nothing after.
     */
    private static final Pattern DISCORD_WEBHOOK = Pattern.compile(
            "https://(?:(?:canary|ptb)\\.)?discord\\.com/api(?:/v\\d{1,2})?"
                    + "/webhooks/\\d{1," + MAX_SNOWFLAKE_DIGITS + "}/[A-Za-z0-9_-]{1,128}");

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

    /** Every link, in the order the config names them. */
    private final List<DiscordChannelLink> channelLinks;
    private final Map<String, DiscordChannelLink> byId;
    /** The links of each game channel key, in config order. */
    private final Map<String, List<DiscordChannelLink>> byKey;
    /** The links that read a Discord channel, one per channel, in config order. */
    private final List<DiscordChannelLink> reading;
    /**
     * Where the bridge posts, once per webhook: the first link to
     * post through each. What a notice for everyone is sent to.
     */
    private final List<DiscordChannelLink> destinations;
    /** Every Discord channel a link that reads or posts names, once each. */
    private final List<String> channels;

    private DiscordChannelLinks(List<DiscordChannelLink> parsed) {
        ArrayList<DiscordChannelLink> named =
                new ArrayList<DiscordChannelLink>(parsed.size());
        HashMap<String, Integer> ordinals = new HashMap<String, Integer>();
        for (DiscordChannelLink channelLink : parsed) {
            Integer seen = ordinals.get(channelLink.key());
            int ordinal = seen == null ? 1 : seen.intValue() + 1;
            ordinals.put(channelLink.key(), Integer.valueOf(ordinal));
            named.add(channelLink.withOrdinal(ordinal));
        }
        this.channelLinks = Collections.unmodifiableList(named);
        LinkedHashMap<String, DiscordChannelLink> ids =
                new LinkedHashMap<String, DiscordChannelLink>();
        LinkedHashMap<String, List<DiscordChannelLink>> keyed =
                new LinkedHashMap<String, List<DiscordChannelLink>>();
        ArrayList<DiscordChannelLink> reads = new ArrayList<DiscordChannelLink>();
        LinkedHashMap<String, DiscordChannelLink> posting =
                new LinkedHashMap<String, DiscordChannelLink>();
        LinkedHashSet<String> known = new LinkedHashSet<String>();
        for (DiscordChannelLink channelLink : this.channelLinks) {
            ids.put(channelLink.id(), channelLink);
            List<DiscordChannelLink> ofKey = keyed.get(channelLink.key());
            if (ofKey == null) {
                ofKey = new ArrayList<DiscordChannelLink>(2);
                keyed.put(channelLink.key(), ofKey);
            }
            ofKey.add(channelLink);
            if (channelLink.readsFromDiscord()) {
                reads.add(channelLink);
            }
            if (channelLink.sendsToDiscord()
                    && !posting.containsKey(channelLink.getWebhookUrl())) {
                posting.put(channelLink.getWebhookUrl(), channelLink);
            }
            // A switched-off entry binds its Discord channel to nothing,
            // so the bridge leaves that channel alone.
            if (channelLink.getDiscordChannelId().length() > 0
                    && (channelLink.readsFromDiscord() || channelLink.sendsToDiscord())) {
                known.add(channelLink.getDiscordChannelId());
            }
        }
        for (Map.Entry<String, List<DiscordChannelLink>> entry : keyed.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        this.byId = Collections.unmodifiableMap(ids);
        this.byKey = Collections.unmodifiableMap(keyed);
        this.reading = Collections.unmodifiableList(reads);
        this.destinations = Collections.unmodifiableList(
                new ArrayList<DiscordChannelLink>(posting.values()));
        this.channels = Collections.unmodifiableList(new ArrayList<String>(known));
    }

    /** Reads the config list; never throws, every fault is one warning. */
    public static DiscordChannelLinks parse(String[] entries,
                                               boolean botTokenPresent,
                                               Warnings warnings) {
        ArrayList<DiscordChannelLink> parsed = new ArrayList<DiscordChannelLink>();
        if (entries == null) {
            return EMPTY;
        }
        for (int index = 0; index < entries.length; index++) {
            DiscordChannelLink channelLink = parseEntry(entries[index], index + 1,
                    warnings);
            if (channelLink != null) {
                parsed.add(channelLink);
            }
        }
        return validated(parsed, botTokenPresent, warnings);
    }

    /**
     * One entry, the {@code position}th of the list, in the form the link
     * commands write; any other is refused. A warning about an entry that
     * cannot be read names it by that position alone and never repeats
     * its text: a token or a webhook's address pasted into the wrong
     * place would otherwise reach the log.
     */
    private static DiscordChannelLink parseEntry(String entry, int position,
                                                    Warnings warnings) {
        String text = entry == null ? "" : entry;
        int equals = text.indexOf('=');
        String[] parts = equals <= 0 ? new String[0]
                : text.substring(equals + 1).split(String.valueOf(ENTRY_SEPARATOR), -1);
        if ((parts.length != 3 && parts.length != 4)
                || !parts[1].startsWith(CHANNEL_KEY + "=")
                || !parts[2].startsWith(WEBHOOK_KEY + "=")
                || (parts.length == 4 && !parts[3].startsWith(LANGUAGE_KEY + "="))) {
            warn(warnings, "Discord link entry " + position + " is not"
                    + " <channel>=<direction>;channel=<id>;webhook=<address>"
                    + "[;language=<code>], the form the link commands write; ignored");
            return null;
        }
        String target = text.substring(0, equals);
        ChatCodeNames.Named named = ChatCodeNames.parse(target);
        if (named == null || !target.equals(
                DiscordChannelLink.keyOf(named.channel, named.scope))) {
            warn(warnings, "Discord link entry " + position
                    + " names no channel the game knows; ignored");
            return null;
        }
        ChatChannel channel = named.channel;
        String scope = named.scope;
        if (!channel.isBridgeable()) {
            warn(warnings, "Discord link for the " + channel.getDisplayName()
                    + " channel refused: that channel is private and never leaves the game");
            return null;
        }
        if (DiscordBridgePolicy.isHeardNearby(channel)) {
            warn(warnings, "Discord link for the " + channel.getDisplayName()
                    + " channel refused: that channel is heard only nearby and never"
                    + " leaves the game");
            return null;
        }
        if (!DiscordBridgePolicy.isOpenToTheBridge(channel)) {
            warn(warnings, "Discord link for the " + channel.getDisplayName()
                    + " channel refused: its gate in channels.cfg lets nobody read it,"
                    + " so nothing would cross");
            return null;
        }
        DiscordBridgeDirection direction = linkedDirection(parts[0]);
        if (direction == null) {
            warn(warnings, "Discord link '" + target + "' names no direction"
                    + " (GAME_TO_DISCORD, DISCORD_TO_GAME or BIDIRECTIONAL); ignored");
            return null;
        }
        String discordChannel = parts[1].substring(CHANNEL_KEY.length() + 1);
        if (!isSnowflake(discordChannel)) {
            warn(warnings, "Discord link '" + target
                    + "' has a channel id that is not a Discord id; ignored");
            return null;
        }
        String webhook = parts[2].substring(WEBHOOK_KEY.length() + 1);
        if (!isDiscordWebhook(webhook)) {
            // Never repeated in the log: an address is as good as a
            // password to whoever reads it.
            warn(warnings, "Discord link '" + target + "' has a webhook that is"
                    + " not a Discord webhook address; ignored");
            return null;
        }
        String language = parts.length == 4
                ? parts[3].substring(LANGUAGE_KEY.length() + 1) : "";
        if (language.length() > 0) {
            String shipped = LostTalesLanguages.find(language);
            if (shipped.length() == 0) {
                warn(warnings, "Discord link '" + target + "' names a language"
                        + " the mod ships no lang file for; it writes in the server's"
                        + " default");
            }
            language = shipped;
        }
        return new DiscordChannelLink(channel, scope, discordChannel, webhook,
                direction, language);
    }

    /** A direction a link can be made with, named exactly; null for any other. */
    private static DiscordBridgeDirection linkedDirection(String name) {
        for (DiscordBridgeDirection direction : DiscordBridgeDirection.values()) {
            if (direction != DiscordBridgeDirection.DISABLED
                    && direction.name().equals(name)) {
                return direction;
            }
        }
        return null;
    }

    /**
     * Trims every link to what it can do, drops an entry of a game
     * channel that names a webhook or a Discord channel the same game
     * channel names already, and refuses a second game channel a
     * Discord channel another has: the whole entry when it names a
     * Discord channel another game channel reads or posts into, its
     * posting when it posts through a webhook another posts through. A
     * Discord channel belongs to one game channel.
     */
    private static DiscordChannelLinks validated(List<DiscordChannelLink> parsed,
                                                    boolean botTokenPresent,
                                                    Warnings warnings) {
        ArrayList<DiscordChannelLink> kept = new ArrayList<DiscordChannelLink>();
        Set<String> seen = new HashSet<String>();
        Map<String, String> owners = new HashMap<String, String>();
        Map<String, String> posters = new HashMap<String, String>();
        Map<String, Integer> ordinals = new HashMap<String, Integer>();
        for (int index = 0; index < parsed.size(); index++) {
            DiscordChannelLink channelLink = parsed.get(index);
            String key = channelLink.key();
            boolean webhookSeen = !seen.add(key + "\nwebhook\n" + channelLink.getWebhookUrl());
            boolean channelSeen = !seen.add(key + "\nchannel\n"
                    + channelLink.getDiscordChannelId());
            if (webhookSeen || channelSeen) {
                warn(warnings, "Discord link '" + key + "' names a "
                        + (webhookSeen ? "webhook" : "Discord channel")
                        + " it names already; the first stands");
                continue;
            }
            Integer count = ordinals.get(key);
            int ordinal = count == null ? 1 : count.intValue() + 1;
            ordinals.put(key, Integer.valueOf(ordinal));
            String id = channelLink.withOrdinal(ordinal).id();
            DiscordBridgeDirection direction = channelLink.getDirection();
            String poster = posters.get(channelLink.getWebhookUrl());
            if (poster != null && !poster.equals(key)) {
                refuse(warnings, "Discord link '" + id + "' posts through"
                        + " the webhook '" + posters.get(
                                channelLink.getWebhookUrl() + "\nid")
                        + "' posts through: a Discord channel belongs to"
                        + " one game channel; posting is off for '" + id + "'");
                direction = direction.withoutSends();
            } else if (poster == null && direction.sendsToDiscord()) {
                posters.put(channelLink.getWebhookUrl(), key);
                posters.put(channelLink.getWebhookUrl() + "\nid", id);
            }
            if (direction.readsFromDiscord() && !botTokenPresent) {
                warn(warnings, "Discord link '" + id + "' needs the bot token to read;"
                        + " reading is off");
                direction = direction.withoutReads();
            }
            String discordChannel = channelLink.getDiscordChannelId();
            if (direction.readsFromDiscord() || direction.sendsToDiscord()) {
                String owner = owners.get(discordChannel);
                if (owner != null && !owner.equals(key)) {
                    refuse(warnings, "Discord link '" + id + "' names Discord"
                            + " channel " + discordChannel + ", which '" + owner
                            + "' has already: a Discord channel belongs to one"
                            + " game channel; '" + id + "' is off");
                    direction = DiscordBridgeDirection.DISABLED;
                } else {
                    owners.put(discordChannel, key);
                }
            }
            kept.add(channelLink.withDirection(direction));
        }
        return new DiscordChannelLinks(kept);
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
        for (DiscordChannelLink channelLink : this.channelLinks) {
            if ((channelLink.readsFromDiscord() || channelLink.sendsToDiscord())
                    && channelLink.getDiscordChannelId().equals(discordChannelId)) {
                return channelLink.key();
            }
        }
        return "";
    }

    /**
     * The link a Discord channel belongs to: the one that reads it or
     * posts into it by naming it; null for a channel no live link names.
     * What a topic and a slash command's answer take their language from.
     */
    public DiscordChannelLink linkOfChannel(String discordChannelId) {
        if (discordChannelId == null || discordChannelId.length() == 0) {
            return null;
        }
        for (DiscordChannelLink channelLink : this.channelLinks) {
            if ((channelLink.readsFromDiscord() || channelLink.sendsToDiscord())
                    && channelLink.getDiscordChannelId().equals(discordChannelId)) {
                return channelLink;
            }
        }
        return null;
    }

    /**
     * The link that posts through a webhook: the first to, since a
     * webhook posts for one link. Null for a webhook none posts through.
     */
    public DiscordChannelLink posterOf(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.length() == 0) {
            return null;
        }
        for (DiscordChannelLink channelLink : this.destinations) {
            if (channelLink.getWebhookUrl().equals(webhookUrl)) {
                return channelLink;
            }
        }
        return null;
    }

    /** Whether the text is a Discord webhook's address and nothing else. */
    static boolean isDiscordWebhook(String url) {
        return url != null && url.length() <= 256
                && DISCORD_WEBHOOK.matcher(url).matches();
    }

    /**
     * Whether the text is a Discord id: digits alone, at most
     * {@link #MAX_SNOWFLAKE_DIGITS} of them. Every Discord id the bridge
     * reads, saves or sends is checked here.
     */
    static boolean isSnowflake(String value) {
        if (value == null || value.length() == 0
                || value.length() > MAX_SNOWFLAKE_DIGITS) {
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

    /**
     * The links a game line goes out through, in config order: for the
     * Faction channel, those of the line's own faction, compared
     * case-insensitively; for any other, the channel's own; else none.
     */
    public List<DiscordChannelLink> forGame(ChatChannel channel, String factionId) {
        String key = DiscordChannelLink.keyOf(channel, factionId);
        List<DiscordChannelLink> own = key == null ? null : this.byKey.get(key);
        return own == null ? Collections.<DiscordChannelLink>emptyList() : own;
    }

    /** The link with this id, or null. */
    public DiscordChannelLink byId(String id) {
        return id == null ? null : this.byId.get(id);
    }

    /**
     * The link that reads that Discord channel, or null: at most one
     * link reads a Discord channel. What word arriving from Discord is
     * matched by, since a link's id is not stable across a reordering.
     */
    public DiscordChannelLink readerOf(String discordChannelId) {
        if (discordChannelId == null || discordChannelId.length() == 0) {
            return null;
        }
        for (DiscordChannelLink channelLink : this.reading) {
            if (discordChannelId.equals(channelLink.getDiscordChannelId())) {
                return channelLink;
            }
        }
        return null;
    }

    public List<DiscordChannelLink> all() {
        return this.channelLinks;
    }

    /** The links that read a Discord channel, one per channel. */
    public List<DiscordChannelLink> reading() {
        return this.reading;
    }

    /**
     * Where the bridge posts, once per webhook: the first link to
     * post through each, in config order. A notice for everyone goes to
     * each of these exactly once.
     */
    public List<DiscordChannelLink> destinations() {
        return this.destinations;
    }

    /**
     * Every Discord channel a link that reads or posts names by id,
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
        for (DiscordChannelLink channelLink : this.channelLinks) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(channelLink.toString());
        }
        return text.length() == 0 ? "none" : text.toString();
    }
}
