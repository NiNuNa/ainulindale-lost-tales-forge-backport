package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.server.CharacterCreationRequest;
import com.ninuna.losttales.character.server.CharacterNetworkRequestHandler;
import com.ninuna.losttales.character.server.CharacterServerPacketDispatcher;
import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Narrow client request to create one character in one unlocked slot. */
public final class CharacterCreateRequestPacket implements IMessage {

    private int requestId;
    private long expectedRosterRevision;
    private int slotIndex;
    private String name = "";
    private String raceId = "";
    private String genderId = "";
    private String skinId = "";
    private String description = "";
    private int age;
    private String startingFactionId = "";
    private String startingWaypointId = "";
    private boolean unconventionalSettings;
    private boolean malformed;

    public CharacterCreateRequestPacket() {}

    public CharacterCreateRequestPacket(int requestId, CharacterCreationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        this.requestId = requestId;
        this.expectedRosterRevision = request.getExpectedRosterRevision();
        this.slotIndex = request.getSlotIndex();
        this.name = request.getName();
        this.raceId = request.getRaceId();
        this.genderId = request.getGenderId();
        this.skinId = request.getSkinId();
        this.description = request.getDescription();
        this.age = request.getAge();
        this.startingFactionId = request.getStartingFactionId();
        this.startingWaypointId = request.getStartingWaypointId();
        this.unconventionalSettings = request.hasUnconventionalSettings();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.requestId = buffer.readInt();
            this.expectedRosterRevision = buffer.readLong();
            this.slotIndex = buffer.readByte();
            this.name = CharacterPacketCodec.readString(buffer, CharacterPacketCodec.MAX_NAME_BYTES);
            this.raceId = CharacterPacketCodec.readString(buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            this.genderId = CharacterPacketCodec.readString(
                    buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            this.skinId = CharacterPacketCodec.readString(
                    buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            this.description = CharacterPacketCodec.readString(
                    buffer, CharacterPacketCodec.MAX_DESCRIPTION_BYTES);
            this.age = buffer.readInt();
            this.startingFactionId = CharacterPacketCodec.readString(buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            this.startingWaypointId = CharacterPacketCodec.readString(
                    buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            this.unconventionalSettings = buffer.readBoolean();
            CharacterPacketCodec.requireFinished(buffer);
            if (this.expectedRosterRevision < 0L) {
                throw new CharacterPacketCodec.DecodeException("missing roster revision");
            }
        } catch (RuntimeException exception) {
            this.malformed = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.requestId);
        buffer.writeLong(this.expectedRosterRevision);
        buffer.writeByte(this.slotIndex);
        CharacterPacketCodec.writeString(buffer, this.name, CharacterPacketCodec.MAX_NAME_BYTES);
        CharacterPacketCodec.writeString(buffer, this.raceId, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
        CharacterPacketCodec.writeString(
                buffer, this.genderId, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
        CharacterPacketCodec.writeString(
                buffer, this.skinId, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
        CharacterPacketCodec.writeString(
                buffer, this.description, CharacterPacketCodec.MAX_DESCRIPTION_BYTES);
        buffer.writeInt(this.age);
        CharacterPacketCodec.writeString(buffer, this.startingFactionId, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
        CharacterPacketCodec.writeString(buffer, this.startingWaypointId,
                CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
        buffer.writeBoolean(this.unconventionalSettings);
    }

    private CharacterCreationRequest toRequest() {
        return new CharacterCreationRequest(
                this.expectedRosterRevision,
                this.slotIndex,
                this.name,
                this.raceId,
                this.genderId,
                this.skinId,
                this.age,
                this.startingFactionId,
                this.startingWaypointId,
                this.unconventionalSettings,
                this.description
        );
    }

    public static final class Handler implements IMessageHandler<CharacterCreateRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(final CharacterCreateRequestPacket message, MessageContext context) {
            final EntityPlayerMP player = CharacterServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            final int requestId = message.requestId;
            final CharacterCreationRequest request = message.toRequest();
            CharacterServerPacketDispatcher.submit(
                    player,
                    requestId,
                    CharacterOperationType.CREATE,
                    message.malformed,
                    "CharacterCreateRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            CharacterNetworkRequestHandler.handleCreateRequest(
                                    livePlayer, requestId, request);
                        }
                    }
            );
            return null;
        }
    }
}
