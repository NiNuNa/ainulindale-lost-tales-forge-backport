package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.client.party.ClientPartyStateCache;
import com.ninuna.losttales.client.party.ClientPartyTrackingCache;
import com.ninuna.losttales.client.party.PartyClientRequestManager;
import com.ninuna.losttales.party.model.PartyPersonalMarkerOwner;
import com.ninuna.losttales.party.sync.PartyStateSnapshot;
import com.ninuna.losttales.world.map.waypoint.LostTalesMapCoordinateHelper;
import java.util.UUID;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesWaystoneTravelRequestPacket;
import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lotr.client.LOTRKeyHandler;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.LOTRDimension;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.fellowship.LOTRFellowshipClient;
import lotr.common.network.LOTRPacketCreateCWP;
import lotr.common.network.LOTRPacketDeleteCWP;
import lotr.common.network.LOTRPacketHandler;
import lotr.common.network.LOTRPacketRenameCWP;
import lotr.common.network.LOTRPacketShareCWP;
import lotr.common.world.map.LOTRCustomWaypoint;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.map.LOTRAbstractWaypoint;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

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
    private static Method hasConquestScrollBarMethod;
    private static boolean smoothZoomReflectionReady;
    private static boolean smoothZoomReflectionFailed;

    /** Opens the personal-waypoint editor while the map is on screen. */
    static final int CREATE_WAYPOINT_KEY = Keyboard.KEY_C;
    /** Takes the camera back to where the player is standing. */
    static final int CURRENT_LOCATION_KEY = Keyboard.KEY_R;
    /** Opens the find-a-place popup. */
    static final int FIND_LOCATION_KEY = Keyboard.KEY_F;
    /** Held and dragged sideways to turn the map. */
    static final int ROTATE_MAP_BUTTON = 1;

    /**
     * Zoom bounds, a little wider either way than LOTR's own -3..4. Its
     * integer zoom power is still kept inside its own range; only the eased
     * exponent this class owns goes further.
     */
    static final float SMOOTH_ZOOM_MIN = -3.6F;
    static final float SMOOTH_ZOOM_MAX = 4.6F;
    /** LOTR's own zoom power, which its unused internals still read. */
    private static final int LOTR_ZOOM_POWER_MIN = -3;
    private static final int LOTR_ZOOM_POWER_MAX = 4;
    /** One notch of the wheel, in zoom exponent. */
    static final float SMOOTH_ZOOM_WHEEL_INCREMENT = 0.12F;
    private static final float SMOOTH_ZOOM_EASING = 0.2F;
    private static final float SMOOTH_ZOOM_SNAP_EPSILON = 0.001F;
    /**
     * Zoom a travel destination is framed at, unless the player is already
     * looking closer than that. Close enough to read the ground around it,
     * short of the level where the map stops grouping markers at all.
     */
    static final float FAST_TRAVEL_FOCUS_ZOOM_EXP = 2.6F;
    /**
     * Zoom the map settles at when asked where the player is or where a
     * searched place is, unless it is already closer than that.
     */
    static final float LOCATION_FOCUS_ZOOM_EXP = 2.6F;
    /**
     * The lean runs 0..1 while the shared smoothing works in degrees and
     * stops within a hundredth of one, so it is scaled into that range and
     * back rather than given a second easing of its own.
     */
    private static final float LEAN_SMOOTHING_SCALE = 90.0F;

    private boolean transientEnemyMarkersRendered;
    private boolean roleplayPlayerHeadsRendered;
    private boolean mapControlBarRendered;
    private boolean mapLegendOpen;
    private int mapLegendScrollIndex;
    private boolean smoothZoomInitialized;
    private float smoothZoomPrevious;
    private float smoothZoomCurrent;
    private float smoothZoomTarget;
    private List<LOTRAbstractWaypoint> clickableNativeWaypoints =
            Collections.emptyList();
    private LostTalesMapFastTravelPrompt fastTravelPrompt;
    private LostTalesMapWaypointPrompt waypointPrompt;
    private LostTalesMapMoveMarkerPrompt moveMarkerPrompt;
    private LostTalesMapSearchPrompt searchPrompt;
    /**
     * Where the "go here" marker would move to, as {@code {dimension, x, z}},
     * while that question is on screen. Null when the popup was opened on the
     * marker itself, which offers only leaving or removing it.
     */
    private int[] pendingGoHereDestination;
    /** The waypoint the editor is open on, or null while creating one. */
    private LOTRCustomWaypoint editedWaypoint;
    private LostTalesMapMarkerData promptCustomMarker;
    private LOTRAbstractWaypoint promptNativeWaypoint;
    /**
     * The destinations the open travel popup can be stepped between, in a
     * fixed order, built when it opened. Null whenever no popup is open.
     */
    private LostTalesMapFastTravelCycle fastTravelCycle;
    /**
     * Every waypoint LOTR handed the last marker pass, kept so the travel
     * popup can be moved between destinations without waiting for a frame.
     * The raw list, before the overlay decides which of them it draws itself.
     */
    private List<LOTRAbstractWaypoint> lastRenderedWaypoints =
            Collections.emptyList();
    private boolean lastRenderedIncludeHidden;
    private final LostTalesMapInputTracker mapInput =
            new LostTalesMapInputTracker();
    /**
     * One camera for the whole screen. Framing a stack and framing a travel
     * destination are the same movement with a different screen anchor, so a
     * new one always replaces whatever was running rather than fighting it.
     */
    private final LostTalesMapCameraFocus cameraFocus =
            new LostTalesMapCameraFocus();
    /**
     * Set while a left press has landed on empty map. The "go here" marker is
     * only placed if that press comes back up without becoming a drag, so
     * panning the map never drops one.
     */
    private boolean goHerePressPending;
    /** How far the map is drawn turned; follows the dragged angle. */
    private float mapRotationDegrees;
    private float mapRotationTargetDegrees;
    /** Accumulated horizontal drag, which is what buys the angle. */
    private float rotationInput;
    private long rotationLastNanos;
    /** How far the map is drawn leaning, 0 flat to 1, and its drag. */
    private float mapLean;
    private float mapLeanTarget;
    private float leanInput;
    private int leanDragLastY;
    private boolean rotatingMap;
    private int rotationDragLastX;
    private float rotationDragTravel;
    /** Set while a left drag is panning, which a turned map has to redirect. */
    private boolean panningMap;
    private int panLastX;
    private int panLastY;

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
        // Taken here rather than on the first frame, so a resize that runs
        // initGui again finds it already held and does nothing.
        LostTalesMapCursor.acquire();
        boolean preserveSmoothZoom = this.smoothZoomInitialized;
        LostTalesLotrMapLayout.prepareBeforeInit(this);
        super.initGui();
        if (LostTalesLotrMapLayout.finishInit(this)
                && this.buttonMenuReturn != null) {
            this.buttonList.remove(this.buttonMenuReturn);
        }
        if (!preserveSmoothZoom) {
            initializeSmoothZoom();
        } else if (ensureSmoothZoomReflection()) {
            try {
                zoomTicksField.setInt(this, 0);
                zoomPowerField.setInt(null, Math.max(LOTR_ZOOM_POWER_MIN,
                        Math.min(LOTR_ZOOM_POWER_MAX,
                                Math.round(this.smoothZoomTarget))));
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

    /** True while a popup owns the screen and the map must hold still. */
    private boolean isModalOpen() {
        return this.fastTravelPrompt != null
                || this.waypointPrompt != null
                || this.moveMarkerPrompt != null
                || this.searchPrompt != null;
    }

    @Override
    public void updateScreen() {
        float[] camera = new float[
                LostTalesMapCameraFocus.CAMERA_STATE_SIZE];
        boolean frozen = isModalOpen()
                && LostTalesMapCameraFocus.captureCamera(this, camera);
        try {
            super.updateScreen();
        } finally {
            if (frozen) {
                // LOTR's keyboard movement runs inside its own tick, so the
                // only way to hold the map is to put back what it moved.
                LostTalesMapCameraFocus.restoreCamera(this, camera);
            }
        }
        if (this.waypointPrompt != null) {
            this.waypointPrompt.updateCursor();
        }
        if (this.searchPrompt != null) {
            this.searchPrompt.updateCursor();
        }
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
            renderRoadsWithSharedFade(labels);
            return;
        }
        // Preserve LOTR's pixel-art road dots, but replace only its unstable
        // interval-based names with fixed world anchors and smooth fades.
        renderRoadsWithSharedFade(false);
        prepared.render();
    }

    /**
     * Draws LOTR's roads with the map's own zoom fade instead of LOTR's.
     *
     * <p>LOTR works the road opacity out from {@code zoomExp} inside the
     * method that draws them, against the zoom range it shipped with — so on
     * this map the roads faded out with a third of the zoom still to go. The
     * exponent it reads is the only input to that calculation on this path,
     * because the label branch that also reads it is never taken here, so it
     * is set to whatever produces the wanted opacity and put straight back.
     * Every road dot, its spacing and its clipping stay LOTR's own.</p>
     */
    private void renderRoadsWithSharedFade(boolean labels) {
        if (!this.smoothZoomInitialized || !ensureSmoothZoomReflection()) {
            super.renderRoads(labels);
            return;
        }
        float actual;
        try {
            actual = zoomExpField.getFloat(this);
            zoomExpField.setFloat(this,
                    LostTalesMapZoomFade.nativeZoomExpForAlpha(
                            LostTalesMapZoomFade.alpha(actual,
                                    SMOOTH_ZOOM_MIN, SMOOTH_ZOOM_MAX)));
        } catch (IllegalAccessException exception) {
            markSmoothZoomReflectionFailed(exception);
            this.smoothZoomInitialized = false;
            super.renderRoads(labels);
            return;
        }
        try {
            super.renderRoads(labels);
        } finally {
            try {
                zoomExpField.setFloat(this, actual);
            } catch (IllegalAccessException exception) {
                markSmoothZoomReflectionFailed(exception);
                this.smoothZoomInitialized = false;
            }
        }
    }

    @Override
    public void handleMouseInput() {
        int wheel = Mouse.getEventDWheel();
        if (isModalOpen() && wheel != 0) {
            if (this.waypointPrompt != null) {
                this.waypointPrompt.mouseWheel(wheel);
            }
            if (this.searchPrompt != null) {
                this.searchPrompt.mouseWheel(wheel);
            }
            return;
        }
        if (wheel != 0 && LostTalesLotrMapLegend.handleMouseWheel(
                this, getEventMouseX(), getEventMouseY(), wheel)) {
            return;
        }
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

    private int getEventMouseX() {
        if (this.mc == null || this.mc.displayWidth <= 0) {
            return 0;
        }
        return Mouse.getEventX() * this.width / this.mc.displayWidth;
    }

    private int getEventMouseY() {
        if (this.mc == null || this.mc.displayHeight <= 0) {
            return 0;
        }
        return this.height - Mouse.getEventY() * this.height
                / this.mc.displayHeight - 1;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (isModalOpen()) {
            return;
        }
        super.actionPerformed(button);
    }

    private void adjustSmoothZoom(float delta) {
        // Zooming by hand takes the camera back from a focus in progress.
        this.cameraFocus.cancel();
        setSmoothZoomTarget(this.smoothZoomTarget + delta);
    }

    /**
     * Puts the whole eased zoom where a focus movement says it should be.
     *
     * <p>The wheel easing interpolates towards a target of its own; while a
     * focus is running it is the focus that decides, so current, previous
     * and target are set together and the wheel easing has nothing left to
     * catch up on.</p>
     */
    private void applyFocusZoom(float zoomExp) {
        if (!this.smoothZoomInitialized) {
            return;
        }
        float clamped = clampSmoothZoom(zoomExp);
        this.smoothZoomPrevious = clamped;
        this.smoothZoomCurrent = clamped;
        setSmoothZoomTarget(clamped);
    }

    /** Retargets the eased zoom; a target already in flight is replaced. */
    private void setSmoothZoomTarget(float zoomExp) {
        if (!this.smoothZoomInitialized
                || !ensureSmoothZoomReflection()) {
            return;
        }
        float adjusted = clampSmoothZoom(zoomExp);
        if (adjusted == this.smoothZoomTarget) {
            return;
        }
        this.smoothZoomTarget = adjusted;
        try {
            zoomPowerField.setInt(null, Math.max(LOTR_ZOOM_POWER_MIN,
                    Math.min(LOTR_ZOOM_POWER_MAX,
                            Math.round(adjusted))));
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

    /**
     * How far this map screen can actually be zoomed, which is what anything
     * fading with the zoom has to measure itself against.
     *
     * <p>The Lost Tales map goes wider than LOTR's own screens either way, and
     * only when its continuous zoom is running. A screen still on LOTR's
     * integer zoom keeps LOTR's range, so a fade means the same thing on both.
     * </p>
     */
    static float minZoomExpOf(LOTRGuiMap gui) {
        return hasSmoothZoom(gui) ? SMOOTH_ZOOM_MIN : LOTR_ZOOM_POWER_MIN;
    }

    static float maxZoomExpOf(LOTRGuiMap gui) {
        return hasSmoothZoom(gui) ? SMOOTH_ZOOM_MAX : LOTR_ZOOM_POWER_MAX;
    }

    private static boolean hasSmoothZoom(LOTRGuiMap gui) {
        return gui instanceof LostTalesLotrMapGui
                && ((LostTalesLotrMapGui)gui).smoothZoomInitialized;
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
            List<LOTRAbstractWaypoint> lotrWaypoints =
                    LostTalesLotrMapMarkerIconOverlay
                            .getWaypointsForLotrRender(waypoints, pass);
            if (pass == 1) {
                // The overlay invokes LOTR's own full tooltip renderer for
                // the single delayed hover owner. Passing no waypoints here
                // prevents the native short hover card from drawing first.
                super.renderWaypoints(
                        Collections.<LOTRAbstractWaypoint>emptyList(),
                        pass, mouseX, mouseY, drawLabels, includeHidden);
            } else {
                super.renderWaypoints(
                        lotrWaypoints, pass, mouseX, mouseY,
                        drawLabels, includeHidden);
            }
            if (pass == 1) {
                LostTalesLotrMapMarkerIconOverlay
                        .renderFocusedHoverTooltip(this, mouseX, mouseY);
            }
            return;
        }

        this.lastRenderedWaypoints = waypoints == null
                ? Collections.<LOTRAbstractWaypoint>emptyList()
                : Collections.unmodifiableList(
                        new ArrayList<LOTRAbstractWaypoint>(waypoints));
        this.lastRenderedIncludeHidden = includeHidden;
        List<LOTRAbstractWaypoint> baseWaypoints =
                LostTalesLotrMapMarkerIconOverlay
                        .getWaypointsForLotrRender(waypoints, pass);
        this.clickableNativeWaypoints = baseWaypoints == null
                ? Collections.<LOTRAbstractWaypoint>emptyList()
                : Collections.unmodifiableList(
                        new ArrayList<LOTRAbstractWaypoint>(baseWaypoints));
        super.renderWaypoints(baseWaypoints, pass, mouseX, mouseY, drawLabels, includeHidden);
        // Hover ownership is decided inside this call, against the geometry
        // it has just built, so drawing and highlighting cannot disagree.
        LostTalesLotrMapMarkerIconOverlay.renderGroupedMarkers(
                this, waypoints, baseWaypoints, mouseX, mouseY,
                drawLabels, includeHidden);
        renderTransientEnemyMarkersOnce(mouseX, mouseY, drawLabels);
        renderRoleplayPlayerHeadsOnce(mouseX, mouseY);
        if (isModalOpen()) {
            // The popup covers the map; nothing behind it may claim hover.
            LostTalesLotrMapMarkerIconOverlay.suspendHoverFocus(this);
        }
        LostTalesLotrMapMarkerIconOverlay
                .suppressSelectedLotrTooltipForFocusedHover(this);
        LostTalesLotrMapMarkerIconOverlay.renderHoveredIconForeground(
                this, mouseX, mouseY, drawLabels);
        LostTalesLotrMapMarkerIconOverlay
                .renderSelectedMarkerFrame(this);
        LOTRAbstractWaypoint focusedNative =
                LostTalesLotrMapMarkerIconOverlay
                        .getFocusedNativeWaypoint(this);
        if (focusedNative != null) {
            float[] transform = LostTalesLotrMapMarkerIconOverlay
                    .getFocusedNativeIconTransform(this, focusedNative);
            if (transform != null) {
                GL11.glPushMatrix();
                try {
                    GL11.glTranslatef(
                            transform[0], transform[1], 0.0F);
                    GL11.glScalef(
                            transform[2], transform[2], 1.0F);
                    GL11.glTranslatef(
                            -transform[0], -transform[1], 0.0F);
                    super.renderWaypoints(
                            Collections.singletonList(focusedNative),
                            pass, mouseX, mouseY, false, includeHidden);
                } finally {
                    GL11.glPopMatrix();
                }
            } else {
                super.renderWaypoints(
                        Collections.singletonList(focusedNative),
                        pass, mouseX, mouseY, false, includeHidden);
            }
        }
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
        LostTalesLotrMapLayout.prepareForDraw(this);
        this.mapControlBarRendered = false;
        this.transientEnemyMarkersRendered = false;
        this.roleplayPlayerHeadsRendered = false;
        LostTalesLotrMapMarkerIconOverlay
                .clearInvalidLotrSelection(this);
        long now = System.nanoTime();
        // A pass that throws part way through cannot leave the map stuck
        // drawing as though it were square.
        LostTalesLotrMapRotation.clearSheetPasses();
        // Before anything reads the angle, so the image, the markers and the
        // hit tests all use the same one for this frame.
        advanceMapRotation(now);
        // Ahead of LOTR's own panning so a focus in progress owns the camera
        // for this frame and a drag can take it back on the next one.
        if (this.cameraFocus.advance(this, now)) {
            applyFocusZoom(this.cameraFocus.getCurrentZoomExp());
        }
        if (isModalOpen()) {
            // LOTR drags from inside its own draw by polling the mouse and
            // measuring against the previous frame's pointer. Telling it the
            // pointer has not moved is what actually holds the map still;
            // putting the camera back afterwards only hid the movement for
            // one frame and let it snap.
            LostTalesMapCameraFocus.holdPointer(this, mouseX, mouseY);
        } else {
            dragRotatedMap(mouseX, mouseY);
        }
        float[] camera = new float[
                LostTalesMapCameraFocus.CAMERA_STATE_SIZE];
        boolean captured =
                LostTalesMapCameraFocus.captureCamera(this, camera);
        boolean frozen = isModalOpen() && captured;
        try {
            super.drawScreen(mouseX, mouseY, partialTicks);
        } finally {
            if (frozen) {
                // LOTR drags the map from inside its own draw by polling the
                // mouse, so the popup's grip on the pointer is not enough.
                LostTalesMapCameraFocus.restoreCamera(this, camera);
            } else if (captured) {
                alignCameraMovementWithRotation(camera);
            }
            LostTalesLotrMapMarkerIconOverlay
                    .restoreSelectedLotrWaypointAfterDraw(this);
        }
        // Over the map and everything pinned to it, under the strip and the
        // panels, so it draws the eye towards the middle without dimming
        // anything the player has to read.
        renderVignette();
        renderControlBar(false);
        LostTalesLotrMapLegend.render(this, mouseX, mouseY);
        if (this.fastTravelPrompt != null) {
            this.fastTravelPrompt.render(
                    this, mouseX, mouseY, canPlacePromptMarker());
        }
        if (this.waypointPrompt != null) {
            this.waypointPrompt.render(
                    this.width, this.height, mouseX, mouseY,
                    getUsedCustomWaypoints(), getMaxCustomWaypoints());
        }
        if (this.moveMarkerPrompt != null) {
            this.moveMarkerPrompt.render(
                    this.width, this.height, mouseX, mouseY);
        }
        if (this.searchPrompt != null) {
            this.searchPrompt.render(
                    this.width, this.height, mouseX, mouseY);
        }
        // Last of everything: the pointer is over the map, the popups and the
        // strip alike, and it is drawn at the very coordinate every hit test
        // above was resolved against.
        LostTalesMapCursor.render(this.mc, mouseX, mouseY);
    }

    /**
     * Takes the camera back to where the player is standing.
     *
     * <p>The same movement every other map action uses, aimed at the player's
     * own position rather than at a marker, so it arrives the same way and can
     * be interrupted the same way.</p>
     */
    private void focusCurrentLocation() {
        if (this.mc == null || this.mc.thePlayer == null
                || this.mc.thePlayer.dimension
                != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return;
        }
        focusMapPoint(
                (float)LostTalesMapCoordinateHelper
                        .worldToRenderedMapImageX(this.mc.thePlayer.posX),
                (float)LostTalesMapCoordinateHelper
                        .worldToRenderedMapImageZ(this.mc.thePlayer.posZ));
    }

    /** Centres the camera on a map-image point at a comfortable zoom. */
    private void focusMapPoint(float mapImageX, float mapImageY) {
        this.cameraFocus.focus(this, mapImageX, mapImageY,
                this.smoothZoomCurrent,
                clampSmoothZoom(Math.max(this.smoothZoomCurrent,
                        LOCATION_FOCUS_ZOOM_EXP)),
                Float.NaN, Float.NaN, System.nanoTime());
    }

    private void openSearchPrompt() {
        if (this.mc == null || this.mc.fontRenderer == null) {
            return;
        }
        this.searchPrompt = LostTalesMapSearchPrompt.open(
                this.mc.fontRenderer, this.width, this.height,
                LostTalesClientMapMarkerStore.getMapMarkers(
                        ClientPartyTrackingCache.getMapMarkers()));
        this.mapLegendOpen = false;
        LostTalesLotrMapMarkerIconOverlay.suspendHoverFocus(this);
    }

    private void clearSearchPrompt() {
        this.searchPrompt = null;
        this.mapInput.cancelPress();
        this.goHerePressPending = false;
        LostTalesLotrMapMarkerIconOverlay.suspendHoverFocus(this);
    }

    /**
     * Acts on a place picked out of the find popup: the popup closes and the
     * camera goes there, exactly as if the player had clicked its stack.
     */
    private void handleSearchSelection() {
        if (this.searchPrompt == null) {
            return;
        }
        LostTalesMapMarkerData marker =
                this.searchPrompt.takeChosenMarker();
        if (marker == null) {
            return;
        }
        float[] target = LostTalesLotrMapMarkerIconOverlay
                .resolveMapImagePosition(marker, null);
        clearSearchPrompt();
        if (target != null) {
            focusMapPoint(target[0], target[1]);
        }
    }

    /**
     * Drags a turned map, in place of LOTR's own panning.
     *
     * <p>LOTR moves the camera along the map's axes, which stop being the
     * screen's axes the moment the map is turned, so a horizontal drag would
     * slide the ground away at a slant. Turning the movement back is the whole
     * correction; doing it here rather than after LOTR has drawn is what keeps
     * the ground under the pointer on the frame the pointer moves.</p>
     *
     * <p>LOTR is then told the pointer has not moved, so it does not pan a
     * second time. Everything else it moves the camera with — the keyboard,
     * above all — is left to
     * {@link #alignCameraMovementWithRotation(float[])}.</p>
     */
    private void dragRotatedMap(int mouseX, int mouseY) {
        if (this.mapRotationDegrees == 0.0F || !this.panningMap) {
            return;
        }
        float[] delta = new float[] {
                mouseX - this.panLastX, mouseY - this.panLastY
        };
        this.panLastX = mouseX;
        this.panLastY = mouseY;
        float[] camera = new float[
                LostTalesMapCameraFocus.CAMERA_STATE_SIZE];
        if (!LostTalesMapCameraFocus.captureCamera(this, camera)) {
            return;
        }
        LostTalesLotrMapRotation.rotateCameraDelta(
                delta, this.mapRotationDegrees);
        float zoomScale = (float)Math.pow(2.0D, this.smoothZoomCurrent);
        float posX = LostTalesLotrMapRotation.clampToMapImage(
                camera[0] - delta[0] / zoomScale,
                LostTalesLotrMapRotation.mapImageWidth());
        float posY = LostTalesLotrMapRotation.clampToMapImage(
                camera[1] - delta[1] / zoomScale,
                LostTalesLotrMapRotation.mapImageHeight());
        camera[0] = posX;
        camera[1] = posY;
        camera[2] = posX;
        camera[3] = posY;
        LostTalesMapCameraFocus.restoreCamera(this, camera);
        LostTalesMapCameraFocus.holdPointer(this, mouseX, mouseY);
    }

    /**
     * Re-applies the camera movement LOTR just made along the map's own axes.
     *
     * <p>What is left after {@link #dragRotatedMap(int, int)} has taken the
     * drag: the keyboard, which steers by the screen's axes too and would
     * otherwise walk the map off at a slant.</p>
     */
    private void alignCameraMovementWithRotation(float[] before) {
        if (this.mapRotationDegrees == 0.0F) {
            return;
        }
        float[] after = new float[
                LostTalesMapCameraFocus.CAMERA_STATE_SIZE];
        if (!LostTalesMapCameraFocus.captureCamera(this, after)) {
            return;
        }
        float[] delta = new float[] {
                after[0] - before[0], after[1] - before[1]
        };
        if (delta[0] == 0.0F && delta[1] == 0.0F) {
            return;
        }
        LostTalesLotrMapRotation.rotateCameraDelta(
                delta, this.mapRotationDegrees);
        float posX = LostTalesLotrMapRotation.clampToMapImage(
                before[0] + delta[0],
                LostTalesLotrMapRotation.mapImageWidth());
        float posY = LostTalesLotrMapRotation.clampToMapImage(
                before[1] + delta[1],
                LostTalesLotrMapRotation.mapImageHeight());
        after[0] = posX;
        after[1] = posY;
        after[2] = posX;
        after[3] = posY;
        LostTalesMapCameraFocus.restoreCamera(this, after);
    }

    /**
     * How far the map is currently drawn turned, in degrees.
     *
     * <p>The drawn angle, not the dragged one: everything that has to agree
     * about where the ground is — the image, the markers, hit testing — reads
     * this, so they all follow the same smoothed value.</p>
     */
    float getMapRotationDegrees() {
        return this.mapRotationDegrees;
    }

    /**
     * Turns the map by a horizontal drag.
     *
     * <p>Only the horizontal component counts, so a steep diagonal barely
     * turns the map while a flat sweep across the screen turns it fully. What
     * accumulates is the drag itself rather than the angle, because the angle
     * it buys is not linear — see
     * {@link LostTalesLotrMapRotation#degreesForInput(float)}. The threshold
     * is measured against the whole gesture rather than the last frame, so
     * once the drag has been recognised the map follows the pointer without a
     * second jump.</p>
     */
    private void dragMapRotation(int mouseX, int mouseY) {
        int delta = mouseX - this.rotationDragLastX;
        int verticalDelta = mouseY - this.leanDragLastY;
        this.rotationDragLastX = mouseX;
        this.leanDragLastY = mouseY;
        this.rotationDragTravel += Math.abs(delta)
                + Math.abs(verticalDelta);
        if (this.rotationDragTravel
                < LostTalesLotrMapRotation.DRAG_THRESHOLD_PIXELS) {
            return;
        }
        this.rotationInput = LostTalesLotrMapRotation.advanceInput(
                this.rotationInput,
                delta * LostTalesLotrMapRotation.inputPerPixel());
        this.mapRotationTargetDegrees =
                LostTalesLotrMapRotation.degreesForInput(
                        this.rotationInput);
        // Dragging down lowers the eye towards the sheet; dragging back up
        // lays it flat again. What accumulates is the drag, as with the turn,
        // because the lean it buys stiffens the same way — and gives that
        // stiffness back the same way when the drag reverses.
        this.leanInput = Math.max(0.0F,
                LostTalesLotrMapRotation.advanceInput(this.leanInput,
                        verticalDelta
                                * LostTalesLotrMapRotation
                                        .leanInputPerPixel()));
        this.mapLeanTarget =
                LostTalesLotrMapRotation.leanForInput(this.leanInput);
    }

    /** Follows the dragged angle, once per frame, off elapsed time. */
    private void advanceMapRotation(long nowNanos) {
        float elapsed = this.rotationLastNanos == 0L ? 0.0F
                : Math.min(0.25F,
                        (nowNanos - this.rotationLastNanos)
                                / 1000000000.0F);
        this.rotationLastNanos = nowNanos;
        this.mapRotationDegrees =
                LostTalesLotrMapRotation.approachDegrees(
                        this.mapRotationDegrees,
                        this.mapRotationTargetDegrees, elapsed);
        // The lean follows on the same clock, so turning and leaning at once
        // is one movement rather than two arriving at different times.
        this.mapLean = LostTalesLotrMapRotation.approachDegrees(
                this.mapLean * LEAN_SMOOTHING_SCALE,
                this.mapLeanTarget * LEAN_SMOOTHING_SCALE, elapsed)
                / LEAN_SMOOTHING_SCALE;
    }

    /** How far the map is drawn leaning, 0 flat to 1. */
    float getMapLean() {
        return this.mapLean;
    }

    /**
     * The edge shade, over the whole map screen.
     *
     * <p>Only on the Lost Tales fullscreen map: LOTR's own windowed map has a
     * frame of its own and its menu background is a different screen
     * entirely.</p>
     */
    private void renderVignette() {
        if (!LostTalesLotrMapLayout.isFullscreenLayoutActive(this)) {
            return;
        }
        // The strip only. The legend is a panel that opens and closes over
        // the map, and measuring the shade against it moved the whole oval
        // every time it was toggled.
        int reserved = LostTalesLotrMapLayout.isControlBarVisible(this)
                ? LostTalesLotrMapControlBar.HEIGHT : 0;
        LostTalesLotrMapVignette.render(this.width, this.height, reserved);
    }

    void renderControlBar(boolean force) {
        if (this.mapControlBarRendered && !force) {
            return;
        }
        this.mapControlBarRendered |=
                LostTalesLotrMapControlBar.render(this);
    }

    /**
     * One owner per click, in a fixed order: the popup, the legend, the
     * marker under the pointer, then the empty map.
     *
     * <p>Only the first three can act on the press itself. Empty map is
     * decided on release, because until the button comes back up there is no
     * telling a placement from the start of a pan.</p>
     */
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (this.waypointPrompt != null) {
            handleWaypointPromptAction(
                    this.waypointPrompt.mouseClicked(
                            this.width, this.height,
                            mouseX, mouseY, button,
                            isWithinCustomWaypointLimit()));
            return;
        }
        if (this.fastTravelPrompt != null) {
            handleFastTravelPromptAction(
                    this.fastTravelPrompt.mouseClicked(
                            this.width, this.height,
                            mouseX, mouseY, button,
                            canPlacePromptMarker()));
            return;
        }
        if (this.moveMarkerPrompt != null) {
            handleMoveMarkerPromptAction(
                    this.moveMarkerPrompt.mouseClicked(
                            this.width, this.height,
                            mouseX, mouseY, button));
            return;
        }
        if (this.searchPrompt != null) {
            this.searchPrompt.mouseClicked(
                    this.width, this.height, mouseX, mouseY, button);
            handleSearchSelection();
            return;
        }
        if (LostTalesKeyBindings.isMapLegendMouseButton(button)) {
            toggleMapLegend();
            return;
        }
        if (LostTalesLotrMapLegend.handleMouseClick(
                this, mouseX, mouseY, button)) {
            return;
        }
        if (button != 0) {
            // Both halves have to be in place: the transformer that turns
            // LOTR's map space, and the Lost Tales renderer that turns the
            // image to match. With only one, the ground and the markers on it
            // would disagree.
            if (button == ROTATE_MAP_BUTTON && this.smoothZoomInitialized
                    && LostTalesLotrMapRotation.isSupported()) {
                // The press only arms the gesture. Whether it was a right
                // click or the start of a turn is decided by what the pointer
                // does next, so LOTR still sees the click either way.
                this.rotatingMap = true;
                this.rotationDragLastX = mouseX;
                this.leanDragLastY = mouseY;
                this.rotationDragTravel = 0.0F;
            }
            super.mouseClicked(mouseX, mouseY, button);
            LostTalesLotrMapMarkerIconOverlay
                    .clearInvalidLotrSelection(this);
            return;
        }

        this.goHerePressPending = false;
        this.mapInput.press(mouseX, mouseY);
        PartyStateSnapshot state = ClientPartyStateCache.getSnapshot();
        if (handleMarkerClick(mouseX, mouseY, state)) {
            return;
        }
        // Empty map: remember the press and let LOTR start its pan. The
        // release decides which of the two it was. LOTR's own widgets sit
        // inside the viewport, so they are excluded here rather than left to
        // drop a marker behind whatever button was pressed.
        this.goHerePressPending = personalMarkerOwnerId(state) != null
                && !LostTalesLotrMapLayout.isPointerOverMapWidget(
                        this, mouseX, mouseY)
                && LostTalesLotrMapMarkerIconOverlay
                        .getMapClickWorldPosition(this, mouseX, mouseY)
                        != null;
        super.mouseClicked(mouseX, mouseY, button);
        LostTalesLotrMapMarkerIconOverlay
                .clearInvalidLotrSelection(this);
    }

    @Override
    protected void mouseClickMove(
            int mouseX, int mouseY, int button, long timeSinceClick) {
        if (button == 0 && this.mapInput.moved(mouseX, mouseY)) {
            // Taking hold of the map cancels a focus and any pending drop.
            this.cameraFocus.cancel();
            this.goHerePressPending = false;
            if (!this.panningMap) {
                this.panningMap = true;
                this.panLastX = mouseX;
                this.panLastY = mouseY;
            }
        }
        if (button == ROTATE_MAP_BUTTON && this.rotatingMap
                && !isModalOpen()) {
            dragMapRotation(mouseX, mouseY);
        }
        super.mouseClickMove(mouseX, mouseY, button, timeSinceClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        if (which == 0) {
            this.panningMap = false;
            boolean click = this.mapInput.releaseAsClick(mouseX, mouseY);
            boolean place = click && this.goHerePressPending;
            this.goHerePressPending = false;
            if (place) {
                placeGoHereMarkerAtPointer(mouseX, mouseY);
            }
        }
        if (which == ROTATE_MAP_BUTTON) {
            this.rotatingMap = false;
            this.rotationDragTravel = 0.0F;
        }
        super.mouseMovedOrUp(mouseX, mouseY, which);
    }

    /**
     * Marker interaction for a left press, in the order the icons stack.
     *
     * @return true when a marker took the click, which is also what stops a
     *         "go here" marker being dropped underneath it
     */
    private boolean handleMarkerClick(
            int mouseX, int mouseY, PartyStateSnapshot state) {
        if (this.mc == null || this.mc.thePlayer == null) {
            return false;
        }
        // A stack is opened before its members can be used: clicking one
        // frames it, and only once the markers stand apart on their own does
        // a click reach the marker underneath.
        if (focusGroupUnderPointer(mouseX, mouseY)) {
            return true;
        }
        if (LostTalesLotrMapMarkerIconOverlay
                .getHoveredLockedMappedMarker(this, mouseX, mouseY)
                != null) {
            // An undiscovered location is still a target: it takes the click
            // and keeps its anonymous hover card, but offers nothing.
            return true;
        }
        LostTalesLotrMapMarkerIconOverlay.HoveredWaypoint mapped =
                LostTalesLotrMapMarkerIconOverlay
                        .getHoveredMappedWaypoint(this, mouseX, mouseY);
        if (mapped != null) {
            // A waypoint the player owns opens its own editor, which is
            // where renaming, recolouring, sharing, deleting and travelling
            // to it all live. Everything else opens the travel popup.
            if (!openWaypointEditor(mapped.getWaypoint())) {
                openFastTravelPrompt(
                        mapped.getMarker(), mapped.getWaypoint());
            }
            return true;
        }
        String localMarkerId = getLocalGoHereMarkerId(state);
        LostTalesMapMarkerData marker =
                LostTalesLotrMapMarkerIconOverlay
                        .getHoveredStandaloneMarker(
                                this, mouseX, mouseY, localMarkerId);
        if (marker != null) {
            if (localMarkerId != null
                    && localMarkerId.equals(marker.getId())) {
                // Touching the marker no longer throws it away. Removing it is
                // one of the answers to the question this opens.
                openMoveMarkerPrompt(null);
            } else {
                openFastTravelPrompt(marker, null);
            }
            LostTalesLotrMapMarkerIconOverlay
                    .clearLotrSelectedWaypoint(this);
            return true;
        }
        LOTRAbstractWaypoint nativeWaypoint =
                LostTalesLotrMapMarkerIconOverlay
                        .getHoveredNativeWaypoint(
                                this, this.clickableNativeWaypoints,
                                mouseX, mouseY, false);
        if (nativeWaypoint != null) {
            openFastTravelPrompt(null, nativeWaypoint);
            return true;
        }
        return false;
    }

    /**
     * Frames the stack under the pointer: centres the marker it is drawn as
     * and zooms only as far as the clustering rule says its members need to
     * stand apart.
     *
     * @return true when a stack was found, so the click is not reused
     */
    private boolean focusGroupUnderPointer(int mouseX, int mouseY) {
        float[] target = LostTalesLotrMapMarkerIconOverlay
                .resolveGroupFocusTarget(
                        this, mouseX, mouseY, SMOOTH_ZOOM_MAX);
        if (target == null) {
            return false;
        }
        // Pan and zoom are one movement, so the focus drives the zoom for
        // its whole duration rather than handing it to the wheel easing and
        // letting the two finish at different moments. A stack is opened at
        // the middle of the viewport, since nothing is covering it.
        this.cameraFocus.focus(this, target[0], target[1],
                this.smoothZoomCurrent,
                clampSmoothZoom(target[2]),
                Float.NaN, Float.NaN, System.nanoTime());
        return true;
    }

    /**
     * Brings a travel destination to rest above the popup that describes it.
     *
     * <p>The same camera the stacks use, given a different anchor: the marker
     * has to stay visible while the player reads the question about it, so it
     * is framed in the space the popup leaves rather than at the centre of the
     * screen, where the popup would sit on top of it.</p>
     */
    private void focusFastTravelTarget(
            LostTalesMapMarkerData marker,
            LOTRAbstractWaypoint waypoint) {
        float[] target = LostTalesLotrMapMarkerIconOverlay
                .resolveMapImagePosition(marker, waypoint);
        if (target == null) {
            return;
        }
        this.cameraFocus.focus(this, target[0], target[1],
                this.smoothZoomCurrent,
                clampSmoothZoom(Math.max(this.smoothZoomCurrent,
                        FAST_TRAVEL_FOCUS_ZOOM_EXP)),
                this.width * 0.5F,
                LostTalesMapFastTravelPrompt.focusAnchorY(
                        this.width, this.height),
                System.nanoTime());
    }

    /**
     * Acts on a click that landed on empty map.
     *
     * <p>The first marker is dropped where the player clicked, with nothing to
     * ask about. Once one exists it is shared with the party and is what
     * everyone is walking towards, so moving it is asked for rather than
     * assumed.</p>
     */
    private void placeGoHereMarkerAtPointer(int mouseX, int mouseY) {
        PartyStateSnapshot state = ClientPartyStateCache.getSnapshot();
        UUID ownerId = personalMarkerOwnerId(state);
        if (this.mc == null || this.mc.thePlayer == null
                || ownerId == null) {
            return;
        }
        int[] worldPosition = LostTalesLotrMapMarkerIconOverlay
                .getMapClickWorldPosition(this, mouseX, mouseY);
        if (worldPosition == null) {
            return;
        }
        int[] destination = new int[] {
                this.mc.thePlayer.dimension,
                worldPosition[0], worldPosition[1]
        };
        if (ClientPartyTrackingCache.hasLocalGoHereMarker(state)) {
            openMoveMarkerPrompt(destination);
            return;
        }
        sendGoHereMarker(ownerId, destination);
        LostTalesLotrMapMarkerIconOverlay
                .clearLotrSelectedWaypoint(this);
    }

    private void sendGoHereMarker(UUID ownerId, int[] destination) {
        if (ownerId == null || destination == null
                || destination.length < 3) {
            return;
        }
        PartyClientRequestManager.setGoHereMarker(
                ownerId, null, -1L,
                destination[0], destination[1], destination[2]);
    }

    /**
     * Asks what should happen to the marker that already exists.
     *
     * @param destination the position Move would apply, or null when the
     *                    question was opened on the marker itself
     */
    private void openMoveMarkerPrompt(int[] destination) {
        this.pendingGoHereDestination = destination;
        this.moveMarkerPrompt =
                new LostTalesMapMoveMarkerPrompt(destination != null);
        this.mapLegendOpen = false;
        LostTalesLotrMapMarkerIconOverlay
                .clearLotrSelectedWaypoint(this);
        LostTalesLotrMapMarkerIconOverlay.suspendHoverFocus(this);
    }

    private void handleMoveMarkerPromptAction(
            LostTalesMapMoveMarkerPrompt.Action action) {
        if (action == null
                || action == LostTalesMapMoveMarkerPrompt.Action.NONE) {
            return;
        }
        int[] destination = this.pendingGoHereDestination;
        UUID ownerId = personalMarkerOwnerId(
                ClientPartyStateCache.getSnapshot());
        clearMoveMarkerPrompt();
        // Every answer is a request the server re-derives ownership for; the
        // popup itself owns nothing.
        if (action == LostTalesMapMoveMarkerPrompt.Action.MOVE) {
            sendGoHereMarker(ownerId, destination);
        } else if (action
                == LostTalesMapMoveMarkerPrompt.Action.REMOVE) {
            PartyClientRequestManager.removeGoHereMarker(
                    ownerId, null, -1L);
        }
    }

    private void clearMoveMarkerPrompt() {
        this.moveMarkerPrompt = null;
        this.pendingGoHereDestination = null;
        this.mapInput.cancelPress();
        this.goHerePressPending = false;
        LostTalesLotrMapMarkerIconOverlay.suspendHoverFocus(this);
    }

    /**
     * Who this player's "go here" marker belongs to: their active character,
     * or the player themselves when they have none.
     *
     * <p>The server resolves the same thing independently for every request;
     * this only decides what to ask for and where to look for the answer.</p>
     */
    private UUID personalMarkerOwnerId(PartyStateSnapshot state) {
        UUID characterId = state != null && state.isAvailable()
                ? state.getActiveCharacterId() : null;
        UUID playerId = this.mc == null || this.mc.thePlayer == null
                ? null : this.mc.thePlayer.getUniqueID();
        return PartyPersonalMarkerOwner.resolve(characterId, playerId);
    }

    private String getLocalGoHereMarkerId(PartyStateSnapshot state) {
        UUID ownerId = personalMarkerOwnerId(state);
        return ownerId == null ? null : "party_go_here:" + ownerId;
    }

    /**
     * Opens the shared confirmation for a destination the player clicked.
     *
     * <p>A destination can be reached two ways: from a waystone the player is
     * standing at, or straight from the map through LOTR's own fast travel,
     * which every marker registered as a public waypoint supports. Both end
     * in a request the server re-validates, so this only decides what to
     * offer. A destination that supports travel but cannot be used right now
     * still opens the popup, with the reason on it, rather than silently
     * doing nothing.</p>
     */
    private void openFastTravelPrompt(
            LostTalesMapMarkerData marker,
            LOTRAbstractWaypoint waypoint) {
        if (!supportsFastTravel(marker, waypoint)) {
            return;
        }
        this.fastTravelCycle = buildFastTravelCycle(marker, waypoint);
        showFastTravelPrompt(marker, waypoint);
    }

    /**
     * Puts the popup on a destination.
     *
     * <p>Everything opening the popup does apart from working out what else it
     * could be moved to, which is decided once and then stepped through.</p>
     */
    private void showFastTravelPrompt(
            LostTalesMapMarkerData marker,
            LOTRAbstractWaypoint waypoint) {
        // An undiscovered destination is named by the same rule the map label
        // and the hover card use, so the popup cannot be the one place that
        // gives it away.
        String name = marker != null
                ? LostTalesLotrWaypointText.resolveTitle(
                        marker, marker.getName())
                : waypoint.getDisplayName();
        this.fastTravelPrompt = new LostTalesMapFastTravelPrompt(
                name, fastTravelBlockedReason(marker, waypoint),
                this.fastTravelCycle != null
                        && this.fastTravelCycle.hasAlternatives());
        this.promptCustomMarker = marker;
        this.promptNativeWaypoint = waypoint;
        this.mapLegendOpen = false;
        LostTalesLotrMapMarkerIconOverlay
                .clearLotrSelectedWaypoint(this);
        LostTalesLotrMapMarkerIconOverlay.suspendHoverFocus(this);
        LostTalesLotrMapMarkerIconOverlay.setSelectedMarkerFrame(
                this, marker, waypoint);
        focusFastTravelTarget(marker, waypoint);
    }

    /**
     * Orders every other destination around the one that was just clicked.
     *
     * <p>Built from the waypoints of the last drawn frame and the markers the
     * map is showing, so what an arrow key can reach is exactly what a click
     * could have reached.</p>
     */
    private LostTalesMapFastTravelCycle buildFastTravelCycle(
            LostTalesMapMarkerData marker,
            LOTRAbstractWaypoint waypoint) {
        double anchorX;
        double anchorZ;
        if (marker != null) {
            anchorX = marker.getX();
            anchorZ = marker.getZ();
        } else {
            anchorX = waypoint.getXCoord();
            anchorZ = waypoint.getZCoord();
        }
        LostTalesLotrMapMarkerIconOverlay.FastTravelCandidate anchor =
                LostTalesLotrMapMarkerIconOverlay.FastTravelCandidate.of(
                        marker, waypoint);
        return LostTalesMapFastTravelCycle.around(
                LostTalesLotrMapMarkerIconOverlay
                        .collectFastTravelCandidates(
                                this.lastRenderedWaypoints,
                                this.lastRenderedIncludeHidden,
                                getLocalGoHereMarkerId(
                                        ClientPartyStateCache
                                                .getSnapshot())),
                anchorX, anchorZ,
                anchor == null ? null : anchor.getKey());
    }

    /**
     * Moves the open popup to the next destination in a direction.
     *
     * <p>The popup is replaced in place rather than closed and reopened: the
     * camera runs on to the new destination, the frame moves with it, and the
     * order the player is stepping along is left exactly as it was.</p>
     */
    private void stepFastTravelPrompt(int direction) {
        if (this.fastTravelPrompt == null
                || this.fastTravelCycle == null) {
            return;
        }
        LostTalesLotrMapMarkerIconOverlay.FastTravelCandidate next =
                this.fastTravelCycle.step(direction);
        if (next == null) {
            // Everywhere else has been filtered out or deleted since the
            // popup opened. Leaving it where it is beats closing it.
            return;
        }
        showFastTravelPrompt(next.getMarker(), next.getWaypoint());
    }

    /** Whether this destination is meant to be a fast-travel target at all. */
    private static boolean supportsFastTravel(
            LostTalesMapMarkerData marker,
            LOTRAbstractWaypoint waypoint) {
        return marker != null ? marker.hasFastTravel() : waypoint != null;
    }

    /**
     * Localization key describing why travel is refused, or null when it is
     * offered. Purely for presentation — the server checks all of this again.
     */
    private String fastTravelBlockedReason(
            LostTalesMapMarkerData marker,
            LOTRAbstractWaypoint waypoint) {
        if (this.mc == null || this.mc.thePlayer == null) {
            return "gui.losttales.map.fast_travel.blocked.unavailable";
        }
        if (marker != null && marker.isDiscoverable()
                && !LostTalesClientMapMarkerVisibility
                        .isDiscovered(marker)) {
            return "gui.losttales.map.fast_travel.blocked.undiscovered";
        }
        if (hasWaystoneTravelContext()) {
            return null;
        }
        if (waypoint == null) {
            // Nothing but a waystone can reach a marker LOTR does not know as
            // an available waypoint of its own.
            return "gui.losttales.map.fast_travel.blocked.needs_waystone";
        }
        if (this.mc.thePlayer.dimension
                != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return "gui.losttales.map.fast_travel.blocked.unavailable";
        }
        try {
            if (!waypoint.hasPlayerUnlocked(this.mc.thePlayer)) {
                return "gui.losttales.map.fast_travel.blocked.locked";
            }
            LOTRPlayerData playerData = LOTRLevelData.getData(
                    this.mc.thePlayer);
            if (playerData == null) {
                return "gui.losttales.map.fast_travel.blocked.unavailable";
            }
            return playerData.getTimeSinceFT()
                    >= playerData.getWaypointFTTime(
                            waypoint, this.mc.thePlayer)
                    ? null
                    : "gui.losttales.map.fast_travel.blocked.cooldown";
        } catch (Throwable ignored) {
            return "gui.losttales.map.fast_travel.blocked.unavailable";
        }
    }

    private boolean hasWaystoneTravelContext() {
        return this.mc != null && this.mc.thePlayer != null
                && LostTalesClientWaystoneTravelContext.get(
                        this.mc.thePlayer.dimension) != null;
    }

    private boolean canPlacePromptMarker() {
        return this.fastTravelPrompt != null
                && (this.promptCustomMarker != null
                        || this.promptNativeWaypoint != null)
                && personalMarkerOwnerId(
                        ClientPartyStateCache.getSnapshot()) != null;
    }

    private void handleFastTravelPromptAction(
            LostTalesMapFastTravelPrompt.Action action) {
        if (action == null
                || action == LostTalesMapFastTravelPrompt.Action.NONE) {
            return;
        }
        if (action == LostTalesMapFastTravelPrompt.Action.PREVIOUS) {
            stepFastTravelPrompt(-1);
            return;
        }
        if (action == LostTalesMapFastTravelPrompt.Action.NEXT) {
            stepFastTravelPrompt(1);
            return;
        }
        LostTalesMapMarkerData customMarker = this.promptCustomMarker;
        LOTRAbstractWaypoint nativeWaypoint = this.promptNativeWaypoint;
        if (action == LostTalesMapFastTravelPrompt.Action.NO) {
            clearFastTravelPrompt();
            return;
        }
        if (action == LostTalesMapFastTravelPrompt.Action.PLACE_MARKER) {
            placePromptMarker(customMarker, nativeWaypoint);
            clearFastTravelPrompt();
            return;
        }
        clearFastTravelPrompt();
        if (fastTravelBlockedReason(customMarker, nativeWaypoint) != null) {
            return;
        }
        // A waystone the player is standing at is the stronger origin: it
        // carries a source the server can re-derive from the world.
        if (customMarker != null && hasWaystoneTravelContext()
                && sendWaystoneTravel(customMarker.getId())) {
            return;
        }
        if (nativeWaypoint != null
                && LostTalesLotrMapMarkerIconOverlay
                        .selectLotrWaypoint(this, nativeWaypoint)) {
            super.keyTyped('\0',
                    LOTRKeyHandler.keyBindingFastTravel.getKeyCode());
        }
    }

    private void placePromptMarker(
            LostTalesMapMarkerData customMarker,
            LOTRAbstractWaypoint nativeWaypoint) {
        UUID ownerId = personalMarkerOwnerId(
                ClientPartyStateCache.getSnapshot());
        if (ownerId == null) {
            return;
        }
        int dimensionId;
        double x;
        double z;
        if (customMarker != null) {
            dimensionId = customMarker.getDimensionId();
            x = customMarker.getX();
            z = customMarker.getZ();
        } else if (nativeWaypoint != null) {
            dimensionId = LOTRDimension.MIDDLE_EARTH.dimensionID;
            x = nativeWaypoint.getXCoord();
            z = nativeWaypoint.getZCoord();
        } else {
            return;
        }
        PartyClientRequestManager.setGoHereMarker(
                ownerId, null, -1L, dimensionId, x, z);
    }

    /**
     * Opens the personal-waypoint editor.
     *
     * <p>LOTR creates the waypoint where the player is standing, not where
     * the map is looking, so there is nothing to pick on the map first.</p>
     */
    private void openWaypointPrompt() {
        if (this.mc == null || this.mc.thePlayer == null
                || this.mc.fontRenderer == null
                || this.mc.thePlayer.dimension
                != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return;
        }
        this.editedWaypoint = null;
        this.waypointPrompt = LostTalesMapWaypointPrompt.forCreation(
                this.mc.fontRenderer, this.width, this.height);
        this.mapLegendOpen = false;
        LostTalesLotrMapMarkerIconOverlay.suspendHoverFocus(this);
    }

    /**
     * Opens the editor for one of the player's own waypoints.
     *
     * @return false when this is not a waypoint the player may edit, so the
     *         caller can fall back to the ordinary travel popup
     */
    private boolean openWaypointEditor(LOTRAbstractWaypoint waypoint) {
        if (!(waypoint instanceof LOTRCustomWaypoint)
                || this.mc == null || this.mc.fontRenderer == null) {
            return false;
        }
        LOTRCustomWaypoint custom = (LOTRCustomWaypoint)waypoint;
        String name;
        try {
            // A shared waypoint belongs to whoever made it; the player
            // seeing it here can travel to it but not change it.
            if (custom.isShared()) {
                return false;
            }
            // The code name is what the player typed. LOTR's display name
            // wraps it in "(Custom)", which is not a name they can edit and
            // is not the key their colour and description are stored under —
            // editing under it silently saved both somewhere nothing reads.
            name = custom.getCodeName();
        } catch (Throwable ignored) {
            return false;
        }
        this.editedWaypoint = custom;
        this.waypointPrompt = LostTalesMapWaypointPrompt.forEditing(
                this.mc.fontRenderer, this.width, this.height,
                name,
                LostTalesCustomWaypointStyle.getNote(name),
                LostTalesCustomWaypointStyle.getColor(name, false),
                collectShareTargets(custom));
        this.mapLegendOpen = false;
        LostTalesLotrMapMarkerIconOverlay
                .clearLotrSelectedWaypoint(this);
        LostTalesLotrMapMarkerIconOverlay.suspendHoverFocus(this);
        return true;
    }

    /** The player's fellowships, and which of them this waypoint reaches. */
    private List<LostTalesMapWaypointPrompt.ShareTarget>
    collectShareTargets(LOTRCustomWaypoint waypoint) {
        ArrayList<LostTalesMapWaypointPrompt.ShareTarget> targets =
                new ArrayList<LostTalesMapWaypointPrompt.ShareTarget>();
        LOTRPlayerData data = getLotrPlayerData();
        if (data == null) {
            return targets;
        }
        try {
            List<LOTRFellowshipClient> fellowships =
                    data.getClientFellowships();
            if (fellowships == null) {
                return targets;
            }
            for (LOTRFellowshipClient fellowship : fellowships) {
                if (fellowship == null
                        || fellowship.getName() == null) {
                    continue;
                }
                targets.add(new LostTalesMapWaypointPrompt.ShareTarget(
                        fellowship.getName(),
                        waypoint.hasSharedFellowship(
                                fellowship.getFellowshipID())));
            }
        } catch (Throwable ignored) {
            // A changed LOTR fellowship API costs the share list, nothing
            // more; the rest of the editor keeps working.
            targets.clear();
        }
        return targets;
    }

    private void handleWaypointPromptAction(
            LostTalesMapWaypointPrompt.Action action) {
        LostTalesMapWaypointPrompt prompt = this.waypointPrompt;
        if (prompt == null) {
            return;
        }
        // A toggled fellowship is its own request and leaves the editor
        // open, so several can be changed in one visit.
        LostTalesMapWaypointPrompt.ShareTarget toggled =
                prompt.takePendingShareToggle();
        if (toggled != null) {
            sendShareRequest(toggled);
            return;
        }
        if (action == null
                || action == LostTalesMapWaypointPrompt.Action.NONE) {
            return;
        }
        LOTRCustomWaypoint waypoint = this.editedWaypoint;
        clearWaypointPrompt();
        if (action == LostTalesMapWaypointPrompt.Action.CANCEL) {
            return;
        }
        if (action == LostTalesMapWaypointPrompt.Action.TRAVEL) {
            openFastTravelPrompt(null, waypoint);
            return;
        }
        if (action == LostTalesMapWaypointPrompt.Action.DELETE) {
            if (waypoint != null) {
                sendWaypointRequest(new LOTRPacketDeleteCWP(waypoint));
                LostTalesCustomWaypointStyle.forget(
                        prompt.getOriginalName());
            }
            return;
        }
        if (!prompt.canConfirm(isWithinCustomWaypointLimit())) {
            return;
        }
        // Every change goes through LOTR's own request, so its name rules,
        // waypoint limit, ownership and placement stay exactly as the server
        // already enforces them. The colour is the one thing LOTR has no
        // field for; it is remembered here and never sent anywhere.
        String name = prompt.getName();
        LostTalesCustomWaypointStyle.setColor(
                name, prompt.getColorName());
        LostTalesCustomWaypointStyle.setNote(name, prompt.getNote());
        if (action == LostTalesMapWaypointPrompt.Action.CREATE) {
            sendWaypointRequest(new LOTRPacketCreateCWP(name));
            return;
        }
        if (prompt.isRenamed() && waypoint != null) {
            sendWaypointRequest(new LOTRPacketRenameCWP(waypoint, name));
            // The colour is keyed by name, so it moves with the rename
            // rather than being left behind under the old one.
            LostTalesCustomWaypointStyle.forget(prompt.getOriginalName());
        }
    }

    private void sendShareRequest(
            LostTalesMapWaypointPrompt.ShareTarget target) {
        LOTRCustomWaypoint waypoint = this.editedWaypoint;
        if (waypoint == null || target == null
                || target.getName().length() == 0) {
            return;
        }
        sendWaypointRequest(new LOTRPacketShareCWP(
                waypoint, target.getName(), !target.isShared()));
        // Rows always show LOTR's own sharing state, so a request the server
        // refuses simply leaves the row as it was rather than showing a
        // change that never happened.
        if (this.waypointPrompt != null) {
            this.waypointPrompt.setShareTargets(
                    collectShareTargets(waypoint));
        }
    }

    private void sendWaypointRequest(IMessage request) {
        if (request == null) {
            return;
        }
        try {
            LOTRPacketHandler.networkWrapper.sendToServer(request);
        } catch (Throwable ignored) {
            // A changed LOTR request must not break the map screen.
        }
    }

    private void clearWaypointPrompt() {
        this.waypointPrompt = null;
        this.editedWaypoint = null;
        this.mapInput.cancelPress();
        this.goHerePressPending = false;
        LostTalesLotrMapMarkerIconOverlay.suspendHoverFocus(this);
    }

    private boolean isWithinCustomWaypointLimit() {
        return getUsedCustomWaypoints() < getMaxCustomWaypoints();
    }

    private int getUsedCustomWaypoints() {
        LOTRPlayerData data = getLotrPlayerData();
        try {
            return data == null || data.getCustomWaypoints() == null
                    ? 0 : data.getCustomWaypoints().size();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private int getMaxCustomWaypoints() {
        LOTRPlayerData data = getLotrPlayerData();
        try {
            return data == null ? 0 : data.getMaxCustomWaypoints();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private LOTRPlayerData getLotrPlayerData() {
        if (this.mc == null || this.mc.thePlayer == null) {
            return null;
        }
        try {
            return LOTRLevelData.getData(this.mc.thePlayer);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void clearFastTravelPrompt() {
        this.fastTravelPrompt = null;
        this.fastTravelCycle = null;
        this.promptCustomMarker = null;
        this.promptNativeWaypoint = null;
        LostTalesLotrMapMarkerIconOverlay
                .clearSelectedMarkerFrame(this);
        // Nothing the popup covered may come back as hover or as a pending
        // placement once it closes.
        this.mapInput.cancelPress();
        this.goHerePressPending = false;
        LostTalesLotrMapMarkerIconOverlay.suspendHoverFocus(this);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (this.waypointPrompt != null) {
            handleWaypointPromptAction(
                    this.waypointPrompt.keyTyped(typedChar, keyCode,
                            isWithinCustomWaypointLimit()));
            return;
        }
        if (this.fastTravelPrompt != null) {
            handleFastTravelPromptAction(
                    this.fastTravelPrompt.keyTyped(keyCode));
            return;
        }
        if (this.moveMarkerPrompt != null) {
            handleMoveMarkerPromptAction(
                    this.moveMarkerPrompt.keyTyped(keyCode));
            return;
        }
        if (this.searchPrompt != null) {
            if (!this.searchPrompt.keyTyped(typedChar, keyCode)) {
                clearSearchPrompt();
                return;
            }
            handleSearchSelection();
            return;
        }
        if (keyCode == CREATE_WAYPOINT_KEY) {
            openWaypointPrompt();
            return;
        }
        if (keyCode == CURRENT_LOCATION_KEY) {
            focusCurrentLocation();
            return;
        }
        if (keyCode == FIND_LOCATION_KEY) {
            openSearchPrompt();
            return;
        }
        if (LostTalesKeyBindings.isMapLegendKey(keyCode)) {
            toggleMapLegend();
            return;
        }
        if (this.mapLegendOpen && keyCode == Keyboard.KEY_ESCAPE) {
            this.mapLegendOpen = false;
            return;
        }
        if (keyCode == LOTRKeyHandler.keyBindingFastTravel.getKeyCode()) {
            // Travel is confirmed by clicking a destination and choosing Yes.
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void toggleMapLegend() {
        if (!this.mapLegendOpen
                && !LostTalesLotrMapLayout.isControlBarVisible(this)) {
            return;
        }
        this.mapLegendOpen = !this.mapLegendOpen;
        if (!this.mapLegendOpen) {
            this.mapLegendScrollIndex = 0;
        }
    }

    boolean isMapLegendOpen() {
        return this.mapLegendOpen;
    }

    int getMapLegendScrollIndex() {
        return this.mapLegendScrollIndex;
    }

    void setMapLegendScrollIndex(int index) {
        this.mapLegendScrollIndex = Math.max(0, index);
    }

    /**
     * A marker the player has just filtered out must not stay selected in
     * LOTR's own state, where it would keep its native tooltip and travel
     * button alive behind an icon that is no longer drawn.
     */
    void onMapLegendFiltersChanged() {
        LostTalesLotrMapMarkerIconOverlay.clearInvalidLotrSelection(this);
    }

    @Override
    public void onGuiClosed() {
        LostTalesMapCursor.release();
        clearFastTravelPrompt();
        clearWaypointPrompt();
        clearMoveMarkerPrompt();
        clearSearchPrompt();
        this.mapInput.clear();
        this.cameraFocus.cancel();
        this.goHerePressPending = false;
        this.rotatingMap = false;
        this.panningMap = false;
        this.mapRotationDegrees = 0.0F;
        this.mapRotationTargetDegrees = 0.0F;
        this.rotationInput = 0.0F;
        this.rotationLastNanos = 0L;
        this.mapLean = 0.0F;
        this.mapLeanTarget = 0.0F;
        this.leanInput = 0.0F;
        this.clickableNativeWaypoints = Collections.emptyList();
        this.lastRenderedWaypoints = Collections.emptyList();
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
