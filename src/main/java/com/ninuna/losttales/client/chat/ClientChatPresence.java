package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.chat.ChatStatusLine;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatPresencePacket;
import com.ninuna.losttales.network.packet.LostTalesChatPresenceSyncPacket;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Presence as this client knows it: what the server says each account's
 * identities show, with their status lines, and what this player chose
 * and set for each of its own. A choice is made in the head button's
 * menu — for the character spoken as on a roleplaying tab, for the
 * account on the others — remembered for the server it was made on
 * ({@link ClientChatPresenceChoices}) and told to the server, which
 * decides what everyone else sees of it; a status line alike. Whether
 * anybody is at the keyboard is sampled once a tick ({@link ChatAutoAway})
 * and told as it changes. Do Not Disturb holds the mention cue silent in
 * the tabs that speak as the identity it was chosen for; mentions still
 * count and tint.
 */
public final class ClientChatPresence {
    /** What the server said each account's identities show; what it left out is Offline. */
    private static final Map<UUID, Map<ChatPresenceIdentity, ChatPresence>> SHOWN =
            new HashMap<UUID, Map<ChatPresenceIdentity, ChatPresence>>();
    /** This player's own choices on the server it is on; Online is no entry. */
    private static final Map<ChatPresenceIdentity, ChatPresence> CHOSEN =
            new LinkedHashMap<ChatPresenceIdentity, ChatPresence>();
    /** The status lines the server says each shown identity has. */
    private static final Map<UUID, Map<ChatPresenceIdentity, String>> SHOWN_LINES =
            new HashMap<UUID, Map<ChatPresenceIdentity, String>>();
    /** This player's own status lines on the server it is on; none is no entry. */
    private static final Map<ChatPresenceIdentity, String> CHOSEN_LINES =
            new LinkedHashMap<ChatPresenceIdentity, String>();
    private static String serverKey = "";
    /** Whether the choices still wait to be told to the server just joined. */
    private static boolean statePending;
    private static boolean idle;
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

    /**
     * Takes up the choices this player made on the server just joined,
     * to be told once the player is in the world. What anybody showed on
     * the last server is forgotten: the new one says it afresh.
     */
    public static synchronized void beginSession(String key) {
        SHOWN.clear();
        CHOSEN.clear();
        SHOWN_LINES.clear();
        CHOSEN_LINES.clear();
        serverKey = key == null ? "" : key;
        CHOSEN.putAll(ClientChatPresenceChoices.forPlace(serverKey));
        CHOSEN_LINES.putAll(ClientChatPresenceChoices.linesForPlace(serverKey));
        statePending = true;
        idle = false;
        sampled = false;
        lastActivityNanos = System.nanoTime();
    }

    /** The server's word on what some accounts show, on the client thread. */
    public static synchronized void accept(LostTalesChatPresenceSyncPacket packet) {
        if (packet == null || packet.isMalformed()) {
            return;
        }
        for (Map.Entry<UUID, Map<ChatPresenceIdentity, ChatPresence>> account
                : packet.getAccounts().entrySet()) {
            Map<ChatPresenceIdentity, String> lines =
                    packet.getLines().get(account.getKey());
            if (account.getValue().isEmpty()) {
                SHOWN.remove(account.getKey());
                SHOWN_LINES.remove(account.getKey());
            } else {
                SHOWN.put(account.getKey(),
                        new HashMap<ChatPresenceIdentity, ChatPresence>(
                                account.getValue()));
                SHOWN_LINES.put(account.getKey(), lines == null
                        ? new HashMap<ChatPresenceIdentity, String>()
                        : new HashMap<ChatPresenceIdentity, String>(lines));
            }
        }
    }

    /**
     * The status line an identity of an account shows, cleaned; empty for
     * none, and for an identity the server says nothing of.
     */
    public static synchronized String lineOf(UUID account,
                                             ChatPresenceIdentity identity) {
        Map<ChatPresenceIdentity, String> lines =
                account == null ? null : SHOWN_LINES.get(account);
        String line = lines == null || identity == null ? null
                : lines.get(identity);
        return line == null ? "" : ChatStatusLine.clean(line);
    }

    /** The status line this player set for one of its identities; empty for none. */
    public static synchronized String chosenLine(ChatPresenceIdentity identity) {
        String line = identity == null ? null : CHOSEN_LINES.get(identity);
        return line == null ? "" : line;
    }

