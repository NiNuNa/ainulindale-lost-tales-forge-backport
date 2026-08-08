package com.ninuna.losttales.client.mapmarker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How far the map has to zoom for one marker stack to come apart.
 *
 * <p>The zoom is not approximated. Icons are drawn at a fixed screen size, so
 * zooming only pushes their centres apart; that is enough to ask the real
 * clustering rule what a candidate zoom would decide, and the search stops at
 * the first level where every member is its own stack. A small tolerance is
 * added on top so the arrival zoom is not sitting exactly on the threshold it
 * just cleared.</p>
 *
 * <p>Getting there is not this class's business: the movement itself belongs to
 * {@link LostTalesMapCameraFocus}, which every map camera action shares.</p>
 */
final class LostTalesMapGroupFocus {
    /** Zoom exponent granularity of the bounded search. */
    static final float ZOOM_SEARCH_STEP = 0.05F;
    /**
     * Extra zoom past the level that separates the stack. Arriving exactly on
     * the join threshold would let rounding regroup the markers again.
     */
    static final float ZOOM_TOLERANCE = 0.15F;

    private LostTalesMapGroupFocus() {
    }

    /**
     * Smallest zoom exponent above {@code currentZoomExp} at which every
     * member of the stack is its own group, plus {@link #ZOOM_TOLERANCE}.
     *
     * @param members  the stack's markers, as the last grouping decision saw
     *                 them on screen
     * @param centerX  screen point the zoom expands away from — the same
     *                 point the camera will frame
     */
    static float separatingZoomExponent(
            List<LostTalesMapMarkerGrouping.Entry> members,
            float currentZoomExp, float maxZoomExp,
            float centerX, float centerY) {
        if (members == null || members.size() <= 1
                || !(maxZoomExp > currentZoomExp)) {
            return currentZoomExp;
        }
        int steps = (int)Math.ceil(
                (maxZoomExp - currentZoomExp) / ZOOM_SEARCH_STEP);
        for (int step = 1; step <= steps; step++) {
            float zoomExp = Math.min(maxZoomExp,
                    currentZoomExp + step * ZOOM_SEARCH_STEP);
            if (isFullySeparated(members, centerX, centerY,
                    (float)Math.pow(2.0D, zoomExp - currentZoomExp))) {
                return Math.min(maxZoomExp, zoomExp + ZOOM_TOLERANCE);
            }
        }
        return maxZoomExp;
    }

    /** Asks the real clustering rule what {@code factor} more zoom decides. */
    private static boolean isFullySeparated(
            List<LostTalesMapMarkerGrouping.Entry> members,
            float centerX, float centerY, float factor) {
        List<LostTalesMapMarkerGrouping.Entry> scaled =
                new ArrayList<LostTalesMapMarkerGrouping.Entry>(
                        members.size());
        for (LostTalesMapMarkerGrouping.Entry member : members) {
            scaled.add(LostTalesMapMarkerGrouping.scaledAbout(
                    member, centerX, centerY, factor));
        }
        // No previous links: the stack is judged by the threshold that would
        // bring these markers together, not the looser one that keeps them.
        return LostTalesMapMarkerGrouping.group(scaled,
                Collections.<String, String>emptyMap())
                .getGroups().size() == scaled.size();
    }
}
