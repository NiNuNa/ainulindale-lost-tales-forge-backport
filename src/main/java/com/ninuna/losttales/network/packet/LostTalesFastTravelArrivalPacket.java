package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.share.ChatShareReference;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server-to-client: the player has just fast-travelled to the marker
 * named by {@code markerId}. Sent from LOTR's own completion path, so it
 * fires only for travel that actually happened — never for a request that
 * was refused or cancelled. Feeds the client's "recently travelled to"
 * list; nothing gameplay-relevant depends on it.
 */
public final class LostTalesFastTravelArrivalPacket implements IMessage {
    public static final int MAX_MARKER_ID_BYTES =
            ChatShareReference.MAX_MARKER_ID_BYTES;
    private static final int MAX_PACKET_BYTES = MAX_MARKER_ID_BYTES + 16;

    private String markerId = "";
    private boolean malformed;

    public LostTalesFastTravelArrivalPacket() {}

    public LostTalesFastTravelArrivalPacket(String markerId) {
        this.markerId = markerId == null ? "" : markerId.trim();
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid fast travel arrival packet size");
            }
            this.markerId = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_MARKER_ID_BYTES);
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.markerId = "";
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.markerId, MAX_MARKER_ID_BYTES);
    }

    private void validate() {
        if (this.markerId.length() == 0
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.markerId, MAX_MARKER_ID_BYTES)) {
            throw new IllegalArgumentException(
                    "invalid fast travel arrival");
        }
    }

    public String getMarkerId() { return this.markerId; }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesFastTravelArrivalPacket, IMessage> {
        @Override
        public IMessage onMessage(
                final LostTalesFastTravelArrivalPacket message,
                MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleFastTravelArrival(message);
                }
            });
            return null;
        }
    }
}
