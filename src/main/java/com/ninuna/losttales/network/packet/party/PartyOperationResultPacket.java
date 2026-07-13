package com.ninuna.losttales.network.packet.party;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.party.server.PartyErrorId;
import com.ninuna.losttales.party.server.PartyInvitationOperationResult;
import com.ninuna.losttales.party.server.PartyOperationResult;
import com.ninuna.losttales.party.sync.PartyOperationFeedback;
import com.ninuna.losttales.party.sync.PartyOperationType;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Structured result for one party mutation request. */
public final class PartyOperationResultPacket implements IMessage {

    private int requestId;
    private PartyOperationType operationType = PartyOperationType.UNKNOWN;
    private boolean successful;
    private boolean changed;
    private boolean partyDisbanded;
    private PartyErrorId errorId = PartyErrorId.INTERNAL_ERROR;
    private long partyRevision = -1L;
    private boolean stateFollows;
    private boolean malformed;

    public PartyOperationResultPacket() {}

    public PartyOperationResultPacket(int requestId,
                                      PartyOperationType operationType,
                                      PartyOperationResult result,
                                      boolean stateFollows) {
        if (operationType == null || result == null) {
            throw new IllegalArgumentException("operationType and result must not be null");
        }
        this.requestId = requestId;
        this.operationType = operationType;
        this.successful = result.isSuccessful();
        this.changed = result.wasChanged();
        this.partyDisbanded = result.wasPartyDisbanded();
        this.errorId = result.getErrorId();
        this.partyRevision = result.getParty() == null
                ? -1L : result.getParty().getRevision();
        this.stateFollows = stateFollows;
    }

    public PartyOperationResultPacket(int requestId,
                                      PartyOperationType operationType,
                                      PartyInvitationOperationResult result,
                                      boolean stateFollows) {
        if (operationType == null || result == null) {
            throw new IllegalArgumentException("operationType and result must not be null");
        }
        this.requestId = requestId;
        this.operationType = operationType;
        this.successful = result.isSuccessful();
        this.changed = result.wasChanged();
        this.partyDisbanded = false;
        this.errorId = result.getErrorId();
        this.partyRevision = result.getParty() == null
                ? -1L : result.getParty().getRevision();
        this.stateFollows = stateFollows;
    }

    public PartyOperationResultPacket(int requestId,
                                      PartyOperationType operationType,
                                      PartyErrorId errorId,
                                      long partyRevision,
                                      boolean stateFollows) {
        this.requestId = requestId;
        this.operationType = operationType == null
                ? PartyOperationType.UNKNOWN : operationType;
        this.successful = false;
        this.changed = false;
        this.partyDisbanded = false;
        this.errorId = errorId == null
                ? PartyErrorId.INTERNAL_ERROR : errorId;
        this.partyRevision = partyRevision;
        this.stateFollows = stateFollows;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.requestId = buffer.readInt();
            this.operationType = PartyOperationType.fromNetworkId(
                    buffer.readUnsignedByte());
            this.successful = buffer.readBoolean();
            this.changed = buffer.readBoolean();
            this.partyDisbanded = buffer.readBoolean();
            this.errorId = PartyErrorId.fromId(PartyPacketCodec.readString(
                    buffer, PartyPacketCodec.MAX_ERROR_ID_BYTES));
            this.partyRevision = buffer.readLong();
            this.stateFollows = buffer.readBoolean();
            PartyPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.operationType = PartyOperationType.UNKNOWN;
            this.errorId = PartyErrorId.INTERNAL_ERROR;
            this.successful = false;
            this.changed = false;
            this.partyDisbanded = false;
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
        buffer.writeBoolean(this.partyDisbanded);
        PartyPacketCodec.writeString(
                buffer, this.errorId.getId(), PartyPacketCodec.MAX_ERROR_ID_BYTES);
        buffer.writeLong(this.partyRevision);
        buffer.writeBoolean(this.stateFollows);
    }

    public PartyOperationFeedback toFeedback() {
        return new PartyOperationFeedback(
                this.requestId,
                this.operationType,
                this.successful,
                this.changed,
                this.partyDisbanded,
                this.errorId,
                this.partyRevision,
                this.stateFollows);
    }

    public int getRequestId() {
        return this.requestId;
    }

    public PartyOperationType getOperationType() {
        return this.operationType;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    private void validate() {
        if (this.operationType == null
                || (this.operationType == PartyOperationType.UNKNOWN
                    && (this.successful
                        || this.errorId != PartyErrorId.MALFORMED_REQUEST))
                || (this.successful && this.errorId != PartyErrorId.NONE)
                || (!this.successful && this.errorId == PartyErrorId.NONE)
                || (this.partyDisbanded && (!this.successful || !this.changed))
                || (this.partyRevision < -1L)) {
            throw new PartyPacketCodec.DecodeException("invalid party operation result");
        }
    }

    public static final class Handler implements IMessageHandler<PartyOperationResultPacket, IMessage> {
        @Override
        public IMessage onMessage(final PartyOperationResultPacket message,
                                  MessageContext context) {
            if (message == null) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handlePartyOperationResult(message);
                }
            });
            return null;
        }
    }
}
