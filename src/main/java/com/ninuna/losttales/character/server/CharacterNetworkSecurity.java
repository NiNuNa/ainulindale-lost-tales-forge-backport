package com.ninuna.losttales.character.server;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Small per-player packet gate and throttled diagnostics for character requests. */
final class CharacterNetworkSecurity {

    private static final int MAX_REQUESTS_PER_WINDOW = 40;
    private static final long WINDOW_MILLIS = 5000L;
    private static final long LOG_INTERVAL_MILLIS = 10000L;

    private static final ConcurrentMap<UUID, RequestWindow> WINDOWS =
            new ConcurrentHashMap<UUID, RequestWindow>();

    private CharacterNetworkSecurity() {}

    static boolean allowRequest(EntityPlayerMP player) {
        if (player == null || player.getUniqueID() == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        UUID ownerId = player.getUniqueID();
        RequestWindow window = WINDOWS.get(ownerId);
        if (window == null) {
            RequestWindow created = new RequestWindow(now);
            RequestWindow previous = WINDOWS.putIfAbsent(ownerId, created);
            window = previous == null ? created : previous;
        }
        synchronized (window) {
            if (now - window.startedAt >= WINDOW_MILLIS) {
                window.startedAt = now;
                window.requestCount = 0;
            }
            window.requestCount++;
            return window.requestCount <= MAX_REQUESTS_PER_WINDOW;
        }
    }

    static void logMalformed(EntityPlayerMP player, String packetName) {
        logThrottled(player, "Rejected malformed " + packetName);
    }

    static void logRateLimited(EntityPlayerMP player) {
        logThrottled(player, "Rate-limited character requests");
    }

    static boolean shouldSendRateLimitResponse(EntityPlayerMP player) {
        if (player == null || player.getUniqueID() == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        UUID ownerId = player.getUniqueID();
        RequestWindow window = WINDOWS.get(ownerId);
        if (window == null) {
            RequestWindow created = new RequestWindow(now);
            RequestWindow previous = WINDOWS.putIfAbsent(ownerId, created);
            window = previous == null ? created : previous;
        }
        synchronized (window) {
            if (now - window.lastRateLimitResponseAt < 1000L) {
                return false;
            }
            window.lastRateLimitResponseAt = now;
            return true;
        }
    }

    static void logQueueFull(EntityPlayerMP player) {
        logThrottled(player, "Rejected character request because the server task queue is full");
    }

    static void clearPlayer(UUID ownerId) {
        if (ownerId != null) {
            WINDOWS.remove(ownerId);
        }
    }

    private static void logThrottled(EntityPlayerMP player, String message) {
        if (player == null || player.getUniqueID() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID ownerId = player.getUniqueID();
        RequestWindow window = WINDOWS.get(ownerId);
        if (window == null) {
            RequestWindow created = new RequestWindow(now);
            RequestWindow previous = WINDOWS.putIfAbsent(ownerId, created);
            window = previous == null ? created : previous;
        }
        synchronized (window) {
            if (now - window.lastLogAt < LOG_INTERVAL_MILLIS) {
                return;
            }
            window.lastLogAt = now;
        }
        FMLLog.warning("[%s] %s from player %s",
                LostTalesMetaData.MOD_ID, message, ownerId);
    }

    private static final class RequestWindow {
        private long startedAt;
        private int requestCount;
        private long lastLogAt;
        private long lastRateLimitResponseAt;

        private RequestWindow(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
