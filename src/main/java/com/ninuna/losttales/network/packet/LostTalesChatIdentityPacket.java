package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.server.ChatIdentitySelection;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Client-to-server: the chat identity chosen — one owned character, or
 * with no id the played character — and whether the Narrator's voice is
 * taken up over it. A presence byte, the id when present, and the
 * voice's flag; anything short, trailing or out of range is malformed.
 */
public final class LostTalesChatIdentityPacket implements IMessage {
    private static final int MAX_PACKET_BYTES = 18;

    private UUID characterId;
    private boolean narrating;
    private boolean malformed;

    public LostTalesChatIdentityPacket() {}

    public LostTalesChatIdentityPacket(UUID characterId, boolean narrating) {
        this.characterId = characterId;
        this.narrating = narrating;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException("invalid chat identity size");
            }
            int present = buffer.readUnsignedByte();
            if (present > 1) {
                throw new LostTalesPacketCodec.DecodeException("invalid identity flag");
            }
            this.characterId = present == 0 ? null
                    : new UUID(buffer.readLong(), buffer.readLong());
            int voice = buffer.readUnsignedByte();
            if (voice > 1) {
                throw new LostTalesPacketCodec.DecodeException("invalid narrator flag");
            }
            this.narrating = voice == 1;
            LostTalesPacketCodec.requireFinished(buffer);
        } catch (RuntimeException failure) {
            this.malformed = true;
            this.characterId = null;
            this.narrating = false;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(this.characterId != null);
        if (this.characterId != null) {
            buffer.writeLong(this.characterId.getMostSignificantBits());
            buffer.writeLong(this.characterId.getLeastSignificantBits());
        }
        buffer.writeBoolean(this.narrating);
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    /** Whether the Narrator's voice is taken up over the identity. */
    public boolean isNarrating() {
        return this.narrating;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler
            implements IMessageHandler<LostTalesChatIdentityPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatIdentityPacket message,
                                  MessageContext context) {
            EntityPlayerMP player = LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(player,
                    LostTalesRequestRateLimiter.RequestType.CHAT_IDENTITY,
                    message.isMalformed(), "LostTalesChatIdentityPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP sender) {
                            ChatIdentitySelection.select(sender,
                                    message.getCharacterId(), message.isNarrating());
                        }
                    });
            return null;
        }
    }
}
