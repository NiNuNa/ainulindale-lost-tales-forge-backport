package com.ninuna.losttales.chat;

import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small stable channel catalogue shared by packet validation and client UI.
 * Each constant is one built-in {@link ChatChannelDescriptor}: the id, how
 * its lines present their sender, the routing rule, the access a player
 * needs, the colour, and whether the Discord bridge may carry it — the
 * Party channel, the console and whispers are private and never leave
 * the game. The string ids are the wire and storage surface —
 * packets and the layout file carry them, and client view state is keyed
 * by tab identity — so an id is permanent while declaration order carries
 * no meaning of its own; the order channels are presented in is
 * {@link #presentationOrder()}.
 */
public final class ChatChannel {

    /**
     * Every channel in force, by id, in the order they were registered.
     * The built-ins register as this class is first read; a server may
     * register its own beside them, and everything that walks the
     * channels walks this rather than a fixed set.
     */
    private static final Map<String, ChatChannel> BY_ID =
            new LinkedHashMap<String, ChatChannel>();

    public static final ChatChannel ALL = register("all", "Global", ChatPresentationMode.IN_CHARACTER,
            ChatRecipientRule.GLOBAL, ChatChannelAccess.NONE,
            LostTalesColors.rgb(LostTalesColors.FERN_GREEN), true);
    // Orchid, the palette's pink, so the two open channels never share
    // a family: Global is green.
    public static final ChatChannel PROXIMITY = register("proximity", "Proximity", ChatPresentationMode.IN_CHARACTER,
            ChatRecipientRule.PROXIMITY, ChatChannelAccess.NONE,
            LostTalesColors.rgb(LostTalesColors.ORCHID), true);
    // Presentation shows the member's own party colour; this seafoam is
    // only the fallback outside a party.
    public static final ChatChannel PARTY = register("party", "Party", ChatPresentationMode.IN_CHARACTER,
            ChatRecipientRule.PARTY, ChatChannelAccess.PARTY_MEMBERSHIP,
            LostTalesColors.rgb(LostTalesColors.SEAFOAM), false,
            ChatChannelScope.PARTY);
    // Presentation shows the sender's LOTR faction colour; this palette
    // honey is only the indicator/selector fallback.
    public static final ChatChannel FACTION = register("faction", "Faction", ChatPresentationMode.IN_CHARACTER,
            ChatRecipientRule.FACTION, ChatChannelAccess.CHARACTER_FACTION,
            LostTalesColors.rgb(LostTalesColors.HONEY), true,
            ChatChannelScope.FACTION);
    /**
     * Out-of-character conversation, and the channel the Discord bridge
     * carries by default: out of character, everyone online reads it,
     * and it is there whether or not the server bridges anything.
     */
    public static final ChatChannel OOC = register("ooc", "OOC & Discord", ChatPresentationMode.OUT_OF_CHARACTER,
            ChatRecipientRule.GLOBAL, ChatChannelAccess.NONE,
            LostTalesColors.rgb(LostTalesColors.STEEL_BLUE), true);
    /** Staff channel: operators only, out of character; the wire id stays. */
    public static final ChatChannel ADMIN = register("admin", "Operator", ChatPresentationMode.OUT_OF_CHARACTER,
            ChatRecipientRule.OPERATORS, ChatChannelAccess.NONE,
            LostTalesColors.rgb(LostTalesColors.CRIMSON), true);
    /**
     * This player's own console: what only they see anyway — command
     * output, fast-travel countdowns, other mods' notices — plus anything
     * they type there, which is echoed back to them alone. Nobody else
     * is ever shown a line of it. The wire id stays {@code console}: it
     * is the console every player has had, and layouts and read marks
     * name it by that.
     */
    public static final ChatChannel CONSOLE = register("console", "Client Console", ChatPresentationMode.OUT_OF_CHARACTER,
            ChatRecipientRule.SELF, ChatChannelAccess.NONE,
            LostTalesColors.rgb(LostTalesColors.ROSE_GRAY), false);
    /**
     * The server's own console, one stream every reader shares: what the
     * server did — started, stopped, a command run, a message taken
     * back, a setting changed, a warning it had to raise — and the talk
     * its readers have over it. Held by the {@code chat.console.read}
     * capability rather than by a channel gate, and never bridged.
     */
    // The two consoles wear one grey: they are one kind of place.
    public static final ChatChannel SERVER_CONSOLE = register("server_console", "Server Console", ChatPresentationMode.OUT_OF_CHARACTER,
            ChatRecipientRule.CONSOLE_READERS, ChatChannelAccess.NONE,
            LostTalesColors.rgb(LostTalesColors.ROSE_GRAY), false);
    /**
     * A private conversation between two players, in character. Not a tab of its own:
     * every whisper partner is one tab on this channel, and the client
     * keeps them apart by the partner's name.
     */
    // A conversation wears the colour of the person it is with; this
    // plain ivory is only what one with nobody known behind it reads in.
    public static final ChatChannel WHISPER = register("whisper", "Whisper", ChatPresentationMode.IN_CHARACTER,
            ChatRecipientRule.WHISPER, ChatChannelAccess.NONE,
            LostTalesColors.rgb(LostTalesColors.HUD_LABEL), false);

    /** Tab, indicator, and cycle order for the built-in channels: the two
     *  global ones bracket the scoped role-play ones, then Party, staff,
     *  and the two consoles, this player's before the server's. Whispers
     *  are not listed: their tabs exist per conversation. Anything
     *  registered besides these follows them, in the order it was
     *  registered. */
    private static final List<ChatChannel> BUILT_IN_ORDER =
            Collections.unmodifiableList(Arrays.asList(
                    ALL, PROXIMITY, FACTION, OOC, PARTY, ADMIN, CONSOLE,
                    SERVER_CONSOLE));

    /** The ids that are the code's own and are never taken out of force. */
    private static final java.util.Set<String> BUILT_IN_IDS =
            Collections.unmodifiableSet(
                    new java.util.LinkedHashSet<String>(BY_ID.keySet()));

    private final ChatChannelDescriptor descriptor;

    private ChatChannel(ChatChannelDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    private static ChatChannel register(String id, String displayName,
                                        ChatPresentationMode presentation,
                                        ChatRecipientRule recipientRule,
                                        ChatChannelAccess access,
                                        int displayColor, boolean bridgeable) {
        return register(id, displayName, presentation, recipientRule, access,
                displayColor, bridgeable, ChatChannelScope.NONE);
    }

    private static ChatChannel register(String id, String displayName,
                                        ChatPresentationMode presentation,
                                        ChatRecipientRule recipientRule,
                                        ChatChannelAccess access,
                                        int displayColor, boolean bridgeable,
                                        ChatChannelScope scope) {
        return register(new ChatChannelDescriptor(id, displayName, presentation,
                recipientRule, access, displayColor, bridgeable, scope));
    }

    /**
     * Puts a channel in force. The id is what everything names it by —
     * packets, the layout file, the config — so registering one twice is
     * a mistake in whatever described it, not a channel to be replaced.
     */
    public static synchronized ChatChannel register(
            ChatChannelDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }
        String key = descriptor.getId().toLowerCase(Locale.ROOT);
        if (BY_ID.containsKey(key)) {
            throw new IllegalStateException(
                    "chat channel " + key + " is already registered");
        }
        ChatChannel channel = new ChatChannel(descriptor);
        BY_ID.put(key, channel);
        return channel;
    }

    /**
     * The built-in channels and nothing else. Every reload of the config
     * starts here: a channel a server defined is in force only while its
     * file says so, and registering the same id twice is refused, so the
     * set has to be put back before it is read again.
     */
    public static synchronized void resetToBuiltIn() {
        BY_ID.keySet().retainAll(BUILT_IN_IDS);
    }

    /**
     * Puts exactly these channels in force beside the built-in ones, as
     * one step.
     *
     * <p>Resetting and then registering would leave the registry holding
     * only the built-ins for as long as the loop takes, and on an
     * integrated server the logical server reads the same registry: a
     * line sent in that window would find its channel missing. Swapping
     * the set under the lock means nobody ever sees a half-applied
     * catalogue. A descriptor that cannot be put in force is reported
     * and skipped; the rest still go in.</p>
     */
    public static synchronized void installDefined(
            Iterable<ChatChannelDescriptor> descriptors, Warnings out) {
        Map<String, ChatChannel> replacement =
                new LinkedHashMap<String, ChatChannel>();
        for (Map.Entry<String, ChatChannel> entry : BY_ID.entrySet()) {
            if (BUILT_IN_IDS.contains(entry.getKey())) {
                replacement.put(entry.getKey(), entry.getValue());
            }
        }
        if (descriptors != null) {
            for (ChatChannelDescriptor descriptor : descriptors) {
                if (descriptor == null) {
                    continue;
                }
                String key = descriptor.getId().toLowerCase(Locale.ROOT);
                if (replacement.containsKey(key)) {
                    if (out != null) {
                        out.warn("Channel '" + key + "' is named twice; the "
                                + "second was skipped");
                    }
                    continue;
                }
                replacement.put(key, new ChatChannel(descriptor));
            }
        }
        BY_ID.clear();
        BY_ID.putAll(replacement);
    }

    /** Told about a channel that could not be put in force. */
    public interface Warnings {
        void warn(String message);
    }

    /**
     * How many channels a server's config may define beside the built-in
     * ones. The access packet carries the whole catalogue in one payload
     * and is bounded, so this is that bound stated where a config can be
     * warned about it rather than where a list is quietly cut short.
     */
    public static final int MAX_DEFINED_CHANNELS = 48;

    /** Whether the channel is one of the code's own rather than a config's. */
    public static synchronized boolean isBuiltIn(ChatChannel channel) {
        return channel != null && BUILT_IN_IDS.contains(
                channel.getId().toLowerCase(Locale.ROOT));
    }

    /** Every channel in force, in the order they were registered. */
    public static synchronized ChatChannel[] values() {
        return BY_ID.values().toArray(new ChatChannel[BY_ID.size()]);
    }

    /**
     * The constant's own name, for a log line or a test that needs to say
     * which channel it means. A channel a server defined has none of its
     * own and answers with its id.
     */
    public String name() {
        for (java.lang.reflect.Field field : ChatChannel.class.getFields()) {
            try {
                if (field.getType() == ChatChannel.class
                        && field.get(null) == this) {
                    return field.getName();
                }
            } catch (IllegalAccessException unreadable) {
                break;
            }
        }
        return getId();
    }

    public String getId() { return this.descriptor.getId(); }
    public String getDisplayName() { return this.descriptor.getDisplayName(); }
    /** How the channel's lines present their sender; see {@link ChatRolePresentation}. */
    public ChatPresentationMode getPresentation() {
        return this.descriptor.getPresentation();
    }
    public ChatRecipientRule getRecipientRule() {
        return this.descriptor.getRecipientRule();
    }
    public ChatChannelAccess getAccess() {
        return this.descriptor.getAccess();
    }
    public int getDisplayColor() { return this.descriptor.getDisplayColor(); }
    /** Whether the Discord bridge may carry this channel at all; see {@link ChatChannelDescriptor#isBridgeable}. */
    public boolean isBridgeable() { return this.descriptor.isBridgeable(); }
    /** What tells one conversation on the channel from another. */
    public ChatChannelScope getScope() { return this.descriptor.getScope(); }
    /** Whether a tab here carries the identity it is read as. */
    public boolean isScoped() { return this.descriptor.getScope().isScoped(); }
    /** The channel as the facts that describe it. */
    public ChatChannelDescriptor getDescriptor() { return this.descriptor; }

    /**
     * Every channel in the order the client presents them: the built-ins
     * in the order chosen for them, then anything a server registered, in
     * the order it did. Whispers are left out, as their tabs are one per
     * conversation rather than one for the channel.
     */
    public static synchronized List<ChatChannel> presentationOrder() {
        List<ChatChannel> order = new ArrayList<ChatChannel>(BUILT_IN_ORDER);
        for (ChatChannel channel : BY_ID.values()) {
            if (channel != WHISPER && !order.contains(channel)) {
                order.add(channel);
            }
        }
        return Collections.unmodifiableList(order);
    }

    /** The channel an id names; null for anything unknown. */
    public static ChatChannel fromId(String id) {
        String normalized = id == null ? ""
                : id.trim().toLowerCase(Locale.ROOT);
        for (ChatChannel channel : values()) {
            if (channel.getId().equals(normalized)) {
                return channel;
            }
        }
        return null;
    }
}
