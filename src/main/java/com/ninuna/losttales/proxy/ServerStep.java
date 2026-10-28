package com.ninuna.losttales.proxy;

import com.ninuna.losttales.util.LostTalesLog;

/**
 * One step of the server's start or stop. A step that throws is logged
 * by its name and the caller goes on to its next step, so one fault never
 * skips the resets after it and leaves a store holding the last world's
 * data. An {@link Error} is not caught.
 */
public final class ServerStep {

    private ServerStep() {}

    /** Runs {@code work}; whether it ran through without a fault. */
    public static boolean run(String name, Runnable work) {
        try {
            work.run();
            return true;
        } catch (RuntimeException fault) {
            LostTalesLog.error(fault, "The server step %s failed;"
                    + " the steps after it still run", name);
            return false;
        }
    }
}
