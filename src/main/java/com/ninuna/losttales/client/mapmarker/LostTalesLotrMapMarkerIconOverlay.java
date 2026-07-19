package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.cache.LostTalesClientMobAggroCache;
import com.ninuna.losttales.client.cache.LostTalesClientMobAggroCache.TrackedEnemy;
import com.ninuna.losttales.client.party.ClientPartyTrackingCache;
import com.ninuna.losttales.client.party.ClientPartyStateCache;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarker;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import com.ninuna.losttales.network.packet.LostTalesMobAggroSyncPacket;
import com.ninuna.losttales.party.sync.PartyMemberSnapshot;
import com.ninuna.losttales.party.sync.PartyStateSnapshot;
import com.ninuna.losttales.party.sync.PartyTrackedMemberSnapshot;
import com.ninuna.losttales.world.map.waypoint.LostTalesMapCoordinateHelper;
import com.ninuna.losttales.party.sync.PartyTrackingSnapshot;
import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.LOTRDimension;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.world.map.LOTRAbstractWaypoint;
import lotr.common.world.map.LOTRCustomWaypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/**
 * Client-only renderer for Lost Tales icons on the LOTR map.
 *
 * <p>LOTR keeps handling discovered waypoint selection. This helper owns every
 * mapped icon and removes undiscovered markers from LOTR's tooltip/selection
 * pass so an entered region can reveal only an anonymous question mark.</p>
 */
public final class LostTalesLotrMapMarkerIconOverlay {
    private static final int ICON_DRAW_SIZE = 13;
    private static final int ICON_HOVER_DRAW_SIZE = 16;
    private static final int HOVER_RADIUS = ICON_HOVER_DRAW_SIZE / 2;
    private static final int EDGE_PADDING = ICON_HOVER_DRAW_SIZE / 2 + 1;
    private static final float MAP_MARKER_HOVER_SCALE =
            (float) ICON_HOVER_DRAW_SIZE / (float) ICON_DRAW_SIZE;
    private static final float PLAYER_HEAD_DRAW_SIZE = 9.0F;
    private static final float PLAYER_HEAD_HOVER_DRAW_SIZE =
            PLAYER_HEAD_DRAW_SIZE * MAP_MARKER_HOVER_SCALE;
    private static final double COORDINATE_MATCH_EPSILON = 0.5D;

    private static Method transformCoordsMethod;
    private static Field mapXMinField;
    private static Field mapXMaxField;
    private static Field mapYMinField;
    private static Field mapYMaxField;
    private static Field zoomExpField;
    private static Field selectedWaypointField;
    private static Field mouseXCoordField;
    private static Field mouseZCoordField;
    private static Field isMouseWithinMapField;
    private static Field hasOverlayField;
    private static Field loadingConquestGridField;
    private static Field playerLocationsField;
    private static Field playerLocationXField;
    private static Field playerLocationZField;
    private static Method renderWaypointTooltipMethod;
    private static Method drawFancyRectMethod;
    private static boolean reflectionReady;
    private static boolean reflectionFailed;

    private LostTalesLotrMapMarkerIconOverlay() {}

    public static List<LOTRAbstractWaypoint> getWaypointsForLotrRender(
            List<LOTRAbstractWaypoint> waypoints, int pass) {
        if (waypoints == null || waypoints.isEmpty()) {
            return waypoints;
        }

        List<LOTRAbstractWaypoint> filtered = new ArrayList<LOTRAbstractWaypoint>(waypoints.size());
        for (LOTRAbstractWaypoint waypoint : waypoints) {
            LostTalesMapMarkerData marker = getMappedMarker(waypoint);
            if (marker != null) {
                // Lost Tales owns both the icon and hover hitbox for every
                // mapped waypoint. Mixing JSON icon coordinates with LOTR's
                // native pass caused visible markers that could not be hovered
                // or selected. Native selected-waypoint and packet behavior is
                // restored explicitly after our hit test.
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
                if (marker == null || !shouldRenderReplacementWaypoint(
                        waypoint, marker, includeHidden)) {
                    continue;
                }

                // The JSON marker is also the compass source of truth. Using
                // its exact world coordinates keeps both displays aligned,
                // even for positions between LOTR's 128-block map pixels.
                ScreenPosition position = transformMarker(context, marker);
                if (position == null || !isInsideMap(position.x, position.y, context)) {
                    continue;
                }

                boolean hover = isMouseOverIcon(position.x, position.y, mouseX, mouseY);
                drawMarkerIcon(context.minecraft, marker, position.x, position.y, context.alpha, hover);

                if (drawLabels && context.labelAlpha > 0.0F) {
                    drawLabel(context.fontRenderer,
                            getWaypointDisplayName(waypoint, marker),
                            position.x, position.y,
                            context.labelAlpha, context);
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

        List<LostTalesMapMarkerData> markers = getVisibleStandaloneMarkers();
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
                    drawLabel(context.fontRenderer,
                            getMarkerDisplayName(marker),
                            position.x, position.y,
                            context.labelAlpha, context);
                }
            }
        } catch (Throwable ignored) {
            // Standalone Lost Tales markers are decorative; never break the LOTR map.
        } finally {
            endIconRender();
        }
    }

