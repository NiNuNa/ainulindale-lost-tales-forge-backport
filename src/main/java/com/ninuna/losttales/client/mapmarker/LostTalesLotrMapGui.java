package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.party.ClientPartyStateCache;
import com.ninuna.losttales.client.party.PartyClientRequestManager;
import com.ninuna.losttales.party.sync.PartyStateSnapshot;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesWaystoneTravelRequestPacket;
import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import lotr.client.LOTRKeyHandler;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.map.LOTRAbstractWaypoint;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Mouse;

/**
 * LOTR map screen wrapper used only to intercept the waypoint rendering pass.
 *
 * <p>The base map handles movement and discovered LOTR waypoints. This wrapper
 * owns Lost Tales icons plus undiscovered hover/selection so private names,
 * lore, and fast-travel actions never reach the native GUI path.</p>
 */
public class LostTalesLotrMapGui extends LOTRGuiMap {
    private static Field controlZoneFactionField;
    private static Field conquestGridField;
    private static boolean initialModeReflectionReady;
    private static boolean initialModeReflectionFailed;
    private static Field zoomPowerField;
    private static Field zoomTicksField;
    private static Field zoomExpField;
    private static Field zoomScaleField;
    private static Field zoomScaleStableField;
    private static Field hasOverlayField;
    private static Field mouseInFacScrollField;
    private static Field selectedWaypointField;
    private static Field widgetZoomInField;
    private static Field widgetZoomOutField;
    private static Method hasConquestScrollBarMethod;
    private static boolean smoothZoomReflectionReady;
    private static boolean smoothZoomReflectionFailed;

    static final float SMOOTH_ZOOM_MIN = -3.0F;
    static final float SMOOTH_ZOOM_MAX = 4.0F;
    static final float SMOOTH_ZOOM_WHEEL_INCREMENT = 0.25F;
    private static final float SMOOTH_ZOOM_EASING = 0.35F;
    private static final float SMOOTH_ZOOM_SNAP_EPSILON = 0.001F;

    private LostTalesMapMarkerData selectedCustomMarker;
    private boolean transientEnemyMarkersRendered;
    private boolean roleplayPlayerHeadsRendered;
    private boolean smoothZoomInitialized;
    private float smoothZoomPrevious;
    private float smoothZoomCurrent;
    private float smoothZoomTarget;

    /**
     * Creates the marker-aware map without discarding a mode configured by
     * LOTR before {@code GuiOpenEvent}, such as faction control zones or the
     * conquest grid. If the expected v36.15 fields are unavailable, retain
     * LOTR's original screen so base-mod behavior is not silently disabled.
     */
    public static LOTRGuiMap replace(LOTRGuiMap original) {
        if (original == null) {
            return null;
        }
        LostTalesLotrMapGui replacement = new LostTalesLotrMapGui();
        return copyInitialMode(original, replacement)
                ? replacement : original;
    }

    static boolean copyInitialMode(
            LOTRGuiMap original, LostTalesLotrMapGui replacement) {
        if (!ensureInitialModeReflection()) {
            return false;
        }
        try {
            Object faction = controlZoneFactionField.get(original);
            if (faction instanceof LOTRFaction) {
                replacement.setControlZone((LOTRFaction)faction);
            }
            if (conquestGridField.getBoolean(original)) {
                replacement.setConquestGrid();
            }
            return true;
        } catch (IllegalAccessException exception) {
            markInitialModeReflectionFailed(exception);
            return false;
        } catch (RuntimeException exception) {
            markInitialModeReflectionFailed(exception);
            return false;
        } catch (LinkageError error) {
            markInitialModeReflectionFailed(error);
            return false;
        }
    }

