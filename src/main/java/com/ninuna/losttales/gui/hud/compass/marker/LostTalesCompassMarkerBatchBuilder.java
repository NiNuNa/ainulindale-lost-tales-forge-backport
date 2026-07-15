package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderHelper;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.MathHelper;

public final class LostTalesCompassMarkerBatchBuilder {
    private LostTalesCompassMarkerBatchBuilder() {}

    public static LostTalesCompassMarkerBatch build(List<LostTalesCompassMarker> markers, LostTalesCompassHudRenderHelper.PlayerPos playerPos, float yawDeg, float pxPerDeg, int visibleDeg, int centerX, String currentFocusedMarkerStateKey) {
        float halfWidth = LostTalesCompassHudRenderer.COMPASS_WIDTH / 2.0F;
        float minX = centerX - halfWidth + LostTalesCompassMarkerIcon.WIDTH / 2.0F;
        float maxX = centerX + halfWidth - LostTalesCompassMarkerIcon.WIDTH / 2.0F;
        float focusFadeOutEndDistPx = Math.max(
                LostTalesCompassHudRenderer.MAP_MARKER_BEGIN_CENTER_FOCUS_OFFSET + 1.0F,
                halfWidth - LostTalesCompassHudRenderer.MAP_MARKER_BEGIN_EDGE_FADE_OUT_OFFSET
        );
        float focusFadeOutStartDistPx = Math.max(
                LostTalesCompassHudRenderer.MAP_MARKER_BEGIN_CENTER_FOCUS_OFFSET,
                focusFadeOutEndDistPx - LostTalesCompassHudRenderer.MAP_MARKER_FOCUS_EDGE_FADE_OUT_WIDTH
        );

        List<LostTalesCompassMarkerRenderItem> renderItems = new ArrayList<LostTalesCompassMarkerRenderItem>(markers.size());

        LostTalesCompassMarker centeredFocusMarker = null;
        float centeredFocusPx = 0.0F;
        float bestCenteredDistPx = Float.MAX_VALUE;
        float bestCenteredFadeInAlpha = 0.0F;
        float bestCenteredFadeOutAlpha = 0.0F;
        float bestCenteredDistanceAlpha = 0.0F;
        double bestCenteredDistSq = Double.MAX_VALUE;
        double bestCenteredDx = 0.0D;
        double bestCenteredDy = 0.0D;
        double bestCenteredDz = 0.0D;

        LostTalesCompassMarker retainedFocusMarker = null;
        float retainedFocusPx = 0.0F;
        float retainedFadeInAlpha = 0.0F;
        float retainedFadeOutAlpha = 0.0F;
        float retainedDistanceAlpha = 0.0F;
        double retainedDx = 0.0D;
        double retainedDy = 0.0D;
        double retainedDz = 0.0D;

        for (LostTalesCompassMarker marker : markers) {
            double dx = marker.getX() - playerPos.x;
            double dy = marker.getY() - playerPos.y;
            double dz = marker.getZ() - playerPos.z;

            boolean activeQuestMarker = marker.isActiveQuestMarker();
            boolean forceFullOpacity = marker.isForceFullOpacity();
            float targetDeg = marker.isBearingMarker()
                    ? LostTalesCompassHudRenderHelper.normalizeViewYaw(marker.getBearingDegrees())
                    : LostTalesCompassHudRenderHelper.angleDegToTarget(dx, dz);
            float delta = LostTalesCompassHudRenderHelper.shortestDeltaDegrees(targetDeg, yawDeg);

            if (!activeQuestMarker && !forceFullOpacity
                    && Math.abs(delta) > visibleDeg / 2.0F) continue;

            float rawPx = centerX + delta * pxPerDeg;
            float px = MathHelper.clamp_float(rawPx, minX, maxX);

            float iconEdgeAlpha = activeQuestMarker || forceFullOpacity
                    ? 1.0F
                    : LostTalesCompassHudRenderHelper.edgeCenterFactor(rawPx, centerX, halfWidth, LostTalesCompassHudRenderer.MAP_MARKER_BEGIN_EDGE_FADE_OUT_OFFSET);
            if (iconEdgeAlpha <= 0.0F) continue;

            float centerDistPx = Math.abs(rawPx - centerX);
            float fadeInAlpha = LostTalesCompassHudRenderHelper.focusEmphasis(centerDistPx, LostTalesCompassHudRenderer.MAP_MARKER_BEGIN_CENTER_FOCUS_OFFSET);
            float fadeOutAlpha = LostTalesCompassHudRenderHelper.edgeIntervalFactor(centerDistPx, focusFadeOutStartDistPx, focusFadeOutEndDistPx);

            double distSq = dx * dx + dy * dy + dz * dz;
            float distT = 1.0F;
            if (!forceFullOpacity && !marker.isBearingMarker()
                    && marker.getFadeInRadius() > 0.0D) {
                double dist = Math.sqrt(distSq);
                double radius = marker.getFadeInRadius();
                if (dist > radius && !marker.isRetainedBeyondFadeRadius()) continue;
                float t = MathHelper.clamp_float((float) (dist / radius), 0.0F, 1.0F);
                // The marker is fully opaque nearby and eases down to the
                // configured opacity floor at the distance threshold. Cardinal/bearing
                // icons do not have a world distance, so they are intentionally skipped.
                distT = 1.0F - (t * t * (3.0F - 2.0F * t));
            }

            float distanceAlpha = LostTalesCompassHudRenderer.MAP_MARKER_DISTANCE_FADE_IN_FLOOR_ALPHA
                    + (1.0F - LostTalesCompassHudRenderer.MAP_MARKER_DISTANCE_FADE_IN_FLOOR_ALPHA) * MathHelper.clamp_float(distT, 0.0F, 1.0F);
            float alpha = MathHelper.clamp_float(distanceAlpha, 0.0F, 1.0F) * iconEdgeAlpha;

            if (alpha <= 0.0F) continue;

            renderItems.add(new LostTalesCompassMarkerRenderItem(marker, px, alpha, distSq, dy, fadeInAlpha));

            if (isFocusCandidate(marker)) {
                boolean insideGreenFocusZone = centerDistPx <= LostTalesCompassHudRenderer.MAP_MARKER_BEGIN_CENTER_FOCUS_OFFSET;
                if (insideGreenFocusZone) {
                    boolean better = centeredFocusMarker == null
                            || centerDistPx < bestCenteredDistPx
                            || (centerDistPx == bestCenteredDistPx && distSq < bestCenteredDistSq);
                    if (better) {
                        bestCenteredDistPx = centerDistPx;
                        bestCenteredFadeInAlpha = fadeInAlpha;
                        bestCenteredFadeOutAlpha = fadeOutAlpha;
                        bestCenteredDistanceAlpha = distanceAlpha;
                        bestCenteredDistSq = distSq;
                        centeredFocusMarker = marker;
                        centeredFocusPx = px;
                        bestCenteredDx = dx;
                        bestCenteredDy = dy;
                        bestCenteredDz = dz;
                    }
                } else if (currentFocusedMarkerStateKey != null && currentFocusedMarkerStateKey.equals(getFocusStateKey(marker))) {
                    retainedFocusMarker = marker;
                    retainedFocusPx = px;
                    retainedFadeInAlpha = fadeInAlpha;
                    retainedFadeOutAlpha = fadeOutAlpha;
                    retainedDistanceAlpha = distanceAlpha;
                    retainedDx = dx;
                    retainedDy = dy;
                    retainedDz = dz;
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

        if (centeredFocusMarker != null) {
            return new LostTalesCompassMarkerBatch(renderItems, centeredFocusMarker, centeredFocusPx, bestCenteredFadeInAlpha, bestCenteredFadeOutAlpha, bestCenteredDistanceAlpha, bestCenteredDx, bestCenteredDy, bestCenteredDz);
        }
        return new LostTalesCompassMarkerBatch(renderItems, retainedFocusMarker, retainedFocusPx, retainedFadeInAlpha, retainedFadeOutAlpha, retainedDistanceAlpha, retainedDx, retainedDy, retainedDz);
    }

    public static String getFocusStateKey(LostTalesCompassMarker marker) {
        if (marker.getStateKey() != null && marker.getStateKey().length() > 0) {
            return marker.getStateKey();
        }
        String name = marker.getName();
        StringBuilder key = new StringBuilder();
        key.append(marker.getIcon().name());
        key.append('|');
        key.append(name == null ? "" : name);
        key.append('|');
        key.append(MathHelper.floor_double(marker.getX()));
        key.append('|');
        key.append(MathHelper.floor_double(marker.getY()));
        key.append('|');
        key.append(MathHelper.floor_double(marker.getZ()));
        return key.toString();
    }

    private static boolean isFocusCandidate(LostTalesCompassMarker marker) {
        return marker.isScaleWithCenterFocus() && marker.getName() != null && marker.getName().length() > 0;
    }

    public static final class LostTalesCompassMarkerBatch {
        public final List<LostTalesCompassMarkerRenderItem> renderItems;
        public final LostTalesCompassMarker focusedMarker;
        public final float focusedPx;
        public final float focusFadeInAlpha;
        public final float focusFadeOutAlpha;
        public final float focusDistanceAlpha;
        public final double dx;
        public final double dy;
        public final double dz;

        private LostTalesCompassMarkerBatch(List<LostTalesCompassMarkerRenderItem> renderItems, LostTalesCompassMarker focusedMarker, float focusedPx, float focusFadeInAlpha, float focusFadeOutAlpha, float focusDistanceAlpha, double dx, double dy, double dz) {
            this.renderItems = renderItems;
            this.focusedMarker = focusedMarker;
            this.focusedPx = focusedPx;
            this.focusFadeInAlpha = focusFadeInAlpha;
            this.focusFadeOutAlpha = focusFadeOutAlpha;
            this.focusDistanceAlpha = focusDistanceAlpha;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }
}
