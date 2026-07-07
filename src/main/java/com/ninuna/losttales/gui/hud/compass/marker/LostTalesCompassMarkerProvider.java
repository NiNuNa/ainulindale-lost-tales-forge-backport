package com.ninuna.losttales.gui.hud.compass.marker;

import java.util.List;
import net.minecraft.client.Minecraft;

public interface LostTalesCompassMarkerProvider {
    List<LostTalesCompassMarker> collectMarkers(Minecraft minecraft, float partialTicks);
}
