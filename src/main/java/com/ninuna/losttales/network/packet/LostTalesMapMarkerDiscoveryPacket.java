package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Server-to-client one-shot notification for a map marker discovered by walking into its radius. */
public class LostTalesMapMarkerDiscoveryPacket implements IMessage {
    private String markerId = "";
    private String markerName = "";

    public LostTalesMapMarkerDiscoveryPacket() {}

    public LostTalesMapMarkerDiscoveryPacket(String markerId, String markerName) {
        this.markerId = markerId == null ? "" : markerId;
        this.markerName = markerName == null ? "" : markerName;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.markerId = ByteBufUtils.readUTF8String(buf);
        this.markerName = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.markerId == null ? "" : this.markerId);
        ByteBufUtils.writeUTF8String(buf, this.markerName == null ? "" : this.markerName);
    }

    public String getMarkerId() {
        return markerId == null ? "" : markerId;
    }

    public String getMarkerName() {
        return markerName == null ? "" : markerName;
    }

    /** Common-safe clientbound handler; real client work is delegated to the sided proxy. */
    public static class Handler implements IMessageHandler<LostTalesMapMarkerDiscoveryPacket, IMessage> {
        @Override
        public IMessage onMessage(LostTalesMapMarkerDiscoveryPacket message, MessageContext ctx) {
            if (LostTalesMod.proxy != null) {
                LostTalesMod.proxy.handleMapMarkerDiscovery(message);
            }
            return null;
        }
    }
}
