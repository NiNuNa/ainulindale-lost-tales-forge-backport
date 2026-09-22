package com.ninuna.losttales.network.packet;

import java.util.Map;
import java.util.LinkedHashMap;
import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.chat.ChatPresentationMode;
import com.ninuna.losttales.chat.ChatChannelScope;
import com.ninuna.losttales.chat.ChatChannelDescriptor;
import com.ninuna.losttales.chat.ChatChannelAccess;
import com.ninuna.losttales.chat.ChatChannelIconCatalog;
import com.ninuna.losttales.chat.ChatChannelIconSpec;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.profanity.ChatProfanityWords;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Server-to-client: what this player may do in the chat, and everything
 * a client draws from the server's word alone. Sent on login and again
 * whenever something in it changes or a refused message makes it worth
 * saying again; the server still checks on every request regardless.
 * Every fact here is presentation: the Operator channel's send gate, the
 * role mask this player holds (what lets a client notice {@code @Operator}
 * was addressed to it), the <em>role roster</em> of every online account
 * holding a role with its mask (account names and role marks are public
 * on the tab list, so nothing here widens what a client can learn), the
 * senders under a mute (sent to moderators only), the <em>role
 * catalogue</em> in force (so a client draws config-defined roles it has
 * never seen in code), the <em>channel gates</em> (the ids this client may
 * read and send into), the two capability flags the moderation menus and
 * the Server Settings button are offered on, every capability held by id,
 * the channels this server defines for itself, the roles told apart by
 * identity (the account's own mask, each own character's mask, and each
 * roster holder's account mask beside the character they play, since a
 * role given to an account is worn by every character of it and one given
 * to a character by that character alone), the server's Proximity radius,
 * the icons the server puts on its channels, and the words the server
 * adds to the chat's profanity list.
 *
 * <p>The layout is complete or the payload is malformed: a client reads
 * exactly what this build's server writes, and a payload that stops
 * short, names an unknown role bit, repeats a channel or holds an entry
 * that reads as nothing is refused whole.</p>
 */
public final class LostTalesChatAccessPacket implements IMessage {
    private static final int MAX_HOLDERS = 256;
    private static final int MAX_HOLDER_NAME_BYTES = 64;
    /** As many capability ids as a player could plausibly hold. */
    public static final int MAX_CAPABILITIES = 128;
    private static final int MAX_CAPABILITY_ID_BYTES = 64;
    /** As many as the mute store holds; an operator is told them all. */
    public static final int MAX_MUTED_SENDERS = 1024;
    private static final int MAX_ROLE_ID_BYTES = ChatAccountRole.MAX_ID_LENGTH * 4;
    private static final int MAX_ROLE_TEXT_BYTES = ChatAccountRole.MAX_TEXT_LENGTH * 4;
    private static final int MAX_ROLE_DESCRIPTION_BYTES =
            ChatAccountRole.MAX_DESCRIPTION_LENGTH * 4;
    private static final int MAX_ROLE_BYTES = MAX_ROLE_ID_BYTES + 2 * MAX_ROLE_TEXT_BYTES
            + MAX_ROLE_DESCRIPTION_BYTES + 32;
    /** A channel id is bounded as the send packet bounds the same field. */
    private static final int MAX_CHANNEL_ID_BYTES = 16;
    /** More channel ids than this is a broken payload, not an access answer. */
    private static final int MAX_CHANNEL_IDS = 64;
    /** A channel icon's text is plain ASCII, so its bytes are its characters. */
    private static final int MAX_CHANNEL_ICON_BYTES =
            ChatChannelIconSpec.MAX_TEXT_LENGTH;
    /** A channel's shown name is bounded like a role's text. */
    /** Four bytes per character is the most UTF-8 spends on one. */
    private static final int MAX_CHANNEL_NAME_BYTES =
            ChatChannelDescriptor.MAX_DISPLAY_NAME_LENGTH * 4;
    /** An enum constant's name, for the facts a channel is described by. */
    private static final int MAX_ENUM_NAME_BYTES = 32;
    /** More own characters than a roster could hold is a broken payload. */
    private static final int MAX_OWN_CHARACTERS = 32;
    /** The most channels a statement names as linked to Discord. */
    public static final int MAX_DISCORD_LINKS = 64;
    /** A link key's longest: {@code faction:} and a faction's id. */
    private static final int MAX_DISCORD_LINK_BYTES = 96;
    /** The Proximity radius's own upper bound in the server's config. */
    public static final int MAX_PROXIMITY_RADIUS = 512;

