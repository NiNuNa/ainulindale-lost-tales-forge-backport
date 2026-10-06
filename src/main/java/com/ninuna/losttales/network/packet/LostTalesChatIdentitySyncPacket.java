package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatFellowship;
import com.ninuna.losttales.fellowship.model.Fellowship;
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

/**
 * The server's word on whom the player reads the chat as: the characters
 * it kept of those the copies read as besides the one played, and the
 * fellowships of the character played as the chat sees them, the one
 * they travel with first: each one's id, name and the colour worn in it,
 * which name and colour its conversation.
 */
public final class LostTalesChatIdentitySyncPacket implements IMessage {
    /** A fellowship's name in UTF-8: its most characters, at four bytes each at most. */
    static final int MAX_FELLOWSHIP_NAME_BYTES = Fellowship.MAX_NAME_LENGTH * 4;
    /** The characters read as and their count, then each fellowship's id, colour and name and their count. */
    private static final int MAX_PACKET_BYTES = 1 + LostTalesChatIdentityPacket.MAX_READ * 16
            + 1 + Fellowship.MAX_FELLOWSHIPS_PER_IDENTITY * (16 + 4 + 2 + MAX_FELLOWSHIP_NAME_BYTES);

    private List<UUID> characterIds = Collections.emptyList();
    private List<ChatFellowship> fellowships = Collections.emptyList();
    private boolean malformed;

    public LostTalesChatIdentitySyncPacket() {}

    public LostTalesChatIdentitySyncPacket(List<UUID> characterIds,
                                           List<ChatFellowship> fellowships) {
        this.characterIds = characterIds == null ? Collections.<UUID>emptyList()
                : Collections.unmodifiableList(new ArrayList<UUID>(characterIds));
        this.fellowships = fellowships == null ? Collections.<ChatFellowship>emptyList()
                : Collections.unmodifiableList(new ArrayList<ChatFellowship>(fellowships));
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException("invalid chat identity sync size");
            }
            int readCount = buffer.readUnsignedByte();
            if (readCount > LostTalesChatIdentityPacket.MAX_READ) {
                throw new LostTalesPacketCodec.DecodeException("too many identities");
            }
            List<UUID> ids = new ArrayList<UUID>(readCount);
            for (int index = 0; index < readCount; index++) {
                ids.add(new UUID(buffer.readLong(), buffer.readLong()));
            }
            this.characterIds = Collections.unmodifiableList(ids);
            int count = buffer.readUnsignedByte();
            if (count > Fellowship.MAX_FELLOWSHIPS_PER_IDENTITY) {
                throw new LostTalesPacketCodec.DecodeException("too many fellowships");
            }
            List<ChatFellowship> read = new ArrayList<ChatFellowship>(count);
            for (int index = 0; index < count; index++) {
                UUID id = new UUID(buffer.readLong(), buffer.readLong());
                int color = buffer.readInt();
                String name = LostTalesPacketCodec.readUtf8String(buffer,
                        MAX_FELLOWSHIP_NAME_BYTES);
                if ((color & 0xFF000000) != 0 || !Fellowship.isWellFormedName(name)) {
                    throw new LostTalesPacketCodec.DecodeException("invalid chat fellowship");
                }
                read.add(new ChatFellowship(id, name, color));
            }
            this.fellowships = Collections.unmodifiableList(read);
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException failure) {
            this.malformed = true;
            this.characterIds = Collections.emptyList();
            this.fellowships = Collections.emptyList();
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        buffer.writeByte(this.characterIds.size());
        for (UUID id : this.characterIds) {
            buffer.writeLong(id.getMostSignificantBits());
            buffer.writeLong(id.getLeastSignificantBits());
        }
        buffer.writeByte(this.fellowships.size());
        for (ChatFellowship fellowship : this.fellowships) {
            buffer.writeLong(fellowship.getId().getMostSignificantBits());
            buffer.writeLong(fellowship.getId().getLeastSignificantBits());
            buffer.writeInt(fellowship.getColor());
            LostTalesPacketCodec.writeUtf8String(buffer, fellowship.getName(),
                    MAX_FELLOWSHIP_NAME_BYTES);
        }
    }

    private void validate() {
        LostTalesChatIdentityPacket.validate(this.characterIds);
        if (this.fellowships.size() > Fellowship.MAX_FELLOWSHIPS_PER_IDENTITY) {
            throw new IllegalArgumentException("too many fellowships");
        }
        Set<UUID> ids = new HashSet<UUID>();
        for (ChatFellowship fellowship : this.fellowships) {
            if (fellowship == null || !ids.add(fellowship.getId())
                    || !LostTalesPacketCodec.isUtf8WithinLimit(fellowship.getName(),
                            MAX_FELLOWSHIP_NAME_BYTES)) {
                throw new IllegalArgumentException("fellowships must be distinct and bounded");
            }
        }
    }

    /** The characters the server keeps as read besides the one played. */
    public List<UUID> getCharacterIds() {
        return this.characterIds;
    }

    /** The fellowships of the character played, the one travelled with first. */
    public List<ChatFellowship> getFellowships() {
        return this.fellowships;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler
            implements IMessageHandler<LostTalesChatIdentitySyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatIdentitySyncPacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleChatIdentity(message);
                }
            });
            return null;
        }
    }
}
