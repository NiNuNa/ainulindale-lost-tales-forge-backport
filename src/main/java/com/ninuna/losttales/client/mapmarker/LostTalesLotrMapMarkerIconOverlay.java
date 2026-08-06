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
import net.minecraft.util.StatCollector;
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
    /**
     * Traces every committed grouping decision to the client log. Off unless
     * the game is started with
     * {@code -Dlosttales.mapmarker.groupingDebug=true}, because it logs one
     * line per decision and a pan produces one decision per frame.
     */
    private static final boolean GROUPING_DEBUG =
            Boolean.getBoolean("losttales.mapmarker.groupingDebug");
    private static final float UNGROUPED_CLOSE_ZOOM_EXP = 3.99F;
    private static final float GROUPING_FADE_PER_SECOND = 4.0F;
    private static final float GROUPING_ENABLE_ZOOM_EXP = 3.95F;
    private static final float GROUPING_DISABLE_ZOOM_EXP = 3.99F;
    /** Opacity of the fan sprites behind the marker leading a stack. */
    private static final float GROUP_COMPANION_ALPHA = 0.5F;
    /** Companion offset sideways, as a share of the leader's half-width. */
    private static final float COMPANION_SPREAD_RATIO = 1.15F;
    /** Companion offset upward, as a share of the leader's height above centre. */
    private static final float COMPANION_RISE_RATIO = 0.40F;
    /** Localized "+X more" summary for members the fan cannot show. */
    private static final String GROUP_MORE_LANG_KEY =
            "map.losttales.marker.group_more";
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
    /**
     * Presentation-only animation state, keyed by marker ID and retained
     * across frames. No entry here ever influences grouping; it only decides
     * where an already-decided member is drawn on its way to that decision.
     */
    private static final Map<String, MarkerAnimationState> groupingAnimation =
            new HashMap<String, MarkerAnimationState>();
    private static String lastTracedGrouping = "";
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
            for (MarkerRenderCandidate candidate : candidates) {
                entries.add(candidate.groupingEntry);
            }
            // The links, not the memberships: a marker is held by the one
            // marker it came to rest on, so that is what the next decision
            // has to be given back.
            Map<String, String> previous = groupingFrame.gui == gui
                    ? groupingFrame.result.getLinks()
                    : Collections.<String, String>emptyMap();
            boolean groupingEnabled = shouldGroupAtZoom(
                    gui, context.zoomExp);
            result = groupingEnabled
                    ? LostTalesMapMarkerGrouping.group(entries, previous)
                    : LostTalesMapMarkerGrouping.ungroup(entries);
        }
        boolean groupingEnabled = result.getMembership().size() > 0
                || shouldGroupAtZoom(gui, context.zoomExp);
        LostTalesMapMarkerRenderedGeometry.Frame renderedGeometry =
                previousFrame.gui == gui
                ? previousFrame.renderedGeometry
                : new LostTalesMapMarkerRenderedGeometry.Frame();
        groupingFrame = new GroupingFrame(
                gui, signature, candidates, result, groupingEnabled,
                context.zoomExp, renderedGeometry);
        traceGroupingDecision(candidates, result);
        boolean[] representatives = representativeFlags(
                candidates.size(), result);
        updateGroupingAnimation(gui, candidates, representatives, result);
        rebuildRenderedGeometry(context, candidates, result,
                renderedGeometry);
        if (candidates.isEmpty()) {
            return;
        }

        beginIconRender();
        try {
            // One pass over one sorted list of stacks. Each stack is drawn
            // whole before the next one starts, so nothing unrelated can be
            // drawn between the sprites of one fan.
            List<LostTalesMapMarkerGrouping.Group> groups =
                    result.getGroups();
            for (Integer orderValue : groupingFrame.groupsBottomToTop) {
                drawMarkerStack(context, candidates, renderedGeometry,
                        groups.get(orderValue.intValue()), drawLabels);
            }
        } catch (Throwable ignored) {
            // Optional marker grouping must never make the LOTR map unusable.
        } finally {
            endIconRender();
        }
    }

    /**
     * Draws one stack as an indivisible unit: its companions from the back
     * forward, then the marker leading it, then its labels.
     *
     * <p>Members travel into or out of the leader while fading. This is
     * presentation only — no waypoint, marker or waystone coordinate is ever
     * modified.</p>
     */
    private static void drawMarkerStack(
            RenderContext context,
            List<MarkerRenderCandidate> candidates,
            LostTalesMapMarkerRenderedGeometry.Frame renderedGeometry,
            LostTalesMapMarkerGrouping.Group group,
            boolean drawLabels) {
        int representativeIndex = group.getRepresentativeIndex();
        List<Integer> members = group.getMemberIndices();
        boolean withLabels = drawLabels && context.labelAlpha > 0.0F;
        // Members are held in relevance order behind the leader, so drawing
        // them in reverse leaves the leader on top of its own fan.
        for (int position = members.size() - 1; position >= 0; position--) {
            int candidateIndex = members.get(position).intValue();
            LostTalesMapMarkerRenderedGeometry.Member geometry =
                    renderedGeometry.getMemberForCandidate(candidateIndex);
            if (geometry == null || !geometry.isVisible()) {
                continue;
            }
            MarkerRenderCandidate candidate =
                    candidates.get(candidateIndex);
            if (candidateIndex == representativeIndex) {
                drawMarkerIcon(context.minecraft, candidate.marker,
                        geometry.getCenterX(), geometry.getCenterY(),
                        geometry.getRenderAlpha(), false);
            } else {
                drawMarkerIcon(context.minecraft, candidate.marker,
                        geometry.getCenterX(), geometry.getCenterY(),
                        geometry.getRenderAlpha(),
                        ICON_DRAW_SIZE * geometry.getVisualScale());
                if (withLabels) {
                    drawLabel(context.fontRenderer,
                            candidate.displayName,
                            geometry.getCenterX(), geometry.getCenterY(),
                            context.labelAlpha
                                    * geometry.getTransitionVisibility(),
                            context);
                }
            }
        }
        if (!withLabels) {
            return;
        }
        LostTalesMapMarkerRenderedGeometry.RenderedObject object =
                renderedGeometry.getObjectForCandidate(
                        representativeIndex);
        LostTalesMapMarkerRenderedGeometry.Member representativeGeometry =
                renderedGeometry.getMemberForCandidate(
                        representativeIndex);
        if (representativeGeometry == null
                || !representativeGeometry.isVisible()) {
            return;
        }
        // Both labels are placed from the shared geometry: the name above the
        // representative, the summary below the complete fan, so neither
        // drifts when the cluster layout changes.
        drawLabel(context.fontRenderer,
                candidates.get(representativeIndex).displayName,
                object == null ? representativeGeometry.getCenterX()
                        : object.getNameAnchorX(),
                object == null ? representativeGeometry.getCenterY()
                        : object.getNameAnchorY(),
                context.labelAlpha
                        * representativeGeometry.getTransitionVisibility(),
                context);
        if (object != null && group.getCondensedMemberCount() > 0) {
            drawGroupLabel(context.fontRenderer,
                    getCondensedMemberLabel(
                            group.getCondensedMemberCount()),
                    object.getStatusAnchorX(),
                    object.getStatusAnchorY(),
                    context.labelAlpha
                            * groupLabelFade(group, candidates),
                    context);
        }
    }

    public static void clearGrouping(LOTRGuiMap gui) {
        if (gui == null || groupingFrame.gui == gui) {
            groupingFrame = GroupingFrame.empty();
            groupingAnimation.clear();
            groupingFadeGui = null;
            groupingFadeLastNanos = 0L;
            lastTracedGrouping = "";
        }
        clearHoverFocus(gui);
        clearSuppressedLotrSelection(gui);
    }

    /**
     * Drops every cached screen-space, animation and compatibility reference.
     * Called on disconnect so a reconnect cannot reuse marker IDs, a stale GUI,
     * or a player from the previous session.
     */
    public static void clearClientState() {
        clearGrouping(null);
        roleplayPlayerHeadFrameGui = null;
        roleplayPlayerHeadFrame = Collections.emptyList();
        nativeMappingCacheTick = Long.MIN_VALUE;
        nativeMappingCachePlayer = null;
        nativeMappingCacheSnapshot = null;
        nativeMappedMarkerIds = Collections.emptySet();
    }

    /**
     * Names the real members behind every stack, so an unexpected merge can
     * be read off the log instead of guessed at from the screen.
     *
     * <p>Also checks the invariant that makes such a report meaningful: every
     * active marker belongs to exactly one stack. A mismatch would mean
     * something that is not an active marker reached the clustering input.</p>
     */
    private static void traceGroupingDecision(
            List<MarkerRenderCandidate> candidates,
            LostTalesMapMarkerGrouping.Result result) {
        if (!GROUPING_DEBUG) {
            return;
        }
        int members = 0;
        StringBuilder stacks = new StringBuilder();
        for (LostTalesMapMarkerGrouping.Group group : result.getGroups()) {
            members += group.size();
            if (group.size() <= 1) {
                continue;
            }
            if (stacks.length() > 0) {
                stacks.append(" | ");
            }
            List<Integer> memberIndices = group.getMemberIndices();
            for (int position = 0;
                 position < memberIndices.size(); position++) {
                MarkerRenderCandidate member = candidates.get(
                        memberIndices.get(position).intValue());
                stacks.append(position == 0 ? "" : " + ")
                        .append(member.marker.getId())
                        .append('[')
                        .append(member.groupingEntry
                                .getGroupingCategory())
                        .append(']');
            }
        }
        String trace = candidates.size() + " markers, "
                + result.getGroups().size() + " stacks: "
                + (stacks.length() == 0 ? "none grouped" : stacks);
        // Only changes are worth a line; a pan recomputes the same decision
        // every frame.
        if (trace.equals(lastTracedGrouping)) {
            return;
        }
        lastTracedGrouping = trace;
        if (members != candidates.size()) {
            FMLLog.warning("[%s] map marker grouping covered %d of %d "
                            + "active markers; %s",
                    LostTalesMetaData.MOD_ID, Integer.valueOf(members),
                    Integer.valueOf(candidates.size()), trace);
            return;
        }
        FMLLog.info("[%s] map marker grouping: %s",
                LostTalesMetaData.MOD_ID, trace);
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

    /**
     * Which side of its stack each marker is drawn on: -1 left, 1 right, 0
     * for a leader or a member the fan cannot show.
     *
     * <p>Read once per marker per frame, so it is gathered in one pass over
     * the committed groups rather than by searching them for every marker.</p>
     */
    private static float[] companionSides(
            int candidateCount, LostTalesMapMarkerGrouping.Result result) {
        float[] sides = new float[candidateCount];
        if (result == null) {
            return sides;
        }
        for (LostTalesMapMarkerGrouping.Group group : result.getGroups()) {
            for (Integer memberValue : group.getMemberIndices()) {
                int member = memberValue.intValue();
                if (member >= 0 && member < candidateCount) {
                    sides[member] = group.getCompanionSide(member);
                }
            }
        }
        return sides;
    }

    /**
     * Advances every marker's presentation one frame toward what the grouping
     * decision just committed.
     *
     * <p>Every marker is stepped by the same elapsed-time budget, so all the
     * transitions a single decision causes run together rather than trailing
     * one another. A marker released from a stack captures the offset it was
     * last drawn at here, before anything overwrites it, so its exit starts
     * from its rendered fan position instead of the stack's centre.</p>
     */
    private static void updateGroupingAnimation(
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
            groupingAnimation.clear();
            groupingFadeGui = gui;
        }
        float[] sides = companionSides(candidates.size(), result);
        HashSet<String> activeIds = new HashSet<String>();
        for (int index = 0; index < candidates.size(); index++) {
            String id = candidates.get(index).marker.getId();
            activeIds.add(id);
            boolean standsAlone = representatives[index];
            float targetSlot = standsAlone ? 0.0F : sides[index];
            MarkerAnimationState state = groupingAnimation.get(id);
            if (state == null) {
                groupingAnimation.put(id, MarkerAnimationState.settled(
                        standsAlone, targetSlot));
                continue;
            }
            stepMarkerAnimation(state, standsAlone, targetSlot, step);
        }
        groupingAnimation.keySet().retainAll(activeIds);
        groupingFadeLastNanos = now;
    }

    /**
     * Advances one marker's presentation by one frame's worth of easing.
     *
     * <p>The frame a member is released is the last chance to read where it
     * was actually drawn, so the exit offset is captured before anything is
     * stepped. While a member travels back out of a fan both its side and its
     * membership are held steady and only released once it arrives.
     * Collapsing them on the way used to drag opacity and size down before
     * they rose again, which read as a flicker.</p>
     */
    static void stepMarkerAnimation(
            MarkerAnimationState state, boolean standsAlone,
            float targetSlot, float step) {
        if (state == null) {
            return;
        }
        if (standsAlone && state.visibility < 1.0F
                && !state.leavingStack) {
            state.beginExit();
        }
        state.visibility = approach(
                state.visibility, standsAlone ? 1.0F : 0.0F, step);
        if (!state.leavingStack) {
            state.companionSlot = approach(
                    state.companionSlot, targetSlot, step);
            state.fanStrength = approach(state.fanStrength,
                    fanStrengthTarget(targetSlot), step);
        }
        if (state.leavingStack
                && (state.visibility >= 1.0F || !standsAlone)) {
            state.leavingStack = false;
        }
        if (standsAlone && !state.leavingStack) {
            // Nothing about a fan applies to a marker standing on its own.
            // Letting a trace of one linger is the only way such a marker
            // could keep drawing dimmed, shrunk or pushed to one side after
            // it has arrived, so it is cleared outright rather than eased.
            state.companionSlot = 0.0F;
            state.fanStrength = 0.0F;
        }
    }

    static float approach(
            float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        return current > target
                ? Math.max(target, current - step) : current;
    }

    /**
     * Full membership for either fan side, none for a leader or a member the
     * fan cannot show. Deliberately independent of which side, so changing
     * sides is a slide rather than a disappearance.
     */
    static float fanStrengthTarget(float companionSide) {
        return companionSide == 0.0F ? 0.0F : 1.0F;
    }

    /** Eased 0..1 companion membership, ignoring which side it sits on. */
    private static float getGroupingFanStrength(String markerId) {
        MarkerAnimationState state = groupingAnimation.get(markerId);
        return state == null
                ? 0.0F : smoothStep(clamp01(state.fanStrength));
    }

    private static float getGroupingFade(String markerId) {
        MarkerAnimationState state = groupingAnimation.get(markerId);
        return smoothStep(state == null ? 1.0F : state.visibility);
    }

    private static float getGroupingCompanionSlot(String markerId) {
        MarkerAnimationState state = groupingAnimation.get(markerId);
        if (state == null) {
            return 0.0F;
        }
        float linear = state.companionSlot;
        float sign = linear < 0.0F ? -1.0F : 1.0F;
        return sign * smoothStep(Math.abs(linear));
    }

    private static float smoothStep(float linear) {
        return linear * linear * (3.0F - 2.0F * linear);
    }

    /**
     * Opacity of one member of a stack.
     *
     * <p>A fan companion rests at {@link #GROUP_COMPANION_ALPHA} and rises
     * straight to full opacity as it travels back out, because the caller
     * holds its fan membership steady for the whole journey. A member the fan
     * cannot show has no membership and simply fades away.</p>
     *
     * <p>Membership is deliberately not the signed fan offset: a companion
     * moving from one side of the stack to the other passes through offset
     * zero, and reading opacity from the offset made it blink out on the
     * way.</p>
     */
    static float groupedIconAlpha(
            float visibility, float fanStrength) {
        float released = clamp01(visibility);
        float companionFloor = clamp01(fanStrength)
                * GROUP_COMPANION_ALPHA;
        return companionFloor + (1.0F - companionFloor) * released;
    }

    /** Companions shrink toward the back of the stack as they settle in. */
    static float groupedIconScale(
            float visibility, float fanStrength) {
        float settled = clamp01(fanStrength)
                * (1.0F - clamp01(visibility));
        return 1.0F - settled
                * (1.0F - LostTalesMapMarkerGrouping.COMPANION_SCALE);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static void rebuildRenderedGeometry(
            RenderContext context,
            List<MarkerRenderCandidate> candidates,
            LostTalesMapMarkerGrouping.Result result,
            LostTalesMapMarkerRenderedGeometry.Frame geometry) {
        ScaledResolution resolution = new ScaledResolution(
                context.minecraft,
                context.minecraft.displayWidth,
                context.minecraft.displayHeight);
        geometry.begin(context.gui, context.zoomExp,
                resolution.getScaleFactor(), candidates.size());
        for (LostTalesMapMarkerGrouping.Group group
                : result.getGroups()) {
            int representativeIndex = group.getRepresentativeIndex();
            MarkerRenderCandidate representative =
                    candidates.get(representativeIndex);
            LostTalesMapMarkerRenderedGeometry.RenderedObject object =
                    geometry.beginObject(
                            representative.marker.getId(),
                            representativeIndex, group.size());
            // The fan is laid out around the marker leading it, so its spacing
            // follows that icon's artwork rather than a fixed pixel count.
            LostTalesCompassMarkerIcon leaderIcon =
                    getMarkerIcon(representative.marker);
            // Companions hang off wherever the leader is drawn this frame, so
            // the whole stack pans, zooms and settles as one piece. The leader
            // is resolved once, not re-derived through each companion.
            float anchorX = releasedCoordinate(
                    representative.marker.getId(),
                    representative.position.x, true);
            float anchorY = releasedCoordinate(
                    representative.marker.getId(),
                    representative.position.y, false);
            for (Integer indexValue : group.getMemberIndices()) {
                int candidateIndex = indexValue.intValue();
                MarkerRenderCandidate candidate =
                        candidates.get(candidateIndex);
                String markerId = candidate.marker.getId();
                float visibility = getGroupingFade(markerId);
                boolean isRepresentative =
                        candidateIndex == representativeIndex;
                // Fan state is read from the marker's retained animation, not
                // from whether it currently leads a stack: a companion that
                // has just been released still has to finish the journey out
                // of the fan it was drawn in a frame ago.
                float companionSlot = getGroupingCompanionSlot(markerId);
                float fanStrength = getGroupingFanStrength(markerId);
                float localAlpha = groupedIconAlpha(
                        visibility, fanStrength);
                float drawSize = ICON_DRAW_SIZE
                        * groupedIconScale(visibility, fanStrength);
                float fanOffsetX = fanOffsetX(
                        leaderIcon, ICON_DRAW_SIZE, companionSlot);
                float fanOffsetY = fanOffsetY(
                        leaderIcon, ICON_DRAW_SIZE, companionSlot);
                float finalX;
                float finalY;
                if (isRepresentative) {
                    finalX = anchorX;
                    finalY = anchorY;
                } else {
                    finalX = LostTalesMapMarkerGrouping
                            .transitionCoordinate(anchorX + fanOffsetX,
                                    candidate.position.x, visibility);
                    finalY = LostTalesMapMarkerGrouping
                            .transitionCoordinate(anchorY + fanOffsetY,
                                    candidate.position.y, visibility);
                }
                LostTalesCompassMarkerIcon icon =
                        getMarkerIcon(candidate.marker);
                geometry.addMember(object,
                        candidateIndex, markerId,
                        candidate.position.x, candidate.position.y,
                        finalX, finalY,
                        artHalfWidth(icon, drawSize),
                        artAbove(icon, drawSize),
                        artBelow(icon, drawSize),
                        drawSize / ICON_DRAW_SIZE,
                        visibility, context.alpha * localAlpha,
                        fanOffsetX, fanOffsetY);
                recordRenderedOffset(markerId,
                        finalX - candidate.position.x,
                        finalY - candidate.position.y);
            }
            geometry.finishObject(object);
        }
    }

    /**
     * Where a marker that is standing on its own is drawn.
     *
     * <p>A marker released from a stack keeps travelling from the offset it
     * was last drawn at — its rendered fan position — straight to its own map
     * position. A marker that was never in a stack, or has arrived, simply
     * sits at that position.</p>
     */
    private static float releasedCoordinate(
            String markerId, float markerPosition, boolean horizontal) {
        MarkerAnimationState state = groupingAnimation.get(markerId);
        if (state == null || !state.leavingStack) {
            return markerPosition;
        }
        float offset = horizontal
                ? state.exitOffsetX : state.exitOffsetY;
        // The same eased travel a companion uses, run from the position the
        // marker was last drawn at rather than from a stack anchor.
        return LostTalesMapMarkerGrouping.transitionCoordinate(
                markerPosition + offset, markerPosition,
                getGroupingFade(markerId));
    }

    private static void recordRenderedOffset(
            String markerId, float offsetX, float offsetY) {
        MarkerAnimationState state = groupingAnimation.get(markerId);
        if (state != null) {
            state.recordRendered(offsetX, offsetY);
        }
    }

    /**
     * Marker lookups run once per member per frame while resolving transition
     * anchors, so they read the frame's index instead of scanning the
     * candidate list.
     */
    private static MarkerRenderCandidate findCandidateById(String id) {
        int index = findCandidateIndexById(id);
        return index < 0 ? null : groupingFrame.candidates.get(index);
    }

    private static int findCandidateIndexById(String id) {
        return groupingFrame.indexOf(id);
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

    /**
     * Opacity of the "+X more" summary.
     *
     * <p>It follows only the members it actually counts — the ones the fan
     * cannot draw. Reading every member instead let a companion travelling
     * into or out of the fan blank a label that was still describing several
     * markers nobody could see.</p>
     */
    private static float groupLabelFade(
            LostTalesMapMarkerGrouping.Group group,
            List<MarkerRenderCandidate> candidates) {
        List<Integer> members = group.getMemberIndices();
        float emerged = 0.0F;
        for (int position = LostTalesMapMarkerGrouping.MAX_FAN_MEMBERS;
             position < members.size(); position++) {
            emerged = Math.max(emerged, getGroupingFade(
                    candidates.get(members.get(position).intValue())
                            .marker.getId()));
        }
        return condensedLabelFade(emerged);
    }

    /**
     * The summary fades out as the markers it stands for leave the stack, so
     * it is gone by the time they are visible in their own right.
     */
    static float condensedLabelFade(float emergedVisibility) {
        return 1.0F - clamp01(emergedVisibility);
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
        // Group by the ink that would actually cover another icon. Using the
        // atlas cell overstated every glyph by several transparent pixels, and
        // long waypoint names must not collapse distant map locations either.
        // The rectangle is where the marker is being drawn right now: the map
        // zooms continuously, so there is no settled zoom to look ahead to,
        // and anticipating one merged markers well before they touched.
        LostTalesCompassMarkerIcon icon = getMarkerIcon(marker);
        float halfWidth = artHalfWidth(icon, ICON_DRAW_SIZE);
        float left = LostTalesMapMarkerRenderedGeometry.artLeft(
                position.x, halfWidth);
        float right = LostTalesMapMarkerRenderedGeometry.artRight(
                position.x, halfWidth);
        float top = LostTalesMapMarkerRenderedGeometry.artTop(
                position.y, artAbove(icon, ICON_DRAW_SIZE));
        float bottom = LostTalesMapMarkerRenderedGeometry.artBottom(
                position.y, artBelow(icon, ICON_DRAW_SIZE));
        LostTalesMapMarkerGrouping.Entry entry =
                new LostTalesMapMarkerGrouping.Entry(
                        markerId, displayName, marker.getPriority(),
                        // Grouping eligibility reuses the legend's single
                        // classifier, so what the player filters and what may
                        // share a stack can never disagree.
                        LostTalesMapMarkerGrouping.GroupingCategory
                                .forLegendCategory(
                                        LostTalesMapLegendRegistry
                                                .categoryFor(marker)),
                        left, top, right, bottom);
        candidates.add(new MarkerRenderCandidate(
                marker, waypoint, position, displayName, entry));
    }

    /**
     * Identifies the grouping input, so a decision is recomputed only when
     * something it depends on actually changed.
     *
     * <p>It covers the live zoom and the live marker layout, because the map
     * zooms continuously and grouping follows what is actually on screen. A
     * decision is still one batch: the whole frame is decided from this one
     * snapshot and every transition it causes begins together. Marker
     * identity is hashed in list order because the committed result addresses
     * members by index.</p>
     */
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
            hash = hash * 31L + Float.floatToIntBits(
                    candidate.groupingEntry.getLeft());
            hash = hash * 31L + Float.floatToIntBits(
                    candidate.groupingEntry.getTop());
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
            LostTalesMapMarkerRenderedGeometry.RenderedObject object =
                    getRenderedObject(marker);
            LostTalesMapMarkerRenderedGeometry.Member representative =
                    object == null ? null
                    : object.getRepresentativeMember();
            float markerFade = getGroupingFade(marker.marker.getId());
            if (representative != null) {
                ScreenPosition markerPosition = new ScreenPosition(
                        representative.getCenterX(),
                        representative.getCenterY());
                best = HoverCandidate.marker(marker, markerPosition,
                        markerFade,
                        object.distanceSqToVisibleMemberCenter(
                                mouseX, mouseY));
            }
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
                focused.marker.marker.getId());
        LostTalesMapMarkerRenderedGeometry.RenderedObject object =
                groupingFrame.renderedGeometry
                        .getObjectForCandidate(representativeIndex);
        if (object != null) {
            // The whole stack grows together: the fan spacing is rebuilt at
            // the hover size, otherwise the enlarged sprites close over each
            // other and the fan reads as one smeared blob.
            float hoverScale = (float)ICON_COMPANION_HOVER_DRAW_SIZE
                    / (float)ICON_DRAW_SIZE;
            List<LostTalesMapMarkerRenderedGeometry.Member> members =
                    object.getMembers();
            for (int memberIndex = members.size() - 1;
                 memberIndex >= 0; memberIndex--) {
                LostTalesMapMarkerRenderedGeometry.Member geometry =
                        members.get(memberIndex);
                int candidateIndex = geometry.getCandidateIndex();
                if (candidateIndex == representativeIndex
                        || !geometry.isVisible()) {
                    continue;
                }
                MarkerRenderCandidate companion =
                        groupingFrame.candidates.get(candidateIndex);
                drawMarkerIcon(context.minecraft, companion.marker,
                        geometry.getCenterX()
                                + geometry.getFanOffsetX()
                                * (hoverScale - 1.0F),
                        geometry.getCenterY()
                                + geometry.getFanOffsetY()
                                * (hoverScale - 1.0F),
                        geometry.getRenderAlpha(),
                        ICON_COMPANION_HOVER_DRAW_SIZE);
            }
        }
        LostTalesMapMarkerRenderedGeometry.Member representativeGeometry =
                object == null ? null : object.getRepresentativeMember();
        drawMarkerIcon(context.minecraft,
                focused.marker.marker,
                focused.position.x, focused.position.y,
                representativeGeometry == null
                        ? context.alpha * focused.markerFade
                        : representativeGeometry.getRenderAlpha(),
                true);
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

    /**
     * Portraits are hovered at the size they are drawn at, not at the larger
     * size they grow to once hovered.
     */
    private static boolean isMouseOverPlayerHead(
            float screenX, float screenY, int mouseX, int mouseY) {
        return isMouseOverSquareIcon(screenX, screenY,
                PLAYER_HEAD_DRAW_SIZE, mouseX, mouseY);
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
        LostTalesMapMarkerRenderedGeometry.Frame geometry =
                groupingFrame.renderedGeometry;
        for (int objectIndex = 0;
             objectIndex < geometry.getObjectCount(); objectIndex++) {
            LostTalesMapMarkerRenderedGeometry.RenderedObject object =
                    geometry.getObject(objectIndex);
            MarkerRenderCandidate candidate =
                    groupingFrame.candidates.get(
                            object.getRepresentativeIndex());
            if (!kind.matches(candidate)
                    // Any visible part of the fan owns the whole cluster, at
                    // every zoom level and during grouping animations.
                    || !object.containsInteractionPoint(mouseX, mouseY)) {
                continue;
            }
            double distanceSq =
                    object.distanceSqToVisibleMemberCenter(
                            mouseX, mouseY);
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

    private static LostTalesMapMarkerRenderedGeometry.RenderedObject
    getRenderedObject(
            MarkerRenderCandidate representative) {
        int index = findCandidateIndexById(
                representative.marker.getId());
        return groupingFrame.renderedGeometry
                .getObjectForCandidate(index);
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

        ScreenPosition position = null;
        MarkerRenderCandidate renderedCandidate =
                findCandidateById(marker.getId());
        LostTalesMapMarkerRenderedGeometry.RenderedObject object =
                renderedCandidate == null ? null
                : getRenderedObject(renderedCandidate);
        LostTalesMapMarkerRenderedGeometry.Member renderedRepresentative =
                object == null ? null : object.getRepresentativeMember();
        if (renderedRepresentative != null) {
            position = new ScreenPosition(
                    renderedRepresentative.getCenterX(),
                    renderedRepresentative.getCenterY());
        }
        if (position == null) {
            position = transformMarker(context, marker);
        }
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

    /**
     * Generous cull test: a marker just off the edge may still be the leader
     * of a stack whose fan reaches back onto the map.
     */
    static boolean markerStackOverlapsMapBounds(
            float x, float y, float mapXMin, float mapXMax,
            float mapYMin, float mapYMax) {
        float iconExtent = ICON_HOVER_DRAW_SIZE * 0.5F;
        float horizontalExtent =
                iconExtent * (1.0F + COMPANION_SPREAD_RATIO);
        float topExtent = iconExtent * (1.0F + COMPANION_RISE_RATIO);
        float bottomExtent = iconExtent;
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

    /**
     * Hit test against the ink the renderer actually drew, plus the shared
     * interaction padding. Testing the atlas cell instead made every glyph
     * respond several pixels early, worst of all on its right side where the
     * transparent margin is widest.
     */
    static boolean isMouseOverArt(
            float centerX, float centerY, float artHalfWidth,
            float artAbove, float artBelow, int mouseX, int mouseY) {
        float padding =
                LostTalesMapMarkerRenderedGeometry.INTERACTION_PADDING;
        return mouseX >= LostTalesMapMarkerRenderedGeometry.artLeft(
                        centerX, artHalfWidth) - padding
                && mouseX <= LostTalesMapMarkerRenderedGeometry.artRight(
                        centerX, artHalfWidth) + padding
                && mouseY >= LostTalesMapMarkerRenderedGeometry.artTop(
                        centerY, artAbove) - padding
                && mouseY <= LostTalesMapMarkerRenderedGeometry.artBottom(
                        centerY, artBelow) + padding;
    }

    /** Atlas glyphs hit-test against their ink, not their 17-pixel cell. */
    static boolean isMouseOverAtlasIcon(
            LostTalesCompassMarkerIcon icon,
            float centerX, float centerY, float size,
            int mouseX, int mouseY) {
        return isMouseOverArt(centerX, centerY,
                artHalfWidth(icon, size), artAbove(icon, size),
                artBelow(icon, size), mouseX, mouseY);
    }

    /** Player portraits are plain squares drawn with the same drop shadow. */
    private static boolean isMouseOverSquareIcon(
            float centerX, float centerY, float size,
            int mouseX, int mouseY) {
        float half = size * 0.5F;
        return isMouseOverArt(centerX, centerY, half, half, half,
                mouseX, mouseY);
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
            if (!isMouseOverAtlasIcon(
                    LostTalesCompassMarkerIcon.HOSTILE,
                    position.x, position.y, ICON_DRAW_SIZE,
                    mouseX, mouseY)) {
                continue;
            }
            double dx = position.x - mouseX;
            double dy = position.y - mouseY;
            double distanceSq = dx * dx + dy * dy;
            if (distanceSq < nearestDistanceSq) {
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
                hover ? (float)ICON_HOVER_DRAW_SIZE
                        : (float)ICON_DRAW_SIZE);
    }

    private static void drawMarkerIcon(
            Minecraft minecraft, LostTalesMapMarkerData marker,
            float centerX, float centerY, float alpha, float size) {
        boolean undiscovered = isUndiscoveredButVisible(marker);
        LostTalesCompassMarkerIcon icon = getMarkerIcon(marker);
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
    private static void drawFixedScreenIcon(Minecraft minecraft, float centerX, float centerY, float size, LostTalesCompassMarkerIcon icon, float red, float green, float blue, float alpha) {
        // Anchor the quad so the glyph's ink lands on the marker coordinate.
        // Centring the 17-pixel cell instead left every icon roughly two
        // pixels left and one pixel above where it belongs, and pushed its
        // label off to the side.
        drawIcon(minecraft,
                centerX - icon.getArtCenterX() * artScaleX(size),
                centerY - icon.getArtCenterY() * artScaleY(size),
                size, icon, red, green, blue, alpha);
    }

    private static LostTalesCompassMarkerIcon getMarkerIcon(
            LostTalesMapMarkerData marker) {
        return isUndiscoveredButVisible(marker)
                ? LostTalesCompassMarkerIcon.UNDISCOVERED
                : LostTalesCompassMarkerIcon.fromName(
                        marker.getIconName());
    }

    private static float artScaleX(float size) {
        return size / (float)LostTalesCompassMarkerIcon.WIDTH;
    }

    private static float artScaleY(float size) {
        return size / (float)LostTalesCompassMarkerIcon.HEIGHT;
    }

    /** Ink half-width of a glyph drawn at the given quad size, in GUI pixels. */
    static float artHalfWidth(
            LostTalesCompassMarkerIcon icon, float size) {
        return (icon.getArtRight() - icon.getArtLeft())
                * 0.5F * artScaleX(size);
    }

    static float artAbove(LostTalesCompassMarkerIcon icon, float size) {
        return (icon.getArtCenterY() - icon.getArtTop())
                * artScaleY(size);
    }

    static float artBelow(LostTalesCompassMarkerIcon icon, float size) {
        return (icon.getArtBottom() - icon.getArtCenterY())
                * artScaleY(size);
    }

    /**
     * Sideways offset of a fan companion, as a share of the leading icon's
     * own half-width.
     *
     * <p>A fixed pixel offset was tuned against the narrow pin glyph. The
     * wider artwork buried its companions almost completely, and enlarging
     * the sprites on hover without enlarging the spacing collapsed the fan
     * into a single blob. Deriving the spread from the icon keeps the same
     * arrangement at every icon size and at every scale.</p>
     *
     * @param slot -1 for the left companion, 1 for the right, eased through
     *             zero while a companion changes sides
     */
    static float fanOffsetX(
            LostTalesCompassMarkerIcon icon, float size, float slot) {
        return slot * artHalfWidth(icon, size) * COMPANION_SPREAD_RATIO;
    }

    /** Companions rise slightly behind the leader, whichever side they sit on. */
    static float fanOffsetY(
            LostTalesCompassMarkerIcon icon, float size, float slot) {
        return -Math.abs(slot) * artAbove(icon, size)
                * COMPANION_RISE_RATIO;
    }

    private static void drawIcon(Minecraft minecraft, float x, float y, float size, LostTalesCompassMarkerIcon icon, float red, float green, float blue, float alpha) {
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

    /**
     * Draws the cluster summary directly at the supplied anchor, which the
     * shared geometry already places below the complete fan. Scale and fade
     * match {@link #drawLabel}, so both lines shrink together while zooming.
     */
    private static void drawGroupLabel(
            FontRenderer fontRenderer, String label,
            float anchorX, float anchorY, float alpha,
            RenderContext context) {
        if (fontRenderer == null || label == null || label.length() == 0
                || alpha <= 0.0F) {
            return;
        }
        float scale = alpha;
        int textWidth = fontRenderer.getStringWidth(label);
        float halfWidth = textWidth * scale / 2.0F;
        float bottom = anchorY + fontRenderer.FONT_HEIGHT * scale;
        if (anchorX + halfWidth < context.mapXMin
                || anchorX - halfWidth > context.mapXMax
                || bottom < context.mapYMin || anchorY > context.mapYMax) {
            return;
        }
        int alphaInt = Math.max(4,
                Math.min(255, (int)(alpha * 0.8F * 255.0F)));
        int color = (alphaInt << 24) | 0x00FFFFFF;
        int shadowColor = alphaInt << 24;
        GL11.glPushMatrix();
        GL11.glTranslatef(anchorX, anchorY, 0.0F);
        GL11.glScalef(scale, scale, scale);
        try {
            int x = -textWidth / 2;
            fontRenderer.drawString(label, x + 1, 1, shadowColor);
            fontRenderer.drawString(label, x, 0, color);
        } finally {
            GL11.glPopMatrix();
        }
    }

    static String getCondensedMemberLabel(int condensedMemberCount) {
        return StatCollector.translateToLocalFormatted(
                GROUP_MORE_LANG_KEY,
                Integer.valueOf(condensedMemberCount));
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

    /**
     * What one marker's presentation is doing between two grouping decisions.
     *
     * <p>{@code visibility} is how far the marker has travelled out of the
     * stack it belongs to: 0 is gathered on its representative, 1 is standing
     * at its own position. {@code companionSlot} and {@code fanStrength} are
     * eased separately so a companion changing sides slides across the
     * leader instead of blinking out as its offset crosses zero.</p>
     *
     * <p>{@code renderedOffsetX/Y} is the marker's last drawn position
     * relative to its own map position. Storing the offset rather than the
     * screen point keeps it correct while the map pans or zooms underneath
     * it. When a visible fan member is released, that offset is copied into
     * {@code exitOffsetX/Y} and the marker travels from exactly where it was
     * last drawn to its own position — not from the stack's centre.</p>
     */
    static final class MarkerAnimationState {
        /**
         * A marker seen for the first time is already where it belongs; only
         * later decisions are worth animating.
         */
        static MarkerAnimationState settled(
                boolean standsAlone, float companionSlot) {
            MarkerAnimationState state = new MarkerAnimationState();
            state.visibility = standsAlone ? 1.0F : 0.0F;
            state.companionSlot = standsAlone ? 0.0F : companionSlot;
            state.fanStrength = fanStrengthTarget(state.companionSlot);
            return state;
        }

        private float visibility;
        private float companionSlot;
        private float fanStrength;
        private float renderedOffsetX;
        private float renderedOffsetY;
        private boolean hasRenderedOffset;
        private float exitOffsetX;
        private float exitOffsetY;
        private boolean leavingStack;

        private void beginExit() {
            this.exitOffsetX = this.hasRenderedOffset
                    ? this.renderedOffsetX : 0.0F;
            this.exitOffsetY = this.hasRenderedOffset
                    ? this.renderedOffsetY : 0.0F;
            this.leavingStack = true;
        }

        void recordRendered(float offsetX, float offsetY) {
            this.renderedOffsetX = offsetX;
            this.renderedOffsetY = offsetY;
            this.hasRenderedOffset = true;
        }

        float getVisibility() { return this.visibility; }
        float getCompanionSlot() { return this.companionSlot; }
        float getFanStrength() { return this.fanStrength; }
        float getExitOffsetX() { return this.exitOffsetX; }
        float getExitOffsetY() { return this.exitOffsetY; }
        boolean isLeavingStack() { return this.leavingStack; }
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
                Float.NaN,
                new LostTalesMapMarkerRenderedGeometry.Frame());
        private final LOTRGuiMap gui;
        private final long signature;
        private final List<MarkerRenderCandidate> candidates;
        private final LostTalesMapMarkerGrouping.Result result;
        private final boolean groupingEnabled;
        private final float zoomExp;
        private final LostTalesMapMarkerRenderedGeometry.Frame
                renderedGeometry;
        /** Marker ID to candidate index, so per-frame lookups stay O(1). */
        private final Map<String, Integer> indexById;
        /**
         * Back-to-front draw order over whole stacks; it only changes with
         * the committed grouping decision.
         */
        private final List<Integer> groupsBottomToTop;

        private GroupingFrame(
                LOTRGuiMap gui, long signature,
                List<MarkerRenderCandidate> candidates,
                LostTalesMapMarkerGrouping.Result result,
                boolean groupingEnabled, float zoomExp,
                LostTalesMapMarkerRenderedGeometry.Frame
                        renderedGeometry) {
            this.gui = gui;
            this.signature = signature;
            this.candidates = Collections.unmodifiableList(
                    new ArrayList<MarkerRenderCandidate>(candidates));
            this.result = result;
            this.groupingEnabled = groupingEnabled;
            this.zoomExp = zoomExp;
            this.renderedGeometry = renderedGeometry == null
                    ? new LostTalesMapMarkerRenderedGeometry.Frame()
                    : renderedGeometry;
            this.indexById = new HashMap<String, Integer>(
                    Math.max(4, this.candidates.size() * 2));
            ArrayList<LostTalesMapMarkerGrouping.Entry> entries =
                    new ArrayList<LostTalesMapMarkerGrouping.Entry>(
                            this.candidates.size());
            for (int index = 0; index < this.candidates.size(); index++) {
                MarkerRenderCandidate candidate =
                        this.candidates.get(index);
                this.indexById.put(candidate.marker.getId(),
                        Integer.valueOf(index));
                entries.add(candidate.groupingEntry);
            }
            this.groupsBottomToTop =
                    LostTalesMapMarkerGrouping.groupsBottomToTop(
                            entries, result.getGroups());
        }

        private int indexOf(String markerId) {
            Integer index = markerId == null
                    ? null : this.indexById.get(markerId);
            return index == null ? -1 : index.intValue();
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
