package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.model.CharacterProfile;
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
 * Client request to change what one owned character says about itself:
 * its profile — the About texts, the facts and the glances — and its age.
 * The wire only bounds the words; the server normalises them and holds
 * them to the profile's bounds and the profanity list.
 */
public final class CharacterProfileUpdateRequestPacket implements IMessage {

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private int requestId;
    private long expectedRosterRevision;
    private UUID characterId;
    private CharacterProfile profile = CharacterProfile.EMPTY;
    private int age;
    private boolean malformed;

    public CharacterProfileUpdateRequestPacket() {}

    public CharacterProfileUpdateRequestPacket(int requestId,
                                               long expectedRosterRevision,
                                               UUID characterId,
                                               CharacterProfile profile,
                                               int age) {
        if (expectedRosterRevision < 0L || characterId == null
                || NIL_UUID.equals(characterId)
                || !CharacterPacketCodec.fits(profile)) {
            throw new IllegalArgumentException("invalid profile update request");
        }
        this.requestId = requestId;
        this.expectedRosterRevision = expectedRosterRevision;
        this.characterId = characterId;
        this.profile = profile;
        this.age = age;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.requestId = buffer.readInt();
            this.expectedRosterRevision = buffer.readLong();
            this.characterId = CharacterPacketCodec.readUuid(buffer);
            this.profile = CharacterPacketCodec.readProfile(buffer);
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
            this.profile = CharacterProfile.EMPTY;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.requestId);
        buffer.writeLong(this.expectedRosterRevision);
        CharacterPacketCodec.writeUuid(buffer, this.characterId);
        CharacterPacketCodec.writeProfile(buffer, this.profile);
        buffer.writeInt(this.age);
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public CharacterProfile getProfile() {
        return this.profile;
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
            final CharacterProfile profile = message.profile;
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
                                    profile,
                                    age);
                        }
                    }
            );
            return null;
        }
    }
}