    private static synchronized boolean ensureInitialModeReflection() {
        if (initialModeReflectionReady) {
            return true;
        }
        if (initialModeReflectionFailed) {
            return false;
        }
        try {
            controlZoneFactionField = LOTRGuiMap.class.getDeclaredField(
                    "controlZoneFaction");
            conquestGridField = LOTRGuiMap.class.getDeclaredField(
                    "isConquestGrid");
            controlZoneFactionField.setAccessible(true);
            conquestGridField.setAccessible(true);
            initialModeReflectionReady = true;
            return true;
        } catch (ReflectiveOperationException exception) {
            markInitialModeReflectionFailed(exception);
            return false;
        } catch (RuntimeException exception) {
            markInitialModeReflectionFailed(exception);
            return false;
        } catch (LinkageError error) {
            markInitialModeReflectionFailed(error);
            return false;
        }
    }

    private static void markInitialModeReflectionFailed(Throwable cause) {
        initialModeReflectionReady = false;
        initialModeReflectionFailed = true;
        FMLLog.warning(
                "[%s] LOTR map wrapper disabled: expected v36.15 map-mode "
                        + "fields were unavailable; preserving the native map (%s)",
                LostTalesMetaData.MOD_ID,
                cause == null ? "unknown error" : cause.toString());
    }

    @Override
    public void initGui() {
        boolean preserveSmoothZoom = this.smoothZoomInitialized;
        super.initGui();
        if (!preserveSmoothZoom) {
            initializeSmoothZoom();
        } else if (ensureSmoothZoomReflection()) {
            try {
                zoomTicksField.setInt(this, 0);
                zoomPowerField.setInt(null,
                        Math.round(this.smoothZoomTarget));
            } catch (IllegalAccessException exception) {
                markSmoothZoomReflectionFailed(exception);
                this.smoothZoomInitialized = false;
            }
        }
    }

