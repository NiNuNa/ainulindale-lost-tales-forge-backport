package com.ninuna.losttales.network.server;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Per-player request windows and throttled diagnostics for legacy C2S packets. */
public final class LostTalesRequestRateLimiter {

    private static final long LOG_INTERVAL_MILLIS = 10000L;
    private static final ConcurrentMap<RequestKey, RequestWindow> WINDOWS =
            new ConcurrentHashMap<RequestKey, RequestWindow>();

    private LostTalesRequestRateLimiter() {}

    public enum RequestType {
        // The HUD refreshes every 250 ms (20 requests per five seconds) in normal use.
        QUICK_LOOT_SNAPSHOT(30, 5000L),
        QUICK_LOOT_MUTATION(20, 5000L),
        QUEST_ACTION(30, 5000L),
        MISSIVE_ACCEPT(10, 5000L),
        PARTY_SNAPSHOT(20, 5000L),
        PARTY_MUTATION(12, 5000L),
        // Allows ordinary high-rate clicking while bounding custom-packet floods.
        THIRD_PERSON_ENTITY_ACTION(120, 5000L),
        THIRD_PERSON_BLOCK_ACTION(120, 5000L),
        // Ranged aim is synchronized every two client ticks while active.
        THIRD_PERSON_AIM(70, 5000L),
        WAYSTONE_SETTINGS(20, 5000L),
        WAYSTONE_TRAVEL(6, 5000L),
        // Normal conversation can be fast, but sustained packet floods are bounded.
        CHAT_MESSAGE(20, 5000L),
        // One ahead of every command a player sends; commands are typed
        // no faster than messages.
        CHAT_COMMAND_CONTEXT(20, 5000L),
        // A typing client repeats itself every 2.5 s and sends one stop:
        // three in five seconds in normal use.
        CHAT_TYPING(8, 5000L),
        // Correcting a typo or taking a line back is deliberate and rare
        // next to sending; a handful in five seconds covers a fumbled edit.
        CHAT_REVISION(10, 5000L),
        // A reaction is one click on a chip or one pick in the picker;
        // twenty in five seconds covers a burst of clicking through a
        // message's chips and back.
        CHAT_REACTION(20, 5000L),
        // A report is chosen from a menu and a reason; three in five
        // seconds is already more than a hand does. The five in ten
        // minutes a player may file is the report rules' own
        // (ChatReports).
        CHAT_REPORT(3, 5000L),
        // One ask per conversation the first time it is read, and only
        // for a channel that has more than one; a handful covers a
        // player moving between their characters.
        CHAT_HISTORY(6, 5000L),
        // Selecting a character can replay two conversations; bound rapid cycling.
        CHAT_IDENTITY(6, 5000L),
        // A presence is chosen by hand now and then, and Away comes and
        // goes with idle time; a handful in five seconds covers both.
        CHAT_PRESENCE(6, 5000L),
        // A window whose member list stands asks for its channel's members
        // every four seconds, and at once when its tab, the chat's
        // identity or the player's own status changes; the chat asks for
        // up to eight lists ahead as it opens. A few windows doing so, and
        // a player walking through tabs, stay inside twenty in five
        // seconds.
        CHAT_MEMBERS(20, 5000L),
        // An operator opens the settings screen and saves it; a few of each
        // in five seconds is already impatient.
        SERVER_CONFIG(6, 5000L),
        // Every character request shares one budget: opening the roster,
        // making somebody, switching, deleting, claiming a lore character.
        // They are all deliberate, and a screen full of them is a handful
        // of clicks rather than a stream.
        CHARACTER_REQUEST(40, 5000L);

        private final int maximumRequests;
        private final long windowMillis;

        RequestType(int maximumRequests, long windowMillis) {
            this.maximumRequests = maximumRequests;
            this.windowMillis = windowMillis;
        }
    }

    public static boolean allow(EntityPlayerMP player, RequestType requestType) {
        if (player == null || player.getUniqueID() == null || requestType == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        RequestWindow window = getWindow(player.getUniqueID(), requestType, now);
        synchronized (window) {
            if (now - window.startedAt >= requestType.windowMillis) {
                window.startedAt = now;
                window.requestCount = 0;
            }
            window.requestCount++;
            return window.requestCount <= requestType.maximumRequests;
        }
    }

    public static void logMalformed(EntityPlayerMP player, String packetName) {
        logThrottled(player, null, "Rejected malformed " + safePacketName(packetName));
    }

    public static void logRateLimited(EntityPlayerMP player, RequestType requestType, String packetName) {
        logThrottled(player, requestType,
                "Rate-limited " + safePacketName(packetName) + " requests");
    }

    public static void logQueueFull(EntityPlayerMP player, String packetName) {
        logThrottled(player, null,
                "Rejected " + safePacketName(packetName) + " because the server task queue is full");
    }

    public static void clearPlayer(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        for (RequestType requestType : RequestType.values()) {
            WINDOWS.remove(new RequestKey(ownerId, requestType));
        }
        WINDOWS.remove(new RequestKey(ownerId, null));
    }

    public static void clear() {
        WINDOWS.clear();
    }

    /**
     * Whether the player may be told something about this request type
     * again yet, given the last time they were told.
     *
     * <p>A refusal a client is answered with is itself worth bounding:
     * a flood of requests would otherwise be a flood of replies. The
     * answer is remembered per player and request type, beside the
     * window that counts the requests themselves.</p>
     */
    public static boolean allowReply(EntityPlayerMP player,
                                     RequestType requestType,
                                     long intervalMillis) {
        if (player == null || player.getUniqueID() == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        RequestWindow window = getWindow(player.getUniqueID(), requestType, now);
        synchronized (window) {
            if (now - window.lastReplyAt < intervalMillis) {
                return false;
            }
            window.lastReplyAt = now;
            return true;
        }
    }

    private static void logThrottled(EntityPlayerMP player, RequestType requestType, String message) {
        if (player == null || player.getUniqueID() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        RequestWindow window = getWindow(player.getUniqueID(), requestType, now);
        synchronized (window) {
            if (now - window.lastLogAt < LOG_INTERVAL_MILLIS) {
                return;
            }
            window.lastLogAt = now;
        }
        FMLLog.warning("[%s] %s from player %s",
                LostTalesMetaData.MOD_ID, message, player.getUniqueID());
    }

    private static RequestWindow getWindow(UUID ownerId, RequestType requestType, long now) {
        RequestKey key = new RequestKey(ownerId, requestType);
        RequestWindow window = WINDOWS.get(key);
        if (window == null) {
            RequestWindow created = new RequestWindow(now);
            RequestWindow previous = WINDOWS.putIfAbsent(key, created);
            window = previous == null ? created : previous;
        }
        return window;
    }

    private static String safePacketName(String packetName) {
        return packetName == null || packetName.length() == 0 ? "packet" : packetName;
    }

    private static final class RequestKey {
        private final UUID ownerId;
        private final RequestType requestType;

        private RequestKey(UUID ownerId, RequestType requestType) {
            this.ownerId = ownerId;
            this.requestType = requestType;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof RequestKey)) {
                return false;
            }
            RequestKey other = (RequestKey) object;
            return this.ownerId.equals(other.ownerId) && this.requestType == other.requestType;
        }

        @Override
        public int hashCode() {
            return 31 * this.ownerId.hashCode() + (this.requestType == null ? 0 : this.requestType.hashCode());
        }
    }

    private static final class RequestWindow {
        private long startedAt;
        private int requestCount;
        private long lastLogAt;
        /** When the player was last answered about this request type. */
        private long lastReplyAt;

        private RequestWindow(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
