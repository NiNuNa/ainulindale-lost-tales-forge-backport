package com.ninuna.losttales.gui.hud.compass.marker;

import net.minecraft.client.Minecraft;

import java.util.List;

public interface LostTalesCompassMarkerProvider {
    List<LostTalesCompassMarker> collectMarkers(Minecraft minecraft, float partialTicks);
}
