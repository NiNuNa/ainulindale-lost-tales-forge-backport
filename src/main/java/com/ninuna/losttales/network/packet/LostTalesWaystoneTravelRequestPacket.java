package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.compat.lotr.LostTalesLotrWaystoneTravelAdapter;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Bounded source-and-destination request; the server trusts neither ID. */
public final class LostTalesWaystoneTravelRequestPacket
        implements IMessage {
    private static final int MAX_PACKET_BYTES = 4096;
    private static final int MAX_MARKER_ID_BYTES = 1024;

    private int sourceX;
    private int sourceY;
    private int sourceZ;
    private String sourceMarkerId = "";
    private String destinationMarkerId = "";
    private boolean malformed;

    public LostTalesWaystoneTravelRequestPacket() {}

    public LostTalesWaystoneTravelRequestPacket(
            int sourceX, int sourceY, int sourceZ,
            String sourceMarkerId, String destinationMarkerId) {
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.sourceZ = sourceZ;
        this.sourceMarkerId = sourceMarkerId == null
                ? "" : sourceMarkerId;
        this.destinationMarkerId = destinationMarkerId == null
                ? "" : destinationMarkerId;
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null
                    || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid waystone travel packet size");
            }
            this.sourceX = buffer.readInt();
            this.sourceY = buffer.readInt();
            this.sourceZ = buffer.readInt();
            this.sourceMarkerId =
                    LostTalesPacketCodec.readUtf8String(
                            buffer, MAX_MARKER_ID_BYTES);
            this.destinationMarkerId =
                    LostTalesPacketCodec.readUtf8String(
                            buffer, MAX_MARKER_ID_BYTES);
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        buffer.writeInt(this.sourceX);
        buffer.writeInt(this.sourceY);
        buffer.writeInt(this.sourceZ);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.sourceMarkerId, MAX_MARKER_ID_BYTES);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.destinationMarkerId,
                MAX_MARKER_ID_BYTES);
    }

    private void validate() {
        if (!LostTalesPacketCodec.isValidBlockPosition(
                this.sourceX, this.sourceY, this.sourceZ)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.sourceMarkerId, MAX_MARKER_ID_BYTES)
                || this.sourceMarkerId.length() == 0
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.destinationMarkerId,
                        MAX_MARKER_ID_BYTES)
                || this.destinationMarkerId.length() == 0) {
            throw new IllegalArgumentException(
                    "invalid waystone travel request");
        }
    }

    public int getSourceX() { return this.sourceX; }
    public int getSourceY() { return this.sourceY; }
    public int getSourceZ() { return this.sourceZ; }
    public String getSourceMarkerId() {
        return this.sourceMarkerId;
    }
    public String getDestinationMarkerId() {
        return this.destinationMarkerId;
    }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesWaystoneTravelRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(
                final LostTalesWaystoneTravelRequestPacket message,
                MessageContext context) {
            EntityPlayerMP player =
                    LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType.WAYSTONE_TRAVEL,
                    message.isMalformed(),
                    "LostTalesWaystoneTravelRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            LostTalesLotrWaystoneTravelAdapter
                                    .beginTravel(
                                            livePlayer,
                                            message.getSourceX(),
                                            message.getSourceY(),
                                            message.getSourceZ(),
                                            message.getSourceMarkerId(),
                                            message.getDestinationMarkerId());
                        }
                    });
            return null;
        }
    }
}
