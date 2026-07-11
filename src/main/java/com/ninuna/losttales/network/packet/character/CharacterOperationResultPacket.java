package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.character.server.CharacterOperationResult;
import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Structured result for one character-management request. */
public final class CharacterOperationResultPacket implements IMessage {

    private int requestId;
    private CharacterOperationType operationType = CharacterOperationType.UNKNOWN;
    private boolean successful;
    private boolean changed;
    private CharacterErrorId errorId = CharacterErrorId.INTERNAL_ERROR;
    private long rosterRevision = -1L;
    private boolean rosterFollows;
    private boolean malformed;

    public CharacterOperationResultPacket() {}

    public CharacterOperationResultPacket(int requestId,
                                          CharacterOperationType operationType,
                                          CharacterOperationResult result) {
        if (operationType == null || result == null) {
            throw new IllegalArgumentException("operationType and result must not be null");
        }
        this.requestId = requestId;
        this.operationType = operationType;
        this.successful = result.isSuccessful();
        this.changed = result.wasChanged();
        this.errorId = result.getErrorId();
        this.rosterRevision = result.getRosterRevision();
        this.rosterFollows = result.getRoster() != null;
    }

    public CharacterOperationResultPacket(int requestId,
                                          CharacterOperationType operationType,
                                          CharacterErrorId errorId,
                                          long rosterRevision) {
        this.requestId = requestId;
        this.operationType = operationType == null ? CharacterOperationType.UNKNOWN : operationType;
        this.successful = false;
        this.changed = false;
        this.errorId = errorId == null ? CharacterErrorId.INTERNAL_ERROR : errorId;
        this.rosterRevision = rosterRevision;
        this.rosterFollows = false;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.requestId = buffer.readInt();
            this.operationType = CharacterOperationType.fromNetworkId(buffer.readUnsignedByte());
            this.successful = buffer.readBoolean();
            this.changed = buffer.readBoolean();
            this.errorId = CharacterErrorId.fromId(CharacterPacketCodec.readString(
                    buffer, CharacterPacketCodec.MAX_ERROR_ID_BYTES));
            this.rosterRevision = buffer.readLong();
            this.rosterFollows = buffer.readBoolean();
            CharacterPacketCodec.requireFinished(buffer);

            if (this.operationType == CharacterOperationType.UNKNOWN
                    || (this.successful && this.errorId != CharacterErrorId.NONE)
                    || (!this.successful && this.errorId == CharacterErrorId.NONE)
                    || (!this.successful && this.changed)
                    || (this.successful && !this.rosterFollows)
                    || (this.rosterFollows && this.rosterRevision < 0L)) {
                throw new CharacterPacketCodec.DecodeException("invalid operation result");
            }
        } catch (RuntimeException exception) {
            this.operationType = CharacterOperationType.UNKNOWN;
            this.errorId = CharacterErrorId.INTERNAL_ERROR;
            this.successful = false;
            this.changed = false;
            this.malformed = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.requestId);
        buffer.writeByte(this.operationType.getNetworkId());
        buffer.writeBoolean(this.successful);
        buffer.writeBoolean(this.changed);
        CharacterPacketCodec.writeString(buffer, this.errorId.getId(), CharacterPacketCodec.MAX_ERROR_ID_BYTES);
        buffer.writeLong(this.rosterRevision);
        buffer.writeBoolean(this.rosterFollows);
    }

    public CharacterOperationFeedback toFeedback() {
        return new CharacterOperationFeedback(
                this.requestId,
                this.operationType,
                this.successful,
                this.changed,
                this.errorId,
                this.rosterRevision,
                this.rosterFollows
        );
    }

    public int getRequestId() {
        return this.requestId;
    }

    public CharacterOperationType getOperationType() {
        return this.operationType;
    }

    public boolean isSuccessful() {
        return this.successful;
    }

    public boolean wasChanged() {
        return this.changed;
    }

    public CharacterErrorId getErrorId() {
        return this.errorId;
    }

    public long getRosterRevision() {
        return this.rosterRevision;
    }

    public boolean isRosterFollows() {
        return this.rosterFollows;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler implements IMessageHandler<CharacterOperationResultPacket, IMessage> {
        @Override
        public IMessage onMessage(final CharacterOperationResultPacket message, MessageContext context) {
            if (message == null) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleCharacterOperationResult(message);
                }
            });
            return null;
        }
    }
}
