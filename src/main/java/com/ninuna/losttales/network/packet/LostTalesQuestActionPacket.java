package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import com.ninuna.losttales.quest.LostTalesQuestManager;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.Locale;

/** Client-to-server tracking request from the unified quest journal. */
public class LostTalesQuestActionPacket implements IMessage {
    public static final String ACTION_PIN = "pin";
    public static final String ACTION_UNPIN = "unpin";
    /**
     * Giving a quest up. The client names the quest; the server decides
     * whether it is one this player is on and one that may be given up
     * at all, so a forged id abandons nothing.
     */
    public static final String ACTION_ABANDON = "abandon";
    /**
     * Taking a quest that was offered in conversation. The client names
     * the quest; the server checks that somebody the quest names as its
     * giver is within reach and that the quest may be started that way,
     * so a client that never spoke to anybody starts nothing.
     */
    public static final String ACTION_ACCEPT = "accept";
    /**
     * Giving over what a quest asked for, in conversation. Checked the
     * same way, and it moves only the objectives of the quest named.
     */
    public static final String ACTION_HAND_IN = "hand_in";

    private String action = "";
    private String questId = "";
    private boolean malformed;

    public LostTalesQuestActionPacket() {}

    public LostTalesQuestActionPacket(String action, String questId) {
        this.action = action == null ? "" : action;
        this.questId = questId == null ? "" : questId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.action = normalizeAction(LostTalesPacketCodec.readUtf8String(
                    buffer, LostTalesPacketCodec.MAX_ACTION_BYTES));
            this.questId = normalizeIdentifier(LostTalesPacketCodec.readUtf8String(
                    buffer, LostTalesPacketCodec.MAX_IDENTIFIER_BYTES));
            LostTalesPacketCodec.requireFinished(buffer);
            if (!isKnownAction(this.action) || !hasValidIdentifierUsage(this.action, this.questId)) {
                throw new LostTalesPacketCodec.DecodeException("invalid quest action request");
            }
        } catch (RuntimeException exception) {
            this.malformed = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.action == null ? "" : this.action,
                LostTalesPacketCodec.MAX_ACTION_BYTES);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.questId == null ? "" : this.questId,
                LostTalesPacketCodec.MAX_IDENTIFIER_BYTES);
    }

    private static String normalizeAction(String action) {
        return action == null ? "" : action.trim().toLowerCase(Locale.ENGLISH);
    }

    private static String normalizeIdentifier(String identifier) {
        return identifier == null ? "" : identifier.trim();
    }

    private static boolean isKnownAction(String action) {
        return ACTION_PIN.equals(action) || ACTION_UNPIN.equals(action)
                || ACTION_ABANDON.equals(action)
                || ACTION_ACCEPT.equals(action)
                || ACTION_HAND_IN.equals(action);
    }

    private static boolean hasValidIdentifierUsage(String action, String identifier) {
        if (ACTION_PIN.equals(action) || ACTION_ABANDON.equals(action)
                || ACTION_ACCEPT.equals(action)
                || ACTION_HAND_IN.equals(action)) {
            return identifier.length() > 0;
        }
        return true;
    }

    private static void execute(EntityPlayerMP player, String action, String questId) {
        if (ACTION_PIN.equals(action)) {
            LostTalesQuestManager.pinQuest(player, questId);
        } else if (ACTION_UNPIN.equals(action)) {
            if (questId.length() > 0) {
                LostTalesQuestManager.unpinQuest(player, questId);
            } else {
                LostTalesQuestManager.unpinQuest(player);
            }
        } else if (ACTION_ABANDON.equals(action)) {
            LostTalesQuestManager.abandonOwnQuest(player, questId);
        } else if (ACTION_ACCEPT.equals(action)) {
            LostTalesQuestManager.acceptFromConversation(player, questId);
        } else if (ACTION_HAND_IN.equals(action)) {
            LostTalesQuestManager.handInFromConversation(player, questId);
        }
    }

    public static class Handler implements IMessageHandler<LostTalesQuestActionPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesQuestActionPacket message, MessageContext context) {
            EntityPlayerMP player = LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }

            final String action = message.action;
            final String questId = message.questId;
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType.QUEST_ACTION,
                    message.malformed,
                    "LostTalesQuestActionPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            execute(livePlayer, action, questId);
                        }
                    }
            );
            return null;
        }
    }
}
