package com.ninuna.losttales.client.mapmarker;

import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;

/**
 * What a map marker is allowed to say about itself.
 *
 * <p>One place decides it, because a location the player has not found yet is
 * drawn in several passes — a label on the map, a hover card, a travel popup —
 * and every one of them has to withhold the same thing. Splitting that decision
 * across the renderers is how a name ends up hidden in one place and printed in
 * another.</p>
 *
 * <p>An undiscovered location keeps its position, since that is what the player
 * can see from a distance, and gives up everything else: its name becomes a
 * question mark on the map, its card says only that it has not been found, and
 * its description is not read at all.</p>
 *
 * <p>What a discovered location is <em>called</em> is not decided here. The
 * bundled marker definitions carry the names, and every screen reads them, so
 * the map and the compass cannot disagree about a place.</p>
 */
public final class LostTalesLotrWaypointText {
    /** The label an undiscovered location carries on the map itself. */
    public static final String UNDISCOVERED_MAP_LABEL = "?";
    private static final String UNDISCOVERED_TITLE_KEY =
            "map.losttales.marker.undiscovered";
    private static final String UNDISCOVERED_HINT_KEY =
            "map.losttales.marker.undiscovered.hint";

    private LostTalesLotrWaypointText() {}

    /**
     * Whether this marker is drawn but not yet earned.
     *
     * <p>The same rule the icon pass uses to swap the artwork for a question
     * mark, so what is drawn and what is said cannot disagree.</p>
     */
    public static boolean isUndiscovered(LostTalesMapMarkerData marker) {
        return LostTalesClientMapMarkerVisibility
                .isUndiscoveredRegionVisible(marker);
    }

    /** The name to draw beside the icon on the map. */
    public static String resolveMapLabel(
            LostTalesMapMarkerData marker, String discoveredName) {
        return isUndiscovered(marker)
                ? UNDISCOVERED_MAP_LABEL : trim(discoveredName);
    }

    /** The heading of a hover card or a travel popup. */
    public static String resolveTitle(
            LostTalesMapMarkerData marker, String discoveredName) {
        return isUndiscovered(marker)
                ? translate(UNDISCOVERED_TITLE_KEY)
                : trim(discoveredName);
    }

    /**
     * The body of a hover card: the location's lore once it has been found,
     * and until then a line saying only that it has not been.
     */
    public static String resolveTooltipBody(
            LostTalesMapMarkerData marker, EntityPlayer player) {
        return isUndiscovered(marker)
                ? translate(UNDISCOVERED_HINT_KEY)
                : resolveDescription(marker, player);
    }

    public static String resolveDescription(
            LostTalesMapMarkerData marker, EntityPlayer player) {
        if (marker == null) {
            return "";
        }
        return resolveDescription(
                marker.getDescription(), marker.getLotrWaypointId(), player);
    }

    public static String resolveDescription(
            String configuredDescription, String lotrWaypointId,
            EntityPlayer player) {
        String configured = trim(configuredDescription);
        if (configured.length() > 0) {
            return configured;
        }
        String waypointId = trim(lotrWaypointId);
        if (waypointId.length() == 0) {
            return configured;
        }
        try {
            LOTRWaypoint waypoint = LOTRWaypoint.waypointForName(
                    waypointId);
            String nativeLore = waypoint == null
                    ? "" : trim(waypoint.getLoreText(player));
            return nativeLore.length() == 0 ? configured : nativeLore;
        } catch (RuntimeException ignored) {
            return configured;
        }
    }

    private static String translate(String key) {
        String translated = StatCollector.translateToLocal(key);
        return translated == null || translated.equals(key)
                ? "" : translated;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
