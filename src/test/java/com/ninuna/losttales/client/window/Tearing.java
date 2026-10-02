package com.ninuna.losttales.client.window;

import java.util.Collections;
import java.util.List;

/**
 * Tears tabs off their window as a hand does, for tests: into a window
 * at a percent place, at a fixed size, since there is no window box to
 * measure the size from here.
 */
public final class Tearing {
    /** The size a torn-off window takes here. */
    public static final int WIDTH = 300;
    public static final double HEIGHT = 200.0D;

    private Tearing() {}

    public static Window off(WindowPage tab, double x, double y) {
        return off(Collections.singletonList(tab), x, y);
    }

    public static Window off(List<? extends WindowPage> tabs, double x,
                             double y) {
        return WindowLayout.tearOff(tabs, x, y, WIDTH, HEIGHT);
    }
}
