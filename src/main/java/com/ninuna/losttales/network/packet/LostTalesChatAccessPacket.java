package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.chat.ChatPresentationMode;
import com.ninuna.losttales.chat.ChatChannelScope;
import com.ninuna.losttales.chat.ChatChannelDescriptor;
import com.ninuna.losttales.chat.ChatChannelAccess;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Server-to-client: which restricted chat channels the player may use,
 * and which roles the player holds. The client cannot know its own
 * operator status on a dedicated server, so the server states it on
 * login and whenever a refused message makes it worth saying again; the
 * server still checks on every send regardless. The role mask is
 * presentation only — it is what lets this client notice that
 * {@code @Operator} was addressed to it — and, like every other role
 * fact, it is the server's word alone. The second flag on the wire once
 * gated a Discord channel of its own; that channel is OOC & Discord now
 * and exists for everyone, so the server always sends it set and this
 * client reads nothing from it.
 *
 * <p>Appended after the personal fields travels the <em>role roster</em>:
 * every online account that holds a role, with its mask. Account names
 * and role marks are public on the tab list and on every line those
 * players send, so nothing here widens what a client can learn; it only
 * lets the role hover card name a role's members, and a mention of a
 * role holder wear their colour before they have spoken.</p>
 *
 * <p>Appended after the muted senders travel the <em>role catalogue</em>
 * — every role in force, with its bit, look and rank, so a client draws
 * config-defined roles it has never seen in code — and the
 * <em>channel gates</em>: one bit per channel saying whether this
 * client may read it and one whether it may send into it, the server's
 * answer for this player alone. A payload written before either
 * existed reads as the built-in roles with every channel open.</p>
 *
 * <p>Then come two capability flags: whether this player may
 * <em>moderate</em> the chat — mute, unmute, take anyone's message back
 * — which is what the client's moderation menus are offered on, and
 * whether they may edit the server's settings, which is what the Server
 * Settings button is shown on. Presentation only, like every flag here:
 * the server decides again on each request. A payload written before
 * they travelled reads both as no.</p>
 *
 * <p>Last travels the list of every capability the player holds, by id,
 * which is what the client offers its menus on: a capability the code
 * gains later needs no flag of its own here. The two flags above stay
 * for a payload written before the list did, and say the same thing.</p>
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
    private static final int MAX_ROLE_BYTES = MAX_ROLE_ID_BYTES + 4 * MAX_ROLE_TEXT_BYTES
            + MAX_ROLE_DESCRIPTION_BYTES + 32;
    /** A channel id is bounded as the send packet bounds the same field. */
    private static final int MAX_CHANNEL_ID_BYTES = 16;
    /** More channel ids than this is a broken payload, not an access answer. */
    private static final int MAX_CHANNEL_IDS = 64;
    /** A channel's shown name is bounded like a role's text. */
    private static final int MAX_CHANNEL_NAME_BYTES = 64;
    /** An enum constant's name, for the facts a channel is described by. */
    private static final int MAX_ENUM_NAME_BYTES = 32;

    private static final int MAX_PACKET_BYTES = 16
            + MAX_HOLDERS * (MAX_HOLDER_NAME_BYTES + 8)
            + 2 + MAX_MUTED_SENDERS * 16
            + 1 + ChatRoleCatalog.MAX_ROLES * MAX_ROLE_BYTES + 8 + 2
            + 2 + MAX_CAPABILITIES * MAX_CAPABILITY_ID_BYTES
            + 2 + 2 * MAX_CHANNEL_IDS * (MAX_CHANNEL_ID_BYTES + 2)
            + 1 + MAX_CHANNEL_IDS * (MAX_CHANNEL_ID_BYTES
                    + MAX_CHANNEL_NAME_BYTES + 4 * MAX_ENUM_NAME_BYTES + 16);
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
    private boolean discordAccess;
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
    private boolean malformed;

    public LostTalesChatAccessPacket() {}

    public LostTalesChatAccessPacket(boolean adminAccess,
                                     boolean discordAccess) {
        this(adminAccess, discordAccess, 0);
    }

    public LostTalesChatAccessPacket(boolean adminAccess,
                                     boolean discordAccess, int roleMask) {
        this(adminAccess, discordAccess, roleMask,
                Collections.<RoleHolder>emptyList());
    }

    public LostTalesChatAccessPacket(boolean adminAccess,
                                     boolean discordAccess, int roleMask,
                                     List<RoleHolder> roleHolders) {
        this(adminAccess, discordAccess, roleMask, roleHolders,
                Collections.<UUID>emptyList());
    }

    public LostTalesChatAccessPacket(boolean adminAccess,
                                     boolean discordAccess, int roleMask,
                                     List<RoleHolder> roleHolders,
                                     List<UUID> mutedSenders) {
        this(adminAccess, discordAccess, roleMask, roleHolders, mutedSenders,
                ChatRoleCatalog.current().roles(), allChannelIds(), allChannelIds());
    }

    public LostTalesChatAccessPacket(boolean adminAccess,
                                     boolean discordAccess, int roleMask,
                                     List<RoleHolder> roleHolders,
                                     List<UUID> mutedSenders,
                                     List<ChatAccountRole> catalog,
                                     List<String> readableChannels,
                                     List<String> sendableChannels) {
        this(adminAccess, discordAccess, roleMask, roleHolders, mutedSenders, catalog,
                readableChannels, sendableChannels, false, false);
    }

    public LostTalesChatAccessPacket(boolean adminAccess,
                                     boolean discordAccess, int roleMask,
                                     List<RoleHolder> roleHolders,
                                     List<UUID> mutedSenders,
                                     List<ChatAccountRole> catalog,
                                     List<String> readableChannels,
                                     List<String> sendableChannels,
                                     boolean canModerate, boolean canEditServerConfig) {
        this(adminAccess, discordAccess, roleMask, roleHolders, mutedSenders, catalog,
                readableChannels, sendableChannels, canModerate, canEditServerConfig,
                Collections.<String>emptyList());
    }

    public LostTalesChatAccessPacket(boolean adminAccess,
                                     boolean discordAccess, int roleMask,
                                     List<RoleHolder> roleHolders,
                                     List<UUID> mutedSenders,
                                     List<ChatAccountRole> catalog,
                                     List<String> readableChannels,
                                     List<String> sendableChannels,
                                     boolean canModerate, boolean canEditServerConfig,
                                     List<String> capabilities) {
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
        this.discordAccess = discordAccess;
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
            this.discordAccess = buffer.readBoolean();
            // Appended after the two flags; a packet written before the
            // roles existed simply ends here and names none.
            int roleMask = buffer.readableBytes() >= 4
                    ? buffer.readInt() : 0;
            // Appended again: the online role holders. A packet written
            // before the roster existed ends here and names none.
            List<RoleHolder> holders = new ArrayList<RoleHolder>();
            if (buffer.readableBytes() >= 2) {
                int count = buffer.readUnsignedShort();
                if (count > MAX_HOLDERS) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "too many role holders");
                }
                for (int index = 0; index < count; index++) {
                    String name = LostTalesPacketCodec.readUtf8String(
                            buffer, MAX_HOLDER_NAME_BYTES);
                    int mask = buffer.readInt();
                    if (name.trim().length() == 0 || mask == 0) {
                        throw new LostTalesPacketCodec.DecodeException(
                                "invalid role holder");
                    }
                    holders.add(new RoleHolder(name.trim(), mask));
                }
            }
            // Appended once more: the muted senders. A packet written
            // before they travelled ends here and names none.
            List<UUID> muted = new ArrayList<UUID>();
            if (buffer.readableBytes() >= 2) {
                int count = buffer.readUnsignedShort();
                if (count > MAX_MUTED_SENDERS) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "too many muted senders");
                }
                for (int index = 0; index < count; index++) {
                    muted.add(new UUID(buffer.readLong(), buffer.readLong()));
                }
            }
            // Appended again: the role catalogue in force, which is what
            // the masks above are read against. Without it, the built-ins.
            List<ChatAccountRole> roles = new ArrayList<ChatAccountRole>();
            if (buffer.readableBytes() >= 1) {
                int count = buffer.readUnsignedByte();
                if (count > ChatRoleCatalog.MAX_ROLES) {
                    throw new LostTalesPacketCodec.DecodeException("too many roles");
                }
                for (int index = 0; index < count; index++) {
                    roles.add(readRole(buffer));
                }
            }
            ChatRoleCatalog known = roles.isEmpty() ? ChatRoleCatalog.builtIn()
                    : ChatRoleCatalog.fromWire(roles);
            // The channel gates for this player, each side a list of ids.
            List<String> readable = allChannelIds();
            List<String> sendable = allChannelIds();
            if (buffer.readableBytes() >= 1) {
                readable = readChannelIds(buffer);
                sendable = readChannelIds(buffer);
            }
            // Appended last of all: the two capability flags.
            boolean moderate = buffer.readableBytes() >= 1 && buffer.readBoolean();
            boolean editConfig = buffer.readableBytes() >= 1 && buffer.readBoolean();
            // Appended after the two flags: every capability held, by id.
            // A payload written before they travelled names none, and the
            // two flags stand for themselves.
            List<String> held = new ArrayList<String>();
            if (buffer.readableBytes() >= 2) {
                int count = buffer.readUnsignedShort();
                if (count > MAX_CAPABILITIES) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "too many capabilities");
                }
                for (int index = 0; index < count; index++) {
                    String id = LostTalesPacketCodec.readUtf8String(
                            buffer, MAX_CAPABILITY_ID_BYTES);
                    if (id.trim().length() == 0) {
                        throw new LostTalesPacketCodec.DecodeException(
                                "invalid capability id");
                    }
                    held.add(id.trim());
                }
            }
            List<ChatChannelDescriptor> defined =
                    new ArrayList<ChatChannelDescriptor>();
            if (buffer.readableBytes() >= 1) {
                int channelCount = buffer.readUnsignedByte();
                if (channelCount > MAX_CHANNEL_IDS) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "too many defined channels");
                }
                for (int index = 0; index < channelCount; index++) {
                    defined.add(readChannel(buffer));
                }
            }
            LostTalesPacketCodec.requireFinished(buffer);
            for (RoleHolder holder : holders) {
                if ((holder.getMask() & ~known.knownMask()) != 0) {
                    throw new LostTalesPacketCodec.DecodeException("invalid role holder");
                }
            }
            this.roleMask = (roleMask & ~known.knownMask()) != 0 ? 0 : roleMask;
            this.roleHolders = Collections.unmodifiableList(holders);
            this.mutedSenders = Collections.unmodifiableList(muted);
            this.catalog = Collections.unmodifiableList(known.roles());
            this.readableChannels = Collections.unmodifiableList(readable);
            this.sendableChannels = Collections.unmodifiableList(sendable);
            this.canModerate = moderate;
            this.canEditServerConfig = editConfig;
            this.capabilities = Collections.unmodifiableList(held);
            this.definedChannels = Collections.unmodifiableList(defined);
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.adminAccess = false;
            this.discordAccess = false;
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
        String tagKey = LostTalesPacketCodec.readUtf8String(buffer, MAX_ROLE_TEXT_BYTES);
        String name = LostTalesPacketCodec.readUtf8String(buffer, MAX_ROLE_TEXT_BYTES);
        String tag = LostTalesPacketCodec.readUtf8String(buffer, MAX_ROLE_TEXT_BYTES);
        String description = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_ROLE_DESCRIPTION_BYTES);
        int color = buffer.readInt();
        boolean mentionable = buffer.readBoolean();
        boolean locked = buffer.readBoolean();
        int rank = buffer.readInt();
        if (id.length() == 0 || bitIndex >= ChatRoleCatalog.MAX_ROLES) {
            throw new LostTalesPacketCodec.DecodeException("invalid role");
        }
        return ChatAccountRole.fromWire(id, bitIndex, nameKey, tagKey, name, tag,
                description, color, mentionable, locked, rank);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(this.adminAccess);
        buffer.writeBoolean(this.discordAccess);
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
            LostTalesPacketCodec.writeUtf8String(buffer, role.getTagKey(), MAX_ROLE_TEXT_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, role.getName(), MAX_ROLE_TEXT_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, role.getTag(), MAX_ROLE_TEXT_BYTES);
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
        // Appended last of all: the channels this server has of its own.
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
    }

    public boolean hasAdminAccess() { return this.adminAccess; }
    /** The roles the server says this player holds, as a bit set. */
    public int getRoleMask() { return this.roleMask; }
    /** The flag an older client gated its Discord tab on; always sent set. */
    public boolean hasDiscordAccess() { return this.discordAccess; }
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
    public boolean isMalformed() { return this.malformed; }

    /** One online account and the roles it holds; masks are never zero. */
    public static final class RoleHolder {
        private final String name;
        private final int mask;

        public RoleHolder(String name, int mask) {
            this.name = name == null ? "" : name;
            this.mask = mask;
        }

        public String getName() { return this.name; }
        public int getMask() { return this.mask; }
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
