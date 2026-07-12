package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.server.CharacterNetworkRequestHandler;
import com.ninuna.losttales.character.server.CharacterServerPacketDispatcher;
import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Client request for the owner's current authoritative roster. */
public final class CharacterRosterRequestPacket implements IMessage {

    private int requestId;
    private boolean malformed;

    public CharacterRosterRequestPacket() {}

    public CharacterRosterRequestPacket(int requestId) {
        this.requestId = requestId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.requestId = buffer.readInt();
            CharacterPacketCodec.requireFinished(buffer);
        } catch (RuntimeException exception) {
            this.malformed = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.requestId);
    }

    public static final class Handler implements IMessageHandler<CharacterRosterRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(final CharacterRosterRequestPacket message, MessageContext context) {
            EntityPlayerMP player = CharacterServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            final int requestId = message.requestId;
            CharacterServerPacketDispatcher.submit(
                    player,
                    requestId,
                    CharacterOperationType.REQUEST_ROSTER,
                    message.malformed,
                    "CharacterRosterRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            CharacterNetworkRequestHandler.handleRosterRequest(livePlayer, requestId);
                        }
                    }
            );
            return null;
        }
    }
}
