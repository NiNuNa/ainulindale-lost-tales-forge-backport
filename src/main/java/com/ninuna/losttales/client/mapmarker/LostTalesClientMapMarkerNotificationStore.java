package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNames;
import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;

/**
 * Client-only map marker notification state.
 *
 * First discovery is driven by the server quest/map-marker sync. Repeat area
 * entry messages are detected locally from the already-discovered marker list.
 */
public final class LostTalesClientMapMarkerNotificationStore {
    private static final String DISCOVERY_SOUND =
            LostTalesMetaData.MOD_ID + ":map_marker.discovery";
    private static final long DISCOVERY_DURATION_MS = 6200L;
    private static final long AREA_DURATION_MS = 2600L;
    private static final long AREA_REPEAT_COOLDOWN_MS = 4500L;

    private static DiscoveryNotice discoveryNotice;
    private static AreaNotice areaNotice;
    private static final Set<String> INSIDE_AREA_MARKERS = new LinkedHashSet<String>();
    private static boolean areaStateInitialized;
    private static long lastAreaNoticeTimeMs;

    private LostTalesClientMapMarkerNotificationStore() {}

    public static synchronized void clear() {
        discoveryNotice = null;
        areaNotice = null;
        INSIDE_AREA_MARKERS.clear();
        areaStateInitialized = false;
        lastAreaNoticeTimeMs = 0L;
    }

    public static synchronized void showDiscovery(String markerId, String markerName) {
        String normalizedId = LostTalesQuestMarkerHelper.normalizeMarkerId(markerId);
        if (normalizedId.length() == 0) {
            return;
        }
        // The server names the marker in its own words; this game names it
        // in its language where the marker is one the lang file names.
        LostTalesMapMarkerData marker = LostTalesClientMapMarkerStore.getSharedMarker(normalizedId);
        String given = markerName == null || markerName.length() == 0
                ? (marker == null ? "" : marker.getGivenName()) : markerName;
        // A marker with no name of its own is named by what it is called after.
        String name = given.trim().length() == 0 && marker != null
                ? marker.getName()
                : LostTalesMapMarkerNames.shownName(normalizedId, given);
        if (name.length() == 0) {
            name = normalizedId;
        }
        long now = System.currentTimeMillis();
        discoveryNotice = new DiscoveryNotice(normalizedId, name, now, DISCOVERY_DURATION_MS);
        areaNotice = null;
        lastAreaNoticeTimeMs = now;
        INSIDE_AREA_MARKERS.add(normalizedId);
        playDiscoveryChime();
    }

    /** Called from the HUD render pass; cheap enough for the small marker lists used here. */
    public static synchronized void updateAreaPresence(Minecraft minecraft) {
        if (minecraft == null || minecraft.thePlayer == null || minecraft.theWorld == null) {
            clearAreaStateOnly();
            return;
        }

        EntityPlayer player = minecraft.thePlayer;
        int dimension = minecraft.theWorld.provider.dimensionId;
        Set<String> newInside = new LinkedHashSet<String>();
        LostTalesMapMarkerData entered = null;
        double bestDistSq = Double.MAX_VALUE;

        for (LostTalesMapMarkerData marker : LostTalesClientMapMarkerStore.getAllMarkers()) {
            if (!isAreaMarkerEligible(marker, dimension)) {
                continue;
            }

            double radius = Math.max(1.0D, marker.getDiscoveryRadius());
            double dx = player.posX - marker.getX();
            double markerY = marker.getEffectiveY(
                    minecraft.theWorld, player.posY);
            double dy = player.posY - markerY;
            double dz = player.posZ - marker.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > radius * radius) {
                continue;
            }

            newInside.add(marker.getId());
            if (areaStateInitialized && !INSIDE_AREA_MARKERS.contains(marker.getId()) && !isDiscoveryNoticeActive(marker.getId()) && distSq < bestDistSq) {
                entered = marker;
                bestDistSq = distSq;
            }
        }

        INSIDE_AREA_MARKERS.clear();
        INSIDE_AREA_MARKERS.addAll(newInside);

        if (!areaStateInitialized) {
            areaStateInitialized = true;
            return;
        }

        long now = System.currentTimeMillis();
        if (entered != null && now - lastAreaNoticeTimeMs >= AREA_REPEAT_COOLDOWN_MS) {
            areaNotice = new AreaNotice(entered.getId(), entered.getName(), now, AREA_DURATION_MS);
            lastAreaNoticeTimeMs = now;
        }
    }

    public static synchronized DiscoveryNotice getDiscoveryNotice() {
        long now = System.currentTimeMillis();
        if (discoveryNotice != null && discoveryNotice.isExpired(now)) {
            discoveryNotice = null;
        }
        return discoveryNotice;
    }

    public static synchronized AreaNotice getAreaNotice() {
        long now = System.currentTimeMillis();
        if (areaNotice != null && areaNotice.isExpired(now)) {
            areaNotice = null;
        }
        return areaNotice;
    }

    public static synchronized boolean isDiscoveryNoticeActive(String markerId) {
        DiscoveryNotice notice = getDiscoveryNotice();
        return notice != null && notice.markerId.equals(markerId);
    }


    private static boolean isAreaMarkerEligible(LostTalesMapMarkerData marker, int dimension) {
        if (marker == null || marker.getId() == null || marker.getId().length() == 0 || marker.getDimensionId() != dimension) {
            return false;
        }
        if (!marker.isDiscoverable()) {
            return false;
        }
        return LostTalesClientQuestProgressStore.isMarkerDiscovered(marker.getId());
    }

    private static void clearAreaStateOnly() {
        INSIDE_AREA_MARKERS.clear();
        areaStateInitialized = false;
        areaNotice = null;
    }

    private static void playDiscoveryChime() {
        if (!LostTalesConfig.playQuestSounds) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.thePlayer != null) {
            Random random = minecraft.theWorld == null
                    ? null : minecraft.theWorld.rand;
            float volume = random == null
                    ? 0.45F : 0.43F + random.nextFloat() * 0.04F;
            float pitch = random == null
                    ? 1.0F : 0.97F + random.nextFloat() * 0.06F;
            minecraft.thePlayer.playSound(DISCOVERY_SOUND, volume, pitch);
        }
    }

    public static class TimedNotice {
        protected final String markerId;
        protected final String name;
        protected final long startedMs;
        protected final long durationMs;

        protected TimedNotice(String markerId, String name, long startedMs, long durationMs) {
            this.markerId = markerId == null ? "" : markerId;
            this.name = name == null || name.length() == 0
                    ? StatCollector.translateToLocal("map.losttales.marker.unnamed")
                    : name;
            this.startedMs = startedMs;
            this.durationMs = durationMs;
        }

        public String getMarkerId() {
            return markerId;
        }

        public String getName() {
            return name;
        }

        public float getAgeFraction(long now) {
            if (durationMs <= 0L) {
                return 1.0F;
            }
            float value = (float) (now - startedMs) / (float) durationMs;
            if (value < 0.0F) {
                return 0.0F;
            }
            if (value > 1.0F) {
                return 1.0F;
            }
            return value;
        }

        public boolean isExpired(long now) {
            return now - startedMs >= durationMs;
        }
    }

    public static final class DiscoveryNotice extends TimedNotice {
        private DiscoveryNotice(String markerId, String name, long startedMs, long durationMs) {
            super(markerId, name, startedMs, durationMs);
        }
    }

    public static final class AreaNotice extends TimedNotice {
        private AreaNotice(String markerId, String name, long startedMs, long durationMs) {
            super(markerId, name, startedMs, durationMs);
        }
    }
}