    private void initializeSmoothZoom() {
        if (!ensureSmoothZoomReflection()) {
            this.smoothZoomInitialized = false;
            return;
        }
        try {
            float initial = clampSmoothZoom(
                    zoomPowerField.getInt(null));
            this.smoothZoomPrevious = initial;
            this.smoothZoomCurrent = initial;
            this.smoothZoomTarget = initial;
            zoomTicksField.setInt(this, 0);
            this.smoothZoomInitialized = true;
        } catch (IllegalAccessException exception) {
            markSmoothZoomReflectionFailed(exception);
            this.smoothZoomInitialized = false;
        } catch (RuntimeException exception) {
            markSmoothZoomReflectionFailed(exception);
            this.smoothZoomInitialized = false;
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (!this.smoothZoomInitialized) {
            return;
        }
        this.smoothZoomPrevious = this.smoothZoomCurrent;
        this.smoothZoomCurrent = advanceSmoothZoom(
                this.smoothZoomCurrent, this.smoothZoomTarget);
    }

    @Override
    public void setupZoomVariables(float partialTicks) {
        if (!this.smoothZoomInitialized || !ensureSmoothZoomReflection()) {
            super.setupZoomVariables(partialTicks);
            return;
        }
        float partial = Math.max(0.0F, Math.min(1.0F, partialTicks));
        float exponent = this.smoothZoomPrevious
                + (this.smoothZoomCurrent - this.smoothZoomPrevious)
                * partial;
        float scale = (float)Math.pow(2.0D, exponent);
        try {
            zoomExpField.setFloat(this, exponent);
            zoomScaleField.setFloat(this, scale);
            // LOTR normally pins this to an integer zoom during its six-tick
            // animation. Matching the live scale removes the visible level
            // steps while retaining LOTR's existing transform and map center.
            zoomScaleStableField.setFloat(this, scale);
        } catch (IllegalAccessException exception) {
            markSmoothZoomReflectionFailed(exception);
            this.smoothZoomInitialized = false;
            super.setupZoomVariables(partialTicks);
        } catch (RuntimeException exception) {
            markSmoothZoomReflectionFailed(exception);
            this.smoothZoomInitialized = false;
            super.setupZoomVariables(partialTicks);
        }
    }

    @Override
    public void renderMapAndOverlay(
            boolean sepia, float alpha, boolean drawOverlay) {
        if (!this.smoothZoomInitialized
                || !LostTalesLotrSmoothMapRenderer.render(
                        this, sepia, alpha, drawOverlay)) {
            super.renderMapAndOverlay(sepia, alpha, drawOverlay);
        }
    }

    @Override
    public void renderRoads(boolean labels) {
        LostTalesLotrRoadLabelRenderer.Prepared prepared = labels
                ? LostTalesLotrRoadLabelRenderer.prepare(this) : null;
        if (prepared == null) {
            super.renderRoads(labels);
            return;
        }
        // Preserve LOTR's pixel-art road dots, but replace only its unstable
        // interval-based names with fixed world anchors and smooth fades.
        super.renderRoads(false);
        prepared.render();
    }

    @Override
    public void handleMouseInput() {
        int wheel = Mouse.getEventDWheel();
        if (!shouldHandleSmoothZoomWheel(wheel)) {
            super.handleMouseInput();
            return;
        }

        int oldZoomTicks;
        try {
            oldZoomTicks = zoomTicksField.getInt(this);
            // Let GuiScreen process this mouse event, but make LOTR skip only
            // its integer zoom branch for the wheel event.
            zoomTicksField.setInt(this, 1);
        } catch (IllegalAccessException exception) {
            markSmoothZoomReflectionFailed(exception);
            this.smoothZoomInitialized = false;
            super.handleMouseInput();
            return;
        }
        try {
            super.handleMouseInput();
        } finally {
            try {
                zoomTicksField.setInt(this, oldZoomTicks);
            } catch (IllegalAccessException exception) {
                markSmoothZoomReflectionFailed(exception);
                this.smoothZoomInitialized = false;
            }
        }
        float wheelSteps = Math.max(1.0F,
                Math.abs((float)wheel) / 120.0F);
        adjustSmoothZoom(Math.signum((float)wheel)
                * SMOOTH_ZOOM_WHEEL_INCREMENT * wheelSteps);
    }

    private boolean shouldHandleSmoothZoomWheel(int wheel) {
        if (wheel == 0 || !this.smoothZoomInitialized
                || !ensureSmoothZoomReflection()) {
            return false;
        }
        try {
            if (hasOverlayField.getBoolean(this)) {
                return false;
            }
            return !conquestGridField.getBoolean(this)
                    || !mouseInFacScrollField.getBoolean(this)
                    || !((Boolean)hasConquestScrollBarMethod.invoke(this))
                            .booleanValue();
        } catch (ReflectiveOperationException exception) {
            markSmoothZoomReflectionFailed(exception);
            this.smoothZoomInitialized = false;
            return false;
        } catch (RuntimeException exception) {
            markSmoothZoomReflectionFailed(exception);
            this.smoothZoomInitialized = false;
            return false;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (this.smoothZoomInitialized && button != null
                && ensureSmoothZoomReflection()) {
            try {
                if (!hasOverlayField.getBoolean(this)) {
                    if (widgetZoomInField.get(this) == button) {
                        adjustSmoothZoom(SMOOTH_ZOOM_WHEEL_INCREMENT);
                        return;
                    }
                    if (widgetZoomOutField.get(this) == button) {
                        adjustSmoothZoom(-SMOOTH_ZOOM_WHEEL_INCREMENT);
                        return;
                    }
                }
            } catch (IllegalAccessException exception) {
                markSmoothZoomReflectionFailed(exception);
                this.smoothZoomInitialized = false;
            } catch (RuntimeException exception) {
                markSmoothZoomReflectionFailed(exception);
                this.smoothZoomInitialized = false;
            }
        }
        super.actionPerformed(button);
    }

    private void adjustSmoothZoom(float delta) {
        float adjusted = clampSmoothZoom(this.smoothZoomTarget + delta);
        if (adjusted == this.smoothZoomTarget) {
            return;
        }
        this.smoothZoomTarget = adjusted;
        try {
            zoomPowerField.setInt(null, Math.round(adjusted));
            selectedWaypointField.set(this, null);
        } catch (IllegalAccessException exception) {
            markSmoothZoomReflectionFailed(exception);
            this.smoothZoomInitialized = false;
        }
    }

    static float clampSmoothZoom(float value) {
        return Math.max(SMOOTH_ZOOM_MIN,
                Math.min(SMOOTH_ZOOM_MAX, value));
    }

    static float advanceSmoothZoom(float current, float target) {
        target = clampSmoothZoom(target);
        float difference = target - current;
        if (Math.abs(difference) <= SMOOTH_ZOOM_SNAP_EPSILON) {
            return target;
        }
        float advanced = current + difference * SMOOTH_ZOOM_EASING;
        if ((difference > 0.0F && advanced > target)
                || (difference < 0.0F && advanced < target)) {
            return target;
        }
        return clampSmoothZoom(advanced);
    }

    private static synchronized boolean ensureSmoothZoomReflection() {
        if (smoothZoomReflectionReady) {
            return true;
        }
        if (smoothZoomReflectionFailed) {
            return false;
        }
        try {
            zoomPowerField = field("zoomPower");
            zoomTicksField = field("zoomTicks");
            zoomExpField = field("zoomExp");
            zoomScaleField = field("zoomScale");
            zoomScaleStableField = field("zoomScaleStable");
            hasOverlayField = field("hasOverlay");
            if (conquestGridField == null) {
                conquestGridField = field("isConquestGrid");
            }
            mouseInFacScrollField = field("mouseInFacScroll");
            selectedWaypointField = field("selectedWaypoint");
            widgetZoomInField = field("widgetZoomIn");
            widgetZoomOutField = field("widgetZoomOut");
            hasConquestScrollBarMethod = LOTRGuiMap.class
                    .getDeclaredMethod("hasConquestScrollBar");
            hasConquestScrollBarMethod.setAccessible(true);
            smoothZoomReflectionReady = true;
            return true;
        } catch (ReflectiveOperationException exception) {
            markSmoothZoomReflectionFailed(exception);
            return false;
        } catch (RuntimeException exception) {
            markSmoothZoomReflectionFailed(exception);
            return false;
        } catch (LinkageError error) {
            markSmoothZoomReflectionFailed(error);
            return false;
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = LOTRGuiMap.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void markSmoothZoomReflectionFailed(Throwable cause) {
        if (!smoothZoomReflectionFailed) {
            FMLLog.warning(
                    "[%s] Smooth LOTR map zoom disabled; using native zoom (%s)",
                    LostTalesMetaData.MOD_ID,
                    cause == null ? "unknown error" : cause.toString());
        }
        smoothZoomReflectionReady = false;
        smoothZoomReflectionFailed = true;
    }

    @Override
    public void renderWaypoints(List<LOTRAbstractWaypoint> waypoints, int pass, int mouseX, int mouseY, boolean drawLabels, boolean includeHidden) {
        if (pass != 0) {
            super.renderWaypoints(
                    LostTalesLotrMapMarkerIconOverlay
                            .getWaypointsForLotrRender(waypoints, pass),
                    pass, mouseX, mouseY, drawLabels, includeHidden);
            if (pass == 1) {
                LostTalesLotrMapMarkerIconOverlay
                        .renderMappedWaypointHoverTooltip(
                                this, this.selectedCustomMarker,
                                mouseX, mouseY);
                LostTalesLotrMapMarkerIconOverlay
                        .renderLockedMappedMarkerHoverTooltip(
                                this, this.selectedCustomMarker,
                                mouseX, mouseY);
                LostTalesLotrMapMarkerIconOverlay.renderStandaloneMarkerHoverTooltip(this, this.selectedCustomMarker, mouseX, mouseY);
                LostTalesLotrMapMarkerIconOverlay.renderTransientEnemyMarkerHoverTooltip(this, mouseX, mouseY);
            }
            return;
        }

        List<LOTRAbstractWaypoint> baseWaypoints =
                LostTalesLotrMapMarkerIconOverlay
                        .getWaypointsForLotrRender(waypoints, pass);
        super.renderWaypoints(baseWaypoints, pass, mouseX, mouseY, drawLabels, includeHidden);
        LostTalesLotrMapMarkerIconOverlay.renderGroupedMarkers(
                this, waypoints, mouseX, mouseY,
                drawLabels, includeHidden);
        renderTransientEnemyMarkersOnce(mouseX, mouseY, drawLabels);
        renderRoleplayPlayerHeadsOnce(mouseX, mouseY);
    }

    private void renderTransientEnemyMarkersOnce(int mouseX, int mouseY, boolean drawLabels) {
        if (!this.transientEnemyMarkersRendered) {
            this.transientEnemyMarkersRendered = true;
            LostTalesLotrMapMarkerIconOverlay.renderTransientEnemyMarkers(this, mouseX, mouseY, drawLabels);
        }
    }

    private void renderRoleplayPlayerHeadsOnce(int mouseX, int mouseY) {
        if (!this.roleplayPlayerHeadsRendered) {
            this.roleplayPlayerHeadsRendered = true;
            LostTalesLotrMapMarkerIconOverlay.renderRoleplayPlayerHeads(
                    this, mouseX, mouseY);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.transientEnemyMarkersRendered = false;
        this.roleplayPlayerHeadsRendered = false;
        LostTalesLotrMapMarkerIconOverlay
                .clearInvalidLotrSelection(this);
        if (this.selectedCustomMarker != null) {
            this.selectedCustomMarker =
                    LostTalesClientMapMarkerStore.getSharedMarker(
                            this.selectedCustomMarker.getId());
            LostTalesLotrMapMarkerIconOverlay.clearLotrSelectedWaypoint(this);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (this.selectedCustomMarker != null
                && !LostTalesLotrMapMarkerIconOverlay
                .renderCustomMarkerSelection(
                        this, this.selectedCustomMarker, mouseX, mouseY)) {
            this.selectedCustomMarker = null;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (this.mc != null && this.mc.thePlayer != null) {
            PartyStateSnapshot state = ClientPartyStateCache.getSnapshot();
            if (button == 0) {
                LostTalesMapMarkerData lockedMarker =
                        LostTalesLotrMapMarkerIconOverlay
                                .getHoveredLockedMappedMarker(
                                        this, mouseX, mouseY);
                if (lockedMarker != null) {
                    this.selectedCustomMarker = lockedMarker;
                    LostTalesLotrMapMarkerIconOverlay
                            .clearLotrSelectedWaypoint(this);
                    return;
                }
                LOTRAbstractWaypoint mappedWaypoint =
                        LostTalesLotrMapMarkerIconOverlay
                                .getHoveredMappedWaypoint(
                                        this, mouseX, mouseY);
                LostTalesMapMarkerData mappedMarker =
                        LostTalesLotrMapMarkerIconOverlay
                                .getMarkerForWaypoint(mappedWaypoint);
                if (mappedMarker != null) {
                    this.selectedCustomMarker = mappedMarker;
                    LostTalesLotrMapMarkerIconOverlay
                            .clearLotrSelectedWaypoint(this);
                    return;
                }
                String localMarkerId = getLocalGoHereMarkerId(state);
                LostTalesMapMarkerData marker =
                        LostTalesLotrMapMarkerIconOverlay
                        .getHoveredStandaloneMarker(
                                this, mouseX, mouseY, localMarkerId);
                if (marker != null
                        && localMarkerId != null
                        && localMarkerId.equals(marker.getId())) {
                    PartyClientRequestManager.removeGoHereMarker(
                            state.getActiveCharacterId(),
                            null,
                            -1L);
                    this.selectedCustomMarker = null;
                    LostTalesLotrMapMarkerIconOverlay
                            .clearLotrSelectedWaypoint(this);
                    return;
                }
                if (marker != null) {
                    this.selectedCustomMarker = marker;
                    LostTalesLotrMapMarkerIconOverlay
                            .clearLotrSelectedWaypoint(this);
                    return;
                }
                this.selectedCustomMarker = null;
            } else if (button == 1 && hasUsableCharacter(state)) {
                int[] worldPosition = LostTalesLotrMapMarkerIconOverlay
                        .getMapClickWorldPosition(this);
                if (worldPosition != null) {
                    PartyClientRequestManager.setGoHereMarker(
                            state.getActiveCharacterId(),
                            null,
                            -1L,
                            this.mc.thePlayer.dimension,
                            worldPosition[0], worldPosition[1]);
                    this.selectedCustomMarker = null;
                    LostTalesLotrMapMarkerIconOverlay
                            .clearLotrSelectedWaypoint(this);
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
        LostTalesLotrMapMarkerIconOverlay
                .clearInvalidLotrSelection(this);
    }

    private static boolean hasUsableCharacter(PartyStateSnapshot state) {
        return state != null && state.isAvailable()
                && state.getActiveCharacterId() != null;
    }

    private static String getLocalGoHereMarkerId(PartyStateSnapshot state) {
        return hasUsableCharacter(state)
                ? "party_go_here:" + state.getActiveCharacterId()
                : null;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (this.selectedCustomMarker != null
                && keyCode == LOTRKeyHandler.keyBindingFastTravel.getKeyCode()) {
            if (this.selectedCustomMarker.hasFastTravel()
                    && (!this.selectedCustomMarker.isDiscoverable()
                    || LostTalesClientMapMarkerVisibility.isDiscovered(
                            this.selectedCustomMarker))
                    && sendWaystoneTravel(
                            this.selectedCustomMarker.getId())) {
                return;
            }
            return;
        }
        if (keyCode == LOTRKeyHandler.keyBindingFastTravel.getKeyCode()) {
            LOTRAbstractWaypoint selected =
                    LostTalesLotrMapMarkerIconOverlay
                            .getSelectedWaypoint(this);
            if (LostTalesLotrMapMarkerIconOverlay
                    .isDeletedMappedWaypoint(selected)) {
                LostTalesLotrMapMarkerIconOverlay
                        .clearLotrSelectedWaypoint(this);
                return;
            }
            LostTalesMapMarkerData marker =
                    LostTalesLotrMapMarkerIconOverlay
                            .getMarkerForWaypoint(selected);
            if (marker != null) {
                if (!marker.hasFastTravel()) {
                    return;
                }
                if (sendWaystoneTravel(marker.getId())) {
                    return;
                }
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        LostTalesLotrMapMarkerIconOverlay.clearGrouping(this);
        LostTalesLotrRoadLabelRenderer.clear(this);
        super.onGuiClosed();
    }

    private boolean sendWaystoneTravel(String destinationMarkerId) {
        if (this.mc == null || this.mc.thePlayer == null
                || destinationMarkerId == null
                || destinationMarkerId.length() == 0) {
            return false;
        }
        LostTalesClientWaystoneTravelContext.Context context =
                LostTalesClientWaystoneTravelContext.get(
                        this.mc.thePlayer.dimension);
        if (context == null) {
            return false;
        }
        try {
            LostTalesNetworkHandler.CHANNEL.sendToServer(
                    new LostTalesWaystoneTravelRequestPacket(
                            context.getX(), context.getY(),
                            context.getZ(),
                            context.getSourceMarkerId(),
                            destinationMarkerId));
            LostTalesClientWaystoneTravelContext.clear();
            this.mc.thePlayer.closeScreen();
            return true;
        } catch (IllegalArgumentException exception) {
            LostTalesClientWaystoneTravelContext.clear();
            return false;
        }
    }
}
