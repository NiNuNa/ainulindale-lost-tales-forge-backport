package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.config.server.LostTalesServerConfigService;
import com.ninuna.losttales.config.server.ServerConfigApplyResult;
import com.ninuna.losttales.config.server.ServerConfigChange;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The changes an operator's screen or command wants written. The server
 * trusts none of it: the asker is checked to be an operator on the server
 * thread, and every change is validated against the file before it is
 * set; the result comes back as its own packet.
 */
public final class LostTalesServerConfigApplyPacket implements IMessage {

    private List<ServerConfigChange> changes = Collections.emptyList();
    private boolean malformed;

    public LostTalesServerConfigApplyPacket() {}

    public LostTalesServerConfigApplyPacket(List<ServerConfigChange> changes) {
        if (changes == null || changes.isEmpty()
                || changes.size() > ServerConfigPacketCodec.MAX_ENTRIES) {
            throw new IllegalArgumentException("invalid config change count");
        }
        this.changes = Collections.unmodifiableList(new ArrayList<ServerConfigChange>(changes));
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            int count = LostTalesPacketCodec.readCount(buffer,
                    ServerConfigPacketCodec.MAX_ENTRIES, "config change");
            if (count == 0) {
                throw new LostTalesPacketCodec.DecodeException("no config changes");
            }
            List<ServerConfigChange> decoded = new ArrayList<ServerConfigChange>(count);
            for (int index = 0; index < count; index++) {
                decoded.add(ServerConfigPacketCodec.readChange(buffer));
            }
            LostTalesPacketCodec.requireFinished(buffer);
            this.changes = Collections.unmodifiableList(decoded);
        } catch (RuntimeException exception) {
            this.changes = Collections.emptyList();
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        LostTalesPacketCodec.writeCount(buffer, this.changes.size(),
                ServerConfigPacketCodec.MAX_ENTRIES, "config change");
        for (ServerConfigChange change : this.changes) {
            ServerConfigPacketCodec.writeChange(buffer, change);
        }
    }

    public List<ServerConfigChange> getChanges() {
        return this.changes;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler implements IMessageHandler<
            LostTalesServerConfigApplyPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesServerConfigApplyPacket message,
                                  MessageContext context) {
            EntityPlayerMP player = LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType.SERVER_CONFIG,
                    message.isMalformed(),
                    "LostTalesServerConfigApplyPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            if (!LostTalesServerConfigService.isOperator(livePlayer)) {
                                return;
                            }
                            ServerConfigApplyResult result =
                                    LostTalesServerConfigService.apply(message.getChanges());
                            LostTalesNetworkHandler.CHANNEL.sendTo(
                                    new LostTalesServerConfigResultPacket(result), livePlayer);
                        }
                    });
            return null;
        }
    }
}
