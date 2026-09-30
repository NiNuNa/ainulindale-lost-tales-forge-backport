package com.ninuna.losttales.config.client;

import java.util.Set;
import java.util.TreeSet;
import net.minecraftforge.common.config.Configuration;

/** The key of every camera option, read as the first load defines the options. */
public final class DefinedCameraOptions {
    private DefinedCameraOptions() {}

    public static Set<String> keys() {
        Configuration definitions = new Configuration();
        LostTalesThirdPersonConfig.defineOptions(definitions);
        return new TreeSet<String>(definitions.getCategory(
                LostTalesThirdPersonConfig.CATEGORY_CAMERA).keySet());
    }
}
