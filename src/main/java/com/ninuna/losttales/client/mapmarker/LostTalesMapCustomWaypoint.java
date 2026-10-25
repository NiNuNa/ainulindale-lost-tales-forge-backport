package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * One of LOTR's custom waypoints as Find Location lists it (MAP-13): the
 * player's own, or one a fellowship shares with them, read off LOTR's
 * client by {@code compat/lotr/LotrClientCustomWaypoints}. LOTR owns the
 * waypoint; this is its name, its kind and where it stands.
 */
@SideOnly(Side.CLIENT)
public final class LostTalesMapCustomWaypoint {
    /** What the map writes before a waypoint's name, as its tooltip does. */
    static final String PERSONAL_KEY = "map.losttales.marker.personal_waypoint";
    static final String SHARED_KEY = "map.losttales.marker.shared_waypoint";

    private final int id;
    private final String name;
    private final boolean shared;
    private final boolean hidden;
    private final int dimension;
    private final int x;
    private final int y;
    private final int z;

    /**
     * {@code name} is the name the player gave it (LOTR's code name),
     * {@code hidden} whether the player hid it from the map, which only
     * a shared waypoint can be.
     */
    public LostTalesMapCustomWaypoint(int id, String name, boolean shared,
                                      boolean hidden, int dimension,
                                      int x, int y, int z) {
        this.id = id;
        this.name = name == null ? "" : name.trim();
        this.shared = shared;
        this.hidden = shared && hidden;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    String getName() {
        return this.name;
    }

    boolean isShared() {
        return this.shared;
    }

    /**
     * Whether Find Location lists it: a waypoint with a name, and a
     * shared one the player hid only while LOTR's map shows the hidden
     * ones, as the map draws it.
     */
    boolean isListed(boolean hiddenShown) {
        return this.name.length() > 0 && (!this.hidden || hiddenShown);
    }

    /** The lang line of the words before its name: Personal: or Shared:. */
    String kindKey() {
        return this.shared ? SHARED_KEY : PERSONAL_KEY;
    }

    /**
     * The marker the map draws for it: the same id, icon, colour and
     * note, so the frame Find Location puts round it lands on the drawn
     * one.
     */
    LostTalesMapMarkerData marker() {
        return new LostTalesMapMarkerData(
                (this.shared
                        ? LostTalesLotrMapMarkerIconOverlay.SHARED_WAYPOINT_ID_PREFIX
                        : LostTalesLotrMapMarkerIconOverlay.PERSONAL_WAYPOINT_ID_PREFIX)
                        + this.id,
                this.name,
                LostTalesCompassMarkerIcon.PERSONAL.name(),
                LostTalesCustomWaypointStyle.getColor(this.name, this.shared),
                "",
                LostTalesCustomWaypointStyle.getNote(this.name),
                true,
                this.dimension,
                this.x, this.y, this.z,
                0.0D, 0.0D, false, false);
    }
}
