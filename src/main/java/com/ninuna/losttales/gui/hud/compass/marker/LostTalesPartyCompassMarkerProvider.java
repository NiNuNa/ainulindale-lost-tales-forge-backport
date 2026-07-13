package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import com.ninuna.losttales.client.party.ClientPartyTrackingCache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Client-only compass projection of server-authorized party tracking data. */
public final class LostTalesPartyCompassMarkerProvider
        implements LostTalesCompassMarkerProvider {

    @Override
    public List<LostTalesCompassMarker> collectMarkers(
            Minecraft minecraft, float partialTicks) {
        if (minecraft == null || minecraft.theWorld == null
                || minecraft.thePlayer == null) {
            return Collections.emptyList();
        }
        int dimensionId = minecraft.thePlayer.dimension;
        List<LostTalesMapMarkerData> partyMarkers =
                ClientPartyTrackingCache.getMapMarkers();
        if (partyMarkers.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<LostTalesCompassMarker> result =
                new ArrayList<LostTalesCompassMarker>(partyMarkers.size());
        for (LostTalesMapMarkerData marker : partyMarkers) {
            if (marker == null || marker.getDimensionId() != dimensionId) {
                continue;
            }
            result.add(LostTalesCompassMarker.positionWithStateKey(
                    marker.getId(),
                    marker.getName(),
                    LostTalesCompassMarkerIcon.fromName(marker.getIconName()),
                    marker.getX(), marker.getY(), marker.getZ(),
                    true,
                    true,
                    marker.getCompassFadeInRadius(),
                    marker.getColorName()));
        }
        return result;
    }
}
