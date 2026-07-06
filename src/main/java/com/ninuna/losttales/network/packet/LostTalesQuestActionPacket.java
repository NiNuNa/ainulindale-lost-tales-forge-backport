package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.quest.LostTalesQuestManager;
import com.ninuna.losttales.quest.LostTalesQuestStartSource;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

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

    public LostTalesQuestActionPacket() {}

    public LostTalesQuestActionPacket(String action, String questId) {
        this.action = action == null ? "" : action;
        this.questId = questId == null ? "" : questId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = ByteBufUtils.readUTF8String(buf);
        this.questId = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.action == null ? "" : this.action);
        ByteBufUtils.writeUTF8String(buf, this.questId == null ? "" : this.questId);
    }

    public static class Handler implements IMessageHandler<LostTalesQuestActionPacket, IMessage> {
        @Override
        public IMessage onMessage(LostTalesQuestActionPacket message, MessageContext ctx) {
            if (ctx == null || ctx.getServerHandler() == null || ctx.getServerHandler().playerEntity == null || message == null) {
                return null;
            }

            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            String action = message.action == null ? "" : message.action.trim().toLowerCase();
            String questId = message.questId == null ? "" : message.questId.trim();

            if (ACTION_START.equals(action) && questId.length() > 0) {
                LostTalesQuestManager.startQuest(player, questId, LostTalesQuestStartSource.JOURNAL);
            } else if (ACTION_ABANDON.equals(action) && questId.length() > 0) {
                LostTalesQuestManager.abandonQuest(player, questId);
            } else if (ACTION_SCAN.equals(action)) {
                LostTalesQuestManager.refreshGatherProgressFromInventory(player);
            } else if (ACTION_PIN.equals(action) && questId.length() > 0) {
                LostTalesQuestManager.pinQuest(player, questId);
            } else if (ACTION_UNPIN.equals(action)) {
                LostTalesQuestManager.unpinQuest(player);
            } else if (ACTION_REVEAL_MARKERS.equals(action) && questId.length() > 0) {
                LostTalesQuestManager.revealQuestMarkers(player, questId);
            } else if (ACTION_PIN_MARKER.equals(action) && questId.length() > 0) {
                LostTalesQuestManager.pinMapMarker(player, questId);
            } else if (ACTION_UNPIN_MARKER.equals(action)) {
                LostTalesQuestManager.unpinMapMarker(player);
            }
            return null;
        }
    }
}