    /**
     * Replaces native account heads with synchronized roleplaying-character
     * portraits. Native markers remain the safe fallback when no appearance
     * is available, and still own tracking and hover text.
     */
    public static void renderRoleplayPlayerHeads(
            LOTRGuiMap gui, int mouseX, int mouseY) {
        RenderContext context = createRenderContext(gui);
        if (context == null) {
            return;
        }
        beginIconRender();
        try {
            Set<UUID> renderedOwners = new HashSet<UUID>();
            // LOTR renders the local player outside playerLocations. Cover
            // that separate path first so solo players receive the same
            // roleplaying portrait as remote party members.
            EntityPlayer localPlayer = context.minecraft.thePlayer;
            UUID localOwnerId = localPlayer == null
                    ? null : localPlayer.getUniqueID();
            if (localPlayer != null && localOwnerId != null) {
                renderRoleplayPlayerHead(
                        context, localOwnerId,
                        localPlayer.posX, localPlayer.posZ,
                        mouseX, mouseY);
                renderedOwners.add(localOwnerId);
            }

            // Lost Tales parties are independent of LOTR fellowships, so
            // their synchronized members may not exist in LOTR's native
            // playerLocations map. Render those authorized positions here.
            PartyStateSnapshot partyState =
                    ClientPartyStateCache.getSnapshot();
            PartyTrackingSnapshot tracking =
                    ClientPartyTrackingCache.getMatching(partyState);
            if (tracking != null && partyState != null
                    && partyState.getParty() != null) {
                for (PartyTrackedMemberSnapshot tracked
                        : tracking.getTrackedMembers()) {
                    if (tracked.getDimensionId()
                            != LOTRDimension.MIDDLE_EARTH.dimensionID) {
                        continue;
                    }
                    PartyMemberSnapshot member = partyState.getParty()
                            .getMember(tracked.getCharacterId());
                    if (member == null
                            || !renderedOwners.add(member.getOwnerId())) {
                        continue;
                    }
                    renderRoleplayPlayerHead(
                            context, member.getOwnerId(),
                            tracked.getX(), tracked.getZ(),
                            mouseX, mouseY);
                }
            }

            Map<?, ?> locations = (Map<?, ?>)
                    playerLocationsField.get(null);
            if (locations == null) {
                return;
            }
            for (Map.Entry<?, ?> entry : locations.entrySet()) {
                if (!(entry.getKey() instanceof UUID)
                        || entry.getValue() == null) {
                    continue;
                }
                UUID ownerId = (UUID) entry.getKey();
                if (!renderedOwners.add(ownerId)) {
                    continue;
                }
                Object location = entry.getValue();
                double worldX = playerLocationXField.getDouble(location);
                double worldZ = playerLocationZField.getDouble(location);
                renderRoleplayPlayerHead(
                        context, ownerId, worldX, worldZ,
                        mouseX, mouseY);
            }
        } catch (Throwable ignored) {
            // LOTR internals are compatibility-only; the normal map stays usable.
        } finally {
            endIconRender();
        }
    }

    private static void renderRoleplayPlayerHead(
            RenderContext context, UUID ownerId,
            double worldX, double worldZ, int mouseX, int mouseY) {
        ScreenPosition position = transformWorldCoords(
                context, worldX, worldZ);
        if (position == null) {
            return;
        }
        // Mirror LOTRGuiMap.renderPlayerIcon exactly: player coordinates are
        // rounded, then clamped five pixels inside the map. This keeps the
        // replacement directly over the native head even beyond map edges.
        float centerX = clampPlayerIconCoordinate(
                Math.round(position.x), context.mapXMin, context.mapXMax)
                + 0.5F;
        float centerY = clampPlayerIconCoordinate(
                Math.round(position.y), context.mapYMin, context.mapYMax)
                + 0.5F;
        boolean hovered = isMouseOverPlayerHead(
                centerX, centerY, mouseX, mouseY);
        float size = hovered
                ? PLAYER_HEAD_HOVER_DRAW_SIZE
                : PLAYER_HEAD_DRAW_SIZE;
        LostTalesCharacterHeadIconRenderer.drawRoleplayHead(
                context.minecraft, ownerId,
                centerX - size * 0.5F + 1.0F,
                centerY - size * 0.5F + 1.0F,
                size, 0.15F, 0.75F);
        LostTalesCharacterHeadIconRenderer.drawRoleplayHead(
                context.minecraft, ownerId,
                centerX - size * 0.5F,
                centerY - size * 0.5F,
                size, 1.0F, 1.0F);
    }

