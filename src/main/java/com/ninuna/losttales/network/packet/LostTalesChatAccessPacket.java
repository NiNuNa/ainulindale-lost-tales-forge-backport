package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatAccountRole;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server-to-client: which restricted chat channels the player may use,
 * and which roles the player holds. The client cannot know its own
 * operator status on a dedicated server, nor whether the server bridges
 * Discord, so the server states both on login and whenever a refused
 * message makes it worth saying again; the server still checks on every
 * send regardless. The role mask is presentation only — it is what lets
 * this client notice that {@code @Operator} was addressed to it — and,
 * like every other role fact, it is the server's word alone.
 */
public final class LostTalesChatAccessPacket implements IMessage {
    private static final int MAX_PACKET_BYTES = 16;

    private boolean adminAccess;
    private boolean discordAccess;
    private int roleMask;
    private boolean malformed;

    public LostTalesChatAccessPacket() {}

    public LostTalesChatAccessPacket(boolean adminAccess,
                                     boolean discordAccess) {
        this(adminAccess, discordAccess, 0);
    }

    public LostTalesChatAccessPacket(boolean adminAccess,
                                     boolean discordAccess, int roleMask) {
        this.adminAccess = adminAccess;
        this.discordAccess = discordAccess;
        this.roleMask = roleMask;
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
            LostTalesPacketCodec.requireFinished(buffer);
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.adminAccess = false;
            this.discordAccess = false;
            this.roleMask = 0;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(this.adminAccess);
        buffer.writeBoolean(this.discordAccess);
        buffer.writeInt(this.roleMask);
    }

    public boolean hasAdminAccess() { return this.adminAccess; }
    /** The roles the server says this player holds, as a bit set. */
    public int getRoleMask() { return this.roleMask; }
    /** Whether the server bridges the Discord channel right now. */
    public boolean hasDiscordAccess() { return this.discordAccess; }
    public boolean isMalformed() { return this.malformed; }

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
