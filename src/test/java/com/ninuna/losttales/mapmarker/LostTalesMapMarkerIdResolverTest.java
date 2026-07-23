package com.ninuna.losttales.mapmarker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class LostTalesMapMarkerIdResolverTest {
    @Test
    public void derivesNativeWaypointFromNamespacedMarkerId() {
        assertEquals("OATBARTON",
                LostTalesMapMarkerIdResolver.resolveLotrWaypointId(
                        "lotr:waypoint:oatbarton"));
    }

    @Test
    public void unrelatedAndMalformedIdsDoNotCreateAssociations() {
        assertEquals("",
                LostTalesMapMarkerIdResolver.resolveLotrWaypointId(
                        "losttales:custom/oatbarton"));
        assertEquals("",
                LostTalesMapMarkerIdResolver.resolveLotrWaypointId(
                        "lotr:waypoint:not/valid"));
    }
}
