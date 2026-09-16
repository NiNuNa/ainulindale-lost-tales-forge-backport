package com.ninuna.losttales.client.chat;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Which server the chat on screen belongs to.
 *
 * <p>The game clears its own message list whenever the main menu opens,
 * so every trip out of a world or a server empties the chat; the lines
 * come back from the server's replay on the next join, filed under the
 * read marks. What outlives the trip is what Lost Tales knows about the
 * <em>server</em> — its channels, roles, identities, the open
 * conversations and the layout — and the session keeps that state as
 * long as the player comes back to the same place: the same server
 * address, or the same single-player world. Arriving anywhere else
 * drops it and starts clean.</p>
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

    /** Where the chat on screen came from, as {@link #resume} last read it; empty before a join. */
    public static synchronized String currentKey() {
        return serverKey;
    }

    /** Names where the chat came from directly; for tests. */
    static synchronized void resumeAt(String key) {
        serverKey = key == null ? "" : key;
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
