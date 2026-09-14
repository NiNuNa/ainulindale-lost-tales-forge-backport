package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatDeliveryMark;
import com.ninuna.losttales.chat.ChatMessageIds;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server-to-client: how the Discord post of a line this player said is
 * going — still waiting, or not posted and why — or that a clock shown
 * on it can go. Sent to the line's sender alone, at most twice for one
 * line.
 *
 * <p>Ten bytes: the message id, the state and the reason. A reason code
 * this build does not know reads as
 * {@link ChatDeliveryMark.Reason#UNKNOWN}, so new reasons can be
 * appended; a state it does not know is malformed.</p>
 */
public final class LostTalesChatDeliveryMarkPacket implements IMessage {
    /** The id, the state and the reason: every mark is exactly this long. */
    static final int PACKET_BYTES = 8 + 1 + 1;

    private long messageId = ChatMessageIds.NONE;
    private ChatDeliveryMark.State state = ChatDeliveryMark.State.NONE;
    private ChatDeliveryMark.Reason reason = ChatDeliveryMark.Reason.NONE;
    private boolean malformed;

    public LostTalesChatDeliveryMarkPacket() {}

    public LostTalesChatDeliveryMarkPacket(long messageId,
                                           ChatDeliveryMark.State state,
                                           ChatDeliveryMark.Reason reason) {
        if (!ChatMessageIds.isServerId(messageId) || state == null
                || reason == null
                || reason == ChatDeliveryMark.Reason.UNKNOWN) {
            throw new IllegalArgumentException("invalid chat delivery mark");
        }
        this.messageId = messageId;
        this.state = state;
        this.reason = reason;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() != PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat delivery mark size");
            }
            long id = buffer.readLong();
            ChatDeliveryMark.State decodedState =
                    ChatDeliveryMark.State.fromCode(buffer.readByte());
            ChatDeliveryMark.Reason decodedReason =
                    ChatDeliveryMark.Reason.fromCode(buffer.readUnsignedByte());
            LostTalesPacketCodec.requireFinished(buffer);
            if (!ChatMessageIds.isServerId(id) || decodedState == null) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat delivery mark");
            }
            this.messageId = id;
            this.state = decodedState;
            this.reason = decodedReason;
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.messageId = ChatMessageIds.NONE;
            this.state = ChatDeliveryMark.State.NONE;
            this.reason = ChatDeliveryMark.Reason.NONE;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(this.messageId);
        buffer.writeByte(this.state.code());
        buffer.writeByte(this.reason.code());
    }

    public long getMessageId() { return this.messageId; }
    public ChatDeliveryMark.State getState() { return this.state; }
    public ChatDeliveryMark.Reason getReason() { return this.reason; }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesChatDeliveryMarkPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatDeliveryMarkPacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleChatDeliveryMark(message);
                }
            });
            return null;
        }
    }
}
