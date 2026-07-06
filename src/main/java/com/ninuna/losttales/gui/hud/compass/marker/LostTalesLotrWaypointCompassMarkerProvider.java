package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.config.LostTalesConfig;
import lotr.common.LOTRDimension;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only bridge from Lord of the Rings Legacy public waypoints into the Lost Tales compass.
 *
 * This does not create, unlock, teleport to, or otherwise modify LOTR waypoints. It only reads
 * the public LOTR waypoint enum and renders compatible entries as Point of Interest markers.
 * Private/custom waypoints are intentionally left for a later pass.
 */
public class LostTalesLotrWaypointCompassMarkerProvider implements LostTalesCompassMarkerProvider {
    public static final String CATEGORY_NAME = "Point of Interest";
    private static final double DEFAULT_FADE_IN_RADIUS = 640.0D;

    @Override
    public List<LostTalesCompassMarker> collectMarkers(Minecraft minecraft) {
        if (!LostTalesConfig.showStaticCompassMarkers || !LostTalesConfig.showLotrWaypointCompassMarkers || minecraft == null || minecraft.theWorld == null || minecraft.thePlayer == null) {
            return Collections.emptyList();
        }
        if (minecraft.theWorld.provider.dimensionId != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return Collections.emptyList();
        }

        List<LostTalesCompassMarker> markers = new ArrayList<LostTalesCompassMarker>();
        for (LOTRWaypoint waypoint : LOTRWaypoint.values()) {
            if (waypoint == null) {
                continue;
            }
            try {
                if (waypoint.isHidden()) {
                    continue;
                }
                if (LostTalesConfig.onlyShowUnlockedLotrWaypoints && !waypoint.hasPlayerUnlocked(minecraft.thePlayer)) {
                    continue;
                }

                String name = waypoint.getDisplayName();
                if (name == null || name.length() == 0) {
                    name = waypoint.getCodeName();
                }
                if (name == null || name.length() == 0) {
                    name = waypoint.name();
                }

                markers.add(LostTalesCompassMarker.position(
                        name,
                        LostTalesCompassMarkerIcon.FORT,
                        waypoint.getXCoord(),
                        getWaypointY(waypoint),
                        waypoint.getZCoord(),
                        true,
                        true,
                        DEFAULT_FADE_IN_RADIUS
                ));
            } catch (Throwable ignored) {
                // A third-party enum extension or missing LOTR data should never crash the HUD.
            }
        }
        return markers;
    }

    private static int getWaypointY(LOTRWaypoint waypoint) {
        int y = waypoint.getYCoordSaved();
        return y > 0 ? y : 64;
    }
}
