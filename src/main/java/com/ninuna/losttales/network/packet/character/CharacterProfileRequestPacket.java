package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.server.CharacterProfileViews;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

/**
 * A client asking what a character says about itself, by the character's
 * id alone. The server decides whether this player may read it — one of
 * their own characters, or one another online player is using — and
 * answers with {@link CharacterProfilePacket} either way.
 */
public final class CharacterProfileRequestPacket implements IMessage {

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private UUID characterId;
    private boolean malformed;

    public CharacterProfileRequestPacket() {}

    public CharacterProfileRequestPacket(UUID characterId) {
        if (characterId == null || NIL_UUID.equals(characterId)) {
            throw new IllegalArgumentException("invalid profile request");
        }
        this.characterId = characterId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.characterId = CharacterPacketCodec.readUuid(buffer);
            CharacterPacketCodec.requireFinished(buffer);
            if (NIL_UUID.equals(this.characterId)) {
                throw new CharacterPacketCodec.DecodeException("nil character id");
            }
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.characterId = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        CharacterPacketCodec.writeUuid(buffer, this.characterId);
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler
            implements IMessageHandler<CharacterProfileRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(final CharacterProfileRequestPacket message,
                                  MessageContext context) {
            EntityPlayerMP player =
                    LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            final UUID characterId = message.characterId;
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType.CHARACTER_PROFILE,
                    message.malformed,
                    "CharacterProfileRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            CharacterProfileViews.send(livePlayer, characterId);
                        }
                    });
            return null;
        }
    }
}
