package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.quest.LostTalesQuestIds;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * A giver offers the player a quest: the creature that speaks, by its
 * entity id, and the quest, by its id. The server sends it as the player
 * touches the giver, once the quest is one the client may know
 * ({@link com.ninuna.losttales.quest.ServerQuestSync}); the client opens
 * the conversation. Taking the offer is a request the start gate checks
 * again.
 */
public final class LostTalesQuestOfferPacket implements IMessage {
    private static final int MAX_PACKET_BYTES = 4 + 2 + LostTalesQuestIds.MAX_BYTES;

    private int entityId;
    private String questId = "";
    private boolean malformed;

    public LostTalesQuestOfferPacket() {}

    public LostTalesQuestOfferPacket(int entityId, String questId) {
        this.entityId = entityId;
        this.questId = questId == null ? "" : questId;
        if (!isValid()) {
            throw new IllegalArgumentException("invalid quest offer");
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.malformed = false;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid quest offer packet size");
            }
            this.entityId = buf.readInt();
            this.questId = LostTalesPacketCodec.readUtf8String(buf,
                    LostTalesQuestIds.MAX_BYTES);
            LostTalesPacketCodec.requireFinished(buf);
            if (!isValid()) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid quest offer");
            }
        } catch (RuntimeException exception) {
            this.entityId = 0;
            this.questId = "";
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (!isValid()) {
            throw new IllegalStateException("invalid quest offer");
        }
        buf.writeInt(this.entityId);
        LostTalesPacketCodec.writeUtf8String(buf, this.questId,
                LostTalesQuestIds.MAX_BYTES);
    }

    public int getEntityId() {
        return this.entityId;
    }

    public String getQuestId() {
        return this.questId;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    private boolean isValid() {
        return this.questId != null && this.questId.length() > 0
                && LostTalesQuestIds.fits(this.questId);
    }

    /** Hands the offer to the client thread; a malformed one is dropped. */
    public static final class Handler
            implements IMessageHandler<LostTalesQuestOfferPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesQuestOfferPacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()
                    || LostTalesMod.proxy == null) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleQuestOffer(message);
                }
            });
            return null;
        }
    }
}
