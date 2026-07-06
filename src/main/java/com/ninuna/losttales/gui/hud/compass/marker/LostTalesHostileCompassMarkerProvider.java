package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.client.cache.LostTalesClientMobAggroCache;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LostTalesHostileCompassMarkerProvider implements LostTalesCompassMarkerProvider {
    private static final double SCAN_RADIUS = 48.0D;

    @Override
    public List<LostTalesCompassMarker> collectMarkers(Minecraft minecraft, float partialTicks) {
        if (!LostTalesConfig.showHostileCompassMarkers || minecraft == null || minecraft.theWorld == null || minecraft.thePlayer == null) {
            return Collections.emptyList();
        }

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
            if (dx * dx + dy * dy + dz * dz > SCAN_RADIUS * SCAN_RADIUS) continue;

            boolean serverConfirmedAggro = LostTalesClientMobAggroCache.isAggro(living.getEntityId());
            boolean clientTargetingPlayer = living instanceof EntityLiving && ((EntityLiving) living).getAttackTarget() == minecraft.thePlayer;
            boolean vanillaHostile = !LostTalesConfig.onlyShowAggroHostileCompassMarkers && living instanceof IMob;
            if (!serverConfirmedAggro && !clientTargetingPlayer && !vanillaHostile) continue;

            String name = living.getCommandSenderName();
            markers.add(LostTalesCompassMarker.position(name, LostTalesCompassMarkerIcon.HOSTILE, livingPos.x, livingPos.y, livingPos.z, true, true, SCAN_RADIUS));
        }
        return markers;
    }
}
