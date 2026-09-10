package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatBroadcastIdMarkers;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.ChatSystemLineClassifier;
import com.ninuna.losttales.compat.discord.DiscordGameEventRelay;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import cpw.mods.fml.common.FMLLog;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * Where every server-wide line passes on its way out — an achievement,
 * a death, a join or a leave, {@code /say} — patched by the coremod
 * into the head of {@code ServerConfigurationManager.sendChatMsg}. Two
 * things happen to it here: the Discord relay is told, and a line
 * that will land in Global is given a message id of the server's own,
 * carried as an empty run on the component and recorded in the chat
 * history under the Server's name, so a reply to it on any client
 * names the same message and a click on the quote finds it. The words
 * are never changed, and a line is never delayed or refused: whatever
 * fails, the component goes out as it came.
 */
public final class LostTalesServerBroadcastHook {
    private static volatile boolean failureLogged;

    private LostTalesServerBroadcastHook() {}

    /**
     * Sees a line about to be broadcast and hands back the one to send:
     * the same component, with an id run appended when the line is
     * Global's. The relay is told first, of the line as it came.
     */
    public static IChatComponent onBroadcast(IChatComponent message) {
        if (message == null) {
            return null;
        }
        try {
            DiscordGameEventRelay.onServerBroadcast(message);
        } catch (Throwable throwable) {
            logOnce("relay", throwable);
        }
        try {
            if (ChatSystemLineClassifier.classify(message) == ChatChannel.ALL) {
                stamp(message);
            }
        } catch (Throwable throwable) {
            logOnce("name", throwable);
        }
        return message;
    }

    /**
     * Gives the line an id and records it for everyone online: they are
     * the ones who can be shown it, so they are the ones who may reply
     * to it by that id. The history keeps the words alone, cleaned as a
     * message is, under the Server's name in the Console's colour.
     */
    private static void stamp(IChatComponent message) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return;
        }
        String text = ChatMessageValidator.cleaned(message.getUnformattedText());
        if (text.length() == 0) {
            return;
        }
        long messageId = ChatMessageIdAllocator.next();
        List<UUID> recipients = new ArrayList<UUID>();
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online = server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : online) {
            if (player != null && player.getUniqueID() != null) {
                recipients.add(player.getUniqueID());
            }
        }
        LostTalesChatMessagePacket record = new LostTalesChatMessagePacket(
                ChatChannel.ALL, LostTalesChatMessagePacket.SERVER_SENDER_ID,
                SERVER_NAME, SERVER_NAME, "",
                LostTalesColors.rgb(LostTalesColors.HUD_LABEL),
                ChatChannel.CONSOLE.getDisplayColor(), text,
                System.currentTimeMillis(), "", null, "", "", 0, true,
                messageId, ChatReplyReference.NONE, "");
        ChatHistory.record(messageId, LostTalesChatMessagePacket.SERVER_SENDER_ID,
                SERVER_NAME, null, record, recipients,
                ChatHistory.Audience.everyone());
        ChatComponentText mark = new ChatComponentText("");
        mark.setChatStyle(mark.getChatStyle().setChatClickEvent(new ClickEvent(
                ClickEvent.Action.SUGGEST_COMMAND,
                ChatBroadcastIdMarkers.value(messageId))));
        message.appendSibling(mark);
    }

    /** The name the server's lines are recorded under; the client shows its own word for it. */
    static final String SERVER_NAME = "Server";

    private static void logOnce(String what, Throwable throwable) {
        if (!failureLogged) {
            failureLogged = true;
            FMLLog.warning("[LostTales] Could not %s a server broadcast: %s",
                    what, throwable);
        }
    }
}
