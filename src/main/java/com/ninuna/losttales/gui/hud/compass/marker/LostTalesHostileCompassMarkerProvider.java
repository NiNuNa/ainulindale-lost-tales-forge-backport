package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.client.cache.LostTalesClientMobAggroCache;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.entity.LostTalesHostilityHelper;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

public class LostTalesHostileCompassMarkerProvider implements LostTalesCompassMarkerProvider {
    @Override
    public List<LostTalesCompassMarker> collectMarkers(Minecraft minecraft, float partialTicks) {
        if (!LostTalesConfig.showHostileCompassMarkers || minecraft == null || minecraft.theWorld == null || minecraft.thePlayer == null) {
            return Collections.emptyList();
        }

        double scanRadius = Math.max(8.0D, LostTalesConfig.hostileCompassMarkerScanRadius);
        double scanRadiusSq = scanRadius * scanRadius;
        List<LostTalesCompassMarker> markers = new ArrayList<LostTalesCompassMarker>();
        LostTalesCompassHudRenderHelper.PlayerPos playerPos = LostTalesCompassHudRenderHelper.lerpPlayerPos(minecraft.thePlayer, partialTicks);
        List entities = minecraft.theWorld.loadedEntityList;
        for (Object object : entities) {
            if (!(object instanceof EntityLivingBase) || object == minecraft.thePlayer) continue;
            EntityLivingBase living = (EntityLivingBase) object;
            if (!living.isEntityAlive()) continue;

            LostTalesCompassHudRenderHelper.PlayerPos livingPos = LostTalesCompassHudRenderHelper.lerpEntityPos(living, partialTicks);
            double dx = livingPos.x - playerPos.x;
            double dy = livingPos.y - playerPos.y;
            double dz = livingPos.z - playerPos.z;
            if (dx * dx + dy * dy + dz * dz > scanRadiusSq) continue;

            boolean serverConfirmedAggro = LostTalesClientMobAggroCache.isAggro(living.getEntityId());
            boolean clientTargetingPlayer = LostTalesHostilityHelper.isActivelyTargetingPlayer(living, minecraft.thePlayer);
            boolean fallbackHostile = !LostTalesConfig.onlyShowAggroHostileCompassMarkers && LostTalesHostilityHelper.isPassiveFallbackHostile(living, minecraft.thePlayer);
            if (!serverConfirmedAggro && !clientTargetingPlayer && !fallbackHostile) continue;

            String name = living.getCommandSenderName();
            markers.add(LostTalesCompassMarker.position(name, LostTalesCompassMarkerIcon.HOSTILE, livingPos.x, livingPos.y, livingPos.z, true, true, scanRadius));
        }
        return markers;
    }
}
