package com.ninuna.losttales.mapmarker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesMapMarkerIdentityTest {
    @Test
    public void nativeLotrWaypointCodesUseOneCanonicalIdentity() {
        LostTalesMapMarkerIdentity world =
                LostTalesMapMarkerIdentity.create("lotr:waypoint:HOBBITON");
        LostTalesMapMarkerIdentity quest =
                LostTalesMapMarkerIdentity.create("LOTR:WAYPOINT:hobbiton");

        assertTrue(world.isSameLogicalMarker(quest));
        assertEquals(world, quest);
        assertEquals("lotr:waypoint:hobbiton",
                world.getCanonicalKey());
    }

    @Test
    public void nonLotrIdsRetainExistingCaseSensitiveIdentity() {
        LostTalesMapMarkerIdentity first =
                LostTalesMapMarkerIdentity.create("losttales:Town");
        LostTalesMapMarkerIdentity second =
                LostTalesMapMarkerIdentity.create("losttales:town");

        assertFalse(first.isSameLogicalMarker(second));
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankMarkerIdIsRejected() {
        LostTalesMapMarkerIdentity.create("  ");
    }
}
