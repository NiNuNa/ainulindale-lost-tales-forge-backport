package com.ninuna.losttales.client.diagnostics;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.util.HashSet;
import java.util.Set;

/** Once-per-process warnings for optional client rendering integrations. */
public final class LostTalesClientDiagnostics {
    private static final Set<String> WARNED_KEYS = new HashSet<String>();

    private LostTalesClientDiagnostics() {}

    public static synchronized void warnOnce(
            String key, String message, Throwable throwable) {
        if (key == null || !WARNED_KEYS.add(key)) {
            return;
        }
        String reason = throwable == null
                ? "unknown error" : throwable.toString();
        FMLLog.warning("[%s] %s (%s)",
                LostTalesMetaData.MOD_ID, message, reason);
    }
}
