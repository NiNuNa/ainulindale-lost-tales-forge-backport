package com.ninuna.losttales.client.gui;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

public class LostTalesWindowDefaultsTest {
    @Test
    public void replacesOnlyTheLegacyDefaultPair() {
        assertArrayEquals(new int[] {1024, 768}, LostTalesWindowDefaults.resolve(854, 480));
        for (int[] custom : new int[][] {{1024, 768}, {1920, 1080}, {854, 600},
                {640, 480}, {320, 240}}) {
            assertArrayEquals(custom, LostTalesWindowDefaults.resolve(custom[0], custom[1]));
        }
    }
}
