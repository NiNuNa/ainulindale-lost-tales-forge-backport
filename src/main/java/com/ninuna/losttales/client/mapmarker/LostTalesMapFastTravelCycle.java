package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.mapmarker.LostTalesLotrMapMarkerIconOverlay
        .FastTravelCandidate;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Stepping the travel popup from one destination to the next.
 *
 * <p>The order is worked out once, when the popup opens, and does not change
 * while it is open: the player is moving along a list, and a list that
 * reshuffled itself under them — because the camera moved, or because a
 * distance tied differently this frame — would make the arrow keys mean
 * nothing. Nearest to where the popup opened comes first, and equal distances
 * are settled by the destination's own key so the answer is the same on every
 * client and every frame.</p>
 *
 * <p>Eligibility is re-checked as each step is taken rather than when the list
 * was made, because a marker can be filtered out, discovered, deleted or
 * shared away while the popup is on screen. A destination that has gone is
 * stepped over; if they have all gone, the popup stays where it is.</p>
 */
@SideOnly(Side.CLIENT)
final class LostTalesMapFastTravelCycle {
    private final List<FastTravelCandidate> candidates;
    private int index;

    private LostTalesMapFastTravelCycle(
            List<FastTravelCandidate> candidates, int index) {
        this.candidates = candidates;
        this.index = index;
    }

    /**
     * Orders {@code candidates} around the destination the popup opened on.
     *
     * @param anchorKey the opened destination's key, so the cycle knows where
     *                  in its own order the player is standing
     */
    static LostTalesMapFastTravelCycle around(
            List<FastTravelCandidate> candidates,
            double anchorX, double anchorZ, String anchorKey) {
        ArrayList<FastTravelCandidate> ordered =
                new ArrayList<FastTravelCandidate>();
        if (candidates != null) {
            for (FastTravelCandidate candidate : candidates) {
                if (candidate != null) {
                    ordered.add(candidate);
                }
            }
        }
        Collections.sort(ordered, new ByDistance(anchorX, anchorZ));
        return new LostTalesMapFastTravelCycle(
                Collections.unmodifiableList(ordered),
                indexOfKey(ordered, anchorKey));
    }

    private static int indexOfKey(
            List<FastTravelCandidate> ordered, String key) {
        if (key == null) {
            return -1;
        }
        for (int position = 0; position < ordered.size(); position++) {
            if (key.equals(ordered.get(position).getKey())) {
                return position;
            }
        }
        return -1;
    }

    /** Whether stepping could reach anywhere the popup is not already on. */
    boolean hasAlternatives() {
        return this.candidates.size() > 1;
    }

    int size() {
        return this.candidates.size();
    }

    /**
     * The next destination in the given direction, or null when there is
     * nowhere else to go.
     *
     * <p>Wraps at both ends, and never hands back the destination the popup is
     * already showing: with one candidate, or with only one still eligible,
     * pressing the key does nothing rather than reopening the same popup.</p>
     *
     * @param direction {@code 1} for next, {@code -1} for previous
     */
    FastTravelCandidate step(int direction) {
        int count = this.candidates.size();
        if (count == 0 || direction == 0) {
            return null;
        }
        int forward = direction > 0 ? 1 : -1;
        // The current position may be unknown — the popup was opened on
        // something the list no longer holds — in which case the first step
        // simply enters the order at its start.
        int from = this.index < 0
                ? (forward > 0 ? -1 : 0) : this.index;
        for (int taken = 1; taken <= count; taken++) {
            int position = Math.floorMod(from + forward * taken, count);
            if (position == this.index) {
                break;
            }
            FastTravelCandidate candidate = this.candidates.get(position);
            if (candidate.isStillEligible()) {
                this.index = position;
                return candidate;
            }
        }
        return null;
    }

    /** Nearest first, and the key breaks a tie so the order cannot drift. */
    private static final class ByDistance
            implements Comparator<FastTravelCandidate> {
        private final double anchorX;
        private final double anchorZ;

        private ByDistance(double anchorX, double anchorZ) {
            this.anchorX = anchorX;
            this.anchorZ = anchorZ;
        }

        @Override
        public int compare(
                FastTravelCandidate first, FastTravelCandidate second) {
            int byDistance = Double.compare(
                    distanceSq(first), distanceSq(second));
            return byDistance != 0
                    ? byDistance
                    : first.getKey().compareTo(second.getKey());
        }

        private double distanceSq(FastTravelCandidate candidate) {
            double deltaX = candidate.getWorldX() - this.anchorX;
            double deltaZ = candidate.getWorldZ() - this.anchorZ;
            return deltaX * deltaX + deltaZ * deltaZ;
        }
    }
}
