package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import com.ninuna.losttales.client.quest.LostTalesClientQuestMarkerHelper;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.config.LostTalesConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

public class LostTalesStaticCompassMarkerProvider implements LostTalesCompassMarkerProvider {
    @Override
    public List<LostTalesCompassMarker> collectMarkers(Minecraft minecraft, float partialTicks) {
        if (!LostTalesConfig.showStaticCompassMarkers || minecraft == null || minecraft.theWorld == null || minecraft.thePlayer == null) {
            return Collections.emptyList();
        }

        int dimension = minecraft.theWorld.provider.dimensionId;
        List<LostTalesCompassMarker> markers = new ArrayList<LostTalesCompassMarker>();
        String pinnedMarkerId = LostTalesClientQuestProgressStore.getPinnedMapMarkerId();
        Map<String, String> activeQuestMarkers = LostTalesClientQuestMarkerHelper.collectActiveQuestMarkerLabels();

        for (LostTalesMapMarkerData entry : LostTalesClientMapMarkerStore.getSharedMarkers()) {
            if (entry.getDimensionId() != dimension) continue;

            boolean activeQuestMarker = activeQuestMarkers.containsKey(entry.getId());
            boolean discovered = LostTalesClientQuestProgressStore.isMarkerDiscovered(entry.getId());
            boolean pinned = entry.getId() != null && entry.getId().equals(pinnedMarkerId);
            if (entry.isHiddenUntilDiscovered() && !discovered && !activeQuestMarker && !pinned) {
                continue;
            }

            String name = activeQuestMarker ? activeQuestMarkers.get(entry.getId()) : entry.getName();
            if (pinned) {
                name = "Tracked: " + name;
            }
            double fadeInRadius = activeQuestMarker || pinned ? Math.max(entry.getFadeInRadius(), 512.0D) : entry.getFadeInRadius();
            if (activeQuestMarker) {
                markers.add(LostTalesCompassMarker.questPosition(name, entry.getX(), entry.getY(), entry.getZ(), true, fadeInRadius));
            } else {
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
        }

        for (LostTalesClientQuestMarkerHelper.ActiveCoordinateMarker entry : LostTalesClientQuestMarkerHelper.collectActiveCoordinateMarkers()) {
            if (entry.getDimensionId() != dimension) continue;
            markers.add(LostTalesCompassMarker.questPosition(entry.getLabel(), entry.getX(), entry.getY(), entry.getZ(), true, 512.0D));
        }
        return markers;
    }
}
