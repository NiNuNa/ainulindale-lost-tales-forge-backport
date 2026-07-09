package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarker;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.LOTRDimension;
import lotr.common.world.map.LOTRAbstractWaypoint;
import lotr.common.world.map.LOTRCustomWaypoint;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/**
 * Client-only renderer for Lost Tales icons on the LOTR map.
 *
 * <p>This is deliberately visual-only. LOTR waypoints are still the objects
 * rendered and selected by LOTRGuiMap; this helper only removes matching
 * waypoints from LOTR's first/icon pass once a Lost Tales marker should use its
 * real icon, then draws Lost Tales icon quads at the same transformed map
 * coordinates. Undiscovered existing LOTR waypoints keep their vanilla LOTR dot
 * unless the marker is explicitly configured to be visible before discovery.</p>
 */
public final class LostTalesLotrMapMarkerIconOverlay {
    private static final int ICON_DRAW_SIZE = 13;
    private static final int ICON_HOVER_DRAW_SIZE = 16;
    private static final int HOVER_RADIUS = ICON_HOVER_DRAW_SIZE / 2;
    private static final int EDGE_PADDING = ICON_HOVER_DRAW_SIZE / 2 + 1;
    private static final double COORDINATE_MATCH_EPSILON = 0.5D;

    private static Method transformCoordsMethod;
    private static Field mapXMinField;
    private static Field mapXMaxField;
    private static Field mapYMinField;
    private static Field mapYMaxField;
    private static Field zoomExpField;
    private static Field selectedWaypointField;
    private static Method renderWaypointTooltipMethod;
    private static Method drawFancyRectMethod;
    private static boolean reflectionReady;
    private static boolean reflectionFailed;

    private LostTalesLotrMapMarkerIconOverlay() {}

    public static List<LOTRAbstractWaypoint> getWaypointsForLotrBaseRender(List<LOTRAbstractWaypoint> waypoints) {
        if (waypoints == null || waypoints.isEmpty()) {
            return waypoints;
        }

        List<LOTRAbstractWaypoint> filtered = new ArrayList<LOTRAbstractWaypoint>(waypoints.size());
        for (LOTRAbstractWaypoint waypoint : waypoints) {
            if (waypoint != null && getReplacementMarker(waypoint) != null) {
                continue;
            }
            filtered.add(waypoint);
        }
        return filtered;
    }

    public static void renderReplacementWaypoints(LOTRGuiMap gui, List<LOTRAbstractWaypoint> waypoints, int mouseX, int mouseY, boolean drawLabels, boolean includeHidden) {
        if (gui == null || waypoints == null || waypoints.isEmpty()) {
            return;
        }

        RenderContext context = createRenderContext(gui);
        if (context == null || context.alpha <= 0.0F) {
            return;
        }

        beginIconRender();
        try {
            for (LOTRAbstractWaypoint waypoint : waypoints) {
                LostTalesMapMarkerData marker = getReplacementMarker(waypoint);
                if (marker == null || !shouldRenderWaypoint(context.minecraft, waypoint, includeHidden)) {
                    continue;
                }

                ScreenPosition position = transformWaypoint(context, waypoint);
                if (position == null || !isInsideMap(position.x, position.y, context)) {
                    continue;
                }

                boolean hover = isMouseOverIcon(position.x, position.y, mouseX, mouseY);
                drawMarkerIcon(context.minecraft, marker, position.x, position.y, context.alpha, hover);

                if (drawLabels && context.labelAlpha > 0.0F) {
                    drawLabel(context.fontRenderer, getWaypointDisplayName(waypoint, marker), position.x, position.y, context.labelAlpha, context);
                }
            }
        } catch (Throwable ignored) {
            // If LOTR internals change, fail closed and let the normal map stay usable.
        } finally {
            endIconRender();
        }
    }

    public static void renderStandaloneMarkers(LOTRGuiMap gui, int mouseX, int mouseY, boolean drawLabels) {
        RenderContext context = createRenderContext(gui);
        if (context == null || context.alpha <= 0.0F) {
            return;
        }

        List<LostTalesMapMarkerData> markers = LostTalesClientMapMarkerStore.getAllMarkers();
        if (markers.isEmpty()) {
            return;
        }

        beginIconRender();
        try {
            for (LostTalesMapMarkerData marker : markers) {
                if (!shouldRenderStandaloneMarker(marker)) {
                    continue;
                }

                ScreenPosition position = transformMarker(context, marker);
                if (position == null || !isInsideMap(position.x, position.y, context)) {
                    continue;
                }

                boolean hover = isMouseOverIcon(position.x, position.y, mouseX, mouseY);
                drawMarkerIcon(context.minecraft, marker, position.x, position.y, context.alpha, hover);

                if (drawLabels && context.labelAlpha > 0.0F) {
                    drawLabel(context.fontRenderer, marker.getName(), position.x, position.y, context.labelAlpha, context);
                }
            }
        } catch (Throwable ignored) {
            // Standalone Lost Tales markers are decorative; never break the LOTR map.
        } finally {
            endIconRender();
        }
    }

