package com.ninuna.losttales.client.mapmarker;

import com.mojang.authlib.GameProfile;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.cache.LostTalesClientMobAggroCache;
import com.ninuna.losttales.client.cache.LostTalesClientMobAggroCache.TrackedEnemy;
import com.ninuna.losttales.client.character.ClientRoleplayCharacterIdentityHook;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import net.minecraft.client.gui.ScaledResolution;
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
    private static final float HIGHLIGHT_SCALE = 16.0F / ICON_DRAW_SIZE;
    private static final int ICON_HOVER_DRAW_SIZE =
            Math.round(ICON_DRAW_SIZE * HIGHLIGHT_SCALE);
    private static final int ICON_COMPANION_HOVER_DRAW_SIZE = 15;
    private static final int HOVER_RADIUS = ICON_HOVER_DRAW_SIZE / 2;
    private static final float UNGROUPED_CLOSE_ZOOM_EXP = 3.99F;
    private static final float GROUPING_FADE_PER_SECOND = 4.0F;
    private static final float GROUPING_ENABLE_ZOOM_EXP = 3.95F;
    private static final float GROUPING_DISABLE_ZOOM_EXP = 3.99F;
    private static final float GROUPING_FAR_ZOOM_EXP = -1.0F;
    private static final float GROUPING_NEAR_ZOOM_EXP = 3.0F;
    /** Four wheel steps before minimum zoom, grouping stops consolidating. */
    private static final float GROUPING_FAR_FREEZE_ZOOM_EXP = -2.0F;
    private static final float GROUPING_FAR_RADIUS_SCALE = 0.50F;
    private static final float GROUPING_NEAR_RADIUS_SCALE = 0.30F;
    private static final float GROUP_COMPANION_ALPHA = 0.72F;
    private static final float PLAYER_HEAD_DRAW_SIZE = 9.0F;
    private static final float PLAYER_HEAD_HOVER_DRAW_SIZE =
            highlightedSize(PLAYER_HEAD_DRAW_SIZE);
    private static final float NATIVE_WAYPOINT_DRAW_SIZE = 4.0F;
    private static final float NATIVE_WAYPOINT_HOVER_SCALE =
            highlightedSize(NATIVE_WAYPOINT_DRAW_SIZE)
                    / NATIVE_WAYPOINT_DRAW_SIZE;
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
    private static Field playerLocationProfileField;
    private static Field playerLocationXField;
    private static Field playerLocationZField;
    private static Method renderWaypointTooltipMethod;
    private static Method drawFancyRectMethod;
    private static boolean reflectionReady;
    private static boolean reflectionFailed;
    private static long nativeMappingCacheTick = Long.MIN_VALUE;
    private static EntityPlayer nativeMappingCachePlayer;
    private static Object nativeMappingCacheSnapshot;
    private static Set<String> nativeMappedMarkerIds =
            Collections.emptySet();
    private static GroupingFrame groupingFrame = GroupingFrame.empty();
    private static final Map<String, Float> groupingFadeAlpha =
            new HashMap<String, Float>();
    /**
     * Presentation-only source anchors for markers emerging from a group.
     * Values are marker IDs, never altered world coordinates.
     */
    private static final Map<String, String> groupingMotionOrigins =
            new HashMap<String, String>();
    private static final Map<String, Float> groupingCompanionSlots =
            new HashMap<String, Float>();
    private static LOTRGuiMap groupingFadeGui;
    private static long groupingFadeLastNanos;
    private static LOTRGuiMap roleplayPlayerHeadFrameGui;
    private static List<RoleplayPlayerHead> roleplayPlayerHeadFrame =
            Collections.emptyList();
    private static final LostTalesMapHoverFocus hoverFocus =
            new LostTalesMapHoverFocus();
    private static LOTRGuiMap hoverFocusGui;
    private static HoverCandidate activeHoverCandidate;
    private static LOTRGuiMap suppressedLotrSelectionGui;
    private static LOTRAbstractWaypoint suppressedLotrSelection;

    private LostTalesLotrMapMarkerIconOverlay() {}

    public static List<LOTRAbstractWaypoint> getWaypointsForLotrRender(
            List<LOTRAbstractWaypoint> waypoints, int pass) {
        if (waypoints == null || waypoints.isEmpty()) {
            return waypoints;
        }

        List<LOTRAbstractWaypoint> filtered = new ArrayList<LOTRAbstractWaypoint>(waypoints.size());
        for (LOTRAbstractWaypoint waypoint : waypoints) {
            LostTalesMapMarkerData marker = getMappedMarker(waypoint);
            if (marker != null || hasDecorativeMapping(waypoint)) {
                // Lost Tales owns both the icon and hover hitbox for every
                // mapped waypoint. Mixing JSON icon coordinates with LOTR's
                // native pass caused visible markers that could not be hovered
                // or selected. Native selected-waypoint and packet behavior is
                // restored explicitly after our hit test.
                continue;
            }
            if (!LostTalesMapLegendRegistry.isWaypointVisible(waypoint)) {
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
     * Renders mapped and standalone markers through one screen-space grouping
     * pass so the two marker sources cannot draw overlapping representatives.
     */
    public static void renderGroupedMarkers(
            LOTRGuiMap gui, List<LOTRAbstractWaypoint> waypoints,
            int mouseX, int mouseY, boolean drawLabels,
            boolean includeHidden) {
        RenderContext context = createRenderContext(gui);
        if (context == null || context.alpha <= 0.0F) {
            groupingFrame = GroupingFrame.empty();
            return;
        }
        List<MarkerRenderCandidate> candidates;
        try {
            candidates = collectMarkerCandidates(
                    context, waypoints, includeHidden);
        } catch (Throwable ignored) {
            groupingFrame = GroupingFrame.empty();
            return;
        }
        long signature = calculateGroupingSignature(
                context, candidates, drawLabels, includeHidden);
        GroupingFrame previousFrame = groupingFrame;
        LostTalesMapMarkerGrouping.Result result;
        if (groupingFrame.gui == gui
                && groupingFrame.signature == signature) {
            result = groupingFrame.result;
        } else {
            ArrayList<LostTalesMapMarkerGrouping.Entry> entries =
                    new ArrayList<LostTalesMapMarkerGrouping.Entry>(
                            candidates.size());
            float decisionScale = groupingDecisionScaleForZoom(
                    context.zoomExp);
            for (MarkerRenderCandidate candidate : candidates) {
                entries.add(candidate.groupingEntry
                        .withScaledSpacing(decisionScale));
            }
            Map<String, String> previous = groupingFrame.gui == gui
                    ? groupingFrame.result.getMembership()
                    : Collections.<String, String>emptyMap();
            boolean groupingEnabled = shouldGroupAtZoom(gui, context.zoomExp);
            boolean preservePreviousGroups = previousFrame.gui == gui
                    && context.zoomExp <= previousFrame.zoomExp + 0.0001F;
            result = groupingEnabled
                    ? LostTalesMapMarkerGrouping.group(
                            entries, previous,
                            groupingRadiusScaleForZoom(context.zoomExp),
                            preservePreviousGroups)
                    : LostTalesMapMarkerGrouping.ungroup(entries);
        }
        updateGroupingMotionOrigins(
                gui, previousFrame, candidates, result);
        boolean groupingEnabled = result.getMembership().size() > 0
                || shouldGroupAtZoom(gui, context.zoomExp);
        groupingFrame = new GroupingFrame(
                gui, signature, candidates, result, groupingEnabled,
                context.zoomExp);
        if (candidates.isEmpty()) {
            return;
        }
        boolean[] representatives = representativeFlags(
                candidates.size(), result);
        updateGroupingFade(gui, candidates, representatives, result);

        beginIconRender();
        try {
            // Markers visually travel into or out of the representative while
            // fading. This is presentation-only: no waypoint, marker, or
            // waystone coordinate is ever modified.
            ArrayList<LostTalesMapMarkerGrouping.Entry> renderEntries =
                    new ArrayList<LostTalesMapMarkerGrouping.Entry>(
                            candidates.size());
            for (MarkerRenderCandidate candidate : candidates) {
                renderEntries.add(candidate.groupingEntry);
            }
            List<Integer> bottomToTop =
                    LostTalesMapMarkerGrouping.bottomToTop(renderEntries);
            for (Integer indexValue : bottomToTop) {
                int index = indexValue.intValue();
                if (representatives[index]) {
                    continue;
                }
                MarkerRenderCandidate candidate = candidates.get(index);
                float fade = getGroupingFade(candidate.marker.getId());
                float companionSlot = getGroupingCompanionSlot(
                        candidate.marker.getId());
                float iconAlpha = groupedIconAlpha(
                        fade, companionSlot, context.alpha);
                if (iconAlpha <= 0.001F) {
                    continue;
                }
                ScreenPosition renderPosition = groupingRenderPosition(
                        candidate, candidates, result, fade);
                drawMarkerIcon(context.minecraft, candidate.marker,
                        renderPosition.x, renderPosition.y,
                        context.alpha * iconAlpha, false);
                if (drawLabels && context.labelAlpha > 0.0F) {
                    drawLabel(context.fontRenderer,
                            candidate.displayName,
                            renderPosition.x, renderPosition.y,
                            context.labelAlpha * fade, context);
                }
            }
            List<LostTalesMapMarkerGrouping.Group> groups =
                    result.getGroups();
            for (int groupIndex = groups.size() - 1;
                 groupIndex >= 0; groupIndex--) {
                LostTalesMapMarkerGrouping.Group group =
                        groups.get(groupIndex);
                MarkerRenderCandidate candidate = candidates.get(
                        group.getRepresentativeIndex());
                float fade = getGroupingFade(candidate.marker.getId());
                ScreenPosition renderPosition = groupingRenderPosition(
                        candidate, candidates, result, fade);
                drawMarkerIcon(context.minecraft, candidate.marker,
                        renderPosition.x, renderPosition.y,
                        context.alpha * fade, false);
                if (drawLabels && context.labelAlpha > 0.0F) {
                    drawLabel(context.fontRenderer,
                            candidate.displayName,
                            renderPosition.x, renderPosition.y,
                            context.labelAlpha * fade, context);
                }
                if (group.size() > 1 && drawLabels
                        && context.labelAlpha > 0.0F) {
                    float groupFade = groupLabelFade(group, candidates);
                    drawGroupLabel(context.fontRenderer,
                            group.getAdditionalLabel(),
                            renderPosition.x, renderPosition.y,
                            context.labelAlpha * groupFade, context);
                }
            }
        } catch (Throwable ignored) {
            // Optional marker grouping must never make the LOTR map unusable.
        } finally {
            endIconRender();
        }
    }

    public static void clearGrouping(LOTRGuiMap gui) {
        if (gui == null || groupingFrame.gui == gui) {
            groupingFrame = GroupingFrame.empty();
            groupingFadeAlpha.clear();
            groupingMotionOrigins.clear();
            groupingCompanionSlots.clear();
            groupingFadeGui = null;
            groupingFadeLastNanos = 0L;
        }
        clearHoverFocus(gui);
        clearSuppressedLotrSelection(gui);
    }

    private static void updateGroupingMotionOrigins(
            LOTRGuiMap gui, GroupingFrame previousFrame,
            List<MarkerRenderCandidate> candidates,
            LostTalesMapMarkerGrouping.Result currentResult) {
        if (previousFrame.gui != gui) {
            groupingMotionOrigins.clear();
        }
        Map<String, String> previousMembership = previousFrame.gui == gui
                ? previousFrame.result.getMembership()
                : Collections.<String, String>emptyMap();
        Map<String, String> currentMembership =
                currentResult.getMembership();
        HashSet<String> activeIds = new HashSet<String>();
        for (MarkerRenderCandidate candidate : candidates) {
            String id = candidate.marker.getId();
            activeIds.add(id);
            String previousRepresentative = previousMembership.get(id);
            String currentRepresentative = currentMembership.get(id);
            boolean wasHidden = previousRepresentative != null
                    && !id.equals(previousRepresentative);
            boolean isVisible = currentRepresentative == null
                    || id.equals(currentRepresentative);
            if (wasHidden && isVisible) {
                groupingMotionOrigins.put(id, previousRepresentative);
            } else if (previousRepresentative != null
                    && currentRepresentative != null
                    && !previousRepresentative.equals(
                    currentRepresentative)
                    && !id.equals(previousRepresentative)) {
                String previousForCurrentRepresentative =
                        previousMembership.get(currentRepresentative);
                boolean currentRepresentativeIsSplittingOut =
                        previousForCurrentRepresentative != null
                        && !currentRepresentative.equals(
                        previousForCurrentRepresentative);
                if (currentRepresentativeIsSplittingOut) {
                    // The new child representative owns this companion. It
                    // will follow the representative's animated position.
                    groupingMotionOrigins.remove(id);
                } else {
                    // On zoom-out, keep the old fan together while the
                    // complete stack eases into its new representative.
                    groupingMotionOrigins.put(id,
                            previousRepresentative);
                }
            } else if (!isVisible && previousRepresentative == null) {
                groupingMotionOrigins.remove(id);
            }
        }
        groupingMotionOrigins.keySet().retainAll(activeIds);
    }

    private static boolean[] representativeFlags(
            int candidateCount,
            LostTalesMapMarkerGrouping.Result result) {
        boolean[] representatives = new boolean[candidateCount];
        for (LostTalesMapMarkerGrouping.Group group : result.getGroups()) {
            int index = group.getRepresentativeIndex();
            if (index >= 0 && index < representatives.length) {
                representatives[index] = true;
            }
        }
        return representatives;
    }

    private static LostTalesMapMarkerGrouping.Group findGroupForCandidate(
            LostTalesMapMarkerGrouping.Result result, int candidateIndex) {
        if (result == null) {
            return null;
        }
        for (LostTalesMapMarkerGrouping.Group group : result.getGroups()) {
            if (group.getMemberIndices().contains(
                    Integer.valueOf(candidateIndex))) {
                return group;
            }
        }
        return null;
    }

    private static void updateGroupingFade(
            LOTRGuiMap gui, List<MarkerRenderCandidate> candidates,
            boolean[] representatives,
            LostTalesMapMarkerGrouping.Result result) {
        long now = System.nanoTime();
        boolean reset = groupingFadeGui != gui;
        float step = reset || groupingFadeLastNanos == 0L
                ? 1.0F
                : Math.min(1.0F,
                        (now - groupingFadeLastNanos) / 1000000000.0F
                                * GROUPING_FADE_PER_SECOND);
        if (reset) {
            groupingFadeAlpha.clear();
            groupingCompanionSlots.clear();
            groupingFadeGui = gui;
        }
        HashSet<String> activeIds = new HashSet<String>();
        for (int index = 0; index < candidates.size(); index++) {
            String id = candidates.get(index).marker.getId();
            activeIds.add(id);
            float target = representatives[index] ? 1.0F : 0.0F;
            Float stored = groupingFadeAlpha.get(id);
            float current = stored == null ? target : stored.floatValue();
            if (current < target) {
                current = Math.min(target, current + step);
            } else if (current > target) {
                current = Math.max(target, current - step);
            }
            groupingFadeAlpha.put(id, Float.valueOf(current));
            LostTalesMapMarkerGrouping.Group group =
                    findGroupForCandidate(result, index);
            float targetSlot = representatives[index] || group == null
                    ? 0.0F : group.getCompanionSide(index);
            Float storedSlot = groupingCompanionSlots.get(id);
            float currentSlot = storedSlot == null
                    ? targetSlot : storedSlot.floatValue();
            if (currentSlot < targetSlot) {
                currentSlot = Math.min(
                        targetSlot, currentSlot + step);
            } else if (currentSlot > targetSlot) {
                currentSlot = Math.max(
                        targetSlot, currentSlot - step);
            }
            groupingCompanionSlots.put(
                    id, Float.valueOf(currentSlot));
            if (target >= 1.0F && current >= 0.999F) {
                groupingMotionOrigins.remove(id);
            }
        }
        groupingFadeAlpha.keySet().retainAll(activeIds);
        groupingCompanionSlots.keySet().retainAll(activeIds);
        ArrayList<String> completedMerges = new ArrayList<String>();
        for (Map.Entry<String, String> motion
                : groupingMotionOrigins.entrySet()) {
            String markerId = motion.getKey();
            String originId = motion.getValue();
            if (result.getMembership().containsKey(markerId)
                    && getGroupingFade(originId) <= 0.001F) {
                completedMerges.add(markerId);
            }
        }
        for (String markerId : completedMerges) {
            groupingMotionOrigins.remove(markerId);
        }
        groupingFadeLastNanos = now;
    }

    private static float getGroupingFade(String markerId) {
        Float value = groupingFadeAlpha.get(markerId);
        float linear = value == null ? 1.0F : value.floatValue();
        return linear * linear * (3.0F - 2.0F * linear);
    }

    private static float getGroupingCompanionSlot(String markerId) {
        Float value = groupingCompanionSlots.get(markerId);
        if (value == null) {
            return 0.0F;
        }
        float linear = value.floatValue();
        float sign = linear < 0.0F ? -1.0F : 1.0F;
        float magnitude = Math.abs(linear);
        magnitude = magnitude * magnitude
                * (3.0F - 2.0F * magnitude);
        return sign * magnitude;
    }

    static float groupedIconAlpha(
            float visibility, float companionSlot,
            float mapMarkerAlpha) {
        float clampedVisibility = Math.max(0.0F,
                Math.min(1.0F, visibility));
        float companionStrength = Math.max(0.0F,
                Math.min(1.0F, Math.abs(companionSlot)));
        float alpha = clampedVisibility
                + (1.0F - clampedVisibility)
                * companionStrength * GROUP_COMPANION_ALPHA;
        if (companionStrength <= 0.0F) {
            return alpha;
        }
        float globalAlpha = Math.max(0.0F,
                Math.min(1.0F, mapMarkerAlpha));
        float farZoomFloor = companionStrength
                * (GROUP_COMPANION_ALPHA
                + (1.0F - GROUP_COMPANION_ALPHA)
                * (1.0F - globalAlpha));
        return Math.max(alpha, farZoomFloor);
    }

    private static ScreenPosition groupingRenderPosition(
            MarkerRenderCandidate candidate,
            List<MarkerRenderCandidate> candidates,
            LostTalesMapMarkerGrouping.Result result,
            float visibility) {
        float companionSlot = getGroupingCompanionSlot(
                candidate.marker.getId());
        return groupingRenderPosition(candidate, candidates, result,
                visibility,
                companionSlot
                        * LostTalesMapMarkerGrouping.COMPANION_OFFSET_X,
                Math.abs(companionSlot)
                        * LostTalesMapMarkerGrouping.COMPANION_OFFSET_Y);
    }

    private static ScreenPosition groupingRenderPosition(
            MarkerRenderCandidate candidate,
            List<MarkerRenderCandidate> candidates,
            LostTalesMapMarkerGrouping.Result result,
            float visibility, float groupedOffsetX,
            float groupedOffsetY) {
        String id = candidate.marker.getId();
        String representative = result.getMembership().get(id);
        String transitionOrigin = groupingMotionOrigins.get(id);
        String originId = transitionOrigin != null
                ? transitionOrigin
                : representative != null && !id.equals(representative)
                        ? representative : null;
        if (originId == null || id.equals(originId)) {
            return candidate.position;
        }
        ScreenPosition originPosition = resolveGroupingOriginPosition(
                originId, candidates, result);
        if (originPosition == null) {
            groupingMotionOrigins.remove(id);
            return candidate.position;
        }
        return new ScreenPosition(
                LostTalesMapMarkerGrouping.transitionCoordinate(
                        originPosition.x + groupedOffsetX,
                        candidate.position.x, visibility),
                LostTalesMapMarkerGrouping.transitionCoordinate(
                        originPosition.y + groupedOffsetY,
                        candidate.position.y, visibility));
    }

    private static ScreenPosition resolveGroupingOriginPosition(
            String originId,
            List<MarkerRenderCandidate> candidates,
            LostTalesMapMarkerGrouping.Result result) {
        return resolveGroupingOriginPosition(
                originId, candidates, result,
                new HashSet<String>());
    }

    private static ScreenPosition resolveGroupingOriginPosition(
            String originId,
            List<MarkerRenderCandidate> candidates,
            LostTalesMapMarkerGrouping.Result result,
            Set<String> resolving) {
        if (originId == null || !resolving.add(originId)) {
            return null;
        }
        MarkerRenderCandidate origin = findCandidateById(
                candidates, originId);
        if (origin == null) {
            return null;
        }
        String transitionOrigin = groupingMotionOrigins.get(originId);
        if (transitionOrigin != null
                && !originId.equals(transitionOrigin)) {
            ScreenPosition transitionPosition =
                    resolveGroupingOriginPosition(
                            transitionOrigin, candidates,
                            result, resolving);
            if (transitionPosition != null) {
                float visibility = getGroupingFade(originId);
                return new ScreenPosition(
                        LostTalesMapMarkerGrouping.transitionCoordinate(
                                transitionPosition.x,
                                origin.position.x, visibility),
                        LostTalesMapMarkerGrouping.transitionCoordinate(
                                transitionPosition.y,
                                origin.position.y, visibility));
            }
        }
        String destinationId = result.getMembership().get(originId);
        if (destinationId == null || originId.equals(destinationId)) {
            return origin.position;
        }
        ScreenPosition destination = resolveGroupingOriginPosition(
                destinationId, candidates, result, resolving);
        if (destination == null) {
            return origin.position;
        }
        float visibility = getGroupingFade(originId);
        return new ScreenPosition(
                LostTalesMapMarkerGrouping.transitionCoordinate(
                        destination.x, origin.position.x,
                        visibility),
                LostTalesMapMarkerGrouping.transitionCoordinate(
                        destination.y, origin.position.y,
                        visibility));
    }

    private static MarkerRenderCandidate findCandidateById(
            List<MarkerRenderCandidate> candidates, String id) {
        for (MarkerRenderCandidate candidate : candidates) {
            if (id.equals(candidate.marker.getId())) {
                return candidate;
            }
        }
        return null;
    }

    private static int findCandidateIndexById(
            List<MarkerRenderCandidate> candidates, String id) {
        if (candidates == null || id == null) {
            return -1;
        }
        for (int index = 0; index < candidates.size(); index++) {
            if (id.equals(candidates.get(index).marker.getId())) {
                return index;
            }
        }
        return -1;
    }

    private static boolean shouldGroupAtZoom(
            LOTRGuiMap gui, float zoomExp) {
        if (groupingFrame.gui != gui) {
            return zoomExp < UNGROUPED_CLOSE_ZOOM_EXP;
        }
        return groupingFrame.groupingEnabled
                ? zoomExp < GROUPING_DISABLE_ZOOM_EXP
                : zoomExp < GROUPING_ENABLE_ZOOM_EXP;
    }

    static float groupingRadiusScaleForZoom(float zoomExp) {
        if (zoomExp > GROUPING_NEAR_ZOOM_EXP) {
            float closeProgress = (zoomExp - GROUPING_NEAR_ZOOM_EXP)
                    / (UNGROUPED_CLOSE_ZOOM_EXP
                    - GROUPING_NEAR_ZOOM_EXP);
            closeProgress = Math.max(0.0F,
                    Math.min(1.0F, closeProgress));
            return GROUPING_NEAR_RADIUS_SCALE
                    * (1.0F - closeProgress);
        }
        float progress = (zoomExp - GROUPING_FAR_ZOOM_EXP)
                / (GROUPING_NEAR_ZOOM_EXP - GROUPING_FAR_ZOOM_EXP);
        progress = Math.max(0.0F, Math.min(1.0F, progress));
        return GROUPING_FAR_RADIUS_SCALE
                + (GROUPING_NEAR_RADIUS_SCALE
                - GROUPING_FAR_RADIUS_SCALE) * progress;
    }

    /**
     * Below the far threshold, evaluate overlap at the threshold's apparent
     * spacing. Icons may continue fading and moving with the map, but no new
     * mega-stack can form simply because the final zoom steps compress them.
     */
    static float groupingDecisionScaleForZoom(float zoomExp) {
        if (zoomExp >= GROUPING_FAR_FREEZE_ZOOM_EXP) {
            return 1.0F;
        }
        return (float)Math.pow(2.0D,
                GROUPING_FAR_FREEZE_ZOOM_EXP - zoomExp);
    }

    private static float groupLabelFade(
            LostTalesMapMarkerGrouping.Group group,
            List<MarkerRenderCandidate> candidates) {
        float alpha = 1.0F;
        int representative = group.getRepresentativeIndex();
        for (Integer memberValue : group.getMemberIndices()) {
            int member = memberValue.intValue();
            if (member != representative) {
                alpha = Math.min(alpha, 1.0F - getGroupingFade(
                        candidates.get(member).marker.getId()));
            }
        }
        return Math.max(0.0F, alpha);
    }

    private static List<MarkerRenderCandidate> collectMarkerCandidates(
            RenderContext context, List<LOTRAbstractWaypoint> waypoints,
            boolean includeHidden) {
        ArrayList<MarkerRenderCandidate> candidates =
                new ArrayList<MarkerRenderCandidate>();
        HashSet<String> includedIds = new HashSet<String>();
        if (waypoints != null) {
            for (LOTRAbstractWaypoint waypoint : waypoints) {
                LostTalesMapMarkerData marker =
                        getReplacementMarker(waypoint);
                if (marker == null || !shouldRenderReplacementWaypoint(
                        waypoint, marker, includeHidden)) {
                    continue;
                }
                addMarkerCandidate(context, candidates, includedIds,
                        marker, waypoint,
                        getWaypointDisplayName(waypoint, marker));
            }
        }
        for (LostTalesMapMarkerData marker : getVisibleStandaloneMarkers()) {
            if (shouldRenderStandaloneMarker(marker)) {
                addMarkerCandidate(context, candidates, includedIds,
                        marker, null, getMarkerDisplayName(marker));
            }
        }
        return candidates;
    }

    private static void addMarkerCandidate(
            RenderContext context,
            List<MarkerRenderCandidate> candidates, Set<String> includedIds,
            LostTalesMapMarkerData marker, LOTRAbstractWaypoint waypoint,
            String displayName) {
        String markerId = safeString(marker.getId());
        if (markerId.length() == 0 || !includedIds.add(markerId)) {
            return;
        }
        ScreenPosition position = transformMarker(context, marker);
        if (position == null
                || !isInsideMap(position.x, position.y, context)) {
            return;
        }
        // Group by the artwork that would actually cover another icon. Long
        // waypoint names must not collapse otherwise distant map locations.
        float iconExtent = (ICON_DRAW_SIZE + 1.0F) / 2.0F;
        float left = position.x - iconExtent;
        float right = position.x + iconExtent;
        float top = position.y - iconExtent;
        float bottom = position.y + iconExtent;
        LostTalesMapMarkerGrouping.Entry entry =
                new LostTalesMapMarkerGrouping.Entry(
                        markerId, displayName, marker.getPriority(),
                        left, top, right, bottom);
        candidates.add(new MarkerRenderCandidate(
                marker, waypoint, position, displayName, entry));
    }

    private static long calculateGroupingSignature(
            RenderContext context,
            List<MarkerRenderCandidate> candidates,
            boolean drawLabels, boolean includeHidden) {
        long hash = 1125899906842597L;
        hash = hash * 31L + System.identityHashCode(context.gui);
        hash = hash * 31L + System.identityHashCode(context.fontRenderer);
        hash = hash * 31L + context.gui.width;
        hash = hash * 31L + context.gui.height;
        hash = hash * 31L + context.minecraft.displayWidth;
        hash = hash * 31L + context.minecraft.displayHeight;
        hash = hash * 31L + Float.floatToIntBits(context.zoomExp);
        hash = hash * 31L + context.mapXMin;
        hash = hash * 31L + context.mapXMax;
        hash = hash * 31L + context.mapYMin;
        hash = hash * 31L + context.mapYMax;
        hash = hash * 31L + (drawLabels ? 1 : 0);
        hash = hash * 31L + (includeHidden ? 1 : 0);
        for (MarkerRenderCandidate candidate : candidates) {
            hash = hash * 31L + candidate.marker.getId().hashCode();
            hash = hash * 31L + candidate.displayName.hashCode();
            hash = hash * 31L + candidate.marker.getPriority();
            hash = hash * 31L
                    + Float.floatToIntBits(candidate.position.x);
            hash = hash * 31L
                    + Float.floatToIntBits(candidate.position.y);
            hash = hash * 31L + (candidate.waypoint == null ? 0 : 1);
        }
        return hash * 31L + candidates.size();
    }

    /**
     * Draws resource-defined markers over LOTR's animated menu map. This view
     * is intentionally decorative: it does not read player discovery,
     * ownership, region, or server-synchronized marker state. Definitions
     * explicitly hidden until discovery are omitted because showing them here
     * would reveal locations that the author marked as secret.
     */
    public static void renderDecorativeBackgroundMarkers(
            LOTRGuiMap gui, boolean sepia) {
        RenderContext context = createMenuRenderContext(gui);
        if (context == null) {
            return;
        }
        beginMenuMapClipping(context);
        try {
            beginIconRender();
            try {
                for (LostTalesMapMarkerData marker :
                        LostTalesClientMapMarkerStore
                                .getDecorativeMarkers()) {
                    if (!shouldRenderDecorativeMarker(marker)) {
                        continue;
                    }
                    ScreenPosition position =
                            transformMarker(context, marker);
                    if (position == null || !iconOverlapsMap(
                            position.x, position.y,
                            ICON_DRAW_SIZE + 2, context)) {
                        continue;
                    }
                    drawDecorativeMarkerIcon(
                            context.minecraft, marker,
                            position.x, position.y, sepia);
                }
            } finally {
                endIconRender();
            }
        } catch (Throwable ignored) {
            // The menu remains usable if LOTR changes its fake-map internals.
        } finally {
            endMenuMapClipping();
        }
    }

    static boolean shouldRenderDecorativeMarker(
            LostTalesMapMarkerData marker) {
        return marker != null
                && !marker.isHiddenUntilDiscovered()
                && marker.getDimensionId()
                        == LOTRDimension.MIDDLE_EARTH.dimensionID;
    }

    /**
     * Replaces native account heads with synchronized roleplaying-character
     * portraits. Native markers remain the safe fallback when no appearance
     * is available, and still own tracking and hover text.
     */
    public static void renderRoleplayPlayerHeads(
            LOTRGuiMap gui, int mouseX, int mouseY) {
        roleplayPlayerHeadFrameGui = gui;
        roleplayPlayerHeadFrame = Collections.emptyList();
        RenderContext context = createRenderContext(gui);
        if (context == null) {
            return;
        }
        ArrayList<RoleplayPlayerHead> renderedHeads =
                new ArrayList<RoleplayPlayerHead>();
        roleplayPlayerHeadFrame = renderedHeads;
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
                        ClientRoleplayCharacterIdentityHook
                                .resolveMapPlayerName(
                                localPlayer.getGameProfile()),
                        localPlayer.posX, localPlayer.posZ,
                        renderedHeads);
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
                            member.getCharacterName(),
                            tracked.getX(), tracked.getZ(),
                            renderedHeads);
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
                Object profileValue =
                        playerLocationProfileField.get(location);
                GameProfile profile = profileValue instanceof GameProfile
                        ? (GameProfile)profileValue : null;
                double worldX = playerLocationXField.getDouble(location);
                double worldZ = playerLocationZField.getDouble(location);
                renderRoleplayPlayerHead(
                        context, ownerId,
                        ClientRoleplayCharacterIdentityHook
                                .resolveMapPlayerName(profile),
                        worldX, worldZ,
                        renderedHeads);
            }
        } catch (Throwable ignored) {
            // LOTR internals are compatibility-only; the normal map stays usable.
        } finally {
            endIconRender();
        }
    }

    /** The Lost Tales map redraws every authorized player itself. */
    public static boolean shouldSuppressNativePlayerRendering(
            LOTRGuiMap gui) {
        return gui instanceof LostTalesLotrMapGui && ensureReflection();
    }

    private static void renderRoleplayPlayerHead(
            RenderContext context, UUID ownerId,
            String displayName,
            double worldX, double worldZ,
            List<RoleplayPlayerHead> renderedHeads) {
        ScreenPosition position = transformWorldCoords(
                context, worldX, worldZ);
        if (position == null) {
            return;
        }
        // Keep the transformed coordinate fractional. LOTR rounds this value
        // to an integer, which turns otherwise smooth fractional zoom into a
        // visibly jittering player portrait.
        float centerX = clampPlayerIconCoordinate(
                position.x, context.mapXMin, context.mapXMax)
                + 0.5F;
        float centerY = clampPlayerIconCoordinate(
                position.y, context.mapYMin, context.mapYMax)
                + 0.5F;
        RoleplayPlayerHead head = new RoleplayPlayerHead(
                ownerId, displayName, centerX, centerY);
        renderedHeads.add(head);
        drawRoleplayPlayerHead(context, head, PLAYER_HEAD_DRAW_SIZE);
    }

    private static void drawRoleplayPlayerHead(
            RenderContext context, RoleplayPlayerHead head, float size) {
        LostTalesCharacterHeadIconRenderer.drawHead(
                context.minecraft, head.ownerId,
                head.centerX - size * 0.5F + 1.0F,
                head.centerY - size * 0.5F + 1.0F,
                size, 0.15F, 0.75F);
        LostTalesCharacterHeadIconRenderer.drawHead(
                context.minecraft, head.ownerId,
                head.centerX - size * 0.5F,
                head.centerY - size * 0.5F,
                size, 1.0F, 1.0F);
    }

    public static void updateHoverFocus(
            LOTRGuiMap gui, List<LOTRAbstractWaypoint> nativeWaypoints,
            int mouseX, int mouseY, boolean includeHidden) {
        RenderContext context = createRenderContext(gui);
        if (context == null || context.alpha <= 0.0F) {
            clearHoverFocus(gui);
            return;
        }
        if (hoverFocusGui != gui) {
            hoverFocus.clear();
            hoverFocusGui = gui;
            activeHoverCandidate = null;
        }
        HoverCandidate best = null;
        MarkerRenderCandidate marker = getHoveredGroupedCandidate(
                gui, mouseX, mouseY, null, CandidateKind.ANY);
        if (marker != null) {
            float markerFade = getGroupingFade(marker.marker.getId());
            ScreenPosition markerPosition = groupingRenderPosition(
                    marker, groupingFrame.candidates,
                    groupingFrame.result, markerFade);
            best = HoverCandidate.marker(marker, markerPosition,
                    markerFade, groupedMarkerHitDistanceSq(
                    marker, mouseX, mouseY));
        }
        if (LostTalesConfig.showHostileMapMarkers) {
            EnemyMarkerPosition enemy = getHoveredTransientEnemy(
                    context, mouseX, mouseY);
            if (enemy != null) {
                ScreenPosition enemyPosition = transformWorldCoords(
                        context, enemy.x, enemy.z);
                if (enemyPosition != null) {
                    best = chooseBetterHover(best,
                            HoverCandidate.enemy(enemy, enemyPosition,
                            distanceSq(
                            enemyPosition.x, enemyPosition.y,
                            mouseX, mouseY)));
                }
            }
        }
        if (roleplayPlayerHeadFrameGui == gui) {
            for (RoleplayPlayerHead head : roleplayPlayerHeadFrame) {
                if (!isMouseOverPlayerHead(
                        head.centerX, head.centerY, mouseX, mouseY)) {
                    continue;
                }
                best = chooseBetterHover(best,
                        HoverCandidate.player(head, distanceSq(
                                head.centerX, head.centerY,
                                mouseX, mouseY)));
            }
        }
        best = chooseBetterHover(best, findHoveredNativeWaypoint(
                context, nativeWaypoints, mouseX, mouseY,
                includeHidden));
        String activeKey = hoverFocus.update(
                best == null ? "" : best.key, System.nanoTime());
        activeHoverCandidate = best != null
                && best.key.equals(activeKey) ? best : null;
    }

    /**
     * LOTR renders its selected-waypoint tooltip outside renderWaypoints.
     * Hide that field for the rest of this draw when another icon owns hover;
     * the GUI restores it immediately after LOTR finishes rendering.
     */
    public static void suppressSelectedLotrTooltipForFocusedHover(
            LOTRGuiMap gui) {
        HoverCandidate focused = hoverFocusGui == gui
                ? activeHoverCandidate : null;
        if (gui == null || focused == null || !ensureReflection()
                || selectedWaypointField == null
                || suppressedLotrSelectionGui == gui) {
            return;
        }
        try {
            Object selected = selectedWaypointField.get(gui);
            if (!(selected instanceof LOTRAbstractWaypoint)) {
                return;
            }
            suppressedLotrSelectionGui = gui;
            suppressedLotrSelection = (LOTRAbstractWaypoint)selected;
            selectedWaypointField.set(gui, null);
        } catch (Throwable ignored) {
            clearSuppressedLotrSelection(gui);
        }
    }

    public static LOTRAbstractWaypoint restoreSelectedLotrWaypointAfterDraw(
            LOTRGuiMap gui) {
        if (gui == null || suppressedLotrSelectionGui != gui) {
            return null;
        }
        LOTRAbstractWaypoint selected = suppressedLotrSelection;
        try {
            if (selected != null && ensureReflection()
                    && selectedWaypointField != null
                    && selectedWaypointField.get(gui) == null) {
                selectedWaypointField.set(gui, selected);
            }
        } catch (Throwable ignored) {
            // Rendering focus must never leave a stale compatibility failure.
        } finally {
            clearSuppressedLotrSelection(gui);
        }
        return selected;
    }

    /** Redraws the single delayed hover owner after every map icon layer. */
    public static void renderHoveredIconForeground(
            LOTRGuiMap gui, int mouseX, int mouseY,
            boolean drawLabels) {
        HoverCandidate focused = hoverFocusGui == gui
                ? activeHoverCandidate : null;
        if (focused == null) {
            return;
        }
        RenderContext context = createRenderContext(gui);
        if (context == null || context.alpha <= 0.0F) {
            return;
        }
        beginIconRender();
        try {
            if (focused.kind == HoverKind.MARKER) {
                drawFocusedMarkerStack(context, focused, drawLabels);
            } else if (focused.kind == HoverKind.ENEMY) {
                drawFixedScreenIcon(context.minecraft,
                        focused.position.x, focused.position.y,
                        ICON_HOVER_DRAW_SIZE,
                        LostTalesCompassMarkerIcon.HOSTILE,
                        1.0F, 1.0F, 1.0F, context.alpha);
            } else if (focused.kind == HoverKind.PLAYER) {
                drawRoleplayPlayerHead(context, focused.playerHead,
                        PLAYER_HEAD_HOVER_DRAW_SIZE);
            }
        } catch (Throwable ignored) {
            // Foreground emphasis is optional and must not break the map.
        } finally {
            endIconRender();
        }
    }

    private static void drawFocusedMarkerStack(
            RenderContext context, HoverCandidate focused,
            boolean drawLabels) {
        int representativeIndex = findCandidateIndexById(
                groupingFrame.candidates,
                focused.marker.marker.getId());
        LostTalesMapMarkerGrouping.Group group =
                findGroupForCandidate(
                groupingFrame.result, representativeIndex);
        if (group != null) {
            List<Integer> members = group.getMemberIndices();
            for (int memberIndex = members.size() - 1;
                 memberIndex >= 1; memberIndex--) {
                int candidateIndex = members.get(memberIndex).intValue();
                MarkerRenderCandidate companion =
                        groupingFrame.candidates.get(candidateIndex);
                float fade = getGroupingFade(companion.marker.getId());
                float slot = getGroupingCompanionSlot(
                        companion.marker.getId());
                float alpha = fade + (1.0F - fade)
                        * Math.abs(slot) * GROUP_COMPANION_ALPHA;
                if (alpha <= 0.001F) {
                    continue;
                }
                ScreenPosition position = groupingRenderPosition(
                        companion, groupingFrame.candidates,
                        groupingFrame.result, fade);
                drawMarkerIcon(context.minecraft, companion.marker,
                        position.x, position.y,
                        context.alpha * alpha,
                        ICON_COMPANION_HOVER_DRAW_SIZE);
            }
        }
        drawMarkerIcon(context.minecraft,
                focused.marker.marker,
                focused.position.x, focused.position.y,
                context.alpha * focused.markerFade, true);
        if (drawLabels && context.labelAlpha > 0.0F) {
            drawLabel(context.fontRenderer,
                    focused.marker.displayName,
                    focused.position.x, focused.position.y,
                    context.labelAlpha * focused.markerFade,
                    context);
        }
    }

    public static LOTRAbstractWaypoint getFocusedNativeWaypoint(
            LOTRGuiMap gui) {
        HoverCandidate focused = hoverFocusGui == gui
                ? activeHoverCandidate : null;
        return focused != null && focused.kind == HoverKind.NATIVE
                ? focused.nativeWaypoint : null;
    }

    public static float[] getFocusedNativeIconTransform(
            LOTRGuiMap gui, LOTRAbstractWaypoint waypoint) {
        RenderContext context = createRenderContext(gui);
        ScreenPosition position = context == null || waypoint == null
                ? null : transformWorldCoords(context,
                waypoint.getXCoord(), waypoint.getZCoord());
        return position == null ? null : new float[] {
                position.x, position.y, NATIVE_WAYPOINT_HOVER_SCALE
        };
    }

    public static void renderRestoredSelectedLotrTooltip(
            LOTRGuiMap gui, LOTRAbstractWaypoint selected,
            int mouseX, int mouseY) {
        if (gui == null || selected == null) {
            return;
        }
        HoverCandidate focused = hoverFocusGui == gui
                ? activeHoverCandidate : null;
        if (focused != null) {
            return;
        }
        RenderContext context = createRenderContext(gui);
        renderLotrWaypointTooltip(
                context, selected, true, mouseX, mouseY, null);
    }

    public static void renderFocusedHoverTooltip(
            LOTRGuiMap gui, LostTalesMapMarkerData selectedMarker,
            int mouseX, int mouseY) {
        HoverCandidate focused = hoverFocusGui == gui
                ? activeHoverCandidate : null;
        if (focused == null) {
            return;
        }
        RenderContext context = createRenderContext(gui);
        if (context == null || context.alpha <= 0.0F) {
            return;
        }
        if (focused.kind == HoverKind.MARKER) {
            LostTalesMapMarkerData marker = focused.marker.marker;
            if (!sameMarker(marker, selectedMarker)) {
                renderLotrWaypointTooltip(context,
                        new LostTalesMarkerWaypoint(marker), true,
                        mouseX, mouseY, focused.position);
            }
        } else if (focused.kind == HoverKind.ENEMY) {
            renderLotrWaypointTooltip(context,
                    new LostTalesEntityWaypoint(focused.enemy), false,
                    mouseX, mouseY, focused.position);
        } else if (focused.kind == HoverKind.NATIVE) {
            renderLotrWaypointTooltip(context,
                    focused.nativeWaypoint, true,
                    mouseX, mouseY, focused.position);
        } else if (focused.kind == HoverKind.PLAYER) {
            renderPlayerTooltip(context, focused.playerHead);
        }
    }

    private static void renderPlayerTooltip(
            RenderContext context, RoleplayPlayerHead head) {
        if (context == null || head == null
                || head.displayName.length() == 0
                || context.fontRenderer == null) {
            return;
        }
        FontRenderer font = context.fontRenderer;
        int padding = 3;
        int width = font.getStringWidth(head.displayName) + padding * 2;
        int height = font.FONT_HEIGHT + padding * 2;
        int bottom = context.mapYMax - (LostTalesLotrMapLayout
                .isControlBarVisible(context.gui)
                ? LostTalesLotrMapControlBar.HEIGHT : 0);
        float desiredX = Math.max(context.mapXMin + 2.0F,
                Math.min(context.mapXMax - width - 2.0F,
                        head.centerX - width * 0.5F));
        float desiredY = Math.max(context.mapYMin + 2.0F,
                Math.min(bottom - height - 2.0F,
                        head.centerY + PLAYER_HEAD_DRAW_SIZE * 0.5F
                                + 3.0F));
        int x = Math.round(desiredX);
        int y = Math.round(desiredY);
        float translateX = desiredX - x;
        float translateY = desiredY - y;
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(translateX, translateY, 0.0F);
            drawFancyRect(context, x, y, x + width, y + height);
            font.drawString(
                    head.displayName, x + padding, y + padding,
                    0xFFFFFF);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static HoverCandidate chooseBetterHover(
            HoverCandidate current, HoverCandidate candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || isBetterForegroundHit(
                candidate.distanceSq, candidate.priority,
                current.distanceSq, current.priority)
                ? candidate : current;
    }

    static boolean isBetterForegroundHit(
            double distanceSq, int priority,
            double currentDistanceSq, int currentPriority) {
        double difference = distanceSq - currentDistanceSq;
        return difference < -0.0001D
                || (Math.abs(difference) <= 0.0001D
                    && priority > currentPriority);
    }

    private static double distanceSq(
            float centerX, float centerY, int mouseX, int mouseY) {
        double deltaX = centerX - mouseX;
        double deltaY = centerY - mouseY;
        return deltaX * deltaX + deltaY * deltaY;
    }

    private static HoverCandidate findHoveredNativeWaypoint(
            RenderContext context,
            List<LOTRAbstractWaypoint> waypoints,
            int mouseX, int mouseY, boolean includeHidden) {
        if (context == null || waypoints == null) {
            return null;
        }
        HoverCandidate nearest = null;
        for (LOTRAbstractWaypoint waypoint : waypoints) {
            try {
                if (waypoint == null
                        || (!includeHidden
                        && !isWaypointVisibleInLotrToggles(waypoint))
                        || (waypoint.isHidden()
                        && !waypoint.hasPlayerUnlocked(
                                context.minecraft.thePlayer))) {
                    continue;
                }
                ScreenPosition position = transformWorldCoords(
                        context, waypoint.getXCoord(),
                        waypoint.getZCoord());
                if (position == null
                        || position.x < context.mapXMin - 2
                        || position.x > context.mapXMax + 2
                        || position.y < context.mapYMin - 2
                        || position.y > context.mapYMax + 2) {
                    continue;
                }
                double distanceSq = distanceSq(
                        position.x, position.y, mouseX, mouseY);
                if (distanceSq > 25.0D) {
                    continue;
                }
                HoverCandidate candidate = HoverCandidate.nativeWaypoint(
                        waypoint, position, distanceSq);
                nearest = chooseBetterHover(nearest, candidate);
            } catch (Throwable ignored) {
                // One malformed compatibility waypoint must not own hover.
            }
        }
        return nearest;
    }

    public static LOTRAbstractWaypoint getHoveredNativeWaypoint(
            LOTRGuiMap gui, List<LOTRAbstractWaypoint> waypoints,
            int mouseX, int mouseY, boolean includeHidden) {
        HoverCandidate candidate = findHoveredNativeWaypoint(
                createRenderContext(gui), waypoints,
                mouseX, mouseY, includeHidden);
        return candidate == null ? null : candidate.nativeWaypoint;
    }

    private static void clearHoverFocus(LOTRGuiMap gui) {
        if (gui == null || hoverFocusGui == gui) {
            hoverFocus.clear();
            hoverFocusGui = null;
            activeHoverCandidate = null;
        }
    }

    private static void clearSuppressedLotrSelection(LOTRGuiMap gui) {
        if (gui == null || suppressedLotrSelectionGui == gui) {
            suppressedLotrSelectionGui = null;
            suppressedLotrSelection = null;
        }
    }

    private static float clampPlayerIconCoordinate(
            float coordinate, int minimum, int maximum) {
        return Math.max(minimum + 5.0F,
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
                if (position == null || !iconOverlapsMap(
                        position.x, position.y,
                        ICON_HOVER_DRAW_SIZE, context)) {
                    continue;
                }

                drawFixedScreenIcon(context.minecraft,
                        position.x, position.y, ICON_DRAW_SIZE,
                        LostTalesCompassMarkerIcon.HOSTILE, 1.0F, 1.0F, 1.0F, context.alpha);
            }
        } catch (Throwable ignored) {
            // Transient markers are visual-only; never break the LOTR map.
        } finally {
            endIconRender();
        }
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
        MarkerRenderCandidate candidate = getHoveredGroupedCandidate(
                gui, mouseX, mouseY, preferredMarkerId,
                CandidateKind.STANDALONE);
        return candidate == null ? null : candidate.marker;
    }

    public static LostTalesMapMarkerData getHoveredLockedMappedMarker(
            LOTRGuiMap gui, int mouseX, int mouseY) {
        MarkerRenderCandidate candidate = getHoveredGroupedCandidate(
                gui, mouseX, mouseY, null, CandidateKind.LOCKED_MAPPED);
        return candidate == null ? null : candidate.marker;
    }

    /** Finds a visible, non-private replacement using its rendered position. */
    public static LOTRAbstractWaypoint getHoveredMappedWaypoint(
            LOTRGuiMap gui, int mouseX, int mouseY) {
        MarkerRenderCandidate candidate = getHoveredGroupedCandidate(
                gui, mouseX, mouseY, null, CandidateKind.MAPPED);
        return candidate == null ? null : candidate.waypoint;
    }

    private static MarkerRenderCandidate getHoveredGroupedCandidate(
            LOTRGuiMap gui, int mouseX, int mouseY,
            String preferredMarkerId, CandidateKind kind) {
        if (gui == null || groupingFrame.gui != gui) {
            return null;
        }
        MarkerRenderCandidate nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        for (LostTalesMapMarkerGrouping.Group group
                : groupingFrame.result.getGroups()) {
            MarkerRenderCandidate candidate = groupingFrame.candidates.get(
                    group.getRepresentativeIndex());
            if (!kind.matches(candidate)) {
                continue;
            }
            float fade = getGroupingFade(candidate.marker.getId());
            if (fade <= 0.5F) {
                continue;
            }
            double distanceSq = groupStackHitDistanceSq(
                    group, mouseX, mouseY);
            if (distanceSq > (double)(HOVER_RADIUS * HOVER_RADIUS)) {
                continue;
            }
            if (preferredMarkerId != null
                    && preferredMarkerId.equals(candidate.marker.getId())) {
                return candidate;
            }
            if (distanceSq < nearestDistanceSq) {
                nearest = candidate;
                nearestDistanceSq = distanceSq;
            }
        }
        return nearest;
    }

    private static double groupedMarkerHitDistanceSq(
            MarkerRenderCandidate representative,
            int mouseX, int mouseY) {
        int index = findCandidateIndexById(
                groupingFrame.candidates,
                representative.marker.getId());
        LostTalesMapMarkerGrouping.Group group =
                findGroupForCandidate(groupingFrame.result, index);
        return group == null ? Double.MAX_VALUE
                : groupStackHitDistanceSq(group, mouseX, mouseY);
    }

    private static double groupStackHitDistanceSq(
            LostTalesMapMarkerGrouping.Group group,
            int mouseX, int mouseY) {
        double nearest = Double.MAX_VALUE;
        for (Integer indexValue : group.getMemberIndices()) {
            int index = indexValue.intValue();
            MarkerRenderCandidate candidate =
                    groupingFrame.candidates.get(index);
            float fade = getGroupingFade(candidate.marker.getId());
            if (index != group.getRepresentativeIndex()) {
                float slot = getGroupingCompanionSlot(
                        candidate.marker.getId());
                float alpha = fade + (1.0F - fade)
                        * Math.abs(slot) * GROUP_COMPANION_ALPHA;
                if (alpha <= 0.001F) {
                    continue;
                }
            }
            ScreenPosition position = groupingRenderPosition(
                    candidate, groupingFrame.candidates,
                    groupingFrame.result, fade);
            nearest = Math.min(nearest, distanceSq(
                    position.x, position.y, mouseX, mouseY));
        }
        return nearest;
    }

    public static boolean renderCustomMarkerSelection(
            LOTRGuiMap gui, LostTalesMapMarkerData marker,
            int mouseX, int mouseY) {
        RenderContext context = createRenderContext(gui);
        if (context == null || marker == null
                || !isSelectableCustomMarker(marker)
                || !isGroupedRepresentative(gui, marker)) {
            return false;
        }

        ScreenPosition position = transformMarker(context, marker);
        if (position == null || !isInsideMap(position.x, position.y, context)) {
            return false;
        }

        LostTalesMarkerWaypoint tooltipWaypoint = new LostTalesMarkerWaypoint(marker);
        HoverCandidate focused = hoverFocusGui == gui
                ? activeHoverCandidate : null;
        if (focused == null
                || (focused.kind == HoverKind.MARKER
                && sameMarker(focused.marker.marker, marker))) {
            renderLotrWaypointTooltip(
                    context, tooltipWaypoint, true,
                    mouseX, mouseY, position);
        }
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

    public static LOTRAbstractWaypoint getSelectedWaypoint(
            LOTRGuiMap gui) {
        if (gui == null || !ensureReflection()
                || selectedWaypointField == null) {
            return null;
        }
        try {
            Object selected = selectedWaypointField.get(gui);
            return selected instanceof LOTRAbstractWaypoint
                    ? (LOTRAbstractWaypoint)selected : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Clears native interaction state that no longer has an authoritative
     * visible marker. The decorative catalog still identifies deleted native
     * waypoints so their invisible LOTR hitboxes cannot remain selectable.
     */
    public static void clearInvalidLotrSelection(LOTRGuiMap gui) {
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
            if ((marker == null
                    && hasDecorativeMapping(
                            (LOTRAbstractWaypoint)selected))
                    || !LostTalesMapLegendRegistry.isWaypointVisible(
                            (LOTRAbstractWaypoint)selected)
                    || (marker != null
                    && !LostTalesMapLegendRegistry.isMarkerVisible(marker))
                    || (marker != null && marker.isDiscoverable()
                    && !isDiscovered(marker))) {
                selectedWaypointField.set(gui, null);
            }
        } catch (Throwable ignored) {
            // Selection cleanup is best-effort; the server policy still denies it.
        }
    }

    public static boolean isDeletedMappedWaypoint(
            LOTRAbstractWaypoint waypoint) {
        return waypoint != null
                && getMappedMarker(waypoint) == null
                && hasDecorativeMapping(waypoint);
    }

    private static List<LostTalesMapMarkerData> getVisibleStandaloneMarkers() {
        List<LostTalesMapMarkerData> party =
                ClientPartyTrackingCache.getMapMarkers();
        return LostTalesClientMapMarkerStore.getMapMarkers(party);
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

    public static LostTalesMapMarkerData getMarkerForWaypoint(
            LOTRAbstractWaypoint waypoint) {
        return getMappedMarker(waypoint);
    }

    private static LostTalesMapMarkerData getMappedMarker(LOTRAbstractWaypoint waypoint) {
        if (waypoint == null) {
            return null;
        }
        return LostTalesClientMapMarkerStore.findMappedWaypointMarker(
                safeString(waypoint.getCodeName()),
                safeString(waypoint.getDisplayName()),
                waypoint.getXCoord(), waypoint.getZCoord());
    }

    private static boolean hasDecorativeMapping(
            LOTRAbstractWaypoint waypoint) {
        return waypoint != null
                && LostTalesClientMapMarkerStore
                        .hasDecorativeWaypointMapping(
                                safeString(waypoint.getCodeName()),
                                safeString(waypoint.getDisplayName()),
                                waypoint.getXCoord(),
                                waypoint.getZCoord());
    }

    private static boolean isReplacementMarkerEligible(LostTalesMapMarkerData marker) {
        return isWaypointMappingEligible(marker) && shouldShowLostTalesIcon(marker);
    }

    private static boolean isWaypointMappingEligible(LostTalesMapMarkerData marker) {
        if (marker == null) {
            return false;
        }
        if (marker.getDimensionId() != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return false;
        }
        return true;
    }

    private static boolean shouldRenderStandaloneMarker(LostTalesMapMarkerData marker) {
        if (marker == null) {
            return false;
        }
        if (marker.getDimensionId() != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return false;
        }
        if (hasNativeWaypointMapping(marker)) {
            return false;
        }
        return shouldShowLostTalesIcon(marker);
    }

    private static boolean hasNativeWaypointMapping(
            LostTalesMapMarkerData marker) {
        return marker != null && marker.getId() != null
                && getNativeMappedMarkerIds().contains(marker.getId());
    }

    private static Set<String> getNativeMappedMarkerIds() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null
                || minecraft.theWorld == null) {
            return Collections.emptySet();
        }
        EntityPlayer player = minecraft.thePlayer;
        long tick = minecraft.theWorld.getTotalWorldTime();
        Object snapshot =
                LostTalesClientMapMarkerStore.getSnapshotIdentity();
        if (nativeMappingCachePlayer == player
                && nativeMappingCacheTick == tick
                && nativeMappingCacheSnapshot == snapshot) {
            return nativeMappedMarkerIds;
        }
        HashSet<String> mapped = new HashSet<String>();
        LOTRPlayerData data = LOTRLevelData.getData(player);
        if (data != null) {
            for (LOTRAbstractWaypoint waypoint
                    : data.getAllAvailableWaypoints()) {
                LostTalesMapMarkerData marker =
                        getMappedMarker(waypoint);
                if (marker != null && marker.getId() != null) {
                    mapped.add(marker.getId());
                }
            }
        }
        nativeMappingCachePlayer = player;
        nativeMappingCacheTick = tick;
        nativeMappingCacheSnapshot = snapshot;
        nativeMappedMarkerIds = mapped.isEmpty()
                ? Collections.<String>emptySet() : mapped;
        return nativeMappedMarkerIds;
    }

    private static boolean shouldShowLostTalesIcon(LostTalesMapMarkerData marker) {
        return LostTalesClientMapMarkerVisibility.isMapVisible(marker)
                && LostTalesMapLegendRegistry.isMarkerVisible(marker);
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
        if (isWaypointMappingEligible(marker)
                && shouldShowLostTalesIcon(marker)
                && hasNativeWaypointMapping(marker)) {
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

    private static boolean isGroupedRepresentative(
            LOTRGuiMap gui, LostTalesMapMarkerData marker) {
        if (gui == null || marker == null || groupingFrame.gui != gui) {
            return true;
        }
        String markerId = safeString(marker.getId());
        for (LostTalesMapMarkerGrouping.Group group
                : groupingFrame.result.getGroups()) {
            MarkerRenderCandidate representative =
                    groupingFrame.candidates.get(
                            group.getRepresentativeIndex());
            if (markerId.equals(representative.marker.getId())) {
                return true;
            }
        }
        return false;
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

    private static RenderContext createMenuRenderContext(LOTRGuiMap gui) {
        if (gui == null || !ensureReflection()) {
            return null;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.fontRenderer == null) {
            return null;
        }
        try {
            float zoomExp = getZoomExp(gui);
            return new RenderContext(
                    gui, minecraft, minecraft.fontRenderer,
                    zoomExp, 1.0F, 0.0F,
                    mapXMinField.getInt(null),
                    mapXMaxField.getInt(null),
                    mapYMinField.getInt(null),
                    mapYMaxField.getInt(null));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ScreenPosition transformMarker(RenderContext context, LostTalesMapMarkerData marker) {
        if (context == null || marker == null) {
            return null;
        }
        return transformWorldCoords(context, marker.getX(), marker.getZ());
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
        return markerStackOverlapsMapBounds(
                x, y, context.mapXMin, context.mapXMax,
                context.mapYMin, context.mapYMax);
    }

    static boolean markerStackOverlapsMapBounds(
            float x, float y, float mapXMin, float mapXMax,
            float mapYMin, float mapYMax) {
        float iconExtent = ICON_HOVER_DRAW_SIZE * 0.5F;
        float horizontalExtent = iconExtent
                + LostTalesMapMarkerGrouping.COMPANION_OFFSET_X;
        float topExtent = iconExtent + Math.max(0.0F,
                -LostTalesMapMarkerGrouping.COMPANION_OFFSET_Y);
        float bottomExtent = iconExtent + Math.max(0.0F,
                LostTalesMapMarkerGrouping.COMPANION_OFFSET_Y);
        return x + horizontalExtent >= mapXMin
                && x - horizontalExtent <= mapXMax
                && y + bottomExtent >= mapYMin
                && y - topExtent <= mapYMax;
    }

    private static boolean iconOverlapsMap(
            float centerX, float centerY, int drawSize,
            RenderContext context) {
        float extent = (float)drawSize / 2.0F;
        return centerX + extent >= context.mapXMin
                && centerX - extent <= context.mapXMax
                && centerY + extent >= context.mapYMin
                && centerY - extent <= context.mapYMax;
    }

    private static void beginMenuMapClipping(RenderContext context) {
        ScaledResolution resolution = new ScaledResolution(
                context.minecraft,
                context.minecraft.displayWidth,
                context.minecraft.displayHeight);
        int scale = resolution.getScaleFactor();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_SCISSOR_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
                context.mapXMin * scale,
                (context.gui.height - context.mapYMax) * scale,
                (context.mapXMax - context.mapXMin) * scale,
                (context.mapYMax - context.mapYMin) * scale);
    }

    private static void endMenuMapClipping() {
        GL11.glPopAttrib();
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
                    || !iconOverlapsMap(position.x, position.y,
                    ICON_HOVER_DRAW_SIZE, context)) {
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
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void endIconRender() {
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    private static void drawMarkerIcon(Minecraft minecraft, LostTalesMapMarkerData marker, float centerX, float centerY, float alpha, boolean hover) {
        drawMarkerIcon(minecraft, marker, centerX, centerY, alpha,
                hover ? ICON_HOVER_DRAW_SIZE : ICON_DRAW_SIZE);
    }

    private static void drawMarkerIcon(
            Minecraft minecraft, LostTalesMapMarkerData marker,
            float centerX, float centerY, float alpha, int size) {
        boolean undiscovered = isUndiscoveredButVisible(marker);
        LostTalesCompassMarkerIcon icon = undiscovered
                ? LostTalesCompassMarkerIcon.UNDISCOVERED
                : LostTalesCompassMarkerIcon.fromName(marker.getIconName());
        // White is a neutral texture tint. Undiscovered icons should retain
        // their artwork instead of being recolored gray or washed out.
        float[] color = LostTalesCompassMarker.parseColor(
                undiscovered ? "white" : marker.getColorName());
        // Match Minecraft's one-pixel text shadow without modifying the
        // configured icon tint itself.
        drawFixedScreenIcon(minecraft, centerX + 1.0F, centerY + 1.0F,
                size, icon, 0.0F, 0.0F, 0.0F, alpha * 0.75F);
        drawFixedScreenIcon(minecraft, centerX, centerY, size, icon,
                color[0], color[1], color[2], alpha);
    }

    /** Draws the same marker artwork in client editors without map logic. */
    public static void renderEditorIconPreview(
            Minecraft minecraft, String iconName, String colorName,
            float centerX, float centerY) {
        if (minecraft == null) {
            return;
        }
        LostTalesCompassMarkerIcon icon =
                LostTalesCompassMarkerIcon.fromName(iconName);
        float[] color = LostTalesCompassMarker.parseColor(colorName);
        beginIconRender();
        try {
            drawFixedScreenIcon(
                    minecraft, centerX + 1.0F, centerY + 1.0F,
                    ICON_DRAW_SIZE, icon,
                    0.0F, 0.0F, 0.0F, 0.75F);
            drawFixedScreenIcon(
                    minecraft, centerX, centerY,
                    ICON_DRAW_SIZE, icon,
                    color[0], color[1], color[2], 1.0F);
        } finally {
            endIconRender();
        }
    }

    private static void drawDecorativeMarkerIcon(
            Minecraft minecraft, LostTalesMapMarkerData marker,
            float centerX, float centerY, boolean sepia) {
        LostTalesCompassMarkerIcon icon =
                LostTalesCompassMarkerIcon.fromName(
                        marker.getIconName());
        float[] color = LostTalesCompassMarker.parseColor(
                marker.getColorName());
        if (sepia) {
            color = toLotrSepiaTint(
                    color[0], color[1], color[2]);
        }
        drawFixedScreenIcon(
                minecraft, centerX + 1.0F, centerY + 1.0F,
                ICON_DRAW_SIZE, icon,
                0.0F, 0.0F, 0.0F, 0.75F);
        drawFixedScreenIcon(
                minecraft, centerX, centerY,
                ICON_DRAW_SIZE, icon,
                color[0], color[1], color[2], 1.0F);
    }

    static float[] toLotrSepiaTint(
            float red, float green, float blue) {
        return new float[] {
                clampColor(red * 0.79F + green * 0.39F
                        + blue * 0.26F),
                clampColor(red * 0.52F + green * 0.35F
                        + blue * 0.19F),
                clampColor(red * 0.35F + green * 0.26F
                        + blue * 0.15F)
        };
    }

    private static float clampColor(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
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

    private static void drawGroupLabel(
            FontRenderer fontRenderer, String label,
            float screenX, float screenY, float alpha,
            RenderContext context) {
        if (fontRenderer == null || label == null || label.length() == 0
                || alpha <= 0.0F) {
            return;
        }
        float scale = alpha;
        int textWidth = fontRenderer.getStringWidth(label);
        float halfWidth = textWidth * scale / 2.0F;
        float top = screenY + 10.0F * scale;
        float bottom = top + fontRenderer.FONT_HEIGHT * scale;
        if (screenX + halfWidth < context.mapXMin
                || screenX - halfWidth > context.mapXMax
                || bottom < context.mapYMin || top > context.mapYMax) {
            return;
        }
        int alphaInt = Math.max(4,
                Math.min(255, (int)(alpha * 0.8F * 255.0F)));
        int color = (alphaInt << 24) | 0x00FFFFFF;
        int shadowColor = alphaInt << 24;
        GL11.glPushMatrix();
        GL11.glTranslatef(screenX, screenY, 0.0F);
        GL11.glScalef(scale, scale, scale);
        try {
            int x = -textWidth / 2;
            int y = 10;
            fontRenderer.drawString(label, x + 1, y + 1, shadowColor);
            fontRenderer.drawString(label, x, y, color);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void renderLotrWaypointTooltip(
            RenderContext context, LOTRAbstractWaypoint waypoint,
            boolean selected, int mouseX, int mouseY,
            ScreenPosition desiredAnchor) {
        if (context == null || waypoint == null
                || renderWaypointTooltipMethod == null) {
            return;
        }
        float[] translation = getTooltipTranslation(
                context, waypoint, desiredAnchor);
        GL11.glPushMatrix();
        try {
            if (translation != null) {
                GL11.glTranslatef(
                        translation[0], translation[1], 0.0F);
            }
            renderWaypointTooltipMethod.invoke(
                    context.gui, waypoint, Boolean.valueOf(selected),
                    Integer.valueOf(mouseX), Integer.valueOf(mouseY));
        } catch (Throwable ignored) {
            // A changed LOTR tooltip renderer must not break the map.
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static float[] getTooltipTranslation(
            RenderContext context, LOTRAbstractWaypoint waypoint,
            ScreenPosition desiredAnchor) {
        if (context == null || waypoint == null) {
            return null;
        }
        ScreenPosition internalAnchor = transformWorldCoords(
                context, waypoint.getXCoord(), waypoint.getZCoord());
        if (internalAnchor == null) {
            return null;
        }
        ScreenPosition desired = desiredAnchor == null
                ? internalAnchor : desiredAnchor;
        return new float[] {
                tooltipTranslation(internalAnchor.x, desired.x),
                tooltipTranslation(internalAnchor.y, desired.y)
        };
    }

    static float tooltipTranslation(
            float roundedSource, float desiredPosition) {
        return desiredPosition - Math.round(roundedSource);
    }

    static float highlightedSize(float normalSize) {
        return normalSize * HIGHLIGHT_SCALE;
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
            playerLocationProfileField = playerLocationClass
                    .getDeclaredField("profile");
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
            playerLocationProfileField.setAccessible(true);
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

    private static String safeString(String value) {
        return value == null ? "" : value.trim();
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

    private enum HoverKind {
        MARKER,
        ENEMY,
        PLAYER,
        NATIVE
    }

    private static final class HoverCandidate {
        private final HoverKind kind;
        private final String key;
        private final int priority;
        private final double distanceSq;
        private final ScreenPosition position;
        private final MarkerRenderCandidate marker;
        private final float markerFade;
        private final EnemyMarkerPosition enemy;
        private final RoleplayPlayerHead playerHead;
        private final LOTRAbstractWaypoint nativeWaypoint;

        private HoverCandidate(
                HoverKind kind, String key, int priority,
                double distanceSq, ScreenPosition position,
                MarkerRenderCandidate marker, float markerFade,
                EnemyMarkerPosition enemy,
                RoleplayPlayerHead playerHead,
                LOTRAbstractWaypoint nativeWaypoint) {
            this.kind = kind;
            this.key = key;
            this.priority = priority;
            this.distanceSq = distanceSq;
            this.position = position;
            this.marker = marker;
            this.markerFade = markerFade;
            this.enemy = enemy;
            this.playerHead = playerHead;
            this.nativeWaypoint = nativeWaypoint;
        }

        private static HoverCandidate marker(
                MarkerRenderCandidate marker,
                ScreenPosition position, float fade,
                double distanceSq) {
            return new HoverCandidate(HoverKind.MARKER,
                    "marker:" + marker.marker.getId(), 2,
                    distanceSq, position, marker, fade,
                    null, null, null);
        }

        private static HoverCandidate enemy(
                EnemyMarkerPosition enemy,
                ScreenPosition position, double distanceSq) {
            return new HoverCandidate(HoverKind.ENEMY,
                    "enemy:" + enemy.entityId, 1,
                    distanceSq, position, null, 0.0F,
                    enemy, null, null);
        }

        private static HoverCandidate player(
                RoleplayPlayerHead playerHead,
                double distanceSq) {
            return new HoverCandidate(HoverKind.PLAYER,
                    "player:" + playerHead.ownerId, 3,
                    distanceSq,
                    new ScreenPosition(playerHead.centerX,
                            playerHead.centerY),
                    null, 0.0F, null, playerHead, null);
        }

        private static HoverCandidate nativeWaypoint(
                LOTRAbstractWaypoint waypoint,
                ScreenPosition position, double distanceSq) {
            String code = safeString(waypoint.getCodeName());
            String key = "native:" + waypoint.getClass().getName()
                    + ':' + waypoint.getID() + ':' + code
                    + ':' + waypoint.getXCoord()
                    + ':' + waypoint.getZCoord();
            return new HoverCandidate(HoverKind.NATIVE,
                    key, 2, distanceSq, position,
                    null, 0.0F, null, null, waypoint);
        }
    }

    private enum CandidateKind {
        ANY {
            @Override
            boolean matches(MarkerRenderCandidate candidate) {
                return true;
            }
        },
        STANDALONE {
            @Override
            boolean matches(MarkerRenderCandidate candidate) {
                return candidate.waypoint == null;
            }
        },
        MAPPED {
            @Override
            boolean matches(MarkerRenderCandidate candidate) {
                return candidate.waypoint != null
                        && !isLockedMappedMarkerVisible(candidate.marker);
            }
        },
        LOCKED_MAPPED {
            @Override
            boolean matches(MarkerRenderCandidate candidate) {
                return candidate.waypoint != null
                        && isLockedMappedMarkerVisible(candidate.marker);
            }
        };

        abstract boolean matches(MarkerRenderCandidate candidate);
    }

    private static final class MarkerRenderCandidate {
        private final LostTalesMapMarkerData marker;
        private final LOTRAbstractWaypoint waypoint;
        private final ScreenPosition position;
        private final String displayName;
        private final LostTalesMapMarkerGrouping.Entry groupingEntry;

        private MarkerRenderCandidate(
                LostTalesMapMarkerData marker,
                LOTRAbstractWaypoint waypoint,
                ScreenPosition position, String displayName,
                LostTalesMapMarkerGrouping.Entry groupingEntry) {
            this.marker = marker;
            this.waypoint = waypoint;
            this.position = position;
            this.displayName = displayName == null ? "" : displayName;
            this.groupingEntry = groupingEntry;
        }
    }

    private static final class RoleplayPlayerHead {
        private final UUID ownerId;
        private final String displayName;
        private final float centerX;
        private final float centerY;

        private RoleplayPlayerHead(
                UUID ownerId, String displayName,
                float centerX, float centerY) {
            this.ownerId = ownerId;
            this.displayName = safeString(displayName);
            this.centerX = centerX;
            this.centerY = centerY;
        }
    }

    private static final class GroupingFrame {
        private static final GroupingFrame EMPTY = new GroupingFrame(
                null, Long.MIN_VALUE,
                Collections.<MarkerRenderCandidate>emptyList(),
                LostTalesMapMarkerGrouping.Result.empty(), false,
                Float.NaN);
        private final LOTRGuiMap gui;
        private final long signature;
        private final List<MarkerRenderCandidate> candidates;
        private final LostTalesMapMarkerGrouping.Result result;
        private final boolean groupingEnabled;
        private final float zoomExp;

        private GroupingFrame(
                LOTRGuiMap gui, long signature,
                List<MarkerRenderCandidate> candidates,
                LostTalesMapMarkerGrouping.Result result,
                boolean groupingEnabled, float zoomExp) {
            this.gui = gui;
            this.signature = signature;
            this.candidates = Collections.unmodifiableList(
                    new ArrayList<MarkerRenderCandidate>(candidates));
            this.result = result;
            this.groupingEnabled = groupingEnabled;
            this.zoomExp = zoomExp;
        }

        private static GroupingFrame empty() { return EMPTY; }
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
            Minecraft minecraft = Minecraft.getMinecraft();
            double fallbackY = minecraft == null
                    || minecraft.thePlayer == null
                    ? marker.getY() : minecraft.thePlayer.posY;
            this.y = Math.round((float)marker.getEffectiveY(
                    minecraft == null ? null : minecraft.theWorld,
                    fallbackY));
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
                    ? null
                    : LostTalesLotrWaypointText.resolveDescription(
                            this.marker, player);
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
