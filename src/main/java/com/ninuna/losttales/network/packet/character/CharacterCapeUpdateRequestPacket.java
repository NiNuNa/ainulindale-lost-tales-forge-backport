package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
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

/** Client request to update the persistent cape settings of one owned character. */
public final class CharacterCapeUpdateRequestPacket implements IMessage {

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private int requestId;
    private long expectedRosterRevision;
    private UUID characterId;
    private boolean showMinecraftCape;
    private String cosmeticCapeId = "";
    private boolean malformed;

    public CharacterCapeUpdateRequestPacket() {}

    public CharacterCapeUpdateRequestPacket(int requestId,
                                            long expectedRosterRevision,
                                            UUID characterId,
                                            boolean showMinecraftCape,
                                            String cosmeticCapeId) {
        if (expectedRosterRevision < 0L || characterId == null
                || cosmeticCapeId == null
                || cosmeticCapeId.length() > CharacterCapeCatalog.MAX_ID) {
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
            this.characterId = LostTalesPacketCodec.readUuid(buffer);
            this.showMinecraftCape = buffer.readBoolean();
            this.cosmeticCapeId = LostTalesPacketCodec.readUtf8String(
                    buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            if (NIL_UUID.equals(this.characterId)) {
                throw new CharacterPacketCodec.DecodeException("nil character id");
            }
            LostTalesPacketCodec.requireFinished(buffer);
            if (this.expectedRosterRevision < 0L) {
                throw new CharacterPacketCodec.DecodeException(
                        "missing roster revision");
            }
            // An unknown cape is not made "none" here: the service refuses it.
            if (this.cosmeticCapeId.length() > CharacterCapeCatalog.MAX_ID) {
                throw new CharacterPacketCodec.DecodeException("cape id too long");
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
        buffer.writeBoolean(this.showMinecraftCape);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.cosmeticCapeId, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
    }

    /** The character asked about. */
    public UUID getCharacterId() {
        return this.characterId;
    }

    public String getCosmeticCapeId() {
        return this.cosmeticCapeId;
    }

    public boolean isMalformed() {
        return this.malformed;
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
            final String cosmeticCapeId = message.cosmeticCapeId;
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