    private static final int MAX_PACKET_BYTES = 16
            + MAX_HOLDERS * (MAX_HOLDER_NAME_BYTES + 8)
            + 2 + MAX_MUTED_SENDERS * 16
            + 1 + ChatRoleCatalog.MAX_ROLES * MAX_ROLE_BYTES + 8 + 2
            + 2 + MAX_CAPABILITIES * MAX_CAPABILITY_ID_BYTES
            + 2 + 2 * MAX_CHANNEL_IDS * (MAX_CHANNEL_ID_BYTES + 2)
            + 1 + MAX_CHANNEL_IDS * (MAX_CHANNEL_ID_BYTES
                    + MAX_CHANNEL_NAME_BYTES + 4 * MAX_ENUM_NAME_BYTES + 16)
            + 4 + 2 + MAX_OWN_CHARACTERS * 20
            + 2 + MAX_HOLDERS * 21 + 2
            + 1 + ChatChannelIconCatalog.MAX_ICONS
                    * (MAX_CHANNEL_ID_BYTES + MAX_CHANNEL_ICON_BYTES + 4)
            + 2 + ChatProfanityWords.MAX_WORDS
                    * (ChatProfanityWords.MAX_ENTRY_BYTES + 2)
            + 1 + MAX_DISCORD_LINKS * (MAX_DISCORD_LINK_BYTES + 2);
    /**
     * Every channel this build knows, by id: what a payload written
     * without a channel answer reads as, and what a client falls back to.
     */
    public static List<String> allChannelIds() {
        List<String> ids = new ArrayList<String>();
        for (ChatChannel channel : ChatChannel.values()) {
            ids.add(channel.getId());
        }
        return Collections.unmodifiableList(ids);
    }

    private boolean adminAccess;
    private int roleMask;
    private List<RoleHolder> roleHolders = Collections.emptyList();
    /**
     * The sender ids under a server mute — accounts and Discord members
     * alike — sent to operators only, so their menus can offer to lift a
     * mute where one is in force and to lay one where none is. Empty
     * for everyone else: who is muted is the operators' business.
     */
    private List<UUID> mutedSenders = Collections.emptyList();
    /** The roles in force, in precedence order; empty means the built-ins. */
    private List<ChatAccountRole> catalog = Collections.emptyList();
    /**
     * The channels this player may read and send into, by id. Ids and not
     * a bit set over the declaration order: a channel's id is its wire
     * surface and is permanent, while the order the constants happen to be
     * declared in is not, and a set of bits also could not carry a channel
     * a server defines for itself.
     */
    private List<String> readableChannels = allChannelIds();
    private List<String> sendableChannels = allChannelIds();
    private boolean canModerate;
    private boolean canEditServerConfig;
    /**
     * Every capability the server says this player holds, by id. What
     * the client offers its menus on, so a capability added later needs
     * no new flag. Presentation only: the server decides again on the
     * request.
     */
    private List<String> capabilities = Collections.emptyList();
    /**
     * The channels this server has of its own. The built-in ones are in
     * both builds' code and are never sent; these are the ones a client
     * would otherwise never have heard of, and cannot show until it is
     * told. Empty from a server that defines none.
     */
    private List<ChatChannelDescriptor> definedChannels = Collections.emptyList();
    /**
     * The account's own roles, apart from the character being played:
     * what an account line wears, and what every character of the
     * account wears too.
     */
    private int accountRoleMask;
    /** The roles assigned to each of the player's own characters, by id. */
    private Map<UUID, Integer> characterRoleMasks = Collections.emptyMap();
    /** The server's Proximity radius in blocks; zero when unstated. */
    private int proximityRadius;
    /** The icons the server puts on its channels, by channel id: the channels file's choice. */
    private Map<String, ChatChannelIconSpec> channelIcons =
            Collections.emptyMap();
    /** The words the server adds to the chat's profanity list. */
    private ChatProfanityWords profanityWords = ChatProfanityWords.NONE;
    /**
     * The game channels linked to Discord now, by their link key: a
     * channel's id, or {@code faction:<id>} for one faction's chat. What
     * the tabs' Discord mark reads.
     */
    private List<String> discordLinks = Collections.emptyList();
    private boolean malformed;

    public LostTalesChatAccessPacket() {}

    public LostTalesChatAccessPacket(boolean adminAccess) {
        this(adminAccess, 0);
    }

    public LostTalesChatAccessPacket(boolean adminAccess, int roleMask) {
        this(adminAccess, roleMask, Collections.<RoleHolder>emptyList());
    }

    public LostTalesChatAccessPacket(boolean adminAccess, int roleMask,
                                     List<RoleHolder> roleHolders) {
        this(adminAccess, roleMask, roleHolders, Collections.<UUID>emptyList());
    }

    public LostTalesChatAccessPacket(boolean adminAccess, int roleMask,
                                     List<RoleHolder> roleHolders,
                                     List<UUID> mutedSenders) {
        this(adminAccess, roleMask, roleHolders, mutedSenders,
                ChatRoleCatalog.current().roles(), allChannelIds(), allChannelIds());
    }

