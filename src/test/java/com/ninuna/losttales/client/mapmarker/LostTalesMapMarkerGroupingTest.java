package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static com.ninuna.losttales.client.mapmarker
        .LostTalesMapMarkerGrouping.GroupingCategory.LOCATION;
import static com.ninuna.losttales.client.mapmarker
        .LostTalesMapMarkerGrouping.GroupingCategory.PARTY;
import static com.ninuna.losttales.client.mapmarker
        .LostTalesMapMarkerGrouping.GroupingCategory.QUEST;
import static com.ninuna.losttales.client.mapmarker
        .LostTalesMapMarkerGrouping.GroupingCategory.UNKNOWN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LostTalesMapMarkerGroupingTest {
    /** Marker ink is 13 pixels across plus its one-pixel shadow. */
    private static final float ICON_HALF_EXTENT = 6.5F;

    @Test
    public void overlapRatioUsesTheSmallerVisibleArea() {
        assertEquals(1.0F, LostTalesMapMarkerGrouping.overlapRatio(
                0, 0, 10, 10, 0, 0, 10, 10), 0.0001F);
        // A small icon swallowed by a wide cluster is fully covered, even
        // though it fills only half of the cluster.
        assertEquals(1.0F, LostTalesMapMarkerGrouping.overlapRatio(
                0, 0, 20, 10, 10, 0, 20, 10), 0.0001F);
        // Half of the smaller rectangle, not half of the union.
        assertEquals(0.5F, LostTalesMapMarkerGrouping.overlapRatio(
                0, 0, 20, 10, 15, 0, 25, 10), 0.0001F);
        assertEquals(0.0F, LostTalesMapMarkerGrouping.overlapRatio(
                0, 0, 10, 10, 10, 0, 20, 10), 0.0001F);
        assertEquals(0.0F, LostTalesMapMarkerGrouping.overlapRatio(
                0, 0, 10, 10, 40, 40, 50, 50), 0.0001F);
    }

    @Test
    public void markersJoinWhenOneCoversAQuarterOfTheOther() {
        // 14-pixel icons ten pixels apart cover 29% of each other.
        assertEquals(1, group(fresh(),
                icon("losttales:a", "A", 10, 0.0F, 0.0F),
                icon("losttales:b", "B", 5, 10.0F, 0.0F))
                .getGroups().size());
        // Eleven pixels apart is 21%, under the join threshold.
        assertEquals(2, group(fresh(),
                icon("losttales:a", "A", 10, 0.0F, 0.0F),
                icon("losttales:b", "B", 5, 11.0F, 0.0F))
                .getGroups().size());
    }

    @Test
    public void hysteresisKeepsMarkersTogetherBelowTheJoinThreshold() {
        Map<String, String> grouped = group(fresh(),
                icon("losttales:a", "A", 10, 0.0F, 0.0F),
                icon("losttales:b", "B", 5, 10.0F, 0.0F))
                .getMembership();

        // 24% is under the join ratio but still over the leave ratio.
        assertEquals(1, group(grouped,
                icon("losttales:a", "A", 10, 0.0F, 0.0F),
                icon("losttales:b", "B", 5, 10.7F, 0.0F))
                .getGroups().size());
        // 21% releases the member.
        assertEquals(2, group(grouped,
                icon("losttales:a", "A", 10, 0.0F, 0.0F),
                icon("losttales:b", "B", 5, 11.0F, 0.0F))
                .getGroups().size());
    }

    @Test
    public void aReleasedMemberNeverRejoinsTheGroupItJustLeft() {
        Map<String, String> grouped = group(fresh(),
                icon("losttales:a", "A", 10, 0.0F, 0.0F),
                icon("losttales:b", "B", 5, 10.0F, 0.0F))
                .getMembership();
        LostTalesMapMarkerGrouping.Result released = group(grouped,
                icon("losttales:a", "A", 10, 0.0F, 0.0F),
                icon("losttales:b", "B", 5, 11.0F, 0.0F));

        assertEquals(2, released.getGroups().size());
        assertEquals(0, released.getMembership().size());
    }

    @Test
    public void aMarkerMovesStraightFromOneGroupToAnother() {
        Map<String, String> previous = new HashMap<String, String>();
        previous.put("losttales:a", "losttales:a");
        previous.put("losttales:b", "losttales:a");

        // B is drawn on top of A but really sits on top of C, so zooming in
        // must hand it to C instead of releasing it as an independent marker.
        LostTalesMapMarkerGrouping.Result result = group(previous,
                icon("losttales:a", "A", 30, 0.0F, 0.0F),
                icon("losttales:b", "B", 20, 100.0F, 0.0F),
                icon("losttales:c", "C", 10, 100.0F, 0.0F));

        assertEquals(2, result.getGroups().size());
        assertEquals("losttales:b",
                result.getMembership().get("losttales:b"));
        assertEquals("losttales:b",
                result.getMembership().get("losttales:c"));
        assertNull(result.getMembership().get("losttales:a"));
    }

    @Test
    public void groupingFollowsMarkerPositionsNotTheDrawnFan() {
        // The fan is cosmetic. Carrying a companion must not extend a stack's
        // reach, or markers stay grouped long after they have visibly left.
        LostTalesMapMarkerGrouping.Result withCompanion = group(fresh(),
                icon("losttales:a", "A", 30, 0.0F, 0.0F),
                icon("losttales:b", "B", 20, 2.0F, 0.0F),
                icon("losttales:d", "D", 10, -12.0F, 0.0F));

        assertEquals(2, withCompanion.getGroups().size());
        assertEquals(2, withCompanion.getGroups().get(0).size());

        // A marker sitting roughly where a fan companion is drawn still
        // overlaps the marker leading the stack, so it does join.
        assertEquals(1, group(fresh(),
                icon("losttales:a", "A", 30, 0.0F, 0.0F),
                icon("losttales:b", "B", 20, 4.0F, -2.0F))
                .getGroups().size());
    }

    @Test
    public void neighboursInALineDoNotCollapseOntoOneEnd() {
        // A touches B and B touches C, but A and C are nowhere near each
        // other. A marker joins a stack by reaching that stack, so C must not
        // be dragged across to A through B as the stacks form.
        LostTalesMapMarkerGrouping.Result result = group(fresh(),
                icon("losttales:a", "A", 30, 0.0F, 0.0F),
                icon("losttales:b", "B", 20, 10.0F, 0.0F),
                icon("losttales:c", "C", 10, 20.0F, 0.0F));

        assertEquals(describe(result), 2, result.getGroups().size());
        assertEquals(2, result.getGroups().get(0).size());
        assertEquals(1, result.getGroups().get(1).size());
    }

    @Test
    public void aMarkerLeavesWhenItLosesTheMarkerThatAdmittedIt() {
        // C joined by landing on A, so A is what holds it. Once it is off A
        // it leaves, even though it is still sitting on B. Letting B keep it
        // held markers in a stack long after they had visibly parted, and
        // then dropped the whole stack at once when that last incidental
        // overlap finally gave way.
        LostTalesMapMarkerGrouping.Result result = group(
                stackedAs("losttales:a", "losttales:b", "losttales:c"),
                icon("losttales:a", "A", 30, 0.0F, 0.0F),
                icon("losttales:b", "B", 20, 10.0F, 0.0F),
                icon("losttales:c", "C", 10, 20.0F, 0.0F));

        assertEquals(describe(result), 2, result.getGroups().size());
        assertEquals("losttales:a",
                result.getMembership().get("losttales:b"));
        assertNull(result.getMembership().get("losttales:c"));
    }

    @Test
    public void aMarkerHeldByAMemberLeavesOnlyWhenItLosesThatMember() {
        // The same three markers, but C came to rest on B rather than on A.
        // Now B is what holds it, so it stays while it is still on B — and
        // when B itself lets go of A, C goes with B rather than being
        // stranded on its own.
        LostTalesMapMarkerGrouping.Result held = group(
                chainedFrom("losttales:a", "losttales:b", "losttales:c"),
                icon("losttales:a", "A", 30, 0.0F, 0.0F),
                icon("losttales:b", "B", 20, 10.0F, 0.0F),
                icon("losttales:c", "C", 10, 20.0F, 0.0F));
        assertEquals(describe(held), 1, held.getGroups().size());
        assertEquals(3, held.getGroups().get(0).size());

        LostTalesMapMarkerGrouping.Result parted = group(
                chainedFrom("losttales:a", "losttales:b", "losttales:c"),
                icon("losttales:a", "A", 30, 0.0F, 0.0F),
                icon("losttales:b", "B", 20, 12.0F, 0.0F),
                icon("losttales:c", "C", 10, 22.0F, 0.0F));

        assertEquals(describe(parted), 2, parted.getGroups().size());
        assertEquals("B took C with it", "losttales:b",
                parted.getMembership().get("losttales:c"));
        assertNull(parted.getMembership().get("losttales:a"));
    }

    @Test
    public void aMarkerBetweenTwoGroupsJoinsTheOneItSitsOn() {
        // A and B are far enough apart to lead separate groups, but M lies
        // between them - barely touching A, almost on top of B. It must go to
        // B even though A is the more relevant marker.
        LostTalesMapMarkerGrouping.Result result = group(fresh(),
                icon("losttales:a", "A", 30, 0.0F, 0.0F),
                icon("losttales:b", "B", 20, 12.0F, 0.0F),
                icon("losttales:m", "M", 1, 10.0F, 0.0F));

        assertEquals(2, result.getGroups().size());
        assertEquals("losttales:b",
                result.getMembership().get("losttales:m"));
    }

    @Test
    public void everyMemberOfAGroupIsReleasedTogether() {
        // Two markers sit the same distance from their leader, so zooming in
        // must let go of both on the same frame rather than one at a time.
        Map<String, String> grouped = group(fresh(),
                icon("losttales:a", "A", 30, 0.0F, 0.0F),
                icon("losttales:b", "B", 20, 10.0F, 0.0F),
                icon("losttales:c", "C", 10, -10.0F, 0.0F))
                .getMembership();
        assertEquals(3, grouped.size());

        LostTalesMapMarkerGrouping.Result released = group(grouped,
                icon("losttales:a", "A", 30, 0.0F, 0.0F),
                icon("losttales:b", "B", 20, 11.0F, 0.0F),
                icon("losttales:c", "C", 10, -11.0F, 0.0F));

        assertEquals(3, released.getGroups().size());
        assertEquals(0, released.getMembership().size());
    }

    @Test
    public void aMarkerBeyondTheChainStaysOnItsOwn() {
        // A and B chain; C overlaps neither, so it is not swept in.
        LostTalesMapMarkerGrouping.Result result = group(fresh(),
                icon("losttales:a", "A", 10, 0.0F, 0.0F),
                icon("losttales:b", "B", 5, 5.0F, 0.0F),
                icon("losttales:c", "C", 0, 20.0F, 0.0F));

        assertEquals(2, result.getGroups().size());
        assertEquals(2, result.getGroups().get(0).size());
        assertEquals(1, result.getGroups().get(1).size());
    }

    @Test
    public void repeatedFramesWithUnchangedInputProduceTheSameResult() {
        List<LostTalesMapMarkerGrouping.Entry> entries = Arrays.asList(
                icon("losttales:a", "A", 30, 0.0F, 0.0F),
                icon("losttales:b", "B", 20, 3.0F, 1.0F),
                icon("losttales:c", "C", 10, 40.0F, 0.0F));

        Map<String, String> membership = Collections.emptyMap();
        String first = null;
        for (int frame = 0; frame < 5; frame++) {
            LostTalesMapMarkerGrouping.Result result =
                    LostTalesMapMarkerGrouping.group(
                            entries, new HashMap<String, String>(membership));
            membership = result.getMembership();
            String description = describe(result);
            if (first == null) {
                first = description;
            }
            assertEquals(first, description);
        }
    }

    @Test
    public void groupingIgnoresTheOrderMarkersAreSuppliedIn() {
        LostTalesMapMarkerGrouping.Entry a =
                icon("losttales:a", "A", 30, 0.0F, 0.0F);
        LostTalesMapMarkerGrouping.Entry b =
                icon("losttales:b", "B", 20, 3.0F, 0.0F);
        LostTalesMapMarkerGrouping.Entry c =
                icon("losttales:c", "C", 10, 40.0F, 0.0F);

        LostTalesMapMarkerGrouping.Result forwards =
                LostTalesMapMarkerGrouping.group(
                        Arrays.asList(a, b, c), fresh());
        LostTalesMapMarkerGrouping.Result backwards =
                LostTalesMapMarkerGrouping.group(
                        Arrays.asList(c, b, a), fresh());

        assertEquals(forwards.getMembership(), backwards.getMembership());
        assertEquals(forwards.getGroups().size(),
                backwards.getGroups().size());
    }

    @Test
    public void condensedCountOnlyCoversMembersTheFanCannotDraw() {
        LostTalesMapMarkerGrouping.Result stack = group(fresh(),
                icon("losttales:a", "A", 50, 0.0F, 0.0F),
                icon("losttales:b", "B", 40, 0.0F, 0.0F),
                icon("losttales:c", "C", 30, 0.0F, 0.0F),
                icon("losttales:d", "D", 20, 0.0F, 0.0F),
                icon("losttales:e", "E", 10, 0.0F, 0.0F));
        LostTalesMapMarkerGrouping.Result fanOnly = group(fresh(),
                icon("losttales:a", "A", 50, 0.0F, 0.0F),
                icon("losttales:b", "B", 40, 0.0F, 0.0F),
                icon("losttales:c", "C", 30, 0.0F, 0.0F));

        assertEquals(5, stack.getGroups().get(0).size());
        assertEquals(2, stack.getGroups().get(0)
                .getCondensedMemberCount());
        assertEquals(0, fanOnly.getGroups().get(0)
                .getCondensedMemberCount());
    }

    @Test
    public void zoomingBackInUndoesExactlyWhatZoomingOutDid() {
        // One zoom step halves the distance between markers and the next
        // restores it, so a step out followed by a step in must land back on
        // the arrangement it started from.
        LostTalesMapMarkerGrouping.Result apart = group(fresh(),
                icon("losttales:a", "A", 10, 0.0F, 0.0F),
                icon("losttales:b", "B", 5, 11.0F, 0.0F));
        LostTalesMapMarkerGrouping.Result together = group(
                apart.getMembership(),
                icon("losttales:a", "A", 10, 0.0F, 0.0F),
                icon("losttales:b", "B", 5, 10.0F, 0.0F));
        LostTalesMapMarkerGrouping.Result apartAgain = group(
                together.getMembership(),
                icon("losttales:a", "A", 10, 0.0F, 0.0F),
                icon("losttales:b", "B", 5, 11.0F, 0.0F));

        assertEquals(2, apart.getGroups().size());
        assertEquals(1, together.getGroups().size());
        assertEquals(describe(apart), describe(apartAgain));
    }

    @Test
    public void everyGroupSettlesInTheSameFrame() {
        // Two independent pairs cross the threshold together. Neither may lag
        // a frame behind the other.
        Map<String, String> grouped = group(fresh(),
                icon("losttales:a", "A", 40, 0.0F, 0.0F),
                icon("losttales:b", "B", 30, 10.0F, 0.0F),
                icon("losttales:c", "C", 20, 100.0F, 0.0F),
                icon("losttales:d", "D", 10, 110.0F, 0.0F))
                .getMembership();
        assertEquals(4, grouped.size());

        LostTalesMapMarkerGrouping.Result separated = group(grouped,
                icon("losttales:a", "A", 40, 0.0F, 0.0F),
                icon("losttales:b", "B", 30, 11.0F, 0.0F),
                icon("losttales:c", "C", 20, 100.0F, 0.0F),
                icon("losttales:d", "D", 10, 111.0F, 0.0F));

        assertEquals(4, separated.getGroups().size());
        assertEquals(0, separated.getMembership().size());
    }

    @Test
    public void zoomingInPartitionsALargeStackIntoChildStacks() {
        Map<String, String> previous = new HashMap<String, String>();
        previous.put("losttales:a", "losttales:a");
        previous.put("losttales:b", "losttales:a");
        previous.put("losttales:c", "losttales:a");
        previous.put("losttales:d", "losttales:a");

        LostTalesMapMarkerGrouping.Result result = group(previous,
                icon("losttales:a", "A", 40, 0.0F, 0.0F),
                icon("losttales:b", "B", 30, 7.0F, 0.0F),
                icon("losttales:c", "C", 20, 40.0F, 0.0F),
                icon("losttales:d", "D", 10, 47.0F, 0.0F));

        assertEquals(2, result.getGroups().size());
        assertEquals(2, result.getGroups().get(0).size());
        assertEquals(2, result.getGroups().get(1).size());
        assertEquals("losttales:a",
                result.getMembership().get("losttales:b"));
        assertEquals("losttales:c",
                result.getMembership().get("losttales:d"));
    }

    @Test
    public void overlapUsesHigherRelevanceRepresentative() {
        LostTalesMapMarkerGrouping.Result result = group(fresh(),
                icon("losttales:low", "Low", 1, 0.0F, 0.0F),
                icon("losttales:high", "High", 10, 3.0F, 0.0F));

        assertEquals(1, result.getGroups().size());
        assertEquals(1, result.getGroups().get(0)
                .getRepresentativeIndex());
    }

    @Test
    public void equalRelevanceUsesNameThenStableId() {
        LostTalesMapMarkerGrouping.Result result = group(fresh(),
                icon("losttales:z", "Bree", 0, 0.0F, 0.0F),
                icon("losttales:b", "Aldburg", 0, 0.0F, 0.0F),
                icon("losttales:a", "Aldburg", 0, 0.0F, 0.0F));

        assertEquals(2, result.getGroups().get(0)
                .getRepresentativeIndex());
    }

    @Test
    public void mergedStacksChooseCompanionsByGlobalRelevance() {
        Map<String, String> previous = new HashMap<String, String>();
        previous.put("losttales:highest", "losttales:highest");
        previous.put("losttales:low", "losttales:highest");
        previous.put("losttales:second", "losttales:second");
        previous.put("losttales:third", "losttales:second");

        LostTalesMapMarkerGrouping.Result result =
                LostTalesMapMarkerGrouping.group(Arrays.asList(
                        icon("losttales:highest", "Highest", 30, 0.0F, 0.0F),
                        icon("losttales:low", "Low", 1, 2.0F, 0.0F),
                        icon("losttales:second", "Second", 20, 1.0F, 0.0F),
                        icon("losttales:third", "Third", 10, 3.0F, 0.0F)),
                        previous);
        LostTalesMapMarkerGrouping.Group group = result.getGroups().get(0);

        assertEquals(1, result.getGroups().size());
        assertEquals(-1, group.getCompanionSide(2));
        assertEquals(1, group.getCompanionSide(3));
        assertEquals(0, group.getCompanionSide(1));
    }

    @Test
    public void groupCompanionsUseTheNextTwoMostRelevantMarkers() {
        LostTalesMapMarkerGrouping.Result result = group(fresh(),
                icon("losttales:low", "Low", 1, 0.0F, 0.0F),
                icon("losttales:highest", "Highest", 30, 0.0F, 0.0F),
                icon("losttales:second", "Second", 20, 0.0F, 0.0F),
                icon("losttales:third", "Third", 10, 0.0F, 0.0F));
        LostTalesMapMarkerGrouping.Group group = result.getGroups().get(0);

        assertEquals(1, group.getRepresentativeIndex());
        assertEquals(-1, group.getCompanionSide(2));
        assertEquals(1, group.getCompanionSide(3));
        assertEquals(0, group.getCompanionSide(0));
    }

    @Test
    public void closeZoomCanForceEveryMarkerToRemainVisible() {
        LostTalesMapMarkerGrouping.Result result =
                LostTalesMapMarkerGrouping.ungroup(Arrays.asList(
                        icon("losttales:a", "A", 10, 0.0F, 0.0F),
                        icon("losttales:b", "B", 5, 0.0F, 0.0F)));

        assertEquals(2, result.getGroups().size());
        assertEquals(0, result.getMembership().size());
    }

    @Test
    public void renderOrderLeavesRelevanceAndAlphabeticalWinnerOnTop() {
        // Lone markers are stacks of one, so the existing relevance-then-
        // alphabetical order has to survive untouched.
        List<LostTalesMapMarkerGrouping.Entry> entries = Arrays.asList(
                icon("losttales:low", "Low", 0, 0.0F, 0.0F),
                icon("losttales:b", "Bree", 10, 400.0F, 0.0F),
                icon("losttales:a", "Aldburg", 10, 800.0F, 0.0F));
        LostTalesMapMarkerGrouping.Result result =
                LostTalesMapMarkerGrouping.ungroup(entries);

        assertEquals(Arrays.asList(
                        Integer.valueOf(0),
                        Integer.valueOf(1),
                        Integer.valueOf(2)),
                LostTalesMapMarkerGrouping.groupsBottomToTop(
                        entries, result.getGroups()));
    }

    @Test
    public void aStackIsRankedByItsMostRelevantMember() {
        // Y sits between X and Z: X covers it, but it lies almost on top of
        // Z, so it ends up in a stack led by the marker it outranks. That
        // stack has to draw by Y's relevance, above the lone marker A.
        List<LostTalesMapMarkerGrouping.Entry> entries = Arrays.asList(
                icon("losttales:x", "X", 50, 0.0F, 0.0F),
                icon("losttales:y", "Y", 40, 9.0F, 0.0F),
                icon("losttales:z", "Z", 30, 11.0F, 0.0F),
                icon("losttales:a", "A", 35, 400.0F, 0.0F));
        LostTalesMapMarkerGrouping.Result result =
                LostTalesMapMarkerGrouping.group(entries, fresh());

        assertEquals(describe(result), "losttales:z",
                result.getMembership().get("losttales:y"));

        List<Integer> order = LostTalesMapMarkerGrouping.groupsBottomToTop(
                entries, result.getGroups());
        assertEquals("the stack must not sink below A", "losttales:a",
                leaderIdOf(entries, result, order.get(0)));
        assertEquals("losttales:z",
                leaderIdOf(entries, result, order.get(1)));
        assertEquals("losttales:x",
                leaderIdOf(entries, result, order.get(2)));
    }

    @Test
    public void everyMarkerBelongsToExactlyOneRenderEntry() {
        // A fan is one entry in the draw order, so no marker may appear in
        // two entries and none may be missing from them.
        List<LostTalesMapMarkerGrouping.Entry> entries = Arrays.asList(
                icon("losttales:a", "A", 40, 0.0F, 0.0F),
                icon("losttales:b", "B", 30, 4.0F, 0.0F),
                icon("losttales:c", "C", 20, 400.0F, 0.0F),
                icon("losttales:d", "D", 10, 800.0F, 0.0F));
        LostTalesMapMarkerGrouping.Result result =
                LostTalesMapMarkerGrouping.group(entries, fresh());
        List<Integer> order = LostTalesMapMarkerGrouping.groupsBottomToTop(
                entries, result.getGroups());

        assertEquals(result.getGroups().size(), order.size());
        List<Integer> drawn = new ArrayList<Integer>();
        for (Integer entry : order) {
            drawn.addAll(result.getGroups().get(entry.intValue())
                    .getMemberIndices());
        }
        assertEquals(entries.size(), drawn.size());
        assertTrue(drawn.containsAll(Arrays.asList(
                Integer.valueOf(0), Integer.valueOf(1),
                Integer.valueOf(2), Integer.valueOf(3))));
    }

    private static String leaderIdOf(
            List<LostTalesMapMarkerGrouping.Entry> entries,
            LostTalesMapMarkerGrouping.Result result, Integer orderIndex) {
        return entries.get(result.getGroups()
                .get(orderIndex.intValue()).getRepresentativeIndex())
                .getId();
    }

    @Test
    public void fanOffsetsPlaceCompanionsAroundTheRepresentative() {
        LostTalesCompassMarkerIcon pin =
                LostTalesCompassMarkerIcon.UNDISCOVERED;
        float spread = LostTalesLotrMapMarkerIconOverlay.fanOffsetX(
                pin, 13.0F, 1.0F);

        // Mirrored sideways, and far enough out to clear the leader.
        assertEquals(-spread, LostTalesLotrMapMarkerIconOverlay.fanOffsetX(
                pin, 13.0F, -1.0F), 0.0001F);
        assertTrue("companions must clear the leading icon",
                spread > LostTalesLotrMapMarkerIconOverlay.artHalfWidth(
                        pin, 13.0F));
        // Both companions rise by the same amount, whichever side they sit on.
        float rise = LostTalesLotrMapMarkerIconOverlay.fanOffsetY(
                pin, 13.0F, 1.0F);
        assertEquals(rise, LostTalesLotrMapMarkerIconOverlay.fanOffsetY(
                pin, 13.0F, -1.0F), 0.0001F);
        assertTrue("companions sit above the leader", rise < 0.0F);
        assertEquals(0.0F, LostTalesLotrMapMarkerIconOverlay.fanOffsetY(
                pin, 13.0F, 0.0F), 0.0F);
    }

    @Test
    public void fanSpacingGrowsWithTheIconAndWithScale() {
        // A wider glyph spreads its fan wider, and the whole arrangement
        // scales with the sprite so an enlarged fan does not close over
        // itself.
        float pinSpread = LostTalesLotrMapMarkerIconOverlay.fanOffsetX(
                LostTalesCompassMarkerIcon.UNDISCOVERED, 13.0F, 1.0F);
        float shackSpread = LostTalesLotrMapMarkerIconOverlay.fanOffsetX(
                LostTalesCompassMarkerIcon.SHACK, 13.0F, 1.0F);
        float pinHovered = LostTalesLotrMapMarkerIconOverlay.fanOffsetX(
                LostTalesCompassMarkerIcon.UNDISCOVERED, 16.0F, 1.0F);

        assertTrue("wider artwork needs a wider fan",
                shackSpread > pinSpread);
        assertTrue("an enlarged fan spreads further",
                pinHovered > pinSpread);
    }

    @Test
    public void leaveThresholdStaysUnderTheJoinThreshold() {
        assertFalse(LostTalesMapMarkerGrouping.LEAVE_OVERLAP_RATIO
                >= LostTalesMapMarkerGrouping.JOIN_OVERLAP_RATIO);
    }

    @Test
    public void visibilitySynchronizesGroupTravelWithFade() {
        // Both ends are exact whatever the curve does in between, and any
        // visibility outside 0..1 is held at the nearer end.
        assertPoint(20.0F, 40.0F, transition(
                20.0F, 40.0F, 100.0F, 40.0F, 0.0F));
        assertPoint(100.0F, 40.0F, transition(
                20.0F, 40.0F, 100.0F, 40.0F, 1.0F));
        assertPoint(20.0F, 40.0F, transition(
                20.0F, 40.0F, 100.0F, 40.0F, -1.0F));
        assertPoint(100.0F, 40.0F, transition(
                20.0F, 40.0F, 100.0F, 40.0F, 2.0F));

        // Halfway along, the marker is halfway between its endpoints and
        // bowed to one side by the capped arc.
        float[] middle = transition(
                20.0F, 40.0F, 100.0F, 40.0F, 0.5F);
        assertEquals(60.0F, middle[0], 0.0001F);
        assertEquals(40.0F
                        + LostTalesMapMarkerGrouping
                                .TRANSITION_ARC_MAX_PIXELS,
                middle[1], 0.0001F);
    }

    @Test
    public void travelBowsToOneSideWithoutMovingItsEndpoints() {
        // A short hop bows by a share of its own length rather than the cap,
        // so nearby markers do not swing further than they travel.
        float[] middle = transition(
                0.0F, 0.0F, 10.0F, 0.0F, 0.5F);
        assertEquals(5.0F, middle[0], 0.0001F);
        assertEquals(10.0F * LostTalesMapMarkerGrouping
                        .TRANSITION_ARC_RATIO,
                middle[1], 0.0001F);

        // The bow closes smoothly and is gone at both ends.
        assertTrue(Math.abs(transition(
                0.0F, 0.0F, 10.0F, 0.0F, 0.1F)[1])
                < Math.abs(middle[1]));
        assertEquals(0.0F,
                transition(0.0F, 0.0F, 10.0F, 0.0F, 1.0F)[1], 0.0001F);

        // A marker that is not travelling has no path to bow.
        assertPoint(7.0F, 7.0F,
                transition(7.0F, 7.0F, 7.0F, 7.0F, 0.5F));
    }

    @Test
    public void aJoiningMarkerNeverCrossesPastItsOwnSlot() {
        float anchorX = 100.0F;
        float markerX = 160.0F;
        float slotOffsetX = -8.0F;
        float previous = Float.MAX_VALUE;

        for (int step = 10; step >= 0; step--) {
            float[] point = companion(anchorX, markerX,
                    slotOffsetX, step / 10.0F);

            assertTrue("the marker overshot the slot it is heading for",
                    point[0] >= anchorX + slotOffsetX - 0.0001F);
            assertTrue("the marker doubled back on its way in",
                    point[0] <= previous + 0.0001F);
            previous = point[0];
        }
    }

    /**
     * The reported regression: two stacks meeting measured every member of the
     * stack that gave way from a different marker on the very next frame, so
     * its markers teleported into the other fan instead of joining it.
     */
    @Test
    public void amergedStackTravelsToItsNewLeaderRatherThanJumping() {
        LostTalesLotrMapMarkerIconOverlay.MarkerAnimationState state =
                settledCompanion(-1.0F);
        float[] offset = new float[2];

        // Settled behind its own leader, eight pixels to its left.
        LostTalesLotrMapMarkerIconOverlay.stepStackHandover(
                state, "losttales:old", -8.0F, 0.0F, 1.0F, offset);
        assertEquals(-8.0F, offset[0], 0.0001F);

        // Its stack is swallowed: the new leader is sixty pixels away.
        LostTalesLotrMapMarkerIconOverlay.stepStackHandover(
                state, "losttales:new", -68.0F, 0.0F, 0.25F, offset);
        assertTrue("the marker jumped to the new stack",
                offset[0] > -68.0F + 0.0001F);
        assertTrue("the marker did not set off at all",
                offset[0] < -8.0F - 0.0001F);

        float previous = offset[0];
        for (int frame = 0; frame < 4; frame++) {
            LostTalesLotrMapMarkerIconOverlay.stepStackHandover(
                    state, "losttales:new", -68.0F, 0.0F, 0.25F, offset);
            assertTrue("the travel went backwards",
                    offset[0] <= previous + 0.0001F);
            previous = offset[0];
        }
        assertEquals("the marker never arrived at its new stack",
                -68.0F, offset[0], 0.0001F);
        assertEquals(1.0F, state.getHandoverProgress(), 0.0001F);
    }

    /** Panning moves a stack and its members together, so it is not a change. */
    @Test
    public void followingTheSameStackNeedsNoHandover() {
        LostTalesLotrMapMarkerIconOverlay.MarkerAnimationState state =
                settledCompanion(1.0F);
        float[] offset = new float[2];

        LostTalesLotrMapMarkerIconOverlay.stepStackHandover(
                state, "losttales:leader", 8.0F, -3.0F, 1.0F, offset);
        // A zoom changes the distance between the two; the member follows it
        // exactly rather than easing after it.
        LostTalesLotrMapMarkerIconOverlay.stepStackHandover(
                state, "losttales:leader", 12.0F, -4.0F, 0.1F, offset);

        assertEquals(12.0F, offset[0], 0.0001F);
        assertEquals(-4.0F, offset[1], 0.0001F);
    }

    @Test
    public void theFanSlotOnlyOpensAsTheMarkerReachesTheStack() {
        float anchorX = 100.0F;
        float markerX = 160.0F;
        float slotOffsetX = -8.0F;

        // Barely under way: the slot has hardly opened, so the marker is
        // still travelling towards the leader rather than towards the slot.
        float[] early = companion(anchorX, markerX, slotOffsetX, 0.9F);
        assertTrue(early[0] > anchorX + slotOffsetX * 0.2F);

        // Arrived: the marker is standing in its slot beside the leader.
        assertEquals(anchorX + slotOffsetX,
                companion(anchorX, markerX, slotOffsetX, 0.0F)[0],
                0.0001F);
        // Released: no trace of the slot remains at its own position.
        assertEquals(markerX,
                companion(anchorX, markerX, slotOffsetX, 1.0F)[0],
                0.0001F);
    }

    /**
     * A slot exchange is one movement in two directions: the marker taking the
     * slot comes out of the stack as the marker losing it goes back in, and
     * neither waits for the other.
     */
    @Test
    public void aSlotExchangeMovesBothMarkersAtOnce() {
        LostTalesLotrMapMarkerIconOverlay.MarkerAnimationState incoming =
                LostTalesLotrMapMarkerIconOverlay.MarkerAnimationState
                        .settled(false, 0.0F);
        LostTalesLotrMapMarkerIconOverlay.MarkerAnimationState displaced =
                settledCompanion(-1.0F);

        for (int frame = 0; frame < 3; frame++) {
            // One shared budget, the same call for both.
            LostTalesLotrMapMarkerIconOverlay.stepMarkerAnimation(
                    incoming, false, -1.0F, 0.25F);
            LostTalesLotrMapMarkerIconOverlay.stepMarkerAnimation(
                    displaced, false, 0.0F, 0.25F);
        }

        assertTrue("the marker taking the slot never left the stack",
                incoming.getCompanionSlot() < -0.0001F);
        assertTrue("the displaced marker never went back to the stack",
                displaced.getCompanionSlot() > incoming.getCompanionSlot());
        // Both are still grouped: neither of them travels to its own map
        // position during an exchange.
        assertEquals(0.0F, incoming.getVisibility(), 0.0001F);
        assertEquals(0.0F, displaced.getVisibility(), 0.0001F);
    }

    private static float[] companion(
            float anchorX, float markerX, float slotOffsetX,
            float visibility) {
        float[] point = new float[2];
        LostTalesLotrMapMarkerIconOverlay.companionPoint(
                anchorX, 100.0F, markerX, 100.0F,
                slotOffsetX, 0.0F, visibility, point);
        return point;
    }

    private static float[] transition(
            float anchorX, float anchorY, float markerX, float markerY,
            float visibility) {
        float[] point = new float[2];
        LostTalesMapMarkerGrouping.transitionPoint(
                anchorX, anchorY, markerX, markerY, visibility, point);
        return point;
    }

    private static void assertPoint(
            float expectedX, float expectedY, float[] actual) {
        assertEquals(expectedX, actual[0], 0.0001F);
        assertEquals(expectedY, actual[1], 0.0001F);
    }

    @Test
    public void fanCompanionsRestBehindTheMarkerLeadingTheStack() {
        // Settled companion: dimmed and slightly smaller.
        assertEquals(0.5F,
                LostTalesLotrMapMarkerIconOverlay.groupedIconAlpha(
                        0.0F, 1.0F), 0.0001F);
        assertEquals(0.9F,
                LostTalesLotrMapMarkerIconOverlay.groupedIconScale(
                        0.0F, 1.0F), 0.0001F);
        // A member the fan cannot show has no slot and fades out entirely.
        assertEquals(0.0F,
                LostTalesLotrMapMarkerIconOverlay.groupedIconAlpha(
                        0.0F, 0.0F), 0.0001F);
        // Fully released: back to a normal marker.
        assertEquals(1.0F,
                LostTalesLotrMapMarkerIconOverlay.groupedIconAlpha(
                        1.0F, 1.0F), 0.0001F);
        assertEquals(1.0F,
                LostTalesLotrMapMarkerIconOverlay.groupedIconScale(
                        1.0F, 1.0F), 0.0001F);
    }

    @Test
    public void aStackNeverShedsAndReabsorbsTheSameMember() {
        // C fails a member-to-member test against A but still sits well
        // inside the stack's drawn fan. Splitting on one shape and merging on
        // the other made the stack drop C and take it straight back, flipping
        // every frame - and a re-absorbed member lands in the right-hand fan
        // slot, so the right sprite blinked.
        Map<String, String> membership = new HashMap<String, String>();
        membership.put("losttales:a", "losttales:a");
        membership.put("losttales:b", "losttales:a");
        membership.put("losttales:c", "losttales:a");

        List<LostTalesMapMarkerGrouping.Entry> entries = Arrays.asList(
                icon("losttales:a", "A", 30, 0.0F, 0.0F),
                icon("losttales:b", "B", 20, 3.0F, 0.0F),
                icon("losttales:c", "C", 10, -11.0F, 0.0F));

        String settled = null;
        for (int frame = 0; frame < 6; frame++) {
            LostTalesMapMarkerGrouping.Result result =
                    LostTalesMapMarkerGrouping.group(entries, membership);
            membership = new HashMap<String, String>(
                    result.getMembership());
            String description = describe(result);
            if (settled != null) {
                assertEquals("grouping flipped on frame " + frame,
                        settled, description);
            }
            settled = description;
        }
    }

    @Test
    public void aMemberThatTrulyLeavesTheFanStaysOut() {
        Map<String, String> membership = new HashMap<String, String>();
        membership.put("losttales:a", "losttales:a");
        membership.put("losttales:b", "losttales:a");
        membership.put("losttales:c", "losttales:a");

        // Far enough out that it no longer overlaps the drawn stack at all.
        List<LostTalesMapMarkerGrouping.Entry> entries = Arrays.asList(
                icon("losttales:a", "A", 30, 0.0F, 0.0F),
                icon("losttales:b", "B", 20, 3.0F, 0.0F),
                icon("losttales:c", "C", 10, -16.0F, 0.0F));

        String settled = null;
        for (int frame = 0; frame < 6; frame++) {
            LostTalesMapMarkerGrouping.Result result =
                    LostTalesMapMarkerGrouping.group(entries, membership);
            membership = new HashMap<String, String>(
                    result.getMembership());
            if (settled != null) {
                assertEquals("grouping flipped on frame " + frame,
                        settled, describe(result));
            }
            settled = describe(result);
            assertEquals(2, result.getGroups().size());
            assertNull(result.getMembership().get("losttales:c"));
        }
    }

    @Test
    public void bothFanSidesCountAsFullMembership() {
        assertEquals(1.0F, LostTalesLotrMapMarkerIconOverlay
                .fanStrengthTarget(-1.0F), 0.0F);
        assertEquals(1.0F, LostTalesLotrMapMarkerIconOverlay
                .fanStrengthTarget(1.0F), 0.0F);
        // The leader, and any member the fan cannot show, have no slot.
        assertEquals(0.0F, LostTalesLotrMapMarkerIconOverlay
                .fanStrengthTarget(0.0F), 0.0F);
    }

    @Test
    public void swappingFanSidesDoesNotBlinkTheCompanionOut() {
        // Losing a member re-sorts the survivors, so the right companion
        // becomes the left one and its offset travels from +1 through 0 to -1.
        // Opacity and size read membership, not the offset, so they must not
        // move at all while it slides across.
        float slot = 1.0F;
        float strength = 1.0F;
        float targetStrength = LostTalesLotrMapMarkerIconOverlay
                .fanStrengthTarget(-1.0F);
        boolean passedThroughTheMiddle = false;
        for (int frame = 0; frame < 20; frame++) {
            slot = LostTalesLotrMapMarkerIconOverlay.approach(
                    slot, -1.0F, 0.2F);
            strength = LostTalesLotrMapMarkerIconOverlay.approach(
                    strength, targetStrength, 0.2F);
            if (Math.abs(slot) < 0.0001F) {
                passedThroughTheMiddle = true;
            }
            assertEquals("opacity moved mid-swap", 0.5F,
                    LostTalesLotrMapMarkerIconOverlay.groupedIconAlpha(
                            0.0F, strength), 0.0001F);
            assertEquals("size moved mid-swap", 0.9F,
                    LostTalesLotrMapMarkerIconOverlay.groupedIconScale(
                            0.0F, strength), 0.0001F);
        }
        assertTrue("the companion should cross the middle",
                passedThroughTheMiddle);
        assertEquals(-1.0F, slot, 0.0001F);
    }

    @Test
    public void leavingAFanNeverDimsOrShrinksOnTheWayOut() {
        // The flicker regression: opacity and size used to sag mid-transition
        // because they were tied to the collapsing fan offset.
        float previousAlpha = -1.0F;
        float previousScale = -1.0F;
        for (int step = 0; step <= 20; step++) {
            float visibility = step / 20.0F;
            float alpha = LostTalesLotrMapMarkerIconOverlay
                    .groupedIconAlpha(visibility, 1.0F);
            float scale = LostTalesLotrMapMarkerIconOverlay
                    .groupedIconScale(visibility, 1.0F);
            assertTrue("opacity dipped at " + visibility,
                    alpha >= previousAlpha);
            assertTrue("size dipped at " + visibility,
                    scale >= previousScale);
            previousAlpha = alpha;
            previousScale = scale;
        }
    }

    @Test
    public void questMarkersNeverGroup() {
        // Two quest markers drawn exactly on top of each other still stay two
        // readable objectives.
        LostTalesMapMarkerGrouping.Result result = group(fresh(),
                icon("losttales:q1", "Q1", 10, QUEST, 0.0F, 0.0F),
                icon("losttales:q2", "Q2", 5, QUEST, 0.0F, 0.0F));

        assertEquals(2, result.getGroups().size());
        assertEquals(0, result.getMembership().size());
    }

    @Test
    public void aQuestMarkerNeitherJoinsNorSwallowsALocation() {
        // Whichever one ranks higher, the two must not end up in one stack.
        assertEquals(2, group(fresh(),
                icon("losttales:q", "Q", 10, QUEST, 0.0F, 0.0F),
                icon("losttales:a", "A", 5, LOCATION, 0.0F, 0.0F))
                .getGroups().size());
        assertEquals(2, group(fresh(),
                icon("losttales:a", "A", 10, LOCATION, 0.0F, 0.0F),
                icon("losttales:q", "Q", 5, QUEST, 0.0F, 0.0F))
                .getGroups().size());
    }

    @Test
    public void incompatibleCategoriesNeverShareAGroup() {
        for (LostTalesMapMarkerGrouping.GroupingCategory first
                : LostTalesMapMarkerGrouping.GroupingCategory.values()) {
            for (LostTalesMapMarkerGrouping.GroupingCategory second
                    : LostTalesMapMarkerGrouping.GroupingCategory
                            .values()) {
                // Stacked at the same point: two markers merge only when
                // they share a category and that category groups at all.
                int expected = first == second
                        && first.isGroupingEligible() ? 1 : 2;
                assertEquals(first + " with " + second, expected,
                        group(fresh(),
                                icon("losttales:a", "A", 10, first,
                                        0.0F, 0.0F),
                                icon("losttales:b", "B", 5, second,
                                        0.0F, 0.0F))
                                .getGroups().size());
            }
        }
    }

    @Test
    public void deliberatelyPlacedMarkersStayIndividuallyReadable() {
        // A player's own waypoints and quest objectives each mean something
        // different even when they sit on the same spot, so they never
        // collapse into a stack. Waystones are ordinary map furniture and do.
        assertFalse(LostTalesMapMarkerGrouping.GroupingCategory
                .PERSONAL_WAYPOINT.isGroupingEligible());
        assertFalse(LostTalesMapMarkerGrouping.GroupingCategory
                .SHARED_WAYPOINT.isGroupingEligible());
        assertFalse(LostTalesMapMarkerGrouping.GroupingCategory
                .QUEST.isGroupingEligible());
        assertTrue(LostTalesMapMarkerGrouping.GroupingCategory
                .PLAYER_WAYSTONE.isGroupingEligible());
    }

    @Test
    public void anUnclassifiedMarkerGroupsWithNothing() {
        // Fail closed: two markers we cannot classify are not evidence that
        // they belong together.
        assertEquals(2, group(fresh(),
                icon("losttales:a", "A", 10, UNKNOWN, 0.0F, 0.0F),
                icon("losttales:b", "B", 5, UNKNOWN, 0.0F, 0.0F))
                .getGroups().size());
    }

    @Test
    public void anIncompatibleMarkerDoesNotSplitTheStackAroundIt() {
        // The quest marker sits on top of both locations. It must neither
        // join them nor stop them from finding each other.
        LostTalesMapMarkerGrouping.Result result = group(fresh(),
                icon("losttales:q", "Q", 30, QUEST, 0.0F, 0.0F),
                icon("losttales:a", "A", 20, LOCATION, 0.0F, 0.0F),
                icon("losttales:b", "B", 10, LOCATION, 3.0F, 0.0F));

        assertEquals(describe(result), 2, result.getGroups().size());
        assertEquals("losttales:a",
                result.getMembership().get("losttales:b"));
        assertNull(result.getMembership().get("losttales:q"));
    }

    @Test
    public void legendCategoriesMapOntoGroupingCategories() {
        assertEquals(LOCATION,
                LostTalesMapMarkerGrouping.GroupingCategory
                        .forLegendCategory(
                                LostTalesMapLegendRegistry.LOCATIONS));
        assertEquals(QUEST,
                LostTalesMapMarkerGrouping.GroupingCategory
                        .forLegendCategory(
                                LostTalesMapLegendRegistry.QUESTS));
        assertEquals(PARTY,
                LostTalesMapMarkerGrouping.GroupingCategory
                        .forLegendCategory(
                                LostTalesMapLegendRegistry.PARTY));
        assertEquals(UNKNOWN,
                LostTalesMapMarkerGrouping.GroupingCategory
                        .forLegendCategory(null));
        assertEquals(UNKNOWN,
                LostTalesMapMarkerGrouping.GroupingCategory
                        .forLegendCategory("not_a_legend_category"));
        assertFalse(QUEST.isGroupingEligible());
        assertFalse(UNKNOWN.isGroupingEligible());
        assertTrue(LOCATION.isGroupingEligible());
    }

    @Test
    public void zoomingOutNeverBreaksAStackApart() {
        // C leads B and D, which sit on opposite sides of it and do not reach
        // each other. Zooming out only brings everything closer, so the stack
        // must stay one stack led by the same marker. It used to be rebuilt
        // around B, which left D stranded as a brand new group.
        Map<String, String> grouped = group(fresh(),
                icon("losttales:c", "C", 50, 0.0F, 0.0F),
                icon("losttales:b", "B", 40, 7.0F, 0.0F),
                icon("losttales:d", "D", 20, -7.0F, 0.0F))
                .getMembership();
        assertEquals(3, grouped.size());

        LostTalesMapMarkerGrouping.Result zoomedOut = group(grouped,
                icon("losttales:c", "C", 50, 0.0F, 0.0F),
                icon("losttales:b", "B", 40, 6.3F, 0.0F),
                icon("losttales:d", "D", 20, -6.3F, 0.0F));

        assertEquals(describe(zoomedOut), 1, zoomedOut.getGroups().size());
        assertEquals("the stack changed leader", "losttales:c",
                zoomedOut.getMembership().get("losttales:c"));
        assertEquals("losttales:c",
                zoomedOut.getMembership().get("losttales:b"));
        assertEquals("D was stranded in a new group", "losttales:c",
                zoomedOut.getMembership().get("losttales:d"));
    }

    @Test
    public void twoStacksMergeAsWholeStacks() {
        // Two settled stacks drift together. Every member of the absorbed
        // stack has to arrive in the surviving one, not scatter.
        Map<String, String> grouped = group(fresh(),
                icon("losttales:a", "A", 50, 0.0F, 0.0F),
                icon("losttales:a2", "A2", 45, 7.0F, 0.0F),
                icon("losttales:e", "E", 40, 40.0F, 0.0F),
                icon("losttales:e2", "E2", 35, 47.0F, 0.0F))
                .getMembership();
        assertEquals(4, grouped.size());

        LostTalesMapMarkerGrouping.Result merged = group(grouped,
                icon("losttales:a", "A", 50, 0.0F, 0.0F),
                icon("losttales:a2", "A2", 45, 5.0F, 0.0F),
                icon("losttales:e", "E", 40, 9.0F, 0.0F),
                icon("losttales:e2", "E2", 35, 14.0F, 0.0F));

        assertEquals(describe(merged), 1, merged.getGroups().size());
        assertEquals(4, merged.getGroups().get(0).size());
    }

    @Test
    public void aMergedStackDoesNotShedTheMemberItJustAbsorbed() {
        // Two stacks meet and merge. The absorbed stack's far member sits
        // beyond the leave threshold from the surviving leader, even though
        // it is still sitting on its own neighbours. Testing it against that
        // one leader ejected it on the very next recalculation, which is the
        // lone marker that appeared to split off out of a merge.
        // The links a real merge leaves behind: E2 came to rest on E, and E
        // then came to rest on A when the two stacks met.
        Map<String, String> merged = chainedFrom("losttales:a",
                "losttales:a2", "losttales:e", "losttales:e2");

        // E2 is 14 pixels from the leader and touches nothing of it, but it
        // is still sitting squarely on the marker that admitted it.
        LostTalesMapMarkerGrouping.Result settled = group(merged,
                icon("losttales:a", "A", 50, 0.0F, 0.0F),
                icon("losttales:a2", "A2", 45, 5.0F, 0.0F),
                icon("losttales:e", "E", 40, 9.0F, 0.0F),
                icon("losttales:e2", "E2", 35, 14.0F, 0.0F));

        assertEquals(describe(settled), 1, settled.getGroups().size());
        assertEquals(4, settled.getGroups().get(0).size());
    }

    @Test
    public void aStretchedStackSplitsIntoThePartsStillTouching() {
        // The inverse: zooming in until the middle link breaks has to leave
        // the two halves as two stacks, not one stack and two loose markers.
        Map<String, String> merged = stackedAs("losttales:a",
                "losttales:a2", "losttales:e", "losttales:e2");

        // Each pair still touches; the gap in the middle no longer does.
        LostTalesMapMarkerGrouping.Result split = group(merged,
                icon("losttales:a", "A", 50, 0.0F, 0.0F),
                icon("losttales:a2", "A2", 45, 8.0F, 0.0F),
                icon("losttales:e", "E", 40, 22.0F, 0.0F),
                icon("losttales:e2", "E2", 35, 30.0F, 0.0F));

        assertEquals(describe(split), 2, split.getGroups().size());
        assertEquals("losttales:a",
                split.getMembership().get("losttales:a2"));
        assertEquals("losttales:e",
                split.getMembership().get("losttales:e2"));
    }

    @Test
    public void zoomingBackInComesApartWhereZoomingOutCameTogether() {
        // Twelve markers merged all the way down and pulled apart again. A
        // marker is held by the one marker it landed on, so it lets go at the
        // spacing it took hold at — the stacks reappear in reverse order
        // instead of surviving far too long and then all bursting at once.
        List<Float> spacings = new ArrayList<Float>();
        for (float spacing = 24.0F; spacing > 1.5F; spacing *= 0.9F) {
            spacings.add(Float.valueOf(spacing));
        }

        Map<String, String> links = fresh();
        List<Integer> merging = new ArrayList<Integer>();
        for (Float spacing : spacings) {
            LostTalesMapMarkerGrouping.Result result =
                    lineOfMarkers(links, spacing.floatValue());
            links = result.getLinks();
            merging.add(Integer.valueOf(result.getGroups().size()));
        }
        List<Integer> parting = new ArrayList<Integer>();
        for (int index = spacings.size() - 1; index >= 0; index--) {
            LostTalesMapMarkerGrouping.Result result = lineOfMarkers(
                    links, spacings.get(index).floatValue());
            links = result.getLinks();
            parting.add(Integer.valueOf(result.getGroups().size()));
        }
        Collections.reverse(parting);

        assertEquals("the line never merged", Integer.valueOf(12),
                merging.get(0));
        assertTrue("the line never came together",
                merging.get(merging.size() - 1).intValue() < 12);
        assertEquals("the line did not come apart again",
                Integer.valueOf(12), parting.get(0));
        // On the way in a marker may hold on a little past where it merged —
        // that band is what stops it flickering on the threshold — but by no
        // more than one step of this sweep. Anything beyond that is a marker
        // clinging to a stack it has visibly left.
        for (int index = 0; index < merging.size(); index++) {
            int allowed = merging.get(
                    Math.min(index + 1, merging.size() - 1)).intValue();
            assertTrue("held on too long at spacing "
                            + spacings.get(index) + ": merged into "
                            + merging.get(index) + " stacks, parted into "
                            + parting.get(index),
                    parting.get(index).intValue() >= allowed);
        }
    }

    private static LostTalesMapMarkerGrouping.Result lineOfMarkers(
            Map<String, String> links, float spacing) {
        List<LostTalesMapMarkerGrouping.Entry> entries =
                new ArrayList<LostTalesMapMarkerGrouping.Entry>();
        for (int index = 0; index < 12; index++) {
            entries.add(icon("losttales:m" + index, "M" + index,
                    12 - index, index * spacing, 0.0F));
        }
        return LostTalesMapMarkerGrouping.group(entries, links);
    }

    @Test
    public void aStackNeverSprawlsAcrossTheMapAsItAbsorbsOthers() {
        // Thirty markers in a line, collapsing together all the way. Stacks
        // must keep merging without any of them growing into a smear that
        // reaches markers nowhere near the icon it is drawn as. Measured
        // worst case is about 2.2 icon widths; this leaves headroom.
        float widthLimit = ICON_HALF_EXTENT * 2.0F * 4.0F;
        Map<String, String> membership = fresh();
        int fewestStacks = Integer.MAX_VALUE;
        for (float spacing = 30.0F; spacing > 1.0F; spacing *= 0.93F) {
            List<LostTalesMapMarkerGrouping.Entry> entries =
                    new ArrayList<LostTalesMapMarkerGrouping.Entry>();
            for (int index = 0; index < 30; index++) {
                entries.add(icon("losttales:m" + index, "M" + index,
                        30 - index, index * spacing, 0.0F));
            }
            LostTalesMapMarkerGrouping.Result result =
                    LostTalesMapMarkerGrouping.group(entries, membership);
            membership = result.getMembership();
            fewestStacks = Math.min(
                    fewestStacks, result.getGroups().size());
            for (LostTalesMapMarkerGrouping.Group group
                    : result.getGroups()) {
                float lowest = Float.MAX_VALUE;
                float highest = -Float.MAX_VALUE;
                for (Integer member : group.getMemberIndices()) {
                    float x = member.intValue() * spacing;
                    lowest = Math.min(lowest, x);
                    highest = Math.max(highest, x);
                }
                assertTrue("a stack of " + group.size()
                                + " spanned " + (highest - lowest)
                                + " pixels at spacing " + spacing,
                        highest - lowest <= widthLimit);
            }
        }
        assertTrue("the line never actually merged",
                fewestStacks < 30);
    }

    @Test
    public void aStackNeverLosesAMemberWhileZoomingAllTheWayOut() {
        // Walk a whole zoom-out sweep and assert the invariant at every step:
        // markers that share a stack keep sharing one.
        List<LostTalesMapMarkerGrouping.Entry> field = crowdedField();
        Map<String, String> membership = LostTalesMapMarkerGrouping.group(
                field, fresh()).getMembership();
        int largestStack = 0;
        for (float scale = 0.95F; scale > 0.05F; scale -= 0.05F) {
            Map<String, String> previous = membership;
            LostTalesMapMarkerGrouping.Result result =
                    LostTalesMapMarkerGrouping.group(
                            contracted(field, scale), previous);
            membership = result.getMembership();
            for (LostTalesMapMarkerGrouping.Group group
                    : result.getGroups()) {
                largestStack = Math.max(largestStack, group.size());
            }
            for (Map.Entry<String, String> was : previous.entrySet()) {
                String other = findAnotherMemberOf(previous, was.getKey());
                if (other == null) {
                    continue;
                }
                assertEquals("scale " + scale + ": " + was.getKey()
                                + " and " + other + " were split apart",
                        membership.get(was.getKey()),
                        membership.get(other));
            }
        }
        assertTrue("the sweep never actually formed a stack",
                largestStack > 1);
    }

    private static String findAnotherMemberOf(
            Map<String, String> membership, String markerId) {
        String stack = membership.get(markerId);
        for (Map.Entry<String, String> entry : membership.entrySet()) {
            if (!entry.getKey().equals(markerId)
                    && entry.getValue().equals(stack)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** The same field seen at a lower zoom, contracted about the origin. */
    private static List<LostTalesMapMarkerGrouping.Entry> contracted(
            List<LostTalesMapMarkerGrouping.Entry> field, float scale) {
        List<LostTalesMapMarkerGrouping.Entry> scaled =
                new ArrayList<LostTalesMapMarkerGrouping.Entry>(
                        field.size());
        for (LostTalesMapMarkerGrouping.Entry entry : field) {
            scaled.add(new LostTalesMapMarkerGrouping.Entry(
                    entry.getId(), entry.getId(),
                    entry.getRelevanceRank(),
                    entry.getGroupingCategory(),
                    (entry.getLeft() + ICON_HALF_EXTENT) * scale
                            - ICON_HALF_EXTENT,
                    (entry.getTop() + ICON_HALF_EXTENT) * scale
                            - ICON_HALF_EXTENT,
                    (entry.getLeft() + ICON_HALF_EXTENT) * scale
                            + ICON_HALF_EXTENT + 1.0F,
                    (entry.getTop() + ICON_HALF_EXTENT) * scale
                            + ICON_HALF_EXTENT + 1.0F));
        }
        return scaled;
    }

    @Test
    public void mixedMarkersGroupIdenticallyWhateverOrderTheyArriveIn() {
        // Same crowded field, many different input orders, mixed categories.
        // The answer must be the marker IDs, never the list positions.
        List<LostTalesMapMarkerGrouping.Entry> entries = crowdedField();
        Map<String, String> expected = LostTalesMapMarkerGrouping.group(
                entries, fresh()).getMembership();
        assertTrue("the field should actually produce stacks",
                expected.size() > 0);

        for (int rotation = 1; rotation < entries.size(); rotation++) {
            List<LostTalesMapMarkerGrouping.Entry> rotated =
                    new ArrayList<LostTalesMapMarkerGrouping.Entry>(
                            entries.subList(rotation, entries.size()));
            rotated.addAll(entries.subList(0, rotation));
            assertEquals("rotation " + rotation, expected,
                    LostTalesMapMarkerGrouping.group(rotated, fresh())
                            .getMembership());
        }
        List<LostTalesMapMarkerGrouping.Entry> reversed =
                new ArrayList<LostTalesMapMarkerGrouping.Entry>(entries);
        Collections.reverse(reversed);
        assertEquals(expected, LostTalesMapMarkerGrouping.group(
                reversed, fresh()).getMembership());
    }

    @Test
    public void aLargeFieldStillProducesOneEntryPerMarker() {
        List<LostTalesMapMarkerGrouping.Entry> entries =
                new ArrayList<LostTalesMapMarkerGrouping.Entry>();
        for (int index = 0; index < 400; index++) {
            // A tight lattice, so most markers really do have neighbours.
            entries.add(icon("losttales:m" + index, "M" + index,
                    400 - index,
                    index % 20 * 9.0F, index / 20 * 9.0F));
        }
        LostTalesMapMarkerGrouping.Result result =
                LostTalesMapMarkerGrouping.group(entries, fresh());

        int members = 0;
        for (LostTalesMapMarkerGrouping.Group group : result.getGroups()) {
            members += group.size();
        }
        assertEquals("every marker belongs to exactly one stack",
                entries.size(), members);
        assertEquals(result.getGroups().size(),
                LostTalesMapMarkerGrouping.groupsBottomToTop(
                        entries, result.getGroups()).size());
    }

    /** Overlapping markers of several categories at mixed relevance. */
    private static List<LostTalesMapMarkerGrouping.Entry> crowdedField() {
        return Arrays.asList(
                icon("losttales:town", "Town", 50, LOCATION, 0.0F, 0.0F),
                icon("losttales:camp", "Camp", 45, LOCATION, 5.0F, 2.0F),
                icon("losttales:cave", "Cave", 40, LOCATION, 9.0F, 0.0F),
                icon("losttales:fort", "Fort", 35, LOCATION, 14.0F, 3.0F),
                icon("losttales:quest", "Quest", 60, QUEST, 4.0F, 1.0F),
                icon("losttales:gohere", "Go Here", 30, PARTY,
                        6.0F, 1.0F),
                icon("losttales:party2", "Rally", 25, PARTY, 8.0F, 2.0F),
                icon("losttales:far", "Far", 20, LOCATION, 90.0F, 90.0F));
    }

    @Test
    public void markersOnlyMergeOnceTheyActuallyOverlap() {
        // Grouping reads the layout on screen, not a zoom the map is heading
        // for. Anticipating the next zoom level merged markers while they
        // were still visibly apart, and a stack never gives a member back on
        // the way out, so the premature merge stuck.
        assertEquals(2, group(fresh(),
                icon("losttales:a", "A", 10, 0.0F, 0.0F),
                icon("losttales:b", "B", 5, 11.0F, 0.0F))
                .getGroups().size());
        // The same pair as one wheel step of look-ahead would have placed
        // them: 16% closer. That must not be enough to merge them either.
        assertEquals(2, group(fresh(),
                icon("losttales:a", "A", 10, 0.0F, 0.0F),
                icon("losttales:b", "B", 5, 13.0F, 0.0F))
                .getGroups().size());
    }

    @Test
    public void oneZoomStepSplitsEveryAffectedGroupTogether() {
        // Two independent pairs, each just inside the join threshold. The
        // settled layout is what decides, so both pairs answer the same wheel
        // step in the same recalculation rather than one after the other.
        Map<String, String> grouped = group(fresh(),
                icon("losttales:a", "A", 40, 0.0F, 0.0F),
                icon("losttales:b", "B", 30, 10.0F, 0.0F),
                icon("losttales:c", "C", 20, 200.0F, 0.0F),
                icon("losttales:d", "D", 10, 210.0F, 0.0F))
                .getMembership();
        assertEquals(4, grouped.size());

        LostTalesMapMarkerGrouping.Result settled = group(grouped,
                icon("losttales:a", "A", 40, 0.0F, 0.0F),
                icon("losttales:b", "B", 30, 12.0F, 0.0F),
                icon("losttales:c", "C", 20, 200.0F, 0.0F),
                icon("losttales:d", "D", 10, 212.0F, 0.0F));

        assertEquals(describe(settled), 4, settled.getGroups().size());
        assertEquals(0, settled.getMembership().size());
    }

    @Test
    public void aNeighbourTakesAMemberOnlyByTakingTheWholeStack() {
        // M is already B's companion, and it has drifted onto A. A cannot
        // pick M out of the stack it is in: either the whole stack comes, or
        // none of it does. Handing single members between stacks is what made
        // one marker merge while its partner was left behind.
        LostTalesMapMarkerGrouping.Result result = group(
                stackedAs("losttales:b", "losttales:m"),
                icon("losttales:a", "A", 30, 0.0F, 0.0F),
                icon("losttales:b", "B", 20, 16.0F, 0.0F),
                icon("losttales:m", "M", 1, 7.0F, 0.0F));

        assertEquals("B and M were separated",
                result.getMembership().get("losttales:b"),
                result.getMembership().get("losttales:m"));
        // A is nowhere near B, so the stack stays where it is rather than
        // being dragged across by the one member that reaches A.
        assertEquals(describe(result), 2, result.getGroups().size());
        assertNull(result.getMembership().get("losttales:a"));
    }

    @Test
    public void aWideStackReachesNoFurtherThanTheMarkerLeadingIt() {
        // A stack that has absorbed several others covers the map wherever
        // its members really are, but it only draws one icon and a short fan.
        // Letting it group by everything it contains had stacks that were
        // plainly far apart swallowing one another.
        LostTalesMapMarkerGrouping.Result result = group(
                chainedFrom("losttales:a", "losttales:a2", "losttales:a3"),
                icon("losttales:a", "A", 50, 0.0F, 0.0F),
                icon("losttales:a2", "A2", 45, 9.0F, 0.0F),
                icon("losttales:a3", "A3", 40, 18.0F, 0.0F),
                // Sitting on the stack's outermost member, but a full stack's
                // width away from the marker the stack is drawn at.
                icon("losttales:far", "Far", 35, 26.0F, 0.0F));

        assertEquals(describe(result), 2, result.getGroups().size());
        assertNull("a distant marker joined across the stack's members",
                result.getMembership().get("losttales:far"));
    }

    @Test
    public void aMemberLeavesOnceItsOwnGroupReleasesIt() {
        // The same pairing, but now B really has let go, so M is free to join
        // whichever stack it is actually sitting on.
        Map<String, String> previous = new HashMap<String, String>();
        previous.put("losttales:b", "losttales:b");
        previous.put("losttales:m", "losttales:b");

        LostTalesMapMarkerGrouping.Result result = group(previous,
                icon("losttales:a", "A", 30, 0.0F, 0.0F),
                icon("losttales:b", "B", 20, 40.0F, 0.0F),
                icon("losttales:m", "M", 1, 3.0F, 0.0F));

        assertEquals(describe(result), "losttales:a",
                result.getMembership().get("losttales:m"));
    }

    @Test
    public void membershipDoesNotOscillateAcrossRepeatedFrames() {
        // Drive the same layout through many frames, feeding each result back
        // in as history. It must reach one answer and stay there.
        Map<String, String> membership = fresh();
        String settled = null;
        for (int frame = 0; frame < 12; frame++) {
            LostTalesMapMarkerGrouping.Result result = group(membership,
                    icon("losttales:a", "A", 30, 0.0F, 0.0F),
                    icon("losttales:b", "B", 20, 13.0F, 0.0F),
                    icon("losttales:m", "M", 1, 6.5F, 0.0F));
            membership = result.getMembership();
            String current = describe(result);
            if (frame >= 2) {
                assertEquals("membership oscillated at frame " + frame,
                        settled, current);
            }
            settled = current;
        }
    }

    @Test
    public void aReleasedCompanionLeavesFromWhereItWasDrawn() {
        // The reported regression: a visible fan member dropped back onto the
        // leader before travelling out, because its exit was rebuilt from its
        // membership instead of from the position it was drawn at.
        LostTalesLotrMapMarkerIconOverlay.MarkerAnimationState state =
                settledCompanion(-1.0F);
        state.recordRendered(-8.0F, -3.0F);

        LostTalesLotrMapMarkerIconOverlay.stepMarkerAnimation(
                state, true, 0.0F, 0.25F);

        assertTrue(state.isLeavingStack());
        assertEquals(-8.0F, state.getExitOffsetX(), 0.0001F);
        assertEquals(-3.0F, state.getExitOffsetY(), 0.0001F);
        // Opacity and size follow these two, so holding them is what keeps
        // the sprite from restarting at nothing.
        assertEquals(-1.0F, state.getCompanionSlot(), 0.0001F);
        assertEquals(1.0F, state.getFanStrength(), 0.0001F);
    }

    @Test
    public void aReleasedCompanionTravelsStraightToItsOwnPosition() {
        LostTalesLotrMapMarkerIconOverlay.MarkerAnimationState state =
                settledCompanion(1.0F);
        state.recordRendered(9.0F, -4.0F);

        float previousDistance = Float.MAX_VALUE;
        for (int frame = 0; frame < 12; frame++) {
            LostTalesLotrMapMarkerIconOverlay.stepMarkerAnimation(
                    state, true, 0.0F, 0.25F);
            float travelled = 1.0F - state.getVisibility();
            float[] point = transition(100.0F + 9.0F, 100.0F,
                    100.0F, 100.0F, state.getVisibility());
            assertTrue("the marker overshot its own position",
                    point[0] >= 100.0F - 0.0001F
                            && point[0] <= 109.0F + 0.0001F);
            float distance = Math.abs(point[0] - 100.0F);
            assertTrue("the marker moved away from its destination",
                    distance <= previousDistance + 0.0001F);
            previousDistance = distance;
            if (travelled <= 0.0F) {
                break;
            }
        }
        assertEquals(1.0F, state.getVisibility(), 0.0001F);
        assertFalse("the hold must be released once it arrives",
                state.isLeavingStack());
    }

    @Test
    public void anInterruptedTransitionResumesFromTheCurrentState() {
        // Released, half way out, then pulled back into a stack and released
        // again: the second exit must start from where it actually is.
        LostTalesLotrMapMarkerIconOverlay.MarkerAnimationState state =
                settledCompanion(-1.0F);
        state.recordRendered(-8.0F, 0.0F);
        LostTalesLotrMapMarkerIconOverlay.stepMarkerAnimation(
                state, true, 0.0F, 0.5F);
        assertEquals(0.5F, state.getVisibility(), 0.0001F);

        LostTalesLotrMapMarkerIconOverlay.stepMarkerAnimation(
                state, false, -1.0F, 0.25F);
        assertFalse(state.isLeavingStack());
        state.recordRendered(-6.0F, 0.0F);

        LostTalesLotrMapMarkerIconOverlay.stepMarkerAnimation(
                state, true, 0.0F, 0.25F);
        assertTrue(state.isLeavingStack());
        assertEquals("the exit restarted from the old fan position",
                -6.0F, state.getExitOffsetX(), 0.0001F);
    }

    @Test
    public void theSummaryLabelOnlyCountsWhatTheFanCannotDraw() {
        // A stack draws its leader and two companions, so two- and
        // three-marker stacks have nothing left to summarise.
        assertEquals(0, stackOf(2).getCondensedMemberCount());
        assertEquals(0, stackOf(3).getCondensedMemberCount());
        assertEquals(1, stackOf(4).getCondensedMemberCount());
        assertEquals(17, stackOf(20).getCondensedMemberCount());
    }

    @Test
    public void theSummaryLabelHoldsWhileTheMarkersItCountsAreHidden() {
        // Fully hidden members: the label is at full strength.
        assertEquals(1.0F, LostTalesLotrMapMarkerIconOverlay
                .condensedLabelFade(0.0F), 0.0001F);
        // As they emerge it gives way, and is gone once they stand alone.
        assertEquals(0.5F, LostTalesLotrMapMarkerIconOverlay
                .condensedLabelFade(0.5F), 0.0001F);
        assertEquals(0.0F, LostTalesLotrMapMarkerIconOverlay
                .condensedLabelFade(1.0F), 0.0001F);
        assertEquals(0.0F, LostTalesLotrMapMarkerIconOverlay
                .condensedLabelFade(9.0F), 0.0001F);
    }

    /** A stack of the given size, all markers on the same spot. */
    private static LostTalesMapMarkerGrouping.Group stackOf(int size) {
        List<LostTalesMapMarkerGrouping.Entry> entries =
                new ArrayList<LostTalesMapMarkerGrouping.Entry>();
        for (int index = 0; index < size; index++) {
            entries.add(icon("losttales:m" + index, "M" + index,
                    size - index, 0.0F, 0.0F));
        }
        LostTalesMapMarkerGrouping.Result result =
                LostTalesMapMarkerGrouping.group(entries, fresh());
        assertEquals(1, result.getGroups().size());
        assertEquals(size, result.getGroups().get(0).size());
        return result.getGroups().get(0);
    }

    @Test
    public void aMarkerStandingAloneIsDrawnAtFullOpacity() {
        // A marker that is not in a stack must not keep a companion's dimming
        // or offset. Half-faded markers that were not part of any fan were
        // exactly the leftover this guards against.
        LostTalesLotrMapMarkerIconOverlay.MarkerAnimationState state =
                settledCompanion(-1.0F);
        state.recordRendered(-8.0F, 0.0F);

        for (int frame = 0; frame < 12; frame++) {
            LostTalesLotrMapMarkerIconOverlay.stepMarkerAnimation(
                    state, true, 0.0F, 0.25F);
        }

        assertEquals(1.0F, state.getVisibility(), 0.0001F);
        assertEquals(0.0F, state.getFanStrength(), 0.0001F);
        assertEquals(0.0F, state.getCompanionSlot(), 0.0001F);
        assertFalse(state.isLeavingStack());
        assertEquals("a lone marker was still dimmed", 1.0F,
                LostTalesLotrMapMarkerIconOverlay.groupedIconAlpha(
                        state.getVisibility(), state.getFanStrength()),
                0.0001F);
        assertEquals("a lone marker was still shrunk", 1.0F,
                LostTalesLotrMapMarkerIconOverlay.groupedIconScale(
                        state.getVisibility(), state.getFanStrength()),
                0.0001F);
    }

    @Test
    public void aMarkerThatNeverJoinedAStackIsNeverDimmed() {
        LostTalesLotrMapMarkerIconOverlay.MarkerAnimationState state =
                LostTalesLotrMapMarkerIconOverlay.MarkerAnimationState
                        .settled(true, 0.0F);
        for (int frame = 0; frame < 6; frame++) {
            LostTalesLotrMapMarkerIconOverlay.stepMarkerAnimation(
                    state, true, 0.0F, 0.25F);
            assertEquals(1.0F,
                    LostTalesLotrMapMarkerIconOverlay.groupedIconAlpha(
                            state.getVisibility(), state.getFanStrength()),
                    0.0001F);
        }
    }

    @Test
    public void aMemberThatWasNeverDrawnLeavesFromItsOwnPosition() {
        LostTalesLotrMapMarkerIconOverlay.MarkerAnimationState state =
                LostTalesLotrMapMarkerIconOverlay.MarkerAnimationState
                        .settled(false, 0.0F);

        LostTalesLotrMapMarkerIconOverlay.stepMarkerAnimation(
                state, true, 0.0F, 0.25F);

        assertEquals(0.0F, state.getExitOffsetX(), 0.0001F);
        assertEquals(0.0F, state.getExitOffsetY(), 0.0001F);
    }

    /** A companion fully gathered on its leader, on the given fan side. */
    private static LostTalesLotrMapMarkerIconOverlay.MarkerAnimationState
            settledCompanion(float side) {
        LostTalesLotrMapMarkerIconOverlay.MarkerAnimationState state =
                LostTalesLotrMapMarkerIconOverlay.MarkerAnimationState
                        .settled(false, side);
        for (int frame = 0; frame < 20; frame++) {
            LostTalesLotrMapMarkerIconOverlay.stepMarkerAnimation(
                    state, false, side, 0.25F);
        }
        assertEquals(0.0F, state.getVisibility(), 0.0001F);
        assertEquals(side, state.getCompanionSlot(), 0.0001F);
        assertEquals(1.0F, state.getFanStrength(), 0.0001F);
        return state;
    }

    private static Map<String, String> fresh() {
        return Collections.emptyMap();
    }

    /** A stack whose members all came to rest on the marker leading it. */
    private static Map<String, String> stackedAs(
            String leaderId, String... memberIds) {
        Map<String, String> links = new HashMap<String, String>();
        links.put(leaderId, leaderId);
        for (String memberId : memberIds) {
            links.put(memberId, leaderId);
        }
        return links;
    }

    /**
     * A stack built the way merging really builds one: each marker came to
     * rest on the one before it, so the links form a chain rather than a star.
     */
    private static Map<String, String> chainedFrom(
            String leaderId, String... memberIds) {
        Map<String, String> links = new HashMap<String, String>();
        links.put(leaderId, leaderId);
        String previous = leaderId;
        for (String memberId : memberIds) {
            links.put(memberId, previous);
            previous = memberId;
        }
        return links;
    }

    private static LostTalesMapMarkerGrouping.Result group(
            Map<String, String> previousMembership,
            LostTalesMapMarkerGrouping.Entry... entries) {
        return LostTalesMapMarkerGrouping.group(
                Arrays.asList(entries), previousMembership);
    }

    /** Builds the exact bounds the overlay renders a marker icon at. */
    private static LostTalesMapMarkerGrouping.Entry icon(
            String id, String name, int relevanceRank,
            float centerX, float centerY) {
        return icon(id, name, relevanceRank,
                LostTalesMapMarkerGrouping.GroupingCategory.LOCATION,
                centerX, centerY);
    }

    private static LostTalesMapMarkerGrouping.Entry icon(
            String id, String name, int relevanceRank,
            LostTalesMapMarkerGrouping.GroupingCategory category,
            float centerX, float centerY) {
        return new LostTalesMapMarkerGrouping.Entry(
                id, name, relevanceRank, category,
                LostTalesMapMarkerRenderedGeometry.artLeft(
                        centerX, ICON_HALF_EXTENT),
                LostTalesMapMarkerRenderedGeometry.artTop(
                        centerY, ICON_HALF_EXTENT),
                LostTalesMapMarkerRenderedGeometry.artRight(
                        centerX, ICON_HALF_EXTENT),
                LostTalesMapMarkerRenderedGeometry.artBottom(
                        centerY, ICON_HALF_EXTENT));
    }

    private static String describe(
            LostTalesMapMarkerGrouping.Result result) {
        List<String> groups = new ArrayList<String>();
        for (LostTalesMapMarkerGrouping.Group group : result.getGroups()) {
            groups.add(group.getRepresentativeIndex()
                    + "=" + group.getMemberIndices());
        }
        return groups.toString();
    }
}
