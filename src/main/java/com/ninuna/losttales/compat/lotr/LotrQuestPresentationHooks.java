package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.config.LostTalesConfig;

/** Client presentation choices used by the LOTR quest compatibility hook. */
public final class LotrQuestPresentationHooks {
    private LotrQuestPresentationHooks() {}

    public static boolean shouldRenderNativeTracker() {
        return LostTalesConfig.showNativeLotrQuestTracker;
    }
}