    public static LostTalesMapMarkerData getHoveredStandaloneMarker(LOTRGuiMap gui, int mouseX, int mouseY) {
        RenderContext context = createRenderContext(gui);
        if (context == null || context.alpha <= 0.0F) {
            return null;
        }

        LostTalesMapMarkerData nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        List<LostTalesMapMarkerData> markers = LostTalesClientMapMarkerStore.getAllMarkers();
        for (LostTalesMapMarkerData marker : markers) {
            if (!shouldRenderStandaloneMarker(marker)) {
                continue;
            }
            ScreenPosition position = transformMarker(context, marker);
            if (position == null || !isInsideMap(position.x, position.y, context)) {
                continue;
            }
            double dx = position.x - mouseX;
            double dy = position.y - mouseY;
            double distanceSq = dx * dx + dy * dy;
            if (distanceSq <= (double) (HOVER_RADIUS * HOVER_RADIUS) && distanceSq < nearestDistanceSq) {
                nearest = marker;
                nearestDistanceSq = distanceSq;
            }
        }
        return nearest;
    }

    public static void renderStandaloneMarkerHoverTooltip(LOTRGuiMap gui, LostTalesMapMarkerData selectedMarker, int mouseX, int mouseY) {
        RenderContext context = createRenderContext(gui);
        if (context == null || context.alpha <= 0.0F) {
            return;
        }

        LostTalesMapMarkerData hoveredMarker = getHoveredStandaloneMarker(gui, mouseX, mouseY);
        if (hoveredMarker == null || sameMarker(hoveredMarker, selectedMarker)) {
            return;
        }

        renderLotrWaypointTooltip(context, new LostTalesMarkerWaypoint(hoveredMarker), false, mouseX, mouseY);
    }

    public static boolean renderStandaloneMarkerSelection(LOTRGuiMap gui, LostTalesMapMarkerData marker, int mouseX, int mouseY) {
        RenderContext context = createRenderContext(gui);
        if (context == null || marker == null || !shouldRenderStandaloneMarker(marker)) {
            return false;
        }

        ScreenPosition position = transformMarker(context, marker);
        if (position == null || !isInsideMap(position.x, position.y, context)) {
            return false;
        }

        LostTalesMarkerWaypoint tooltipWaypoint = new LostTalesMarkerWaypoint(marker);
        renderLotrWaypointTooltip(context, tooltipWaypoint, true, mouseX, mouseY);
        drawStandaloneFastTravelStatus(context);
        return true;
    }

    public static void clearLotrSelectedWaypoint(LOTRGuiMap gui) {
        if (gui == null || !ensureReflection() || selectedWaypointField == null) {
            return;
        }
        try {
            selectedWaypointField.set(gui, null);
        } catch (Throwable ignored) {
            // Selection clearing is best-effort only.
        }
    }

    private static LostTalesMapMarkerData getReplacementMarker(LOTRAbstractWaypoint waypoint) {
        if (waypoint == null) {
            return null;
        }

        List<LostTalesMapMarkerData> markers = LostTalesClientMapMarkerStore.getAllMarkers();
        if (markers.isEmpty()) {
            return null;
        }

        String waypointCode = normalizeKey(safeString(waypoint.getCodeName()));
        String waypointDisplay = normalizeKey(safeString(waypoint.getDisplayName()));

        for (LostTalesMapMarkerData marker : markers) {
            if (!isReplacementMarkerEligible(marker)) {
                continue;
            }
            String markerCode = normalizeKey(marker.getFastTravelWaypointCode());
            if (markerCode.length() > 0 && markerCode.equals(waypointCode)) {
                return marker;
            }
        }

        for (LostTalesMapMarkerData marker : markers) {
            if (!isReplacementMarkerEligible(marker) || marker.getFastTravelWaypointCode().length() > 0) {
                continue;
            }
            if (sameMapX(marker.getX(), waypoint.getXCoord()) && sameMapZ(marker.getZ(), waypoint.getZCoord())) {
                return marker;
            }
            String markerName = normalizeKey(marker.getName());
            if (markerName.length() > 0 && (markerName.equals(waypointCode) || markerName.equals(waypointDisplay))) {
                return marker;
            }
        }

        return null;
    }