    /**
     * Sets the status line of one of this player's identities: remembered
     * for this server and told to the server. An empty line clears it.
     */
    public static void setLine(ChatPresenceIdentity identity, String line) {
        if (identity == null) {
            return;
        }
        String cleaned = ChatStatusLine.clean(line);
        String key;
        synchronized (ClientChatPresence.class) {
            if (cleaned.length() == 0) {
                CHOSEN_LINES.remove(identity);
            } else {
                CHOSEN_LINES.put(identity, cleaned);
            }
            key = serverKey;
        }
        ClientChatPresenceChoices.rememberLine(key, identity, cleaned);
        send();
    }

    /**
     * What an identity of an account shows: Offline for one the server
     * says nothing of — an identity not in use, one hidden, or an account
     * that is not here.
     */
    public static synchronized ChatPresence presenceOf(UUID account,
                                                       ChatPresenceIdentity identity) {
        Map<ChatPresenceIdentity, ChatPresence> shown =
                account == null ? null : SHOWN.get(account);
        ChatPresence presence = shown == null || identity == null ? null
                : shown.get(identity);
        return presence == null ? ChatPresence.OFFLINE : presence;
    }

    /** The status this player chose for one of its identities. */
    public static synchronized ChatPresence chosen(ChatPresenceIdentity identity) {
        ChatPresence presence = identity == null ? null : CHOSEN.get(identity);
        return presence == null ? ChatPresence.ONLINE : presence;
    }

    /**
     * Chooses a status for one of this player's identities: remembered
     * for this server and told to the server. A choice is activity, so it
     * ends an idle spell too.
     */
    public static void choose(ChatPresenceIdentity identity,
                              ChatPresence presence) {
        if (identity == null || presence == null || !presence.isChoosable()) {
            return;
        }
        String key;
        synchronized (ClientChatPresence.class) {
            if (presence == ChatPresence.ONLINE) {
                CHOSEN.remove(identity);
            } else {
                CHOSEN.put(identity, presence);
            }
            idle = false;
            lastActivityNanos = System.nanoTime();
            key = serverKey;
        }
        ClientChatPresenceChoices.remember(key, identity, presence);
        send();
    }

    /**
     * The identity a tab speaks as, whose status its menu sets: the chat
     * identity on a roleplaying tab, the account on every other.
     */
    static ChatPresenceIdentity speakerOf(ChatTab tab) {
        ClientChatIdentities.Identity identity =
                ClientChatIdentities.effectiveFor(tab);
        return identity == null || identity.account
                ? ChatPresenceIdentity.ACCOUNT
                : ChatPresenceIdentity.character(identity.characterId);
    }

    /** Whether the mention cue stays silent in a tab: its speaker is not to be disturbed. */
    static boolean holdsCues(ChatTab tab) {
        return chosen(speakerOf(tab)) == ChatPresence.DO_NOT_DISTURB;
    }

    /**
     * Once a client tick: tells the server this player's choices the
     * first time the player stands in the world, samples whether the
     * player did anything, and goes idle and comes back by
     * {@link ChatAutoAway}'s rule.
     */
    public static void onClientTick(Minecraft minecraft) {
        if (minecraft == null || minecraft.thePlayer == null
                || minecraft.theWorld == null) {
            return;
        }
        boolean tell;
        synchronized (ClientChatPresence.class) {
            tell = statePending;
            statePending = false;
            boolean active = sample(minecraft.thePlayer);
            long now = System.nanoTime();
            ChatAutoAway.Change change = ChatAutoAway.step(active, now,
                    lastActivityNanos, idle);
            if (active) {
                lastActivityNanos = now;
            }
            if (change == ChatAutoAway.Change.GO_IDLE) {
                idle = true;
                tell = true;
            } else if (change == ChatAutoAway.Change.COME_BACK) {
                idle = false;
                tell = true;
            }
        }
        if (tell) {
            send();
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

    /** Tells the server this player's whole presence as it stands. */
    private static void send() {
        LostTalesChatPresencePacket packet;
        synchronized (ClientChatPresence.class) {
            packet = new LostTalesChatPresencePacket(idle,
                    new LinkedHashMap<ChatPresenceIdentity, ChatPresence>(CHOSEN),
                    new LinkedHashMap<ChatPresenceIdentity, String>(CHOSEN_LINES));
        }
        LostTalesNetworkHandler.CHANNEL.sendToServer(packet);
    }

    /** Forgets everyone's presence and this player's choices; the remembered ones stay on file. */
    public static synchronized void clear() {
        SHOWN.clear();
        CHOSEN.clear();
        SHOWN_LINES.clear();
        CHOSEN_LINES.clear();
        serverKey = "";
        statePending = false;
        idle = false;
        lastActivityNanos = 0L;
        sampled = false;
    }
}
