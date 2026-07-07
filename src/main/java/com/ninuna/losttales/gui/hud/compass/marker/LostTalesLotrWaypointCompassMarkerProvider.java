package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.config.LostTalesConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lotr.common.LOTRDimension;
import lotr.common.world.map.LOTRAbstractWaypoint;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.client.Minecraft;
/**
 * Read-only bridge from Lord of the Rings Legacy public waypoints into the Lost Tales compass.
 *
 * This does not create, unlock, teleport to, or otherwise modify LOTR waypoints. It only reads
 * LOTR's own waypoint data and renders compatible entries as Point of Interest markers.
 * Private/custom waypoints are intentionally left for a later pass if LOTR exposes a stable API.
 */
public class LostTalesLotrWaypointCompassMarkerProvider implements LostTalesCompassMarkerProvider {
    public static final String CATEGORY_NAME = "Point of Interest";
    private static final double DEFAULT_FADE_IN_RADIUS = 640.0D;

    @Override
    public List<LostTalesCompassMarker> collectMarkers(Minecraft minecraft, float partialTicks) {
        if (!LostTalesConfig.showStaticCompassMarkers || !LostTalesConfig.showLotrWaypointCompassMarkers || minecraft == null || minecraft.theWorld == null || minecraft.thePlayer == null) {
            return Collections.emptyList();
        }
        if (minecraft.theWorld.provider.dimensionId != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return Collections.emptyList();
        }

        List<LostTalesCompassMarker> markers = new ArrayList<LostTalesCompassMarker>();
        for (LOTRAbstractWaypoint waypoint : getLotrWaypoints()) {
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
                    name = "Waypoint";
                }

                int x = waypoint.getXCoord();
                int z = waypoint.getZCoord();
                markers.add(LostTalesCompassMarker.position(
                        name,
                        LostTalesCompassMarkerIcon.FORT,
                        x,
                        getWaypointY(minecraft, waypoint, x, z),
                        z,
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

    private static List<LOTRAbstractWaypoint> getLotrWaypoints() {
        try {
            List<LOTRAbstractWaypoint> waypoints = LOTRWaypoint.listAllWaypoints();
            return waypoints == null ? Collections.<LOTRAbstractWaypoint>emptyList() : waypoints;
        } catch (Throwable ignored) {
            LOTRWaypoint[] values = LOTRWaypoint.values();
            List<LOTRAbstractWaypoint> fallback = new ArrayList<LOTRAbstractWaypoint>(values.length);
            for (LOTRWaypoint waypoint : values) {
                fallback.add(waypoint);
            }
            return fallback;
        }
    }

    private static int getWaypointY(Minecraft minecraft, LOTRAbstractWaypoint waypoint, int x, int z) {
        int y = waypoint.getYCoordSaved();
        if (y > 0) {
            return y;
        }
        try {
            if (minecraft != null && minecraft.theWorld != null) {
                y = waypoint.getYCoord(minecraft.theWorld, x, z);
                if (y > 0) {
                    return y;
                }
            }
        } catch (Throwable ignored) {
        }
        return 64;
    }
}