    public LostTalesChatAccessPacket(boolean adminAccess, int roleMask,
                                     List<RoleHolder> roleHolders,
                                     List<UUID> mutedSenders,
                                     List<ChatAccountRole> catalog,
                                     List<String> readableChannels,
                                     List<String> sendableChannels) {
        this(adminAccess, roleMask, roleHolders, mutedSenders, catalog,
                readableChannels, sendableChannels, false, false);
    }

    public LostTalesChatAccessPacket(boolean adminAccess, int roleMask,
                                     List<RoleHolder> roleHolders,
                                     List<UUID> mutedSenders,
                                     List<ChatAccountRole> catalog,
                                     List<String> readableChannels,
                                     List<String> sendableChannels,
                                     boolean canModerate, boolean canEditServerConfig) {
        this(adminAccess, roleMask, roleHolders, mutedSenders, catalog,
                readableChannels, sendableChannels, canModerate, canEditServerConfig,
                Collections.<String>emptyList());
    }

    public LostTalesChatAccessPacket(boolean adminAccess, int roleMask,
                                     List<RoleHolder> roleHolders,
                                     List<UUID> mutedSenders,
                                     List<ChatAccountRole> catalog,
                                     List<String> readableChannels,
                                     List<String> sendableChannels,
                                     boolean canModerate, boolean canEditServerConfig,
                                     List<String> capabilities) {
        this(adminAccess, roleMask, roleHolders, mutedSenders, catalog,
                readableChannels, sendableChannels, canModerate,
                canEditServerConfig, capabilities, roleMask,
                Collections.<UUID, Integer>emptyMap(), 0);
    }

    public LostTalesChatAccessPacket(boolean adminAccess, int roleMask,
                                     List<RoleHolder> roleHolders,
                                     List<UUID> mutedSenders,
                                     List<ChatAccountRole> catalog,
                                     List<String> readableChannels,
                                     List<String> sendableChannels,
                                     boolean canModerate, boolean canEditServerConfig,
                                     List<String> capabilities,
                                     int accountRoleMask,
                                     Map<UUID, Integer> characterRoleMasks,
                                     int proximityRadius) {
        this(adminAccess, roleMask, roleHolders, mutedSenders, catalog,
                readableChannels, sendableChannels, canModerate,
                canEditServerConfig, capabilities, accountRoleMask,
                characterRoleMasks, proximityRadius,
                Collections.<String, ChatChannelIconSpec>emptyMap(),
                ChatProfanityWords.NONE);
    }

