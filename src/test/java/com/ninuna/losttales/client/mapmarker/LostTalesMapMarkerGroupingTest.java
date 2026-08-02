package com.ninuna.losttales.client.mapmarker;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class LostTalesMapMarkerGroupingTest {
    @Test
    public void overlapUsesHigherRelevanceRepresentative() {
        LostTalesMapMarkerGrouping.Result result =
                LostTalesMapMarkerGrouping.group(Arrays.asList(
                        entry("losttales:low", "Low", 1, 0, 10),
                        entry("losttales:high", "High", 10, 2.5F, 12.5F)),
                        Collections.<String, String>emptyMap());

        assertEquals(1, result.getGroups().size());
        assertEquals(1, result.getGroups().get(0)
                .getRepresentativeIndex());
        assertEquals("+1 more", result.getGroups().get(0)
                .getAdditionalLabel());
    }

    @Test
    public void equalRelevanceUsesNameThenStableId() {
        LostTalesMapMarkerGrouping.Result result =
                LostTalesMapMarkerGrouping.group(Arrays.asList(
                        entry("losttales:z", "Bree", 0, 0, 10),
                        entry("losttales:b", "Aldburg", 0, 0, 10),
                        entry("losttales:a", "Aldburg", 0, 0, 10)),
                        Collections.<String, String>emptyMap());

        assertEquals(2, result.getGroups().get(0)
                .getRepresentativeIndex());
        assertEquals("+2 more", result.getGroups().get(0)
                .getAdditionalLabel());
    }

    @Test
    public void splitThresholdPreventsBoundaryFlicker() {
        LostTalesMapMarkerGrouping.Result merged =
                LostTalesMapMarkerGrouping.group(Arrays.asList(
                        entry("losttales:a", "A", 0, 0, 10),
                        entry("losttales:b", "B", 0, 4.8F, 14.8F)),
                        Collections.<String, String>emptyMap());
        LostTalesMapMarkerGrouping.Result retained =
                LostTalesMapMarkerGrouping.group(Arrays.asList(
                        entry("losttales:a", "A", 0, 0, 10),
                        entry("losttales:b", "B", 0, 5.5F, 15.5F)),
                        merged.getMembership());
        LostTalesMapMarkerGrouping.Result split =
                LostTalesMapMarkerGrouping.group(Arrays.asList(
                        entry("losttales:a", "A", 0, 0, 10),
                        entry("losttales:b", "B", 0, 5.71F, 15.71F)),
                        retained.getMembership());

        assertEquals(1, merged.getGroups().size());
        assertEquals(1, retained.getGroups().size());
        assertEquals(2, split.getGroups().size());
    }

    @Test
    public void horizontalArtworkUsesItsWiderVisibleFootprint() {
        LostTalesMapMarkerGrouping.Result correctedOverlap =
                LostTalesMapMarkerGrouping.group(Arrays.asList(
                        entry("losttales:a", "A", 0, 0, 10),
                        entry("losttales:b", "B", 0, 4.8F, 14.8F)),
                        Collections.<String, String>emptyMap());
        LostTalesMapMarkerGrouping.Result slightlyLessOverlap =
                LostTalesMapMarkerGrouping.group(Arrays.asList(
                        entry("losttales:a", "A", 0, 0, 10),
                        entry("losttales:b", "B", 0, 4.81F, 14.81F)),
                        Collections.<String, String>emptyMap());

        assertEquals(1, correctedOverlap.getGroups().size());
        assertEquals(2, slightlyLessOverlap.getGroups().size());
    }

    @Test
    public void verticalGroupingThresholdRemainsUnchanged() {
        LostTalesMapMarkerGrouping.Result sixtyPercentOverlap =
                LostTalesMapMarkerGrouping.group(Arrays.asList(
                        entryBounds("losttales:a", "A", 0,
                                0, 0, 10, 10),
                        entryBounds("losttales:b", "B", 0,
                                0, 4.0F, 10, 14.0F)),
                        Collections.<String, String>emptyMap());
        LostTalesMapMarkerGrouping.Result slightlyLessOverlap =
                LostTalesMapMarkerGrouping.group(Arrays.asList(
                        entryBounds("losttales:a", "A", 0,
                                0, 0, 10, 10),
                        entryBounds("losttales:b", "B", 0,
                                0, 4.01F, 10, 14.01F)),
                        Collections.<String, String>emptyMap());

        assertEquals(1, sixtyPercentOverlap.getGroups().size());
        assertEquals(2, slightlyLessOverlap.getGroups().size());
    }

    @Test
    public void zoomScaleGroupsMoreFarAwayAndLessWhenClose() {
        java.util.List<LostTalesMapMarkerGrouping.Entry> entries =
                Arrays.asList(
                        entry("losttales:a", "A", 0, 0, 10),
                        entry("losttales:b", "B", 0, 4.5F, 14.5F));

        assertEquals(1, LostTalesMapMarkerGrouping.group(
                entries, Collections.<String, String>emptyMap(),
                0.50F, false).getGroups().size());
        assertEquals(2, LostTalesMapMarkerGrouping.group(
                entries, Collections.<String, String>emptyMap(),
                0.30F, false).getGroups().size());
        assertEquals(0.50F,
                LostTalesLotrMapMarkerIconOverlay
                        .groupingRadiusScaleForZoom(-2.0F), 0.0F);
        assertEquals(0.40F,
                LostTalesLotrMapMarkerIconOverlay
                        .groupingRadiusScaleForZoom(1.0F), 0.0001F);
        assertEquals(0.30F,
                LostTalesLotrMapMarkerIconOverlay
                        .groupingRadiusScaleForZoom(3.0F), 0.0F);
        assertEquals(0.0F,
                LostTalesLotrMapMarkerIconOverlay
                        .groupingRadiusScaleForZoom(4.0F), 0.0F);
    }

    @Test
    public void zoomingOutKeepsExistingGroupsAtomic() {
        java.util.List<LostTalesMapMarkerGrouping.Entry> groupedEntries =
                Arrays.asList(
                        entry("losttales:a", "A", 10, 0, 10),
                        entry("losttales:b", "B", 5, 4.0F, 14.0F));
        LostTalesMapMarkerGrouping.Result initial =
                LostTalesMapMarkerGrouping.group(groupedEntries,
                        Collections.<String, String>emptyMap());
        java.util.List<LostTalesMapMarkerGrouping.Entry> separatedEntries =
                Arrays.asList(
                        entry("losttales:a", "A", 10, 0, 10),
                        entry("losttales:b", "B", 5, 8.0F, 18.0F));

        assertEquals(1, LostTalesMapMarkerGrouping.group(
                separatedEntries, initial.getMembership(),
                0.40F, true).getGroups().size());
        assertEquals(2, LostTalesMapMarkerGrouping.group(
                separatedEntries, initial.getMembership(),
                0.40F, false).getGroups().size());
    }

    @Test
    public void zoomingInSplitsWithoutReassigningAnotherGroup() {
        Map<String, String> previous = new HashMap<String, String>();
        previous.put("losttales:a", "losttales:a");
        previous.put("losttales:b", "losttales:a");

        LostTalesMapMarkerGrouping.Result result =
                LostTalesMapMarkerGrouping.group(Arrays.asList(
                        entry("losttales:a", "A", 30, 0.0F, 10.0F),
                        entry("losttales:b", "B", 10, 6.0F, 16.0F),
                        entry("losttales:c", "C", 20, 6.0F, 16.0F)),
                        previous, 0.40F, false);

        assertEquals(3, result.getGroups().size());
        assertEquals(0, result.getMembership().size());
    }

    @Test
    public void zoomingInPartitionsALargeStackIntoChildStacks() {
        Map<String, String> previous = new HashMap<String, String>();
        previous.put("losttales:a", "losttales:a");
        previous.put("losttales:b", "losttales:a");
        previous.put("losttales:c", "losttales:a");
        previous.put("losttales:d", "losttales:a");

        LostTalesMapMarkerGrouping.Result result =
                LostTalesMapMarkerGrouping.group(Arrays.asList(
                        entry("losttales:a", "A", 40, 0.0F, 10.0F),
                        entry("losttales:b", "B", 30, 5.0F, 15.0F),
                        entry("losttales:c", "C", 20, 20.0F, 30.0F),
                        entry("losttales:d", "D", 10, 25.0F, 35.0F)),
                        previous, 0.40F, false);

        assertEquals(2, result.getGroups().size());
        assertEquals(2, result.getGroups().get(0).size());
        assertEquals(2, result.getGroups().get(1).size());
        assertEquals("losttales:a",
                result.getMembership().get("losttales:b"));
        assertEquals("losttales:c",
                result.getMembership().get("losttales:d"));
    }

    @Test
    public void farZoomKeepsFanCompanionsNearlyAsVisibleAsMainMarker() {
        assertEquals(0.72F,
                LostTalesLotrMapMarkerIconOverlay.groupedIconAlpha(
                        0.0F, 1.0F, 1.0F), 0.0001F);
        assertEquals(0.96192F,
                LostTalesLotrMapMarkerIconOverlay.groupedIconAlpha(
                        0.0F, 1.0F, 0.136F), 0.0001F);
        assertEquals(0.0F,
                LostTalesLotrMapMarkerIconOverlay.groupedIconAlpha(
                        0.0F, 0.0F, 0.136F), 0.0001F);
    }

    @Test
    public void finalZoomStepsFreezeGroupingDecisionSpacing() {
        assertEquals(1.0F,
                LostTalesLotrMapMarkerIconOverlay
                        .groupingDecisionScaleForZoom(-2.0F), 0.0F);
        assertEquals(2.0F,
                LostTalesLotrMapMarkerIconOverlay
                        .groupingDecisionScaleForZoom(-3.0F), 0.0001F);

        java.util.List<LostTalesMapMarkerGrouping.Entry> atThreshold =
                Arrays.asList(
                        entry("losttales:a", "A", 10, 0.0F, 10.0F),
                        entry("losttales:b", "B", 5, 6.1F, 16.1F));
        java.util.List<LostTalesMapMarkerGrouping.Entry> atMinimum =
                Arrays.asList(
                        entry("losttales:a", "A", 10, -2.5F, 7.5F)
                                .withScaledSpacing(2.0F),
                        entry("losttales:b", "B", 5, 0.55F, 10.55F)
                                .withScaledSpacing(2.0F));

        assertEquals(
                LostTalesMapMarkerGrouping.group(
                        atThreshold,
                        Collections.<String, String>emptyMap(),
                        0.50F, false).getGroups().size(),
                LostTalesMapMarkerGrouping.group(
                        atMinimum,
                        Collections.<String, String>emptyMap(),
                        0.50F, false).getGroups().size());
    }

    @Test
    public void zoomOutOverlapUsesTheVisibleStackFootprint() {
        Map<String, String> previous = new HashMap<String, String>();
        previous.put("losttales:a", "losttales:a");
        previous.put("losttales:b", "losttales:a");

        LostTalesMapMarkerGrouping.Result result =
                LostTalesMapMarkerGrouping.group(Arrays.asList(
                        entry("losttales:a", "A", 30, 0.0F, 10.0F),
                        entry("losttales:b", "B", 20, 100.0F, 110.0F),
                        entry("losttales:c", "C", 10, -7.0F, 3.0F)),
                        previous, 0.40F, true);

        assertEquals(1, result.getGroups().size());
        assertEquals(3, result.getGroups().get(0).size());
    }

    @Test
    public void groupedMembersNoLongerOverlapAtTheirOldPositions() {
        Map<String, String> previous = new HashMap<String, String>();
        previous.put("losttales:a", "losttales:a");
        previous.put("losttales:b", "losttales:a");

        LostTalesMapMarkerGrouping.Result result =
                LostTalesMapMarkerGrouping.group(Arrays.asList(
                        entry("losttales:a", "A", 30, 0.0F, 10.0F),
                        entry("losttales:b", "B", 20, 100.0F, 110.0F),
                        entry("losttales:c", "C", 10, 100.0F, 110.0F)),
                        previous, 0.40F, true);

        assertEquals(2, result.getGroups().size());
        assertEquals(2, result.getGroups().get(0).size());
        assertEquals(1, result.getGroups().get(1).size());
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
                        entry("losttales:highest", "Highest", 30, 0, 10),
                        entry("losttales:low", "Low", 1, 100, 110),
                        entry("losttales:second", "Second", 20, 0, 10),
                        entry("losttales:third", "Third", 10, 100, 110)),
                        previous, 0.40F, true);
        LostTalesMapMarkerGrouping.Group group =
                result.getGroups().get(0);

        assertEquals(1, result.getGroups().size());
        assertEquals(-1, group.getCompanionSide(2));
        assertEquals(1, group.getCompanionSide(3));
        assertEquals(0, group.getCompanionSide(1));
    }

    @Test
    public void groupCompanionsUseTheNextTwoMostRelevantMarkers() {
        LostTalesMapMarkerGrouping.Result result =
                LostTalesMapMarkerGrouping.group(Arrays.asList(
                        entry("losttales:low", "Low", 1, 0, 10),
                        entry("losttales:highest", "Highest", 30, 0, 10),
                        entry("losttales:second", "Second", 20, 0, 10),
                        entry("losttales:third", "Third", 10, 0, 10)),
                        Collections.<String, String>emptyMap());
        LostTalesMapMarkerGrouping.Group group =
                result.getGroups().get(0);

        assertEquals(1, group.getRepresentativeIndex());
        assertEquals(-1, group.getCompanionSide(2));
        assertEquals(1, group.getCompanionSide(3));
        assertEquals(0, group.getCompanionSide(0));
    }

    @Test
    public void overlapChainsDoNotHideMarkersFarFromRepresentative() {
        LostTalesMapMarkerGrouping.Result result =
                LostTalesMapMarkerGrouping.group(Arrays.asList(
                        entry("losttales:a", "A", 10, 0, 10),
                        entry("losttales:b", "B", 5, 2.5F, 12.5F),
                        entry("losttales:c", "C", 0, 5, 15)),
                        Collections.<String, String>emptyMap());

        assertEquals(2, result.getGroups().size());
        assertEquals(2, result.getGroups().get(0).size());
        assertEquals(1, result.getGroups().get(1).size());
    }

    @Test
    public void closeZoomCanForceEveryMarkerToRemainVisible() {
        LostTalesMapMarkerGrouping.Result result =
                LostTalesMapMarkerGrouping.ungroup(Arrays.asList(
                        entry("losttales:a", "A", 10, 0, 10),
                        entry("losttales:b", "B", 5, 0, 10)));

        assertEquals(2, result.getGroups().size());
        assertEquals(1, result.getGroups().get(0).size());
        assertEquals(1, result.getGroups().get(1).size());
    }

    @Test
    public void renderOrderLeavesRelevanceAndAlphabeticalWinnerOnTop() {
        java.util.List<LostTalesMapMarkerGrouping.Entry> entries =
                Arrays.asList(
                        entry("losttales:low", "Low", 0, 0, 10),
                        entry("losttales:b", "Bree", 10, 0, 10),
                        entry("losttales:a", "Aldburg", 10, 0, 10));

        assertEquals(Arrays.asList(
                        Integer.valueOf(0),
                        Integer.valueOf(1),
                        Integer.valueOf(2)),
                LostTalesMapMarkerGrouping.bottomToTop(entries));
    }

    @Test
    public void visibilitySynchronizesGroupTravelWithFade() {
        assertEquals(20.0F,
                LostTalesMapMarkerGrouping.transitionCoordinate(
                        20.0F, 100.0F, 0.0F), 0.0F);
        assertEquals(60.0F,
                LostTalesMapMarkerGrouping.transitionCoordinate(
                        20.0F, 100.0F, 0.5F), 0.0F);
        assertEquals(100.0F,
                LostTalesMapMarkerGrouping.transitionCoordinate(
                        20.0F, 100.0F, 1.0F), 0.0F);
        assertEquals(20.0F,
                LostTalesMapMarkerGrouping.transitionCoordinate(
                        20.0F, 100.0F, -1.0F), 0.0F);
        assertEquals(100.0F,
                LostTalesMapMarkerGrouping.transitionCoordinate(
                        20.0F, 100.0F, 2.0F), 0.0F);
    }

    private static LostTalesMapMarkerGrouping.Entry entry(
            String id, String name, int relevanceRank,
            float left, float right) {
        return entryBounds(id, name, relevanceRank,
                left, 0.0F, right, 10.0F);
    }

    private static LostTalesMapMarkerGrouping.Entry entryBounds(
            String id, String name, int relevanceRank,
            float left, float top, float right, float bottom) {
        return new LostTalesMapMarkerGrouping.Entry(
                id, name, relevanceRank, left, top, right, bottom);
    }
}
