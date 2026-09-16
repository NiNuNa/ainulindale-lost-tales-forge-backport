package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatPresencePacket;
import com.ninuna.losttales.network.packet.LostTalesChatPresenceSyncPacket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Presence as this client knows it: everyone's, as the server states
 * it, and this player's own choice. The choice is made in the head
 * button's menu and told to the server; Away also sets in on its own
 * after idle time ({@link ChatAutoAway}) and lifts on the first key,
 * click, mouse movement or step, which is sampled once a tick. While
 * Do Not Disturb is chosen the mention cue stays silent here; mentions
 * still count and tint. Cleared with the rest of the session.
 */
public final class ClientChatPresence {
    private static final Map<UUID, ChatPresence> OTHERS =
            new HashMap<UUID, ChatPresence>();
    private static ChatPresence chosen = ChatPresence.ONLINE;
    private static boolean autoAway;
    private static long lastActivityNanos;
    /** Whether a first sample has been taken; the first one is no activity. */
    private static boolean sampled;
    private static int mouseX;
    private static int mouseY;
    private static double posX;
    private static double posY;
    private static double posZ;
    private static float yaw;
    private static float pitch;

    private ClientChatPresence() {}

    /** The server's word on someone's presence, on the client thread. */
    public static synchronized void accept(LostTalesChatPresenceSyncPacket packet) {
        if (packet == null || packet.isMalformed()) {
            return;
        }
        for (Map.Entry<UUID, ChatPresence> entry : packet.getEntries().entrySet()) {
            if (entry.getValue() == ChatPresence.ONLINE) {
                OTHERS.remove(entry.getKey());
            } else {
                OTHERS.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /** An account's presence; Online for anyone the server said nothing of. */
    public static synchronized ChatPresence presenceOf(UUID account) {
        ChatPresence presence = account == null ? null : OTHERS.get(account);
        return presence == null ? ChatPresence.ONLINE : presence;
    }

    /** The presence this player chose. */
    public static synchronized ChatPresence chosen() {
        return chosen;
    }

    /** Whether this player asked not to be disturbed: no mention cue sounds. */
    public static synchronized boolean isDoNotDisturb() {
        return chosen == ChatPresence.DO_NOT_DISTURB;
    }

    /**
     * Chooses a presence by hand and tells the server. A choice ends an
     * Away that idle time set, whichever way it goes.
     */
    public static synchronized void choose(ChatPresence presence) {
        if (presence == null) {
            return;
        }
        chosen = presence;
        autoAway = false;
        lastActivityNanos = System.nanoTime();
        send(presence);
    }

    /** The presence the server is told: Away while idle time set it, else the choice. */
    static synchronized ChatPresence stated() {
        return autoAway ? ChatPresence.AWAY : chosen;
    }

    /**
     * Once a client tick: samples whether the player did anything, and
     * lets Away set in and lift by {@link ChatAutoAway}'s rule.
     */
    public static void onClientTick(Minecraft minecraft) {
        if (minecraft == null || minecraft.thePlayer == null
                || minecraft.theWorld == null) {
            return;
        }
        ChatPresence tell = null;
        synchronized (ClientChatPresence.class) {
            boolean active = sample(minecraft.thePlayer);
            long now = System.nanoTime();
            ChatAutoAway.Change change = ChatAutoAway.step(active, now,
                    lastActivityNanos, chosen, autoAway);
            if (active) {
                lastActivityNanos = now;
            }
            if (change == ChatAutoAway.Change.GO_AWAY) {
                autoAway = true;
                tell = ChatPresence.AWAY;
            } else if (change == ChatAutoAway.Change.COME_BACK) {
                autoAway = false;
                tell = chosen;
            }
        }
        if (tell != null) {
            send(tell);
        }
    }

    /**
     * Whether anything happened since the last sample: the mouse moved,
     * a button or a key is down, or the player moved or turned. The
     * first sample after a session starts only takes the bearings.
     */
    private static boolean sample(EntityPlayer player) {
        boolean active = false;
        boolean first = !sampled;
        sampled = true;
        if (Mouse.isCreated()) {
            int x = Mouse.getX();
            int y = Mouse.getY();
            if (x != mouseX || y != mouseY) {
                active = !first;
                mouseX = x;
                mouseY = y;
            }
            active |= Mouse.isButtonDown(0) || Mouse.isButtonDown(1)
                    || Mouse.isButtonDown(2);
        }
        if (player.posX != posX || player.posY != posY || player.posZ != posZ
                || player.rotationYaw != yaw || player.rotationPitch != pitch) {
            active |= !first;
            posX = player.posX;
            posY = player.posY;
            posZ = player.posZ;
            yaw = player.rotationYaw;
            pitch = player.rotationPitch;
        }
        return active || anyKeyDown();
    }

    private static boolean anyKeyDown() {
        if (!Keyboard.isCreated()) {
            return false;
        }
        for (int key = 1; key < 256; key++) {
            if (Keyboard.isKeyDown(key)) {
                return true;
            }
        }
        return false;
    }

    private static void send(ChatPresence presence) {
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesChatPresencePacket(presence));
    }

    /** Forgets everyone's presence and this player's choice. */
    public static synchronized void clear() {
        OTHERS.clear();
        chosen = ChatPresence.ONLINE;
        autoAway = false;
        lastActivityNanos = 0L;
        sampled = false;
    }
}
