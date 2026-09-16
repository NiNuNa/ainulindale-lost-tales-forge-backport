package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.UUID;

/**
 * The server's answer to a chat identity selection: the identity it now
 * holds for the player, and that identity's party as the chat sees it,
 * independent of the gameplay party snapshot: the party's id, the colour
 * the identity wears in it, and its leader's character name, which names
 * the Party tab.
 */
public final class LostTalesChatIdentitySyncPacket implements IMessage {
    /** A character name is at most 32 characters; room for them in UTF-8. */
    static final int MAX_PARTY_LEADER_BYTES = 96;
    /** Two optional ids, the colour, the leader's name behind its length, the voice's flag. */
    private static final int MAX_PACKET_BYTES = 2 * 17 + 4 + 2 + MAX_PARTY_LEADER_BYTES + 1;

    private UUID characterId;
    private UUID partyId;
    private int partyColor;
    private String partyLeader = "";
    /** Whether the Narrator's voice is taken up over the identity. */
    private boolean narrating;
    private boolean malformed;

    public LostTalesChatIdentitySyncPacket() {}

    public LostTalesChatIdentitySyncPacket(UUID characterId, UUID partyId, int partyColor,
                                           String partyLeader, boolean narrating) {
        this.characterId = characterId;
        this.partyId = partyId;
        this.partyColor = partyColor;
        this.partyLeader = partyLeader == null ? "" : partyLeader;
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
            this.partyId = readId(buffer);
            this.partyColor = buffer.readInt();
            this.partyLeader = LostTalesPacketCodec.readUtf8String(buffer,
                    MAX_PARTY_LEADER_BYTES);
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
            this.partyId = null;
            this.partyColor = 0;
            this.partyLeader = "";
            this.narrating = false;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        writeId(buffer, this.characterId);
        writeId(buffer, this.partyId);
        buffer.writeInt(this.partyColor);
        LostTalesPacketCodec.writeUtf8String(buffer, this.partyLeader, MAX_PARTY_LEADER_BYTES);
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
        if (this.partyColor < 0 || this.partyColor > 0xFFFFFF) {
            throw new IllegalArgumentException("invalid party color");
        }
        if (!LostTalesPacketCodec.isUtf8WithinLimit(this.partyLeader, MAX_PARTY_LEADER_BYTES)) {
            throw new IllegalArgumentException("party leader name exceeds packet limit");
        }
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public UUID getPartyId() {
        return this.partyId;
    }

    public int getPartyColor() {
        return this.partyColor;
    }

    /** The party leader's character name; empty without a party. */
    public String getPartyLeader() {
        return this.partyLeader;
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
