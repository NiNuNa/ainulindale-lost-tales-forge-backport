package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.config.LostTalesConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LostTalesHostileCompassMarkerProvider implements LostTalesCompassMarkerProvider {
    private static final double SCAN_RADIUS = 48.0D;

    @Override
    public List<LostTalesCompassMarker> collectMarkers(Minecraft minecraft) {
        if (!LostTalesConfig.showHostileCompassMarkers || minecraft == null || minecraft.theWorld == null || minecraft.thePlayer == null) {
            return Collections.emptyList();
        }

        List<LostTalesCompassMarker> markers = new ArrayList<LostTalesCompassMarker>();
        List entities = minecraft.theWorld.loadedEntityList;
        for (Object object : entities) {
            if (!(object instanceof EntityLivingBase) || object == minecraft.thePlayer) continue;
            EntityLivingBase living = (EntityLivingBase) object;
            if (!living.isEntityAlive()) continue;

            double dx = living.posX - minecraft.thePlayer.posX;
            double dy = living.posY - minecraft.thePlayer.posY;
            double dz = living.posZ - minecraft.thePlayer.posZ;
            if (dx * dx + dy * dy + dz * dz > SCAN_RADIUS * SCAN_RADIUS) continue;

            boolean targetingPlayer = living instanceof EntityLiving && ((EntityLiving) living).getAttackTarget() == minecraft.thePlayer;
            boolean vanillaHostile = living instanceof IMob;
            if (!targetingPlayer && !vanillaHostile) continue;

            String name = living.getCommandSenderName();
            markers.add(LostTalesCompassMarker.position(name, LostTalesCompassMarkerIcon.HOSTILE, living.posX, living.posY, living.posZ, true, true, SCAN_RADIUS));
        }
        return markers;
    }
}
