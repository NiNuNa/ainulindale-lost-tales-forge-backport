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

/** Narrow claim request; all lore metadata and state remain server-owned. */
public final class LoreCharacterClaimRequestPacket implements IMessage {

    private int requestId;
    private long expectedRosterRevision;
    private long expectedOwnershipRevision;
    private int slotIndex;
    private String loreCharacterId = "";
    private boolean malformed;

    public LoreCharacterClaimRequestPacket() {}

    public LoreCharacterClaimRequestPacket(
            int requestId, long expectedRosterRevision,
            long expectedOwnershipRevision, int slotIndex,
            String loreCharacterId) {
        this.requestId = requestId;
        this.expectedRosterRevision = expectedRosterRevision;
        this.expectedOwnershipRevision = expectedOwnershipRevision;
        this.slotIndex = slotIndex;
        this.loreCharacterId = loreCharacterId;
    }

    @Override public void fromBytes(ByteBuf buffer) {
        try {
            this.requestId = buffer.readInt();
            this.expectedRosterRevision = buffer.readLong();
            this.expectedOwnershipRevision = buffer.readLong();
            this.slotIndex = buffer.readByte();
            this.loreCharacterId = CharacterPacketCodec.readString(
                    buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            CharacterPacketCodec.requireFinished(buffer);
            if (this.expectedRosterRevision < 0L
                    || this.expectedOwnershipRevision < 0L
                    || this.loreCharacterId.length() == 0) {
                throw new CharacterPacketCodec.DecodeException("invalid claim");
            }
        } catch (RuntimeException exception) { this.malformed = true; }
    }

    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.requestId);
        buffer.writeLong(this.expectedRosterRevision);
        buffer.writeLong(this.expectedOwnershipRevision);
        buffer.writeByte(this.slotIndex);
        CharacterPacketCodec.writeString(buffer, this.loreCharacterId,
                CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
    }

    public static final class Handler implements IMessageHandler<
            LoreCharacterClaimRequestPacket, IMessage> {
        @Override public IMessage onMessage(
                final LoreCharacterClaimRequestPacket message,
                MessageContext context) {
            final EntityPlayerMP player =
                    CharacterServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) return null;
            CharacterServerPacketDispatcher.submit(
                    player, message.requestId,
                    CharacterOperationType.LORE_CLAIM,
                    message.malformed,
                    "LoreCharacterClaimRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override public void run(EntityPlayerMP livePlayer) {
                            CharacterNetworkRequestHandler.handleLoreClaimRequest(
                                    livePlayer, message.requestId,
                                    message.loreCharacterId,
                                    message.expectedOwnershipRevision,
                                    message.expectedRosterRevision,
                                    message.slotIndex);
                        }
                    });
            return null;
        }
    }
}
