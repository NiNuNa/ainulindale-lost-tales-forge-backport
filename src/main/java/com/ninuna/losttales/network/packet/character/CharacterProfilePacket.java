package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.character.model.CharacterProfile;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

import java.util.UUID;

/**
 * What a character says about itself, sent to the player who asked, or
 * to its owner when they have just changed it: the profile, or word that
 * it cannot be read here — a character that is not the reader's own and
 * that nobody online is using.
 */
public final class CharacterProfilePacket implements IMessage {

    private UUID characterId;
    private boolean available;
    private CharacterProfile profile = CharacterProfile.EMPTY;
    private boolean malformed;

    public CharacterProfilePacket() {}

    private CharacterProfilePacket(UUID characterId, boolean available,
                                   CharacterProfile profile) {
        if (characterId == null
                || (available && !CharacterPacketCodec.fits(profile))) {
            throw new IllegalArgumentException("invalid profile");
        }
        this.characterId = characterId;
        this.available = available;
        this.profile = available ? profile : CharacterProfile.EMPTY;
    }

    public static CharacterProfilePacket of(UUID characterId,
                                            CharacterProfile profile) {
        return new CharacterProfilePacket(characterId, true, profile);
    }

    public static CharacterProfilePacket unavailable(UUID characterId) {
        return new CharacterProfilePacket(characterId, false,
                CharacterProfile.EMPTY);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.characterId = CharacterPacketCodec.readUuid(buffer);
            this.available = buffer.readBoolean();
            this.profile = this.available
                    ? CharacterPacketCodec.readProfile(buffer)
                    : CharacterProfile.EMPTY;
            CharacterPacketCodec.requireFinished(buffer);
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.available = false;
            this.profile = CharacterProfile.EMPTY;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        CharacterPacketCodec.writeUuid(buffer, this.characterId);
        buffer.writeBoolean(this.available);
        if (this.available) {
            CharacterPacketCodec.writeProfile(buffer, this.profile);
        }
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    /** Whether the profile could be read; false for a character this player may not see. */
    public boolean isAvailable() {
        return this.available;
    }

    public CharacterProfile getProfile() {
        return this.profile;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler
            implements IMessageHandler<CharacterProfilePacket, IMessage> {
        @Override
        public IMessage onMessage(final CharacterProfilePacket message,
                                  MessageContext context) {
            if (message == null || message.malformed) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleCharacterProfile(message);
                }
            });
            return null;
        }
    }
}
