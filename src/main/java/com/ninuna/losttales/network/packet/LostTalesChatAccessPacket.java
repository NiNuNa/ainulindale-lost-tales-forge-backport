package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatAccountRole;
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
 * operator status on a dedicated server, nor whether the server bridges
 * Discord, so the server states both on login and whenever a refused
 * message makes it worth saying again; the server still checks on every
 * send regardless. The role mask is presentation only — it is what lets
 * this client notice that {@code @Operator} was addressed to it — and,
 * like every other role fact, it is the server's word alone.
 *
 * <p>Appended after the personal fields travels the <em>role roster</em>:
 * every online account that holds a role, with its mask. Account names
 * and role marks are public on the tab list and on every line those
 * players send, so nothing here widens what a client can learn; it only
 * lets the role hover card name a role's members, and a mention of a
 * role holder wear their colour before they have spoken.</p>
 */
public final class LostTalesChatAccessPacket implements IMessage {
    private static final int MAX_HOLDERS = 256;
    private static final int MAX_HOLDER_NAME_BYTES = 64;
    /** As many as the mute store holds; an operator is told them all. */
    public static final int MAX_MUTED_SENDERS = 1024;
    private static final int MAX_PACKET_BYTES = 16
            + MAX_HOLDERS * (MAX_HOLDER_NAME_BYTES + 8)
            + 2 + MAX_MUTED_SENDERS * 16;

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
        this.adminAccess = adminAccess;
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
            this.roleMask = buffer.readableBytes() >= 4
                    ? buffer.readInt() : 0;
            if (!ChatAccountRole.isValidMask(this.roleMask)) {
                this.roleMask = 0;
            }
            // Appended again: the online role holders. A packet written
            // before the roster existed ends here and names none.
            if (buffer.readableBytes() >= 2) {
                int count = buffer.readUnsignedShort();
                if (count > MAX_HOLDERS) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "too many role holders");
                }
                List<RoleHolder> decoded = new ArrayList<RoleHolder>(count);
                for (int index = 0; index < count; index++) {
                    String name = LostTalesPacketCodec.readUtf8String(
                            buffer, MAX_HOLDER_NAME_BYTES);
                    int mask = buffer.readInt();
                    if (name.trim().length() == 0
                            || !ChatAccountRole.isValidMask(mask)
                            || mask == 0) {
                        throw new LostTalesPacketCodec.DecodeException(
                                "invalid role holder");
                    }
                    decoded.add(new RoleHolder(name.trim(), mask));
                }
                this.roleHolders = Collections.unmodifiableList(decoded);
            } else {
                this.roleHolders = Collections.emptyList();
            }
            // Appended once more: the muted senders. A packet written
            // before they travelled ends here and names none.
            if (buffer.readableBytes() >= 2) {
                int count = buffer.readUnsignedShort();
                if (count > MAX_MUTED_SENDERS) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "too many muted senders");
                }
                List<UUID> muted = new ArrayList<UUID>(count);
                for (int index = 0; index < count; index++) {
                    muted.add(new UUID(buffer.readLong(), buffer.readLong()));
                }
                this.mutedSenders = Collections.unmodifiableList(muted);
            } else {
                this.mutedSenders = Collections.emptyList();
            }
            LostTalesPacketCodec.requireFinished(buffer);
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.adminAccess = false;
            this.discordAccess = false;
            this.roleMask = 0;
            this.roleHolders = Collections.emptyList();
            this.mutedSenders = Collections.emptyList();
            LostTalesPacketCodec.discardRemaining(buffer);
        }
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
    }

    public boolean hasAdminAccess() { return this.adminAccess; }
    /** The roles the server says this player holds, as a bit set. */
    public int getRoleMask() { return this.roleMask; }
    /** Whether the server bridges the Discord channel right now. */
    public boolean hasDiscordAccess() { return this.discordAccess; }
    /** Every online account holding a role, as the server states it. */
    public List<RoleHolder> getRoleHolders() { return this.roleHolders; }

    /** The muted sender ids; empty for anyone but an operator. */
    public List<UUID> getMutedSenders() { return this.mutedSenders; }
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
