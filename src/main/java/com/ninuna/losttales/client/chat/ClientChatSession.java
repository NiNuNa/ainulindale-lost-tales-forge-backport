package com.ninuna.losttales.client.chat;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Which server the chat history on screen belongs to.
 *
 * <p>Minecraft keeps its own chat history for as long as the game is
 * running — {@code GuiIngame} and its {@code GuiNewChat} are built once —
 * so leaving a server and coming back does not lose the messages. What
 * used to be lost with them was everything Lost Tales knows <em>about</em>
 * those messages: which tab each line belongs to, the open conversations,
 * the scroll offsets. Rejoining therefore looked like an empty chat even
 * though the lines were still there.</p>
 *
 * <p>The session keeps that state instead, and drops it only when the
 * player arrives somewhere else: a different server address, or a
 * different single-player world. That is as close to "until the server
 * restarts" as a client can get on its own — the server never tells a
 * client it was restarted — so a restart while this client stays running
 * leaves the old messages in the history until the client is closed.</p>
 */
public final class ClientChatSession {
    /** Where the chat currently on screen came from; empty before a join. */
    private static String serverKey = "";

    private ClientChatSession() {}

    /**
     * Called as the client finishes connecting. Returns true when this is
     * the same place the chat on screen came from, in which case the
     * history, its tabs and its conversations are kept; false when the
     * player has arrived somewhere else and the chat starts clean.
     */
    public static synchronized boolean resume(Minecraft minecraft) {
        String key = keyOf(minecraft);
        boolean same = key.length() > 0 && key.equals(serverKey);
        serverKey = key;
        return same;
    }

    /** Forgets the session, so the next join starts a clean chat. */
    public static synchronized void forget() {
        serverKey = "";
    }

    /**
     * A stable name for where the client is: the server address for
     * multiplayer, the save folder for single player. Empty when neither
     * can be read, which starts a clean chat rather than guessing.
     */
    private static String keyOf(Minecraft minecraft) {
        if (minecraft == null) {
            return "";
        }
        ServerData server = minecraft.func_147104_D();
        if (server != null && server.serverIP != null
                && server.serverIP.trim().length() > 0) {
            return "server:" + server.serverIP.trim()
                    .toLowerCase(Locale.ROOT);
        }
        if (minecraft.isSingleplayer()
                && minecraft.getIntegratedServer() != null) {
            String folder = minecraft.getIntegratedServer().getFolderName();
            if (folder != null && folder.trim().length() > 0) {
                return "world:" + folder.trim();
            }
        }
        return "";
    }
}
