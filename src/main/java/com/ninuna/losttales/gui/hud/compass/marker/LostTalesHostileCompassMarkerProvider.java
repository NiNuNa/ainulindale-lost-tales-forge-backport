package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.client.cache.LostTalesClientMobAggroCache;
import com.ninuna.losttales.client.cache.LostTalesClientMobAggroCache.TrackedEnemy;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderHelper;
import com.ninuna.losttales.network.packet.LostTalesMobAggroSyncPacket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

public class LostTalesHostileCompassMarkerProvider
        implements LostTalesCompassMarkerProvider {
    @Override
    public List<LostTalesCompassMarker> collectMarkers(
            Minecraft minecraft, float partialTicks) {
        if (!LostTalesConfig.showHostileCompassMarkers || minecraft == null
                || minecraft.theWorld == null || minecraft.thePlayer == null) {
            return Collections.emptyList();
        }

        List<TrackedEnemy> trackedEnemies =
                LostTalesClientMobAggroCache.getTrackedEnemies();
        if (trackedEnemies.isEmpty()) {
            return Collections.emptyList();
        }

        double configuredRadius = Math.max(8.0D,
                LostTalesConfig.hostileCompassMarkerScanRadius);
        int serverRadius =
                LostTalesClientMobAggroCache.getServerTrackingRadius();
        if (serverRadius < LostTalesMobAggroSyncPacket.MIN_TRACKING_RADIUS) {
            return Collections.emptyList();
        }
        double displayRadius = Math.min(
                configuredRadius, (double) serverRadius);
        double displayRadiusSq = displayRadius * displayRadius;
        List<LostTalesCompassMarker> markers =
                new ArrayList<LostTalesCompassMarker>(trackedEnemies.size());
        LostTalesCompassHudRenderHelper.PlayerPos playerPos =
                LostTalesCompassHudRenderHelper.lerpPlayerPos(
                        minecraft.thePlayer, partialTicks);

        for (TrackedEnemy trackedEnemy : trackedEnemies) {
            double x = trackedEnemy.getX();
            double y = trackedEnemy.getY();
            double z = trackedEnemy.getZ();
            String name = trackedEnemy.getName();
            Entity entity = minecraft.theWorld.getEntityByID(
                    trackedEnemy.getEntityId());
            if (entity instanceof EntityLivingBase
                    && entity != minecraft.thePlayer) {
                EntityLivingBase living = (EntityLivingBase) entity;
                if (!living.isEntityAlive() || living.isDead
                        || living.dimension
                        != minecraft.thePlayer.dimension) {
                    continue;
                }
                LostTalesCompassHudRenderHelper.PlayerPos livingPos =
                        LostTalesCompassHudRenderHelper.lerpEntityPos(
                                living, partialTicks);
                x = livingPos.x;
                y = livingPos.y;
                z = livingPos.z;
                name = living.getCommandSenderName();
            }

            double dx = x - playerPos.x;
            double dy = y - playerPos.y;
            double dz = z - playerPos.z;
            if (dx * dx + dy * dy + dz * dz > displayRadiusSq) {
                continue;
            }
            markers.add(LostTalesCompassMarker.positionWithStateKey(
                    "enemy:" + trackedEnemy.getEntityId(),
                    name,
                    LostTalesCompassMarkerIcon.HOSTILE,
                    x, y, z,
                    true, true, displayRadius));
        }
        return markers;
    }
}
