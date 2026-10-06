package com.ninuna.losttales.network.packet.fellowship;

import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipSwitch;
import com.ninuna.losttales.fellowship.server.FellowshipNetworkRequestHandler;
import com.ninuna.losttales.fellowship.server.FellowshipServerPacketDispatcher;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationType;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

/**
 * Single bounded client-to-server request envelope for fellowship operations.
 * The colour travels only for a colour change, the position only for a
 * go-here marker placed on the map, the name only for a new fellowship and a
 * rename, the value byte only for a guide made or unmade and a switch.
 */
public final class FellowshipActionRequestPacket implements IMessage {

    public static final long NO_FELLOWSHIP_REVISION = -1L;

    private int requestId;
    private FellowshipOperationType operationType = FellowshipOperationType.UNKNOWN;
    private UUID expectedActiveIdentityId;
    private UUID expectedFellowshipId;
    private long expectedFellowshipRevision = NO_FELLOWSHIP_REVISION;
    private UUID targetId;
    private FellowshipColor color;
    private boolean hasMarkerPosition;
    private int markerDimensionId;
    private double markerX;
    private double markerZ;
    private String name;
    /** A guide request's yes or no, or a switch request's switch and state; -1 for none. */
    private int value = -1;
    private boolean malformed;

    public FellowshipActionRequestPacket() {}

