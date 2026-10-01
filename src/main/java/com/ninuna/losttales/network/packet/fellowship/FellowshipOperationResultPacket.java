package com.ninuna.losttales.network.packet.fellowship;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import com.ninuna.losttales.fellowship.server.FellowshipErrorId;
import com.ninuna.losttales.fellowship.server.FellowshipInvitationOperationResult;
import com.ninuna.losttales.fellowship.server.FellowshipOperationResult;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationFeedback;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationType;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Structured result for one fellowship mutation request. */
public final class FellowshipOperationResultPacket implements IMessage {

    private int requestId;
    private FellowshipOperationType operationType = FellowshipOperationType.UNKNOWN;
    private boolean successful;
    private boolean changed;
    private boolean fellowshipDisbanded;
    private FellowshipErrorId errorId = FellowshipErrorId.INTERNAL_ERROR;
    private long fellowshipRevision = -1L;
    private boolean stateFollows;
    private boolean malformed;

    public FellowshipOperationResultPacket() {}

    public FellowshipOperationResultPacket(int requestId,
                                      FellowshipOperationType operationType,
                                      FellowshipOperationResult result,
                                      boolean stateFollows) {
        if (operationType == null || result == null) {
            throw new IllegalArgumentException("operationType and result must not be null");
        }
        this.requestId = requestId;
        this.operationType = operationType;
        this.successful = result.isSuccessful();
        this.changed = result.wasChanged();
        this.fellowshipDisbanded = result.wasFellowshipDisbanded();
        this.errorId = result.getErrorId();
        this.fellowshipRevision = result.getFellowship() == null
                || result.wasFellowshipDisbanded()
                ? -1L : result.getFellowship().getRevision();
        this.stateFollows = stateFollows;
    }

    public FellowshipOperationResultPacket(int requestId,
                                      FellowshipOperationType operationType,
                                      FellowshipInvitationOperationResult result,
                                      boolean stateFollows) {
        if (operationType == null || result == null) {
            throw new IllegalArgumentException("operationType and result must not be null");
        }
        this.requestId = requestId;
        this.operationType = operationType;
        this.successful = result.isSuccessful();
        this.changed = result.wasChanged();
        this.fellowshipDisbanded = false;
        this.errorId = result.getErrorId();
        this.fellowshipRevision = result.getFellowship() == null
                ? -1L : result.getFellowship().getRevision();
        this.stateFollows = stateFollows;
    }

    public FellowshipOperationResultPacket(int requestId,
                                      FellowshipOperationType operationType,
                                      FellowshipErrorId errorId,
                                      long fellowshipRevision,
                                      boolean stateFollows) {
        this.requestId = requestId;
        this.operationType = operationType == null
                ? FellowshipOperationType.UNKNOWN : operationType;
        this.successful = false;
        this.changed = false;
        this.fellowshipDisbanded = false;
        this.errorId = errorId == null
                ? FellowshipErrorId.INTERNAL_ERROR : errorId;
        this.fellowshipRevision = fellowshipRevision;
        this.stateFollows = stateFollows;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.requestId = buffer.readInt();
            this.operationType = FellowshipOperationType.fromNetworkId(
                    buffer.readUnsignedByte());
            this.successful = buffer.readBoolean();
            this.changed = buffer.readBoolean();
            this.fellowshipDisbanded = buffer.readBoolean();
            this.errorId = FellowshipErrorId.fromId(LostTalesPacketCodec.readUtf8String(
                    buffer, FellowshipPacketCodec.MAX_ERROR_ID_BYTES));
            this.fellowshipRevision = buffer.readLong();
            this.stateFollows = buffer.readBoolean();
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.operationType = FellowshipOperationType.UNKNOWN;
            this.errorId = FellowshipErrorId.INTERNAL_ERROR;
            this.successful = false;
            this.changed = false;
            this.fellowshipDisbanded = false;
            this.stateFollows = false;
            this.malformed = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        buffer.writeInt(this.requestId);
        buffer.writeByte(this.operationType.getNetworkId());
        buffer.writeBoolean(this.successful);
        buffer.writeBoolean(this.changed);
        buffer.writeBoolean(this.fellowshipDisbanded);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.errorId.getId(), FellowshipPacketCodec.MAX_ERROR_ID_BYTES);
        buffer.writeLong(this.fellowshipRevision);
        buffer.writeBoolean(this.stateFollows);
    }

    public FellowshipOperationFeedback toFeedback() {
        return new FellowshipOperationFeedback(
                this.requestId,
                this.operationType,
                this.successful,
                this.errorId,
                this.stateFollows);
    }

    public int getRequestId() {
        return this.requestId;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    private void validate() {
        if (this.operationType == null
                || (this.operationType == FellowshipOperationType.UNKNOWN
                    && (this.successful
                        || this.errorId != FellowshipErrorId.MALFORMED_REQUEST))
                || (this.successful && this.errorId != FellowshipErrorId.NONE)
                || (!this.successful && this.errorId == FellowshipErrorId.NONE)
                || (this.fellowshipDisbanded && (!this.successful || !this.changed))
                || (this.fellowshipRevision < -1L)) {
            throw new FellowshipPacketCodec.DecodeException("invalid fellowship operation result");
        }
    }

    public static final class Handler implements IMessageHandler<FellowshipOperationResultPacket, IMessage> {
        @Override
        public IMessage onMessage(final FellowshipOperationResultPacket message,
                                  MessageContext context) {
            if (message == null) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleFellowshipOperationResult(message);
                }
            });
            return null;
        }
    }
}
