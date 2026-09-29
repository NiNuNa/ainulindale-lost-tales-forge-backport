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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Client request to change one owned character's look: its skin, arm
 * width and chest, as Save in Change Look sends them. The wire only bounds
 * the ids; the server holds them to the creator's checks for the
 * character's own race and sex.
 */
public final class CharacterLookUpdateRequestPacket implements IMessage {

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private int requestId;
    private long expectedRosterRevision;
    private UUID characterId;
    private String skinId = "";
    private String bodyTypeId = "";
    private String chestTypeId = "";
    private boolean malformed;

    public CharacterLookUpdateRequestPacket() {}

    public CharacterLookUpdateRequestPacket(int requestId,
                                            long expectedRosterRevision,
                                            UUID characterId, String skinId,
                                            String bodyTypeId,
                                            String chestTypeId) {
        if (expectedRosterRevision < 0L || characterId == null
                || NIL_UUID.equals(characterId)
                || !fits(skinId) || !fits(bodyTypeId) || !fits(chestTypeId)) {
            throw new IllegalArgumentException("invalid look update request");
        }
        this.requestId = requestId;
        this.expectedRosterRevision = expectedRosterRevision;
        this.characterId = characterId;
        this.skinId = skinId;
        this.bodyTypeId = bodyTypeId;
        this.chestTypeId = chestTypeId;
    }

    private static boolean fits(String id) {
        return id != null && id.getBytes(StandardCharsets.UTF_8).length
                <= CharacterPacketCodec.MAX_IDENTIFIER_BYTES;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            this.requestId = buffer.readInt();
            this.expectedRosterRevision = buffer.readLong();
            this.characterId = LostTalesPacketCodec.readUuid(buffer);
            this.skinId = LostTalesPacketCodec.readUtf8String(buffer,
                    CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            this.bodyTypeId = LostTalesPacketCodec.readUtf8String(buffer,
                    CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            this.chestTypeId = LostTalesPacketCodec.readUtf8String(buffer,
                    CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            LostTalesPacketCodec.requireFinished(buffer);
            if (this.expectedRosterRevision < 0L) {
                throw new CharacterPacketCodec.DecodeException(
                        "missing roster revision");
            }
            if (NIL_UUID.equals(this.characterId)) {
                throw new CharacterPacketCodec.DecodeException("nil character id");
            }
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.skinId = "";
            this.bodyTypeId = "";
            this.chestTypeId = "";
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.requestId);
        buffer.writeLong(this.expectedRosterRevision);
        LostTalesPacketCodec.writeUuid(buffer, this.characterId);
        LostTalesPacketCodec.writeUtf8String(buffer, this.skinId,
                CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, this.bodyTypeId,
                CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, this.chestTypeId,
                CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public String getSkinId() {
        return this.skinId;
    }

    public String getBodyTypeId() {
        return this.bodyTypeId;
    }

    public String getChestTypeId() {
        return this.chestTypeId;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler
            implements IMessageHandler<CharacterLookUpdateRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(final CharacterLookUpdateRequestPacket message,
                                  MessageContext context) {
            final EntityPlayerMP player = CharacterServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            final int requestId = message.requestId;
            final long expectedRosterRevision = message.expectedRosterRevision;
            final UUID characterId = message.characterId;
            final String skinId = message.skinId;
            final String bodyTypeId = message.bodyTypeId;
            final String chestTypeId = message.chestTypeId;
            CharacterServerPacketDispatcher.submit(
                    player,
                    requestId,
                    CharacterOperationType.LOOK_UPDATE,
                    message.malformed,
                    "CharacterLookUpdateRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            CharacterNetworkRequestHandler.handleLookUpdateRequest(
                                    livePlayer, requestId,
                                    expectedRosterRevision, characterId,
                                    skinId, bodyTypeId, chestTypeId);
                        }
                    }
            );
            return null;
        }
    }
}
