package com.ninuna.losttales.network.packet.party;

import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.server.PartyNetworkRequestHandler;
import com.ninuna.losttales.party.server.PartyServerPacketDispatcher;
import com.ninuna.losttales.party.sync.PartyOperationType;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

/** Single bounded client-to-server request envelope for party operations. */
public final class PartyActionRequestPacket implements IMessage {

    public static final long NO_PARTY_REVISION = -1L;

    private int requestId;
    private PartyOperationType operationType = PartyOperationType.UNKNOWN;
    private UUID expectedActiveCharacterId;
    private UUID expectedPartyId;
    private long expectedPartyRevision = NO_PARTY_REVISION;
    private UUID targetId;
    private PartyColor color;
    private boolean hasMarkerPosition;
    private int markerDimensionId;
    private double markerX;
    private double markerZ;
    private boolean malformed;

    public PartyActionRequestPacket() {}

    public PartyActionRequestPacket(int requestId,
                                    PartyOperationType operationType,
                                    UUID expectedActiveCharacterId,
                                    UUID expectedPartyId,
                                    long expectedPartyRevision,
                                    UUID targetId,
                                    PartyColor color,
                                    boolean hasMarkerPosition,
                                    int markerDimensionId,
                                    double markerX,
                                    double markerZ) {
        this.requestId = requestId;
        this.operationType = operationType == null
                ? PartyOperationType.UNKNOWN : operationType;
        this.expectedActiveCharacterId = expectedActiveCharacterId;
        this.expectedPartyId = expectedPartyId;
        this.expectedPartyRevision = expectedPartyRevision;
        this.targetId = targetId;
        this.color = color;
        this.hasMarkerPosition = hasMarkerPosition;
        this.markerDimensionId = markerDimensionId;
        this.markerX = markerX;
        this.markerZ = markerZ;
        validateShape();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.requestId = buffer.readInt();
            this.operationType = PartyOperationType.fromNetworkId(
                    buffer.readUnsignedByte());
            this.expectedActiveCharacterId =
                    LostTalesPacketCodec.readNullableUuid(buffer);
            this.expectedPartyId = LostTalesPacketCodec.readNullableUuid(buffer);
            this.expectedPartyRevision = buffer.readLong();
            this.targetId = LostTalesPacketCodec.readNullableUuid(buffer);
            int colorId = buffer.readByte();
            if (colorId == -1) {
                this.color = null;
            } else {
                this.color = PartyColor.fromNetworkId(colorId);
                if (this.color == null) {
                    throw new PartyPacketCodec.DecodeException(
                            "invalid party color identifier");
                }
            }
            this.hasMarkerPosition = buffer.readBoolean();
            if (this.hasMarkerPosition) {
                this.markerDimensionId = buffer.readInt();
                this.markerX = buffer.readDouble();
                this.markerZ = buffer.readDouble();
            } else {
                this.markerDimensionId = 0;
                this.markerX = 0.0D;
                this.markerZ = 0.0D;
            }
            LostTalesPacketCodec.requireFinished(buffer);
            validateShape();
        } catch (RuntimeException exception) {
            this.operationType = this.operationType == null
                    ? PartyOperationType.UNKNOWN : this.operationType;
            this.malformed = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validateShape();
        buffer.writeInt(this.requestId);
        buffer.writeByte(this.operationType.getNetworkId());
        LostTalesPacketCodec.writeNullableUuid(
                buffer, this.expectedActiveCharacterId);
        LostTalesPacketCodec.writeNullableUuid(buffer, this.expectedPartyId);
        buffer.writeLong(this.expectedPartyRevision);
        LostTalesPacketCodec.writeNullableUuid(buffer, this.targetId);
        buffer.writeByte(this.color == null ? -1 : this.color.getNetworkId());
        buffer.writeBoolean(this.hasMarkerPosition);
        if (this.hasMarkerPosition) {
            buffer.writeInt(this.markerDimensionId);
            buffer.writeDouble(this.markerX);
            buffer.writeDouble(this.markerZ);
        }
    }

    private void validateShape() {
        if (this.operationType == null
                || this.operationType == PartyOperationType.UNKNOWN) {
            throw new PartyPacketCodec.DecodeException("unknown party operation");
        }
        boolean stateRequest = this.operationType
                == PartyOperationType.REQUEST_STATE;
        if (stateRequest) {
            if (this.expectedActiveCharacterId != null) {
                throw new PartyPacketCodec.DecodeException(
                        "unexpected active character context");
            }
        } else if (this.expectedActiveCharacterId == null) {
            throw new PartyPacketCodec.DecodeException(
                    "missing active character context");
        }
        if (this.operationType.requiresPartyRevision()) {
            if (this.expectedPartyId == null
                    || this.expectedPartyRevision < 0L) {
                throw new PartyPacketCodec.DecodeException(
                        "missing party context");
            }
        } else if (this.expectedPartyId != null
                || this.expectedPartyRevision != NO_PARTY_REVISION) {
            throw new PartyPacketCodec.DecodeException(
                    "unexpected party context");
        }
        if (this.operationType.requiresTargetId() != (this.targetId != null)) {
            throw new PartyPacketCodec.DecodeException("invalid target payload");
        }
        if (this.operationType.requiresColor() != (this.color != null)) {
            throw new PartyPacketCodec.DecodeException("invalid color payload");
        }
        if ((this.operationType == PartyOperationType.SET_GO_HERE_MARKER)
                != this.hasMarkerPosition) {
            throw new PartyPacketCodec.DecodeException(
                    "invalid map marker position payload");
        }
        if (this.hasMarkerPosition
                && (!Double.isFinite(this.markerX) || !Double.isFinite(this.markerZ)
                || Math.abs(this.markerX) > 30000000.0D
                || Math.abs(this.markerZ) > 30000000.0D)) {
            throw new PartyPacketCodec.DecodeException(
                    "invalid map marker coordinates");
        }
    }

    public static final class Handler implements IMessageHandler<PartyActionRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(final PartyActionRequestPacket message,
                                  MessageContext context) {
            EntityPlayerMP player = PartyServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            final int requestId = message.requestId;
            final PartyOperationType operationType = message.operationType;
            final UUID expectedActiveCharacterId =
                    message.expectedActiveCharacterId;
            final UUID expectedPartyId = message.expectedPartyId;
            final long expectedPartyRevision = message.expectedPartyRevision;
            final UUID targetId = message.targetId;
            final PartyColor color = message.color;
            final boolean hasMarkerPosition = message.hasMarkerPosition;
            final int markerDimensionId = message.markerDimensionId;
            final double markerX = message.markerX;
            final double markerZ = message.markerZ;
            PartyServerPacketDispatcher.submit(
                    player,
                    requestId,
                    operationType,
                    message.malformed,
                    "PartyActionRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            PartyNetworkRequestHandler.handleAction(
                                    livePlayer,
                                    requestId,
                                    operationType,
                                    expectedActiveCharacterId,
                                    expectedPartyId,
                                    expectedPartyRevision,
                                    targetId,
                                    color,
                                    hasMarkerPosition,
                                    markerDimensionId,
                                    markerX,
                                    markerZ);
                        }
                    });
            return null;
        }
    }
}
