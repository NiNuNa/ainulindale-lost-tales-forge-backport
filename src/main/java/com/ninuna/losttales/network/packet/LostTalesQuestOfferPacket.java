package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.quest.LostTalesQuestIds;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * A giver offers the player a quest, or somebody tells its rumour
 * (QUEST-20): the creature that speaks, by its entity id, the quest, by
 * its id, and which of the two it is. The server sends it as the player
 * touches them, once the quest is one the client may know
 * ({@link com.ninuna.losttales.quest.ServerQuestSync}); the client opens
 * the conversation, or says the rumour. Taking an offer is a request the
 * start gate checks again.
 */
public final class LostTalesQuestOfferPacket implements IMessage {
    private static final int MAX_PACKET_BYTES = 4 + 2 + LostTalesQuestIds.MAX_BYTES + 1;

    private int entityId;
    private String questId = "";
    private boolean rumour;
    private boolean malformed;

    public LostTalesQuestOfferPacket() {}

    public LostTalesQuestOfferPacket(int entityId, String questId) {
        this(entityId, questId, false);
    }

    /** An offer, or with {@code rumour} the quest's rumour told. */
    public LostTalesQuestOfferPacket(int entityId, String questId, boolean rumour) {
        this.entityId = entityId;
        this.rumour = rumour;
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
            this.rumour = buf.readBoolean();
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
        buf.writeBoolean(this.rumour);
    }

    /** Whether it tells the quest's rumour rather than offering the quest. */
    public boolean isRumour() {
        return this.rumour;
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
