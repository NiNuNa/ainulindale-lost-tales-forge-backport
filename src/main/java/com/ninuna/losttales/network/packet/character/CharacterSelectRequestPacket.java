package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.server.CharacterNetworkRequestHandler;
import com.ninuna.losttales.character.server.CharacterServerPacketDispatcher;
import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

/**
 * Client request to atomically play as one owned character, the account
 * character included. The server takes the owner from the live player and
 * never from the wire.
 */
public final class CharacterSelectRequestPacket implements IMessage {

    private int requestId;
    private long expectedRosterRevision;
    private UUID characterId;
    private boolean malformed;

    public CharacterSelectRequestPacket() {}

    public CharacterSelectRequestPacket(int requestId, long expectedRosterRevision,
                                        UUID characterId) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId must not be null");
        }
        this.requestId = requestId;
        this.expectedRosterRevision = expectedRosterRevision;
        this.characterId = characterId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.requestId = buffer.readInt();
            this.expectedRosterRevision = buffer.readLong();
            this.characterId = LostTalesPacketCodec.readUuid(buffer);
            LostTalesPacketCodec.requireFinished(buffer);
            if (this.expectedRosterRevision < 0L) {
                throw new CharacterPacketCodec.DecodeException("missing roster revision");
            }
        } catch (RuntimeException exception) {
            this.malformed = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.requestId);
        buffer.writeLong(this.expectedRosterRevision);
        LostTalesPacketCodec.writeUuid(buffer, this.characterId);
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler implements IMessageHandler<CharacterSelectRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(final CharacterSelectRequestPacket message, MessageContext context) {
            final EntityPlayerMP player = CharacterServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            final int requestId = message.requestId;
            final long expectedRosterRevision = message.expectedRosterRevision;
            final UUID characterId = message.characterId;
            CharacterServerPacketDispatcher.submit(
                    player,
                    requestId,
                    CharacterOperationType.SELECT,
                    message.malformed,
                    "CharacterSelectRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            CharacterNetworkRequestHandler.handleSelectRequest(
                                    livePlayer, requestId,
                                    expectedRosterRevision, characterId);
                        }
                    }
            );
            return null;
        }
    }
}
