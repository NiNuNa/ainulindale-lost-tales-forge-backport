package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class LostTalesLotrMapLabelStyleTest {
    private static final int OPAQUE = 0xFF000000;

    @Test
    public void plainWhiteBecomesTheInterfacesOwnWhite() {
        assertEquals(OPAQUE | LostTalesLotrMapLabelStyle.LABEL_RGB,
                LostTalesLotrMapLabelStyle.restyle(OPAQUE | 0xFFFFFF));
        assertEquals(LostTalesLotrMapLabelStyle.LABEL_RGB,
                LostTalesLotrMapLabelStyle.restyleMapSubtitle(0xFFFFFF));
    }

    /**
     * The same pass draws each label's drop shadow, and on the old-school map
     * an amber highlight. Recolouring those would turn a shadow into a second
     * copy of the text and lose a deliberate colour.
     */
    @Test
    public void everyOtherColourIsLeftExactlyAsItWas() {
        assertEquals(OPAQUE, LostTalesLotrMapLabelStyle.restyle(OPAQUE));
        assertEquals(OPAQUE | 0xFF9900,
                LostTalesLotrMapLabelStyle.restyle(OPAQUE | 0xFF9900));
        assertEquals(OPAQUE | 0xFEFEFE,
                LostTalesLotrMapLabelStyle.restyle(OPAQUE | 0xFEFEFE));
    }

    /** Labels fade in and out with the zoom, so the alpha has to survive. */
    @Test
    public void whateverAlphaTheLabelAskedForIsKept() {
        assertEquals(0x40000000 | LostTalesLotrMapLabelStyle.LABEL_RGB,
                LostTalesLotrMapLabelStyle.restyle(0x40FFFFFF));
        assertEquals(LostTalesLotrMapLabelStyle.LABEL_RGB,
                LostTalesLotrMapLabelStyle.restyle(0x00FFFFFF));
    }
}
