package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.config.LostTalesConfig;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LostTalesStaticCompassMarkerProvider implements LostTalesCompassMarkerProvider {
    @Override
    public List<LostTalesCompassMarker> collectMarkers(Minecraft minecraft) {
        if (!LostTalesConfig.showStaticCompassMarkers || minecraft == null || minecraft.theWorld == null || minecraft.thePlayer == null) {
            return Collections.emptyList();
        }

        int dimension = minecraft.theWorld.provider.dimensionId;
        List<LostTalesCompassMarker> markers = new ArrayList<LostTalesCompassMarker>();
        String pinnedMarkerId = LostTalesClientQuestProgressStore.getPinnedMapMarkerId();
        for (LostTalesMapMarkerData entry : LostTalesClientMapMarkerStore.getSharedMarkers()) {
            if (entry.getDimensionId() != dimension) continue;
            if (entry.isHiddenUntilDiscovered() && !LostTalesClientQuestProgressStore.isMarkerDiscovered(entry.getId())) {
                continue;
            }

            boolean pinned = entry.getId() != null && entry.getId().equals(pinnedMarkerId);
            String name = pinned ? "Tracked: " + entry.getName() : entry.getName();
            double fadeInRadius = pinned ? Math.max(entry.getFadeInRadius(), 512.0D) : entry.getFadeInRadius();
            markers.add(LostTalesCompassMarker.position(
                    name,
                    LostTalesCompassMarkerIcon.fromName(pinned ? "quest" : entry.getIconName()),
                    entry.getX(),
                    entry.getY(),
                    entry.getZ(),
                    true,
                    true,
                    fadeInRadius
            ));
        }
        return markers;
    }
}
