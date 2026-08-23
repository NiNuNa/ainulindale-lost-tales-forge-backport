package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server-to-client: which restricted chat channels the player may use.
 * The client cannot know its own operator status on a dedicated server,
 * nor whether the server bridges Discord, so the server states both on
 * login and whenever a refused message makes it worth saying again; the
 * server still checks on every send regardless.
 */
public final class LostTalesChatAccessPacket implements IMessage {
    private static final int MAX_PACKET_BYTES = 16;

    private boolean adminAccess;
    private boolean discordAccess;
    private boolean malformed;

    public LostTalesChatAccessPacket() {}

    public LostTalesChatAccessPacket(boolean adminAccess,
                                     boolean discordAccess) {
        this.adminAccess = adminAccess;
        this.discordAccess = discordAccess;
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
            LostTalesPacketCodec.requireFinished(buffer);
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.adminAccess = false;
            this.discordAccess = false;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(this.adminAccess);
        buffer.writeBoolean(this.discordAccess);
    }

    public boolean hasAdminAccess() { return this.adminAccess; }
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
