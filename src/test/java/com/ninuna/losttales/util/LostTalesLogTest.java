package com.ninuna.losttales.util;

import org.junit.Test;

/**
 * A warning never fails its caller: here, outside a running game, FML's
 * logger is not set up and the line is dropped.
 */
public final class LostTalesLogTest {

    @Test
    public void aWarningOutsideAGameIsDroppedQuietly() {
        LostTalesLog.warning("Party data uses unsupported version %d; data will "
                + "remain read-only", Integer.valueOf(3));
        LostTalesLog.warning("No arguments at all");
        LostTalesLog.warning("A null argument: %s", (Object) null);
        LostTalesLog.warning("Null arguments", (Object[]) null);
    }
}
