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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Client request to change what one owned character says about itself:
 * its description and its age. The wire only bounds the text; the server
 * normalises it and holds both to the bounds creation uses.
 */
public final class CharacterProfileUpdateRequestPacket implements IMessage {

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private int requestId;
    private long expectedRosterRevision;
    private UUID characterId;
    private String description = "";
    private int age;
    private boolean malformed;

    public CharacterProfileUpdateRequestPacket() {}

    public CharacterProfileUpdateRequestPacket(int requestId,
                                               long expectedRosterRevision,
                                               UUID characterId,
                                               String description,
                                               int age) {
        String text = description == null ? "" : description;
        if (expectedRosterRevision < 0L || characterId == null
                || NIL_UUID.equals(characterId)
                || text.getBytes(StandardCharsets.UTF_8).length
                > CharacterPacketCodec.MAX_DESCRIPTION_BYTES) {
            throw new IllegalArgumentException("invalid profile update request");
        }
        this.requestId = requestId;
        this.expectedRosterRevision = expectedRosterRevision;
        this.characterId = characterId;
        this.description = text;
        this.age = age;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.requestId = buffer.readInt();
            this.expectedRosterRevision = buffer.readLong();
            this.characterId = CharacterPacketCodec.readUuid(buffer);
            this.description = CharacterPacketCodec.readString(buffer,
                    CharacterPacketCodec.MAX_DESCRIPTION_BYTES);
            this.age = buffer.readInt();
            CharacterPacketCodec.requireFinished(buffer);
            if (this.expectedRosterRevision < 0L) {
                throw new CharacterPacketCodec.DecodeException(
                        "missing roster revision");
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
        CharacterPacketCodec.writeUuid(buffer, this.characterId);
        CharacterPacketCodec.writeString(buffer, this.description,
                CharacterPacketCodec.MAX_DESCRIPTION_BYTES);
        buffer.writeInt(this.age);
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public String getDescription() {
        return this.description;
    }

    public int getAge() {
        return this.age;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler
            implements IMessageHandler<CharacterProfileUpdateRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(final CharacterProfileUpdateRequestPacket message,
                                  MessageContext context) {
            final EntityPlayerMP player = CharacterServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            final int requestId = message.requestId;
            final long expectedRosterRevision = message.expectedRosterRevision;
            final UUID characterId = message.characterId;
            final String description = message.description;
            final int age = message.age;
            CharacterServerPacketDispatcher.submit(
                    player,
                    requestId,
                    CharacterOperationType.PROFILE_UPDATE,
                    message.malformed,
                    "CharacterProfileUpdateRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            CharacterNetworkRequestHandler.handleProfileUpdateRequest(
                                    livePlayer,
                                    requestId,
                                    expectedRosterRevision,
                                    characterId,
                                    description,
                                    age);
                        }
                    }
            );
            return null;
        }
    }
}
