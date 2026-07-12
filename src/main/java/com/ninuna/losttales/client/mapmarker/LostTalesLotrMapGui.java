package com.ninuna.losttales.client.mapmarker;

import java.util.List;
import lotr.client.LOTRKeyHandler;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.world.map.LOTRAbstractWaypoint;

/**
 * LOTR map screen wrapper used only to intercept the waypoint rendering pass.
 *
 * <p>The base LOTRGuiMap still handles map movement, LOTR waypoint selection,
 * tooltips, and fast travel. This subclass filters only the first visual
 * waypoint pass so selected LOTR waypoints can be redrawn with Lost Tales icons
 * instead of the small LOTR dot. Lost Tales markers without fast travel are
 * drawn and selected by this wrapper as visual map markers, but they never send
 * LOTR fast-travel packets.</p>
 */
public class LostTalesLotrMapGui extends LOTRGuiMap {
    private LostTalesMapMarkerData selectedStandaloneMarker;
    private boolean transientEnemyMarkersRendered;

    @Override
    public void renderWaypoints(List<LOTRAbstractWaypoint> waypoints, int pass, int mouseX, int mouseY, boolean drawLabels, boolean includeHidden) {
        if (pass != 0) {
            super.renderWaypoints(waypoints, pass, mouseX, mouseY, drawLabels, includeHidden);
            if (pass == 1) {
                LostTalesLotrMapMarkerIconOverlay.renderStandaloneMarkerHoverTooltip(this, this.selectedStandaloneMarker, mouseX, mouseY);
                LostTalesLotrMapMarkerIconOverlay.renderTransientEnemyMarkerHoverTooltip(this, mouseX, mouseY);
            }
            return;
        }

        if (waypoints == null || waypoints.isEmpty()) {
            super.renderWaypoints(waypoints, pass, mouseX, mouseY, drawLabels, includeHidden);
            LostTalesLotrMapMarkerIconOverlay.renderStandaloneMarkers(this, mouseX, mouseY, drawLabels);
            renderTransientEnemyMarkersOnce(mouseX, mouseY, drawLabels);
            return;
        }

        List<LOTRAbstractWaypoint> baseWaypoints = LostTalesLotrMapMarkerIconOverlay.getWaypointsForLotrBaseRender(waypoints);
        super.renderWaypoints(baseWaypoints, pass, mouseX, mouseY, drawLabels, includeHidden);
        LostTalesLotrMapMarkerIconOverlay.renderReplacementWaypoints(this, waypoints, mouseX, mouseY, drawLabels, includeHidden);
        LostTalesLotrMapMarkerIconOverlay.renderStandaloneMarkers(this, mouseX, mouseY, drawLabels);
        renderTransientEnemyMarkersOnce(mouseX, mouseY, drawLabels);
    }

    private void renderTransientEnemyMarkersOnce(int mouseX, int mouseY, boolean drawLabels) {
        if (!this.transientEnemyMarkersRendered) {
            this.transientEnemyMarkersRendered = true;
            LostTalesLotrMapMarkerIconOverlay.renderTransientEnemyMarkers(this, mouseX, mouseY, drawLabels);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.transientEnemyMarkersRendered = false;
        if (this.selectedStandaloneMarker != null) {
            LostTalesLotrMapMarkerIconOverlay.clearLotrSelectedWaypoint(this);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (this.selectedStandaloneMarker != null && !LostTalesLotrMapMarkerIconOverlay.renderStandaloneMarkerSelection(this, this.selectedStandaloneMarker, mouseX, mouseY)) {
            this.selectedStandaloneMarker = null;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && this.mc != null && this.mc.thePlayer != null) {
            LostTalesMapMarkerData marker = LostTalesLotrMapMarkerIconOverlay.getHoveredStandaloneMarker(this, mouseX, mouseY);
            if (marker != null) {
                this.selectedStandaloneMarker = marker;
                LostTalesLotrMapMarkerIconOverlay.clearLotrSelectedWaypoint(this);
                return;
            }
            this.selectedStandaloneMarker = null;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (this.selectedStandaloneMarker != null && keyCode == LOTRKeyHandler.keyBindingFastTravel.getKeyCode()) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}
