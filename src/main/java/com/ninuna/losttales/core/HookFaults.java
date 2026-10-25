package com.ninuna.losttales.core;

import cpw.mods.fml.common.FMLLog;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.Level;

/**
 * Where a hook's fault ends (DATA-08). Every call the coremod injects
 * into vanilla or LOTR runs inside a catch that hands the fault here and
 * then answers what the game would have had without the patch, so the
 * game carries on. Each hook's first fault is logged with its stack
 * trace; later ones are not, so a hook that fails every frame does not
 * fill the log. What has been logged is kept for the whole run of the
 * game, across worlds: a hook's fault is the build's, not a world's.
 *
 * <p>The guards call this from vanilla's own classes, some of them
 * before Forge is set up, so it needs nothing but FML's logger, and a
 * logger that cannot write yet costs only the line.</p>
 */
public final class HookFaults {

    private static final Set<String> REPORTED = Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());

    private HookFaults() {}

    /** Reports the fault of the hook {@code hook} names, once per hook. */
    public static void caught(String hook, Throwable fault) {
        String name = hook == null ? "an unnamed hook" : hook;
        if (!REPORTED.add(name)) {
            return;
        }
        try {
            FMLLog.log(Level.ERROR, fault, "[losttales] The hook %s failed; the game"
                    + " carries on there as it would without the patch. Its later"
                    + " faults are not logged.", name);
        } catch (Throwable unavailable) {
            // FML's logger is not set up this early; the fault is still caught.
        }
    }

    /** Whether the hook has failed since the game started. */
    static boolean hasFailed(String hook) {
        return REPORTED.contains(hook);
    }

    /** Forgets every fault, so a test starts from none. */
    static void forget() {
        REPORTED.clear();
    }
}
