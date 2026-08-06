package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class LostTalesMapMarkerRenderedGeometryTest {
    @Test
    public void iconBoundsIncludeTheRenderedShadow() {
        Object gui = new Object();
        LostTalesMapMarkerRenderedGeometry.Frame frame = frame(
                gui, 2.25F, 3, 1);
        LostTalesMapMarkerRenderedGeometry.RenderedObject object =
                frame.beginObject("losttales:a", 0, 1);
        frame.addMember(object, 0, "losttales:a",
                100.0F, 50.0F, 100.0F, 50.0F,
                6.5F, 6.5F, 6.5F, 1.0F, 1.0F, 1.0F,
                0.0F, 0.0F);
        frame.finishObject(object);

        assertSame(gui, frame.getGuiIdentity());
        assertEquals(2.25F, frame.getZoomExp(), 0.0F);
        assertEquals(3, frame.getGuiScale());
        assertBounds(object.getVisibleBounds(),
                93.5F, 43.5F, 107.5F, 57.5F);
        assertEquals(14.0F,
                object.getVisibleBounds().getWidth(), 0.0F);
        assertEquals(14.0F,
                object.getVisibleBounds().getHeight(), 0.0F);
        assertBounds(object.getInteractionBounds(),
                92.5F, 42.5F, 108.5F, 58.5F);
    }

    @Test
    public void clusterBoundsContainTheCompleteFan() {
        LostTalesMapMarkerRenderedGeometry.Frame frame = frame(
                new Object(), -2.0F, 2, 3);
        LostTalesMapMarkerRenderedGeometry.RenderedObject object =
                frame.beginObject("losttales:a", 0, 3);
        add(frame, object, 0, "losttales:a",
                100.0F, 50.0F, 0.0F, 0.0F, 1.0F);
        add(frame, object, 1, "losttales:b",
                96.0F, 48.0F, -4.0F, -2.0F, 0.72F);
        add(frame, object, 2, "losttales:c",
                104.0F, 48.0F, 4.0F, -2.0F, 0.72F);
        frame.finishObject(object);

        assertTrue(object.isCluster());
        assertEquals(3, object.getRepresentedMemberCount());
        assertEquals(3, object.getVisibleMemberCount());
        assertBounds(object.getVisibleBounds(),
                89.5F, 41.5F, 111.5F, 57.5F);
        assertBounds(object.getInteractionBounds(),
                88.5F, 40.5F, 112.5F, 58.5F);
        assertEquals(100.0F, object.getNameAnchorX(), 0.0F);
        assertEquals(50.0F, object.getNameAnchorY(), 0.0F);
        assertEquals(100.5F, object.getStatusAnchorX(), 0.0F);
        assertEquals(60.5F, object.getStatusAnchorY(), 0.0F);
    }

    @Test
    public void hiddenCondensedMembersDoNotExpandVisibleBounds() {
        LostTalesMapMarkerRenderedGeometry.Frame frame = frame(
                new Object(), -3.0F, 1, 3);
        LostTalesMapMarkerRenderedGeometry.RenderedObject object =
                frame.beginObject("losttales:a", 0, 3);
        add(frame, object, 0, "losttales:a",
                10.0F, 10.0F, 0.0F, 0.0F, 1.0F);
        add(frame, object, 1, "losttales:b",
                6.0F, 8.0F, -4.0F, -2.0F, 0.72F);
        add(frame, object, 2, "losttales:hidden",
                500.0F, 500.0F, 0.0F, 0.0F, 0.0F);
        frame.finishObject(object);

        assertEquals(3, object.getRepresentedMemberCount());
        assertEquals(2, object.getVisibleMemberCount());
        assertBounds(object.getVisibleBounds(),
                -0.5F, 1.5F, 17.5F, 17.5F);
        assertFalse(frame.getMemberForCandidate(2).isVisible());
    }

    @Test
    public void memberLayoutRecordsFanAndAnimationOffsets() {
        LostTalesMapMarkerRenderedGeometry.Frame frame = frame(
                new Object(), 1.0F, 2, 1);
        LostTalesMapMarkerRenderedGeometry.RenderedObject object =
                frame.beginObject("losttales:a", 0, 1);
        frame.addMember(object, 0, "losttales:a",
                30.0F, 40.0F, 24.0F, 37.0F,
                7.5F, 7.5F, 7.5F, 15.0F / 13.0F,
                0.4F, 0.6F, -4.0F, -2.0F);
        frame.finishObject(object);
        LostTalesMapMarkerRenderedGeometry.Member member =
                frame.getMemberForCandidate(0);

        assertEquals(24.0F, member.getCenterX(), 0.0F);
        assertEquals(37.0F, member.getCenterY(), 0.0F);
        assertEquals(7.5F, member.getArtHalfWidth(), 0.0F);
        assertEquals(7.5F, member.getArtAbove(), 0.0F);
        assertEquals(7.5F, member.getArtBelow(), 0.0F);
        assertEquals(15.0F / 13.0F,
                member.getVisualScale(), 0.0F);
        assertEquals(0.4F,
                member.getTransitionVisibility(), 0.0F);
        assertEquals(0.6F, member.getRenderAlpha(), 0.0F);
        assertEquals(-4.0F, member.getFanOffsetX(), 0.0F);
        assertEquals(-2.0F, member.getFanOffsetY(), 0.0F);
        assertEquals(-6.0F, member.getAnimationOffsetX(), 0.0F);
        assertEquals(-3.0F, member.getAnimationOffsetY(), 0.0F);
    }

    @Test
    public void interactionUsesTheCompleteVisibleObjectWithSmallPadding() {
        LostTalesMapMarkerRenderedGeometry.Frame frame = frame(
                new Object(), 0.0F, 1, 1);
        LostTalesMapMarkerRenderedGeometry.RenderedObject object =
                frame.beginObject("losttales:a", 0, 1);
        add(frame, object, 0, "losttales:a",
                10.0F, 10.0F, 0.0F, 0.0F, 1.0F);
        frame.finishObject(object);

        assertTrue(object.containsInteractionPoint(2.5F, 10.0F));
        assertTrue(object.containsInteractionPoint(18.5F, 10.0F));
        assertFalse(object.containsInteractionPoint(2.49F, 10.0F));
        assertFalse(object.containsInteractionPoint(18.51F, 10.0F));
        assertEquals(0.0D,
                object.distanceSqToVisibleMemberCenter(
                        10.0F, 10.0F), 0.0D);
    }

    @Test
    public void theWholeFanIsOneTarget() {
        LostTalesMapMarkerRenderedGeometry.Frame frame = frame(
                new Object(), 0.0F, 1, 3);
        LostTalesMapMarkerRenderedGeometry.RenderedObject object =
                frame.beginObject("losttales:a", 0, 3);
        add(frame, object, 0, "losttales:a",
                100.0F, 50.0F, 0.0F, 0.0F, 1.0F);
        add(frame, object, 1, "losttales:b",
                96.0F, 48.0F, -4.0F, -2.0F, 0.5F);
        add(frame, object, 2, "losttales:c",
                104.0F, 48.0F, 4.0F, -2.0F, 0.5F);
        frame.finishObject(object);

        // The gaps between the sprites belong to the stack too, so a
        // neighbouring marker cannot win a click inside its outline.
        assertTrue(object.containsInteractionPoint(96.0F, 42.0F));
        assertTrue(object.containsInteractionPoint(104.0F, 42.0F));
        assertTrue(object.containsInteractionPoint(90.0F, 50.0F));
        assertFalse(object.containsInteractionPoint(120.0F, 50.0F));
    }

    @Test
    public void aMemberOnItsWayOutStopsWideningTheStack() {
        LostTalesMapMarkerRenderedGeometry.Frame frame = frame(
                new Object(), 0.0F, 1, 2);
        LostTalesMapMarkerRenderedGeometry.RenderedObject object =
                frame.beginObject("losttales:a", 0, 2);
        add(frame, object, 0, "losttales:a",
                0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
        // Most of the way to its own position, far from the leader.
        add(frame, object, 1, "losttales:b",
                100.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.9F);
        frame.finishObject(object);

        assertFalse("a departing member must not stretch the target area",
                object.containsInteractionPoint(50.0F, 0.0F));
        assertTrue(object.containsInteractionPoint(0.0F, 0.0F));
    }

    @Test
    public void hoverFollowsTheInkNotTheAtlasCell() {
        // The pin glyph is 9x13 of ink inside a 17-pixel cell. Drawn at size
        // 13 its ink spans roughly 3.44 left/right and 4.97 up/down from the
        // marker coordinate, plus the one-pixel shadow and one pixel of
        // interaction padding.
        LostTalesCompassMarkerIcon pin =
                LostTalesCompassMarkerIcon.UNDISCOVERED;

        assertTrue(over(pin, 96, 50));
        assertTrue(over(pin, 105, 50));
        assertTrue(over(pin, 100, 45));
        assertTrue(over(pin, 100, 56));

        assertFalse(over(pin, 95, 50));
        assertFalse(over(pin, 106, 50));
        assertFalse(over(pin, 100, 44));
        assertFalse(over(pin, 100, 57));
    }

    @Test
    public void hoverIsNoLongerBiasedTowardTheEmptyRightMargin() {
        // The regression: the cell's transparent margin is six pixels on the
        // right and two on the left, so a cell-shaped hit box reached far
        // further past the art on one side than the other.
        LostTalesCompassMarkerIcon pin =
                LostTalesCompassMarkerIcon.UNDISCOVERED;
        float half = LostTalesLotrMapMarkerIconOverlay.artHalfWidth(
                pin, 13.0F);

        assertTrue("ink is narrower than the 13-pixel quad",
                half * 2.0F < 13.0F);
        // Symmetric about the coordinate, apart from the shadow.
        assertEquals(
                LostTalesLotrMapMarkerIconOverlay.artAbove(pin, 13.0F),
                LostTalesLotrMapMarkerIconOverlay.artBelow(pin, 13.0F),
                0.0001F);
    }

    private static boolean over(
            LostTalesCompassMarkerIcon icon, int mouseX, int mouseY) {
        return LostTalesLotrMapMarkerIconOverlay.isMouseOverAtlasIcon(
                icon, 100.0F, 50.0F, 13.0F, mouseX, mouseY);
    }

    @Test
    public void statusAnchorSitsBelowTheFanWithoutTouchingIt() {
        LostTalesMapMarkerRenderedGeometry.Frame frame = frame(
                new Object(), 1.0F, 2, 3);
        LostTalesMapMarkerRenderedGeometry.RenderedObject object =
                frame.beginObject("losttales:a", 0, 5);
        add(frame, object, 0, "losttales:a",
                100.0F, 50.0F, 0.0F, 0.0F, 1.0F);
        add(frame, object, 1, "losttales:b",
                96.0F, 48.0F, -4.0F, -2.0F, 0.72F);
        add(frame, object, 2, "losttales:c",
                104.0F, 48.0F, 4.0F, -2.0F, 0.72F);
        frame.finishObject(object);

        assertEquals(LostTalesMapMarkerRenderedGeometry.GROUP_LABEL_GAP,
                object.getStatusAnchorY()
                        - object.getVisibleBounds().getBottom(), 0.0F);
        assertEquals(object.getVisibleBounds().getCenterX(),
                object.getStatusAnchorX(), 0.0F);
        // The summary must clear the artwork and stay clear of the name,
        // which is drawn above the representative.
        assertTrue(object.getStatusAnchorY()
                > object.getVisibleBounds().getBottom());
        assertTrue(object.getStatusAnchorY() > object.getNameAnchorY());
    }

    @Test
    public void frameReusesGeometryRecordsAfterWarmup() {
        LostTalesMapMarkerRenderedGeometry.Frame frame = frame(
                new Object(), 0.0F, 1, 1);
        LostTalesMapMarkerRenderedGeometry.RenderedObject first =
                frame.beginObject("losttales:a", 0, 1);
        add(frame, first, 0, "losttales:a",
                0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
        frame.finishObject(first);
        LostTalesMapMarkerRenderedGeometry.Member firstMember =
                frame.getMemberForCandidate(0);

        frame.begin(new Object(), 1.0F, 2, 1);
        LostTalesMapMarkerRenderedGeometry.RenderedObject second =
                frame.beginObject("losttales:b", 0, 1);
        add(frame, second, 0, "losttales:b",
                20.0F, 30.0F, 0.0F, 0.0F, 1.0F);
        frame.finishObject(second);

        assertSame(first, second);
        assertSame(firstMember, frame.getMemberForCandidate(0));
        assertEquals("losttales:b", second.getIdentity());
    }

    private static LostTalesMapMarkerRenderedGeometry.Frame frame(
            Object gui, float zoom, int guiScale, int candidates) {
        LostTalesMapMarkerRenderedGeometry.Frame frame =
                new LostTalesMapMarkerRenderedGeometry.Frame();
        frame.begin(gui, zoom, guiScale, candidates);
        return frame;
    }

    /**
     * Adds a settled member: index 0 leads the stack, the rest are gathered
     * into its fan. Visibility is how far a member has travelled out of the
     * fan, which is separate from how opaque it is drawn.
     */
    private static void add(
            LostTalesMapMarkerRenderedGeometry.Frame frame,
            LostTalesMapMarkerRenderedGeometry.RenderedObject object,
            int candidateIndex, String id,
            float centerX, float centerY,
            float fanX, float fanY, float alpha) {
        add(frame, object, candidateIndex, id, centerX, centerY,
                fanX, fanY, alpha, candidateIndex == 0 ? 1.0F : 0.0F);
    }

    private static void add(
            LostTalesMapMarkerRenderedGeometry.Frame frame,
            LostTalesMapMarkerRenderedGeometry.RenderedObject object,
            int candidateIndex, String id,
            float centerX, float centerY,
            float fanX, float fanY, float alpha, float visibility) {
        frame.addMember(object, candidateIndex, id,
                centerX - fanX, centerY - fanY,
                centerX, centerY,
                6.5F, 6.5F, 6.5F, 1.0F, visibility, alpha,
                fanX, fanY);
    }

    private static void assertBounds(
            LostTalesMapMarkerRenderedGeometry.Bounds bounds,
            float left, float top, float right, float bottom) {
        assertTrue(bounds.isValid());
        assertEquals(left, bounds.getLeft(), 0.0F);
        assertEquals(top, bounds.getTop(), 0.0F);
        assertEquals(right, bounds.getRight(), 0.0F);
        assertEquals(bottom, bounds.getBottom(), 0.0F);
    }
}
