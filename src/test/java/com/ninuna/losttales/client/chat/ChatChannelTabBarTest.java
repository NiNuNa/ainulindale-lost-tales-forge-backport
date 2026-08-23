package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/**
 * The row's way of fitting too many tabs: the widest labels are capped
 * to one common width, narrower ones keep theirs, and the cap is the
 * largest that fits — so tabs shrink evenly, like a browser's.
 */
public final class ChatChannelTabBarTest {

    @Test
    public void widestLabelsAreCappedToOneWidthThatFits() {
        int[] widths = {40, 10, 60, 25};
        ChatChannelTabBar.capLabels(widths, 135);
        // Everything fits whole: nothing changes.
        assertArrayEquals(new int[] {40, 10, 60, 25}, widths);
        widths = new int[] {40, 10, 60, 25};
        ChatChannelTabBar.capLabels(widths, 100);
        // 60 and 40 come down to the same cap; 25 + 10 + 2 * cap <= 100.
        assertArrayEquals(new int[] {32, 10, 32, 25}, widths);
        widths = new int[] {40, 10, 60, 25};
        ChatChannelTabBar.capLabels(widths, 40);
        assertArrayEquals(new int[] {10, 10, 10, 10}, widths);
    }

    @Test
    public void noRoomLeavesNoLabelRatherThanAnOverflow() {
        int[] widths = {40, 10};
        ChatChannelTabBar.capLabels(widths, 0);
        assertArrayEquals(new int[] {0, 0}, widths);
        widths = new int[] {40, 10};
        ChatChannelTabBar.capLabels(widths, -5);
        assertArrayEquals(new int[] {0, 0}, widths);
    }
}