    private static boolean isReplacementMarkerEligible(LostTalesMapMarkerData marker) {
        if (marker == null || !marker.hasFastTravel()) {
            return false;
        }
        if (marker.getDimensionId() != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return false;
        }
        return shouldShowLostTalesIcon(marker);
    }

    private static boolean shouldRenderStandaloneMarker(LostTalesMapMarkerData marker) {
        if (marker == null || marker.hasFastTravel()) {
            return false;
        }
        if (marker.getDimensionId() != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return false;
        }
        return shouldShowLostTalesIcon(marker);
    }

    private static boolean shouldShowLostTalesIcon(LostTalesMapMarkerData marker) {
        if (marker == null) {
            return false;
        }
        if (!marker.isDiscoverable()) {
            return true;
        }
        if (isDiscovered(marker)) {
            return true;
        }
        return !marker.isHiddenUntilDiscovered();
    }

    private static boolean isDiscovered(LostTalesMapMarkerData marker) {
        return marker != null && LostTalesClientQuestProgressStore.isMarkerDiscovered(marker.getId());
    }

    private static boolean isUndiscoveredButVisible(LostTalesMapMarkerData marker) {
        return marker != null && marker.isDiscoverable() && !isDiscovered(marker) && !marker.isHiddenUntilDiscovered();
    }

    private static boolean sameMarker(LostTalesMapMarkerData first, LostTalesMapMarkerData second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return safeString(first.getId()).equals(safeString(second.getId()));
    }

    private static boolean shouldRenderWaypoint(Minecraft minecraft, LOTRAbstractWaypoint waypoint, boolean includeHidden) {
        if (waypoint == null) {
            return false;
        }
        if (!includeHidden && !isWaypointVisibleInLotrToggles(waypoint)) {
            return false;
        }
        try {
            if (waypoint.isHidden() && (minecraft.thePlayer == null || !waypoint.hasPlayerUnlocked(minecraft.thePlayer))) {
                return false;
            }
        } catch (Throwable ignored) {
            return false;
        }
        return true;
    }

    private static boolean isWaypointVisibleInLotrToggles(LOTRAbstractWaypoint waypoint) {
        if (waypoint instanceof LOTRCustomWaypoint) {
            LOTRCustomWaypoint customWaypoint = (LOTRCustomWaypoint) waypoint;
            try {
                if (customWaypoint.isShared() && customWaypoint.isSharedHidden() && !LOTRGuiMap.showHiddenSWP) {
                    return false;
                }
            } catch (Throwable ignored) {
                return false;
            }
            return LOTRGuiMap.showCWP;
        }
        return LOTRGuiMap.showWP;
    }

