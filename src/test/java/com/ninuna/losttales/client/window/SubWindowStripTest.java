package com.ninuna.losttales.client.window;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A sub-window's strip keeps its whole name and the room its controls
 * leave it, so a name too long for that room is cut there, fading into
 * its end and sliding along under the pointer as a tab's does (LOOK-37),
 * never shortened with dots.
 */
public final class SubWindowStripTest {

    private static final String LONG_NAME =
            "A name far too long for the narrowest strip there is";

    @Test
    public void theStripKeepsItsWholeName() {
        SubWindowStrip strip = SubWindowStrip.layOut(0,
                SubWindowStrip.minWidth(), 0, LONG_NAME, true);
        assertEquals(LONG_NAME, strip.label);
    }

    /** The narrowest strip leaves the name the few letters it is made to keep. */
    @Test
    public void theNameKeepsTheRoomItsControlsLeave() {
        int least = SubWindowStrip.minWidth();
        SubWindowStrip narrowest = SubWindowStrip.layOut(0, least, 0,
                LONG_NAME, true);
        SubWindowStrip wider = SubWindowStrip.layOut(0, least + 40, 0,
                LONG_NAME, true);
        assertEquals(40, wider.labelRoom - narrowest.labelRoom);
        // Without an icon the name starts where the icon would have stood.
        SubWindowStrip bare = SubWindowStrip.layOut(0, least, 0, LONG_NAME,
                false);
        assertEquals(narrowest.textX - (TabIcons.SLOT + TabIcons.GAP),
                bare.textX);
        assertEquals(narrowest.labelRoom + TabIcons.SLOT + TabIcons.GAP,
                bare.labelRoom);
    }

    @Test
    public void aStripTooNarrowForAnyNameLeavesItNoRoom() {
        SubWindowStrip strip = SubWindowStrip.layOut(0, 10, 0, LONG_NAME,
                true);
        assertEquals(0, strip.labelRoom);
        assertEquals("", SubWindowStrip.layOut(0, 100, 0, null, true).label);
    }
}
