package com.ninuna.losttales.client.mapmarker;

import java.util.Arrays;
import java.util.Collections;
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
                        entry("losttales:b", "B", 0, 2.5F, 12.5F)),
                        Collections.<String, String>emptyMap());
        LostTalesMapMarkerGrouping.Result retained =
                LostTalesMapMarkerGrouping.group(Arrays.asList(
                        entry("losttales:a", "A", 0, 0, 10),
                        entry("losttales:b", "B", 0, 3.0F, 13.0F)),
                        merged.getMembership());
        LostTalesMapMarkerGrouping.Result split =
                LostTalesMapMarkerGrouping.group(Arrays.asList(
                        entry("losttales:a", "A", 0, 0, 10),
                        entry("losttales:b", "B", 0, 3.5F, 13.5F)),
                        retained.getMembership());

        assertEquals(1, merged.getGroups().size());
        assertEquals(1, retained.getGroups().size());
        assertEquals(2, split.getGroups().size());
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
        return new LostTalesMapMarkerGrouping.Entry(
                id, name, relevanceRank,
                left, 0.0F, right, 10.0F);
    }
}
