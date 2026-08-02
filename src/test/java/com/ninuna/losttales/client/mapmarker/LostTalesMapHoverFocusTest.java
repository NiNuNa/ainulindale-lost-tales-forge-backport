package com.ninuna.losttales.client.mapmarker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class LostTalesMapHoverFocusTest {
    @Test
    public void defaultDelayStaysResponsive() {
        assertEquals(80000000L,
                LostTalesMapHoverFocus.DEFAULT_DELAY_NANOS);
    }

    @Test
    public void focusActivatesOnlyAfterTheConfiguredDelay() {
        LostTalesMapHoverFocus focus =
                new LostTalesMapHoverFocus(100L);

        assertEquals("", focus.update("marker:a", 1000L));
        assertEquals("", focus.update("marker:a", 1099L));
        assertEquals("marker:a", focus.update("marker:a", 1100L));
    }

    @Test
    public void changingCandidatesImmediatelyClearsTheOldFocus() {
        LostTalesMapHoverFocus focus =
                new LostTalesMapHoverFocus(100L);
        focus.update("marker:a", 1000L);
        focus.update("marker:a", 1100L);

        assertEquals("", focus.update("marker:b", 1110L));
        assertEquals("", focus.getActiveKey());
        assertEquals("marker:b", focus.update("marker:b", 1210L));
    }

    @Test
    public void leavingEveryIconClearsPendingAndActiveFocus() {
        LostTalesMapHoverFocus focus =
                new LostTalesMapHoverFocus(100L);
        focus.update("marker:a", 1000L);
        focus.update("marker:a", 1100L);

        assertEquals("", focus.update("", 1110L));
        assertEquals("", focus.getActiveKey());
        assertEquals("", focus.update("marker:a", 1150L));
    }
}
