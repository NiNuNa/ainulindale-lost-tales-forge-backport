package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerVisibility;
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
            if (!LostTalesConfig.showLotrWaypointCompassMarkers
                    && entry.getLotrWaypointId().length() > 0) {
                continue;
            }

            boolean activeQuestMarker = activeQuestMarkers.containsKey(entry.getId());
            boolean discovered = LostTalesClientQuestProgressStore.isMarkerDiscovered(entry.getId());
            boolean pinned = entry.getId() != null && entry.getId().equals(pinnedMarkerId);
            boolean undiscovered = entry.isDiscoverable() && !discovered;

            if (!LostTalesClientMapMarkerVisibility
                    .isRegionRequirementMet(entry)) {
                continue;
            }
            if (undiscovered
                    && !LostTalesClientMapMarkerVisibility
                    .isUndiscoveredCompassVisible(entry)) {
                continue;
            }

            String name = activeQuestMarker ? activeQuestMarkers.get(entry.getId()) : entry.getName();
            if (pinned) {
                name = "Tracked: " + name;
            }

            LostTalesCompassMarkerIcon icon = LostTalesCompassMarkerIcon.fromName(pinned ? "quest" : entry.getIconName());
            String color = entry.getColorName();
            boolean scaleWithFocus = true;
            boolean showDistanceLabel = true;

            if (undiscovered) {
                icon = LostTalesCompassMarkerIcon.UNDISCOVERED;
                name = "?";
                color = "white";
                // Undiscovered markers still need to be valid center-focus
                // candidates so the compass can show "?" and distance.
                scaleWithFocus = true;
                showDistanceLabel = true;
            }

            double compassFadeInRadius = activeQuestMarker || pinned ? Math.max(entry.getCompassFadeInRadius(), 512.0D) : entry.getCompassFadeInRadius();
            double markerY = entry.getEffectiveY(
                    minecraft.theWorld, minecraft.thePlayer.posY);
            if (activeQuestMarker) {
                markers.add(LostTalesCompassMarker.questPosition(
                        name, entry.getCompassTargetX(), markerY,
                        entry.getCompassTargetZ(), true,
                        compassFadeInRadius));
            } else {
                markers.add(LostTalesCompassMarker.position(
                        name,
                        icon,
                        entry.getCompassTargetX(),
                        markerY,
                        entry.getCompassTargetZ(),
                        scaleWithFocus,
                        showDistanceLabel,
                        compassFadeInRadius,
                        color
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
