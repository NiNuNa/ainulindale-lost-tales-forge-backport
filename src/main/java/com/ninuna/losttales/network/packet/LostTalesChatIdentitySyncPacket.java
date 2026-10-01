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
 * The server's answer to a chat identity selection: the identity it now
 * holds for the player, and the fellowships of the character they play as
 * the chat sees them, the one they travel with first: each one's id, name
 * and the colour worn in it, which name and colour its conversation.
 */
public final class LostTalesChatIdentitySyncPacket implements IMessage {
    /** A fellowship's name in UTF-8: its most characters, at four bytes each at most. */
    static final int MAX_FELLOWSHIP_NAME_BYTES = Fellowship.MAX_NAME_LENGTH * 4;
    /** The optional id, the count, each fellowship's id, colour and name, the voice's flag. */
    private static final int MAX_PACKET_BYTES = 17 + 1
            + Fellowship.MAX_FELLOWSHIPS_PER_IDENTITY * (16 + 4 + 2 + MAX_FELLOWSHIP_NAME_BYTES)
            + 1;

    private UUID characterId;
    private List<ChatFellowship> fellowships = Collections.emptyList();
    /** Whether the Narrator's voice is taken up over the identity. */
    private boolean narrating;
    private boolean malformed;

    public LostTalesChatIdentitySyncPacket() {}

    public LostTalesChatIdentitySyncPacket(UUID characterId, List<ChatFellowship> fellowships,
                                           boolean narrating) {
        this.characterId = characterId;
        this.fellowships = fellowships == null ? Collections.<ChatFellowship>emptyList()
                : Collections.unmodifiableList(new ArrayList<ChatFellowship>(fellowships));
        this.narrating = narrating;
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException("invalid chat identity sync size");
            }
            this.characterId = readId(buffer);
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
                if (color < 0 || color > 0xFFFFFF || !Fellowship.isWellFormedName(name)) {
                    throw new LostTalesPacketCodec.DecodeException("invalid chat fellowship");
                }
                read.add(new ChatFellowship(id, name, color));
            }
            this.fellowships = Collections.unmodifiableList(read);
            int voice = buffer.readUnsignedByte();
            if (voice > 1) {
                throw new LostTalesPacketCodec.DecodeException("invalid narrator flag");
            }
            this.narrating = voice == 1;
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException failure) {
            this.malformed = true;
            this.characterId = null;
            this.fellowships = Collections.emptyList();
            this.narrating = false;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        writeId(buffer, this.characterId);
        buffer.writeByte(this.fellowships.size());
        for (ChatFellowship fellowship : this.fellowships) {
            buffer.writeLong(fellowship.getId().getMostSignificantBits());
            buffer.writeLong(fellowship.getId().getLeastSignificantBits());
            buffer.writeInt(fellowship.getColor());
            LostTalesPacketCodec.writeUtf8String(buffer, fellowship.getName(),
                    MAX_FELLOWSHIP_NAME_BYTES);
        }
        buffer.writeBoolean(this.narrating);
    }

    /** An id behind a presence byte that is exactly 0 or 1. */
    private static UUID readId(ByteBuf buffer) {
        int present = buffer.readUnsignedByte();
        if (present > 1) {
            throw new LostTalesPacketCodec.DecodeException("invalid identity flag");
        }
        return present == 0 ? null : new UUID(buffer.readLong(), buffer.readLong());
    }

    private static void writeId(ByteBuf buffer, UUID id) {
        buffer.writeBoolean(id != null);
        if (id != null) {
            buffer.writeLong(id.getMostSignificantBits());
            buffer.writeLong(id.getLeastSignificantBits());
        }
    }

    private void validate() {
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

    public UUID getCharacterId() {
        return this.characterId;
    }

    /** The fellowships of the character played, the one travelled with first. */
    public List<ChatFellowship> getFellowships() {
        return this.fellowships;
    }

    /** Whether the Narrator's voice is taken up over the identity. */
    public boolean isNarrating() {
        return this.narrating;
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
