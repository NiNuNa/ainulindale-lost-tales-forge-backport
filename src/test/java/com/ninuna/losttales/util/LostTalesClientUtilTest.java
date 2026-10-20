package com.ninuna.losttales.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/** The item tooltips' lore part. */
public final class LostTalesClientUtilTest {

    /** An item without lore lines shows no hint to hold Shift, and no raw key. */
    @Test
    public void anItemWithoutLoreShowsNoLorePart() {
        List<String> tooltip = new ArrayList<String>();

        LostTalesClientUtil.addItemLore(tooltip, new String[0]);

        assertTrue(tooltip.isEmpty());
    }
}
