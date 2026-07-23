package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.party.ClientPartyStateCache;
import com.ninuna.losttales.client.party.PartyClientRequestManager;
import com.ninuna.losttales.party.sync.PartyStateSnapshot;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesWaystoneTravelRequestPacket;
import java.util.List;
import lotr.client.LOTRKeyHandler;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.world.map.LOTRAbstractWaypoint;

/**
 * LOTR map screen wrapper used only to intercept the waypoint rendering pass.
 *
 * <p>The base map handles movement and discovered LOTR waypoints. This wrapper
 * owns Lost Tales icons plus undiscovered hover/selection so private names,
 * lore, and fast-travel actions never reach the native GUI path.</p>
 */
public class LostTalesLotrMapGui extends LOTRGuiMap {
    private LostTalesMapMarkerData selectedCustomMarker;
    private boolean transientEnemyMarkersRendered;
    private boolean roleplayPlayerHeadsRendered;

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
                                this, mouseX, mouseY);
                LostTalesLotrMapMarkerIconOverlay
                        .renderLockedMappedMarkerHoverTooltip(
                                this, this.selectedCustomMarker,
                                mouseX, mouseY);
                LostTalesLotrMapMarkerIconOverlay.renderStandaloneMarkerHoverTooltip(this, this.selectedCustomMarker, mouseX, mouseY);
                LostTalesLotrMapMarkerIconOverlay.renderTransientEnemyMarkerHoverTooltip(this, mouseX, mouseY);
            }
            return;
        }

        if (waypoints == null || waypoints.isEmpty()) {
            super.renderWaypoints(waypoints, pass, mouseX, mouseY, drawLabels, includeHidden);
            LostTalesLotrMapMarkerIconOverlay.renderStandaloneMarkers(this, mouseX, mouseY, drawLabels);
            renderTransientEnemyMarkersOnce(mouseX, mouseY, drawLabels);
            renderRoleplayPlayerHeadsOnce(mouseX, mouseY);
            return;
        }

        List<LOTRAbstractWaypoint> baseWaypoints =
                LostTalesLotrMapMarkerIconOverlay
                        .getWaypointsForLotrRender(waypoints, pass);
        super.renderWaypoints(baseWaypoints, pass, mouseX, mouseY, drawLabels, includeHidden);
        LostTalesLotrMapMarkerIconOverlay.renderReplacementWaypoints(this, waypoints, mouseX, mouseY, drawLabels, includeHidden);
        LostTalesLotrMapMarkerIconOverlay.renderStandaloneMarkers(this, mouseX, mouseY, drawLabels);
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
                .clearUndiscoveredLotrSelection(this);
        if (this.selectedCustomMarker != null) {
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
                if (mappedWaypoint != null
                        && LostTalesLotrMapMarkerIconOverlay
                        .selectLotrWaypoint(this, mappedWaypoint)) {
                    this.selectedCustomMarker = null;
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
            LostTalesMapMarkerData marker =
                    LostTalesLotrMapMarkerIconOverlay
                            .getMarkerForWaypoint(selected);
            if (marker != null && marker.hasFastTravel()
                    && sendWaystoneTravel(marker.getId())) {
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
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
