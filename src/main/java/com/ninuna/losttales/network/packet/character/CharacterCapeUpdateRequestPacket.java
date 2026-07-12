package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
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

/** Client request to update one owned character's persistent cape settings. */
public final class CharacterCapeUpdateRequestPacket implements IMessage {

    private int requestId;
    private long expectedRosterRevision;
    private UUID characterId;
    private boolean showMinecraftCape;
    private int cosmeticCapeId;
    private boolean malformed;

    public CharacterCapeUpdateRequestPacket() {}

    public CharacterCapeUpdateRequestPacket(int requestId,
                                            long expectedRosterRevision,
                                            UUID characterId,
                                            boolean showMinecraftCape,
                                            int cosmeticCapeId) {
        if (expectedRosterRevision < 0L || characterId == null
                || !CharacterCapeCatalog.isValidSelection(cosmeticCapeId)) {
            throw new IllegalArgumentException("invalid cape update request");
        }
        this.requestId = requestId;
        this.expectedRosterRevision = expectedRosterRevision;
        this.characterId = characterId;
        this.showMinecraftCape = showMinecraftCape;
        this.cosmeticCapeId = cosmeticCapeId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.requestId = buffer.readInt();
            this.expectedRosterRevision = buffer.readLong();
            this.characterId = CharacterPacketCodec.readUuid(buffer);
            this.showMinecraftCape = buffer.readBoolean();
            this.cosmeticCapeId = buffer.readUnsignedShort();
            CharacterPacketCodec.requireFinished(buffer);
            if (this.expectedRosterRevision < 0L) {
                throw new CharacterPacketCodec.DecodeException(
                        "missing roster revision");
            }
            // Do not normalize an unknown client value. The authoritative
            // service must reject it rather than silently accepting "none".
            if (this.cosmeticCapeId > CharacterCapeCatalog.MAX_NETWORK_ID) {
                throw new CharacterPacketCodec.DecodeException(
                        "cape ID is outside the network range");
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
        buffer.writeBoolean(this.showMinecraftCape);
        buffer.writeShort(this.cosmeticCapeId);
    }

    public static final class Handler
            implements IMessageHandler<CharacterCapeUpdateRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(final CharacterCapeUpdateRequestPacket message,
                                  MessageContext context) {
            final EntityPlayerMP player = CharacterServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            final int requestId = message.requestId;
            final long expectedRosterRevision = message.expectedRosterRevision;
            final UUID characterId = message.characterId;
            final boolean showMinecraftCape = message.showMinecraftCape;
            final int cosmeticCapeId = message.cosmeticCapeId;
            CharacterServerPacketDispatcher.submit(
                    player,
                    requestId,
                    CharacterOperationType.CAPE_UPDATE,
                    message.malformed,
                    "CharacterCapeUpdateRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            CharacterNetworkRequestHandler.handleCapeUpdateRequest(
                                    livePlayer,
                                    requestId,
                                    expectedRosterRevision,
                                    characterId,
                                    showMinecraftCape,
                                    cosmeticCapeId);
                        }
                    }
            );
            return null;
        }
    }
}
