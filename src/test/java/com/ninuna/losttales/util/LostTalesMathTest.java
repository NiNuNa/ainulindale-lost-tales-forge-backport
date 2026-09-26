package com.ninuna.losttales.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class LostTalesMathTest {

    @Test
    public void aSumAddsWhileItFits() {
        assertEquals(15L, LostTalesMath.saturatingAdd(10L, 5L));
        assertEquals(5L, LostTalesMath.saturatingAdd(10L, -5L));
        assertEquals(Long.MAX_VALUE, LostTalesMath.saturatingAdd(Long.MAX_VALUE - 1L, 1L));
    }

    @Test
    public void aSumPastTheLargestLongStaysThere() {
        assertEquals(Long.MAX_VALUE, LostTalesMath.saturatingAdd(Long.MAX_VALUE, 1L));
        assertEquals(Long.MAX_VALUE, LostTalesMath.saturatingAdd(Long.MAX_VALUE - 3L, Long.MAX_VALUE));
    }
}
