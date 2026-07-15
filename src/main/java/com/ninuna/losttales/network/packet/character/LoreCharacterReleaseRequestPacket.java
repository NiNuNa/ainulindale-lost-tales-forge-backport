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

/** Server-validated release request for one owned, inactive lore character. */
public final class LoreCharacterReleaseRequestPacket implements IMessage {
    private int requestId;
    private long expectedRosterRevision;
    private long expectedOwnershipRevision;
    private String loreCharacterId = "";
    private boolean malformed;

    public LoreCharacterReleaseRequestPacket() {}
    public LoreCharacterReleaseRequestPacket(
            int requestId, long expectedRosterRevision,
            long expectedOwnershipRevision, String loreCharacterId) {
        this.requestId = requestId;
        this.expectedRosterRevision = expectedRosterRevision;
        this.expectedOwnershipRevision = expectedOwnershipRevision;
        this.loreCharacterId = loreCharacterId;
    }
    @Override public void fromBytes(ByteBuf buffer) {
        try {
            this.requestId = buffer.readInt();
            this.expectedRosterRevision = buffer.readLong();
            this.expectedOwnershipRevision = buffer.readLong();
            this.loreCharacterId = CharacterPacketCodec.readString(
                    buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            CharacterPacketCodec.requireFinished(buffer);
            if (this.expectedRosterRevision < 0L
                    || this.expectedOwnershipRevision < 0L
                    || this.loreCharacterId.length() == 0) {
                throw new CharacterPacketCodec.DecodeException("invalid release");
            }
        } catch (RuntimeException exception) { this.malformed = true; }
    }
    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.requestId);
        buffer.writeLong(this.expectedRosterRevision);
        buffer.writeLong(this.expectedOwnershipRevision);
        CharacterPacketCodec.writeString(buffer, this.loreCharacterId,
                CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
    }
    public static final class Handler implements IMessageHandler<
            LoreCharacterReleaseRequestPacket, IMessage> {
        @Override public IMessage onMessage(
                final LoreCharacterReleaseRequestPacket message,
                MessageContext context) {
            final EntityPlayerMP player =
                    CharacterServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) return null;
            CharacterServerPacketDispatcher.submit(
                    player, message.requestId,
                    CharacterOperationType.LORE_RELEASE,
                    message.malformed,
                    "LoreCharacterReleaseRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override public void run(EntityPlayerMP livePlayer) {
                            CharacterNetworkRequestHandler.handleLoreReleaseRequest(
                                    livePlayer, message.requestId,
                                    message.loreCharacterId,
                                    message.expectedOwnershipRevision,
                                    message.expectedRosterRevision);
                        }
                    });
            return null;
        }
    }
}
