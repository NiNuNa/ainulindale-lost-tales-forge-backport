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
 * Client request to bring back one of the player's own deleted characters
 * after the player said yes. The server decides whether it is theirs,
 * still within its retention, and which open slot it goes into.
 */
public final class CharacterRestoreRequestPacket implements IMessage {

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private int requestId;
    private long expectedRosterRevision;
    private UUID characterId;
    private boolean malformed;

    public CharacterRestoreRequestPacket() {}

    public CharacterRestoreRequestPacket(int requestId, long expectedRosterRevision,
                                         UUID characterId) {
        if (expectedRosterRevision < 0L || characterId == null
                || NIL_UUID.equals(characterId)) {
            throw new IllegalArgumentException("invalid restore request");
        }
        this.requestId = requestId;
        this.expectedRosterRevision = expectedRosterRevision;
        this.characterId = characterId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            this.requestId = buffer.readInt();
            this.expectedRosterRevision = buffer.readLong();
            this.characterId = LostTalesPacketCodec.readUuid(buffer);
            LostTalesPacketCodec.requireFinished(buffer);
            if (this.expectedRosterRevision < 0L) {
                throw new CharacterPacketCodec.DecodeException("missing roster revision");
            }
            if (NIL_UUID.equals(this.characterId)) {
                throw new CharacterPacketCodec.DecodeException("nil character id");
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

    public static final class Handler
            implements IMessageHandler<CharacterRestoreRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(final CharacterRestoreRequestPacket message,
                                  MessageContext context) {
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
                    CharacterOperationType.RESTORE,
                    message.malformed,
                    "CharacterRestoreRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            CharacterNetworkRequestHandler.handleRestoreRequest(
                                    livePlayer, requestId,
                                    expectedRosterRevision, characterId);
                        }
                    }
            );
            return null;
        }
    }
}
