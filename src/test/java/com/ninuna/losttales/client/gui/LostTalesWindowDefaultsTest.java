package com.ninuna.losttales.client.gui;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class LostTalesWindowDefaultsTest {
    @Test
    public void replacesOnlyTheLegacyDefaultPair() {
        assertEquals(1024, LostTalesWindowDefaults.width(854, 480));
        assertEquals(768, LostTalesWindowDefaults.height(854, 480));
        for (int[] custom : new int[][] {{1024, 768}, {1920, 1080}, {854, 600},
                {640, 480}, {320, 240}}) {
            assertEquals(custom[0], LostTalesWindowDefaults.width(custom[0], custom[1]));
            assertEquals(custom[1], LostTalesWindowDefaults.height(custom[0], custom[1]));
        }
    }
}