    public FellowshipActionRequestPacket(int requestId,
                                    FellowshipOperationType operationType,
                                    UUID expectedActiveIdentityId,
                                    UUID expectedFellowshipId,
                                    long expectedFellowshipRevision,
                                    UUID targetId,
                                    FellowshipColor color,
                                    boolean hasMarkerPosition,
                                    int markerDimensionId,
                                    double markerX,
                                    double markerZ,
                                    String name,
                                    int value) {
        this.requestId = requestId;
        this.operationType = operationType == null
                ? FellowshipOperationType.UNKNOWN : operationType;
        this.expectedActiveIdentityId = expectedActiveIdentityId;
        this.expectedFellowshipId = expectedFellowshipId;
        this.expectedFellowshipRevision = expectedFellowshipRevision;
        this.targetId = targetId;
        this.color = color;
        this.hasMarkerPosition = hasMarkerPosition;
        this.markerDimensionId = markerDimensionId;
        this.markerX = markerX;
        this.markerZ = markerZ;
        this.name = name;
        this.value = value;
        validateShape();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.requestId = buffer.readInt();
            this.operationType = FellowshipOperationType.fromNetworkId(
                    buffer.readUnsignedByte());
            this.expectedActiveIdentityId =
                    LostTalesPacketCodec.readNullableUuid(buffer);
            this.expectedFellowshipId = LostTalesPacketCodec.readNullableUuid(buffer);
            this.expectedFellowshipRevision = buffer.readLong();
            this.targetId = LostTalesPacketCodec.readNullableUuid(buffer);
            int colorId = buffer.readByte();
            if (colorId == -1) {
                this.color = null;
            } else {
                this.color = FellowshipColor.fromNetworkId(colorId);
                if (this.color == null) {
                    throw new FellowshipPacketCodec.DecodeException(
                            "invalid fellowship color identifier");
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
            this.name = this.operationType.requiresName()
                    ? LostTalesPacketCodec.readUtf8String(
                            buffer, FellowshipPacketCodec.MAX_FELLOWSHIP_NAME_BYTES)
                    : null;
            this.value = this.operationType.requiresValue()
                    ? buffer.readUnsignedByte() : -1;
            LostTalesPacketCodec.requireFinished(buffer);
            validateShape();
        } catch (RuntimeException exception) {
            this.operationType = this.operationType == null
                    ? FellowshipOperationType.UNKNOWN : this.operationType;
            this.malformed = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validateShape();
        buffer.writeInt(this.requestId);
        buffer.writeByte(this.operationType.getNetworkId());
        LostTalesPacketCodec.writeNullableUuid(
                buffer, this.expectedActiveIdentityId);
        LostTalesPacketCodec.writeNullableUuid(buffer, this.expectedFellowshipId);
        buffer.writeLong(this.expectedFellowshipRevision);
        LostTalesPacketCodec.writeNullableUuid(buffer, this.targetId);
        buffer.writeByte(this.color == null ? -1 : this.color.getNetworkId());
        buffer.writeBoolean(this.hasMarkerPosition);
        if (this.hasMarkerPosition) {
            buffer.writeInt(this.markerDimensionId);
            buffer.writeDouble(this.markerX);
            buffer.writeDouble(this.markerZ);
        }
        if (this.name != null) {
            LostTalesPacketCodec.writeUtf8String(buffer, this.name,
                    FellowshipPacketCodec.MAX_FELLOWSHIP_NAME_BYTES);
        }
        if (this.operationType.requiresValue()) {
            buffer.writeByte(this.value);
        }
    }

    /** The operation asked for; UNKNOWN for a payload that could not be read. */
    FellowshipOperationType getOperationType() {
        return this.operationType;
    }

    /** The name a new fellowship or a rename asks for, as typed; null for every other operation. */
    String getName() {
        return this.name;
    }

    /** Whether the payload could not be read, or broke the operation's shape. */
    boolean isMalformed() {
        return this.malformed;
    }

    private void validateShape() {
        if (this.operationType == null
                || this.operationType == FellowshipOperationType.UNKNOWN) {
            throw new FellowshipPacketCodec.DecodeException("unknown fellowship operation");
        }
        boolean stateRequest = this.operationType
                == FellowshipOperationType.REQUEST_STATE;
        if (stateRequest) {
            if (this.expectedActiveIdentityId != null) {
                throw new FellowshipPacketCodec.DecodeException(
                        "unexpected active character context");
            }
        } else if (this.expectedActiveIdentityId == null) {
            throw new FellowshipPacketCodec.DecodeException(
                    "missing active character context");
        }
        if (this.operationType.requiresFellowshipRevision()) {
            if (this.expectedFellowshipId == null
                    || this.expectedFellowshipRevision < 0L) {
                throw new FellowshipPacketCodec.DecodeException(
                        "missing fellowship context");
            }
        } else if (this.expectedFellowshipId != null
                || this.expectedFellowshipRevision != NO_FELLOWSHIP_REVISION) {
            throw new FellowshipPacketCodec.DecodeException(
                    "unexpected fellowship context");
        }
        if (this.operationType.requiresTargetId() != (this.targetId != null)) {
            throw new FellowshipPacketCodec.DecodeException("invalid target payload");
        }
        if (this.operationType.requiresColor() != (this.color != null)) {
            throw new FellowshipPacketCodec.DecodeException("invalid color payload");
        }
        if (this.operationType.requiresMapPosition() != this.hasMarkerPosition) {
            throw new FellowshipPacketCodec.DecodeException(
                    "invalid map marker position payload");
        }
        if (this.hasMarkerPosition
                && (!Double.isFinite(this.markerX) || !Double.isFinite(this.markerZ)
                || Math.abs(this.markerX) > 30000000.0D
                || Math.abs(this.markerZ) > 30000000.0D)) {
            throw new FellowshipPacketCodec.DecodeException(
                    "invalid map marker coordinates");
        }
        if (this.operationType.requiresName() != (this.name != null)) {
            throw new FellowshipPacketCodec.DecodeException("invalid name payload");
        }
        if (this.name != null && this.name.getBytes(FellowshipPacketCodec.UTF_8).length
                > FellowshipPacketCodec.MAX_FELLOWSHIP_NAME_BYTES) {
            throw new FellowshipPacketCodec.DecodeException("fellowship name too long");
        }
        if (this.operationType.requiresValue() != (this.value >= 0)) {
            throw new FellowshipPacketCodec.DecodeException("invalid value payload");
        }
        if (this.operationType == FellowshipOperationType.SET_GUIDE
                && this.value > 1) {
            throw new FellowshipPacketCodec.DecodeException("invalid guide payload");
        }
        if (this.operationType == FellowshipOperationType.SET_SWITCH
                && FellowshipSwitch.fromNetworkId(this.value >> 1) == null) {
            throw new FellowshipPacketCodec.DecodeException("invalid switch payload");
        }
    }

    public static final class Handler implements IMessageHandler<FellowshipActionRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(final FellowshipActionRequestPacket message,
                                  MessageContext context) {
            EntityPlayerMP player = FellowshipServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            final int requestId = message.requestId;
            final FellowshipOperationType operationType = message.getOperationType();
            final UUID expectedActiveIdentityId =
                    message.expectedActiveIdentityId;
            final UUID expectedFellowshipId = message.expectedFellowshipId;
            final long expectedFellowshipRevision = message.expectedFellowshipRevision;
            final UUID targetId = message.targetId;
            final FellowshipColor color = message.color;
            final boolean hasMarkerPosition = message.hasMarkerPosition;
            final int markerDimensionId = message.markerDimensionId;
            final double markerX = message.markerX;
            final double markerZ = message.markerZ;
            final String name = message.getName();
            final int value = message.value;
            FellowshipServerPacketDispatcher.submit(
                    player,
                    requestId,
                    operationType,
                    message.isMalformed(),
                    "FellowshipActionRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            FellowshipNetworkRequestHandler.handleAction(
                                    livePlayer,
                                    requestId,
                                    operationType,
                                    expectedActiveIdentityId,
                                    expectedFellowshipId,
                                    expectedFellowshipRevision,
                                    targetId,
                                    color,
                                    hasMarkerPosition,
                                    markerDimensionId,
                                    markerX,
                                    markerZ,
                                    name,
                                    value);
                        }
                    });
            return null;
        }
    }
}
