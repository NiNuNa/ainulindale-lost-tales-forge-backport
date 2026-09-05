package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.config.server.ServerConfigEntry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The server's config as an operator's client may see it: every
 * server-side key with its type, bounds and comment, secrets blanked.
 * Sent only to a player the server has checked is an operator.
 */
public final class LostTalesServerConfigSyncPacket implements IMessage {

    private List<ServerConfigEntry> entries = Collections.emptyList();
    private boolean malformed;

    public LostTalesServerConfigSyncPacket() {}

    public LostTalesServerConfigSyncPacket(List<ServerConfigEntry> entries) {
        if (entries == null || entries.size() > ServerConfigPacketCodec.MAX_ENTRIES) {
            throw new IllegalArgumentException("invalid config entry count");
        }
        this.entries = Collections.unmodifiableList(new ArrayList<ServerConfigEntry>(entries));
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            int count = LostTalesPacketCodec.readCount(buffer,
                    ServerConfigPacketCodec.MAX_ENTRIES, "config entry");
            List<ServerConfigEntry> decoded = new ArrayList<ServerConfigEntry>(count);
            for (int index = 0; index < count; index++) {
                decoded.add(ServerConfigPacketCodec.readEntry(buffer));
            }
            LostTalesPacketCodec.requireFinished(buffer);
            this.entries = Collections.unmodifiableList(decoded);
        } catch (RuntimeException exception) {
            this.entries = Collections.emptyList();
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        LostTalesPacketCodec.writeCount(buffer, this.entries.size(),
                ServerConfigPacketCodec.MAX_ENTRIES, "config entry");
        for (ServerConfigEntry entry : this.entries) {
            ServerConfigPacketCodec.writeEntry(buffer, entry);
        }
    }

    public List<ServerConfigEntry> getEntries() {
        return this.entries;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler implements IMessageHandler<
            LostTalesServerConfigSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesServerConfigSyncPacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleServerConfigSync(message);
                }
            });
            return null;
        }
    }
}