    /** The whole statement, see the class comment. */
    public LostTalesChatAccessPacket(boolean adminAccess, int roleMask,
                                     List<RoleHolder> roleHolders,
                                     List<UUID> mutedSenders,
                                     List<ChatAccountRole> catalog,
                                     List<String> readableChannels,
                                     List<String> sendableChannels,
                                     boolean canModerate, boolean canEditServerConfig,
                                     List<String> capabilities,
                                     int accountRoleMask,
                                     Map<UUID, Integer> characterRoleMasks,
                                     int proximityRadius,
                                     Map<String, ChatChannelIconSpec> channelIcons,
                                     ChatProfanityWords profanityWords) {
        this.accountRoleMask = accountRoleMask & roleMask;
        Map<UUID, Integer> characters = new LinkedHashMap<UUID, Integer>();
        if (characterRoleMasks != null) {
            for (Map.Entry<UUID, Integer> entry : characterRoleMasks.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null
                        && entry.getValue().intValue() != 0
                        && characters.size() < MAX_OWN_CHARACTERS) {
                    characters.put(entry.getKey(), entry.getValue());
                }
            }
        }
        this.characterRoleMasks = Collections.unmodifiableMap(characters);
        this.proximityRadius = Math.max(0,
                Math.min(MAX_PROXIMITY_RADIUS, proximityRadius));
        List<String> held = new ArrayList<String>();
        if (capabilities != null) {
            for (String capability : capabilities) {
                if (capability != null && capability.trim().length() > 0
                        && held.size() < MAX_CAPABILITIES) {
                    held.add(capability.trim());
                }
            }
        }
        this.capabilities = Collections.unmodifiableList(held);
        this.adminAccess = adminAccess;
        this.canModerate = canModerate;
        this.canEditServerConfig = canEditServerConfig;
        this.roleMask = roleMask;
        this.roleHolders = roleHolders == null || roleHolders.isEmpty()
                ? Collections.<RoleHolder>emptyList()
                : Collections.unmodifiableList(
                        new ArrayList<RoleHolder>(roleHolders.size()
                                > MAX_HOLDERS
                                ? roleHolders.subList(0, MAX_HOLDERS)
                                : roleHolders));
        List<UUID> muted = new ArrayList<UUID>();
        if (mutedSenders != null) {
            for (UUID sender : mutedSenders) {
                if (sender != null && muted.size() < MAX_MUTED_SENDERS) {
                    muted.add(sender);
                }
            }
        }
        this.mutedSenders = muted.isEmpty()
                ? Collections.<UUID>emptyList()
                : Collections.unmodifiableList(muted);
        List<ChatAccountRole> roles = new ArrayList<ChatAccountRole>();
        if (catalog != null) {
            for (ChatAccountRole role : catalog) {
                if (role != null && !role.isNone() && roles.size() < ChatRoleCatalog.MAX_ROLES) {
                    roles.add(role);
                }
            }
        }
        this.catalog = Collections.unmodifiableList(roles);
        this.readableChannels = channelIds(readableChannels);
        this.sendableChannels = channelIds(sendableChannels);
        this.definedChannels = definedChannels();
        this.channelIcons = channelIcons(channelIcons);
        this.profanityWords = profanityWords == null
                ? ChatProfanityWords.NONE : profanityWords;
    }

    /** The given icons, keyed by trimmed id, deduplicated and bounded. */
    private static Map<String, ChatChannelIconSpec> channelIcons(
            Map<String, ChatChannelIconSpec> icons) {
        Map<String, ChatChannelIconSpec> kept =
                new LinkedHashMap<String, ChatChannelIconSpec>();
        if (icons != null) {
            for (Map.Entry<String, ChatChannelIconSpec> entry
                    : icons.entrySet()) {
                String id = entry.getKey() == null ? "" : entry.getKey().trim();
                if (id.length() > 0 && entry.getValue() != null
                        && !kept.containsKey(id)
                        && kept.size() < ChatChannelIconCatalog.MAX_ICONS) {
                    kept.put(id, entry.getValue());
                }
            }
        }
        return Collections.unmodifiableMap(kept);
    }

    /** The channels in force that this build does not have of its own. */
    private static List<ChatChannelDescriptor> definedChannels() {
        List<ChatChannelDescriptor> defined =
                new ArrayList<ChatChannelDescriptor>();
        for (ChatChannel channel : ChatChannel.values()) {
            if (!ChatChannel.isBuiltIn(channel)
                    && defined.size() < MAX_CHANNEL_IDS) {
                defined.add(channel.getDescriptor());
            }
        }
        return Collections.unmodifiableList(defined);
    }

    /** The given ids, trimmed, deduplicated and bounded. */
    private static List<String> channelIds(List<String> ids) {
        List<String> kept = new ArrayList<String>();
        if (ids != null) {
            for (String id : ids) {
                String trimmed = id == null ? "" : id.trim();
                if (trimmed.length() > 0 && !kept.contains(trimmed)
                        && kept.size() < MAX_CHANNEL_IDS) {
                    kept.add(trimmed);
                }
            }
        }
        return Collections.unmodifiableList(kept);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat access packet size");
            }
            this.adminAccess = buffer.readBoolean();
            int roleMask = buffer.readInt();
            int holderCount = buffer.readUnsignedShort();
            if (holderCount > MAX_HOLDERS) {
                throw new LostTalesPacketCodec.DecodeException(
                        "too many role holders");
            }
            String[] holderNames = new String[holderCount];
            int[] holderMasks = new int[holderCount];
            for (int index = 0; index < holderCount; index++) {
                String name = LostTalesPacketCodec.readUtf8String(
                        buffer, MAX_HOLDER_NAME_BYTES);
                int mask = buffer.readInt();
                if (name.trim().length() == 0 || mask == 0) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid role holder");
                }
                holderNames[index] = name.trim();
                holderMasks[index] = mask;
            }
            int mutedCount = buffer.readUnsignedShort();
            if (mutedCount > MAX_MUTED_SENDERS) {
                throw new LostTalesPacketCodec.DecodeException(
                        "too many muted senders");
            }
            List<UUID> muted = new ArrayList<UUID>(mutedCount);
            for (int index = 0; index < mutedCount; index++) {
                muted.add(new UUID(buffer.readLong(), buffer.readLong()));
            }
            // The catalogue in force is what every mask is read against;
            // an empty one means the built-ins.
            int roleCount = buffer.readUnsignedByte();
            if (roleCount > ChatRoleCatalog.MAX_ROLES) {
                throw new LostTalesPacketCodec.DecodeException("too many roles");
            }
            List<ChatAccountRole> roles = new ArrayList<ChatAccountRole>(roleCount);
            for (int index = 0; index < roleCount; index++) {
                roles.add(readRole(buffer));
            }
            ChatRoleCatalog known = roles.isEmpty() ? ChatRoleCatalog.builtIn()
                    : ChatRoleCatalog.fromWire(roles);
            List<String> readable = readChannelIds(buffer);
            List<String> sendable = readChannelIds(buffer);
            boolean moderate = buffer.readBoolean();
            boolean editConfig = buffer.readBoolean();
            int capabilityCount = buffer.readUnsignedShort();
            if (capabilityCount > MAX_CAPABILITIES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "too many capabilities");
            }
            List<String> held = new ArrayList<String>(capabilityCount);
            for (int index = 0; index < capabilityCount; index++) {
                String id = LostTalesPacketCodec.readUtf8String(
                        buffer, MAX_CAPABILITY_ID_BYTES);
                if (id.trim().length() == 0) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid capability id");
                }
                held.add(id.trim());
            }
            int channelCount = buffer.readUnsignedByte();
            if (channelCount > MAX_CHANNEL_IDS) {
                throw new LostTalesPacketCodec.DecodeException(
                        "too many defined channels");
            }
            List<ChatChannelDescriptor> defined =
                    new ArrayList<ChatChannelDescriptor>(channelCount);
            for (int index = 0; index < channelCount; index++) {
                defined.add(readChannel(buffer));
            }
            int accountRoles = buffer.readInt();
            int characterCount = buffer.readUnsignedShort();
            if (characterCount > MAX_OWN_CHARACTERS) {
                throw new LostTalesPacketCodec.DecodeException(
                        "too many characters");
            }
            Map<UUID, Integer> characters = new LinkedHashMap<UUID, Integer>();
            for (int index = 0; index < characterCount; index++) {
                UUID id = new UUID(buffer.readLong(), buffer.readLong());
                int mask = buffer.readInt();
                if (mask == 0 || characters.containsKey(id)) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid character roles");
                }
                characters.put(id, Integer.valueOf(mask));
            }
            if (buffer.readUnsignedShort() != holderCount) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid role holder split");
            }
            int[] holderAccountRoles = new int[holderCount];
            UUID[] holderCharacters = new UUID[holderCount];
            for (int index = 0; index < holderCount; index++) {
                holderAccountRoles[index] = buffer.readInt();
                holderCharacters[index] = buffer.readBoolean()
                        ? new UUID(buffer.readLong(), buffer.readLong())
                        : null;
            }
            int radius = buffer.readUnsignedShort();
            if (radius > MAX_PROXIMITY_RADIUS) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid proximity radius");
            }
            int iconCount = buffer.readUnsignedByte();
            if (iconCount > ChatChannelIconCatalog.MAX_ICONS) {
                throw new LostTalesPacketCodec.DecodeException(
                        "too many channel icons");
            }
            Map<String, ChatChannelIconSpec> icons =
                    new LinkedHashMap<String, ChatChannelIconSpec>();
            for (int index = 0; index < iconCount; index++) {
                String id = LostTalesPacketCodec.readUtf8String(
                        buffer, MAX_CHANNEL_ID_BYTES).trim();
                ChatChannelIconSpec icon = ChatChannelIconSpec.parse(
                        LostTalesPacketCodec.readUtf8String(
                                buffer, MAX_CHANNEL_ICON_BYTES));
                if (id.length() == 0 || icon == null
                        || icons.containsKey(id)) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid channel icon");
                }
                icons.put(id, icon);
            }
            // The server's profanity words, each an entry the list reads
            // as it reads a config line; one it would skip is refused.
            int wordCount = buffer.readUnsignedShort();
            if (wordCount > ChatProfanityWords.MAX_WORDS) {
                throw new LostTalesPacketCodec.DecodeException(
                        "too many profanity words");
            }
            String[] entries = new String[wordCount];
            for (int index = 0; index < wordCount; index++) {
                entries[index] = LostTalesPacketCodec.readUtf8String(
                        buffer, ChatProfanityWords.MAX_ENTRY_BYTES);
            }
            ChatProfanityWords words = ChatProfanityWords.parse(
                    entries, ChatProfanityWords.MAX_WORDS, null);
            if (words.size() != wordCount) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid profanity word");
            }
            // The channels linked to Discord, each a key the tabs can
            // match and nothing else.
            int linkCount = buffer.readUnsignedByte();
            if (linkCount > MAX_DISCORD_LINKS) {
                throw new LostTalesPacketCodec.DecodeException(
                        "too many Discord links");
            }
            List<String> links = new ArrayList<String>(linkCount);
            for (int index = 0; index < linkCount; index++) {
                String key = LostTalesPacketCodec.readUtf8String(buffer,
                        MAX_DISCORD_LINK_BYTES);
                if (!isLinkKey(key) || links.contains(key)) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid Discord link");
                }
                links.add(key);
            }
            LostTalesPacketCodec.requireFinished(buffer);
            int knownMask = known.knownMask();
            List<RoleHolder> stated = new ArrayList<RoleHolder>(holderCount);
            for (int index = 0; index < holderCount; index++) {
                if ((holderMasks[index] & ~knownMask) != 0) {
                    throw new LostTalesPacketCodec.DecodeException("invalid role holder");
                }
                if ((holderAccountRoles[index] & ~holderMasks[index]) != 0) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid role holder split");
                }
                stated.add(new RoleHolder(holderNames[index], holderMasks[index],
                        holderAccountRoles[index], holderCharacters[index]));
            }
            for (Integer mask : characters.values()) {
                if ((mask.intValue() & ~knownMask) != 0) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid character roles");
                }
            }
            this.roleMask = (roleMask & ~knownMask) != 0 ? 0 : roleMask;
            this.accountRoleMask = (accountRoles & ~knownMask) != 0 ? 0
                    : accountRoles & this.roleMask;
            this.characterRoleMasks = Collections.unmodifiableMap(characters);
            this.proximityRadius = radius;
            this.roleHolders = Collections.unmodifiableList(stated);
            this.mutedSenders = Collections.unmodifiableList(muted);
            this.catalog = Collections.unmodifiableList(known.roles());
            this.readableChannels = Collections.unmodifiableList(readable);
            this.sendableChannels = Collections.unmodifiableList(sendable);
            this.canModerate = moderate;
            this.canEditServerConfig = editConfig;
            this.capabilities = Collections.unmodifiableList(held);
            this.definedChannels = Collections.unmodifiableList(defined);
            this.channelIcons = Collections.unmodifiableMap(icons);
            this.profanityWords = words;
            this.discordLinks = Collections.unmodifiableList(links);
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.adminAccess = false;
            this.roleMask = 0;
            this.roleHolders = Collections.emptyList();
            this.mutedSenders = Collections.emptyList();
            this.catalog = Collections.emptyList();
            this.readableChannels = allChannelIds();
            this.sendableChannels = allChannelIds();
            this.canModerate = false;
            this.canEditServerConfig = false;
            this.capabilities = Collections.emptyList();
            this.definedChannels = Collections.emptyList();
            this.accountRoleMask = 0;
            this.characterRoleMasks = Collections.emptyMap();
            this.proximityRadius = 0;
            this.channelIcons = Collections.emptyMap();
            this.profanityWords = ChatProfanityWords.NONE;
            this.discordLinks = Collections.emptyList();
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    /** One channel a server described, refused rather than guessed at. */
    private static ChatChannelDescriptor readChannel(ByteBuf buffer) {
        String id = LostTalesPacketCodec.readUtf8String(
                buffer, MAX_CHANNEL_ID_BYTES).trim();
        String name = LostTalesPacketCodec.readUtf8String(
                buffer, MAX_CHANNEL_NAME_BYTES);
        ChatPresentationMode presentation = named(ChatPresentationMode.class,
                LostTalesPacketCodec.readUtf8String(buffer, MAX_ENUM_NAME_BYTES));
        ChatRecipientRule rule = named(ChatRecipientRule.class,
                LostTalesPacketCodec.readUtf8String(buffer, MAX_ENUM_NAME_BYTES));
        ChatChannelAccess access = named(ChatChannelAccess.class,
                LostTalesPacketCodec.readUtf8String(buffer, MAX_ENUM_NAME_BYTES));
        ChatChannelScope scope = named(ChatChannelScope.class,
                LostTalesPacketCodec.readUtf8String(buffer, MAX_ENUM_NAME_BYTES));
        int colour = buffer.readInt();
        boolean bridgeable = buffer.readBoolean();
        if (id.length() == 0 || presentation == null || rule == null
                || access == null || scope == null) {
            throw new LostTalesPacketCodec.DecodeException(
                    "invalid channel description");
        }
        return new ChatChannelDescriptor(id, name, presentation, rule, access,
                colour & 0xFFFFFF, bridgeable, scope);
    }

    /** The constant a name names; null for one this build does not have. */
    private static <T extends Enum<T>> T named(Class<T> type, String name) {
        for (T constant : type.getEnumConstants()) {
            if (constant.name().equals(name)) {
                return constant;
            }
        }
        return null;
    }

    private static List<String> readChannelIds(ByteBuf buffer) {
        int count = buffer.readUnsignedByte();
        if (count > MAX_CHANNEL_IDS) {
            throw new LostTalesPacketCodec.DecodeException("too many channel ids");
        }
        List<String> ids = new ArrayList<String>(count);
        for (int index = 0; index < count; index++) {
            String id = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_CHANNEL_ID_BYTES).trim();
            if (id.length() == 0 || ids.contains(id)) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid channel id");
            }
            ids.add(id);
        }
        return ids;
    }

    private static void writeChannelIds(ByteBuf buffer, List<String> ids) {
        buffer.writeByte(ids.size());
        for (String id : ids) {
            LostTalesPacketCodec.writeUtf8String(buffer, id, MAX_CHANNEL_ID_BYTES);
        }
    }

    private static ChatAccountRole readRole(ByteBuf buffer) {
        String id = LostTalesPacketCodec.readUtf8String(buffer, MAX_ROLE_ID_BYTES);
        int bitIndex = buffer.readUnsignedByte();
        String nameKey = LostTalesPacketCodec.readUtf8String(buffer, MAX_ROLE_TEXT_BYTES);
        String name = LostTalesPacketCodec.readUtf8String(buffer, MAX_ROLE_TEXT_BYTES);
        String description = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_ROLE_DESCRIPTION_BYTES);
        int color = buffer.readInt();
        boolean mentionable = buffer.readBoolean();
        boolean locked = buffer.readBoolean();
        int rank = buffer.readInt();
        if (id.length() == 0 || bitIndex >= ChatRoleCatalog.MAX_ROLES) {
            throw new LostTalesPacketCodec.DecodeException("invalid role");
        }
        return ChatAccountRole.fromWire(id, bitIndex, nameKey, name, description, color,
                mentionable, locked, rank);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(this.adminAccess);
        buffer.writeInt(this.roleMask);
        buffer.writeShort(this.roleHolders.size());
        for (RoleHolder holder : this.roleHolders) {
            LostTalesPacketCodec.writeUtf8String(buffer, holder.getName(),
                    MAX_HOLDER_NAME_BYTES);
            buffer.writeInt(holder.getMask());
        }
        buffer.writeShort(this.mutedSenders.size());
        for (UUID sender : this.mutedSenders) {
            buffer.writeLong(sender.getMostSignificantBits());
            buffer.writeLong(sender.getLeastSignificantBits());
        }
        buffer.writeByte(this.catalog.size());
        for (ChatAccountRole role : this.catalog) {
            LostTalesPacketCodec.writeUtf8String(buffer, role.getId(), MAX_ROLE_ID_BYTES);
            buffer.writeByte(Integer.numberOfTrailingZeros(role.bit()));
            LostTalesPacketCodec.writeUtf8String(buffer, role.getNameKey(), MAX_ROLE_TEXT_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, role.getName(), MAX_ROLE_TEXT_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, role.getDescription(),
                    MAX_ROLE_DESCRIPTION_BYTES);
            buffer.writeInt(role.getColor());
            buffer.writeBoolean(role.isMentionable());
            buffer.writeBoolean(role.isLocked());
            buffer.writeInt(role.getRank());
        }
        writeChannelIds(buffer, this.readableChannels);
        writeChannelIds(buffer, this.sendableChannels);
        buffer.writeBoolean(this.canModerate);
        buffer.writeBoolean(this.canEditServerConfig);
        buffer.writeShort(this.capabilities.size());
        for (String capability : this.capabilities) {
            LostTalesPacketCodec.writeUtf8String(buffer, capability,
                    MAX_CAPABILITY_ID_BYTES);
        }
        // The channels this server has of its own.
        buffer.writeByte(this.definedChannels.size());
        for (ChatChannelDescriptor channel : this.definedChannels) {
            LostTalesPacketCodec.writeUtf8String(buffer, channel.getId(),
                    MAX_CHANNEL_ID_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer,
                    channel.getDisplayName(), MAX_CHANNEL_NAME_BYTES);
            // By name and not by position: an enum's declaration order is
            // not something either side promises the other.
            LostTalesPacketCodec.writeUtf8String(buffer,
                    channel.getPresentation().name(), MAX_ENUM_NAME_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer,
                    channel.getRecipientRule().name(), MAX_ENUM_NAME_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer,
                    channel.getAccess().name(), MAX_ENUM_NAME_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer,
                    channel.getScope().name(), MAX_ENUM_NAME_BYTES);
            buffer.writeInt(channel.getDisplayColor());
            buffer.writeBoolean(channel.isBridgeable());
        }
        // Then the roles told apart by identity, and the Proximity radius.
        buffer.writeInt(this.accountRoleMask);
        buffer.writeShort(this.characterRoleMasks.size());
        for (Map.Entry<UUID, Integer> entry : this.characterRoleMasks.entrySet()) {
            buffer.writeLong(entry.getKey().getMostSignificantBits());
            buffer.writeLong(entry.getKey().getLeastSignificantBits());
            buffer.writeInt(entry.getValue().intValue());
        }
        buffer.writeShort(this.roleHolders.size());
        for (RoleHolder holder : this.roleHolders) {
            buffer.writeInt(holder.getAccountMask());
            UUID character = holder.getCharacterId();
            buffer.writeBoolean(character != null);
            if (character != null) {
                buffer.writeLong(character.getMostSignificantBits());
                buffer.writeLong(character.getLeastSignificantBits());
            }
        }
        buffer.writeShort(this.proximityRadius);
        // Then the icons the channels wear, by id.
        buffer.writeByte(this.channelIcons.size());
        for (Map.Entry<String, ChatChannelIconSpec> entry
                : this.channelIcons.entrySet()) {
            LostTalesPacketCodec.writeUtf8String(buffer, entry.getKey(),
                    MAX_CHANNEL_ID_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer,
                    entry.getValue().toText(), MAX_CHANNEL_ICON_BYTES);
        }
        // Then the words the server adds to the profanity list.
        List<String> words = this.profanityWords.entries();
        buffer.writeShort(words.size());
        for (String entry : words) {
            LostTalesPacketCodec.writeUtf8String(buffer, entry,
                    ChatProfanityWords.MAX_ENTRY_BYTES);
        }
        // Last, the channels linked to Discord.
        buffer.writeByte(this.discordLinks.size());
        for (String key : this.discordLinks) {
            LostTalesPacketCodec.writeUtf8String(buffer, key,
                    MAX_DISCORD_LINK_BYTES);
        }
    }

    /**
     * The same statement naming the game channels linked to Discord:
     * each a channel's id or {@code faction:<id>}, at most
     * {@link #MAX_DISCORD_LINKS}, a key that is none of these left out.
     */
    public LostTalesChatAccessPacket withDiscordLinks(List<String> keys) {
        List<String> kept = new ArrayList<String>();
        if (keys != null) {
            for (String key : keys) {
                if (isLinkKey(key) && !kept.contains(key)
                        && kept.size() < MAX_DISCORD_LINKS) {
                    kept.add(key);
                }
            }
        }
        this.discordLinks = Collections.unmodifiableList(kept);
        return this;
    }

    /** The game channels linked to Discord, by link key. */
    public List<String> getDiscordLinks() { return this.discordLinks; }

    /** A link key: a channel's id, or {@code faction:} and a faction's id. */
    static boolean isLinkKey(String key) {
        return key != null && key.length() > 0
                && key.length() <= MAX_DISCORD_LINK_BYTES
                && key.matches("[a-z0-9_]+(?::[a-z0-9_.:-]+)?");
    }

    public boolean hasAdminAccess() { return this.adminAccess; }
    /** The roles the server says this player holds, as a bit set. */
    public int getRoleMask() { return this.roleMask; }
    /** Every online account holding a role, as the server states it. */
    public List<RoleHolder> getRoleHolders() { return this.roleHolders; }

    /** The muted sender ids; empty for anyone but an operator. */
    public List<UUID> getMutedSenders() { return this.mutedSenders; }
    /** The roles in force, in precedence order. */
    public List<ChatAccountRole> getCatalog() { return this.catalog; }
    /** The ids of the channels this player may read. */
    public List<String> getReadableChannels() { return this.readableChannels; }
    /** The ids of the channels this player may send into. */
    public List<String> getSendableChannels() { return this.sendableChannels; }
    /** The channels this server has of its own, for the client to put in force. */
    public List<ChatChannelDescriptor> getDefinedChannels() {
        return this.definedChannels;
    }
    /** Whether the server says this player may moderate the chat. */
    public boolean canModerate() { return this.canModerate; }
    /** Whether the server says this player may edit its settings. */
    public boolean canEditServerConfig() { return this.canEditServerConfig; }
    /** Every capability the server says this player holds, by id. */
    public List<String> getCapabilities() { return this.capabilities; }
    /**
     * The account's own roles, apart from the character being played.
     */
    public int getAccountRoleMask() { return this.accountRoleMask; }
    /** The roles assigned to each of the player's own characters, by id. */
    public Map<UUID, Integer> getCharacterRoleMasks() {
        return this.characterRoleMasks;
    }
    /** The server's Proximity radius in blocks; zero when unstated. */
    public int getProximityRadius() { return this.proximityRadius; }
    /** The icons the server puts on its channels, by id; empty when unstated. */
    /** The words the server adds to the chat's profanity list; none when it adds none. */
    public ChatProfanityWords getProfanityWords() { return this.profanityWords; }
    public Map<String, ChatChannelIconSpec> getChannelIcons() {
        return this.channelIcons;
    }
    public boolean isMalformed() { return this.malformed; }

    /**
     * One online account and the roles it wears as the identity it is
     * playing — masks are never zero — with the account's own roles
     * apart from them and the character it plays, where the payload
     * stated them.
     */
    public static final class RoleHolder {
        private final String name;
        private final int mask;
        private final int accountMask;
        private final UUID characterId;

        public RoleHolder(String name, int mask) {
            this(name, mask, mask, null);
        }

        public RoleHolder(String name, int mask, int accountMask,
                          UUID characterId) {
            this.name = name == null ? "" : name;
            this.mask = mask;
            this.accountMask = accountMask & mask;
            this.characterId = characterId;
        }

        public String getName() { return this.name; }
        /** The roles worn as the identity being played. */
        public int getMask() { return this.mask; }
        /** The account's own roles, worn by every character of it. */
        public int getAccountMask() { return this.accountMask; }
        /** The character the account is playing; null for none or unstated. */
        public UUID getCharacterId() { return this.characterId; }
    }

    public static final class Handler implements IMessageHandler<
            LostTalesChatAccessPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatAccessPacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleChatAccess(message);
                }
            });
            return null;
        }
    }
}
