package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Where a sub-window hangs from a button: on the side it opens toward,
 * or the other side where only that one has room; lined up with the
 * button's left edge, or its right edge growing leftward; kept in its
 * room. And beside the sub-window that opened it, right first.
 */
public final class SubWindowAnchorTest {
    private static final LostTalesUiHitBox ROOM =
            new LostTalesUiHitBox(2.0D, 2.0D, 956.0D, 536.0D);
    private static final int STRIP = SubWindow.STRIP_HEIGHT;
    private static final int REACH = SubWindowAnchor.REACH;

    /** Near the top right it hangs below, its right edge on the button's. */
    @Test
    public void belowAndFromTheRight() {
        SubWindowAnchor anchor = new SubWindowAnchor(900, 10, 911, 21, true,
                true, null);
        assertBox(747, 21 + REACH + STRIP, 164, 60,
                anchor.contentBoxFor(164, 60, ROOM));
    }

    /** Near the bottom it hangs above, its left edge on the button's. */
    @Test
    public void aboveAndFromTheLeft() {
        SubWindowAnchor anchor = new SubWindowAnchor(40, 520, 51, 531, false,
                false, null);
        assertBox(40, 520 - REACH - (60 + STRIP) + STRIP, 164, 60,
                anchor.contentBoxFor(164, 60, ROOM));
    }

    /** Asked to hang below with no room there, it hangs above instead. */
    @Test
    public void turnsRoundWhereItsSideHasNoRoom() {
        SubWindowAnchor anchor = new SubWindowAnchor(40, 500, 51, 511, true,
                false, null);
        assertBox(40, 500 - REACH - (60 + STRIP) + STRIP, 164, 60,
                anchor.contentBoxFor(164, 60, ROOM));
    }

    /** Pressed against the room's right edge, it stays inside it. */
    @Test
    public void staysInItsRoom() {
        SubWindowAnchor anchor = new SubWindowAnchor(900, 10, 911, 21, true,
                false, null);
        assertBox(958 - 164, 21 + REACH + STRIP, 164, 60,
                anchor.contentBoxFor(164, 60, ROOM));
    }

    /** Beside the sub-window that opened it: right where there is room, else left. */
    @Test
    public void besideItsOpener() {
        int gap = WindowPlacement.WINDOW_GAP;
        assertBox(100 + 120 + gap, 50 + STRIP, 164, 60,
                SubWindowAnchor.contentBoxBeside(new LostTalesUiHitBox(
                        100.0D, 50.0D, 120.0D, 80.0D), 164, 60, ROOM));
        assertBox(800 - gap - 164, 50 + STRIP, 164, 60,
                SubWindowAnchor.contentBoxBeside(new LostTalesUiHitBox(
                        800.0D, 50.0D, 120.0D, 80.0D), 164, 60, ROOM));
    }

    private static void assertBox(double left, double top, double width,
                                  double height, LostTalesUiHitBox box) {
        assertEquals(left, box.left, 0.0D);
        assertEquals(top, box.top, 0.0D);
        assertEquals(width, box.width, 0.0D);
        assertEquals(height, box.height, 0.0D);
    }
}