    private static int clampPlayerIconCoordinate(
            int coordinate, int minimum, int maximum) {
        return Math.max(minimum + 5,
                Math.min(maximum - 6, coordinate));
    }

    /** Returns the world X/Z under a valid, unobstructed LOTR-map click. */
    public static int[] getMapClickWorldPosition(LOTRGuiMap gui) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (gui == null || minecraft == null || minecraft.theWorld == null
                || minecraft.theWorld.provider.dimensionId
                != LOTRDimension.MIDDLE_EARTH.dimensionID
                || !ensureReflection()) {
            return null;
        }
        try {
            if (!isMouseWithinMapField.getBoolean(gui)
                    || hasOverlayField.getBoolean(gui)
                    || loadingConquestGridField.getBoolean(gui)) {
                return null;
            }
            return new int[] {
                    mouseXCoordField.getInt(gui),
                    mouseZCoordField.getInt(gui)
            };
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isMouseOverPlayerHead(
            float screenX, float screenY, int mouseX, int mouseY) {
        float hoverRadius = PLAYER_HEAD_HOVER_DRAW_SIZE * 0.5F;
        return Math.abs(screenX - mouseX) <= hoverRadius
                && Math.abs(screenY - mouseY) <= hoverRadius;
    }

    /** Renders non-interactive, non-persistent enemies from the shared combat snapshot. */
    public static void renderTransientEnemyMarkers(LOTRGuiMap gui, int mouseX, int mouseY, boolean drawLabels) {
        if (!LostTalesConfig.showHostileMapMarkers) {
            return;
        }

        RenderContext context = createRenderContext(gui);
        if (context == null || context.alpha <= 0.0F) {
            return;
        }

        List<TrackedEnemy> trackedEnemies = LostTalesClientMobAggroCache.getTrackedEnemies();
        if (trackedEnemies.isEmpty()) {
            return;
        }

        double displayRadiusSq = getTransientEnemyDisplayRadiusSq();
        if (displayRadiusSq < 0.0D) {
            return;
        }

        beginIconRender();
        try {
            for (TrackedEnemy trackedEnemy : trackedEnemies) {
                EnemyMarkerPosition enemy = resolveVisibleTransientEnemy(
                        context, trackedEnemy, displayRadiusSq);
                if (enemy == null) {
                    continue;
                }

                ScreenPosition position = transformWorldCoords(
                        context, enemy.x, enemy.z);
                if (position == null || !isInsideMap(position.x, position.y, context)) {
                    continue;
                }

                boolean hover = isMouseOverIcon(position.x, position.y, mouseX, mouseY);
                int iconSize = hover ? ICON_HOVER_DRAW_SIZE : ICON_DRAW_SIZE;
                drawFixedScreenIcon(context.minecraft, position.x, position.y, iconSize,
                        LostTalesCompassMarkerIcon.HOSTILE, 1.0F, 1.0F, 1.0F, context.alpha);
            }
        } catch (Throwable ignored) {
            // Transient markers are visual-only; never break the LOTR map.
        } finally {
            endIconRender();
        }
    }

    /** Draws a standard LOTR-style tooltip for the enemy currently under the mouse. */
    public static void renderTransientEnemyMarkerHoverTooltip(LOTRGuiMap gui, int mouseX, int mouseY) {
        if (!LostTalesConfig.showHostileMapMarkers) {
            return;
        }

        RenderContext context = createRenderContext(gui);
        if (context == null || context.alpha <= 0.0F) {
            return;
        }

        EnemyMarkerPosition hoveredEnemy = getHoveredTransientEnemy(
                context, mouseX, mouseY);
        if (hoveredEnemy == null) {
            return;
        }

        renderLotrWaypointTooltip(context,
                new LostTalesEntityWaypoint(hoveredEnemy),
                false, mouseX, mouseY);
    }

    public static LostTalesMapMarkerData getHoveredStandaloneMarker(
            LOTRGuiMap gui, int mouseX, int mouseY) {
        return getHoveredStandaloneMarker(gui, mouseX, mouseY, null);
    }

    /**
     * Returns the marker under the mouse, preferring a requested marker ID
     * when several icons overlap at the same map location.
     */
    public static LostTalesMapMarkerData getHoveredStandaloneMarker(
            LOTRGuiMap gui, int mouseX, int mouseY,
            String preferredMarkerId) {
        RenderContext context = createRenderContext(gui);
        if (context == null || context.alpha <= 0.0F) {
            return null;
        }

        LostTalesMapMarkerData nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        List<LostTalesMapMarkerData> markers = getVisibleStandaloneMarkers();
        for (LostTalesMapMarkerData marker : markers) {
            if (!shouldRenderStandaloneMarker(marker)) {
                continue;
            }
            ScreenPosition position = transformMarker(context, marker);
            if (position == null
                    || !isInsideMap(position.x, position.y, context)) {
                continue;
            }
            double dx = position.x - mouseX;
            double dy = position.y - mouseY;
            double distanceSq = dx * dx + dy * dy;
            if (distanceSq > (double) (HOVER_RADIUS * HOVER_RADIUS)) {
                continue;
            }
            if (preferredMarkerId != null
                    && preferredMarkerId.equals(marker.getId())) {
                return marker;
            }
            if (distanceSq < nearestDistanceSq) {
                nearest = marker;
                nearestDistanceSq = distanceSq;
            }
        }
        return nearest;
    }

    public static LostTalesMapMarkerData getHoveredLockedMappedMarker(
            LOTRGuiMap gui, int mouseX, int mouseY) {
        RenderContext context = createRenderContext(gui);
        if (context == null || context.alpha <= 0.0F) {
            return null;
        }
        LostTalesMapMarkerData nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        for (LostTalesMapMarkerData marker
                : LostTalesClientMapMarkerStore.getAllMarkers()) {
            if (!isLockedMappedMarkerVisible(marker)) {
                continue;
            }
            ScreenPosition position = transformMarker(context, marker);
            if (position == null
                    || !isInsideMap(position.x, position.y, context)) {
                continue;
            }
            double dx = position.x - mouseX;
            double dy = position.y - mouseY;
            double distanceSq = dx * dx + dy * dy;
            if (distanceSq <= (double)(HOVER_RADIUS * HOVER_RADIUS)
                    && distanceSq < nearestDistanceSq) {
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

    /** Draws native LOTR hover text at the exact replacement-icon hitbox. */
    public static void renderMappedWaypointHoverTooltip(
            LOTRGuiMap gui, int mouseX, int mouseY) {
        RenderContext context = createRenderContext(gui);
        if (context == null || context.alpha <= 0.0F) {
            return;
        }
        LOTRAbstractWaypoint waypoint = getHoveredMappedWaypoint(
                gui, mouseX, mouseY);
        if (waypoint == null || isSelectedWaypoint(gui, waypoint)) {
            return;
        }
        renderLotrWaypointTooltip(
                context, waypoint, false, mouseX, mouseY);
    }

    /** Finds a visible, non-private replacement using its rendered position. */
    public static LOTRAbstractWaypoint getHoveredMappedWaypoint(
            LOTRGuiMap gui, int mouseX, int mouseY) {
        RenderContext context = createRenderContext(gui);
        if (context == null || context.alpha <= 0.0F) {
            return null;
        }
        LOTRPlayerData playerData = LOTRLevelData.getData(
                context.minecraft.thePlayer);
        if (playerData == null) {
            return null;
        }
        List<LOTRAbstractWaypoint> waypoints =
                playerData.getAllAvailableWaypoints();
        LOTRAbstractWaypoint nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        for (LOTRAbstractWaypoint waypoint : waypoints) {
            LostTalesMapMarkerData marker = getReplacementMarker(waypoint);
            if (marker == null || isLockedMappedMarkerVisible(marker)
                    || !shouldRenderReplacementWaypoint(
                    waypoint, marker, false)) {
                continue;
            }
            ScreenPosition position = transformMarker(context, marker);
            if (position == null
                    || !isInsideMap(position.x, position.y, context)) {
                continue;
            }
            double dx = position.x - mouseX;
            double dy = position.y - mouseY;
            double distanceSq = dx * dx + dy * dy;
            if (distanceSq <= (double)(HOVER_RADIUS * HOVER_RADIUS)
                    && distanceSq < nearestDistanceSq) {
                nearest = waypoint;
                nearestDistanceSq = distanceSq;
            }
        }
        return nearest;
    }

    /** Hands our replacement hit back to LOTR's normal selection/FT flow. */
    public static boolean selectLotrWaypoint(
            LOTRGuiMap gui, LOTRAbstractWaypoint waypoint) {
        if (gui == null || waypoint == null || !ensureReflection()
                || selectedWaypointField == null) {
            return false;
        }
        try {
            selectedWaypointField.set(gui, waypoint);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void renderLockedMappedMarkerHoverTooltip(
            LOTRGuiMap gui, LostTalesMapMarkerData selectedMarker,
            int mouseX, int mouseY) {
        RenderContext context = createRenderContext(gui);
        if (context == null || context.alpha <= 0.0F) {
            return;
        }
        LostTalesMapMarkerData hoveredMarker =
                getHoveredLockedMappedMarker(gui, mouseX, mouseY);
        if (hoveredMarker == null || sameMarker(hoveredMarker, selectedMarker)) {
            return;
        }
        renderLotrWaypointTooltip(context,
                new LostTalesMarkerWaypoint(hoveredMarker),
                false, mouseX, mouseY);
    }

    public static boolean renderCustomMarkerSelection(
            LOTRGuiMap gui, LostTalesMapMarkerData marker,
            int mouseX, int mouseY) {
        RenderContext context = createRenderContext(gui);
        if (context == null || marker == null
                || !isSelectableCustomMarker(marker)) {
            return false;
        }

        ScreenPosition position = transformMarker(context, marker);
        if (position == null || !isInsideMap(position.x, position.y, context)) {
            return false;
        }

        LostTalesMarkerWaypoint tooltipWaypoint = new LostTalesMarkerWaypoint(marker);
        renderLotrWaypointTooltip(context, tooltipWaypoint, true, mouseX, mouseY);
        drawCustomFastTravelStatus(context, marker);
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

    private static boolean isSelectedWaypoint(
            LOTRGuiMap gui, LOTRAbstractWaypoint waypoint) {
        if (gui == null || waypoint == null || !ensureReflection()
                || selectedWaypointField == null) {
            return false;
        }
        try {
            return selectedWaypointField.get(gui) == waypoint;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Clears a native selection that became private after a character swap. */
    public static void clearUndiscoveredLotrSelection(LOTRGuiMap gui) {
        if (gui == null || !ensureReflection() || selectedWaypointField == null) {
            return;
        }
        try {
            Object selected = selectedWaypointField.get(gui);
            if (!(selected instanceof LOTRAbstractWaypoint)) {
                return;
            }
            LostTalesMapMarkerData marker = getMappedMarker(
                    (LOTRAbstractWaypoint)selected);
            if (marker != null && marker.isDiscoverable()
                    && !isDiscovered(marker)) {
                selectedWaypointField.set(gui, null);
            }
        } catch (Throwable ignored) {
            // Selection cleanup is best-effort; the server policy still denies it.
        }
    }

    private static List<LostTalesMapMarkerData> getVisibleStandaloneMarkers() {
        List<LostTalesMapMarkerData> shared =
                LostTalesClientMapMarkerStore.getAllMarkers();
        List<LostTalesMapMarkerData> party =
                ClientPartyTrackingCache.getMapMarkers();
        if (party.isEmpty()) {
            return shared;
        }
        ArrayList<LostTalesMapMarkerData> combined =
                new ArrayList<LostTalesMapMarkerData>(
                        shared.size() + party.size());
        combined.addAll(shared);
        combined.addAll(party);
        return combined;
    }

    private static boolean containsVisibleStandaloneMarker(
            LostTalesMapMarkerData target) {
        if (target == null || target.getId() == null) {
            return false;
        }
        for (LostTalesMapMarkerData marker : getVisibleStandaloneMarkers()) {
            if (sameMarker(marker, target)) {
                return true;
            }
        }
        return false;
    }

    private static LostTalesMapMarkerData getReplacementMarker(LOTRAbstractWaypoint waypoint) {
        LostTalesMapMarkerData marker = getMappedMarker(waypoint);
        return isReplacementMarkerEligible(marker) ? marker : null;
    }

    private static LostTalesMapMarkerData getMappedMarker(LOTRAbstractWaypoint waypoint) {
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
            if (!isWaypointMappingEligible(marker)) {
                continue;
            }
            String markerCode = normalizeKey(marker.getFastTravelWaypointCode());
            if (markerCode.length() > 0 && markerCode.equals(waypointCode)) {
                return marker;
            }
        }

        for (LostTalesMapMarkerData marker : markers) {
            if (!isWaypointMappingEligible(marker)
                    || marker.getFastTravelWaypointCode().length() > 0) {
                continue;
            }
            if (sameWorldX(marker.getX(), waypoint.getXCoord()) && sameWorldZ(marker.getZ(), waypoint.getZCoord())) {
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
        return isWaypointMappingEligible(marker) && shouldShowLostTalesIcon(marker);
    }

    private static boolean isWaypointMappingEligible(LostTalesMapMarkerData marker) {
        if (marker == null || !marker.hasFastTravel()) {
            return false;
        }
        if (marker.getDimensionId() != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return false;
        }
        return true;
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
        return LostTalesClientMapMarkerVisibility.isMapVisible(marker);
    }

    private static boolean isDiscovered(LostTalesMapMarkerData marker) {
        return LostTalesClientMapMarkerVisibility.isDiscovered(marker);
    }

    private static boolean isUndiscoveredButVisible(LostTalesMapMarkerData marker) {
        return marker != null && marker.isDiscoverable()
                && !isDiscovered(marker)
                && !marker.isHiddenUntilDiscovered()
                && isVisibilityRegionUnlocked(marker);
    }

    private static boolean isLockedMappedMarkerVisible(
            LostTalesMapMarkerData marker) {
        return isWaypointMappingEligible(marker)
                && isUndiscoveredButVisible(marker);
    }

    private static boolean isSelectableCustomMarker(
            LostTalesMapMarkerData marker) {
        if (isLockedMappedMarkerVisible(marker)) {
            return true;
        }
        return shouldRenderStandaloneMarker(marker)
                && containsVisibleStandaloneMarker(marker);
    }

    private static boolean isVisibilityRegionUnlocked(
            LostTalesMapMarkerData marker) {
        return LostTalesClientMapMarkerVisibility
                .isUndiscoveredRegionVisible(marker);
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

    private static boolean shouldRenderReplacementWaypoint(
            LOTRAbstractWaypoint waypoint,
            LostTalesMapMarkerData marker, boolean includeHidden) {
        if (waypoint == null
                || !includeHidden && !isWaypointVisibleInLotrToggles(waypoint)) {
            return false;
        }
        return shouldShowLostTalesIcon(marker);
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

    private static ScreenPosition transformMarker(RenderContext context, LostTalesMapMarkerData marker) {
        if (context == null || marker == null) {
            return null;
        }
        return transformMapCoords(context, Math.round((float) marker.getX()), Math.round((float) marker.getZ()));
    }

    private static ScreenPosition transformMapCoords(RenderContext context, int mapX, int mapZ) {
        return transformWorldCoords(context, (double) mapX, (double) mapZ);
    }

    private static ScreenPosition transformWorldCoords(RenderContext context, double worldX, double worldZ) {
        try {
            float[] pos = (float[]) transformCoordsMethod.invoke(context.gui, Float.valueOf((float) worldX), Float.valueOf((float) worldZ));
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

    private static EnemyMarkerPosition getHoveredTransientEnemy(
            RenderContext context, int mouseX, int mouseY) {
        List<TrackedEnemy> trackedEnemies =
                LostTalesClientMobAggroCache.getTrackedEnemies();
        if (trackedEnemies.isEmpty()) {
            return null;
        }
        double displayRadiusSq = getTransientEnemyDisplayRadiusSq();
        if (displayRadiusSq < 0.0D) {
            return null;
        }

        EnemyMarkerPosition nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        for (TrackedEnemy trackedEnemy : trackedEnemies) {
            EnemyMarkerPosition enemy = resolveVisibleTransientEnemy(
                    context, trackedEnemy, displayRadiusSq);
            if (enemy == null) {
                continue;
            }
            ScreenPosition position = transformWorldCoords(
                    context, enemy.x, enemy.z);
            if (position == null
                    || !isInsideMap(position.x, position.y, context)) {
                continue;
            }
            double dx = position.x - mouseX;
            double dy = position.y - mouseY;
            double distanceSq = dx * dx + dy * dy;
            if (distanceSq <= (double) (HOVER_RADIUS * HOVER_RADIUS)
                    && distanceSq < nearestDistanceSq) {
                nearest = enemy;
                nearestDistanceSq = distanceSq;
            }
        }
        return nearest;
    }

    private static EnemyMarkerPosition resolveVisibleTransientEnemy(
            RenderContext context, TrackedEnemy trackedEnemy,
            double displayRadiusSq) {
        if (context == null || trackedEnemy == null
                || context.minecraft.theWorld == null
                || context.minecraft.thePlayer == null) {
            return null;
        }

        double x = trackedEnemy.getX();
        double y = trackedEnemy.getY();
        double z = trackedEnemy.getZ();
        String name = trackedEnemy.getName();
        Entity entity = context.minecraft.theWorld.getEntityByID(
                trackedEnemy.getEntityId());
        if (entity instanceof EntityLivingBase
                && entity != context.minecraft.thePlayer) {
            EntityLivingBase living = (EntityLivingBase) entity;
            if (!living.isEntityAlive() || living.isDead
                    || living.dimension
                    != context.minecraft.thePlayer.dimension) {
                return null;
            }
            x = living.posX;
            y = living.posY;
            z = living.posZ;
            name = living.getCommandSenderName();
        }

        if (!trackedEnemy.isSharedFromParty()) {
            double dx = x - context.minecraft.thePlayer.posX;
            double dy = y - context.minecraft.thePlayer.posY;
            double dz = z - context.minecraft.thePlayer.posZ;
            if (dx * dx + dy * dy + dz * dz > displayRadiusSq) {
                return null;
            }
        }
        return new EnemyMarkerPosition(
                trackedEnemy.getEntityId(), name, x, y, z);
    }

    private static double getTransientEnemyDisplayRadiusSq() {
        int serverRadius = LostTalesClientMobAggroCache.getServerTrackingRadius();
        if (serverRadius < LostTalesMobAggroSyncPacket.MIN_TRACKING_RADIUS) {
            return -1.0D;
        }
        double configuredRadius = Math.max(8.0D, LostTalesConfig.hostileMapMarkerDisplayRadius);
        double displayRadius = Math.min(configuredRadius, (double) serverRadius);
        return displayRadius * displayRadius;
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
        boolean undiscovered = isUndiscoveredButVisible(marker);
        LostTalesCompassMarkerIcon icon = undiscovered
                ? LostTalesCompassMarkerIcon.UNDISCOVERED
                : LostTalesCompassMarkerIcon.fromName(marker.getIconName());
        // White is a neutral texture tint. Undiscovered icons should retain
        // their artwork instead of being recolored gray or washed out.
        float[] color = LostTalesCompassMarker.parseColor(
                undiscovered ? "white" : marker.getColorName());
        int size = hover ? ICON_HOVER_DRAW_SIZE : ICON_DRAW_SIZE;
        // Match Minecraft's one-pixel text shadow without modifying the
        // configured icon tint itself.
        drawFixedScreenIcon(minecraft, centerX + 1.0F, centerY + 1.0F,
                size, icon, 0.0F, 0.0F, 0.0F, alpha * 0.75F);
        drawFixedScreenIcon(minecraft, centerX, centerY, size, icon,
                color[0], color[1], color[2], alpha);
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

    private static void drawCustomFastTravelStatus(
            RenderContext context, LostTalesMapMarkerData marker) {
        FontRenderer font = context.fontRenderer;
        if (font == null) {
            return;
        }

        String line = isLockedMappedMarkerVisible(marker)
                ? "Discover this location before fast travelling here."
                : "Fast travel is not available for this location.";
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
        if (isUndiscoveredButVisible(marker)) {
            return "?";
        }
        String displayName = safeString(waypoint.getDisplayName());
        if (displayName.length() > 0) {
            return displayName;
        }
        if (marker != null && marker.getName() != null && marker.getName().length() > 0) {
            return marker.getName();
        }
        return safeString(waypoint.getCodeName());
    }

    private static String getMarkerDisplayName(LostTalesMapMarkerData marker) {
        return isUndiscoveredButVisible(marker) ? "?" : marker.getName();
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
            mouseXCoordField = LOTRGuiMap.class.getDeclaredField("mouseXCoord");
            mouseZCoordField = LOTRGuiMap.class.getDeclaredField("mouseZCoord");
            isMouseWithinMapField = LOTRGuiMap.class.getDeclaredField("isMouseWithinMap");
            hasOverlayField = LOTRGuiMap.class.getDeclaredField("hasOverlay");
            loadingConquestGridField = LOTRGuiMap.class.getDeclaredField("loadingConquestGrid");
            playerLocationsField = LOTRGuiMap.class.getDeclaredField("playerLocations");
            Class<?> playerLocationClass = Class.forName(
                    "lotr.client.gui.LOTRGuiMap$PlayerLocationInfo");
            playerLocationXField = playerLocationClass
                    .getDeclaredField("posX");
            playerLocationZField = playerLocationClass
                    .getDeclaredField("posZ");
            renderWaypointTooltipMethod = LOTRGuiMap.class.getDeclaredMethod("renderWaypointTooltip", LOTRAbstractWaypoint.class, boolean.class, int.class, int.class);
            drawFancyRectMethod = LOTRGuiMap.class.getDeclaredMethod("drawFancyRect", int.class, int.class, int.class, int.class);
            mapXMinField.setAccessible(true);
            mapXMaxField.setAccessible(true);
            mapYMinField.setAccessible(true);
            mapYMaxField.setAccessible(true);
            zoomExpField.setAccessible(true);
            selectedWaypointField.setAccessible(true);
            mouseXCoordField.setAccessible(true);
            mouseZCoordField.setAccessible(true);
            isMouseWithinMapField.setAccessible(true);
            hasOverlayField.setAccessible(true);
            loadingConquestGridField.setAccessible(true);
            playerLocationsField.setAccessible(true);
            playerLocationXField.setAccessible(true);
            playerLocationZField.setAccessible(true);
            renderWaypointTooltipMethod.setAccessible(true);
            drawFancyRectMethod.setAccessible(true);
            reflectionReady = true;
            return true;
        } catch (Throwable throwable) {
            reflectionFailed = true;
            FMLLog.warning("[%s] LOTR map marker compatibility disabled: expected LOTR Legacy v36.15 map members were unavailable (%s)",
                    LostTalesMetaData.MOD_ID, throwable.toString());
            return false;
        }
    }

    private static boolean sameWorldX(double markerWorldX, int waypointWorldX) {
        return Math.abs(markerWorldX - (double) waypointWorldX) <= COORDINATE_MATCH_EPSILON;
    }

    private static boolean sameWorldZ(double markerWorldZ, int waypointWorldZ) {
        return Math.abs(markerWorldZ - (double) waypointWorldZ) <= COORDINATE_MATCH_EPSILON;
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
        private final int worldX;
        private final int worldZ;
        private final double mapImageX;
        private final double mapImageY;
        private final int y;

        private LostTalesMarkerWaypoint(LostTalesMapMarkerData marker) {
            this.marker = marker;
            this.worldX = Math.round((float) marker.getX());
            this.worldZ = Math.round((float) marker.getZ());
            this.mapImageX = LostTalesMapCoordinateHelper
                    .worldToMapImageX(marker.getX());
            this.mapImageY = LostTalesMapCoordinateHelper
                    .worldToMapImageZ(marker.getZ());
            this.y = Math.round((float) marker.getY());
        }

        @Override
        public double getX() {
            return this.mapImageX;
        }

        @Override
        public double getY() {
            return this.mapImageY;
        }

        @Override
        public int getXCoord() {
            return this.worldX;
        }

        @Override
        public int getZCoord() {
            return this.worldZ;
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
            return getMarkerDisplayName(this.marker);
        }

        @Override
        public String getLoreText(EntityPlayer player) {
            return isUndiscoveredButVisible(this.marker)
                    ? null : this.marker.getDescription();
        }

        @Override
        public boolean hasPlayerUnlocked(EntityPlayer player) {
            return !isUndiscoveredButVisible(this.marker);
        }

        @Override
        public LOTRAbstractWaypoint.WaypointLockState getLockState(EntityPlayer player) {
            return isUndiscoveredButVisible(this.marker)
                    ? LOTRAbstractWaypoint.WaypointLockState.STANDARD_LOCKED
                    : LOTRAbstractWaypoint.WaypointLockState.STANDARD_UNLOCKED;
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

    /** Temporary adapter used only while LOTR renders an enemy hover tooltip. */
    private static final class LostTalesEntityWaypoint implements LOTRAbstractWaypoint {
        private final int entityId;
        private final String displayName;
        private final int worldX;
        private final int worldY;
        private final int worldZ;
        private final double mapImageX;
        private final double mapImageY;

        private LostTalesEntityWaypoint(EnemyMarkerPosition enemy) {
            this.entityId = enemy.entityId;
            this.displayName = enemy.name;
            this.worldX = Math.round((float) enemy.x);
            this.worldY = Math.round((float) enemy.y);
            this.worldZ = Math.round((float) enemy.z);
            this.mapImageX = LostTalesMapCoordinateHelper
                    .worldToMapImageX(enemy.x);
            this.mapImageY = LostTalesMapCoordinateHelper
                    .worldToMapImageZ(enemy.z);
        }

        @Override
        public double getX() {
            return this.mapImageX;
        }

        @Override
        public double getY() {
            return this.mapImageY;
        }

        @Override
        public int getXCoord() {
            return this.worldX;
        }

        @Override
        public int getZCoord() {
            return this.worldZ;
        }

        @Override
        public int getYCoord(World world, int x, int z) {
            return this.worldY;
        }

        @Override
        public int getYCoordSaved() {
            return this.worldY;
        }

        @Override
        public String getCodeName() {
            return "losttales_enemy_" + this.entityId;
        }

        @Override
        public String getDisplayName() {
            return this.displayName;
        }

        @Override
        public String getLoreText(EntityPlayer player) {
            return "";
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
            return -this.entityId - 1;
        }
    }

    private static final class EnemyMarkerPosition {
        private final int entityId;
        private final String name;
        private final double x;
        private final double y;
        private final double z;

        private EnemyMarkerPosition(int entityId, String name,
                                    double x, double y, double z) {
            this.entityId = entityId;
            this.name = name == null || name.length() == 0
                    ? "Enemy" : name;
            this.x = x;
            this.y = y;
            this.z = z;
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
