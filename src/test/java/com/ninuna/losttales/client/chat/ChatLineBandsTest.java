package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChatLineBandsTest {

    @Test
    public void findsTheBandExactlyAsRecorded() {
        ChatLineBands bands = new ChatLineBands();
        List<Object> source = new ArrayList<Object>();
        bands.reset(source, 0, 1.0F);
        // Two contiguous 11px bands above a baseline at y=300, newest first.
        bands.add(0, 2.0F, 322.0F, 289.0F, 300.0F);
        bands.add(1, 2.0F, 322.0F, 278.0F, 289.0F);

        assertEquals(0, bands.find(10.0F, 299.0F));
        assertEquals(0, bands.find(10.0F, 289.0F));
        assertEquals(1, bands.find(10.0F, 288.9F));
        assertEquals(1, bands.find(10.0F, 278.0F));
        assertEquals(-1, bands.find(10.0F, 277.9F));
        assertEquals(-1, bands.find(10.0F, 300.0F));
        assertEquals(-1, bands.find(1.9F, 295.0F));
        assertEquals(-1, bands.find(322.0F, 295.0F));
        assertEquals(1, bands.viewIndexOf(1));
        assertTrue(bands.describes(source, 0));
        assertFalse(bands.describes(source, 1));
        assertFalse(bands.describes(new ArrayList<Object>(), 0));
    }

    @Test
    public void mapsScreenXIntoLineSpaceThroughScaleAndSlide() {
        ChatLineBands bands = new ChatLineBands();
        bands.reset(null, 0, 2.0F);
        // A line slid 6px right at chat scale 2: text origin at x=8.
        bands.add(0, 8.0F, 648.0F, 278.0F, 300.0F);
        assertEquals(0, bands.find(100.0F, 290.0F));
        assertEquals(46.0F, bands.localX(0, 100.0F), 0.0001F);
        assertEquals(2.0F, bands.scale(), 0.0F);
    }

    @Test
    public void growsPastInitialCapacityAndResets() {
        ChatLineBands bands = new ChatLineBands();
        bands.reset(null, 0, 1.0F);
        for (int index = 0; index < 40; index++) {
            bands.add(index, 0.0F, 100.0F, index * 11.0F,
                    index * 11.0F + 11.0F);
        }
        assertEquals(40, bands.count());
        assertEquals(39, bands.find(50.0F, 39 * 11.0F + 5.0F));
        bands.reset(null, 0, 1.0F);
        assertEquals(0, bands.count());
        assertEquals(-1, bands.find(50.0F, 5.0F));
    }
}
