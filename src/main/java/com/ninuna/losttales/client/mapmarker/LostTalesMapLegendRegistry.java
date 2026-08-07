package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.world.map.LOTRAbstractWaypoint;
import lotr.common.world.map.LOTRCustomWaypoint;
import lotr.common.world.map.LOTRWaypoint;

/** Ordered registry and deterministic classifier for map-only filters. */
@SideOnly(Side.CLIENT)
public final class LostTalesMapLegendRegistry {
    public static final String LOCATIONS = "locations";
    public static final String PERSONAL_WAYPOINTS = "personal_waypoints";
    public static final String SHARED_WAYPOINTS = "shared_waypoints";
    public static final String PLAYER_WAYSTONES = "player_waystones";
    public static final String QUESTS = "quests";
    public static final String PARTY = "party";

    private static final Map<String, LostTalesMapLegendCategory> CATEGORIES =
            new LinkedHashMap<String, LostTalesMapLegendCategory>();

    static {
        register(new LostTalesMapLegendCategory(
                LOCATIONS, "gui.losttales.map.legend.locations",
                LostTalesCompassMarkerIcon.TOWN, "white"));
        register(new LostTalesMapLegendCategory(
                PERSONAL_WAYPOINTS,
                "gui.losttales.map.legend.personal_waypoints",
                LostTalesCompassMarkerIcon.UNDISCOVERED, "white"));
        register(new LostTalesMapLegendCategory(
                SHARED_WAYPOINTS,
                "gui.losttales.map.legend.shared_waypoints",
                LostTalesCompassMarkerIcon.UNDISCOVERED, "blue"));
        register(new LostTalesMapLegendCategory(
                PLAYER_WAYSTONES,
                "gui.losttales.map.legend.player_waystones",
                LostTalesCompassMarkerIcon.FORT, "yellow"));
        register(new LostTalesMapLegendCategory(
                QUESTS, "gui.losttales.map.legend.quests",
                LostTalesCompassMarkerIcon.QUEST, "yellow"));
        register(new LostTalesMapLegendCategory(
                PARTY, "gui.losttales.map.legend.party",
                LostTalesCompassMarkerIcon.QUEST, "blue"));
    }

    private LostTalesMapLegendRegistry() {
    }

    public static synchronized void register(
            LostTalesMapLegendCategory category) {
        if (category == null) {
            return;
        }
        String id = LostTalesMapLegendPreferences.normalize(category.getId());
        if (id.length() == 0 || CATEGORIES.containsKey(id)) {
            return;
        }
        CATEGORIES.put(id, category);
    }

    public static synchronized List<LostTalesMapLegendCategory>
            getCategories() {
        return Collections.unmodifiableList(
                new ArrayList<LostTalesMapLegendCategory>(
                        CATEGORIES.values()));
    }

    public static boolean isCategoryEnabled(String categoryId) {
        return LostTalesMapLegendPreferences.isEnabled(categoryId);
    }

    static boolean toggleCategory(String categoryId) {
        return LostTalesMapLegendPreferences.toggle(categoryId);
    }

    static boolean isMarkerVisible(LostTalesMapMarkerData marker) {
        String category = categoryFor(marker);
        return category == null || isCategoryEnabled(category);
    }

    static boolean isWaypointVisible(LOTRAbstractWaypoint waypoint) {
        String category = categoryFor(waypoint);
        return category == null || isCategoryEnabled(category);
    }

    static String categoryFor(LostTalesMapMarkerData marker) {
        if (marker == null) {
            return null;
        }
        String id = marker.getId() == null ? "" : marker.getId();
        if (id.startsWith("party_go_here:")
                || "Go Here".equalsIgnoreCase(marker.getCategoryName())) {
            return PARTY;
        }
        // A custom waypoint is drawn as a Lost Tales marker but filtered as
        // the waypoint it stands for, so one toggle covers both views of it.
        if (id.startsWith(LostTalesLotrMapMarkerIconOverlay
                .PERSONAL_WAYPOINT_ID_PREFIX)) {
            return PERSONAL_WAYPOINTS;
        }
        if (id.startsWith(LostTalesLotrMapMarkerIconOverlay
                .SHARED_WAYPOINT_ID_PREFIX)) {
            return SHARED_WAYPOINTS;
        }
        LostTalesMapMarkerSource source = marker.getSource();
        if (source == LostTalesMapMarkerSource.PLAYER_CREATED) {
            return PLAYER_WAYSTONES;
        }
        if (source == LostTalesMapMarkerSource.QUEST_DYNAMIC) {
            return QUESTS;
        }
        if (source == LostTalesMapMarkerSource.CUSTOM_PRESET
                || source == LostTalesMapMarkerSource.LOTR_ADAPTER) {
            return LOCATIONS;
        }
        return null;
    }

    static String categoryFor(LOTRAbstractWaypoint waypoint) {
        if (waypoint instanceof LOTRCustomWaypoint) {
            try {
                return ((LOTRCustomWaypoint)waypoint).isShared()
                        ? SHARED_WAYPOINTS : PERSONAL_WAYPOINTS;
            } catch (Throwable ignored) {
                return null;
            }
        }
        return waypoint instanceof LOTRWaypoint ? LOCATIONS : null;
    }

    /** Transformer hook for LOTR's separate active-mini-quest map pass. */
    public static boolean shouldRenderLotrMiniQuests(LOTRGuiMap gui) {
        return !(gui instanceof LostTalesLotrMapGui)
                || isCategoryEnabled(QUESTS);
    }
}
