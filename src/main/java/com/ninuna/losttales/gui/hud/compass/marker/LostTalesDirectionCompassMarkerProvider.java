package com.ninuna.losttales.gui.hud.compass.marker;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LostTalesDirectionCompassMarkerProvider implements LostTalesCompassMarkerProvider {
    private static final List<LostTalesCompassMarker> DIRECTION_MARKERS = createDirectionMarkers();

    @Override
    public List<LostTalesCompassMarker> collectMarkers(Minecraft minecraft, float partialTicks) {
        if (minecraft == null || minecraft.theWorld == null || minecraft.thePlayer == null) {
            return Collections.emptyList();
        }
        return DIRECTION_MARKERS;
    }

    private static List<LostTalesCompassMarker> createDirectionMarkers() {
        List<LostTalesCompassMarker> markers = new ArrayList<LostTalesCompassMarker>();
        markers.add(LostTalesCompassMarker.bearing(null, LostTalesCompassMarkerIcon.N, 180.0F));
        markers.add(LostTalesCompassMarker.bearing(null, LostTalesCompassMarkerIcon.NE, 225.0F));
        markers.add(LostTalesCompassMarker.bearing(null, LostTalesCompassMarkerIcon.E, 270.0F));
        markers.add(LostTalesCompassMarker.bearing(null, LostTalesCompassMarkerIcon.SE, 315.0F));
        markers.add(LostTalesCompassMarker.bearing(null, LostTalesCompassMarkerIcon.S, 0.0F));
        markers.add(LostTalesCompassMarker.bearing(null, LostTalesCompassMarkerIcon.SW, 45.0F));
        markers.add(LostTalesCompassMarker.bearing(null, LostTalesCompassMarkerIcon.W, 90.0F));
        markers.add(LostTalesCompassMarker.bearing(null, LostTalesCompassMarkerIcon.NW, 135.0F));
        return Collections.unmodifiableList(markers);
    }
}
