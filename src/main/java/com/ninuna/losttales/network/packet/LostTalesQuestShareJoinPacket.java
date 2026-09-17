package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.server.ChatHistory;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.compat.lotr.LotrQuestReference;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyMember;
import com.ninuna.losttales.party.server.PartyService;
import com.ninuna.losttales.quest.LostTalesQuestManager;
import com.ninuna.losttales.quest.LostTalesQuestStartSource;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

/** Requests the independent copy advertised by one recorded party quest card. */
public final class LostTalesQuestShareJoinPacket implements IMessage {
    private long messageId;
    private int tokenIndex;
    private boolean malformed;

    public LostTalesQuestShareJoinPacket() {}

    public LostTalesQuestShareJoinPacket(long messageId, int tokenIndex) {
        this.messageId = messageId;
        this.tokenIndex = tokenIndex;
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.messageId = buffer.readLong();
            this.tokenIndex = buffer.readUnsignedByte();
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        buffer.writeLong(this.messageId);
        buffer.writeByte(this.tokenIndex);
    }

    private void validate() {
        if (!ChatMessageIds.isServerId(this.messageId)
                || this.tokenIndex < 0
                || this.tokenIndex >= ChatShareTokenParser.MAX_TOKENS) {
            throw new IllegalArgumentException("invalid quest share join");
        }
    }

    private static void execute(EntityPlayerMP player, long messageId,
                                int tokenIndex) {
        ChatHistory.QuestShareClaim claim = ChatHistory.questShareFor(
                messageId, tokenIndex,
                LostTalesChatService.historyRequesterFor(player));
        Party party = PartyService.getInstance()
                .getPartyForActiveCharacter(player);
        if (claim == null || claim.authorId == null || party == null
                || !containsOwner(party, claim.authorId)
                || LotrQuestReference.isLotrQuest(
                        claim.showcase.getQuestReference())) {
            player.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.quest.join_unavailable"));
            return;
        }
        LostTalesQuestManager.startQuest(player,
                claim.showcase.getQuestReference(),
                LostTalesQuestStartSource.SHARED);
    }

    private static boolean containsOwner(Party party,
                                         java.util.UUID ownerId) {
        for (PartyMember member : party.getMembers()) {
            if (ownerId.equals(member.getOwnerId())) return true;
        }
        return false;
    }

    public static final class Handler implements IMessageHandler<
            LostTalesQuestShareJoinPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesQuestShareJoinPacket message,
                                  MessageContext context) {
            EntityPlayerMP player = LostTalesServerPacketDispatcher
                    .getPlayer(context);
            if (player == null || message == null) return null;
            LostTalesServerPacketDispatcher.submit(player,
                    LostTalesRequestRateLimiter.RequestType.QUEST_ACTION,
                    message.malformed, "LostTalesQuestShareJoinPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            execute(livePlayer, message.messageId,
                                    message.tokenIndex);
                        }
                    });
            return null;
        }
    }
}
