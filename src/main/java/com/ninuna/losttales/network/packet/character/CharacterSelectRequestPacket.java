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

import java.util.UUID;

/**
 * Client request to atomically play as one owned character, or as the
 * account itself. The account form carries the owner's UUID in the
 * character slot as a placeholder and says so with the trailing flag; the
 * server builds the target from the live player and never from that slot.
 * The flag is the last field, so a request without it still decodes as a
 * character selection.
 */
public final class CharacterSelectRequestPacket implements IMessage {

    private int requestId;
    private long expectedRosterRevision;
    private UUID characterId;
    private boolean selectAccount;
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

    /** A request to play as the account; {@code ownerId} only fills the character slot. */
    public static CharacterSelectRequestPacket forAccount(int requestId,
                                                          long expectedRosterRevision,
                                                          UUID ownerId) {
        CharacterSelectRequestPacket packet = new CharacterSelectRequestPacket(
                requestId, expectedRosterRevision, ownerId);
        packet.selectAccount = true;
        return packet;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.requestId = buffer.readInt();
            this.expectedRosterRevision = buffer.readLong();
            this.characterId = CharacterPacketCodec.readUuid(buffer);
            this.selectAccount = buffer.isReadable() && buffer.readBoolean();
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
        buffer.writeBoolean(this.selectAccount);
    }

    public boolean isSelectAccount() {
        return this.selectAccount;
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
            final boolean selectAccount = message.selectAccount;
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
                                    expectedRosterRevision, characterId,
                                    selectAccount);
                        }
                    }
            );
            return null;
        }
    }
}
