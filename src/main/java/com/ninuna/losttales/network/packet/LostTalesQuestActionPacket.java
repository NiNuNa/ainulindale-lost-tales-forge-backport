package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import com.ninuna.losttales.quest.LostTalesQuestManager;
import com.ninuna.losttales.quest.LostTalesQuestStartSource;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.Locale;

/** Client-to-server quest action request from the legacy 1.7.10 quest journal. */
public class LostTalesQuestActionPacket implements IMessage {
    public static final String ACTION_START = "start";
    public static final String ACTION_ABANDON = "abandon";
    public static final String ACTION_SCAN = "scan";
    public static final String ACTION_PIN = "pin";
    public static final String ACTION_UNPIN = "unpin";
    public static final String ACTION_REVEAL_MARKERS = "reveal_markers";
    public static final String ACTION_PIN_MARKER = "pin_marker";
    public static final String ACTION_UNPIN_MARKER = "unpin_marker";

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
        return ACTION_START.equals(action)
                || ACTION_ABANDON.equals(action)
                || ACTION_SCAN.equals(action)
                || ACTION_PIN.equals(action)
                || ACTION_UNPIN.equals(action)
                || ACTION_REVEAL_MARKERS.equals(action)
                || ACTION_PIN_MARKER.equals(action)
                || ACTION_UNPIN_MARKER.equals(action);
    }

    private static boolean hasValidIdentifierUsage(String action, String identifier) {
        if (ACTION_START.equals(action)
                || ACTION_ABANDON.equals(action)
                || ACTION_PIN.equals(action)
                || ACTION_REVEAL_MARKERS.equals(action)
                || ACTION_PIN_MARKER.equals(action)) {
            return identifier.length() > 0;
        }
        return true;
    }

    private static void execute(EntityPlayerMP player, String action, String questId) {
        if (ACTION_START.equals(action)) {
            LostTalesQuestManager.startQuest(player, questId, LostTalesQuestStartSource.JOURNAL);
        } else if (ACTION_ABANDON.equals(action)) {
            LostTalesQuestManager.abandonQuest(player, questId);
        } else if (ACTION_SCAN.equals(action)) {
            LostTalesQuestManager.refreshGatherProgressFromInventory(player);
        } else if (ACTION_PIN.equals(action)) {
            LostTalesQuestManager.pinQuest(player, questId);
        } else if (ACTION_UNPIN.equals(action)) {
            if (questId.length() > 0) {
                LostTalesQuestManager.unpinQuest(player, questId);
            } else {
                LostTalesQuestManager.unpinQuest(player);
            }
        } else if (ACTION_REVEAL_MARKERS.equals(action)) {
            LostTalesQuestPlayerData data = LostTalesQuestManager.getPlayerData(player);
            if (data != null && data.isQuestActive(questId)) {
                LostTalesQuestManager.revealQuestMarkers(player, questId);
            }
        } else if (ACTION_PIN_MARKER.equals(action)) {
            LostTalesQuestManager.pinMapMarker(player, questId);
        } else if (ACTION_UNPIN_MARKER.equals(action)) {
            LostTalesQuestManager.unpinMapMarker(player);
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
