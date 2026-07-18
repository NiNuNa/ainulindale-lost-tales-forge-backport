package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Server-to-client one-shot notification for a map marker discovered by walking into its radius. */
public class LostTalesMapMarkerDiscoveryPacket implements IMessage {
    static final int MAX_MARKER_ID_BYTES = 128;
    static final int MAX_MARKER_NAME_BYTES = 512;
    private static final int MAX_PACKET_BYTES =
            MAX_MARKER_ID_BYTES + MAX_MARKER_NAME_BYTES + 8;

    private String markerId = "";
    private String markerName = "";
    private boolean malformed;

    public LostTalesMapMarkerDiscoveryPacket() {}

    public LostTalesMapMarkerDiscoveryPacket(String markerId, String markerName) {
        this.markerId = markerId == null ? "" : markerId;
        this.markerName = markerName == null ? "" : markerName;
        if (!isValid()) {
            throw new IllegalArgumentException("invalid map marker discovery");
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.malformed = false;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid map marker discovery packet size");
            }
            this.markerId = LostTalesPacketCodec.readUtf8String(
                    buf, MAX_MARKER_ID_BYTES);
            this.markerName = LostTalesPacketCodec.readUtf8String(
                    buf, MAX_MARKER_NAME_BYTES);
            LostTalesPacketCodec.requireFinished(buf);
            if (!isValid()) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid map marker discovery state");
            }
        } catch (RuntimeException exception) {
            this.markerId = "";
            this.markerName = "";
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (!isValid()) {
            throw new IllegalStateException("invalid map marker discovery");
        }
        LostTalesPacketCodec.writeUtf8String(
                buf, this.markerId, MAX_MARKER_ID_BYTES);
        LostTalesPacketCodec.writeUtf8String(
                buf, this.markerName, MAX_MARKER_NAME_BYTES);
    }

    public String getMarkerId() {
        return markerId == null ? "" : markerId;
    }

    public String getMarkerName() {
        return markerName == null ? "" : markerName;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    private boolean isValid() {
        return this.markerId != null && this.markerId.length() > 0
                && LostTalesPacketCodec.isUtf8WithinLimit(
                this.markerId, MAX_MARKER_ID_BYTES)
                && LostTalesPacketCodec.isUtf8WithinLimit(
                this.markerName, MAX_MARKER_NAME_BYTES);
    }

    /** Common-safe clientbound handler; real client work is delegated to the sided proxy. */
    public static class Handler implements IMessageHandler<LostTalesMapMarkerDiscoveryPacket, IMessage> {
        @Override
        public IMessage onMessage(
                final LostTalesMapMarkerDiscoveryPacket message,
                MessageContext ctx) {
            if (message != null && !message.isMalformed()
                    && LostTalesMod.proxy != null) {
                LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                    @Override
                    public void run() {
                        if (LostTalesMod.proxy != null) {
                            LostTalesMod.proxy.handleMapMarkerDiscovery(message);
                        }
                    }
                });
            }
            return null;
        }
    }
}
