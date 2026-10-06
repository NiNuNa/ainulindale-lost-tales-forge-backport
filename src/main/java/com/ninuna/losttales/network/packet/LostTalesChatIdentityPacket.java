package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.server.ChatIdentitySelection;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Client-to-server: the characters the chat's copies read as besides the
 * one played, each copy its own person. A count and that many distinct
 * ids, at most {@link #MAX_READ}; anything short, trailing, repeated or
 * over the bound is malformed. The server keeps only the ones the player
 * owns.
 */
public final class LostTalesChatIdentityPacket implements IMessage {
    /** Most characters the copies may read as besides the one played. */
    public static final int MAX_READ = 16;
    private static final int MAX_PACKET_BYTES = 1 + MAX_READ * 16;

    private List<UUID> characterIds = Collections.emptyList();
    private boolean malformed;

    public LostTalesChatIdentityPacket() {}

    public LostTalesChatIdentityPacket(List<UUID> characterIds) {
        this.characterIds = characterIds == null ? Collections.<UUID>emptyList()
                : Collections.unmodifiableList(new ArrayList<UUID>(characterIds));
        validate(this.characterIds);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException("invalid chat identity size");
            }
            int count = buffer.readUnsignedByte();
            if (count > MAX_READ) {
                throw new LostTalesPacketCodec.DecodeException("too many identities");
            }
            List<UUID> read = new ArrayList<UUID>(count);
            for (int index = 0; index < count; index++) {
                read.add(new UUID(buffer.readLong(), buffer.readLong()));
            }
            LostTalesPacketCodec.requireFinished(buffer);
            validate(read);
            this.characterIds = Collections.unmodifiableList(read);
        } catch (RuntimeException failure) {
            this.malformed = true;
            this.characterIds = Collections.emptyList();
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(this.characterIds.size());
        for (UUID id : this.characterIds) {
            buffer.writeLong(id.getMostSignificantBits());
            buffer.writeLong(id.getLeastSignificantBits());
        }
    }

    /** At most {@link #MAX_READ} ids, none null and none twice. */
    static void validate(List<UUID> ids) {
        if (ids.size() > MAX_READ) {
            throw new IllegalArgumentException("too many identities");
        }
        Set<UUID> seen = new HashSet<UUID>();
        for (UUID id : ids) {
            if (id == null || !seen.add(id)) {
                throw new IllegalArgumentException("identities must be distinct");
            }
        }
    }

    /** The characters read as besides the one played. */
    public List<UUID> getCharacterIds() {
        return this.characterIds;
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
                            ChatIdentitySelection.read(sender,
                                    message.getCharacterIds());
                        }
                    });
            return null;
        }
    }
}
