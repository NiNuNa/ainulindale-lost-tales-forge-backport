package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.server.CharacterGearViews;
import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
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
 * A client asking what one of its own characters wears in its saved
 * life, by the character's id alone, for the stage (CHAR-42). The server
 * answers with {@link CharacterGearPacket}: that character's armour and
 * held item when it is the asker's and not the one played, nothing for
 * anything else.
 */
public final class CharacterGearRequestPacket implements IMessage {

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private UUID characterId;
    private boolean malformed;

    public CharacterGearRequestPacket() {}

    public CharacterGearRequestPacket(UUID characterId) {
        if (characterId == null || NIL_UUID.equals(characterId)) {
            throw new IllegalArgumentException("invalid gear request");
        }
        this.characterId = characterId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            this.characterId = LostTalesPacketCodec.readUuid(buffer);
            LostTalesPacketCodec.requireFinished(buffer);
            if (NIL_UUID.equals(this.characterId)) {
                throw new CharacterPacketCodec.DecodeException("nil character id");
            }
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.characterId = null;
            if (buffer != null && buffer.isReadable()) {
                buffer.skipBytes(buffer.readableBytes());
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        LostTalesPacketCodec.writeUuid(buffer, this.characterId);
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler
            implements IMessageHandler<CharacterGearRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(final CharacterGearRequestPacket message,
                                  MessageContext context) {
            EntityPlayerMP player =
                    LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            final UUID characterId = message.characterId;
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType.CHARACTER_GEAR,
                    message.malformed,
                    "CharacterGearRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            CharacterGearViews.send(livePlayer, characterId);
                        }
                    });
            return null;
        }
    }
}
