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
        PARTY_MUTATION(12, 5000L);

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

        private RequestWindow(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
