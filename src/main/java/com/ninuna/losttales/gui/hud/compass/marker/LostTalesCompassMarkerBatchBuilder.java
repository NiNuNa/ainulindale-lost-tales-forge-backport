package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderHelper;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderer;
import net.minecraft.util.MathHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class LostTalesCompassMarkerBatchBuilder {
    private LostTalesCompassMarkerBatchBuilder() {}

    public static LostTalesCompassMarkerBatch build(List<LostTalesCompassMarker> markers, LostTalesCompassHudRenderHelper.PlayerPos playerPos, float yawDeg, float pxPerDeg, int visibleDeg, int centerX) {
        float halfWidth = LostTalesCompassHudRenderer.COMPASS_WIDTH / 2.0F;
        float minX = centerX - halfWidth + LostTalesCompassMarkerIcon.WIDTH / 2.0F;
        float maxX = centerX + halfWidth - LostTalesCompassMarkerIcon.WIDTH / 2.0F;

        List<LostTalesCompassMarkerRenderItem> renderItems = new ArrayList<LostTalesCompassMarkerRenderItem>(markers.size());
        LostTalesCompassMarker focusedMarker = null;
        float focusedPx = 0.0F;
        float bestEmphasis = 0.0F;
        float bestAlpha = 0.0F;
        double bestDx = 0.0D;
        double bestDy = 0.0D;
        double bestDz = 0.0D;

        for (LostTalesCompassMarker marker : markers) {
            double dx = marker.getX() - playerPos.x;
            double dy = marker.getY() - playerPos.y;
            double dz = marker.getZ() - playerPos.z;

            boolean activeQuestMarker = marker.isActiveQuestMarker();
            float targetDeg = marker.isBearingMarker()
                    ? LostTalesCompassHudRenderHelper.normalizeViewYaw(marker.getBearingDegrees())
                    : LostTalesCompassHudRenderHelper.angleDegToTarget(dx, dz);
            float delta = LostTalesCompassHudRenderHelper.shortestDeltaDegrees(targetDeg, yawDeg);

            if (!activeQuestMarker && Math.abs(delta) > visibleDeg / 2.0F) continue;

            float px = centerX + delta * pxPerDeg;
            px = MathHelper.clamp_float(px, minX, maxX);

            float edgeT = activeQuestMarker
                    ? 1.0F
                    : LostTalesCompassHudRenderHelper.edgeCenterFactor(px, centerX, halfWidth, LostTalesCompassHudRenderer.MAP_MARKER_BEGIN_EDGE_FADE_OUT_OFFSET);
            if (edgeT <= 0.0F) continue;

            float centerDistPx = Math.abs(px - centerX);
            float emphasis = LostTalesCompassHudRenderHelper.focusEmphasis(centerDistPx, LostTalesCompassHudRenderer.MAP_MARKER_BEGIN_CENTER_FOCUS_OFFSET);

            double distSq = dx * dx + dy * dy + dz * dz;
            float distT = 1.0F;
            if (!marker.isBearingMarker() && marker.getFadeInRadius() > 0.0D) {
                double dist = Math.sqrt(distSq);
                double radius = marker.getFadeInRadius();
                if (dist > radius) continue;
                float t = MathHelper.clamp_float((float) (dist / radius), 0.0F, 1.0F);
                // Distance fade is strongest when standing on the marker and eases out
                // to the configured opacity floor at the distance threshold. Cardinal/bearing
                // icons do not have a world distance, so they are intentionally skipped.
                distT = 1.0F - (t * t * (3.0F - 2.0F * t));
            }

            float distanceAlpha = LostTalesCompassHudRenderer.MAP_MARKER_DISTANCE_FADE_IN_FLOOR_ALPHA
                    + (1.0F - LostTalesCompassHudRenderer.MAP_MARKER_DISTANCE_FADE_IN_FLOOR_ALPHA) * MathHelper.clamp_float(distT, 0.0F, 1.0F);
            float alpha = MathHelper.clamp_float(distanceAlpha, 0.0F, 1.0F) * edgeT;

            if (alpha <= 0.0F) continue;

            renderItems.add(new LostTalesCompassMarkerRenderItem(marker, px, alpha, distSq, dy, emphasis));

            if (marker.isScaleWithCenterFocus()) {
                boolean better = emphasis > bestEmphasis || (emphasis == bestEmphasis && Math.abs(px - centerX) < Math.abs(focusedPx - centerX));
                if (better) {
                    bestEmphasis = emphasis;
                    bestAlpha = alpha;
                    focusedMarker = marker;
                    focusedPx = px;
                    bestDx = dx;
                    bestDy = dy;
                    bestDz = dz;
                }
            }
        }

        Collections.sort(renderItems, new Comparator<LostTalesCompassMarkerRenderItem>() {
            @Override
            public int compare(LostTalesCompassMarkerRenderItem a, LostTalesCompassMarkerRenderItem b) {
                if (a.marker.isBearingMarker() != b.marker.isBearingMarker()) {
                    return a.marker.isBearingMarker() ? -1 : 1;
                }
                int c = Double.compare(b.distSq, a.distSq);
                if (c != 0) return c;
                c = Float.compare(a.alpha, b.alpha);
                if (c != 0) return c;
                return Float.compare(Math.abs(b.x - centerX), Math.abs(a.x - centerX));
            }
        });

        return new LostTalesCompassMarkerBatch(renderItems, focusedMarker, focusedPx, bestEmphasis, bestAlpha, bestDx, bestDy, bestDz);
    }

    public static final class LostTalesCompassMarkerBatch {
        public final List<LostTalesCompassMarkerRenderItem> renderItems;
        public final LostTalesCompassMarker focusedMarker;
        public final float focusedPx;
        public final float focusEmphasis;
        public final float focusAlpha;
        public final double dx;
        public final double dy;
        public final double dz;

        private LostTalesCompassMarkerBatch(List<LostTalesCompassMarkerRenderItem> renderItems, LostTalesCompassMarker focusedMarker, float focusedPx, float focusEmphasis, float focusAlpha, double dx, double dy, double dz) {
            this.renderItems = renderItems;
            this.focusedMarker = focusedMarker;
            this.focusedPx = focusedPx;
            this.focusEmphasis = focusEmphasis;
            this.focusAlpha = focusAlpha;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }
}
