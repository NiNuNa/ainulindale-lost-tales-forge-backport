package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.client.party.ClientPartyStateCache;
import com.ninuna.losttales.client.party.PartyClientRequestManager;
import com.ninuna.losttales.party.model.PartyPersonalMarkerOwner;
import com.ninuna.losttales.party.sync.PartyStateSnapshot;
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

    static final float SMOOTH_ZOOM_MIN = -3.0F;
    static final float SMOOTH_ZOOM_MAX = 4.0F;
    /** One notch of the wheel, in zoom exponent. */
    static final float SMOOTH_ZOOM_WHEEL_INCREMENT = 0.2F;
    private static final float SMOOTH_ZOOM_EASING = 0.28F;
    private static final float SMOOTH_ZOOM_SNAP_EPSILON = 0.001F;

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
    /** The waypoint the editor is open on, or null while creating one. */
    private LOTRCustomWaypoint editedWaypoint;
    private LostTalesMapMarkerData promptCustomMarker;
    private LOTRAbstractWaypoint promptNativeWaypoint;
    private final LostTalesMapInputTracker mapInput =
            new LostTalesMapInputTracker();
    private final LostTalesMapGroupFocus groupFocus =
            new LostTalesMapGroupFocus();
    /**
     * Set while a left press has landed on empty map. The "go here" marker is
     * only placed if that press comes back up without becoming a drag, so
     * panning the map never drops one.
     */
    private boolean goHerePressPending;

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

    /** True while a popup owns the screen and the map must hold still. */
    private boolean isModalOpen() {
        return this.fastTravelPrompt != null
                || this.waypointPrompt != null;
    }

    @Override
    public void updateScreen() {
        float[] camera = new float[
                LostTalesMapGroupFocus.CAMERA_STATE_SIZE];
        boolean frozen = isModalOpen()
                && LostTalesMapGroupFocus.captureCamera(this, camera);
        try {
            super.updateScreen();
        } finally {
            if (frozen) {
                // LOTR's keyboard movement runs inside its own tick, so the
                // only way to hold the map is to put back what it moved.
                LostTalesMapGroupFocus.restoreCamera(this, camera);
            }
        }
        if (this.waypointPrompt != null) {
            this.waypointPrompt.updateCursor();
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
        if (isModalOpen() && wheel != 0) {
            if (this.waypointPrompt != null) {
                this.waypointPrompt.mouseWheel(wheel);
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
        this.groupFocus.cancel();
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
        // Ahead of LOTR's own panning so a focus in progress owns the camera
        // for this frame and a drag can take it back on the next one.
        if (this.groupFocus.advance(this, System.nanoTime())) {
            applyFocusZoom(this.groupFocus.getCurrentZoomExp());
        }
        if (isModalOpen()) {
            // LOTR drags from inside its own draw by polling the mouse and
            // measuring against the previous frame's pointer. Telling it the
            // pointer has not moved is what actually holds the map still;
            // putting the camera back afterwards only hid the movement for
            // one frame and let it snap.
            LostTalesMapGroupFocus.holdPointer(this, mouseX, mouseY);
        }
        float[] camera = new float[
                LostTalesMapGroupFocus.CAMERA_STATE_SIZE];
        boolean frozen = isModalOpen()
                && LostTalesMapGroupFocus.captureCamera(this, camera);
        try {
            super.drawScreen(mouseX, mouseY, partialTicks);
        } finally {
            if (frozen) {
                // LOTR drags the map from inside its own draw by polling the
                // mouse, so the popup's grip on the pointer is not enough.
                LostTalesMapGroupFocus.restoreCamera(this, camera);
            }
            LostTalesLotrMapMarkerIconOverlay
                    .restoreSelectedLotrWaypointAfterDraw(this);
        }
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
        if (LostTalesKeyBindings.isMapLegendMouseButton(button)) {
            toggleMapLegend();
            return;
        }
        if (LostTalesLotrMapLegend.handleMouseClick(
                this, mouseX, mouseY, button)) {
            return;
        }
        if (button != 0) {
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
                        .getMapClickWorldPosition(this) != null;
        super.mouseClicked(mouseX, mouseY, button);
        LostTalesLotrMapMarkerIconOverlay
                .clearInvalidLotrSelection(this);
    }

    @Override
    protected void mouseClickMove(
            int mouseX, int mouseY, int button, long timeSinceClick) {
        if (button == 0 && this.mapInput.moved(mouseX, mouseY)) {
            // Taking hold of the map cancels a focus and any pending drop.
            this.groupFocus.cancel();
            this.goHerePressPending = false;
        }
        super.mouseClickMove(mouseX, mouseY, button, timeSinceClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        if (which == 0) {
            boolean click = this.mapInput.releaseAsClick(mouseX, mouseY);
            boolean place = click && this.goHerePressPending;
            this.goHerePressPending = false;
            if (place) {
                placeGoHereMarkerAtPointer();
            }
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
                PartyClientRequestManager.removeGoHereMarker(
                        personalMarkerOwnerId(state), null, -1L);
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
        // letting the two finish at different moments.
        this.groupFocus.focus(this, target[0], target[1],
                this.smoothZoomCurrent,
                clampSmoothZoom(target[2]), System.nanoTime());
        return true;
    }

    /** Drops the party marker on a waypoint rather than on empty map. */
    private void placeGoHereMarkerAt(LOTRAbstractWaypoint waypoint) {
        UUID ownerId = personalMarkerOwnerId(
                ClientPartyStateCache.getSnapshot());
        if (waypoint == null || ownerId == null) {
            return;
        }
        PartyClientRequestManager.setGoHereMarker(
                ownerId, null, -1L,
                LOTRDimension.MIDDLE_EARTH.dimensionID,
                waypoint.getXCoord(), waypoint.getZCoord());
    }

    private void placeGoHereMarkerAtPointer() {
        UUID ownerId = personalMarkerOwnerId(
                ClientPartyStateCache.getSnapshot());
        if (this.mc == null || this.mc.thePlayer == null
                || ownerId == null) {
            return;
        }
        int[] worldPosition = LostTalesLotrMapMarkerIconOverlay
                .getMapClickWorldPosition(this);
        if (worldPosition == null) {
            return;
        }
        PartyClientRequestManager.setGoHereMarker(
                ownerId, null, -1L,
                this.mc.thePlayer.dimension,
                worldPosition[0], worldPosition[1]);
        LostTalesLotrMapMarkerIconOverlay
                .clearLotrSelectedWaypoint(this);
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
        String name = marker != null
                ? marker.getName() : waypoint.getDisplayName();
        this.fastTravelPrompt = new LostTalesMapFastTravelPrompt(
                name, fastTravelBlockedReason(marker, waypoint));
        this.promptCustomMarker = marker;
        this.promptNativeWaypoint = waypoint;
        this.mapLegendOpen = false;
        LostTalesLotrMapMarkerIconOverlay
                .clearLotrSelectedWaypoint(this);
        LostTalesLotrMapMarkerIconOverlay.suspendHoverFocus(this);
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
            name = custom.getDisplayName();
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
        if (action == LostTalesMapWaypointPrompt.Action.PLACE_MARKER) {
            placeGoHereMarkerAt(waypoint);
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
        this.promptCustomMarker = null;
        this.promptNativeWaypoint = null;
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
        if (keyCode == CREATE_WAYPOINT_KEY) {
            openWaypointPrompt();
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
        clearFastTravelPrompt();
        clearWaypointPrompt();
        this.mapInput.clear();
        this.groupFocus.cancel();
        this.goHerePressPending = false;
        this.clickableNativeWaypoints = Collections.emptyList();
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
