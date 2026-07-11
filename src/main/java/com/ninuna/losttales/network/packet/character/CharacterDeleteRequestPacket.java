package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.server.CharacterNetworkRequestHandler;
import com.ninuna.losttales.character.server.CharacterServerPacketDispatcher;
import com.ninuna.losttales.character.sync.CharacterOperationType;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

/** Client request to delete one owned character after client confirmation. */
public final class CharacterDeleteRequestPacket implements IMessage {

    private int requestId;
    private long expectedRosterRevision;
    private UUID characterId;
    private boolean malformed;

    public CharacterDeleteRequestPacket() {}

    public CharacterDeleteRequestPacket(int requestId, long expectedRosterRevision,
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
            this.characterId = CharacterPacketCodec.readUuid(buffer);
            CharacterPacketCodec.requireFinished(buffer);
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
        CharacterPacketCodec.writeUuid(buffer, this.characterId);
    }

    public static final class Handler implements IMessageHandler<CharacterDeleteRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(final CharacterDeleteRequestPacket message, MessageContext context) {
            final EntityPlayerMP player = CharacterServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            CharacterServerPacketDispatcher.submit(
                    player,
                    message.requestId,
                    CharacterOperationType.DELETE,
                    message.malformed,
                    "CharacterDeleteRequestPacket",
                    new Runnable() {
                        @Override
                        public void run() {
                            CharacterNetworkRequestHandler.handleDeleteRequest(
                                    player, message.requestId,
                                    message.expectedRosterRevision, message.characterId);
                        }
                    }
            );
            return null;
        }
    }
}
