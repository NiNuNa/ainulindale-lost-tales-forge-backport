package com.ninuna.losttales.client.mapmarker;

import java.util.List;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.world.map.LOTRAbstractWaypoint;

/**
 * LOTR menu/loading map delegate with decorative Lost Tales marker icons.
 * It deliberately has no discovery, selection, or travel behavior.
 */
public final class LostTalesLotrMenuMapGui extends LOTRGuiMap {
    private final boolean sepia;

    public LostTalesLotrMenuMapGui(boolean sepia) {
        this.sepia = sepia;
    }

    @Override
    public void renderWaypoints(
            List<LOTRAbstractWaypoint> waypoints, int pass,
            int mouseX, int mouseY, boolean drawLabels,
            boolean includeHidden) {
        /*
         * The animated menu has no gameplay state, so LOTR's native waypoint
         * pass is never needed here. Drawing it first caused dots near the
         * viewport edge to appear before their Lost Tales icon reached the
         * visible area.
         */
        if (pass == 0) {
            LostTalesLotrMapMarkerIconOverlay
                    .renderDecorativeBackgroundMarkers(
                            this, this.sepia);
        }
    }
}
