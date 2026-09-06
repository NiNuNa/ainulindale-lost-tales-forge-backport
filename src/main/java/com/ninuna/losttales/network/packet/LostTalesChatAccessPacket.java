package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatAccountRole;
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
 * <p>Last come two capability flags: whether this player may
 * <em>moderate</em> the chat — mute, unmute, take anyone's message back
 * — which is what the client's moderation menus are offered on, and
 * whether they may edit the server's settings, which is what the Server
 * Settings button is shown on. Presentation only, like every flag here:
 * the server decides again on each request. A payload written before
 * they travelled reads both as no.</p>
 */
public final class LostTalesChatAccessPacket implements IMessage {
    private static final int MAX_HOLDERS = 256;
    private static final int MAX_HOLDER_NAME_BYTES = 64;
    /** As many as the mute store holds; an operator is told them all. */
    public static final int MAX_MUTED_SENDERS = 1024;
    private static final int MAX_ROLE_ID_BYTES = ChatAccountRole.MAX_ID_LENGTH * 4;
    private static final int MAX_ROLE_TEXT_BYTES = ChatAccountRole.MAX_TEXT_LENGTH * 4;
    private static final int MAX_ROLE_DESCRIPTION_BYTES =
            ChatAccountRole.MAX_DESCRIPTION_LENGTH * 4;
    private static final int MAX_ROLE_BYTES = MAX_ROLE_ID_BYTES + 4 * MAX_ROLE_TEXT_BYTES
            + MAX_ROLE_DESCRIPTION_BYTES + 32;
    private static final int MAX_PACKET_BYTES = 16
            + MAX_HOLDERS * (MAX_HOLDER_NAME_BYTES + 8)
            + 2 + MAX_MUTED_SENDERS * 16
            + 1 + ChatRoleCatalog.MAX_ROLES * MAX_ROLE_BYTES + 8 + 2;
    /** Every channel open: what a payload without gates means. */
    public static final int ALL_CHANNELS = -1;

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
    private int readableChannels = ALL_CHANNELS;
    private int sendableChannels = ALL_CHANNELS;
    private boolean canModerate;
    private boolean canEditServerConfig;
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
                ChatRoleCatalog.current().roles(), ALL_CHANNELS, ALL_CHANNELS);
    }

    public LostTalesChatAccessPacket(boolean adminAccess,
                                     boolean discordAccess, int roleMask,
                                     List<RoleHolder> roleHolders,
                                     List<UUID> mutedSenders,
                                     List<ChatAccountRole> catalog,
                                     int readableChannels, int sendableChannels) {
        this(adminAccess, discordAccess, roleMask, roleHolders, mutedSenders, catalog,
                readableChannels, sendableChannels, false, false);
    }

    public LostTalesChatAccessPacket(boolean adminAccess,
                                     boolean discordAccess, int roleMask,
                                     List<RoleHolder> roleHolders,
                                     List<UUID> mutedSenders,
                                     List<ChatAccountRole> catalog,
                                     int readableChannels, int sendableChannels,
                                     boolean canModerate, boolean canEditServerConfig) {
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
        this.readableChannels = readableChannels;
        this.sendableChannels = sendableChannels;
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
            // Appended last: the channel gates for this player.
            int readable = ALL_CHANNELS;
            int sendable = ALL_CHANNELS;
            if (buffer.readableBytes() >= 8) {
                readable = buffer.readInt();
                sendable = buffer.readInt();
            }
            // Appended last of all: the two capability flags.
            boolean moderate = buffer.readableBytes() >= 1 && buffer.readBoolean();
            boolean editConfig = buffer.readableBytes() >= 1 && buffer.readBoolean();
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
            this.readableChannels = readable;
            this.sendableChannels = sendable;
            this.canModerate = moderate;
            this.canEditServerConfig = editConfig;
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.adminAccess = false;
            this.discordAccess = false;
            this.roleMask = 0;
            this.roleHolders = Collections.emptyList();
            this.mutedSenders = Collections.emptyList();
            this.catalog = Collections.emptyList();
            this.readableChannels = ALL_CHANNELS;
            this.sendableChannels = ALL_CHANNELS;
            this.canModerate = false;
            this.canEditServerConfig = false;
            LostTalesPacketCodec.discardRemaining(buffer);
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
        buffer.writeInt(this.readableChannels);
        buffer.writeInt(this.sendableChannels);
        buffer.writeBoolean(this.canModerate);
        buffer.writeBoolean(this.canEditServerConfig);
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
    /** One bit per channel ordinal: whether this player may read it. */
    public int getReadableChannels() { return this.readableChannels; }
    /** One bit per channel ordinal: whether this player may send into it. */
    public int getSendableChannels() { return this.sendableChannels; }
    /** Whether the server says this player may moderate the chat. */
    public boolean canModerate() { return this.canModerate; }
    /** Whether the server says this player may edit its settings. */
    public boolean canEditServerConfig() { return this.canEditServerConfig; }
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
