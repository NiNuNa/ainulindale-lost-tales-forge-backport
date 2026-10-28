package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.config.LostTalesConfig;

/** Whether LOTR's own quest tracker draws; asked by the coremod's patch of it. */
public final class LotrQuestPresentationHooks {
    private LotrQuestPresentationHooks() {}

    public static boolean shouldRenderNativeTracker() {
        return LostTalesConfig.showNativeLotrQuestTracker;
    }
}