    private static RenderContext createRenderContext(LOTRGuiMap gui) {
        if (gui == null) {
            return null;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null || minecraft.theWorld == null) {
            return null;
        }
        if (minecraft.theWorld.provider.dimensionId != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return null;
        }
        if (!ensureReflection()) {
            return null;
        }

        try {
            float zoomExp = getZoomExp(gui);
            float alpha = getWaypointAlpha(gui, zoomExp);
            int mapXMin = mapXMinField.getInt(null);
            int mapXMax = mapXMaxField.getInt(null);
            int mapYMin = mapYMinField.getInt(null);
            int mapYMax = mapYMaxField.getInt(null);
            return new RenderContext(gui, minecraft, minecraft.fontRenderer, zoomExp, alpha, getLabelAlpha(zoomExp), mapXMin, mapXMax, mapYMin, mapYMax);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ScreenPosition transformWaypoint(RenderContext context, LOTRAbstractWaypoint waypoint) {
        if (context == null || waypoint == null) {
            return null;
        }
        return transformMapCoords(context, waypoint.getXCoord(), waypoint.getZCoord());
    }

    private static ScreenPosition transformMarker(RenderContext context, LostTalesMapMarkerData marker) {
        if (context == null || marker == null) {
            return null;
        }
        return transformMapCoords(context, Math.round((float) marker.getX()), Math.round((float) marker.getZ()));
    }

    private static ScreenPosition transformMapCoords(RenderContext context, int mapX, int mapZ) {
        try {
            float[] pos = (float[]) transformCoordsMethod.invoke(context.gui, Float.valueOf((float) mapX), Float.valueOf((float) mapZ));
            if (pos == null || pos.length < 2) {
                return null;
            }
            return new ScreenPosition(pos[0], pos[1]);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static float getZoomExp(LOTRGuiMap gui) throws IllegalAccessException {
        return ((Float) zoomExpField.get(gui)).floatValue();
    }

    private static float getWaypointAlpha(LOTRGuiMap gui, float zoomExp) {
        float alpha = (zoomExp + 3.3F) / 2.2F;
        alpha = Math.min(alpha, 1.0F);
        if (!gui.enableZoomOutWPFading) {
            alpha = 1.0F;
        }
        if (alpha < 0.0F) {
            alpha = 0.0F;
        }
        return alpha;
    }

    private static float getLabelAlpha(float zoomExp) {
        float alpha = (zoomExp + 1.0F) / 4.0F;
        alpha = Math.min(alpha, 1.0F);
        if (alpha < 0.0F) {
            alpha = 0.0F;
        }
        return alpha;
    }

    private static boolean isInsideMap(float x, float y, RenderContext context) {
        return x >= context.mapXMin + EDGE_PADDING
                && x <= context.mapXMax - EDGE_PADDING
                && y >= context.mapYMin + EDGE_PADDING
                && y <= context.mapYMax - EDGE_PADDING;
    }

    private static boolean isMouseOverIcon(float screenX, float screenY, int mouseX, int mouseY) {
        double dx = screenX - mouseX;
        double dy = screenY - mouseY;
        return dx * dx + dy * dy <= (double) (HOVER_RADIUS * HOVER_RADIUS);
    }

    private static void beginIconRender() {
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void endIconRender() {
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
    }

    private static void drawMarkerIcon(Minecraft minecraft, LostTalesMapMarkerData marker, float centerX, float centerY, float alpha, boolean hover) {
        LostTalesCompassMarkerIcon icon = LostTalesCompassMarkerIcon.fromName(marker.getIconName());
        float[] color = isUndiscoveredButVisible(marker) ? LostTalesCompassMarker.parseColor("gray") : LostTalesCompassMarker.parseColor(marker.getColorName());
        float iconAlpha = isUndiscoveredButVisible(marker) ? alpha * 0.85F : alpha;
        int size = hover ? ICON_HOVER_DRAW_SIZE : ICON_DRAW_SIZE;
        drawFixedScreenIcon(minecraft, centerX, centerY, size, icon, color[0], color[1], color[2], iconAlpha);
    }

    /**
     * Draws the marker icon in fixed GUI pixels, matching LOTR waypoint dots.
     *
     * <p>The LOTR map zoom is already baked into {@code centerX}/{@code centerY}
     * by {@code transformCoords}. The icon itself must not be multiplied by
     * {@code zoomExp}, {@code zoomScale}, or {@code labelAlpha}; only the hover
     * state changes its size. This keeps icons from growing/shrinking when the
     * map is zoomed.</p>
     */
    private static void drawFixedScreenIcon(Minecraft minecraft, float centerX, float centerY, int size, LostTalesCompassMarkerIcon icon, float red, float green, float blue, float alpha) {
        float halfSize = (float) size / 2.0F;
        drawIcon(minecraft, centerX - halfSize, centerY - halfSize, size, icon, red, green, blue, alpha);
    }

    private static void drawIcon(Minecraft minecraft, float x, float y, int size, LostTalesCompassMarkerIcon icon, float red, float green, float blue, float alpha) {
        float u0 = (float) icon.getU() / (float) LostTalesCompassMarkerIcon.TEXTURE_WIDTH;
        float v0 = (float) icon.getV() / (float) LostTalesCompassMarkerIcon.TEXTURE_HEIGHT;
        float u1 = (float) (icon.getU() + LostTalesCompassMarkerIcon.WIDTH) / (float) LostTalesCompassMarkerIcon.TEXTURE_WIDTH;
        float v1 = (float) (icon.getV() + LostTalesCompassMarkerIcon.HEIGHT) / (float) LostTalesCompassMarkerIcon.TEXTURE_HEIGHT;

        minecraft.getTextureManager().bindTexture(LostTalesCompassMarkerIcon.TEXTURE);
        GL11.glColor4f(red, green, blue, alpha);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + size, 0.0D, u0, v1);
        tessellator.addVertexWithUV(x + size, y + size, 0.0D, u1, v1);
        tessellator.addVertexWithUV(x + size, y, 0.0D, u1, v0);
        tessellator.addVertexWithUV(x, y, 0.0D, u0, v0);
        tessellator.draw();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawLabel(FontRenderer fontRenderer, String label, float screenX, float screenY, float alpha, RenderContext context) {
        if (fontRenderer == null || label == null || label.length() == 0 || alpha <= 0.0F) {
            return;
        }

        int textWidth = fontRenderer.getStringWidth(label);
        float halfWidth = (float) textWidth * alpha / 2.0F;
        float top = screenY - 15.0F * alpha;
        float bottom = top + (float) fontRenderer.FONT_HEIGHT * alpha;
        if (screenX + halfWidth < (float) context.mapXMin
                || screenX - halfWidth > (float) context.mapXMax
                || bottom < (float) context.mapYMin
                || top > (float) context.mapYMax) {
            return;
        }

        int alphaInt = Math.max(4, Math.min(255, (int) (alpha * 0.8F * 255.0F)));
        int color = (alphaInt << 24) | 0x00FFFFFF;
        int shadowColor = (alphaInt << 24);

        GL11.glPushMatrix();
        GL11.glTranslatef(screenX, screenY, 0.0F);
        GL11.glScalef(alpha, alpha, alpha);
        try {
            int x = -textWidth / 2;
            int y = -15;
            fontRenderer.drawString(label, x + 1, y + 1, shadowColor);
            fontRenderer.drawString(label, x, y, color);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void renderLotrWaypointTooltip(RenderContext context, LOTRAbstractWaypoint waypoint, boolean selected, int mouseX, int mouseY) {
        if (context == null || waypoint == null || renderWaypointTooltipMethod == null) {
            return;
        }
        try {
            renderWaypointTooltipMethod.invoke(context.gui, waypoint, Boolean.valueOf(selected), Integer.valueOf(mouseX), Integer.valueOf(mouseY));
        } catch (Throwable ignored) {
            // If the private LOTR tooltip renderer changes, skip only this optional tooltip/selection box.
        }
    }

    private static void drawStandaloneFastTravelStatus(RenderContext context) {
        FontRenderer font = context.fontRenderer;
        if (font == null) {
            return;
        }

        String line = "Fast travel is not available for this location.";
        int width = context.mapXMax - context.mapXMin;
        int padding = 3;
        int height = padding * 3 + font.FONT_HEIGHT;
        int x0 = context.mapXMin;
        int y0 = context.mapYMax + 10;
        drawFancyRect(context, x0, y0, x0 + width, y0 + height);
        font.drawStringWithShadow(line, x0 + (width - font.getStringWidth(line)) / 2, y0 + padding, 0xFFFFFF);
    }

    private static void drawFancyRect(RenderContext context, int x0, int y0, int x1, int y1) {
        if (context == null || drawFancyRectMethod == null) {
            return;
        }
        try {
            drawFancyRectMethod.invoke(context.gui, Integer.valueOf(x0), Integer.valueOf(y0), Integer.valueOf(x1), Integer.valueOf(y1));
        } catch (Throwable ignored) {
            // Keep the map usable even if LOTR internals change.
        }
    }

    private static String getWaypointDisplayName(LOTRAbstractWaypoint waypoint, LostTalesMapMarkerData marker) {
        String displayName = safeString(waypoint.getDisplayName());
        if (displayName.length() > 0) {
            return displayName;
        }
        if (marker != null && marker.getName() != null && marker.getName().length() > 0) {
            return marker.getName();
        }
        return safeString(waypoint.getCodeName());
    }

    private static boolean ensureReflection() {
        if (reflectionReady) {
            return true;
        }
        if (reflectionFailed) {
            return false;
        }
        try {
            transformCoordsMethod = LOTRGuiMap.class.getDeclaredMethod("transformCoords", float.class, float.class);
            transformCoordsMethod.setAccessible(true);
            mapXMinField = LOTRGuiMap.class.getDeclaredField("mapXMin");
            mapXMaxField = LOTRGuiMap.class.getDeclaredField("mapXMax");
            mapYMinField = LOTRGuiMap.class.getDeclaredField("mapYMin");
            mapYMaxField = LOTRGuiMap.class.getDeclaredField("mapYMax");
            zoomExpField = LOTRGuiMap.class.getDeclaredField("zoomExp");
            selectedWaypointField = LOTRGuiMap.class.getDeclaredField("selectedWaypoint");
            renderWaypointTooltipMethod = LOTRGuiMap.class.getDeclaredMethod("renderWaypointTooltip", LOTRAbstractWaypoint.class, boolean.class, int.class, int.class);
            drawFancyRectMethod = LOTRGuiMap.class.getDeclaredMethod("drawFancyRect", int.class, int.class, int.class, int.class);
            mapXMinField.setAccessible(true);
            mapXMaxField.setAccessible(true);
            mapYMinField.setAccessible(true);
            mapYMaxField.setAccessible(true);
            zoomExpField.setAccessible(true);
            selectedWaypointField.setAccessible(true);
            renderWaypointTooltipMethod.setAccessible(true);
            drawFancyRectMethod.setAccessible(true);
            reflectionReady = true;
            return true;
        } catch (Throwable ignored) {
            reflectionFailed = true;
            return false;
        }
    }

    private static boolean sameMapX(double markerWorldX, int waypointMapX) {
        return Math.abs((double) LOTRWaypoint.worldToMapX(markerWorldX) - (double) waypointMapX) <= COORDINATE_MATCH_EPSILON;
    }

    private static boolean sameMapZ(double markerWorldZ, int waypointMapZ) {
        return Math.abs((double) LOTRWaypoint.worldToMapZ(markerWorldZ) - (double) waypointMapZ) <= COORDINATE_MATCH_EPSILON;
    }

    private static String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeKey(String value) {
        String normalized = safeString(value).toUpperCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') {
                builder.append(c);
            } else if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '_') {
                builder.append('_');
            }
        }
        while (builder.length() > 0 && builder.charAt(builder.length() - 1) == '_') {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.toString();
    }

    private static final class RenderContext {
        private final LOTRGuiMap gui;
        private final Minecraft minecraft;
        private final FontRenderer fontRenderer;
        private final float zoomExp;
        private final float alpha;
        private final float labelAlpha;
        private final int mapXMin;
        private final int mapXMax;
        private final int mapYMin;
        private final int mapYMax;

        private RenderContext(LOTRGuiMap gui, Minecraft minecraft, FontRenderer fontRenderer, float zoomExp, float alpha, float labelAlpha, int mapXMin, int mapXMax, int mapYMin, int mapYMax) {
            this.gui = gui;
            this.minecraft = minecraft;
            this.fontRenderer = fontRenderer;
            this.zoomExp = zoomExp;
            this.alpha = alpha;
            this.labelAlpha = labelAlpha;
            this.mapXMin = mapXMin;
            this.mapXMax = mapXMax;
            this.mapYMin = mapYMin;
            this.mapYMax = mapYMax;
        }
    }

    private static final class LostTalesMarkerWaypoint implements LOTRAbstractWaypoint {
        private final LostTalesMapMarkerData marker;
        private final int mapX;
        private final int mapZ;
        private final int y;

        private LostTalesMarkerWaypoint(LostTalesMapMarkerData marker) {
            this.marker = marker;
            this.mapX = Math.round((float) marker.getX());
            this.mapZ = Math.round((float) marker.getZ());
            this.y = Math.round((float) marker.getY());
        }

        @Override
        public double getX() {
            return this.marker.getX();
        }

        @Override
        public double getY() {
            return this.marker.getY();
        }

        @Override
        public int getXCoord() {
            return this.mapX;
        }

        @Override
        public int getZCoord() {
            return this.mapZ;
        }

        @Override
        public int getYCoord(World world, int x, int z) {
            return this.y;
        }

        @Override
        public int getYCoordSaved() {
            return this.y;
        }

        @Override
        public String getCodeName() {
            return this.marker.getId();
        }

        @Override
        public String getDisplayName() {
            return this.marker.getName();
        }

        @Override
        public String getLoreText(EntityPlayer player) {
            return this.marker.getDescription();
        }

        @Override
        public boolean hasPlayerUnlocked(EntityPlayer player) {
            return true;
        }

        @Override
        public LOTRAbstractWaypoint.WaypointLockState getLockState(EntityPlayer player) {
            return LOTRAbstractWaypoint.WaypointLockState.STANDARD_UNLOCKED;
        }

        @Override
        public boolean isHidden() {
            return false;
        }

        @Override
        public int getID() {
            return this.marker.getId() == null ? -1 : -Math.abs(this.marker.getId().hashCode());
        }
    }

    private static final class ScreenPosition {
        private final float x;
        private final float y;

        private ScreenPosition(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
